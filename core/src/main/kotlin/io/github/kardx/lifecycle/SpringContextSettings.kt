package io.github.kardx.lifecycle

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.spring.applyApplicationContext
import org.springframework.context.ApplicationContext

/**
 * 테스트 프레임워크의 Spring 연동에서 얻은 애플리케이션 컨텍스트를 문서화 설정에 반영한다. JUnit 확장은 JUnit 의
 * `SpringExtension` 에서, Kotest 확장은 Kotest Spring 확장에서 컨텍스트를 얻는다.
 */
@RestDocsExtensionApi
public object SpringContextSettings {
    /**
     * [context] 의 `kardx.*` 속성(yml·`@TestPropertySource` 등)을 기본값에 반영하고, 앱의 Jackson 매퍼 빈을 이 스레드의
     * 매퍼로 쓴다. `restDocsMockMvc(context, ...)` 가 하는 일과 같다. 같은 컨텍스트는 한 번만 읽는다.
     *
     * 속성 값은 JVM 전역 기본값 하나에 반영되므로, 값이 다른 컨텍스트의 테스트를 병렬로 돌릴 때의 제한은
     * [RestDocsConfiguration] 의 "병렬 실행 제한" 과 같다.
     */
    public fun apply(context: ApplicationContext) {
        applyApplicationContext(context)
    }
}
