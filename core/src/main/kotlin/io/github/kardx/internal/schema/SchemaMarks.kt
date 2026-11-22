package io.github.kardx.internal.schema

/**
 * 마스킹되는 필드를 스키마에 나타내는 규칙. Gradle 플러그인의 OpenAPI 후처리(`DescriptorHints`)도 같은 문구·키를 쓴다.
 * - 설명 끝: `Masked` 또는 `Masked: <규칙>`
 * - 확장 속성: `x-masked: true`, 규칙이 있으면 `x-masked-rule: <규칙>`
 */
internal object SchemaMarks {
    const val MASKED: String = "x-masked"
    const val MASKED_RULE: String = "x-masked-rule"
    private const val LABEL = "Masked"

    /** 설명 끝에 붙일 문구. [rule] 이 비었으면 `Masked` 만. */
    fun maskedNote(rule: String): String = if (rule.isBlank()) LABEL else "$LABEL: $rule"

    fun putMasked(schema: MutableMap<String, Any?>, rule: String) {
        schema[MASKED] = true

        if (rule.isNotBlank()) {
            schema[MASKED_RULE] = rule
        }
    }
}
