package io.github.kardx.websocket

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.type
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.http.HttpHeaders
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompDecoder
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketExtension
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.AbstractWebSocketClient
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.Principal
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val NUL = '\u0000'

/**
 * 메모리 안의 STOMP 서버. `/app/x` 로 보낸 메시지를 `/topic/x` 구독자에게 그대로 전한다.
 * [dropFirstSends] 개의 SEND 는 버려서 구독 등록 전 메시지가 사라지는 실제 브로커를 흉내 낸다.
 * [requiredHeader] 가 CONNECT 에 없으면 ERROR 로 거절한다. [replyDelay] 만큼 늦게 답해 처리가 느린 핸들러를 흉내 내고,
 * [connectDelay] 만큼 늦게 CONNECTED 를 보내 늦은 연결을 흉내 낸다. 받은 DISCONNECT 는 [disconnects] 로 센다.
 * [rejectedSend] 로 보낸 SEND 에는 ERROR 프레임으로 답한다 (핸들러·인터셉터가 거절한 메시지).
 */
private class LoopbackStompServer(
    private val dropFirstSends: Int = 0,
    private val requiredHeader: String? = null,
    private val replyDelay: Duration = Duration.ZERO,
    private val connectDelay: Duration = Duration.ZERO,
    private val rejectedSend: String? = null,
) : AbstractWebSocketClient() {
    val connectHeaders = ConcurrentHashMap<String, String>()
    val sends = AtomicInteger()
    val disconnects = AtomicInteger()
    private val subscriptions = ConcurrentHashMap<String, String>()
    private val executor = Executors.newSingleThreadExecutor()

    override fun executeInternal(
        handler: WebSocketHandler,
        headers: HttpHeaders,
        uri: URI,
        subProtocols: List<String>,
        extensions: List<WebSocketExtension>,
        attributes: Map<String, Any>,
    ): CompletableFuture<WebSocketSession> {
        val session = LoopbackSession(uri) { session, text -> receive(handler, session, text) }

        handler.afterConnectionEstablished(session)

        return CompletableFuture.completedFuture(session)
    }

    private fun receive(handler: WebSocketHandler, session: WebSocketSession, text: String) {
        StompDecoder().decode(ByteBuffer.wrap(text.toByteArray()))
            .map { StompHeaderAccessor.wrap(it) to String(it.payload) }
            .forEach { (frame, body) -> answer(frame, body)?.let { reply(handler, session, it) } }
    }

    private fun answer(frame: StompHeaderAccessor, body: String): List<String>? = when (frame.command) {
        StompCommand.CONNECT, StompCommand.STOMP -> {
            frame.toNativeHeaderMap()
                .forEach { (name, values) -> connectHeaders[name] = values.first() }

            if (requiredHeader != null && !connectHeaders.containsKey(requiredHeader)) {
                listOf("ERROR\nmessage:missing $requiredHeader\n\n$NUL")
            } else {
                listOf("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n$NUL")
            }
        }

        StompCommand.SUBSCRIBE -> {
            subscriptions[requireNotNull(frame.subscriptionId)] = requireNotNull(frame.destination)
            null
        }

        StompCommand.UNSUBSCRIBE -> {
            subscriptions.remove(frame.subscriptionId)
            null
        }

        StompCommand.SEND -> if (frame.destination == rejectedSend) {
            listOf(
                "ERROR\nmessage:Access denied\ncontent-type:text/plain\n\nno permission for ${frame.destination}$NUL",
            )
        } else {
            deliver(requireNotNull(frame.destination), body)
        }

        StompCommand.DISCONNECT -> {
            disconnects.incrementAndGet()
            null
        }

        else -> null
    }

    private fun deliver(destination: String, body: String): List<String>? {
        if (sends.incrementAndGet() <= dropFirstSends) return null

        val topic = destination.replaceFirst("/app/", "/topic/")

        return subscriptions.filterValues { it == topic }
            .keys
            .map { id ->
                listOf(
                    "MESSAGE",
                    "destination:$topic",
                    "subscription:$id",
                    "message-id:m-${sends.get()}",
                    "content-type:application/json",
                    "content-length:${body.toByteArray().size}",
                    "",
                    body + NUL,
                ).joinToString("\n")
            }
    }

    private fun reply(handler: WebSocketHandler, session: WebSocketSession, frames: List<String>) {
        frames.forEach { frame ->
            executor.execute {
                if (frame.startsWith("MESSAGE")) Thread.sleep(replyDelay.toMillis())

                if (frame.startsWith("CONNECTED")) Thread.sleep(connectDelay.toMillis())

                handler.handleMessage(session, TextMessage(frame))
            }
        }
    }
}

