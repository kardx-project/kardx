package io.github.kardx.dsl

/**
 * `openApi { bearerAuth() }`·`basicAuth()` 로 선언한 인증 방식. 요청에 `Authorization` 헤더가 없어도 OpenAPI 조각에만
 * 그 방식의 가짜 값을 넣어 epages 가 보안 스키마를 만들게 한다 ([io.github.kardx.internal.openapi.OpenApiSecurity]).
 */
internal enum class DeclaredAuthentication {
    /** `Authorization: Bearer <JWT>`. 보안 스키마 `bearerAuthJWT` (`type: http`, `scheme: bearer`, `bearerFormat: JWT`) */
    BEARER,

    /** `Authorization: Basic ...`. 보안 스키마 `basic` (`type: http`, `scheme: basic`) */
    BASIC,
}
