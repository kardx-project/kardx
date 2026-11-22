package io.github.kardx.kotest

import io.kotest.extensions.spring.SpringTestContextCoroutineContextElement
import kotlinx.coroutines.currentCoroutineContext
import org.springframework.context.ApplicationContext

/**
 * Kotest Spring 확장(`io.kotest:kotest-extensions-spring`)이 지금 스펙에 만든 애플리케이션 컨텍스트를 찾는다.
 *
 * Spring 확장은 스펙을 실행하는 코루틴 문맥에 `TestContextManager` 를 둔다. 그 확장이 클래스패스에 없거나 스펙에 등록되지
 * 않았으면 null 이다. 클래스가 없을 때 `NoClassDefFoundError` 가 나지 않도록 Spring 확장의 클래스는 [KotestSpring] 안에서만
 * 참조하고, 클래스가 있는지 먼저 확인한 뒤에 부른다.
 */
internal object SpringContexts {
    private const val SPRING_ELEMENT = "io.kotest.extensions.spring.SpringTestContextCoroutineContextElement"
    private const val TEST_CONTEXT_MANAGER = "org.springframework.test.context.TestContextManager"

    private val available: Boolean by lazy {
        listOf(SPRING_ELEMENT, TEST_CONTEXT_MANAGER).all(::isLoadable)
    }

    /** 지금 코루틴의 Spring 테스트 컨텍스트. 없거나 만들지 못했으면 null (실패는 Spring 확장이 테스트에서 알린다). */
    suspend fun current(): ApplicationContext? = if (available) KotestSpring.applicationContext() else null

    private fun isLoadable(className: String): Boolean = try {
        Class.forName(className, false, SpringContexts::class.java.classLoader)

        true
    } catch (ignored: ClassNotFoundException) {
        // 선택 의존성이 없는 것은 정상이다. Spring 연동 없이 코드 설정만 쓴다
        false
    } catch (ignored: LinkageError) {
        // 클래스는 있지만 의존 클래스(Spring Test 등)가 없어 읽지 못하는 경우도 연동 없이 진행한다
        false
    }
}

/** Kotest Spring 확장의 클래스를 참조하는 곳. [SpringContexts] 가 클래스가 있을 때만 부른다. */
private object KotestSpring {
    suspend fun applicationContext(): ApplicationContext? {
        val manager = currentCoroutineContext()[SpringTestContextCoroutineContextElement]?.value ?: return null

        return runCatching { manager.testContext.applicationContext }.getOrNull()
    }
}
