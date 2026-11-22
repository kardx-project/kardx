package io.github.kardx.dsl

/** 메시지 방향. */
public enum class MessageDirection {
    /** 클라이언트 → 서버 (STOMP SEND) */
    SEND,

    /** 서버 → 클라이언트 (STOMP SUBSCRIBE 로 받는 MESSAGE) */
    SUBSCRIBE,

    /** 연결 (STOMP CONNECT 프레임 헤더, WebSocket 핸드셰이크 헤더). 인증 헤더 등 연결마다 한 번 보내는 값 */
    CONNECT,
}
