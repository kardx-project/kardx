package io.github.kardx.internal.documentation

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.http.BinaryBodyPlaceholder
import io.github.kardx.internal.http.SensitiveHeaderMasking
import io.github.kardx.internal.http.SensitiveParameterMasking
import io.github.kardx.internal.http.VolatileCookieExpires
import org.springframework.restdocs.operation.OperationRequest
import org.springframework.restdocs.operation.OperationRequestFactory
import org.springframework.restdocs.operation.preprocess.OperationPreprocessorAdapter
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor
import org.springframework.restdocs.operation.preprocess.Preprocessors
import java.net.URI

/**
 * 모든 HTTP 문서화(MockMvc·WebTestClient `andDocument`)에 쓰는 요청 전처리. URI 를 localhost 로 맞추고 민감 헤더·쿼리·폼 파라미터를 가리고 본문을
 * pretty print 한 뒤 사용자 전처리기([io.github.kardx.config.HttpSettings.http.requestPreprocessors])를 순서대로 적용한다.
 *
 * 글자가 아닌 본문·파트는 맨 앞에서 크기 표시로 바꾼다([BinaryBodyPlaceholder]). URI 변경이 본문을 글자로 읽어 바이트를 망가뜨리기
 * 전에 크기를 세기 위해서다. 뒤의 전처리기가 고친 `Content-Length` 는 pretty print 뒤에 한 번 더 적용해 원래 크기로 되돌린다.
 *
 * REST Docs 는 문서화 호출의 전처리기를 MockMvc·WebTestClient 의 기본 전처리기(`withRequestDefaults`)보다 먼저 적용하므로,
 * 라이브러리 가공은 모두 여기에 두어 사용자 전처리기가 항상 마지막에 오게 한다.
 */
internal fun requestPreprocessor(): OperationRequestPreprocessor = Preprocessors.preprocessRequest(
    BinaryBodyPlaceholder,
    AsciiUri,
    Preprocessors.modifyUris()
        .scheme("http")
        .host("localhost")
        .removePort(),
    SensitiveHeaderMasking,
    SensitiveParameterMasking,
    Preprocessors.prettyPrint(),
    BinaryBodyPlaceholder,
    *RestDocsConfiguration.current.http.requestPreprocessors.toTypedArray(),
)

/**
 * 모든 HTTP 문서화에 쓰는 응답 전처리. pretty print, 민감 헤더 가리기, 글자가 아닌 본문의 크기 표시([BinaryBodyPlaceholder]),
 * 실행마다 값이 바뀌는 헤더 빼기([io.github.kardx.config.HttpSettings.http.removedResponseHeaders])와 `Max-Age` 가 있는
 * `Set-Cookie` 의 `Expires` 빼기([VolatileCookieExpires], 그 설정이 비어 있으면 둘 다 하지 않는다) 뒤에
 * 사용자 전처리기([io.github.kardx.config.HttpSettings.http.responsePreprocessors])를 순서대로 적용한다.
 *
 * @param documentedHeaders 테스트가 `responseHeaders(...)`(DSL 이나 `snippets(...)` 로 넘긴 REST Docs 스니펫)로 문서화한 헤더
 *   이름. 이 헤더는 빼지 않는다
 */
internal fun responsePreprocessor(documentedHeaders: Collection<String> = emptyList()): OperationResponsePreprocessor {
    val settings = RestDocsConfiguration.current
    val removed = settings.http.removedResponseHeaders
        .filterNot { name -> documentedHeaders.any { it.equals(name, ignoreCase = true) } }
    val removal = removed.takeIf { it.isNotEmpty() }
        ?.fold(Preprocessors.modifyHeaders()) { headers, name -> headers.remove(name) }
    val cookieExpires = VolatileCookieExpires.takeIf { settings.http.removedResponseHeaders.isNotEmpty() }

    return Preprocessors.preprocessResponse(
        Preprocessors.prettyPrint(),
        SensitiveHeaderMasking,
        BinaryBodyPlaceholder,
        *listOfNotNull(removal, cookieExpires).toTypedArray(),
        *settings.http.responsePreprocessors.toTypedArray(),
    )
}

/**
 * URI 의 ASCII 가 아닌 글자를 퍼센트 인코딩한다. WebTestClient 의 `uri("/users?name={name}", "홍길동")` 은 글자를 그대로 둔
 * URI 를 남기는데, REST Docs 의 `modifyUris()` 는 인코딩된 URI 만 받아 실패한다. MockMvc 의 URI 는 이미 ASCII 라 그대로다.
 */
private object AsciiUri : OperationPreprocessorAdapter() {
    override fun preprocess(request: OperationRequest): OperationRequest {
        val ascii = request.uri.toASCIIString()

        if (ascii == request.uri.toString()) {
            return request
        }

        return OperationRequestFactory().create(
            URI(ascii),
            request.method,
            request.content,
            request.headers,
            request.parts,
            request.cookies,
        )
    }
}
