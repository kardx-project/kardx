@file:JvmName("DocumentationIndexCli")

package io.github.kardx.asciidoc

import io.github.kardx.internal.i18n.DocsText
import io.github.kardx.internal.output.DocumentRecord
import io.github.kardx.internal.reflect.loadClassCatching
import java.io.File
import kotlin.system.exitProcess

/**
 * [DocumentationIndex] 의 명령행 진입점. 사용자 코드 없이 Gradle `JavaExec` 로 실행한다.
 *
 * 빌드 도구용 진입점이며 호환성 보장 대상(공개 API)이 아니다. 지켜지는 것은 JVM 클래스 이름
 * `io.github.kardx.asciidoc.DocumentationIndexCli` 와 아래 명령행 옵션뿐이다. 코드에서는 [DocumentationIndex.write] 를 쓴다.
 *
 * ```kotlin
 * val docsIndex by tasks.registering(JavaExec::class) {
 *     dependsOn(tasks.test)
 *     classpath = sourceSets.test.get().runtimeClasspath
 *     mainClass.set("io.github.kardx.asciidoc.DocumentationIndexCli")
 *     args(
 *         "--template", "src/docs/asciidoc/index-template.adoc",
 *         "--output", "build/docs-index/index.adoc",
 *         "--documents", "build/generated-asciidoc",
 *         "--package", "com.myapp",
 *         "--base-class", "com.myapp.support.ApiDocsTest",
 *     )
 * }
 * ```
 *
 * 옵션
 * - `--output <file>`: 만들 index 파일 (필수)
 * - `--template <file>`: 직접 쓴 index 템플릿 (`// restdocs:api-index` 줄 자리에 항목이 들어간다). 없으면 `--title` 을
 *   제목으로 하는 내장 템플릿
 * - `--title <text>`: 내장 템플릿의 제목. 기본값 `API Documentation` (`kardx.language=ko` 면 `API 문서`)
 * - `--package <name>`: 테스트 클래스를 찾을 패키지. 하위 패키지도 훑는다. `--base-class` 와 함께 준다
 * - `--base-class <name>`: 문서화 테스트의 공통 상위 클래스. 같은 패키지의 Kotest 스펙은 이 클래스와 상관없이 찾는다
 * - `--documents <dir>`: 생성된 테스트 문서(`*.adoc`) 디렉터리. 없으면 `--output` 파일의 디렉터리
 *
 * `--package`·`--base-class` 를 주지 않으면 클래스패스를 훑지 않고, 테스트가 문서를 쓸 때 `--documents` 에 함께 남긴
 * 항목으로 문서를 만든 모든 테스트 클래스를 넣는다.
 *
 * 내장 템플릿의 문구는 시스템 속성 `kardx.language` (`en`, `ko`)를 따른다. Gradle 플러그인이 넘긴다.
 */
internal fun main(args: Array<String>) {
    val options = try {
        DocumentationIndexCliOptions.parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("docs-index: ${e.message}")
        exitProcess(2)
    }

    val scan = options.scan
    val entries = if (scan == null) {
        DocumentRecord.readAll(options.documents)
    } else {
        documentClasses(scan).map(DocumentRecord::of)
    }

    DocumentationIndex.writeIndex(options.template, options.title, options.output, entries, options.documents)
    println("Documentation index: ${entries.size} documents -> ${options.output}")
}

/** [scan] 의 패키지에서 기본 클래스를 상속한 테스트 클래스와 Kotest 스펙. 기본 클래스가 없으면 종료한다. */
private fun documentClasses(scan: DocumentationIndexCliOptions.Scan): List<Class<*>> {
    val baseClass = loadClassCatching(scan.baseClass, DocumentationIndexCliOptions::class.java.classLoader)
        .getOrElse { e ->
            System.err.println(
                "docs-index: --base-class '${scan.baseClass}' not found on the test classpath (${e.message})",
            )

            exitProcess(2)
        }
        .kotlin

    return DocumentationIndex.findDocumentClasses(scan.packageName, baseClass)
}

/** [main] 의 명령행 옵션. [scan] 이 null 이면 클래스패스를 훑지 않는다. */
internal class DocumentationIndexCliOptions(
    val template: File?,
    val title: String,
    val output: File,
    val scan: Scan?,
    val documents: File,
) {
    /** 문서화 테스트를 찾을 패키지와 공통 상위 클래스. */
    internal class Scan(val packageName: String, val baseClass: String)

    internal companion object {
        /** Gradle 플러그인의 `IndexTasks` 가 같은 이름으로 넘긴다. */
        private val OPTIONS =
            listOf("--template", "--title", "--output", "--package", "--base-class", "--documents")

        fun parse(args: Array<String>): DocumentationIndexCliOptions {
            val values = mutableMapOf<String, String>()
            var index = 0

            while (index < args.size) {
                val name = args[index]
                require(name in OPTIONS) { "unknown option $name (expected ${OPTIONS.joinToString()})" }
                values[name] = requireNotNull(args.getOrNull(index + 1)) { "$name needs a value" }
                index += 2
            }

            val output = File(requireNotNull(values["--output"]) { "--output is required" })
            val packageName = values["--package"]
            val baseClass = values["--base-class"]

            require((packageName == null) == (baseClass == null)) { "--package and --base-class go together" }

            val scan = if (packageName != null && baseClass != null) Scan(packageName, baseClass) else null

            return DocumentationIndexCliOptions(
                template = values["--template"]?.let(::File),
                title = values["--title"] ?: DocsText.INDEX_TITLE.text(),
                output = output,
                scan = scan,
                documents = values["--documents"]?.let(::File) ?: output.absoluteFile.parentFile,
            )
        }
    }
}
