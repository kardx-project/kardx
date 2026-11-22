package io.github.kardx.internal.provenance

import kotlin.reflect.KType

/**
 * 필드를 만든 추출. [schemaType] 이 null 이면 본문 전체가 아닌 경로(`data`, `content[]`)로 추출했거나
 * 공통 래퍼 필드처럼 본문 타입을 정할 수 없는 필드다.
 *
 * 필드(`dsl.RestDocsField`)가 이 값을 들고 다니므로, 이 패키지는 다른 저장소 패키지에 기대지 않는다
 * (`dsl` 과 `internal.introspection` 이 서로 기대지 않게 하려는 것이다).
 */
internal data class FieldOrigin(val schemaType: KType?, val dataType: KType?) {
    companion object {
        val UNTYPED: FieldOrigin = FieldOrigin(null, null)
    }
}
