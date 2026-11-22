package io.github.kardx.internal.output

import io.github.kardx.config.DocumentSectionOrder
import io.github.kardx.internal.reflect.hasName
import io.github.kardx.internal.reflect.memberValue
import java.lang.reflect.AnnotatedElement

/**
 * JUnit Jupiter 의 순서 어노테이션을 이름으로 읽어 문서 순서를 정한다. JUnit 은 `compileOnly` 라 직접 참조하지 않는다.
 *
 * - 메서드: 테스트 클래스(상위·바깥 클래스 포함)의 `@TestMethodOrder` 가 `OrderAnnotation` 이면 `@Order` 값,
 *   `MethodName`·`Alphanumeric` 이면 메서드 이름, `DisplayName` 이면 표시 이름. 그 밖(`Random`, 사용자 정의)과 어노테이션이
 *   없으면 [DocumentSectionOrder]
 * - `@Nested` 클래스: 바깥 클래스의 `@TestClassOrder` 가 `OrderAnnotation`·`ClassName`·`DisplayName` 이면 그 순서
 * - 같은 순번은 [DocumentSectionOrder] 순서로 푼다
 */
internal object JUnitOrder {
    /** `@Order` 가 없을 때의 값. JUnit `Order.DEFAULT` 와 같다 */
    const val DEFAULT_ORDER: Int = Int.MAX_VALUE / 2

    private const val JUPITER_PACKAGE = "org.junit.jupiter.api."
    private const val ORDER = "org.junit.jupiter.api.Order"
    private const val TEST_METHOD_ORDER = "org.junit.jupiter.api.TestMethodOrder"
    private const val TEST_CLASS_ORDER = "org.junit.jupiter.api.TestClassOrder"
    private const val ORDER_ANNOTATION = "OrderAnnotation"
    private const val DISPLAY_NAME = "DisplayName"

    fun methods(testClass: Class<*>, tests: List<DocumentedTest>, default: DocumentSectionOrder): List<DocumentedTest> {
        val byName = compareBy<DocumentedTest> { it.baseName }
        val byDisplayName = compareBy<DocumentedTest> { DisplayNames.of(it.method) ?: it.method.name }
        val byDefault = if (default == DocumentSectionOrder.DISPLAY_NAME) byDisplayName.then(byName) else byName

        val comparator = when (orderer(testClass, TEST_METHOD_ORDER)) {
            ORDER_ANNOTATION -> compareBy<DocumentedTest> { orderOf(it.method) }.then(byDefault)
            "MethodName", "Alphanumeric" -> byName
            DISPLAY_NAME -> byDisplayName.then(byDefault)
            else -> byDefault
        }

        return tests.sortedWith(comparator)
    }

    fun nestedClasses(testClass: Class<*>, nested: List<Class<*>>, default: DocumentSectionOrder): List<Class<*>> {
        val byName = compareBy<Class<*>> { it.simpleName }
        val byDisplayName = compareBy<Class<*>> { DisplayNames.titleOf(it) }
        val byDefault = if (default == DocumentSectionOrder.DISPLAY_NAME) byDisplayName.then(byName) else byName

        val comparator = when (orderer(testClass, TEST_CLASS_ORDER)) {
            ORDER_ANNOTATION -> compareBy<Class<*>> { orderOf(it) }.then(byDefault)
            "ClassName" -> byName
            DISPLAY_NAME -> byDisplayName.then(byDefault)
            else -> byDefault
        }

        return nested.sortedWith(comparator)
    }

    /** [element] 의 `@Order` 값. 없으면 [DEFAULT_ORDER]. */
    fun orderOf(element: AnnotatedElement): Int = element.annotations.firstOrNull { it.hasName(ORDER) }
        ?.memberValue() as? Int ?: DEFAULT_ORDER

    /**
     * [testClass]·상위 클래스·바깥 클래스 중 처음 찾은 [annotation] 의 JUnit 내장 orderer 단순 이름 (`OrderAnnotation` …).
     * 사용자 정의 orderer 면 null.
     */
    private fun orderer(testClass: Class<*>, annotation: String): String? =
        generateSequence(testClass) { it.enclosingClass }.flatMap { it.annotations.asSequence() }
            .firstOrNull { it.hasName(annotation) }
            ?.memberValue()
            .let { it as? Class<*> }
            ?.takeIf { it.name.startsWith(JUPITER_PACKAGE) }
            ?.simpleName
}
