package com.example.restdocs.common

import io.github.kardx.annotation.Description

/** 모든 API 가 쓰는 공통 응답 래퍼. */
data class RestResponse<T>(
    @Description("응답 코드") val code: Int,
    @Description("응답 메시지") val message: String,
    @Description("응답 데이터") val data: T?,
) {
    companion object {
        fun <T> ok(data: T) = RestResponse(200, "OK", data)
    }
}

/** 페이지 응답. */
data class PageResponse<T>(
    @Description("전체 개수") val total: Long,
    @Description("페이지 번호 (0부터)") val page: Int,
    @Description("페이지 크기") val size: Int,
    @Description("목록") val content: List<T>,
)
