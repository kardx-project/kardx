package io.github.kardx.resttestclient

import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.client.ClientDocumentation
import io.github.kardx.lifecycle.RestDocsExtensionApi
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.web.servlet.client.ExchangeResult
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.concurrent.ConcurrentHashMap

/**
 * 요청마다 REST Docs 설정 맵(문맥·순번·템플릿 엔진·스니펫 설정)을 만들어 RestTestClient 의 요청 ID 로 기록한다.
 * `andDocument` 가 [take] 로 꺼내 쓴다. REST Docs 의 WebTestClient 지원과 같은 방식이다.
 */
@OptIn(RestDocsExtensionApi::class, ExperimentalRestDocsApi::class)
internal class DocumentationInterceptor(private val provider: RestDocumentationContextProvider) :
    ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        request.headers.getFirst(RestTestClient.RESTTESTCLIENT_REQUEST_ID)
            ?.let { CONFIGURATIONS[it] = ClientDocumentation.configuration(provider) }

        return execution.execute(request, body)
    }

    companion object {
        private val CONFIGURATIONS = ConcurrentHashMap<String, MutableMap<String, Any>>()

        /** [result] 의 요청에 기록한 설정. 한 번만 꺼낼 수 있다. `restDocs(provider)` 없이 만든 클라이언트면 null. */
        fun take(result: ExchangeResult): MutableMap<String, Any>? =
            result.requestHeaders.getFirst(RestTestClient.RESTTESTCLIENT_REQUEST_ID)
                ?.let { CONFIGURATIONS.remove(it) }
    }
}
