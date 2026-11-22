package com.example.restdocs.memo

/**
 * 메모 목록의 한 페이지.
 *
 * @property total 조건에 맞는 메모의 전체 개수
 * @property page 페이지 번호 (0부터)
 * @property size 페이지 크기
 * @property content 이 페이지의 메모
 */
data class MemoPage(val total: Int, val page: Int, val size: Int, val content: List<MemoResponse>)
