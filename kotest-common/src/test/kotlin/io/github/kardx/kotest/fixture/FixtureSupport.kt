package io.github.kardx.kotest.fixture

import io.github.kardx.config.RestDocsConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockServletContext
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.util.concurrent.ConcurrentHashMap

/**
 * 고정 스펙은 테스트가 런처로 실행할 때만 켠다. Gradle 이 직접 실행하지 않게 한다.
 * 켜는 조건(`LaunchedOnly`)은 Kotest 버전마다 인터페이스가 달라 각 모듈의 테스트 소스에 있다.
 */
object FixtureLaunch {
    @Volatile
    var active: Boolean = false

    /** 고정 스펙의 테스트가 본 값 (테스트 이름 → 값) */
    val seen: MutableMap<String, Any?> = ConcurrentHashMap()

    /** 지금 설정의 `commonIncludeFiles` 를 [name] 으로 기록한다. */
    fun record(name: String) {
        seen[name] = RestDocsConfiguration.current.document.commonIncludeFiles
    }
}

@RestController
class HelloController {
    @GetMapping("/hello/{id}")
    fun hello(@PathVariable("id") id: Int): Map<String, Any> = mapOf("id" to id, "name" to "kim")
}

/** [HelloController] 하나만 띄운 REST Docs MockMvc. Spring 컨텍스트 없이 문서화한다. */
fun helloMockMvc(provider: RestDocumentationContextProvider): MockMvc =
    MockMvcBuilders.standaloneSetup(HelloController())
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(provider))
        .build()

@Configuration
@EnableWebMvc
@Import(HelloController::class)
class HelloWebConfig

/** [HelloController] 하나를 담은 서블릿 앱 컨텍스트. `restDocsMockMvc(context)` 처럼 컨텍스트를 받는 함수에 쓴다. */
fun helloWebContext(): WebApplicationContext = AnnotationConfigWebApplicationContext().apply {
    servletContext = MockServletContext()
    register(HelloWebConfig::class.java)
    refresh()
}
