package io.github.kardx.internal.output

import io.github.kardx.diagnostics.RestDocsErrorCode
import org.springframework.restdocs.RestDocumentationContext
import org.springframework.restdocs.RestDocumentationContextProvider
import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 시작했고 아직 끝나지 않은 테스트 실행 하나 ([io.github.kardx.lifecycle.DocumentationRun]).
 *
 * @property documentClass 문서 하나로 모이는 클래스
 * @property snippetName 스니펫 자리 이름 (REST Docs 문맥의 메서드 이름)
 * @property outputDirectory 이 실행의 스니펫 출력 디렉터리
 * @property provider 이 실행의 REST Docs 문맥
 */
internal class OpenRun(
    val documentClass: Class<*>,
    val snippetName: String,
    val outputDirectory: File,
    val provider: RestDocumentationContextProvider,
) {
    /** 이 실행의 스니펫이 생기는 디렉터리 (`<출력>/user-api-test/get-user`). 문서가 이 자리를 읽는다. */
    val snippetDirectory: File
        get() = SnippetPaths.snippetDirectory(outputDirectory, documentClass, snippetName)
}

/**
 * 테스트 확장(JUnit·Kotest)이 연 테스트 실행들. 두 가지에 쓴다.
 *
 * - 확장 인스턴스나 테스트 파라미터 없이 "지금 테스트"의 REST Docs 문맥을 찾는다 ([currentProvider]).
 *   `restDocsMockMvc(context)` 처럼 provider 를 받지 않는 함수가 쓴다
 * - 문서화에 쓴 REST Docs 문맥이 이 라이브러리의 실행이 만든 것인지 가린다 ([isOwn]). Spring Boot 의
 *   `@AutoConfigureRestDocs` 가 만든 문맥은 스니펫 디렉터리 규칙이 달라 문서에서 섹션이 빠질 수 있다
 */
internal object OpenRuns {
    private val current = ThreadLocal<OpenRun?>()

    private val open: MutableSet<OpenRun> = ConcurrentHashMap.newKeySet()

    /** 이 라이브러리의 실행이 만든 문맥. 문맥은 요청마다 새로 만들어지고 짧게 쓰이므로 약한 참조로 둔다 */
    private val owned: MutableSet<RestDocumentationContext> = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap()),
    )

    /**
     * 지금 테스트의 REST Docs 문맥. 이 스레드에 묶인 실행(JUnit 테스트 스레드, Kotest 테스트 코루틴)을 먼저 쓰고, 없으면
     * 열려 있는 실행이 하나뿐일 때 그 실행을 쓴다 (테스트가 만든 스레드·서버 스레드에서 문서화하는 경우).
     */
    val currentProvider: RestDocumentationContextProvider = RestDocumentationContextProvider {
        val run = current() ?: throw IllegalStateException(
            RestDocsErrorCode.NO_RUNNING_TEST.message(
                "no documented test is running on this thread (${Thread.currentThread().name}), so there is no " +
                    "snippet directory for this request. Register AutoRestDocumentationExtension " +
                    "(@ExtendWith) or the Kotest RestDocsExtension, and send documented requests inside a test " +
                    "method or a Kotest leaf test.",
            ),
        )

        run.provider.beforeOperation()
    }

    /** 이 스레드의 실행, 없으면 열려 있는 실행이 하나뿐일 때 그 실행. 없으면 null. */
    fun current(): OpenRun? = current.get() ?: open.singleOrNull()

    /** [run] 을 이 스레드의 실행으로 둔다. 이전 값을 돌려준다. */
    fun set(run: OpenRun?): OpenRun? {
        val previous = current.get()

        if (run == null) current.remove() else current.set(run)

        return previous
    }

    fun opened(run: OpenRun) {
        open += run
    }

    fun closed(run: OpenRun) {
        open -= run
    }

    /** [context] 를 이 라이브러리의 실행이 만들었다고 기록한다. */
    fun own(context: RestDocumentationContext): RestDocumentationContext = context.also { owned += it }

    /** [context] 를 이 라이브러리의 실행이 만들었는지. */
    fun isOwn(context: RestDocumentationContext): Boolean = context in owned
}
