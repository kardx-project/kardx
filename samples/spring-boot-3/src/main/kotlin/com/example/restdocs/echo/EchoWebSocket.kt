package com.example.restdocs.echo

import com.example.restdocs.common.DemoTokenHandshakeInterceptor
import io.github.kardx.annotation.Description
import org.springframework.boot.json.JsonParserFactory
import org.springframework.boot.json.JsonWriter
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler

data class WelcomeMessage(
    @Description("메시지 종류 (`welcome`)") val type: String,
    @Description("접속한 사용자. 핸드셰이크에서 토큰을 확인하고 세션 속성 `userId` 로 넘긴다") val user: String,
)

data class EchoRequest(@Description("보낼 문자열") val text: String)

data class EchoResponse(
    @Description("받은 문자열") val text: String,
    @Description("문자 수") val length: Int,
    @Description("대문자로 바꾼 문자열") val upper: String,
)

/**
 * STOMP 없는 순수 WebSocket 예제.
 * 연결되면 환영 메시지를 보내고, 받은 JSON 의 text 를 가공해 돌려준다. text 가 비어 있으면 연결을 끊는다.
 */
class EchoHandler : TextWebSocketHandler() {
    private val parser = JsonParserFactory.getJsonParser()
    private val writer = JsonWriter.standard<Map<String, Any>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val user = session.attributes["userId"]?.toString() ?: "anonymous"
        session.sendMessage(TextMessage(writer.writeToString(mapOf("type" to "welcome", "user" to user))))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val text =
            parser.parseMap(message.payload)["text"]
                ?.toString()
                .orEmpty()

        if (text.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION)

            return
        }

        val reply = mapOf("text" to text, "length" to text.length, "upper" to text.uppercase())
        session.sendMessage(TextMessage(writer.writeToString(reply)))
    }
}

@Configuration
@EnableWebSocket
class EchoWebSocketConfig : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // 샘플 코드: 핸드셰이크에 데모 토큰(?access_token=demo-token 또는 Authorization 헤더)이 없으면 401 (DemoAuth)
        registry.addHandler(EchoHandler(), "/ws/echo")
            .addInterceptors(DemoTokenHandshakeInterceptor())
    }
}
