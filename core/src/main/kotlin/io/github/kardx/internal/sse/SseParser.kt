package io.github.kardx.internal.sse

/** 파싱된 SSE 이벤트 하나. */
internal data class SseEvent(val name: String, val data: String, val id: String?)

/**
 * `text/event-stream` 본문을 이벤트 목록으로 파싱한다 (WHATWG HTML 의 SSE 규칙).
 *
 * - 빈 줄이 이벤트 경계다. `data:` 여러 줄은 개행으로 잇는다.
 * - `:` 로 시작하는 줄은 주석이다. 콜론 뒤 공백 하나는 값에서 뺀다.
 * - `event:` 가 없거나 비어 있으면 이름은 `message` 다. `data` 가 없는 블록은 이벤트가 아니다.
 * - 본문 앞의 BOM 은 무시한다.
 */
internal object SseParser {
    fun parse(text: String): List<SseEvent> {
        val buffer = EventBuffer()
        val lines = text.removePrefix("\uFEFF")
            .lines()
        val events = lines.mapNotNull { line ->
            when {
                line.isEmpty() -> buffer.dispatch()

                // 주석 줄
                line.startsWith(":") -> null

                else -> {
                    buffer.accept(line.substringBefore(':'), fieldValue(line))
                    null
                }
            }
        }

        return events + listOfNotNull(buffer.dispatch())
    }

    /** `name: value` 의 value. 콜론 뒤 공백 하나는 뺀다. 콜론이 없으면 빈 값. */
    private fun fieldValue(line: String): String = if (':' in line) {
        line.substringAfter(':')
            .removePrefix(" ")
    } else {
        ""
    }

    /** 빈 줄 사이의 필드를 모은다. `id` 는 규칙대로 다음 이벤트에도 남는다. */
    private class EventBuffer {
        private var name: String? = null
        private var id: String? = null
        private val data = mutableListOf<String>()

        fun accept(field: String, value: String) {
            when (field) {
                "event" -> name = value.ifEmpty { null }
                "data" -> data += value
                "id" -> id = value
                else -> Unit // retry 와 모르는 필드는 규칙대로 무시한다
            }
        }

        /** 모은 이벤트를 내보내고 비운다. `data` 가 없는 블록은 이벤트가 아니라 null. */
        fun dispatch(): SseEvent? {
            val event = data.takeIf { it.isNotEmpty() }
                ?.let { SseEvent(name ?: DEFAULT_EVENT, it.joinToString("\n"), id) }

            name = null
            data.clear()

            return event
        }
    }

    private const val DEFAULT_EVENT = "message"
}
