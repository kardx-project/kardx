package io.github.kardx.internal.documentation

import io.github.kardx.dsl.CONSTRAINTS_ATTRIBUTE
import io.github.kardx.dsl.DEFAULT_VALUE_ATTRIBUTE
import io.github.kardx.dsl.DEPRECATED_ATTRIBUTE
import io.github.kardx.dsl.DEPRECATION_NOTE_ATTRIBUTE
import io.github.kardx.dsl.EXAMPLE_ATTRIBUTE
import io.github.kardx.dsl.MASKED_ATTRIBUTE
import io.github.kardx.dsl.MASKING_RULE_ATTRIBUTE
import io.github.kardx.dsl.REQUIRED_BY_CONSTRAINT_ATTRIBUTE
import org.springframework.restdocs.snippet.AbstractDescriptor

/**
 * 디스크립터 목록을 글자 표로 만든다. 한글·한자·가나는 두 칸으로 세어 열을 맞춘다.
 * AsciiDoc 표와 같은 판정(ignored 는 빼고, `@NotNull` 이 붙은 optional 은 Required `true`)을 쓴다.
 */
internal object DescriptorTable {
    private const val WIDE = 2

    /** 터미널에서 두 칸을 차지하는 문자 */
    private val WIDE_SCRIPTS = setOf(
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
    )

    /** 머리줄·구분줄·행으로 된 Markdown 모양 표. [rows] 의 각 행은 [headers] 와 칸 수가 같다. */
    fun render(headers: List<String>, rows: List<List<String>>): String {
        val widths = headers.indices.map { column ->
            (rows.map { it[column] } + headers[column]).maxOf(::width)
        }

        fun line(cells: List<String>) = cells.mapIndexed { column, cell -> pad(cell, widths[column]) }
            .joinToString(" | ", "| ", " |")

        val separator = widths.joinToString("-|-", "|-", "-|") { "-".repeat(it) }

        return (listOf(line(headers), separator) + rows.map(::line)).joinToString("\n")
    }

    fun required(descriptor: AbstractDescriptor<*>, optional: Boolean): String =
        (!optional || descriptor.attributes[REQUIRED_BY_CONSTRAINT_ATTRIBUTE] == true).toString()

    /** 설명 칸. `@Deprecated`·`@Masked` 표시, 기본값·예시를 한 줄로 붙인다. */
    fun description(descriptor: AbstractDescriptor<*>): String {
        val attributes = descriptor.attributes

        return listOfNotNull(
            mark(attributes, DEPRECATED_ATTRIBUTE, DEPRECATION_NOTE_ATTRIBUTE, "Deprecated"),
            mark(attributes, MASKED_ATTRIBUTE, MASKING_RULE_ATTRIBUTE, "Masked"),
            descriptor.description?.toString(),
            attributes[DEFAULT_VALUE_ATTRIBUTE]?.let { "(Default: $it)" },
            attributes[EXAMPLE_ATTRIBUTE]?.let { "(Example: $it)" },
        ).joinToString(" ")
            .let(::cell)
    }

    /** `[Deprecated: 이유]`·`[Masked]` 같은 표시. [flag] 속성이 없으면 null. */
    private fun mark(attributes: Map<String, Any>, flag: String, note: String, label: String): String? = when {
        attributes[note] != null -> "[$label: ${attributes[note]}]"
        attributes[flag] == true -> "[$label]"
        else -> null
    }

    fun constraints(descriptor: AbstractDescriptor<*>): String =
        cell(descriptor.attributes[CONSTRAINTS_ATTRIBUTE]?.toString().orEmpty())

    /** 표 칸에 넣을 수 있게: 줄바꿈은 공백, `|` 는 `\|`. */
    fun cell(text: String): String = text.replace(Regex("\\s*\\R\\s*"), " ")
        .replace("|", "\\|")
        .trim()

    private fun pad(text: String, size: Int): String = text + " ".repeat(size - width(text))

    private fun width(text: String): Int = text.codePoints()
        .map { if (isWide(it)) WIDE else 1 }
        .sum()

    /** 한글·한자·가나 (터미널에서 두 칸) */
    private fun isWide(codePoint: Int): Boolean = Character.UnicodeScript.of(codePoint) in WIDE_SCRIPTS
}
