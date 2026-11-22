package io.github.kardx.websocket

import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.websocket.RawTextMessageConverter
import io.github.kardx.internal.websocket.cancel
import io.github.kardx.internal.websocket.payloadText
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.WebSocketClient
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * 테스트에서 실행 중인 앱의 STOMP 엔드포인트에 붙어 보내고 받는 작은 클라이언트. 받은 프레임을 [MessageDocumentation] 으로
 * 문서화할 때의 연결·구독·재전송·대기 코드를 줄인다. 프레임 본문은 문자열 그대로 주고받는다 (문서에 실제 JSON 이 나간다).
 *
 * spring-messaging·spring-websocket 은 이 라이브러리의 선택 의존성이다. STOMP 를 쓰는 앱(`spring-boot-starter-websocket`)에는
 * 이미 있다. 기본 [WebSocketClient] 는 JSR-356 표준 클라이언트(Tomcat·Jetty 가 제공)다.
 *
 * ```kotlin
 * @Test
 * fun chat(provider: RestDocumentationContextProvider) {
 *     StompTestClient("ws://localhost:$port/ws", connectHeaders = mapOf("Authorization" to "Bearer token")).use { stomp ->
 *         val request = ChatSendRequest(roomId = 1, content = "안녕하세요")
 *         val frame = stomp.sendAndReceive("/app/chat.send", request, subscribeTo = "/topic/rooms.1")
 *
 *         val docs = MessageDocumentation(provider)
 *
 *         docs.connect("/ws") {
 *             headerValues(stomp.connectHeaders)
 *             headers("Authorization" headerMeans "Bearer 액세스 토큰")
 *         }
 *         docs.send("/app/chat.send", payload = request) { fields<ChatSendRequest>() }
 *         docs.subscribe("/topic/rooms.{roomId}", payload = frame.body) { fields<ChatMessage>() }
 *     }
 * }
 * ```
 *
 * @param url STOMP 엔드포인트 (`ws://localhost:8080/ws`)
 * @property connectHeaders CONNECT 프레임에 넣는 헤더 (`Authorization` 등). 문서화할 때 `headerValues` 에 그대로 넘길 수 있다
 * @param timeout 연결과 프레임 대기의 기본 제한 시간. 기본 5초
 * 서버가 STOMP `ERROR` 프레임을 보내거나 연결이 끊기면(인터셉터의 거절, 잘못된 목적지 …) 연결·[sendAndReceive]·
 * [StompSubscription.await] 가 제한 시간을 기다리지 않고 바로 `STOMP ERROR from server: <message 헤더>: <본문>` 으로 실패한다.
 *
 * @param webSocketClient WebSocket 연결에 쓸 클라이언트
 * @throws IllegalStateException 제한 시간 안에 연결하지 못하거나 서버가 연결을 거절하면
 */
