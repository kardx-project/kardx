package io.github.kardx.internal.asyncapi

/**
 * Map / List / 스칼라 트리를 YAML 로 쓴다. 문자열은 항상 JSON 형식의 큰따옴표 문자열로 써서
 * 특수 문자·여러 줄·한글이 안전하게 들어간다 (YAML 1.2 는 JSON 문자열 문법을 그대로 받는다).
 */
internal object YamlWriter {
    private val PLAIN_KEY = Regex("""^[A-Za-z_$][A-Za-z0-9_$.\-]*$""")

    /** YAML 1.1 파서(SnakeYAML 등)가 불리언·null 로 읽는 단어. 키로 쓰면 따옴표로 감싼다. */
    private val RESERVED_KEYS = setOf("null", "~", "true", "false", "yes", "no", "on", "off", "y", "n")

    fun write(value: Any?): String = buildString { node(value, 0, this) }
        .trimEnd() + "\n"

    private fun node(value: Any?, indent: Int, out: StringBuilder) {
        when (value) {
            is Map<*, *> -> if (value.isEmpty()) out.append("{}\n") else map(value, indent, out)

            is List<*> -> if (value.isEmpty()) out.append("[]\n") else list(value, indent, out)

            else ->
                out.append(scalar(value))
                    .append('\n')
        }
    }

    private fun map(map: Map<*, *>, indent: Int, out: StringBuilder) {
        map.entries.forEachIndexed { index, (key, value) ->
            if (index > 0 || out.isEmpty() || out.last() == '\n') {
                out.append(" ".repeat(indent))
            }

            out.append(key(key.toString()))
                .append(':')
            appendChild(value, indent, out)
        }
    }

    private fun list(list: List<*>, indent: Int, out: StringBuilder) {
        list.forEachIndexed { index, value ->
            if (index > 0 || out.isEmpty() || out.last() == '\n') {
                out.append(" ".repeat(indent))
            }

            out.append("- ")

            when {
                value is Map<*, *> && value.isNotEmpty() -> map(value, indent + 2, out)

                value is List<*> && value.isNotEmpty() -> {
                    out.append('\n')
                    list(value, indent + 2, out)
                }

                else -> node(value, indent + 2, out)
            }
        }
    }

    private fun appendChild(value: Any?, indent: Int, out: StringBuilder) {
        when {
            value is Map<*, *> && value.isNotEmpty() -> {
                out.append('\n')
                map(value, indent + 2, out)
            }

            value is List<*> && value.isNotEmpty() -> {
                out.append('\n')
                list(value, indent + 2, out)
            }

            else -> {
                out.append(' ')
                node(value, indent + 2, out)
            }
        }
    }

    private fun key(key: String): String = if (PLAIN_KEY.matches(key) && key.lowercase() !in RESERVED_KEYS) {
        key
    } else {
        scalar(key)
    }

    private fun scalar(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Int, is Long, is Short, is Byte -> value.toString()
        is Number -> if (value.toDouble().isFinite()) value.toString() else quote(value.toString())
        else -> quote(value.toString())
    }

    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { c ->
            when (c) {
                '"' -> append("\\\"")

                '\\' -> append("\\\\")

                '\n' -> append("\\n")

                '\r' -> append("\\r")

                '\t' -> append("\\t")

                // 제어 문자와 유니코드 줄바꿈(NEL, LS, PS)은 YAML 에서 줄 끝으로 읽힐 수 있어 이스케이프한다
                '\u0085', '\u2028', '\u2029' -> append("\\u%04x".format(c.code))

                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }

        append('"')
    }
}
