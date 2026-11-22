package io.github.kardx.internal.http

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.restdocs.operation.OperationRequestFactory
import org.springframework.restdocs.operation.OperationRequestPartFactory
import org.springframework.restdocs.operation.OperationResponseFactory
import java.net.URI

private fun headers(contentType: String?): HttpHeaders = HttpHeaders().apply {
    contentType?.let { add(HttpHeaders.CONTENT_TYPE, it) }
}

private val PDF = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0xE2.toByte(), 0xE3.toByte())

class BinaryBodyPlaceholderTest :
    BehaviorSpec({
        given("응답 본문") {
            then("글자가 아닌 본문은 크기 표시로 바꾸고 Content-Length 는 원래 값을 남긴다") {
                val original = OperationResponseFactory().create(HttpStatus.OK, headers("application/pdf"), PDF)
                val response = BinaryBodyPlaceholder.preprocess(original)

                response.contentAsString shouldBe "<binary: 6 bytes>"
                response.headers.contentLength shouldBe 6L
                response.status shouldBe HttpStatus.OK
            }

            then("1 바이트면 단수로 쓴다") {
                val original = OperationResponseFactory().create(
                    HttpStatus.OK,
                    headers("application/octet-stream"),
                    byteArrayOf(1),
                )

                BinaryBodyPlaceholder.preprocess(original).contentAsString shouldBe "<binary: 1 byte>"
            }

            listOf(
                "text/csv;charset=UTF-8",
                "text/plain",
                "application/json",
                "application/hal+json",
                "application/problem+json",
                "application/xml",
                "image/svg+xml",
                "application/x-www-form-urlencoded",
                "application/yaml",
                "application/x-ndjson",
                "text/event-stream",
                null,
            ).forEach { type ->
                then("글자 본문($type)은 그대로 둔다") {
                    val original = OperationResponseFactory()
                        .create(HttpStatus.OK, headers(type), "id,name".toByteArray())

                    BinaryBodyPlaceholder.preprocess(original) shouldBeSameInstanceAs original
                }
            }

            then("글자 본문이 자리 표시와 같은 글자여도 Content-Length 를 고치지 않는다") {
                val original = OperationResponseFactory()
                    .create(HttpStatus.OK, headers("text/plain"), "<binary: 3 bytes>".toByteArray())

                BinaryBodyPlaceholder.preprocess(original) shouldBeSameInstanceAs original
            }

            then("빈 본문은 그대로 둔다") {
                val original = OperationResponseFactory().create(HttpStatus.OK, headers("image/png"), ByteArray(0))

                BinaryBodyPlaceholder.preprocess(original) shouldBeSameInstanceAs original
            }
        }

        given("요청 본문과 multipart 파트") {
            val file = OperationRequestPartFactory().create("file", "a.png", PDF, headers("image/png"))
            val meta = OperationRequestPartFactory()
                .create("meta", null, """{"a":1}""".toByteArray(), headers("application/json"))

            then("바이너리 파트만 크기 표시로 바꾸고 JSON 파트·이름·파일 이름은 그대로 둔다") {
                val original = OperationRequestFactory().create(
                    URI("http://localhost/upload"),
                    HttpMethod.POST,
                    ByteArray(0),
                    headers("multipart/form-data"),
                    listOf(file, meta),
                )
                val parts = BinaryBodyPlaceholder.preprocess(original).parts.toList()

                parts.map { it.name } shouldBe listOf("file", "meta")
                parts[0].submittedFileName shouldBe "a.png"
                parts[0].contentAsString shouldBe "<binary: 6 bytes>"
                parts[1].contentAsString shouldBe """{"a":1}"""
            }

            then("application/octet-stream 요청 본문은 크기 표시로 바꾼다") {
                val original = OperationRequestFactory().create(
                    URI("http://localhost/upload"),
                    HttpMethod.PUT,
                    PDF,
                    headers("application/octet-stream"),
                    emptyList(),
                )
                val request = BinaryBodyPlaceholder.preprocess(original)

                request.contentAsString shouldBe "<binary: 6 bytes>"
                request.uri shouldBe original.uri
                request.headers.contentLength shouldBe 6L
            }

            then("다시 적용하면 본문은 그대로 두고 다른 전처리기가 고친 Content-Length 만 원래 크기로 되돌린다") {
                val original = OperationRequestFactory().create(
                    URI("http://localhost/upload"),
                    HttpMethod.PUT,
                    PDF,
                    headers("application/octet-stream"),
                    emptyList(),
                )
                val placeholder = BinaryBodyPlaceholder.preprocess(original)
                // REST Docs 의 URI 변경·pretty print 처럼 본문을 다시 만들면 Content-Length 가 자리 표시 길이가 된다
                val rebuilt = OperationRequestFactory().createFrom(placeholder, placeholder.content)

                rebuilt.headers.contentLength shouldBe 17L

                val again = BinaryBodyPlaceholder.preprocess(rebuilt)

                again.contentAsString shouldBe "<binary: 6 bytes>"
                again.headers.contentLength shouldBe 6L
            }

            then("JSON 요청은 그대로 둔다") {
                val original = OperationRequestFactory().create(
                    URI("http://localhost/echo"),
                    HttpMethod.POST,
                    """{"a":1}""".toByteArray(),
                    headers("application/json"),
                    emptyList(),
                )

                BinaryBodyPlaceholder.preprocess(original) shouldBeSameInstanceAs original
            }
        }
    })
