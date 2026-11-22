package io.github.kardx.internal.i18n

import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.asyncapi.AsyncApiMessageSchemas
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DocsTextTest :
    BehaviorSpec({
        given("문구 카탈로그") {
            then("모든 문구가 두 언어로 있다") {
                DocsLanguage.entries.forEach { language ->
                    DocsText.entries.forEach { text -> text.text(language = language).isNotBlank() shouldBe true }
                    ViewerText.entries.forEach { text -> text.text(language).isNotBlank() shouldBe true }
                }
            }

            then("기본은 지금 설정의 언어이고, 인자는 한 번에 넣는다") {
                DocsText.SUBTYPE_VALUE.text("car") shouldBe "Value when the type is car"
                DocsText.CONNECT_HEADERS.text("/ws/{1}", "A") shouldBe "Headers sent when connecting (/ws/{1}): A"

                RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    DocsText.SUBTYPE_VALUE.text("car") shouldBe "car 타입일 때의 값"
                }
            }

            then("LocalizedText 는 만든 때가 아니라 resolve 할 때의 언어를 쓴다") {
                val text = LocalizedText(DocsText.TYPE_DISCRIMINATOR)

                text.resolve() shouldBe "Type discriminator"
                RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    text.resolve() shouldBe "타입 구분자"
                }
            }
        }

        given("항상 오지는 않는 SSE 이벤트") {
            then("메시지 설명 끝의 안내는 language 를 따른다") {
                val english = AsyncApiMessageSchemas.message("tick", null, "틱", emptyList(), emptyList(), null, true)
                val korean = RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    AsyncApiMessageSchemas.message("tick", null, "틱", emptyList(), emptyList(), null, true)
                }

                english["description"] shouldBe "틱\n\nNot always sent on the stream."
                korean["description"] shouldBe "틱\n\n스트림에 항상 오지는 않는다."
            }
        }
    })
