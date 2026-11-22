package io.github.kardx.lifecycle

import io.github.kardx.asciidoc.DocumentGeneratorContext
import io.github.kardx.asciidoc.DocumentGeneratorFactory
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.asciidoc.SnippetFileListExtractor
import io.github.kardx.internal.i18n.PluginLanguageWarning
import io.github.kardx.internal.output.SnippetPaths
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 테스트 프레임워크와 무관한 문서화 수명주기. JUnit 확장(`AutoRestDocumentationExtension`)과 Kotest 확장이 함께 쓴다.
 *
 * 테스트 하나는 다음 순서로 흐른다.
 * 1. [startTest]: 스니펫 자리를 정하고, 이번 JVM 에서 처음 쓰는 자리면 지난 스니펫을 지운다
 * 2. [DocumentationRun.attachToCurrentThread]: 테스트를 실행하는 스레드에 붙인다. 붙은 동안 한 `configure` 는 이 실행의 것이다
 * 3. [DocumentationRun.provider] 를 MockMvc·WebTestClient·`MessageDocumentation` 에 넘겨 스니펫을 만든다
 * 4. [DocumentationRun.captureSettings]: 문서를 만들 때 쓸 설정을 기억한다 (테스트 준비가 끝나고 본문 전에)
 * 5. [DocumentationRun.finish]: 실패한 테스트의 스니펫을 지우고 실패로 기록한다
 * 6. [DocumentationRun.close]: 2 의 동안 이 실행이 `configure` 로 더한 블록만 뺀다
 *
 * 문서 클래스(JUnit 테스트 클래스, Kotest 스펙)가 끝나면 [writeDocument] 로 `<document-class>.adoc` 을 쓴다.
 * 다른 패키지의 같은 이름 클래스가 같은 문서 이름을 쓰면 경고한다.
 *
 * 비운 스니펫 디렉터리, 실패한 테스트, 문서 클래스별 설정, 문서 이름 주인은 JVM 전역이다. 그래서 확장 인스턴스가 여럿이어도
 * (스펙 인스턴스마다 확장을 새로 만드는 Kotest 격리 모드 등) 같은 문서 클래스의 기록이 한곳에 모인다.
 *
 * ```kotlin
 * val lifecycle = DocumentationLifecycle()
 * val run = lifecycle.startTest(UserApiDocsSpec::class.java, "get-user", sectionTitle = "사용자 조회")
 * try {
 *     run.attachToCurrentThread().use {
 *         // 테스트 준비 뒤 run.captureSettings(), run.provider 로 문서화
 *     }
 *     run.finish(failed = false)
 * } finally {
 *     run.close()
 * }
 * lifecycle.writeDocument(UserApiDocsSpec::class.java, DocumentGeneratorFactory.DEFAULT) { DocumentOutline(...) }
 * ```
 */
@RestDocsExtensionApi
public class DocumentationLifecycle {
    /**
     * 스니펫 출력 디렉터리. 처음 읽을 때 [io.github.kardx.config.OutputSettings.snippetsDirectory] (없으면 Spring REST Docs 규칙: `build/` 또는 `target/generated-snippets`)에서 정하고
     * 이 인스턴스에서는 바꾸지 않는다.
     */
    public val snippetsDirectory: File by lazy { RestDocsConfiguration.snippetsDirectory() }

    /**
     * 테스트 실행 하나를 시작한다.
     *
     * @param documentClass 문서 하나로 모이는 클래스. 스니펫 디렉터리의 첫 단계가 된다
     * @param snippetName 이 실행의 스니펫 자리 이름 (REST Docs `{method-name}` 에 들어가는 값). [DocumentSection.snippetName]
     * @param cleanName 처음 쓸 때 비울 자리 이름. 기본값은 [snippetName]. 실행마다 하위 디렉터리를 쓰는 테스트(JUnit
     *   `@ParameterizedTest` 의 `get-user/2`)는 상위 자리(`get-user`)를 넘겨 한 번에 비운다
     * @param sectionTitle 이 테스트의 표시 이름. 넘기면 [DocumentationRun.close] 까지 이 실행에서 문서화한 operation 의
     *   OpenAPI·AsyncAPI 요약이 된다 (테스트 스레드가 아닌 곳에서 문서화해도). JUnit 확장은 테스트 표시 이름, Kotest 확장은
     *   섹션 제목을 넘긴다. null 이면 호출 스택의 `@DisplayName` 테스트 메서드, 그다음 메서드 이름으로 정한다.
     *   태그는 이 값과 상관없이 [documentClass] 의 제목(`@DisplayName`, 없으면 클래스 단순 이름)이다
     */
    public fun startTest(
        documentClass: Class<*>,
        snippetName: String,
        cleanName: String = snippetName,
        sectionTitle: String? = null,
    ): DocumentationRun {
        val directory = SnippetPaths.snippetDirectory(snippetsDirectory, documentClass, cleanName)

        if (CLEANED.add(directory.absolutePath)) {
            delete(directory)
        }

        return DocumentationRun(this, documentClass, snippetName, sectionTitle)
    }

