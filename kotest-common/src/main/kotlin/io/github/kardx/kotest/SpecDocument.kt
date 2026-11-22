@file:OptIn(RestDocsExtensionApi::class)

package io.github.kardx.kotest

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.lifecycle.DocumentSection
import io.github.kardx.lifecycle.RestDocsExtensionApi
import java.util.concurrent.ConcurrentHashMap

/**
 * 스펙 클래스 하나의 문서 섹션 기록. 실제로 실행된 리프 테스트를 처음 시작한 순서대로 둔다.
 *
 * 스펙 클래스마다 JVM 에 하나다([of]). Kotest 격리 모드(`InstancePerRoot` 등)는 스펙 인스턴스마다 확장을 새로 만드는데,
 * 기록은 인스턴스가 아니라 클래스에 모이므로 어느 인스턴스가 문서를 써도 그때까지 실행된 모든 테스트가 들어간다.
 *
 * 같은 테스트 경로(이름 목록)는 같은 섹션이다. 다른 테스트가 같은 스니펫 자리 이름을 쓰면 뒤의 테스트에 `-2`, `-3` … 을
 * 붙이고 경고한다.
 */
internal class SpecDocument(private val specClass: Class<*>) {
    private val entries = LinkedHashMap<List<String>, Entry>()

    /** 스니펫 자리 이름 → 그 이름을 처음 쓴 테스트 경로 */
    private val owners = HashMap<String, List<String>>()

    /** [path] 테스트의 섹션. 처음 보는 테스트면 [snippetName]·[title] 로 만들어 끝에 둔다. 이름이 겹치면 번호를 붙인다. */
    @Synchronized
    fun section(path: List<String>, snippetName: String, title: String): DocumentSection {
        entries[path]?.let { return it.section }

        val unique = uniqueName(path, snippetName)
        val section = DocumentSection(unique, title)

        owners[unique] = path
        entries[path] = Entry(section)

        return section
    }

    /** [path] 테스트의 마지막 결과. 성공한 테스트만 문서에 들어간다. */
    @Synchronized
    fun complete(path: List<String>, succeeded: Boolean) {
        entries[path]?.included = succeeded
    }

    /** 문서에 넣을 섹션. 성공한 테스트만, 처음 시작한 순서대로. */
    @Synchronized
    fun sections(): List<DocumentSection> = entries.values.filter { it.included }
        .map { it.section }

    private fun uniqueName(path: List<String>, snippetName: String): String {
        if (snippetName !in owners) return snippetName

        val unique = generateSequence(2) { it + 1 }
            .map { "$snippetName-$it" }
            .first { it !in owners }

        LOGGER.log(
            System.Logger.Level.WARNING,
            RestDocsErrorCode.KOTEST_DUPLICATE_SNIPPET_NAME.message(
                "tests ''{0}'' and ''{1}'' in {2} map to the same snippet directory ''{3}''. " +
                    "Using ''{4}'' for the second one; rename one of them so the name does not depend on test order.",
            ),
            owners[snippetName]?.joinToString(" / "),
            path.joinToString(" / "),
            specClass.name,
            snippetName,
            unique,
        )

        return unique
    }

    private class Entry(val section: DocumentSection) {
        /** 마지막 실행이 성공했는지. 실행 중이거나 실패·건너뜀이면 false */
        var included: Boolean = false
    }

    companion object {
        private val DOCUMENTS = ConcurrentHashMap<String, SpecDocument>()
        private val LOGGER: System.Logger = System.getLogger(RestDocsExtension::class.java.name)

        /** [specClass] 의 기록. 처음이면 만든다. */
        fun of(specClass: Class<*>): SpecDocument =
            DOCUMENTS.computeIfAbsent(specClass.name) { SpecDocument(specClass) }

        /** [specClass] 의 기록. 실행한 리프 테스트가 없었으면 null. */
        fun find(specClass: Class<*>): SpecDocument? = DOCUMENTS[specClass.name]
    }
}
