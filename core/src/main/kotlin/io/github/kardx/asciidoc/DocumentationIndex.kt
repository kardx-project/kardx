package io.github.kardx.asciidoc

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.i18n.DocsText
import io.github.kardx.internal.output.DocumentRecord
import io.github.kardx.internal.output.writeTextAtomically
import io.github.kardx.internal.reflect.defaultClassLoader
import io.github.kardx.internal.reflect.loadClassOrNull
import io.github.kardx.internal.reflect.scanClassMetadata
import java.io.File
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

/**
 * 여러 테스트 클래스의 문서를 하나의 index 로 묶는다.
 *
 * 직접 쓴 템플릿(`index-template.adoc`)의 [MARKER] 줄 자리에 테스트 클래스별 항목을 넣어 index 를 만든다.
 * 마커가 없으면 문서 끝에 붙인다. 항목은 테스트 클래스의 JUnit `@Order` 값 순서이고(없으면 맨 뒤), 같으면
 * `@DisplayName`(JUnit 또는 Kotest, 없으면 클래스 이름) 순서다.
 * 문서(`.adoc`)를 만들지 않은 클래스(문서화한 테스트가 없음)는 링크가 깨지므로 넣지 않는다.
 *
 * 패키지를 훑을 때([write] 의 `packageName`·`baseClass` 판, CLI)는 기본 클래스를 상속한 테스트 클래스에 더해 그 패키지의
 * Kotest 스펙(`io.kotest.core.spec.Spec` 의 구체 하위 클래스)도 찾는다. Kotest 스펙은 JUnit 기본 클래스를 상속할 수 없기
 * 때문이다. 문서를 만든 스펙만 항목이 되므로 문서화하지 않는 스펙은 들어가지 않는다.
 *
 * 패키지와 기본 클래스를 주지 않으면(CLI) 훑지 않는다. 클래스별 문서를 쓸 때 생성 문서 디렉터리에 함께 남긴 항목
 * (문서 이름, 제목, 순서)으로 문서를 만든 모든 테스트 클래스를 같은 순서 규칙으로 넣는다. 템플릿도 없으면 내장 템플릿을 쓴다.
 *
 * ```asciidoc
 * [[user-api-docs-test]]
 * === 사용자 API
 *
 * link:./user-api-docs-test.html[Open document]
 *
 * * link:./user-api-docs-test.html#get-user[사용자 조회]
 * * link:./user-api-docs-test.html#create-user[사용자 생성]
 * ```
 *
 * Gradle 에서는 `io.github.kardx.asciidoc.DocumentationIndexCli` 를 `JavaExec` 로 실행한다.
 */
public object DocumentationIndex {
    /** 템플릿에서 항목을 넣을 자리를 표시하는 줄. AsciiDoc 주석이라 템플릿 자체를 렌더링해도 보이지 않는다. */
    public const val MARKER: String = "// restdocs:api-index"

    /**
     * 출력 파일과 같은 디렉터리에서 테스트 문서(`*.adoc`)를 찾는다.
     *
     * @param template 직접 쓴 index 템플릿
     * @param output 만들 index 파일. 생성된 테스트 문서(`*.adoc`)와 같은 디렉터리에 둔다
     * @param testClasses 링크할 테스트 클래스
     */
    public fun write(template: File, output: File, testClasses: List<Class<*>>) {
        write(template, output, testClasses, output.absoluteFile.parentFile)
    }

    /**
     * [documents] 디렉터리에서 테스트 문서(`*.adoc`)를 찾는다. index 를 테스트 문서와 다른 디렉터리에 만들 때 쓴다.
     * 링크는 `./<문서>.html` 이므로 렌더링할 때는 index 와 테스트 문서를 같은 디렉터리에 모아야 한다.
     *
     * @param template 직접 쓴 index 템플릿
     * @param output 만들 index 파일
     * @param testClasses 링크할 테스트 클래스
     * @param documents 생성된 테스트 문서(`*.adoc`) 디렉터리
     */
    public fun write(template: File, output: File, testClasses: List<Class<*>>, documents: File) {
        require(template.isFile) {
            RestDocsErrorCode.INDEX_TEMPLATE_NOT_FOUND.message("Index template not found: ${template.path}")
        }
        require(template.canonicalFile != output.canonicalFile) { "output must differ from the template" }

        writeEntries(template.readLines(), output, testClasses.map(DocumentRecord::of), documents)
    }

    /**
     * 패키지를 훑지 않고, 클래스별 문서를 쓸 때 [documents] 에 함께 남긴 항목([DocumentRecord])으로 index 를 만든다.
     * [template] 이 null 이면 [title] 을 제목으로 하는 내장 템플릿([defaultTemplate])을 쓴다.
     */
    internal fun writeFromDocuments(template: File?, title: String, output: File, documents: File) {
        writeIndex(template, title, output, DocumentRecord.readAll(documents), documents)
    }

    /** [entries] 로 index 를 만든다. [template] 이 null 이면 [title] 을 제목으로 하는 내장 템플릿을 쓴다. */
    internal fun writeIndex(
        template: File?,
        title: String,
        output: File,
        entries: List<DocumentRecord>,
        documents: File,
    ) {
        val lines = if (template == null) {
            defaultTemplate(title).lines()
        } else {
            require(template.isFile) {
                RestDocsErrorCode.INDEX_TEMPLATE_NOT_FOUND.message("Index template not found: ${template.path}")
            }
            require(template.canonicalFile != output.canonicalFile) { "output must differ from the template" }
            template.readLines()
        }

        writeEntries(lines, output, entries, documents)
    }

