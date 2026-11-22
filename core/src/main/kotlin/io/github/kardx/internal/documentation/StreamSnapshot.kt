package io.github.kardx.internal.documentation

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.reflect.overrideGetter
import io.github.kardx.internal.sse.SseParser
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import java.time.Duration

/**
 * 끝나지 않는 스트림 응답에서 처음 몇 개 이벤트까지만 잘라 고정한다.
 * 스트림은 문서화하는 동안에도 계속 쓰이므로, 고정본으로 문서를 만들어야 결과가 매번 같다.
 */
internal object StreamSnapshot {
    private const val POLL_MILLIS = 5L
    private val EVENT_END = Regex("\r?\n\r?\n")

    /** [eventCount] 개 이벤트가 끝까지 쓰일 때까지 기다린 뒤, 그 이벤트까지만 담은 결과를 돌려준다. */
    fun take(result: MvcResult, eventCount: Int, timeout: Duration): ResultActions {
        val response = result.response
        val deadline = System.nanoTime() + timeout.toNanos()

        while (true) {
            val body = completeEvents(response.getContentAsString(Charsets.UTF_8), eventCount)

            if (body != null) {
                return StreamResultActions(freeze(result, copyOf(response, body)))
            }

            if (System.nanoTime() > deadline) {
                val received = SseParser.parse(response.getContentAsString(Charsets.UTF_8)).size

                error(
                    RestDocsErrorCode.STREAM_TIMEOUT.message(
                        "Expected $eventCount stream events within $timeout but received $received. " +
                            "Increase `timeout`, or trigger the events in `whileStreaming`.",
                    ),
                )
            }

            Thread.sleep(POLL_MILLIS)
        }
    }

    /** 빈 줄로 끝난 이벤트가 [count] 개 이상이면 그 이벤트까지의 본문, 아니면 null. 주석 블록은 세지 않는다. */
    fun completeEvents(content: String, count: Int): String? {
        var seen = 0
        var start = 0

        for (end in EVENT_END.findAll(content)) {
            if (SseParser.parse(content.substring(start, end.range.first)).isNotEmpty()) {
                seen++
            }

            start = end.range.last + 1

            if (seen == count) return content.substring(0, start)
        }

        return null
    }

    private fun copyOf(source: MockHttpServletResponse, body: String) = MockHttpServletResponse().apply {
        status = source.status

        source.headerNames.filterNot { it.equals("Content-Type", ignoreCase = true) }
            .forEach { name ->
                source.getHeaders(name)
                    .forEach { addHeader(name, it) }
            }

        source.contentType?.let { contentType = it }
        characterEncoding = Charsets.UTF_8.name()
        outputStream.write(body.toByteArray(Charsets.UTF_8))
    }

    /** 응답만 고정본으로 바꾼 MvcResult. Spring 버전마다 메서드가 달라도 되도록 프록시로 위임한다. */
    private fun freeze(result: MvcResult, response: MockHttpServletResponse): MvcResult =
        overrideGetter(MvcResult::class.java, result, "getResponse", response)
}

/** [io.github.kardx.sse.performStream] 이 돌려주는 고정된 결과. 끝나지 않는 스트림이라는 표시이기도 하다. */
internal class StreamResultActions(result: MvcResult) : MvcResultActions(result)
