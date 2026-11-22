package io.github.kardx.kotest

import io.github.kardx.asciidoc.DocumentGeneratorFactory
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.lifecycle.DocumentationLifecycle
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeInvocationListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.springframework.restdocs.RestDocumentationContextProvider

/**
 * Kotest 5.x 스펙에서 Spring REST Docs 스니펫과 스펙별 AsciiDoc 문서를 만드는 확장. Kotest 6.x 용
 * `kardx-kotest` 의 같은 이름 클래스와 공개 API·동작이 같다. JUnit 의 `AutoRestDocumentationExtension` 과
 * 같은 일을 하고, 수명주기는 같은 코드([DocumentationLifecycle])를 쓴다.
 *
 * - 리프 테스트(`then`, `it`, `test` 등)마다 스니펫 디렉터리 `<spec>/<스니펫 자리 이름>/` 을 정하고, 이번 실행에서 처음 쓸 때
 *   지난 스니펫을 지운다. 컨테이너(`given`, `when`, `describe`, `context`)는 문서화 대상이 아니다. 데이터 테스트(`withData`)의
 *   행은 리프로 보고 문서화한다 (5.x 는 행이 컨테이너인지 미리 알 수 없다. `withData` 를 중첩하면 안쪽은 바깥 행의 섹션에 들어간다)
 * - 실패한 테스트의 스니펫은 지우고 문서와 OpenAPI 에서 뺀다. 문서화 호출이 없는 테스트(스니펫 없음)는 문서에 넣지 않는다
 * - 리프 테스트 안과 그 `beforeEach`·`beforeTest`·`afterEach`·`afterTest` 에서 한 `RestDocsConfiguration.configure` 는
 *   테스트가 끝나면 그 블록만 뺀다. 스펙 본문(init)이나 `beforeSpec` 에서 한 설정은 유지된다. 격리 모드(`InstancePerLeaf`,
 *   `InstancePerTest`)는 스펙 본문과 `beforeSpec` 을 인스턴스마다 다시 실행하므로 격리 모드에서는 `beforeEach` 에서 설정한다
 * - 문서(`.adoc`)에 쓸 설정은 사용자 `beforeEach`·`beforeTest` 가 끝나고 테스트 본문이 시작하기 직전에 기억한다
 *   (Kotest `BeforeInvocationListener`). 테스트 본문에서 한 `configure` 는 문서에 들어가지 않는다
 * - 스펙이 끝나면 `<spec-name>.adoc` 을 [io.github.kardx.config.OutputSettings.asciidocDirectory] 에 쓴다. 섹션은 리프 테스트를 처음 실행한 순서다
 * - Kotest Spring 확장(5.x 는 `io.kotest.extensions:kotest-extensions-spring`)이 스펙에 있으면 테스트마다 그 컨텍스트의
 *   `kardx.*` 속성과 Jackson 매퍼 빈을 쓴다. 없으면 코드 설정만 쓴다
 *
 * 이름 규칙(문서 제목, 섹션 제목 [TestNames.sectionTitle], 스니펫 자리 이름 [TestNames.snippetName])과 생성자 인자는 6.x 판과 같다.
 *
 * ```kotlin
 * @SpringBootTest
 * @DisplayName("사용자 API")
 * class UserApiDocsSpec(context: WebApplicationContext) : BehaviorSpec({
 *     val restDocs = extension(RestDocsExtension())
 *     val mockMvc = restDocsMockMvc(context, restDocs.provider)
 *
 *     given("사용자가 있을 때") {
 *         `when`("단건 조회하면") {
 *             then("사용자 정보를 돌려준다") {
 *                 mockMvc.perform(get("/api/v1/users/{id}", 1))
 *                     .andDocument { responseFields<UserResponse>() }
 *             }
 *         }
 *     }
 * }) {
 *     override fun extensions() = listOf(SpringExtension)
 * }
 * ```
 *
 * 프로젝트 전체에 등록하려면 `AbstractProjectConfig` 의 `override fun extensions()` 에 넣고, 스펙에서는 [currentTestProvider] 를
 * 쓴다. 스펙과 프로젝트 양쪽에 등록하면 스펙에 등록한 확장이 그 스펙의 테스트를 문서화하고 문서도 그 확장이 한 번만 쓴다
 * (스펙의 생성자 인자가 프로젝트 설정의 것보다 우선한다). 프로젝트 설정의 확장은 그 스펙을 건너뛴다.
 * 한 스펙 안의 테스트를 병렬로 실행하는 것은 권하지 않는다 ([RestDocsConfiguration] 의 "병렬 실행 제한").
 *
 * @param sectionTitle 테스트 이름(바깥 컨테이너 → 리프, 스타일 접두사 제외) → 섹션 제목. 기본값 [SectionTitleProvider.DEFAULT]
 * @param snippetName 테스트 이름 → 스니펫 자리 이름. 기본값 [SnippetNameProvider.DEFAULT]. 돌려준 이름도 경로에 쓸 수 있는
 *   모양으로 다듬는다 ([TestNames.slug] 를 `-` 사이마다 적용, 비면 `test`)
 * @param documentGenerator 문서 각 파트를 만드는 생성기. 제목 형식·AsciiDoc 속성·공통 include 를 바꿀 때 쓴다.
 *   JUnit `AutoRestDocumentationExtension` 의 같은 이름 인자와 같다
 */
