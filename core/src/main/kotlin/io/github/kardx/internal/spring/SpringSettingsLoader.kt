package io.github.kardx.internal.spring

import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.BootstrapWith
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestContextAnnotationUtils
import org.springframework.test.context.junit.jupiter.SpringExtension

/**
 * Spring 테스트면 테스트 컨텍스트의 `Environment` 에서 `kardx.*` 속성을 읽는다.
 *
 * 컨텍스트는 Spring 의 `SpringExtension` 과 같은 캐시에서 얻으므로 새로 만들지 않는다.
 * Spring 테스트가 아니거나 컨텍스트를 만들 수 없으면 아무것도 하지 않는다. (실패는 `SpringExtension` 이 알린다)
 */
internal object SpringSettingsLoader {
    fun load(context: ExtensionContext) {
        val testClass = context.testClass.orElse(null) ?: return

        if (!isSpringTest(testClass)) return

        // 컨텍스트를 만들지 못한 실패는 SpringExtension 이 테스트에서 알리므로 여기서는 건너뛴다
        val applicationContext = runCatching { SpringExtension.getApplicationContext(context) }
            .getOrNull()
            ?: return

        applyEnvironmentSettings(applicationContext.environment)
    }

    /**
     * `@SpringBootTest` 류(`@BootstrapWith` 메타)와 `@ExtendWith(SpringExtension) @ContextConfiguration` 조합 모두.
     * 어노테이션을 읽지 못하면(클래스패스에 없는 메타 어노테이션 등) Spring 테스트가 아닌 것으로 본다.
     */
    fun isSpringTest(testClass: Class<*>): Boolean = runCatching {
        TestContextAnnotationUtils.hasAnnotation(testClass, BootstrapWith::class.java) ||
            TestContextAnnotationUtils.hasAnnotation(testClass, ContextConfiguration::class.java)
    }.getOrDefault(false)
}
