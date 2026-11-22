package io.github.kardx.internal.i18n

import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration

/**
 * 라이브러리가 생성 문서·스펙에 넣는 문구 ([io.github.kardx.config.RestDocsSettings.language] 를 따른다).
 * 뷰어 화면 문구는 [ViewerText]. 로그·경고·예외 메시지는 번역하지 않고 영어로 둔다.
 *
 * `{0}`, `{1}` … 은 [text] 의 인자 자리다. 한국어 문구는 언어 설정을 넣기 전의 출력과 글자 하나까지 같아야 한다.
 */
internal enum class DocsText(private val english: String, private val korean: String) {
    // ---- 문서 목록 (DocumentationIndex) ----
    INDEX_TITLE("API Documentation", "API 문서"),
    INDEX_HEADING("API list", "API 목록"),
    INDEX_OPEN_DOCUMENT("Open document", "문서 열기"),
    INDEX_LINK_TEXT("Back to index", "메인으로 돌아가기"),

    // ---- 스키마 설명 접두사 (뒤에 ": " 를 붙인다) ----
    CONSTRAINTS("Constraints", "제약"),
    ITEMS("Items", "원소"),

    // ---- sealed 타입 판별자 ----
    TYPE_DISCRIMINATOR("Type discriminator", "타입 구분자"),
    SUBTYPE_VALUE("Value when the type is {0}", "{0} 타입일 때의 값"),

    // ---- Spring Data Page·Slice·Sort·Pageable ----
    PAGE_CONTENT("Items on this page", "이 페이지의 항목"),
    PAGE_PAGEABLE("Requested page", "요청한 페이지 정보"),
    PAGE_TOTAL_ELEMENTS("Total number of items", "전체 항목 수"),
    PAGE_TOTAL_PAGES("Total number of pages", "전체 페이지 수"),
    PAGE_FIRST("Whether this is the first page", "첫 페이지인지"),
    PAGE_LAST("Whether this is the last page", "마지막 페이지인지"),
    PAGE_SIZE("Page size", "페이지 크기"),
    PAGE_NUMBER("Page number (zero-based)", "페이지 번호 (0부터)"),
    PAGE_SORT("Sort state", "정렬 상태"),
    PAGE_NUMBER_OF_ELEMENTS("Number of items on this page", "이 페이지의 항목 수"),
    PAGE_EMPTY("Whether this page is empty", "이 페이지가 비었는지"),
    PAGE_METADATA("Page information", "페이지 정보"),
    SORT_EMPTY("Whether no sort order is set", "정렬 기준이 없는지"),
    SORT_SORTED("Whether the result is sorted", "정렬했는지"),
    SORT_UNSORTED("Whether the result is unsorted", "정렬하지 않았는지"),
    PAGEABLE_OFFSET("Number of skipped items (pageNumber × pageSize)", "건너뛴 항목 수 (pageNumber × pageSize)"),
    PAGEABLE_PAGED("Whether the request is paged", "페이지를 나눴는지"),
    PAGEABLE_UNPAGED("Whether the request is unpaged", "페이지를 나누지 않았는지"),
    SORT_PARAMETER(
        "Sort order in the format `property,asc|desc`. Can be repeated (e.g. `sort=name,desc`)",
        "정렬. `속성,asc|desc` 형식이고 여러 번 보낼 수 있다 (예: `sort=name,desc`)",
    ),

    // ---- Spring HATEOAS (HAL) ----
    HAL_LINKS(
        "Links to related resources, keyed by relation (`self` …). Each link has `href`",
        "관계 이름(`self` …)별 관련 리소스 링크. 링크마다 `href` 가 있다",
    ),
    HAL_EMBEDDED("Embedded resources, keyed by relation", "관계 이름별로 포함한 리소스"),
    HAL_EMBEDDED_ITEMS("Resources in this collection", "이 컬렉션의 리소스"),

    // ---- Spring ProblemDetail (RFC 9457) ----
    PROBLEM_TYPE(
        "URI identifying the problem type. May be omitted when it is the default (about:blank)",
        "문제 유형을 가리키는 URI. 기본값(about:blank)이면 빠질 수 있다",
    ),
    PROBLEM_TITLE("Short summary of the problem type", "문제 유형의 짧은 요약"),
    PROBLEM_STATUS("HTTP status code", "HTTP 상태 코드"),
    PROBLEM_DETAIL("Explanation specific to this occurrence", "이번 발생에 대한 설명"),
    PROBLEM_INSTANCE("URI of the resource where the problem occurred", "문제가 발생한 리소스 URI"),

    // ---- 선언한 오류 응답 (error-responses 스니펫) ----
    ERROR_STATUS("Status", "상태"),
    ERROR_DESCRIPTION("Description", "설명"),
    ERROR_FIELD_PATH("Path", "경로"),
    ERROR_FIELD_TYPE("Type", "타입"),
    ERROR_RESPONSE_FIELDS("{0} response fields", "{0} 응답 필드"),
    ERROR_RESPONSE_EXAMPLE("{0} response example", "{0} 응답 예시"),

    // ---- 파일 본문 (request-file·response-file 스니펫) ----
    FILE_DESCRIPTION("Description", "설명"),
    FILE_MEDIA_TYPE("Media type", "미디어 타입"),
    FILE_NAME("File name (example)", "파일 이름 (예시)"),
    FILE_SIZE("Size (example)", "크기 (예시)"),
    FILE_BYTES("{0} bytes", "{0}바이트"),
    FILE_ONE_BYTE("1 byte", "1바이트"),

    // ---- AsyncAPI ----
    CONNECT_HEADERS("Headers sent when connecting ({0}): {1}", "연결할 때 헤더를 보낸다 ({0}): {1}"),
    SUBSCRIBE_HEADERS("Subscription request headers: {0}", "구독 요청 헤더: {0}"),
    OPTIONAL("optional", "선택"),
    OPTIONAL_EVENT("Not always sent on the stream.", "스트림에 항상 오지는 않는다."),
    ;

    /** [language] (기본값: 지금 설정의 언어) 의 문구. `{0}`, `{1}` … 자리에 [arguments] 를 넣는다. */
    fun text(vararg arguments: Any, language: DocsLanguage = RestDocsConfiguration.current.language): String {
        val template = when (language) {
            DocsLanguage.EN -> english
            DocsLanguage.KO -> korean
        }

        if (arguments.isEmpty()) {
            return template
        }

        // 한 번에 바꿔서, 넣은 값 안의 `{1}` 같은 글자는 다시 바꾸지 않는다
        return ARGUMENT.replace(template) { match ->
            arguments.getOrNull(match.groupValues[1].toInt())
                ?.toString()
                ?: match.value
        }
    }

    private companion object {
        val ARGUMENT = Regex("""\{(\d+)}""")
    }
}
