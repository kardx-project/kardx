// StompTestClient·WebSocketHandlerTester 는 실험 API 다 (ExperimentalRestDocsApi)
@file:OptIn(ExperimentalRestDocsApi::class)

package com.example.restdocs.chat

import com.example.restdocs.common.DemoAuth
import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.dsl.headerMeans
import io.github.kardx.extractor.fields
import io.github.kardx.websocket.MessageDocumentation
import io.github.kardx.websocket.StompTestClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.restdocs.RestDocumentationContextProvider
import java.nio.file.Files
import java.nio.file.Paths

/**
 * STOMP 예제. 실제 서버에 STOMP 로 메시지를 보내고, 구독해서 받은 프레임으로 문서를 만든다.
 *
 * HTTP 가 아니므로 MockMvc 대신 `MessageDocumentation` 을 쓴다. 연결·구독·재전송은 `StompTestClient` 가 맡는다.
 * 테스트 메서드의 `RestDocumentationContextProvider` 파라미터는 `AutoRestDocumentationExtension` 이 넣어 준다.
 * 샘플 앱은 CONNECT 에 데모 토큰(`Authorization: Bearer demo-token`, DemoAuth)이 없으면 연결을 거절한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("채팅 (STOMP)")
class ChatMessageDocsTest : RestDocsTestSupport() {

    @LocalServerPort
    var port: Int = 0

    @Test
    @DisplayName("메시지 보내고 받기")
    fun chat(provider: RestDocumentationContextProvider) {
        val frame = StompTestClient("ws://localhost:$port/ws", connectHeaders = CONNECT_HEADERS).use { stomp ->
            stomp.sendAndReceive("/app/chat.send", MESSAGE_BODY, subscribeTo = "/topic/rooms.1")
        }

        // 실제 프레임에 값이 있는지만 확인하고, 문서에는 고정 예시값을 쓴다 (message-id 는 매번 새 UUID 라서
        // 그대로 쓰면 실행마다 문서가 달라져 Asciidoctor 가 매번 다시 돈다)
        check(!frame.headers["subscription"].isNullOrBlank()) { "subscription header missing: $frame" }
        check(!frame.headers["message-id"].isNullOrBlank()) { "message-id header missing: $frame" }

        val docs = MessageDocumentation(provider)

        docs.connect("/ws") {
            description = "연결할 때 한 번 보내는 인증·클라이언트 헤더. 샘플 앱은 토큰 `demo-token` 만 받는다."
            headerValues(CONNECT_HEADERS)
            headers(
                "Authorization" headerMeans "Bearer 액세스 토큰. STOMP CONNECT 프레임에 넣는다",
                "X-Client-Version" headerMeans "앱 버전" isOptional true,
            )
        }

        docs.send("/app/chat.send", payload = MESSAGE_BODY) {
            description = "채팅방에 메시지를 보낸다. 서버는 `/topic/rooms.{roomId}` 구독자에게 전달한다."
            name = "chatSend"
            header("content-type", "application/json", "페이로드 형식 (`application/json`)")
            fields<ChatSendRequest>()
        }

        docs.subscribe("/topic/rooms.{roomId}", payload = frame.body) {
            description = "채팅방에 새 메시지가 오면 받는다."
            name = "chatMessage"
            headerValues(SUBSCRIBE_HEADERS_EXAMPLE)
            headers(
                "subscription" headerMeans "구독 ID",
                "message-id" headerMeans "메시지 ID",
            )

            fields<ChatMessage>()
        }

        checkSnippetsWritten(
            "message-01.adoc",
            "message-02.adoc",
            "request-fields-message-02.adoc",
            "message-03.adoc",
            "request-fields-message-03.adoc",
        )
    }

    @Test
    fun rejectsConnectWithoutToken() {
        assertThrows<IllegalStateException> {
            StompTestClient("ws://localhost:$port/ws").close()
        }
    }

    /** 메시지마다 스니펫이 생겼는지 확인한다. */
    private fun checkSnippetsWritten(vararg names: String) {
        val dir = Paths.get("build/generated-snippets/chat-message-docs-test/chat")
        val written = Files.list(dir).use { files ->
            files.map { it.fileName.toString() }
                .toList()
        }

        check(written.containsAll(names.toList())) { "missing snippets in $dir: $written" }
    }

    private companion object {
        /** 보내는 채팅 메시지 페이로드. 문서의 send 예시로도 쓴다 */
        const val MESSAGE_BODY = """{"roomId":1,"content":"안녕하세요"}"""

        /** STOMP CONNECT 프레임에 실제로 보내는 헤더. 토큰은 샘플 앱의 데모 토큰이다 */
        val CONNECT_HEADERS = mapOf("Authorization" to DemoAuth.BEARER, "X-Client-Version" to "1.4.0")

        /** 구독 프레임 헤더의 문서용 예시값. 실제 값(구독 ID, 서버가 매번 새로 만드는 메시지 ID)은 실행마다 다르다 */
        val SUBSCRIBE_HEADERS_EXAMPLE = mapOf(
            "subscription" to "0",
            "message-id" to "3f2c9a1e-7b4d-4e8a-9c61-2d5f0b8e4a17-0",
        )
    }
}
