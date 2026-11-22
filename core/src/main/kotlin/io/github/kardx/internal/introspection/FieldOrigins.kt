package io.github.kardx.internal.introspection

import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.provenance.FieldOrigin

/**
 * 추출한 필드마다 출처([RestDocsField.origin])를 적고 읽는다. `responseFieldsOf<T>().filterNot { ... } + ("x" type ...)`
 * 처럼 Kotlin 표준 함수로 목록을 고쳐 [TypedFields] 가 보통 목록이 되어도 OpenAPI 스키마 이름 기본값을 유지하려고 쓴다.
 *
 * 출처는 필드 객체가 들고 있으므로 필드가 더 쓰이지 않으면 같이 사라지고, 수식어로 복사한 필드(`optional()`, `masked()`)는
 * 그대로 이어받는다.
 */
internal object FieldOrigins {
    fun mark(fields: Iterable<RestDocsField>, origin: FieldOrigin) {
        fields.forEach { it.origin = origin }
    }

    /**
     * 목록의 출처. 추출한 필드가 모두 같은 출처일 때만 정한다. 직접 쓴 필드(`"x" type STRING means ...`)는 출처가 없어
     * 결과에 영향을 주지 않는다. 다른 타입이나 다른 경로에서 추출한 필드가 섞이면 본문이 어느 한 타입이라고 볼 수 없어 null 이다.
     */
    fun of(fields: List<RestDocsField>): FieldOrigin? = fields.mapNotNull { it.origin }
        .distinct()
        .singleOrNull()
}
