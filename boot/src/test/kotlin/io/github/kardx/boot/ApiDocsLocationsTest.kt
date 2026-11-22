package io.github.kardx.boot

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.mock.env.MockEnvironment

private val ALL_DOCS = setOf("static/docs/index.html", "static/docs/openapi3.yaml", "static/docs/asyncapi.html")

private fun web(port: String = "8080") = MockEnvironment().withProperty("local.server.port", port)

class ApiDocsLocationsTest :
    BehaviorSpec({
        given("웹 서버가 8080 에서 뜨고 문서·springdoc 이 있을 때") {
            then("기본 이름(영어)으로 네 문서의 주소를 남긴다") {
                ApiDocsLocations.lines(web(), ALL_DOCS::contains, swaggerUiAvailable = true) shouldContainExactly
                    listOf(
                        "API docs (REST Docs    ) http://localhost:8080/docs/index.html",
                        "API docs (Swagger UI   ) http://localhost:8080/swagger-ui/index.html",
                        "API docs (OpenAPI spec ) http://localhost:8080/docs/openapi3.yaml",
                        "API docs (WebSocket/SSE) http://localhost:8080/docs/asyncapi.html",
                    )
            }

            then("kardx.language=ko 면 한국어 이름을 쓰고 한글 폭에 맞춰 줄을 맞춘다") {
                val environment = web().withProperty("kardx.language", "KO")

                ApiDocsLocations.lines(environment, ALL_DOCS::contains, swaggerUiAvailable = true) shouldContainExactly
                    listOf(
                        "API 문서 (REST Docs    ) http://localhost:8080/docs/index.html",
                        "API 문서 (Swagger UI   ) http://localhost:8080/swagger-ui/index.html",
                        "API 문서 (OpenAPI 스펙 ) http://localhost:8080/docs/openapi3.yaml",
                        "API 문서 (WebSocket·SSE) http://localhost:8080/docs/asyncapi.html",
                    )
            }

            then("지원하지 않는 언어는 앱을 멈추지 않고 경고 한 번을 남긴 뒤 영어로 쓴다") {
                val environment = web().withProperty("kardx.language", "fr")
                val warnings = mutableListOf<String>()

                val lines = ApiDocsLocations.lines(
                    environment,
                    ALL_DOCS::contains,
                    swaggerUiAvailable = true,
                    warn = warnings::add,
                )

                warnings shouldContainExactly listOf(
                    "kardx[KDX-601]: kardx.language: unsupported language 'fr'. " +
                        "Use one of en, ko; falling back to en",
                )
                lines.first() shouldBe "API docs (REST Docs    ) http://localhost:8080/docs/index.html"
            }

            then("플러그인이 남긴 언어 값이 잘못돼도 같은 경고 후 영어로 쓴다") {
                val warnings = mutableListOf<String>()

                val lines = ApiDocsLocations.lines(
                    web(),
                    ALL_DOCS::contains,
                    swaggerUiAvailable = true,
                    defaults = mapOf("language" to "kr"),
                    warn = warnings::add,
                )

                warnings.single() shouldContain "unsupported language 'kr'"
                lines.first() shouldBe "API docs (REST Docs    ) http://localhost:8080/docs/index.html"
            }

            then("지원하는 언어면 경고하지 않는다") {
                val warnings = mutableListOf<String>()

                ApiDocsLocations.lines(
                    web().withProperty("kardx.language", " Ko "),
                    ALL_DOCS::contains,
                    swaggerUiAvailable = true,
                    warn = warnings::add,
                )

                warnings.shouldBeEmpty()
            }
        }

        given("정적 OpenAPI 뷰어(openapi.html)가 있으면") {
            then("Swagger UI 다음 줄에 남긴다") {
                ApiDocsLocations.lines(
                    web(),
                    (ALL_DOCS + "static/docs/openapi.html")::contains,
                    swaggerUiAvailable = true,
                ) shouldContainExactly
                    listOf(
                        "API docs (REST Docs     ) http://localhost:8080/docs/index.html",
                        "API docs (Swagger UI    ) http://localhost:8080/swagger-ui/index.html",
                        "API docs (OpenAPI viewer) http://localhost:8080/docs/openapi.html",
                        "API docs (OpenAPI spec  ) http://localhost:8080/docs/openapi3.yaml",
                        "API docs (WebSocket/SSE ) http://localhost:8080/docs/asyncapi.html",
                    )
            }
        }

        given("Gradle 플러그인이 남긴 문서 위치 (경로 manual, 파일 이름이 기본값과 다름)") {
            val defaults = mapOf(
                "docs-path" to "manual",
                "startup-log.open-api.file" to "api.json",
                "startup-log.async-api.file" to "async/index.html",
            )
            val docs = setOf("static/manual/index.html", "static/manual/api.json", "static/manual/async/index.html")

            then("yml 에 적지 않아도 그 경로·파일 이름을 쓴다") {
                ApiDocsLocations.lines(web(), docs::contains, swaggerUiAvailable = false, defaults = defaults) shouldBe
                    listOf(
                        "API docs (REST Docs    ) http://localhost:8080/manual/index.html",
                        "API docs (OpenAPI spec ) http://localhost:8080/manual/api.json",
                        "API docs (WebSocket/SSE) http://localhost:8080/manual/async/index.html",
                    )
            }

            then("플러그인이 남긴 language 도 기본값으로 쓰고, yml 의 language 가 이긴다") {
                val korean = defaults + ("language" to "ko")
                val english = web().withProperty("kardx.language", "en")

                ApiDocsLocations.lines(web(), docs::contains, false, defaults = korean)
                    .first() shouldBe "API 문서 (REST Docs    ) http://localhost:8080/manual/index.html"
                ApiDocsLocations.lines(english, docs::contains, false, defaults = korean)
                    .first() shouldBe "API docs (REST Docs    ) http://localhost:8080/manual/index.html"
            }

            then("yml 에 적은 값이 이긴다") {
                val environment = web().withProperty("kardx.startup-log.open-api.file", "openapi3.yaml")
                val withYaml = docs + "static/manual/openapi3.yaml"

                ApiDocsLocations.lines(environment, withYaml::contains, false, defaults = defaults)
                    .joinToString("\n") shouldContain
                    "API docs (OpenAPI spec ) http://localhost:8080/manual/openapi3.yaml"
            }
        }

        given("이름·파일·제목을 설정으로 바꾸면") {
            val environment =
                web()
                    .withProperty("kardx.startup-log.title", "Docs")
                    .withProperty("kardx.startup-log.rest-docs.label", "가이드")
                    .withProperty("kardx.startup-log.async-api.label", "메시지")
                    .withProperty("kardx.startup-log.async-api.file", "messages.html")
                    .withProperty("kardx.startup-log.open-api.enabled", "false")
                    .withProperty("kardx.startup-log.swagger-ui.enabled", "false")

            then("언어와 상관없이 그대로 쓴다") {
                val docs = setOf("static/docs/index.html", "static/docs/messages.html", "static/docs/openapi3.yaml")

                ApiDocsLocations.lines(
                    environment.withProperty("kardx.language", "ko"),
                    docs::contains,
                    swaggerUiAvailable = true,
                ) shouldContainExactly
                    listOf(
                        "Docs (가이드) http://localhost:8080/docs/index.html",
                        "Docs (메시지) http://localhost:8080/docs/messages.html",
                    )
            }
        }

        given("context-path, https, 바꾼 경로") {
            val environment =
                web("8443")
                    .withProperty("server.ssl.enabled", "true")
                    .withProperty("server.servlet.context-path", "/api/")
                    .withProperty("kardx.docs-path", "/api-docs/")
                    .withProperty("springdoc.swagger-ui.path", "/swagger")

            then("주소에 반영한다") {
                ApiDocsLocations.lines(
                    environment,
                    setOf("public/api-docs/index.html")::contains,
                    true,
                ) shouldContainExactly
                    listOf(
                        "API docs (REST Docs ) https://localhost:8443/api/api-docs/index.html",
                        "API docs (Swagger UI) https://localhost:8443/api/swagger",
                    )
            }
        }

        given("DispatcherServlet 경로 (spring.mvc.servlet.path)") {
            val environment =
                web()
                    .withProperty("server.servlet.context-path", "/app")
                    .withProperty("spring.mvc.servlet.path", "/api/")

            then("정적 문서는 DispatcherServlet 이 서빙하므로 context-path 뒤에 servlet path 를 붙인다") {
                ApiDocsLocations.lines(environment, setOf("static/docs/index.html")::contains, false)
                    .single() shouldBe "API docs (REST Docs) http://localhost:8080/app/api/docs/index.html"
            }

            then("WebFlux 앱에서는 보지 않는다") {
                ApiDocsLocations.lines(environment, setOf("static/docs/index.html")::contains, false, reactive = true)
                    .single() shouldBe "API docs (REST Docs) http://localhost:8080/docs/index.html"
            }

            then("springdoc Swagger UI 도 DispatcherServlet 의 리소스 핸들러가 서빙하므로 servlet path 를 붙인다") {
                ApiDocsLocations.lines(environment, setOf("static/docs/index.html")::contains, true)
                    .last() shouldBe "API docs (Swagger UI) http://localhost:8080/app/api/swagger-ui/index.html"
            }
        }

        given("WebFlux 앱 (spring.webflux.base-path)") {
            val environment =
                web()
                    .withProperty("spring.webflux.base-path", "/api/")
                    .withProperty("server.servlet.context-path", "/ignored")

            then("base-path 를 붙이고 Swagger UI 는 springdoc WebFlux 의 /webjars 주소를 쓴다") {
                ApiDocsLocations.lines(
                    environment,
                    setOf("static/docs/index.html")::contains,
                    swaggerUiAvailable = true,
                    reactive = true,
                ) shouldContainExactly
                    listOf(
                        "API docs (REST Docs ) http://localhost:8080/api/docs/index.html",
                        "API docs (Swagger UI) http://localhost:8080/api/webjars/swagger-ui/index.html",
                    )
            }

            then("서블릿 앱에서는 base-path 를 보지 않는다") {
                ApiDocsLocations.lines(environment, setOf("static/docs/index.html")::contains, false)
                    .single() shouldBe "API docs (REST Docs) http://localhost:8080/ignored/docs/index.html"
            }
        }

        given("서버 설정의 특수한 경우") {
            then("key-store 만 있어도 https, IPv6 주소는 대괄호, 루트 경로는 슬래시 하나") {
                val environment =
                    web("8443")
                        .withProperty("server.ssl.key-store", "classpath:keystore.p12")
                        .withProperty("server.address", "::1")
                        .withProperty("kardx.docs-path", "")

                ApiDocsLocations.lines(environment, setOf("static/index.html")::contains, false) shouldContainExactly
                    listOf("API docs (REST Docs) https://[::1]:8443/index.html")
            }

            then("server.port=-1 이면 남기지 않는다") {
                ApiDocsLocations.lines(web("-1"), ALL_DOCS::contains, true).shouldBeEmpty()
            }
        }

        given("문서가 아직 없을 때") {
            then("만드는 방법을 알려 준다") {
                ApiDocsLocations.lines(web(), { false }, swaggerUiAvailable = false) shouldContainExactly listOf(
                    "API docs not found [KDX-602]: no documents in classpath:static/docs. " +
                        "Run ./gradlew apiDocs or restart the app (Gradle plugin generateOnRun)",
                )
            }

            then("language 가 ko 면 한국어로 알려 준다") {
                val environment = web().withProperty("kardx.language", "ko")

                ApiDocsLocations.lines(environment, { false }, swaggerUiAvailable = false) shouldContainExactly listOf(
                    "API 문서 없음 [KDX-602]: classpath:static/docs 에 문서가 없습니다. " +
                        "./gradlew apiDocs 로 만들거나 앱을 다시 실행하세요 (Gradle 플러그인 generateOnRun)",
                )
            }
        }

        given("웹 서버가 없거나 로그를 끈 경우") {
            then("아무것도 남기지 않는다") {
                ApiDocsLocations.lines(MockEnvironment(), ALL_DOCS::contains, true).shouldBeEmpty()
                ApiDocsLocations.lines(
                    web().withProperty("kardx.startup-log.enabled", "false"),
                    ALL_DOCS::contains,
                    true,
                ).shouldBeEmpty()
            }
        }

        given("설정 메타데이터 (IDE 자동 완성)") {
            then("모든 키를 담는다") {
                val json = requireNotNull(
                    javaClass.classLoader.getResource("META-INF/spring-configuration-metadata.json"),
                ).readText()

                Regex(""""name"\s*:\s*"kardx\.([^"]+)"""")
                    .findAll(json)
                    .map { it.groupValues[1] }
                    .filterNot { it == "startup-log" } // 그룹 이름
                    .distinct() // hints 에도 같은 이름이 나온다
                    .toList() shouldContainExactlyInAnyOrder ApiDocsLocations.KEYS
            }
        }
    })
