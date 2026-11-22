package io.github.kardx.mockmvc

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.DocumentationMismatchException
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.type
import io.github.kardx.extractor.fields
import io.github.kardx.extractor.responseFieldsOf
import io.github.kardx.internal.documentation.toSpec
import io.github.kardx.internal.openapi.OpenApiOperationExtras
import io.github.kardx.webtestclient.andDocument
import io.github.kardx.webtestclient.restDocs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.HttpStatus
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.templates.TemplateFormats
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files

class CatalogProduct(val id: Long, val name: String)

class CatalogError(val code: String, val message: String)

@RestController
class CatalogController {
    @GetMapping("/catalog/{id}")
    fun find(@PathVariable id: Long): CatalogProduct = CatalogProduct(id, "키보드")
}

/** 한 테스트 메서드의 스니펫과 `openapi-extras.json` 을 임시 디렉터리에 남긴다. */
private class CatalogDocs(markdown: Boolean = false) {
    val output: File = Files.createTempDirectory("error-responses").toFile()
    private val documentation = ManualRestDocumentation(output.absolutePath)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(CatalogController())
        .apply<StandaloneMockMvcBuilder>(
            documentationConfiguration(documentation)
                .snippets()
                .apply {
                    if (markdown) withTemplateFormat(TemplateFormats.markdown())
                },
        )
        .build()
    private val webTestClient: WebTestClient = WebTestClient.bindToController(CatalogController())
        .configureClient()
        .restDocs(documentation)
        .build()

    fun mvc(method: String, block: DocumentationBuilder.() -> Unit) = run(method) {
        mockMvc.perform(get("/catalog/{id}", 1))
            .andDocument(block)
    }

    fun web(method: String, block: DocumentationBuilder.() -> Unit) = run(method) {
        webTestClient.get()
            .uri("/catalog/{id}", 1)
            .exchange()
            .expectStatus()
            .isOk
            .andDocument(block)
    }

    fun snippet(method: String, name: String = "error-responses.adoc"): String = file(method, name).readText()

    fun exists(method: String): Boolean = file(method, "error-responses.adoc").isFile

    fun extras(method: String): Map<*, *> =
        ObjectMapper().readValue(file(method, OpenApiOperationExtras.FILE_NAME), Map::class.java)

    private fun file(method: String, name: String) = File(output, "error-response-documentation-test/$method/$name")

    private fun run(method: String, block: () -> Unit) {
        documentation.beforeTest(ErrorResponseDocumentationTest::class.java, method)

        try {
            block()
        } finally {
            documentation.afterTest()
        }
    }
}

private val productFields: DocumentationBuilder.() -> Unit = {
    responseFields(responseFieldsOf<CatalogProduct>())
}

