package io.github.kardx.internal.introspection

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.createType
import kotlin.reflect.full.withNullability

// 제네릭 타입 파라미터 바인딩과 치환.

/**
 * [type] 의 클래스와 상위 타입들의 타입 파라미터 바인딩을 계산한다.
 * 예: `Page<User>` 와 `class UserPage : Page<User>()` 모두 `Page.T -> User`.
 *
 * 키는 이름이 아니라 타입 파라미터 자체(선언한 클래스 + 이름)다. `class PageResp<T> : Base<List<T>>` 처럼 하위 클래스와 상위 클래스가
 * 같은 이름(`T`)을 다른 타입에 쓰면, 상위 클래스에서 물려받은 Java getter 의 `T` 는 상위 클래스의 `T`(`List<User>`)로 풀려야 한다.
 */
internal fun bindingsOf(type: KType): Map<KTypeParameter, KType> {
    val k = type.classifier as? KClass<*> ?: return emptyMap()

    val own = k.typeParameters.zip(type.arguments)
        .mapNotNull { (param, arg) -> arg.type?.let { param to it } }
        .toMap()
    val inherited = k.allSupertypes.flatMap { superType ->
        val superClass = superType.classifier as? KClass<*> ?: return@flatMap emptyList()

        superClass.typeParameters.zip(superType.arguments)
            .mapNotNull { (param, arg) -> arg.type?.let { param to it.substitute(own) } }
    }.toMap()

    return inherited + own
}

/** 타입 파라미터를 [bindings] 로 치환한다. */
internal fun KType.substitute(bindings: Map<KTypeParameter, KType>): KType {
    if (bindings.isEmpty()) return this

    return when (val c = classifier) {
        is KTypeParameter ->
            bindings[c]
                ?.let { if (isMarkedNullable) it.withNullability(true) else it }
                ?: this

        is KClass<*> -> if (arguments.isEmpty()) {
            this
        } else {
            c.createType(
                arguments.map { arg ->
                    arg.type?.let { KTypeProjection(arg.variance, it.substitute(bindings)) } ?: arg
                },
                isMarkedNullable,
                annotations,
            )
        }

        else -> this
    }
}
