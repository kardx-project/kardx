package io.github.kardx.internal.provenance

import kotlin.reflect.KType

/**
 * 필드를 추출한 타입과 그 앞에 붙인 경로. `requestFieldsOf<PaymentRequest>("data")` 면 `PaymentRequest` 와 `data` 다.
 * 본문 전체의 타입([FieldOrigin.schemaType])을 정할 수 없는 목록(경로를 붙인 추출, 여러 목록을 합친 본문)에서도
 * 각 부분이 어느 타입의 어느 위치인지 알려 준다. 다형 프로퍼티(`internal.openapi.BodyPolymorphism`)를 찾을 때 쓴다.
 *
 * @property prefix 추출한 필드 앞에 붙인 경로 (`data`, `data.content[]`, `[]`). 본문 자체에서 추출했으면 빈 문자열이다
 */
internal data class FieldSource(val type: KType, val prefix: String)
