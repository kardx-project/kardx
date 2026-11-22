package io.github.kardx.resttestclient

import org.springframework.http.HttpHeaders
import org.springframework.restdocs.operation.OperationRequest
import org.springframework.restdocs.operation.OperationRequestFactory
import org.springframework.restdocs.operation.OperationResponse
import org.springframework.restdocs.operation.OperationResponseFactory
import org.springframework.restdocs.operation.RequestConverter
import org.springframework.restdocs.operation.RequestCookie
import org.springframework.restdocs.operation.ResponseConverter
import org.springframework.restdocs.operation.ResponseCookie
import org.springframework.test.web.servlet.client.ExchangeResult
import org.springframework.test.web.servlet.client.RestTestClient
import java.net.URI

/**
 * RestTestClient 의 요청을 REST Docs 요청으로 바꾼다. 내부 요청 ID 헤더는 문서에서 뺀다. `Cookie` 헤더는 요청 쿠키가 된다.
 * 상대 URI 는 `http://localhost` 기준으로 푼다.
 * multipart 요청의 파트는 나누지 않는다 (본문 그대로다).
 */
internal object ExchangeRequestConverter : RequestConverter<ExchangeResult> {
    override fun convert(result: ExchangeResult): OperationRequest {
        val headers = copyOf(result.requestHeaders) { it != RestTestClient.RESTTESTCLIENT_REQUEST_ID }
        val cookies = headers[HttpHeaders.COOKIE].orEmpty()
            .flatMap { it.split(';') }
            .mapNotNull(::cookie)

        return OperationRequestFactory().create(
            absolute(result.url),
            result.method,
            result.requestBodyContent,
            headers,
            emptyList(),
            cookies,
        )
    }

    /** MockMvc 에 묶은 클라이언트는 `baseUrl` 이 없으면 상대 URI 를 남긴다. REST Docs 는 절대 URI 가 필요해 `http://localhost` 로 푼다. */
    private fun absolute(url: URI): URI = if (url.isAbsolute) url else LOCALHOST.resolve(url)

    private val LOCALHOST = URI("http://localhost")

    private fun cookie(pair: String): RequestCookie? {
        val name = pair.substringBefore('=')
            .trim()

        if (name.isEmpty()) return null

        return RequestCookie(name, pair.substringAfter('=', "").trim())
    }
}

/** RestTestClient 의 응답을 REST Docs 응답으로 바꾼다. `Set-Cookie` 는 응답 쿠키가 된다. */
internal object ExchangeResponseConverter : ResponseConverter<ExchangeResult> {
    override fun convert(result: ExchangeResult): OperationResponse {
        val cookies = result.responseCookies
            .values
            .flatten()
            .map { ResponseCookie(it.name, it.value) }

        return OperationResponseFactory().create(
            result.status,
            copyOf(result.responseHeaders) { true },
            result.responseBodyContent,
            cookies,
        )
    }
}

/** [headers] 중 [keep] 이 받는 이름만 새 [HttpHeaders] 로 복사한다. 원본과 이어지지 않는 독립된 사본이다. */
private inline fun copyOf(headers: HttpHeaders, keep: (String) -> Boolean): HttpHeaders = HttpHeaders().also { copy ->
    headers.headerSet()
        .filter { keep(it.key) }
        .forEach { (name, values) -> copy.addAll(name, values.filterNotNull()) }
}
