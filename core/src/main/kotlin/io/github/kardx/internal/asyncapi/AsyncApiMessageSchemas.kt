package io.github.kardx.internal.asyncapi

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.i18n.DocsText
import io.github.kardx.internal.jackson.deserializeJson
import io.github.kardx.internal.schema.JsonSchemaBuilder
import io.github.kardx.internal.schema.SchemaField

/** AsyncAPI 메시지(`components.messages.*`)와 헤더 JSON Schema, 메시지 예시를 만든다. */
internal object AsyncApiMessageSchemas {
    private const val EXAMPLES = "examples"

    /** 메시지 하나. [payload] 가 있으면 첫 예시가 된다. */
    fun message(
        name: String,
        title: String?,
        description: String?,
        headers: List<HeaderRecord>,
        fields: List<SchemaField>,
        payload: String?,
        optional: Boolean = false,
    ): MutableMap<String, Any?> = linkedMapOf<String, Any?>("name" to name).apply {
        title?.let { put("title", it) }

        val text = listOfNotNull(
            description?.takeIf { it.isNotBlank() },
            DocsText.OPTIONAL_EVENT.text().takeIf { optional },
        )

        if (text.isNotEmpty()) {
            put("description", text.joinToString("\n\n"))
        }

        put("contentType", "application/json")

        if (headers.isNotEmpty()) {
            put("headers", headerSchema(headers))
        }

        if (fields.isNotEmpty()) {
            put("payload", JsonSchemaBuilder.fromFields(fields))
        }

        addExample(this, headers, payload)
    }

    /** 실제 헤더·페이로드를 메시지 예시로 더한다. 같은 예시는 한 번만. */
    fun addExample(message: MutableMap<String, Any?>, headers: List<HeaderRecord>, payload: String?) {
        val example = linkedMapOf<String, Any?>()

        headers.filter { it.example != null }
            .takeIf { it.isNotEmpty() }
            ?.let { h ->
                example["headers"] = h.associate { it.name to it.example }
            }

        payload?.let { example["payload"] = parseJson(it) }

        if (example.isEmpty()) return

        val examples = (message[EXAMPLES] as? List<*>).orEmpty()

        if (example !in examples) {
            message[EXAMPLES] = examples + example
        }
    }

    /** 헤더 목록을 `type: object` JSON Schema 로. optional 이 아니면 required 에 넣는다. */
    fun headerSchema(headers: List<HeaderRecord>): Map<String, Any?> = linkedMapOf<String, Any?>(
        "type" to "object",
        "properties" to headers.associate { header ->
            header.name to linkedMapOf<String, Any?>("type" to "string").apply {
                header.description?.let { put("description", it) }
                header.example?.let { put(EXAMPLES, listOf(it)) }
            }
        },
    ).apply {
        val required = headers.filter { !it.optional }
            .map { it.name }

        if (required.isNotEmpty()) {
            put("required", required)
        }
    }

    /** JSON 이면 파싱한 트리, 아니면 문자열 그대로. */
    private fun parseJson(text: String): Any? = runCatching {
        deserializeJson<Any?>(RestDocsConfiguration.objectMapper(), text, Any::class.java)
    }.getOrDefault(text)
}
