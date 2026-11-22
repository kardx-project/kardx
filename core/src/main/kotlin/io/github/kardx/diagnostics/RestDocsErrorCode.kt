package io.github.kardx.diagnostics

/**
 * 라이브러리가 내는 오류·경고의 고정 코드. 메시지는 `kardx[KDX-011]: …` 로 시작하고 문제 해결 문서의 같은 코드
 * 항목(`troubleshooting.md#kdx-011`) 주소로 끝난다. 메시지 문구가 바뀌어도 코드는 바뀌지 않으므로 코드로 검색한다.
 *
 * 백의 자리가 단계다.
 *
 * | 코드 | 단계 |
 * |---|---|
 * | `KDX-0xx` | 테스트 준비와 설정 (yml, 테스트 확장, MockMvc·WebTestClient 만들기) |
 * | `KDX-1xx` | 문서화 (`andDocument { }` 선언과 실제 요청·응답 검증) |
 * | `KDX-2xx` | OpenAPI |
 * | `KDX-3xx` | 메시지 (WebSocket·STOMP·SSE) |
 * | `KDX-4xx` | Kotest |
 * | `KDX-5xx` | 문서·파일 쓰기 (AsciiDoc, Enum 코드 문서, index) |
 * | `KDX-6xx` | 앱 시작 로그 (`kardx-boot`) |
 *
 * 단순한 인자 검사(빈 이름, 음수 개수 등 호출한 줄에서 바로 알 수 있는 것)에는 코드를 붙이지 않는다.
 * 한 번 정한 코드는 다른 뜻으로 다시 쓰지 않는다. 없앤 코드는 비워 둔다.
 *
 * @property code `KDX-` 와 세 자리 숫자 (`KDX-011`)
 * @property summary 문제 해결 문서의 제목으로 쓰는 한 줄 요약 (영어)
 */
public enum class RestDocsErrorCode(public val code: String, public val summary: String) {
    /** 알 수 없는 `kardx.*` 속성. 오타로 보고 무시한다 (경고). */
    UNKNOWN_PROPERTY("KDX-001", "Unknown kardx property"),

    /** `kardx.*` 속성 값이 잘못됐다 (모르는 값, 숫자·true/false 가 아님, 클래스 없음). */
    INVALID_PROPERTY("KDX-002", "Invalid kardx property value"),

    /** `response-wrapper.data-path` 만 있고 `response-wrapper.class` 가 없다. */
    WRAPPER_DATA_PATH_WITHOUT_CLASS("KDX-003", "response-wrapper.data-path without response-wrapper.class"),

    /** `description-annotations`·`masked-annotations` 항목이 어노테이션이 아니거나 그런 속성이 없다. */
    INVALID_ANNOTATION_SETTING("KDX-004", "Invalid annotation setting"),

    /** 테스트(yml)의 문서 언어와 Gradle 플러그인의 언어가 다르다 (경고). */
    LANGUAGE_MISMATCH("KDX-005", "Documentation language differs between tests and the Gradle plugin"),

    /** `springSecurityFilterChain` 빈이 있는데 `apply-spring-security` 를 정하지 않아 걸지 않았다 (경고). */
    SECURITY_NOT_APPLIED("KDX-006", "Spring Security filter chain is not applied"),

    /** spring-security-test 가 없어 필터 체인을 일반 필터로 걸었다 (정보). */
    SECURITY_TEST_MISSING("KDX-007", "spring-security-test not found"),

    /** 클래스패스에 Jackson 매퍼가 없다. */
    NO_JACKSON("KDX-008", "No Jackson ObjectMapper/JsonMapper"),

    /** `objectMapper` 설정이 Jackson 매퍼가 아닌 것을 돌려줬다. */
    NOT_A_JACKSON_MAPPER("KDX-009", "objectMapper is not a Jackson mapper"),

    /** KDoc/Javadoc 설명 색인(`source-docs.json`)을 읽지 못했다 (경고). */
    SOURCE_DOCS_UNREADABLE("KDX-010", "Source docs index cannot be read"),

    /**
     * 테스트 확장이 만들지 않은 REST Docs 설정(Spring Boot `@AutoConfigureRestDocs` 로 주입받은 `MockMvc` 등)으로 문서화했다.
     * 스니펫이 문서가 읽는 곳과 다른 곳에 생기면 실패, 같은 곳이면 경고한다.
     */
    FOREIGN_DOCUMENTATION_CONTEXT("KDX-011", "Client not built by kardx (@AutoConfigureRestDocs)"),

