package io.github.kardx.internal.websocket

import io.github.kardx.dsl.MessageDocumentationBuilder
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.RestDocsHeader

/** [MessageDocumentationBuilder] 입력을 모은 값 객체. */
internal data class MessageSpec(
    val description: String? = null,
    val name: String? = null,
    val headerValues: Map<String, String> = emptyMap(),
    val headers: List<RestDocsHeader> = emptyList(),
    val fields: List<RestDocsField> = emptyList(),
    val relaxed: Boolean = false,
)

/** 블록에서 모은 항목을 [MessageSpec] 으로 만든다. */
internal fun MessageDocumentationBuilder.toSpec(): MessageSpec = MessageSpec(
    description = description,
    name = name,
    headerValues = headerValues.toMap(),
    headers = headers.toList(),
    fields = fields.toList(),
    relaxed = relaxed,
)
