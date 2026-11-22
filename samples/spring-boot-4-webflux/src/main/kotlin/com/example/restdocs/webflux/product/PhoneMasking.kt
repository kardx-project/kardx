package com.example.restdocs.webflux.product

private const val PHONE_PARTS = 3

/**
 * [ProductResponse.sellerPhone] 의 `@Masked` 규칙대로 가린다: 가운데 자리를 `*`.
 * 예: `010-1234-5678` → `010-****-5678`. `-` 로 나뉜 세 부분이 아니면 그대로 둔다.
 */
internal fun String.maskPhone(): String {
    val parts = split('-')

    if (parts.size != PHONE_PARTS) {
        return this
    }

    return parts.mapIndexed { index, part -> if (index == 1) "*".repeat(part.length) else part }
        .joinToString("-")
}
