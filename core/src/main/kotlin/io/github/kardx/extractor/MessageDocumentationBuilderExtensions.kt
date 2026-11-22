package io.github.kardx.extractor

import io.github.kardx.dsl.MessageDirection
import io.github.kardx.dsl.MessageDocumentationBuilder
import kotlin.reflect.typeOf

/**
 * 페이로드 필드를 타입에서 추출해 등록한다. 메시지 방향에 맞는 추출 규칙을 고른다.
 * - `send`·`connect` (클라이언트가 보내는 메시지): [requestFieldsOf] 규칙. 기본값이 있는 프로퍼티는 optional,
 *   읽기 전용 프로퍼티는 뺀다
 * - `subscribe` (서버가 보내는 메시지): [responseFieldsOf] 규칙
 *
 * `request-fields-message-NN` 표와 AsyncAPI 메시지 `payload` 스키마가 된다.
 * 목록을 고치거나 합칠 때는 `fields(requestFieldsOf<T>() + ...)` 처럼 목록 버전을 쓴다.
 *
 * ```kotlin
 * docs.send("/app/chat.send", payload = body) { fields<ChatSendRequest>() }
 * docs.subscribe("/topic/rooms.1", payload = frame) { fields<ChatMessage>() }
 * ```
 *
 * @param path 추출한 필드 앞에 붙일 경로. 기본값은 페이로드 루트
 */
public inline fun <reified T> MessageDocumentationBuilder.fields(path: String = "") {
    val extracted = if (direction == MessageDirection.SUBSCRIBE) {
        responseFieldsOf(typeOf<T>(), path)
    } else {
        requestFieldsOf(typeOf<T>(), path)
    }

    fields(extracted)
}
