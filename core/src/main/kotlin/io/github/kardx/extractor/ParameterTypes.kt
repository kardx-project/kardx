package io.github.kardx.extractor

import io.github.kardx.dsl.RestDocsParam
import io.github.kardx.internal.introspection.putValueType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType

/**
 * 값의 Kotlin 타입. OpenAPI 파라미터 스키마의 `type`·`format` 이 된다 (기본은 `string`).
 * `Int`·`Long` → `integer` (`int32`·`int64`), `BigDecimal`·`Double` → `number`, `Boolean` → `boolean`,
 * `LocalDate` → `string` + `date`, Enum 은 [RestDocsParam.enumOf] 와 같이 값 목록도 넣는다. AsciiDoc 표에는 나가지 않는다.
 * 목록(`ids=1&ids=2`)은 [ofType] 의 [KType] 버전에 `typeOf<List<Long>>()` 를 넘긴다.
 * [parametersOf] 는 프로퍼티 타입에서 같은 값을 채운다.
 *
 * 정하지 않으면 요청을 처리한 핸들러 메서드의 같은 이름 인자(`@PathVariable`, `@RequestParam`, 어노테이션 없는 단순 타입)의
 * 타입이 기본값이 된다 (MockMvc, 핸들러를 기록하는 WebTestClient). 그래서 핸들러 인자와 같은 타입이면 쓰지 않아도 되고,
 * 다르게 보여야 할 때(핸들러가 `String` 으로 받는 날짜 등)만 쓴다. 이 수식어가 항상 우선한다.
 * `"id" pathMeans "사용자 ID" ofType Long::class`
 */
public infix fun RestDocsParam.ofType(type: KClass<*>): RestDocsParam = ofType(type.starProjectedType)

/** [ofType] 의 [KType] 버전. 배열 파라미터에 쓴다: `"ids" queryMeans "ID 목록" ofType typeOf<List<Long>>()` */
public infix fun RestDocsParam.ofType(type: KType): RestDocsParam = modified { it.attributes.putValueType(type) }
