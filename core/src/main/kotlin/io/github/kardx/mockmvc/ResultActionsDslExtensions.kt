package io.github.kardx.mockmvc

import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.internal.documentation.MvcResultActions
import org.springframework.test.web.servlet.ResultActionsDsl

/**
 * MockMvc Kotlin DSL(`mockMvc.get("/x") { }`) 의 결과를 문서화한다. `ResultActions.andDocument` 와 같은
 * 블록·스니펫·OpenAPI 조각을 만든다. 체인을 이어 쓸 수 있게 받은 `ResultActionsDsl` 을 그대로 돌려준다.
 *
 * ```kotlin
 * mockMvc.get("/api/users/{id}", 1L) {
 *     accept = MediaType.APPLICATION_JSON
 * }.andExpect {
 *     status { isOk() }
 * }.andDocument {
 *     pathParameters("id" pathMeans "사용자 ID")
 *     responseFields<UserResponse>()
 * }
 * ```
 */
public fun ResultActionsDsl.andDocument(block: DocumentationBuilder.() -> Unit = {}): ResultActionsDsl = apply {
    MvcResultActions(andReturn()).andDocument(block)
}
