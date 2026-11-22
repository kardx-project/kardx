package io.github.kardx.asyncapi

import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.asyncapi.AsyncApiEntry
import io.github.kardx.internal.asyncapi.AsyncApiOrigin
import io.github.kardx.internal.asyncapi.AsyncApiRecordCodec
import io.github.kardx.internal.asyncapi.EventRecord
import io.github.kardx.internal.asyncapi.EventStreamRecord
import io.github.kardx.internal.asyncapi.HeaderRecord
import io.github.kardx.internal.asyncapi.MessageRecord
import io.github.kardx.internal.asyncapi.YamlWriter
import io.github.kardx.internal.schema.JsonSchemaBuilder
import io.github.kardx.internal.schema.SchemaField
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

private fun field(
    path: String,
    type: String,
    optional: Boolean = false,
    constraints: String? = null,
    ignored: Boolean = false,
    enumValues: List<String>? = null,
) = SchemaField(
    path = path,
    type = type,
    description = "$path 설명",
    optional = optional,
    ignored = ignored,
    constraints = constraints,
    enumValues = enumValues,
)

/** 디스크 형식(`*.asyncapi.json`)의 필드 JSON. */
private fun fieldJson(path: String, type: String) = mapOf(
    "path" to path,
    "type" to type,
    "description" to "$path 설명",
    "optional" to false,
    "constraints" to null,
    "ignored" to false,
)

/** 디스크 형식의 메타데이터를 읽어 빌더 입력으로 바꾼다. */
private fun entries(vararg json: Map<String, Any?>): List<AsyncApiEntry> =
    json.map { requireNotNull(AsyncApiRecordCodec.decode(it)) }

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.at(vararg keys: String): Any? =
    keys.fold(this as Any?) { node, key -> (node as Map<String, Any?>)[key] }

