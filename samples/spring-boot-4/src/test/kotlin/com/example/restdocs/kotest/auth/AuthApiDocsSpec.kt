package com.example.restdocs.kotest.auth

import com.example.restdocs.auth.MeResponse
import com.example.restdocs.kotest.support.MirrorDocsConfig
import io.github.kardx.dsl.cookieMeans
import io.github.kardx.dsl.formMeans
import io.github.kardx.dsl.headerMeans
import io.github.kardx.extractor.responseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import jakarta.servlet.http.Cookie
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.context.WebApplicationContext
import java.nio.file.Files
import java.nio.file.Paths

/**
 * [com.example.restdocs.auth.AuthApiDocsTest] 를 Kotest 로 옮긴 스펙. 폼 파라미터와 쿠키, 세션 값 마스킹을 확인한다.
 * 스니펫 디렉터리는 테스트 이름에서 나오므로(`로그인`, `내-정보`) JUnit 의 메서드 이름 디렉터리와 다르다.
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("인증 API")
class AuthApiDocsSpec(context: WebApplicationContext) :
    FunSpec({
        val restDocs = extension(MirrorDocsConfig.restDocsExtension())
        val mockMvc = restDocsMockMvc(context, restDocs.provider)

        beforeEach { MirrorDocsConfig.apply() }

        test("로그인") {
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

            val response = snippet("로그인", "http-response.adoc")

            check("SESSION=****; Path=/; HttpOnly" in response) { response }
            check("s3cr3t" !in response) { response }
        }

        test("내 정보") {
            mockMvc.perform(
                get("/api/v1/auth/me").cookie(Cookie("SESSION", "s3cr3t-hong")),
            ).andStatus(HttpStatus.OK)
                .andDocument {
                    description = "세션 쿠키로 로그인한 사용자를 조회한다."
                    requestCookies("SESSION" cookieMeans "로그인 시 받은 세션 ID")
                    responseFields<MeResponse>()
                }

            val request = snippet("내-정보", "curl-request.adoc") + snippet("내-정보", "http-request.adoc")

            check("s3cr3t" !in request) { request }
            check("SESSION=****" in request) { request }
        }
    })

/** 이 스펙의 [test] 스니펫 디렉터리에서 [file] 을 읽는다. */
private fun snippet(test: String, file: String): String =
    Files.readString(Paths.get("build/generated-snippets/auth-api-docs-spec", test, file))
