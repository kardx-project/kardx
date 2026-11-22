package io.github.kardx.kotest

/**
 * 리프 테스트의 섹션 제목 규칙. [RestDocsExtension] 의 생성자 인자 `sectionTitle` 로 넘긴다.
 * 섹션 제목은 OpenAPI·AsyncAPI 요약으로도 쓰인다.
 *
 * ```kotlin
 * RestDocsExtension(sectionTitle = SectionTitleProvider { it.names.last() })
 * ```
 */
public fun interface SectionTitleProvider {
    /** [test] 의 섹션 제목. */
    public fun sectionTitle(test: TestPath): String

    /** 기본 규칙. */
    public companion object {
        /** [TestNames.sectionTitle]: 이름을 ` - ` 로 잇는다. */
        public val DEFAULT: SectionTitleProvider = SectionTitleProvider { TestNames.sectionTitle(it.names) }
    }
}
