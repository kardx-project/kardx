package io.github.kardx.webtestclient

import org.springframework.context.ApplicationContext
import org.springframework.test.web.reactive.server.ExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.Collections

/**
 * [HandlerRecorder] 가 남긴 요청 하나의 기록.
 *
 * @property handler WebFlux 가 고른 핸들러. 모르면 null
 * @property applicationContext 요청을 처리한 앱 컨텍스트 (`ServerWebExchange.getApplicationContext`). 모르면 null
 */
internal class RecordedExchange(val handler: Any?, val applicationContext: ApplicationContext?)

/**
 * 서버 없이 묶은 WebTestClient([restDocsWebTestClient], [recordHandlers])의 요청을 처리한 핸들러와 앱 컨텍스트를 기록하는
 * `WebFilter`. 핸들러는 operationId 기본 규칙(핸들러 메서드 이름)과 API 버전에, 컨텍스트는 엔드포인트 인벤토리에 쓴다.
 *
 * WebTestClient 는 요청마다 `WebTestClient-Request-Id` 헤더를 붙이고, 서버 쪽 exchange 도 같은 헤더를 받는다. 응답을 보내기
 * 직전(`beforeCommit`)에 WebFlux 가 고른 핸들러(`HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE`)를 그 id 로 남기고,
 * `andDocument` 가 [ExchangeResult] 의 같은 헤더로 찾는다. 응답을 보내기 전에 남기므로 클라이언트가 결과를 받을 때는 이미 있다.
 *
 * 실제 서버(`bindToServer`)나 이 필터를 걸지 않은 바인딩에서는 서버 exchange 를 볼 수 없어 핸들러를 모른다 (테스트 이름 기반
 * id 로 대신한다).
 */
internal object HandlerRecorder : WebFilter {
    /** 문서화하지 않은 요청의 기록이 쌓이지 않게 최근 것만 둔다 */
    private const val MAX_ENTRIES = 256

    private val handlers: MutableMap<String, RecordedExchange> = Collections.synchronizedMap(
        object : LinkedHashMap<String, RecordedExchange>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RecordedExchange>?): Boolean =
                size > MAX_ENTRIES
        },
    )

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = exchange.request.headers.getFirst(WebTestClient.WEBTESTCLIENT_REQUEST_ID)
            ?: return chain.filter(exchange)

        exchange.response.beforeCommit {
            val handler = exchange.getAttribute<Any>(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
            val context = exchange.applicationContext

            if (handler != null || context != null) {
                handlers[requestId] = RecordedExchange(handler, context)
            }

            Mono.empty()
        }

        return chain.filter(exchange)
    }

    /** [result] 요청의 기록. 기록이 없으면 null. 찾은 기록은 지운다. */
    fun take(result: ExchangeResult): RecordedExchange? =
        result.requestHeaders.getFirst(WebTestClient.WEBTESTCLIENT_REQUEST_ID)
            ?.let { handlers.remove(it) }
}
