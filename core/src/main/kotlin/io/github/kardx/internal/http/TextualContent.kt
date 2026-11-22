package io.github.kardx.internal.http

import java.util.Locale

/**
 * 본문을 글자로 보여 줄 수 있는 Content-Type 인지 판단한다. 문서 스니펫의 바이너리 본문 자리 표시([BinaryBodyPlaceholder])와
 * WireMock 매핑의 `body`/`base64Body` 선택이 같은 기준을 쓴다.
 *
 * 글자로 보는 것: Content-Type 이 없을 때(알 수 없으면 그대로 둔다), `text/` 전체(`text/csv` 포함), `multipart/`(파트는 따로 본다),
 * 하위 타입에 `json`·`xml` 이 들어간 것(`application/hal+json`, `application/x-ndjson`, `image/svg+xml` …),
 * 그 밖에 [TEXT_SUBTYPES] 의 하위 타입.
 */
internal object TextualContent {
    private val TEXT_SUBTYPES = setOf(
        "x-www-form-urlencoded",
        "javascript",
        "ecmascript",
        "yaml",
        "x-yaml",
        "graphql",
        "sql",
        "csv",
    )

    /** @param contentType `Content-Type` 헤더 값 (`application/pdf`, `text/csv;charset=UTF-8` …). null·빈 값이면 true */
    fun isTextual(contentType: String?): Boolean {
        val mediaType = contentType.orEmpty()
            .substringBefore(';')
            .trim()
            .lowercase(Locale.ROOT)

        if (mediaType.isEmpty()) {
            return true
        }

        val type = mediaType.substringBefore('/')
        val subtype = mediaType.substringAfter('/', "")

        return type == "text" ||
            type == "multipart" ||
            "json" in subtype ||
            "xml" in subtype ||
            subtype in TEXT_SUBTYPES
    }
}
