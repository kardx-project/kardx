package io.github.kardx.internal.asyncapi

import io.github.kardx.dsl.MessageProtocol

/** AsyncAPI 문서 안의 키(채널·operation·메시지 ID) 규칙과 서버 프로토콜 매칭. */
internal object AsyncApiIds {
    private val NON_SLUG = Regex("[^A-Za-z0-9]+")
    private val NON_IDENTIFIER = Regex("[^A-Za-z0-9_.\\-]+")

    /** 주소·방향으로 만드는 ID. 영숫자 외의 문자를 `-` 로 바꾸고 소문자로 만든다 (`/app/chat.send` → `app-chat-send`). */
    fun slug(text: String): String = text.replace(NON_SLUG, "-")
        .trim('-')
        .lowercase()

    /** 사용자가 준 이름을 AsyncAPI 키 규칙(`[A-Za-z0-9_.-]`)에 맞춘다. 대소문자는 유지한다. */
    fun identifier(text: String): String = text.replace(NON_IDENTIFIER, "-")
        .trim('-')
        .ifEmpty { "message" }

    /** [taken] 에 없는 ID. [base] 가 이미 있으면 `-2`, `-3` … 을 붙인다. */
    fun uniqueId(taken: Set<String>, base: String): String = generateSequence(1) { it + 1 }
        .map { if (it == 1) base else "$base-$it" }
        .first { it !in taken }

    /** 메시지 프로토콜(STOMP, WebSocket, SSE)로 그 서버에 연결할 수 있는지. 모르는 프로토콜은 모든 서버와 맞는다. */
    fun protocolMatches(messageProtocol: String, serverProtocol: String): Boolean =
        MessageProtocol.of(messageProtocol)?.matchesServer(serverProtocol) ?: true
}
