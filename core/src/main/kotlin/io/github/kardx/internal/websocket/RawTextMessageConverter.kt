package io.github.kardx.internal.websocket

import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.converter.MessageConverter
import org.springframework.messaging.support.MessageBuilder

/**
 * STOMP 프레임 본문을 UTF-8 문자열 그대로 주고받는 변환기. `content-type` 과 무관하게 바꾸므로 문서에 실제 JSON 이 나간다.
 * `io.github.kardx.websocket.StompTestClient` 가 쓴다.
 */
internal object RawTextMessageConverter : MessageConverter {
    override fun fromMessage(message: Message<*>, targetClass: Class<*>): Any = when (val payload = message.payload) {
        is ByteArray -> String(payload, Charsets.UTF_8)
        else -> payload.toString()
    }

    override fun toMessage(payload: Any, headers: MessageHeaders?): Message<*> = MessageBuilder.createMessage(
        payload.toString()
            .toByteArray(Charsets.UTF_8),
        headers ?: MessageHeaders(null),
    )
}
