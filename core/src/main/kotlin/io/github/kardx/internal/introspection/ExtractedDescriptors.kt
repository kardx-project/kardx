package io.github.kardx.internal.introspection

import io.github.kardx.dsl.ARRAY
import io.github.kardx.dsl.CONSTRAINTS_ATTRIBUTE
import io.github.kardx.dsl.CONSTRAINT_NOTE_ATTRIBUTE
import io.github.kardx.dsl.DEFAULT_VALUE_ATTRIBUTE
import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.ITEMS_TYPE_ATTRIBUTE
import io.github.kardx.dsl.NULLABLE_ATTRIBUTE
import io.github.kardx.dsl.NULLABLE_ITEMS_ATTRIBUTE
import io.github.kardx.dsl.REQUIRED_BY_CONSTRAINT_ATTRIBUTE
import io.github.kardx.dsl.REQUIRED_IN_PARENT_ATTRIBUTE
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.RestDocsParam
import io.github.kardx.dsl.SCHEMA_ATTRIBUTE
import io.github.kardx.dsl.arrayEnumValues
import io.github.kardx.dsl.enumValues
import io.github.kardx.dsl.markDeprecated
import io.github.kardx.dsl.markMasked
import io.github.kardx.dsl.toJsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.request.RequestDocumentation

// 추출 결과([ExtractedField])를 REST Docs 디스크립터로 바꾼다. 표에 나가는 값과 OpenAPI·AsyncAPI 용 속성을 함께 넣는다.

internal fun ExtractedField.toRestDocsField(): RestDocsField {
    val descriptor = if (subsection) {
        PayloadDocumentation.subsectionWithPath(path)
    } else {
        PayloadDocumentation.fieldWithPath(path)
    }

    descriptor.type(type.toJsonFieldType())
        .description(description)

    if (optional) descriptor.optional()

    if (ignored) descriptor.ignored()

    descriptor.attributes.putAll(schemaAttributes())
    epagesItemsType()?.let { descriptor.attributes[ITEMS_TYPE_ATTRIBUTE] = it }

    // Enum 배열은 배열 타입을 그대로 두고 원소 값 목록만 붙인다 (OpenAPI 의 items.enum)
    return RestDocsField(descriptor).also { field ->
        enumValues?.let { values ->
            if (type == ARRAY) field.arrayEnumValues(values) else field.enumValues(values)
        }
    }
}

internal fun ExtractedField.toRestDocsParam(): RestDocsParam {
    val descriptor = RequestDocumentation.parameterWithName(path)
        .description(description)

    if (optional) descriptor.optional()

    descriptor.attributes.putAll(schemaAttributes())
    enumValues?.let { descriptor.attributes[ENUM_VALUES_ATTRIBUTE] = it }

    return RestDocsParam(descriptor)
}

/** 제약 설명과 OpenAPI·AsyncAPI 용 스키마 정보 속성. 값이 없는 키는 넣지 않는다. */
private fun ExtractedField.schemaAttributes(): Map<String, Any> = buildMap {
    constraints?.let { put(CONSTRAINTS_ATTRIBUTE, it) }
    constraintNote?.let { put(CONSTRAINT_NOTE_ATTRIBUTE, it) }
    schema?.let { put(SCHEMA_ATTRIBUTE, it) }

    if (requiredByConstraint) put(REQUIRED_BY_CONSTRAINT_ATTRIBUTE, true)

    if (requiredInParent) put(REQUIRED_IN_PARENT_ATTRIBUTE, true)

    nullable?.let { put(NULLABLE_ATTRIBUTE, it) }

    if (nullableItems) put(NULLABLE_ITEMS_ATTRIBUTE, true)

    deprecation?.let { markDeprecated(it) }
    masking?.let { markMasked(it) }
    defaultValue?.let { put(DEFAULT_VALUE_ATTRIBUTE, it) }
}

/**
 * 값 배열(`List<String>`)의 원소 타입을 epages 가 아는 이름으로. 플러그인 없이 epages 만 써도 `items` 가 `oneOf` 로 뭉개지지 않는다.
 * 정수는 epages 에 `integer` 가 없어 `number` 로 넘긴다. 원소가 배열·객체면 null.
 */
private fun ExtractedField.epagesItemsType(): String? {
    val items = schema?.get(TypeSchemas.ITEMS) as? Map<*, *>

    return when (items?.get(TypeSchemas.TYPE)) {
        JsonTypes.STRING -> JsonTypes.STRING
        JsonTypes.INTEGER, JsonTypes.NUMBER -> JsonTypes.NUMBER
        JsonTypes.BOOLEAN -> JsonTypes.BOOLEAN
        else -> null
    }
}
