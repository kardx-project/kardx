package io.github.kardx.kotest.fixture

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.kotest.RestDocsExtension
import io.github.kardx.mockmvc.andDocument
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * 격리 모드 고정 스펙의 본문. 루트 테스트 하나와 컨테이너 안의 테스트 둘을 문서화하고, 각 테스트는 [prefix] 를 붙여 본 설정을
 * 기록한다. 격리 모드는 스펙 본문과 `beforeSpec` 을 인스턴스마다 다시 실행해 거기서 한 `configure` 가 쌓이므로, 문서에서
 * 권하는 대로 `beforeEach` 에서 설정한다 (테스트마다 더하고 끝날 때 뺀다).
 *
 * `InstancePerLeaf`·`InstancePerTest` 는 Kotest 5.x·6.x 모두 있어 여기 둔다. `InstancePerRoot` 는 6.x 에만 있어 kotest 모듈에 있다.
 */
internal fun FunSpec.isolatedTests(prefix: String) {
    val restDocs = extension(RestDocsExtension())
    val mockMvc = helloMockMvc(restDocs.provider)

    beforeEach {
        RestDocsConfiguration.configure {
            document.commonIncludeFiles =
                document.commonIncludeFiles + "each.adoc"
        }
    }

    test("루트") {
        FixtureLaunch.record("$prefix-root")
        mockMvc.perform(get("/hello/{id}", 1))
            .andDocument { }
    }

    context("묶음") {
        test("첫째") {
            FixtureLaunch.record("$prefix-first")
            mockMvc.perform(get("/hello/{id}", 2))
                .andDocument { }
        }

        test("둘째") {
            FixtureLaunch.record("$prefix-second")
            mockMvc.perform(get("/hello/{id}", 3))
                .andDocument { }
        }
    }
}

@EnabledIf(LaunchedOnly::class)
class PerLeafIsolationDocsSpec :
    FunSpec({
        @Suppress("DEPRECATION")
        isolationMode = IsolationMode.InstancePerLeaf

        isolatedTests("per-leaf")
    })

@EnabledIf(LaunchedOnly::class)
class PerTestIsolationDocsSpec :
    FunSpec({
        @Suppress("DEPRECATION")
        isolationMode = IsolationMode.InstancePerTest

        isolatedTests("per-test")
    })
