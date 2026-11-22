package io.github.kardx.internal.openapi

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.webtestclient.MemberController
import io.github.kardx.webtestclient.andDocument
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.io.File
import java.nio.file.Files

/** 한 테스트 메서드를 문서화하고 스니펫을 읽는다. */
private class SecurityDocs {
    private val directory: File = Files.createTempDirectory("declared-security").toFile()
    private val provider = ManualRestDocumentation(directory.absolutePath)

    /** MockMvc 로 `/members/1` 을 문서화한다. [authorization] 이 있으면 그 헤더를 보낸다. */
    fun mockMvc(method: String, authorization: String? = null, block: DocumentationBuilder.() -> Unit) {
        val mockMvc = MockMvcBuilders.standaloneSetup(MemberController())
            .apply<StandaloneMockMvcBuilder>(documentationConfiguration(provider))
            .build()
        val request = get("/members/{id}", 1L).apply { authorization?.let { header("Authorization", it) } }

        run(method) { mockMvc.perform(request).andDocument(block) }
    }

    /** WebTestClient 로 `/members/1` 을 문서화한다 (헤더 없음). */
    fun webTestClient(method: String, block: DocumentationBuilder.() -> Unit) {
        val client = WebTestClient.bindToController(MemberController())
            .configureClient()
            .filter(WebTestClientRestDocumentation.documentationConfiguration(provider))
            .build()

        run(method) {
            client.get()
                .uri("/members/{id}", 1L)
                .exchange()
                .expectBody()
                .andDocument(block)
        }
    }

    fun snippet(method: String, name: String): String =
        File(directory, "declared-security-test/$method/$name").readText()

    private fun run(method: String, block: () -> Unit) {
        provider.beforeTest(DeclaredSecurityTest::class.java, method)

        try {
            block()
        } finally {
            provider.afterTest()
        }
    }
}

class DeclaredSecurityTest :
    BehaviorSpec({
        given("헤더 없이 인증한 요청 (with(jwt()) 등)") {
            val docs = SecurityDocs()

            docs.mockMvc("undeclared") {}
            docs.mockMvc("bearer") { openApi { bearerAuth() } }
            docs.mockMvc("basic") { openApi { basicAuth() } }
            docs.webTestClient("web-bearer") { openApi { bearerAuth() } }

            then("선언하지 않으면 보안 요구사항이 없다") {
                docs.snippet("undeclared", "resource.json") shouldContain "\"securityRequirements\" : null"
            }

            then("bearerAuth() 는 JWT Bearer 보안 요구사항을 만든다 (MockMvc·WebTestClient)") {
                docs.snippet("bearer", "resource.json") shouldContain "\"type\" : \"JWT_BEARER\""
                docs.snippet("web-bearer", "resource.json") shouldContain "\"type\" : \"JWT_BEARER\""
            }

            then("basicAuth() 는 Basic 보안 요구사항을 만든다") {
                docs.snippet("basic", "resource.json") shouldContain "\"type\" : \"BASIC\""
            }

            then("REST Docs 스니펫에는 Authorization 이 생기지 않는다") {
                listOf("bearer", "basic", "web-bearer").forEach { method ->
                    docs.snippet(method, "http-request.adoc") shouldNotContain "Authorization"
                    docs.snippet(method, "curl-request.adoc") shouldNotContain "Authorization"
                }
            }
        }

        given("다른 방식의 헤더를 보낸 요청") {
            then("OpenAPI 에서는 선언한 방식을 따르고 스니펫에는 실제 헤더가 남는다") {
                val docs = SecurityDocs()

                docs.mockMvc("override", authorization = "Basic dXNlcjpwYXNz") { openApi { bearerAuth() } }

                docs.snippet("override", "resource.json") shouldContain "\"type\" : \"JWT_BEARER\""
                docs.snippet("override", "http-request.adoc") shouldContain "Authorization: Basic dXNlcjpwYXNz"
            }
        }

        given("openApi.securitySchemes = false") {
            then("선언한 operation 에는 보안 요구사항이 붙는다") {
                val docs = SecurityDocs()

                RestDocsConfiguration.withSettings({ openApi.securitySchemes = false }) {
                    docs.mockMvc("disabled") { openApi { bearerAuth() } }
                    docs.mockMvc("disabled-real", authorization = "Bearer token") {}
                }

                docs.snippet("disabled", "resource.json") shouldContain "\"type\" : \"JWT_BEARER\""
                docs.snippet("disabled-real", "resource.json") shouldContain "\"securityRequirements\" : null"
            }
        }
    })
