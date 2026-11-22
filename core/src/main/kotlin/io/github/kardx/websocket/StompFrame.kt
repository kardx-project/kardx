package io.github.kardx.websocket

/**
 * [StompTestClient] 가 구독으로 받은 STOMP `MESSAGE` 프레임 하나. 본문은 UTF-8 문자열 그대로다 (JSON 이면 JSON 문자열).
 *
 * ```kotlin
 * val frame = stomp.sendAndReceive("/app/chat.send", ChatSendRequest(1, "안녕"), "/topic/rooms.1")
 * docs.subscribe("/topic/rooms.{roomId}", payload = frame.body) { fields<ChatMessage>() }
 * ```
 *
 * @property headers 프레임 헤더. 같은 이름이 여러 번이면 첫 값이다 (`destination`, `subscription`, `message-id` …)
 * @property body 프레임 본문
 */
public class StompFrame internal constructor(public val headers: Map<String, String>, public val body: String) {
    override fun toString(): String = "StompFrame(headers=$headers, body=$body)"
}
