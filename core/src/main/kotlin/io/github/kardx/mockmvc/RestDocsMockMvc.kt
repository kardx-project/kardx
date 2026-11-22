package io.github.kardx.mockmvc

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.documentation.CachedTemplateEngine
import io.github.kardx.internal.output.OpenRuns
import io.github.kardx.internal.reflect.defaultClassLoader
import io.github.kardx.internal.reflect.loadClassOrNull
import io.github.kardx.internal.spring.applyApplicationContext
import jakarta.servlet.Filter
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultHandler
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.MockMvcConfigurer
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.filter.CharacterEncodingFilter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UTF-8 인코딩 필터가 적용된 REST Docs 용 [MockMvc] 를 만든다.
 * 요청·응답 본문 pretty print, 헤더 마스킹, URI 를 `http://localhost` 로 맞추는 것은 [andDocument] 의 전처리가 하고,
 * 그 뒤에 [io.github.kardx.config.HttpSettings.http.requestPreprocessors]·`responsePreprocessors` 를 적용한다.
 * [io.github.kardx.config.HttpSettings.http.printRequests] 가 true 이면 요청·응답을 콘솔에 출력한다. 이 값은 요청마다 읽는다.
 *
 * [io.github.kardx.config.HttpSettings.http.applySpringSecurity] 를 켜면(기본값 false, yml
 * `kardx.apply-spring-security: true`) 컨텍스트의 Spring Security 필터 체인(`springSecurityFilterChain` 빈)을
 * `@AutoConfigureMockMvc` 처럼 건다. spring-security-test 가 있으면 `springSecurity()` 를 적용해
 * `with(user("hong"))`·`with(jwt())`·`with(csrf())` 를 쓸 수 있다. 이 값은 이 함수를 부를 때 읽는다. 필터 체인 빈이 있는데
 * 값을 정하지 않았으면 JVM 마다 한 번 경고한다 (의도라면 false 로 명시한다).
 *
 * 컨텍스트에 Jackson 매퍼 빈(Jackson 3 `JsonMapper`, 없으면 Jackson 2 `ObjectMapper`)이 있으면 현재 스레드의
 * 기본 매퍼로 쓴다. [jsonContent], [andReturnAsObject], 필드 이름 규칙이 앱과 같은 설정을 따른다.
 * [io.github.kardx.config.ExtractionSettings.objectMapper] 를 직접 설정하면 그 값이 우선한다.
 *
 * ```kotlin
 * @BeforeEach
 * fun setUp(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
 *     mockMvc = restDocsMockMvc(context, provider)
 * }
 * ```
 */
public fun restDocsMockMvc(context: WebApplicationContext, provider: RestDocumentationContextProvider): MockMvc {
    applyApplicationContext(context)

    return MockMvcBuilders.webAppContextSetup(context)
        // 요청·응답 가공(pretty print 등)은 andDocument 의 전처리가 한다. 사용자 전처리기를 그 뒤에 두려면 기본 전처리기를 쓰지 않는다
        .apply<DefaultMockMvcBuilder>(
            MockMvcRestDocumentation.documentationConfiguration(provider)
                .templateEngine(CachedTemplateEngine),
        )
        .addFilter<DefaultMockMvcBuilder>(CharacterEncodingFilter("UTF-8", true))
        .withSpringSecurity(context)
        .alwaysDo<DefaultMockMvcBuilder>(PrintWhenEnabled)
        .build()
}

