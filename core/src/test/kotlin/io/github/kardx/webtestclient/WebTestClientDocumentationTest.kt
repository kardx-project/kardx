package io.github.kardx.webtestclient

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.DocumentationMismatchException
import io.github.kardx.dsl.eventMeans
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.pathMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.requestFieldsOf
import io.github.kardx.extractor.responseFieldsOf
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.jsonContent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.operation.OperationRequest
import org.springframework.restdocs.operation.OperationResponse
import org.springframework.restdocs.operation.preprocess.OperationPreprocessorAdapter
import org.springframework.restdocs.operation.preprocess.Preprocessors
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.io.File
import java.nio.file.Files

class MemberResponse(val id: Long, val name: String, val tags: List<String>)

class CreateMemberRequest(val name: String, val tags: List<String>)

/** MockMvc 와 WebFlux 가 똑같이 매핑하는 컨트롤러. 두 클라이언트로 같은 요청을 보내 문서를 비교한다. */
@RestController
class MemberController {
    @GetMapping("/members/{id}")
    fun find(
        @PathVariable id: Long,
        @RequestParam(required = false) verbose: Boolean?,
    ): ResponseEntity<MemberResponse> = ResponseEntity.ok()
        .eTag("\"v1\"")
        .body(MemberResponse(id, if (verbose == true) "홍길동 (관리자)" else "홍길동", listOf("a")))

    @PostMapping("/members")
    fun create(@RequestBody request: CreateMemberRequest): ResponseEntity<MemberResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(MemberResponse(1, request.name, request.tags))

    /** 끝나는 SSE 스트림. */
    @GetMapping("/members/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(): Flux<ServerSentEvent<String>> = Flux.just(
        ServerSentEvent.builder("""{"id":1}""")
            .event("joined")
            .build(),
    )
}

/** 한 테스트 메서드의 스니펫을 [provider] 출력에 쓴다. */
private class Output {
    val directory: File = Files.createTempDirectory("webtestclient").toFile()
    val provider = ManualRestDocumentation(directory.absolutePath)

    fun <T> run(method: String, block: () -> T): T {
        provider.beforeTest(WebTestClientDocumentationTest::class.java, method)

        try {
            return block()
        } finally {
            provider.afterTest()
        }
    }

    fun snippet(method: String, name: String): String =
        File(directory, "web-test-client-documentation-test/$method/$name").readText()

    fun exists(method: String, name: String): Boolean =
        File(directory, "web-test-client-documentation-test/$method/$name").isFile
}

/** 핸들러를 기록한다. MockMvc 처럼 operationId·파라미터 타입 기본값이 핸들러에서 나와 두 클라이언트의 결과가 같다 */
private fun webTestClient(output: Output): WebTestClient = WebTestClient.bindToController(MemberController())
    .recordHandlers()
    .configureClient()
    .restDocs(output.provider)
    .build()

/** `restDocsMockMvc` 처럼 REST Docs 설정만 건다. 요청·응답 가공은 `andDocument` 의 전처리가 한다. */
private fun mockMvc(output: Output): MockMvc = MockMvcBuilders.standaloneSetup(MemberController())
    .apply<StandaloneMockMvcBuilder>(documentationConfiguration(output.provider))
    .build()

/**
 * 받은 요청·응답을 기록하고 표시를 더하는 사용자 전처리기. 라이브러리 가공 뒤에 불리는지(가린 헤더, localhost URI,
 * pretty print 된 본문을 보는지)와 바꾼 값이 스니펫에 나가는지 확인한다.
 */
private class RecordingPreprocessor : OperationPreprocessorAdapter() {
    var request: OperationRequest? = null
    var response: OperationResponse? = null

    override fun preprocess(request: OperationRequest): OperationRequest {
        this.request = request

        return Preprocessors.modifyHeaders()
            .add("X-Preprocessed", "yes")
            .preprocess(request)
    }

    override fun preprocess(response: OperationResponse): OperationResponse {
        this.response = response

        return Preprocessors.modifyHeaders()
            .remove(HttpHeaders.ETAG)
            .preprocess(response)
    }
}

private val findDocs: DocumentationBuilder.() -> Unit = {
    description = "회원 조회"
    pathParameters("id" pathMeans "회원 ID")
    queryParameters("verbose" queryMeans "자세히")
    responseHeaders("ETag" headerMeans "버전")
    responseFields(responseFieldsOf<MemberResponse>())
}

