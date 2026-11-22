package io.github.kardx.mockmvc
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import io.github.kardx.config.ObjectMapperProvider
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.DocumentationMismatchException
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.eventMeans
import io.github.kardx.dsl.type
import io.github.kardx.extractor.fields
import io.github.kardx.extractor.responseFieldsOf
import io.github.kardx.internal.documentation.toSpec
import io.github.kardx.internal.openapi.OpenApiOperationExtras
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.HttpStatus
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.templates.TemplateFormats
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files

class ShopError(val code: String, val message: String?, val detail: ShopErrorDetail? = null)

class ShopErrorDetail(val field: String, val reason: String)

class AnnotatedShopError(@get:JsonProperty("error_code") val code: String, val retryAfterSeconds: Int)

class GenericShopError<T>(val code: String, val data: T)

@RestController
class ShopController {
    @GetMapping("/shop/missing")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun missing(): ShopError = ShopError("NOT_FOUND", "없음")

    @GetMapping("/shop/events", produces = ["text/event-stream"])
    fun events(): String = listOf("event:tick", "data:{\"n\":1}", "", "").joinToString("\n")
}

private class ShopDocs(markdown: Boolean = false) {
    val output: File = Files.createTempDirectory("error-examples").toFile()
    private val documentation = ManualRestDocumentation(output.absolutePath)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(ShopController())
        .apply<StandaloneMockMvcBuilder>(
            documentationConfiguration(documentation)
                .snippets()
                .apply {
                    if (markdown) withTemplateFormat(TemplateFormats.markdown())
                },
        )
        .build()

    fun missing(method: String, block: DocumentationBuilder.() -> Unit) = run(method) {
        mockMvc.perform(get("/shop/missing"))
            .andDocument(block)
    }

    fun events(method: String, block: DocumentationBuilder.() -> Unit) = run(method) {
        mockMvc.perform(get("/shop/events"))
            .andDocument(block)
    }

    fun file(method: String, name: String) = File(output, "error-response-example-test/$method/$name")

    fun extras(method: String): Map<*, *> =
        ObjectMapper().readValue(file(method, OpenApiOperationExtras.FILE_NAME), Map::class.java)

    private fun run(method: String, block: () -> Unit) {
        documentation.beforeTest(ErrorResponseExampleTest::class.java, method)

        try {
            block()
        } finally {
            documentation.afterTest()
        }
    }
}

private fun declare(block: DocumentationBuilder.() -> Unit) = DocumentationBuilder().apply(block)
    .toSpec()

private fun exampleOf(block: DocumentationBuilder.() -> Unit): String? = declare(block).errorResponses
    .single()
    .example

private val fieldsOfShopError: DocumentationBuilder.() -> Unit = {
    responseFields(responseFieldsOf<ShopError>())
}

