@file:OptIn(RestDocsExtensionApi::class)

package io.github.kardx.kotest

import io.github.kardx.lifecycle.DocumentationRun
import io.github.kardx.lifecycle.RestDocsExtensionApi
import kotlinx.coroutines.ThreadContextElement
import org.springframework.restdocs.RestDocumentationContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 실행 중인 리프 테스트 하나. [RestDocsExtension.provider] 가 문서화 호출마다 이것을 찾아 스니펫 자리를 받는다.
 *
 * @property owner 이 테스트를 문서화하는 확장의 문서화 상태. 같은 확장을 스펙과 프로젝트 양쪽에 등록했을 때 한쪽만 일하게 한다
 */
internal class ActiveRun(val run: DocumentationRun, val owner: LeafTestDocumentation) {
    fun beforeOperation(): RestDocumentationContext = run.provider.beforeOperation()

    /**
     * 리프 테스트를 실행하는 코루틴에 [ActiveRun] 을 묶는 문맥 요소.
     *
     * 코루틴은 멈췄다 다시 실행될 때 다른 스레드로 옮겨 갈 수 있다. 이 요소는 코루틴이 스레드에서 실행을 시작할 때마다
     * 그 스레드에 실행을 묶고([DocumentationRun.attachToCurrentThread]), 멈출 때 되돌린다. 그래서 `provider` 를 부르는 코드가
     * 어느 스레드에서 돌든 지금 테스트를 찾는다. 테스트 안에서 띄운 자식 코루틴에도 이어진다.
     */
    class Element(val active: ActiveRun) :
        AbstractCoroutineContextElement(Key),
        ThreadContextElement<Element.State> {

        override fun updateThreadContext(context: CoroutineContext): State {
            val previous = CURRENT.get()

            CURRENT.set(active)

            return State(previous, active.run.attachToCurrentThread())
        }

        override fun restoreThreadContext(context: CoroutineContext, oldState: State) {
            oldState.attachment.close()

            if (oldState.previous == null) CURRENT.remove() else CURRENT.set(oldState.previous)
        }

        /** 이 요소를 묶기 전의 스레드 상태 */
        class State(val previous: ActiveRun?, val attachment: AutoCloseable)

        companion object Key : CoroutineContext.Key<Element>
    }

    companion object {
        private val CURRENT = ThreadLocal<ActiveRun?>()

        /** 이 스레드에서 실행 중인 리프 테스트. [Element] 가 묶은 코루틴 밖이면 null. */
        fun current(): ActiveRun? = CURRENT.get()
    }
}
