package io.github.kardx.asyncapi

import io.github.kardx.dsl.MessageProtocol
import io.github.kardx.internal.asyncapi.AsyncApiEntry
import io.github.kardx.internal.asyncapi.AsyncApiIds.identifier
import io.github.kardx.internal.asyncapi.AsyncApiIds.protocolMatches
import io.github.kardx.internal.asyncapi.AsyncApiIds.slug
import io.github.kardx.internal.asyncapi.AsyncApiIds.uniqueId
import io.github.kardx.internal.asyncapi.AsyncApiMessageSchemas.addExample
import io.github.kardx.internal.asyncapi.AsyncApiMessageSchemas.headerSchema
import io.github.kardx.internal.asyncapi.AsyncApiMessageSchemas.message
import io.github.kardx.internal.asyncapi.AsyncApiOrigin
import io.github.kardx.internal.asyncapi.EventRecord
import io.github.kardx.internal.asyncapi.EventStreamRecord
import io.github.kardx.internal.asyncapi.HeaderRecord
import io.github.kardx.internal.asyncapi.MessageRecord
import io.github.kardx.internal.i18n.DocsText
import io.github.kardx.internal.schema.SchemaField

/**
 * 메타데이터 목록을 AsyncAPI 3.0 문서 트리(Map)로 바꾼다. 채널과 operation 을 조립하고,
 * 메시지·스키마는 [io.github.kardx.internal.asyncapi.AsyncApiMessageSchemas],
 * ID 규칙은 [io.github.kardx.internal.asyncapi.AsyncApiIds] 에 맡긴다.
 */
internal class AsyncApiDocumentBuilder(private val info: AsyncApiInfo) {
    private val channels = linkedMapOf<String, MutableMap<String, Any?>>()

    /** 채널 id → 그 채널의 `messages` 맵 ([channels] 안의 같은 인스턴스) */
    private val channelMessages = mutableMapOf<String, MutableMap<String, Any?>>()

    /** 채널 id → 메시지 프로토콜 (STOMP, WebSocket, SSE). 연결 헤더를 같은 프로토콜 채널에 붙일 때 쓴다 */
    private val channelProtocols = linkedMapOf<String, String>()
    private val operations = linkedMapOf<String, MutableMap<String, Any?>>()

    /** operation id → 그 operation 의 `messages` 참조 목록 */
    private val operationMessageRefs = mutableMapOf<String, List<Map<String, String>>>()
    private val messages = linkedMapOf<String, MutableMap<String, Any?>>()
    private val tags = linkedSetOf<String>()

    /**
     * (주소, 방향, 이름, 페이로드 구조) → 메시지 ID. 같은 메시지를 여러 테스트에서 문서화하면 예시만 더한다.
     * 이름이 같아도 페이로드 구조가 다르면 다른 메시지(`이름-2`)로 둔다. 하나로 합치면 한쪽 스키마가 사라지고 예시와 어긋난다
     */
    private val messageKeys = mutableMapOf<MessageKey, String>()

    /** (SSE 주소, 이벤트 이름) → 메시지 ID */
    private val eventKeys = mutableMapOf<Pair<String, String>, String>()

    /** SSE 주소 → operation ID. 한 스트림은 operation 하나다 */
    private val streamOperations = mutableMapOf<String, String>()

    fun build(entries: List<AsyncApiEntry>): Map<String, Any?> {
        val (connects, others) = entries.partition { (it.record as? MessageRecord)?.isConnect == true }

        others.forEach { entry ->
            when (val record = entry.record) {
                is MessageRecord -> addMessage(entry.origin, record)
                is EventStreamRecord -> addEventStream(entry.origin, record)
            }
        }

        connects.forEach { addConnectHeaders(it.record as MessageRecord) }

        return linkedMapOf<String, Any?>(
            "asyncapi" to "3.0.0",
            "info" to linkedMapOf<String, Any?>("title" to info.title, "version" to info.version).apply {
                info.description?.let { put(DESCRIPTION, it) }

                if (tags.isNotEmpty()) {
                    put("tags", tags.map { mapOf("name" to it) })
                }
            },
            "defaultContentType" to "application/json",
        ).apply {
            if (info.servers.isNotEmpty()) {
                put("servers", info.servers.associate { it.name to server(it) })
            }

            put("channels", channels)
            put("operations", operations)
            // 인증 방식이 없으면 지금까지와 같은 `messages` 하나만 둔다 (출력이 바뀌지 않는다)
            put("components", AsyncApiSecuritySchemes.components(info.securitySchemes) + mapOf(MESSAGES to messages))
        }
    }