class ErrorResponseExampleTest :
    BehaviorSpec({
        given("테스트가 실제로 받은 상태와 같은 errorResponse") {
            val docs = ShopDocs()

            docs.missing("received") {
                fieldsOfShopError()
                errorResponse(HttpStatus.NOT_FOUND, "상품 없음")
                errorResponse(409, "중복")
            }
            docs.missing("only-received") {
                fieldsOfShopError()
                errorResponse(404, "상품 없음") { example("""{"code":"NOT_FOUND"}""") }
            }

            then("error-responses 와 errorResponses 에서 빠지고 나머지는 남는다") {
                val snippet = docs.file("received", "error-responses.adoc")
                    .readText()

                snippet shouldNotContain "404"
                snippet shouldContain "409 Conflict"
                (docs.extras("received")["errorResponses"] as List<*>).map { (it as Map<*, *>)["status"] } shouldBe
                    listOf(409)
            }

            then("그것뿐이면 스니펫과 errorResponses 키가 없다 (오류 없음)") {
                docs.file("only-received", "error-responses.adoc").exists() shouldBe false
                docs.extras("only-received").containsKey("errorResponses") shouldBe false
            }
        }

        given("JSON 예시 뒤에 다른 토큰") {
            then("거부한다") {
                listOf("""{"a":1} junk""", """{"a":1} {"b":2}""", """{"a":1}, 2""", """[1] ]""").forEach { json ->
                    shouldThrow<IllegalArgumentException> { declare { errorResponse(400, "x") { example(json) } } }
                        .message shouldContain "is not valid JSON"
                }
            }

            then("문서 하나는 앞뒤 공백이 있어도 받는다") {
                exampleOf { errorResponse(400, "x") { example("""  {"a":1}  """) } } shouldBe "{\n  \"a\" : 1\n}"
                exampleOf { errorResponse(400, "x") { example("[1, 2]") } } shouldBe "[ 1, 2 ]"
            }
        }

        given("example(객체)") {
            then("ObjectMapper 로 직렬화한다 (중첩 객체, null)") {
                exampleOf {
                    errorResponse(400, "x") {
                        example(ShopError("INVALID", null, ShopErrorDetail("name", "blank")))
                    }
                } shouldBe
                    """
                    {
                      "code" : "INVALID",
                      "message" : null,
                      "detail" : {
                        "field" : "name",
                        "reason" : "blank"
                      }
                    }
                    """.trimIndent()
            }

            then("문자열은 그대로 JSON 으로 쓴다 (정적 타입이 Any 여도, StringBuilder 여도)") {
                val json: Any = """{"code":"X"}"""

                exampleOf { errorResponse(400, "x") { example(json) } } shouldBe "{\n  \"code\" : \"X\"\n}"
                exampleOf { errorResponse(400, "x") { example(StringBuilder("""{"code":"Y"}""")) } } shouldBe
                    "{\n  \"code\" : \"Y\"\n}"
            }

            then("@JsonProperty 와 설정한 이름 규칙을 따른다") {
                val snake = ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                val example = RestDocsConfiguration.withSettings({
                    extraction.objectMapper =
                        ObjectMapperProvider { snake }
                }) {
                    exampleOf { errorResponse(429, "x") { example(AnnotatedShopError("SLOW", 3)) } }
                }

                example shouldContain "\"error_code\" : \"SLOW\""
                example shouldContain "\"retry_after_seconds\" : 3"
            }

            then("null 인 nullable 필드는 필드 검사에 걸리지 않는다") {
                val docs = ShopDocs()

                docs.missing("nullable") {
                    fieldsOfShopError()
                    errorResponse(409, "중복") {
                        fields<ShopError>()
                        example(ShopError("DUPLICATE", null))
                    }
                }

                docs.file("nullable", "error-responses.adoc")
                    .readText() shouldContain "\"message\" : null"
            }

            then("필드와 맞지 않으면 문자열 예시와 같이 실패한다") {
                shouldThrow<DocumentationMismatchException> {
                    ShopDocs().missing("mismatch") {
                        fieldsOfShopError()
                        errorResponse(409, "중복") {
                            fields("code" type STRING means "코드")
                            example(ShopError("DUPLICATE", "중복"))
                        }
                    }
                }.message shouldContain "message"
            }
        }

        given("errorResponse(status, description, example) 한 줄") {
            then("필드는 예시의 런타임 클래스에서 추출하고 예시는 직렬화한다") {
                val response = declare {
                    errorResponse(409, "중복", ShopError("DUPLICATE", "중복", ShopErrorDetail("code", "taken")))
                }.errorResponses
                    .single()

                response.fields.map { it.descriptor.path } shouldBe
                    responseFieldsOf<ShopError>().map { it.descriptor.path }
                response.example shouldContain "\"reason\" : \"taken\""
            }

            then("HttpStatusCode 버전, 실제 문서화와 OpenAPI 스키마") {
                val docs = ShopDocs()

                docs.missing("one-liner") {
                    fieldsOfShopError()
                    errorResponse(HttpStatus.CONFLICT, "중복", ShopError("DUPLICATE", null))
                }

                val conflict = (docs.extras("one-liner")["errorResponses"] as List<*>).single() as Map<*, *>
                val schema = conflict["schema"] as Map<*, *>

                conflict["status"] shouldBe 409
                (schema["properties"] as Map<*, *>).keys shouldBe setOf("code", "message", "detail")
                schema["required"] shouldBe listOf("code")
            }

            then("@JsonProperty 이름으로 필드와 예시가 맞는다") {
                val response = declare { errorResponse(429, "x", AnnotatedShopError("SLOW", 3)) }.errorResponses
                    .single()

                response.fields.map { it.descriptor.path } shouldBe listOf("error_code", "retryAfterSeconds")
                response.example shouldContain "\"error_code\" : \"SLOW\""
            }

            then("컬렉션·Map·제네릭·문자열 예시는 필드 없이 예시만 남긴다") {
                listOf(
                    listOf(ShopError("A", null)),
                    mapOf("code" to "A"),
                    GenericShopError("A", 1),
                    """{"code":"A"}""",
                ).forEach { value ->
                    val response = declare { errorResponse(400, "x", value) }.errorResponses
                        .single()

                    response.fields shouldBe emptyList()
                    response.example shouldContain "\"A\""
                }
            }

            then("블록을 넘기면 지금처럼 블록 버전이 불린다") {
                declare { errorResponse(400, "x") { example("""{"a":1}""") } }.errorResponses
                    .single()
                    .fields shouldBe emptyList()
            }
        }

        given("Markdown 템플릿 형식의 SSE 이벤트 표") {
            val docs = ShopDocs(markdown = true)

            docs.events("sse") {
                events("tick" eventMeans "주기 이벤트")
            }

            then("이름·설명 칸이 채워진다") {
                docs.file("sse", "sse-events.md")
                    .readText() shouldContain "`tick` | true | 주기 이벤트"
            }
        }
    })
