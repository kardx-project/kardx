package io.github.kardx.dsl

/**
 * 테스트가 실제로 받지 않고 선언만 한 오류 응답 하나 ([DocumentationBuilder.errorResponse]).
 * `error-responses` 스니펫과 `openapi-extras.json` 의 `errorResponses` 가 된다.
 *
 * @property status 400..599 상태 코드
 * @property contentType 응답 Content-Type. 기본값 `application/json`
 * @property fields 본문 필드. 없으면 빈 목록
 * @property example 본문 예시. JSON 계열이면 문서화할 때 들여쓰기한 값으로 바꾼다. 없으면 null
 * @property fieldsFrom 본문 필드를 추출할 예시 객체 (예시 객체 단축형 `errorResponse(status, description, example)`).
 *   추출은 문서화할 때 `internal.documentation` 이 한다 (이 패키지가 추출 코드에 기대지 않게). 없으면 null
 */
internal data class DeclaredErrorResponse(
    val status: Int,
    val description: String,
    val contentType: String,
    val fields: List<RestDocsField>,
    val example: String?,
    val fieldsFrom: Any? = null,
) {
    /** `application/json` 이나 `application/problem+json` 처럼 본문이 JSON 인 Content-Type 인지. */
    val isJson: Boolean
        get() {
            val mediaType = contentType.substringBefore(';')
                .trim()
                .lowercase()

            return mediaType == JSON_MEDIA_TYPE || mediaType.endsWith(JSON_SUFFIX)
        }

    private companion object {
        const val JSON_MEDIA_TYPE = "application/json"
        const val JSON_SUFFIX = "+json"
    }
}
