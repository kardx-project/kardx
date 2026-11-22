package com.example.restdocs.notification

import com.example.restdocs.common.DemoAuth
import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.dsl.eventMeans
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.withFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.bearerToken
import io.github.kardx.mockmvc.performAsync
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Server-Sent Events 예제.
 *
 * `performAsync` 로 스트림을 끝까지 받고, `events` 로 이벤트 목록과 이벤트별 data 필드를 문서화한다.
 * 문서화한 필수 이벤트가 없거나 문서화하지 않은 이벤트가 오면 테스트가 실패한다.
 */
@DisplayName("알림 스트림 API")
class NotificationApiDocsTest : RestDocsTestSupport() {

    @Test
    @DisplayName("알림 구독 (SSE)")
    fun subscribe() {
        mockMvc.performAsync(
            get("/api/v1/notifications/stream").param(DemoAuth.QUERY_PARAMETER, DemoAuth.TOKEN)
                .accept(MediaType.TEXT_EVENT_STREAM),
        ).andStatus(HttpStatus.OK)
            .andDocument {
                description = "알림을 Server-Sent Events 로 받는다. " +
                    "연결 직후 `connected` 이벤트가 한 번 오고, 이후 알림마다 `notification` 이벤트가 온다. " +
                    "토큰이 없으면 401 이다."
                queryParameters(
                    DemoAuth.QUERY_PARAMETER queryMeans "액세스 토큰 (샘플 앱은 `demo-token`). " +
                        "헤더를 붙일 수 없는 브라우저 EventSource 용. `Authorization` 헤더로 보내도 된다" isOptional true,
                )
                requestHeaders(
                    "Authorization" headerMeans "Bearer 액세스 토큰. `access_token` 쿼리 대신 보낸다" isOptional true,
                    "Last-Event-ID" headerMeans "재연결 시 마지막으로 받은 이벤트 ID" isOptional true,
                )
                events(
                    "connected" eventMeans "연결 성공. 연결마다 한 번" withFields ConnectedEvent::class,
                    "notification" eventMeans "새 알림" withFields NotificationEvent::class,
                    "maintenance" eventMeans "점검 예고. 점검이 잡혔을 때만" isOptional true withFields MaintenanceEvent::class,
                )
            }

        val dir = Paths.get("build/generated-snippets/notification-api-docs-test/subscribe")
        check(Files.exists(dir.resolve("sse-events.adoc")))
        check(Files.exists(dir.resolve("response-fields-event-notification.adoc")))
        check(!Files.exists(dir.resolve("response-fields-event-maintenance.adoc"))) {
            "optional event absent from stream has no field table"
        }
    }

    @Test
    fun rejectsStreamWithoutToken() {
        mockMvc.perform(get("/api/v1/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
            .andStatus(HttpStatus.UNAUTHORIZED)

        mockMvc.perform(get("/api/v1/notifications/stream").bearerToken("wrong-token"))
            .andStatus(HttpStatus.UNAUTHORIZED)
    }
}
