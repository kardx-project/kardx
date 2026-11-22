package io.github.kardx.internal.documentation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.formMeans
import io.github.kardx.dsl.pathMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.ofType
import io.github.kardx.extractor.parametersOf
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.webtestclient.andDocument
import io.github.kardx.webtestclient.recordHandlers
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.MediaType
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

enum class HandlerGrade { GOLD, SILVER }

/** 핸들러 인자와 이름이 같지만 타입은 문자열인 쿼리 객체. 추출한 타입이 핸들러 타입보다 우선하는지 본다. */
class SizeQuery(val size: String)

@RestController
class TypedParameterController {
    @GetMapping("/typed/{id}/items/{code}")
    @Suppress("LongParameterList", "UnusedParameter")
    fun find(
        @PathVariable id: Long,
        @PathVariable("code") itemCode: String,
        @RequestParam size: Int,
        @RequestParam(required = false) grade: HandlerGrade?,
        @RequestParam(name = "ids", required = false) idList: List<Long>?,
        @RequestParam(required = false) since: LocalDate?,
        flag: Boolean?,
    ): Map<String, Any> = mapOf("id" to id)

    @Deprecated("GET /typed/{id}/items/{code} 를 쓰세요")
    @GetMapping("/typed/old")
    fun old(): Map<String, Any> = mapOf("old" to true)

    @PostMapping("/typed/form", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun form(@RequestParam count: Int): Map<String, Any> = mapOf("count" to count)
}

@Deprecated("레거시 컨트롤러")
@RestController
class LegacyTypedController {
    @GetMapping("/legacy")
    fun legacy(): Map<String, Any> = mapOf("legacy" to true)
}

/** 한 테스트 메서드를 문서화하고 스니펫을 읽는다. */
private class HandlerDocs {
    private val directory: File = Files.createTempDirectory("handler-defaults").toFile()
    private val provider = ManualRestDocumentation(directory.absolutePath)

    @Suppress("DEPRECATION")
    private val mockMvc = MockMvcBuilders.standaloneSetup(TypedParameterController(), LegacyTypedController())
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(provider))
        .build()

    fun find(method: String, block: DocumentationBuilder.() -> Unit) = run(method) {
        mockMvc.perform(
            get("/typed/{id}/items/{code}", 1L, "A-1")
                .param("size", "10")
                .param("grade", "GOLD")
                .param("ids", "1", "2")
                .param("since", "2026-01-01")
                .param("flag", "true"),
        ).andDocument(block)
    }

    fun mockMvc(method: String, path: String, block: DocumentationBuilder.() -> Unit) = run(method) {
        mockMvc.perform(get(path)).andDocument(block)
    }

    fun form(method: String, block: DocumentationBuilder.() -> Unit) = run(method) {
        mockMvc.perform(
            post("/typed/form").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("count", "3"),
        ).andDocument(block)
    }

    fun webTestClient(method: String, block: DocumentationBuilder.() -> Unit) {
        val client = WebTestClient.bindToController(TypedParameterController())
            .recordHandlers()
            .configureClient()
            .filter(WebTestClientRestDocumentation.documentationConfiguration(provider))
            .build()

        run(method) {
            client.get()
                .uri("/typed/{id}/items/{code}?size=10&grade=GOLD&ids=1&ids=2", 1L, "A-1")
                .exchange()
                .expectBody()
                .andDocument(block)
        }
    }

    fun snippet(method: String, name: String): String =
        File(directory, "handler-defaults-test/$method/$name").readText()

    /** resource.json 의 [kind] 파라미터 중 [name] */
    fun parameter(method: String, kind: String, name: String): JsonNode =
        ObjectMapper().readTree(snippet(method, "resource.json"))["request"][kind]
            .first { it["name"].asText() == name }

    private fun run(method: String, block: () -> Unit) {
        provider.beforeTest(HandlerDefaultsTest::class.java, method)

        try {
            block()
        } finally {
            provider.afterTest()
        }
    }
}

/** [name] 로거의 경고를 모은다. */
private class CapturedWarnings(name: String) : AutoCloseable {
    private val logger: Logger = Logger.getLogger(name)
    val messages: MutableList<String> = mutableListOf()

