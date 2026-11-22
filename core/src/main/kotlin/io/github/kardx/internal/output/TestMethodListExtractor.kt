package io.github.kardx.internal.output

import io.github.kardx.config.DocumentSectionOrder
import java.lang.reflect.Method

/**
 * 문서에 들어갈 테스트 하나.
 *
 * @property testClass 테스트를 선언한(실행한) 클래스. `@Nested` 테스트면 그 안쪽 클래스다
 * @property baseName 스니펫 디렉터리의 메서드 자리 이름 ([SnippetNames.baseName])
 */
internal class DocumentedTest(val testClass: Class<*>, val method: Method, val baseName: String)

/**
 * 테스트 클래스에서 테스트 메서드를 수집한다.
 *
 * JUnit Platform 의 `@Testable` 메타 어노테이션을 몇 단계든 따라가 판별하므로 `@Test`, `@ParameterizedTest`,
 * `@RepeatedTest`, `@TestFactory`, `@TestTemplate`, 사용자 합성 어노테이션과 `kotlin.test.Test` 를 모두 인식한다.
 * 상위 클래스에 선언된 테스트도 포함한다.
 */
internal object TestMethodListExtractor {
    /**
     * [testClass] 의 문서에 들어갈 테스트. 자기 테스트를 먼저 두고, 그 뒤에 `@Nested` 클래스의 테스트를 붙인다(안쪽 `@Nested` 도
     * 같은 규칙). 순서는 JUnit `@TestMethodOrder`·`@TestClassOrder`·`@Order` 를 따르고, 없으면 [order] 다 ([JUnitOrder]).
     * 재정의한 메서드처럼 스니펫 자리가 같으면 한 번만 센다.
     */
    fun extract(
        testClass: Class<*>,
        order: DocumentSectionOrder = DocumentSectionOrder.METHOD_NAME,
    ): List<DocumentedTest> {
        val own = testMethods(testClass).map { DocumentedTest(testClass, it, SnippetNames.baseName(testClass, it)) }
            .distinctBy { it.baseName }
        val nested = testClass.declaredClasses.filter { SnippetNames.documentClass(it) != it }

        return JUnitOrder.methods(testClass, own, order) +
            JUnitOrder.nestedClasses(testClass, nested, order).flatMap { extract(it, order) }
    }

    /** [clazz] 와 상위 클래스의 테스트 메서드 (같은 이름의 재정의·오버로드 포함). */
    fun testMethods(clazz: Class<*>): List<Method> = generateSequence(clazz) { it.superclass }
        .takeWhile { it != Any::class.java }
        .flatMap { it.declaredMethods.asSequence() }
        .filter(::isTestMethod)
        .toList()

    private fun isTestMethod(method: Method): Boolean = method.annotations.any { isTestable(it, mutableSetOf()) }

    /** [annotation] 이거나 그 메타 어노테이션(재귀) 중에 `@Testable` 이 있는지. [visited] 로 순환(`@Documented` 등)을 끊는다. */
    private fun isTestable(annotation: Annotation, visited: MutableSet<Class<*>>): Boolean {
        val type = annotation.annotationClass.java

        if (!visited.add(type)) return false

        return type.name == TESTABLE || type.name == KOTLIN_TEST || type.annotations.any { isTestable(it, visited) }
    }

    private const val TESTABLE = "org.junit.platform.commons.annotation.Testable"
    private const val KOTLIN_TEST = "kotlin.test.Test"
}
