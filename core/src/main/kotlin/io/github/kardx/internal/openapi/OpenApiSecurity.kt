package io.github.kardx.internal.openapi

import io.github.kardx.dsl.DeclaredAuthentication
import io.github.kardx.internal.http.HttpHeadersCompat
import io.github.kardx.internal.reflect.overrideGetter
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.operation.OperationRequestFactory
import org.springframework.restdocs.snippet.Snippet
import java.util.Base64

/**
 * epages 가 OpenAPI 보안 스키마를 만들 수 있게 OpenAPI 조각(`resource.json`)에만 `Authorization` 값을 맞춘다.
 *
 * epages 는 실제 요청의 `Authorization` 이 `Basic ...` 이거나 해석 가능한 JWT `Bearer` 일 때만 보안 스키마를 만든다.
 * 마스킹(`Bearer ****`)이나 JWT 가 아닌 토큰이면 만들지 못하므로, `Bearer` 값을 형식만 맞춘 가짜 JWT 로 바꿔 넘긴다.
 * REST Docs 스니펫에는 원래(또는 마스킹한) 값이 그대로 남고, 실제 토큰은 OpenAPI 에 들어가지 않는다.
 *
 * `openApi { bearerAuth() }`·`basicAuth()` 로 선언하면([declared]) 요청에 헤더가 없어도 같은 방식으로 가짜 값을 넣는다.
 */
internal object OpenApiSecurity {
    private const val BEARER = "Bearer "

    private const val BASIC = "Basic "

    /** 보안 스키마 감지를 위한 가짜 JWT. 서명은 의미 없다. */
    private val PLACEHOLDER_JWT: String = listOf(
        """{"alg":"none","typ":"JWT"}""",
        """{"sub":"api-docs","scope":[]}""",
        "signature",
    ).joinToString(".") {
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(it.toByteArray())
    }

    /** 선언한 Basic 인증의 가짜 자격 증명 (`api-docs:api-docs`). */
    private val PLACEHOLDER_BASIC: String = Base64.getEncoder()
        .encodeToString("api-docs:api-docs".toByteArray())

    /** 보안 스키마가 만들어지도록 `Authorization` 을 epages 가 해석하는 형태로 맞춘 리소스 스니펫. */
    fun wrap(resource: Snippet): Snippet = Snippet { operation -> resource.document(withPlaceholderToken(operation)) }

    /** 보안 스키마를 만들지 않도록 `Authorization` 을 뺀 리소스 스니펫 (`openApi.securitySchemes = false`). */
    fun withoutSecurity(resource: Snippet): Snippet = Snippet { operation ->
        val hasAuth = HttpHeadersCompat.values(operation.request.headers, HttpHeaders.AUTHORIZATION)
            .isNotEmpty()

        resource.document(
            if (hasAuth) {
                withHeaders(operation) { name, value -> value.unlessAuthorization(name) }
            } else {
                operation
            },
        )
    }

    /**
     * [authentication] 의 가짜 `Authorization` 을 넣은 리소스 스니펫. 요청에 헤더가 없어도, 다른 방식의 헤더가 있어도
     * OpenAPI 조각에서는 선언한 방식의 보안 스키마가 생긴다.
     */
    fun declared(resource: Snippet, authentication: DeclaredAuthentication): Snippet = Snippet { operation ->
        val value = when (authentication) {
            DeclaredAuthentication.BEARER -> BEARER + PLACEHOLDER_JWT
            DeclaredAuthentication.BASIC -> BASIC + PLACEHOLDER_BASIC
        }

        resource.document(withAuthorization(operation, value))
    }

    private fun withPlaceholderToken(operation: Operation): Operation {
        val headers = operation.request.headers
        val auth = HttpHeadersCompat.values(headers, HttpHeaders.AUTHORIZATION)
            .firstOrNull() ?: return operation

        if (!auth.startsWith(BEARER, ignoreCase = true) || isJwt(auth.substring(BEARER.length))) {
            return operation
        }

        return withHeaders(operation) { name, value ->
            if (name.equals(HttpHeaders.AUTHORIZATION, ignoreCase = true)) {
                BEARER + PLACEHOLDER_JWT
            } else {
                value
            }
        }
    }

    private fun String.unlessAuthorization(name: String): String? =
        takeUnless { name.equals(HttpHeaders.AUTHORIZATION, ignoreCase = true) }

    /** `Authorization` 을 [value] 하나로 바꾼(없으면 더한) [Operation]. */
    private fun withAuthorization(operation: Operation, value: String): Operation {
        val headers = HttpHeadersCompat.copy(operation.request.headers) { name, header ->
            header.unlessAuthorization(name)
        }.apply { set(HttpHeaders.AUTHORIZATION, value) }

        return withRequestHeaders(operation, headers)
    }

    /** 요청 헤더만 바꾼 [Operation]. 나머지 호출은 원래 객체로 넘긴다. */
    private fun withHeaders(operation: Operation, replace: (name: String, value: String) -> String?): Operation =
        withRequestHeaders(operation, HttpHeadersCompat.copy(operation.request.headers, replace))

    private fun withRequestHeaders(operation: Operation, headers: HttpHeaders): Operation {
        val request = OperationRequestFactory().createFrom(operation.request, headers)

        return overrideGetter(Operation::class.java, operation, "getRequest", request)
    }

    /** 점으로 나뉜 세 조각이고 가운데가 JSON 객체인 Base64URL 이면 JWT. 디코딩에 실패하면 JWT 가 아니다. */
    private fun isJwt(token: String): Boolean = token.split('.').size == JWT_PARTS &&
        runCatching {
            Base64.getUrlDecoder()
                .decode(token.split('.')[1])
                .decodeToString()
                .trim()
                .startsWith("{")
        }.getOrDefault(false)

    private const val JWT_PARTS = 3
}
