package io.github.kardx.internal.i18n

import io.github.kardx.config.DocsLanguage

/**
 * AsyncAPI 뷰어(`viewer.html`·`viewer.js`) 화면 문구. 페이지를 만들 때 고른 언어의 문구만 JSON 으로 넣고, 스크립트는
 * `T.<이름>` 으로 읽는다. 브라우저 언어는 보지 않는다. `{0}`, `{1}` … 은 스크립트의 `fmt` 가 채운다.
 *
 * `DOC_*` 는 공식 렌더러의 영어 고정 문구를 옮긴 것이라 영어 문구는 렌더러 원문과 같다 (영어에서는 바뀌지 않는다).
 */
internal enum class ViewerText(private val english: String, private val korean: String) {
    // ---- 정적 화면 (viewer.html 의 data-text) ----
    TAB_DOC("Docs", "문서"),
    CONNECTION_SETTINGS("Connection", "연결 설정"),
    SERVER_URL("Server URL", "서버 주소"),
    WS_HELP(
        "WebSocket and STOMP connect to the same address with ws://.",
        "WebSocket·STOMP 는 같은 주소를 ws:// 로 바꿔 연결합니다.",
    ),
    CONNECT_HEADERS("STOMP CONNECT headers (JSON)", "STOMP CONNECT 헤더 (JSON)"),
    MESSAGE_LOG("Message log", "메시지 로그"),
    CLEAR_LOG("Clear", "지우기"),
    LOG_EMPTY("Messages appear here when you subscribe or send.", "구독하거나 메시지를 보내면 여기에 나타납니다."),

    // ---- 연결 설정: 인증 (components.securitySchemes) ----
    AUTHENTICATION("Authentication", "인증"),
    AUTH_USE("Use", "사용할 방식"),
    AUTH_NONE("None", "사용 안 함"),
    AUTH_BEARER("Bearer token", "Bearer 토큰"),
    AUTH_BASIC("HTTP Basic", "HTTP Basic"),
    AUTH_API_KEY("API key", "API 키"),
    AUTH_IN_HEADER("{0} header", "{0} 헤더"),
    AUTH_IN_QUERY("{0} query parameter", "{0} 쿼리 파라미터"),
    AUTH_IN_COOKIE("{0} cookie", "{0} 쿠키"),
    AUTH_TOKEN("Token", "토큰"),
    AUTH_USERNAME("Username", "사용자 이름"),
    AUTH_PASSWORD("Password", "비밀번호"),
    AUTH_API_KEY_VALUE("API key", "API 키"),
    AUTH_MEMORY_ONLY(
        "Kept in this page's memory only. Nothing is saved; reloading clears it.",
        "이 페이지의 메모리에만 둡니다. 저장하지 않으며 새로 고치면 사라집니다.",
    ),
    AUTH_STOMP_HEADER("STOMP sends it as a CONNECT frame header.", "STOMP 는 CONNECT 프레임 헤더로 보냅니다."),
    AUTH_BROWSER_LIMIT(
        "Browsers cannot set headers on the WebSocket handshake or on SSE (EventSource) requests. " +
            "For WebSocket and SSE you can send the credential as a query parameter instead.",
        "브라우저는 WebSocket 핸드셰이크와 SSE(EventSource) 요청에 헤더를 붙일 수 없습니다. " +
            "WebSocket·SSE 는 대신 쿼리 파라미터로 보낼 수 있습니다.",
    ),
    AUTH_AS_QUERY(
        "Send as a query parameter for WebSocket and SSE",
        "WebSocket·SSE 는 쿼리 파라미터로 보내기",
    ),
    AUTH_QUERY_NAME("Query parameter name", "쿼리 파라미터 이름"),
    AUTH_QUERY_ALWAYS(
        "Sent as a query parameter on every connection.",
        "모든 연결에 쿼리 파라미터로 보냅니다.",
    ),
    AUTH_COOKIE_NOTE(
        "This page cannot set the cookie. The browser sends the cookies it has for the server's origin.",
        "이 페이지는 쿠키를 넣을 수 없습니다. 브라우저가 서버 주소에 대해 가진 쿠키를 보냅니다.",
    ),

    // ---- 문서 탭: 방향 배지·문구 ----
    OUT_SHORT("→ Send", "→ 보내기"),
    OUT_LONG("Client → Server", "클라이언트 → 서버"),
    OUT_MESSAGE("Message sent by the client", "클라이언트가 보내는 메시지"),
    OUT_TIP(
        "The client sends this to the server (AsyncAPI action: receive)",
        "클라이언트가 서버로 보낸다 (AsyncAPI action: receive)",
    ),
    IN_SHORT("← Receive", "← 받기"),
    IN_LONG("Server → Client", "서버 → 클라이언트"),
    IN_MESSAGE("Message sent by the server", "서버가 보내는 메시지"),
    IN_TIP("The server sends this to the client (AsyncAPI action: send)", "서버가 클라이언트로 보낸다 (AsyncAPI action: send)"),
    ONE_OF("one of", "다음 중 하나"),

