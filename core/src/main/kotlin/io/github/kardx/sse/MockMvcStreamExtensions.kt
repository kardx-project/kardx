package io.github.kardx.sse

import io.github.kardx.internal.documentation.StreamSnapshot
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Duration

/**
 * 끝나지 않는 스트림(SSE `SseEmitter` 등)에서 처음 [eventCount] 개 이벤트를 받아 문서화할 수 있게 돌려준다.
 * [io.github.kardx.mockmvc.performAsync] 는 응답이 끝날 때까지 기다리므로 `complete()` 하지 않는 스트림에는 이 함수를 쓴다.
 *
 * 받은 이벤트까지의 응답을 고정해서 돌려주므로, 문서화하는 동안 스트림이 더 쓰여도 문서는 매번 같다.
 * [whileStreaming] 은 구독한 뒤 실행된다. 다른 요청으로 이벤트를 일으킬 때 쓴다.
 *
 * ```kotlin
 * mockMvc.performStream(get("/api/todos/stream").accept(MediaType.TEXT_EVENT_STREAM), eventCount = 2) {
 *     mockMvc.perform(post("/api/todos").jsonContent(CreateTodoRequest("문서 배포"))) // todo-created 이벤트를 일으킨다
 * }.andStatus(200)
 *     .andDocument { events("connected" eventMeans "연결", "todo-created" eventMeans "할 일 생성") }
 * ```
 *
 * @param eventCount 받을 이벤트 수 (기본 1). 빈 줄로 끝난 이벤트만 세고 주석(`:ping`)은 세지 않는다.
 *   이름이 블록의 `events(...)`(이벤트 설명)와 겹치지 않게 `eventCount` 다
 * @param timeout 이 시간 안에 [eventCount] 개를 받지 못하면 실패한다 (기본 5초). 오류 메시지에 받은 수와 다음에 할 일이 나온다
 * @throws IllegalArgumentException [eventCount] 가 0 이하이면
 */
public fun MockMvc.performStream(
    requestBuilder: RequestBuilder,
    eventCount: Int = 1,
    timeout: Duration = Duration.ofSeconds(DEFAULT_STREAM_TIMEOUT_SECONDS),
    whileStreaming: () -> Unit = {},
): ResultActions {
    require(eventCount > 0) { "eventCount must be positive" }

    val result = perform(requestBuilder)
        .andExpect(MockMvcResultMatchers.request().asyncStarted())
        .andReturn()

    whileStreaming()

    return StreamSnapshot.take(result, eventCount, timeout)
}

private const val DEFAULT_STREAM_TIMEOUT_SECONDS = 5L
