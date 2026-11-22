package io.github.kardx.kotest.fixture

import io.github.kardx.kotest.RestDocsExtension
import io.github.kardx.mockmvc.andDocument
import io.kotest.core.annotation.Condition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.reflect.KClass

// Kotest 6.x 에만 있는 것: 고정 스펙을 켜는 조건(6.x Condition)과 IsolationMode.InstancePerRoot 고정 스펙.

/** [FixtureLaunch.active] 일 때만 스펙을 실행한다. */
class LaunchedOnly : Condition {
    override fun evaluate(kclass: KClass<out Spec>): Boolean = FixtureLaunch.active
}

/** [extensions] 를 등록한 프로젝트 설정. 6.x 는 `extensions` 속성을 덮어쓴다. */
fun projectConfig(vararg extensions: Extension): AbstractProjectConfig = object : AbstractProjectConfig() {
    override val extensions: List<Extension> = extensions.toList()
}

/** 공유 격리 모드 테스트가 돌리는 스펙 (이름 접두사 → 스펙). 6.x 는 InstancePerRoot 까지 셋이다. */
val isolationFixtures: Map<String, KClass<out Spec>> = linkedMapOf(
    "per-root" to PerRootIsolationDocsSpec::class,
    "per-leaf" to PerLeafIsolationDocsSpec::class,
    "per-test" to PerTestIsolationDocsSpec::class,
)

@EnabledIf(LaunchedOnly::class)
class RootIsolationDocsSpec :
    FunSpec({
        isolationMode = IsolationMode.InstancePerRoot

        val restDocs = extension(RestDocsExtension())
        val mockMvc = helloMockMvc(restDocs.provider)

        test("첫 루트") {
            mockMvc.perform(get("/hello/{id}", 1))
                .andDocument { }
        }

        test("둘째 루트") {
            mockMvc.perform(get("/hello/{id}", 2))
                .andDocument { }
        }

        context("셋째 루트") {
            test("안쪽") {
                mockMvc.perform(get("/hello/{id}", 3))
                    .andDocument { }
            }
        }
    })

@EnabledIf(LaunchedOnly::class)
class PerRootIsolationDocsSpec :
    FunSpec({
        isolationMode = IsolationMode.InstancePerRoot

        isolatedTests("per-root")
    })

/**
 * 프로젝트 설정의 확장과 함께, 스펙이 `override val extensions` 속성(6.x)으로 자기 확장을 등록한다.
 * DSL `extension(...)` 과 같이 스펙의 확장이 문서화해야 한다.
 */
@EnabledIf(LaunchedOnly::class)
class PropertyRegisteredDocsSpec : FunSpec() {
    override val extensions: List<Extension> = listOf(
        RestDocsExtension(sectionTitle = { test -> "속성 제목: ${test.names.joinToString(" / ")}" }),
    )

    init {
        val mockMvc = helloMockMvc(RestDocsExtension.currentTestProvider)

        test("속성 등록") {
            mockMvc.perform(get("/hello/{id}", 1))
                .andDocument { }
        }
    }
}
