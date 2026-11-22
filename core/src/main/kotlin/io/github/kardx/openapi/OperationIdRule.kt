package io.github.kardx.openapi

/**
 * OpenAPI operationId 를 만드는 규칙. [io.github.kardx.config.OpenApiSettings.operationIdRule] 로 고른다
 * (yml: `kardx.open-api.operation-id-rule` 에 [propertyValue]). 기본값 [HANDLER_METHOD].
 *
 * 테스트에서 `openApi { operationId = "..." }` 로 정한 id 와
 * [io.github.kardx.config.OpenApiSettings.operationIdResolver] 가 돌려준 id 가 이 규칙보다 우선한다.
 * 규칙으로 만든 id 가 다른 operation(HTTP 메서드 + 경로)과 겹치면 Gradle 플러그인이 스펙을 만들 때 나눈다
 * ([HANDLER_METHOD] 는 겹친 것만 컨트롤러 이름을 붙이고, 그래도 겹치면 뒤에 숫자). 결과는 생성된 OpenAPI 의 `operationId` 와
 * Swagger UI·코드 생성기의 메서드 이름에 나타난다.
 *
 * | 규칙 | `UserController.searchUsers` 가 처리한 `GET /api/v1/users/search` |
 * |---|---|
 * | [HANDLER_METHOD] | `searchUsers` |
 * | [CONTROLLER_AND_METHOD] | `userSearchUsers` |
 * | [PATH] | `getApiV1UsersSearch` |
 * | [TEST_NAME] | `user-api-docs-test.search-users` (테스트 클래스·메서드 이름) |
 *
 * @property propertyValue yml 에 쓰는 값
 */
public enum class OperationIdRule(public val propertyValue: String) {
    /**
     * 요청을 처리한 핸들러(컨트롤러) 메서드 이름 (`searchUsers`). 기본값.
     * 다른 operation 과 겹친 id 만 컨트롤러 이름을 앞에 붙인다 (`UserController.create` → `userCreate`).
     * 핸들러를 알 수 없으면(함수형 라우터, `bindToServer` 로 만든 WebTestClient 등) [TEST_NAME] 으로 만든다.
     */
    HANDLER_METHOD("handler-method"),

    /**
     * 컨트롤러 이름 + 핸들러 메서드 이름 (`userSearchUsers`). 컨트롤러 이름에서 `RestController`·`Controller`·`Api` 접미사는 뗀다.
     * 핸들러를 알 수 없으면 [TEST_NAME] 으로 만든다.
     */
    CONTROLLER_AND_METHOD("controller-and-method"),

    /**
     * HTTP 메서드 + 경로 템플릿의 camelCase (`GET /api/v1/users/{id}` → `getApiV1UsersById`). 경로 변수는 `By` + 변수 이름이다.
     * 영문·숫자만 쓰므로 핸들러를 알 수 없는 API 도 ASCII id 가 된다.
     */
    PATH("path"),

    /**
     * 테스트 클래스·메서드 이름 (스니펫 디렉터리 `class-name/method-name` 의 `/` 를 `.` 로). 이전 버전의 동작이다.
     * Kotest 처럼 테스트 이름이 한글이면 id 에도 한글이 들어가 코드 생성기가 메서드 이름을 만들지 못할 수 있다.
     */
    TEST_NAME("test-name"),
    ;

    /** yml 값으로 규칙을 찾는다. */

    public companion object {
        /** yml 값([propertyValue], 대소문자·`_`/`-` 무시) → 규칙. 없는 값이면 null. */
        public fun fromPropertyValue(value: String): OperationIdRule? {
            val normalized = value.trim()
                .replace('_', '-')

            return entries.firstOrNull { it.propertyValue.equals(normalized, ignoreCase = true) }
        }
    }
}