@ExperimentalRestDocsApi
public class StompTestClient(
    url: String,
    public val connectHeaders: Map<String, String> = emptyMap(),
    private val timeout: Duration = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
    webSocketClient: WebSocketClient = StandardWebSocketClient(),
) : AutoCloseable {
    private val client = WebSocketStompClient(webSocketClient).apply { messageConverter = RawTextMessageConverter }
    private val failure = StompFailure()
    private val session: StompSession = connect(url)

    /**
     * [destination] 을 구독한다. 받은 프레임은 [StompSubscription.await] 로 꺼낸다.
     *
     * ```kotlin
     * val subscription = stomp.subscribe("/topic/rooms.1")
     * ```
     */
    public fun subscribe(destination: String): StompSubscription {
        val frames = LinkedBlockingQueue<StompFrame>()
        val subscription = session.subscribe(destination, FrameCollector(frames))

        failure.watch(frames)

        return StompSubscription(destination, frames, timeout, failure::message) {
            failure.unwatch(frames)

            if (session.isConnected) subscription.cancel()
        }
    }

    /**
     * [destination] 으로 [payload] 를 보낸다. 페이로드는 문자열·`ByteArray` 그대로, 객체는 설정한 ObjectMapper 로 JSON 이 된다.
     * `content-type` 헤더를 주지 않으면 `application/json` 이다.
     *
     * ```kotlin
     * stomp.send("/app/chat.send", ChatSendRequest(roomId = 1, content = "안녕"), mapOf("X-Trace" to "t-1"))
     * ```
     */
    public fun send(destination: String, payload: Any?, headers: Map<String, String> = emptyMap()) {
        val stompHeaders = stompHeaders(headers).apply {
            this.destination = destination

            if (contentType == null) set(StompHeaders.CONTENT_TYPE, JSON)
        }

        session.send(stompHeaders, payloadText(payload).orEmpty())
    }

    /**
     * [subscribeTo] 를 구독하고 [destination] 으로 [payload] 를 보낸 뒤, 구독으로 받은 첫 프레임을 돌려준다. 끝나면 구독을 닫는다.
     *
     * 구독이 브로커에 등록되기 전에 보낸 메시지는 사라질 수 있다 (Spring 의 simple broker 는 SUBSCRIBE 에 RECEIPT 를 보내지 않아
     * 등록을 확인할 방법이 없다). 그래서 프레임이 올 때까지 같은 페이로드를 **다시 보낸다**. 간격은 200ms 에서 시작해 두 배씩
     * 늘어 1초까지다. 처리에 시간이 걸리는 핸들러(DB 저장 등)는 같은 메시지를 두 번 이상 받을 수 있고, 돌려받는 프레임은 그중
     * 어느 전송의 결과일 수 있다. 한 번만 보내야 하면 [subscribe] → [send] → [StompSubscription.await] 를 직접 쓴다.
     * [timeout] 을 넘겨 기다리지 않고, 남은 시간이 첫 간격(200ms)보다 짧으면 다시 보내지 않고 기다리기만 한다.
     *
     * ```kotlin
     * val frame = stomp.sendAndReceive("/app/chat.send", request, subscribeTo = "/topic/rooms.1")
     * ```
     *
     * @throws IllegalStateException [timeout] 안에 프레임이 오지 않거나 서버가 `ERROR` 프레임을 보내면
     */
    public fun sendAndReceive(
        destination: String,
        payload: Any?,
        subscribeTo: String,
        headers: Map<String, String> = emptyMap(),
        timeout: Duration = this.timeout,
    ): StompFrame {
        val subscription = subscribe(subscribeTo)
        val deadline = System.nanoTime() + timeout.toNanos()
        var interval = FIRST_RESEND_INTERVAL

        try {
            var remaining = Duration.ofNanos(deadline - System.nanoTime())
            var first = true

            while (!remaining.isNegative && !remaining.isZero) {
                if (first || remaining >= FIRST_RESEND_INTERVAL) {
                    send(destination, payload, headers)
                }

                subscription.poll(minOf(interval, remaining))?.let { return it }
                interval = minOf(interval.multipliedBy(2), MAX_RESEND_INTERVAL)
                remaining = Duration.ofNanos(deadline - System.nanoTime())
                first = false
            }
        } finally {
            subscription.unsubscribe()
        }

        error(
            RestDocsErrorCode.MESSAGE_TIMEOUT.message(
                "No STOMP frame arrived on $subscribeTo within $timeout after sending to $destination.",
            ),
        )
    }

    /** 연결을 끊는다. */
    override fun close() {
        if (session.isConnected) session.disconnect()

        client.stop()
    }

    private fun connect(url: String): StompSession {
        val future = client.connectAsync(url, WebSocketHttpHeaders(), stompHeaders(connectHeaders), failure)

        awaitConnection(url, future)

        val reason = failure.message()

        if (reason != null) {
            disconnectLate(future)
        }

        check(reason == null) {
            RestDocsErrorCode.STOMP_CONNECT_FAILED.message("Could not connect to STOMP endpoint $url: $reason")
        }

        return future.get()
    }

    /** 서버가 CONNECT 를 ERROR 로 거절하면 세션 [future] 는 끝나지 않는다. 오류를 같이 기다려 바로 돌아온다. */
    private fun awaitConnection(url: String, future: CompletableFuture<StompSession>) {
        try {
            CompletableFuture.anyOf(future, failure.signal)
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            disconnectLate(future)

            throw IllegalStateException(
                RestDocsErrorCode.STOMP_CONNECT_FAILED.message(
                    "Could not connect to STOMP endpoint $url within $timeout.",
                ),
                e,
            )
        } catch (e: ExecutionException) {
            throw IllegalStateException(
                RestDocsErrorCode.STOMP_CONNECT_FAILED.message(
                    "Could not connect to STOMP endpoint $url: ${e.cause?.message}",
                ),
                e.cause ?: e,
            )
        }
    }

    /** 객체가 만들어지지 않아 close() 를 부를 수 없다. 늦게 맺어진 세션은 여기서 끊는다. */
    private fun disconnectLate(future: CompletableFuture<StompSession>) {
        future.thenAccept { late -> if (late.isConnected) late.disconnect() }
    }

    private fun stompHeaders(values: Map<String, String>) =
        StompHeaders().apply { values.forEach { (name, value) -> add(name, value) } }

    /** 받은 프레임을 [frames] 에 쌓는다. 본문은 [RawTextMessageConverter] 가 문자열로 바꿨다. */
    private class FrameCollector(private val frames: LinkedBlockingQueue<StompFrame>) : StompFrameHandler {
        override fun getPayloadType(headers: StompHeaders): Type = String::class.java

        override fun handleFrame(headers: StompHeaders, payload: Any?) {
            val values = headers.toSingleValueMap()

            frames += StompFrame(values, payload as? String ?: "")
        }
    }

    /**
     * 세션의 첫 오류(서버의 `ERROR` 프레임, 받은 프레임 처리 실패, 연결 끊김)를 기억하고, 기다리는 연결([signal])과 구독 대기열을
     * 깨운다. `DefaultStompSession` 은 `ERROR` 프레임을 세션 핸들러의 `handleFrame` 으로 넘긴다.
     */
    private class StompFailure : StompSessionHandlerAdapter() {
        private val reason = AtomicReference<String?>()
        private val queues: MutableSet<BlockingQueue<StompFrame>> = ConcurrentHashMap.newKeySet()

        val signal = CompletableFuture<Unit>()

        fun message(): String? = reason.get()

        fun watch(frames: BlockingQueue<StompFrame>) {
            queues += frames

            // 구독하기 전에 이미 실패했으면 바로 깨운다
            if (reason.get() != null) frames.offer(StompSubscription.WAKE)
        }

        fun unwatch(frames: BlockingQueue<StompFrame>) {
            queues -= frames
        }

        override fun handleFrame(headers: StompHeaders, payload: Any?) {
            val message = headers.getFirst(MESSAGE_HEADER)
            val body = (payload as? String)?.trim()
                .orEmpty()
            val detail = listOfNotNull(message, body.takeIf { it.isNotEmpty() }).joinToString(": ")

            fail("STOMP ERROR from server: ${detail.ifEmpty { "(no message)" }}")
        }

        override fun handleException(
            session: StompSession,
            command: StompCommand?,
            headers: StompHeaders,
            payload: ByteArray,
            exception: Throwable,
        ) {
            fail("Could not handle a STOMP ${command ?: "frame"} from server: ${exception.message}")
        }

        override fun handleTransportError(session: StompSession, exception: Throwable) {
            fail("STOMP connection failed: ${exception.message}")
        }

        private fun fail(message: String) {
            if (reason.compareAndSet(null, message)) {
                queues.forEach { it.offer(StompSubscription.WAKE) }
                signal.complete(Unit)
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 5L
        const val JSON = "application/json"
        const val MESSAGE_HEADER = "message"
        val FIRST_RESEND_INTERVAL: Duration = Duration.ofMillis(200)
        val MAX_RESEND_INTERVAL: Duration = Duration.ofSeconds(1)
    }
}
