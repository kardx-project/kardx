package io.github.kardx.internal.sourcedocs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.FromParamTag
import io.github.kardx.dsl.formMeans
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.partMeans
import io.github.kardx.dsl.pathMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.internal.openapi.DocumentedExtras
import io.github.kardx.webtestclient.andDocument
import io.github.kardx.webtestclient.recordHandlers
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files

@RestController
@Suppress("UnusedParameter")
class ParamDocsController {
    @GetMapping("/param-docs/memos/{memoId}")
    fun getMemo(
        @PathVariable("memoId") id: Long,
        @RequestParam(required = false) includeDeleted: Boolean?,
        @RequestHeader("X-Trace-Id") traceId: String,
        keyword: String?,
        @RequestParam(required = false) blank: String?,
    ): Map<String, Any> = mapOf("id" to id)

    @PostMapping("/param-docs/upload")
    fun upload(@RequestPart("file") file: MultipartFile): Map<String, Any> = mapOf("size" to file.size)

    @PostMapping("/param-docs/rename", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun rename(@RequestParam nickname: String): Map<String, Any> = mapOf("nickname" to nickname)

    @GetMapping("/param-docs/plain/{id}")
    fun plain(@PathVariable id: Long): Map<String, Any> = mapOf("id" to id)
}

private const val SUMMARY = "api-summary.adoc"
private const val PATH_PARAMETERS = "path-parameters.adoc"
private const val QUERY_PARAMETERS = "query-parameters.adoc"
private const val SOURCE_DESCRIPTION = "메모를 조회한다. 삭제된 메모는 404."

private fun memo(): RequestBuilder = get("/param-docs/memos/{memoId}", 7L)
    .param("includeDeleted", "true")
    .param("keyword", "a")
    .param("blank", "b")
    .header("X-Trace-Id", "t-1")

/** MockMvc 로 [request] 를 문서화하고 [name] 스니펫 파일 내용을 읽는다. */
private fun DocumentedExtras.snippet(
    method: String,
    request: RequestBuilder,
    name: String,
    block: DocumentationBuilder.() -> Unit,
): String {
    run(method, request, block)

    return file(method, name).readText()
}

/** resource.json 의 [kind] 항목 중 이름이 [name] 인 것 */
private fun DocumentedExtras.resourceItem(method: String, kind: String, name: String): JsonNode =
    ObjectMapper().readTree(file(method, "resource.json"))["request"][kind]
        .first { it["name"].asText() == name }

/** WebTestClient(핸들러 기록)로 메모 조회를 문서화하고 [name] 스니펫 파일 내용을 읽는다. */
private fun webTestClientSnippet(name: String, block: DocumentationBuilder.() -> Unit): String {
    val directory = Files.createTempDirectory("param-docs-webtestclient").toFile()
    val provider = ManualRestDocumentation(directory.absolutePath)
    val client = WebTestClient.bindToController(ParamDocsController())
        .recordHandlers()
        .configureClient()
        .filter(WebTestClientRestDocumentation.documentationConfiguration(provider))
        .build()

    provider.beforeTest(SourceDocsParametersTest::class.java, "web")

    try {
        client.get()
            .uri("/param-docs/memos/{memoId}", 7L)
            .header("X-Trace-Id", "t-1")
            .exchange()
            .expectBody()
            .andDocument(block)
    } finally {
        provider.afterTest()
    }

    return File(directory, "source-docs-parameters-test/web/$name").readText()
}

class SourceDocsParametersTest :
    BehaviorSpec({
        val previous = System.getProperty(SourceDocs.PROPERTY)

        beforeSpec { useSourceDocsIndex(sourceDocsFixture("source-docs.json")) }

        afterSpec { useSourceDocsIndex(previous) }

        given("이름만 적은 경로·쿼리 파라미터와 헤더 (MockMvc)") {
            val docs = DocumentedExtras(ParamDocsController())

            docs.run("named", memo()) {
                pathParameters("memoId")
                queryParameters("includeDeleted", "keyword")
                queryParameters("blank" queryMeans "직접 쓴 설명")
                requestHeaders("x-trace-id")
            }

            then("@PathVariable 이름으로 소스 인자를 찾아 그 @param 설명을 쓴다") {
                docs.file("named", PATH_PARAMETERS).readText() shouldContain "메모 ID"
                docs.resourceItem("named", "pathParameters", "memoId")["description"].asText() shouldBe "메모 ID"
            }

            then("@RequestParam 과 어노테이션 없는 인자는 인자 이름으로 찾는다") {
                docs.resourceItem("named", "queryParameters", "includeDeleted")["description"].asText() shouldBe
                    "삭제된 메모도 포함할지"
                docs.resourceItem("named", "queryParameters", "keyword")["description"].asText() shouldBe "검색어"
            }

            then("@RequestHeader 는 대소문자를 가리지 않고 찾는다") {
                docs.resourceItem("named", "headers", "x-trace-id")["description"].asText() shouldBe "추적 ID"
            }

            then("테스트에 적은 설명은 그대로다 (색인의 공백 설명은 쓰지 않는다)") {
                docs.resourceItem("named", "queryParameters", "blank")["description"].asText() shouldBe "직접 쓴 설명"
            }
        }

        given("FromParamTag 에 수식어를 붙인 항목") {
            val docs = DocumentedExtras(ParamDocsController())

            docs.run("modified", memo()) {
                pathParameters("memoId" pathMeans FromParamTag isOptional true example "7")
                queryParameters(
                    "includeDeleted" queryMeans FromParamTag isOptional true example "true",
                    "keyword" queryMeans "직접 쓴 검색어" isOptional true,
                    "blank" queryMeans "직접 쓴 설명",
                )
                requestHeaders("X-Trace-Id" headerMeans FromParamTag isOptional true example "trace-1")
            }

            then("쿼리 파라미터는 @param 설명에 optional 과 example 이 붙는다") {
                val item = docs.resourceItem("modified", "queryParameters", "includeDeleted")

                item["description"].asText() shouldBe "삭제된 메모도 포함할지"
                item["optional"].asBoolean() shouldBe true
                docs.file("modified", QUERY_PARAMETERS).readText() shouldContain "Example: `+true+`"
            }

            then("헤더도 @param 설명에 optional 과 example 이 붙는다") {
                val item = docs.resourceItem("modified", "headers", "X-Trace-Id")

                item["description"].asText() shouldBe "추적 ID"
                item["optional"].asBoolean() shouldBe true
                docs.file("modified", "request-headers.adoc").readText() shouldContain "Example: `+trace-1+`"
            }

            then("경로 변수도 @param 설명에 optional 과 example 이 붙는다") {
                val table = docs.file("modified", PATH_PARAMETERS).readText()

                docs.resourceItem("modified", "pathParameters", "memoId")["description"].asText() shouldBe "메모 ID"
                table shouldContain "Example: `+7+`"
                table shouldContain "false"
            }

            then("같은 호출에 섞은 설명은 그대로 쓴다") {
                val item = docs.resourceItem("modified", "queryParameters", "keyword")

                item["description"].asText() shouldBe "직접 쓴 검색어"
                item["optional"].asBoolean() shouldBe true
                docs.resourceItem("modified", "queryParameters", "blank")["description"].asText() shouldBe "직접 쓴 설명"
            }

            then("상수로 빼 둔 항목에 붙인 수식어는 원본을 바꾸지 않는다") {
                val shared = "page" queryMeans FromParamTag
                val optional = shared isOptional true

                shared.descriptor.isOptional shouldBe false
                optional.descriptor.isOptional shouldBe true
                optional.descriptor.description shouldBe null
            }
        }

        given("설명을 적은 파라미터") {
            val docs = DocumentedExtras(ParamDocsController())

            then("테스트의 설명이 @param 보다 앞선다") {
                val table = docs.snippet("explicit", memo(), PATH_PARAMETERS) {
                    pathParameters("memoId" pathMeans "직접 쓴 메모 ID")
                    requestHeaders("X-Trace-Id" headerMeans "직접 쓴 추적 ID")
                    relaxed = true
                }

                table shouldContain "직접 쓴 메모 ID"
                table shouldNotContain "| 메모 ID"
                docs.resourceItem("explicit", "headers", "X-Trace-Id")["description"].asText() shouldBe "직접 쓴 추적 ID"
            }

            then("@param 이 없는 핸들러도 지금처럼 문서화한다") {
                val table = docs.snippet("plain-explicit", get("/param-docs/plain/{id}", 1L), PATH_PARAMETERS) {
                    pathParameters("id" pathMeans "ID")
                }

                table shouldContain "ID"
            }
        }

        given("@param 으로도 설명을 찾지 못한 이름만 적은 항목") {
            val docs = DocumentedExtras(ParamDocsController())

            then("어느 항목인지와 채우는 방법을 알려 주며 실패한다") {
                val error = shouldThrow<IllegalArgumentException> {
                    docs.run("plain-named", get("/param-docs/plain/{id}", 1L)) { pathParameters("id") }
                }

                error.message shouldContain "path parameter 'id'"
                error.message shouldContain "@param"
            }

            then("FromParamTag 에 수식어를 붙여도 어느 항목인지 알려 준다") {
                val error = shouldThrow<IllegalArgumentException> {
                    docs.run("plain-modified", get("/param-docs/plain/{id}", 1L)) {
                        pathParameters("id" pathMeans FromParamTag example "1")
                    }
                }

                error.message shouldContain "path parameter 'id'"
                error.message shouldContain "FromParamTag"
            }

            then("@param 이 설명하지 않는 응답 헤더에는 쓸 수 없다") {
                val error = shouldThrow<IllegalArgumentException> {
                    DocumentationBuilder().responseHeaders("ETag" headerMeans FromParamTag)
                }

                error.message shouldContain "response headers 'ETag'"
            }

            then("설정으로 source docs 를 끄면 찾지 않는다") {
                shouldThrow<IllegalArgumentException> {
                    RestDocsConfiguration.withSettings({ descriptions.sourceDocs = false }) {
                        docs.run("disabled", memo()) {
                            pathParameters("memoId")
                            relaxed = true
                        }
                    }
                }
            }
        }

        given("이름만 적은 파트와 폼 파라미터") {
            val docs = DocumentedExtras(ParamDocsController())

            then("@RequestPart 이름으로 찾는다") {
                val upload = multipart("/param-docs/upload")
                    .file(MockMultipartFile("file", "memo.txt", MediaType.TEXT_PLAIN_VALUE, "hi".toByteArray()))

                docs.snippet("part", upload, "request-parts.adoc") { requestParts("file") } shouldContain "업로드 파일"
                docs.snippet("part-optional", upload, "request-parts.adoc") {
                    requestParts("file" partMeans FromParamTag isOptional true)
                } shouldContain "업로드 파일"
            }

            then("폼 파라미터는 인자 수까지 맞춘 키를 먼저 찾는다") {
                val rename = post("/param-docs/rename").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("nickname", "evan")

                docs.snippet("form", rename, "form-parameters.adoc") { formParameters("nickname") } shouldContain
                    "새 닉네임"
                docs.snippet("form-example", rename, "form-parameters.adoc") {
                    formParameters("nickname" formMeans FromParamTag example "evan")
                } shouldContain "Example: `+evan+`"
            }
        }

        given("블록에 description 이 없는 api-summary") {
            val docs = DocumentedExtras(ParamDocsController())

            then("핸들러 KDoc 의 전체 설명을 쓴다") {
                docs.snippet("summary-source", memo(), SUMMARY) { relaxed = true } shouldContain SOURCE_DESCRIPTION
            }

            then("OpenAPI 조각의 description 은 바꾸지 않는다 (플러그인이 sourceDescription 으로 정한다)") {
                docs.run("summary-openapi", memo()) { relaxed = true }

                val resource = ObjectMapper().readTree(docs.file("summary-openapi", "resource.json"))

                resource["description"]?.asText() shouldNotBe SOURCE_DESCRIPTION
            }

            then("블록의 description 이 앞선다") {
                val summary = docs.snippet("summary-explicit", memo(), SUMMARY) {
                    description = "직접 쓴 설명"
                    relaxed = true
                }

                summary shouldContain "직접 쓴 설명"
                summary shouldNotContain SOURCE_DESCRIPTION
            }

            then("소스 설명이 없으면(params 만 있는 메서드) 설명 줄이 없다") {
                val upload = multipart("/param-docs/upload")
                    .file(MockMultipartFile("file", "memo.txt", MediaType.TEXT_PLAIN_VALUE, "hi".toByteArray()))
                val summary = docs.snippet("summary-none", upload, SUMMARY) { relaxed = true }

                // [.api-summary] 역할 줄과 메서드·경로 줄뿐이다
                summary.trim()
                    .lines()
                    .size shouldBe 2
            }
        }

        given("핸들러를 기록하는 WebTestClient") {
            then("같은 규칙으로 @param 설명을 쓴다") {
                webTestClientSnippet(PATH_PARAMETERS) {
                    pathParameters("memoId")
                    relaxed = true
                } shouldContain "메모 ID"
            }
        }
    })
