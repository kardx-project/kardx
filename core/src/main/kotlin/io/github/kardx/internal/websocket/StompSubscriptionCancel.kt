package io.github.kardx.internal.websocket

import org.springframework.messaging.simp.stomp.StompSession
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * 구독을 끊는다 (UNSUBSCRIBE 프레임). `unsubscribe()` 의 반환 타입이 Spring 6 은 `void`, Spring 7 은 `Receiptable` 이라서
 * Spring 6 으로 컴파일한 직접 호출은 Spring 7 (Boot 4) 에서 `NoSuchMethodError` 가 난다. 그래서 이름으로 찾아 부른다.
 */
internal fun StompSession.Subscription.cancel() {
    try {
        UNSUBSCRIBE.invoke(this)
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }
}

private val UNSUBSCRIBE: Method = StompSession.Subscription::class.java.getMethod("unsubscribe")
