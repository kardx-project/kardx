package io.github.kardx.internal.schema

/** 스키마를 쓰는 곳. 같은 제약도 키워드 모양이 조금 다르다. */
internal enum class SchemaDialect {
    /** OpenAPI 3.0: `exclusiveMinimum` 은 불리언, 예시는 `example` */
    OPENAPI_30,

    /** AsyncAPI 3.0 기본 스키마 (JSON Schema draft-07): `exclusiveMinimum` 은 숫자, 예시는 `examples` 배열 */
    JSON_SCHEMA,
}

/**
 * 추출기·수식어가 남긴 스키마 조각([io.github.kardx.dsl.SCHEMA_ATTRIBUTE])을 필드 경로로 만든 스키마에 합친다.
 * Gradle 플러그인의 `OpenApiSchemaHints` 와 같은 규칙이다.
 *
 * - 조각의 `type` 이 스키마의 `type` 과 맞지 않으면(직접 기술한 타입이 다르면) 조각 전체를 버린다. `number` 는 `integer` 로 좁힐 수 있다
 * - `items` 는 원소가 값일 때만 합친다. 원소가 객체면 하위 필드가 만든 스키마를 둔다
 */
internal object SchemaHints {
    fun merge(target: MutableMap<String, Any?>, hint: Map<String, Any?>, dialect: SchemaDialect) {
        if (!compatible(target[TYPE] as? String, hint[TYPE] as? String)) {
            return
        }

        adapt(hint, dialect).forEach { (key, value) ->
            if (key == ITEMS) {
                mergeItems(target, value)
            } else {
                target[key] = value
            }
        }
    }

    /** `withDefaultValue`·`example` 문자열을 스키마 타입의 JSON 값으로. 바꿀 수 없으면 문자열 그대로. */
    fun coerce(value: String, schema: Map<*, *>?): Any = when (schema?.get(TYPE)) {
        "integer" -> value.trim().toLongOrNull() ?: value
        "number" -> value.trim().toBigDecimalOrNull() ?: value
        "boolean" -> value.trim().toBooleanStrictOrNull() ?: value
        "array" -> value.split(',').map { coerce(it.trim(), schema[ITEMS] as? Map<*, *>) }
        else -> value
    }

    private fun compatible(current: String?, hinted: String?): Boolean =
        current == null || hinted == null || current == hinted || (current == "number" && hinted == "integer")

    private fun mergeItems(target: MutableMap<String, Any?>, items: Any?) {
        val existing = target[ITEMS] as? Map<*, *>

        if (existing?.containsKey("properties") == true || items !is Map<*, *>) {
            return
        }

        target[ITEMS] = linkedMapOf<String, Any?>().apply {
            existing?.forEach { (key, value) -> put(key.toString(), value) }
            items.forEach { (key, value) -> put(key.toString(), value) }
        }
    }

    /** [dialect] 에 맞게 바꾼 복사본. JSON Schema 는 `exclusiveMinimum: true` + `minimum: 0` 을 `exclusiveMinimum: 0` 으로. */
    private fun adapt(hint: Map<*, *>, dialect: SchemaDialect): Map<String, Any?> {
        val copy = linkedMapOf<String, Any?>()

        hint.forEach { (key, value) -> copy[key.toString()] = if (value is Map<*, *>) adapt(value, dialect) else value }

        if (dialect == SchemaDialect.JSON_SCHEMA) {
            exclusive(copy, "minimum", "exclusiveMinimum")
            exclusive(copy, "maximum", "exclusiveMaximum")
        }

        return copy
    }

    private fun exclusive(schema: MutableMap<String, Any?>, bound: String, exclusive: String) {
        if (schema[exclusive] == true) {
            schema[exclusive] = schema.remove(bound)
        }
    }

    private const val TYPE = "type"
    private const val ITEMS = "items"
}
