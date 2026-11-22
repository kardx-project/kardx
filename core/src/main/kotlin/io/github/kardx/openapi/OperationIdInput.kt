package io.github.kardx.openapi

/**
 * operationId 를 직접 정하는 규칙([OperationIdResolver])이 받는 입력. 문서화한 요청 하나의 정보다.
 * 라이브러리가 만들어 넘기므로 직접 만들 수 없고, 앞으로 정보가 더해질 수 있다.
 *
 * ```kotlin
 * operationIdResolver = OperationIdResolver { input ->
 *     input.handlerMethodName?.let { "${input.controllerName?.removeSuffix("Controller")?.lowercase()}_$it" }
 * }
 * ```
 *
 * @property httpMethod HTTP 메서드 (대문자, `GET`)
 * @property path OpenAPI 경로 템플릿 (`/api/v1/users/{id}`). 경로 변수의 정규식과 쿼리는 뗀 값이다
 * @property controllerName 요청을 처리한 컨트롤러 클래스의 단순 이름 (`UserController`). 핸들러를 알 수 없으면 null
 * @property handlerMethodName 요청을 처리한 핸들러 메서드 이름 (`searchUsers`). 핸들러를 알 수 없으면 null
 * @property testBasedId 테스트 클래스·메서드 이름으로 만든 id ([OperationIdRule.TEST_NAME] 규칙의 값, `user-api-docs-test.search-users`)
 */
public class OperationIdInput internal constructor(
    public val httpMethod: String,
    public val path: String,
    public val controllerName: String?,
    public val handlerMethodName: String?,
    public val testBasedId: String,
) {
    override fun toString(): String =
        "OperationIdInput($httpMethod $path, handler=$controllerName.$handlerMethodName, test=$testBasedId)"
}
