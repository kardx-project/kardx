// StompTestClient·WebSocketHandlerTester 는 실험 API 다 (ExperimentalRestDocsApi)
@file:OptIn(ExperimentalRestDocsApi::class)

package com.example.restdocs.kotest.echo

import com.example.restdocs.echo.EchoHandler
import com.example.restdocs.echo.EchoRequest
import com.example.restdocs.echo.EchoResponse
import com.example.restdocs.echo.WelcomeMessage
import com.example.restdocs.kotest.support.MirrorDocsConfig
import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.dsl.MessageProtocol
import io.github.kardx.extractor.fields
import io.github.kardx.websocket.MessageDocumentation
import io.github.kardx.websocket.WebSocketHandlerTester
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * [com.example.restdocs.echo.EchoWebSocketDocsTest] 를 Kotest 로 옮긴 스펙. 서버 없이 `WebSocketHandlerTester` 로 핸들러를
 * 직접 부르고, 핸들러가 보낸 메시지를 `MessageDocumentation` 으로 문서화한다.
 * Spring 컨텍스트는 JUnit 판처럼 yml 의 `kardx.*`(문서 머리말 등)을 읽으려고 띄운다.
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("에코 (WebSocket)")
class EchoWebSocketDocsSpec :
    FunSpec({
        val restDocs = extension(MirrorDocsConfig.restDocsExtension())

        beforeEach { MirrorDocsConfig.apply() }

        test("연결하고 문자열 보내기") {
            WebSocketHandlerTester(EchoHandler(), path = "/ws/echo", attributes = mapOf("userId" to "hong")).use { ws ->
                val welcome = ws.connect().single()
                val request = """{"text":"hello"}"""
                val reply = ws.send(request).single()

                val docs = MessageDocumentation(restDocs.provider, MessageProtocol.WEBSOCKET)

                docs.subscribe("/ws/echo", payload = welcome) {
                    description = "연결 직후 한 번 받는다. 샘플 앱은 핸드셰이크에 데모 토큰을 요구한다: " +
                        "`?access_token=demo-token` 또는 `Authorization: Bearer demo-token`."
                    name = "welcome"
                    fields<WelcomeMessage>()
                }

                docs.send("/ws/echo", payload = request) {
                    description = "JSON 텍스트 메시지를 보낸다. `text` 가 비어 있으면 서버가 1008 로 연결을 끊는다."
                    name = "echoRequest"
                    fields<EchoRequest>()
                }

                docs.subscribe("/ws/echo", payload = reply) {
                    description = "보낸 메시지마다 가공한 결과를 한 번 받는다."
                    name = "echoResponse"
                    fields<EchoResponse>()
                }
            }
        }

        // JUnit 판의 closesOnBlankText 와 같다. 문서화하지 않으므로 섹션이 생기지 않는다
        test("text 가 비면 1008 로 연결을 닫는다") {
            WebSocketHandlerTester(EchoHandler(), path = "/ws/echo").use { ws ->
                ws.send("""{"text":""}""")

                check(!ws.isOpen && ws.closeCode == 1008)
            }
        }
    })
