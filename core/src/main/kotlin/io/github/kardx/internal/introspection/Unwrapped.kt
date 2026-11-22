package io.github.kardx.internal.introspection

import io.github.kardx.internal.reflect.jsonValueType
import java.util.Optional
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

/** [unwrap] 결과. [nullable] 은 감싸기를 푸는 과정에서 한 번이라도 nullable 이었는지를 뜻한다. */
internal data class Unwrapped(val type: KType, val nullable: Boolean)

/**
 * `Optional<T>`, value class, `@JsonValue` 로 한 값이 되는 클래스를 내부 타입으로 푼다.
 * Spring Data `Page`·`Slice`·`Sort`, Spring HATEOAS 모델과 Spring `ProblemDetail` 은 Jackson 이 실제로 내보내는 모양의
 * 타입으로 바꾼다 ([SpringDataTypes], [HateoasTypes], [problemDetailType]). kotlinx 직렬화기가 값 하나로 쓰는 클래스는 그 값
 * 타입이다 ([KotlinxSerialization.valueType]).
 */
internal fun unwrap(type: KType): Unwrapped {
    var current = type
    var nullable = type.isMarkedNullable

    repeat(MAX_UNWRAP) {
        val k = current.classifier as? KClass<*> ?: return Unwrapped(current, nullable)
        val next = when {
            k == Optional::class ->
                current.arguments.firstOrNull()?.type
                    ?.also { nullable = true }

            k.isValue -> k.primaryConstructor?.parameters?.singleOrNull()?.type

            else -> SpringDataTypes.bodyType(current)
                ?: HateoasTypes.bodyType(current)
                ?: problemDetailType(current)
                ?: KotlinxSerialization.valueType(k)
                ?: jsonValueTypeOf(k, current)
        } ?: return Unwrapped(current, nullable)

        nullable = nullable || next.isMarkedNullable
        current = next
    }

    return Unwrapped(current, nullable)
}

/** `@JsonValue` 로 한 값이 되는 클래스의 값 타입. 제네릭 값 클래스(`Id<T>`)는 타입 인자로 치환해야 실제 값 타입이 나온다. */
private fun jsonValueTypeOf(k: KClass<*>, current: KType): KType? = jsonValueType(k.java)?.toKType()
    ?.substitute(bindingsOf(current))

private const val MAX_UNWRAP = 8
