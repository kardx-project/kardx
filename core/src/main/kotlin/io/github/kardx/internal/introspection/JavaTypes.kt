package io.github.kardx.internal.introspection

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

/** Java 리플렉션 [Type] 을 [KType] 으로 변환한다. Java record / Java bean 지원에 사용한다. */
internal fun Type.toKType(): KType = when (this) {
    is Class<*> -> when {
        isArray && !componentType.isPrimitive -> arrayTypeOf(componentType.toKType())
        else -> kotlin.let { k -> k.createType(k.typeParameters.map { KTypeProjection.STAR }) }
    }

    is ParameterizedType -> (rawType as Class<*>).kotlin.createType(
        actualTypeArguments.map { KTypeProjection.invariant(it.toKType()) },
    )

    is GenericArrayType -> arrayTypeOf(genericComponentType.toKType())

    is WildcardType -> (lowerBounds.firstOrNull() ?: upperBounds.firstOrNull() ?: Any::class.java).toKType()

    // 선언 클래스의 타입 파라미터로 바꿔 두면 bindingsOf/substitute 가 그 파라미터로 치환한다 (Java record·bean 제네릭)
    // Java 타입은 null 가능 여부를 알 수 없으므로 다른 Java 타입처럼 non-null 로 둔다
    is TypeVariable<*> -> (genericDeclaration as? Class<*>)?.kotlin?.typeParameters?.firstOrNull { it.name == name }
        ?.createType()
        ?: Any::class.createType(nullable = true)

    else -> Any::class.createType(nullable = true)
}

private fun arrayTypeOf(component: KType): KType =
    Array<Any>::class.createType(listOf(KTypeProjection.invariant(component)))
