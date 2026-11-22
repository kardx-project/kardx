package com.example.restdocs.auth

import io.github.kardx.annotation.Description
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class MeResponse(
    @Description("로그인 아이디") val username: String,
    @Description("세션 ID 앞 4자리") val sessionPrefix: String,
)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    // rememberDays 는 1~30 만 받는다. 예제라 세션 수명에는 반영하지 않는다.
    // 구현 메모라 KDoc 이 아닌 일반 주석이다 (핸들러 KDoc 은 OpenAPI summary·description 이 된다)
    @PostMapping("/login", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun login(
        @RequestParam username: String,
        @RequestParam password: String,
        @RequestParam(required = false) rememberDays: Int?,
    ): ResponseEntity<Unit> {
        if (password.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        if (rememberDays != null && rememberDays !in REMEMBER_DAYS) {
            return ResponseEntity.badRequest().build()
        }

        val session =
            ResponseCookie.from("SESSION", "s3cr3t-$username")
                .path("/")
                .httpOnly(true)
                .build()

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, session.toString())
            .build()
    }

    @GetMapping("/me")
    fun me(@CookieValue("SESSION") session: String): MeResponse = MeResponse("hong", session.take(4))

    private companion object {
        val REMEMBER_DAYS = 1..30
    }
}
