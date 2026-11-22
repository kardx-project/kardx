package io.github.kardx.junit

import io.github.kardx.config.RestDocsConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.EngineFilter.includeEngines
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 런처 실행 설정 이름. Gradle 이 아래 고정 클래스를 직접 실행하지 않도록 이 값이 있을 때만 켠다. */
private const val CONCURRENT_FIXTURE = "kardx.test.concurrent-restore"

/** 한쪽 클래스가 다른 쪽을 기다리는 최대 시간. 순서가 어긋나 기다림이 끝나지 않아도 테스트가 멈추지 않게 한다. */
private const val WAIT_SECONDS = 20L

/** 고정 클래스의 각 테스트가 본 `commonIncludeFiles` (이름 → 값) */
private val OBSERVED = ConcurrentHashMap<String, List<String>>()

/** 두 클래스의 실행 순서를 맞추는 신호. 런처를 실행할 때마다 새로 만든다 */
private object Signals {
    @Volatile
    var aConfigured = CountDownLatch(1)

    @Volatile
    var bConfigured = CountDownLatch(1)

    @Volatile
    var aFinished = CountDownLatch(1)

    fun reset() {
        aConfigured = CountDownLatch(1)
        bConfigured = CountDownLatch(1)
        aFinished = CountDownLatch(1)
    }
}

private fun observe(name: String) {
    OBSERVED[name] = RestDocsConfiguration.current.document.commonIncludeFiles
}

private fun add(file: String) = RestDocsConfiguration.configure {
    document.commonIncludeFiles =
        document.commonIncludeFiles + file
}

private fun CountDownLatch.awaitOrFail(name: String) {
    check(await(WAIT_SECONDS, TimeUnit.SECONDS)) { "timed out waiting for $name" }
}

/** [CONCURRENT_FIXTURE] 로 실행한 런처에서만 켠다. */
class ConcurrentLauncherOnly : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        if (context.getConfigurationParameter(CONCURRENT_FIXTURE).isPresent) {
            return ConditionEvaluationResult.enabled("launched by ConcurrentConfigurationRestoreTest")
        }

        return ConditionEvaluationResult.disabled("fixture for ConcurrentConfigurationRestoreTest")
    }
}

/** 먼저 설정을 더하고, B 가 설정을 더한 뒤에 끝난다. 끝날 때 B 의 설정을 지우면 안 된다. */
@ExtendWith(ConcurrentLauncherOnly::class, AutoRestDocumentationExtension::class)
@Execution(ExecutionMode.CONCURRENT)
class ConcurrentRestoreAFixture {
    @Test
    fun a() {
        add("a.adoc")
        Signals.aConfigured.countDown()
        Signals.bConfigured.awaitOrFail("B to configure")
        observe("a")
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun afterAll() = Signals.aFinished.countDown()
    }
}

/** A 가 설정을 더한 뒤에 시작해서 A 가 끝날 때까지 기다린다. A 가 끝난 뒤에도 자기 설정이 남아 있어야 한다. */
@ExtendWith(ConcurrentLauncherOnly::class, AutoRestDocumentationExtension::class)
@Execution(ExecutionMode.CONCURRENT)
class ConcurrentRestoreBFixture {
    @Test
    fun b() {
        Signals.aConfigured.awaitOrFail("A to configure")
        add("b.adoc")
        Signals.bConfigured.countDown()
        Signals.aFinished.awaitOrFail("A to finish")
        observe("b")
    }
}

/** 테스트 본문과 `@AfterEach` 의 `reset()` 이 테스트가 끝난 뒤에도 유지되는지. */
@ExtendWith(ConcurrentLauncherOnly::class, AutoRestDocumentationExtension::class)
@TestMethodOrder(MethodOrderer.MethodName::class)
class ResetInsideTestFixture {
    @AfterEach
    fun afterEach() {
        // 테스트가 끝날 때 설정을 지운다. 다음 테스트에 @BeforeAll 설정이 되살아나면 안 된다
        RestDocsConfiguration.reset()
    }

    @Test
    fun aResets() = add("method.adoc")

    @Test
    fun bAfterReset() = observe("afterReset")

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() = add("class.adoc")
    }
}

