package io.github.kardx.internal.asyncapi

/**
 * CSS(`/* */`) 또는 JavaScript(`//`, `/* */`) 소스를 한 번 훑어 주석만 뺀 글을 만든다. [WebComments] 가 쓴다.
 *
 * 주석 안의 줄바꿈은 남겨 원본과 줄 번호가 같다. 문자열은 따옴표 종류대로, JS 템플릿 리터럴은 `${…}` 안까지 따라간다.
 */
internal class CommentScanner(private val source: String, private val javaScript: Boolean) {
    private val out = StringBuilder(source.length)
    private var index = 0

    fun strip(): String {
        code(untilClosingBrace = false)

        return out.toString()
    }

    /** 코드 영역. [untilClosingBrace] 면 템플릿 리터럴의 `${` 에 짝이 맞는 `}` 까지 옮기고 돌아간다. */
    private fun code(untilClosingBrace: Boolean) {
        var depth = 0

        while (index < source.length) {
            val char = source[index]

            if (untilClosingBrace && char == '}' && depth == 0) {
                copy(1)

                return
            }

            depth += when (char) {
                '{' -> 1
                '}' -> -1
                else -> 0
            }

            token(char)
        }
    }

    private fun token(char: Char) {
        val next = source.getOrNull(index + 1)

        when {
            char == '/' && next == '*' -> blockComment()
            javaScript && char == '/' && next == '/' -> lineComment()
            char == '"' || char == '\'' -> quoted(char)
            javaScript && char == '`' -> template()
            javaScript && char == '/' && regexMayStart() -> regex()
            else -> copy(1)
        }
    }

    private fun blockComment() {
        val end = source.indexOf("*/", index + 2)
            .let { if (it < 0) source.length else it + 2 }

        val newlines = WebComments.newlinesOf(source.substring(index, end))
        val following = source.getOrNull(end)

        out.setLength(out.trimEndSpaces())
        out.append(newlines)

        // `a/* … */b` 가 `ab` 로 붙지 않게 한 칸을 남긴다
        if (newlines.isEmpty() && following != null && !following.isWhitespace()) {
            out.append(' ')
        }

        index = end
    }

    private fun lineComment() {
        val end = source.indexOf('\n', index)
            .let { if (it < 0) source.length else it }

        out.setLength(out.trimEndSpaces())
        index = end
    }

    /** `'…'`, `"…"`. 이스케이프를 건너뛰고, 닫히지 않으면 줄 끝에서 멈춘다. */
    private fun quoted(quote: Char) {
        copy(1)

        while (index < source.length && source[index] != '\n') {
            val char = source[index]

            if (char == '\\') {
                copy(2)
            } else {
                copy(1)

                if (char == quote) return
            }
        }
    }

    private fun template() {
        copy(1)

        while (index < source.length) {
            val char = source[index]

            when {
                char == '\\' -> copy(2)

                char == '`' -> {
                    copy(1)

                    return
                }

                char == '$' && source.getOrNull(index + 1) == '{' -> {
                    copy(2)
                    code(untilClosingBrace = true)
                }

                else -> copy(1)
            }
        }
    }

    /**
     * 정규식 리터럴을 그대로 옮긴다. 문자 클래스(`[/]`) 안의 `/` 와 이스케이프는 끝이 아니다.
     * 같은 줄에서 닫히지 않으면 정규식이 아니었으므로 `/` 하나만 옮긴다.
     */
    private fun regex() {
        val end = regexEnd()

        if (end < 0) {
            copy(1)

            return
        }

        copy(end - index)
    }

    /** 닫는 `/` 다음 위치. 줄이 끝나면 -1. */
    private fun regexEnd(): Int {
        var position = index + 1
        var inClass = false

        while (position < source.length) {
            when (source[position]) {
                '\n' -> return -1
                '\\' -> position++
                '[' -> inClass = true
                ']' -> inClass = false
                '/' -> if (!inClass) return position + 1
            }

            position++
        }

        return -1
    }

    /** 앞 토큰이 값(이름·숫자·`)`·`]`)이면 나눗셈이다. 단, `return /x/` 같은 키워드 뒤는 정규식이다. */
    private fun regexMayStart(): Boolean {
        val end = out.trimEndSpaces()
        val last = out.getOrNull(end - 1) ?: return true

        return when {
            last == ')' || last == ']' -> false
            !last.isIdentifierPart() -> true
            else -> out.substring(identifierStart(end), end) in REGEX_KEYWORDS
        }
    }

    private fun identifierStart(end: Int): Int {
        var start = end

        while (start > 0 && out[start - 1].isIdentifierPart()) start--

        return start
    }

    private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'

    private fun copy(count: Int) {
        val end = minOf(index + count, source.length)

        out.append(source, index, end)
        index = end
    }

    /** 끝의 공백·탭을 뺀 길이. 줄바꿈은 남긴다. */
    private fun StringBuilder.trimEndSpaces(): Int {
        var end = length

        while (end > 0 && (this[end - 1] == ' ' || this[end - 1] == '\t')) end--

        return end
    }

    private companion object {
        val REGEX_KEYWORDS =
            setOf("return", "typeof", "case", "do", "else", "in", "of", "void", "delete", "throw", "new")
    }
}
