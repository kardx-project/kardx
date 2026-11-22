package io.github.kardx.internal.asciidoc

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.SnippetFile
import io.github.kardx.config.SnippetInclude
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * 테스트 메서드의 스니펫 디렉터리에서 문서에 include 할 `.adoc` 파일을 고른다.
 *
 * 알려진 종류(라이브러리 종류와 등록한 사용자 종류)는 [io.github.kardx.config.DocumentSettings.order] 순서로,
 * 등록하지 않은 스니펫은 그 뒤에 파일명 순서로 둔다.
 * WebSocket 메시지 스니펫은 메시지 순번별로 묶는다 (`message-01`, 그 필드, `message-02`, 그 필드 …).
 * 기본에서 빠진 종류는 [io.github.kardx.config.DocumentSettings.snippets] 에 넣어야 포함된다.
 * 파일 본문 표(`request-file`·`response-file`)가 들어가면 같은 본문의 `<binary: N bytes>` 원문(`request-body`·`response-body`)은 뺀다.
 */
internal class SnippetFileListExtractor(val directory: String) {

    /** 출력 디렉터리 기준 [snippetDirectory] (`user-controller-test/get-user`) 의 스니펫. */
    fun extract(snippetDirectory: String): List<SnippetInclude> {
        val dir = Paths.get(directory, snippetDirectory)

        if (!Files.isDirectory(dir)) return emptyList()

        val document = RestDocsConfiguration.current.document
        val enabled = document.snippets

        val includes = dir.listDirectoryEntries("*.adoc")
            .filter(Path::isRegularFile)
            .map { SnippetInclude(document.kindOf(it.name), it.name) }
            .filter { it.kind == null || it.kind in enabled }
        val replaced = includes.mapNotNull { REPLACED_BODIES[it.kind] }

        return includes.filterNot { it.kind in replaced }
            .sortedWith(
                compareBy<SnippetInclude> { it.messageGroup }
                    .thenBy { document.position(it.kind) }
                    .thenBy { it.fileName },
            )
    }

    /** 파라미터 테스트의 [snippetDirectory] 아래 실행 번호 디렉터리 (`get-user/1`, `get-user/2` …), 번호순. */
    fun invocations(snippetDirectory: String): List<Int> {
        val dir = Paths.get(directory, snippetDirectory)

        if (!Files.isDirectory(dir)) return emptyList()

        return dir.listDirectoryEntries()
            .filter { Files.isDirectory(it) }
            .mapNotNull { it.name.toIntOrNull() }
            .sorted()
    }

    private companion object {
        /** 파일 본문 표와 그 표가 대신하는 본문 원문 */
        val REPLACED_BODIES = mapOf(
            SnippetFile.REQUEST_FILE to SnippetFile.REQUEST_BODY,
            SnippetFile.RESPONSE_FILE to SnippetFile.RESPONSE_BODY,
        )
    }
}
