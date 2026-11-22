package com.example.restdocs.webflux.event

import com.example.restdocs.webflux.common.DemoAuth
import com.example.restdocs.webflux.support.RestDocsTestSupport
import io.github.kardx.dsl.eventMeans
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.withFields
import io.github.kardx.webtestclient.andDocument
import io.github.kardx.webtestclient.recordHandlers
import io.github.kardx.webtestclient.restDocs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Server-Sent Events 예제.
 *
 * 응답 `Flux` 가 끝나므로 WebTestClient 가 스트림을 끝까지 받고, `events` 로 이벤트 목록과 이벤트별 data 필드를 문서화한다.
 * 문서화한 필수 이벤트가 없거나 문서화하지 않은 이벤트가 오면 테스트가 실패한다. MockMvc 와 같이 AsyncAPI 에도 들어간다.
 *
 * 컨트롤러 하나만 묶은 클라이언트(`bindToController`)에 `WebTestClient.Builder.restDocs` 로 REST Docs 설정을 더하는 예이기도 하다.
 * `recordHandlers()` 는 요청을 처리한 컨트롤러 메서드를 기록해 OpenAPI operationId 를 메서드 이름(`events`)으로 만들게 한다.
 */
@DisplayName("상품 이벤트 스트림 API")
class ProductEventApiDocsTest : RestDocsTestSupport() {

    private lateinit var eventClient: WebTestClient

    @BeforeEach
    fun bindToController(provider: RestDocumentationContextProvider) {
        eventClient = WebTestClient.bindToController(ProductEventController())
            .recordHandlers()
            .configureClient()
            .restDocs(provider)
            .build()
    }

    @Test
    @DisplayName("상품 이벤트 구독 (SSE)")
    fun subscribe() {
        eventClient.get()
            .uri("/api/v1/product-events?${DemoAuth.QUERY_PARAMETER}={token}", DemoAuth.TOKEN)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .andDocument {
                description = "상품 가격·재고 변경을 Server-Sent Events 로 받는다. 토큰이 없으면 401 이다."
                queryParameters(
                    DemoAuth.QUERY_PARAMETER queryMeans "액세스 토큰 (샘플 앱은 `demo-token`). " +
                        "헤더를 붙일 수 없는 브라우저 EventSource 용. `Authorization` 헤더로 보내도 된다" isOptional true,
                )
                requestHeaders(
                    "Authorization" headerMeans "Bearer 액세스 토큰. `access_token` 쿼리 대신 보낸다" isOptional true,
                    "Last-Event-ID" headerMeans "재연결 시 마지막으로 받은 이벤트 ID" isOptional true,
                )
                events(
                    "price-changed" eventMeans "가격 변경" withFields PriceChangedEvent::class,
                    "sold-out" eventMeans "품절" withFields SoldOutEvent::class,
                    "restocked" eventMeans "재입고. 재입고될 때만" isOptional true withFields RestockedEvent::class,
                )
            }
    }

    @Test
    fun rejectsStreamWithoutToken() {
        eventClient.get()
            .uri("/api/v1/product-events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isUnauthorized

        eventClient.get()
            .uri("/api/v1/product-events")
            .headers { it.setBearerAuth("wrong-token") }
            .exchange()
            .expectStatus().isUnauthorized
    }
}
