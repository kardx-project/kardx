package io.github.kardx.internal.coverage

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.webtestclient.andDocument
import io.github.kardx.webtestclient.recordHandlers
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockServletContext
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.io.File
import java.nio.file.Files
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

@RestController
@RequestMapping("/inventory")
class InventoryController {
    @GetMapping("/users/{id}")
    fun user(@PathVariable id: Long): Map<String, Long> = mapOf("id" to id)

    @Deprecated("use user")
    @DeleteMapping("/users/{id}", "/members/{id}")
    fun remove(@PathVariable id: Long): Map<String, Long> = mapOf("id" to id)

    @RequestMapping("/ping")
    fun ping(): Map<String, String> = mapOf("pong" to "ok")
}

@Configuration(proxyBeanMethods = false)
@EnableWebMvc
class InventoryMvcConfig {
    @Bean
    fun inventoryController(): InventoryController = InventoryController()
}

@Configuration(proxyBeanMethods = false)
@EnableWebFlux
class InventoryWebFluxConfig {
    @Bean
    fun inventoryController(): InventoryController = InventoryController()
}

class InventoryDocs

/** 문서화한 출력 디렉터리의 인벤토리 파일들. */
private fun inventories(output: File): List<File> = File(output, EndpointInventory.DIRECTORY).listFiles()
    .orEmpty()
    .filter { it.name.endsWith(".json") }

@Suppress("UNCHECKED_CAST")
private fun endpointsIn(file: File): List<Map<String, Any?>> =
    ObjectMapper().readValue(file, Map::class.java)["endpoints"] as List<Map<String, Any?>>

/** [block] 을 문서화 문맥 하나([method]) 안에서 부른다. */
private fun ManualRestDocumentation.documenting(method: String, block: () -> Unit) {
    beforeTest(InventoryDocs::class.java, method)

    try {
        block()
    } finally {
        afterTest()
    }
}

