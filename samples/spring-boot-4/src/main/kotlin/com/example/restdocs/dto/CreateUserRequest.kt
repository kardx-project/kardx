package com.example.restdocs.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * 사용자 생성 요청. 클래스 KDoc 의 `@property` 가 프로퍼티 설명이 된다.
 *
 * @property nickname 사용자 닉네임
 * @property email 사용자 이메일
 */
data class CreateUserRequest(
    @field:NotBlank
    val nickname: String,

    @field:NotBlank
    @field:Email
    val email: String,
)
