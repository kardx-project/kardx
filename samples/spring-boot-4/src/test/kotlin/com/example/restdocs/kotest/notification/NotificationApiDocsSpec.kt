package com.example.restdocs.kotest.notification

import com.example.restdocs.common.DemoAuth
import com.example.restdocs.kotest.support.MirrorDocsConfig
import com.example.restdocs.notification.ConnectedEvent
import com.example.restdocs.notification.MaintenanceEvent
import com.example.restdocs.notification.NotificationEvent
import io.github.kardx.dsl.eventMeans
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.withFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.bearerToken
import io.github.kardx.mockmvc.performAsync
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.context.WebApplicationContext
import java.nio.file.Files
import java.nio.file.Paths

/**
 * [com.example.restdocs.notification.NotificationApiDocsTest] 를 Kotest 로 옮긴 스펙. `performAsync` 로 SSE 스트림을 끝까지 받아
 * 이벤트를 문서화하고, 스니펫은 테스트 이름 디렉터리(`알림-구독-sse`)에 생긴다.
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("알림 스트림 API")
class NotificationApiDocsSpec(context: WebApplicationContext) :
    FunSpec({
        val restDocs = extension(MirrorDocsConfig.restDocsExtension())
        val mockMvc = restDocsMockMvc(context, restDocs.provider)

        beforeEach { MirrorDocsConfig.apply() }

        test("알림 구독 (SSE)") {
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
                        "maintenance" eventMeans "점검 예고. 점검이 잡혔을 때만" isOptional true withFields
                            MaintenanceEvent::class,
                    )
                }

            val dir = Paths.get("build/generated-snippets/notification-api-docs-spec/알림-구독-sse")

            check(Files.exists(dir.resolve("sse-events.adoc")))
            check(Files.exists(dir.resolve("response-fields-event-notification.adoc")))
            check(!Files.exists(dir.resolve("response-fields-event-maintenance.adoc"))) {
                "optional event absent from stream has no field table"
            }
        }

        test("토큰이 없으면 401") {
            mockMvc.perform(get("/api/v1/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andStatus(HttpStatus.UNAUTHORIZED)

            mockMvc.perform(get("/api/v1/notifications/stream").bearerToken("wrong-token"))
                .andStatus(HttpStatus.UNAUTHORIZED)
        }
    })
