package com.example.restdocs.webflux.common

import io.github.kardx.annotation.Description

/** 모든 오류 응답의 형식. 오류 종류와 상관없이 이 클래스 하나를 쓴다. */
data class ErrorResponse(
    @Description("오류 코드 (HTTP 상태와 같다)") val code: Int,
    @Description("오류 메시지") val message: String,
    @Description("잘못된 입력 필드. 입력값 검증 오류(400)일 때만 있다") val errors: List<FieldErrorDetail>? = null,
)

/** 검증에 실패한 입력 필드 하나. */
data class FieldErrorDetail(@Description("잘못된 필드") val field: String, @Description("이유") val reason: String)