class AsyncApiGeneratorTest :
    BehaviorSpec({

        given("YAML 출력") {
            then("맵·리스트·중첩·특수 문자를 안전하게 쓴다") {
                YamlWriter.write(
                    linkedMapOf(
                        "a" to 1,
                        "b" to listOf(mapOf("x" to "y", "z" to true), "plain"),
                        "\$ref" to "#/x",
                        "with space" to "줄1\n\"따옴표\"",
                        "empty" to emptyMap<String, Any>(),
                    ),
                ) shouldBe """
                a: 1
                b:
                  - x: "y"
                    z: true
                  - "plain"
                ${'$'}ref: "#/x"
                "with space": "줄1\n\"따옴표\""
                empty: {}

                """.trimIndent()
            }
        }

        given("필드 목록") {
            val schema = JsonSchemaBuilder.fromFields(
                listOf(
                    field("roomId", "Number"),
                    field("content", "String", constraints = "NotBlank - Must not be blank"),
                    field("meta", "Object", optional = true),
                    field("meta.tags", "Array", optional = true),
                    field("items", "Array"),
                    field("items[].name", "String"),
                    field("secret", "String", ignored = true),
                ),
            )

            then("중첩 객체·배열 items·required·제약조건을 JSON Schema 로 만든다") {
                schema["type"] shouldBe "object"
                schema["required"] shouldBe listOf("roomId", "content", "items")
                schema.at("properties", "content", "description") shouldBe
                    "content 설명\n\nConstraints: NotBlank - Must not be blank"
                schema.at("properties", "meta", "properties", "tags", "type") shouldBe "array"
                schema.at("properties", "items", "items", "properties", "name", "type") shouldBe "string"
                schema.at("properties", "secret") shouldBe null
            }

            then("최상위 배열 경로([].id)는 루트 배열이 된다") {
                val array = JsonSchemaBuilder.fromFields(listOf(field("[].id", "Number")))

                array["type"] shouldBe "array"
                array.at("items", "properties", "id", "type") shouldBe "number"
            }

            then("Enum 필드는 문자열 + enum 목록") {
                val schema = JsonSchemaBuilder.fromFields(
                    listOf(field("status", "enum", enumValues = listOf("TODO", "DONE"))),
                )

                schema.at("properties", "status", "type") shouldBe "string"
                schema.at("properties", "status", "enum") shouldBe listOf("TODO", "DONE")
            }
        }

        given("메시지와 SSE 메타데이터") {
            val info = AsyncApiInfo(
                title = "Chat",
                version = "1.0.0",
                servers = listOf(
                    AsyncApiServer("stomp", "localhost:8080", "stomp", "/ws"),
                    AsyncApiServer("http", "localhost:8080", "http"),
                ),
            )

            val document = AsyncApiDocumentBuilder(info).build(
                entries(
                    mapOf(
                        "kind" to "message", "key" to "message-01", "name" to "chatSend",
                        "operation" to "chat-test/chat",
                        "tag" to "채팅",
                        "summary" to "메시지 보내기", "protocol" to "STOMP", "direction" to "SEND",
                        "address" to "/app/chat.send",
                        "headers" to
                            listOf(
                                mapOf(
                                    "name" to "content-type",
                                    "description" to "형식",
                                    "optional" to false,
                                    "example" to "application/json",
                                ),
                            ),
                        "payload" to """{"roomId":1}""", "fields" to listOf(fieldJson("roomId", "Number")),
                    ),
                    mapOf(
                        "kind" to "message", "key" to "message-02",
                        "operation" to "chat-test/chat", "tag" to "채팅",
                        "protocol" to "STOMP", "direction" to "SUBSCRIBE", "address" to "/topic/rooms.{roomId}",
                        "payload" to """{"id":1}""", "fields" to listOf(fieldJson("id", "Number")),
                    ),
                    mapOf(
                        "kind" to "event-stream",
                        "operation" to "noti-test/subscribe",
                        "tag" to "알림",
                        "protocol" to "SSE",
                        "address" to "/notifications/stream",
                        "events" to
                            listOf(
                                mapOf(
                                    "name" to "connected",
                                    "description" to "연결",
                                    "optional" to false,
                                    "payload" to """{"a":1}""",
                                    "fields" to listOf(fieldJson("a", "Number")),
                                ),
                            ),
                    ),
                ),
            )

            then("SEND 는 receive, SUBSCRIBE·SSE 는 send 로 옮긴다") {
                document.at("operations", "receive-chatSend", "action") shouldBe "receive"
                document.at("operations", "send-subscribe-topic-rooms-roomid", "action") shouldBe "send"
                document.at("operations", "send-notifications-stream", "action") shouldBe "send"
                document.at("components", "messages", "connected", "name") shouldBe "connected"
            }

            then("채널은 프로토콜이 맞는 서버에만 연결되고, 경로 변수는 parameters 가 된다") {
                document.at("channels", "app-chat-send", "servers") shouldBe listOf(mapOf("\$ref" to "#/servers/stomp"))
                document.at("channels", "notifications-stream", "servers") shouldBe
                    listOf(mapOf("\$ref" to "#/servers/http"))
                document.at("channels", "topic-rooms-roomid", "parameters") shouldBe
                    mapOf("roomId" to emptyMap<String, Any>())
            }

            then("메시지는 headers·payload 스키마와 실제 예시를 가진다") {
                @Suppress("UNCHECKED_CAST")
                val message = document.at("components", "messages", "chatSend") as Map<String, Any?>

                message["title"] shouldBe "chatSend"
                message.at("headers", "required") shouldBe listOf("content-type")
                message.at("payload", "properties", "roomId", "type") shouldBe "number"
                message["examples"] shouldBe
                    listOf(
                        mapOf(
                            "headers" to mapOf("content-type" to "application/json"),
                            "payload" to mapOf("roomId" to 1),
                        ),
                    )

                document.at("operations", "receive-chatSend", "messages") shouldBe
                    listOf(mapOf("\$ref" to "#/channels/app-chat-send/messages/chatSend"))
            }
        }

        given("같은 목적지·방향의 이름 없는 메시지 두 개") {
            fun subscribe(key: String, payload: String, fields: List<Map<String, Any?>>) = mapOf(
                "kind" to "message", "key" to key, "operation" to "ws-test/$key", "tag" to "WS",
                "protocol" to "WebSocket", "direction" to "SUBSCRIBE", "address" to "/ws/shape",
                "payload" to payload, "fields" to fields,
            )

            val document = AsyncApiDocumentBuilder(AsyncApiInfo(title = "WS", version = "1")).build(
                entries(
                    subscribe("a", """{"id":1}""", listOf(fieldJson("id", "Number"))),
                    subscribe("b", """[{"kind":"dog"}]""", listOf(fieldJson("[].kind", "String"))),
                    subscribe("c", """{"id":2}""", listOf(fieldJson("id", "Number"))),
                ),
            )

            @Suppress("UNCHECKED_CAST")
            val messages = document.at("components", "messages") as Map<String, Map<String, Any?>>

            then("페이로드 구조가 다르면 다른 메시지로 두고, 같으면 예시만 더한다") {
                messages.keys shouldBe setOf("subscribe-ws-shape", "subscribe-ws-shape-2")
                messages.getValue("subscribe-ws-shape").at("payload", "properties", "id", "type") shouldBe "number"
                messages.getValue("subscribe-ws-shape-2").at("payload", "type") shouldBe "array"
                (messages.getValue("subscribe-ws-shape")["examples"] as List<*>).size shouldBe 2
            }
        }

        given("Enum 목록 필드") {
            then("배열 그대로 두고 items 를 enum 으로 만든다") {
                val schema = JsonSchemaBuilder.fromFields(
                    listOf(field("levels", "Array", enumValues = listOf("LOW", "HIGH"))),
                )

                schema.at("properties", "levels", "type") shouldBe "array"
                schema.at("properties", "levels", "enum") shouldBe null
                schema.at("properties", "levels", "items", "type") shouldBe "string"
                schema.at("properties", "levels", "items", "enum") shouldBe listOf("LOW", "HIGH")
            }
        }

        given("CLI 인자") {
            then("옵션과 여러 서버를 읽는다") {
                val options = AsyncApiCliOptions.parse(
                    arrayOf(
                        "--snippets", "s", "--output", "o.yaml", "--viewer", "v.html", "--title", "T", "--version", "2",
                        "--server", "stomp=stomp://localhost:8080/ws", "--server", "http=http://api.example.com",
                    ),
                )

                options.snippets shouldBe Path.of("s")
                options.viewer shouldBe Path.of("v.html")
                options.info.servers shouldBe listOf(
                    AsyncApiServer("stomp", "localhost:8080", "stomp", "/ws"),
                    AsyncApiServer("http", "api.example.com", "http", null),
                )
            }
        }

        given("빈 스니펫 디렉터리") {
            then("문서를 만들지 않고, 지난 결과도 지운다") {
                val dir = Files.createTempDirectory("asyncapi")

                Files.createDirectories(dir.resolve("out"))
                Files.writeString(dir.resolve("out/asyncapi.yaml"), "old")

                val count = AsyncApiGenerator.generate(
                    dir.resolve("none"),
                    dir.resolve("out/asyncapi.yaml"),
                    AsyncApiInfo("T", "1"),
                    dir.resolve("out/asyncapi.html"),
                )

                count shouldBe 0
                Files.exists(dir.resolve("out/asyncapi.yaml")) shouldBe false
                Files.exists(dir.resolve("out/asyncapi.html")) shouldBe false
            }
        }

        given("연결·구독 요청 헤더") {
            val header = mapOf(
                "name" to "Authorization",
                "description" to "토큰",
                "optional" to false,
                "example" to "Bearer ****",
            )

            val records = entries(
                mapOf(
                    "kind" to "message",
                    "protocol" to "STOMP",
                    "direction" to "SEND",
                    "address" to "/app/chat",
                    "headers" to emptyList<Any>(),
                    "fields" to emptyList<Any>(),
                ),
                mapOf(
                    "kind" to "message",
                    "protocol" to "STOMP",
                    "direction" to "CONNECT",
                    "address" to "/ws",
                    "headers" to listOf(header),
                ),
                mapOf(
                    "kind" to "event-stream",
                    "protocol" to "SSE",
                    "address" to "/stream",
                    "requestHeaders" to
                        listOf(
                            header.plus("name" to "Last-Event-ID")
                                .plus("optional" to true),
                        ),
                    "events" to emptyList<Any>(),
                ),
            )
            val doc = AsyncApiDocumentBuilder(AsyncApiInfo("T", "1")).build(records)

            then("STOMP CONNECT 헤더는 같은 프로토콜 채널의 ws 바인딩 헤더가 된다") {
                doc.at(
                    "channels",
                    "app-chat",
                    "bindings",
                    "ws",
                    "headers",
                    "properties",
                    "Authorization",
                    "examples",
                ) shouldBe
                    listOf("Bearer ****")
                doc.at("channels", "app-chat", "bindings", "ws", "headers", "required") shouldBe listOf("Authorization")
            }

            then("CONNECT 는 메시지·채널을 따로 만들지 않는다") {
                (doc.at("channels") as Map<*, *>).keys shouldBe setOf("app-chat", "stream")
            }

            then("SSE 구독 요청 헤더는 x-request-headers 와 설명으로 남는다") {
                doc.at("channels", "stream", "x-request-headers", "properties", "Last-Event-ID", "type") shouldBe
                    "string"
                doc.at("channels", "stream", "description") shouldBe
                    "Subscription request headers: Last-Event-ID (optional)"
                doc.at("channels", "app-chat", "description") shouldBe
                    "Headers sent when connecting (/ws): Authorization"
            }

            then("language 가 ko 면 채널 설명이 한국어다") {
                val korean = RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    AsyncApiDocumentBuilder(AsyncApiInfo("T", "1")).build(records)
                }

                korean.at("channels", "stream", "description") shouldBe "구독 요청 헤더: Last-Event-ID (선택)"
                korean.at("channels", "app-chat", "description") shouldBe "연결할 때 헤더를 보낸다 (/ws): Authorization"
            }
        }

        given("메타데이터 코덱") {
            val header = HeaderRecord("Authorization", "토큰", optional = false, example = "Bearer ****")
            val message = AsyncApiEntry(
                AsyncApiOrigin(tag = "채팅", summary = "보내기", operation = "chat-test/send"),
                MessageRecord(
                    key = "message-01",
                    name = null,
                    protocol = "STOMP",
                    direction = "SEND",
                    address = "/app/chat",
                    description = null,
                    headers = listOf(header),
                    payload = """{"a":1}""",
                    fields = listOf(field("a", "Number", enumValues = listOf("1"))),
                ),
            )

            val stream = AsyncApiEntry(
                AsyncApiOrigin(tag = null, summary = "구독", operation = "noti-test/subscribe"),
                EventStreamRecord(
                    protocol = "SSE",
                    address = "/stream",
                    requestHeaders = listOf(header),
                    events = listOf(EventRecord("connected", "연결", optional = true, payload = null, emptyList())),
                ),
            )

            then("디스크 형식의 키 이름과 순서를 유지한다") {
                AsyncApiRecordCodec.encode(message).keys.toList() shouldBe listOf(
                    "tag", "summary", "operation", "kind", "key", "name", "protocol", "direction", "address",
                    "description", "headers", "payload", "fields",
                )

                @Suppress("UNCHECKED_CAST")
                val encodedField = (AsyncApiRecordCodec.encode(message)["fields"] as List<Map<String, Any?>>).single()

                encodedField.keys.toList() shouldBe
                    listOf(
                        "path", "type", "description", "optional", "ignored", "constraints", "enum",
                        "schema", "constraintNote", "default", "example", "requiredByConstraint", "deprecated",
                        "masking",
                    )

                AsyncApiRecordCodec.encode(stream).keys.toList() shouldBe listOf(
                    "tag",
                    "summary",
                    "operation",
                    "kind",
                    "protocol",
                    "address",
                    "requestHeaders",
                    "events",
                )

                AsyncApiRecordCodec.encode(stream)["kind"] shouldBe "event-stream"
            }

            then("쓴 것을 그대로 읽는다") {
                AsyncApiRecordCodec.decode(AsyncApiRecordCodec.encode(message)) shouldBe message
                AsyncApiRecordCodec.decode(AsyncApiRecordCodec.encode(stream)) shouldBe stream
            }

            then("모르는 kind 는 읽지 않는다") {
                AsyncApiRecordCodec.decode(mapOf("kind" to "other")) shouldBe null
            }
        }
    })