private val createDocs: DocumentationBuilder.() -> Unit = {
    description = "회원 생성"
    requestFields(requestFieldsOf<CreateMemberRequest>())
    responseFields(responseFieldsOf<MemberResponse>())
}

/** 클라이언트와 무관해야 하는 스니펫. `http-request`·`http-response` 는 헤더(Content-Length 등)가 클라이언트마다 다르다 */
private val CLIENT_INDEPENDENT = listOf(
    "api-summary.adoc",
    "path-parameters.adoc",
    "query-parameters.adoc",
    "request-fields.adoc",
    "response-fields.adoc",
    "response-headers.adoc",
)

/** OpenAPI 리소스에서 클라이언트와 무관한 부분 (예시의 헤더를 뺀 나머지) */
private fun openApiShape(json: String): String {
    val mapper = ObjectMapper()
    val tree = mapper.readTree(json)

    listOf("request", "response").forEach { (tree[it] as ObjectNode).remove("headers") }

    return mapper.writeValueAsString(tree)
}

class WebTestClientDocumentationTest :
    BehaviorSpec({
        given("같은 andDocument 블록을 MockMvc 와 WebTestClient 로 실행하면") {
            val mvc = Output()
            val web = Output()

            mvc.run("find") {
                mockMvc(mvc).perform(get("/members/{id}", 1).param("verbose", "true"))
                    .andDocument(findDocs)
            }

            web.run("find") {
                webTestClient(web).get()
                    .uri("/members/{id}?verbose={verbose}", 1, true)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .andDocument(findDocs)
            }

            mvc.run("create") {
                mockMvc(mvc).perform(post("/members").jsonContent(CreateMemberRequest("홍길동", listOf("a"))))
                    .andDocument(createDocs)
            }

            web.run("create") {
                webTestClient(web).post()
                    .uri("/members")
                    .bodyValue(CreateMemberRequest("홍길동", listOf("a")))
                    .exchange()
                    .expectBody()
                    .jsonPath("$.name")
                    .isEqualTo("홍길동")
                    .andDocument(createDocs)
            }

            then("파라미터·필드·헤더·요약 스니펫이 같다") {
                listOf("find", "create").forEach { method ->
                    CLIENT_INDEPENDENT.filter { mvc.exists(method, it) }
                        .forEach { name -> web.snippet(method, name) shouldBe mvc.snippet(method, name) }
                }

                web.snippet("find", "api-summary.adoc") shouldContain "GET /members/{id}"
                web.snippet("create", "api-summary.adoc") shouldContain "201 Created"
            }

            then("OpenAPI 리소스(resource.json)와 스키마 이름 조각이 같다") {
                listOf("find", "create").forEach { method ->
                    openApiShape(web.snippet(method, "resource.json")) shouldBe
                        openApiShape(mvc.snippet(method, "resource.json"))
                    web.snippet(method, "openapi-schemas.json") shouldBe mvc.snippet(method, "openapi-schemas.json")
                }
            }

            then("요청 본문은 pretty print, URI 는 localhost 로 맞춘다") {
                val request = web.snippet("create", "http-request.adoc")

                request shouldContain "\"name\" : \"홍길동\""
                request shouldContain "Host: localhost"
                request shouldNotContain "8080"
            }
        }

        given("쿼리 값이 한글인 uri 템플릿") {
            then("URI 를 인코딩해 문서화하고 쿼리 파라미터 표에는 원래 값이 남는다") {
                val web = Output()

                web.run("korean-query") {
                    webTestClient(web).get()
                        .uri("/members/{id}?verbose={verbose}&name={name}", 1, true, "홍길동")
                        .exchange()
                        .andDocument {
                            queryParameters("verbose" queryMeans "자세히", "name" queryMeans "이름")
                        }
                }

                web.snippet("korean-query", "http-request.adoc") shouldContain "name=%ED%99%8D"
                web.snippet("korean-query", "api-summary.adoc") shouldContain "GET /members/{id}`"
            }
        }

        given("expectBody<T>() 로 본문을 타입으로 받은 응답") {
            then("BodySpec 에서 문서화하고 받은 객체를 그대로 쓸 수 있다") {
                val web = Output()

                val member = web.run("typed-body") {
                    webTestClient(web).get()
                        .uri("/members/{id}", 1)
                        .exchange()
                        .expectBody<MemberResponse>()
                        .andDocument {
                            pathParameters("id" pathMeans "회원 ID")
                            responseFields(responseFieldsOf<MemberResponse>())
                        }
                        .returnResult()
                        .responseBody
                }

                member?.name shouldBe "홍길동"
                web.snippet("typed-body", "response-fields.adoc") shouldContain "`+tags+`"
                web.snippet("typed-body", "http-response.adoc") shouldContain "\"name\" : \"홍길동\""
            }
        }

        given("문서와 실제 응답이 다르면") {
            then("MockMvc 와 같은 진단 예외로 실패한다") {
                val web = Output()

                shouldThrow<DocumentationMismatchException> {
                    web.run("mismatch") {
                        webTestClient(web).get()
                            .uri("/members/{id}", 1)
                            .exchange()
                            .andDocument { pathParameters("memberId" pathMeans "회원 ID") }
                    }
                }.message shouldContain "\"id\" pathMeans \"...\""
            }
        }

        given("민감한 요청 헤더") {
            then("masking.maskSensitive 이면 문서에서 가린다") {
                val web = Output()

                RestDocsConfiguration.withSettings({ masking.maskSensitive() }) {
                    web.run("masked") {
                        webTestClient(web).get()
                            .uri("/members/{id}", 1)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
                            .exchange()
                            .andDocument { requestHeaders("Authorization" headerMeans "토큰") }
                    }
                }

                web.snippet("masked", "http-request.adoc") shouldNotContain "secret-token"
            }
        }

        given("http.requestPreprocessors·http.responsePreprocessors 설정") {
            listOf("mock-mvc", "web-test-client").forEach { client ->
                then("$client: 라이브러리 가공(마스킹·localhost·pretty print) 뒤에 적용되고 스니펫에 나간다") {
                    val output = Output()
                    val recorder = RecordingPreprocessor()
                    val settings: RestDocsSettings.() -> Unit = {
                        masking.maskSensitive()
                        http.requestPreprocessors = listOf(recorder)
                        http.responsePreprocessors = listOf(recorder)
                    }

                    RestDocsConfiguration.withSettings(settings) {
                        output.run(client) {
                            if (client == "mock-mvc") {
                                mockMvc(output).perform(
                                    post("/members")
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
                                        .jsonContent(CreateMemberRequest("홍길동", listOf("a"))),
                                ).andDocument()
                            } else {
                                webTestClient(output).post()
                                    .uri("http://example.com:8080/members")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer secret-token")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue("""{"name":"홍길동","tags":["a"]}""")
                                    .exchange()
                                    .andDocument()
                            }
                        }
                    }

                    val request = requireNotNull(recorder.request)

                    request.uri.host shouldBe "localhost"
                    request.headers.getFirst(HttpHeaders.AUTHORIZATION) shouldBe "Bearer ****"
                    request.contentAsString shouldContain "\n"
                    requireNotNull(recorder.response).contentAsString shouldContain "\n"
                    output.snippet(client, "http-request.adoc") shouldContain "X-Preprocessed: yes"
                }
            }

            then("기본값은 빈 목록이다") {
                RestDocsSettings().http.requestPreprocessors shouldBe emptyList()
                RestDocsSettings().http.responsePreprocessors shouldBe emptyList()
            }
        }

        given("output.wireMockStubs 설정") {
            then("WireMock 매핑을 쓴다") {
                val web = Output()

                RestDocsConfiguration.withSettings({ output.wireMockStubs = true }) {
                    web.run("stub") {
                        webTestClient(web).get()
                            .uri("/members/{id}", 1)
                            .exchange()
                            .andDocument()
                    }
                }

                File(web.directory, "wiremock/mappings/web-test-client-documentation-test/stub.json")
                    .readText() shouldContain "\"urlPath\" : \"/members/1\""
            }
        }

        given("끝나는 SSE 스트림") {
            then("events 로 이벤트를 문서화한다") {
                val web = Output()

                web.run("events") {
                    webTestClient(web).get()
                        .uri("/members/events")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .exchange()
                        .andDocument { events("joined" eventMeans "가입") }
                }

                web.snippet("events", "sse-events.adoc") shouldContain "joined"
            }
        }
    })
