package io.github.kardx.internal.jackson

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.reflect.loadClassOrNull

/** [io.github.kardx.config.ExtractionSettings.objectMapper] 와 컨텍스트 매퍼가 모두 없을 때 쓰는 기본 Jackson 매퍼. */
internal object DefaultObjectMapper {
    /**
     * 클래스패스의 Jackson 으로 기본 매퍼를 만든다. Jackson 3 을 먼저, 없으면 Jackson 2 를 쓴다.
     * (Spring Boot 4 앱에도 springdoc 등이 Jackson 2 를 가져오므로, 앱이 쓰는 Jackson 3 을 우선한다.)
     */
    val instance: Any by lazy {
        jackson3Mapper() ?: jackson2Mapper() ?: error(
            RestDocsErrorCode.NO_JACKSON.message(
                "Jackson ObjectMapper/JsonMapper not found on classpath. " +
                    "Set it with RestDocsConfiguration.configure { extraction { objectMapper = ObjectMapperProvider { mapper } } }.",
            ),
        )
    }

    private fun jackson2Mapper(): Any? {
        val mapperClass = loadClassOrNull("com.fasterxml.jackson.databind.ObjectMapper") ?: return null

        val mapper = mapperClass.getDeclaredConstructor()
            .newInstance()

        mapperClass.getMethod("findAndRegisterModules")
            .invoke(mapper)

        return mapper
    }

    private fun jackson3Mapper(): Any? {
        val mapperClass = loadClassOrNull("tools.jackson.databind.json.JsonMapper") ?: return null

        val builder = mapperClass.getMethod("builder")
            .invoke(null)
        val withModules = builder.javaClass.getMethod("findAndAddModules")
            .invoke(builder)

        return withModules.javaClass.getMethod("build")
            .invoke(withModules)
    }
}
