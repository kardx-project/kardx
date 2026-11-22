package io.github.kardx.internal.http

import org.springframework.http.HttpHeaders
import org.springframework.restdocs.operation.OperationResponse
import org.springframework.restdocs.operation.preprocess.OperationPreprocessorAdapter
import org.springframework.restdocs.operation.preprocess.Preprocessors

/**
 * `Max-Age` 가 있는 `Set-Cookie` 에서 `Expires` 속성을 뺀다. Spring `ResponseCookie.maxAge(...)` 는 지금 시각에 `Max-Age` 를 더한
 * `Expires` 를 붙이므로 그대로 두면 실행마다 `http-response`·WireMock 매핑이 바뀐다. 둘 다 있으면 브라우저는 `Max-Age` 를 따르므로
 * (RFC 6265 5.3) 빼도 쿠키의 뜻은 같다. `Max-Age` 가 없는 쿠키의 `Expires` 는 고정된 날짜일 수 있어 그대로 둔다.
 *
 * [io.github.kardx.config.HttpSettings.http.removedResponseHeaders] 가 비어 있으면("받은 그대로 둔다") 적용하지 않는다.
 */
internal object VolatileCookieExpires : OperationPreprocessorAdapter() {
    private val MAX_AGE = Regex("""^\s*max-age\s*=""", RegexOption.IGNORE_CASE)
    private val EXPIRES = Regex("""^\s*expires\s*=""", RegexOption.IGNORE_CASE)

    override fun preprocess(response: OperationResponse): OperationResponse {
        val name = HttpHeadersCompat.names(response.headers)
            .firstOrNull { it.equals(HttpHeaders.SET_COOKIE, ignoreCase = true) } ?: return response
        val values = HttpHeadersCompat.values(response.headers, name)
        val cleaned = values.map(::withoutExpires)

        if (cleaned == values) {
            return response
        }

        return Preprocessors.modifyHeaders()
            .set(name, *cleaned.toTypedArray())
            .preprocess(response)
    }

    private fun withoutExpires(cookie: String): String {
        val attributes = cookie.split(';')

        if (attributes.drop(1).none { MAX_AGE.containsMatchIn(it) }) {
            return cookie
        }

        return attributes.filterIndexed { index, attribute -> index == 0 || !EXPIRES.containsMatchIn(attribute) }
            .joinToString(";")
    }
}
