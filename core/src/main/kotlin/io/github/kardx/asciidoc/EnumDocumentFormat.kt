package io.github.kardx.asciidoc

/**
 * Enum 코드 문서의 포맷. 기본은 [AsciiDocEnumDocumentFormat] 이다.
 *
 * 각 함수는 블록 하나를 돌려주고, 블록 사이는 빈 줄 하나로 이어진다. 빈 문자열을 돌려준 블록은 생략된다.
 * 다른 포맷이 필요하면 구현해서 JUnit 은 [io.github.kardx.junit.EnumDocumentationExtension.format] 을 재정의하고,
 * Kotest 는 `io.github.kardx.kotest.EnumDocumentationExtension` 의 생성자 인자 `format` 으로 넘긴다.
 */
public interface EnumDocumentFormat {
    /** 출력 파일 확장자 (예: `adoc`) */
    public val fileExtension: String

    /** 문서 머리. [packageName] 은 스캔한 패키지. */
    public fun header(title: String, packageName: String): String

    /**
     * index 링크를 함께 받는 문서 머리. [indexLink] 는 이 문서 위치에서 본 index 경로(기본 출력 `code/` 면 `../index.html`)이고,
     * index 링크를 끈 설정(`index-link: none`)이거나 문서가 AsciiDoc 출력 디렉터리 밖에 있으면 null 이다.
     * 기본 구현은 링크 없이 [header] 를 부른다. [AsciiDocEnumDocumentFormat] 은 왼쪽 목차 제목 자리에 링크를 넣는다.
     */
    public fun header(title: String, packageName: String, indexLink: String?): String = header(title, packageName)

    /** Enum 하나의 표 머리. [title] 은 `@EnumCode` 의 `value`, [className] 은 Enum 의 단순 이름. */
    public fun tableHeader(title: String, className: String): String

    /**
     * 상수 한 줄. [code] 는 [io.github.kardx.annotation.EnumCode.codeProperty] 값(기본은 JSON 값),
     * [description] 은 [io.github.kardx.annotation.EnumCode.descriptionProperty] 값(기본은 `description` 등)이다.
     */
    public fun tableRow(code: String, description: String): String

    /** 표 끝. */
    public fun tableFooter(): String

    /** 문서 끝. */
    public fun footer(): String = ""
}

/**
 * AsciiDoc Enum 코드 문서.
 *
 * ```asciidoc
 * [[사용자_상태]]
 * == 사용자 상태
 * enum : UserStatus
 *
 * [cols="3,7"]
 * |===
 * | Code | Description
 *
 * | ACTIVE
 * | 활성
 * |===
 * ```
 *
 * 머리글이나 열 비율을 바꾸려면 상속해서 [tableHeader] 를 재정의한다.
 * 다른 문서에서 표로 링크하려면 `link:./code/enum-code.html#사용자_상태[사용자 상태]` 처럼 제목의 공백을 `_` 로 바꾼 앵커를 쓴다.
 */
public open class AsciiDocEnumDocumentFormat : EnumDocumentFormat {
    override val fileExtension: String = "adoc"

    /** 제목과 테스트 문서와 같은 기본 헤더 속성([DocumentGenerator.Config]). */
    override fun header(title: String, packageName: String): String = header(title, packageName, null)

    /**
     * [header] 에 더해, 테스트 문서([DocumentGenerator.tocTitleAttribute])처럼 왼쪽 목차 제목 자리에 index
     * 링크를 넣는다. 글자는 [io.github.kardx.config.DocumentSettings.indexLinkText] (없으면 언어의 기본 문구).
     */
    override fun header(title: String, packageName: String, indexLink: String?): String {
        val config = DocumentGenerator.Config()
        val tocTitle = DocumentGenerator.tocTitleAttribute(
            config,
            indexLink,
            DocumentGenerator.indexLinkText(),
        )

        return (listOf("= $title") + config.attributes() + listOfNotNull(tocTitle)).joinToString("\n")
    }

    override fun tableHeader(title: String, className: String): String = """
        [[${anchorOf(title)}]]
        == $title
        enum : $className

        [cols="3,7"]
        |===
        | Code | Description
    """.trimIndent()

    override fun tableRow(code: String, description: String): String = """
        | ${code.cell()}
        | ${description.cell()}
    """.trimIndent()

    /** 표 칸 안의 `|` 는 칸 구분자가 되므로 이스케이프한다. */
    private fun String.cell(): String = replace("|", "\\|")

    override fun tableFooter(): String = "|==="

    /** 표 제목으로 만드는 AsciiDoc 앵커. */
    protected open fun anchorOf(title: String): String = title.replace(" ", "_")
}
