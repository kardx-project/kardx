package io.github.kardx.kotest

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Kotest 테스트 이름에서 문서 이름을 만드는 기본 규칙. [RestDocsExtension] 의 기본값이고, 생성자 인자로 바꿀 수 있다.
 *
 * 규칙이 받는 `names` 는 바깥 컨테이너에서 리프 테스트까지의 테스트 이름이다. 스펙 스타일이 붙이는 접두사
 * (`Given: `, `When: `, `Then: `, `Describe: `, `It: ` 등)와 포커스·제외 표시(`f:`, `!`)는 뺀, 사용자가 적은 그대로의 이름이다.
 *
 * ```kotlin
 * given("사용자가 있을 때") {          // names = ["사용자가 있을 때", "단건 조회하면", "사용자 정보를 돌려준다"]
 *     `when`("단건 조회하면") {
 *         then("사용자 정보를 돌려준다") { ... }
 *     }
 * }
 * ```
 */
public object TestNames {
    /** 스니펫 자리 이름의 최대 길이 (UTF-8 바이트). 넘으면 잘라 내고 전체 이름의 해시를 붙인다 */
    public const val MAX_SNIPPET_NAME_BYTES: Int = 150

    /** 해시 접미사 길이 (16진수 글자 수) */
    private const val HASH_LENGTH = 8

    /** 이름에 남기는 글자: 글자(한글 포함)·결합 문자·숫자·`_` */
    private val NOT_NAME = Regex("""[^\p{L}\p{M}\p{N}_]+""")

    /** 소문자·숫자 뒤의 대문자 (`getUser` 의 `U`). REST Docs 와 같이 낱말 경계로 본다 */
    private val CAMEL_BOUNDARY = Regex("""(?<=[\p{Ll}\p{N}])(?=\p{Lu})""")

    private const val SEPARATOR = "-"
    private const val FALLBACK = "test"

    /**
     * 섹션 제목 기본 규칙: 테스트 이름을 ` - ` 로 잇는다. JUnit `@Nested` 테스트의 섹션 제목(`성공 - 조회`)과 같은 모양이다.
     *
     * `["사용자가 있을 때", "단건 조회하면", "사용자 정보를 돌려준다"]` → `사용자가 있을 때 - 단건 조회하면 - 사용자 정보를 돌려준다`
     */
    public fun sectionTitle(names: List<String>): String = names.joinToString(" - ")

    /**
     * 스니펫 자리 이름 기본 규칙. 스니펫 디렉터리(`<spec>/<이 이름>/`), 문서 섹션 앵커(`test-name` operationId 규칙이면
     * OpenAPI operationId 도)가 이 이름에서 나오므로 순번이 아닌 테스트 이름으로 만든다. 테스트를 추가하거나 순서를 바꿔도 다른 테스트의 이름은 바뀌지 않는다.
     *
     * 이름마다 [slug] 로 다듬어 `-` 로 잇는다. 결과가 [MAX_SNIPPET_NAME_BYTES] 를 넘으면 잘라 내고 전체 이름의 해시를 붙인다.
     *
     * `["사용자가 있을 때", "단건 조회하면", "사용자 정보를 돌려준다"]` → `사용자가-있을-때-단건-조회하면-사용자-정보를-돌려준다`,
     * `["GET /users/{id}", "returns 404"]` → `get-users-id-returns-404`
     */
    public fun snippetName(names: List<String>): String = names.map(::slug)
        .filter { it.isNotEmpty() }
        .joinToString(SEPARATOR)
        .let(::limit)

    /**
     * 이름 하나를 파일 시스템·AsciiDoc include·URL 에서 그대로 쓸 수 있는 모양으로 다듬는다.
     *
     * JUnit 의 백틱 메서드 이름(`` `사용자 조회 성공` ``)은 JVM 이 `/`·`.`·`:` 같은 글자를 막아 주므로 REST Docs 규칙 그대로
     * 디렉터리가 되고, 문서 앵커만 공백 등을 `-` 로 바꾼다([io.github.kardx.asciidoc.DocumentGenerator.asciidocAnchor]).
     * Kotest 이름에는 그런 제한이 없으므로 앵커 규칙을 디렉터리에도 쓰되, 경로에서 문제가 되는 `.`·`:` 도 바꾼다.
     *
     * 1. 유니코드 NFC 로 정규화한다 (macOS 파일 이름처럼 자모가 풀린 한글을 합친다)
     * 2. 소문자·숫자 뒤의 대문자 앞을 낱말 경계로 본다 (`getUser` → `get-User`, REST Docs 의 kebab-case 와 같은 결과)
     * 3. 모두 소문자로 바꾼다. REST Docs 는 `{method-name}` 의 대문자를 `-소문자` 로 바꾸므로(`API` → `-a-p-i`) 미리 바꾼다
     * 4. 글자(한글 등)·숫자·`_` 가 아닌 글자가 이어진 곳(공백, `/`, `.`, 괄호 …)을 `-` 하나로 바꾸고 앞뒤 `-` 를 뗀다
     *
     * `사용자 조회 (성공)` → `사용자-조회-성공`, `getUser` → `get-user`, `GET /api/v1.2` → `get-api-v1-2`
     */
    public fun slug(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)
        .replace(CAMEL_BOUNDARY, SEPARATOR)
        .lowercase(Locale.ROOT)
        .replace(NOT_NAME, SEPARATOR)
        .trim('-')

    /**
     * 사용자 규칙이 돌려준 이름도 기본 규칙과 같이 안전한 모양으로 맞춘다. 이미 안전한 이름(기본 규칙의 결과)은 그대로다.
     * 비면 `test`.
     */
    internal fun safe(name: String): String = name.split('-')
        .map(::slug)
        .filter { it.isNotEmpty() }
        .joinToString(SEPARATOR)
        .ifEmpty { FALLBACK }
        .let(::limit)

    /** [MAX_SNIPPET_NAME_BYTES] 를 넘으면 글자 경계에서 잘라 `-<해시 8자리>` 를 붙인다. 해시는 전체 이름에서 만들어 늘 같다. */
    private fun limit(name: String): String {
        val safe = name.ifEmpty { FALLBACK }

        if (safe.utf8Size() <= MAX_SNIPPET_NAME_BYTES) return safe

        val budget = MAX_SNIPPET_NAME_BYTES - HASH_LENGTH - SEPARATOR.length
        val head = StringBuilder()
        var bytes = 0

        for (codePoint in safe.codePoints().toArray()) {
            val size = String(Character.toChars(codePoint)).utf8Size()

            if (bytes + size > budget) break

            head.appendCodePoint(codePoint)
            bytes += size
        }

        return head.toString()
            .trimEnd('-') + SEPARATOR + hash(safe)
    }

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(HASH_LENGTH)

    private fun String.utf8Size(): Int = toByteArray(StandardCharsets.UTF_8).size
}
