package com.example.restdocs.memo

/** 메모 목록 조회 조건. 쿼리 파라미터로 받고, 테스트는 `queryParameters<MemoSearchCondition>()` 로 문서화한다. */
data class MemoSearchCondition(
    /** 이 색([MemoColor])의 메모만 돌려준다. 보내지 않으면 모든 색 */
    val color: MemoColor? = null,

    /** 페이지 번호 (0부터) */
    val page: Int = 0,

    /** 페이지 크기 */
    val size: Int = 20,
)
