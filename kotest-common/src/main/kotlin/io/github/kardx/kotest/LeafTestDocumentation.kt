@file:OptIn(RestDocsExtensionApi::class)

package io.github.kardx.kotest

import io.github.kardx.asciidoc.DocumentGeneratorFactory
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.lifecycle.DocumentOutline
import io.github.kardx.lifecycle.DocumentationLifecycle
import io.github.kardx.lifecycle.RestDocsExtensionApi
import io.github.kardx.lifecycle.SpringContextSettings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationContext
import org.springframework.restdocs.RestDocumentationContextProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * [RestDocsExtension] 하나의 문서화 상태와 동작. Kotest 버전과 상관없는 부분이다.
 *
 * Kotest 5.x·6.x 모듈의 [RestDocsExtension] 은 Kotest API(테스트 이름·부모·타입, 결과, 리스너 인터페이스)를 읽어 이 클래스에
 * 넘기기만 한다. 그래서 이 클래스는 Kotest 타입을 참조하지 않는다.
 *
 * @param sectionTitle 테스트 이름 → 섹션 제목
 * @param snippetName 테스트 이름 → 스니펫 자리 이름 (돌려준 이름도 [TestNames.safe] 로 다듬는다)
 * @param documentGenerator 문서 각 파트를 만드는 생성기
 * @param springContext 지금 테스트의 Spring 애플리케이션 컨텍스트 (기본은 Kotest Spring 확장이 만든 것, [SpringContexts])
 */
