package io.github.kardx.junit

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.io.File
import java.nio.file.Files

/** 런처 실행 설정 이름. Gradle 이 아래 고정 클래스를 직접 실행하지 않도록 이 값이 있을 때만 켠다. */
private const val CLIENTS_PARAMETER = "kardx.test.client-parameters"

/** 스니펫 출력 디렉터리. 런처 실행 전에 정한다 (Boot 의 `@AutoConfigureRestDocs` 흉내도 같은 곳에 쓴다) */
private const val OUTPUT_PARAMETER = "kardx.test.client-parameters.output"

/** [CLIENTS_PARAMETER] 로 실행한 런처에서만 켠다. */
class ClientsLauncherOnly : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        if (context.getConfigurationParameter(CLIENTS_PARAMETER).isPresent) {
            return ConditionEvaluationResult.enabled("launched by ClientParameterTest")
        }

        return ConditionEvaluationResult.disabled("fixture for ClientParameterTest")
    }
}

@RestController
class ClientParameterController {
    @GetMapping("/clients/{id}")
    fun find(@PathVariable id: Long): Map<String, Any> = mapOf("id" to id)
}

@Configuration
@EnableWebMvc
@Import(ClientParameterController::class)
class ClientParameterConfig

@ExtendWith(ClientsLauncherOnly::class, AutoRestDocumentationExtension::class)
@SpringJUnitWebConfig(ClientParameterConfig::class)
class InjectedClientFixture {
    @Autowired
    lateinit var context: WebApplicationContext

    private lateinit var fromBeforeEach: MockMvc

    /** 기본 클래스 없이 provider 를 받지 않는 함수로 만든 클라이언트 */
    private val fromField by lazy { restDocsMockMvc(context) }

    @BeforeEach
    fun setUp(mockMvc: MockMvc) {
        fromBeforeEach = mockMvc
    }

    @Test
    fun methodParameter(mockMvc: MockMvc) {
        mockMvc.perform(get("/clients/{id}", 1)).andDocument { }
    }

    @Test
    fun beforeEachParameter() {
        fromBeforeEach.perform(get("/clients/{id}", 2)).andDocument { }
    }

    @Test
    fun currentTestClient() {
        fromField.perform(get("/clients/{id}", 3)).andDocument { }
    }

    @Nested
    inner class Inner {
        @Test
        fun nestedCase(mockMvc: MockMvc) {
            mockMvc.perform(get("/clients/{id}", 4)).andDocument { }
        }
    }
}

/** Spring 테스트가 아닌 클래스의 MockMvc 파라미터 */
@ExtendWith(ClientsLauncherOnly::class, AutoRestDocumentationExtension::class)
class NotSpringFixture {
    @Test
    fun notSpring(mockMvc: MockMvc) {
        mockMvc.perform(get("/clients/{id}", 1))
    }
}

/**
 * Spring Boot `@AutoConfigureRestDocs` 가 주입하는 MockMvc 흉내: 테스트 클래스(`@Nested` 면 안쪽 클래스)와 메서드 이름으로
 * 스니펫 자리를 정하는 `ManualRestDocumentation` 을 쓴다.
 */
private fun bootLikeMockMvc(context: WebApplicationContext, testClass: Class<*>, methodName: String): MockMvc {
    val output = checkNotNull(System.getProperty(OUTPUT_PARAMETER))
    val documentation = ManualRestDocumentation(output).apply { beforeTest(testClass, methodName) }

    return MockMvcBuilders.webAppContextSetup(context)
        .apply<DefaultMockMvcBuilder>(documentationConfiguration(documentation))
        .build()
}

@ExtendWith(ClientsLauncherOnly::class, AutoRestDocumentationExtension::class)
@SpringJUnitWebConfig(ClientParameterConfig::class)
class BootInjectedFixture {
    @Autowired
    lateinit var context: WebApplicationContext

    @Test
    fun plainMethod() {
        bootLikeMockMvc(context, BootInjectedFixture::class.java, "plainMethod")
            .perform(get("/clients/{id}", 1))
            .andDocument { }
    }

    @Nested
    inner class Inner {
        @Test
        fun nestedCase() {
            bootLikeMockMvc(context, Inner::class.java, "nestedCase")
                .perform(get("/clients/{id}", 2))
                .andDocument { }
        }
    }
}

/** REST Docs 설정 없이 주입받은 그대로의 MockMvc (`@AutoConfigureMockMvc`) */
@ExtendWith(ClientsLauncherOnly::class, AutoRestDocumentationExtension::class)
@SpringJUnitWebConfig(ClientParameterConfig::class)
class PlainInjectedFixture {
    @Autowired
    lateinit var context: WebApplicationContext

