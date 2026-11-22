package io.github.kardx.asyncapi

/**
 * [AsyncApiSecurityScheme] → AsyncAPI 3.0 Security Scheme Object 와, 서버 `security` 에 넣는 참조.
 *
 * 3.0 의 서버 `security` 는 2.x 의 요구 사항 맵(`{name: []}`)이 아니라 Security Scheme Object(또는 `$ref`)의 목록이다.
 * HTTP 위의 Basic 은 `userPassword`(브로커 사용자·비밀번호)가 아니라 `http` + `scheme: basic` 이다.
 */
internal object AsyncApiSecuritySchemes {
    private const val TYPE = "type"
    private const val SCHEME = "scheme"
    private const val HTTP = "http"

    /** `components` 의 `securitySchemes` 항목. 인증 방식이 없으면 빈 맵이다. */
    fun components(schemes: List<AsyncApiSecurityScheme>): Map<String, Any?> {
        if (schemes.isEmpty()) return emptyMap()

        return linkedMapOf("securitySchemes" to schemes.associate { it.name to scheme(it) })
    }

    private fun scheme(scheme: AsyncApiSecurityScheme): Map<String, Any?> = when (scheme.type) {
        AsyncApiSecurityType.BEARER -> linkedMapOf(TYPE to HTTP, SCHEME to "bearer")

        AsyncApiSecurityType.BASIC -> linkedMapOf(TYPE to HTTP, SCHEME to "basic")

        AsyncApiSecurityType.API_KEY -> linkedMapOf(
            TYPE to "httpApiKey",
            "name" to scheme.parameterName,
            "in" to scheme.location?.value,
        )
    }

    fun reference(scheme: AsyncApiSecurityScheme): Map<String, String> =
        mapOf("\$ref" to "#/components/securitySchemes/${scheme.name}")
}
