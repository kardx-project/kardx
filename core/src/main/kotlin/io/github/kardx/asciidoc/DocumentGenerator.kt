package io.github.kardx.asciidoc
import io.github.kardx.config.DocumentSettings
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.config.SnippetInclude
import io.github.kardx.internal.i18n.DocsText
import java.util.Objects

/**
 * AsciiDoc 문서의 각 파트(제목, 섹션, 스니펫 include)를 만든다.
 * 테스트 클래스 하나가 문서 하나, 테스트 메서드 하나가 섹션 하나다.
 *
 * 각 함수는 끝 개행 없는 블록을 반환하고, `AsciiDocumentWriter` 가
 * 블록 사이를 빈 줄로 잇는다. 빈 문자열을 반환한 블록은 생략된다.
 *
 * 상속해서 [config], [commonIncludeFiles] 등을 바꿀 수 있다.
 *
 * @param snippetsIncludePrefix 스니펫 include 경로의 접두사. 기본값은 [DocumentSettings.includePrefix] (`{snippets}`)
 */
public open class DocumentGenerator(
    /**
     * 스니펫 include 경로의 접두사 (`{snippets}`). [sectionRowPart] 가 `include::<접두사>/<스니펫 디렉터리>/<파일>[]` 을 만들 때 쓴다.
     * 하위 클래스가 include 줄을 직접 만들 때 같은 접두사를 쓰도록 공개한다.
     *
     * ```kotlin
     * override fun sectionRowPart(snippet: SnippetInclude, snippetDirectory: String) =
     *     "include::$snippetsIncludePrefix/$snippetDirectory/${snippet.fileName}[leveloffset=+1]"
     * ```
     */
    protected val snippetsIncludePrefix: String = RestDocsConfiguration.current.document.includePrefix,
) {
    /** 문서 맨 앞(제목보다 먼저)에 넣을 블록. 기본값은 빈 문자열(생략). 문서 헤더 앞의 주석이나 전처리 지시문에 쓴다. */
    public open fun startPart(): String = ""

    /** 문서 맨 끝(끝 쪽 index 링크 뒤)에 넣을 블록. 기본값은 빈 문자열(생략). */
    public open fun endPart(): String = ""

    /** 문서 제목, AsciiDoc 속성(목차 링크 [tocTitleAttribute] 포함), index 링크([navigationPart]), 공통 include 블록. */
    public open fun titlePart(title: String, description: String?): String {
        val attributes = config().attributes() + listOfNotNull(tocTitleAttribute().ifEmpty { null })
        val header = (listOf("= ${title.withDescription(description)}") + attributes)
            .joinToString("\n")
        val includes = commonIncludeFiles()
            .joinToString("\n") { "include::$it[]" }

        return listOf(header, navigationPart(), includes)
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }

    /**
     * 문서 제목 바로 아래와 문서 끝에 넣는 index 링크. 대상은 [DocumentSettings.indexLink], 글자는 [DocumentSettings.indexLinkText]
     * (없으면 [RestDocsSettings.language] 의 기본 문구).
     * `indexLink` 가 null 이면 빈 문자열(생략). 글자는 목차 링크([tocTitleAttribute])와 같이 다듬는다: 줄바꿈은 공백으로,
     * 링크를 닫는 `]` 와 HTML 특수 문자는 문자 참조로 바꾼다.
     */
    public open fun navigationPart(): String {
        val settings = RestDocsConfiguration.current
        val target = settings.document.indexLink?.takeIf { it.isNotBlank() } ?: return ""

        return """
            [.restdocs-nav]
            link:$target[&#8592; ${indexLinkText(settings).linkText()}]
        """.trimIndent()
    }

    /**
     * 왼쪽 목차(`:toc: left`) 제목 자리에 넣는 index 링크. 스크롤해도 보이는 목차에서 index 로 갈 수 있다.
     * 목차 제목 `Table of Contents` 는 링크 아래에 그대로 남는다. 대상과 글자는 [navigationPart] 와 같은
     * [DocumentSettings.indexLink]·[DocumentSettings.indexLinkText] 를 쓰므로, `indexLink` 를 끄면(`index-link: none`)
     * 함께 빠진다. 목차를 쓰지 않는 설정([Config.toc] 이 빈 값)에서도 빠진다.
     * 목차 링크만 빼려면 이 함수를 재정의해 빈 문자열을 돌려준다.
     *
     * 문서 헤더의 `:toc-title: pass:[...]` 한 줄이다. Asciidoctor HTML 변환기는 이 값을 `<div id="toctitle">` 에 그대로 넣는다.
     */
    public open fun tocTitleAttribute(): String {
        val settings = RestDocsConfiguration.current

        return tocTitleAttribute(config(), settings.document.indexLink, indexLinkText(settings)).orEmpty()
    }

    /** 테스트 메서드 하나에 대응하는 섹션 제목 블록. 앵커는 [asciidocAnchor] 로 다듬은 [methodId]. */
    public open fun sectionTitlePart(methodId: String, title: String, description: String?): String = """
        [[${asciidocAnchor(methodId)}]]
        == icon:link[] ${title.withDescription(description)}
    """.trimIndent()

    /**
     * 섹션 안의 스니펫 include 블록. 소제목이 없는 스니펫(api-summary)은 include 만 쓴다.
     * [snippetDirectory] 는 스니펫 출력 디렉터리 기준 경로다 (`user-controller-test/get-user`,
     * 파라미터 테스트는 `user-controller-test/get-user/2`).
     */
    public open fun sectionRowPart(snippet: SnippetInclude, snippetDirectory: String): String {
        val include = "include::$snippetsIncludePrefix/$snippetDirectory/${snippet.fileName}[]"

        if (snippet.title.isEmpty()) return include

        return """
            .${snippet.title}
            $include
        """.trimIndent()
    }

    /** AsciiDoc 문서 헤더 속성. */
    public open fun config(): Config = Config()

    /** 모든 문서 상단에 include 할 파일 목록. 기본값은 [DocumentSettings.commonIncludeFiles]. */
    public open fun commonIncludeFiles(): List<String> = RestDocsConfiguration.current.document.commonIncludeFiles

    private fun String.withDescription(description: String?): String =
        if (description.isNullOrBlank()) this else "$this $description"

    /** 생성기 밖에서도 쓰는 AsciiDoc 도우미 ([asciidocAnchor]). */

    public companion object {
        private val NOT_ANCHOR = Regex("""[^\p{L}\p{N}_\-.:]+""")

        /** 줄바꿈과 그 앞뒤 공백 */
        private val LINE_BREAK = Regex("""\s*\R\s*""")

        /** Asciidoctor 가 목차에 붙이는 기본 제목. 링크 아래에 그대로 둔다. */
        private const val DEFAULT_TOC_TITLE = "Table of Contents"

        /**
         * 목차 제목 자리에 index 링크를 넣는 `:toc-title:` 줄. 목차를 쓰지 않거나 [target] 이 없으면 null.
         * Enum 코드 문서처럼 index 와 다른 디렉터리에 있는 문서는 상대 경로를 맞춘 [target] 을 넘긴다.
         */
        internal fun tocTitleAttribute(config: Config, target: String?, text: String): String? {
            if (config.toc.isBlank() || target.isNullOrBlank()) return null

            val link = """<a class="restdocs-nav" href="${target.escapeHtml()}">&#8592; ${text.linkText()}</a>"""

            return ":toc-title: pass:[$link<br>$DEFAULT_TOC_TITLE]"
        }

        /** index 링크 글자. 정한 값이 없으면 [RestDocsSettings.language] 의 기본 문구 (`Back to index`, `메인으로 돌아가기`). */
        internal fun indexLinkText(settings: RestDocsSettings = RestDocsConfiguration.current): String =
            settings.document.indexLinkText ?: DocsText.INDEX_LINK_TEXT.text(language = settings.language)

        /** index 링크 글자를 다듬는다. 한 줄이어야 하므로(문서 헤더 속성, 링크 매크로) 줄바꿈은 공백으로 바꾸고 [escapeHtml] 한다. */
        private fun String.linkText(): String = replace(LINE_BREAK, " ")
            .escapeHtml()

        /**
         * HTML 속성·본문에 그대로 넣을 수 있게 바꾼다. `]` 는 pass·link 매크로를 닫으므로 엔티티로 바꾼다.
         * Asciidoctor 는 문자 참조(`&#93;`, `&amp;`)를 그대로 HTML 에 넘긴다.
         */
        private fun String.escapeHtml(): String = replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("]", "&#93;")

        /**
         * 텍스트를 AsciiDoc 앵커 ID 로 다듬는다. 공백·괄호 같은 문자는 `-` 로 바꾸고, 글자나 `_` 로 시작하지 않으면 `_` 를 붙인다.
         * 백틱 메서드 이름(`` fun `사용자 조회 성공`() ``)은 REST Docs 스니펫 경로에는 그대로 쓰이지만 앵커로는 쓸 수 없다.
         */
        public fun asciidocAnchor(text: String): String {
            val cleaned = text.replace(NOT_ANCHOR, "-")
                .trim('-')

            return if (cleaned.isEmpty() ||
                !(cleaned.first().isLetter() || cleaned.first() == '_')
            ) {
                "_$cleaned"
            } else {
                cleaned
            }
        }
    }

    /**
     * AsciiDoc 문서 헤더 속성값. 생성 문서(`build/generated-asciidoc`)의 `= 제목` 바로 아래 `:이름: 값` 줄이 된다.
     * 빈 값이나 null 인 속성은 출력하지 않는다. yml 키는 없고, [config] 를 재정의해 바꾼다.
     *
     * @property doctype `:doctype:`. 기본값 `book`
     * @property icons `:icons:`. 기본값 `font` (섹션 제목의 `icon:link[]` 가 Font Awesome 아이콘으로 보인다)
     * @property sourceHighlighter `:source-highlighter:`. 코드 블록 강조기. 기본값 `highlightjs`
     * @property toc `:toc:`. 목차 위치. 기본값 `left`
     * @property tocLevels `:toclevels:`. 목차에 넣을 제목 깊이. 기본값 3, null 이면 줄을 쓰지 않는다
     * @property isSectLinks true 이면 `:sectlinks:` 를 써서 섹션 제목을 자기 앵커 링크로 만든다. 기본값 true
     */
    public class Config(
        public val doctype: String = "book",
        public val icons: String = "font",
        public val sourceHighlighter: String = "highlightjs",
        public val toc: String = "left",
        public val tocLevels: Int? = 3,
        public val isSectLinks: Boolean = true,
    ) {
        /** 헤더에 쓸 `:이름: 값` 줄 목록. 속성 선언 순서대로이며, 빈 값·null·false 인 속성은 빠진다. */
        public fun attributes(): List<String> = listOfNotNull(
            doctype.attribute("doctype"),
            icons.attribute("icons"),
            sourceHighlighter.attribute("source-highlighter"),
            toc.attribute("toc"),
            tocLevels?.let { ":toclevels: $it" },
            ":sectlinks:".takeIf { isSectLinks },
        )

        override fun equals(other: Any?): Boolean = other is Config &&
            doctype == other.doctype &&
            icons == other.icons &&
            sourceHighlighter == other.sourceHighlighter &&
            toc == other.toc &&
            tocLevels == other.tocLevels &&
            isSectLinks == other.isSectLinks

        override fun hashCode(): Int = Objects.hash(doctype, icons, sourceHighlighter, toc, tocLevels, isSectLinks)

        override fun toString(): String =
            "Config(doctype=$doctype, icons=$icons, sourceHighlighter=$sourceHighlighter, toc=$toc, " +
                "tocLevels=$tocLevels, isSectLinks=$isSectLinks)"

        private fun String.attribute(name: String): String? = takeIf { it.isNotBlank() }?.let { ":$name: $it" }
    }
}
