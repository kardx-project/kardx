package io.github.kardx.kotest

/**
 * 리프 테스트의 스니펫 자리 이름 규칙. [RestDocsExtension] 의 생성자 인자 `snippetName` 으로 넘긴다. 돌려준 이름도 경로에 쓸 수
 * 있는 모양으로 다듬는다 ([TestNames.slug] 를 `-` 사이마다 적용, 비면 `test`).
 *
 * ```kotlin
 * RestDocsExtension(snippetName = SnippetNameProvider { TestNames.slug(it.names.last()) })
 * ```
 */
public fun interface SnippetNameProvider {
    /** [test] 의 스니펫 자리 이름. */
    public fun snippetName(test: TestPath): String

    /** 기본 규칙. */
    public companion object {
        /** [TestNames.snippetName]: 이름마다 [TestNames.slug] 를 적용해 `-` 로 잇는다. */
        public val DEFAULT: SnippetNameProvider = SnippetNameProvider { TestNames.snippetName(it.names) }
    }
}
