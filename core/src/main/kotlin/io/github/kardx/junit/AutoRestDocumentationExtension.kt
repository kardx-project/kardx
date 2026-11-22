package io.github.kardx.junit

import io.github.kardx.asciidoc.DocumentGeneratorFactory
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.internal.asciidoc.SnippetFileListExtractor
import io.github.kardx.internal.output.DisplayNames
import io.github.kardx.internal.output.SnippetNames
import io.github.kardx.internal.output.SnippetTarget
import io.github.kardx.internal.spring.SpringSettingsLoader
import io.github.kardx.lifecycle.DocumentationLifecycle
import io.github.kardx.lifecycle.DocumentationRun
import io.github.kardx.lifecycle.RestDocsExtensionApi
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

/**
 * Spring REST Docs 의 [RestDocumentationExtension] 대신 쓰는 JUnit 5 확장.
 * 수명주기(스니펫 정리·실패 처리·설정 되돌리기·문서 쓰기)는 Kotest 확장과 같은 [DocumentationLifecycle] 을 쓴다.
 *
 * REST Docs 확장과 같이 테스트 메서드에 `RestDocumentationContextProvider` 를 주입하고 스니펫을 만든다. 더해서
 * - 테스트 메서드를 실행하기 전에 그 메서드의 지난 스니펫을 지운다 (반복·파라미터 테스트는 첫 실행 전에만)
 * - 실패한 테스트는 스니펫을 지우고 문서에 넣지 않는다. 실패한 API 가 문서와 OpenAPI 에 남지 않는다
 * - 테스트 클래스가 끝나면 스니펫을 모아 `<class-name>.adoc` 을 [io.github.kardx.config.OutputSettings.asciidocDirectory] 에 쓴다.
 *   다른 패키지의 같은 이름 클래스가 같은 문서 이름을 쓰면 경고한다
 * - `@Nested` 테스트는 가장 바깥 클래스의 문서에 들어가고, `@ParameterizedTest` 는 실행마다 스니펫 디렉터리와
 *   문서 섹션이 따로 생기며, 이름이 같은 오버로드 테스트 메서드도 디렉터리가 겹치지 않는다
 *   (`user-api-test/get-user`, `@Nested` 는 `user-api-test/success-get-user`, 오버로드는 파라미터 타입 이름이 붙은
 *   `user-api-test/create-rest-documentation-context-provider`, 파라미터 테스트 실행은 `user-api-test/get-user/2`)
 * - OpenAPI·AsyncAPI 요약은 테스트 메서드의 `@DisplayName`, 없으면 JUnit 표시 이름에서 `()` 를 뗀 메서드 이름
 *   (`` fun `사용자 조회`() `` → `사용자 조회`)이다. 파라미터 테스트의 실행들은 메서드의 요약을 함께 쓴다. 태그는 문서 클래스(가장
 *   바깥 클래스)의 `@DisplayName`, 없으면 클래스 단순 이름이다. 테스트가 만든 다른 스레드에서 문서화해도 같다
 *
 * - 테스트 메서드가 끝나면 그 테스트 실행 중(`@BeforeEach`, 테스트 메서드, `@AfterEach`)에 테스트 스레드에서 한
 *   [RestDocsConfiguration.configure] 블록만 뺀다. 그래서 `@AfterEach` 에서 [RestDocsConfiguration.reset] 을 부르지 않아도
 *   그 설정이 다음 테스트에 쌓이지 않는다. `@BeforeAll`·`companion object`·클래스 밖에서 한 설정은 되돌리지 않으므로
 *   클래스가 끝나도 JVM 에 남아 뒤에 실행되는 다른 테스트 클래스에도 적용된다 (지우려면 `@AfterAll` 에서 `reset()`).
 *   테스트가 직접 만든 스레드에서 한 `configure` 도 전역에 남는다
 *
 * Spring 테스트(`@SpringBootTest` 등)면 첫 테스트 직전에 애플리케이션 설정의 `kardx.*` 속성을 읽는다.
 * 스니펫 디렉터리는 첫 테스트 직전에 [io.github.kardx.config.OutputSettings.snippetsDirectory] 에서 읽으므로 yml 이나 `@BeforeAll`,
 * `companion object` 에서 바꾼다. 문서를 만들 때는 마지막 테스트가 실행될 때(`@BeforeEach` 이후)의 설정을 쓰므로,
 * 테스트가 끝나 설정이 되돌아가도 `@BeforeEach` 에서 정한 `document.commonIncludeFiles` 같은 문서 설정이 클래스 문서에 쓰인다.
 * 병렬 실행(`@Execution(CONCURRENT)`)에서도 각 테스트는 자기가 더한 설정만 빼므로 다른 테스트의 설정을 지우거나 되살리지
 * 않는다. 다만 설정 값은 JVM 전역 하나라 동시에 도는 테스트가 서로 다른 `configure` 값을 쓰면 문서에 섞이고, 같은 클래스의
 * 메서드를 병렬로 돌리면 문서에 쓰는 "마지막 테스트" 의 설정이 정해지지 않는다. 테스트마다 다른 설정은
 * [RestDocsConfiguration.withSettings] 로 준다. 자세한 제한은 [RestDocsConfiguration] 을 본다.
 *
 * 테스트 메서드(와 `@BeforeEach`)의 `MockMvc`·`WebTestClient` 파라미터에 이 테스트의 REST Docs 용 클라이언트를 넣는다
 * ([supportsParameter]). 그래서 `@BeforeEach` 에서 `restDocsMockMvc(context, provider)` 를 부르는 기본 클래스가 없어도 된다.
 *
 * ```kotlin
 * @SpringBootTest
 * @ExtendWith(AutoRestDocumentationExtension::class)
 * @DisplayName("사용자 API")
 * class UserApiDocsTest {
 *     @Test
 *     fun getUser(mockMvc: MockMvc) {
 *         mockMvc.perform(get("/api/v1/users/{id}", 1))
 *             .andDocument { responseFields<UserResponse>() }
 *     }
 * }
 * ```
 *
 * 문서 생성기를 바꾸려면 `@ExtendWith` 대신 `@RegisterExtension` 으로 생성자 인자를 준다. 이 클래스는 상속할 수 없고 JUnit 콜백도
 * 바꿀 수 없다 (콜백 순서와 동작은 라이브러리가 정한다). Kotest `RestDocsExtension` 의 같은 이름 인자와 같다.
 *
 * ```kotlin
 * class UserApiDocsTest {
 *     companion object {
 *         @JvmField
 *         @RegisterExtension
 *         val restDocs = AutoRestDocumentationExtension(
 *             documentGenerator = DocumentGeneratorFactory {
 *                 object : DocumentGenerator() {
 *                     override fun config() = Config(toc = "right")
 *                 }
 *             },
 *         )
 *     }
 * }
 * ```
 *
 * Spring Boot 가 주입한 `MockMvc`(`@AutoConfigureRestDocs`)로 문서화하면 스니펫 디렉터리 규칙이 달라 `@Nested`·파라미터 테스트의
 * 섹션이 문서에서 빠질 수 있다. 그런 요청은 `KDX-011` 로 실패하거나(스니펫이 다른 곳에 생길 때) 한 번 경고한다.
 *
 * @param documentGenerator 문서 각 파트를 만드는 생성기. 제목 형식·AsciiDoc 속성·공통 include 를 바꿀 때 쓴다
 */
