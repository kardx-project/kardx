package io.github.kardx.asyncapi

/**
 * API 키([AsyncApiSecurityType.API_KEY])를 보내는 자리. AsyncAPI `httpApiKey` 의 `in` 값이 된다.
 *
 * ```kotlin
 * val scheme = AsyncApiSecurityScheme.apiKey("apiKeyAuth", AsyncApiApiKeyLocation.HEADER, "X-API-KEY")
 * check(scheme.location?.value == "header")
 * ```
 *
 * @property value AsyncAPI 문서와 명령행(`--security name=apiKey:<value>:<name>`)에 쓰는 이름
 */
public enum class AsyncApiApiKeyLocation(public val value: String) {
    /** 요청 헤더 (`in: header`). */
    HEADER("header"),

    /** 쿼리 파라미터 (`in: query`). */
    QUERY("query"),

    /** 쿠키 (`in: cookie`). */
    COOKIE("cookie"),
}
