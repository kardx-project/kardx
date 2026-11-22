package io.github.kardx.internal.http

import io.github.kardx.config.MaskLocation
import io.github.kardx.config.MaskTarget
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.ValueMasker

/** 지금 설정으로 헤더를 가린다. HTTP 스니펫, 메시지 스니펫, AsyncAPI 가 같은 규칙을 쓴다. */
internal object HeaderMasking {
    fun isMasked(headerName: String): Boolean =
        RestDocsConfiguration.current.masking.headers.any { it.equals(headerName, ignoreCase = true) }

    /** 가릴 헤더면 가린 값, 아니면 원래 값. */
    fun mask(headerName: String, value: String): String =
        if (isMasked(headerName)) maskValue(headerName, value) else value

    /** 설정과 상관없이 가린다. 배포되는 산출물(AsyncAPI 문서·뷰어)에 토큰이 남지 않게 민감 헤더에 쓴다. */
    fun maskAlways(headerName: String, value: String): String = maskValue(headerName, value)

    /** 쿼리·폼 파라미터 하나의 값을 가린다. 마스커는 파라미터 이름으로 부른다. */
    fun maskParameter(parameterName: String, value: String): String =
        masker().mask(MaskTarget(MaskLocation.PARAMETER, parameterName, value))

    /** 쿠키 [cookieName] 하나의 값을 가린다. [headerName] 은 `Cookie`·`Set-Cookie`. */
    fun maskCookie(headerName: String, cookieName: String, value: String): String =
        masker().mask(MaskTarget(MaskLocation.COOKIE, headerName, value, cookieName))

    private fun maskValue(headerName: String, value: String): String = when {
        headerName.equals(COOKIE, ignoreCase = true) ->
            value.split(';')
                .joinToString("; ") { cookie ->
                    val name = cookie.substringBefore('=')
                        .trim()
                    val cookieValue = cookie.substringAfter('=', "")
                        .trim()

                    "$name=${maskCookie(headerName, name, cookieValue)}"
                }

        headerName.equals(SET_COOKIE, ignoreCase = true) -> {
            val first = value.substringBefore(';')
            val name = first.substringBefore('=')
                .trim()
            val firstValue = first.substringAfter('=', "")
                .trim()
            val maskedFirst = "$name=${maskCookie(headerName, name, firstValue)}"
            val attributes = value.split(';')
                .drop(1)
                .map(String::trim)

            (listOf(maskedFirst) + attributes).joinToString("; ")
        }

        else -> maskAuthorization(headerName, value)
            ?: masker().mask(MaskTarget(MaskLocation.HEADER, headerName, value))
    }

    /**
     * 사용자 마스커가 없으면 인증 헤더는 방식(`Bearer`, `Basic`)을 남기고 값만 가린다: `Bearer ****`.
     * 문서를 읽는 사람에게 인증 방식을 알려 주고, OpenAPI 생성기가 인증 방식을 알아볼 수 있게 한다.
     */
    private fun maskAuthorization(headerName: String, value: String): String? {
        val settings = RestDocsConfiguration.current

        if (settings.masking.masker != null || AUTH_HEADERS.none { it.equals(headerName, ignoreCase = true) }) {
            return null
        }

        val scheme = value.substringBefore(' ', "")
            .takeIf { it.isNotBlank() && ' ' in value } ?: return null

        return "$scheme ${settings.masking.replacement}"
    }

    private fun masker(): ValueMasker {
        val settings = RestDocsConfiguration.current

        return settings.masking.masker ?: ValueMasker { settings.masking.replacement }
    }

    private val AUTH_HEADERS = listOf("Authorization", "Proxy-Authorization")
    private const val COOKIE = "Cookie"
    private const val SET_COOKIE = "Set-Cookie"
}
