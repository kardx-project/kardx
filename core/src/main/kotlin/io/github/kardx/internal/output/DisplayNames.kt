package io.github.kardx.internal.output

import io.github.kardx.internal.reflect.defaultClassLoader
import io.github.kardx.internal.reflect.hasName
import io.github.kardx.internal.reflect.loadClassOrNull
import io.github.kardx.internal.reflect.memberValue
import java.lang.reflect.AnnotatedElement
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * JUnit Jupiter 의 `@DisplayName` 을 이름으로 읽는다. 라이브러리에서 `@DisplayName` 을 읽는 곳은 모두 이것을 쓴다.
 * JUnit 은 `compileOnly` 라 클래스패스에 없을 수 있으므로(Kotest 전용 프로젝트) 클래스를 직접 참조하지 않는다.
 *
 * Kotest 스펙 클래스에 붙는 Kotest 의 `@DisplayName(name)`(`io.kotest.core.annotation.DisplayName`)도 같은 뜻으로 읽는다.
 * 둘 다 붙어 있으면 JUnit 것을 쓴다.
 */
internal object DisplayNames {
    private const val DISPLAY_NAME = "org.junit.jupiter.api.DisplayName"
    private const val KOTEST_DISPLAY_NAME = "io.kotest.core.annotation.DisplayName"

    /** (클래스 이름, 메서드 이름) → 메서드 표시 이름. 프레임마다 클래스를 다시 로드하지 않는다. 표시 이름이 없으면 빈 값 */
    private val stackCache = ConcurrentHashMap<Pair<String, String>, Optional<String>>()

    /** [element] 에 붙은 `@DisplayName` 값. 없으면 null. */
    fun of(element: AnnotatedElement): String? {
        val annotations = element.annotations

        return annotations.firstOrNull { it.hasName(DISPLAY_NAME) }
            ?.let { it.memberValue() as? String }
            ?: annotations.firstOrNull { it.hasName(KOTEST_DISPLAY_NAME) }
                ?.let { it.memberValue("name") as? String }
    }

    /** [clazz] 의 표시 이름. `@DisplayName` 이 없으면 클래스 단순 이름. */
    fun titleOf(clazz: Class<*>): String = of(clazz) ?: clazz.simpleName

    /**
     * [testClass] 와 상위 클래스에서 이름이 [methodName] 인 첫 메서드의 `@DisplayName`. 없으면 null.
     * 상속받은 테스트 메서드도 찾도록 상위 클래스까지 본다.
     */
    fun ofMethod(testClass: Class<*>, methodName: String): String? = generateSequence(testClass) { it.superclass }
        .flatMap { it.declaredMethods.asSequence() }
        .firstOrNull { it.name == methodName }
        ?.let(::of)

    /**
     * 호출 스택에서 `@DisplayName` 이 붙은 첫 메서드(테스트 메서드)의 표시 이름. 찾지 못하면 null.
     * 이 라이브러리의 확장 없이 REST Docs 확장만 쓰는 JUnit 테스트의 OpenAPI·AsyncAPI 요약에 쓴다.
     */
    fun methodFromCallStack(): String? {
        val loader = defaultClassLoader()

        return Thread.currentThread().stackTrace
            .asSequence()
            .map { frame -> stackCache.getOrPut(frame.className to frame.methodName) { ofFrame(frame, loader) } }
            .firstNotNullOfOrNull { it.orElse(null) }
    }

    private fun ofFrame(frame: StackTraceElement, loader: ClassLoader): Optional<String> {
        val clazz = loadClassOrNull(frame.className, loader) ?: return Optional.empty()
        val name = clazz.declaredMethods.filter { it.name == frame.methodName }
            .firstNotNullOfOrNull(::of)

        return Optional.ofNullable(name)
    }
}
