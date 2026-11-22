package com.example.restdocs.memo

import java.time.LocalDateTime

/** 메모 알림. 메모 안의 중첩 객체 예제로, 프로퍼티마다 KDoc 을 단다. */
data class MemoReminder(
    /** 알림을 보낼 시각 */
    val remindAt: LocalDateTime,

    /** 매일 같은 시각에 다시 알릴지 여부 */
    val daily: Boolean = false,
)
