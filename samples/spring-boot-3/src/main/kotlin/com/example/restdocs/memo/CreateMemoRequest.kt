package com.example.restdocs.memo

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 메모 작성 요청. 프로퍼티마다 단 KDoc 이 요청 필드 설명이 된다. */
data class CreateMemoRequest(
    /** 메모 제목 */
    @field:NotBlank
    @field:Size(max = 100)
    val title: String,

    /** 메모 본문. 줄바꿈을 그대로 저장한다 */
    val content: String,

    /** 메모 색. 보내지 않으면 [MemoColor.YELLOW] 다 */
    val color: MemoColor = MemoColor.YELLOW,

    /** 메모를 찾을 때 쓰는 태그 */
    val tags: List<String> = emptyList(),

    /** 알림. 알림이 필요 없으면 보내지 않는다 */
    @field:Valid
    val reminder: MemoReminder? = null,
)
