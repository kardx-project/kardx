package io.github.kardx.junit

import io.github.kardx.config.RestDocsConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.EngineFilter.includeEngines
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/** 런처 실행 설정 이름. Gradle 이 아래 고정 클래스를 직접 실행하지 않도록 이 값이 있을 때만 켠다. */
private const val FIXTURE_PARAMETER = "kardx.test.configuration-restore"

/** 고정 클래스의 각 테스트가 본 `commonIncludeFiles` (테스트 이름 → 값) */
private val SEEN = ConcurrentHashMap<String, List<String>>()

private fun record(name: String) {
    SEEN[name] = RestDocsConfiguration.current.document.commonIncludeFiles
}

private fun include(file: String) = RestDocsConfiguration.configure {
    document.commonIncludeFiles =
        document.commonIncludeFiles + file
}

/** [FIXTURE_PARAMETER] 로 실행한 런처에서만 켠다. */
class LauncherOnlyCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        if (context.getConfigurationParameter(FIXTURE_PARAMETER).isPresent) {
            return ConditionEvaluationResult.enabled("launched by ConfigurationRestoreTest")
        }

        return ConditionEvaluationResult.disabled("fixture for ConfigurationRestoreTest")
    }
}

@ExtendWith(LauncherOnlyCondition::class, AutoRestDocumentationExtension::class)
@TestMethodOrder(MethodOrderer.MethodName::class)
class ConfigurationRestoreFixture {
    @BeforeEach
    fun beforeEach() = include("each.adoc")

    @Test
    fun aFirst() {
        record("aFirst")
        include("method.adoc")
    }

    @Test
    fun bFailing() {
        include("failing.adoc")

        error("실패해도 설정은 되돌아간다")
    }

    @Test
    fun cSecond() = record("cSecond")

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() = include("class.adoc")
    }
}

@ExtendWith(LauncherOnlyCondition::class, AutoRestDocumentationExtension::class)
class ConfigurationRestoreNextFixture {
    @Test
    fun next() = record("next")
}

class ConfigurationRestoreTest :
    BehaviorSpec({
        given("AutoRestDocumentationExtension 을 붙인 테스트 클래스 두 개를 차례로 실행하면") {
            `when`("@AfterEach 에서 reset 하지 않아도") {
                SEEN.clear()

                val output = Files.createTempDirectory("restore").toFile()

                RestDocsConfiguration.configure {
                    this.output.snippetsDirectory = output.resolve("snippets").path
                    this.output.asciidocDirectory = output.resolve("asciidoc").path
                    document.commonIncludeFiles = listOf("outside.adoc")
                }

                val summary = SummaryGeneratingListener()
                val request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(
                        selectClass(ConfigurationRestoreFixture::class.java),
                        selectClass(ConfigurationRestoreNextFixture::class.java),
                    ).filters(includeEngines("junit-jupiter"))
                    .configurationParameter(FIXTURE_PARAMETER, "true")
                    .build()

                LauncherFactory.create()
                    .execute(request, summary)

                // 프로젝트 리스너가 then 마다 설정을 지우므로 실행 직후의 값을 잡아 둔다
                val afterRun = RestDocsConfiguration.current.document.commonIncludeFiles

                then("모든 테스트가 실행된다 (일부러 실패시킨 하나만 실패)") {
                    summary.summary.testsStartedCount shouldBe 4
                    summary.summary.testsFailedCount shouldBe 1
                }

                then("@BeforeEach 설정은 테스트마다 한 번만 적용되고 앞 테스트의 설정이 쌓이지 않는다") {
                    SEEN["aFirst"].orEmpty() shouldContainExactly listOf("outside.adoc", "class.adoc", "each.adoc")
                    SEEN["cSecond"].orEmpty() shouldContainExactly listOf("outside.adoc", "class.adoc", "each.adoc")
                }

                then("다음 클래스에는 @BeforeAll 과 클래스 밖의 설정만 남는다") {
                    SEEN["next"].orEmpty() shouldContainExactly listOf("outside.adoc", "class.adoc")
                    afterRun shouldContainExactly listOf("outside.adoc", "class.adoc")
                }
            }
        }
    })
