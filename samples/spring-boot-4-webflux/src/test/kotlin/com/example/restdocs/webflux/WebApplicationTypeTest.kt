package com.example.restdocs.webflux

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.context.reactive.ReactiveWebApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

/**
 * 테스트 클래스패스에 Spring MVC 가 섞이면 `@SpringBootTest` 가 이 앱을 서블릿 앱으로 띄운다.
 * 플러그인의 starter-web 제외가 빠지거나 의존성(라이브러리 본체 포함)이 MVC 를 새로 끌고 오면 여기서 바로 실패한다.
 * 이 클래스패스에서 다른 문서화 테스트(JUnit·Kotest, WebTestClient·SSE)가 통과하므로 라이브러리가 MockMvc 없이 동작하는 것도 함께
 * 확인된다.
 */
@SpringBootTest
@DisplayName("웹 애플리케이션 종류")
class WebApplicationTypeTest {

    @Test
    @DisplayName("테스트 컨텍스트가 서블릿이 아니라 리액티브 웹 앱이다")
    fun reactiveContext(@Autowired context: ApplicationContext) {
        assertThat(context).isInstanceOf(ReactiveWebApplicationContext::class.java)
    }

    @Test
    @DisplayName("테스트 클래스패스에 MockMvc·Spring MVC·서블릿 API 가 없다")
    fun noServletStack() {
        val servletClasses = listOf(
            "org.springframework.restdocs.mockmvc.MockMvcRestDocumentation",
            "org.springframework.web.servlet.DispatcherServlet",
            "jakarta.servlet.Filter",
        )

        assertThat(servletClasses.filter { ClassUtils.isPresent(it, javaClass.classLoader) }).isEmpty()
    }
}
