package io.github.kardx.webtestclient

import io.github.kardx.dsl.DocumentationBuilder
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * WebTestClient 응답을 Spring REST Docs 로 문서화한다. `expectBody().andDocument { }` 와 같다.
 * MockMvc 의 `ResultActions.andDocument` 와 같은 블록·스니펫·OpenAPI 조각을 만든다.
 * 본문 검증(`jsonPath` 등)을 이어 쓸 수 있게 [WebTestClient.BodyContentSpec] 을 돌려준다.
 *
 * ```kotlin
 * webTestClient.get()
 *     .uri("/api/users/{id}", 1L)
 *     .exchange()
 *     .expectStatus().isOk
 *     .andDocument {
 *         description = "ID로 사용자 조회"
 *         pathParameters("id" pathMeans "사용자 ID")
 *         responseFields<UserResponse>()
 *     }
 * ```
 */
public fun WebTestClient.ResponseSpec.andDocument(
    block: DocumentationBuilder.() -> Unit = {},
): WebTestClient.BodyContentSpec = expectBody().andDocument(block)
