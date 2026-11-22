package com.example.restdocs.edge

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.kardx.annotation.Description

data class FileInfo(@Description("파일 이름") val name: String, @Description("크기 (바이트)") val size: Long)

/** 요청과 응답의 모양이 다른 DTO. 요청에는 `password`, 응답에는 `createdAt` 만 있다. */
data class Account(
    @Description("아이디") val username: String,
    @Description("비밀번호 (요청에만)") @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) val password: String?,
    @Description("가입 시각 (응답에만)") @get:JsonProperty(access = JsonProperty.Access.READ_ONLY) val createdAt: String?,
)

data class Article(@Description("글 ID") val id: Long, @Description("제목") val title: String)
