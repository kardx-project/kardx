package io.github.kardx.internal.documentation

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.dsl.DeclaredErrorResponse
import io.github.kardx.internal.introspection.ExampleFields
import io.github.kardx.internal.jackson.prettySingleJsonOrNull

/**
 * 예시 객체 단축형([DeclaredErrorResponse.fieldsFrom])이면 그 런타임 클래스에서 추출한 본문 필드를 붙인다
 * ([ExampleFields], `responseFieldsOf` 와 같은 규칙). 아니면 그대로 둔다.
 */
internal fun DeclaredErrorResponse.withExampleFields(): DeclaredErrorResponse {
    val source = fieldsFrom ?: return this

    return copy(fields = fields + ExampleFields.of(source), fieldsFrom = null)
}

/**
 * JSON 계열 Content-Type 의 예시를 들여쓰기한 값으로 바꾼다. 예시가 없거나 JSON 이 아니면 그대로 둔다.
 *
 * @throws IllegalArgumentException JSON 계열인데 예시가 JSON 문서 하나가 아니면 (읽을 수 없거나 뒤에 다른 토큰이 있으면)
 */
internal fun DeclaredErrorResponse.withPrettyExample(): DeclaredErrorResponse {
    val source = example

    if (source == null || !isJson) {
        return this
    }

    val pretty = prettySingleJsonOrNull(source, RestDocsConfiguration.objectMapper())

    requireNotNull(pretty) {
        RestDocsErrorCode.INVALID_ERROR_EXAMPLE.message(
            "the example of errorResponse($status) is not valid JSON for content type $contentType: $source",
        )
    }

    return copy(example = pretty)
}
