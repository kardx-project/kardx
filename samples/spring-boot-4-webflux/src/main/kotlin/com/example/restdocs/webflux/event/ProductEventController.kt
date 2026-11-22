package com.example.restdocs.webflux.event

import com.example.restdocs.webflux.common.DemoAuth
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

/** Server-Sent Events 예제. 테스트가 끝까지 받을 수 있도록 이벤트를 보낸 뒤 스트림을 닫는다(끝나는 `Flux`). */
@RestController
class ProductEventController {

    /**
     * 샘플 코드: 데모 토큰을 `Authorization` 헤더나 `?access_token=` 쿼리로 받는다 (브라우저 EventSource 는 헤더를 붙일 수 없다).
     * 없으면 본문 없이 401 (DemoAuth).
     */
    @GetMapping("/api/v1/product-events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestParam(DemoAuth.QUERY_PARAMETER, required = false) accessToken: String?,
    ): ResponseEntity<Flux<ServerSentEvent<Any>>> {
        if (!DemoAuth.accepts(authorization, accessToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .build()
        }

        return ResponseEntity.ok(
            Flux.just(
                ServerSentEvent.builder<Any>(PriceChangedEvent(productId = 1, before = 59000, after = 49000))
                    .id("1")
                    .event("price-changed")
                    .build(),
                ServerSentEvent.builder<Any>(SoldOutEvent(productId = 2))
                    .id("2")
                    .event("sold-out")
                    .build(),
            ),
        )
    }
}
