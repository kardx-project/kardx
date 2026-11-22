package com.example.restdocs.chat

import com.example.restdocs.common.StompAuthInterceptor
import io.github.kardx.annotation.Description
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.stereotype.Controller
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.util.concurrent.atomic.AtomicLong

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    // 샘플 코드: CONNECT 에 데모 토큰(Authorization: Bearer demo-token)이 없으면 거절한다 (DemoAuth)
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(StompAuthInterceptor())
    }
}

data class ChatSendRequest(
    @Description("채팅방 ID") val roomId: Long,
    @Description("메시지 내용") @field:NotBlank @field:Size(max = 500) val content: String,
)

data class ChatMessage(
    @Description("메시지 ID") val id: Long,
    @Description("채팅방 ID") val roomId: Long,
    @Description("보낸 사람") val sender: String,
    @Description("메시지 내용") val content: String,
)

/** STOMP 예제. `/app/chat.send` 로 받은 메시지를 `/topic/rooms.{roomId}` 구독자에게 보낸다. */
@Controller
class ChatController(private val messagingTemplate: SimpMessagingTemplate) {
    private val sequence = AtomicLong()

    @MessageMapping("/chat.send")
    fun send(request: ChatSendRequest) {
        val message = ChatMessage(sequence.incrementAndGet(), request.roomId, "hong", request.content)
        messagingTemplate.convertAndSend("/topic/rooms.${request.roomId}", message)
    }
}
