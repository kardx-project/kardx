package io.github.kardx.webtestclient

import io.github.kardx.dsl.DocumentationBuilder
import org.springframework.test.web.reactive.server.WebTestClient

// WebTestClient 진입점. spring-restdocs-webtestclient·spring-webflux 는 compileOnly 라, 이 파일의 클래스는
// 이 함수를 부를 때만 로드된다. MockMvc 만 쓰는 프로젝트에는 두 의존성이 없어도 된다.

/**
 * WebTestClient 응답 본문을 Spring REST Docs 로 문서화한다. MockMvc 의 `ResultActions.andDocument` 와 같은 블록으로
 * 같은 스니펫(`api-summary` 포함)·OpenAPI 조각(epages 가 있을 때)·WireMock 매핑을 만들고, 검증 실패도 같은
 * [io.github.kardx.dsl.DocumentationMismatchException] 이다. 체인을 이어 쓸 수 있게 받은 값을 그대로 돌려준다.
 *
 * 클라이언트에는 REST Docs 설정이 있어야 한다: [restDocsWebTestClient] 또는 `WebTestClient.Builder.restDocs`.
 * 본문을 끝까지 읽은 뒤 문서화하므로 끝나는 응답에 쓴다(끝나는 SSE `Flux` 는 `events(...)` 로 문서화된다).
 *
 * ```kotlin
 * webTestClient.get()
 *     .uri("/api/users/{id}", 1L)
 *     .exchange()
 *     .expectBody()
 *     .jsonPath("$.name").isEqualTo("홍길동")
 *     .andDocument {
 *         pathParameters("id" pathMeans "사용자 ID")
 *         responseFields<UserResponse>()
 *     }
 * ```
 */
public fun WebTestClient.BodyContentSpec.andDocument(
    block: DocumentationBuilder.() -> Unit = {},
): WebTestClient.BodyContentSpec = consumeWith { result -> documentExchange(result, block) }
