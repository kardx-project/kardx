package io.github.kardx.mockmvc

import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.internal.documentation.StreamResultActions
import io.github.kardx.internal.documentation.httpDocumentation
import io.github.kardx.internal.documentation.withRestDocsConfiguration
import io.github.kardx.internal.output.SnippetPaths.DOCUMENT_IDENTIFIER
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.test.web.servlet.ResultActions
import org.springframework.web.servlet.DispatcherServlet

/**
 * [ResultActions] 를 Spring REST Docs 로 문서화한다.
 *
 * 블록에서 지정한 항목마다 표준 스니펫을 만들고, 항상 `api-summary` 스니펫(메서드·URL 템플릿·응답 상태와 설명)을 만든다.
 * epages restdocs-api-spec 이 클래스패스에 있고 [io.github.kardx.config.OpenApiSettings.enabled] 이면
 * OpenAPI 리소스(`resource.json`)도 만든다. 이때 테스트 클래스·메서드의 `@DisplayName` 을 tag·summary 로 쓴다.
 * [io.github.kardx.config.OutputSettings.output.wireMockStubs] 이면 WireMock 매핑(`wiremock/mappings/…json`)도 만든다.
 *
 * ```kotlin
 * mockMvc.perform(get("/api/users/{id}", 1L))
 *     .andStatus(HttpStatus.OK)
 *     .andDocument {
 *         description = "ID로 사용자 조회"
 *         pathParameters("id" pathMeans "사용자 ID")
 *         responseHeaders("ETag" headerMeans "리소스 버전")
 *         responseFields<UserResponse>()
 *     }
 * ```
 */
public fun ResultActions.andDocument(block: DocumentationBuilder.() -> Unit = {}): ResultActions {
    val result = andReturn()
    val parts = httpDocumentation(
        block,
        result.response.status,
        stream = this is StreamResultActions,
        handler = result.handler,
        rawResponseBody = { result.response.contentAsByteArray },
        applicationContext = result.request.getAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE),
    )

    val documentation = MockMvcRestDocumentation.document(
        DOCUMENT_IDENTIFIER,
        parts.requestPreprocessor,
        parts.responsePreprocessor,
        *parts.snippets,
    )

    return withRestDocsConfiguration("MockMvc", MOCK_MVC_FIX) { andDo(documentation) }
}

private const val MOCK_MVC_FIX = "Take it as a test method parameter (fun getUser(mockMvc: MockMvc)) with " +
    "AutoRestDocumentationExtension, or build it with restDocsMockMvc(context) or restDocsMockMvc(context, provider)."
