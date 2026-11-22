package io.github.kardx.dsl

/**
 * 메시지 문서의 프로토콜. [io.github.kardx.websocket.MessageDocumentation] 에 넘긴다.
 *
 * [displayName] 이 `message-NN` 스니펫의 프로토콜 표시와 AsyncAPI 채널의 `x-protocol` 이 되고, AsyncAPI 서버
 * (`AsyncApiServer.protocol`: `stomp`, `ws`, `http` …) 중 이 프로토콜로 연결할 수 있는 서버를 채널에 붙이는 데 쓴다.
 *
 * ```kotlin
 * MessageDocumentation(provider)                              // STOMP (기본값)
 * MessageDocumentation(provider, MessageProtocol.WEBSOCKET)   // 순수 WebSocket
 * ```
 *
 * @property displayName 문서에 나가는 이름 (`STOMP`, `WebSocket`, `SSE`)
 */
public enum class MessageProtocol(
    public val displayName: String,
    private val aliases: Set<String>,
    private val serverSchemes: Set<String>,
) {
    /** STOMP over WebSocket. `stomp`·`stomps` 서버와 맞는다 */
    STOMP("STOMP", setOf("stomp"), setOf("stomp", "stomps")),

    /** 순수 WebSocket. `ws`·`wss` 서버와 맞는다 */
    WEBSOCKET("WebSocket", setOf("websocket", "ws"), setOf("ws", "wss")),

    /** Server-Sent Events. SSE 문서화(`events(...)`)가 쓴다. `http`·`https` 서버와 맞는다 */
    SSE("SSE", setOf("sse", "http"), setOf("http", "https")),
    ;

    /** [serverProtocol] (AsyncAPI 서버의 `protocol`) 로 이 프로토콜의 메시지를 주고받을 수 있는지. */
    internal fun matchesServer(serverProtocol: String): Boolean = serverProtocol.lowercase() in serverSchemes

    internal companion object {
        /**
         * 프로토콜 이름(표시 이름이나 `ws`·`http` 같은 별칭, 대소문자·앞뒤 공백 무시)에 맞는 값. 사용자 정의 프로토콜이면 null.
         * 문자열로 받은 프로토콜의 표시 이름과 AsyncAPI 서버 연결 판정이 모두 이 함수를 쓴다.
         */
        fun of(name: String): MessageProtocol? {
            val key = name.trim()
                .lowercase()

            return entries.firstOrNull { key in it.aliases }
        }
    }
}
