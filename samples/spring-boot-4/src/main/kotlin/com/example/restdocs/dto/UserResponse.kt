package com.example.restdocs.dto

import io.github.kardx.annotation.Description
import io.github.kardx.annotation.Masked

// 프로퍼티 KDoc 이 설명이 된다 (Gradle 플러그인의 apiDocsSourceDocs). @Description 이 있으면 그것이 먼저다
data class UserResponse(
    /** 사용자 ID */
    val id: Long,

    /** 사용자 닉네임 */
    val nickname: String,

    // 앱이 마스킹해서 내보낸다는 사실을 문서에 표시한다. 가리는 것은 앱이 한다 (controller/EmailMasking.kt)
    @Description("사용자 이메일")
    @Masked("아이디의 첫 글자와 끝 글자만 남기고 *")
    val email: String,

    /** 사용자 상태 */
    val status: UserStatus,
)
