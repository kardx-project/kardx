package io.github.kardx.config

/**
 * 가린 값을 문서에 어떻게 남길지 정하는 규칙. [RestDocsSettings] 의 `masking.masker` 로 정한다.
 * 정하지 않으면 값을 `masking.replacement` (기본값 `****`)로 바꾸고, 인증 헤더는 방식을 남긴다 (`Bearer ****`).
 *
 * 헤더(`masking.headers`)뿐 아니라 쿠키, 쿼리·폼 파라미터(`masking.queryParameters`, `masking.maskSensitive()` 가 켜는
 * `access_token`·`password` …)에도 불린다. 어디의 값인지는 [MaskTarget.location] 으로 가른다.
 *
 * ```kotlin
 * // 인증 헤더는 방식만 남기고("Bearer ****"), 그 밖의 헤더·쿠키·파라미터는 통째로 가린다
 * masking {
 *     masker = ValueMasker { target ->
 *         if (target.location == MaskLocation.HEADER && target.name.equals("Authorization", ignoreCase = true)) {
 *             target.value.substringBefore(' ') + " ****"
 *         } else {
 *             "****"
 *         }
 *     }
 * }
 * ```
 */
public fun interface ValueMasker {
    /** [target] 값 대신 문서에 남길 값. */
    public fun mask(target: MaskTarget): String
}

/**
 * [ValueMasker] 가 받는 입력. 라이브러리가 만들어 넘기며, 앞으로 정보가 더해질 수 있다.
 *
 * @property location 값이 있는 곳
 * @property name 헤더 이름(`Authorization`, 쿠키면 `Cookie`·`Set-Cookie`) 또는 쿼리·폼 파라미터 이름(`access_token`)
 * @property value 원래 값 (쿠키면 쿠키 하나의 값, 파라미터면 디코딩한 값 하나)
 * @property cookieName 쿠키 이름. [location] 이 [MaskLocation.COOKIE] 일 때만 있다. 쿠키 이름과 속성(`Path` …)은 그대로 남는다
 */
public class MaskTarget internal constructor(
    public val location: MaskLocation,
    public val name: String,
    public val value: String,
    public val cookieName: String? = null,
) {
    override fun toString(): String = "MaskTarget($location $name${cookieName?.let { " ($it)" }.orEmpty()})"
}

/** 가릴 값이 있는 곳. 상수가 더해질 수 있으므로 `when` 에는 `else` 를 둔다. */
public enum class MaskLocation {
    /** 요청·응답 헤더 (`Authorization`), 메시지 헤더 */
    HEADER,

    /** `Cookie`·`Set-Cookie` 헤더 안의 쿠키 하나 */
    COOKIE,

    /** 요청 URI 의 쿼리, 폼 본문, multipart 의 파일이 아닌 파트 */
    PARAMETER,
}
