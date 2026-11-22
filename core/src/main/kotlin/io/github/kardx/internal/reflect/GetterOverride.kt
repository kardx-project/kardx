package io.github.kardx.internal.reflect

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * 인자 없는 [getter] 하나만 [value] 를 돌려주고 나머지 호출은 [delegate] 로 넘기는 [type] 프록시.
 * Spring·REST Docs 버전마다 인터페이스 메서드가 달라도 되도록 직접 구현하지 않고 위임한다.
 * 위임한 메서드가 던진 예외는 감싸지 않고 그대로 던진다.
 */
internal fun <T : Any> overrideGetter(type: Class<T>, delegate: T, getter: String, value: Any): T {
    val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
        if (method.name == getter && method.parameterCount == 0) {
            value
        } else {
            try {
                method.invoke(delegate, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    return type.cast(proxy)
}
