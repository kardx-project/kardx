package io.github.kardx.internal.openapi

import io.github.kardx.dsl.DEFAULT_VALUE_ATTRIBUTE
import io.github.kardx.dsl.SCHEMA_ATTRIBUTE
import io.github.kardx.internal.introspection.JsonTypes
import io.github.kardx.internal.schema.SchemaHints
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType

/**
 * 본문 필드의 기본값 (`fieldDefaults`). Kotlin 생성자 기본값([io.github.kardx.internal.introspection.KotlinDefaults])과
 * `withDefaultValue` 로 적은 값이다. 직접 적은 값이 추출한 값을 덮어쓰므로 사용자의 값이 이긴다.
 *
 * `{ "requestBody": { "page.size": 20 }, "responseBody": { ... } }`. 값은 필드 스키마 타입의 JSON 값이다 (정수·숫자·불리언,
 * 그 밖은 문자열). 기본값이 있는 필드가 없는 본문은 키가 없고, 둘 다 없으면 null 이다.
 */
internal fun fieldDefaults(
    requestFields: List<FieldDescriptor>,
    responseFields: List<FieldDescriptor>,
): Map<String, Any>? {
    val defaults = linkedMapOf<String, Any>()

    defaultsOf(requestFields).takeIf { it.isNotEmpty() }?.let { defaults["requestBody"] = it }
    defaultsOf(responseFields).takeIf { it.isNotEmpty() }?.let { defaults["responseBody"] = it }

    return defaults.takeIf { it.isNotEmpty() }
}

private fun defaultsOf(fields: List<FieldDescriptor>): Map<String, Any> = fields.filterNot { it.isIgnored }
    .mapNotNull { field -> defaultOf(field)?.let { field.path to it } }
    .toMap()

/** [field] 의 기본값을 스키마 타입의 JSON 값으로. 없으면 null. */
private fun defaultOf(field: FieldDescriptor): Any? = field.attributes[DEFAULT_VALUE_ATTRIBUTE]
    ?.let { SchemaHints.coerce(it.toString(), schemaOf(field)) }

/** 기본값을 바꿀 타입. 추출한 스키마 조각이 없으면(직접 적은 필드) REST Docs 타입으로 정한다. */
private fun schemaOf(field: FieldDescriptor): Map<*, *>? = field.attributes[SCHEMA_ATTRIBUTE] as? Map<*, *>
    ?: when (field.type) {
        JsonFieldType.NUMBER -> mapOf("type" to JsonTypes.NUMBER)
        JsonFieldType.BOOLEAN -> mapOf("type" to JsonTypes.BOOLEAN)
        else -> null
    }
