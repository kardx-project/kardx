package io.github.kardx.resttestclient

import io.github.kardx.dsl.DocumentationMismatchException
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.pathMeans
import io.github.kardx.dsl.type
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files

class RtcUser(val id: Long, val name: String)

@RestController
class RtcController {
    @GetMapping("/rtc/users/{id}")
    fun find(@PathVariable id: Long): RtcUser = RtcUser(id, "홍길동")
}

private class RtcDocs {
    val output: File = Files.createTempDirectory("resttestclient").toFile()
    val documentation = ManualRestDocumentation(output.absolutePath)

    fun run(method: String, block: () -> Unit) {
        documentation.beforeTest(RestTestClientDocumentationTest::class.java, method)

        try {
            block()
        } finally {
            documentation.afterTest()
        }
    }

    fun snippet(method: String, name: String): String =
        File(output, "rest-test-client-documentation-test/$method/$name").readText()
}

class RestTestClientDocumentationTest :
    BehaviorSpec({
        given("restDocs 를 단 RestTestClient (bindToController)") {
            val docs = RtcDocs()
            val client = RestTestClient.bindToController(RtcController())
                .restDocs(docs.documentation)
                .build()

            docs.run("find") {
                client.get()
                    .uri("/rtc/users/{id}", 1)
                    .header("Cookie", "SESSION=abc")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .andDocument {
                        description = "사용자 조회"
                        pathParameters("id" pathMeans "사용자 ID")
                        responseFields("id" type NUMBER means "ID", "name" type STRING means "이름")
                    }
            }

            then("MockMvc 와 같은 스니펫을 만들고, 내부 요청 ID 헤더는 문서에 없다") {
                docs.snippet("find", "path-parameters.adoc") shouldContain "`+id+`"
                docs.snippet("find", "response-fields.adoc") shouldContain "`+name+`"
                docs.snippet("find", "api-summary.adoc") shouldContain "사용자 조회"
                docs.snippet("find", "http-response.adoc") shouldContain "홍길동"

                val request = docs.snippet("find", "http-request.adoc")

                request shouldContain "GET /rtc/users/1"
                request shouldNotContain RestTestClient.RESTTESTCLIENT_REQUEST_ID
                docs.snippet("find", "curl-request.adoc") shouldContain "--cookie 'SESSION=abc'"
            }
        }

        given("bindTo(mockMvc) 와 타입 본문") {
            then("expectBody(T) 뒤에서도 문서화하고 값을 그대로 돌려준다") {
                val docs = RtcDocs()
                val client = RestTestClient.bindTo(MockMvcBuilders.standaloneSetup(RtcController()).build())
                    .restDocs(docs.documentation)
                    .build()
                var user: Map<*, *>? = null

                docs.run("typed") {
                    user = client.get()
                        .uri("/rtc/users/{id}", 2)
                        .exchange()
                        .expectBody(Map::class.java)
                        .andDocument { pathParameters("id" pathMeans "사용자 ID") }
                        .returnResult()
                        .responseBody
                }

                user?.get("id") shouldBe 2
                docs.snippet("typed", "path-parameters.adoc") shouldContain "`+id+`"
            }
        }

        given("문서와 응답이 다르면") {
            then("DocumentationMismatchException") {
                val docs = RtcDocs()
                val client = RestTestClient.bindToController(RtcController())
                    .restDocs(docs.documentation)
                    .build()

                shouldThrow<DocumentationMismatchException> {
                    docs.run("mismatch") {
                        client.get()
                            .uri("/rtc/users/{id}", 1)
                            .exchange()
                            .andDocument { responseFields("id" type NUMBER means "ID") }
                    }
                }.message shouldContain "name"
            }
        }

        given("restDocs 없이 만든 클라이언트") {
            then("영어 메시지로 실패한다") {
                val client = RestTestClient.bindToController(RtcController())
                    .build()

                shouldThrow<IllegalStateException> {
                    client.get()
                        .uri("/rtc/users/{id}", 1)
                        .exchange()
                        .andDocument()
                }.message shouldContain "Build it with restDocs(provider)"
            }
        }
    })
