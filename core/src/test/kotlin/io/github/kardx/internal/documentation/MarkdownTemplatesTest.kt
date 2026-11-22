package io.github.kardx.internal.documentation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.restdocs.templates.StandardTemplateResourceResolver
import org.springframework.restdocs.templates.TemplateFormats
import org.springframework.restdocs.templates.mustache.MustacheTemplateEngine

/**
 * 라이브러리의 Markdown 템플릿을 REST Docs 의 Markdown 템플릿 엔진으로 그린다. Markdown 형식은 AsciiDoc 의
 * `tableCellContent` 람다를 주지 않으므로, 템플릿이 그것을 쓰면 표 칸이 비어 버린다.
 */
private val engine = MustacheTemplateEngine(StandardTemplateResourceResolver(TemplateFormats.markdown()))

private fun render(template: String, model: Map<String, Any>): String = engine.compileTemplate(template)
    .render(model)

class MarkdownTemplatesTest :
    BehaviorSpec({
        given("Markdown message 템플릿") {
            val model = mapOf(
                "direction" to "SEND",
                "destination" to "/app/chat",
                "protocol" to "STOMP",
                "hasHeaders" to true,
                "headers" to listOf(
                    mapOf("name" to "Authorization", "description" to "토큰", "optional" to false, "example" to "****"),
                ),
            )

            then("헤더 표의 이름·설명·예시 칸이 채워진다") {
                val markdown = render("message", model)

                markdown shouldContain "`Authorization` | true | 토큰 | `****`"
                markdown shouldNotContain "tableCellContent"
            }
        }

        given("Markdown sse-events 템플릿") {
            val model = mapOf(
                "events" to listOf(mapOf("name" to "tick", "description" to "주기 이벤트", "optional" to true)),
            )

            then("이벤트 이름·설명 칸이 채워진다") {
                render("sse-events", model) shouldContain "`tick` | false | 주기 이벤트"
            }
        }
    })