@OptIn(RestDocsExtensionApi::class)
public class AutoRestDocumentationExtension(
    private val documentGenerator: DocumentGeneratorFactory = DocumentGeneratorFactory.DEFAULT,
) : BeforeEachCallback,
    BeforeTestExecutionCallback,
    AfterTestExecutionCallback,
    AfterEachCallback,
    AfterAllCallback,
    ParameterResolver {

    private val lifecycle = DocumentationLifecycle()

    override fun beforeEach(context: ExtensionContext) {
        SpringSettingsLoader.load(context)

        val target = targetOf(context) ?: return

        target.invocation?.let {
            INVOCATION_TITLES.computeIfAbsent(target.documentClass.name) { ConcurrentHashMap() }[target.methodName] =
                context.displayName
        }

        // 파라미터 테스트는 실행마다 하위 디렉터리를 쓰므로 첫 실행 전에 메서드 디렉터리(baseName)를 통째로 비운다
        val run = lifecycle.startTest(
            target.documentClass,
            target.methodName,
            cleanName = target.baseName,
            sectionTitle = summaryOf(context, context.testMethod.orElseThrow()),
        )

        store(context).put(RUN_KEY, run)
        // 사용자 `@BeforeEach` 보다 먼저 불리므로, 여기서부터 [afterEach] 까지 테스트 스레드에서 한 configure 가 이 실행의 것이다
        store(context).put(ATTACHMENT_KEY, run.attachToCurrentThread())
    }

    /**
     * 테스트가 실제로 쓰는 설정 (`@BeforeEach` 적용 후). 클래스 문서를 만들 때 다시 적용한다.
     * 메서드가 실행될 때마다 덮어쓰므로, 문서에는 마지막으로 실행한 메서드의 설정이 쓰인다. 같은 클래스의 메서드를 병렬로
     * 돌리면(`@Execution(CONCURRENT)`) 어느 메서드의 설정인지 보장되지 않는다. 메서드마다 설정이 다르면 차례로 실행한다.
     */
    override fun beforeTestExecution(context: ExtensionContext) {
        runOf(context)?.captureSettings()
    }

    /**
     * 실패한 테스트의 스니펫은 지우고 문서에서 뺀다. 반복 테스트는 실행마다 같은 스니펫 디렉터리를 쓰므로
     * 마지막 실행 결과를 따른다: 실패 뒤의 실행이 성공하면 스니펫을 다시 만들고 문서(AsciiDoc·OpenAPI 모두)에 넣는다.
     * 파라미터 테스트는 실행마다 디렉터리가 달라 실패한 실행만 빠진다.
     */
    override fun afterTestExecution(context: ExtensionContext) {
        val run = runOf(context) ?: return

        run.finish(failed = context.executionException.isPresent)
        store(context).put(FINISHED_KEY, true)
    }

    /**
     * 사용자 `@AfterEach` 뒤에 불린다. 테스트 스레드에서 떼고, 이 테스트가 더한 설정을 빼고, 문서화 상태를 정리한다.
     * `@BeforeEach` 가 실패하면 JUnit 이 [afterTestExecution] 을 부르지 않으므로 여기서 결과를 기록한다 (반복 테스트의 앞선
     * 실행이 남긴 스니펫이 문서에 남지 않게). 본문은 성공했는데 사용자 `@AfterEach` 가 실패해도 JUnit 은 테스트를 실패로 보고하므로
     * 다시 실패로 기록해 문서에서 뺀다.
     */
    override fun afterEach(context: ExtensionContext) {
        val run = runOf(context) ?: return
        val finished = store(context).remove(FINISHED_KEY) != null
        val failed = context.executionException.isPresent

        if (!finished || failed) {
            run.finish(failed = failed)
        }

        try {
            store(context).remove(ATTACHMENT_KEY, AutoCloseable::class.java)?.close()
        } finally {
            run.close()
        }
    }

    /**
     * 테스트 메서드와 `@BeforeEach`·`@AfterEach` 의 파라미터 중 이 확장이 넣는 것:
     * - `RestDocumentationContextProvider`: 이 테스트 실행의 REST Docs 문맥
     * - `MockMvc`: Spring 테스트 컨텍스트로 만든 `restDocsMockMvc(context, provider)`
     * - `WebTestClient`: Spring 테스트 컨텍스트로 만든 `restDocsWebTestClient(context, provider)`
     *
     * `MockMvc`·`WebTestClient` 는 실행마다 새로 만든다 (기본 클래스의 `@BeforeEach` 에서 만들던 것과 같다). `@Autowired` 를 붙인
     * 파라미터는 Spring 이 넣으므로 건드리지 않는다. Spring 테스트가 아니면 `KDX-014` 로 실패한다.
     */
    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        if (targetOf(extensionContext) == null) return false

        return RestDocumentationContextProvider::class.java.isAssignableFrom(parameterContext.parameter.type) ||
            SpringTestClients.supports(parameterContext)
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        val provider = RestDocumentationContextProvider {
            checkNotNull(runOf(extensionContext)).provider.beforeOperation()
        }

        if (RestDocumentationContextProvider::class.java.isAssignableFrom(parameterContext.parameter.type)) {
            return provider
        }

        return SpringTestClients.resolve(parameterContext, extensionContext, provider)
    }

    /**
     * 테스트 클래스가 끝나면 AsciiDoc 문서를 만든다. 실패한 테스트 메서드는 뺀다.
     * `@BeforeAll`·`@AfterAll` 이 실패했으면(클래스 수준 예외) 건너뛴다. `@Nested` 클래스는 바깥 클래스가 끝날 때 함께 쓴다.
     */
    override fun afterAll(context: ExtensionContext) {
        if (context.executionException.isPresent) return

        val testClass = context.testClass.orElse(null) ?: return

        if (SnippetNames.documentClass(testClass) != testClass) return

        lifecycle.writeDocument(testClass, documentGenerator) {
            JUnitDocumentOutline(SnippetFileListExtractor(lifecycle.snippetsDirectory.path)).of(
                testClass,
                lifecycle.failedSnippets(testClass),
                INVOCATION_TITLES[testClass.name].orEmpty(),
            )
        }
    }

    /** 테스트 메서드 실행이면 그 스니펫 자리. 클래스 수준 콜백이면 null. */
    private fun targetOf(context: ExtensionContext): SnippetTarget? {
        val testClass = context.testClass.orElse(null) ?: return null
        val method = context.testMethod.orElse(null) ?: return null

        return SnippetNames.target(testClass, method, context.uniqueId)
    }

    /**
     * OpenAPI·AsyncAPI 요약이 될 테스트 표시 이름. 메서드의 `@DisplayName`, 없으면 JUnit 표시 이름(`DisplayNameGenerator`
     * 결과)에서 기본 형식의 인자 목록(`getUser()`, `getUser(RestDocumentationContextProvider)`)을 뗀 이름이다.
     * `@ParameterizedTest`·`@RepeatedTest` 의 실행은 같은 operation 을 문서화하므로 실행 이름(`[1] id=1`)이 아니라 메서드의
     * 표시 이름을 함께 쓴다. `@Nested` 클래스의 표시 이름은 붙이지 않는다 (태그는 문서 클래스 제목이다).
     */
    private fun summaryOf(context: ExtensionContext, method: Method): String {
        val methodContext = context.parent.getOrNull()
            ?.takeIf { it.testMethod.isPresent } ?: context

        return DisplayNames.of(method) ?: withoutParameterList(methodContext.displayName, method.name)
    }

    /** 표시 이름이 JUnit 기본 형식(`메서드 이름(인자 타입…)`)이면 메서드 이름만. 다른 형식(사용자 생성기)은 그대로. */
    private fun withoutParameterList(displayName: String, methodName: String): String {
        if (displayName.startsWith("$methodName(") && displayName.endsWith(")")) {
            return methodName
        }

        return displayName.ifBlank { methodName }
    }

    /** 테스트 실행마다 하나인 저장소. REST Docs 확장과 같이 실행의 고유 ID 로 나눈다. */
    private fun store(context: ExtensionContext): ExtensionContext.Store =
        context.getStore(ExtensionContext.Namespace.create(javaClass, context.uniqueId))

    /** 테스트 실행의 문서화 상태. [beforeEach] 가 테스트 메서드 실행에만 만든다. 클래스 수준 콜백이면 null. */
    private fun runOf(context: ExtensionContext): DocumentationRun? =
        store(context).get(RUN_KEY, DocumentationRun::class.java)

    private companion object {
        /** 테스트 실행 저장소에서 [DocumentationRun] 을 찾는 키 */
        private const val RUN_KEY = "documentation-run"

        /** 테스트 실행 저장소에서 스레드 묶음([DocumentationRun.attachToCurrentThread])을 찾는 키 */
        private const val ATTACHMENT_KEY = "documentation-run-attachment"

        /** [afterTestExecution] 이 결과를 기록했다는 표시. 없으면 [afterEach] 가 기록한다 */
        private const val FINISHED_KEY = "documentation-run-finished"

        /** 문서 클래스 이름 → (파라미터 테스트 실행의 메서드 자리 이름 → 실행 표시 이름) */
        val INVOCATION_TITLES: ConcurrentHashMap<String, MutableMap<String, String>> = ConcurrentHashMap()
    }
}
