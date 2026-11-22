package com.example.restdocs.dto

import io.github.kardx.annotation.EnumCode

@EnumCode("사용자 상태", descriptionProperty = "description")
enum class UserStatus(val description: String) {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    BANNED("차단"),
}
