package io.github.kardx.config

/**
 * 자동 생성 AsciiDoc 문서의 모양 (include 할 스니펫, 순서, 링크, 공통 include).
 * [RestDocsSettings.document] 로 읽고 `document { }` 블록으로 바꾼다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     document { commonIncludeFiles = listOf("landing.adoc") }
 * }
 * ```
 */
@RestDocsSettingsDsl
public class DocumentSettings internal constructor() {
    /**
     * AsciiDoc 의 스니펫 include 경로 접두사. 기본값 `{snippets}` 은 Asciidoctor 의 REST Docs 확장이 채운다.
     * 그 확장 없이 렌더링하면 스니펫 디렉터리 경로를 직접 준다. yml: `kardx.snippets.include-prefix`.
     */
    public var includePrefix: String = "{snippets}"

    /**
     * 모든 AsciiDoc 문서의 제목 아래에 include 할 파일 (AsciiDoc 디렉터리 기준). 기본값 없음.
     * 공통 스타일·속성·안내문을 넣을 때 쓴다. yml: `kardx.common-include-files` (쉼표 또는 YAML 목록).
     */
    public var commonIncludeFiles: List<String> = emptyList()
        set(value) {
            field = value.toList()
        }

    /**
     * 생성한 AsciiDoc 문서의 처음과 끝, 왼쪽 목차 제목 자리에 넣는 index("Back to index") 링크 대상. 기본값 `index.html`.
     * Enum 코드 문서(`code/enum-code`)는 목차에만 넣고 상대 경로를 맞춘다(`../index.html`).
     * index 문서 이름이 다르면 바꾸고, 링크를 넣지 않으려면 `null` (yml 은 `none`). yml: `kardx.index-link`.
     */
    public var indexLink: String? = "index.html"

    /**
     * [indexLink] 링크의 글자. `null` (기본값)이면 [RestDocsSettings.language] 의 기본 문구 (`Back to index`, 한국어는 `메인으로 돌아가기`).
     * 정한 값은 [RestDocsSettings.language] 와 상관없이 그대로 쓴다. yml: `kardx.index-link-text`.
     */
    public var indexLinkText: String? = null

    /**
     * 자동 생성 AsciiDoc 에 include 할 스니펫 종류. 기본값은 [SnippetFile.defaults] (HTTPie, 요청·응답 본문처럼 겹치는 것 제외).
     * 알려지지 않은 사용자 정의 스니펫은 항상 포함된다. 선언 순서대로 include 하며 이 집합의 순서와는 상관없다.
     * yml: `kardx.document-snippets` (상수 이름 `response-body` 또는 스니펫 파일 이름 `httpie-request`).
     *
     * ```kotlin
     * snippets = SnippetFile.defaults() + SnippetFile.RESPONSE_BODY
     * ```
     *
     * 사용자 종류를 [register] 로 등록한 뒤 이 값을 통째로 바꾸면 등록 때 더한 종류도 빠지므로, 바꾼 다음에 등록하거나 `+=` 로 더한다.
     */
    public var snippets: Set<SnippetFile> = SnippetFile.defaults()
        set(value) {
            field = value.toSet()
        }

    /**
     * 생성 문서의 섹션 순서 기본값. 테스트 클래스에 JUnit `@TestMethodOrder`(`OrderAnnotation`·`MethodName`·`DisplayName`)·
     * `@TestClassOrder` 가 있으면 그것을 따르고, 없을 때 이 순서를 쓴다. 기본값 [DocumentSectionOrder.METHOD_NAME].
     * yml: `kardx.document-section-order` (`method-name`, `display-name`).
     */
    public var sectionOrder: DocumentSectionOrder = DocumentSectionOrder.METHOD_NAME

    /**
     * 문서 안 스니펫 종류 순서. 기본값 [SnippetFile.BUILT_IN]. 사용자 종류는 [register] 로 끼운다.
     * 여기 없는 스니펫(등록하지 않은 사용자 스니펫)은 언제나 이 순서 뒤에 파일 이름 순서로 들어간다.
     */
    public var order: List<SnippetFile> = SnippetFile.BUILT_IN
        private set

    /**
     * 사용자 스니펫 종류 [kind] 를 [order] 에 끼운다. [after] 를 주면 그 뒤, [before] 를 주면 그 앞, 둘 다 없으면 맨 뒤다.
     * [SnippetFile.includedByDefault] 면 [snippets] 에도 더한다. 같은 종류를 다시 등록하면 자리를 옮긴다.
     *
     * ```kotlin
     * document { register(SnippetFile.custom("audit-log", "Audit Log"), after = SnippetFile.RESPONSE_FIELDS) }
     * ```
     */
    public fun register(kind: SnippetFile, after: SnippetFile? = null, before: SnippetFile? = null) {
        require(!kind.builtIn) { "${kind.name} is a built-in snippet file; it is already registered" }
        require(after == null || before == null) { "give either after or before, not both" }

        val rest = order.filterNot { it == kind }
        val anchor = after ?: before
        val index = when {
            anchor == null -> rest.size
            after != null -> rest.indexOf(after) + 1
            else -> rest.indexOf(anchor)
        }

        require(anchor == null || anchor in rest) { "${anchor?.name} is not in document.order" }

        order = rest.take(index) + kind + rest.drop(index)

        if (kind.includedByDefault) {
            snippets = snippets + kind
        }
    }

    /** 스니펫 파일 이름에 맞는 종류. 사용자 종류를 먼저 본다. 모르는 스니펫이면 null. */
    internal fun kindOf(fileName: String): SnippetFile? = order.firstOrNull { !it.builtIn && it.matches(fileName) }
        ?: order.firstOrNull { it.builtIn && it.matches(fileName) }

    /** [kind] 의 문서 안 순서. 모르는 종류면 [Int.MAX_VALUE]. */
    internal fun position(kind: SnippetFile?): Int = order.indexOf(kind)
        .takeIf { it >= 0 } ?: Int.MAX_VALUE

    internal fun copy(): DocumentSettings = DocumentSettings().also {
        it.includePrefix = includePrefix
        it.commonIncludeFiles = commonIncludeFiles
        it.indexLink = indexLink
        it.indexLinkText = indexLinkText
        it.snippets = snippets
        it.sectionOrder = sectionOrder
        it.order = order
    }
}
