package io.github.kardx.internal.documentation

import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.ResultHandler
import org.springframework.test.web.servlet.ResultMatcher

/**
 * 이미 끝난 [MvcResult] 를 [ResultActions] 로 감싼다. `ResultActions` 가 아닌 결과(MockMvc Kotlin DSL 의 `ResultActionsDsl`,
 * `MockMvcTester` 의 `MvcTestResult`, 고정한 스트림)도 같은 [io.github.kardx.mockmvc.andDocument] 로 문서화하게 한다.
 */
internal open class MvcResultActions(private val result: MvcResult) : ResultActions {
    override fun andExpect(matcher: ResultMatcher): ResultActions = apply { matcher.match(result) }

    override fun andDo(handler: ResultHandler): ResultActions = apply { handler.handle(result) }

    override fun andReturn(): MvcResult = result
}
