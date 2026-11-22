package io.github.kardx.websocket

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.dsl.MessageDirection
import io.github.kardx.dsl.MessageDocumentationBuilder
import io.github.kardx.dsl.MessageProtocol
import io.github.kardx.internal.asyncapi.AsyncApiMetadata
import io.github.kardx.internal.asyncapi.MessageRecord
import io.github.kardx.internal.diagnostics.diagnosed
import io.github.kardx.internal.output.SnippetPaths.DOCUMENT_IDENTIFIER
import io.github.kardx.internal.websocket.DocumentedMessage
import io.github.kardx.internal.websocket.MessageRequestConverter
import io.github.kardx.internal.websocket.MessageSnippet
import io.github.kardx.internal.websocket.MessageSpec
import io.github.kardx.internal.websocket.NoResponseConverter
import io.github.kardx.internal.websocket.WholePayloadExtractor
import io.github.kardx.internal.websocket.documentationConfigurationMap
import io.github.kardx.internal.websocket.payloadText
import io.github.kardx.internal.websocket.toSpec
import org.springframework.restdocs.RestDocumentationContext
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.snippet.Snippet

/**
 * WebSocket / STOMP 메시지를 Spring REST Docs 스니펫으로 문서화한다.
 *
 * HTTP 요청이 없으므로 MockMvc 대신 이 클래스를 쓴다. 템플릿, 필드 검증, 스니펫 디렉터리 규칙은
 * MockMvc 문서화와 같아서 [io.github.kardx.junit.AutoRestDocumentationExtension] 이 같은 문서에 모은다.
 * 설명·헤더·필드는 [MessageDocumentationBuilder] 블록으로 준다 (REST 의 `andDocument { ... }` 와 같은 방식).
 *
 * 메시지마다 다음 스니펫을 만든다 (`NN` 은 테스트 안의 순번).
 * - `message-NN`: 방향·목적지·설명, 헤더 표, 페이로드 예시
 * - `request-fields-message-NN`: 페이로드 필드 표. 실제 페이로드로 검증한다
 * - `message-NN.asyncapi.json`: [io.github.kardx.asyncapi.AsyncApiGenerator] 가 AsyncAPI 문서를 만들 때 읽는 메타데이터
 *
 * ```kotlin
 * @Test
 * @DisplayName("채팅 메시지 전송")
 * fun chat(provider: RestDocumentationContextProvider) {
 *     val docs = MessageDocumentation(provider)
 *
 *     docs.send("/app/chat.send", payload = ChatSendRequest(roomId = 1, content = "안녕하세요")) {
 *         description = "채팅방에 메시지를 보낸다."
 *         fields<ChatSendRequest>()
 *     }
 *
 *     // 실제로 받은 프레임 본문 (String / ByteArray / 객체)
 *     docs.subscribe("/topic/rooms.1", payload = receivedFrameBody) {
 *         fields<ChatMessage>()
 *     }
 * }
 * ```
 *
 * 프로토콜은 `message-NN` 스니펫의 표시와 AsyncAPI 채널(`x-protocol`, 연결할 서버)을 정한다. 기본은 [MessageProtocol.STOMP],
 * 순수 WebSocket 이면 `MessageDocumentation(provider, MessageProtocol.WEBSOCKET)` 이다.
 */
