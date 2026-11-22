package io.github.kardx.mockmvc

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.headerMeans
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.operation.preprocess.Preprocessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files
import java.time.Duration

private const val DATE = "Sat, 21 Nov 2026 05:42:32 GMT"

/** 실제 서버처럼 응답마다 `Date` 를 붙인다 (MockMvc 는 스스로 붙이지 않는다). */
@RestController
class DatedController {
    @GetMapping("/dated")
    fun dated(): ResponseEntity<String> = ResponseEntity.ok()
        .header("Date", DATE)
        .header("X-Request-Id", "req-1")
        .body("ok")

    /** `ResponseCookie.maxAge` 는 지금 시각에서 계산한 `Expires` 를 붙인다 (실행마다 바뀐다) */
    @GetMapping("/remember")
    fun remember(): ResponseEntity<String> = ResponseEntity.ok()
        .header(
            "Set-Cookie",
            ResponseCookie.from("REMEMBER", "r-1")
                .path("/")
                .maxAge(Duration.ofDays(7))
                .httpOnly(true)
                .build()
                .toString(),
        )
        .header("Set-Cookie", "LEGACY=l-1; Path=/; Expires=$DATE")
        .body("ok")
}

private class DatedDocs {
    val output: File = Files.createTempDirectory("removed-headers").toFile()
    private val documentation = ManualRestDocumentation(output.absolutePath)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(DatedController())
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()

    fun document(method: String, path: String = "/dated", block: DocumentationBuilder.() -> Unit = {}) {
        documentation.beforeTest(RemovedResponseHeadersTest::class.java, method)

        try {
            mockMvc.perform(get(path))
                .andDocument(block)
        } finally {
            documentation.afterTest()
        }
    }

    fun file(path: String): String = File(output, path).readText()

    fun httpResponse(method: String): String = file("removed-response-headers-test/$method/http-response.adoc")
}

class RemovedResponseHeadersTest :
    BehaviorSpec({
        given("응답마다 값이 바뀌는 Date 헤더") {
            then("기본값은 스니펫에서 Date 를 빼고 다른 헤더는 남긴다") {
                val docs = DatedDocs()

                docs.document("default")

                val response = docs.httpResponse("default")

                response shouldNotContain "Date:"
                response shouldContain "X-Request-Id: req-1"
            }

            then("WireMock 매핑에도 Date 를 남기지 않는다") {
                val docs = DatedDocs()

                RestDocsConfiguration.withSettings({ output.wireMockStubs = true }) { docs.document("wire-mock") }

                val mapping = docs.file("wiremock/mappings/removed-response-headers-test/wire-mock.json")

                mapping shouldNotContain DATE
                mapping shouldContain "req-1"
            }

            then("responseHeaders 로 문서화한 헤더는 빼지 않는다 (대소문자 무시)") {
                val docs = DatedDocs()

                docs.document("documented") { responseHeaders("date" headerMeans "응답 시각") }

                docs.httpResponse("documented") shouldContain "Date: $DATE"
                docs.file("removed-response-headers-test/documented/response-headers.adoc") shouldContain "date"
            }

            then("http.removedResponseHeaders 를 비우면 Date 를 남긴다") {
                val docs = DatedDocs()

                RestDocsConfiguration.withSettings({
                    http.removedResponseHeaders = emptySet()
                }) { docs.document("kept") }

                docs.httpResponse("kept") shouldContain "Date: $DATE"
            }

            then("목록에 더한 헤더도 빼고, 사용자 전처리기는 그 뒤에 적용한다") {
                val docs = DatedDocs()

                RestDocsConfiguration.withSettings({
                    http.removedResponseHeaders = http.removedResponseHeaders + "X-Request-Id"
                    http.responsePreprocessors = listOf(Preprocessors.modifyHeaders().add("X-Request-Id", "added"))
                }) { docs.document("extra") }

                val response = docs.httpResponse("extra")

                response shouldNotContain "req-1"
                response shouldContain "X-Request-Id: added"
            }

            then("REST Docs 의 responseHeaders 를 snippets(...) 로 넘겨 문서화한 헤더도 빼지 않는다") {
                val docs = DatedDocs()

                docs.document("raw-snippet") {
                    snippets(responseHeaders(headerWithName("Date").description("응답 시각")))
                }

                docs.httpResponse("raw-snippet") shouldContain "Date: $DATE"
            }
        }

        given("Max-Age 가 있는 Set-Cookie") {
            then("실행마다 바뀌는 Expires 속성을 뺀다 (Max-Age 가 우선한다)") {
                val docs = DatedDocs()

                RestDocsConfiguration.withSettings({
                    output.wireMockStubs = true
                }) { docs.document("cookie", "/remember") }

                val response = docs.httpResponse("cookie")

                response shouldContain "REMEMBER=r-1; Path=/; Max-Age=604800; HttpOnly"
                response shouldNotContain "REMEMBER=r-1; Path=/; Max-Age=604800; Expires"
                response shouldContain "LEGACY=l-1; Path=/; Expires=$DATE"
                docs.file("wiremock/mappings/removed-response-headers-test/cookie.json") shouldNotContain
                    "Max-Age=604800; Expires"
            }

            then("http.removedResponseHeaders 를 비우면 받은 그대로 둔다") {
                val docs = DatedDocs()

                RestDocsConfiguration.withSettings({ http.removedResponseHeaders = emptySet() }) {
                    docs.document("cookie-kept", "/remember")
                }

                docs.httpResponse("cookie-kept") shouldContain "Max-Age=604800; Expires="
            }
        }
    })
