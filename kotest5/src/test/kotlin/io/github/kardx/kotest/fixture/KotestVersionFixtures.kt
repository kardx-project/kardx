package io.github.kardx.kotest.fixture

import io.github.kardx.kotest.RestDocsExtension
import io.github.kardx.mockmvc.andDocument
import io.kotest.core.annotation.EnabledCondition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.reflect.KClass

// Kotest 5.x 에만 있는 것: 고정 스펙을 켜는 조건(5.x EnabledCondition)과 데이터 테스트(TestType.Dynamic) 고정 스펙.

/** [FixtureLaunch.active] 일 때만 스펙을 실행한다. */
class LaunchedOnly : EnabledCondition {
    override fun enabled(kclass: KClass<out Spec>): Boolean = FixtureLaunch.active
}

/** [extensions] 를 등록한 프로젝트 설정. 5.x 는 `extensions()` 함수를 덮어쓴다. */
fun projectConfig(vararg extensions: Extension): AbstractProjectConfig = object : AbstractProjectConfig() {
    override fun extensions(): List<Extension> = extensions.toList()
}

/** 공유 격리 모드 테스트가 돌리는 스펙 (이름 접두사 → 스펙). 5.x 에는 InstancePerRoot 가 없다. */
val isolationFixtures: Map<String, KClass<out Spec>> = linkedMapOf(
    "per-leaf" to PerLeafIsolationDocsSpec::class,
    "per-test" to PerTestIsolationDocsSpec::class,
)

/** `withData` 의 행(5.x `TestType.Dynamic`)을 리프 테스트로 문서화한다. */
@EnabledIf(LaunchedOnly::class)
class DataDocsSpec :
    FunSpec({
        val restDocs = extension(RestDocsExtension())
        val mockMvc = helloMockMvc(restDocs.provider)

        context("아이디별 조회") {
            withData(nameFn = { "아이디 $it" }, 1, 2) { id ->
                mockMvc.perform(get("/hello/{id}", id))
                    .andDocument { }
            }
        }
    })

/**
 * 5.x 의 스펙 클래스 등록(`override fun extensions()`). 5.x 는 `extensions()` 를 부를 때마다 목록을 다시 받으므로 확장 인스턴스를
 * 프로퍼티에 두고 같은 것을 돌려준다 (매번 새로 만들면 `afterSpec` 이 문서화하지 않은 인스턴스로 와서 문서를 쓰지 않는다).
 */
@EnabledIf(LaunchedOnly::class)
class OverriddenExtensionsDocsSpec :
    FunSpec({
        val mockMvc = helloMockMvc(RestDocsExtension.currentTestProvider)

        test("함수로 등록") {
            mockMvc.perform(get("/hello/{id}", 1))
                .andDocument { }
        }
    }) {
    private val restDocs = RestDocsExtension()

    override fun extensions(): List<Extension> = listOf(restDocs)
}
