package io.github.kardx.internal.http

import org.springframework.http.HttpHeaders
import org.springframework.restdocs.operation.OperationRequest
import org.springframework.restdocs.operation.OperationRequestPart
import org.springframework.restdocs.operation.OperationResponse
import org.springframework.restdocs.operation.preprocess.OperationPreprocessor
import java.nio.charset.StandardCharsets

/**
 * 글자가 아닌 본문(PDF, 이미지, `application/octet-stream` …)을 `<binary: 1234 bytes>` 로 바꾼다. 요청 본문, multipart 파트,
 * 응답 본문에 적용한다. 바이트를 그대로 두면 `http-response.adoc`·`response-body.adoc` 에 깨진 글자가 찍히고, epages
 * `resource.json` 의 예시(OpenAPI `examples`)에도 들어간다.
 *
 * 글자인지는 [TextualContent] 가 Content-Type 으로 판단한다. JSON·XML·`text/csv` 같은 글자 본문과 Content-Type 이 없는 본문은
 * 그대로 둔다. `Content-Length` 헤더는 원래 크기로 둔다.
 *
 * 여러 번 적용해도 된다. 이미 바꾼 본문은 다시 바꾸지 않고, 그 사이 다른 전처리기(REST Docs 의 URI 변경·pretty print 는 본문을
 * 다시 만들며 `Content-Length` 를 새 본문 길이로 고친다)가 바꾼 `Content-Length` 만 원래 크기로 되돌린다. 그래서 요청은 URI 변경이
 * 바이트를 글자로 읽어 망가뜨리기 전(맨 앞)과 pretty print 뒤에 한 번씩 적용한다.
 *
 * REST Docs 의 팩토리는 본문을 바꾸면 `Content-Length` 도 고치므로, 원래 객체에 위임하고 본문(과 헤더)만 바꾼 객체를 돌려준다.
 */
internal object BinaryBodyPlaceholder : OperationPreprocessor {
    private val PLACEHOLDER = Regex("""<binary: (\d+) bytes?>""")

    /** `<binary: 9223372036854775807 bytes>` 보다 긴 본문은 자리 표시가 아니다. */
    private const val MAX_PLACEHOLDER_LENGTH = 40

    override fun preprocess(request: OperationRequest): OperationRequest {
        val content = placeholder(request.content, request.headers)
        val parts = request.parts.map { part ->
            placeholder(part.content, part.headers)?.let { PlaceholderPart(part, it) } ?: part
        }

        val body = content ?: request.content
        val headers = withOriginalLength(request.headers, body)

        if (content == null && headers == null && parts.none { it is PlaceholderPart }) {
            return request
        }

        return PlaceholderRequest(request, body, headers ?: request.headers, parts)
    }

    override fun preprocess(response: OperationResponse): OperationResponse {
        val content = placeholder(response.content, response.headers)
        val body = content ?: response.content
        val headers = withOriginalLength(response.headers, body)

        if (content == null && headers == null) {
            return response
        }

        return PlaceholderResponse(response, body, headers ?: response.headers)
    }

    /** 글자가 아닌 본문이면 자리 표시 바이트. 비었거나 글자 본문이거나 이미 자리 표시면 null. */
    private fun placeholder(content: ByteArray, headers: HttpHeaders): ByteArray? {
        if (content.isEmpty() || isTextual(headers) || originalSize(content) != null) {
            return null
        }

        val unit = if (content.size == 1) "byte" else "bytes"

        return "<binary: ${content.size} $unit>".toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * 글자가 아닌 본문의 [body] 가 자리 표시인데 `Content-Length` 가 원래 크기가 아니면 원래 크기로 고친 헤더.
     * 고칠 것이 없으면(글자 본문이 우연히 자리 표시와 같은 글자인 경우 포함) null.
     */
    private fun withOriginalLength(headers: HttpHeaders, body: ByteArray): HttpHeaders? {
        val size = originalSize(body) ?: return null
        val hasLength = HttpHeadersCompat.names(headers)
            .any { it.equals(HttpHeaders.CONTENT_LENGTH, ignoreCase = true) }

        if (!hasLength || headers.contentLength == size || isTextual(headers)) {
            return null
        }

        return HttpHeadersCompat.copy(headers)
            .apply { contentLength = size }
    }

    private fun isTextual(headers: HttpHeaders): Boolean = TextualContent.isTextual(
        HttpHeadersCompat.values(headers, HttpHeaders.CONTENT_TYPE)
            .firstOrNull(),
    )

    /** 자리 표시 본문이면 그 안의 원래 크기, 아니면 null. 파일 본문 표([FileBody])가 원래 크기를 읽는 데도 쓴다. */
    fun originalSize(content: ByteArray): Long? {
        if (content.size > MAX_PLACEHOLDER_LENGTH) {
            return null
        }

        return PLACEHOLDER.matchEntire(String(content, StandardCharsets.UTF_8))
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    }

    private class PlaceholderRequest(
        original: OperationRequest,
        private val body: ByteArray,
        private val headers: HttpHeaders,
        private val parts: List<OperationRequestPart>,
    ) : OperationRequest by original {
        override fun getContent(): ByteArray = body.copyOf()

        override fun getContentAsString(): String = String(body, StandardCharsets.UTF_8)

        override fun getHeaders(): HttpHeaders = headers

        override fun getParts(): Collection<OperationRequestPart> = parts
    }

    private class PlaceholderPart(original: OperationRequestPart, private val body: ByteArray) :
        OperationRequestPart by original {
        override fun getContent(): ByteArray = body.copyOf()

        override fun getContentAsString(): String = String(body, StandardCharsets.UTF_8)
    }

    private class PlaceholderResponse(
        original: OperationResponse,
        private val body: ByteArray,
        private val headers: HttpHeaders,
    ) : OperationResponse by original {
        override fun getContent(): ByteArray = body.copyOf()

        override fun getContentAsString(): String = String(body, StandardCharsets.UTF_8)

        override fun getHeaders(): HttpHeaders = headers
    }
}
