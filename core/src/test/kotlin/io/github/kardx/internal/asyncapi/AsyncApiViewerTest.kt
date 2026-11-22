package io.github.kardx.internal.asyncapi

import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.i18n.ViewerText
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private val DOCUMENT = mapOf("asyncapi" to "3.0.0")

private val HANGUL = Regex("[\\uAC00-\\uD7A3]")

/** 스크립트·HTML 이 읽는 문구 이름 (`T.NAME`, `fmt('NAME'`, `data-text="NAME"`). */
private val REFERENCE = Regex("""\bT\.([A-Z_]+)|fmt\('([A-Z_]+)'|data-text="([A-Z_]+)"""")

private fun resource(name: String): String = requireNotNull(AsyncApiViewer::class.java.getResource(name)) { name }
    .readText()

class AsyncApiViewerTest :
    BehaviorSpec({
        given("AsyncAPI 뷰어 페이지") {
            then("기본은 영어 문구와 lang=en 이고 자리 표시자가 남지 않는다") {
                val html = AsyncApiViewer.html("Chat", DOCUMENT)

                html shouldContain "<html lang=\"en\">"
                html shouldContain "\"TAB_DOC\":\"Docs\""
                html shouldContain "\"NOT_CONNECTED\":\"Not connected\""
                html shouldNotContain "\"TAB_DOC\":\"문서\""
                html shouldNotContain "__VIEWER_TEXTS__"
                html shouldNotContain "__LANG__"
            }

            then("language 가 ko 면 한국어 문구와 lang=ko 를 넣는다 (브라우저 언어는 보지 않는다)") {
                val html = RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    AsyncApiViewer.html("Chat", DOCUMENT)
                }

                html shouldContain "<html lang=\"ko\">"
                html shouldContain "\"TAB_DOC\":\"문서\""
                html shouldContain "\"LOG_EMPTY\":\"구독하거나 메시지를 보내면 여기에 나타납니다.\""
                html shouldNotContain "navigator.language"
            }

            then("language 를 정하지 않으면 리소스의 한국어 주석이 페이지에 남지 않는다") {
                val html = AsyncApiViewer.html("Chat", DOCUMENT)

                HANGUL.containsMatchIn(html) shouldBe false
                html shouldNotContain "/* "
                html shouldContain "url.includes('?')"
                html shouldContain ".replace(/^#\\//, '')"
            }

            then("인증 문구는 두 언어 모두 있다") {
                val english = ViewerText.all(DocsLanguage.EN)
                val korean = ViewerText.all(DocsLanguage.KO)

                english.getValue("AUTHENTICATION") shouldBe "Authentication"
                korean.getValue("AUTHENTICATION") shouldBe "인증"
                english.filterKeys { it.startsWith("AUTH_") }
                    .values
                    .none { HANGUL.containsMatchIn(it) } shouldBe true
            }

            then("STOMP 프레임 헤더는 이스케이프를 풀어 보여 준다 (거절된 CONNECT 의 ERROR 는 stompjs 가 풀지 않는다)") {
                val html = AsyncApiViewer.html("Chat", DOCUMENT)

                html shouldContain "value.replace(/\\\\([cnr\\\\])/g, (_, ch) => STOMP_ESCAPES[ch])"
                html shouldContain "stompHeader(frame, 'message')"
                html shouldContain "stompHeader(message, 'destination')"
                html shouldNotContain "frame.headers.message"
                html shouldNotContain "message.headers.destination"
            }

            then("스크립트와 HTML 이 읽는 문구는 모두 카탈로그에 있다") {
                val names = ViewerText.entries.map { it.name }
                    .toSet()
                val referenced = (resource("viewer.js") + resource("viewer.html")).let { source ->
                    REFERENCE.findAll(source)
                        .map { match -> match.groupValues.drop(1).first { it.isNotEmpty() } }
                        .toSet()
                }

                (referenced - names).shouldBeEmpty()
            }
        }
    })
