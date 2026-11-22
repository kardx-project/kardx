package io.github.kardx.internal.introspection

import java.math.BigDecimal

/**
 * 제약조건을 JSON Schema 키워드로 옮긴 결과.
 *
 * @property keywords `minLength`, `maximum`, `pattern`, `format` … `exclusiveMinimum` 은 OpenAPI 3.0 처럼 불리언이다
 * @property note 키워드로 옮기지 못한 제약(`@Past`, `@Digits`, 사용자 정의, 검증 그룹이 있는 제약)의 설명. 없으면 null
 * @property required 기본 그룹의 `@NotNull`·`@NotBlank`·`@NotEmpty` 가 있어 값이 꼭 있어야 하는지
 */
internal class ConstraintSchema(val keywords: Map<String, Any?>, val note: String?, val required: Boolean)

/**
 * Bean Validation 제약을 JSON 값 타입(`jsonType`: `string`, `integer`, `number`, `array`, `object`)에 맞는 키워드로 옮긴다.
 *
 * | 제약 | 키워드 |
 * |---|---|
 * | `@Size`, `@Length` | 문자열 `minLength`·`maxLength`, 배열 `minItems`·`maxItems`, 맵 `minProperties`·`maxProperties` |
 * | `@NotBlank` | `minLength: 1` + required |
 * | `@NotEmpty` | 길이·개수 1 이상 + required |
 * | `@NotNull` | required |
 * | `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Range` | `minimum`·`maximum` (`inclusive = false` 면 exclusive) |
 * | `@Positive`, `@PositiveOrZero`, `@Negative`, `@NegativeOrZero` | 0 기준 `minimum`·`maximum` |
 * | `@Pattern` | `pattern` |
 * | `@Email`, Hibernate `@URL` | `format: email`, `format: uri` |
 *
 * 그 밖의 제약과 검증 그룹이 지정된 제약은 [ConstraintSchema.note] 로 남는다. 그룹이 있는 제약은 어느 요청에서 검증되는지
 * 문서만으로 알 수 없으므로 키워드·required 에 넣지 않는다.
 */
internal object ConstraintSchemas {
    fun of(constraints: List<ResolvedConstraint>, jsonType: String?): ConstraintSchema {
        val keywords = linkedMapOf<String, Any?>()
        val unmapped = mutableListOf<ResolvedConstraint>()

        constraints.forEach { constraint ->
            val mapped = constraint.takeIf { it.defaultGroup }
                ?.let { keywordsOf(it, jsonType) }

            if (mapped == null) {
                unmapped += constraint
            } else {
                keywords.putAll(mapped)
            }
        }

        val required = constraints.any { it.defaultGroup && it.name in REQUIRED }

        return ConstraintSchema(keywords, ConstraintResolver.describe(unmapped), required)
    }

    /** 키워드로 옮길 수 없으면 null. 키워드 없이 required 만 뜻하는 제약은 빈 맵. */
    private fun keywordsOf(constraint: ResolvedConstraint, jsonType: String?): Map<String, Any?>? {
        val attributes = constraint.attributes

        return when (constraint.name) {
            "NotNull" -> emptyMap()
            "NotBlank" -> if (jsonType == JsonTypes.STRING) mapOf("minLength" to 1) else emptyMap()
            "NotEmpty" -> sizeKeywords(jsonType, 1, Int.MAX_VALUE) ?: emptyMap()
            "Size" -> sizeKeywords(jsonType, attributes)
            "Length" -> sizeKeywords(jsonType?.takeIf { it == JsonTypes.STRING }, attributes)
            "Min" -> numeric(jsonType, MINIMUM to attributes[VALUE])
            "Max" -> numeric(jsonType, MAXIMUM to attributes[VALUE])
            "Range" -> numeric(jsonType, MINIMUM to attributes["min"], MAXIMUM to attributes["max"])
            "DecimalMin" -> decimal(jsonType, MINIMUM, EXCLUSIVE_MINIMUM, attributes)
            "DecimalMax" -> decimal(jsonType, MAXIMUM, EXCLUSIVE_MAXIMUM, attributes)
            "Positive" -> numeric(jsonType, MINIMUM to 0, EXCLUSIVE_MINIMUM to true)
            "PositiveOrZero" -> numeric(jsonType, MINIMUM to 0)
            "Negative" -> numeric(jsonType, MAXIMUM to 0, EXCLUSIVE_MAXIMUM to true)
            "NegativeOrZero" -> numeric(jsonType, MAXIMUM to 0)
            "Pattern" -> string(jsonType, "pattern" to attributes["regexp"])
            "Email" -> string(jsonType, FORMAT to "email")
            "URL" -> string(jsonType, FORMAT to "uri")
            else -> null
        }
    }

    /** 타입별 길이·개수 키워드. 기본값(0, Int.MAX_VALUE)인 쪽은 넣지 않는다. 길이를 잴 수 없는 타입이면 null. */
    private fun sizeKeywords(jsonType: String?, min: Int?, max: Int?): Map<String, Any?>? {
        val (minKey, maxKey) = when (jsonType) {
            JsonTypes.STRING -> "minLength" to "maxLength"
            JsonTypes.ARRAY -> "minItems" to "maxItems"
            JsonTypes.OBJECT -> "minProperties" to "maxProperties"
            else -> return null
        }

        return buildMap {
            min?.takeIf { it > 0 }?.let { put(minKey, it) }
            max?.takeIf { it != Int.MAX_VALUE }?.let { put(maxKey, it) }
        }
    }

    private fun sizeKeywords(jsonType: String?, attributes: Map<String, Any?>): Map<String, Any?>? =
        sizeKeywords(jsonType, attributes.int("min"), attributes.int("max"))

    private fun numeric(jsonType: String?, vararg keywords: Pair<String, Any?>): Map<String, Any?>? =
        keywords.toMap().takeIf { jsonType == JsonTypes.INTEGER || jsonType == JsonTypes.NUMBER }

    private fun string(jsonType: String?, keyword: Pair<String, Any?>): Map<String, Any?>? =
        mapOf(keyword).takeIf { jsonType == JsonTypes.STRING && keyword.second != null }

    /** `@DecimalMin("0.5", inclusive = false)` → `minimum: 0.5, exclusiveMinimum: true` */
    private fun decimal(
        jsonType: String?,
        boundKey: String,
        exclusiveKey: String,
        attributes: Map<String, Any?>,
    ): Map<String, Any?>? {
        val bound = (attributes[VALUE] as? String)?.toBigDecimalOrNull() ?: return null
        val exclusive = attributes["inclusive"] == false

        return numeric(jsonType, boundKey to bound.compact(), exclusiveKey to true.takeIf { exclusive })
            ?.filterValues { it != null }
    }

    /** 정수로 나타낼 수 있으면 Long (`minimum: 0`), 아니면 BigDecimal (`minimum: 0.5`). */
    private fun BigDecimal.compact(): Number = runCatching { longValueExact() }.getOrElse { stripTrailingZeros() }

    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

    private val REQUIRED = setOf("NotNull", "NotBlank", "NotEmpty")
    private const val MINIMUM = "minimum"
    private const val MAXIMUM = "maximum"
    private const val EXCLUSIVE_MINIMUM = "exclusiveMinimum"
    private const val EXCLUSIVE_MAXIMUM = "exclusiveMaximum"
    private const val FORMAT = "format"
    private const val VALUE = "value"
}
