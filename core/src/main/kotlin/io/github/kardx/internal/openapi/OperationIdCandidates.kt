package io.github.kardx.internal.openapi

import io.github.kardx.config.RestDocsSettings
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.openapi.OperationIdInput
import io.github.kardx.openapi.OperationIdRule
import io.github.kardx.openapi.isValidOperationId
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.operation.Operation

/**
 * 문서화한 요청 하나의 operationId 후보. [OpenApiOperationExtras] 가 `openapi-extras.json` 의 [KEY] 로 남기고, Gradle 플러그인
 * (`OpenApiOperationIds`)이 스펙의 모든 operation 을 보고 최종 id 를 정한다 (겹침 처리는 스펙 전체를 봐야 하므로 플러그인에서 한다).
 *
 * 우선순위: 테스트의 `openApi { operationId }` > [io.github.kardx.config.OpenApiSettings.operationIdResolver] > [io.github.kardx.config.OpenApiSettings.operationIdRule].
 * 앞의 둘은 그대로 쓰는 고정 id(`fixed`)다. 규칙의 결과는 `id` 와, 겹칠 때 쓸 `qualified`(컨트롤러 이름을 붙인 id)다.
 * 후보가 없으면(규칙이 `test-name` 이거나 핸들러를 알 수 없음) 플러그인이 테스트 이름으로 만든다.
 */
internal object OperationIdCandidates {
    /** `openapi-extras.json` 의 키. 플러그인의 `OpenApiResources.Resource.operationIdCandidate` 와 같다 */
    const val KEY: String = "operationId"

    private const val ID = "id"
    private const val QUALIFIED = "qualified"
    private const val FIXED = "fixed"

    /**
     * @param explicit 테스트에서 정한 id (`openApi { operationId }`). 이미 검사한 값이다
     * @param handler 요청을 처리한 핸들러. 알 수 없으면 null
     */
    fun of(
        operation: Operation,
        explicit: String?,
        handler: DocumentedHandler?,
        settings: RestDocsSettings,
    ): Map<String, Any>? {
        if (explicit != null) {
            return fixed(explicit)
        }

        val method = operation.request.method.name()
        val path = pathTemplate(operation)
        val resolved = settings.openApi.operationIdResolver?.resolve(
            OperationIdInput(
                httpMethod = method,
                path = path,
                controllerName = handler?.controllerName,
                handlerMethodName = handler?.methodName,
                testBasedId = OperationIdNames.testBased(documentIdentifier(operation)),
            ),
        )

        if (resolved != null) {
            check(isValidOperationId(resolved)) {
                RestDocsErrorCode.INVALID_RESOLVED_OPERATION_ID.message(
                    "openApi.operationIdResolver returned an invalid operationId '$resolved' for " +
                        "$method $path. An operationId must not be empty or contain whitespace.",
                )
            }

            return fixed(resolved)
        }

        return ruleCandidate(settings.openApi.operationIdRule, method, path, handler)
    }

    private fun ruleCandidate(
        rule: OperationIdRule,
        method: String,
        path: String,
        handler: DocumentedHandler?,
    ): Map<String, Any>? = when (rule) {
        OperationIdRule.HANDLER_METHOD -> handler?.let {
            linkedMapOf(
                ID to it.methodName,
                QUALIFIED to OperationIdNames.controllerAndMethod(it.controllerName, it.methodName),
            )
        }

        OperationIdRule.CONTROLLER_AND_METHOD -> handler?.let {
            linkedMapOf(ID to OperationIdNames.controllerAndMethod(it.controllerName, it.methodName))
        }

        OperationIdRule.PATH -> linkedMapOf(ID to OperationIdNames.path(method, path))

        OperationIdRule.TEST_NAME -> null
    }

    private fun fixed(id: String): Map<String, Any> = linkedMapOf(ID to id, FIXED to true)

    /** `{class-name}/{method-name}` 을 바꾼 문서 식별자. epages 의 resource.json operationId 와 같다. */
    private fun documentIdentifier(operation: Operation): String = SnippetPaths.documentationContext(operation)
        ?.let { SnippetPaths.documentIdentifier(operation, it) }
        ?: operation.name

    /** epages 와 같은 OpenAPI 경로: REST Docs URL 템플릿에서 정규식과 쿼리를 뗀 값. 템플릿이 없으면 요청 경로. */
    private fun pathTemplate(operation: Operation): String {
        val template = operation.attributes[RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE] as? String
            ?: operation.request.uri.rawPath

        return OpenApiPathTemplate.withoutPatterns(template)
    }
}
