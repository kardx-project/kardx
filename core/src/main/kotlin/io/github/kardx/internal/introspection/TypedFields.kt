package io.github.kardx.internal.introspection

import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.provenance.FieldOrigin
import kotlin.reflect.KType

/**
 * 어떤 타입에서 추출했는지 기억하는 필드 목록. OpenAPI 스키마 이름의 기본값을 정할 때 쓴다.
 *
 * 걸러 내거나 더하면(`filterNot`, `+`) 보통 목록이 되어 이 타입은 사라지지만, 만들 때 각 필드의 출처를
 * [FieldOrigins] 에 적어 두므로 보통 목록에서도 [schemaTypeOf] 가 같은 타입을 찾는다.
 */
internal class TypedFields(
    val schemaType: KType,
    private val fields: List<RestDocsField>,
    /** 공통 응답 래퍼로 감싼 목록이면 래퍼를 뺀 데이터 타입 (`ApiResponse<TodoResponse>` 의 `TodoResponse`) */
    val dataType: KType? = null,
) : List<RestDocsField> by fields {
    init {
        // 라이브러리 안에서 다른 목록의 필드를 감싸 다시 만들면 나중(바깥) 목록의 출처로 덮어쓴다
        FieldOrigins.mark(fields, FieldOrigin(schemaType, dataType))
    }

    override fun toString(): String = fields.toString()
}

/** [fields] 가 타입에서 추출한 목록(또는 그 목록을 거르고 직접 쓴 필드를 더한 목록)이면 그 타입. */
internal fun schemaTypeOf(fields: List<RestDocsField>): KType? = originOf(fields)?.schemaType

/** [fields] 가 공통 응답 래퍼로 감싸 추출한 목록이면 래퍼를 뺀 데이터 타입. */
internal fun dataTypeOf(fields: List<RestDocsField>): KType? = originOf(fields)?.dataType

private fun originOf(fields: List<RestDocsField>): FieldOrigin? = if (fields is TypedFields) {
    FieldOrigin(fields.schemaType, fields.dataType)
} else {
    FieldOrigins.of(fields)
}
