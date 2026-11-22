package io.github.kardx.kotest

import io.github.kardx.kotest.fixture.DataDocsSpec
import io.github.kardx.kotest.fixture.OverriddenExtensionsDocsSpec
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain

/** Kotest 5.x 에만 있는 동작의 테스트. 공유 테스트는 kotest-common 의 [RestDocsExtensionTest] 에 있다. */
class KotestFiveOnlyTest :
    BehaviorSpec({
        given("스펙 클래스의 override fun extensions() 로 등록한 확장") {
            val run = runFixtures(OverriddenExtensionsDocsSpec::class)

            then("리프 테스트를 문서화하고 스펙이 끝나면 문서를 쓴다") {
                run.document("overridden-extensions-docs-spec")
                    .readText() shouldContain section("함수로-등록", "함수로 등록")
            }
        }

        given("데이터 테스트(withData) 스펙") {
            val run = runFixtures(DataDocsSpec::class)
            val document = run.document("data-docs-spec")
                .readText()

            then("행(TestType.Dynamic)마다 섹션과 스니펫 디렉터리를 만든다") {
                document shouldContain section("아이디별-조회-아이디-1", "아이디별 조회 - 아이디 1")
                document shouldContain section("아이디별-조회-아이디-2", "아이디별 조회 - 아이디 2")
                run.snippet("data-docs-spec", "아이디별-조회-아이디-2/http-request.adoc").isFile.shouldBeTrue()
            }
        }
    })
