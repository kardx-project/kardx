package io.github.kardx.lifecycle

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.output.OpenRun
import io.github.kardx.internal.output.OpenRuns
import io.github.kardx.internal.output.RunningTest
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.RestDocumentationContextProvider

/**
 * 테스트 실행 하나의 문서화 상태. [DocumentationLifecycle.startTest] 가 만들고, 테스트가 끝나면 [finish] 와 [close] 를 부른다.
 *
 * 만들 때 REST Docs 의 테스트를 시작한다(`ManualRestDocumentation.beforeTest`). [attachToCurrentThread] 로 스레드에 붙인 동안
 * 그 스레드에서 한 `RestDocsConfiguration.configure` 는 이 실행이 더한 블록으로 기억했다가 [close] 에서 그 블록만 뺀다.
 *
 * @property documentClass 문서 하나로 모이는 클래스
 * @property snippetName 스니펫 자리 이름 ([DocumentSection.snippetName])
 */
@RestDocsExtensionApi
public class DocumentationRun internal constructor(
    private val lifecycle: DocumentationLifecycle,
    public val documentClass: Class<*>,
    public val snippetName: String,
    sectionTitle: String?,
) {
    /** 이 실행이 `configure` 로 더한 블록. [close] 가 이 블록만 뺀다 */
    private val changes = RestDocsConfiguration.Changes()

    /** 시작할 때 이 스레드의 애플리케이션 매퍼. [close] 가 되돌린다 */
    private val startMapper: Any? = RestDocsConfiguration.threadObjectMapper

    private val documentation = ManualRestDocumentation(lifecycle.snippetsDirectory.path).apply {
        beforeTest(documentClass, snippetName)
    }

    private val runningTest = sectionTitle?.let { RunningTest(documentClass, snippetName, it) }
        ?.also { RunningTest.opened(it) }

    /** 이 실행에 묶인 애플리케이션 매퍼. 스레드를 옮길 때 따라간다 ([attachToCurrentThread]) */
    @Volatile
    private var contextObjectMapper: Any? = RestDocsConfiguration.threadObjectMapper

    /**
     * 이 실행의 스니펫을 쓰는 REST Docs 문맥. `restDocsMockMvc(context, provider)`, `restDocsWebTestClient(...)`,
     * `MessageDocumentation(...)` 에 넘긴다. 문서화 호출(`andDocument` 등)마다 순번이 하나씩 오른다.
     */
    public val provider: RestDocumentationContextProvider = RestDocumentationContextProvider {
        OpenRuns.own(documentation.beforeOperation())
    }

    /** provider 없이 지금 테스트를 찾을 때(`restDocsMockMvc(context)`)와 문맥 출처 검사에 쓰는 이 실행의 정보 */
    private val openRun = OpenRun(documentClass, snippetName, lifecycle.snippetsDirectory, provider)
        .also(OpenRuns::opened)

    /**
     * 지금 설정을 이 문서 클래스의 문서 설정으로 기억한다. [DocumentationLifecycle.writeDocument] 가 쓴다.
     * 테스트 준비(JUnit `@BeforeEach`, Kotest `beforeEach`)가 끝나고 문서화하기 전에 부른다. 실행마다 덮어쓰므로 문서에는
     * 마지막으로 실행한 테스트의 설정이 쓰인다.
     */
    public fun captureSettings() {
        lifecycle.recordSettings(documentClass, RestDocsConfiguration.snapshot())
    }

    /**
     * 테스트 결과를 기록한다. [failed] 면 이 실행의 스니펫을 지우고 실패로 기록해 문서와 OpenAPI 에서 뺀다.
     * 성공하면 실패 기록을 지운다 (같은 자리를 다시 실행해 성공한 경우).
     */
    public fun finish(failed: Boolean) {
        lifecycle.recordFailure(documentClass, snippetName, failed)
    }

    /**
     * REST Docs 의 테스트를 끝내고(`afterTest`), 이 실행이 `configure` 로 더한 블록을 뺀다. 붙어 있던 동안 테스트 준비·본문·정리
     * (`@BeforeEach`·`@AfterEach`, Kotest `beforeEach`·`afterEach`)에서 한 `configure` 는 그 테스트에만 적용되고, 시작 전이나
     * 실행 밖에서 한 설정, 동시에 도는 다른 실행의 설정은 그대로다. 이 스레드의 애플리케이션 매퍼는 시작할 때로 되돌린다.
     * 테스트가 실패해도 부른다.
     */
    public fun close() {
        try {
            documentation.afterTest()
        } finally {
            runningTest?.let { RunningTest.closed(it) }
            OpenRuns.closed(openRun)
            changes.revert()
            RestDocsConfiguration.threadObjectMapper = startMapper
        }
    }

    /**
     * 이 실행을 지금 스레드에 묶는다. 묶인 동안
     * - 이 스레드에서 한 `RestDocsConfiguration.configure` 는 이 실행이 더한 블록이 되어 [close] 때 빠진다
     * - OpenAPI·AsyncAPI 요약은 이 실행의 `sectionTitle`([DocumentationLifecycle.startTest])이다. 이 요약은 묶지 않은
     *   스레드에서 문서화해도 REST Docs 문맥(문서 클래스, 스니펫 자리 이름)으로 찾으므로 [close] 전까지 어디서나 쓰인다
     * - 이 실행의 애플리케이션 매퍼를 쓴다
     * - provider 를 받지 않는 함수(`restDocsMockMvc(context)`, `restDocsWebTestClient(context)`)가 이 실행의 스니펫 자리를 쓴다
     *
     * 돌려받은 값을 닫으면 묶기 전으로 되돌리고, 그동안 스레드에서 바뀐 매퍼는 이 실행이 기억한다. 묶은 스레드에서 닫는다.
     * 묶지 않은 스레드(테스트가 직접 만든 스레드, 서버·비동기 스레드)에서 한 `configure` 는 전역 설정으로 남는다.
     *
     * JUnit 확장은 테스트 스레드에서 `beforeEach` 부터 `afterEach` 까지 묶는다. 코루틴처럼 실행 중에 스레드를 옮기는
     * 프레임워크는 코루틴이 스레드에서 실행을 시작할 때마다 묶고 멈출 때마다 닫는다(`kotlinx.coroutines.ThreadContextElement`).
     */
    public fun attachToCurrentThread(): AutoCloseable {
        val previousTest = RunningTest.set(runningTest)
        val previousRun = OpenRuns.set(openRun)
        val previousMapper = RestDocsConfiguration.threadObjectMapper
        val previousChanges = RestDocsConfiguration.track(changes)

        RestDocsConfiguration.threadObjectMapper = contextObjectMapper

        return AutoCloseable {
            contextObjectMapper = RestDocsConfiguration.threadObjectMapper
            RestDocsConfiguration.threadObjectMapper = previousMapper
            RestDocsConfiguration.track(previousChanges)
            RunningTest.set(previousTest)
            OpenRuns.set(previousRun)
        }
    }

    override fun toString(): String = "DocumentationRun(documentClass=${documentClass.name}, snippetName=$snippetName)"
}
