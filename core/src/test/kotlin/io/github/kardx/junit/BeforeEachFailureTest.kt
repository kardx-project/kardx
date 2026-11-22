package io.github.kardx.junit

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.mockmvc.andDocument
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.EngineFilter.includeEngines
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.io.File
import java.nio.file.Files

/** 런처 실행 설정 이름. Gradle 이 아래 고정 클래스를 직접 실행하지 않도록 이 값이 있을 때만 켠다. */
private const val BEFORE_EACH_PARAMETER = "kardx.test.before-each-failure"

/** [BEFORE_EACH_PARAMETER] 로 실행한 런처에서만 켠다. */
class BeforeEachLauncherOnly : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult =
        if (context.getConfigurationParameter(BEFORE_EACH_PARAMETER).isPresent) {
            ConditionEvaluationResult.enabled("launched by BeforeEachFailureTest")
        } else {
            ConditionEvaluationResult.disabled("fixture for BeforeEachFailureTest")
        }
}

private fun documentTitle(provider: RestDocumentationContextProvider) {
    MockMvcBuilders.standaloneSetup(TitleController())
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(provider))
        .build()
        .perform(get("/titles/{id}", 1))
        .andDocument { }
}

/** 반복 테스트의 두 번째 실행이 `@BeforeEach` 에서 실패한다. 첫 실행은 성공해 스니펫을 남긴다. */
@ExtendWith(BeforeEachLauncherOnly::class, AutoRestDocumentationExtension::class)
class BeforeEachFailureFixture {
    @BeforeEach
    fun prepare(info: TestInfo) {
        check(info.displayName != "repetition 2 of 2") { "before each failed" }
    }

    @RepeatedTest(2)
    fun repeated(provider: RestDocumentationContextProvider) = documentTitle(provider)

    @Test
    fun plain(provider: RestDocumentationContextProvider) = documentTitle(provider)
}

/** `cleanupFails` 는 본문이 성공한 뒤 `@AfterEach` 에서 실패한다. JUnit 은 이 테스트를 실패로 보고한다. */
@ExtendWith(BeforeEachLauncherOnly::class, AutoRestDocumentationExtension::class)
class AfterEachFailureFixture {
    @AfterEach
    fun cleanUp(info: TestInfo) {
        check(info.testMethod.map { it.name }.orElse("") != "cleanupFails") { "after each failed" }
    }

    @Test
    fun cleanupFails(provider: RestDocumentationContextProvider) = documentTitle(provider)

    @Test
    fun kept(provider: RestDocumentationContextProvider) = documentTitle(provider)
}

/** [fixture] 를 JUnit 런처로 돌리고, 요약과 생성된 문서를 돌려준다. */
private fun launch(fixture: Class<*>, documentName: String): Pair<SummaryGeneratingListener, String> {
    val output = Files.createTempDirectory("lifecycle-failure").toFile()

    RestDocsConfiguration.configure {
        this.output.snippetsDirectory = output.resolve("snippets").path
        this.output.asciidocDirectory = output.resolve("asciidoc").path
    }

    val summary = SummaryGeneratingListener()
    val request = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectClass(fixture))
        .filters(includeEngines("junit-jupiter"))
        .configurationParameter(BEFORE_EACH_PARAMETER, "true")
        .build()

    try {
        LauncherFactory.create()
            .execute(request, summary)
    } finally {
        RestDocsConfiguration.reset()
    }

    return summary to File(output, "asciidoc/$documentName.adoc").readText()
}

class BeforeEachFailureTest :
    BehaviorSpec({
        given("반복 테스트의 나중 실행이 @BeforeEach 에서 실패하면") {
            val (summary, document) = launch(BeforeEachFailureFixture::class.java, "before-each-failure-fixture")

            then("그 메서드는 실패로 기록되어 문서에서 빠진다") {
                summary.summary.testsFailedCount shouldBe 1
                document shouldContain "plain"
                document shouldNotContain "repeated"
            }
        }

        given("본문은 성공하고 사용자 @AfterEach 에서 실패하면") {
            val (summary, document) = launch(AfterEachFailureFixture::class.java, "after-each-failure-fixture")

            then("JUnit 이 실패로 보고한 테스트이므로 문서에서 빠진다") {
                summary.summary.testsFailedCount shouldBe 1
                document shouldContain "kept"
                document shouldNotContain "cleanup"
            }
        }
    })
