package io.github.kardx.kotest

import io.github.kardx.kotest.fixture.PropertyRegisteredDocsSpec
import io.github.kardx.kotest.fixture.RootIsolationDocsSpec
import io.github.kardx.kotest.fixture.projectConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Kotest 6.x 에만 있는 기능의 테스트. 공유 테스트는 kotest-common 의 [RestDocsExtensionTest] 에 있다. */
class KotestSixOnlyTest :
    BehaviorSpec({
        given("IsolationMode.InstancePerRoot 스펙") {
            val run = runFixtures(RootIsolationDocsSpec::class)
            val document = run.document("root-isolation-docs-spec")
                .readText()

            then("스펙 인스턴스가 여럿이어도 모든 루트의 테스트가 한 문서에 들어간다") {
                document shouldContain section("첫-루트", "첫 루트")
                document shouldContain section("둘째-루트", "둘째 루트")
                document shouldContain section("셋째-루트-안쪽", "셋째 루트 - 안쪽")
            }
        }

        given("프로젝트 설정의 확장과 `override val extensions` 로 확장을 등록한 스펙") {
            val run = runFixtures(PropertyRegisteredDocsSpec::class, config = projectConfig(RestDocsExtension()))
            val document = run.document("property-registered-docs-spec")
                .readText()

            then("스펙 확장의 섹션 제목을 쓰고 한 번만 문서화한다") {
                document shouldContain "속성 제목: 속성 등록"
                document.split("[[속성-등록]]").size shouldBe 2
            }
        }
    })