    // ---- 문서 탭: 공식 렌더러 문구 ----
    DOC_INTRODUCTION("Introduction", "소개"),
    DOC_SERVERS("Servers", "서버"),
    DOC_OPERATIONS("Operations", "오퍼레이션"),
    DOC_MESSAGES("Messages", "메시지"),
    DOC_SCHEMAS("Schemas", "스키마"),
    DOC_EXAMPLES("Examples", "예시"),
    DOC_EXPAND_ALL("Expand all", "모두 펼치기"),
    DOC_COLLAPSE_ALL("Collapse all", "모두 접기"),
    DOC_SERVERS_ONLY("Available only on servers:", "서버"),
    DOC_CHANNEL_BINDINGS("Channel specific information", "채널 바인딩"),
    DOC_CHANNEL_EXTENSIONS("Channel Extensions", "채널 확장 필드"),
    DOC_PARAMETERS("Parameters", "경로 변수"),
    DOC_GENERATED_EXAMPLE("This example has been generated automatically.", "스키마로 자동 생성한 예시입니다."),
    DOC_EXAMPLE_NUMBER("#{0} Example", "예시 #{0}"),

    // ---- Try it out: 연결 상태 ----
    NOT_CONNECTED("Not connected", "연결 안 됨"),
    CONNECTING("Connecting…", "연결 중…"),
    CONNECTED("Connected", "연결됨"),
    CONNECT_FAILED("Connection failed", "연결 실패"),
    REJECTED("Rejected", "거절됨"),
    WS_OPEN_COUNT("{0} connected", "{0}개 연결됨"),
    SSE_OPEN_COUNT("{0} subscribed", "{0}개 구독 중"),
    WS_NONE("No connections", "연결 없음"),
    SSE_NONE("No subscriptions", "구독 없음"),
    NOT_SUBSCRIBED("Not subscribed", "구독 안 함"),
    SUBSCRIBED("Subscribed", "구독 중"),

    // ---- Try it out: 버튼·입력 ----
    CONNECT("Connect", "연결"),
    DISCONNECT("Disconnect", "끊기"),
    CLOSE_SOCKET("Disconnect", "연결 끊기"),
    SUBSCRIBE("Subscribe", "구독"),
    UNSUBSCRIBE("Unsubscribe", "구독 해제"),
    STOP("Stop", "중지"),
    SEND("Send", "보내기"),
    DESTINATION("Destination", "목적지"),
    PATH("Path", "경로"),
    SEND_MESSAGES("→ Messages to send", "→ 보낼 메시지"),
    RECEIVE_MESSAGES("← Messages to receive", "← 받을 메시지"),
    HEADERS_JSON("Headers (JSON)", "헤더 (JSON)"),
    PAYLOAD("Payload", "페이로드"),
    EXAMPLE("Example", "예시"),
    SSE_EVENTS("Events:", "받는 이벤트:"),
    MASKED_HINT(
        "This value was masked for the docs (****). Replace it with a real value.",
        "문서용으로 가린 값(****)입니다. 실제 값으로 바꿔 주세요.",
    ),
    PATH_VARIABLE_HINT("Replace path variables {…} with real values", "경로 변수 {…} 를 실제 값으로 바꿔 주세요"),
    SSE_HEADERS_REQUIRED(
        "This subscription needs the {0} header(s). Browsers (EventSource) cannot send headers, so the server may reject it.",
        "이 구독은 {0} 헤더가 필요합니다. 브라우저(EventSource)는 헤더를 보낼 수 없어 서버가 거절할 수 있습니다.",
    ),

    // ---- Try it out: 메시지 로그 ----
    LOG_OUT("→ Sent", "→ 보냄"),
    LOG_IN("← Received", "← 받음"),
    LOG_INFO("• Info", "• 정보"),
    LOG_ERROR("✕ Error", "✕ 오류"),
    ERROR("error", "오류"),
    CONNECT_HEADERS_INVALID(
        "CONNECT headers are not valid JSON. Fix them and try again",
        "CONNECT 헤더가 JSON 이 아닙니다. 고친 뒤 다시 시도하세요",
    ),
    HEADERS_INVALID("Headers are not valid JSON", "헤더가 JSON 이 아닙니다"),
    STOMP_CONNECTED("STOMP connected {0}", "STOMP 연결됨 {0}"),
    STOMP_CONNECT_FAILED("STOMP connection failed {0}", "STOMP 연결 실패 {0}"),
    STOMP_CLOSED("STOMP connection closed", "STOMP 연결 종료"),
    WS_CONNECTED("WebSocket connected {0}", "WebSocket 연결됨 {0}"),
    WS_CONNECT_FAILED("WebSocket connection failed {0}", "WebSocket 연결 실패 {0}"),
    WS_CLOSED("WebSocket closed {0} (code {1})", "WebSocket 종료 {0} (code {1})"),
    SSE_SUBSCRIBING("SSE subscribe {0}", "SSE 구독 {0}"),
    SSE_STOPPED("SSE stopped {0}", "SSE 중지 {0}"),
    SSE_CLOSED("SSE stream closed {0}", "SSE 스트림 종료 {0}"),
    SSE_CONNECT_FAILED(
        "SSE connection failed {0} (the server rejected it or headers are required)",
        "SSE 연결 실패 {0} (서버가 거절했거나 헤더가 필요합니다)",
    ),
    SUBSCRIBED_TO("Subscribed {0}", "구독 {0}"),
    UNSUBSCRIBED_FROM("Unsubscribed {0}", "구독 해제 {0}"),
    ;

    /** [language] 의 문구. 자리 표시자(`{0}`)는 그대로 둔다. */
    fun text(language: DocsLanguage): String = when (language) {
        DocsLanguage.EN -> english
        DocsLanguage.KO -> korean
    }

    companion object {
        /** 이름 → [language] 문구. 뷰어 페이지에 JSON 으로 넣는다. */
        fun all(language: DocsLanguage): Map<String, String> = entries.associate { it.name to it.text(language) }
    }
}
