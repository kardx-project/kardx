package com.example.restdocs.controller

private const val MIN_MASKABLE_ID_LENGTH = 3

/**
 * [com.example.restdocs.dto.UserResponse.email] 의 `@Masked` 규칙대로 가린다: 아이디의 첫 글자와 끝 글자만 남기고 `*`.
 * 예: `kim@example.com` → `k*m@example.com`. 아이디가 두 글자 이하면 가릴 글자가 없어 그대로 둔다.
 */
internal fun String.maskEmail(): String {
    val at = indexOf('@')

    if (at < MIN_MASKABLE_ID_LENGTH) {
        return this
    }

    return "${first()}${"*".repeat(at - 2)}${substring(at - 1)}"
}
