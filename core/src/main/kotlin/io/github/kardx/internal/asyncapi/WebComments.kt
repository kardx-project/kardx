package io.github.kardx.internal.asyncapi

/**
 * 뷰어 페이지에 넣을 HTML·CSS·JS 에서 주석을 지운다. 리소스 원본의 (한국어) 주석은 그대로 두고, 만든 페이지에만 빠진다.
 *
 * 문자열·템플릿 리터럴·정규식 리터럴 안의 `//` 나 블록 주석 시작 기호는 주석이 아니므로 그대로 둔다 (`'https://…'`, `/^#\//`).
 * 정규식인지 나눗셈인지는 앞의 토큰으로 가린다. 판단이 틀려 정규식으로 본 경우에도 원문을 그대로 옮길 뿐이라 코드가 깨지지 않는다.
 * 주석만 있던 줄은 지우고, 줄 끝 주석 앞의 공백도 지운다.
 */
internal object WebComments {
    private val HTML_COMMENT = Regex("<!--[\\s\\S]*?-->")

    fun stripHtml(source: String): String =
        dropEmptiedLines(source, HTML_COMMENT.replace(source) { newlinesOf(it.value) })

    fun stripCss(source: String): String = dropEmptiedLines(source, CommentScanner(source, javaScript = false).strip())

    fun stripJs(source: String): String = dropEmptiedLines(source, CommentScanner(source, javaScript = true).strip())

    /** 지운 주석이 품고 있던 줄바꿈. 남겨서 원본과 줄 번호를 맞춘다. */
    internal fun newlinesOf(text: String): String = "\n".repeat(text.count { it == '\n' })

    /** 주석을 지워 빈 줄이 된 줄은 뺀다. 원래 빈 줄은 둔다. */
    private fun dropEmptiedLines(original: String, stripped: String): String {
        val originalLines = original.lines()

        return stripped.lines()
            .mapIndexedNotNull { index, line ->
                val wasBlank = originalLines.getOrNull(index)
                    ?.isBlank() ?: true

                line.trimEnd()
                    .takeIf { it.isNotEmpty() || wasBlank }
            }
            .joinToString("\n")
    }
}
