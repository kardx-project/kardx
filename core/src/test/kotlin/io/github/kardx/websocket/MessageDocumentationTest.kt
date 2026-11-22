package io.github.kardx.websocket

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.MessageProtocol
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.type
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.restdocs.ManualRestDocumentation
import java.io.File
import java.nio.file.Files

class MessageDocumentationTest :
    BehaviorSpec({
        given("STOMP 메시지") {
            val output = Files.createTempDirectory("messages").toFile()
            val documentation = ManualRestDocumentation(output.path)

            documentation.beforeTest(MessageDocumentationTest::class.java, "chat")

            val docs = MessageDocumentation(documentation)

            RestDocsConfiguration.configure { masking.maskSensitive() }

            docs.send("/app/chat.send", payload = mapOf("text" to "안녕")) {
                description = "메시지를 보낸다"
                name = "chatSend"
                headerValues("Authorization" to "Bearer secret")
                headers("Authorization" headerMeans "토큰")
                fields("text" type STRING means "본문")
            }

            documentation.afterTest()

            val dir = File(output, "message-documentation-test/chat")

            then("메시지 스니펫·필드 표·AsyncAPI 메타데이터를 만든다") {
                val message = File(dir, "message-01.adoc").readText()

                message shouldContain "`SEND /app/chat.send` (STOMP)"
                message shouldContain "메시지를 보낸다"
                File(dir, "request-fields-message-01.adoc").readText() shouldContain "`+text+`"
                File(dir, "message-01.asyncapi.json").readText() shouldContain "\"name\":\"chatSend\""
            }

            then("가리기로 한 헤더 값은 스니펫과 메타데이터 모두에서 가린다") {
                File(dir, "message-01.adoc").readText() shouldNotContain "secret"
                File(dir, "message-01.asyncapi.json").readText() shouldNotContain "secret"
            }
        }

        given("문서화하지 않은 페이로드 필드") {
            then("relaxed 가 아니면 실패한다") {
                val output = Files.createTempDirectory("messages").toFile()
                val documentation = ManualRestDocumentation(output.path)

                documentation.beforeTest(MessageDocumentationTest::class.java, "strict")

                shouldThrowAny {
                    MessageDocumentation(documentation).subscribe("/topic/a", payload = """{"text":"a","extra":1}""") {
                        fields("text" type STRING means "본문")
                    }
                }.message shouldContain "extra"

                documentation.afterTest()
            }
        }

        given("프로토콜") {
            fun recordedProtocol(method: String, docs: (ManualRestDocumentation) -> MessageDocumentation): String {
                val output = Files.createTempDirectory("messages").toFile()
                val documentation = ManualRestDocumentation(output.path)

                documentation.beforeTest(MessageDocumentationTest::class.java, method)
                docs(documentation).send("/ws/echo", "hi")
                documentation.afterTest()

                return File(output, "message-documentation-test/$method/message-01.asyncapi.json").readText()
            }

            then("기본은 STOMP, enum 은 표시 이름으로 기록한다") {
                recordedProtocol("default") { MessageDocumentation(it) } shouldContain "\"protocol\":\"STOMP\""
                recordedProtocol("enum") { MessageDocumentation(it, MessageProtocol.WEBSOCKET) } shouldContain
                    "\"protocol\":\"WebSocket\""
            }

            then("문자열이 알려진 이름과 대소문자만 다르면 표시 이름으로 맞추고, 모르는 이름은 그대로 둔다") {
                recordedProtocol("lower") { MessageDocumentation(it, "websocket") } shouldContain
                    "\"protocol\":\"WebSocket\""
                recordedProtocol("custom") { MessageDocumentation(it, "MQTT") } shouldContain "\"protocol\":\"MQTT\""
            }

            then("앞뒤 공백은 지우고, 별칭(ws·stomp·http)이면 AsyncAPI 판정과 같은 enum 의 표시 이름으로 기록한다") {
                recordedProtocol("alias-ws") { MessageDocumentation(it, " ws ") } shouldContain
                    "\"protocol\":\"WebSocket\""
                recordedProtocol("alias-stomp") { MessageDocumentation(it, "Stomp") } shouldContain
                    "\"protocol\":\"STOMP\""
                recordedProtocol("alias-http") { MessageDocumentation(it, "HTTP") } shouldContain "\"protocol\":\"SSE\""
                recordedProtocol("trimmed") { MessageDocumentation(it, "  MQTT\t") } shouldContain
                    "\"protocol\":\"MQTT\""
            }

            then("빈 문자열은 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    MessageDocumentation(ManualRestDocumentation(), " ")
                }.message shouldBe "protocol must not be blank"
            }
        }

        given("asyncApi.enabled = false") {
            then("메타데이터를 남기지 않는다") {
                val output = Files.createTempDirectory("messages").toFile()
                val documentation = ManualRestDocumentation(output.path)

                documentation.beforeTest(MessageDocumentationTest::class.java, "no-asyncapi")

                RestDocsConfiguration.withSettings({ asyncApi.enabled = false }) {
                    MessageDocumentation(documentation, MessageProtocol.WEBSOCKET).send("/ws/echo", "hi")
                }

                documentation.afterTest()

                File(output, "message-documentation-test/no-asyncapi/message-01.asyncapi.json").exists() shouldBe false
            }
        }

        given("연결 헤더 (connect)") {
            then("CONNECT 스니펫과 헤더 표, AsyncAPI 메타데이터를 만든다") {
                val output = Files.createTempDirectory("messages").toFile()
                val documentation = ManualRestDocumentation(output.path)

                documentation.beforeTest(MessageDocumentationTest::class.java, "connect")
                RestDocsConfiguration.configure { masking.maskSensitive() }

                MessageDocumentation(documentation).connect("/ws") {
                    headerValues(mapOf("Authorization" to "Bearer secret", "X-Client-Version" to "1.4.0"))
                    headers(
                        listOf("Authorization" headerMeans "토큰", "X-Client-Version" headerMeans "앱 버전"),
                    )
                }

                documentation.afterTest()

                val dir = File(output, "message-documentation-test/connect")
                val snippet = File(dir, "message-01.adoc").readText()

                snippet shouldContain "`CONNECT /ws` (STOMP)"
                snippet shouldContain "`+X-Client-Version+`"
                snippet shouldNotContain "secret"
                File(dir, "message-01.asyncapi.json").readText() shouldContain "\"direction\":\"CONNECT\""
            }
        }

        given("relaxed = true") {
            then("문서화하지 않은 페이로드 필드를 허용한다") {
                val output = Files.createTempDirectory("messages").toFile()
                val documentation = ManualRestDocumentation(output.path)

                documentation.beforeTest(MessageDocumentationTest::class.java, "relaxed")

                MessageDocumentation(documentation).subscribe("/topic/a", payload = """{"text":"a","extra":1}""") {
                    fields("text" type STRING means "본문")
                    relaxed = true
                }

                documentation.afterTest()

                File(output, "message-documentation-test/relaxed/request-fields-message-01.adoc").exists() shouldBe true
            }
        }

        given("필수 헤더 디스크립터에 맞는 헤더 값이 없을 때") {
            then("실패한다") {
                val output = Files.createTempDirectory("messages").toFile()
                val documentation = ManualRestDocumentation(output.path)

                documentation.beforeTest(MessageDocumentationTest::class.java, "missing-header")

                shouldThrowAny {
                    MessageDocumentation(documentation).send("/app/x", """{"a":1}""") {
                        headers("Authorization" headerMeans "토큰")
                    }
                }.message shouldContain "Authorization"

                documentation.afterTest()
            }
        }

        given("디스크립터 없이 헤더 값만 줄 때") {
            then("헤더 표와 AsyncAPI 헤더에는 나오지 않는다") {
                val output = Files.createTempDirectory("messages").toFile()
                val documentation = ManualRestDocumentation(output.path)

                documentation.beforeTest(MessageDocumentationTest::class.java, "header-only")

                MessageDocumentation(documentation).send("/app/x", """{"a":1}""") {
                    headerValues("X-Trace" to "abc")
                }

                documentation.afterTest()

                val dir = File(output, "message-documentation-test/header-only")

                File(dir, "message-01.adoc").readText() shouldNotContain "X-Trace"
                File(dir, "message-01.asyncapi.json").readText() shouldNotContain "X-Trace"
            }
        }
    })
