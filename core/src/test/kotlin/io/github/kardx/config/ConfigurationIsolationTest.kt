package io.github.kardx.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/** 프로젝트 리스너(`io.kotest.provided.ProjectConfig`)가 매 테스트 뒤 전역 설정을 되돌리는지. */
class ConfigurationIsolationTest :
    BehaviorSpec({
        given("정리하지 않고 끝난 테스트") {
            then("전역 설정을 바꾸고 그대로 끝난다") {
                RestDocsConfiguration.configure { extraction.maxDepth = 99 }

                RestDocsConfiguration.current.extraction.maxDepth shouldBe 99
            }

            then("다음 테스트는 기본값을 본다") {
                RestDocsConfiguration.current.extraction.maxDepth shouldBe 10
            }
        }
    })
