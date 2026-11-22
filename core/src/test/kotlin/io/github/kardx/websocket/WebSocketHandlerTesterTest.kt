package io.github.kardx.websocket

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.AbstractWebSocketHandler
import java.time.Duration
import kotlin.concurrent.thread

private class SampleHandler : AbstractWebSocketHandler() {
    var closedWith: CloseStatus? = null

    override fun afterConnectionEstablished(session: WebSocketSession) {
        session.sendMessage(TextMessage("hello ${session.attributes["user"]} from ${session.uri?.path}"))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        when (message.payload) {
            "bye" -> session.close(CloseStatus.GOING_AWAY)

            "later" -> thread {
                Thread.sleep(50)
                session.sendMessage(TextMessage("late"))
            }

            else -> {
                session.sendMessage(TextMessage("echo:${message.payload}"))
                session.sendMessage(TextMessage("done"))
            }
        }
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        session.sendMessage(BinaryMessage(message.payload))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        closedWith = status
    }
}

class WebSocketHandlerTesterTest :
    BehaviorSpec({
        given("WebSocket 핸들러") {
            `when`("연결하고 메시지를 보내면") {
                val handler = SampleHandler()
                val tester = WebSocketHandlerTester(handler, path = "/ws/sample", attributes = mapOf("user" to "hong"))

                then("연결 중에 보낸 메시지와 처리 중에 보낸 메시지를 따로 돌려준다") {
                    tester.connect() shouldContainExactly listOf("hello hong from /ws/sample")
                    tester.send("hi") shouldContainExactly listOf("echo:hi", "done")
                    tester.send("bin".toByteArray()) shouldContainExactly listOf("bin")
                    tester.messages.size shouldBe 4
                }

                then("닫으면 afterConnectionClosed 가 NORMAL 로 호출된다") {
                    tester.close()

                    handler.closedWith shouldBe CloseStatus.NORMAL
                }
            }

            `when`("핸들러가 세션을 닫으면") {
                val handler = SampleHandler()

                then("종료 코드가 남고 afterConnectionClosed 에 그 코드가 전달된다") {
                    WebSocketHandlerTester(handler).use { ws ->
                        ws.send("bye")

                        ws.isOpen shouldBe false
                        ws.closeCode shouldBe 1001
                    }

                    handler.closedWith shouldBe CloseStatus.GOING_AWAY
                }
            }

            `when`("다른 스레드에서 나중에 보내면") {
                then("awaitMessages 로 기다린다") {
                    WebSocketHandlerTester(SampleHandler()).use { ws ->
                        ws.connect()

                        ws.send("later") shouldBe emptyList()
                        ws.awaitMessages(2, Duration.ofSeconds(2)).last() shouldBe "late"
                    }
                }
            }
        }
    })
