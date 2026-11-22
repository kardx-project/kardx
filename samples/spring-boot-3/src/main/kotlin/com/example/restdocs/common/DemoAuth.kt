package com.example.restdocs.common

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageDeliveryException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder

/**
 * 샘플 코드: 메시지 API(STOMP·WebSocket·SSE)의 데모용 인증. 고정 토큰 [TOKEN] 하나만 받는다. 실제 앱에서 쓰지 않는다.
 *
 * 토큰 값은 AsyncAPI 문서 설명(build 파일의 `asyncApi.description`)에도 적어 두어, 문서 뷰어의 연결 시험에서 그대로 넣을 수 있다.
 * - STOMP: CONNECT 프레임 헤더 `Authorization: Bearer demo-token` ([StompAuthInterceptor])
 * - WebSocket·SSE: `Authorization` 헤더, 또는 헤더를 붙일 수 없는 브라우저용 쿼리 `?access_token=demo-token`
 */
object DemoAuth {
    /** 데모 토큰. */
    const val TOKEN = "demo-token"

    /** `Authorization` 헤더 값. */
    const val BEARER = "Bearer $TOKEN"

    /** 브라우저(WebSocket 핸드셰이크, EventSource)가 토큰을 보내는 쿼리 파라미터 이름. */
    const val QUERY_PARAMETER = "access_token"

    /** [authorization] 헤더나 [accessToken] 쿼리 값 중 하나가 데모 토큰이면 true. */
    fun accepts(authorization: String?, accessToken: String?): Boolean = authorization == BEARER || accessToken == TOKEN
}

/** 샘플 코드: STOMP CONNECT 프레임에 데모 토큰이 없으면 연결을 거절한다. 서버는 ERROR 프레임을 보내고 연결을 닫는다. */
class StompAuthInterceptor : ChannelInterceptor {
    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor?.command == StompCommand.CONNECT &&
            !DemoAuth.accepts(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION), null)
        ) {
            throw MessageDeliveryException(message, "Missing or invalid token. Send '$HEADER'.")
        }

        return message
    }

    private companion object {
        const val HEADER = "${HttpHeaders.AUTHORIZATION}: ${DemoAuth.BEARER}"
    }
}

/** 샘플 코드: WebSocket 핸드셰이크에 데모 토큰(헤더 또는 `?access_token=`)이 없으면 401 로 거절한다. */
class DemoTokenHandshakeInterceptor : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val accessToken = UriComponentsBuilder.fromUri(request.uri)
            .build()
            .queryParams
            .getFirst(DemoAuth.QUERY_PARAMETER)

        if (!DemoAuth.accepts(request.headers.getFirst(HttpHeaders.AUTHORIZATION), accessToken)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)

            return false
        }

        attributes["userId"] = DEMO_USER

        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit

    private companion object {
        /** 데모 토큰의 사용자. 환영 메시지의 `user` 가 된다 */
        const val DEMO_USER = "hong"
    }
}
