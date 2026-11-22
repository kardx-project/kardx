package io.github.kardx.kotest

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.kotest.fixture.FixtureLaunch
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.Spec
import io.kotest.core.spec.SpecRef
import io.kotest.core.test.TestCase
import io.kotest.engine.TestEngineLauncher
import io.kotest.engine.listener.AbstractTestEngineListener
import io.kotest.engine.test.TestResult
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * [specs] 를 새 출력 디렉터리에 Kotest 6.x 엔진(`TestEngineLauncher`)으로 실행한다. 끝나면 `configure` 상태를 지운다.
 * 고정 스펙은 [FixtureLaunch.active] 일 때만 켜지므로 Gradle 이 직접 실행하지 않는다.
 */
suspend fun runFixtures(vararg specs: KClass<out Spec>, config: AbstractProjectConfig? = null): FixtureRun {
    val output = Files.createTempDirectory("kotest-docs").toFile()
    val snippets = File(output, "snippets")
    val asciidoc = File(output, "asciidoc")
    val results = ConcurrentHashMap<String, FixtureResult>()
    val listener = object : AbstractTestEngineListener() {
        override suspend fun testFinished(testCase: TestCase, result: TestResult) {
            results[testCase.name.name] = FixtureResult(result.isSuccess, result.isErrorOrFailure)
        }
    }

    RestDocsConfiguration.configure {
        this.output.snippetsDirectory = snippets.path
        this.output.asciidocDirectory = asciidoc.path
    }

    FixtureLaunch.active = true

    try {
        TestEngineLauncher()
            .withListener(listener)
            .withProjectConfig(config)
            .withSpecRefs(specs.map { SpecRef.Reference(it, it.java.name) })
            .execute()
    } finally {
        FixtureLaunch.active = false
        RestDocsConfiguration.reset()
    }

    return FixtureRun(snippets, asciidoc, results)
}
