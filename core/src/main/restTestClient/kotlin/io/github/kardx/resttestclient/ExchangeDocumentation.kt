package io.github.kardx.resttestclient

import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.client.ClientDocumentation
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.lifecycle.RestDocsExtensionApi
import org.springframework.test.web.servlet.client.ExchangeResult

/**
 * 본문까지 받은 RestTestClient 교환 하나를 [block] 대로 문서화한다. `andDocument` 들이 같이 쓴다.
 * 공개 어댑터 SPI([ClientDocumentation])만으로 만든다. 요청을 처리한 핸들러는 알 수 없으므로 operationId·파라미터 타입의 핸들러 기본값은 쓰지 않는다.
 *
 * @throws IllegalStateException 클라이언트에 `restDocs(provider)` 를 달지 않았으면
 */
@OptIn(RestDocsExtensionApi::class, ExperimentalRestDocsApi::class)
internal fun documentExchange(result: ExchangeResult, block: DocumentationBuilder.() -> Unit) {
    val configuration = checkNotNull(DocumentationInterceptor.take(result)) {
        RestDocsErrorCode.REST_DOCS_NOT_CONFIGURED.message(
            "this RestTestClient has no REST Docs configuration. Build it with restDocs(provider).",
        )
    }

    ClientDocumentation.document(
        exchange = result,
        requestConverter = ExchangeRequestConverter,
        responseConverter = ExchangeResponseConverter,
        configuration = configuration,
        status = result.status.value(),
        urlTemplate = result.uriTemplate,
        rawResponseBody = { result.responseBodyContent },
        block = block,
    )
}
