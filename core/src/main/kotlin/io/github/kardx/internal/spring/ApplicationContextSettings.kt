package io.github.kardx.internal.spring

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.reflect.defaultClassLoader
import io.github.kardx.internal.reflect.loadClassOrNull
import org.springframework.context.ApplicationContext

/**
 * 테스트 클라이언트(`restDocsMockMvc`, `restDocsWebTestClient`)를 만들 때 앱 컨텍스트의 설정을 가져온다.
 * `kardx.*` 속성을 반영하고, 앱이 쓰는 Jackson 매퍼 빈을 현재 스레드의 기본 매퍼로 쓴다.
 */
internal fun applyApplicationContext(context: ApplicationContext) {
    applyEnvironmentSettings(context.environment)
    RestDocsConfiguration.threadObjectMapper = findMapperBean(context)
}

/** 앱이 쓰는 Jackson 매퍼 빈을 찾는다. Jackson 3 을 먼저 본다. */
private fun findMapperBean(context: ApplicationContext): Any? = MAPPER_BEAN_TYPES.firstNotNullOfOrNull { typeName ->
    val type = loadClassOrNull(typeName, context.classLoader ?: defaultClassLoader())
        ?: return@firstNotNullOfOrNull null

    // 후보가 여럿이고 @Primary 가 없으면 null (예외 없이 기본 매퍼로 간다)
    context.getBeanProvider(type).ifUnique
}

private val MAPPER_BEAN_TYPES =
    listOf("tools.jackson.databind.json.JsonMapper", "com.fasterxml.jackson.databind.ObjectMapper")
