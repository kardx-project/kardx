package io.github.kardx.lifecycle

import io.github.kardx.asciidoc.DocumentGenerator
import io.github.kardx.internal.asciidoc.SnippetFileListExtractor
import io.github.kardx.internal.output.DocumentRecord
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.output.writeTextAtomically
import java.io.File

/**
 * [DocumentOutline] 의 섹션마다 스니펫을 모아 AsciiDoc 문서 하나를 만든다. 테스트 프레임워크와 무관하다.
 *
 * - 문서 제목: [DocumentOutline.title] + [DocumentOutline.description]
 * - 섹션: [DocumentOutline.sections] 순서. 스니펫이 없는 섹션(문서화하지 않은 테스트, 지운 실패 테스트)은 건너뛴다
 * - 섹션 앵커: 스니펫 디렉터리에서 문서 클래스 부분을 뗀 값 (`get-user`, 파라미터 테스트 `get-user-2`)
 * - 제목 아래와 끝에 index 로 돌아가는 링크 ([DocumentGenerator.navigationPart])
 * - 파일: `<document-class>.adoc`. 쓸 섹션이 하나도 없으면 파일을 만들지 않는다
 * - 문서 기록: 문서를 쓰면 빌드 도구가 읽을 기록(index 항목, 헤더 가리기 여부)도 남긴다 ([DocumentRecord])
 */
@OptIn(RestDocsExtensionApi::class)
internal class AsciiDocumentWriter(
    private val documentGenerator: DocumentGenerator,
    private val snippetFileListExtractor: SnippetFileListExtractor,
    private val directory: File,
) {
    /** @return 문서를 썼으면 true */
    fun write(outline: DocumentOutline): Boolean {
        val documentClass = outline.documentClass
        val sections = outline.sections.mapNotNull { section(documentClass, it) }

        if (sections.isEmpty()) return false

        val title = documentGenerator.titlePart(outline.title, outline.description)
        val parts = listOf(documentGenerator.startPart(), title) + sections +
            listOf(documentGenerator.navigationPart(), documentGenerator.endPart())

        File(directory, "${SnippetPaths.camelCaseToDash(documentClass.simpleName)}.adoc")
            .writeTextAtomically(parts.joinBlocks(postfix = "\n"))
        DocumentRecord.of(documentClass)
            .write(directory)

        return true
    }

    private fun section(documentClass: Class<*>, section: DocumentSection): String? {
        val directory = SnippetPaths.relativeDirectory(documentClass, section.snippetName)
        val snippets = snippetFileListExtractor.extract(directory)
            .ifEmpty { return null }
        val methodId = directory.substringAfter('/')
            .replace('/', '-')
        val sectionTitle = documentGenerator.sectionTitlePart(methodId, section.title, section.description)

        return (listOf(sectionTitle) + snippets.map { documentGenerator.sectionRowPart(it, directory) }).joinBlocks()
    }

    /** 비어 있지 않은 블록을 빈 줄 하나로 잇는다. */
    private fun List<String>.joinBlocks(postfix: String = ""): String = filter { it.isNotEmpty() }
        .joinToString(separator = "\n\n", postfix = postfix)
}
