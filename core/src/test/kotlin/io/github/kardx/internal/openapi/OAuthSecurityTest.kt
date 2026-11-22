package io.github.kardx.internal.openapi

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class OAuthController {
    @GetMapping("/oauth/users")
    fun users(): List<String> = listOf("kim")
}

class OAuthSecurityTest :
    BehaviorSpec({
        given("openApi { oauth2(...) / openIdConnect(...) }") {
            val docs = DocumentedExtras(OAuthController())
            val bearer = "Bearer opaque-token"

            `when`("oauth2 를 두 번, openIdConnect 를 한 번 선언하면") {
                val json = docs.run("oauth", get("/oauth/users").header("Authorization", bearer)) {
                    openApi {
                        oauth2("users:read")
                        oauth2("users:read", "users:write")
                        openIdConnect()
                    }
                }

                then("스키마마다 scope 를 합쳐 선언 순서로 남긴다") {
                    json["security"] shouldBe listOf(
                        mapOf("type" to "oauth2", "scopes" to listOf("users:read", "users:write")),
                        mapOf("type" to "openIdConnect", "scopes" to emptyList<String>()),
                    )
                }

                then("요청의 Authorization 헤더로 Bearer 보안 스키마를 만들지 않는다") {
                    docs.file("oauth", "resource.json").readText() shouldContain "\"securityRequirements\" : null"
                }

                then("REST Docs 스니펫의 요청 헤더는 그대로다") {
                    docs.file("oauth", "http-request.adoc").readText() shouldContain "Authorization"
                }
            }

            `when`("bearerAuth() 와 함께 선언하면") {
                docs.run("oauth-and-bearer", get("/oauth/users")) {
                    openApi {
                        bearerAuth()
                        oauth2("users:read")
                    }
                }

                then("선언한 Bearer 보안 스키마도 남는다") {
                    docs.file("oauth-and-bearer", "resource.json").readText() shouldNotContain
                        "\"securityRequirements\" : null"
                }
            }

            `when`("선언하지 않으면") {
                val json = docs.run("none", get("/oauth/users")) {}

                then("security 키가 없고, Spring 6 핸들러에는 apiVersion 도 없다") {
                    json.containsKey("security") shouldBe false
                    json.containsKey("apiVersion") shouldBe false
                }
            }

            `when`("scope 에 공백이 있으면") {
                then("선언할 때 실패한다") {
                    shouldThrow<IllegalArgumentException> {
                        docs.run("invalid", get("/oauth/users")) { openApi { oauth2("users read") } }
                    }.message shouldContain "invalid oauth2 scope"
                }
            }
        }
    })