    private fun addMessage(origin: AsyncApiOrigin, record: MessageRecord) {
        val address = record.address
        val channelId = channel(address, record.protocol)
        val direction = record.direction
        val base = record.name?.let(::identifier) ?: slug("${direction.lowercase()}-$address")
        val key = MessageKey(address, direction, base, structureOf(record.fields))

        messageKeys[key]?.let { existing ->
            addExample(messages.getValue(existing), record.headers, record.payload)

            return
        }

        val messageId = uniqueId(messages.keys, base)
        messageKeys[key] = messageId
        messages[messageId] = message(
            name = messageId,
            // 방향은 operation 의 action(서버 기준 send/receive)이 나타낸다. 제목에 클라이언트 기준 방향을 쓰면 서로 반대로 보인다.
            title = messageId,
            description = record.description,
            headers = record.headers,
            fields = record.fields,
            payload = record.payload,
        )

        addToChannel(channelId, messageId)

        val action = if (direction == MessageRecord.SEND) "receive" else "send"
        addOperation("$action-$messageId", action, channelId, listOf(messageId), origin, record.description)
    }

    /** 페이로드 구조: 필드마다 경로·타입·optional·값 목록. 설명은 보지 않는다. */
    private fun structureOf(fields: List<SchemaField>): Set<String> =
        fields.mapTo(sortedSetOf()) { "${it.path}|${it.type}|${it.optional}|${it.ignored}|${it.enumValues}" }

    private data class MessageKey(
        val address: String,
        val direction: String,
        val name: String,
        val structure: Set<String>,
    )

    /**
     * 연결 헤더(STOMP CONNECT, WebSocket 핸드셰이크)를 같은 프로토콜 채널의 WebSocket 바인딩 헤더로 붙인다.
     * AsyncAPI ws 채널 바인딩의 `headers` 는 핸드셰이크 헤더를 표현하는 자리다.
     */
    private fun addConnectHeaders(record: MessageRecord) {
        val headers = record.headers.ifEmpty { return }

        channelProtocols.filterValues { it.equals(record.protocol, ignoreCase = true) }
            .keys
            .forEach { id ->
                val channel = channels.getValue(id)

                // 엔드포인트가 여럿이면 처음 기록한 것을 둔다 (채널은 목적지라 엔드포인트와 짝지을 수 없다)
                if (channel.containsKey("bindings")) return@forEach

                val names = headers.joinToString(", ") { it.name }

                channel["bindings"] =
                    mapOf("ws" to mapOf("headers" to headerSchema(headers), "bindingVersion" to "0.1.0"))
                channel[DESCRIPTION] = DocsText.CONNECT_HEADERS.text(record.address, names)
            }
    }

    private fun addEventStream(origin: AsyncApiOrigin, record: EventStreamRecord) {
        val address = record.address
        val channelId = channel(address, MessageProtocol.SSE.displayName)

        if (record.requestHeaders.isNotEmpty()) {
            addRequestHeaders(channels.getValue(channelId), record.requestHeaders)
        }

        val ids = record.events.map { eventMessage(channelId, address, it) }
        val operationId = streamOperations[address]

        if (operationId == null) {
            streamOperations[address] = addOperation("send-${slug(address)}", "send", channelId, ids, origin, null)
        } else {
            addOperationMessages(operationId, channelId, ids)
        }
    }

    /** AsyncAPI HTTP 바인딩에는 요청 헤더 자리가 없어 확장 필드와 설명으로 남긴다. */
    private fun addRequestHeaders(channel: MutableMap<String, Any?>, headers: List<HeaderRecord>) {
        val optional = DocsText.OPTIONAL.text()
        val names = headers.joinToString(", ") { it.name + if (it.optional) " ($optional)" else "" }

        channel["x-request-headers"] = headerSchema(headers)
        channel[DESCRIPTION] = DocsText.SUBSCRIBE_HEADERS.text(names)
    }

