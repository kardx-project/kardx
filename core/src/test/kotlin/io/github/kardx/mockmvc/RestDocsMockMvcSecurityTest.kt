package io.github.kardx.mockmvc

import io.github.kardx.config.RestDocsConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockServletContext
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

@RestController
open class SecuredController {
    @GetMapping("/secure")
    open fun secure(): String = "secret"
}

/** `springSecurityFilterChain` 이름의 필터만 있는 앱. Authorization 헤더가 없으면 401 이다. */
@Configuration
@EnableWebMvc
open class SecuredWebConfig {
    @Bean
    open fun securedController(): SecuredController = SecuredController()

    @Bean(name = ["springSecurityFilterChain"])
    open fun springSecurityFilterChain(): Filter = Filter { request, response, chain ->
        if ((request as HttpServletRequest).getHeader("Authorization") == null) {
            (response as HttpServletResponse).status = HttpServletResponse.SC_UNAUTHORIZED
        } else {
            chain.doFilter(request, response)
        }
    }
}

private fun securedContext(): AnnotationConfigWebApplicationContext = AnnotationConfigWebApplicationContext().apply {
    servletContext = MockServletContext()
    register(SecuredWebConfig::class.java)
    refresh()
}

private fun status(context: AnnotationConfigWebApplicationContext, authorization: String? = null): Int {
    val documentation = ManualRestDocumentation(Files.createTempDirectory("security").toString())
    val request = get("/secure")

    authorization?.let { request.header("Authorization", it) }
    documentation.beforeTest(RestDocsMockMvcSecurityTest::class.java, "secure")

    try {
        return restDocsMockMvc(context, documentation).perform(request)
            .andReturn()
            .response
            .status
    } finally {
        documentation.afterTest()
    }
}

/** 라이브러리 MockMvc 로거(System.Logger → JUL)의 경고를 모은다. */
private class WarningRecorder : Handler() {
    val messages = mutableListOf<String>()

    override fun publish(record: LogRecord) {
        if (record.level == Level.WARNING) messages += record.message
    }

    override fun flush() = Unit

    override fun close() = Unit
}

/** [block] 이 System.out 에 쓴 내용. */
private fun stdout(block: () -> Unit): String {
    val original = System.out
    val buffer = ByteArrayOutputStream()

    System.setOut(PrintStream(buffer, true, Charsets.UTF_8))

    try {
        block()
    } finally {
        System.setOut(original)
    }

    return buffer.toString(Charsets.UTF_8)
}

class RestDocsMockMvcSecurityTest :
    BehaviorSpec({
        val warnings = WarningRecorder()
        // JUL 은 로거를 약하게 참조하므로 스펙이 끝날 때까지 붙잡아 둔다
        val logger = Logger.getLogger("io.github.kardx.mockmvc")

        logger.addHandler(warnings)
        afterSpec { logger.removeHandler(warnings) }

        given("컨텍스트에 springSecurityFilterChain 이 있을 때") {
            val context = securedContext()

            then("기본으로는 필터 체인을 걸지 않는다") {
                status(context) shouldBe HttpServletResponse.SC_OK
            }

            then("http.applySpringSecurity = true 면 걸어 인증 없는 요청은 401 이다") {
                RestDocsConfiguration.withSettings({ http.applySpringSecurity = true }) {
                    status(context) shouldBe HttpServletResponse.SC_UNAUTHORIZED
                    status(context, "Bearer token") shouldBe HttpServletResponse.SC_OK
                }
            }
        }

        given("http.applySpringSecurity 를 정하지 않았을 때") {
            then("필터 체인을 걸지 않는다는 경고를 JVM 마다 한 번 남긴다") {
                val context = securedContext()

                status(context)
                status(context)

                val warning = warnings.messages.single()

                warning shouldContain "springSecurityFilterChain"
                warning shouldContain "kardx.apply-spring-security: true"
                warning shouldContain "http { applySpringSecurity = true }"
                warning shouldContain "set it to false explicitly"
            }

            then("false 로 명시하면 정한 것으로 보고, 설정 복사본에도 남는다") {
                RestDocsConfiguration.withSettings({}) {
                    RestDocsConfiguration.current.http.applySpringSecurityDecided shouldBe false
                }

                RestDocsConfiguration.withSettings({ http.applySpringSecurity = false }) {
                    RestDocsConfiguration.current.http.applySpringSecurityDecided shouldBe true

                    RestDocsConfiguration.withSettings({}) {
                        RestDocsConfiguration.current.http.applySpringSecurityDecided shouldBe true
                    }
                }
            }
        }

        given("http.printRequests") {
            then("요청마다 읽어 MockMvc 를 만든 뒤에 켜도 출력한다") {
                val context = securedContext()
                val documentation = ManualRestDocumentation(Files.createTempDirectory("print").toString())
                val mockMvc = restDocsMockMvc(context, documentation)
                val request = get("/secure").header("Authorization", "Bearer token")

                documentation.beforeTest(RestDocsMockMvcSecurityTest::class.java, "print")

                try {
                    stdout { mockMvc.perform(request) } shouldNotContain "MockHttpServletRequest"
                    RestDocsConfiguration.withSettings({ http.printRequests = true }) {
                        stdout { mockMvc.perform(request) } shouldContain "MockHttpServletRequest"
                    }
                } finally {
                    documentation.afterTest()
                }
            }
        }
    })
