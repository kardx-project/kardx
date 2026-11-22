package io.github.kardx.websocket

import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.websocket.RecordingWebSocketSession
import io.github.kardx.internal.websocket.webSocketUri
import org.springframework.http.HttpHeaders
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketMessage
import java.nio.ByteBuffer
import java.security.Principal
import java.time.Duration

/**
 * 서버 없이 [WebSocketHandler](`TextWebSocketHandler`, `BinaryWebSocketHandler`, 직접 구현한 핸들러)를 호출하는 테스트 도구.
 *
 * 가짜 세션을 붙여 연결·메시지·종료를 핸들러에 전달하고, 핸들러가 세션으로 보낸 메시지를 모은다.
 * 모은 메시지를 [MessageDocumentation] 에 넘기면 문서가 된다.
 *
 * ```kotlin
 * WebSocketHandlerTester(EchoHandler(), path = "/ws/echo").use { ws ->
 *     val greeting = ws.connect()                       // afterConnectionEstablished 에서 보낸 메시지
 *     val replies = ws.send("""{"text":"hello"}""")      // 이 메시지 처리 중에 보낸 메시지
 *
 *     val docs = MessageDocumentation(provider, MessageProtocol.WEBSOCKET) // io.github.kardx.dsl.MessageProtocol
 *
 *     docs.send("/ws/echo", """{"text":"hello"}""") { fields<EchoRequest>() }
 *     docs.subscribe("/ws/echo", replies.single()) { fields<EchoResponse>() }
 * }
 * ```
 *
 * 다른 스레드에서 나중에 메시지를 보내는 핸들러는 [awaitMessages] 로 기다린다.
 *
 * @param path 세션 URI 의 경로 (`session.uri`)
 * @param handshakeHeaders 핸드셰이크 요청 헤더 (`session.handshakeHeaders`)
 * @param attributes 세션 속성. 핸드셰이크 인터셉터가 넣는 값(사용자 ID 등)을 흉내 낸다
 * @param principal 인증 사용자 (`session.principal`)
 */
@ExperimentalRestDocsApi
public class WebSocketHandlerTester(
    private val handler: WebSocketHandler,
    path: String = "/ws",
    handshakeHeaders: Map<String, String> = emptyMap(),
    attributes: Map<String, Any> = emptyMap(),
    principal: Principal? = null,
) : AutoCloseable {

    private val session = RecordingWebSocketSession(
        uri = webSocketUri(path),
        handshakeHeaders = HttpHeaders().apply { handshakeHeaders.forEach { (name, value) -> add(name, value) } },
        attributes = attributes.toMutableMap(),
        principal = principal,
    )

    private var connected = false

    /** 핸들러가 지금까지 보낸 모든 메시지 (텍스트, 바이너리는 UTF-8 로 읽음). */
    public val messages: List<String>
        get() = session.sent.map(::textOf)

    /** 세션이 열려 있는지. 핸들러가 `session.close()` 를 부르면 false 가 된다. */
    public val isOpen: Boolean
        get() = session.isOpen

    /** 핸들러가 세션을 닫았다면 그 종료 코드 (예: 1000, 1008). */
    public val closeCode: Int?
        get() = session.closeStatus?.code

    /**
     * `afterConnectionEstablished` 를 호출하고, 그동안 핸들러가 보낸 메시지를 돌려준다.
     *
     * @throws IllegalStateException 이미 연결했으면
     */
    public fun connect(): List<String> = collect {
        check(!connected) { "Already connected" }
        connected = true
        handler.afterConnectionEstablished(session)
    }

    /** 텍스트 메시지를 보내고, 처리 중에 핸들러가 보낸 메시지를 돌려준다. 연결 전이면 먼저 연결한다. */
    public fun send(text: String): List<String> = deliver(TextMessage(text))

    /** 바이너리 메시지를 보내고, 처리 중에 핸들러가 보낸 메시지를 돌려준다. 연결 전이면 먼저 연결한다. */
    public fun send(bytes: ByteArray): List<String> = deliver(BinaryMessage(bytes))

    /**
     * 핸들러가 보낸 메시지가 [count] 개가 될 때까지 기다린다. 비동기로 보내는 핸들러용이다.
     *
     * @param timeout 기다릴 시간 (기본 5초)
     * @return 지금까지 보낸 모든 메시지
     * @throws IllegalStateException [timeout] 안에 [count] 개가 오지 않으면
     */
    public fun awaitMessages(count: Int, timeout: Duration = DEFAULT_AWAIT): List<String> {
        val deadline = System.nanoTime() + timeout.toNanos()

        while (session.sent.size < count) {
            check(System.nanoTime() < deadline) {
                RestDocsErrorCode.MESSAGE_TIMEOUT.message(
                    "Expected $count messages but received ${session.sent.size} within $timeout",
                )
            }

            Thread.sleep(POLL_MILLIS)
        }

        return messages
    }

    /** `afterConnectionClosed` 를 호출한다. 이미 닫혔으면 아무것도 하지 않는다. */
    override fun close() {
        if (!connected) return

        connected = false

        val status = session.closeStatus ?: CloseStatus.NORMAL

        session.close(status)
        handler.afterConnectionClosed(session, status)
    }

    private fun deliver(message: WebSocketMessage<*>): List<String> {
        if (!connected) connect()

        return collect { handler.handleMessage(session, message) }
    }

    private inline fun collect(block: () -> Unit): List<String> {
        val before = session.sent.size

        block()

        return session.sent.drop(before)
            .map(::textOf)
    }

    private fun textOf(message: WebSocketMessage<*>): String = when (val payload = message.payload) {
        is String -> payload

        is ByteBuffer ->
            Charsets.UTF_8.decode(payload.duplicate())
                .toString()

        else -> payload.toString()
    }

    private companion object {
        // private: private companion 의 const 도 바깥 클래스의 public static 필드로 나가므로 막는다
        private const val POLL_MILLIS = 20L
        val DEFAULT_AWAIT: Duration = Duration.ofSeconds(5)
    }
}
