package io.github.kardx.dsl

/**
 * [io.github.kardx.websocket.MessageDocumentation.send]·
 * [io.github.kardx.websocket.MessageDocumentation.subscribe]·
 * [io.github.kardx.websocket.MessageDocumentation.connect] 블록.
 * REST 문서화 블록(`andDocument { ... }`)과 같은 규칙을 따른다: 같은 함수를 여러 번 부르면 항목이 이어 붙고,
 * 각 함수는 목록과 가변 인자 두 형태를 받는다.
 *
 * ```kotlin
 * docs.send("/app/chat.send", payload = body) {
 *     description = "채팅방에 메시지를 보낸다."
 *     name = "chatSend"
 *     headerValues("content-type" to "application/json") // 실제 값
 *     headers("content-type" headerMeans "페이로드 형식") // 설명 (HTTP 블록의 requestHeaders 와 같다)
 *     fields<ChatSendRequest>()
 * }
 * ```
 *
 * 메시지 방향(보내기·받기)은 타입에서 필드를 추출하는 단축형 `fields<T>()` 가 추출 규칙(요청·응답)을 고르는 데 쓴다.
 */
@RestDocsDsl
public class MessageDocumentationBuilder internal constructor(@PublishedApi internal val direction: MessageDirection) {
    /** 메시지 설명. `message-NN` 스니펫과 AsyncAPI 메시지 description 에 들어간다. */
    public var description: String? = null

    /** AsyncAPI 메시지 이름 (예: `chatMessage`). 없으면 방향과 목적지로 만든다 (`send-app-chat-send`). */
    public var name: String? = null

    /** true 이면 페이로드 필드를 relaxed 모드로 검증한다 (문서화하지 않은 필드 허용). */
    public var relaxed: Boolean = false

    internal val headerValues = linkedMapOf<String, String>()
    internal val headers = mutableListOf<RestDocsHeader>()
    internal val fields = mutableListOf<RestDocsField>()

    /**
     * 문서화할 헤더 설명. HTTP 블록의 `requestHeaders(...)` 와 같은 자리다. 필수 헤더가 [headerValues] 에 없으면 실패한다.
     * `message-NN` 스니펫의 Headers 표(Example 열은 [headerValues] 의 값을 마스킹한 것)와 AsyncAPI 메시지 `headers` 스키마가 된다.
     * `headers("Authorization" headerMeans "Bearer 토큰")`
     */
    public fun headers(headers: List<RestDocsHeader>) {
        this.headers += headers.requireDescribed("message headers")
    }

    /** [headers] 의 가변 인자 버전. */
    public fun headers(vararg headers: RestDocsHeader): Unit = headers(headers.toList())

    /**
     * 실제 프레임 헤더 값. 민감 헤더는 [io.github.kardx.config.MaskingSettings.headers] 규칙으로 가린다.
     * 설명([headers])이 없는 헤더는 표와 AsyncAPI 에 나오지 않고 요청 헤더로만 남는다.
     * 같은 이름을 다시 주면 나중 값이 이긴다.
     */
    public fun headerValues(values: Map<String, String>) {
        headerValues += values
    }

    /** [headerValues] 의 가변 인자 버전. `headerValues("content-type" to "application/json")` */
    public fun headerValues(vararg values: Pair<String, String>): Unit = headerValues(values.toMap())

    /**
     * 헤더 값과 설명을 한 번에 준다.
     * `headerValues(name to value)` 와 `headers(name headerMeans description)` 를 함께 부른 것과 같다.
     */
    public fun header(name: String, value: String, description: String) {
        headerValues(name to value)
        headers(name headerMeans description)
    }

    /**
     * 페이로드 필드. 실제 페이로드로 검증한다. 직접 기술하거나 `requestFieldsOf<T>()` 등으로 추출한 목록을 준다.
     * 타입에서 추출한 목록을 그대로 쓸 때는 단축형 [io.github.kardx.extractor.fields] (`fields<ChatMessage>()`)가
     * 방향에 맞는 추출 규칙을 골라 준다.
     * `request-fields-message-NN` 표와 AsyncAPI 메시지 `payload` 스키마가 된다. 방향(SEND·SUBSCRIBE)과 상관없이 같다.
     */
    public fun fields(fields: List<RestDocsField>) {
        this.fields += fields
    }

    /** [fields] 의 가변 인자 버전. */
    public fun fields(vararg fields: RestDocsField): Unit = fields(fields.toList())
}
