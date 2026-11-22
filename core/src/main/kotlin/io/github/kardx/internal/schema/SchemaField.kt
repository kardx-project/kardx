package io.github.kardx.internal.schema

import io.github.kardx.dsl.CONSTRAINTS_ATTRIBUTE
import io.github.kardx.dsl.CONSTRAINT_NOTE_ATTRIBUTE
import io.github.kardx.dsl.DEFAULT_VALUE_ATTRIBUTE
import io.github.kardx.dsl.DEPRECATED_ATTRIBUTE
import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.EXAMPLE_ATTRIBUTE
import io.github.kardx.dsl.MASKED_ATTRIBUTE
import io.github.kardx.dsl.MASKING_RULE_ATTRIBUTE
import io.github.kardx.dsl.NULLABLE_ATTRIBUTE
import io.github.kardx.dsl.NULLABLE_ITEMS_ATTRIBUTE
import io.github.kardx.dsl.REQUIRED_BY_CONSTRAINT_ATTRIBUTE
import io.github.kardx.dsl.REQUIRED_IN_PARENT_ATTRIBUTE
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.SCHEMA_ATTRIBUTE

/**
 * JSON Schema 를 만들 필드 하나. AsyncAPI 메타데이터(`*.asyncapi.json`)에 이 모양으로 남고, multipart 파트 스키마에도 쓴다.
 *
 * @property type REST Docs 필드 타입 이름 (`String`, `Number`, `enum` …)
 * @property constraints 표의 Constraints 열 내용
 * @property schema 타입·제약에서 만든 스키마 조각 ([io.github.kardx.dsl.SCHEMA_ATTRIBUTE])
 * @property constraintNote 키워드로 옮기지 못한 제약 설명. 설명 뒤에 `제약: ...` 으로 붙는다
 * @property defaultValue `withDefaultValue` 값 (문자열 그대로. 스키마 타입에 맞춰 바꾼다)
 * @property example `example` 값
 * @property requiredByConstraint optional 이지만 스키마에서는 required 에 넣는다. `@NotNull` 등이 붙었거나
 *   ([io.github.kardx.dsl.REQUIRED_BY_CONSTRAINT_ATTRIBUTE]) 부모가 있으면 꼭 있는 하위 필드
 *   ([io.github.kardx.dsl.REQUIRED_IN_PARENT_ATTRIBUTE])
 * @property deprecated 더 이상 쓰지 않는 필드 ([io.github.kardx.dsl.DEPRECATED_ATTRIBUTE]).
 *   스키마에 `deprecated: true`
 * @property masking 값이 마스킹되어 나가는 필드면 규칙 설명(없으면 빈 문자열), 아니면 null
 *   ([io.github.kardx.dsl.MASKED_ATTRIBUTE]). 스키마에 `x-masked: true`, 설명 끝에 `Masked: ...`
 * @property nullable 값이 null 일 수 있다 ([NULLABLE_ATTRIBUTE]). OpenAPI 3.0 스키마에 `nullable: true`
 *   ([requiredByConstraint] 면 넣지 않는다). Gradle 플러그인이 epages 스키마에 맞추는 규칙과 같다
 * @property nullableItems 배열 원소가 null 일 수 있다 ([NULLABLE_ITEMS_ATTRIBUTE]). OpenAPI 3.0 `items` 에 `nullable: true`
 */
internal data class SchemaField(
    val path: String,
    val type: String?,
    val description: String?,
    val optional: Boolean,
    val ignored: Boolean,
    val constraints: String?,
    val enumValues: List<String>?,
    val schema: Map<String, Any?>? = null,
    val constraintNote: String? = null,
    val defaultValue: String? = null,
    val example: String? = null,
    val requiredByConstraint: Boolean = false,
    val deprecated: Boolean = false,
    val masking: String? = null,
    val nullable: Boolean = false,
    val nullableItems: Boolean = false,
) {
    /** 설명 뒤에 붙일 제약. 스키마 조각이 없는 예전 메타데이터는 표 문구 전체를 쓴다. */
    val note: String? get() = constraintNote ?: constraints.takeIf { schema == null }

    companion object {
        fun of(field: RestDocsField): SchemaField {
            val descriptor = field.descriptor
            val attributes = descriptor.attributes

            @Suppress("UNCHECKED_CAST")
            return SchemaField(
                path = descriptor.path,
                type = descriptor.type?.toString(),
                description = descriptor.description?.toString(),
                optional = descriptor.isOptional,
                ignored = descriptor.isIgnored,
                constraints = attributes[CONSTRAINTS_ATTRIBUTE]?.toString(),
                enumValues = (attributes[ENUM_VALUES_ATTRIBUTE] as? List<*>)?.map { it.toString() },
                schema = attributes[SCHEMA_ATTRIBUTE] as? Map<String, Any?>,
                constraintNote = attributes[CONSTRAINT_NOTE_ATTRIBUTE]?.toString(),
                defaultValue = attributes[DEFAULT_VALUE_ATTRIBUTE]?.toString(),
                example = attributes[EXAMPLE_ATTRIBUTE]?.toString(),
                requiredByConstraint = attributes[REQUIRED_BY_CONSTRAINT_ATTRIBUTE] == true ||
                    attributes[REQUIRED_IN_PARENT_ATTRIBUTE] == true,
                deprecated = attributes[DEPRECATED_ATTRIBUTE] == true,
                masking = attributes[MASKING_RULE_ATTRIBUTE]?.toString()
                    ?: "".takeIf { attributes[MASKED_ATTRIBUTE] == true },
                nullable = attributes[NULLABLE_ATTRIBUTE] == true,
                nullableItems = attributes[NULLABLE_ITEMS_ATTRIBUTE] == true,
            )
        }
    }
}
