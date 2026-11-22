package io.github.kardx.internal.openapi

import io.github.kardx.dsl.ENUM_FIELD_TYPE
import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.payload.SubsectionDescriptor

/**
 * epages 에 넘기는 필드 디스크립터. REST Docs 스니펫과 다르게 넘겨야 하는 것만 복사본을 만든다.
 *
 * Enum 값 목록이 있는 문자열 필드는 타입을 `enum` 으로 바꾼다. epages 는 그 타입일 때만 `enum` 스키마를 만들고,
 * REST Docs 스니펫에는 원래 타입(`String`)이 남아 표의 Type 열이 `String` 이고 JSON 타입 검증도 한다.
 */
internal object OpenApiDescriptors {
    fun field(descriptor: FieldDescriptor): FieldDescriptor {
        if (descriptor.attributes[ENUM_VALUES_ATTRIBUTE] == null || descriptor.type != JsonFieldType.STRING) {
            return descriptor
        }

        val copy = if (descriptor is SubsectionDescriptor) {
            PayloadDocumentation.subsectionWithPath(descriptor.path)
        } else {
            PayloadDocumentation.fieldWithPath(descriptor.path)
        }

        copy.type(ENUM_FIELD_TYPE)
            .description(descriptor.description)
            .attributes
            .putAll(descriptor.attributes)

        if (descriptor.isOptional) copy.optional()

        if (descriptor.isIgnored) copy.ignored()

        return copy
    }
}
