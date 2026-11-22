package io.github.kardx.internal.documentation

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.internal.coverage.EndpointInventory
import io.github.kardx.internal.openapi.DocumentedHandler
import io.github.kardx.internal.openapi.HandlerApiVersion
import io.github.kardx.internal.openapi.OpenApiGlobalHeaders
import io.github.kardx.internal.openapi.OpenApiOperationExtras
import io.github.kardx.internal.openapi.OpenApiSchemaMetadata
import io.github.kardx.internal.openapi.OpenApiSupport
import io.github.kardx.internal.openapi.UndocumentedPathVariables
import org.springframework.restdocs.headers.AbstractHeadersSnippet
import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.headers.ResponseHeadersSnippet
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor
import org.springframework.restdocs.snippet.Snippet
import java.lang.reflect.Method

/**
 * `andDocument { }` 한 번의 REST Docs 입력: 전처리기와 스니펫 목록. 클라이언트마다 이 값을 자기 REST Docs 문서화 함수
 * (`MockMvcRestDocumentation.document` 등)에 넘기기만 한다.
 */
internal class HttpDocumentation(
    val requestPreprocessor: OperationRequestPreprocessor,
    val responsePreprocessor: OperationResponsePreprocessor,
    val snippets: Array<Snippet>,
)

/**
 * `andDocument { }` 블록을 REST Docs 전처리기와 스니펫 목록으로 바꾼다. 테스트 클라이언트(MockMvc, WebTestClient)와 무관한
 * 부분이다. 응답 전처리는 블록이 문서화한 응답 헤더(DSL `responseHeaders` 와 `snippets(...)` 로 넘긴 REST Docs
 * `responseHeaders`)를 빼지 않는다([responsePreprocessor]).
 *
 * 문맥 검사([DocumentationContextCheck]), 표준 스니펫(항상 `api-summary` 포함), epages 가 있고 켜져 있으면 OpenAPI 조각,
 * `output.wireMockStubs` 면 WireMock 매핑 순이다.
 *
 * @param status 응답 상태. 오류 응답의 스키마 이름([withSchemaNames])에 쓰고, 같은 상태의 `errorResponse` 선언은 뺀다
 *   ([withoutReceivedErrorResponse])
 * @param stream 끝나지 않는 스트림을 고정한 결과인지. 그렇다면 OpenAPI 에서 뺀다([withStreamExclusion])
 * @param handler 요청을 처리한 핸들러 (MockMvc `MvcResult.handler` 등). operationId 기본 규칙, 파라미터 타입·deprecated
 *   기본값([withHandlerDefaults])에 쓴다. 모르면 null
 * @param rawResponseBody 전처리 전의 응답 본문. 바이너리 본문의 WireMock 매핑(`base64Body`)에 쓴다
 * @param applicationContext 요청을 처리한 앱 컨텍스트. 엔드포인트 인벤토리([EndpointInventory])에 쓴다. 모르면 null
 */
internal fun httpDocumentation(
    block: DocumentationBuilder.() -> Unit,
    status: Int,
    stream: Boolean,
    handler: Any? = null,
    rawResponseBody: () -> ByteArray? = { null },
    applicationContext: Any? = null,
): HttpDocumentation {
    val spec = DocumentationBuilder().apply(block)
        .toSpec()
        .withHandlerDefaults(handler)
        .requireDescriptions()
        .withSchemaNames(status)
        .withStreamExclusion(stream)
        .withoutReceivedErrorResponse(status)
    val settings = RestDocsConfiguration.current

    val openApiSnippets = if (settings.openApi.enabled && OpenApiSupport.isAvailable && !spec.openApi.excluded) {
        OpenApiGlobalHeaders.publish(settings.openApi.globalHeaders, RestDocsConfiguration.snippetsDirectory())

        val resource = spec.openApiResource(
            settings.openApi.securitySchemes,
            DocumentedHandler.of(handler),
            HandlerApiVersion.of(handler),
        )

        listOfNotNull(
            OpenApiSupport.resourceSnippet(resource),
            OpenApiSchemaMetadata(spec.openApi.requestOrigin, spec.openApi.responseOrigin),
            OpenApiOperationExtras(resource),
            UndocumentedPathVariables(spec.pathParameters.map { it.name }.toSet()),
            EndpointInventory.snippet(applicationContext),
        )
    } else {
        emptyList()
    }

    val stubSnippets = if (settings.output.wireMockStubs) listOf(WireMockStubSnippet(rawResponseBody)) else emptyList()

    // 문맥 검사를 맨 앞에, 그다음 표준 스니펫을 둔다. epages 리소스 스니펫도 같은 검증을 하므로, 앞에 있으면 진단 없는 원래
    // 예외로 실패한다
    val snippets = listOf(DocumentationContextCheck) + spec.standardSnippets() + spec.extraSnippets + openApiSnippets +
        stubSnippets
    val documentedHeaders = spec.responseHeaders.map { it.descriptor.name } + rawResponseHeaderNames(spec.extraSnippets)

    return HttpDocumentation(
        requestPreprocessor(),
        responsePreprocessor(documentedHeaders),
        snippets.toTypedArray(),
    )
}

/**
 * `snippets(...)` 로 넘긴 REST Docs `ResponseHeadersSnippet` 이 문서화한 헤더 이름. 설명자 목록은 `protected` 라 리플렉션으로
 * 읽는다 (REST Docs 3·4 모두 `AbstractHeadersSnippet.getHeaderDescriptors()`). 읽지 못하면 그 스니펫은 없는 것으로 본다.
 */
private fun rawResponseHeaderNames(snippets: List<Snippet>): List<String> =
    snippets.filterIsInstance<ResponseHeadersSnippet>()
        .flatMap { snippet ->
            // 읽지 못하면(다른 REST Docs 버전) 그 헤더를 빼므로 REST Docs 가 "not found" 로 알려 준다
            runCatching { HEADER_DESCRIPTORS.invoke(snippet) as List<*> }
                .getOrDefault(emptyList<Any>())
                .filterIsInstance<HeaderDescriptor>()
                .map(HeaderDescriptor::getName)
        }

private val HEADER_DESCRIPTORS: Method by lazy {
    AbstractHeadersSnippet::class.java.getDeclaredMethod("getHeaderDescriptors")
        .apply { isAccessible = true }
}
