package io.github.kardx.config

import java.util.Objects

/**
 * 생성 문서에 include 하는 스니펫 종류. 선언 순서가 문서 안의 순서다.
 * 포함할 종류는 [io.github.kardx.config.DocumentSettings.snippets]
 * (yml `kardx.document-snippets`)로 정한다. 기본값은 [defaults].
 *
 * @property title 섹션 안에 표시할 소제목 (빈 문자열이면 소제목 없이 include)
 * @property includedByDefault false 이면 [io.github.kardx.config.DocumentSettings.snippets] 에
 *   명시해야 포함된다 (다른 스니펫과 내용이 겹치는 것들)
 */
public class SnippetFile private constructor(
    public val name: String,
    public val title: String,
    fileNamePattern: String,
    public val includedByDefault: Boolean,
    /** 라이브러리가 아는 종류인지. 사용자 종류는 false */
    public val builtIn: Boolean,
) {
    private val pattern = Regex("$fileNamePattern\\.adoc")

    /** 스니펫 파일 이름(확장자 포함)이 이 종류인지. */
    internal fun matches(fileName: String): Boolean = pattern.matches(fileName)

    /** 이름이 같으면 같은 종류다. */
    override fun equals(other: Any?): Boolean = other is SnippetFile && name == other.name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name

    /** 라이브러리가 아는 종류(문서 안 순서대로)와 사용자 종류를 만드는 함수. */
    public companion object {
        private val registered = mutableListOf<SnippetFile>()

        private fun builtIn(
            name: String,
            title: String,
            fileNamePattern: String,
            includedByDefault: Boolean = true,
        ): SnippetFile = SnippetFile(name, title, fileNamePattern, includedByDefault, builtIn = true)
            .also { registered += it }

        /** `api-summary.adoc`: 메서드·URL 템플릿·응답 상태와 설명. 이 라이브러리가 만들며 소제목 없이 섹션 맨 앞에 온다. */
        public val API_SUMMARY: SnippetFile = builtIn("API_SUMMARY", "", "api-summary")

        /** `message-NN.adoc`: WebSocket·STOMP 메시지 하나(방향, 목적지, 본문). 이 라이브러리가 만들며 소제목이 없다. */
        public val MESSAGE: SnippetFile = builtIn("MESSAGE", "", "message-\\d+")

        /** `request-fields-message-NN.adoc`: 같은 번호 메시지의 본문 필드 표. 소제목 `Payload Fields`. */
        public val MESSAGE_FIELDS: SnippetFile =
            builtIn("MESSAGE_FIELDS", "Payload Fields", "request-fields-message-\\d+")

        /** `curl-request.adoc`: REST Docs 가 만드는 curl 명령 예. */
        public val CURL_INFO: SnippetFile = builtIn("CURL_INFO", "CURL", "curl-request")

        /** `httpie-request.adoc`: HTTPie 명령 예. curl 과 겹쳐 기본으로는 넣지 않는다. */
        public val HTTPIE_INFO: SnippetFile =
            builtIn("HTTPIE_INFO", "HTTPie", "httpie-request", includedByDefault = false)

        /** `http-request.adoc`: 실제 HTTP 요청 원문. */
        public val REQUEST_INFO: SnippetFile = builtIn("REQUEST_INFO", "Request Info", "http-request")

        /** `path-parameters.adoc`: 경로 파라미터 표. */
        public val REQUEST_PATH_PARAMETERS: SnippetFile =
            builtIn("REQUEST_PATH_PARAMETERS", "Path Parameters", "path-parameters")

        /** `query-parameters.adoc`: 쿼리 파라미터 표. */
        public val QUERY_PARAMETERS: SnippetFile = builtIn("QUERY_PARAMETERS", "Query Parameters", "query-parameters")

        /** `form-parameters.adoc`: 폼 파라미터 표. */
        public val FORM_PARAMETERS: SnippetFile = builtIn("FORM_PARAMETERS", "Form Parameters", "form-parameters")

        /** `request-headers.adoc`: 요청 헤더 표. */
        public val REQUEST_HEADERS: SnippetFile = builtIn("REQUEST_HEADERS", "Request Headers", "request-headers")

        /** `request-cookies.adoc`: 요청 쿠키 표. */
        public val REQUEST_COOKIES: SnippetFile = builtIn("REQUEST_COOKIES", "Request Cookies", "request-cookies")

        /** `request-parts.adoc`: 멀티파트 파트 목록 표. */
        public val REQUEST_PARTS: SnippetFile = builtIn("REQUEST_PARTS", "Request Parts", "request-parts")

        /** `request-part-<파트>-fields.adoc`: 파트 본문의 필드 표. 소제목에 파트 이름을 괄호로 붙인다. */
        public val REQUEST_PART_FIELDS: SnippetFile =
            builtIn("REQUEST_PART_FIELDS", "Request Part Fields", "request-part-.+-fields")

        /** `request-part-<파트>-body.adoc`: 파트 본문 원문. 필드 표와 겹쳐 기본으로는 넣지 않는다. */
        public val REQUEST_PART_BODY: SnippetFile =
            builtIn("REQUEST_PART_BODY", "Request Part Body", "request-part-.+-body", includedByDefault = false)

        /** `request-fields.adoc`: 요청 본문 필드 표. */
        public val REQUEST_FIELDS: SnippetFile = builtIn("REQUEST_FIELDS", "Request Fields", "request-fields")

        /** `request-fields-<이름>.adoc`: 요청 필드의 subsection 표. 소제목에 이름을 괄호로 붙인다. */
        public val REQUEST_SUBSECTION_FIELDS: SnippetFile =
            builtIn("REQUEST_SUBSECTION_FIELDS", "Request Fields", "request-fields-.+")

        /**
         * `request-file.adoc`: 파일 요청 본문(바이너리 본문 또는 `requestFile(...)`)의 설명·미디어 타입·파일 이름·크기 표.
         * 이 라이브러리가 만들며, 있으면 문서에서 [REQUEST_BODY] 대신 들어간다.
         */
        public val REQUEST_FILE: SnippetFile = builtIn("REQUEST_FILE", "Request File", "request-file")

        /** `request-body.adoc`: 요청 본문 원문. 요청 원문과 겹쳐 기본으로는 넣지 않는다. */
        public val REQUEST_BODY: SnippetFile =
            builtIn("REQUEST_BODY", "Request Body", "request-body", includedByDefault = false)

        /** `http-response.adoc`: 실제 HTTP 응답 원문. */
        public val RESPONSE_INFO: SnippetFile = builtIn("RESPONSE_INFO", "Response Info", "http-response")

        /** `response-headers.adoc`: 응답 헤더 표. */
        public val RESPONSE_HEADERS: SnippetFile = builtIn("RESPONSE_HEADERS", "Response Headers", "response-headers")

        /** `response-cookies.adoc`: 응답 쿠키 표. */
        public val RESPONSE_COOKIES: SnippetFile = builtIn("RESPONSE_COOKIES", "Response Cookies", "response-cookies")

        /**
         * `response-file.adoc`: 파일 응답 본문(바이너리 본문 또는 `responseFile(...)`)의 설명·미디어 타입·파일 이름·크기 표.
         * 이 라이브러리가 만들며, 있으면 문서에서 [RESPONSE_BODY] 대신 들어간다.
         */
        public val RESPONSE_FILE: SnippetFile = builtIn("RESPONSE_FILE", "Response File", "response-file")

        /** `response-fields.adoc`: 응답 본문 필드 표. */
        public val RESPONSE_FIELDS: SnippetFile = builtIn("RESPONSE_FIELDS", "Response Fields", "response-fields")

        /** `sse-events.adoc`: SSE 이벤트 이름·필수 여부·설명 표. 이 라이브러리가 만든다. */
        public val SSE_EVENTS: SnippetFile = builtIn("SSE_EVENTS", "Events", "sse-events")

        /** `response-fields-event-<이벤트>.adoc`: SSE 이벤트 `data` 의 필드 표. 소제목에 이벤트 이름을 괄호로 붙인다. */
        public val EVENT_FIELDS: SnippetFile = builtIn("EVENT_FIELDS", "Event Data", "response-fields-event-.+")

        /** `response-fields-<이름>.adoc`: 응답 필드의 subsection 표(Map 필드 등). 소제목에 이름을 괄호로 붙인다. */
        public val RESPONSE_SUBSECTION_FIELDS: SnippetFile =
            builtIn("RESPONSE_SUBSECTION_FIELDS", "Response Fields", "response-fields-.+")

        /** `links.adoc`: HAL 등 하이퍼미디어 링크 표. */
        public val LINKS: SnippetFile = builtIn("LINKS", "Links", "links")

        /**
         * `error-responses.adoc`: 테스트가 받지 않고 선언만 한 오류 응답(`errorResponse(...)`)의 상태·설명 표와 응답별 필드 표·예시.
         * 이 라이브러리가 만들며, 선언이 있는 테스트에만 생긴다.
         */
        public val ERROR_RESPONSES: SnippetFile = builtIn("ERROR_RESPONSES", "Error Responses", "error-responses")

        /** `response-body.adoc`: 응답 본문 원문. 응답 원문과 겹쳐 기본으로는 넣지 않는다. */
        public val RESPONSE_BODY: SnippetFile =
            builtIn("RESPONSE_BODY", "Response Body", "response-body", includedByDefault = false)

        /** 라이브러리가 아는 모든 종류. 선언 순서가 문서 안의 기본 순서다. */
        public val BUILT_IN: List<SnippetFile> = registered.toList()

        /** 기본으로 문서에 포함하는 라이브러리 종류. */
        public fun defaults(): Set<SnippetFile> = BUILT_IN.filter { it.includedByDefault }
            .toSet()

        /**
         * 사용자 스니펫 종류. 스니펫 디렉터리의 `<fileName>.adoc` 이 이 종류가 되고, 섹션 안 소제목은 [title] 이다
         * (빈 문자열이면 소제목 없이 include). 문서에 넣을 위치는 `document { register(kind, after = …) }` 로 정한다.
         *
         * ```kotlin
         * val AUDIT = SnippetFile.custom("audit-log", title = "Audit Log")
         * document { register(AUDIT, after = SnippetFile.RESPONSE_FIELDS) }
         * ```
         *
         * @param fileName 확장자를 뺀 스니펫 파일 이름 (REST Docs `Snippet` 이 쓰는 이름). 라이브러리 종류와 이름이 같으면 안 된다
         * @param includedByDefault false 면 등록해도 `document.snippets` 에 넣어야 문서에 들어간다
         */
        public fun custom(fileName: String, title: String, includedByDefault: Boolean = true): SnippetFile {
            require(fileName.isNotBlank() && '/' !in fileName) { "invalid snippet file name '$fileName'" }
            require(builtInNamed(fileName) == null && BUILT_IN.none { it.matches("$fileName.adoc") }) {
                "'$fileName' is a built-in snippet file. Use the SnippetFile constant instead"
            }

            return SnippetFile(fileName, title, Regex.escape(fileName), includedByDefault, builtIn = false)
        }

        /** 이름(`RESPONSE_BODY`, `response-body`)으로 찾은 라이브러리 종류. 없으면 null. */
        internal fun builtInNamed(name: String): SnippetFile? =
            BUILT_IN.firstOrNull { it.name.equals(name.trim().replace('-', '_'), ignoreCase = true) }
    }
}

