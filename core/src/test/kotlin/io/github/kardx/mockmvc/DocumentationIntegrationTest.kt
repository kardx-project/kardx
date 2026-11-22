package io.github.kardx.mockmvc

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.ValueMasker
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.eventMeans
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.partMeans
import io.github.kardx.dsl.type
import io.github.kardx.internal.documentation.StreamSnapshot
import io.github.kardx.sse.performStream
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockPart
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.templates.TemplateFormats
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.time.Duration

@RestController
class SampleController {
    @PostMapping("/upload")
    fun upload(@RequestPart("meta") meta: MultipartFile): String = "ok ${meta.size}"

    @PostMapping("/echo")
    fun echo(@RequestBody body: Map<String, Any?>): Map<String, Any?> = body

    /** 끝나지 않는 스트림: 연결 직후 connected, 이후 20ms 마다 tick 을 보낸다. */
    @GetMapping("/live")
    fun live(): SseEmitter {
        val emitter = SseEmitter(0L)

        emitter.send(
            SseEmitter.event()
                .name("connected")
                .data("{\"ok\":true}"),
        )

        Thread {
            repeat(200) { i ->
                Thread.sleep(20)
                // 테스트가 끝나 연결이 닫힌 뒤의 send 실패는 의도한 종료다
                runCatching {
                    emitter.send(
                        SseEmitter.event()
                            .name("tick")
                            .data("{\"n\":$i}"),
                    )
                }
            }
        }.apply { isDaemon = true }
            .start()

        return emitter
    }

    @PostMapping("/login")
    fun login(@RequestParam username: String): String = "hello $username"

    @GetMapping("/ping/{name}")
    fun ping(@PathVariable name: String): String = "pong $name"

    @GetMapping("/files/report.pdf")
    fun pdf(): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
        .body(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0xE2.toByte(), 0xE3.toByte()))

    @GetMapping("/files/report.csv")
    fun csv(): ResponseEntity<String> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.csv\"")
        .body("id,name\n1,keyboard\n")
}

private class Docs(markdown: Boolean = false) {
    val output: File = Files.createTempDirectory("restdocs").toFile()
    private val documentation = ManualRestDocumentation(output.absolutePath)
    val mockMvc: MockMvc =
        MockMvcBuilders.standaloneSetup(SampleController())
            .apply<StandaloneMockMvcBuilder>(
                documentationConfiguration(documentation)
                    .snippets()
                    .apply {
                        if (markdown) withTemplateFormat(TemplateFormats.markdown())
                    },
            )
            .build()

    fun <T> run(method: String, block: Docs.() -> T): T {
        documentation.beforeTest(DocumentationIntegrationTest::class.java, method)

        try {
            return block()
        } finally {
            documentation.afterTest()
        }
    }

    fun snippet(method: String, name: String): String =
        File(output, "documentation-integration-test/$method/$name").readText()
}

private val relaxedResponseFieldsSnippetFields = listOf("a" type NUMBER means "A")

private val metaPart = MockMultipartFile("meta", "", "application/json", """{"name":"a","extra":1}""".toByteArray())

