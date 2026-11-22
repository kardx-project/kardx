package io.github.kardx.resttestclient

import io.github.kardx.dsl.DocumentationBuilder
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * RestTestClient 응답을 Spring REST Docs 로 문서화한다. `expectBody().andDocument { }` 와 같다.
 * MockMvc·WebTestClient 의 `andDocument` 와 같은 블록·스니펫·OpenAPI 조각을 만든다. 클라이언트에는 [restDocs] 가 있어야 한다.
 * 본문 검증(`jsonPath` 등)을 이어 쓸 수 있게 [RestTestClient.BodyContentSpec] 을 돌려준다.
 *
 * ```kotlin
 * restTestClient.get()
 *     .uri("/api/users/{id}", 1L)
 *     .exchange()
 *     .expectStatus().isOk
 *     .andDocument {
 *         pathParameters("id" pathMeans "사용자 ID")
 *         responseFields<UserResponse>()
 *     }
 * ```
 */
public fun RestTestClient.ResponseSpec.andDocument(
    block: DocumentationBuilder.() -> Unit = {},
): RestTestClient.BodyContentSpec = expectBody().andDocument(block)

/**
 * RestTestClient 응답 본문을 문서화한다. [RestTestClient.ResponseSpec.andDocument] 와 같은 블록이다.
 *
 * ```kotlin
 * restTestClient.get()
 *     .uri("/api/users/{id}", 1L)
 *     .exchange()
 *     .expectBody()
 *     .jsonPath("$.name").isEqualTo("홍길동")
 *     .andDocument { responseFields<UserResponse>() }
 * ```
 */
public fun RestTestClient.BodyContentSpec.andDocument(
    block: DocumentationBuilder.() -> Unit = {},
): RestTestClient.BodyContentSpec = consumeWith { result -> documentExchange(result, block) }

/**
 * 본문을 타입으로 받은 RestTestClient 응답(`expectBody<T>()`)을 문서화한다. 문서에는 역직렬화한 객체가 아니라 실제로 받은
 * 본문이 들어간다. 이어서 `value { }`·`returnResult()` 를 쓸 수 있게 같은 [RestTestClient.BodySpec] 을 돌려준다.
 *
 * ```kotlin
 * val user = restTestClient.get()
 *     .uri("/api/users/{id}", 1L)
 *     .exchange()
 *     .expectBody(UserResponse::class.java)
 *     .andDocument { responseFields<UserResponse>() }
 *     .returnResult()
 *     .responseBody
 * ```
 */
public fun <B : Any> RestTestClient.BodySpec<B, *>.andDocument(
    block: DocumentationBuilder.() -> Unit = {},
): RestTestClient.BodySpec<B, *> = consumeWith { result -> documentExchange(result, block) }