internal class LeafTestDocumentation(
    private val sectionTitle: SectionTitleProvider,
    private val snippetName: SnippetNameProvider,
    private val documentGenerator: DocumentGeneratorFactory,
    private val springContext: suspend () -> ApplicationContext? = { SpringContexts.current() },
) {
    private val lifecycle = DocumentationLifecycle()

    /** 이 확장이 리프 테스트를 문서화한 스펙 클래스 이름. [writeDocument] 가 이 스펙의 문서만 쓴다 */
    private val documentedSpecs: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 이 확장이 마지막으로 시작한 리프 테스트. 코루틴 밖에서 [provider] 를 부를 때 쓴다 */
    @Volatile
    private var latest: ActiveRun? = null

    /** 지금 리프 테스트(코루틴 밖이면 마지막으로 시작한 리프 테스트)의 스니펫 자리를 주는 REST Docs 문맥 */
    val provider: RestDocumentationContextProvider = RestDocumentationContextProvider {
        (ActiveRun.current() ?: latest ?: throw notInTest()).beforeOperation()
    }

    /**
     * 테스트 하나를 실행한다. 리프 테스트면 스니펫 자리를 정하고 끝나면 결과대로 문서에 넣거나 뺀다.
     * 컨테이너이거나, 이미 다른 확장(스펙과 프로젝트 양쪽 등록)이 문서화 중인 테스트면 그대로 실행만 한다.
     *
     * @param leaf 이 확장이 문서화할 테스트인지. 리프 테스트이고, 스펙이 자기 확장을 따로 등록했으면 그 확장일 때만 true
     * @param specClass 스펙 클래스
     * @param path 바깥 컨테이너에서 이 테스트까지의 이름 (스타일 접두사 제외)
     * @param outcome 실행 결과 → 성공·실패 판정. 결과 없이 끝났으면(취소 등) null 을 받는다
     * @param execute 테스트 실행
     */
    suspend fun <R> intercept(
        leaf: Boolean,
        specClass: Class<*>,
        path: () -> List<String>,
        outcome: (R?) -> TestOutcome,
        execute: suspend () -> R,
    ): R {
        if (!leaf || currentCoroutineContext()[ActiveRun.Element] != null) {
            return execute()
        }

        val names = path()
        val test = TestPath(names, specClass)
        val document = SpecDocument.of(specClass)
        val section = document.section(
            names,
            TestNames.safe(snippetName.snippetName(test)),
            sectionTitle.sectionTitle(test),
        )
        val active = ActiveRun(lifecycle.startTest(specClass, section.snippetName, sectionTitle = section.title), this)

        documentedSpecs += specClass.name
        latest = active

        // 앱 매퍼는 실행을 스레드에 묶은 뒤에 정한다. 그래야 매퍼가 이 실행을 따라 스레드를 옮기고, 실행이 끝나면 스레드가
        // 이전 매퍼로 돌아간다 (묶기 전에 정하면 같은 스레드에서 다음에 도는 Spring 없는 스펙이 이 앱의 매퍼를 쓴다)
        val attempt = runCatching {
            withContext(ActiveRun.Element(active)) {
                springContext()?.let(SpringContextSettings::apply)

                execute()
            }
        }
        val cleanup = finishRun(active, document, names) { outcome(attempt.getOrNull()) }

        // 테스트의 예외가 먼저다. 정리 예외는 거기에 붙이고, 테스트가 예외 없이 끝났을 때만 정리 예외를 던진다
        attempt.exceptionOrNull()?.let { failure ->
            cleanup?.let(failure::addSuppressed)

            throw failure
        }

        cleanup?.let { throw it }

        return attempt.getOrThrow()
    }

    /**
     * 결과를 기록하고 실행을 닫는다. 기록이 실패해도 실행은 닫는다. 정리 중에 난 예외를 돌려주고, 없으면 null.
     * 테스트의 예외·결과를 가리지 않도록 여기서 던지지 않는다.
     */
    private fun finishRun(
        active: ActiveRun,
        document: SpecDocument,
        names: List<String>,
        outcome: () -> TestOutcome,
    ): Throwable? {
        var finished: TestOutcome? = null
        val recorded = runCatching {
            val result = outcome()

            finished = result
            active.run.finish(failed = result.failed)
            document.complete(names, succeeded = result.succeeded)
        }
        val closed = runCatching { active.run.close() }

        if (latest === active) latest = null

        val error = listOfNotNull(recorded.exceptionOrNull(), closed.exceptionOrNull())
            .reduceOrNull { first, second -> first.apply { addSuppressed(second) } }

        // 테스트가 이미 실패했으면 정리 예외로 그 결과를 바꾸지 않고 경고만 한다
        return if (error != null && finished?.failed == true) {
            LOGGER.log(
                System.Logger.Level.WARNING,
                RestDocsErrorCode.KOTEST_CLEANUP_FAILED.message("cleaning up a failed test failed"),
                error,
            )
            null
        } else {
            error
        }
    }

    /** 테스트 본문을 실행하기 직전. 이 확장이 문서화 중인 테스트면 지금 설정을 문서 설정으로 기억한다. */
    suspend fun captureSettings() {
        val active = currentCoroutineContext()[ActiveRun.Element]?.active ?: return

        if (active.owner === this) {
            active.run.captureSettings()
        }
    }

    /** 스펙 인스턴스가 끝났을 때. 이 확장이 문서화한 스펙이면 그때까지의 테스트로 문서를 쓴다. */
    fun writeDocument(specClass: Class<*>) {
        if (specClass.name !in documentedSpecs) return

        val document = SpecDocument.find(specClass) ?: return

        lifecycle.writeDocument(specClass, documentGenerator) { DocumentOutline(specClass, document.sections()) }
    }

    companion object {
        private val LOGGER: System.Logger = System.getLogger(LeafTestDocumentation::class.java.name)

        /** 확장 인스턴스 없이 지금 리프 테스트를 찾는 REST Docs 문맥 (`RestDocsExtension.currentTestProvider`) */
        val currentTestProvider: RestDocumentationContextProvider = RestDocumentationContextProvider {
            (ActiveRun.current() ?: throw notInTest()).beforeOperation()
        }

        private fun notInTest() = IllegalStateException(
            RestDocsErrorCode.KOTEST_NOT_IN_TEST.message(
                "no Kotest leaf test is running. Document requests inside a leaf test " +
                    "(then/it/test), and register RestDocsExtension on the spec or in the project config.",
            ),
        )
    }
}
