package io.github.kardx.mockmvc

import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * 비동기 응답(SSE `SseEmitter`, `DeferredResult`, `Callable` 등)을 끝까지 받아 [ResultActions] 로 돌려준다.
 * 요청이 비동기로 시작됐는지 확인한 뒤 async dispatch 를 수행한다. 컨트롤러는 응답을 완료(`complete()`)해야 한다.
 * 끝나지 않는 스트림은 [io.github.kardx.sse.performStream] 을 쓴다.
 *
 * ```kotlin
 * mockMvc.performAsync(get("/api/v1/notifications/stream"))
 *     .andStatus(HttpStatus.OK)
 *     .andDocument { events("notification" eventMeans "새 알림" withFields responseFieldsOf<Notification>()) }
 * ```
 */
public fun MockMvc.performAsync(requestBuilder: RequestBuilder): ResultActions {
    val started = perform(requestBuilder)
        .andExpect(MockMvcResultMatchers.request().asyncStarted())
        .andReturn()

    return perform(MockMvcRequestBuilders.asyncDispatch(started))
}
