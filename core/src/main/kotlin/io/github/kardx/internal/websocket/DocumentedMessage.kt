package io.github.kardx.internal.websocket

import io.github.kardx.dsl.MessageDirection

/** 문서화할 메시지. REST Docs 에는 요청 모양으로 넘긴다. */
internal data class DocumentedMessage(
    val direction: MessageDirection,
    val destination: String,
    val payload: String?,
    val description: String?,
    val headers: Map<String, String>,
)
