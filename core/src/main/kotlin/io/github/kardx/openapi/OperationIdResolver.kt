package io.github.kardx.openapi

/**
 * operationId 를 직접 만드는 규칙. [io.github.kardx.config.RestDocsSettings] 의 `openApi.operationIdResolver` 로 정한다.
 * 요청마다 [OperationIdInput] 을 받아 id 를 돌려준다. `null` 을 돌려주면 `openApi.operationIdRule` 로 만든다.
 * 돌려준 id 는 그대로 쓰이므로(겹치면 Gradle 빌드 경고) 겹치지 않게 만든다. 비어 있거나 공백이 있으면 문서화가 실패한다.
 *
 * ```kotlin
 * openApi {
 *     operationIdResolver = OperationIdResolver { input ->
 *         input.handlerMethodName?.let { "${input.httpMethod.lowercase()}_$it" }
 *     }
 * }
 * ```
 */
public fun interface OperationIdResolver {
    /** [input] 요청의 operationId. 규칙에 맡기려면 null. */
    public fun resolve(input: OperationIdInput): String?
}
