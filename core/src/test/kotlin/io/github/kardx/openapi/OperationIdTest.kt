package io.github.kardx.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.internal.openapi.DocumentedHandler
import io.github.kardx.internal.openapi.OpenApiOperationExtras
import io.github.kardx.internal.openapi.OperationIdNames
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.webtestclient.andDocument
import io.github.kardx.webtestclient.recordHandlers
import io.github.kardx.webtestclient.restDocs
import io.github.kardx.webtestclient.restDocsWebTestClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.aop.framework.ProxyFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerMethod
import org.springframework.web.reactive.config.EnableWebFlux
import java.io.File
import java.nio.file.Files

@RestController
open class AccountApiController {
    @GetMapping("/accounts/{id}")
    open fun find(@PathVariable id: Long): Map<String, Long> = mapOf("id" to id)

    open suspend fun findLater(id: Long): Map<String, Long> = mapOf("id" to id)

    internal fun internalLookup(): Map<String, Long> = mapOf("id" to 1)
}

@Configuration
@EnableWebFlux
open class AccountWebFluxConfig {
    @Bean
    open fun accountApiController(): AccountApiController = AccountApiController()
}

/** 한 테스트의 스니펫 출력과 남은 `openapi-extras.json` 의 operationId 후보. */
private class Output {
    val directory: File = Files.createTempDirectory("operation-id").toFile()
    val provider = ManualRestDocumentation(directory.absolutePath)

    fun run(method: String, block: () -> Unit) {
        provider.beforeTest(OperationIdTest::class.java, method)

        try {
            block()
        } finally {
            provider.afterTest()
        }
    }

    fun candidate(method: String): Map<*, *>? {
        val file = File(directory, "operation-id-test/$method/${OpenApiOperationExtras.FILE_NAME}")

        return if (file.isFile) ObjectMapper().readValue(file, Map::class.java)["operationId"] as? Map<*, *> else null
    }
}

private fun mockMvc(output: Output): MockMvc = MockMvcBuilders.standaloneSetup(AccountApiController())
    .apply<StandaloneMockMvcBuilder>(documentationConfiguration(output.provider))
    .build()

/** MockMvc 로 `GET /accounts/1` 을 문서화하고 남은 후보. */
private fun documentFind(
    settings: RestDocsSettings.() -> Unit = {},
    block: DocumentationBuilder.() -> Unit = {},
): Map<*, *>? {
    val output = Output()

    RestDocsConfiguration.withSettings(settings) {
        output.run("find") {
            mockMvc(output).perform(get("/accounts/{id}", 1))
                .andDocument(block)
        }
    }

    return output.candidate("find")
}

private fun handlerOf(bean: Any, methodName: String): DocumentedHandler? {
    val method = AccountApiController::class.java.declaredMethods.first { it.name.startsWith(methodName) }

    return DocumentedHandler.of(HandlerMethod(bean, method))
}