/**
 * 지금 실행 중인 테스트의 스니펫 자리로 문서화하는 [MockMvc] 를 만든다. `restDocsMockMvc(context, provider)` 와 같고, provider 대신
 * 요청마다 그때 실행 중인 테스트(JUnit `AutoRestDocumentationExtension` 의 테스트 메서드, Kotest `RestDocsExtension` 의 리프
 * 테스트)를 찾는다. 그래서 provider 를 받는 `@BeforeEach` 나 기본 클래스 없이 필드·생성자·스펙 본문에서 한 번 만들어 둘 수 있다.
 *
 * 테스트 스레드(Kotest 는 테스트 코루틴)에서 보낸 요청은 그 테스트로 찾는다. 테스트가 만든 다른 스레드에서 보내면 실행 중인
 * 테스트가 하나뿐일 때만 그 테스트로 찾는다. 찾지 못하면(확장을 등록하지 않음, 테스트 밖에서 요청) 요청할 때
 * [IllegalStateException] (`KDX-013`)이다.
 *
 * ```kotlin
 * @SpringBootTest
 * @ExtendWith(AutoRestDocumentationExtension::class)
 * class UserApiDocsTest(@Autowired context: WebApplicationContext) {
 *     private val mockMvc = restDocsMockMvc(context)
 *     ...
 * }
 *
 * // Kotest (프로젝트 설정에 RestDocsExtension 을 등록한 경우도 같다)
 * class UserApiDocsSpec(context: WebApplicationContext) : FunSpec({
 *     extension(RestDocsExtension())
 *     val mockMvc = restDocsMockMvc(context)
 *     ...
 * })
 * ```
 *
 * JUnit 에서는 테스트 메서드 파라미터로 받는 것이 가장 짧다
 * (`fun getUser(mockMvc: MockMvc)`, [io.github.kardx.junit.AutoRestDocumentationExtension]).
 */
public fun restDocsMockMvc(context: WebApplicationContext): MockMvc = restDocsMockMvc(context, OpenRuns.currentProvider)

/** [io.github.kardx.config.HttpSettings.http.printRequests] 를 요청마다 읽어 켜져 있으면 요청·응답을 출력한다. */
private object PrintWhenEnabled : ResultHandler {
    override fun handle(result: MvcResult) {
        if (RestDocsConfiguration.current.http.printRequests) {
            MockMvcResultHandlers.print()
                .handle(result)
        }
    }
}

/**
 * 컨텍스트의 Spring Security 필터 체인을 건다. spring-security-test 의 `springSecurity()` 가 있으면 그것을,
 * 없으면 필터 체인 빈을 필터로 바로 더한다 (요청이 실제 앱처럼 인증을 거친다).
 */
private fun DefaultMockMvcBuilder.withSpringSecurity(context: WebApplicationContext): DefaultMockMvcBuilder {
    if (!RestDocsConfiguration.current.http.applySpringSecurity || !context.containsBean(SECURITY_FILTER_CHAIN)) {
        warnIfSecurityUndecided(context)

        return this
    }

    val configurer = loadClassOrNull(SECURITY_CONFIGURERS, context.classLoader ?: defaultClassLoader())
        ?.getMethod("springSecurity")
        ?.invoke(null) as? MockMvcConfigurer

    if (configurer != null) {
        return apply(configurer)
    }

    LOGGER.log(
        System.Logger.Level.INFO,
        RestDocsErrorCode.SECURITY_TEST_MISSING.message(
            "spring-security-test not found; applying springSecurityFilterChain as a plain filter.",
        ),
    )

    return addFilters(context.getBean(SECURITY_FILTER_CHAIN, Filter::class.java))
}

/**
 * 필터 체인 빈이 있는데 [io.github.kardx.config.HttpSettings.http.applySpringSecurity] 를 정하지 않아 걸지 않을 때
 * JVM 마다 한 번 알린다. 테스트 클래스마다 MockMvc 를 만들어도 한 번이다. 값을 정했으면(false 포함) 의도로 보고 알리지 않는다.
 */
private fun warnIfSecurityUndecided(context: WebApplicationContext) {
    val decided = RestDocsConfiguration.current.http.applySpringSecurityDecided

    if (decided || !context.containsBean(SECURITY_FILTER_CHAIN) || !SECURITY_WARNED.compareAndSet(false, true)) {
        return
    }

    LOGGER.log(
        System.Logger.Level.WARNING,
        RestDocsErrorCode.SECURITY_NOT_APPLIED.message(
            "the application context has a springSecurityFilterChain bean, but restDocsMockMvc does " +
                "not apply it, so requests are documented without authentication (no 401/403). To document the API " +
                "as secured, set kardx.apply-spring-security: true (or RestDocsConfiguration.configure " +
                "{ http { applySpringSecurity = true } }) before restDocsMockMvc is called. If this is intended, " +
                "set it to false explicitly to silence this warning.",
        ),
    )
}

private const val SECURITY_FILTER_CHAIN = "springSecurityFilterChain"
private const val SECURITY_CONFIGURERS =
    "org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers"
private val LOGGER: System.Logger = System.getLogger("io.github.kardx.mockmvc")
private val SECURITY_WARNED = AtomicBoolean()
