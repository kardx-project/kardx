package io.github.kardx.internal.asyncapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class WebCommentsTest :
    BehaviorSpec({
        given("JavaScript") {
            then("줄 주석·블록 주석과 주석만 있던 줄을 지운다") {
                val source = listOf(
                    "// 맨 위 주석",
                    "const a = 1; // 줄 끝 주석",
                    "",
                    "/* 여러 줄",
                    "   주석 */",
                    "try { f(); } catch (_) { /* 무시 */ }",
                    "const b = a/* 사이 */+1;",
                ).joinToString("\n")

                WebComments.stripJs(source) shouldBe listOf(
                    "const a = 1;",
                    "",
                    "try { f(); } catch (_) { }",
                    "const b = a +1;",
                ).joinToString("\n")
            }

            then("문자열·템플릿·정규식 안의 // 와 /* 는 그대로 둔다") {
                val source = listOf(
                    "const url = 'https://example.com/a'; const css = \"/* no */\";",
                    "const t = `ws://${'$'}{host + '//x'}/p ${'$'}{ { a: `//in` }.a }`; // tail",
                    "const slash = s.replace(/^#\\//, '').split(/([/.])/g);",
                    "const ticks = text.split(/`([^`]+)`/);",
                    "const half = (a + b) / 2 / c; // div",
                    "function f() { return /\\/\\//.test(x); }",
                    "const esc = 'it\\'s // fine';",
                ).joinToString("\n")

                WebComments.stripJs(source) shouldBe source.lines()
                    .joinToString("\n") { it.substringBefore(" // tail").substringBefore(" // div") }
            }
        }

        given("CSS") {
            then("블록 주석만 지우고 문자열은 둔다") {
                val source = listOf(
                    "/* 섹션 */",
                    "a::before { content: \"/* keep */\"; background: url(//cdn.example.com/x.png); }",
                    "b { color: red; } /* 끝 */",
                ).joinToString("\n")

                WebComments.stripCss(source) shouldBe listOf(
                    "a::before { content: \"/* keep */\"; background: url(//cdn.example.com/x.png); }",
                    "b { color: red; }",
                ).joinToString("\n")
            }
        }

        given("HTML") {
            then("<!-- --> 주석을 지운다") {
                WebComments.stripHtml("<div>\n  <!-- 설명 -->\n  <p>x</p><!-- a\nb -->\n</div>") shouldBe
                    "<div>\n  <p>x</p>\n</div>"
            }
        }
    })
