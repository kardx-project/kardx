package io.github.kardx.internal.i18n

import io.github.kardx.config.DocsLanguage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldContain

class PluginLanguageWarningTest :
    BehaviorSpec({
        given("Gradle 플러그인이 알린 언어") {
            then("테스트 언어와 다르면 build 파일에 넣을 설정을 알려 준다") {
                val message = PluginLanguageWarning.message(DocsLanguage.KO, "en")

                message.orEmpty() shouldContain "documentation language is 'ko'"
                message.orEmpty() shouldContain "the Gradle plugin uses 'en'"
                message.orEmpty() shouldContain "kardx { language = \"ko\" }"
            }

            then("같으면(대소문자 무시) 경고하지 않는다") {
                PluginLanguageWarning.message(DocsLanguage.KO, "KO").shouldBeNull()
                PluginLanguageWarning.message(DocsLanguage.EN, " en ").shouldBeNull()
            }

            then("플러그인 없이(속성 없음·빈 값) 돌면 경고하지 않는다") {
                PluginLanguageWarning.message(DocsLanguage.KO, null).shouldBeNull()
                PluginLanguageWarning.message(DocsLanguage.KO, "").shouldBeNull()
            }
        }

        given("여러 번 확인할 때") {
            `when`("언어가 계속 다르면") {
                val logged = mutableListOf<String>()
                val warning = PluginLanguageWarning(logged::add)

                repeat(3) { warning.check(DocsLanguage.KO, "en") }
                warning.check(DocsLanguage.EN, "ko")

                then("한 번만 경고한다") {
                    logged shouldHaveSize 1
                }
            }

            `when`("언어가 같으면") {
                val logged = mutableListOf<String>()
                val warning = PluginLanguageWarning(logged::add)

                warning.check(DocsLanguage.EN, "en")
                warning.check(DocsLanguage.KO, null)

                then("경고하지 않는다") {
                    logged.shouldBeEmpty()
                }
            }
        }
    })
