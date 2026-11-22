package io.github.kardx.internal.output

import io.github.kardx.config.RestDocsConfiguration
import java.io.File
import java.io.StringWriter
import java.util.Properties

/**
 * 문서를 쓴 테스트 클래스 하나의 기록. index 항목(문서 파일 이름, 제목, 순서 = JUnit `@Order`)과, 그 문서를 만들 때 민감 헤더·쿼리 파라미터를
 * 가렸는지([headersMasked])를 담는다. 빌드 도구가 읽는다: index CLI 는 패키지를 훑지 않을 때 index 를 만들고, Gradle 플러그인은
 * 문서를 실행 jar 에 넣을 때 가리지 않은 문서가 있으면 경고한다.
 *
 * 클래스별 문서를 쓸 때 [write] 가 생성 문서 디렉터리의 [DIRECTORY] 에 클래스마다 작은 파일을 남기고 [readAll] 로 모은다.
 * 클래스마다 파일이 따로라서 병렬 fork 가 서로 덮어쓰지 않고, 테스트가 다시 돌지 않은 클래스(증분 실행)의 기록도 그대로 남는다.
 */
internal class DocumentRecord(
    val documentName: String,
    val title: String,
    val order: Int,
    val className: String,
    val headersMasked: Boolean,
) {
    /** [documents] 의 [DIRECTORY] 에 이 항목을 쓴다. 내용이 같으면 파일도 같다 (날짜 주석 없음, 키 순서 고정). */
    fun write(documents: File) {
        val properties = Properties().apply {
            setProperty(DOCUMENT, documentName)
            setProperty(TITLE, title)
            setProperty(ORDER, order.toString())
            setProperty(CLASS, className)
            setProperty(HEADERS_MASKED, headersMasked.toString())
        }

        val text = StringWriter().also { properties.store(it, null) }
            .toString()
            .lines()
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .sorted()
            .joinToString(separator = "\n", postfix = "\n")

        File(File(documents, DIRECTORY), "$documentName.properties").writeTextAtomically(text)
    }

    companion object {
        /** 생성 문서 디렉터리 안의 기록 디렉터리. Asciidoctor 입력(`*.adoc`)과 겹치지 않는다. Gradle 플러그인과 같은 이름이다. */
        const val DIRECTORY: String = ".restdocs-documents"

        private const val DOCUMENT = "document"
        private const val TITLE = "title"
        private const val ORDER = "order"
        private const val CLASS = "class"
        private const val HEADERS_MASKED = "headers-masked"

        /** index 항목 순서: `@Order` 값, 제목, 클래스 이름. */
        val COMPARATOR: Comparator<DocumentRecord> = compareBy({ it.order }, { it.title }, { it.className })

        /** [documentClass] 의 기록. 가리기 여부는 지금 설정([RestDocsConfiguration.current])에서 읽는다. */
        fun of(documentClass: Class<*>): DocumentRecord {
            val settings = RestDocsConfiguration.current

            return DocumentRecord(
                documentName = SnippetPaths.camelCaseToDash(documentClass.simpleName),
                title = DisplayNames.titleOf(documentClass),
                order = JUnitOrder.orderOf(documentClass),
                className = documentClass.name,
                headersMasked =
                settings.masking.headers.isNotEmpty() || settings.masking.queryParameters.isNotEmpty() ||
                    settings.masking.masker != null,
            )
        }

        /** [documents] 에 남은 기록을 모두 읽는다. 문서 이름이 없는 파일은 건너뛴다. */
        fun readAll(documents: File): List<DocumentRecord> = File(documents, DIRECTORY)
            .listFiles { file -> file.isFile && file.name.endsWith(".properties") }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull(::read)

        private fun read(file: File): DocumentRecord? {
            val properties = Properties().apply { file.reader().use(::load) }
            val document = properties.getProperty(DOCUMENT) ?: return null

            return DocumentRecord(
                documentName = document,
                title = properties.getProperty(TITLE) ?: document,
                order = properties.getProperty(ORDER)?.toIntOrNull() ?: JUnitOrder.DEFAULT_ORDER,
                className = properties.getProperty(CLASS).orEmpty(),
                headersMasked = properties.getProperty(HEADERS_MASKED).toBoolean(),
            )
        }
    }
}
