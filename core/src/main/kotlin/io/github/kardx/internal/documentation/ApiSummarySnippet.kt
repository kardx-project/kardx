package io.github.kardx.internal.documentation

import io.github.kardx.dsl.DEPRECATED_ATTRIBUTE
import io.github.kardx.dsl.DEPRECATION_NOTE_ATTRIBUTE
import io.github.kardx.internal.http.statusText
import io.github.kardx.internal.openapi.OpenApiPathTemplate
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.TemplatedSnippet

/**
 * 메서드, URL 템플릿, 응답 상태, 설명을 담은 `api-summary` 스니펫.
 * 같은 API 의 성공·오류 응답을 따로 문서화할 때 상태로 섹션을 구분할 수 있다.
 * 템플릿은 라이브러리에 포함된 `org/springframework/restdocs/templates/asciidoctor/api-summary.snippet` 이다.
 *
 * ```asciidoc
 * `GET /api/v1/users/{id}` → `200 OK`
 *
 * ID로 사용자 조회
 * ```
 *
 * 모델: `method`, `path`, `status` 는 항상, `description` 은 설명이 있을 때만, `deprecated`(true)와 `deprecationNote` 는
 * deprecated API 일 때만 넣는다. 그래서 이 키를 쓰지 않는 사용자 템플릿도 그대로 동작한다.
 *
 * @param deprecated true 면 템플릿이 `*Deprecated*` 줄을 쓴다 (설명보다 앞)
 * @param deprecationNote deprecated 의 이유·대안. `*Deprecated*: <note>`
 */
internal class ApiSummarySnippet(
    private val description: String?,
    private val deprecated: Boolean = false,
    private val deprecationNote: String? = null,
) : TemplatedSnippet(SNIPPET_NAME, null) {
    // REST Docs 4 는 반환된 모델에 속성을 더 넣으므로 가변 맵을 돌려줘야 한다.
    override fun createModel(operation: Operation): MutableMap<String, Any> = hashMapOf<String, Any>(
        "method" to operation.request.method.name(),
        "path" to urlTemplate(operation),
        "status" to status(operation),
    ).apply {
        description?.takeIf { it.isNotBlank() }
            ?.let { put("description", it) }

        if (deprecated) {
            put(DEPRECATED_ATTRIBUTE, true)
            deprecationNote?.takeIf { it.isNotBlank() }
                ?.let { put(DEPRECATION_NOTE_ATTRIBUTE, it) }
        }
    }

    /** `404 Not Found`. HttpStatusCode.value() 는 Spring 6·7 모두에 있다. */
    private fun status(operation: Operation): String = statusText(operation.response.status.value())

    /** 쿼리는 뗀다. 쿼리 파라미터는 `query-parameters` 스니펫이 보여 준다 */
    private fun urlTemplate(operation: Operation): String =
        (operation.attributes[RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE] as? String)
            ?.let(OpenApiPathTemplate::withoutQuery)
            ?: operation.request.uri.rawPath

    companion object {
        const val SNIPPET_NAME: String = "api-summary"
    }
}
