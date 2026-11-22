package io.github.kardx.dsl

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.jackson.serializeJson

/**
 * [DocumentationBuilder.errorResponse] 블록. 테스트가 실제로 받지 않는 오류 응답의 본문 필드·예시·Content-Type 을 적는다.
 * 여기 적은 내용은 선언일 뿐 테스트가 검증하지 않는다. 예시와 필드를 함께 적으면 예시가 필드와 맞는지만 확인한다.
 *
 * 타입에서 필드를 추출하는 단축형 `fields<T>()` 는 `io.github.kardx.extractor` 패키지의 확장 함수다.
 *
 * ```kotlin
 * .andDocument {
 *     errorResponse(HttpStatus.CONFLICT, "이미 등록된 상품 코드") {
 *         fields("code" type STRING means "오류 코드", "message" type STRING means "오류 메시지")
 *         example("""{"code":"DUPLICATE","message":"이미 등록된 상품 코드입니다"}""")
 *         contentType = "application/problem+json"
 *     }
 * }
 * ```
 */
@RestDocsDsl
public class ErrorResponseBuilder internal constructor() {
    /**
     * 응답 Content-Type. 기본값 `application/json`. `application/problem+json` 처럼 `+json` 으로 끝나는 것도 JSON 으로 보아
     * [example] 을 JSON 으로 읽고 들여쓰기한다.
     */
    public var contentType: String = DEFAULT_CONTENT_TYPE

    internal val declaredFields = mutableListOf<RestDocsField>()

    internal var example: String? = null
        private set

    /** 본문 필드를 추출할 예시 객체. [DeclaredErrorResponse.fieldsFrom] */
    internal var fieldsFrom: Any? = null

    /**
     * 본문 필드. `error-responses` 스니펫의 필드 표와 OpenAPI 응답 스키마가 된다. 다시 부르면 이어 붙인다.
     * 타입에서 추출할 때는 단축형 `fields<ErrorResponse>()` ([io.github.kardx.extractor.fields]).
     *
     * ```kotlin
     * errorResponse(400, "잘못된 요청") {
     *     fields(listOf("code" type STRING means "오류 코드"))
     * }
     * ```
     */
    public fun fields(fields: List<RestDocsField>) {
        declaredFields += fields
    }

    /** [fields] 의 가변 인자 버전. `fields("code" type STRING means "오류 코드")` */
    public fun fields(vararg fields: RestDocsField): Unit = fields(fields.toList())

    /**
     * 본문 예시. [contentType] 이 JSON 계열이면 JSON 으로 읽을 수 있어야 하고(아니면 `IllegalArgumentException`), 들여쓰기한
     * 값이 `error-responses` 스니펫과 OpenAPI 응답 `example` 에 들어간다. 필드도 적었다면 예시에 문서화하지 않은 필드가 있거나
     * 필수 필드가 없을 때 문서화가 [DocumentationMismatchException] 으로 실패한다. 다시 부르면 덮어쓴다.
     *
     * ```kotlin
     * errorResponse(404, "상품 없음") {
     *     example("""{"code":"NOT_FOUND","message":"상품이 없습니다"}""")
     * }
     * ```
     */
    public fun example(json: String) {
        example = json
    }

    /**
     * 본문 예시를 객체로 적는다. 응답 본문과 같은 ObjectMapper (`extraction.objectMapper`, 없으면 테스트의 Spring
     * 컨텍스트, 그것도 없으면 기본 매퍼)로 JSON 을 만들므로 `@JsonProperty`·이름 규칙·null 처리 설정이 그대로 반영된다.
     * 공통 응답 래퍼(`extraction.responseWrapper`)로 감싸지 않는다. 래퍼 모양이 필요하면 래퍼 객체를 직접 넘긴다.
     * 문자열(`CharSequence`)은 이미 JSON 으로 보고 [example] (`String`) 과 같이 그대로 쓴다 (다시 JSON 문자열로 감싸지 않는다).
     * 그 밖의 규칙(필드와 맞는지 검사, 다시 부르면 덮어쓰기)은 [example] (`String`) 과 같다.
     *
     * ```kotlin
     * errorResponse(409, "이미 등록된 상품 코드") {
     *     fields<ErrorResponse>()
     *     example(ErrorResponse(code = "DUPLICATE", message = "이미 등록된 상품 코드입니다"))
     * }
     * ```
     */
    public fun example(value: Any) {
        example = if (value is CharSequence) {
            value.toString()
        } else {
            serializeJson(RestDocsConfiguration.objectMapper(), value)
        }
    }

    /** 블록에서 적은 값을 검사해 [DeclaredErrorResponse] 로 만든다. */
    internal fun build(status: Int, description: String): DeclaredErrorResponse {
        require(contentType.isNotBlank()) {
            RestDocsErrorCode.INVALID_ERROR_RESPONSE.message("errorResponse($status) has a blank contentType.")
        }
        require(example?.isNotBlank() ?: true) {
            RestDocsErrorCode.INVALID_ERROR_RESPONSE.message(
                "errorResponse($status) has a blank example. Remove example(...) or give a body.",
            )
        }

        return DeclaredErrorResponse(
            status,
            description,
            contentType.trim(),
            declaredFields.toList(),
            example,
            fieldsFrom,
        )
    }

    private companion object {
        const val DEFAULT_CONTENT_TYPE = "application/json"
    }
}
