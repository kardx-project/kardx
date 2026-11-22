package com.example.restdocs.extraction

import com.example.restdocs.common.DemoAuth
import com.example.restdocs.notification.ConnectedEvent
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.eventMeans
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.type
import io.github.kardx.extractor.withFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.bearerToken
import io.github.kardx.mockmvc.performAsync
import io.github.kardx.mockmvc.restDocsMockMvc
import io.github.kardx.websocket.MessageDocumentation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.snippet.SnippetException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.context.WebApplicationContext

/** SSE / STOMP 문서화가 잘못된 문서를 거부하는지 검증한다. */
@ExtendWith(RestDocumentationExtension::class)
@SpringBootTest
@DisplayName("스트리밍 계약")
class StreamingContractTest {

    lateinit var mockMvc: MockMvc
    lateinit var provider: RestDocumentationContextProvider

    @BeforeEach
    fun setUp(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        // 계약 테스트는 문서가 아니므로 OpenAPI 스펙에 넣지 않는다.
        RestDocsConfiguration.configure { openApi.enabled = false }
        RestDocsConfiguration.configure { asyncApi.enabled = false }
        mockMvc = restDocsMockMvc(context, provider)
        this.provider = provider
    }

    @AfterEach
    fun tearDown() = RestDocsConfiguration.reset()

    /** 알림 스트림 요청. 샘플 앱은 데모 토큰을 요구한다 (DemoAuth) */
    private fun notificationStream() = get("/api/v1/notifications/stream").bearerToken(DemoAuth.TOKEN)

    @Test
    @DisplayName("문서화하지 않은 이벤트가 오면 실패")
    fun undocumentedEvent() {
        val error =
            assertThrows(SnippetException::class.java) {
                mockMvc.performAsync(notificationStream())
                    .andDocument {
                        events("connected" eventMeans "연결" withFields ConnectedEvent::class)
                    }
            }

        check("- notification" in error.message.orEmpty()) { error.message.orEmpty() }
    }

    @Test
    @DisplayName("relaxed 면 문서화하지 않은 이벤트 허용")
    fun relaxedEvents() {
        mockMvc.performAsync(notificationStream())
            .andDocument {
                relaxed = true
                events("connected" eventMeans "연결")
            }
    }

    @Test
    @DisplayName("필수 이벤트가 없으면 실패")
    fun missingEvent() {
        val error =
            assertThrows(SnippetException::class.java) {
                mockMvc.performAsync(notificationStream())
                    .andDocument {
                        relaxed = true
                        events("closed" eventMeans "종료")
                    }
            }

        check("- closed" in error.message.orEmpty()) { error.message.orEmpty() }
    }

    @Test
    @DisplayName("이벤트 data 필드가 틀리면 실패")
    fun wrongEventFields() {
        assertThrows(SnippetException::class.java) {
            mockMvc.performAsync(notificationStream())
                .andDocument {
                    relaxed = true
                    events("connected" eventMeans "연결" withFields listOf("missing" type NUMBER means "없는 필드"))
                }
        }
    }

    @Test
    @DisplayName("STOMP 필수 헤더와 페이로드 필드 검증")
    fun messageValidation() {
        val docs = MessageDocumentation(provider)

        assertThrows(SnippetException::class.java) {
            docs.send("/app/x", """{"a":1}""") { headers("Authorization" headerMeans "토큰") }
        }

        assertThrows(SnippetException::class.java) {
            docs.send("/app/x", """{"a":1}""") { fields("b" type NUMBER means "없는 필드") }
        }

        docs.send("/app/x", """{"a":1}""") { fields("a" type NUMBER means "있는 필드") }
    }
}