    /** REST Docs 설정이 없는 클라이언트(주입받은 그대로의 `MockMvc`, `restDocs(provider)` 없는 클라이언트)로 문서화했다. */
    REST_DOCS_NOT_CONFIGURED("KDX-012", "Client has no REST Docs configuration"),

    /** 문서화 중인 테스트가 없는데 지금 테스트의 문맥을 찾았다 (확장 미등록, 테스트 밖에서 요청). */
    NO_RUNNING_TEST("KDX-013", "No documented test is running"),

    /** `MockMvc`·`WebTestClient` 테스트 파라미터를 만들 Spring 테스트 컨텍스트가 없거나 종류가 맞지 않는다. */
    CLIENT_PARAMETER_NEEDS_SPRING("KDX-014", "Client parameter needs a Spring test context"),

    /** 이름만(또는 `FromParamTag` 로) 선언한 항목의 설명을 KDoc/Javadoc 에서 찾지 못했다. */
    MISSING_DESCRIPTION("KDX-101", "Description not found for a name-only item"),

    /** 응답 항목에 `FromParamTag` 를 썼다. `@param` 은 요청 항목만 설명한다. */
    FROM_PARAM_TAG_ON_RESPONSE("KDX-102", "FromParamTag on a response item"),

    /** 파일 본문(`requestFile`·`responseFile`)과 필드·파트를 함께 선언했다. */
    CONFLICTING_BODY("KDX-103", "File body declared together with fields or parts"),

    /** `requestFile`·`responseFile` 설명이 비었거나 두 번 선언했다. */
    INVALID_FILE_BODY("KDX-104", "Invalid requestFile/responseFile"),

    /** `errorResponse` 선언이 잘못됐다 (상태 범위, 빈 설명·contentType·예시, 중복). */
    INVALID_ERROR_RESPONSE("KDX-105", "Invalid errorResponse declaration"),

    /** `errorResponse` 예시가 JSON content type 인데 올바른 JSON 이 아니다. */
    INVALID_ERROR_EXAMPLE("KDX-106", "errorResponse example is not valid JSON"),

    /** 타입에서 추출할 필드가 없다 (단일 값, Map). 경로를 주거나 그 섹션을 빼야 한다 (경고). */
    NOTHING_EXTRACTED("KDX-107", "Nothing to document in the type"),

    /** 필드·파라미터 목록에서 고른 이름이 그 타입에 없다. */
    UNKNOWN_ITEM("KDX-108", "No such field or parameter in the type"),

    /** 문서화한 내용이 실제 요청·응답·메시지와 맞지 않는다 (`DocumentationMismatchException`). */
    DOCUMENTATION_MISMATCH("KDX-109", "Documentation does not match the actual request or response"),

    /** 응답 본문을 객체로 읽으려는데 본문이 비었다. */
    EMPTY_RESPONSE_BODY("KDX-110", "Response body is empty"),

    /** `andDocument { openApi { operationId } }` 값이 비었거나 공백을 담았다. */
    INVALID_OPERATION_ID("KDX-201", "Invalid operationId"),

    /** `openApi.operationIdResolver` 가 비었거나 공백을 담은 operationId 를 돌려줬다. */
    INVALID_RESOLVED_OPERATION_ID("KDX-202", "openApi.operationIdResolver returned an invalid operationId"),

    /** 보안 scope 가 비었거나 공백을 담았다. */
    INVALID_SCOPE("KDX-203", "Invalid security scope"),

    /** 경로 변수를 문서화하지 않아 OpenAPI 에 설명 없이 들어간다 (경고). */
    UNDOCUMENTED_PATH_VARIABLE("KDX-204", "Path variable not documented"),

    /** 테스트 밖에서 메시지를 문서화했다 (REST Docs 문맥 없음). */
    MESSAGE_OUTSIDE_TEST("KDX-301", "Message documented outside a test"),

    /** 정한 시간 안에 SSE 이벤트가 다 오지 않았다. */
    STREAM_TIMEOUT("KDX-302", "Stream events did not arrive in time"),

    /** STOMP 엔드포인트에 연결하지 못했다. */
    STOMP_CONNECT_FAILED("KDX-303", "Could not connect to the STOMP endpoint"),

