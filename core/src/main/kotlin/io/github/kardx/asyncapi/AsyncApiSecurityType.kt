package io.github.kardx.asyncapi

/**
 * [AsyncApiSecurityScheme] 의 인증 방식. AsyncAPI 3.0 `securitySchemes` 의 `type`(과 `scheme`)으로 이렇게 옮긴다.
 *
 * | 값 | AsyncAPI | 자격 증명 위치 |
 * |---|---|---|
 * | [BEARER] | `type: http`, `scheme: bearer` | `Authorization: Bearer <token>` |
 * | [BASIC] | `type: http`, `scheme: basic` | `Authorization: Basic <base64(user:password)>` |
 * | [API_KEY] | `type: httpApiKey`, `in`, `name` | [AsyncApiApiKeyLocation] 이 정한 헤더·쿼리·쿠키 |
 *
 * ```kotlin
 * val scheme = AsyncApiSecurityScheme.bearer("bearerAuth")
 * check(scheme.type == AsyncApiSecurityType.BEARER)
 * ```
 */
public enum class AsyncApiSecurityType {
    /** HTTP Bearer 토큰 (`type: http`, `scheme: bearer`). */
    BEARER,

    /** HTTP Basic (`type: http`, `scheme: basic`). */
    BASIC,

    /** 헤더·쿼리·쿠키로 보내는 API 키 (`type: httpApiKey`). */
    API_KEY,
}
