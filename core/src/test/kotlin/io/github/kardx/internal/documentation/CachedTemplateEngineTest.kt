package io.github.kardx.internal.documentation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.springframework.restdocs.mustache.Mustache
import org.springframework.restdocs.templates.StandardTemplateResourceResolver
import org.springframework.restdocs.templates.TemplateFormats
import org.springframework.restdocs.templates.mustache.AsciidoctorTableCellContentLambda
import org.springframework.restdocs.templates.mustache.MustacheTemplateEngine
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/** REST Docs 가 기본으로 만드는 엔진 (RestDocumentationConfigurer 와 같은 규칙). */
private val restDocsDefault = MustacheTemplateEngine(
    StandardTemplateResourceResolver(TemplateFormats.asciidoctor()),
    StandardCharsets.UTF_8,
    Mustache.compiler().escapeHTML(false),
    mapOf("tableCellContent" to AsciidoctorTableCellContentLambda()),
)

private val model: Map<String, Any> = mapOf(
    "fields" to listOf(
        mapOf("path" to "a|b", "type" to "String", "description" to "pipe | inside", "optional" to false),
    ),
)

private const val TEMPLATE = "request-fields"

class CachedTemplateEngineTest :
    BehaviorSpec({
        given("스니펫 템플릿") {
            val expected = restDocsDefault.compileTemplate(TEMPLATE)
                .render(model)

            then("이름마다 한 번만 컴파일해 같은 템플릿을 돌려준다") {
                CachedTemplateEngine.compileTemplate(TEMPLATE) shouldBeSameInstanceAs
                    CachedTemplateEngine.compileTemplate(TEMPLATE)
            }

            then("REST Docs 기본 엔진과 같은 결과를 만든다 (표 칸의 | 이스케이프 포함)") {
                val actual = CachedTemplateEngine.compileTemplate(TEMPLATE)
                    .render(model)

                actual shouldBe expected
                actual shouldContain "a\\|b"
            }

            then("여러 스레드가 같은 템플릿으로 렌더링해도 결과가 같다") {
                val mismatches = AtomicInteger()
                val threads = (1..THREADS).map {
                    Thread {
                        repeat(RENDERS) {
                            val rendered = CachedTemplateEngine.compileTemplate(TEMPLATE)
                                .render(model)

                            if (rendered != expected) mismatches.incrementAndGet()
                        }
                    }.apply { start() }
                }

                threads.forEach { it.join() }

                mismatches.get() shouldBe 0
            }
        }
    }) {
    private companion object {
        const val THREADS = 8
        const val RENDERS = 200
    }
}