/** `@AfterEach` 에서 한 설정이 다음 테스트로 새지 않는지. */
@ExtendWith(ConcurrentLauncherOnly::class, AutoRestDocumentationExtension::class)
@TestMethodOrder(MethodOrderer.MethodName::class)
class AfterEachConfigureFixture {
    @AfterEach
    fun afterEach() = add("after-each.adoc")

    @Test
    fun aFirst() = observe("afterEachFirst")

    @Test
    fun bSecond() = observe("afterEachSecond")
}

/** [classes] 를 JUnit 런처로 실행한다. [parallel] 이면 클래스를 동시에 실행한다. */
private fun launch(vararg classes: Class<*>, parallel: Boolean = false): SummaryGeneratingListener {
    val summary = SummaryGeneratingListener()
    val request = LauncherDiscoveryRequestBuilder.request()
        .selectors(classes.map { selectClass(it) })
        .filters(includeEngines("junit-jupiter"))
        .configurationParameter(CONCURRENT_FIXTURE, "true")
        .configurationParameter("junit.jupiter.execution.parallel.enabled", parallel.toString())
        .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
        .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
        .build()

    LauncherFactory.create()
        .execute(request, summary)

    return summary
}

private fun useTemporaryOutput() {
    val output = Files.createTempDirectory("concurrent-restore").toFile()

    RestDocsConfiguration.configure {
        this.output.snippetsDirectory = output.resolve("snippets").path
        this.output.asciidocDirectory = output.resolve("asciidoc").path
        document.commonIncludeFiles = listOf("outside.adoc")
    }
}

class ConcurrentConfigurationRestoreTest :
    BehaviorSpec({
        given("설정을 바꾸는 테스트 클래스 두 개를 동시에 실행하면") {
            OBSERVED.clear()
            Signals.reset()
            useTemporaryOutput()

            val summary =
                launch(ConcurrentRestoreAFixture::class.java, ConcurrentRestoreBFixture::class.java, parallel = true)
            val afterRun = RestDocsConfiguration.current.document.commonIncludeFiles

            then("두 테스트 모두 성공한다") {
                summary.summary.testsFailedCount shouldBe 0
                summary.summary.testsSucceededCount shouldBe 2
            }

            then("먼저 끝난 클래스가 다른 클래스의 설정을 지우지 않는다") {
                OBSERVED["b"].orEmpty() shouldContainExactly listOf("outside.adoc", "b.adoc")
            }

            then("동시에 실행 중인 동안에는 전역 값이 하나라 두 설정이 함께 보인다 (문서화한 제한)") {
                OBSERVED["a"].orEmpty() shouldContainExactly listOf("outside.adoc", "a.adoc", "b.adoc")
            }

            then("모두 끝나면 실행 밖에서 한 설정만 남는다 (늦게 시작한 클래스가 앞 클래스의 설정을 되살리지 않는다)") {
                afterRun shouldContainExactly listOf("outside.adoc")
            }
        }

        given("테스트의 @AfterEach 에서 reset() 을 부르면") {
            OBSERVED.clear()
            useTemporaryOutput()

            val summary = launch(ResetInsideTestFixture::class.java)
            val afterRun = RestDocsConfiguration.current.document.commonIncludeFiles

            then("테스트가 끝나도 지운 설정이 되살아나지 않는다 (@BeforeAll·클래스 밖 설정 포함)") {
                summary.summary.testsFailedCount shouldBe 0
                OBSERVED["afterReset"].orEmpty() shouldContainExactly emptyList()
                afterRun shouldContainExactly emptyList()
            }
        }

        given("@AfterEach 에서 configure 하면") {
            OBSERVED.clear()
            useTemporaryOutput()

            val summary = launch(AfterEachConfigureFixture::class.java)
            val afterRun = RestDocsConfiguration.current.document.commonIncludeFiles

            then("그 테스트가 끝날 때 되돌아가 다음 테스트로 새지 않는다") {
                summary.summary.testsFailedCount shouldBe 0
                OBSERVED["afterEachSecond"].orEmpty() shouldContainExactly listOf("outside.adoc")
                afterRun shouldContainExactly listOf("outside.adoc")
            }
        }
    })
