package io.github.kardx.webtestclient

import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.internal.documentation.httpDocumentation
import io.github.kardx.internal.documentation.withRestDocsConfiguration
import io.github.kardx.internal.output.SnippetPaths.DOCUMENT_IDENTIFIER
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.web.reactive.server.ExchangeResult

/**
 * 본문까지 받은 WebTestClient 교환 하나를 [block] 대로 문서화한다. `BodyContentSpec`·`BodySpec` 의 `andDocument` 가
 * 같이 쓴다. 본문을 끝까지 읽은 뒤 부르므로 끝나는 응답이다.
 */
internal fun documentExchange(result: ExchangeResult, block: DocumentationBuilder.() -> Unit) {
    val recorded = HandlerRecorder.take(result)
    val parts = httpDocumentation(
        block,
        result.status.value(),
        stream = false,
        handler = recorded?.handler,
        rawResponseBody = { result.responseBodyContent },
        applicationContext = recorded?.applicationContext,
    )

    val documentation = WebTestClientRestDocumentation.document<ExchangeResult>(
        DOCUMENT_IDENTIFIER,
        parts.requestPreprocessor,
        parts.responsePreprocessor,
        *parts.snippets,
    )

    withRestDocsConfiguration("WebTestClient", WEB_TEST_CLIENT_FIX) { documentation.accept(result) }
}

private const val WEB_TEST_CLIENT_FIX = "Take it as a test method parameter (fun getUser(webTestClient: " +
    "WebTestClient)) with AutoRestDocumentationExtension, or build it with restDocsWebTestClient(context), or add " +
    "the REST Docs filter with .mutate().restDocs(provider)."
