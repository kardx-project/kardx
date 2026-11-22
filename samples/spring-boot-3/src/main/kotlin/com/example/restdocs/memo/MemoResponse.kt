package com.example.restdocs.memo

import java.time.LocalDateTime

/**
 * 메모. 프로퍼티 설명은 클래스 KDoc 의 `@property` 에서 온다.
 *
 * @property id 메모 ID
 * @property title 메모 제목
 * @property content 메모 본문
 * @property color 메모 색
 * @property tags 메모 태그
 * @property reminder 메모 알림. 알림을 정하지 않은 메모는 `null` 이다
 * @property createdAt 메모를 작성한 시각
 */
data class MemoResponse(
    val id: Long,
    val title: String,
    val content: String,
    val color: MemoColor,
    val tags: List<String>,
    val reminder: MemoReminder?,
    val createdAt: LocalDateTime,
)
