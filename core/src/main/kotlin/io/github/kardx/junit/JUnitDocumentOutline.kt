package io.github.kardx.junit

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.asciidoc.SnippetFileListExtractor
import io.github.kardx.internal.output.DisplayNames
import io.github.kardx.internal.output.DocumentedTest
import io.github.kardx.internal.output.SnippetNames
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.output.SnippetTarget
import io.github.kardx.internal.output.TestMethodListExtractor
import io.github.kardx.internal.output.asciiDescription
import io.github.kardx.lifecycle.DocumentOutline
import io.github.kardx.lifecycle.DocumentSection
import io.github.kardx.lifecycle.RestDocsExtensionApi

/**
 * JUnit 테스트 클래스를 리플렉션으로 읽어 [DocumentOutline] 을 만든다.
 *
 * - 섹션: 테스트 메서드마다 하나. 순서는 [TestMethodListExtractor] (JUnit `@TestMethodOrder`·`@Order`, 없으면
 *   [io.github.kardx.config.DocumentSettings.sectionOrder]). 실패한 테스트(`excludedMethods`)는 뺀다
 * - 섹션 제목: 메서드 `@DisplayName`, 없으면 메서드 이름의 kebab-case. 섹션 설명은 메서드 `@AsciiDescription`
 * - `@Nested` 클래스의 테스트는 바깥 클래스 문서의 섹션이 되고, 제목 앞에 `@Nested` 클래스의 표시 이름이 붙는다
 * - `@ParameterizedTest` 는 스니펫 디렉터리에 남은 실행마다 섹션 하나다. 제목 뒤에 실행 표시 이름(`[1] id=1`)이 붙는다
 *
 * 섹션과 스니펫 디렉터리의 대응은 [SnippetNames] 규칙을 따른다.
 */
@OptIn(RestDocsExtensionApi::class)
internal class JUnitDocumentOutline(private val snippetFileListExtractor: SnippetFileListExtractor) {
    /**
     * @param excludedMethods 섹션을 만들지 않을 테스트의 메서드 자리 이름 ([SnippetTarget.methodName], 실패한 테스트)
     * @param invocationTitles 파라미터 테스트 실행의 메서드 자리 이름 → 실행 표시 이름 (`[1] id=1`)
     */
    fun of(
        clazz: Class<*>,
        excludedMethods: Set<String> = emptySet(),
        invocationTitles: Map<String, String> = emptyMap(),
    ): DocumentOutline {
        val order = RestDocsConfiguration.current.document.sectionOrder
        val sections = TestMethodListExtractor.extract(clazz, order).flatMap { test ->
            runs(clazz, test).filterNot { (methodName, _) -> methodName in excludedMethods }
                .map { (methodName, invocation) ->
                    section(clazz, test, methodName, invocation?.let { invocationTitles[methodName] ?: "[$it]" })
                }
        }

        return DocumentOutline(clazz, sections)
    }

    /** 테스트의 실행별 (메서드 자리 이름, 실행 번호). 파라미터 테스트가 아니면 하나이고 실행 번호는 null. */
    private fun runs(documentClass: Class<*>, test: DocumentedTest): List<Pair<String, Int?>> {
        if (!SnippetNames.isParameterized(test.method)) {
            return listOf(test.baseName to null)
        }

        val directory = SnippetPaths.relativeDirectory(documentClass, test.baseName)

        return snippetFileListExtractor.invocations(directory).map { SnippetTarget(documentClass, test.baseName, it) }
            .map { it.methodName to it.invocation }
    }

    private fun section(
        documentClass: Class<*>,
        test: DocumentedTest,
        methodName: String,
        invocation: String?,
    ): DocumentSection {
        val title = listOfNotNull(
            nestedTitles(documentClass, test.testClass),
            DisplayNames.of(test.method) ?: SnippetPaths.camelCaseToDash(test.method.name),
            invocation,
        ).joinToString(" ")

        return DocumentSection(methodName, title, test.method.asciiDescription())
    }

    /** `@Nested` 테스트의 제목 머리 (`성공 -`). 바깥에서 안쪽 순서로 클래스 표시 이름을 잇는다. 문서 클래스의 테스트는 null. */
    private fun nestedTitles(documentClass: Class<*>, testClass: Class<*>): String? =
        generateSequence(testClass) { it.enclosingClass }
            .takeWhile { it != documentClass }
            .toList()
            .asReversed()
            .joinToString(" ") { "${DisplayNames.titleOf(it)} -" }
            .ifEmpty { null }
}
