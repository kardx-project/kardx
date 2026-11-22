package com.example.restdocs.notification

import com.example.restdocs.common.DemoAuth
import io.github.kardx.annotation.Description
import io.github.kardx.annotation.EnumCode
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

// 상수 설명은 상수 KDoc 에서 읽는다 (description 프로퍼티가 없을 때)
@EnumCode("알림 종류")
enum class NotificationType {
    /** 주문 상태 변경 */
    ORDER,

    /** 관심 상품 가격 인하 */
    PRICE_DROP,
}

data class ConnectedEvent(
    @Description("클라이언트 ID") val clientId: String,
    @Description("재연결 대기 시간 (ms)") val retryMillis: Long,
)

data class NotificationEvent(
    @Description("알림 ID") val id: Long,
    @Description("알림 종류") val type: NotificationType,
    @Description("제목") val title: String,
    @Description("관련 상품 ID") val productId: Long?,
)

data class MaintenanceEvent(@Description("점검 시작 시각") val startsAt: String)

/** Server-Sent Events 예제. 테스트가 끝까지 받을 수 있도록 이벤트를 보낸 뒤 스트림을 닫는다. */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    // 샘플 코드: 데모 토큰을 `Authorization` 헤더나 `?access_token=` 쿼리로 받는다 (브라우저 EventSource 는 헤더를 붙일 수 없다).
    // 없으면 본문 없이 401 (DemoAuth).
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestParam(DemoAuth.QUERY_PARAMETER, required = false) accessToken: String?,
    ): ResponseEntity<SseEmitter> {
        if (!DemoAuth.accepts(authorization, accessToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .build()
        }

        val emitter = SseEmitter()

        emitter.send(
            SseEmitter.event()
                .name("connected")
                .data(ConnectedEvent("client-1", 3000)),
        )

        emitter.send(SseEmitter.event().comment("heartbeat"))
        emitter.send(
            SseEmitter.event()
                .id("1")
                .name("notification")
                .data(NotificationEvent(1, NotificationType.PRICE_DROP, "관심 상품 가격이 내려갔어요", 10)),
        )

        emitter.complete()

        return ResponseEntity.ok(emitter)
    }
}
