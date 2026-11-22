package io.github.kardx.dsl

import org.springframework.restdocs.payload.JsonFieldType

/**
 * REST Docs 필드의 타입을 나타내는 sealed class.
 *
 * Spring REST Docs의 JsonFieldType에 매핑되며,
 * DSL에서 `"fieldName" type STRING means "설명"` 형태로 사용된다.
 *
 * @property typeName Spring REST Docs JsonFieldType에 매핑되는 타입명
 */
public sealed class DocsFieldType(public val typeName: String)

/** 배열 타입 (JsonFieldType.ARRAY) */
public data object ARRAY : DocsFieldType("ARRAY")

/** 불리언 타입 (JsonFieldType.BOOLEAN) */
public data object BOOLEAN : DocsFieldType("BOOLEAN")

/** 객체 타입 (JsonFieldType.OBJECT) */
public data object OBJECT : DocsFieldType("OBJECT")

/** 숫자 타입 (JsonFieldType.NUMBER) */
public data object NUMBER : DocsFieldType("NUMBER")

/** null 타입 (JsonFieldType.NULL) */
public data object NULL : DocsFieldType("NULL")

/** 문자열 타입 (JsonFieldType.STRING) */
public data object STRING : DocsFieldType(STRING_TYPE_NAME)

/** Enum 타입 - JSON에서는 STRING으로 표현된다 */
public data object ENUM : DocsFieldType(STRING_TYPE_NAME)

/** 날짜 타입 - JSON에서는 STRING으로 표현된다 (예: LocalDate) */
public data object DATE : DocsFieldType(STRING_TYPE_NAME)

/** 날짜시간 타입 - JSON에서는 STRING으로 표현된다 (예: LocalDateTime) */
public data object DATETIME : DocsFieldType(STRING_TYPE_NAME)

/** 여러 타입이 될 수 있는 경우 (JsonFieldType.VARIES) */
public data object VARIES : DocsFieldType("VARIES")

/** [DocsFieldType] 을 Spring REST Docs 의 [JsonFieldType] 으로 변환한다. */
internal fun DocsFieldType.toJsonFieldType(): JsonFieldType = when (this) {
    ARRAY -> JsonFieldType.ARRAY
    BOOLEAN -> JsonFieldType.BOOLEAN
    OBJECT -> JsonFieldType.OBJECT
    NUMBER -> JsonFieldType.NUMBER
    NULL -> JsonFieldType.NULL
    STRING, ENUM, DATE, DATETIME -> JsonFieldType.STRING
    VARIES -> JsonFieldType.VARIES
}

/** [DATE]·[DATETIME] 의 OpenAPI·AsyncAPI 스키마 조각 (`format`). 다른 타입은 null. */
internal fun DocsFieldType.schemaFormat(): Map<String, Any?>? = when (this) {
    DATE -> mapOf("type" to "string", "format" to "date")
    DATETIME -> mapOf("type" to "string", "format" to "date-time")
    else -> null
}

/** JSON 에서 문자열로 나가는 타입([STRING], [ENUM], [DATE], [DATETIME])의 이름. */
private const val STRING_TYPE_NAME = "STRING"
