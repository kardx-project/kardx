[English](../messaging.md) | **한국어**

# SSE · WebSocket · AsyncAPI

[문서 목록](README.md)

<!-- toc -->
**목차**

- [브라우저에서 보기](#브라우저에서-보기)
- [Server-Sent Events](#server-sent-events)
  - [끝나지 않는 스트림](#끝나지-않는-스트림)
- [WebSocket / STOMP](#websocket--stomp)
  - [연결 헤더 (인증)](#연결-헤더-인증)
  - [StompTestClient 로 주고받기](#stomptestclient-로-주고받기)
  - [WebSocket 핸들러 테스트](#websocket-핸들러-테스트)
- [AsyncAPI](#asyncapi)
  - [AsyncAPI 인증](#asyncapi-인증)
<!-- /toc -->

HTTP 요청·응답 한 번으로 끝나지 않는 API 를 문서화합니다. Server-Sent Events 는 MockMvc 로(끝나는 스트림은 WebTestClient 도 됩니다, [WebTestClient](webtestclient.md#문서화-테스트)), WebSocket·STOMP 메시지는 `MessageDocumentation` 으로 문서화하고, 기계가 읽는 스펙은 AsyncAPI 로 만듭니다.

## 브라우저에서 보기

| 보고 싶은 것 | 앱 주소 | 파일 |
|---|---|---|
| SSE 문서 (이벤트 표, 이벤트별 필드) | `http://localhost:8080/docs/notification-api-docs-test.html#subscribe` | `build/api-docs/notification-api-docs-test.html` |
| WebSocket·STOMP 메시지 문서 | `http://localhost:8080/docs/chat-message-docs-test.html#chat` | `build/api-docs/chat-message-docs-test.html` |
| AsyncAPI 뷰어 (문서 탭) | `http://localhost:8080/docs/asyncapi.html` | `build/api-docs/asyncapi.html` |
| AsyncAPI Try it out 탭 (끝나지 않는 SSE 구독도 여기서) | `http://localhost:8080/docs/asyncapi.html#try-it-out` | 같은 파일 + `#try-it-out` (서버 주소 기본값은 앱) |
| AsyncAPI 스펙 | `http://localhost:8080/docs/asyncapi.yaml` | `build/api-docs/asyncapi.yaml` |

주소의 `/docs` 는 Gradle 플러그인의 `bootJar.path`(기본 `static/docs`)에서, 포트는 앱 설정에서 옵니다. 파일 경로는 `./gradlew apiDocs` 기준이고 플러그인의 `outputDirectory` 로 바꿀 수 있습니다. 문서 이름은 테스트 클래스 이름, `#` 뒤는 테스트 메서드 이름을 kebab-case 로 바꾼 값입니다 (`ProductApiDocsTest.getProductNotFound` → `product-api-docs-test.html#get-product-not-found`).

- SSE 와 메시지 문서는 HTTP API 와 같은 REST Docs HTML 에 들어갑니다. 별도 화면은 AsyncAPI 뷰어뿐입니다.
- Try it out 은 실제 서버에 연결합니다. 앱에서 열면 그 앱이 기본 서버이고, 파일로 열면 스펙의 `http` 서버(없으면 `localhost:8080`)가 기본값입니다. 화면 위쪽 입력란에서 바꿀 수 있습니다.
- 뷰어는 AsyncAPI 공식 React 뷰어와 STOMP 클라이언트를 CDN 에서 받으므로 인터넷 연결이 필요합니다.
- Swagger UI 에는 메시지 API 가 나오지 않습니다. OpenAPI 가 WebSocket·STOMP 를 표현하지 못하기 때문입니다.

## Server-Sent Events

`performAsync` 로 스트림을 끝까지 받은 뒤 `events` 로 이벤트를 문서화합니다. 컨트롤러는 테스트에서 스트림을 닫아야 합니다 (`emitter.complete()`).

```kotlin
mockMvc
    .performAsync(
        get("/api/notifications/stream")
            .accept(MediaType.TEXT_EVENT_STREAM),
    )
    .andStatus(HttpStatus.OK)
    .andDocument {
        description = "알림을 Server-Sent Events 로 받는다."
        requestHeaders("Last-Event-ID" headerMeans "재연결 시 마지막 이벤트 ID" isOptional true)
        events(
            "connected" eventMeans "연결 성공" withFields ConnectedEvent::class,
            "notification" eventMeans "새 알림" withFields NotificationEvent::class,
            "maintenance" eventMeans "점검 예고" isOptional true,
        )
    }
```

- `sse-events` 스니펫은 이벤트 이름·필수 여부·설명 표입니다. `response-fields-event-<이름>` 은 그 이벤트 `data` 의 필드 표입니다.
- `withFields ConnectedEvent::class` (`import io.github.kardx.extractor.withFields`) 는 그 클래스에서 응답 규칙으로 필드를 추출합니다. 제네릭 이벤트(`Envelope<Payload>`)나 추출 결과를 고칠 때는 목록을 넘깁니다: `withFields responseFieldsOf<Envelope<Payload>>()`, `withFields responseFieldsOf<ConnectedEvent>().except("debug")`.
- 필수 이벤트가 스트림에 없거나 문서화하지 않은 이벤트가 오면 실패합니다. `relaxed(RelaxedSection.EVENTS)`(또는 모든 항목을 느슨하게 하는 `relaxed = true`)면 후자와 문서화하지 않은 `data` 필드를 허용합니다 ([느슨한 검증](http-api.md#느슨한-검증)).
- 스트림에 없는 optional 이벤트는 필드 표를 만들지 않습니다. `event:` 가 없는 이벤트의 이름은 `message` 입니다.

### 끝나지 않는 스트림

실제 서비스의 SSE 는 보통 연결을 끝내지 않고 이벤트가 생길 때마다 보냅니다. `performAsync` 는 응답이 끝날 때까지 기다리므로 이런 스트림에는 `performStream`(`import io.github.kardx.sse.performStream`)을 씁니다. 처음 `eventCount` 개 이벤트를 받으면 그때까지의 응답을 고정해서 문서화합니다. 스트림이 더 쓰여도 문서는 매번 같습니다.

```kotlin
mockMvc
    .performStream(
        get("/api/todos/stream")
            .accept(MediaType.TEXT_EVENT_STREAM),
        eventCount = 2,
    ) {
        // 구독한 뒤 실행된다. 다른 요청으로 이벤트를 일으킬 때 쓴다.
        mockMvc
            .perform(
                post("/api/todos")
                    .jsonContent(CreateTodoRequest("스트림 확인")),
            )
    }
    .andStatus(HttpStatus.OK)
    .andDocument {
        events(
            "connected" eventMeans "연결 성공" withFields ConnectedEvent::class,
            "todo-created" eventMeans "할 일 생성" withFields TodoCreatedEvent::class,
            "heartbeat" eventMeans "연결 유지. 5초마다" isOptional true withFields HeartbeatEvent::class,
        )
    }
```

| 인자 | 기본값 | 설명 |
|---|---|---|
| `eventCount` | `1` | 받을 이벤트 수. 빈 줄로 끝난 이벤트만 세고, 주석(`:ping`)은 세지 않는다. 블록의 `events(...)`(이벤트 설명)와는 다르다 |
| `timeout` | 5초 | 이 시간 안에 받지 못하면 받은 수와 함께 실패한다 (`Expected 2 stream events within PT5S but received 1. Increase ...`). 시간을 늘리거나 `whileStreaming` 에서 이벤트를 일으킨다 |
| `whileStreaming` | 없음 | 구독한 뒤 실행할 코드 |

- 주기 이벤트(heartbeat)가 테스트 중에 끼어들면 문서가 흔들립니다. 테스트 설정에서 주기를 길게 두고, 그 이벤트는 `isOptional true` 로 문서화합니다.
- 문서로 볼 때는 AsyncAPI 뷰어의 Try it out 에서 구독합니다. 연결이 유지되는 동안 이벤트가 계속 쌓입니다.
- 구독 요청 헤더(`requestHeaders`)는 REST Docs 표와 AsyncAPI 채널의 `x-request-headers` 에 들어갑니다. AsyncAPI 의 HTTP 바인딩에는 요청 헤더 자리가 없어 확장 필드로 남깁니다. 브라우저 `EventSource` 는 헤더를 보낼 수 없으므로 Try it out 의 SSE 구독은 헤더 없이 연결합니다.
- `performStream` 으로 문서화한 API 는 OpenAPI(Swagger) 에서 자동으로 빠집니다. Swagger UI 는 응답이 끝나야 결과를 보여 주므로 Try it out 이 계속 로딩만 돌기 때문입니다. REST Docs 문서와 AsyncAPI 에는 남습니다. 그래도 넣으려면 `open-api.include-streams: true` 를 씁니다.

## WebSocket / STOMP

HTTP 요청이 없으므로 `MessageDocumentation` 을 씁니다. 아래 예시는 JUnit 파라미터 주입을 씁니다. Kotest 에서는 `restDocs.provider` 나 `RestDocsExtension.currentTestProvider` 를 넘깁니다 ([Kotest](kotest.md#websocket--stomp-메시지)). 실제로 주고받은 페이로드를 넘기면 필드를 그 페이로드로 검증합니다. 목적지와 페이로드는 인자로, 설명·헤더·필드는 REST 의 `andDocument { ... }` 처럼 블록으로 줍니다.

```kotlin
@Test
@DisplayName("채팅 메시지")
fun chat(provider: RestDocumentationContextProvider) {
    // /app/chat.send 에 보내고 /topic/rooms.1 에서 frameBody 를 받는다 (아래 StompTestClient)

    val docs = MessageDocumentation(provider)                   // STOMP (기본). 순수 WebSocket 은 아래 WebSocketHandlerTester 예시

    docs.send("/app/chat.send", payload = ChatSendRequest(roomId = 1, content = "안녕하세요")) {  // 객체·String·ByteArray
        description = "채팅방에 메시지를 보낸다."
        name = "chatSend"                                       // AsyncAPI 메시지 이름
        headerValues("content-type" to "application/json")      // 실제 헤더 값
        headers("content-type" headerMeans "페이로드 형식")       // 헤더 설명 (HTTP 블록의 requestHeaders 와 같다)
        fields<ChatSendRequest>()
    }
    docs.subscribe("/topic/rooms.{roomId}", payload = frameBody) {
        fields<ChatMessage>()
    }
}
```

| 블록 항목 | 의미 |
|---|---|
| `description` | 메시지 설명. 스니펫과 AsyncAPI description |
| `name` | AsyncAPI 메시지 이름. 없으면 방향과 목적지로 만든다 (`send-app-chat-send`). 같은 목적지·방향·이름을 여러 테스트에서 문서화하면 페이로드 구조(필드 경로·타입·필수 여부)가 같을 때만 한 메시지에 예시를 더하고, 구조가 다르면 `subscribe-ws-shape-2` 처럼 다른 메시지로 둡니다. 서로 다른 메시지라면 `name` 을 주세요 |
| `headers(...)` | 문서화할 헤더 설명 (`"이름" headerMeans "설명"`). HTTP 블록의 `requestHeaders(...)` 와 같다. 필수 헤더가 `headerValues` 에 없으면 실패한다 |
| `headerValues(...)` | 실제 헤더 값 (`"이름" to "값"` 또는 `Map`). 설명이 없는 헤더는 표와 AsyncAPI 에 나오지 않는다 |
| `header(이름, 값, 설명)` | `headerValues` 와 `headers` 를 한 번에 |
| `fields(...)` | 페이로드 필드. 실제 페이로드로 검증한다. 직접 쓰거나 `requestFieldsOf<T>()` 등으로 추출한 목록 |
| `fields<T>(path = "")` | 타입에서 추출해 등록하는 단축형. 방향에 맞는 규칙을 고른다: `send`·`connect` 는 요청 규칙(`requestFieldsOf`: 기본값 있는 프로퍼티 optional, 읽기 전용 제외), `subscribe` 는 응답 규칙(`responseFieldsOf`). 목록을 고치거나 합칠 때는 `fields(responseFieldsOf<T>() + ...)` |
| `relaxed = true` | 문서화하지 않은 페이로드 필드를 허용한다 |

프로토콜은 `MessageProtocol`(`io.github.kardx.dsl`)로 고릅니다: `STOMP`(기본), `WEBSOCKET`, `SSE`. 문서와 AsyncAPI 채널의 `x-protocol` 에는 `STOMP`·`WebSocket`·`SSE` 로 나가고, AsyncAPI 서버(`stomp`·`ws`·`http` …) 중 그 프로토콜로 연결할 수 있는 서버가 채널에 붙습니다. 목록에 없는 프로토콜은 문자열로 줍니다(`MessageDocumentation(provider, "MQTT")`). 그 채널에는 AsyncAPI 서버가 모두 붙습니다. 앞뒤 공백은 지우고, 알려진 프로토콜의 이름·별칭이면(대소문자 무시: `"websocket"`·`"ws"` → `WebSocket`, `"stomp"` → `STOMP`, `"sse"`·`"http"` → `SSE`) 그 프로토콜로 보고 표시 이름도 맞춥니다(문서 표시와 AsyncAPI 서버 연결 판정이 같은 값). 빈 문자열은 거부합니다.

`headers`·`headerValues`·`fields` 는 여러 번 부르면 이어 붙고, 가변 인자와 `List`(`Map`) 두 형태를 받습니다. 방향을 직접 고르려면 `docs.document(MessageDirection.SEND, destination, payload) { ... }` 를 씁니다. `MessageDirection` 과 블록 타입 `MessageDocumentationBuilder` 는 HTTP 블록 타입과 같은 `io.github.kardx.dsl` 에, `MessageDocumentation`·`WebSocketHandlerTester` 는 `io.github.kardx.websocket` 에, 단축형 `fields<T>()` 는 `io.github.kardx.extractor` 에 있습니다.

메시지마다 `message-NN`(방향·목적지·헤더·페이로드 예시)과 `request-fields-message-NN`(필드 표)이 생기고, 페이로드 타입의 `@Masked` 필드는 필드 표와 AsyncAPI 스키마에 [마스킹 표시](http-api.md#마스킹되는-필드-표시)가 붙습니다. 생성 문서에는 메시지 순서대로 들어갑니다. 필수 헤더가 없거나 필드가 페이로드와 맞지 않으면 실패합니다. [마스킹](configuration.md#민감-정보-마스킹)을 켜면 헤더 값을 가립니다. AsyncAPI 문서와 Try it out 뷰어는 배포되는 산출물이라, 마스킹 설정과 상관없이 `Authorization`·`Cookie` 같은 민감 헤더 값은 항상 가립니다 (`Bearer ****`).
목적지는 `/app/chat.send` 처럼 `/` 로 시작하지 않아도 됩니다 (`jms.queue.orders`, `queue/x` 같은 브로커식 이름도 됩니다).

실행마다 바뀌는 헤더 값(`message-id`, `subscription` 등)은 실제 프레임에 값이 있는지만 확인하고 문서에는 고정 예시값을 넘기세요. 스니펫이 실행마다 같아져 테스트를 다시 돌려도 Asciidoctor·AsyncAPI 생성이 UP-TO-DATE 로 건너뜁니다 ([증분 빌드](build.md#생성-위치)).

### 연결 헤더 (인증)

STOMP 의 CONNECT 프레임 헤더나 WebSocket 핸드셰이크 헤더처럼 연결할 때 한 번 보내는 헤더는 `connect` 로 문서화합니다.

```kotlin
docs.connect("/ws") {
    description = "연결할 때 한 번 보내는 인증·클라이언트 헤더."
    headerValues("Authorization" to "Bearer $token", "X-Client-Version" to "1.4.0")   // 실제로 보낸 값
    headers(
        "Authorization" headerMeans "Bearer 액세스 토큰",
        "X-Client-Version" headerMeans "앱 버전" isOptional true,
    )
}
// 값과 설명을 한 번에: header("Authorization", "Bearer $token", "Bearer 액세스 토큰")
```

- REST Docs 에는 `CONNECT /ws` 섹션과 헤더 표가 생깁니다. 필수 헤더가 `headerValues` 에 없으면 실패합니다.
- AsyncAPI 에는 같은 프로토콜 채널의 WebSocket 바인딩 헤더(`bindings.ws.headers`)로 들어갑니다.
- AsyncAPI Try it out 의 STOMP CONNECT 헤더 칸이 이 예시로 미리 채워집니다. 마스킹했다면 값을 실제 토큰으로 바꿔 연결합니다.
- 브라우저 `WebSocket` 은 핸드셰이크 헤더를 보낼 수 없으므로, 순수 WebSocket 의 연결 헤더는 문서로만 남습니다. 그래도 Try it out 에서 인증하려면 서버의 인증 방식을 선언합니다 ([AsyncAPI 인증](#asyncapi-인증)).

### StompTestClient 로 주고받기

`MessageDocumentation` 은 이미 가진 프레임을 문서화합니다. 실행 중인 앱에서 프레임을 받아 오려면 `StompTestClient`(`io.github.kardx.websocket`)를 씁니다. CONNECT 헤더로 연결하고, 구독하고, 보내고, 프레임을 기다리는 일을 맡습니다. 앱은 포트를 열어 띄웁니다 (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).

이것이 없으면 STOMP 테스트마다 같은 연결 코드를 되풀이합니다.

```kotlin
// StompTestClient 없이
val client = WebSocketStompClient(StandardWebSocketClient()).apply { messageConverter = StringMessageConverter() }
val connectHeaders = StompHeaders().apply { add("Authorization", "Bearer test-token") }
val session = client.connectAsync("ws://localhost:$port/ws", WebSocketHttpHeaders(), connectHeaders, object : StompSessionHandlerAdapter() {})
    .get(5, TimeUnit.SECONDS)
val frames = LinkedBlockingQueue<String>()
session.subscribe("/topic/rooms.1", object : StompFrameHandler {
    override fun getPayloadType(headers: StompHeaders): Type = String::class.java
    override fun handleFrame(headers: StompHeaders, payload: Any?) { frames += payload as String }
})
// 이어서: content-type 헤더를 붙여 보내고, 브로커가 구독을 등록할 때까지 다시 보내고,
// 제한 시간을 두고 큐를 기다리고, 연결을 끊고 클라이언트를 멈춘다
```

`StompTestClient` 를 쓰면 이렇게 됩니다.

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("채팅 (STOMP)")
class ChatMessageDocsTest : RestDocsTestSupport() {
    @LocalServerPort
    var port: Int = 0

    @Test
    @DisplayName("메시지 보내고 받기")
    fun chat(provider: RestDocumentationContextProvider) {
        val connectHeaders = mapOf("Authorization" to "Bearer test-token")
        val request = ChatSendRequest(roomId = 1, content = "안녕하세요")

        val frame = StompTestClient("ws://localhost:$port/ws", connectHeaders = connectHeaders).use { stomp ->
            stomp.sendAndReceive("/app/chat.send", request, subscribeTo = "/topic/rooms.1")
        }

        val docs = MessageDocumentation(provider)

        docs.connect("/ws") {
            headerValues(connectHeaders)
            headers("Authorization" headerMeans "Bearer 액세스 토큰")
        }
        docs.send("/app/chat.send", payload = request) { fields<ChatSendRequest>() }
        docs.subscribe("/topic/rooms.{roomId}", payload = frame.body) { fields<ChatMessage>() }
    }
}
```

| 멤버 | 하는 일 |
|---|---|
| `StompTestClient(url, connectHeaders, timeout, webSocketClient)` | 바로 연결합니다. `connectHeaders` 는 CONNECT 프레임에 들어갑니다(`stomp.connectHeaders` 로도 읽음). `timeout` 기본값 5초, `webSocketClient` 기본값은 JSR-356 표준 클라이언트(Tomcat·Jetty 가 제공)입니다. 시간 안에 연결하지 못하거나 서버가 거절하면 `IllegalStateException` 으로 실패합니다 |
| `sendAndReceive(destination, payload, subscribeTo)` | 구독하고 보낸 뒤 처음 받은 프레임을 돌려줍니다. 브로커가 구독을 등록하기 전에 보낸 메시지는 사라질 수 있고 Spring 의 simple broker 는 SUBSCRIBE 에 receipt 를 보내지 않으므로, 프레임이 올 때까지 같은 페이로드를 다시 보냅니다. 간격은 200ms 에서 시작해 두 배씩 늘어 1초까지입니다. 그래서 시간이 걸리는 핸들러(DB 저장 등)는 같은 메시지를 두 번 이상 받을 수 있고, 돌려받는 프레임은 그중 어느 전송의 결과일 수 있습니다. 한 번만 보내야 하면 `subscribe` → `send` → `StompSubscription.await` 를 직접 씁니다. 제한 시간 안에 오지 않으면 실패합니다. 다시 보내는 시간까지 포함해 호출 전체가 제한 시간을 넘지 않고, 마감 직전에는 다시 보내지 않습니다 |
| `send(destination, payload, headers)` | 프레임 하나를 보냅니다. 문자열·`ByteArray` 는 그대로, 객체는 설정한 Jackson 매퍼로 JSON 이 됩니다. `content-type` 기본값은 `application/json` 입니다 |
| `subscribe(destination)` | `StompSubscription` 을 돌려줍니다: `await(timeout)` 은 다음 프레임을 꺼내고 `unsubscribe()` 는 구독을 끝냅니다 |
| `StompFrame` | `headers`(이름마다 첫 값)와 `body`(페이로드 문자열). `frame.body` 를 `docs.subscribe(...)` 에 넘깁니다 |
| `close()` | 연결을 끊습니다. `use { }` 로 씁니다 |

`StompTestClient` 는 Spring Framework 6(Boot 3)과 7(Boot 4)에서 모두 동작합니다. 서버가 CONNECT 를 거절하면(예: 토큰이 없을 때 예외를 던지는 채널 인터셉터) 연결되지 않는 클라이언트를 돌려주지 않고 생성자가 `IllegalStateException` 을 던지므로, 토큰이 틀리면 테스트가 그 자리에서 실패합니다.

연결한 뒤 서버가 STOMP `ERROR` 프레임을 보내거나 연결을 끊으면(SEND·SUBSCRIBE 를 거절하는 인터셉터, 없는 목적지 …) `sendAndReceive` 와 `StompSubscription.await` 는 제한 시간을 기다리지 않고 서버의 `message` 헤더와 본문으로 바로 실패합니다.

```
java.lang.IllegalStateException: STOMP ERROR from server: Access denied: Missing token
```

CONNECT 를 거절하면 생성자가 같은 방식으로 실패합니다: `Could not connect to STOMP endpoint ws://localhost:51234/ws: STOMP ERROR from server: ...`. 연결이 끊기면 `STOMP connection failed: <이유>` 로 알려 줍니다.

프레임 본문은 문자열 그대로 두므로 문서에는 서버가 실제로 보낸 JSON 이 나갑니다. 테스트 클래스패스에 `spring-messaging`·`spring-websocket` 이 있어야 합니다. STOMP 를 쓰는 앱(`spring-boot-starter-websocket`)에는 이미 있습니다. 실행마다 바뀌는 헤더 값(`message-id`, `subscription`)은 `frame.headers` 에서 확인만 하고 문서에는 고정 예시값을 넘깁니다 (위 참고).

### WebSocket 핸들러 테스트

`WebSocketHandlerTester` 는 서버 없이 `WebSocketHandler`(`TextWebSocketHandler` 등)를 호출합니다. 가짜 세션으로 연결·메시지·종료를 전달하고, 핸들러가 보낸 메시지를 모읍니다.

```kotlin
WebSocketHandlerTester(EchoHandler(), path = "/ws/echo", attributes = mapOf("userId" to "hong")).use { ws ->
    val welcome = ws.connect().single()                         // afterConnectionEstablished 에서 보낸 메시지
    val reply = ws.send("""{"text":"hello"}""").single()        // 이 메시지를 처리하며 보낸 메시지

    val docs = MessageDocumentation(provider, MessageProtocol.WEBSOCKET)

    docs.subscribe("/ws/echo", welcome) {
        description = "연결 직후 받는다."
        fields<Welcome>()
    }
    docs.send("/ws/echo", """{"text":"hello"}""") { fields<EchoRequest>() }
    docs.subscribe("/ws/echo", reply) { fields<EchoResponse>() }
}
```

| 기능 | 사용법 |
|---|---|
| 세션 경로, 핸드셰이크 헤더, 세션 속성, 인증 사용자 | 생성자 `path`, `handshakeHeaders`, `attributes`, `principal` |
| 텍스트·바이너리 메시지 | `send(String)`, `send(ByteArray)` |
| 다른 스레드에서 나중에 보내는 핸들러 | `awaitMessages(count, timeout)` |
| 연결 상태 | `isOpen`, `closeCode` |
| 종료 | `close()` 또는 `use { }` |

## AsyncAPI

OpenAPI 는 WebSocket·STOMP 메시지를 표현하지 못합니다. 그래서 라이브러리가 메시지 API 표준인 [AsyncAPI 3.0](https://www.asyncapi.com/docs/reference/specification/v3.0.0) 문서와 뷰어를 만듭니다.

- `MessageDocumentation` 과 `events(...)` 는 스니펫과 함께 AsyncAPI 메타데이터를 남깁니다. 따로 할 일은 없습니다.
- Gradle 플러그인의 `apiDocsAsyncApi` 태스크가 `asyncapi.yaml` 과 뷰어 `asyncapi.html` 을 만듭니다. 서버는 `asyncApi { server(...) }`, 파일 경로는 `asyncApi { fileName }` 으로 정합니다. 문서화한 메시지·SSE 가 하나도 없으면 만들지 않습니다.
- 뷰어의 **문서** 탭은 AsyncAPI 공식 뷰어입니다. **Try it out** 탭에서는 STOMP 연결·구독·전송, WebSocket 연결·전송, SSE 구독을 직접 해 볼 수 있습니다. 서버 주소 기본값은 페이지를 연 앱이고, CDN 스크립트는 무결성(SRI)을 검증합니다.
- 플러그인 없이 쓰려면 메인 클래스 `io.github.kardx.asyncapi.AsyncApiCli` 를 테스트 런타임 클래스패스로(예: Gradle `JavaExec` 태스크, `exec-maven-plugin`) `--snippets`(기본 `build/generated-snippets`), `--output`(기본 `build/api-spec/asyncapi.yaml`), `--viewer`(없으면 뷰어 없음), `--title`(기본 `API`), `--version`(기본 `1.0.0`), `--description`(선택, AsyncAPI `info.description`), `--server name=protocol://host[/path]`(여러 번, 예: `stomp=stomp://localhost:8080/ws`), `--security`(여러 번, [아래](#asyncapi-인증)) 인자로 실행합니다. 문서화한 것이 없으면 `AsyncAPI: no documented WebSocket, STOMP or SSE messages, nothing generated` 를 출력하고, 인자가 잘못되면 종료 코드 2 로 끝납니다.

| 라이브러리 | AsyncAPI |
|---|---|
| 목적지, SSE 경로 | channel (`{roomId}` 는 parameter) |
| `send(...)` | operation `receive` (서버 관점) |
| `subscribe(...)`, SSE 이벤트 | operation `send` |
| `name`, SSE 이벤트 이름 | message 이름 |
| 헤더·필드 | headers·payload JSON Schema (required, 제약조건, `deprecated`, 마스킹 표시 `x-masked`) |
| 실제 페이로드 | examples |
| 테스트 클래스·메서드 `@DisplayName` | tag·summary |

코드에서는 `AsyncApiGenerator.generate(snippetsDir, output, AsyncApiInfo(...), viewer)` 를 부릅니다. `AsyncApiInfo` 는 `title`, `version`, `description`, `servers`(`AsyncApiServer(name, host, protocol, pathname, description)`), `securitySchemes`(`AsyncApiSecurityScheme.bearer`·`basic`, `AsyncApiApiKeyLocation` 을 받는 `apiKey`)를 받고, 인증 방식 이름이 겹치면 거부합니다. `generate` 는 메시지 수를 돌려주고, 0 이면 파일을 쓰지 않습니다.

```kotlin
val info = AsyncApiInfo(
    title = "Chat",
    version = "1.0.0",
    description = null,                                                       // info.description
    servers = listOf(AsyncApiServer("stomp", "localhost:8080", "stomp", "/ws")), // name, host, protocol, pathname
    securitySchemes = listOf(
        AsyncApiSecurityScheme.bearer("bearerAuth"),
        AsyncApiSecurityScheme.apiKey("apiKeyAuth", AsyncApiApiKeyLocation.HEADER, "X-API-KEY"),
    ),
)
val messages = AsyncApiGenerator.generate(snippetsDir, output, info, viewer)   // 0 이면 문서화한 것이 없어 파일을 쓰지 않음
```

검증용 테스트는 `asyncApi.enabled = false` 로 스펙에서 뺍니다.

### AsyncAPI 인증

연결할 때 자격 증명이 필요한 서버라면 Gradle 플러그인에서 인증 방식을 선언합니다. 스펙에 인증 방식이 들어가고, 뷰어의 Try it out 이 자격 증명을 보낼 수 있게 됩니다.

```kotlin
kardx {
    asyncApi {
        server("stomp", "stomp://localhost:8080/ws")
        bearerAuth()                 // bearerAuth: Authorization: Bearer <토큰>
        basicAuth()                  // basicAuth: Authorization: Basic <base64(사용자:비밀번호)>
        apiKeyHeader("X-API-KEY")    // apiKeyAuth: X-API-KEY 헤더의 키
    }
}
```

서버가 받는 방식만 선언합니다. 여럿이면 클라이언트는 그중 하나로 인증합니다. `apiKeyHeader` 는 스키마 이름이 `apiKeyAuth` 하나라 두 번 부르면 마지막 헤더가 남습니다. 쿼리 파라미터나 쿠키로 받는 키는 맵에 CLI 값을 직접 넣습니다: `securitySchemes.put("tokenQuery", "apiKey:query:access_token")`.

플러그인 없이 쓸 때는 `AsyncApiCli` 에 방식마다 `--security name=<종류>` 를 줍니다. 코드에서는 `AsyncApiInfo` 에 `securitySchemes = listOf(AsyncApiSecurityScheme.bearer("bearerAuth"), ...)` 를 넘기고, 스키마 이름은 겹치면 안 됩니다.

| `--security` 값 | 플러그인 | AsyncAPI 스키마 |
|---|---|---|
| `bearerAuth=bearer` | `bearerAuth()` | `type: http`, `scheme: bearer` |
| `basicAuth=basic` | `basicAuth()` | `type: http`, `scheme: basic` |
| `apiKeyAuth=apiKey:header:X-API-KEY` | `apiKeyHeader("X-API-KEY")` | `type: httpApiKey`, `in: header`, `name: X-API-KEY` |
| `tokenQuery=apiKey:query:access_token` | `securitySchemes.put(...)` | `type: httpApiKey`, `in: query` |
| `session=apiKey:cookie:SESSION` | `securitySchemes.put(...)` | `type: httpApiKey`, `in: cookie` |

방식은 `components.securitySchemes` 에 들어가고, 모든 서버의 `security` 가 전부를 가리킵니다. 문서 탭은 서버와 함께 이 방식을 보여 줍니다.

**Try it out.** 연결 설정에 인증 상자가 생겨 방식과 자격 증명이 들어가는 자리를 보여 줍니다. 첫 방식이 골라져 있으니 다른 방식이나 "사용 안 함"을 고르고, 토큰·사용자 이름과 비밀번호·키를 넣습니다. 값은 페이지 메모리에만 두고 저장하지 않으므로 새로 고치면 사라집니다. 브라우저가 페이지에 허용하는 것이 프로토콜마다 달라 보내는 방법도 다릅니다.

| 방식 | STOMP | 순수 WebSocket, SSE |
|---|---|---|
| Bearer, Basic, 헤더 API 키 | CONNECT 프레임 헤더 (`Authorization` 또는 키 헤더) | 기본으로 보내지 않습니다: 브라우저는 WebSocket 핸드셰이크와 `EventSource` 에 헤더를 붙일 수 없습니다. "WebSocket·SSE 는 쿼리 파라미터로 보내기"를 켜면 주소에 붙입니다 (파라미터 이름 기본값 `access_token`, 값은 토큰·키 그대로, Basic 은 `사용자:비밀번호` 의 base64) |
| 쿼리 파라미터 API 키 | 연결 주소의 쿼리 파라미터 | 항상 쿼리 파라미터 |
| 쿠키 API 키 | 페이지가 넣지 못함 | 페이지가 넣지 못함 |

쿠키는 브라우저가 서버 주소에 대해 이미 가진 쿠키를 보냅니다. 메시지 로그의 연결 주소는 자격 증명을 `****` 로 바꿔 보여 줍니다. 쿼리 파라미터 방식은 서버가 그렇게 받을 때만 쓸모가 있으니 핸드셰이크 인터셉터나 필터를 확인합니다.