private val declared: DocumentationBuilder.() -> Unit = {
    productFields()
    errorResponse(HttpStatus.CONFLICT, "이미 등록된 상품 코드") {
        fields<CatalogError>()
        example("""{"code":"DUPLICATE","message":"중복"}""")
    }
    errorResponse(400, "잘못된 요청")
    errorResponse(404, "상품 없음") {
        example("""{"code":"NOT_FOUND"}""")
        contentType = "application/problem+json"
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.list(): List<Map<String, Any?>> = this as List<Map<String, Any?>>

class ErrorResponseDocumentationTest :
    BehaviorSpec({
        given("errorResponse 선언 검사") {
            fun declare(block: DocumentationBuilder.() -> Unit) = DocumentationBuilder().apply(block)
                .toSpec()

            then("400..599 밖의 상태는 거부한다") {
                shouldThrow<IllegalArgumentException> { declare { errorResponse(399, "x") } }
                    .message shouldContain "between 400 and 599, but was 399"
                shouldThrow<IllegalArgumentException> { declare { errorResponse(HttpStatus.OK, "x") } }
                    .message shouldContain "but was 200"
                shouldThrow<IllegalArgumentException> { declare { errorResponse(600, "x") } }
            }

            then("빈 설명, 같은 상태 두 번, 빈 Content-Type·예시는 거부한다") {
                shouldThrow<IllegalArgumentException> { declare { errorResponse(400, " ") } }
                    .message shouldContain "non-blank description"
                shouldThrow<IllegalArgumentException> {
                    declare {
                        errorResponse(409, "a")
                        errorResponse(HttpStatus.CONFLICT, "b")
                    }
                }.message shouldContain "errorResponse(409) is declared more than once"
                shouldThrow<IllegalArgumentException> { declare { errorResponse(400, "a") { contentType = "" } } }
                    .message shouldContain "blank contentType"
                shouldThrow<IllegalArgumentException> { declare { errorResponse(400, "a") { example("") } } }
                    .message shouldContain "blank example"
            }

            then("JSON 계열 Content-Type 의 예시는 JSON 이어야 한다") {
                shouldThrow<IllegalArgumentException> {
                    declare { errorResponse(400, "a") { example("not json") } }
                }.message shouldContain "errorResponse(400) is not valid JSON for content type application/json"
            }

            then("JSON 이 아닌 Content-Type 의 예시는 그대로 둔다") {
                val spec = declare {
                    errorResponse(503, "점검 중") {
                        example("maintenance")
                        contentType = "text/plain"
                    }
                }

                spec.errorResponses.single().example shouldBe "maintenance"
            }

            then("상태순으로 정렬하고 JSON 예시를 들여쓰기한다") {
                val spec = declare(declared)

                spec.errorResponses.map { it.status } shouldBe listOf(400, 404, 409)
                spec.errorResponses.last().example shouldBe "{\n  \"code\" : \"DUPLICATE\",\n  \"message\" : \"중복\"\n}"
            }
        }

        given("MockMvc 로 오류 응답을 선언하면") {
            val docs = CatalogDocs()

            docs.mvc("declared", declared)

            then("openapi-extras.json 의 errorResponses 는 상태순이고, 선언하지 않은 schema·example 은 없다") {
                val responses = docs.extras("declared")["errorResponses"].list()

                responses.map { it["status"] } shouldBe listOf(400, 404, 409)
                responses[0] shouldBe
                    mapOf("status" to 400, "description" to "잘못된 요청", "contentType" to "application/json")
                responses[1]["contentType"] shouldBe "application/problem+json"
                responses[1]["example"] shouldBe "{\n  \"code\" : \"NOT_FOUND\"\n}"
                responses[1].containsKey("schema") shouldBe false
            }

            then("필드를 적은 응답은 OpenAPI 3.0 스키마가 된다") {
                val conflict = docs.extras("declared")["errorResponses"].list()[2]
                val schema = conflict["schema"] as Map<*, *>

                schema["type"] shouldBe "object"
                (schema["properties"] as Map<*, *>).keys shouldBe setOf("code", "message")
                schema["required"] shouldBe listOf("code", "message")
                conflict["example"] shouldBe "{\n  \"code\" : \"DUPLICATE\",\n  \"message\" : \"중복\"\n}"
            }

            then("error-responses 스니펫은 영어 머리글의 상태·설명 표와 응답별 필드 표·예시다") {
                val snippet = docs.snippet("declared")

                snippet shouldContain "|Status|Description"
                snippet shouldContain "|`400 Bad Request`\n|잘못된 요청"
                snippet shouldContain ".409 Conflict response fields\n|===\n|Path|Type|Description"
                snippet shouldContain "|`+code+`\n|`+String+`\n|"
                snippet shouldContain ".404 Not Found response example\n[source,json,options=\"nowrap\"]\n----\n{"
                snippet shouldNotContain ".400 Bad Request response"
                snippet shouldNotContain ".404 Not Found response fields"
            }
        }

        given("언어가 한국어이면") {
            val docs = CatalogDocs()

            RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                docs.mvc("korean", declared)
            }

            then("머리글과 제목이 한국어다") {
                val snippet = docs.snippet("korean")

                snippet shouldContain "|상태|설명"
                snippet shouldContain ".409 Conflict 응답 필드\n|===\n|경로|타입|설명"
                snippet shouldContain ".409 Conflict 응답 예시"
            }
        }

        given("선언이 없으면") {
            val docs = CatalogDocs()

            docs.mvc("plain", productFields)

            then("error-responses 스니펫과 errorResponses 키가 없다") {
                docs.exists("plain") shouldBe false
                docs.extras("plain").containsKey("errorResponses") shouldBe false
            }
        }

        given("JSON 이 아닌 예시") {
            val docs = CatalogDocs()

            docs.mvc("text") {
                productFields()
                errorResponse(503, "점검 중") {
                    example("maintenance")
                    contentType = "text/plain"
                }
            }

            then("언어 표시 없는 source 블록이 된다") {
                docs.snippet("text") shouldContain "[source,options=\"nowrap\"]\n----\nmaintenance\n----"
            }
        }

        given("예시가 선언한 필드와 맞지 않으면") {
            val docs = CatalogDocs()

            then("문서화하지 않은 필드는 DocumentationMismatchException") {
                val error = shouldThrow<DocumentationMismatchException> {
                    docs.mvc("undocumented") {
                        productFields()
                        errorResponse(409, "중복") {
                            fields("code" type STRING means "오류 코드")
                            example("""{"code":"DUPLICATE","retryAfter":3}""")
                        }
                    }
                }

                error.message shouldContain "Documented errorResponse(409) fields do not match the actual example."
                error.message shouldContain "retryAfter (Number)"
            }

            then("빠진 필수 필드도 실패한다") {
                val error = shouldThrow<DocumentationMismatchException> {
                    docs.mvc("missing") {
                        productFields()
                        errorResponse(409, "중복") {
                            fields("code" type STRING means "오류 코드", "retryAfter" type NUMBER means "재시도")
                            example("""{"code":"DUPLICATE"}""")
                        }
                    }
                }

                error.message shouldContain "Documented but not in the example:"
                error.message shouldContain "- retryAfter"
            }
        }

        given("Markdown 템플릿 형식이면") {
            val docs = CatalogDocs(markdown = true)

            docs.mvc("markdown", declared)

            then("같은 내용을 Markdown 표와 코드 블록으로 쓴다") {
                val snippet = docs.snippet("markdown", "error-responses.md")

                snippet shouldContain "Status | Description\n--- | ---\n`400 Bad Request` | 잘못된 요청"
                snippet shouldContain "**409 Conflict response fields**"
                snippet shouldContain "```json\n{"
            }
        }

        given("WebTestClient 로 오류 응답을 선언하면") {
            val docs = CatalogDocs()

            docs.web("web", declared)

            then("MockMvc 와 같은 스니펫과 errorResponses 를 남긴다") {
                docs.snippet("web") shouldContain ".409 Conflict response fields"
                docs.extras("web")["errorResponses"].list()
                    .map { it["status"] } shouldBe listOf(400, 404, 409)
            }
        }
    })
