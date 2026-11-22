package io.github.kardx.resttestclient

import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.web.servlet.client.RestTestClient

// RestTestClient 진입점 (Spring Framework 7 / Spring Boot 4). 이 패키지는 Spring 7 로 따로 컴파일되고, RestTestClient 를 쓸 때만
// 로드된다. Spring 6 (Boot 3) 프로젝트에서는 이 함수들을 부를 수 없으므로 로드되지 않는다.

/**
 * RestTestClient 에 REST Docs 설정을 단다. 요청마다 문서화 문맥(스니펫 경로·순번)을 기록해 두고, `andDocument { }` 가 그것으로
 * 스니펫을 만든다. 기본 스니펫(curl·http-request·http-response 등)도 MockMvc·WebTestClient 와 같이 만든다.
 *
 * ```kotlin
 * val client = RestTestClient.bindTo(mockMvc)
 *     .restDocs(provider)
 *     .build()
 *
 * client.get()
 *     .uri("/api/users/{id}", 1L)
 *     .exchange()
 *     .expectStatus().isOk
 *     .andDocument {
 *         pathParameters("id" pathMeans "사용자 ID")
 *         responseFields<UserResponse>()
 *     }
 * ```
 *
 * @param provider 테스트의 REST Docs 문맥 (`AutoRestDocumentationExtension` 이 테스트 메서드 파라미터로 넣는다)
 */
public fun <B : RestTestClient.Builder<*>> B.restDocs(provider: RestDocumentationContextProvider): B = apply {
    BuilderInterceptors.add(this, DocumentationInterceptor(provider))
}
