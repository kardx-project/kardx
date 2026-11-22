package io.github.kardx.internal.diagnostics

import io.github.kardx.dsl.ARRAY
import io.github.kardx.dsl.BOOLEAN
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.DocumentationMismatchException
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.eventMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.dsl.type
import io.github.kardx.mockmvc.SampleController
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.sse.performStream
import io.github.kardx.support.PayloadController
import io.github.kardx.websocket.MessageDocumentation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.snippet.SnippetException
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.nio.file.Files

/** [payload] 를 응답하는 요청을 [block] 으로 문서화하고, 던진 예외를 돌려준다. */
private fun mismatchOf(
    payload: Any,
    query: Map<String, String> = emptyMap(),
    block: DocumentationBuilder.() -> Unit,
): DocumentationMismatchException {
    val documentation = ManualRestDocumentation(Files.createTempDirectory("mismatch").toString())
    val mockMvc = MockMvcBuilders.standaloneSetup(PayloadController(payload))
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()
    val request = get("/payload").apply { query.forEach { (name, value) -> param(name, value) } }

    documentation.beforeTest(DocumentationMismatchTest::class.java, "mismatch")

    try {
        return shouldThrow<DocumentationMismatchException> { mockMvc.perform(request).andDocument(block) }
    } finally {
        documentation.afterTest()
    }
}

class DocumentationMismatchTest :
    BehaviorSpec({
        given("문서화하지 않은 필드와 없는 필드가 있을 때") {
            val error = mismatchOf(mapOf("id" to 1, "memo" to "m")) {
                responseFields("id" type NUMBER means "ID", "name" type STRING means "이름")
            }

            then("양쪽 목록과 붙여 넣을 DSL 줄을 보여 준다") {
                error.message shouldContain "Documented response fields do not match the actual response."
                error.message shouldContain "Not documented (present in the response):\n  - memo (String)"
                error.message shouldContain "Documented but not in the response:\n  - name"
                error.message shouldContain "\"memo\" type STRING means \"...\""
            }

            then("부모는 있는데 빠진 필드는 null 이 빠졌을 수 있다고 알려 준다") {
                error.message shouldContain "Absent although the parent object is present: `name`"
                error.message shouldContain "@JsonInclude(NON_NULL)"
            }

            then("SnippetException 이고 원래 예외를 cause 로 남긴다") {
                error.shouldBeInstanceOf<SnippetException>()
                error.cause.shouldBeInstanceOf<SnippetException>()
                error.cause?.message.orEmpty() shouldContain "Fields with the following paths were not found"
            }
        }

        given("응답의 배열이 비어 원소 필드가 없을 때") {
            then("빈 배열이 원인이라고 알려 준다") {
                val error = mismatchOf(mapOf("items" to emptyList<Any>())) {
                    responseFields("items" type ARRAY means "목록", "items[].price" type NUMBER means "가격")
                }

                error.message shouldContain "`items` is an empty array here"
                error.message shouldContain "responseFields(responseFieldsOf<T>().optionalUnder(\"items[]\"))"
                error.message shouldNotContain "Absent although"
            }
        }

        given("이름 전략이 다를 때") {
            then("모양만 다른 이름 짝을 알려 준다") {
                val error = mismatchOf(mapOf("user_name" to "a")) {
                    responseFields("userName" type STRING means "이름")
                }

                error.message shouldContain "`userName` (documented) vs `user_name` (actual)"
                error.message shouldContain "naming strategy"
            }
        }

        given("래퍼로 감싼 응답을 래퍼 없이 문서화했을 때") {
            then("래퍼 경로를 알려 준다") {
                val error = mismatchOf(mapOf("code" to 200, "data" to mapOf("id" to 1, "name" to "n"))) {
                    responseFields("id" type NUMBER means "ID", "name" type STRING means "이름")
                }

                error.message shouldContain "The payload has these fields under `data` (e.g. `data.id`)"
                error.message shouldContain "use wrappedResponseFields<T>(); otherwise use responseFields<T>(\"data\")."
                error.message shouldNotContain "FieldsOf"
            }
        }

        given("감싸지 않은 응답을 래퍼와 함께 문서화했을 때") {
            then("이 응답은 감싸지 않았다고 알려 준다") {
                val error = mismatchOf(mapOf("id" to 1)) {
                    responseFields(
                        "code" type NUMBER means "코드",
                        "data" type OBJECT means "데이터",
                        "data.id" type NUMBER means "ID",
                    )
                }

                error.message shouldContain "Fields documented under `data` (e.g. `data.id`) are at the top level"
                error.message shouldContain "with responseFields<T>() instead of wrappedResponseFields<T>()."
                error.message shouldNotContain "Absent although"
            }
        }

        given("쿼리 파라미터가 맞지 않을 때") {
            then("파라미터 DSL 줄을 제안한다") {
                val error = mismatchOf(mapOf("ok" to true), query = mapOf("page" to "1")) {
                    queryParameters("size" queryMeans "크기")
                    responseFields("ok" type BOOLEAN means "성공")
                }

                error.message shouldContain "Documented query parameters do not match the actual request."
                error.message shouldContain "\"page\" queryMeans \"...\""
                error.message shouldContain "Documented but not in the request:\n  - size"
            }
        }

        given("SSE 스트림") {
            then("문서화하지 않은 이벤트에 이벤트 DSL 줄을 제안한다") {
                val documentation = ManualRestDocumentation(Files.createTempDirectory("mismatch").toString())
                val mockMvc = MockMvcBuilders.standaloneSetup(SampleController())
                    .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
                    .build()

                documentation.beforeTest(DocumentationMismatchTest::class.java, "stream")

                val error = try {
                    shouldThrow<DocumentationMismatchException> {
                        mockMvc.performStream(get("/live"), eventCount = 2)
                            .andDocument { events("connected" eventMeans "연결") }
                    }
                } finally {
                    documentation.afterTest()
                }

                error.message shouldContain "Documented SSE events do not match the actual stream."
                error.message shouldContain "\"tick\" eventMeans \"...\""
            }
        }

        given("메시지 페이로드") {
            then("메시지 필드도 같은 진단을 한다") {
                val documentation = ManualRestDocumentation(Files.createTempDirectory("mismatch").toString())

                documentation.beforeTest(DocumentationMismatchTest::class.java, "message")

                val error = try {
                    shouldThrow<DocumentationMismatchException> {
                        MessageDocumentation(documentation).send("/app/chat", mapOf("text" to "hi", "room" to 1)) {
                            fields("text" type STRING means "본문")
                        }
                    }
                } finally {
                    documentation.afterTest()
                }

                error.message shouldContain "Documented message payload fields do not match the actual message."
                error.message shouldContain "\"room\" type NUMBER means \"...\""
            }

            then("힌트는 메시지 블록에서 등록되는 fields(...) 를 보여 준다") {
                val documentation = ManualRestDocumentation(Files.createTempDirectory("mismatch").toString())

                documentation.beforeTest(DocumentationMismatchTest::class.java, "message-empty-array")

                val error = try {
                    shouldThrow<DocumentationMismatchException> {
                        MessageDocumentation(documentation).send("/app/chat", mapOf("tags" to emptyList<Any>())) {
                            fields("tags" type ARRAY means "태그", "tags[].name" type STRING means "이름")
                        }
                    }
                } finally {
                    documentation.afterTest()
                }

                error.message shouldContain "`tags` is an empty array here"
                error.message shouldContain "fields(requestFieldsOf<T>().optionalUnder(\"tags[]\"))"
                error.message shouldNotContain "responseFields("
            }
        }
    })
