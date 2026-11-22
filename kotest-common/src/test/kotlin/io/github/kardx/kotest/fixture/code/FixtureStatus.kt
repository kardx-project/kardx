package io.github.kardx.kotest.fixture.code

import io.github.kardx.annotation.EnumCode

@EnumCode("고정 상태")
enum class FixtureStatus(val description: String) {
    ACTIVE("사용 중"),
    STOPPED("멈춤"),
}
