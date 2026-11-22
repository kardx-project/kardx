package io.github.kardx.dsl

/**
 * 따로 느슨하게(relaxed) 검증할 수 있는 문서 항목. [DocumentationBuilder.relaxed] 에 넘긴다.
 * relaxed 인 항목은 실제 요청·응답에 문서화하지 않은 것이 있어도 실패하지 않는다. 문서화한 필수 항목이 없으면 여전히 실패한다.
 *
 * 헤더는 원래 문서화하지 않은 헤더를 허용하므로(적은 헤더가 있는지만 본다) 항목이 없다.
 *
 * ```kotlin
 * .andDocument {
 *     queryParameters<ProductSearchCondition>()
 *     responseFields<ProductPage>()
 *     relaxed(RelaxedSection.RESPONSE_FIELDS)   // 응답 필드만 느슨하게, 쿼리 파라미터는 그대로 엄격하게
 * }
 * ```
 */
public enum class RelaxedSection {
    /** 경로 변수 (`path-parameters`). */
    PATH_PARAMETERS,

    /** 쿼리 파라미터 (`query-parameters`). */
    QUERY_PARAMETERS,

    /** 폼 파라미터 (`form-parameters`). */
    FORM_PARAMETERS,

    /** 요청 본문 필드 (`request-fields`). OpenAPI 요청 스키마에 필드가 들어가지 않는다 (epages 는 relaxed 검증이 없다). */
    REQUEST_FIELDS,

    /** 응답 본문 필드 (`response-fields`). OpenAPI 응답 스키마에 필드가 들어가지 않는다 (epages 는 relaxed 검증이 없다). */
    RESPONSE_FIELDS,

    /** 멀티파트 파트 (`request-parts`). */
    REQUEST_PARTS,

    /** 멀티파트 파트 본문 필드 (`request-part-<part>-fields`). 모든 파트에 적용한다. */
    REQUEST_PART_FIELDS,

    /** 요청 쿠키 (`request-cookies`). */
    REQUEST_COOKIES,

    /** 응답 쿠키 (`response-cookies`). */
    RESPONSE_COOKIES,

    /** 하이퍼미디어 링크 (`links`). */
    LINKS,

    /** SSE 이벤트 (`sse-events`): 문서화하지 않은 이벤트 이름과 이벤트 data 의 필드. */
    EVENTS,
}
