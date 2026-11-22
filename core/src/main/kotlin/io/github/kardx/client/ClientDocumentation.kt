package io.github.kardx.client

import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.internal.documentation.httpDocumentation
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.websocket.documentationConfigurationMap
import io.github.kardx.lifecycle.RestDocsExtensionApi
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.operation.RequestConverter
import org.springframework.restdocs.operation.ResponseConverter

/**
 * HTTP 테스트 클라이언트 어댑터를 만드는 SPI. MockMvc·WebTestClient·RestTestClient 의 `andDocument` 가 쓰는 것과 같은 문서화
 * (DSL 블록 → 스니펫·OpenAPI 조각·WireMock 매핑, 설정의 가리기·전처리기)를 다른 클라이언트(Ktor, RestAssured …)에 붙일 때 쓴다.
 * 이 라이브러리의 RestTestClient 어댑터(`io.github.kardx.resttestclient`)가 이 API 만으로 만들어져 있다.
 *
 * 어댑터 하나는 다음 순서로 동작한다.
 * 1. 요청을 보내기 전(또는 클라이언트 인터셉터에서) [configuration] 으로 이 요청의 REST Docs 설정 맵을 만든다.
 *    REST Docs 문맥의 순번이 하나 오르므로 요청마다 한 번 부른다
 * 2. 응답을 받으면 클라이언트의 요청·응답을 REST Docs `OperationRequest`·`OperationResponse` 로 바꾸는 변환기와 함께 [document] 를
 *    부른다. URL 템플릿(`/users/{id}`)을 알면 [urlTemplate] 으로 넘긴다 (경로 파라미터 문서화에 필요하다)
 *
 * ```kotlin
 * fun HttpResponse.andDocument(block: DocumentationBuilder.() -> Unit) {
 *     ClientDocumentation.document(
 *         exchange = this,
 *         requestConverter = KtorRequestConverter,
 *         responseConverter = KtorResponseConverter,
 *         configuration = ClientDocumentation.configuration(provider),
 *         status = status.value,
 *         urlTemplate = call.attributes.getOrNull(UrlTemplateKey),
 *         block = block,
 *     )
 * }
 * ```
 *
 * 확장 제작자용 API 라 `@OptIn(RestDocsExtensionApi::class, ExperimentalRestDocsApi::class)` 가 필요하고, 아직 다듬는 중이다.
 */
@RestDocsExtensionApi
@ExperimentalRestDocsApi
public object ClientDocumentation {
    /** REST Docs 문서 식별자. 스니펫 디렉터리(`{class-name}/{method-name}`)를 정한다. */
    public const val DOCUMENT_IDENTIFIER: String = SnippetPaths.DOCUMENT_IDENTIFIER

    /**
     * 요청 하나의 REST Docs 설정 맵 (문맥, 템플릿 엔진, 스니펫 기본값). [provider] 의 순번을 하나 올린다.
     * 기본 HTTP 스니펫(curl, http-request, http-response 등)을 넣는다.
     */
    public fun configuration(provider: RestDocumentationContextProvider): MutableMap<String, Any> =
        documentationConfigurationMap(provider, httpSnippets = true)

    /**
     * 교환 하나를 [block] 대로 문서화한다. [exchange] 는 [requestConverter]·[responseConverter] 에 그대로 넘어간다.
     *
     * @param configuration 이 요청을 보내기 전에 [configuration] 으로 만든 맵
     * @param status 응답 상태 코드. 오류 응답 스키마 이름과 선언한 `errorResponse` 정리에 쓴다
     * @param urlTemplate 요청 URL 템플릿 (`/users/{id}`). 모르면 null (그러면 경로 파라미터를 문서화할 수 없다)
     * @param handler 요청을 처리한 Spring 핸들러(`HandlerMethod`). 알면 operationId·파라미터 타입 기본값에 쓴다
     * @param rawResponseBody 전처리 전의 응답 본문. 바이너리 본문의 WireMock 매핑에 쓴다
     * @param applicationContext 요청을 처리한 앱 컨텍스트. 문서화 누락 리포트(엔드포인트 목록)에 쓴다
     */
    @Suppress("LongParameterList") // 어댑터가 아는 만큼 넘기는 선택 인자들이다
    public fun <T : Any> document(
        exchange: T,
        requestConverter: RequestConverter<T>,
        responseConverter: ResponseConverter<T>,
        configuration: MutableMap<String, Any>,
        status: Int,
        urlTemplate: String? = null,
        handler: Any? = null,
        rawResponseBody: () -> ByteArray? = { null },
        applicationContext: Any? = null,
        block: DocumentationBuilder.() -> Unit,
    ) {
        urlTemplate?.let { configuration[RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE] = it }

        val parts = httpDocumentation(
            block,
            status,
            stream = false,
            handler = handler,
            rawResponseBody = rawResponseBody,
            applicationContext = applicationContext,
        )

        RestDocumentationGenerator(
            DOCUMENT_IDENTIFIER,
            requestConverter,
            responseConverter,
            parts.requestPreprocessor,
            parts.responsePreprocessor,
            *parts.snippets,
        ).handle(exchange, exchange, configuration)
    }
}
