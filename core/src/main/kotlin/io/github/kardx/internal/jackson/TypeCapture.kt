package io.github.kardx.internal.jackson

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/** reified 타입 인자의 전체 제네릭 타입을 익명 하위 클래스의 상위 타입에서 꺼낸다. */
@PublishedApi
internal abstract class TypeCapture<T> {
    val type: Type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments.single()
}