    /**
     * 템플릿이 없을 때 쓰는 index 템플릿. 생성 문서와 같은 헤더 속성을 쓰고, 항목은 `== API list`
     * (한국어 설정이면 `== API 목록`) 아래에 들어간다.
     */
    internal fun defaultTemplate(title: String): String = listOf(
        (listOf("= $title") + DocumentGenerator.Config().attributes()).joinToString("\n"),
        "== ${DocsText.INDEX_HEADING.text()}",
        MARKER,
    ).joinToString(separator = "\n\n", postfix = "\n")

    /** [entries] 를 순서대로 템플릿의 [MARKER] 자리(없으면 끝)에 넣는다. 문서(`.adoc`)가 없는 항목은 뺀다. */
    private fun writeEntries(
        templateLines: List<String>,
        output: File,
        entries: List<DocumentRecord>,
        documents: File,
    ) {
        val text = entries.sortedWith(DocumentRecord.COMPARATOR)
            .mapNotNull { entry(documents, it) }
            .joinToString("\n")
        val marker = templateLines.indexOfFirst { it.trim() == MARKER }

        val result = if (marker < 0) {
            templateLines + "" + text
        } else {
            templateLines.take(marker) + text + templateLines.drop(marker + 1)
        }

        output.writeTextAtomically(result.joinToString(separator = "\n", postfix = "\n"))
    }

    /**
     * [packageName] 과 하위 패키지에서 [baseClass] 를 상속한 구체 테스트 클래스와 Kotest 스펙을 찾아 index 를 만든다.
     */
    public fun write(template: File, output: File, packageName: String, baseClass: KClass<*>) {
        write(template, output, findDocumentClasses(packageName, baseClass))
    }

    /**
     * [packageName] 과 하위 패키지에서 [baseClass] 를 상속한 구체 테스트 클래스와 구체 Kotest 스펙.
     * 클래스는 한 번만 스캔·로드하고 두 기준으로 거른다. Kotest 가 클래스패스에 없으면 [baseClass] 기준만 쓴다.
     * Kotest 에 컴파일 의존하지 않으므로 스펙 기본 클래스를 이름으로 찾는다.
     */
    internal fun findDocumentClasses(packageName: String, baseClass: KClass<*>): List<Class<*>> {
        val loader = defaultClassLoader()
        val bases = listOfNotNull(baseClass.java, loadClassOrNull(KOTEST_SPEC, loader))

        return concreteClasses(packageName, loader).filter { candidate -> bases.any { it.isProperBaseOf(candidate) } }
    }

    /** [packageName] 과 하위 패키지에서 [baseClass] 를 상속한 구체 클래스. */
    internal fun findTestClasses(packageName: String, baseClass: KClass<*>): List<Class<*>> =
        concreteClasses(packageName, defaultClassLoader()).filter { baseClass.java.isProperBaseOf(it) }

    /** [packageName] 과 하위 패키지의 구체 클래스. 같은 클래스가 클래스패스에 여러 번 있어도 한 번만 돌려준다. */
    private fun concreteClasses(packageName: String, loader: ClassLoader): List<Class<*>> =
        scanClassMetadata(packageName, loader).map { it.classMetadata }
            .filter { it.isConcrete && !it.isInterface }
            .map { it.className }
            .distinct()
            .mapNotNull { loadClassOrNull(it, loader) }
            .filterNot { Modifier.isAbstract(it.modifiers) }

    /** 이 클래스가 [candidate] 의 상위 타입이고 [candidate] 자신은 아닌지. */
    private fun Class<*>.isProperBaseOf(candidate: Class<*>): Boolean = isAssignableFrom(candidate) && this != candidate

    private fun entry(directory: File, record: DocumentRecord): String? {
        val documentName = record.documentName
        val document = File(directory, "$documentName.adoc")

        if (!document.isFile) return null

        val page = "./$documentName.html"
        val sections = sectionsOf(document)
            .joinToString("\n") { (anchor, title) -> "* link:$page#$anchor[${linkText(title)}]" }

        return listOf(
            """
            [[$documentName]]
            === ${record.title}

            link:$page[${DocsText.INDEX_OPEN_DOCUMENT.text()}]
            """.trimIndent(),
            sections,
        ).filter { it.isNotEmpty() }
            .joinToString(separator = "\n\n", postfix = "\n")
    }

    /** 링크 글자 안의 `]` 는 링크를 닫아 버리므로 이스케이프한다. */
    private fun linkText(title: String): String = title.replace("]", "\\]")

    /** 생성된 문서에서 `[[anchor]]` 다음 줄의 `== 제목` 섹션을 읽는다. 문서가 없으면 빈 목록. */
    private fun sectionsOf(document: File): List<Pair<String, String>> {
        if (!document.isFile) return emptyList()

        return SECTION.findAll(document.readText())
            .map { it.groupValues[1] to it.groupValues[2].trim() }
            .toList()
    }

    /** Kotest 스펙의 공통 상위 클래스 */
    private const val KOTEST_SPEC = "io.kotest.core.spec.Spec"

    private val SECTION = Regex("""\[\[([^]]+)]]\n==\s+(?:icon:link\[]\s+)?(.+)""")
}
