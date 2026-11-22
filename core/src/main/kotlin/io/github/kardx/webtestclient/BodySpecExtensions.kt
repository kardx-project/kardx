package io.github.kardx.webtestclient

import io.github.kardx.dsl.DocumentationBuilder
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * 본문을 타입으로 받은 WebTestClient 응답(`expectBody<T>()`, `expectBody(T::class.java)`)을 Spring REST Docs 로
 * 문서화한다. [WebTestClient.BodyContentSpec.andDocument] 와 같은 블록으로 같은 스니펫(`api-summary` 포함)·OpenAPI 조각·
 * WireMock 매핑을 만들고, 문서에는 역직렬화한 객체가 아니라 실제로 받은 본문이 들어간다.
 * 이어서 `value { }`·`returnResult()` 를 쓸 수 있게 같은 [WebTestClient.BodySpec] 을 돌려준다.
 *
 * ```kotlin
 * val user = webTestClient.get()
 *     .uri("/api/users/{id}", 1L)
 *     .exchange()
 *     .expectBody<UserResponse>()
 *     .andDocument {
 *         pathParameters("id" pathMeans "사용자 ID")
 *         responseFields<UserResponse>()
 *     }
 *     .returnResult()
 *     .responseBody
 * ```
 */
public fun <B> WebTestClient.BodySpec<B, *>.andDocument(
    block: DocumentationBuilder.() -> Unit = {},
): WebTestClient.BodySpec<B, *> = consumeWith { result -> documentExchange(result, block) }
