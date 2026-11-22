package io.github.kardx.internal.schema

import io.github.kardx.internal.i18n.DocsText

/**
 * REST Docs 필드 경로(`a.b`, `items[].x`, `[].id`)와 타입으로 JSON Schema 를 만든다.
 * 무시(ignored) 필드는 빼고, optional 이 아니면(또는 `@NotNull` 등으로 값이 꼭 있어야 하면) required 에 넣는다.
 * 추출기가 남긴 스키마 조각(`integer`, `format`, `minLength` …)을 합치고([SchemaHints]), 키워드로 옮기지 못한 제약은
 * 설명 뒤에 `Constraints: ...` (한국어 설정이면 `제약: ...`) 으로 붙인다. 기본값·예시는 스키마 타입에 맞춰 넣는다. 마스킹되는 필드는 설명 끝에 `Masked: ...`,
 * `x-masked: true`(규칙이 있으면 `x-masked-rule`) 를 넣는다 ([SchemaMarks]).
 */
internal object JsonSchemaBuilder {

    fun fromFields(fields: List<SchemaField>, dialect: SchemaDialect = SchemaDialect.JSON_SCHEMA): Map<String, Any?> {
        val root = Node()

        fields.filter { !it.ignored }
            .forEach { field ->
                val segments = split(field.path)
                var node = root

                segments.forEachIndexed { index, segment ->
                    node = node.descend(segment)

                    if (index == segments.lastIndex) {
                        node.describe(field)
                    }
                }
            }

        return root.toSchema(dialect)
    }

    /** `data.options[].name` → [Segment(data,0), Segment(options,1), Segment(name,0)], `[].id` → [Segment("",1), Segment(id,0)] */
    private fun split(path: String): List<Segment> = path.split('.')
        .filter { it.isNotEmpty() }
        .map { part ->
            val depth = ARRAY_MARKER.findAll(part)
                .count()

            Segment(part.replace("[]", ""), depth)
        }

    private data class Segment(val name: String, val arrayDepth: Int)

    private class Node {
        var type: String? = null
        var enumValues: List<String>? = null
        var description: String? = null
        var optional = true
        var field: SchemaField? = null
        val properties = linkedMapOf<String, Node>()
        var items: Node? = null

        /** 이름 있는 조각은 속성으로, `[]` 마다 배열 items 로 내려간다. */
        fun descend(segment: Segment): Node {
            var node = if (segment.name.isEmpty()) this else properties.getOrPut(segment.name) { Node() }

            repeat(segment.arrayDepth) {
                node.type = ARRAY
                node = node.elementNode()
            }

            return node
        }

        fun describe(field: SchemaField) {
            val jsonType = jsonType(field.type)

            if (type != ARRAY || jsonType == ARRAY) {
                type = jsonType ?: type
            }

            val constraints = field.note?.takeIf { it.isNotBlank() }
                ?.replace("\n\n", ", ")
            description = listOfNotNull(
                field.description?.takeIf { it.isNotBlank() },
                constraints?.let { "${DocsText.CONSTRAINTS.text()}: $it" },
                field.masking?.let(SchemaMarks::maskedNote),
            ).joinToString("\n\n")
                .ifEmpty { null }
            optional = field.optional && !field.requiredByConstraint
            this.field = field

            // 배열의 값 목록은 원소의 것이다 (List<Enum>)
            if (jsonType == ARRAY) {
                field.enumValues?.let { values ->
                    val element = elementNode()

                    element.type = STRING
                    element.enumValues = values
                }
            } else {
                enumValues = field.enumValues
            }
        }

        private fun elementNode(): Node = items ?: Node().also { items = it }

        fun toSchema(dialect: SchemaDialect): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
            val resolved = type ?: if (properties.isNotEmpty()) "object" else null
            resolved?.let { put("type", it) }
            description?.let { put("description", it) }
            enumValues?.let { put("enum", it) }

            if (properties.isNotEmpty()) {
                put("properties", properties.mapValues { it.value.toSchema(dialect) })

                val required = properties.filterValues { !it.optional }
                    .keys
                    .toList()

                if (required.isNotEmpty()) {
                    put("required", required)
                }
            }

            items?.let { put("items", it.toSchema(dialect)) }
            field?.let { addFieldSchema(this, it, dialect) }
        }

        private fun addFieldSchema(schema: MutableMap<String, Any?>, field: SchemaField, dialect: SchemaDialect) {
            field.schema?.let { SchemaHints.merge(schema, it, dialect) }
            field.defaultValue?.let { schema["default"] = SchemaHints.coerce(it, schema) }

            if (field.deprecated) {
                schema["deprecated"] = true
            }

            field.masking?.let { SchemaMarks.putMasked(schema, it) }

            if (dialect == SchemaDialect.OPENAPI_30) {
                addNullable(schema, field)
            }

            field.example?.let {
                val example = SchemaHints.coerce(it, schema)

                if (dialect == SchemaDialect.OPENAPI_30) {
                    schema["example"] = example
                } else {
                    schema["examples"] = listOf(example)
                }
            }
        }

        /**
         * OpenAPI 3.0 `nullable`. Gradle 플러그인이 epages 스키마에 넣는 것과 같게 해서, 같은 타입으로 만든 스키마가 같은 모양이 되게
         * 한다 (선언한 오류 응답이 이미 있는 `ErrorResponse` 를 가리키도록).
         */
        private fun addNullable(schema: MutableMap<String, Any?>, field: SchemaField) {
            if (field.nullable && !field.requiredByConstraint) {
                schema["nullable"] = true
            }

            @Suppress("UNCHECKED_CAST")
            val items = schema["items"] as? MutableMap<String, Any?>

            if (field.nullableItems && items != null) {
                items["nullable"] = true
            }
        }

        private fun jsonType(restDocsType: String?): String? = when (restDocsType) {
            "String" -> STRING
            "Number" -> "number"
            "Boolean" -> "boolean"
            "Object" -> "object"
            "Array" -> ARRAY
            "Null" -> "null"
            "enum" -> STRING
            else -> null
        }
    }
}

private const val ARRAY = "array"
private val ARRAY_MARKER = Regex("""\[]""")
private const val STRING = "string"