    @Test
    fun withoutRestDocs() {
        MockMvcBuilders.webAppContextSetup(context)
            .build()
            .perform(get("/clients/{id}", 1))
            .andDocument { }
    }
}

private fun launch(output: File, vararg fixtures: Class<*>): SummaryGeneratingListener {
    RestDocsConfiguration.configure {
        this.output.snippetsDirectory = output.resolve("snippets").path
        this.output.asciidocDirectory = output.resolve("asciidoc").path
    }

    System.setProperty(OUTPUT_PARAMETER, output.resolve("snippets").path)

    val summary = SummaryGeneratingListener()
    val request = LauncherDiscoveryRequestBuilder.request()
        .selectors(fixtures.map { selectClass(it) })
        .filters(includeEngines("junit-jupiter"))
        .configurationParameter(CLIENTS_PARAMETER, "true")
        .build()

    try {
        LauncherFactory.create()
            .execute(request, summary)
    } finally {
        System.clearProperty(OUTPUT_PARAMETER)
        RestDocsConfiguration.reset()
    }

    return summary
}

/** 실패한 테스트 이름 → 예외 메시지 */
private fun SummaryGeneratingListener.failureMessages(): Map<String, String> = summary.failures.associate {
    it.testIdentifier.displayName to it.exception.message.orEmpty()
}

private fun File.snippetDirectories(documentName: String): List<String> = resolve("snippets/$documentName")
    .listFiles { file -> file.isDirectory }
    .orEmpty()
    .map { it.name }

class ClientParameterTest :
    BehaviorSpec({
        given("테스트 메서드·@BeforeEach 의 MockMvc 파라미터와 restDocsMockMvc(context)") {
            val output = Files.createTempDirectory("client-parameters").toFile()
            val summary = launch(output, InjectedClientFixture::class.java)

            then("기본 클래스 없이 모든 테스트가 문서화된다") {
                summary.failureMessages() shouldBe emptyMap()
                summary.summary.testsSucceededCount shouldBe 4
                output.snippetDirectories("injected-client-fixture") shouldContainExactlyInAnyOrder listOf(
                    "method-parameter",
                    "before-each-parameter",
                    "current-test-client",
                    "inner-nested-case",
                )
            }
        }

        given("Spring 테스트가 아닌 클래스의 MockMvc 파라미터") {
            val summary = launch(Files.createTempDirectory("client-parameters").toFile(), NotSpringFixture::class.java)

            then("KDX-014 로 어떻게 고치는지 알려 준다") {
                val message = summary.failureMessages().getValue("notSpring(MockMvc)")

                message shouldStartWith "kardx[KDX-014]: "
                message shouldContain "is not a Spring test"
                message shouldContain "troubleshooting.md#kdx-014"
            }
        }

        given("Spring Boot @AutoConfigureRestDocs 처럼 확장이 만들지 않은 REST Docs 설정으로 문서화하면") {
            val output = Files.createTempDirectory("client-parameters").toFile()
            val summary = launch(output, BootInjectedFixture::class.java)
            val failures = summary.failureMessages()

            then("스니펫이 문서가 읽는 자리와 같은 평범한 메서드는 통과한다") {
                failures.keys shouldBe setOf("nestedCase()")
                output.snippetDirectories("boot-injected-fixture") shouldBe listOf("plain-method")
            }

            then("@Nested 처럼 스니펫이 다른 곳에 생기면 KDX-011 로 실패하고 그 자리에 이 라이브러리의 스니펫을 남기지 않는다") {
                val message = failures.getValue("nestedCase()")

                message shouldStartWith "kardx[KDX-011]: "
                message shouldContain "@AutoConfigureRestDocs"
                message shouldContain "boot-injected-fixture/inner-nested-case"
                // REST Docs 기본 스니펫(curl-request 등)은 이 검사보다 먼저 쓰인다
                output.resolve("snippets/inner/nested-case/api-summary.adoc").exists() shouldBe false
            }
        }

        given("REST Docs 설정이 없는 MockMvc 로 문서화하면") {
            val summary =
                launch(Files.createTempDirectory("client-parameters").toFile(), PlainInjectedFixture::class.java)

            then("KDX-012 로 클라이언트를 만드는 방법을 알려 준다") {
                val message = summary.failureMessages().getValue("withoutRestDocs()")

                message shouldStartWith "kardx[KDX-012]: "
                message shouldContain "restDocsMockMvc(context)"
            }
        }
    })
