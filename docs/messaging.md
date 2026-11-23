**English** | [한국어](ko/messaging.md)

# SSE · WebSocket · AsyncAPI

[Document list](README.md)

<!-- toc -->
**Contents**

- [Viewing in a browser](#viewing-in-a-browser)
- [Server-Sent Events](#server-sent-events)
  - [Streams that never end](#streams-that-never-end)
- [WebSocket / STOMP](#websocket--stomp)
  - [Connection headers (authentication)](#connection-headers-authentication)
  - [Sending and receiving with StompTestClient](#sending-and-receiving-with-stomptestclient)
  - [Testing WebSocket handlers](#testing-websocket-handlers)
- [AsyncAPI](#asyncapi)
  - [Authentication in AsyncAPI](#authentication-in-asyncapi)
<!-- /toc -->

This page covers APIs that don't finish in a single HTTP request and response. Server-Sent Events are documented with MockMvc (streams that end also work with WebTestClient, see [WebTestClient](webtestclient.md#documentation-tests)), WebSocket and STOMP messages with `MessageDocumentation`, and the machine-readable spec is produced as AsyncAPI.

## Viewing in a browser

| What you want to see | App address | File |
|---|---|---|
| SSE document (event table, fields per event) | `http://localhost:8080/docs/notification-api-docs-test.html#subscribe` | `build/api-docs/notification-api-docs-test.html` |
| WebSocket and STOMP message document | `http://localhost:8080/docs/chat-message-docs-test.html#chat` | `build/api-docs/chat-message-docs-test.html` |
| AsyncAPI viewer (documentation tab) | `http://localhost:8080/docs/asyncapi.html` | `build/api-docs/asyncapi.html` |
| AsyncAPI Try it out tab (also for subscribing to SSE streams that never end) | `http://localhost:8080/docs/asyncapi.html#try-it-out` | Same file + `#try-it-out` (the server address defaults to the app) |
| AsyncAPI spec | `http://localhost:8080/docs/asyncapi.yaml` | `build/api-docs/asyncapi.yaml` |

The `/docs` in the address comes from the Gradle plugin's `bootJar.path` (default `static/docs`), and the port from the app settings. File paths assume `./gradlew apiDocs` and can be changed with the plugin's `outputDirectory`. The document name is the test class name, and the part after `#` is the test method name converted to kebab-case (`ProductApiDocsTest.getProductNotFound` → `product-api-docs-test.html#get-product-not-found`).

- SSE and message documents go into the same REST Docs HTML as HTTP APIs. The AsyncAPI viewer is the only separate page.
- Try it out connects to a real server. When opened from the app, that app is the default server; when opened as a file, the spec's `http` server (or `localhost:8080` if there is none) is the default. You can change it in the input field at the top of the page.
- The viewer loads the official AsyncAPI React viewer and a STOMP client from a CDN, so it needs an internet connection.
- Message APIs don't appear in Swagger UI, because OpenAPI can't express WebSocket or STOMP.

## Server-Sent Events

Receive the stream to the end with `performAsync`, then document the events with `events`. The controller must close the stream in the test (`emitter.complete()`).

```kotlin
mockMvc
    .performAsync(
        get("/api/notifications/stream")
            .accept(MediaType.TEXT_EVENT_STREAM),
    )
    .andStatus(HttpStatus.OK)
    .andDocument {
        description = "Receives notifications as Server-Sent Events."
        requestHeaders("Last-Event-ID" headerMeans "ID of the last event when reconnecting" isOptional true)
        events(
            "connected" eventMeans "Connected" withFields ConnectedEvent::class,
            "notification" eventMeans "New notification" withFields NotificationEvent::class,
            "maintenance" eventMeans "Maintenance notice" isOptional true,
        )
    }
```

- The `sse-events` snippet is a table of event names, required flags and descriptions. `response-fields-event-<name>` is the field table for that event's `data`.
- `withFields ConnectedEvent::class` (`import io.github.kardx.extractor.withFields`) extracts fields from that class with the response rules. For generic events (`Envelope<Payload>`) or to adjust the extraction result, pass a list: `withFields responseFieldsOf<Envelope<Payload>>()`, `withFields responseFieldsOf<ConnectedEvent>().except("debug")`.
- The test fails if a required event is missing from the stream or an undocumented event arrives. `relaxed(RelaxedSection.EVENTS)` (or `relaxed = true`, which relaxes every section) allows the latter and undocumented `data` fields ([Relaxed verification](http-api.md#relaxed-verification)).
- Optional events that aren't in the stream get no field table. An event without `event:` is named `message`.

### Streams that never end

SSE in real services usually keeps the connection open and sends an event whenever one occurs. `performAsync` waits until the response ends, so for such streams use `performStream` (`import io.github.kardx.sse.performStream`). Once it has received the first `eventCount` events, it freezes the response so far and documents it. Even if the stream keeps writing, the document is the same every time.

```kotlin
mockMvc
    .performStream(
        get("/api/todos/stream")
            .accept(MediaType.TEXT_EVENT_STREAM),
        eventCount = 2,
    ) {
        // Runs after subscribing. Use it to trigger events with other requests.
        mockMvc
            .perform(
                post("/api/todos")
                    .jsonContent(CreateTodoRequest("Check the stream")),
            )
    }
    .andStatus(HttpStatus.OK)
    .andDocument {
        events(
            "connected" eventMeans "Connected" withFields ConnectedEvent::class,
            "todo-created" eventMeans "Todo created" withFields TodoCreatedEvent::class,
            "heartbeat" eventMeans "Keeps the connection alive. Every 5 seconds" isOptional true withFields HeartbeatEvent::class,
        )
    }
```

| Argument | Default | Description |
|---|---|---|
| `eventCount` | `1` | Number of events to receive. Only events terminated by a blank line are counted; comments (`:ping`) are not. Different from `events(...)` in the block (event descriptions) |
| `timeout` | 5 seconds | If not received within this time, fails with the number received (`Expected 2 stream events within PT5S but received 1. Increase ...`). Increase the time or trigger events in `whileStreaming` |
| `whileStreaming` | None | Code to run after subscribing |

- Periodic events (heartbeats) that arrive during the test make the document unstable. Set a long interval in the test settings and document that event with `isOptional true`.
- To watch it as documentation, subscribe in Try it out of the AsyncAPI viewer. Events keep accumulating while the connection stays open.
- Subscription request headers (`requestHeaders`) go into the REST Docs table and `x-request-headers` of the AsyncAPI channel. AsyncAPI's HTTP binding has no place for request headers, so they are kept as an extension field. A browser `EventSource` can't send headers, so the SSE subscription in Try it out connects without headers.
- APIs documented with `performStream` are left out of OpenAPI (Swagger) automatically. Swagger UI shows results only after the response ends, so Try it out would keep loading. They remain in the REST Docs documents and AsyncAPI. To include them anyway, use `open-api.include-streams: true`.

## WebSocket / STOMP

There is no HTTP request, so you use `MessageDocumentation`. The examples below use JUnit parameter injection; in Kotest, pass `restDocs.provider` or `RestDocsExtension.currentTestProvider` ([Kotest](kotest.md#websocket--stomp-messages)). If you pass the payload actually exchanged, the fields are verified against that payload. The destination and payload are arguments, and the description, headers and fields go in a block, like REST's `andDocument { ... }`.

```kotlin
@Test
@DisplayName("Chat message")
fun chat(provider: RestDocumentationContextProvider) {
    // Send to /app/chat.send and receive frameBody from /topic/rooms.1 (StompTestClient below)

    val docs = MessageDocumentation(provider)                   // STOMP (default). For plain WebSocket, see the WebSocketHandlerTester example below

    docs.send("/app/chat.send", payload = ChatSendRequest(roomId = 1, content = "Hello")) {  // object, String or ByteArray
        description = "Sends a message to a chat room."
        name = "chatSend"                                       // AsyncAPI message name
        headerValues("content-type" to "application/json")      // actual header values
        headers("content-type" headerMeans "Payload format")       // header descriptions (same as requestHeaders in the HTTP block)
        fields<ChatSendRequest>()
    }
    docs.subscribe("/topic/rooms.{roomId}", payload = frameBody) {
        fields<ChatMessage>()
    }
}
```

| Block item | Meaning |
|---|---|
| `description` | Message description. Used in the snippet and the AsyncAPI description |
| `name` | AsyncAPI message name. If absent, it is built from the direction and destination (`send-app-chat-send`). When several tests document the same destination, direction and name, examples are added to one message only if the payload structure (field paths, types, required flags) is the same; if the structure differs, it becomes a separate message such as `subscribe-ws-shape-2`. If they are different messages, give them a `name` |
| `headers(...)` | Descriptions of the headers to document (`"name" headerMeans "description"`). Same as `requestHeaders(...)` in the HTTP block. Fails if a required header is not in `headerValues` |
| `headerValues(...)` | Actual header values (`"name" to "value"` or a `Map`). Headers without descriptions don't appear in the table or AsyncAPI |
| `header(name, value, description)` | `headerValues` and `headers` in one call |
| `fields(...)` | Payload fields. Verified against the actual payload. A hand-written list or one extracted with `requestFieldsOf<T>()` and the like |
| `fields<T>(path = "")` | Shortcut that extracts from a type and registers it. It picks the rules that match the direction: `send` and `connect` use request rules (`requestFieldsOf`: properties with default values are optional, read-only ones are excluded), `subscribe` uses response rules (`responseFieldsOf`). To adjust or combine lists, use `fields(responseFieldsOf<T>() + ...)` |
| `relaxed = true` | Allows undocumented payload fields |

The protocol is chosen with `MessageProtocol` (`io.github.kardx.dsl`): `STOMP` (default), `WEBSOCKET`, `SSE`. It appears as `STOMP`, `WebSocket` or `SSE` in the documents and in `x-protocol` of the AsyncAPI channel, and the AsyncAPI servers (`stomp`, `ws`, `http` …) that can connect with that protocol are attached to the channel.

For a protocol not in the list, pass a string (`MessageDocumentation(provider, "MQTT")`); its channels get every AsyncAPI server attached:

- Leading and trailing whitespace is trimmed, and if it is the name or alias of a known protocol (case-insensitive: `"websocket"` and `"ws"` → `WebSocket`, `"stomp"` → `STOMP`, `"sse"` and `"http"` → `SSE`), it is treated as that protocol and the display name is normalized too (the document display and the AsyncAPI server matching use the same value).
- An empty string is rejected.

`headers`, `headerValues` and `fields` append when called several times, and accept both varargs and a `List` (`Map`). To choose the direction yourself, use `docs.document(MessageDirection.SEND, destination, payload) { ... }`. `MessageDirection` and the block type `MessageDocumentationBuilder` are in `io.github.kardx.dsl`, like the HTTP block type; `MessageDocumentation` and `WebSocketHandlerTester` are in `io.github.kardx.websocket`; the shortcut `fields<T>()` is in `io.github.kardx.extractor`.

Each message produces `message-NN` (direction, destination, headers, payload example) and `request-fields-message-NN` (field table), and `@Masked` fields of the payload type get a [masking marker](http-api.md#marking-masked-fields) in the field table and the AsyncAPI schema. They go into the generated document in message order. The test fails if a required header is missing or the fields don't match the payload. If you turn on [masking](configuration.md#masking-sensitive-data), header values are masked. AsyncAPI documents and the Try it out viewer are distributed outputs, so sensitive header values such as `Authorization` and `Cookie` are always masked there, regardless of the masking setting (`Bearer ****`).
Destinations don't have to start with `/`, as in `/app/chat.send` (broker-style names such as `jms.queue.orders` and `queue/x` also work).

For header values that change on every run (`message-id`, `subscription`, and so on), only check that the actual frame has a value, and pass a fixed example value to the documentation. Then the snippets are identical on every run, so when you rerun the tests, Asciidoctor and AsyncAPI generation are skipped as UP-TO-DATE ([incremental build](build.md#output-locations)).

### Connection headers (authentication)

Headers sent once when connecting, such as STOMP CONNECT frame headers or WebSocket handshake headers, are documented with `connect`.

```kotlin
docs.connect("/ws") {
    description = "Authentication and client headers sent once when connecting."
    headerValues("Authorization" to "Bearer $token", "X-Client-Version" to "1.4.0")   // values actually sent
    headers(
        "Authorization" headerMeans "Bearer access token",
        "X-Client-Version" headerMeans "App version" isOptional true,
    )
}
// Value and description at once: header("Authorization", "Bearer $token", "Bearer access token")
```

- REST Docs gets a `CONNECT /ws` section and a header table. The test fails if a required header is not in `headerValues`.
- In AsyncAPI, they go into the WebSocket binding headers (`bindings.ws.headers`) of channels with the same protocol.
- The STOMP CONNECT header fields in AsyncAPI Try it out are prefilled with this example. If it was masked, replace the value with a real token before connecting.
- A browser `WebSocket` can't send handshake headers, so connection headers for plain WebSocket remain documentation only. To let Try it out authenticate anyway, declare how the server authenticates ([Authentication in AsyncAPI](#authentication-in-asyncapi)).

### Sending and receiving with StompTestClient

`MessageDocumentation` documents frames you already have. To get them from the running app, use `StompTestClient` (`io.github.kardx.websocket`): it connects with CONNECT headers, subscribes, sends, and waits for the frame. Start the app on a port (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).

Without it, every STOMP test repeats the same plumbing:

```kotlin
// Without StompTestClient
val client = WebSocketStompClient(StandardWebSocketClient()).apply { messageConverter = StringMessageConverter() }
val connectHeaders = StompHeaders().apply { add("Authorization", "Bearer test-token") }
val session = client.connectAsync("ws://localhost:$port/ws", WebSocketHttpHeaders(), connectHeaders, object : StompSessionHandlerAdapter() {})
    .get(5, TimeUnit.SECONDS)
val frames = LinkedBlockingQueue<String>()
session.subscribe("/topic/rooms.1", object : StompFrameHandler {
    override fun getPayloadType(headers: StompHeaders): Type = String::class.java
    override fun handleFrame(headers: StompHeaders, payload: Any?) { frames += payload as String }
})
// then: send with a content-type header, resend until the broker has registered the subscription,
// poll the queue with a timeout, disconnect, stop the client
```

With it:

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Chat (STOMP)")
class ChatMessageDocsTest : RestDocsTestSupport() {
    @LocalServerPort
    var port: Int = 0

    @Test
    @DisplayName("Send and receive a message")
    fun chat(provider: RestDocumentationContextProvider) {
        val connectHeaders = mapOf("Authorization" to "Bearer test-token")
        val request = ChatSendRequest(roomId = 1, content = "Hello")

        val frame = StompTestClient("ws://localhost:$port/ws", connectHeaders = connectHeaders).use { stomp ->
            stomp.sendAndReceive("/app/chat.send", request, subscribeTo = "/topic/rooms.1")
        }

        val docs = MessageDocumentation(provider)

        docs.connect("/ws") {
            headerValues(connectHeaders)
            headers("Authorization" headerMeans "Bearer access token")
        }
        docs.send("/app/chat.send", payload = request) { fields<ChatSendRequest>() }
        docs.subscribe("/topic/rooms.{roomId}", payload = frame.body) { fields<ChatMessage>() }
    }
}
```

| Member | What it does |
|---|---|
| `StompTestClient(url, connectHeaders, timeout, webSocketClient)` | Connects right away. `connectHeaders` go into the CONNECT frame (also readable as `stomp.connectHeaders`). `timeout` defaults to 5 seconds; `webSocketClient` defaults to the standard JSR-356 client (provided by Tomcat or Jetty). Fails with `IllegalStateException` if it can't connect in time or the server refuses |
| `sendAndReceive(destination, payload, subscribeTo)` | Subscribes, sends, and returns the first frame received. It resends until a frame arrives, so the handler may receive the message more than once (details below the table) |
| `send(destination, payload, headers)` | Sends one frame. Strings and `ByteArray` go as is, objects as JSON with the configured Jackson mapper. `content-type` defaults to `application/json` |
| `subscribe(destination)` | Returns a `StompSubscription`: `await(timeout)` takes the next frame, `unsubscribe()` ends it |
| `StompFrame` | `headers` (first value per name) and `body` (the payload as a string). Pass `frame.body` to `docs.subscribe(...)` |
| `close()` | Disconnects. Use `use { }` |

How `sendAndReceive` delivers:

- A message sent before the broker has registered the subscription can be lost, and Spring's simple broker sends no receipt for SUBSCRIBE. So it resends the same payload until a frame arrives, waiting 200 ms at first and doubling up to 1 second.
- A handler that takes time (saving to a database, for example) may therefore receive the message more than once, and the returned frame may come from any of the sends. To send exactly once, use `subscribe` → `send` → `StompSubscription.await` yourself.
- It fails if nothing arrives within the timeout. The whole call, resends included, stays within the timeout, and nothing is resent right at the deadline.

`StompTestClient` works on Spring Framework 6 (Boot 3) and 7 (Boot 4). If the server rejects the CONNECT (for example a channel interceptor that throws for a missing token), the constructor throws `IllegalStateException` instead of returning a client that never connects, so a wrong token fails the test right there.

If the server sends a STOMP `ERROR` frame or drops the connection later (an interceptor rejecting a SEND or SUBSCRIBE, an unknown destination …), `sendAndReceive` and `StompSubscription.await` fail at once instead of waiting for the timeout, with the server's `message` header and body:

```
java.lang.IllegalStateException: STOMP ERROR from server: Access denied: Missing token
```

A rejected CONNECT fails the constructor the same way: `Could not connect to STOMP endpoint ws://localhost:51234/ws: STOMP ERROR from server: ...`. A dropped connection is reported as `STOMP connection failed: <reason>`.

Frame bodies are kept as strings, so the documents show the JSON the server actually sent. `spring-messaging` and `spring-websocket` must be on the test classpath; an app that uses STOMP (`spring-boot-starter-websocket`) already has them. For header values that change on every run (`message-id`, `subscription`), check them on `frame.headers` and document fixed example values (see above).

### Testing WebSocket handlers

`WebSocketHandlerTester` calls a `WebSocketHandler` (`TextWebSocketHandler` and so on) without a server. It delivers connect, message and close events through a fake session, and collects the messages the handler sends.

```kotlin
WebSocketHandlerTester(EchoHandler(), path = "/ws/echo", attributes = mapOf("userId" to "hong")).use { ws ->
    val welcome = ws.connect().single()                         // message sent in afterConnectionEstablished
    val reply = ws.send("""{"text":"hello"}""").single()        // message sent while handling this message

    val docs = MessageDocumentation(provider, MessageProtocol.WEBSOCKET)

    docs.subscribe("/ws/echo", welcome) {
        description = "Received right after connecting."
        fields<Welcome>()
    }
    docs.send("/ws/echo", """{"text":"hello"}""") { fields<EchoRequest>() }
    docs.subscribe("/ws/echo", reply) { fields<EchoResponse>() }
}
```

| Feature | Usage |
|---|---|
| Session path, handshake headers, session attributes, authenticated user | Constructor `path`, `handshakeHeaders`, `attributes`, `principal` |
| Text and binary messages | `send(String)`, `send(ByteArray)` |
| Handlers that send later from another thread | `awaitMessages(count, timeout)` |
| Connection state | `isOpen`, `closeCode` |
| Closing | `close()` or `use { }` |

## AsyncAPI

OpenAPI can't express WebSocket or STOMP messages. So the library produces a document and viewer in [AsyncAPI 3.0](https://www.asyncapi.com/docs/reference/specification/v3.0.0), the standard for message APIs.

- `MessageDocumentation` and `events(...)` record AsyncAPI metadata along with the snippets. There is nothing else to do.
- The Gradle plugin's `apiDocsAsyncApi` task creates `asyncapi.yaml` and the viewer `asyncapi.html`. Servers are set with `asyncApi { server(...) }` and the file path with `asyncApi { fileName }`. If no messages or SSE are documented, nothing is created.
- The viewer's **documentation** tab is the official AsyncAPI viewer. In the **Try it out** tab, you can connect, subscribe and send over STOMP, connect and send over WebSocket, and subscribe to SSE yourself. The server address defaults to the app that served the page, and CDN scripts are checked for integrity (SRI).
- To use it without the plugin, run the main class `io.github.kardx.asyncapi.AsyncApiCli` on the test runtime classpath (for example a Gradle `JavaExec` task, or `exec-maven-plugin`) with the arguments below. With nothing documented it prints `AsyncAPI: no documented WebSocket, STOMP or SSE messages, nothing generated`; invalid arguments exit with code 2.
  - `--snippets` (default `build/generated-snippets`)
  - `--output` (default `build/api-spec/asyncapi.yaml`)
  - `--viewer` (no viewer if omitted)
  - `--title` (default `API`)
  - `--version` (default `1.0.0`)
  - `--description` (optional, AsyncAPI `info.description`)
  - `--server name=protocol://host[/path]` (repeatable, for example `stomp=stomp://localhost:8080/ws`)
  - `--security` (repeatable, [below](#authentication-in-asyncapi))

| Library | AsyncAPI |
|---|---|
| Destination, SSE path | channel (`{roomId}` is a parameter) |
| `send(...)` | operation `receive` (server's point of view) |
| `subscribe(...)`, SSE events | operation `send` |
| `name`, SSE event name | message name |
| Headers and fields | headers and payload JSON Schema (required, constraints, `deprecated`, masking marker `x-masked`) |
| Actual payload | examples |
| Test class and method `@DisplayName` | tag and summary |

In code, call `AsyncApiGenerator.generate(snippetsDir, output, AsyncApiInfo(...), viewer)`. `AsyncApiInfo` takes `title`, `version`, `description`, `servers` (`AsyncApiServer(name, host, protocol, pathname, description)`) and `securitySchemes` (`AsyncApiSecurityScheme.bearer`, `basic` and `apiKey` with an `AsyncApiApiKeyLocation`); duplicate scheme names are rejected. `generate` returns the number of messages, and writes no file when it is 0.

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
val messages = AsyncApiGenerator.generate(snippetsDir, output, info, viewer)   // 0: nothing documented, no file written
```

Tests that exist only for verification are left out of the spec with `asyncApi.enabled = false`.

### Authentication in AsyncAPI

If the server needs credentials to connect, declare how it authenticates in the Gradle plugin. The spec then says so, and the viewer's Try it out can send a credential.

```kotlin
kardx {
    asyncApi {
        server("stomp", "stomp://localhost:8080/ws")
        bearerAuth()                 // bearerAuth: Authorization: Bearer <token>
        basicAuth()                  // basicAuth: Authorization: Basic <base64(user:password)>
        apiKeyHeader("X-API-KEY")    // apiKeyAuth: the key in the X-API-KEY header
    }
}
```

Declare only the ones your server accepts; with several, a client uses any one of them. `apiKeyHeader` uses the scheme name `apiKeyAuth`, so calling it twice keeps the last header. For a key in a query parameter or a cookie, put the CLI value into the map directly: `securitySchemes.put("tokenQuery", "apiKey:query:access_token")`.

Without the plugin, pass `--security name=<kind>` to `AsyncApiCli`, once per scheme. In code, pass `securitySchemes = listOf(AsyncApiSecurityScheme.bearer("bearerAuth"), ...)` to `AsyncApiInfo`; scheme names must be unique.

| `--security` value | Plugin | AsyncAPI scheme |
|---|---|---|
| `bearerAuth=bearer` | `bearerAuth()` | `type: http`, `scheme: bearer` |
| `basicAuth=basic` | `basicAuth()` | `type: http`, `scheme: basic` |
| `apiKeyAuth=apiKey:header:X-API-KEY` | `apiKeyHeader("X-API-KEY")` | `type: httpApiKey`, `in: header`, `name: X-API-KEY` |
| `tokenQuery=apiKey:query:access_token` | `securitySchemes.put(...)` | `type: httpApiKey`, `in: query` |
| `session=apiKey:cookie:SESSION` | `securitySchemes.put(...)` | `type: httpApiKey`, `in: cookie` |

The schemes go into `components.securitySchemes`, and every server's `security` lists all of them. The documentation tab shows them with the servers.

**Try it out.** The connection settings get an Authentication box that lists the schemes and where each credential goes. The first scheme is selected; pick another or None, then enter a token, a user name and password, or a key. The value stays in the page's memory only; it isn't saved and is gone after a reload. How it is sent depends on the protocol, because browsers limit what a page can set:

| Scheme | STOMP | Plain WebSocket, SSE |
|---|---|---|
| Bearer, Basic, API key in a header | CONNECT frame header (`Authorization` or the key's header) | Not sent by default: browsers can't set headers on the WebSocket handshake or on `EventSource`. Check "Send as a query parameter for WebSocket and SSE" to append it to the URL (parameter name default `access_token`; the raw token, key or `user:password` in base64) |
| API key in a query parameter | Query parameter on the connection URL | Query parameter, always |
| API key in a cookie | Not set by the page | Not set by the page |

For cookies, the browser sends whatever cookies it already has for the server's origin. The message log shows connection URLs with the credential replaced by `****`. The query-parameter option only helps if your server accepts the credential that way; check your handshake interceptor or filter.
