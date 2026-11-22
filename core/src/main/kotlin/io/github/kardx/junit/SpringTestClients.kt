package io.github.kardx.junit

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.spring.SpringSettingsLoader
import io.github.kardx.mockmvc.restDocsMockMvc
import io.github.kardx.webtestclient.restDocsWebTestClient
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.springframework.context.ApplicationContext
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.context.WebApplicationContext
import java.lang.reflect.Method

/**
 * [AutoRestDocumentationExtension] 이 테스트 메서드·`@BeforeEach` 파라미터로 넣는 REST Docs 용 클라이언트.
 *
 * `MockMvc` 는 `restDocsMockMvc(context, provider)`, `WebTestClient` 는 `restDocsWebTestClient(context, provider)` 로 Spring 테스트
 * 컨텍스트(`SpringExtension` 과 같은 캐시)에서 만든다. 타입은 이름으로 비교한다. 서블릿(MockMvc)이나 WebFlux(WebTestClient)가
 * 없는 프로젝트에서 그 클래스를 읽지 않도록, 클래스를 참조하는 코드는 그 타입의 파라미터를 만들 때만 실행한다.
 *
 * Spring 이 넣는 파라미터(`@Autowired`·`@Qualifier`·`@Value`)와 생성자 파라미터는 건드리지 않는다.
 */
internal object SpringTestClients {
    private const val MOCK_MVC = "org.springframework.test.web.servlet.MockMvc"
    private const val WEB_TEST_CLIENT = "org.springframework.test.web.reactive.server.WebTestClient"
    private val TYPES = setOf(MOCK_MVC, WEB_TEST_CLIENT)

    /** Spring 이 직접 넣는 파라미터 표시. 있으면 SpringExtension 이 넣으므로 이 확장은 넣지 않는다 */
    private val SPRING_INJECTION = setOf(
        "org.springframework.beans.factory.annotation.Autowired",
        "org.springframework.beans.factory.annotation.Qualifier",
        "org.springframework.beans.factory.annotation.Value",
    )

    fun supports(parameterContext: ParameterContext): Boolean {
        val parameter = parameterContext.parameter

        return parameterContext.declaringExecutable is Method &&
            parameter.type.name in TYPES &&
            parameter.annotations.none { it.annotationClass.java.name in SPRING_INJECTION }
    }

    fun resolve(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
        provider: RestDocumentationContextProvider,
    ): Any {
        val typeName = parameterContext.parameter.type.simpleName
        val applicationContext = applicationContext(extensionContext, typeName)

        return when (parameterContext.parameter.type.name) {
            MOCK_MVC -> MockMvcParameter.create(applicationContext, provider)
            else -> restDocsWebTestClient(applicationContext, provider)
        }
    }

    private fun applicationContext(context: ExtensionContext, typeName: String): ApplicationContext {
        val testClass = context.requiredTestClass

        if (!SpringSettingsLoader.isSpringTest(testClass)) {
            throw ParameterResolutionException(
                RestDocsErrorCode.CLIENT_PARAMETER_NEEDS_SPRING.message(
                    "AutoRestDocumentationExtension builds the $typeName parameter from the Spring test context, " +
                        "but ${testClass.name} is not a Spring test. Add @SpringBootTest (or @WebMvcTest, " +
                        "@WebFluxTest), or build the client yourself and pass a RestDocumentationContextProvider " +
                        "parameter to restDocs(provider).",
                ),
            )
        }

        // 컨텍스트를 만들지 못하면 SpringExtension 의 예외(원인 포함)를 그대로 알린다
        return SpringExtension.getApplicationContext(context)
    }

    /** MockMvc 를 만드는 곳. 서블릿 클래스를 참조하므로 MockMvc 파라미터를 만들 때만 읽힌다. */
    private object MockMvcParameter {
        fun create(context: ApplicationContext, provider: RestDocumentationContextProvider): Any {
            val webContext = context as? WebApplicationContext ?: throw ParameterResolutionException(
                RestDocsErrorCode.CLIENT_PARAMETER_NEEDS_SPRING.message(
                    "a MockMvc parameter needs a servlet web application context, but the test context is " +
                        "${context.javaClass.name}. Use @SpringBootTest with the default MOCK web environment or " +
                        "@WebMvcTest, or take a WebTestClient parameter for WebFlux.",
                ),
            )

            return restDocsMockMvc(webContext, provider)
        }
    }
}
