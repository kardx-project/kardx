package io.github.kardx.extractor

import io.github.kardx.dsl.ErrorResponseBuilder

// DocumentationBuilderExtensions.kt 와 같은 이유로 멤버가 아니라 확장 함수다 (dsl → extractor 패키지 순환을 피한다).

/**
 * 선언한 오류 응답의 본문 필드를 타입에서 추출해 등록한다. `fields(responseFieldsOf<T>(path))` 와 같다.
 * `error-responses` 스니펫의 필드 표와 OpenAPI 응답 스키마가 된다. 선언일 뿐 실제 응답으로 검증하지 않는다.
 *
 * ```kotlin
 * .andDocument {
 *     errorResponse(HttpStatus.CONFLICT, "이미 등록된 상품 코드") {
 *         fields<ErrorResponse>()
 *     }
 * }
 * ```
 *
 * @param path 추출한 필드 앞에 붙일 경로. 기본값은 본문 루트
 */
public inline fun <reified T> ErrorResponseBuilder.fields(path: String = ""): Unit = fields(responseFieldsOf<T>(path))
