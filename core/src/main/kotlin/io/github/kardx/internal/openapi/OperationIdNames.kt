package io.github.kardx.internal.openapi

import java.util.Locale

/**
 * operationId 규칙([io.github.kardx.openapi.OperationIdRule])이 만드는 이름. 코드 생성기(openapi-generator 등)가 그대로
 * 메서드 이름으로 쓸 수 있도록 camelCase 영문·숫자로 만든다.
 */
internal object OperationIdNames {
    /** 컨트롤러 이름에서 떼는 접미사. 앞의 것부터 반복해서 뗀다 (`UserApiController` → `User`) */
    private val CONTROLLER_SUFFIXES = listOf("RestController", "Controller", "Api")

    /** 경로 조각 안의 변수 (`{id}`) */
    private val PATH_VARIABLE = Regex("""\{([^}]*)}""")

    /** 낱말 경계: 영문·숫자가 아닌 글자 */
    private val NOT_WORD = Regex("""[^A-Za-z0-9]+""")

    /** 테스트 기반 id 에 남기는 글자: 글자(한글 포함)·결합 문자·숫자·`.`·`_`·`-` */
    private val NOT_TEST_ID = Regex("""[^\p{L}\p{M}\p{N}._-]+""")

    private const val VARIABLE_PREFIX = "by"
    private const val FALLBACK = "operation"

    /** 컨트롤러 이름 + 메서드 이름 (`UserController`, `create` → `userCreate`). */
    fun controllerAndMethod(controllerName: String, methodName: String): String =
        camelCase(listOf(controllerBase(controllerName), methodName))

    /**
     * HTTP 메서드 + 경로 (`GET`, `/api/v1/users/{id}` → `getApiV1UsersById`). 경로 변수는 `By` + 변수 이름,
     * 영문·숫자가 아닌 글자(`-`, `.`, 한글 등)는 낱말 경계로 보고 뺀다.
     */
    fun path(httpMethod: String, path: String): String {
        val words = mutableListOf(httpMethod.lowercase(Locale.ROOT))

        path.split('/')
            .filter { it.isNotEmpty() }
            .forEach { segment -> words += segmentWords(segment) }

        return camelCase(words)
    }

    /**
     * 테스트 클래스·메서드 이름의 스니펫 이름(`user-api-docs-test/search-users`)으로 만든 id (`user-api-docs-test.search-users`).
     * Gradle 플러그인이 `test-name` 규칙에서 쓰는 정리와 같다.
     */
    fun testBased(snippetName: String): String {
        val normalized = snippetName.trimEnd('/', '-')
            .replace('/', '.')
            .replace(NOT_TEST_ID, "-")

        return normalized.ifEmpty { FALLBACK }
    }

    /** `UserController` → `User`. 접미사뿐이면 원래 이름 그대로. */
    private fun controllerBase(controllerName: String): String {
        var base = controllerName

        do {
            val suffix = CONTROLLER_SUFFIXES.firstOrNull { base.length > it.length && base.endsWith(it) }

            base = suffix?.let { base.removeSuffix(it) } ?: base
        } while (suffix != null)

        return base
    }

    /** 경로 조각 하나의 낱말. 변수(`{userId}`)는 `by`, `userId` 다. */
    private fun segmentWords(segment: String): List<String> {
        val words = mutableListOf<String>()
        var index = 0

        PATH_VARIABLE.findAll(segment)
            .forEach { match ->
                words += splitWords(segment.substring(index, match.range.first))
                words += VARIABLE_PREFIX
                words += splitWords(match.groupValues[1])
                index = match.range.last + 1
            }

        return words + splitWords(segment.substring(index))
    }

    private fun splitWords(text: String): List<String> = text.split(NOT_WORD)
        .filter { it.isNotEmpty() }

    /** 첫 낱말은 첫 글자를 소문자로, 나머지는 첫 글자를 대문자로 바꿔 잇는다. 나머지 글자는 그대로 둔다. */
    private fun camelCase(words: List<String>): String = words.filter { it.isNotEmpty() }
        .mapIndexed { index, word ->
            val first = if (index == 0) word.first().lowercaseChar() else word.first().uppercaseChar()

            first + word.substring(1)
        }
        .joinToString("")
        .ifEmpty { FALLBACK }
}