    /** 정한 시간 안에 WebSocket·STOMP 메시지가 오지 않았다. */
    MESSAGE_TIMEOUT("KDX-304", "Message did not arrive in time"),

    /** Kotest 리프 테스트 밖에서 문서화했다. */
    KOTEST_NOT_IN_TEST("KDX-401", "No Kotest leaf test is running"),

    /** Kotest 판과 확장 아티팩트(`-kotest`·`-kotest5`)가 맞지 않는다. */
    KOTEST_VERSION_MISMATCH("KDX-402", "Kotest version does not match the artifact"),

    /** 한 스펙의 두 테스트가 같은 스니펫 디렉터리 이름이 된다 (경고). */
    KOTEST_DUPLICATE_SNIPPET_NAME("KDX-403", "Two Kotest tests map to the same snippet directory"),

    /** 실패한 Kotest 테스트의 스니펫을 정리하다 실패했다 (경고). */
    KOTEST_CLEANUP_FAILED("KDX-404", "Cleaning up a failed Kotest test failed"),

    /** 다른 패키지의 같은 이름 테스트 클래스가 같은 문서·스니펫 디렉터리를 쓴다 (경고). */
    DUPLICATE_DOCUMENT_NAME("KDX-501", "Two test classes write the same document"),

    /** 지난 스니펫을 지우지 못했다 (경고). */
    SNIPPET_DELETE_FAILED("KDX-502", "Could not delete old snippets"),

    /** 문서화 결과 파일을 쓰지 못했다. */
    WRITE_FAILED("KDX-503", "Could not write an output file"),

    /** 문서화 커버리지용 엔드포인트 목록을 쓰지 못했다 (경고). */
    ENDPOINT_INVENTORY_WRITE_FAILED("KDX-504", "Could not write the endpoint inventory"),

    /** `@EnumCode` 에 적은 속성이 Enum 에 없다. */
    ENUM_CODE_PROPERTY_MISSING("KDX-505", "@EnumCode property not found"),

    /** `@EnumCode` Enum 클래스를 읽지 못했다. */
    ENUM_CLASS_NOT_LOADED("KDX-506", "Enum class could not be loaded"),

    /** index 템플릿 파일이 없다. */
    INDEX_TEMPLATE_NOT_FOUND("KDX-507", "Index template not found"),

    /** 시작 로그의 언어 설정이 지원하지 않는 값이라 영어로 쓴다 (경고). */
    STARTUP_LANGUAGE_UNSUPPORTED("KDX-601", "Unsupported startup log language"),

    /** 앱 리소스에 문서가 없어 시작 로그에 주소 대신 안내를 쓴다. */
    STARTUP_DOCS_MISSING("KDX-602", "API docs not found in the app resources"),
    ;

    /** 문제 해결 문서의 앵커 (`kdx-011`). */
    public val anchor: String
        get() = code.lowercase()

    /** 이 코드를 설명하는 문제 해결 문서 주소. */
    public val docsUrl: String
        get() = "$TROUBLESHOOTING_URL#$anchor"

    /**
     * [detail] 앞에 `kardx[코드]: ` 를 붙이고 끝에 [docsUrl] 을 붙인 메시지.
     * 여러 줄 메시지는 빈 줄 뒤에 주소를 둔다.
     *
     * ```kotlin
     * RestDocsErrorCode.INVALID_SCOPE.message("invalid oauth2 scope in [a b].")
     * // kardx[KDX-203]: invalid oauth2 scope in [a b]. See https://…/troubleshooting.md#kdx-203
     * ```
     */
    public fun message(detail: String): String {
        val text = detail.trimEnd()

        if ('\n' in text) {
            return listOf("$PREFIX[$code]: $text", "See $docsUrl").joinToString("\n\n")
        }

        val separator = if (text.endsWith('.')) " " else ". "

        return "$PREFIX[$code]: $text${separator}See $docsUrl"
    }

    public companion object {
        /** 모든 메시지 앞에 붙는 라이브러리 이름 */
        public const val PREFIX: String = "kardx"

        /** 문제 해결 문서. 코드마다 `<a id="kdx-011"></a>` 앵커가 있다 */
        public const val TROUBLESHOOTING_URL: String =
            "https://github.com/kardx-project/kardx/blob/main/docs/troubleshooting.md"

        /** [code] 의 항목. 없으면 null. */
        public fun of(code: String): RestDocsErrorCode? = entries.firstOrNull {
            it.code.equals(code, ignoreCase = true)
        }
    }
}
