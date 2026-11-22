package io.github.kardx.junit

import io.github.kardx.asciidoc.DocumentGenerator
import io.github.kardx.asciidoc.DocumentGeneratorFactory
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.mockmvc.andDocument
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
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
import java.lang.reflect.Modifier
import java.nio.file.Files

/** 런처 실행 설정 이름. Gradle 이 아래 고정 클래스를 직접 실행하지 않도록 이 값이 있을 때만 켠다. */
private const val REGISTERED_PARAMETER = "kardx.test.registered-extension"

/** [REGISTERED_PARAMETER] 로 실행한 런처에서만 켠다. */
class RegisteredLauncherOnly : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        if (context.getConfigurationParameter(REGISTERED_PARAMETER).isPresent) {
            return ConditionEvaluationResult.enabled("launched by RegisteredExtensionTest")
        }

        return ConditionEvaluationResult.disabled("fixture for RegisteredExtensionTest")
    }
}

/** 생성자 인자로 문서 생성기를 바꾼 확장을 `@RegisterExtension` 으로 등록한 테스트. */
@ExtendWith(RegisteredLauncherOnly::class)
class RegisteredGeneratorFixture {
    @Test
    fun find(provider: RestDocumentationContextProvider) {
        MockMvcBuilders.standaloneSetup(TitleController())
            .apply<StandaloneMockMvcBuilder>(documentationConfiguration(provider))
            .build()
            .perform(get("/titles/{id}", 1))
            .andDocument { }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val restDocs = AutoRestDocumentationExtension(
            documentGenerator = DocumentGeneratorFactory { context ->
                object : DocumentGenerator() {
                    override fun startPart(): String = "// generated for ${context.documentClass.simpleName}"

                    override fun config(): Config = Config(toc = "right")
                }
            },
        )
    }
}

class RegisteredExtensionTest :
    BehaviorSpec({
        given("AutoRestDocumentationExtension") {
            then("상속할 수 없고, JUnit 콜백도 바꿀 수 없다") {
                Modifier.isFinal(AutoRestDocumentationExtension::class.java.modifiers) shouldBe true
            }

            then("@ExtendWith 로 쓰도록 인자 없는 생성자가 있다") {
                AutoRestDocumentationExtension::class.java.getConstructor()
                    .newInstance()
            }
        }

        given("@RegisterExtension 으로 documentGenerator 를 준 확장") {
            val output = Files.createTempDirectory("registered").toFile()

            RestDocsConfiguration.configure {
                this.output.snippetsDirectory = output.resolve("snippets").path
                this.output.asciidocDirectory = output.resolve("asciidoc").path
            }

            val summary = SummaryGeneratingListener()
            val request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(RegisteredGeneratorFixture::class.java))
                .filters(includeEngines("junit-jupiter"))
                .configurationParameter(REGISTERED_PARAMETER, "true")
                .build()

            LauncherFactory.create()
                .execute(request, summary)

            RestDocsConfiguration.reset()

            then("그 생성기로 문서를 쓰고, 생성기는 문서 클래스를 받는다") {
                summary.summary.totalFailureCount shouldBe 0

                val document = output.resolve("asciidoc/registered-generator-fixture.adoc")
                    .readText()

                document shouldContain "// generated for RegisteredGeneratorFixture"
                document shouldContain ":toc: right"
            }
        }
    })
