package io.github.kardx.mockmvc

import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.internal.documentation.MvcResultActions
import org.springframework.test.web.servlet.assertj.MvcTestResult

// MvcTestResult 는 Spring Framework 6.2(Boot 3.4) 부터 있다. 이 파일의 클래스는 이 함수를 부를 때만 로드되므로
// 낮은 버전에서도 다른 문서화 함수는 그대로 쓸 수 있다.

/**
 * `MockMvcTester` 의 결과를 문서화한다. `ResultActions.andDocument` 와 같은 블록·스니펫·OpenAPI 조각을 만든다.
 * AssertJ 검증을 이어 쓸 수 있게 받은 `MvcTestResult` 를 그대로 돌려준다. Spring Framework 6.2 이상(Boot 3.4+)에서 쓴다.
 *
 * `MockMvcTester` 는 REST Docs 설정이 들어간 MockMvc 로 만든다: `MockMvcTester.create(restDocsMockMvc(context, provider))`.
 *
 * ```kotlin
 * val result = mvc.get().uri("/api/users/{id}", 1L).exchange()
 *     .andDocument {
 *         pathParameters("id" pathMeans "사용자 ID")
 *         responseFields<UserResponse>()
 *     }
 *
 * assertThat(result).hasStatusOk()
 * ```
 */
public fun MvcTestResult.andDocument(block: DocumentationBuilder.() -> Unit = {}): MvcTestResult = apply {
    MvcResultActions(mvcResult).andDocument(block)
}
