package io.github.kardx.internal.asyncapi

import io.github.kardx.internal.schema.SchemaField

/**
 * [AsyncApiEntry] 와 `*.asyncapi.json` 의 JSON 트리(Map) 사이 변환.
 *
 * 키 이름과 순서가 디스크 형식이다. Gradle 플러그인과 이전 버전이 남긴 산출물이 같은 형식을 쓰므로 바꾸지 않는다.
 * JSON 직렬화는 Jackson 2/3 을 리플렉션으로 부르는 [io.github.kardx.internal.jackson.serializeJson] 이 하므로,
 * 여기서는 Jackson 이 그대로 다룰 수 있는 Map·List·기본 타입만 만든다.
 */
internal object AsyncApiRecordCodec {
    private const val KIND_MESSAGE = "message"
    private const val KIND_EVENT_STREAM = "event-stream"

    // 여러 레코드가 함께 쓰는 키
    private const val PROTOCOL = "protocol"
    private const val ADDRESS = "address"
    private const val DESCRIPTION = "description"
    private const val PAYLOAD = "payload"
    private const val FIELDS = "fields"
    private const val OPTIONAL = "optional"

    // 필드의 스키마 정보 (없는 예전 메타데이터는 null)
    private const val SCHEMA = "schema"
    private const val CONSTRAINT_NOTE = "constraintNote"
    private const val DEFAULT = "default"
    private const val EXAMPLE = "example"
    private const val REQUIRED_BY_CONSTRAINT = "requiredByConstraint"
    private const val DEPRECATED = "deprecated"
    private const val MASKING = "masking"

    fun encode(entry: AsyncApiEntry): Map<String, Any?> = linkedMapOf<String, Any?>(
        "tag" to entry.origin.tag,
        "summary" to entry.origin.summary,
        "operation" to entry.origin.operation,
    ) + encode(entry.record)

    /** 알 수 없는 `kind` 면 null (문서에 넣지 않는다). */
    fun decode(json: Map<*, *>): AsyncApiEntry? {
        val record = when (json["kind"]) {
            KIND_MESSAGE -> decodeMessage(json)
            KIND_EVENT_STREAM -> decodeEventStream(json)
            else -> null
        } ?: return null

        val origin = AsyncApiOrigin(
            tag = json["tag"]?.toString(),
            summary = json["summary"]?.toString(),
            operation = json["operation"]?.toString(),
        )

        return AsyncApiEntry(origin, record)
    }

    private fun encode(record: AsyncApiRecord): Map<String, Any?> = when (record) {
        is MessageRecord -> linkedMapOf(
            "kind" to KIND_MESSAGE,
            "key" to record.key,
            "name" to record.name,
            PROTOCOL to record.protocol,
            "direction" to record.direction,
            ADDRESS to record.address,
            DESCRIPTION to record.description,
            "headers" to record.headers.map(::encodeHeader),
            PAYLOAD to record.payload,
            FIELDS to record.fields.map(::encodeField),
        )

        is EventStreamRecord -> linkedMapOf(
            "kind" to KIND_EVENT_STREAM,
            PROTOCOL to record.protocol,
            ADDRESS to record.address,
            "requestHeaders" to record.requestHeaders.map(::encodeHeader),
            "events" to record.events.map(::encodeEvent),
        )
    }

    private fun encodeEvent(event: EventRecord): Map<String, Any?> = linkedMapOf(
        "name" to event.name,
        DESCRIPTION to event.description,
        OPTIONAL to event.optional,
        PAYLOAD to event.payload,
        FIELDS to event.fields.map(::encodeField),
    )

    private fun encodeHeader(header: HeaderRecord): Map<String, Any?> = linkedMapOf(
        "name" to header.name,
        DESCRIPTION to header.description,
        OPTIONAL to header.optional,
        EXAMPLE to header.example,
    )

    private fun encodeField(field: SchemaField): Map<String, Any?> = linkedMapOf(
        "path" to field.path,
        "type" to field.type,
        DESCRIPTION to field.description,
        OPTIONAL to field.optional,
        "ignored" to field.ignored,
        "constraints" to field.constraints,
        "enum" to field.enumValues,
        SCHEMA to field.schema,
        CONSTRAINT_NOTE to field.constraintNote,
        DEFAULT to field.defaultValue,
        EXAMPLE to field.example,
        REQUIRED_BY_CONSTRAINT to field.requiredByConstraint,
        DEPRECATED to field.deprecated,
        MASKING to field.masking,
    )

    private fun decodeMessage(json: Map<*, *>) = MessageRecord(
        key = json["key"]?.toString(),
        name = json["name"]?.toString(),
        protocol = json[PROTOCOL]?.toString().orEmpty(),
        direction = json["direction"].toString(),
        address = json[ADDRESS].toString(),
        description = json[DESCRIPTION]?.toString(),
        headers = objects(json["headers"]).map(::decodeHeader),
        payload = json[PAYLOAD]?.toString(),
        fields = objects(json[FIELDS]).map(::decodeField),
    )

    private fun decodeEventStream(json: Map<*, *>) = EventStreamRecord(
        protocol = json[PROTOCOL]?.toString().orEmpty(),
        address = json[ADDRESS].toString(),
        requestHeaders = objects(json["requestHeaders"]).map(::decodeHeader),
        events = objects(json["events"]).map(::decodeEvent),
    )

    private fun decodeEvent(json: Map<*, *>) = EventRecord(
        name = json["name"].toString(),
        description = json[DESCRIPTION]?.toString(),
        optional = json[OPTIONAL] == true,
        payload = json[PAYLOAD]?.toString(),
        fields = objects(json[FIELDS]).map(::decodeField),
    )

    private fun decodeHeader(json: Map<*, *>) = HeaderRecord(
        name = json["name"].toString(),
        description = json[DESCRIPTION]?.toString(),
        optional = json[OPTIONAL] == true,
        example = json[EXAMPLE]?.toString(),
    )

    private fun decodeField(json: Map<*, *>) = SchemaField(
        path = json["path"].toString(),
        type = json["type"]?.toString(),
        description = json[DESCRIPTION]?.toString(),
        optional = json[OPTIONAL] == true,
        ignored = json["ignored"] == true,
        constraints = json["constraints"]?.toString(),
        enumValues = (json["enum"] as? List<*>)?.map { it.toString() },
        schema = (json[SCHEMA] as? Map<*, *>)?.mapKeys { it.key.toString() },
        constraintNote = json[CONSTRAINT_NOTE]?.toString(),
        defaultValue = json[DEFAULT]?.toString(),
        example = json[EXAMPLE]?.toString(),
        requiredByConstraint = json[REQUIRED_BY_CONSTRAINT] == true,
        deprecated = json[DEPRECATED] == true,
        masking = json[MASKING]?.toString(),
    )

    /** JSON 배열 안의 객체들. 배열이 아니면 빈 목록. */
    private fun objects(value: Any?): List<Map<*, *>> = (value as? List<*>).orEmpty()
        .filterIsInstance<Map<*, *>>()
}
