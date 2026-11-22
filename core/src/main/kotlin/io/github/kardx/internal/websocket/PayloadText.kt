package io.github.kardx.internal.websocket

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.jackson.serializeJson

/**
 * 메시지 페이로드를 문자열로 바꾼다. 문자열은 그대로, `ByteArray` 는 UTF-8 로 읽고, 그 밖의 객체는 설정한 ObjectMapper 로
 * JSON 직렬화한다. `MessageDocumentation` 과 `StompTestClient` 가 같은 규칙을 쓴다.
 */
internal fun payloadText(payload: Any?): String? = when (payload) {
    null -> null
    is CharSequence -> payload.toString()
    is ByteArray -> String(payload, Charsets.UTF_8)
    else -> serializeJson(RestDocsConfiguration.objectMapper(), payload)
}
