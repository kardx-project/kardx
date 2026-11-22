package io.github.kardx.mockmvc

import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.RelaxedSection
import io.github.kardx.dsl.queryMeans
import io.github.kardx.dsl.type
import io.github.kardx.internal.documentation.toSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files

class RelaxedItem(val id: Long, val extra: Int)

@RestController
class RelaxedController {
    @GetMapping("/relaxed-items")
    fun items(@RequestParam page: Int): RelaxedItem = RelaxedItem(page.toLong(), 0)
}

private class RelaxedDocs {
    val output: File = Files.createTempDirectory("relaxed").toFile()
    private val documentation = ManualRestDocumentation(output.absolutePath)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(RelaxedController())
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()

    /** `page` 와 문서화하지 않은 `debug` 쿼리 파라미터로 부른다. 응답의 `extra` 필드도 문서화하지 않는다. */
    fun call(method: String, block: DocumentationBuilder.() -> Unit) {
        documentation.beforeTest(RelaxedSectionTest::class.java, method)

        try {
            mockMvc.perform(get("/relaxed-items").param("page", "1").param("debug", "true"))
                .andDocument {
                    queryParameters("page" queryMeans "페이지")
                    responseFields("id" type NUMBER means "ID")
                    block()
                }
        } finally {
            documentation.afterTest()
        }
    }

    fun snippet(method: String, name: String): String = File(output, "relaxed-section-test/$method/$name").readText()
}

class RelaxedSectionTest :
    BehaviorSpec({
        given("relaxed(RESPONSE_FIELDS)") {
            then("응답 필드만 느슨하고 쿼리 파라미터는 엄격하다") {
                val docs = RelaxedDocs()

                shouldThrowAny { docs.call("response-only") { relaxed(RelaxedSection.RESPONSE_FIELDS) } }
                    .message shouldContain "debug"
            }
        }

        given("relaxed(QUERY_PARAMETERS)") {
            then("쿼리 파라미터만 느슨하고 응답 필드는 엄격하다") {
                val docs = RelaxedDocs()

                shouldThrowAny { docs.call("query-only") { relaxed(RelaxedSection.QUERY_PARAMETERS) } }
                    .message shouldContain "extra"
            }
        }

        given("두 항목을 relaxed 로 하면") {
            then("통과하고 문서화한 것만 표와 OpenAPI 에 나온다") {
                val docs = RelaxedDocs()

                docs.call("both") {
                    relaxed(RelaxedSection.QUERY_PARAMETERS)
                    relaxed(RelaxedSection.RESPONSE_FIELDS)
                }

                docs.snippet("both", "query-parameters.adoc") shouldContain "`+page+`"
                docs.snippet("both", "response-fields.adoc") shouldContain "`+id+`"

                val resource = docs.snippet("both", "resource.json")

                resource shouldContain "\"page\""
                resource shouldNotContain "debug"
            }
        }

        given("relaxed = true") {
            then("모든 항목이 relaxed 이고 false 로 되돌린다") {
                RelaxedDocs().call("all") { relaxed = true }

                val builder = DocumentationBuilder()

                builder.relaxed(RelaxedSection.EVENTS)
                builder.relaxed shouldBe false

                builder.relaxed = true
                builder.toSpec().relaxedSections shouldBe RelaxedSection.entries.toSet()

                builder.relaxed = false
                builder.toSpec().relaxedSections shouldBe emptySet()
            }
        }

        given("OpenAPI 리소스") {
            then("relaxed 인 본문 필드만 epages 에 넘기지 않는다") {
                val spec = DocumentationBuilder().apply {
                    requestFields("name" type NUMBER means "이름")
                    responseFields("id" type NUMBER means "ID")
                    relaxed(RelaxedSection.RESPONSE_FIELDS)
                }.toSpec()
                val resource = spec.openApiResource(securitySchemes = false)

                resource.requestFields.map { it.path } shouldBe listOf("name")
                resource.responseFields shouldBe emptyList()
            }
        }
    })