/**
 * 문서에 include 할 스니펫 파일 하나. [io.github.kardx.asciidoc.DocumentGenerator.sectionRowPart] 가 이것으로
 * 섹션 안의 `.소제목` + `include::` 블록을 만든다.
 *
 * @property kind 알려진 종류. 사용자 정의 스니펫이면 null 이고 파일 이름을 소제목으로 쓴다
 * @property fileName 스니펫 디렉터리 안의 파일 이름 (확장자 포함, 예: `request-fields.adoc`). include 경로 끝에 붙는다
 */
public class SnippetInclude(public val kind: SnippetFile?, public val fileName: String) {
    /** 섹션 안 소제목. 이름에 대상이 들어 있는 스니펫은 괄호로 붙인다 (`Request Part Fields (file)`, `Event Data (notification)`). */
    public val title: String
        get() {
            val base = fileName.removeSuffix(".adoc")

            return when (kind) {
                null -> base

                SnippetFile.REQUEST_PART_FIELDS -> {
                    val title = kind.title
                    val part = base.removePrefix("request-part-")
                        .removeSuffix("-fields")

                    "$title ($part)"
                }

                SnippetFile.EVENT_FIELDS -> "${kind.title} (${base.removePrefix("response-fields-event-")})"

                SnippetFile.REQUEST_SUBSECTION_FIELDS -> "${kind.title} (${base.removePrefix("request-fields-")})"

                SnippetFile.RESPONSE_SUBSECTION_FIELDS -> "${kind.title} (${base.removePrefix("response-fields-")})"

                else -> kind.title
            }
        }

    /** 같은 테스트 안의 메시지 순번 (`message-02`, `request-fields-message-02` → 2). 메시지가 아니면 0. */
    public val messageGroup: Int
        get() = MESSAGE_NUMBER.find(fileName)
            ?.groupValues
            ?.get(1)
            ?.toInt() ?: 0

    override fun equals(other: Any?): Boolean = other is SnippetInclude &&
        kind == other.kind &&
        fileName == other.fileName

    override fun hashCode(): Int = Objects.hash(kind, fileName)

    override fun toString(): String = "SnippetInclude(kind=$kind, fileName=$fileName)"

    private companion object {
        val MESSAGE_NUMBER = Regex("""message-(\d+)\.adoc$""")
    }
}
