package io.github.kardx.internal.http

import io.github.kardx.config.RestDocsConfiguration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.OperationRequest
import org.springframework.restdocs.operation.OperationRequestFactory
import org.springframework.restdocs.operation.OperationRequestPart
import org.springframework.restdocs.operation.OperationRequestPartFactory
import org.springframework.restdocs.operation.preprocess.OperationPreprocessorAdapter
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * [io.github.kardx.config.MaskingSettings.queryParameters] 의 쿼리·폼 파라미터 값을 가린다. 기본값은 아무것도 가리지 않는다.
 *
 * - 요청 URI 의 쿼리, `application/x-www-form-urlencoded` 본문(MockMvc `.param(...)` 의 POST 는 본문이 된다), multipart 의
 *   파일이 아닌 파트(폼 파라미터)를 바꾸므로 curl·httpie·http-request 스니펫, OpenAPI 예시, WireMock 매핑이 모두 가린 값을 본다.
 * - 파라미터 이름·순서와 다른 파라미터는 그대로 둔다. 값만 [HeaderMasking.maskParameter] 로 바꾼다.
 * - 본문을 바꾸면 REST Docs 의 팩토리가 `Content-Length` 를 새 길이로 고친다.
 * - URI 는 [Preprocessors][io.github.kardx.internal.documentation.requestPreprocessor] 앞쪽에서 ASCII 로 바뀐 뒤라 그대로 이어 붙인다.
 */
internal object SensitiveParameterMasking : OperationPreprocessorAdapter() {
    /** [name] 이 가리는 파라미터인지 (대소문자 무시). */
    fun isMasked(name: String): Boolean =
        RestDocsConfiguration.current.masking.queryParameters.any { it.equals(name, ignoreCase = true) }

    override fun preprocess(request: OperationRequest): OperationRequest {
        if (RestDocsConfiguration.current.masking.queryParameters.isEmpty()) {
            return request
        }

        return maskParts(maskForm(maskQuery(request)))
    }

    private fun maskQuery(request: OperationRequest): OperationRequest {
        val rawQuery = request.uri.rawQuery
        val masked = rawQuery?.let(::maskPairs)

        if (masked == null || masked == rawQuery) {
            return request
        }

        return OperationRequestFactory().create(
            withQuery(request.uri, masked),
            request.method,
            request.content,
            request.headers,
            request.parts,
            request.cookies,
        )
    }

    private fun maskForm(request: OperationRequest): OperationRequest {
        if (request.content.isEmpty() || !isForm(request.headers)) {
            return request
        }

        val body = request.contentAsString
        val masked = maskPairs(body)

        if (masked == body) {
            return request
        }

        return OperationRequestFactory().createFrom(request, masked.toByteArray(StandardCharsets.UTF_8))
    }

    private fun maskParts(request: OperationRequest): OperationRequest {
        val masked = request.parts.map { part -> maskPart(part) ?: part }

        if (masked.zip(request.parts).all { (after, before) -> after === before }) {
            return request
        }

        return OperationRequestFactory().create(
            request.uri,
            request.method,
            request.content,
            request.headers,
            masked,
            request.cookies,
        )
    }

    /** 파일이 아닌 글자 파트의 이름이 가리는 파라미터면 값을 가린 파트, 아니면 null. */
    private fun maskPart(part: OperationRequestPart): OperationRequestPart? {
        val textual = TextualContent.isTextual(
            HttpHeadersCompat.values(part.headers, HttpHeaders.CONTENT_TYPE)
                .firstOrNull(),
        )

        if (part.submittedFileName != null || !textual || !isMasked(part.name)) {
            return null
        }

        val value = HeaderMasking.maskParameter(part.name, part.contentAsString)

        return OperationRequestPartFactory().create(
            part.name,
            part.submittedFileName,
            value.toByteArray(StandardCharsets.UTF_8),
            part.headers,
        )
    }

    private fun isForm(headers: HttpHeaders): Boolean {
        val contentType = HttpHeadersCompat.values(headers, HttpHeaders.CONTENT_TYPE)
            .firstOrNull() ?: return false

        return runCatching { MediaType.APPLICATION_FORM_URLENCODED.includes(MediaType.parseMediaType(contentType)) }
            .getOrDefault(false) // 읽을 수 없는 Content-Type 은 폼이 아닌 것으로 본다
    }

    private fun maskPairs(encoded: String): String = encoded.split('&')
        .joinToString("&", transform = ::maskPair)

    private fun maskPair(pair: String): String {
        val name = decode(pair.substringBefore('='))

        if ('=' !in pair || !isMasked(name)) {
            return pair
        }

        val value = HeaderMasking.maskParameter(name, decode(pair.substringAfter('=')))

        return pair.substringBefore('=') + "=" + encode(value)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun decode(text: String): String = // 잘못 인코딩된 조각은 원문 그대로 이름·값으로 본다
        runCatching { URLDecoder.decode(text, StandardCharsets.UTF_8) }.getOrDefault(text)

    private fun withQuery(uri: URI, rawQuery: String): URI {
        val text = uri.toASCIIString()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()

        return URI(text.substringBefore('?') + "?" + rawQuery + fragment)
    }
}
