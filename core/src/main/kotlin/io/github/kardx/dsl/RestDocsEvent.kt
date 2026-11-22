package io.github.kardx.dsl

/**
 * Server-Sent Events 이벤트 DSL. `andDocument { events(...) }` 에 넘긴다.
 *
 * ```kotlin
 * "notification" eventMeans "새 알림" withFields NotificationEvent::class   // io.github.kardx.extractor.withFields
 * "heartbeat" eventMeans "연결 유지 신호" isOptional true
 * ```
 *
 * 수식어(`isOptional`, `withFields`)는 이 객체를 바꾸지 않고 새 이벤트를 돌려준다. 그래서 `val` 상수로 빼서 여러 테스트가
 * 같이 써도 한 테스트의 수식어가 다른 테스트에 나가지 않는다.
 *
 * @property name SSE `event:` 이름. 이름 없는 이벤트는 `message` 다
 * @property description 이벤트 설명. `sse-events` 표와 AsyncAPI 메시지 description 에 들어간다
 */
public class RestDocsEvent private constructor(
    public val name: String,
    public val description: String,
    /** 이벤트 `data` 의 JSON 필드 */
    public val fields: List<RestDocsField>,
    /** true 이면 테스트 스트림에 이 이벤트가 없어도 실패하지 않는다 */
    public val optional: Boolean,
) {
    /**
     * 필드 없는 필수 이벤트. 보통은 [eventMeans] 로 만든다.
     *
     * ```kotlin
     * val connected = RestDocsEvent("connected", "연결됨")
     * ```
     */
    public constructor(name: String, description: String) : this(name, description, emptyList(), false)

    /**
     * 이벤트 `data` 의 JSON 필드. 스트림에서 이 이름의 첫 이벤트로 검증하고 `response-fields-event-<이름>` 표를 만든다.
     * AsyncAPI 메시지 페이로드 스키마도 이 필드로 만든다. 다시 부르면 덮어쓴다.
     * 클래스에서 바로 추출하려면 [io.github.kardx.extractor.withFields] 의 `KClass` 버전을 쓴다.
     * `"notification" eventMeans "새 알림" withFields responseFieldsOf<Envelope<NotificationEvent>>()`
     */
    public infix fun withFields(fields: List<RestDocsField>): RestDocsEvent =
        RestDocsEvent(name, description, fields, optional)

    /** true 이면 [optional] 과 같다. false 는 아무것도 바꾸지 않는다 (이미 optional 이면 그대로 둔다). */
    public infix fun isOptional(value: Boolean): RestDocsEvent = if (value) optional() else this

    /**
     * 없어도 되는 이벤트로 표시한다. 테스트 스트림에 이 이벤트가 없어도 실패하지 않는다.
     * `sse-events` 표의 Required 열이 `false` 가 된다. 스트림에 없으면 필드 표는 만들지 않는다.
     */
    public fun optional(): RestDocsEvent = RestDocsEvent(name, description, fields, true)
}

/**
 * SSE 이벤트 DSL 의 시작점. 이름은 SSE `event:` 값이고, 설명은 `sse-events` 표와 AsyncAPI 메시지 `description` 에 들어간다.
 * `"notification" eventMeans "새 알림"`
 */
public infix fun String.eventMeans(description: String): RestDocsEvent = RestDocsEvent(this, description)