public class RestDocsExtension(
    sectionTitle: SectionTitleProvider = SectionTitleProvider.DEFAULT,
    snippetName: SnippetNameProvider = SnippetNameProvider.DEFAULT,
    documentGenerator: DocumentGeneratorFactory = DocumentGeneratorFactory.DEFAULT,
) : TestCaseExtension,
    BeforeInvocationListener,
    AfterSpecListener {

    init {
        // 클래스패스의 Kotest 판이 이 모듈과 다르면 NoSuchMethodError 대신 바꿀 아티팩트를 알려 주고 실패한다
        KotestLine.FIVE.verify()
    }

    private val documentation = LeafTestDocumentation(sectionTitle, snippetName, documentGenerator)

    /**
     * 지금 실행 중인 리프 테스트의 스니펫 자리를 주는 REST Docs 문맥. `restDocsMockMvc(context, provider)`,
     * `restDocsWebTestClient(context, provider)`, `MessageDocumentation(provider, ...)` 에 넘긴다.
     * 테스트 코루틴 밖의 스레드에서 부르면 이 확장이 마지막으로 시작한 리프 테스트로 대신 찾는다.
     * 리프 테스트 밖(스펙 본문, 컨테이너)에서 문서화하면 [IllegalStateException].
     */
    public val provider: RestDocumentationContextProvider = documentation.provider

    override suspend fun intercept(testCase: TestCase, execute: suspend (TestCase) -> TestResult): TestResult =
        documentation.intercept(
            leaf = testCase.isLeaf && testCase.isDocumentedBy(this),
            specClass = testCase.spec::class.java,
            path = testCase::documentationPath,
            outcome = { result: TestResult? -> TestOutcome(result?.isErrorOrFailure, result?.isSuccess) },
        ) { execute(testCase) }

    /** 사용자 `beforeEach`·`beforeTest` 가 모두 끝나고 테스트 본문을 실행하기 직전에 지금 설정을 문서 설정으로 기억한다. */
    override suspend fun beforeInvocation(testCase: TestCase, iteration: Int) {
        documentation.captureSettings()
    }

    /** 스펙 인스턴스가 끝나면 그 스펙 클래스의 문서를 쓴다 (격리 모드면 끝날 때마다 그때까지의 테스트로 다시 쓴다). */
    override suspend fun afterSpec(spec: Spec) {
        documentation.writeDocument(spec::class.java)
    }

    /** 확장 인스턴스 없이 쓰는 문맥 ([currentTestProvider]). */

    public companion object {
        /**
         * 프로젝트 전체에 등록한 확장과 함께 쓰는 REST Docs 문맥. 스펙에서 확장 인스턴스 없이 지금 리프 테스트를 찾는다.
         * 코루틴 밖의 스레드에서 부르면 [IllegalStateException] 이다 ([provider] 는 마지막 테스트로 찾는다).
         *
         * ```kotlin
         * class ProjectConfig : AbstractProjectConfig() {
         *     override fun extensions() = listOf(RestDocsExtension())
         * }
         * ```
         */
        public val currentTestProvider: RestDocumentationContextProvider = LeafTestDocumentation.currentTestProvider
    }
}