    /**
     * SSE 이벤트의 메시지 id. 같은 엔드포인트를 여러 테스트에서 문서화하면 메시지를 새로 만들지 않고 예시만 더한다.
     */
    private fun eventMessage(channelId: String, address: String, event: EventRecord): String {
        val existing = eventKeys[address to event.name]

        if (existing != null) {
            addExample(messages.getValue(existing), emptyList(), event.payload)

            return existing
        }

        val id = uniqueId(messages.keys, identifier(event.name))

        eventKeys[address to event.name] = id
        messages[id] = message(
            name = event.name,
            title = event.name,
            description = event.description,
            headers = emptyList(),
            fields = event.fields,
            payload = event.payload,
            optional = event.optional,
        )

        addToChannel(channelId, id)

        return id
    }

    private fun channel(address: String, protocol: String): String {
        val existing = channels.entries.firstOrNull { it.value["address"] == address }

        if (existing != null) return existing.key

        val id = uniqueId(channels.keys, slug(address).ifEmpty { "root" })
        val channelMessageMap = linkedMapOf<String, Any?>()

        channelProtocols[id] = protocol
        channelMessages[id] = channelMessageMap
        channels[id] = linkedMapOf<String, Any?>("address" to address, MESSAGES to channelMessageMap).apply {
            putAll(channelDetails(address, protocol))
        }

        return id
    }

    /** 채널의 프로토콜 표시, 그 프로토콜 서버, 주소 파라미터. 없는 항목은 넣지 않는다. */
    private fun channelDetails(address: String, protocol: String): Map<String, Any?> = buildMap {
        // 뷰어가 Try it out 방식(STOMP/WebSocket/SSE)을 고를 때 쓴다. 서버가 없거나 매칭되지 않아도 알 수 있다
        if (protocol.isNotEmpty()) {
            put("x-protocol", protocol)
        }

        val servers = info.servers.filter { protocolMatches(protocol, it.protocol) }

        if (servers.isNotEmpty() && servers.size < info.servers.size) {
            put("servers", servers.map { mapOf(REF to "#/servers/${it.name}") })
        }

        if ('{' in address) {
            val parameters = PARAMETER.findAll(address)
                .associate { it.groupValues[1] to emptyMap<String, Any>() }

            put("parameters", parameters)
        }
    }

    private fun addToChannel(channelId: String, messageId: String) {
        channelMessages.getValue(channelId)[messageId] = mapOf(REF to "#/components/messages/$messageId")
    }

    private fun addOperation(
        id: String,
        action: String,
        channelId: String,
        messageIds: List<String>,
        origin: AsyncApiOrigin,
        description: String?,
    ): String {
        origin.tag?.let { tags += it }

        val operationId = uniqueId(operations.keys, id)
        val refs = messageRefs(channelId, messageIds)
        operationMessageRefs[operationId] = refs
        operations[operationId] = linkedMapOf<String, Any?>(
            "action" to action,
            "channel" to mapOf(REF to "#/channels/$channelId"),
        ).apply {
            origin.summary?.let { put("summary", it) }
            description?.takeIf { it.isNotBlank() }
                ?.let { put(DESCRIPTION, it) }
            origin.tag?.let { put("tags", listOf(mapOf("name" to it))) }
            put(MESSAGES, refs)
        }

        return operationId
    }

    /** 이미 있는 operation 에 새 메시지만 더한다. */
    private fun addOperationMessages(operationId: String, channelId: String, messageIds: List<String>) {
        val refs = (operationMessageRefs.getValue(operationId) + messageRefs(channelId, messageIds))
            .distinct()
        operationMessageRefs[operationId] = refs
        operations.getValue(operationId)[MESSAGES] = refs
    }

    private fun messageRefs(channelId: String, messageIds: List<String>): List<Map<String, String>> =
        messageIds.map { mapOf(REF to "#/channels/$channelId/messages/$it") }

    private fun server(server: AsyncApiServer): Map<String, Any?> = linkedMapOf<String, Any?>(
        "host" to server.host,
        "protocol" to server.protocol,
    ).apply {
        server.pathname?.let { put("pathname", it) }
        server.description?.let { put(DESCRIPTION, it) }

        if (info.securitySchemes.isNotEmpty()) {
            put("security", info.securitySchemes.map { AsyncApiSecuritySchemes.reference(it) })
        }
    }

    private companion object {
        val PARAMETER = Regex("""\{([^}]+)}""")
        const val DESCRIPTION = "description"
        const val MESSAGES = "messages"
        const val REF = "\$ref"
    }
}
