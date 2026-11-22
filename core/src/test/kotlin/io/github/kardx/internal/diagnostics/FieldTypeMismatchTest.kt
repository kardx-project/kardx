package io.github.kardx.internal.diagnostics

import io.github.kardx.dsl.ARRAY
import io.github.kardx.dsl.DATETIME
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.DocumentationMismatchException
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.type
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.support.PayloadController
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.payload.FieldTypeRequiredException
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.snippet.SnippetException
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.nio.file.Files

/** [payload] 를 응답하는 요청을 [block] 으로 문서화하고, 던진 예외를 돌려준다. */
private fun typeMismatchOf(payload: Any, block: DocumentationBuilder.() -> Unit): DocumentationMismatchException {
    val documentation = ManualRestDocumentation(Files.createTempDirectory("type-mismatch").toString())
    val mockMvc = MockMvcBuilders.standaloneSetup(PayloadController(payload))
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()

    documentation.beforeTest(FieldTypeMismatchTest::class.java, "type-mismatch")

    try {
        return shouldThrow<DocumentationMismatchException> { mockMvc.perform(get("/payload")).andDocument(block) }
    } finally {
        documentation.afterTest()
    }
}

class FieldTypeMismatchTest :
    BehaviorSpec({
        given("숫자로 문서화한 필드가 문자열로 직렬화될 때") {
            val error = typeMismatchOf(mapOf("price" to "59000")) {
                responseFields("price" type NUMBER means "가격")
            }

            then("문서화한 타입과 실제 타입, 붙여 넣을 DSL 줄을 보여 준다") {
                error.message shouldContain "Documented response fields do not match the actual response."
                error.message shouldContain "Type mismatch:\n  - price: documented Number, actual String"
                error.message shouldContain
                    "Change the documentation (replace \"...\" with a description):\n  \"price\" type STRING means \"...\""
            }

            then("문자열로 쓰는 숫자의 흔한 원인을 알려 준다") {
                error.message shouldContain "@JsonFormat(shape = JsonFormat.Shape.STRING)"
                error.message shouldContain "JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS"
                error.message shouldNotContain "WRITE_DATES_AS_TIMESTAMPS"
            }

            then("SnippetException 이고 REST Docs 의 원래 예외를 cause 로 남긴다") {
                error.shouldBeInstanceOf<SnippetException>()
                error.cause?.javaClass?.simpleName shouldBe "FieldTypesDoNotMatchException"
                error.cause?.message.orEmpty() shouldContain "The documented type of the field 'price' is Number"
            }
        }

        given("문자열로 문서화한 날짜가 타임스탬프로 직렬화될 때") {
            then("숫자면 WRITE_DATES_AS_TIMESTAMPS 를 알려 준다") {
                val error = typeMismatchOf(mapOf("createdAt" to 1_762_300_800_000L)) {
                    responseFields("createdAt" type DATETIME means "등록 일시")
                }

                error.message shouldContain "- createdAt: documented String, actual Number"
                error.message shouldContain "\"createdAt\" type NUMBER means \"...\""
                error.message shouldContain "WRITE_DATES_AS_TIMESTAMPS"
            }

            then("배열이어도 같은 힌트를 준다") {
                val error = typeMismatchOf(mapOf("date" to listOf(2026, 11, 5))) {
                    responseFields("date" type STRING means "날짜")
                }

                error.message shouldContain "- date: documented String, actual Array"
                error.message shouldContain "WRITE_DATES_AS_TIMESTAMPS"
            }
        }

        given("null 인 필드를 optional 이 아닌 것으로 문서화했을 때") {
            val error = typeMismatchOf(mapOf("nickname" to null)) {
                responseFields("nickname" type STRING means "닉네임")
            }

            then("isOptional 을 붙인 DSL 줄과 nullable 힌트를 준다") {
                error.message shouldContain "- nickname: documented String, actual Null"
                error.message shouldContain "\"nickname\" type STRING means \"...\" isOptional true"
                error.message shouldContain "make the property nullable"
                error.message shouldContain ".optionalAt(\"nickname\")"
            }
        }

        given("배열 원소마다 타입이 다를 때") {
            val error = typeMismatchOf(mapOf("items" to listOf(mapOf("price" to 1), mapOf("price" to "1")))) {
                responseFields("items" type ARRAY means "목록", "items[].price" type NUMBER means "가격")
            }

            then("VARIES 로 바꾸는 DSL 줄을 준다") {
                error.message shouldContain "- items[].price: documented Number, actual Varies"
                error.message shouldContain "\"items[].price\" type VARIES means \"...\""
                error.message shouldContain "different types across the elements of `items[]`"
            }
        }

        given("타입 없이 문서화한 optional 필드가 페이로드에 없을 때") {
            val error = typeMismatchOf(mapOf("id" to 1)) {
                responseFields(
                    "id" type NUMBER means "ID",
                    RestDocsField(
                        PayloadDocumentation.fieldWithPath("memo")
                            .description("메모")
                            .optional(),
                    ),
                )
            }

            then("타입을 정하라고 알려 준다") {
                error.message shouldContain
                    "Not in the response and documented without a type, so the type cannot be determined:\n  - memo"
                error.message shouldContain "\"memo\" type STRING means \"...\" isOptional true"
                error.cause.shouldBeInstanceOf<FieldTypeRequiredException>()
            }
        }

        given("FieldTypeMismatch.of") {
            then("REST Docs 의 타입 예외가 아니면 null") {
                val error = IllegalStateException("The documented type of the field 'a' is X but the actual type is Y")

                FieldTypeMismatch.of(error) shouldBe null
            }
        }
    })