class DocumentationIntegrationTest :
    BehaviorSpec({
        given("멀티파트 파트 필드") {
            then("relaxed 이면 문서화하지 않은 파트 필드를 허용한다") {
                val docs = Docs()

                docs.run("relaxed-part") {
                    mockMvc.perform(multipart("/upload").file(metaPart))
                        .andDocument {
                            requestParts("meta" partMeans "메타데이터")
                            requestPartFields("meta", "name" type STRING means "이름")
                            relaxed = true
                        }
                }

                docs.snippet("relaxed-part", "request-part-meta-fields.adoc") shouldContain "`+name+`"
            }

            then("relaxed 가 아니면 문서화하지 않은 파트 필드로 실패한다") {
                val docs = Docs()

                shouldThrowAny {
                    docs.run("strict-part") {
                        mockMvc.perform(multipart("/upload").file(metaPart))
                            .andDocument {
                                requestParts("meta" partMeans "메타데이터")
                                requestPartFields("meta", "name" type STRING means "이름")
                            }
                    }
                }.message shouldContain "extra"
            }
        }

        given("파일 다운로드") {
            then("바이너리 응답 본문은 스니펫에 크기만 남기고 헤더는 그대로 둔다") {
                val docs = Docs()

                docs.run("download-pdf") {
                    mockMvc.perform(get("/files/report.pdf"))
                        .andDocument {
                            responseHeaders(
                                "Content-Type" headerMeans "파일 형식",
                                "Content-Disposition" headerMeans "첨부 파일 이름",
                            )
                        }
                }

                val response = docs.snippet("download-pdf", "http-response.adoc")

                response shouldContain "Content-Type: application/pdf"
                response shouldContain "Content-Disposition: attachment; filename=\"report.pdf\""
                response shouldContain "Content-Length: 6"
                response shouldContain "<binary: 6 bytes>"
                response shouldNotContain "PDF"
                docs.snippet("download-pdf", "response-body.adoc") shouldContain "<binary: 6 bytes>"
            }

            then("text/csv 응답 본문은 글자 그대로 남긴다") {
                val docs = Docs()

                docs.run("download-csv") {
                    mockMvc.perform(get("/files/report.csv"))
                        .andDocument()
                }

                docs.snippet("download-csv", "response-body.adoc") shouldContain "id,name\n1,keyboard"
            }

            then("multipart 의 바이너리 파트는 크기만 남긴다") {
                val docs = Docs()
                val image = MockMultipartFile("meta", "a.png", "image/png", byteArrayOf(-119, 80, 78, 71, 0, 0))

                docs.run("binary-part") {
                    mockMvc.perform(multipart("/upload").file(image))
                        .andDocument { requestParts("meta" partMeans "이미지") }
                }

                val request = docs.snippet("binary-part", "http-request.adoc")

                request shouldContain "<binary: 6 bytes>"
                request shouldContain "filename=a.png"
            }
        }

        given("andDocument 빌더") {
            then("같은 항목을 여러 번 부르면 이어 붙이고, snippets 로 REST Docs 스니펫을 더한다") {
                val docs = Docs()

                docs.run("builder") {
                    mockMvc.perform(post("/echo").jsonContent(mapOf("a" to 1, "b" to "x")))
                        .andDocument {
                            requestFields("a" type NUMBER means "A")
                            requestFields("b" type STRING means "B")
                            responseFields(relaxedResponseFieldsSnippetFields)
                            relaxed = true
                            snippets(PayloadDocumentation.responseBody())
                        }
                }

                val request = docs.snippet("builder", "request-fields.adoc")

                request shouldContain "`+a+`"
                request shouldContain "`+b+`"
                docs.snippet("builder", "response-body.adoc") shouldContain "\"b\" : \"x\""
            }
        }

        given("jsonContent") {
            then("객체를 JSON 으로 직렬화하고 Content-Type 을 정한다") {
                val docs = Docs()

                docs.run("json-content") {
                    mockMvc.perform(post("/echo").jsonContent(mapOf("name" to "홍길동")))
                        .andDocument()
                }

                val request = docs.snippet("json-content", "http-request.adoc")

                request shouldContain "Content-Type: application/json"
                request shouldContain "\"name\" : \"홍길동\""
                docs.snippet("json-content", "http-response.adoc") shouldContain "\"name\" : \"홍길동\""
            }

            then("String 은 다시 직렬화하지 않고 그대로 보낸다") {
                val docs = Docs()

                docs.run("json-content-string") {
                    mockMvc.perform(post("/echo").jsonContent("""{"name":"홍길동"}"""))
                        .andDocument()
                }

                val request = docs.snippet("json-content-string", "http-request.adoc")

                request shouldContain "Content-Type: application/json"
                request shouldContain "\"name\" : \"홍길동\""
                request shouldNotContain """\"name\""""
                docs.snippet("json-content-string", "http-response.adoc") shouldContain "\"name\" : \"홍길동\""
            }

            then("StringBuilder 같은 CharSequence 도 문자열 그대로 보낸다 (객체로 직렬화하지 않는다)") {
                val docs = Docs()

                docs.run("json-content-builder") {
                    mockMvc.perform(post("/echo").jsonContent(StringBuilder("""{"name":"홍길동"}""")))
                        .andDocument()
                }

                docs.snippet("json-content-builder", "http-request.adoc") shouldContain "\"name\" : \"홍길동\""
                docs.snippet("json-content-builder", "http-response.adoc") shouldContain "\"name\" : \"홍길동\""
            }

            then("ByteArray 는 UTF-8 문자열로 보낸다") {
                val docs = Docs()

                docs.run("json-content-bytes") {
                    mockMvc.perform(post("/echo").jsonContent("""{"name":"홍길동"}""".toByteArray(Charsets.UTF_8)))
                        .andDocument()
                }

                docs.snippet("json-content-bytes", "http-response.adoc") shouldContain "\"name\" : \"홍길동\""
            }
        }

        given("andPrint") {
            then("요청과 응답을 콘솔에 출력하고 이어서 쓸 수 있다") {
                val docs = Docs()
                val out = ByteArrayOutputStream()
                val original = System.out

                System.setOut(PrintStream(out, true, Charsets.UTF_8))

                try {
                    docs.run("print") {
                        mockMvc.perform(get("/ping/{name}", "a"))
                            .andPrint()
                            .andDocument()
                    }
                } finally {
                    System.setOut(original)
                }

                val printed = out.toString(Charsets.UTF_8)

                printed shouldContain "MockHttpServletRequest"
                printed shouldContain "pong a"
                docs.snippet("print", "http-response.adoc") shouldContain "pong a"
            }
        }

        given("검증·조회 보조 함수") {
            then("andStatus, andExpectJsonPath, andReturnContent, bearerToken") {
                val docs = Docs()

                val body = docs.run("helpers") {
                    mockMvc.perform(
                        post("/echo")
                            .bearerToken("abc")
                            .jsonContent(mapOf("name" to "홍길동", "code" to 7)),
                    ).andStatus(200)
                        .andStatus(HttpStatus.OK)
                        .andExpectJsonPath("$.name", "홍길동")
                        .andExpectJsonPath("$.code", 7)
                        .andLog()
                        .andDocument()
                        .andReturnContent()
                }

                body shouldContain "홍길동"
                docs.snippet("helpers", "http-request.adoc") shouldContain "Authorization: Bearer abc"
            }

            then("상태가 다르면 실패한다") {
                val docs = Docs()

                shouldThrowAny {
                    docs.run("wrong-status") {
                        mockMvc.perform(get("/ping/{name}", "a"))
                            .andStatus(404)
                    }
                }
            }
        }

        given("끝나지 않는 스트림 (performStream)") {
            then("처음 N 개 이벤트까지만 고정해서 문서화한다") {
                val docs = Docs()

                docs.run("live") {
                    mockMvc.performStream(get("/live"), eventCount = 3)
                        .andStatus(200)
                        .andDocument {
                            events(
                                "connected" eventMeans "연결",
                                "tick" eventMeans "주기 이벤트" withFields listOf("n" type NUMBER means "순번"),
                            )
                        }
                }

                Thread.sleep(100) // 스트림은 계속 쓰이지만 문서는 그대로여야 한다

                val response = docs.snippet("live", "http-response.adoc")

                Regex("event:").findAll(response)
                    .count() shouldBe 3
                docs.snippet("live", "sse-events.adoc") shouldContain "`+tick+`"
            }

            then("시간 안에 이벤트가 모자라면 실패한다") {
                val docs = Docs()

                val message = shouldThrowAny {
                    docs.run("live-timeout") {
                        mockMvc.performStream(get("/live"), eventCount = 1000, timeout = Duration.ofMillis(200))
                    }
                }.message

                message shouldContain "Expected 1000 stream events"
                message shouldContain "Increase `timeout`, or trigger the events in `whileStreaming`."
            }

            then("빈 줄로 끝난 이벤트만 세고 주석 블록은 세지 않는다") {
                StreamSnapshot.completeEvents("event:a\ndata:1\n\n:ping\n\nevent:b\ndata:2", 2) shouldBe null
                StreamSnapshot.completeEvents(
                    "event:a\ndata:1\n\n:ping\n\nevent:b\ndata:2\n\nevent:c\ndata:3\n\n",
                    2,
                ) shouldBe
                    "event:a\ndata:1\n\n:ping\n\nevent:b\ndata:2\n\n"
            }
        }

        given("민감 헤더") {
            fun documentWithToken(docs: Docs, name: String) = docs.run(name) {
                mockMvc.perform(get("/ping/{name}", "a").header("Authorization", "Bearer secret-token"))
                    .andDocument { requestHeaders("Authorization" headerMeans "토큰") }
            }

            then("기본값은 가리지 않는다") {
                val docs = Docs()

                documentWithToken(docs, "no-masking")

                docs.snippet("no-masking", "http-request.adoc") shouldContain "Authorization: Bearer secret-token"
            }

            then("masking.maskSensitive 로 켜면 값을 가리고 헤더 이름은 남긴다") {
                val docs = Docs()

                RestDocsConfiguration.withSettings({ masking.maskSensitive() }) { documentWithToken(docs, "masking") }

                val request = docs.snippet("masking", "http-request.adoc")

                request shouldContain "Authorization: Bearer ****"
                request shouldNotContain "secret-token"
                docs.snippet("masking", "request-headers.adoc") shouldContain "`+Authorization+`"
            }

            then("masking.masker 로 가리는 방식을 바꾼다") {
                val docs = Docs()

                RestDocsConfiguration.withSettings({
                    masking.headers = setOf("authorization")
                    masking.masker = ValueMasker { it.value.take(7) + "[REDACTED]" }
                }) { documentWithToken(docs, "custom-masking") }

                docs.snippet("custom-masking", "http-request.adoc") shouldContain "Authorization: Bearer [REDACTED]"
            }

            fun documentWithQueryToken(docs: Docs, name: String) = docs.run(name) {
                mockMvc.perform(get("/ping/{name}?access_token=secret-token&sig=abc&page=2", "a"))
                    .andDocument { }
            }

            then("masking.maskSensitive 는 access_token 같은 쿼리 파라미터 값도 가린다") {
                val docs = Docs()

                RestDocsConfiguration.withSettings({ masking.maskSensitive() }) {
                    documentWithQueryToken(docs, "query-masking")
                }

                listOf("http-request.adoc", "curl-request.adoc", "httpie-request.adoc").forEach { snippet ->
                    val text = docs.snippet("query-masking", snippet)

                    text shouldContain "access_token=****"
                    text shouldContain "page=2"
                    text shouldNotContain "secret-token"
                }
            }

            then("masking.queryParameters 로 가릴 쿼리 파라미터를 직접 정한다 (대소문자 무시)") {
                val docs = Docs()

                RestDocsConfiguration.withSettings({ masking.queryParameters = setOf("SIG") }) {
                    documentWithQueryToken(docs, "custom-query-masking")
                }

                val request = docs.snippet("custom-query-masking", "http-request.adoc")

                request shouldContain "sig=****"
                request shouldContain "access_token=secret-token"
            }

            fun documentLogin(docs: Docs, name: String) = docs.run(name) {
                mockMvc.perform(
                    post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "hong")
                        .param("password", "p@ssw0rd")
                        .param("client_secret", "cs-123"),
                ).andDocument { }
            }

            then("masking.maskSensitive 는 폼 본문의 password·client_secret 값도 가린다") {
                val docs = Docs()

                RestDocsConfiguration.withSettings({
                    masking.maskSensitive()
                    output.wireMockStubs = true
                }) { documentLogin(docs, "form-masking") }

                listOf("http-request.adoc", "curl-request.adoc", "httpie-request.adoc", "request-body.adoc")
                    .forEach { snippet ->
                        val text = docs.snippet("form-masking", snippet)

                        text shouldContain "username"
                        text shouldContain "hong"
                        text shouldNotContain "ssw0rd"
                        text shouldNotContain "cs-123"
                    }

                val request = docs.snippet("form-masking", "http-request.adoc")

                request shouldContain "username=hong&password=****&client_secret=****"
                request shouldContain "Content-Length: ${"username=hong&password=****&client_secret=****".length}"
                File(docs.output, "wiremock/mappings/documentation-integration-test/form-masking.json")
                    .readText() shouldNotContain "ssw0rd"
            }

            then("multipart 의 글자 파트(폼 파라미터)도 이름이 같으면 가린다") {
                val docs = Docs()

                RestDocsConfiguration.withSettings({ masking.maskSensitive() }) {
                    docs.run("multipart-masking") {
                        mockMvc.perform(
                            multipart("/upload")
                                .file(metaPart)
                                .part(MockPart("password", "p@ssw0rd".toByteArray())),
                        ).andDocument { }
                    }
                }

                val request = docs.snippet("multipart-masking", "http-request.adoc")

                request shouldNotContain "ssw0rd"
                request shouldContain "name=password"
            }

            then("기본값은 폼 본문을 가리지 않는다") {
                val docs = Docs()

                documentLogin(docs, "no-form-masking")

                docs.snippet("no-form-masking", "http-request.adoc") shouldContain "password=p%40ssw0rd"
            }

            then("기본값은 쿼리 파라미터를 가리지 않는다") {
                val docs = Docs()

                documentWithQueryToken(docs, "no-query-masking")

                docs.snippet("no-query-masking", "http-request.adoc") shouldContain "access_token=secret-token"
            }
        }

        given("헤더 수식어") {
            then("withDefaultValue·example 은 헤더 표의 설명 아래 줄로 나간다") {
                val docs = Docs()

                docs.run("header-modifiers") {
                    mockMvc.perform(get("/ping/{name}", "a").header("Accept-Language", "en"))
                        .andDocument {
                            requestHeaders("Accept-Language" headerMeans "언어" withDefaultValue "ko" example "en")
                        }
                }

                val table = docs.snippet("header-modifiers", "request-headers.adoc")

                table shouldContain "Default: `+ko+`"
                table shouldContain "Example: `+en+`"
            }
        }

        given("Markdown 템플릿") {
            then("라이브러리 고유 스니펫도 Markdown 으로 만든다") {
                val docs = Docs(markdown = true)

                docs.run("markdown") {
                    mockMvc.perform(get("/ping/{name}", "a"))
                        .andDocument { description = "핑" }
                }

                docs.snippet("markdown", "api-summary.md") shouldContain "`GET /ping/{name}` → `200 OK`"
            }
        }
    })