    /** [documentClass] 문서에서 실패로 기록된 스니펫 자리 이름. 마지막 실행이 성공한 자리는 빠진다. */
    public fun failedSnippets(documentClass: Class<*>): Set<String> = FAILED[documentClass.name].orEmpty()
        .toSet()

    /**
     * [documentClass] 의 문서를 쓴다. 문서 클래스의 테스트가 모두 끝난 뒤 부른다.
     *
     * [generator] 와 [outline] 은 그 클래스의 테스트가 실제로 쓴 설정([DocumentationRun.captureSettings], 없으면 지금 설정)을
     * 적용한 상태에서 부른다. 그래서 테스트가 끝나 설정이 되돌아가도 `document.commonIncludeFiles` 같은 문서 설정이 반영된다.
     *
     * @return 문서를 썼으면 true. 쓸 섹션이 없으면 파일을 만들지 않고 false
     */
    public fun writeDocument(
        documentClass: Class<*>,
        generator: DocumentGeneratorFactory,
        outline: () -> DocumentOutline,
    ): Boolean {
        val settings = TEST_SETTINGS[documentClass.name] ?: RestDocsConfiguration.snapshot()

        PluginLanguageWarning.JVM.check(settings.language)

        val written = RestDocsConfiguration.using(settings) {
            AsciiDocumentWriter(
                generator.create(DocumentGeneratorContext(documentClass)),
                SnippetFileListExtractor(snippetsDirectory.path),
                RestDocsConfiguration.asciidocDirectory(),
            ).write(outline())
        }

        // 문서를 쓰지 않는 클래스(확장만 붙은 일반 테스트)는 이름이 겹쳐도 문제가 없다
        if (written) warnIfDocumentNameTaken(documentClass)

        return written
    }

    internal fun recordFailure(documentClass: Class<*>, snippetName: String, failed: Boolean) {
        val snippets = FAILED.computeIfAbsent(documentClass.name) { ConcurrentHashMap.newKeySet() }

        if (!failed) {
            snippets.remove(snippetName)

            return
        }

        snippets.add(snippetName)
        delete(SnippetPaths.snippetDirectory(snippetsDirectory, documentClass, snippetName))
    }

    internal fun recordSettings(documentClass: Class<*>, settings: RestDocsSettings) {
        TEST_SETTINGS[documentClass.name] = settings
    }

    /** 문서 파일 이름은 클래스 단순 이름으로 정하므로, 패키지만 다른 같은 이름의 클래스는 서로 덮어쓴다. */
    private fun warnIfDocumentNameTaken(documentClass: Class<*>) {
        val documentName = SnippetPaths.camelCaseToDash(documentClass.simpleName)
        val previous = DOCUMENT_OWNERS.putIfAbsent(documentName, documentClass.name) ?: return

        if (previous != documentClass.name) {
            LOGGER.log(
                System.Logger.Level.WARNING,
                RestDocsErrorCode.DUPLICATE_DOCUMENT_NAME.message(
                    "{0} and {1} both write {2}.adoc and share snippet directory ''{3}''. Rename one of them.",
                ),
                previous,
                documentClass.name,
                documentName,
                documentName,
            )
        }
    }

    /** 스니펫 디렉터리를 지운다. 지우지 못하면 지난 스니펫이 문서에 남을 수 있으므로 경고한다. */
    private fun delete(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) {
            LOGGER.log(
                System.Logger.Level.WARNING,
                RestDocsErrorCode.SNIPPET_DELETE_FAILED.message("could not delete snippets in {0}"),
                directory,
            )
        }
    }

    private companion object {
        /** 이번 JVM 실행에서 이미 비운 스니펫 디렉터리 */
        val CLEANED: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** 문서 클래스 이름 → 실패한 테스트의 스니펫 자리 이름 */
        val FAILED: ConcurrentHashMap<String, MutableSet<String>> = ConcurrentHashMap()

        /**
         * 문서 클래스 이름 → 그 클래스의 테스트가 실제로 쓴 설정. 실행마다 덮어쓰므로 문서에는 마지막으로 실행한 테스트의 설정이
         * 쓰인다. 같은 클래스의 테스트를 병렬로 돌리면 어느 테스트의 설정인지 보장되지 않는다.
         * 문서를 쓴 뒤에도 지우지 않는다. Kotest 격리 모드는 스펙 인스턴스가 끝날 때마다 같은 문서를 다시 쓰고, 설정을 기록하지 않은
         * 인스턴스(리프 테스트 없이 컨테이너만 실행)도 이 설정으로 써야 하기 때문이다.
         */
        val TEST_SETTINGS: ConcurrentHashMap<String, RestDocsSettings> = ConcurrentHashMap()

        /** 문서 이름 → 처음 그 이름을 쓴 문서 클래스 */
        val DOCUMENT_OWNERS: ConcurrentHashMap<String, String> = ConcurrentHashMap()

        val LOGGER: System.Logger = System.getLogger(DocumentationLifecycle::class.java.name)
    }
}
