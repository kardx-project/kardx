package io.github.kardx.resttestclient

import io.github.kardx.internal.coverage.MappedEndpoints
import io.github.kardx.internal.openapi.HandlerApiVersion
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockServletContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@RestController
class VersionedController {
    @GetMapping("/versioned/users", version = "1.1")
    fun usersV11(): List<String> = listOf("v1.1")

    @GetMapping("/versioned/users", version = "1.2+")
    fun usersV12(): List<String> = listOf("v1.2")

    @GetMapping("/versioned/health")
    fun health(): Map<String, String> = mapOf("status" to "UP")
}

@RestController
@RequestMapping(version = "2")
class ClassVersionedController {
    @GetMapping("/versioned/items")
    fun items(): List<String> = emptyList()
}

@Configuration(proxyBeanMethods = false)
@EnableWebMvc
class VersionedConfig : WebMvcConfigurer {
    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer.useRequestHeader("X-API-Version")
            .setVersionRequired(false)
    }

    @Bean
    fun versionedController(): VersionedController = VersionedController()

    @Bean
    fun classVersionedController(): ClassVersionedController = ClassVersionedController()
}

private fun handler(bean: Any, name: String): HandlerMethod = HandlerMethod(bean, bean.javaClass.getMethod(name))

class ApiVersionTest :
    BehaviorSpec({
        given("Spring Framework 7 의 version 을 선언한 핸들러") {
            then("메서드의 버전, 기준 버전(+)은 + 를 뗀 값, 없으면 null") {
                HandlerApiVersion.of(handler(VersionedController(), "usersV11")) shouldBe "1.1"
                HandlerApiVersion.of(handler(VersionedController(), "usersV12")) shouldBe "1.2"
                HandlerApiVersion.of(handler(VersionedController(), "health")) shouldBe null
            }

            then("메서드에 없으면 컨트롤러의 @RequestMapping(version) 이다") {
                HandlerApiVersion.of(handler(ClassVersionedController(), "items")) shouldBe "2"
            }
        }

        given("API 버전을 쓰는 Spring MVC 앱 컨텍스트") {
            val context = AnnotationConfigWebApplicationContext().apply {
                servletContext = MockServletContext()
                register(VersionedConfig::class.java)
                refresh()
            }

            then("엔드포인트 인벤토리에 버전마다 한 줄이 생긴다") {
                MappedEndpoints.of(context).map { "${it.method} ${it.path} ${it.apiVersion}" } shouldContainExactly
                    listOf(
                        "GET /versioned/health null",
                        "GET /versioned/items 2",
                        "GET /versioned/users 1.1",
                        "GET /versioned/users 1.2",
                    )
            }
        }
    })
