package io.github.kardx.noservlet

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.pathMeans
import io.github.kardx.dsl.type
import io.github.kardx.junit.AutoRestDocumentationExtension
import io.github.kardx.websocket.MessageDocumentation
import io.github.kardx.webtestclient.andDocument
import io.github.kardx.webtestclient.restDocs
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files

private const val FIXTURE_PARAMETER = "kardx.no-servlet-fixture"

class GreetingResponse(val name: String, val message: String)

@RestController
class GreetingController {
    @GetMapping("/greetings/{name}")
    fun greet(@PathVariable name: String): GreetingResponse = GreetingResponse(name, "hello $name")
}

/** [FIXTURE_PARAMETER] 로 실행한 런처에서만 켠다. */
class NoServletFixtureCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        if (context.getConfigurationParameter(FIXTURE_PARAMETER).isPresent) {
            return ConditionEvaluationResult.enabled("launched by NoServletDocumentationTest")
        }

        return ConditionEvaluationResult.disabled("fixture for NoServletDocumentationTest")
    }
}

/** WebFlux 프로젝트의 문서화 테스트처럼 WebTestClient 와 메시지 문서화만 쓴다. */
@ExtendWith(NoServletFixtureCondition::class, AutoRestDocumentationExtension::class)
@DisplayName("인사 API")
class NoServletDocsFixture {
    @Test
    @DisplayName("인사 조회")
    fun greet(provider: RestDocumentationContextProvider) {
        WebTestClient.bindToController(GreetingController())
            .configureClient()
            .restDocs(provider)
            .build()
            .get()
            .uri("/greetings/{name}", "kim")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .andDocument {
                pathParameters("name" pathMeans "이름")
                responseFields("name" type STRING means "이름", "message" type STRING means "인사말")
            }
    }

    @Test
    @DisplayName("인사 메시지")
    fun message(provider: RestDocumentationContextProvider) {
        MessageDocumentation(provider).send("/app/greet", payload = mapOf("name" to "kim")) {
            fields("name" type STRING means "이름")
        }
    }
}

class NoServletDocumentationTest :
    BehaviorSpec({
        given("MockMvc·서블릿 없는 클래스패스의 JUnit 문서화 테스트") {
            val output = Files.createTempDirectory("no-servlet").toFile()

            RestDocsConfiguration.configure {
                this.output.snippetsDirectory = output.resolve("snippets").path
                this.output.asciidocDirectory = output.resolve("asciidoc").path
            }

            val summary = SummaryGeneratingListener()
            val request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(NoServletDocsFixture::class.java))
                .filters(includeEngines("junit-jupiter"))
                .configurationParameter(FIXTURE_PARAMETER, "true")
                .build()

            LauncherFactory.create()
                .execute(request, summary)

            RestDocsConfiguration.reset()

            then("WebTestClient 문서화와 메시지 문서화가 모두 통과한다") {
                summary.summary.failures.map { it.exception.toString() } shouldBe emptyList()
                summary.summary.testsSucceededCount shouldBe 2
            }

            then("스니펫과 클래스 문서를 쓴다") {
                val snippets = File(output, "snippets/no-servlet-docs-fixture")

                File(snippets, "greet/response-fields.adoc").readText() shouldContain "인사말"
                File(snippets, "message/message-01.adoc").readText() shouldContain "/app/greet"
                File(output, "asciidoc/no-servlet-docs-fixture.adoc").readText() shouldContain "인사 API"
                File(output, "asciidoc/.restdocs-documents/no-servlet-docs-fixture.properties").readText() shouldContain
                    "document=no-servlet-docs-fixture"
            }
        }
    })
