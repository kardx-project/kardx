package io.github.kardx.internal.http

import org.springframework.http.HttpHeaders
import org.springframework.restdocs.operation.OperationRequest
import org.springframework.restdocs.operation.OperationRequestFactory
import org.springframework.restdocs.operation.OperationResponse
import org.springframework.restdocs.operation.RequestCookie
import org.springframework.restdocs.operation.preprocess.OperationPreprocessor
import org.springframework.restdocs.operation.preprocess.Preprocessors

/**
 * [io.github.kardx.config.MaskingSettings.headers] 의 값을 가린다. 기본값은 아무것도 가리지 않는다.
 *
 * - 요청·응답에 실제로 있는 헤더만 바꾸므로 없는 헤더가 문서에 생기지 않는다.
 * - `Cookie` / `Set-Cookie` 는 쿠키 이름과 속성을 남기고 값만 바꾼다
 *   (`SESSION=****; Path=/; HttpOnly`). 그래서 `requestCookies` / `responseCookies` 문서화가 그대로 동작한다.
 * - `Cookie` 를 가리면 curl 스니펫의 `--cookie` 에 쓰이는 요청 쿠키 목록도 같이 바꾼다.
 * - 가리는 방식은 [io.github.kardx.config.MaskingSettings.masker] 로 바꾼다.
 */
internal object SensitiveHeaderMasking : OperationPreprocessor {
    override fun preprocess(request: OperationRequest): OperationRequest {
        val headersMasked = maskingFor(request.headers)?.preprocess(request) ?: request

        if (!HeaderMasking.isMasked(HttpHeaders.COOKIE) || headersMasked.cookies.isEmpty()) {
            return headersMasked
        }

        return OperationRequestFactory().create(
            headersMasked.uri,
            headersMasked.method,
            headersMasked.content,
            headersMasked.headers,
            headersMasked.parts,
            headersMasked.cookies.map {
                RequestCookie(it.name, HeaderMasking.maskCookie(HttpHeaders.COOKIE, it.name, it.value))
            },
        )
    }

    override fun preprocess(response: OperationResponse): OperationResponse =
        maskingFor(response.headers)?.preprocess(response) ?: response

    private fun maskingFor(headers: HttpHeaders): OperationPreprocessor? {
        // 설정의 대소문자가 아니라 요청·응답에 실제로 있는 헤더 이름을 쓴다 (문서의 헤더 이름이 바뀌지 않게).
        val present = HttpHeadersCompat.names(headers)
            .filter(HeaderMasking::isMasked)

        if (present.isEmpty()) return null

        return present.fold(Preprocessors.modifyHeaders()) { modifier, name ->
            val masked = HttpHeadersCompat.values(headers, name)
                .map { HeaderMasking.mask(name, it) }

            modifier.set(name, *masked.toTypedArray())
        }
    }
}
