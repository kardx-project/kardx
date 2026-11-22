package io.github.kardx.internal.documentation

import io.github.kardx.config.RestDocsConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.RestDocumentationContext
import org.springframework.restdocs.operation.OperationRequestFactory
import org.springframework.restdocs.operation.OperationResponseFactory
import org.springframework.restdocs.operation.StandardOperation
import java.io.File
import java.net.URI
import java.nio.file.Files

private fun headers(vararg pairs: Pair<String, String>): HttpHeaders = HttpHeaders().apply {
    pairs.forEach { (name, value) -> add(name, value) }
}

private fun document(
    output: File,
    method: String,
    uri: String,
    requestHeaders: HttpHeaders,
    requestBody: String,
    responseHeaders: HttpHeaders,
    responseBody: ByteArray,
    rawResponseBody: ByteArray? = null,
): String {
    val documentation = ManualRestDocumentation(output.path)

    documentation.beforeTest(WireMockStubSnippetTest::class.java, "createOrder")

    val context = documentation.beforeOperation()
    val request = OperationRequestFactory().create(
        URI(uri),
        HttpMethod.valueOf(method),
        requestBody.toByteArray(),
        requestHeaders,
        emptyList(),
    )

    val response = OperationResponseFactory().create(HttpStatus.CREATED, responseHeaders, responseBody)

    WireMockStubSnippet { rawResponseBody }.document(
        StandardOperation(
            "{class-name}/{method-name}",
            request,
            response,
            mapOf(RestDocumentationContext::class.java.name to context),
        ),
    )

    documentation.afterTest()

    return File(output, "wiremock/mappings/wire-mock-stub-snippet-test/create-order.json").readText()
}

class WireMockStubSnippetTest :
    BehaviorSpec({
        given("JSON 요청·응답을 문서화하면") {
            val output = Files.createTempDirectory("stubs").toFile()
            val json = document(
                output,
                "POST",
                "http://localhost:8080/api/orders?channel=web&tag=a&tag=b%20c",
                headers("Content-Type" to "application/json", "Authorization" to "Bearer ****"),
                """{"item":"책","quantity":2}""",
                headers("Content-Type" to "application/json", "Content-Length" to "20", "Location" to "/api/orders/1"),
                """{"id":1}""".toByteArray(),
            )

            then("요청은 메서드·경로·쿼리·JSON 본문으로 맞춘다 (헤더는 맞추지 않는다)") {
                json shouldContain """"method" : "POST""""
                json shouldContain """"urlPath" : "/api/orders""""
                json shouldContain """"equalTo" : "web""""
                json shouldContain """"hasExactly""""
                json shouldContain """"equalTo" : "b c""""
                json shouldContain """"equalToJson" : "{\"item\":\"책\",\"quantity\":2}""""
                json shouldNotContain "Bearer"
            }

            then("응답은 상태·헤더·본문을 담고 Content-Length 는 뺀다") {
                json shouldContain """"status" : 201"""
                json shouldContain """"Location" : "/api/orders/1""""
                json shouldContain """"body" : "{\"id\":1}""""
                json shouldNotContain "Content-Length"
            }

            then("id 는 문서 식별자로 정해져 다시 만들어도 같다") {
                val again = document(
                    Files.createTempDirectory("stubs").toFile(),
                    "POST",
                    "http://localhost:8080/api/orders?channel=web&tag=a&tag=b%20c",
                    headers("Content-Type" to "application/json"),
                    """{"item":"책","quantity":2}""",
                    headers("Content-Type" to "application/json", "Location" to "/api/orders/1"),
                    """{"id":1}""".toByteArray(),
                )

                Regex(""""id" : "([^"]+)"""").find(again)?.groupValues?.get(1) shouldBe
                    Regex(""""id" : "([^"]+)"""").find(json)?.groupValues?.get(1)
            }
        }

        given("바이너리 응답") {
            val json = document(
                Files.createTempDirectory("stubs").toFile(),
                "GET",
                "http://localhost:8080/api/files/1",
                HttpHeaders(),
                "",
                headers("Content-Type" to "image/png"),
                byteArrayOf(-119, 80, 78, 71),
            )

            then("base64Body 로 쓰고 요청 본문 패턴은 없다") {
                json shouldContain """"base64Body" : "iVBORw==""""
                json shouldNotContain "bodyPatterns"
            }
        }

        given("문서 스니펫에서 자리 표시로 바뀐 바이너리 응답") {
            val json = document(
                Files.createTempDirectory("stubs").toFile(),
                "GET",
                "http://localhost:8080/api/files/1",
                HttpHeaders(),
                "",
                headers("Content-Type" to "image/png"),
                "<binary: 4 bytes>".toByteArray(),
                rawResponseBody = byteArrayOf(-119, 80, 78, 71),
            )

            then("자리 표시가 아니라 원래 바이트를 base64Body 로 쓴다") {
                json shouldContain """"base64Body" : "iVBORw==""""
            }
        }

        given("text/csv 응답") {
            val json = document(
                Files.createTempDirectory("stubs").toFile(),
                "GET",
                "http://localhost:8080/api/export",
                HttpHeaders(),
                "",
                headers("Content-Type" to "text/csv;charset=UTF-8"),
                "id,name\n1,a\n".toByteArray(),
            )

            then("글자 본문이라 body 로 쓴다") {
                json shouldContain """"body" : "id,name\n1,a\n""""
            }
        }

        given("가리는 쿼리 파라미터 (masking.maskSensitive)") {
            val json = RestDocsConfiguration.withSettings({ masking.maskSensitive() }) {
                document(
                    Files.createTempDirectory("stubs").toFile(),
                    "GET",
                    "http://localhost:8080/api/stream?access_token=****&page=2",
                    HttpHeaders(),
                    "",
                    headers("Content-Type" to "application/json"),
                    "{}".toByteArray(),
                )
            }

            then("가린 값이 아니라 아무 값이나 맞춘다") {
                json shouldContain """"access_token""""
                json shouldContain """"matches" : ".*""""
                json shouldContain """"equalTo" : "2""""
                json shouldNotContain "****"
            }
        }
    })
