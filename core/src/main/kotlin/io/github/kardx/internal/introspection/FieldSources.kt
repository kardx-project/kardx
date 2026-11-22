package io.github.kardx.internal.introspection

import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.provenance.FieldSource

/**
 * 추출한 필드마다 [FieldSource]([RestDocsField.source])를 적고 읽는다. [FieldOrigins] 처럼 필드 객체가 값을 들고 있어
 * 수식어로 복사한 필드(`optional()`, `masked()`)도 이어받는다.
 */
internal object FieldSources {
    fun mark(fields: Iterable<RestDocsField>, source: FieldSource) {
        fields.forEach { it.source = source }
    }

    /** [fields] 를 추출한 출처. 처음 나온 순서이고 중복은 없다. 직접 쓴 필드는 출처가 없다. */
    fun of(fields: List<RestDocsField>): List<FieldSource> = fields.mapNotNull { it.source }
        .distinct()
}
