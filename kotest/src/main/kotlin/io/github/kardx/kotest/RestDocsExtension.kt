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
import io.kotest.engine.test.TestResult
import org.springframework.restdocs.RestDocumentationContextProvider

/**
 * Kotest 스펙에서 Spring REST Docs 스니펫과 스펙별 AsciiDoc 문서를 만드는 확장. JUnit 의
 * `AutoRestDocumentationExtension` 과 같은 일을 하고, 수명주기는 같은 코드([DocumentationLifecycle])를 쓴다.
 *
 * - 리프 테스트(`then`, `it`, `test` 등)마다 스니펫 디렉터리 `<spec>/<스니펫 자리 이름>/` 을 정하고, 이번 실행에서 처음 쓸 때
 *   지난 스니펫을 지운다. 컨테이너(`given`, `when`, `describe`, `context`)는 문서화 대상이 아니다
 * - 실패한 테스트의 스니펫은 지우고 문서와 OpenAPI 에서 뺀다. 문서화 호출이 없는 테스트(스니펫 없음)는 문서에 넣지 않는다
 * - 리프 테스트 안과 그 `beforeEach`·`beforeTest`·`afterEach`·`afterTest` 에서 한 `RestDocsConfiguration.configure` 는
 *   테스트가 끝나면 그 블록만 뺀다 (테스트 코루틴과 자식 코루틴에서 한 것). 스펙 본문(init)이나 `beforeSpec` 에서 한 설정은
 *   유지된다 (JUnit 의 `@BeforeAll` 과 같다). 격리 모드(`InstancePerRoot` 등)는 스펙 본문과 `beforeSpec` 을 인스턴스마다 다시
 *   실행하므로 거기서 한 `configure` 가 인스턴스 수만큼 쌓인다. 격리 모드에서는 `beforeEach` 에서 설정한다
 * - 문서(`.adoc`)에 쓸 설정은 사용자 `beforeEach`·`beforeTest` 가 끝나고 테스트 본문이 시작하기 직전에 기억한다
 *   (Kotest `BeforeInvocationListener`, JUnit 의 `@BeforeEach` 뒤와 같은 시점). 문서에는 마지막으로 실행한 리프 테스트의
 *   그 시점 설정이 쓰이므로 테스트 본문에서 한 `configure` 는 문서에 들어가지 않는다
 * - 스펙이 끝나면 `<spec-name>.adoc` 을 [io.github.kardx.config.OutputSettings.asciidocDirectory] 에 쓴다. 섹션은 리프 테스트를 처음 실행한 순서다
 * - Kotest Spring 확장(`io.kotest:kotest-extensions-spring`)이 스펙에 있으면 테스트마다 그 컨텍스트의 `kardx.*`
 *   속성과 Jackson 매퍼 빈을 쓴다. 없으면 코드 설정만 쓴다
 *
 * 이름 규칙 (바꾸려면 생성자 인자):
 * - 문서 제목: 스펙 클래스의 `@DisplayName`(Kotest `io.kotest.core.annotation.DisplayName` 또는 JUnit) + `@AsciiDescription`.
 *   없으면 클래스 단순 이름. 문서 파일·스니펫 디렉터리 첫 단계는 클래스 단순 이름의 kebab-case 다 (`user-api-docs-spec`)
 * - 섹션 제목: [TestNames.sectionTitle] (`사용자가 있을 때 - 단건 조회하면 - 사용자 정보를 돌려준다`)
 * - 스니펫 자리 이름: [TestNames.snippetName] (`사용자가-있을-때-단건-조회하면-사용자-정보를-돌려준다`). 섹션 앵커와
 *   (operationId 규칙이 `test-name` 이거나 핸들러를 알 수 없을 때) OpenAPI operationId 가 여기서 나온다. 같은 스펙에서 이름이 겹치면 뒤의 테스트에 `-2` 를 붙이고 경고한다
 *
 * ```kotlin
 * @ApplyExtension(SpringExtension::class)
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
 * })
 * ```
 *
 * 프로젝트 전체에 등록하려면 `AbstractProjectConfig.extensions` 에 넣고, 스펙에서는 [currentTestProvider] 를 쓴다.
 * 스펙과 프로젝트 양쪽에 등록하면 스펙에 등록한 확장이 그 스펙의 테스트를 문서화하고 문서도 그 확장이 한 번만 쓴다. 그래서
 * 스펙의 생성자 인자(`sectionTitle`, `snippetName`, `documentGenerator`)가 프로젝트 설정의 것보다 우선한다 (스펙 하나만
 * `:toclevels:` 를 바꾸는 등). 프로젝트 설정의 확장은 그 스펙을 건너뛴다.
 *
 * 한 스펙 안의 테스트를 병렬로 실행하는 것(`testExecutionMode`·`concurrency`)은 권하지 않는다. 각 테스트는 자기가 더한
 * `configure` 만 빼지만 설정 값은 JVM 전역이라 동시에 도는 테스트끼리 섞이고, 코루틴 밖에서 [provider] 를 부르면 어느
 * 테스트인지 정할 수 없다. 스펙끼리의 병렬 실행은 [RestDocsConfiguration] 의 "병렬 실행 제한" 을 따른다.
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
        KotestLine.SIX.verify()
    }

    private val documentation = LeafTestDocumentation(sectionTitle, snippetName, documentGenerator)

    /**
     * 지금 실행 중인 리프 테스트의 스니펫 자리를 주는 REST Docs 문맥. `restDocsMockMvc(context, provider)`,
     * `restDocsWebTestClient(context, provider)`, `MessageDocumentation(provider, ...)` 에 넘긴다.
     * 스펙 본문에서 MockMvc 를 한 번 만들어 두어도 문서화 호출마다 그때 실행 중인 테스트를 찾는다.
     * 테스트 코루틴 밖의 스레드(테스트가 직접 만든 스레드, `Executors` 등)에서 부르면 이 확장이 마지막으로 시작한 리프 테스트로
     * 대신 찾는다 ([currentTestProvider] 에는 이 대체가 없다).
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

    /**
     * 사용자 `beforeEach`·`beforeTest` 가 모두 끝나고 테스트 본문을 실행하기 직전에 지금 설정을 문서 설정으로 기억한다
     * ([io.github.kardx.lifecycle.DocumentationRun.captureSettings]). `invocations` 로 여러 번 실행하면 실행마다 덮어쓴다.
     */
    override suspend fun beforeInvocation(testCase: TestCase, iteration: Int) {
        documentation.captureSettings()
    }

    /**
     * 스펙 인스턴스가 끝나면 그 스펙 클래스의 문서를 쓴다. 격리 모드로 인스턴스가 여럿이면 끝날 때마다 그때까지의 테스트로
     * 다시 쓰므로 마지막 문서에 모든 테스트가 들어간다. 실행한 리프 테스트가 없는 스펙과, 이 확장이 문서화하지 않은 스펙
     * (스펙과 프로젝트 양쪽에 등록해 스펙의 확장이 문서화한 경우의 프로젝트 확장)은 쓰지 않는다.
     */
    override suspend fun afterSpec(spec: Spec) {
        documentation.writeDocument(spec::class.java)
    }

    /** 확장 인스턴스 없이 쓰는 문맥 ([currentTestProvider]). */

    public companion object {
        /**
         * 프로젝트 전체에 등록한 확장(`AbstractProjectConfig.extensions`)과 함께 쓰는 REST Docs 문맥. 스펙에서 확장 인스턴스를
         * 갖고 있지 않아도 지금 실행 중인 리프 테스트를 찾는다. 테스트 코루틴(과 그 자식 코루틴)에서 부를 때는 [provider] 와 같고,
         * 코루틴 밖의 스레드에서 부르면 대신 찾을 테스트가 없어 [IllegalStateException] 이다 ([provider] 는 마지막 테스트로 찾는다).
         *
         * ```kotlin
         * class ProjectConfig : AbstractProjectConfig() {
         *     override val extensions = listOf(RestDocsExtension())
         * }
         *
         * class UserApiDocsSpec(context: WebApplicationContext) : FunSpec({
         *     val mockMvc = restDocsMockMvc(context, RestDocsExtension.currentTestProvider)
         *     ...
         * })
         * ```
         */
        public val currentTestProvider: RestDocumentationContextProvider = LeafTestDocumentation.currentTestProvider
    }
}
