// StompTestClient·WebSocketHandlerTester 는 실험 API 다 (ExperimentalRestDocsApi)
@file:OptIn(ExperimentalRestDocsApi::class)

package com.example.restdocs.echo

import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.dsl.MessageProtocol
import io.github.kardx.extractor.fields
import io.github.kardx.websocket.MessageDocumentation
import io.github.kardx.websocket.WebSocketHandlerTester
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.RestDocumentationContextProvider

/**
 * 순수 WebSocket 핸들러(`TextWebSocketHandler`) 예제.
 *
 * 서버를 띄우지 않고 `WebSocketHandlerTester` 로 핸들러를 직접 호출한다.
 * 핸들러가 세션으로 보낸 메시지를 그대로 `MessageDocumentation` 에 넘겨 문서화한다.
 * 핸드셰이크를 거치지 않으므로 토큰 확인(DemoTokenHandshakeInterceptor)이 넣는 세션 속성 `userId` 를 직접 준다.
 * (실제 서버와 클라이언트로 주고받는 방식은 `ChatMessageDocsTest` 참고)
 */
@DisplayName("에코 (WebSocket)")
class EchoWebSocketDocsTest : RestDocsTestSupport() {

    @Test
    @DisplayName("연결하고 문자열 보내기")
    fun echo(provider: RestDocumentationContextProvider) {
        WebSocketHandlerTester(EchoHandler(), path = "/ws/echo", attributes = mapOf("userId" to "hong")).use { ws ->
            val welcome = ws.connect().single()
            val request = """{"text":"hello"}"""
            val reply = ws.send(request).single()

            val docs = MessageDocumentation(provider, MessageProtocol.WEBSOCKET)
            docs.subscribe("/ws/echo", payload = welcome) {
                description = "연결 직후 한 번 받는다. 샘플 앱은 핸드셰이크에 데모 토큰을 요구한다: " +
                    "`?access_token=demo-token` 또는 `Authorization: Bearer demo-token`."
                name = "welcome"
                fields<WelcomeMessage>()
            }

            docs.send("/ws/echo", payload = request) {
                description = "JSON 텍스트 메시지를 보낸다. `text` 가 비어 있으면 서버가 1008 로 연결을 끊는다."
                name = "echoRequest"
                fields<EchoRequest>()
            }

            docs.subscribe("/ws/echo", payload = reply) {
                description = "보낸 메시지마다 가공한 결과를 한 번 받는다."
                name = "echoResponse"
                fields<EchoResponse>()
            }
        }
    }

    @Test
    fun closesOnBlankText() {
        WebSocketHandlerTester(EchoHandler(), path = "/ws/echo").use { ws ->
            ws.send("""{"text":""}""")

            check(!ws.isOpen && ws.closeCode == 1008)
        }
    }
}
