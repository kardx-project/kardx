package io.github.kardx.internal.output

import io.github.kardx.internal.reflect.hasName
import java.beans.Introspector
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 테스트 실행 하나가 스니펫을 쓰는 자리. REST Docs 의 `{class-name}/{method-name}` 에 들어갈 클래스와 메서드 이름이다.
 *
 * @property documentClass 문서 하나로 모이는 클래스. `@Nested` 테스트는 가장 바깥 클래스다
 * @property baseName 메서드 자리 이름 ([SnippetNames.baseName])
 * @property invocation `@ParameterizedTest` 의 실행 번호 (1부터). 다른 테스트는 null
 */
internal class SnippetTarget(val documentClass: Class<*>, val baseName: String, val invocation: Int?) {
    /** REST Docs 에 넘기는 메서드 이름. 파라미터 테스트는 실행마다 `<baseName>/<번호>` 하위 디렉터리다. */
    val methodName: String get() = invocation?.let { "$baseName/$it" } ?: baseName
}

/**
 * 테스트의 스니펫 디렉터리 이름 규칙. 확장(스니펫 쓰기·지우기)과 문서 생성(스니펫 찾기)이 이 규칙 하나를 쓴다.
 * 디렉터리 이름은 REST Docs 처럼 kebab-case 다 ([SnippetPaths]).
 *
 * | 테스트 | 디렉터리 |
 * |---|---|
 * | `UserApiTest.getUser()` | `user-api-test/get-user` |
 * | `UserApiTest` 의 `@Nested inner class Success` 의 `getUser()` | `user-api-test/success-get-user` |
 * | 오버로드 `UserApiTest.create(provider: RestDocumentationContextProvider)` | `user-api-test/create-rest-documentation-context-provider` |
 * | `@ParameterizedTest UserApiTest.getUser(id)` 의 2번째 실행 | `user-api-test/get-user/2` |
 *
 * 메서드 자리 이름은 `success-getUser`, `create-restDocumentationContextProvider` 처럼 붙이는 이름의 첫 글자를 소문자로 해
 * REST Docs 의 kebab-case 변환에서 `--` 가 생기지 않게 한다. 인자 없는 오버로드와 오버로드가 없는 메서드는 메서드 이름만 쓴다. `@RepeatedTest` 는 실행마다 같은 디렉터리를 쓴다
 * (같은 요청을 반복하므로 마지막 실행이 남는다).
 */
internal object SnippetNames {
    /** [testClass] 가 속한 문서의 클래스. `@Nested` 테스트는 가장 바깥 클래스의 문서에 들어간다. */
    fun documentClass(testClass: Class<*>): Class<*> = generateSequence(testClass) { c ->
        c.enclosingClass?.takeIf { isNested(c) }
    }.last()

    /** [testClass] 에서 실행한 [method] 의 메서드 자리 이름 (파라미터 테스트의 실행 번호는 빼고). */
    fun baseName(testClass: Class<*>, method: Method): String {
        val nested = generateSequence(testClass) { c -> c.enclosingClass?.takeIf { isNested(c) } }
            .takeWhile { isNested(it) }
            .toList()
            .asReversed()
            .joinToString("") { "${Introspector.decapitalize(it.simpleName)}-" }

        return nested + overloadName(testClass, method)
    }

    /** [testClass] 의 [method] 를 [uniqueId] 로 실행할 때의 스니펫 자리. */
    fun target(testClass: Class<*>, method: Method, uniqueId: String): SnippetTarget = SnippetTarget(
        documentClass(testClass),
        baseName(testClass, method),
        if (isParameterized(method)) invocationOf(uniqueId) else null,
    )

    /** `@ParameterizedTest` (메타 어노테이션 포함)인지. 실행마다 따로 문서화한다. */
    fun isParameterized(method: Method): Boolean = method.annotations.any { annotation ->
        val type = annotation.annotationClass.java

        type.name == PARAMETERIZED_TEST || type.annotations.any { it.hasName(PARAMETERIZED_TEST) }
    }

    /** 같은 이름의 테스트 메서드가 인자 목록만 달리해 여럿이면 인자 타입 이름을 붙인다. */
    private fun overloadName(testClass: Class<*>, method: Method): String {
        val signatures = TestMethodListExtractor.testMethods(testClass)
            .filter { it.name == method.name }
            .map { it.parameterTypes.toList() }
            .distinct()

        if (signatures.size <= 1 || method.parameterCount == 0) {
            return method.name
        }

        return method.name + method.parameterTypes.joinToString("") { "-${Introspector.decapitalize(it.simpleName)}" }
    }

    /** JUnit 고유 ID 의 `[test-template-invocation:#3]` 에서 3. 없으면 null. */
    private fun invocationOf(uniqueId: String): Int? = INVOCATION.findAll(uniqueId)
        .lastOrNull()
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

    private fun isNested(c: Class<*>): Boolean = c.enclosingClass != null &&
        !Modifier.isStatic(c.modifiers) &&
        c.annotations.any { it.hasName(NESTED) }

    private const val NESTED = "org.junit.jupiter.api.Nested"
    private const val PARAMETERIZED_TEST = "org.junit.jupiter.params.ParameterizedTest"
    private val INVOCATION = Regex("""\[test-template-invocation:#(\d+)]""")
}
