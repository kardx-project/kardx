package io.github.kardx.extractor

import io.github.kardx.dsl.RestDocsEvent
import kotlin.reflect.KClass

/**
 * 이벤트 `data` 의 JSON 필드를 [type] 에서 추출한다. `withFields responseFieldsOf(type)` 과 같다 (응답 규칙으로 추출).
 * 스트림에서 이 이름의 첫 이벤트로 검증하고 `response-fields-event-<이름>` 표와 AsyncAPI 메시지 페이로드 스키마가 된다.
 * 타입 분석을 쓰므로 [responseFieldsOf] 와 같은 이 패키지에 있다.
 *
 * ```kotlin
 * "sold-out" eventMeans "품절" withFields SoldOutEvent::class
 * ```
 *
 * 제네릭 이벤트(`Envelope<Payload>`)는 타입 인자를 지킬 수 있게 목록 버전에 `responseFieldsOf<Envelope<Payload>>()` 를 넘긴다.
 */
public infix fun RestDocsEvent.withFields(type: KClass<*>): RestDocsEvent = withFields(responseFieldsOf(type))
