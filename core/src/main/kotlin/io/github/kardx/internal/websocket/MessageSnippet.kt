package io.github.kardx.internal.websocket

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.RestDocsHeader
import io.github.kardx.internal.http.HeaderMasking
import io.github.kardx.internal.jackson.prettyJsonOrNull
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.SnippetException
import org.springframework.restdocs.snippet.TemplatedSnippet

/** `message-NN` 스니펫. 템플릿은 `message.snippet` 이다. */
internal class MessageSnippet(
    snippetName: String,
    private val protocol: String,
    private val message: DocumentedMessage,
    private val headerDescriptors: List<RestDocsHeader>,
) : TemplatedSnippet(snippetName, TEMPLATE_NAME, null) {

    override fun createModel(operation: Operation): MutableMap<String, Any> {
        val actual = message.headers.mapKeys { it.key.lowercase() }
        val missing = headerDescriptors.filter {
            !it.descriptor.isOptional && it.descriptor.name.lowercase() !in actual
        }

        if (missing.isNotEmpty()) {
            val names = missing.map { it.descriptor.name }

            throw SnippetException("Headers with the following names were not found in the message: $names")
        }

        val rows = headerDescriptors.map { header ->
            val name = header.descriptor.name

            hashMapOf<String, Any>(
                "name" to name,
                "description" to (header.descriptor.description?.toString() ?: ""),
                "optional" to header.descriptor.isOptional,
            ).apply { actual[name.lowercase()]?.let { put("example", mask(name, it)) } }
        }

        return hashMapOf(
            "direction" to message.direction.name,
            "destination" to message.destination,
            "protocol" to protocol,
            "hasHeaders" to rows.isNotEmpty(),
            "headers" to rows,
        ).apply {
            message.description?.takeIf { it.isNotBlank() }
                ?.let { put("description", it) }
            message.payload?.let { put("payload", pretty(it)) }
        }
    }

    private fun mask(name: String, value: String): String = HeaderMasking.mask(name, value)

    /** JSON 이면 들여쓰기한 문자열, 아니거나 매퍼를 만들 수 없으면(Jackson 없음) 그대로. */
    private fun pretty(payload: String): String = runCatching { RestDocsConfiguration.objectMapper() }
        .getOrNull()
        ?.let { prettyJsonOrNull(payload, it) }
        ?: payload

    private companion object {
        const val TEMPLATE_NAME = "message"
    }
}