/** 클라이언트가 보내는 메시지를 [onMessage] 로 넘기는 세션. */
private class LoopbackSession(private val uri: URI, private val onMessage: (WebSocketSession, String) -> Unit) :
    WebSocketSession {
    @Volatile
    private var open = true

    override fun getId(): String = "loopback"
    override fun getUri(): URI = uri
    override fun getHandshakeHeaders(): HttpHeaders = HttpHeaders()
    override fun getAttributes(): MutableMap<String, Any> = mutableMapOf()
    override fun getPrincipal(): Principal? = null
    override fun getLocalAddress(): InetSocketAddress = InetSocketAddress("localhost", 0)
    override fun getRemoteAddress(): InetSocketAddress = InetSocketAddress("localhost", 0)
    override fun getAcceptedProtocol(): String = "v12.stomp"
    override fun setTextMessageSizeLimit(messageSizeLimit: Int) = Unit
    override fun getTextMessageSizeLimit(): Int = Int.MAX_VALUE
    override fun setBinaryMessageSizeLimit(messageSizeLimit: Int) = Unit
    override fun getBinaryMessageSizeLimit(): Int = Int.MAX_VALUE
    override fun getExtensions(): List<WebSocketExtension> = emptyList()
    override fun sendMessage(message: WebSocketMessage<*>) {
        onMessage(this, (message as TextMessage).payload)
    }

    override fun isOpen(): Boolean = open
    override fun close() = close(CloseStatus.NORMAL)
    override fun close(status: CloseStatus) {
        open = false
    }
}

private class ChatSend(val roomId: Int, val content: String)

