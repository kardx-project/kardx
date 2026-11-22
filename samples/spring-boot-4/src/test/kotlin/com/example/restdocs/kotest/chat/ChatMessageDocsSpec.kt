// StompTestClient·WebSocketHandlerTester 는 실험 API 다 (ExperimentalRestDocsApi)
@file:OptIn(ExperimentalRestDocsApi::class)

package com.example.restdocs.kotest.chat

import com.example.restdocs.chat.ChatMessage
import com.example.restdocs.chat.ChatSendRequest
import com.example.restdocs.common.DemoAuth
import com.example.restdocs.kotest.support.MirrorDocsConfig
import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.dsl.headerMeans
import io.github.kardx.extractor.fields
import io.github.kardx.websocket.MessageDocumentation
import io.github.kardx.websocket.StompTestClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Paths

/**
 * [com.example.restdocs.chat.ChatMessageDocsTest] 를 Kotest 로 옮긴 스펙. 실제 서버에 STOMP 로 주고받은 프레임으로 문서를 만든다.
 * JUnit 의 `RestDocumentationContextProvider` 파라미터 대신 스펙에 등록한 확장의 `provider` 를 `MessageDocumentation` 에 넘긴다.
 * 서버 포트는 Kotest Spring 확장이 생성자에 넣는다. 연결·구독·재전송은 `StompTestClient` 가 맡는다.
 *
 * 채팅 컨트롤러는 메시지 ID 를 앱 안의 순번으로 매긴다. JUnit 판과 같은 서버 컨텍스트를 이어 쓰면 뒤에 도는 쪽의 문서 예시
 * ID 가 바뀌므로(`id: 1` → `2`), 스펙이 끝나면 컨텍스트를 닫는다(`@DirtiesContext`). 그래서 JUnit 판은 새 서버에서 돈다.
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("docs")
@DirtiesContext
@DisplayName("채팅 (STOMP)")
class ChatMessageDocsSpec(@LocalServerPort port: Int) :
    FunSpec({
        val restDocs = extension(MirrorDocsConfig.restDocsExtension())

        beforeEach { MirrorDocsConfig.apply() }

        test("메시지 보내고 받기") {
            val frame = StompTestClient("ws://localhost:$port/ws", connectHeaders = CONNECT_HEADERS).use { stomp ->
                stomp.sendAndReceive("/app/chat.send", MESSAGE_BODY, subscribeTo = "/topic/rooms.1")
            }

            // 실제 프레임에 값이 있는지만 확인하고, 문서에는 고정 예시값을 쓴다 (message-id 는 매번 새 UUID 라서
            // 그대로 쓰면 실행마다 문서가 달라진다)
            check(!frame.headers["subscription"].isNullOrBlank()) { "subscription header missing: $frame" }
            check(!frame.headers["message-id"].isNullOrBlank()) { "message-id header missing: $frame" }

            val docs = MessageDocumentation(restDocs.provider)

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

        test("토큰 없이 연결하면 거절") {
            shouldThrow<IllegalStateException> {
                StompTestClient("ws://localhost:$port/ws").close()
            }
        }
    })

/** 보내는 채팅 메시지 페이로드. 문서의 send 예시로도 쓴다 */
private const val MESSAGE_BODY = """{"roomId":1,"content":"안녕하세요"}"""

/** STOMP CONNECT 프레임에 실제로 보내는 헤더. 토큰은 샘플 앱의 데모 토큰이다 */
private val CONNECT_HEADERS = mapOf("Authorization" to DemoAuth.BEARER, "X-Client-Version" to "1.4.0")

/** 구독 프레임 헤더의 문서용 예시값. 실제 값(구독 ID, 서버가 매번 새로 만드는 메시지 ID)은 실행마다 다르다 */
private val SUBSCRIBE_HEADERS_EXAMPLE = mapOf(
    "subscription" to "0",
    "message-id" to "3f2c9a1e-7b4d-4e8a-9c61-2d5f0b8e4a17-0",
)

/** 메시지마다 스니펫이 생겼는지 확인한다. */
private fun checkSnippetsWritten(vararg names: String) {
    val dir = Paths.get("build/generated-snippets/chat-message-docs-spec/메시지-보내고-받기")
    val written = Files.list(dir).use { files ->
        files.map { it.fileName.toString() }
            .toList()
    }

    check(written.containsAll(names.toList())) { "missing snippets in $dir: $written" }
}
