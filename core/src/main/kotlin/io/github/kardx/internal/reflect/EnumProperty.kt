package io.github.kardx.internal.reflect

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Enum 상수에서 값을 읽을 프로퍼티. Enum 이 선언한 필드 → getter(`getCode()`) → 같은 이름의 인자 없는 메서드(`name()`)
 * 순서로 찾는다. 없으면 null.
 */
internal fun enumPropertyOf(enumClass: Class<*>, name: String): ((Enum<*>) -> String?)? {
    if (name.isEmpty()) return null

    val field = declaredField(enumClass, name)

    if (field != null) {
        return { constant -> field.get(constant)?.toString() }
    }

    val method = noArgMethod(enumClass, "get${name.replaceFirstChar { it.uppercaseChar() }}")
        ?: noArgMethod(enumClass, name)

    return method?.let { m -> { constant -> m.invoke(constant)?.toString() } }
}

private fun declaredField(enumClass: Class<*>, name: String): Field? =
    enumClass.declaredFields.firstOrNull { it.name == name && !Modifier.isStatic(it.modifiers) && !it.isEnumConstant }
        ?.apply { isAccessible = true }

private fun noArgMethod(enumClass: Class<*>, name: String): Method? =
    enumClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) }
