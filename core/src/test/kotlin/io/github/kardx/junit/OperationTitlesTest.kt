package io.github.kardx.junit

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.mockmvc.andDocument
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.EngineFilter.includeEngines
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.thread

/** 런처 실행 설정 이름. Gradle 이 아래 고정 클래스를 직접 실행하지 않도록 이 값이 있을 때만 켠다. */
private const val TITLES_PARAMETER = "kardx.test.operation-titles"

/** [TITLES_PARAMETER] 로 실행한 런처에서만 켠다. */
class TitlesLauncherOnly : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        if (context.getConfigurationParameter(TITLES_PARAMETER).isPresent) {
            return ConditionEvaluationResult.enabled("launched by OperationTitlesTest")
        }

        return ConditionEvaluationResult.disabled("fixture for OperationTitlesTest")
    }
}

@RestController
class TitleController {
    @GetMapping("/titles/{id}")
    fun find(@PathVariable id: Long): Map<String, Any> = mapOf("id" to id)
}

private fun titleMockMvc(provider: RestDocumentationContextProvider): MockMvc =
    MockMvcBuilders.standaloneSetup(TitleController())
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(provider))
        .build()

private fun document(provider: RestDocumentationContextProvider, id: Long = 1) {
    titleMockMvc(provider).perform(get("/titles/{id}", id))
        .andDocument { }
}

@ExtendWith(TitlesLauncherOnly::class, AutoRestDocumentationExtension::class)
@DisplayName("타이틀 API")
class TitledFixture {
    @Test
    @DisplayName("단건 조회")
    fun withDisplayName(provider: RestDocumentationContextProvider) = document(provider)

    @Test
    fun `백틱 이름`(provider: RestDocumentationContextProvider) = document(provider)

    @Test
    fun plainName(provider: RestDocumentationContextProvider) = document(provider)

    @ParameterizedTest
    @ValueSource(longs = [1, 2])
    @DisplayName("파라미터 조회")
    fun parameterized(id: Long, provider: RestDocumentationContextProvider) = document(provider, id)

    @ParameterizedTest
    @ValueSource(longs = [3])
    fun parameterizedPlain(id: Long, provider: RestDocumentationContextProvider) = document(provider, id)

    @Test
    @DisplayName("다른 스레드")
    fun otherThread(provider: RestDocumentationContextProvider) {
        thread { document(provider) }.join()
    }

    @Nested
    @DisplayName("안쪽")
    inner class Inner {
        @Test
        fun nestedCase(provider: RestDocumentationContextProvider) = document(provider)
    }
}

@ExtendWith(TitlesLauncherOnly::class, AutoRestDocumentationExtension::class)
class UntitledFixture {
    @Test
    fun plain(provider: RestDocumentationContextProvider) = document(provider)
}

/** 스니펫 디렉터리 아래 모든 resource.json 의 (summary, tags). */
private fun operations(directory: File): List<Pair<String, List<String>>> {
    val mapper = ObjectMapper()

    return directory.walkTopDown()
        .filter { it.name == "resource.json" }
        .map { mapper.readTree(it) }
        .map { node -> node["summary"].asText() to node["tags"].map { it.asText() } }
        .toList()
}

class OperationTitlesTest :
    BehaviorSpec({
        given("@DisplayName 이 있거나 없는 JUnit 테스트를 AutoRestDocumentationExtension 으로 실행하면") {
            val output = Files.createTempDirectory("titles").toFile()
            val snippets = output.resolve("snippets")

            RestDocsConfiguration.configure {
                this.output.snippetsDirectory = snippets.path
                this.output.asciidocDirectory = output.resolve("asciidoc").path
            }

            val summary = SummaryGeneratingListener()
            val request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(TitledFixture::class.java), selectClass(UntitledFixture::class.java))
                .filters(includeEngines("junit-jupiter"))
                .configurationParameter(TITLES_PARAMETER, "true")
                .build()

            LauncherFactory.create()
                .execute(request, summary)

            RestDocsConfiguration.reset()

            then("모든 테스트가 성공한다") {
                summary.summary.totalFailureCount shouldBe 0
                summary.summary.testsSucceededCount shouldBe 9
            }

            then("요약은 표시 이름, 태그는 문서 클래스 제목이다") {
                operations(snippets.resolve("titled-fixture")) shouldContainExactlyInAnyOrder listOf(
                    "단건 조회" to listOf("타이틀 API"),
                    "백틱 이름" to listOf("타이틀 API"),
                    "plainName" to listOf("타이틀 API"),
                    "파라미터 조회" to listOf("타이틀 API"),
                    "파라미터 조회" to listOf("타이틀 API"),
                    "parameterizedPlain" to listOf("타이틀 API"),
                    "다른 스레드" to listOf("타이틀 API"),
                    "nestedCase" to listOf("타이틀 API"),
                )
            }

            then("@DisplayName 이 없는 클래스는 클래스 단순 이름이 태그다") {
                operations(snippets.resolve("untitled-fixture")) shouldContainExactlyInAnyOrder listOf(
                    "plain" to listOf("UntitledFixture"),
                )
            }
        }
    })