class OperationIdTest :
    BehaviorSpec({
        given("기본 규칙(핸들러 메서드 이름)") {
            then("MockMvc 가 고른 컨트롤러 메서드 이름과, 겹칠 때 쓸 컨트롤러 이름(접미사 없이)을 붙인 이름을 남긴다") {
                documentFind() shouldBe mapOf("id" to "find", "qualified" to "accountFind")
            }

            then("Kotlin 이 바꾼 JVM 이름(internal), suspend 함수, CGLIB 프록시도 소스의 이름이다") {
                val proxy = ProxyFactory(AccountApiController())
                    .apply { isProxyTargetClass = true }
                    .proxy

                (proxy::class.java == AccountApiController::class.java) shouldBe false
                handlerOf(proxy, "find")?.controllerName shouldBe "AccountApiController"
                handlerOf(AccountApiController(), "internalLookup")?.methodName shouldBe "internalLookup"
                handlerOf(AccountApiController(), "findLater")?.methodName shouldBe "findLater"
            }

            then("어노테이션 컨트롤러 메서드가 아닌 핸들러(함수형 라우터 등)는 모른다 (테스트 이름 id 로 대신한다)") {
                DocumentedHandler.of(Any()).shouldBeNull()
                DocumentedHandler.of(null).shouldBeNull()
            }
        }

        given("openApi { operationId }") {
            then("정한 id 를 그대로 쓰는 고정 id 로 남기고, 규칙보다 우선한다") {
                documentFind({ openApi.operationIdResolver = OperationIdResolver { "fromResolver" } }) {
                    openApi { operationId = "lookupAccount" }
                } shouldBe mapOf("id" to "lookupAccount", "fixed" to true)
            }

            then("빈 문자열이나 공백이 있는 id 는 거부한다") {
                shouldThrow<IllegalArgumentException> { DocumentationBuilder().openApi { operationId = "" } }
                    .message shouldContain "invalid operationId ''"
                shouldThrow<IllegalArgumentException> { DocumentationBuilder().openApi { operationId = "find user" } }
            }
        }

        given("openApi.operationIdResolver") {
            then("입력(HTTP 메서드, 경로 템플릿, 컨트롤러·메서드 이름, 테스트 기반 id)으로 만든 id 를 고정 id 로 남긴다") {
                var seen: OperationIdInput? = null

                documentFind({
                    openApi.operationIdResolver = OperationIdResolver { input ->
                        seen = input
                        "account_${input.handlerMethodName}"
                    }
                }) shouldBe mapOf("id" to "account_find", "fixed" to true)

                val input = seen.shouldNotBeNull()

                input.httpMethod shouldBe "GET"
                input.path shouldBe "/accounts/{id}"
                input.controllerName shouldBe "AccountApiController"
                input.testBasedId shouldBe "operation-id-test.find"
            }

            then("null 을 돌려주면 선택한 규칙으로 만든다") {
                documentFind({
                    openApi.operationIdResolver = OperationIdResolver { null }
                    openApi.operationIdRule = OperationIdRule.CONTROLLER_AND_METHOD
                }) shouldBe mapOf("id" to "accountFind")
            }

            then("공백이 있는 id 를 돌려주면 어느 요청인지 알려 준다") {
                shouldThrow<IllegalStateException> {
                    documentFind({
                        openApi.operationIdResolver =
                            OperationIdResolver { "a b" }
                    })
                }
                    .message shouldContain "invalid operationId 'a b' for GET /accounts/{id}"
            }
        }

        given("규칙 고르기") {
            then("path: HTTP 메서드와 경로의 camelCase, 경로 변수는 By + 이름") {
                documentFind({ openApi.operationIdRule = OperationIdRule.PATH }) shouldBe
                    mapOf("id" to "getAccountsById")
            }

            then("test-name: 후보를 남기지 않는다 (플러그인이 테스트 이름으로 만든다)") {
                documentFind({ openApi.operationIdRule = OperationIdRule.TEST_NAME }).shouldBeNull()
            }

            then("yml 값은 대소문자와 _/- 를 가리지 않는다") {
                OperationIdRule.fromPropertyValue("TEST_NAME") shouldBe OperationIdRule.TEST_NAME
                OperationIdRule.fromPropertyValue("handler-method") shouldBe OperationIdRule.HANDLER_METHOD
                OperationIdRule.fromPropertyValue("nope").shouldBeNull()
            }
        }

        given("이름 규칙") {
            then("경로: 버전·하이픈·경로 변수·한글") {
                OperationIdNames.path("GET", "/api/v1/users/search") shouldBe "getApiV1UsersSearch"
                OperationIdNames.path("DELETE", "/users/{userId}/order-items/{itemId}") shouldBe
                    "deleteUsersByUserIdOrderItemsByItemId"
                OperationIdNames.path("GET", "/files/v{version}") shouldBe "getFilesVByVersion"
                OperationIdNames.path("POST", "/사용자") shouldBe "post"
                OperationIdNames.path("GET", "/") shouldBe "get"
            }

            then("컨트롤러 이름의 RestController·Controller·Api 접미사를 뗀다 (접미사뿐이면 그대로)") {
                OperationIdNames.controllerAndMethod("UserApiController", "create") shouldBe "userCreate"
                OperationIdNames.controllerAndMethod("OrderRestController", "list") shouldBe "orderList"
                OperationIdNames.controllerAndMethod("Controller", "get") shouldBe "controllerGet"
            }

            then("테스트 기반 id 는 플러그인의 test-name 정리와 같다") {
                OperationIdNames.testBased("user-spec/사용자 조회") shouldBe "user-spec.사용자-조회"
                OperationIdNames.testBased("a/b/") shouldBe "a.b"
            }
        }

        given("WebTestClient") {
            then("restDocsWebTestClient 는 WebFlux 가 고른 컨트롤러 메서드를 기록한다") {
                val output = Output()

                AnnotationConfigApplicationContext(AccountWebFluxConfig::class.java).use { context ->
                    output.run("find") {
                        restDocsWebTestClient(context, output.provider)
                            .get()
                            .uri("/accounts/{id}", 1)
                            .exchange()
                            .expectStatus()
                            .isOk
                            .andDocument { }
                    }
                }

                output.candidate("find") shouldBe mapOf("id" to "find", "qualified" to "accountFind")
            }

            then("bindToController 도 recordHandlers 를 걸면 핸들러를 기록한다") {
                val output = Output()

                output.run("find") {
                    WebTestClient.bindToController(AccountApiController())
                        .recordHandlers()
                        .configureClient()
                        .restDocs(output.provider)
                        .build()
                        .get()
                        .uri("/accounts/{id}", 1)
                        .exchange()
                        .expectStatus()
                        .isOk
                        .andDocument { }
                }

                output.candidate("find") shouldBe mapOf("id" to "find", "qualified" to "accountFind")
            }

            then("기록하지 않은 바인딩은 핸들러를 모른다 (후보 없이 테스트 이름 id)") {
                val output = Output()

                output.run("find") {
                    WebTestClient.bindToController(AccountApiController())
                        .configureClient()
                        .restDocs(output.provider)
                        .build()
                        .get()
                        .uri("/accounts/{id}", 1)
                        .exchange()
                        .expectStatus()
                        .isOk
                        .andDocument { }
                }

                output.candidate("find").shouldBeNull()
            }
        }
    })
