package io.github.kardx.asyncapi

import java.util.Objects

/**
 * AsyncAPI 인증 방식 하나. 문서의 `components.securitySchemes.<name>` 이 되고, 모든 서버의 `security` 가 이것을 가리킨다.
 * 서버의 `security` 목록은 "이 중 하나로 인증한다" 는 뜻이다. 명령행에서는 `--security` 로 정한다.
 *
 * ```kotlin
 * val info = AsyncApiInfo(
 *     title = "Chat",
 *     version = "1.0.0",
 *     servers = listOf(AsyncApiServer("stomp", "localhost:8080", "stomp", "/ws")),
 *     securitySchemes = listOf(
 *         AsyncApiSecurityScheme.bearer("bearerAuth"),
 *         AsyncApiSecurityScheme.apiKey("apiKeyAuth", AsyncApiApiKeyLocation.HEADER, "X-API-KEY"),
 *     ),
 * )
 * ```
 *
 * @property name `securitySchemes` 의 키이자 서버가 `#/components/securitySchemes/<name>` 으로 가리키는 이름
 * @property type 인증 방식
 * @property location API 키를 보내는 자리. [AsyncApiSecurityType.API_KEY] 일 때만 있고, 그 밖에는 null
 * @property parameterName API 키를 담는 헤더·쿼리 파라미터·쿠키 이름. [AsyncApiSecurityType.API_KEY] 일 때만 있다
 */
public class AsyncApiSecurityScheme private constructor(
    public val name: String,
    public val type: AsyncApiSecurityType,
    public val location: AsyncApiApiKeyLocation?,
    public val parameterName: String?,
) {
    init {
        require(name.isNotBlank()) { "security scheme name must not be blank" }
    }

    override fun equals(other: Any?): Boolean = other is AsyncApiSecurityScheme &&
        name == other.name &&
        type == other.type &&
        location == other.location &&
        parameterName == other.parameterName

    override fun hashCode(): Int = Objects.hash(name, type, location, parameterName)

    override fun toString(): String =
        "AsyncApiSecurityScheme(name=$name, type=$type, location=$location, parameterName=$parameterName)"

    /**
     * 인증 방식을 만드는 함수.
     *
     * ```kotlin
     * val bearer = AsyncApiSecurityScheme.bearer("bearerAuth")
     * val basic = AsyncApiSecurityScheme.basic("basicAuth")
     * val apiKey = AsyncApiSecurityScheme.apiKey("apiKeyAuth", AsyncApiApiKeyLocation.QUERY, "api_key")
     * ```
     */
    public companion object {
        /**
         * `Authorization: Bearer <token>` 으로 보내는 토큰.
         *
         * ```kotlin
         * val scheme = AsyncApiSecurityScheme.bearer("bearerAuth")
         * ```
         */
        public fun bearer(name: String): AsyncApiSecurityScheme =
            AsyncApiSecurityScheme(name, AsyncApiSecurityType.BEARER, null, null)

        /**
         * `Authorization: Basic <base64(user:password)>` 로 보내는 사용자 이름·비밀번호.
         *
         * ```kotlin
         * val scheme = AsyncApiSecurityScheme.basic("basicAuth")
         * ```
         */
        public fun basic(name: String): AsyncApiSecurityScheme =
            AsyncApiSecurityScheme(name, AsyncApiSecurityType.BASIC, null, null)

        /**
         * [location] 의 [parameterName] 으로 보내는 API 키.
         *
         * ```kotlin
         * val scheme = AsyncApiSecurityScheme.apiKey("apiKeyAuth", AsyncApiApiKeyLocation.HEADER, "X-API-KEY")
         * ```
         */
        public fun apiKey(
            name: String,
            location: AsyncApiApiKeyLocation,
            parameterName: String,
        ): AsyncApiSecurityScheme {
            require(parameterName.isNotBlank()) { "API key parameter name must not be blank" }

            return AsyncApiSecurityScheme(name, AsyncApiSecurityType.API_KEY, location, parameterName)
        }
    }
}
