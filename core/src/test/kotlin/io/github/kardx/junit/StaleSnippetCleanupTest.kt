package io.github.kardx.junit

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.Optional

class StaleSnippetFixture {
    fun getUser() = Unit
}

/** 테스트 클래스·메서드(와 표시 이름)만 알려주는 최소 ExtensionContext. 나머지 호출은 null 을 돌려준다. */
private fun contextOf(testClass: Class<*>, method: Method): ExtensionContext =
    Proxy.newProxyInstance(ExtensionContext::class.java.classLoader, arrayOf(ExtensionContext::class.java)) { _, m, _ ->
        when (m.name) {
            "getTestClass" -> Optional.of(testClass)
            "getTestMethod" -> Optional.of(method)
            "getUniqueId" -> "[engine:junit-jupiter]/[class:${testClass.name}]/[method:${method.name}()]"
            "getParent" -> Optional.empty<ExtensionContext>()
            "getDisplayName" -> "${method.name}()"
            else -> null
        }
    } as ExtensionContext

private fun AutoRestDocumentationExtension.runBeforeEach(context: ExtensionContext) {
    // REST Docs 의 beforeEach 는 최소 컨텍스트에서 실패한다. 정리는 그 전에 끝난다.
    runCatching { beforeEach(context) }
}

class StaleSnippetCleanupTest :
    BehaviorSpec({
        given("이전 실행의 스니펫이 남은 테스트 메서드") {
            val output = Files.createTempDirectory("snippets").toFile()
            val withOutput: RestDocsSettings.() -> Unit = { this.output.snippetsDirectory = output.absolutePath }
            val stale = File(output, "stale-snippet-fixture/get-user/query-parameters.adoc")
            val other = File(output, "stale-snippet-fixture/other/http-request.adoc")

            listOf(stale, other).forEach {
                Files.createDirectories(it.parentFile.toPath())
                it.writeText("old")
            }

            val context =
                contextOf(StaleSnippetFixture::class.java, StaleSnippetFixture::class.java.getMethod("getUser"))

            `when`("메서드를 실행하기 전") {
                RestDocsConfiguration.withSettings(withOutput) {
                    AutoRestDocumentationExtension().runBeforeEach(context)
                }

                then("그 메서드의 스니펫만 지운다") {
                    stale.exists() shouldBe false
                    other.exists() shouldBe true
                }
            }

            `when`("같은 메서드가 다시 실행되면 (반복·파라미터 테스트)") {
                Files.createDirectories(stale.parentFile.toPath())
                stale.writeText("first invocation")

                RestDocsConfiguration.withSettings(withOutput) {
                    AutoRestDocumentationExtension().runBeforeEach(context)
                }

                then("첫 실행 결과를 지우지 않는다") {
                    stale.exists() shouldBe true
                }
            }
        }
    })
