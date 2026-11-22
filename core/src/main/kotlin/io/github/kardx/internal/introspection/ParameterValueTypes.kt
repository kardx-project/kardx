package io.github.kardx.internal.introspection

import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.SCHEMA_ATTRIBUTE
import io.github.kardx.internal.reflect.enumJsonValues
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * 파라미터 디스크립터 attributes 에 값 타입 [type] 의 스키마 조각([SCHEMA_ATTRIBUTE])을 넣고, Enum 이면 값 목록
 * ([ENUM_VALUES_ATTRIBUTE])도 넣는다. `ofType` 수식어와 핸들러 인자에서 정한 기본 타입이 같이 쓴다.
 *
 * @param schema [type] 의 스키마 조각. 이미 계산했으면 넘긴다
 */
internal fun MutableMap<String, Any>.putValueType(type: KType, schema: Map<String, Any?>? = TypeSchemas.of(type)) {
    schema?.let { put(SCHEMA_ATTRIBUTE, it) }
    (type.classifier as? KClass<*>)?.java
        ?.takeIf { it.isEnum }
        ?.let { put(ENUM_VALUES_ATTRIBUTE, enumJsonValues(it)) }
}