public class MessageDocumentation private constructor(
    private val provider: RestDocumentationContextProvider,
    private val protocol: ProtocolName,
) {
    /**
     * @param provider 스니펫 자리를 주는 REST Docs 문맥 (JUnit 파라미터, Kotest `restDocs.provider` 등)
     * @param protocol 문서에 표시할 프로토콜 (기본 [MessageProtocol.STOMP])
     */
    public constructor(
        provider: RestDocumentationContextProvider,
        protocol: MessageProtocol = MessageProtocol.STOMP,
    ) : this(provider, ProtocolName(protocol.displayName))

    /**
     * [MessageProtocol] 에 없는 사용자 정의 프로토콜(`"MQTT"` 등)용. 앞뒤 공백은 지운다. 알려진 프로토콜의 이름이나 별칭이면
     * (대소문자 무시: `"websocket"`, `"ws"` → `WebSocket`, `"stomp"` → `STOMP`, `"sse"`·`"http"` → `SSE`) 그 [MessageProtocol] 로
     * 보고 표시 이름도 [MessageProtocol.displayName] 으로 맞춘다. 그래서 문서의 표시와 AsyncAPI 서버 연결 판정이 어긋나지 않는다.
     * 사용자 정의 프로토콜의 채널에는 AsyncAPI 서버를 모두 붙인다.
     *
     * @throws IllegalArgumentException [protocol] 이 비어 있을 때
     */
    public constructor(provider: RestDocumentationContextProvider, protocol: String) :
        this(provider, ProtocolName.of(protocol))

    /** 문서에 기록하는 프로토콜 이름. 공개 생성자 두 개와 시그니처가 겹치지 않게 감싼다. */
    private class ProtocolName(val value: String) {
        companion object {
            fun of(name: String): ProtocolName {
                val trimmed = name.trim()

                require(trimmed.isNotEmpty()) { "protocol must not be blank" }

                return ProtocolName(MessageProtocol.of(trimmed)?.displayName ?: trimmed)
            }
        }
    }

    /**
     * 클라이언트가 보내는 메시지를 문서화한다.
     *
     * @param payload 객체(JSON 직렬화), `String`, `ByteArray`, 또는 null
     */
    public fun send(destination: String, payload: Any?, block: MessageDocumentationBuilder.() -> Unit = {}): Unit =
        document(MessageDirection.SEND, destination, payload, block)

    /**
     * 서버가 구독자에게 보내는 메시지를 문서화한다.
     *
     * @param payload 실제로 받은 프레임 본문. 객체(JSON 직렬화), `String`, `ByteArray`, 또는 null
     */
    public fun subscribe(
        destination: String,
        payload: Any?,
        block: MessageDocumentationBuilder.() -> Unit = {},
    ): Unit = document(MessageDirection.SUBSCRIBE, destination, payload, block)

    /**
     * 연결할 때 보내는 헤더를 문서화한다. STOMP 는 CONNECT 프레임 헤더, 순수 WebSocket 은 핸드셰이크 헤더다.
     * AsyncAPI 에는 같은 프로토콜 채널의 WebSocket 바인딩 헤더(`bindings.ws.headers`)로 들어가고,
     * AsyncAPI 뷰어 Try it out 의 STOMP CONNECT 헤더 칸이 이 예시로 채워진다. 페이로드는 없다.
     *
     * ```kotlin
     * docs.connect("/ws") {
     *     description = "연결 헤더"
     *     header("Authorization", "Bearer $token", "Bearer 토큰")
     *     header("X-Client-Version", "1.4.0", "앱 버전")
     * }
     * ```
     */
    public fun connect(endpoint: String, block: MessageDocumentationBuilder.() -> Unit = {}): Unit =
        document(MessageDirection.CONNECT, endpoint, null, block)

    /**
     * 메시지 하나를 문서화한다. 방향을 직접 고를 때 쓴다.
     *
     * @param payload 객체(JSON 직렬화), `String`, `ByteArray`, 또는 null
     */
    public fun document(
        direction: MessageDirection,
        destination: String,
        payload: Any?,
        block: MessageDocumentationBuilder.() -> Unit = {},
    ) {
        val spec = MessageDocumentationBuilder(direction).apply(block)
            .toSpec()
        val configuration = documentationConfigurationMap(provider)
        val context = configuration[RestDocumentationContext::class.java.name] as? RestDocumentationContext
            ?: error(OUTSIDE_TEST)

        val key = "message-%02d".format(context.stepCount)
        val message = DocumentedMessage(
            direction,
            destination,
            payloadText(payload),
            spec.description,
            spec.headerValues,
        )

        val snippets = listOfNotNull(
            MessageSnippet(key, protocol.value, message, spec.headers).diagnosed("message headers", MESSAGE),
            payloadFieldsSnippet(key, spec, message),
        )

        RestDocumentationGenerator(
            DOCUMENT_IDENTIFIER,
            MessageRequestConverter,
            NoResponseConverter,
            *snippets.toTypedArray(),
        ).handle(message, Unit, configuration)

        AsyncApiMetadata.write(
            context,
            key,
            MessageRecord(
                key = key,
                name = spec.name,
                protocol = protocol.value,
                direction = direction.name,
                address = destination,
                description = spec.description,
                headers = AsyncApiMetadata.headers(spec.headers, spec.headerValues),
                payload = message.payload,
                fields = AsyncApiMetadata.fields(spec.fields),
            ),
        )
    }

    /** 페이로드 필드 표. 필드를 적지 않았으면 null. */
    private fun payloadFieldsSnippet(key: String, spec: MessageSpec, message: DocumentedMessage): Snippet? {
        if (spec.fields.isEmpty()) {
            return null
        }

        val extractor = WholePayloadExtractor(key)
        val descriptors = spec.fields.map { it.descriptor }
        val snippet = if (spec.relaxed) {
            PayloadDocumentation.relaxedRequestFields(extractor, descriptors)
        } else {
            PayloadDocumentation.requestFields(extractor, descriptors)
        }

        return snippet.diagnosed("message payload fields", MESSAGE) { message.payload?.toByteArray() }
    }

    private companion object {
        // private: private companion 의 const 도 바깥 클래스의 public static 필드로 나가므로 막는다
        private const val EXTENSION_HINT = "AutoRestDocumentationExtension (or RestDocumentationExtension)"

        /** 테스트 밖에서 문서화했을 때의 메시지 (`KDX-301`) */
        private val OUTSIDE_TEST = RestDocsErrorCode.MESSAGE_OUTSIDE_TEST.message(
            "No RestDocumentationContext. Call MessageDocumentation inside a test method run by $EXTENSION_HINT.",
        )
        private const val MESSAGE = "message"
    }
}