class EndpointInventoryTest :
    BehaviorSpec({
        given("앱 컨텍스트에 묶은 MockMvc (webAppContextSetup)") {
            val output = Files.createTempDirectory("inventory-mvc").toFile()
            val documentation = ManualRestDocumentation(output.absolutePath)
            val context = AnnotationConfigWebApplicationContext().apply {
                servletContext = MockServletContext()
                register(InventoryMvcConfig::class.java)
                refresh()
            }
            val mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(documentationConfiguration(documentation))
                .build()

            documentation.documenting("first") { mockMvc.perform(get("/inventory/users/{id}", 1)).andDocument() }
            documentation.documenting("second") { mockMvc.perform(get("/inventory/ping")).andDocument() }

            then("컨텍스트마다 인벤토리 파일 하나를 남긴다") {
                inventories(output) shouldHaveSize 1
            }

            then("메서드 × 경로마다 한 줄이고, 메서드 없는 매핑은 * 다") {
                val endpoints = endpointsIn(inventories(output).single())

                endpoints.map { "${it["method"]} ${it["path"]}" } shouldContainExactly listOf(
                    "DELETE /inventory/members/{id}",
                    "* /inventory/ping",
                    "DELETE /inventory/users/{id}",
                    "GET /inventory/users/{id}",
                )
                endpoints.first() shouldBe mapOf(
                    "method" to "DELETE",
                    "path" to "/inventory/members/{id}",
                    "handler" to "${InventoryController::class.java.name}#remove",
                    "deprecated" to true,
                    "apiVersion" to null,
                )
            }

            then("같은 앱이면 같은 id 라 다른 컨텍스트도 같은 파일을 쓴다") {
                val other = AnnotationConfigWebApplicationContext().apply {
                    servletContext = MockServletContext()
                    register(InventoryMvcConfig::class.java)
                    refresh()
                }

                EndpointInventory.record(other, output)

                inventories(output) shouldHaveSize 1
            }
        }

        given("standalone MockMvc") {
            val output = Files.createTempDirectory("inventory-standalone").toFile()
            val documentation = ManualRestDocumentation(output.absolutePath)
            val mockMvc = MockMvcBuilders.standaloneSetup(InventoryController())
                .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
                .build()

            documentation.documenting("standalone") { mockMvc.perform(get("/inventory/ping")).andDocument() }

            then("앱 전체가 아니므로 인벤토리를 남기지 않는다") {
                inventories(output) shouldHaveSize 0
            }
        }

        given("WebFlux 앱 컨텍스트에 묶은 WebTestClient") {
            val output = Files.createTempDirectory("inventory-webflux").toFile()
            val documentation = ManualRestDocumentation(output.absolutePath)
            val context = AnnotationConfigApplicationContext(InventoryWebFluxConfig::class.java)
            val client = WebTestClient.bindToApplicationContext(context)
                .recordHandlers()
                .configureClient()
                .filter(WebTestClientRestDocumentation.documentationConfiguration(documentation))
                .build()

            documentation.documenting("webflux") {
                client.get()
                    .uri("/inventory/ping")
                    .exchange()
                    .expectBody()
                    .andDocument()
            }

            then("리액티브 매핑으로 인벤토리를 남긴다") {
                endpointsIn(inventories(output).single()).map { "${it["method"]} ${it["path"]}" } shouldContainExactly
                    listOf(
                        "DELETE /inventory/members/{id}",
                        "* /inventory/ping",
                        "DELETE /inventory/users/{id}",
                        "GET /inventory/users/{id}",
                    )
            }
        }

        given("컨트롤러에 묶은 WebTestClient (bindToController)") {
            val output = Files.createTempDirectory("inventory-controller").toFile()
            val documentation = ManualRestDocumentation(output.absolutePath)
            val client = WebTestClient.bindToController(InventoryController())
                .recordHandlers()
                .configureClient()
                .filter(WebTestClientRestDocumentation.documentationConfiguration(documentation))
                .build()

            documentation.documenting("controller") {
                client.get()
                    .uri("/inventory/ping")
                    .exchange()
                    .expectBody()
                    .andDocument()
            }

            then("standalone 이므로 인벤토리를 남기지 않는다") {
                inventories(output) shouldHaveSize 0
            }
        }

        given("인벤토리 파일을 쓸 수 없는 출력 디렉터리") {
            val output = Files.createTempDirectory("inventory-blocked").toFile()
            // 디렉터리가 있어야 할 자리에 파일을 두어 쓰기를 실패하게 한다
            val blocker = File(output, EndpointInventory.DIRECTORY.substringBefore('/')).apply { writeText("") }
            val context = AnnotationConfigWebApplicationContext().apply {
                servletContext = MockServletContext()
                register(InventoryMvcConfig::class.java)
                refresh()
            }

            then("문서화 테스트를 실패시키지 않고, 쓸 수 있게 되면 다시 시도한다") {
                shouldNotThrowAny { EndpointInventory.record(context, output) }

                blocker.delete() shouldBe true
                EndpointInventory.record(context, output)

                inventories(output) shouldHaveSize 1
            }
        }

        given("계속 쓸 수 없는 출력 디렉터리") {
            val output = Files.createTempDirectory("inventory-always-blocked").toFile()

            File(output, EndpointInventory.DIRECTORY.substringBefore('/')).writeText("")

            val context = AnnotationConfigWebApplicationContext().apply {
                servletContext = MockServletContext()
                register(InventoryMvcConfig::class.java)
                refresh()
            }
            val records = mutableListOf<LogRecord>()
            val handler = object : Handler() {
                override fun publish(record: LogRecord) {
                    records += record
                }

                override fun flush() = Unit

                override fun close() = Unit
            }
            val logger = Logger.getLogger(EndpointInventory::class.java.name)

            logger.addHandler(handler)

            try {
                repeat(5) { EndpointInventory.record(context, output) }
            } finally {
                logger.removeHandler(handler)
            }

            then("문서화 요청마다 다시 시도하지 않고 한 번만 더 시도한 뒤 그만둔다") {
                records shouldHaveSize 2
            }

            then("스택 트레이스는 처음 한 번만 남긴다") {
                records.map { it.thrown != null } shouldContainExactly listOf(true, false)
            }
        }
    })
