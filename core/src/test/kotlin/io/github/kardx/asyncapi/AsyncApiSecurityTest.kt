package io.github.kardx.asyncapi

import io.github.kardx.internal.asyncapi.AsyncApiRecordCodec
import io.github.kardx.internal.asyncapi.YamlWriter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private val SERVERS = listOf(
    AsyncApiServer("stomp", "localhost:8080", "stomp", "/ws"),
    AsyncApiServer("http", "localhost:8080", "http"),
)

private val RECORDS = listOf(
    mapOf(
        "kind" to "message",
        "protocol" to "STOMP",
        "direction" to "SEND",
        "address" to "/app/chat",
        "payload" to """{"a":1}""",
        "fields" to emptyList<Any>(),
    ),
).map { requireNotNull(AsyncApiRecordCodec.decode(it)) }

private fun cli(vararg security: String): AsyncApiInfo =
    AsyncApiCliOptions.parse(security.flatMap { listOf("--security", it) }.toTypedArray()).info

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.at(vararg keys: String): Any? =
    keys.fold(this as Any?) { node, key -> (node as Map<String, Any?>)[key] }

class AsyncApiSecurityTest :
    BehaviorSpec({
        given("--security 옵션") {
            then("bearer, basic, apiKey:<in>:<name> 을 순서대로 읽는다") {
                val schemes = cli("bearerAuth=bearer", "basicAuth=basic", "apiKeyAuth=apiKey:header:X-API-KEY")
                    .securitySchemes

                schemes shouldBe
                    listOf(
                        AsyncApiSecurityScheme.bearer("bearerAuth"),
                        AsyncApiSecurityScheme.basic("basicAuth"),
                        AsyncApiSecurityScheme.apiKey("apiKeyAuth", AsyncApiApiKeyLocation.HEADER, "X-API-KEY"),
                    )
                cli("q=apiKey:query:api_key", "c=apiKey:cookie:SESSION").securitySchemes shouldBe
                    listOf(
                        AsyncApiSecurityScheme.apiKey("q", AsyncApiApiKeyLocation.QUERY, "api_key"),
                        AsyncApiSecurityScheme.apiKey("c", AsyncApiApiKeyLocation.COOKIE, "SESSION"),
                    )
            }

            then("형식이 틀리면 영어 메시지와 함께 거절한다") {
                listOf("bearer", "x=oauth2", "x=apiKey:body:X", "x=apiKey:header", "x=bearer:extra", "=bearer")
                    .forEach { spec ->
                        shouldThrow<IllegalArgumentException> { cli(spec) }
                            .message shouldContain "--security expects"
                    }
            }

            then("같은 이름을 두 번 주면 거절한다") {
                shouldThrow<IllegalArgumentException> { cli("auth=bearer", "auth=basic") }
                    .message shouldBe "duplicate security scheme names: auth"
            }
        }

        given("인증 방식이 있는 문서") {
            val info = AsyncApiInfo(
                title = "Chat",
                version = "1",
                servers = SERVERS,
                securitySchemes = cli("bearerAuth=bearer", "basicAuth=basic", "apiKeyAuth=apiKey:header:X-API-KEY")
                    .securitySchemes,
            )
            val document = AsyncApiDocumentBuilder(info).build(RECORDS)

            then("components.securitySchemes 는 AsyncAPI 3.0 형식이다") {
                document.at("components", "securitySchemes") shouldBe mapOf(
                    "bearerAuth" to mapOf("type" to "http", "scheme" to "bearer"),
                    "basicAuth" to mapOf("type" to "http", "scheme" to "basic"),
                    "apiKeyAuth" to mapOf("type" to "httpApiKey", "name" to "X-API-KEY", "in" to "header"),
                )
            }

            then("모든 서버의 security 가 인증 방식을 참조한다") {
                val references = listOf("bearerAuth", "basicAuth", "apiKeyAuth")
                    .map { mapOf("\$ref" to "#/components/securitySchemes/$it") }

                document.at("servers", "stomp", "security") shouldBe references
                document.at("servers", "http", "security") shouldBe references
            }

            then("YAML 로 쓴다") {
                val yaml = YamlWriter.write(document)

                yaml shouldContain
                    """
                    |    security:
                    |      - ${'$'}ref: "#/components/securitySchemes/bearerAuth"
                    """.trimMargin()
                yaml shouldContain
                    """
                    |  securitySchemes:
                    |    bearerAuth:
                    |      type: "http"
                    |      scheme: "bearer"
                    """.trimMargin()
            }
        }

        given("인증 방식이 없는 문서") {
            val document = AsyncApiDocumentBuilder(AsyncApiInfo("Chat", "1", servers = SERVERS)).build(RECORDS)

            then("securitySchemes 와 서버 security 를 쓰지 않는다") {
                (document["components"] as Map<*, *>).keys shouldBe setOf("messages")
                YamlWriter.write(document) shouldNotContain "security"
            }
        }
    })
