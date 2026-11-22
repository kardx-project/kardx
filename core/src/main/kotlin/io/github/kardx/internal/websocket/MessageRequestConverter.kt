package io.github.kardx.internal.websocket

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.OperationRequest
import org.springframework.restdocs.operation.OperationRequestFactory
import org.springframework.restdocs.operation.RequestConverter

/** [DocumentedMessage] 를 REST Docs 요청으로 바꾼다. 목적지는 `ws://localhost` URI, 페이로드는 본문이 된다. */
internal object MessageRequestConverter : RequestConverter<DocumentedMessage> {
    override fun convert(message: DocumentedMessage): OperationRequest {
        val headers = HttpHeaders()

        message.headers.forEach { (name, value) -> headers.add(name, value) }

        // 사용자가 content-type 을 주지 않았을 때만 기본값을 넣는다 (값이 두 개가 되지 않게)
        if (message.headers.keys.none { it.equals(HttpHeaders.CONTENT_TYPE, ignoreCase = true) }) {
            headers.contentType = MediaType.APPLICATION_JSON
        }

        return OperationRequestFactory().create(
            webSocketUri(message.destination),
            HttpMethod.POST,
            message.payload?.toByteArray(Charsets.UTF_8) ?: ByteArray(0),
            headers,
            emptyList(),
        )
    }
}