class StompTestClientTest :
    BehaviorSpec({
        given("연결 헤더를 준 STOMP 클라이언트") {
            val server = LoopbackStompServer(dropFirstSends = 2, requiredHeader = "Authorization")

            then("CONNECT 헤더를 보내고, 받을 때까지 다시 보내 첫 프레임을 돌려준다") {
                StompTestClient(
                    "ws://localhost/ws",
                    connectHeaders = mapOf("Authorization" to "Bearer t"),
                    webSocketClient = server,
                ).use { stomp ->
                    val frame = stomp.sendAndReceive("/app/chat", ChatSend(1, "안녕"), subscribeTo = "/topic/chat")

                    server.connectHeaders["Authorization"] shouldBe "Bearer t"
                    stomp.connectHeaders shouldBe mapOf("Authorization" to "Bearer t")
                    frame.body shouldBe """{"roomId":1,"content":"안녕"}"""
                    frame.headers["destination"] shouldBe "/topic/chat"
                    (server.sends.get() > 2) shouldBe true
                }
            }
        }

        given("처리가 느린 핸들러 (답이 350ms 뒤에 온다)") {
            val server = LoopbackStompServer(replyDelay = Duration.ofMillis(350))

            then("다시 보내는 간격이 늘어나므로 같은 페이로드를 여러 번 보내지 않는다") {
                StompTestClient("ws://localhost/ws", webSocketClient = server).use { stomp ->
                    val frame = stomp.sendAndReceive("/app/slow", ChatSend(1, "느림"), subscribeTo = "/topic/slow")

                    frame.body shouldBe """{"roomId":1,"content":"느림"}"""
                    (server.sends.get() <= 2) shouldBe true
                }
            }
        }

        given("subscribe·send·await") {
            then("구독으로 받은 프레임을 순서대로 꺼내고, 문자열 페이로드는 그대로 보낸다") {
                StompTestClient("ws://localhost/ws", webSocketClient = LoopbackStompServer()).use { stomp ->
                    val subscription = stomp.subscribe("/topic/echo")

                    stomp.send("/app/echo", """{"n":1}""")
                    stomp.send("/app/echo", """{"n":2}""")

                    subscription.await().body shouldBe """{"n":1}"""
                    subscription.await().body shouldBe """{"n":2}"""

                    subscription.unsubscribe()
                    subscription.unsubscribe()
                }
            }

            then("시간 안에 프레임이 없으면 영어 메시지로 실패한다") {
                StompTestClient("ws://localhost/ws", webSocketClient = LoopbackStompServer()).use { stomp ->
                    shouldThrow<IllegalStateException> {
                        stomp.subscribe("/topic/silent")
                            .await(Duration.ofMillis(200))
                    }.message shouldBe RestDocsErrorCode.MESSAGE_TIMEOUT.message(
                        "No STOMP frame arrived on /topic/silent within PT0.2S.",
                    )
                }
            }
        }

        given("서버가 CONNECT 를 거절하면") {
            then("제한 시간을 기다리지 않고 ERROR 프레임의 이유로 실패한다") {
                val started = System.nanoTime()
                val message = shouldThrow<IllegalStateException> {
                    StompTestClient(
                        "ws://localhost/ws",
                        timeout = Duration.ofSeconds(5),
                        webSocketClient = LoopbackStompServer(requiredHeader = "Authorization"),
                    )
                }.message

                message shouldContain "Could not connect to STOMP endpoint ws://localhost/ws"
                message shouldContain "STOMP ERROR from server: missing Authorization"
                (Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(2)) shouldBe true
            }
        }

        given("보낸 메시지를 서버가 ERROR 프레임으로 거절하면") {
            then("sendAndReceive 는 제한 시간을 기다리지 않고 ERROR 의 내용으로 실패한다") {
                val server = LoopbackStompServer(rejectedSend = "/app/secret")

                StompTestClient("ws://localhost/ws", webSocketClient = server).use { stomp ->
                    val started = System.nanoTime()
                    val message = shouldThrow<IllegalStateException> {
                        stomp.sendAndReceive("/app/secret", ChatSend(1, "x"), subscribeTo = "/topic/secret")
                    }.message

                    message shouldBe "STOMP ERROR from server: Access denied: no permission for /app/secret"
                    (Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(2)) shouldBe true
                }
            }

            then("이미 기다리는 await 도 ERROR 로 깨어난다") {
                StompTestClient(
                    "ws://localhost/ws",
                    webSocketClient = LoopbackStompServer(rejectedSend = "/app/secret"),
                )
                    .use { stomp ->
                        val subscription = stomp.subscribe("/topic/secret")

                        stomp.send("/app/secret", "{}")

                        shouldThrow<IllegalStateException> { subscription.await() }.message shouldContain
                            "Access denied"
                    }
            }
        }

        given("답이 없는 목적지로 sendAndReceive") {
            then("제한 시간을 넘겨 기다리지 않고, 기다릴 시간이 없으면 다시 보내지 않는다") {
                val server = LoopbackStompServer()

                StompTestClient("ws://localhost/ws", webSocketClient = server).use { stomp ->
                    val started = System.nanoTime()

                    shouldThrow<IllegalStateException> {
                        stomp.sendAndReceive(
                            "/app/none",
                            "{}",
                            subscribeTo = "/topic/other",
                            timeout = Duration.ofMillis(700),
                        )
                    }

                    // 0ms·200ms 에 보내고, 600ms 에는 남은 100ms 가 첫 간격보다 짧아 보내지 않는다
                    (Duration.ofNanos(System.nanoTime() - started) < Duration.ofMillis(1000)) shouldBe true
                    server.sends.get() shouldBe 2
                }
            }
        }

        given("제한 시간이 지난 뒤에 연결되는 서버") {
            val server = LoopbackStompServer(connectDelay = Duration.ofMillis(400))

            then("생성자는 실패하고, 늦게 맺어진 세션은 끊는다") {
                shouldThrow<IllegalStateException> {
                    StompTestClient("ws://localhost/ws", timeout = Duration.ofMillis(100), webSocketClient = server)
                }.message shouldContain "within PT0.1S"

                val deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos()

                while (server.disconnects.get() == 0 && System.nanoTime() < deadline) {
                    Thread.sleep(20)
                }

                server.disconnects.get() shouldBe 1
            }
        }

        given("받은 프레임을 MessageDocumentation 으로 문서화하면") {
            then("구독 메시지 스니펫에 실제 본문이 들어간다") {
                val output = Files.createTempDirectory("stomp").toFile()
                val documentation = ManualRestDocumentation(output.path)

                documentation.beforeTest(StompTestClientTest::class.java, "chat")

                StompTestClient("ws://localhost/ws", webSocketClient = LoopbackStompServer()).use { stomp ->
                    val request = ChatSend(7, "hi")
                    val frame = stomp.sendAndReceive("/app/rooms", request, subscribeTo = "/topic/rooms")
                    val docs = MessageDocumentation(documentation)

                    docs.send("/app/rooms", payload = request) {
                        fields("roomId" type NUMBER means "방", "content" type STRING means "내용")
                    }
                    docs.subscribe("/topic/rooms", payload = frame.body) {
                        fields("roomId" type NUMBER means "방", "content" type STRING means "내용")
                    }
                }

                documentation.afterTest()

                File(output, "stomp-test-client-test/chat/message-02.adoc").readText() shouldContain "\"roomId\" : 7"
            }
        }
    })