    private val handler = object : Handler() {
        override fun publish(record: LogRecord) {
            messages += record.message
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    init {
        logger.addHandler(handler)
    }

    override fun close() {
        logger.removeHandler(handler)
    }
}

private val findDocs: DocumentationBuilder.() -> Unit = {
    pathParameters("id" pathMeans "상품 ID", "code" pathMeans "품목 코드")
    queryParameters(
        "size" queryMeans "크기",
        "grade" queryMeans "등급",
        "ids" queryMeans "ID 목록",
        "since" queryMeans "시작일",
        "flag" queryMeans "플래그",
    )
}

class HandlerDefaultsTest :
    BehaviorSpec({
        given("타입을 정하지 않은 경로·쿼리 파라미터 (MockMvc)") {
            val docs = HandlerDocs()

            docs.find("inferred", findDocs)

            then("핸들러 인자의 타입이 OpenAPI 타입이 된다") {
                docs.parameter("inferred", "pathParameters", "id")["type"].asText() shouldBe "INTEGER"
                docs.parameter("inferred", "pathParameters", "code")["type"].asText() shouldBe "STRING"
                docs.parameter("inferred", "queryParameters", "size")["type"].asText() shouldBe "INTEGER"
                docs.parameter("inferred", "queryParameters", "flag")["type"].asText() shouldBe "BOOLEAN"
            }

            then("스키마 조각에 format·enum·배열 원소가 들어간다 (Gradle 플러그인이 합친다)") {
                val json = docs.snippet("inferred", "resource.json")

                json shouldContain "\"format\" : \"int64\""
                json shouldContain "\"format\" : \"date\""
                json shouldContain "\"GOLD\""
                docs.parameter("inferred", "queryParameters", "ids")["attributes"]["schema"]["type"]
                    .asText() shouldBe "array"
            }

            then("AsciiDoc 표는 바뀌지 않는다") {
                docs.snippet("inferred", "path-parameters.adoc") shouldNotContain "integer"
            }
        }

        given("ofType 이나 추출한 타입이 있는 파라미터") {
            val docs = HandlerDocs()

            docs.find("explicit") {
                pathParameters("id" pathMeans "상품 ID" ofType String::class, "code" pathMeans "품목 코드")
                queryParameters(parametersOf<SizeQuery>())
                queryParameters(
                    "grade" queryMeans "등급",
                    "ids" queryMeans "ID 목록",
                    "since" queryMeans "시작일",
                    "flag" queryMeans "플래그",
                )
            }

            then("명시한 타입이 이긴다") {
                docs.parameter("explicit", "pathParameters", "id")["type"].asText() shouldBe "STRING"
                docs.parameter("explicit", "queryParameters", "size")["type"].asText() shouldBe "STRING"
            }
        }

        given("폼 파라미터") {
            val docs = HandlerDocs()

            docs.form("form") { formParameters("count" formMeans "개수") }

            then("핸들러의 @RequestParam 타입이 스키마 조각이 된다") {
                docs.parameter("form", "formParameters", "count")["attributes"]["schema"]["type"]
                    .asText() shouldBe "integer"
            }
        }

        given("WebTestClient 에서 핸들러를 기록하면") {
            val docs = HandlerDocs()

            docs.webTestClient("web") {
                pathParameters("id" pathMeans "상품 ID", "code" pathMeans "품목 코드")
                queryParameters(
                    "size" queryMeans "크기",
                    "grade" queryMeans "등급",
                    "ids" queryMeans "ID 목록",
                )
            }

            then("MockMvc 와 같이 핸들러 타입을 쓴다") {
                docs.parameter("web", "pathParameters", "id")["type"].asText() shouldBe "INTEGER"
                docs.parameter("web", "queryParameters", "size")["type"].asText() shouldBe "INTEGER"
            }
        }

        given("경로 변수를 문서화하지 않은 블록") {
            val docs = HandlerDocs()

            CapturedWarnings("io.github.kardx.internal.openapi.UndocumentedPathVariables").use { warnings ->
                docs.find("undocumented") { }
                docs.find("undocumented") { }

                RestDocsConfiguration.withSettings({ openApi.enabled = false }) {
                    docs.find("openapi-off") { }
                }

                then("OpenAPI 를 만들 때 테스트마다 한 번, 붙여 넣을 줄과 함께 경고한다") {
                    warnings.messages shouldHaveSize 1
                    warnings.messages.single() shouldContain "GET /typed/{id}/items/{code}"
                    warnings.messages.single() shouldContain
                        "pathParameters(\"id\" pathMeans \"...\", \"code\" pathMeans \"...\")"
                }
            }
        }

        given("deprecated") {
            val docs = HandlerDocs()

            docs.mockMvc("plain", "/typed/old?x=1") { deprecated = false }
            docs.mockMvc("handler", "/typed/old") { }
            docs.mockMvc("legacy", "/legacy") { }
            docs.mockMvc("declared", "/legacy") { deprecated("v2 를 쓰세요") }
            docs.mockMvc("openapi-block", "/typed/old") { openApi { deprecated = false } }
            docs.form("none") { formParameters("count" formMeans "개수") }

            then("핸들러 메서드의 Kotlin @Deprecated 와 그 message 를 기본값으로 쓴다") {
                docs.snippet("handler", "api-summary.adoc").lines() shouldContainExactly listOf(
                    "[.api-summary]",
                    "`GET /typed/old` → `200 OK`",
                    "",
                    "*Deprecated*: GET /typed/{id}/items/{code} 를 쓰세요",
                    "",
                )
                docs.snippet("handler", "resource.json") shouldContain "\"deprecated\" : true"
            }

            then("컨트롤러 클래스의 @Deprecated 도 따르고, 블록의 이유가 이긴다") {
                docs.snippet("legacy", "api-summary.adoc") shouldContain "*Deprecated*: 레거시 컨트롤러"
                docs.snippet("declared", "api-summary.adoc") shouldContain "*Deprecated*: v2 를 쓰세요"
            }

            then("블록이나 openApi 블록에서 false 로 정하면 핸들러가 @Deprecated 여도 표시하지 않는다") {
                docs.snippet("plain", "api-summary.adoc") shouldNotContain "Deprecated"
                docs.snippet("openapi-block", "api-summary.adoc") shouldNotContain "Deprecated"
                docs.snippet("openapi-block", "resource.json") shouldContain "\"deprecated\" : false"
            }

            then("deprecated 가 아니면 api-summary 는 예전과 같다") {
                docs.snippet("none", "api-summary.adoc") shouldBe "[.api-summary]\n`POST /typed/form` → `200 OK`\n"
            }
        }

        given("block 의 deprecated 와 openApi.deprecated") {
            then("같은 값이다") {
                val builder = DocumentationBuilder()

                builder.openApi { deprecated = true }
                builder.deprecated shouldBe true

                builder.deprecated = false
                builder.toSpec().openApi.deprecated shouldBe false

                builder.deprecated("이유")
                builder.toSpec().openApi.deprecationNote shouldBe "이유"
                builder.toSpec().openApi.deprecated shouldBe true
            }
        }
    })
