package io.github.kardx.internal.sse

import org.springframework.http.MediaType
import org.springframework.restdocs.payload.PayloadSubsectionExtractor
import org.springframework.restdocs.snippet.SnippetException

/**
 * SSE 본문에서 [eventName] 첫 이벤트의 `data` 를 꺼내는 subsection 추출기.
 * 스니펫 이름은 `response-fields-event-<이름>` 이다. 이름의 파일에 쓸 수 없는 문자(`:`, `/` 등)는 `-` 로 바꾼다.
 */
internal class SseEventDataExtractor(
    private val eventName: String,
    private val subsectionId: String = "event-" + eventName.replace(Regex("[^A-Za-z0-9_.\\-]+"), "-"),
) : PayloadSubsectionExtractor<SseEventDataExtractor> {

    override fun extractSubsection(payload: ByteArray, contentType: MediaType?): ByteArray {
        val event = SseParser.parse(String(payload, Charsets.UTF_8))
            .firstOrNull { it.name == eventName }
            ?: throw SnippetException("Event '$eventName' was not found in the stream")

        return event.data.toByteArray(Charsets.UTF_8)
    }

    override fun getSubsectionId(): String = subsectionId

    override fun withSubsectionId(subsectionId: String): SseEventDataExtractor =
        SseEventDataExtractor(eventName, subsectionId)
}
