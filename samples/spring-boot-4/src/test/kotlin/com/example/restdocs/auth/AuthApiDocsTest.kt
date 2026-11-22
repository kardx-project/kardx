package com.example.restdocs.auth

import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.dsl.cookieMeans
import io.github.kardx.dsl.formMeans
import io.github.kardx.dsl.headerMeans
import io.github.kardx.extractor.responseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 폼 파라미터와 쿠키 예제. 세션 값은 스니펫에 `****` 로 남는다.
 */
@DisplayName("인증 API")
class AuthApiDocsTest : RestDocsTestSupport() {

    @Test
    @DisplayName("로그인")
    fun login() {
        // 문서 예시용 가짜 계정. 세션 값(s3cr3t-...)이 스니펫에서 가려지는지 아래에서 확인한다
        mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "hong")
                .param("password", "p@ssw0rd")
                .param("rememberDays", "7"),
        ).andStatus(HttpStatus.NO_CONTENT)
            .andDocument {
                description = "폼으로 로그인하고 세션 쿠키를 받는다."
                formParameters(
                    "username" formMeans "아이디",
                    "password" formMeans "비밀번호",
                    "rememberDays" formMeans "로그인 유지 일수" isOptional true constraint "1~30",
                )

                responseHeaders("Set-Cookie" headerMeans "세션 쿠키")
                responseCookies("SESSION" cookieMeans "세션 ID (HttpOnly)")
            }

        val response = snippet("login", "http-response.adoc")
        check("SESSION=****; Path=/; HttpOnly" in response) { response }
        check("s3cr3t" !in response) { response }
    }

    @Test
    @DisplayName("내 정보")
    fun me() {
        mockMvc.perform(
            get("/api/v1/auth/me").cookie(Cookie("SESSION", "s3cr3t-hong")),
        ).andStatus(HttpStatus.OK)
            .andDocument {
                description = "세션 쿠키로 로그인한 사용자를 조회한다."
                requestCookies("SESSION" cookieMeans "로그인 시 받은 세션 ID")
                responseFields<MeResponse>()
            }

        val request = snippet("me", "curl-request.adoc") + snippet("me", "http-request.adoc")
        check("s3cr3t" !in request) { request }
        check("SESSION=****" in request) { request }
    }

    private fun snippet(method: String, file: String): String =
        Files.readString(Paths.get("build/generated-snippets/auth-api-docs-test", method, file))
}
