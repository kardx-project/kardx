package io.github.kardx.mockmvc

import io.github.kardx.dsl.DocumentationMismatchException
import io.github.kardx.dsl.pathMeans
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.assertj.core.api.Assertions.assertThat
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.io.File
import java.nio.file.Files

/** 진입점마다 같은 문서화가 되는지 보려고 같은 요청을 다른 방식으로 보낸다. */
private class EntryPointDocs {
    val output: File = Files.createTempDirectory("entry-points").toFile()
    val documentation = ManualRestDocumentation(output.absolutePath)
    val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(SampleController())
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()

    fun <T> run(method: String, block: () -> T): T {
        documentation.beforeTest(EntryPointsTest::class.java, method)

        try {
            return block()
        } finally {
            documentation.afterTest()
        }
    }

    fun snippet(method: String, name: String): String = File(output, "entry-points-test/$method/$name").readText()
}

class EntryPointsTest :
    BehaviorSpec({
        given("MockMvc Kotlin DSL (mockMvc.get { })") {
            then("ResultActionsDsl 에서 같은 andDocument 블록으로 문서화한다") {
                val docs = EntryPointDocs()

                docs.run("kotlin-dsl") {
                    docs.mockMvc.get("/ping/{name}", "kim")
                        .andExpect { status { isOk() } }
                        .andDocument {
                            description = "핑"
                            pathParameters("name" pathMeans "이름")
                        }
                }

                docs.snippet("kotlin-dsl", "path-parameters.adoc") shouldContain "`+name+`"
                docs.snippet("kotlin-dsl", "api-summary.adoc") shouldContain "핑"
            }

            then("검증 실패는 같은 진단 예외다") {
                val docs = EntryPointDocs()

                shouldThrow<DocumentationMismatchException> {
                    docs.run("kotlin-dsl-mismatch") {
                        docs.mockMvc.get("/ping/{name}", "kim")
                            .andDocument { pathParameters("id" pathMeans "ID") }
                    }
                }.message shouldContain "\"name\" pathMeans \"...\""
            }
        }

        given("MockMvcTester") {
            then("MvcTestResult 에서 같은 andDocument 블록으로 문서화하고 AssertJ 검증을 이어 쓴다") {
                val docs = EntryPointDocs()
                val tester = MockMvcTester.create(docs.mockMvc)

                val result = docs.run("tester") {
                    tester.get()
                        .uri("/ping/{name}", "kim")
                        .exchange()
                        .andDocument { pathParameters("name" pathMeans "이름") }
                }

                assertThat(result).hasStatusOk()
                docs.snippet("tester", "path-parameters.adoc") shouldContain "`+name+`"
                File(docs.output, "entry-points-test/tester/http-response.adoc").isFile shouldBe true
            }
        }
    })
