package io.github.kardx.internal.output

import java.util.concurrent.ConcurrentHashMap

/**
 * 실행 중인 테스트의 문서 정보. 테스트 확장(JUnit·Kotest)이 테스트 실행([io.github.kardx.lifecycle.DocumentationRun])에 묶는다.
 *
 * OpenAPI·AsyncAPI 요약은 이 값의 [sectionTitle] 이다([OperationTitles]). 테스트 스레드에서는 [current] 로 찾고, 테스트가
 * 직접 만든 스레드나 서버·비동기 스레드에서 문서화하면 REST Docs 문맥의 (문서 클래스, 스니펫 자리 이름)으로 [find] 한다.
 *
 * 코루틴은 실행 중에 스레드를 옮겨 다니므로, Kotest 확장은 코루틴이 다시 실행될 때마다 이 값을 다시 둔다
 * (`ThreadContextElement`). 그래서 일반 ThreadLocal 처럼 처음 스레드에만 남는 문제가 없다.
 *
 * @property documentClass 문서 하나로 모이는 클래스 (JUnit 의 가장 바깥 테스트 클래스, Kotest 스펙 클래스)
 * @property snippetName 스니펫 자리 이름. REST Docs 문맥의 메서드 이름과 같다
 * @property sectionTitle OpenAPI·AsyncAPI 요약 (JUnit 은 테스트 표시 이름, Kotest 는 섹션 제목)
 */
internal class RunningTest(val documentClass: Class<*>, val snippetName: String, val sectionTitle: String) {
    companion object {
        private val current = ThreadLocal<RunningTest?>()

        /** (문서 클래스 이름, 스니펫 자리 이름) → 시작했고 아직 끝나지 않은 테스트 */
        private val open = ConcurrentHashMap<Pair<String, String>, RunningTest>()

        /** 이 스레드에서 실행 중인 테스트. 없으면 null (테스트 밖, 테스트에 묶지 않은 스레드). */
        fun current(): RunningTest? = current.get()

        /** [test] 를 이 스레드의 실행 중인 테스트로 둔다. 이전 값을 돌려준다. */
        fun set(test: RunningTest?): RunningTest? {
            val previous = current.get()

            if (test == null) current.remove() else current.set(test)

            return previous
        }

        /** [test] 가 시작했다. 끝날 때까지 다른 스레드에서도 [find] 로 찾는다. */
        fun opened(test: RunningTest) {
            open[test.key()] = test
        }

        /** [test] 가 끝났다. 같은 자리를 그사이 다른 실행이 열었으면 그 실행은 남긴다. */
        fun closed(test: RunningTest) {
            open.remove(test.key(), test)
        }

        /** [documentClass] 의 [snippetName] 자리에서 실행 중인 테스트. 없으면 null. */
        fun find(documentClass: Class<*>, snippetName: String): RunningTest? = open[documentClass.name to snippetName]

        private fun RunningTest.key() = documentClass.name to snippetName
    }
}
