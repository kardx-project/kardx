package io.github.kardx.config

import kotlin.reflect.KClass

/**
 * 문서에서 값을 가리는 방식 (헤더, 쿠키, 쿼리·폼 파라미터, 본문 필드 표시).
 * [RestDocsSettings.masking] 로 읽고 `masking { }` 블록으로 바꾼다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     masking { maskSensitive() }
 * }
 * ```
 */
@RestDocsSettingsDsl
public class MaskingSettings internal constructor() {
    /**
     * 필드 값이 마스킹되어 나간다는 표시로 읽을 어노테이션 (값을 바꾸지 않는 문서 표시).
     * [@Masked][io.github.kardx.annotation.Masked] 는 이 목록과 상관없이 항상 읽고, 그다음 이 순서로 찾는다.
     * 붙은 본문 필드는 AsciiDoc 필드 표에 `*Masked*`, OpenAPI·AsyncAPI 스키마에 `x-masked: true` 로 표시된다.
     * yml 로는 `kardx.masked-annotations` (예: `com.example.MaskName, com.example.Masking#rule`).
     * 기본값은 빈 목록.
     *
     * ```kotlin
     * annotation("com.example.MaskName")               // yml 과 같은 문법: FQCN, FQCN#attribute
     * annotation(Masking::class, "rule")               // rule 속성 값(String·enum)을 규칙 설명으로
     * annotations = emptyList()                        // 목록을 통째로 바꾼다
     * ```
     */
    public var annotations: List<MaskedAnnotation> = emptyList()
        set(value) {
            field = value.toList()
        }

    /** [spec] (`FQCN`, `FQCN#attribute`) 을 [annotations] 끝에 더한다. */
    public fun annotation(spec: String) {
        annotations = annotations + MaskedAnnotation.parse(spec)
    }

    /** [annotationClass] 를 [annotations] 끝에 더한다. [attribute] 를 주면 그 속성 값이 규칙 설명이 된다. */
    public fun annotation(annotationClass: KClass<out Annotation>, attribute: String? = null) {
        annotations = annotations + MaskedAnnotation.of(annotationClass, attribute)
    }

    /**
     * 문서에서 값을 가릴 헤더 이름 (대소문자 무시). 기본값은 비어 있어 아무것도 가리지 않는다.
     * 헤더 자체는 남으므로 `requestHeaders` 로 문서화할 수 있다. 스니펫(curl, http-request 등)과 OpenAPI 예시,
     * 메시지 문서, AsyncAPI 예시에 적용된다.
     * yml: `kardx.masked-headers`, `kardx.mask-sensitive-headers: true`.
     *
     * ```kotlin
     * maskSensitive()                                  // Authorization, Cookie 등 흔한 민감 헤더
     * headers = setOf("Authorization", "X-Api-Key")     // 직접 지정
     * ```
     */
    public var headers: Set<String> = emptySet()
        set(value) {
            field = value.toSet()
        }

    /**
     * 가린 값 대신 기록할 문자열. 기본값 `****`. [masker] 를 정하면 쓰지 않는다.
     * `Authorization`·`Proxy-Authorization` 은 방식을 남겨 `Bearer ****` 처럼, 쿠키는 이름을 남기고 값만 바꾼다.
     * yml: `kardx.masked-header-value`.
     */
    public var replacement: String = "****"

    /**
     * 가리는 방식. `null` 이면 값을 [replacement] 로 바꾼다. 코드로만 정한다.
     * [headers] 의 헤더, [queryParameters] 의 쿼리·폼 파라미터, 설정과 상관없이 가리는 AsyncAPI 예시의 민감 헤더
     * ([SENSITIVE_HEADERS])에 쓴다. 값이 어디(헤더·쿠키·파라미터)에 있는지는 [MaskTarget.location] 으로 받는다.
     *
     * ```kotlin
     * masker = ValueMasker { target -> if (target.location == MaskLocation.PARAMETER) "<hidden>" else "****" }
     * ```
     */
    public var masker: ValueMasker? = null

    /**
     * 문서에서 값을 가릴 쿼리·폼 파라미터 이름 (대소문자 무시). 기본값은 비어 있어 아무것도 가리지 않는다.
     * 브라우저 `EventSource`·WebSocket 처럼 헤더를 보낼 수 없는 클라이언트는 토큰을 쿼리(`?access_token=`)로 보내고,
     * OAuth 토큰 요청·로그인 폼은 `password`·`client_secret` 을 `application/x-www-form-urlencoded` 본문으로 보낸다.
     * 요청 URI 의 쿼리, 폼 본문, multipart 의 파일이 아닌 파트에서 이 이름의 값을 가린다.
     * 파라미터 이름은 남기고 값만 [replacement] 로 바꾼다 ([masker] 가 있으면 [MaskLocation.PARAMETER] 로 부른다).
     * 스니펫(curl, httpie, http-request, request-body 등)과 OpenAPI 예시, WireMock 매핑(가린 쿼리 파라미터는 아무 값이나 맞춘다)에
     * 적용된다. JSON 본문의 필드는 `@Masked` 로 가린다. [maskSensitive] 가 [SENSITIVE_QUERY_PARAMETERS] 를 더한다.
     * yml: `kardx.masked-query-parameters`.
     *
     * ```kotlin
     * queryParameters = setOf("access_token", "sig")
     * ```
     */
    public var queryParameters: Set<String> = emptySet()
        set(value) {
            field = value.toSet()
        }

    /**
     * [SENSITIVE_HEADERS] 를 [headers] 에, [SENSITIVE_QUERY_PARAMETERS] 를 [queryParameters] 에 더한다.
     * 헤더뿐 아니라 쿼리·폼 파라미터의 자격증명(`access_token`, `password`, `client_secret` …)도 가린다.
     * yml: `kardx.mask-sensitive-headers: true`.
     */
    public fun maskSensitive() {
        headers = headers + SENSITIVE_HEADERS
        queryParameters = queryParameters + SENSITIVE_QUERY_PARAMETERS
    }

    internal fun copy(): MaskingSettings = MaskingSettings().also {
        it.annotations = annotations
        it.headers = headers
        it.replacement = replacement
        it.masker = masker
        it.queryParameters = queryParameters
    }

    /** 가리기 기본값 상수. */

    public companion object {
        /** 흔한 민감 헤더. [maskSensitive] 가 쓴다. */
        public val SENSITIVE_HEADERS: Set<String> =
            setOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie")

        /** 흔히 자격증명을 담는 쿼리·폼 파라미터. [maskSensitive] 가 [queryParameters] 에 더한다. */
        public val SENSITIVE_QUERY_PARAMETERS: Set<String> = setOf(
            "access_token",
            "id_token",
            "refresh_token",
            "token",
            "api_key",
            "apikey",
            "client_secret",
            "client_assertion",
            "password",
            "passwd",
            "current_password",
            "new_password",
            "signature",
            "sig",
            "X-Amz-Signature",
            "X-Amz-Security-Token",
            "X-Goog-Signature",
        )
    }
}
