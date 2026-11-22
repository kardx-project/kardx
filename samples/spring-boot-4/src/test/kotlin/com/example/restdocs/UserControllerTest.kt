package com.example.restdocs

import com.example.restdocs.common.ErrorResponse
import com.example.restdocs.common.FieldErrorDetail
import com.example.restdocs.dto.CreateUserRequest
import com.example.restdocs.dto.UserResponse
import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.annotation.AsciiDescription
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.pathMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.fields
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@DisplayName("사용자 API")
@AsciiDescription("회원 관리")
class UserControllerTest : RestDocsTestSupport() {

    @Test
    @DisplayName("사용자 조회")
    fun getUser() {
        mockMvc.perform(get("/api/v1/users/{id}", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "ID로 사용자를 조회한다."
                pathParameters("id" pathMeans "사용자 ID")
                responseHeaders("ETag" headerMeans "리소스 버전")
                responseFields<UserResponse>()

                // 테스트가 만들지 않는 404 를 예시 객체 하나로 선언한다. 필드는 객체의 클래스에서, 예시는 객체를 직렬화한 JSON
                errorResponse(404, "사용자가 없음", ErrorResponse(404, "사용자를 찾을 수 없습니다"))
                errorResponse(401, "토큰이 없거나 만료됨", ErrorResponse(401, "로그인이 필요합니다"))
                // 상태는 HttpStatus 로도 적는다
                errorResponse(
                    HttpStatus.FORBIDDEN,
                    "다른 사용자의 정보는 조회할 수 없음",
                    ErrorResponse(403, "본인 정보만 조회할 수 있습니다"),
                )
            }
    }

    @Test
    @DisplayName("사용자 생성")
    fun createUser() {
        mockMvc.perform(
            post("/api/v1/users").jsonContent(CreateUserRequest("홍길동", "hong@example.com")),
        ).andStatus(HttpStatus.OK)
            .andExpect(jsonPath("$.email").value("h**g@example.com")) // @Masked 규칙대로 가린 값
            .andDocument {
                description = "새 사용자를 생성한다."
                requestFields<CreateUserRequest>()
                responseFields<UserResponse>()

                // 블록 형식: 필드는 타입에서, 예시는 객체로. 예시가 필드와 맞지 않으면 문서화가 실패한다
                errorResponse(HttpStatus.BAD_REQUEST, "입력값 검증 실패. 잘못된 필드는 `errors` 에 있다") {
                    fields<ErrorResponse>()
                    example(
                        ErrorResponse(
                            400,
                            "입력값이 올바르지 않습니다",
                            listOf(FieldErrorDetail("email", "must be a well-formed email address")),
                        ),
                    )
                }

                errorResponse(409, "이미 가입한 이메일") {
                    fields<ErrorResponse>()
                    example("""{"code":409,"message":"이미 가입한 이메일입니다"}""")
                }
            }
    }

    @Test
    @DisplayName("사용자 목록 조회")
    fun listUsers() {
        mockMvc.perform(
            get("/api/v1/users").param("page", "0")
                .param("size", "10"),
        ).andStatus(HttpStatus.OK)
            .andDocument {
                queryParameters(
                    "page" queryMeans "페이지 번호" isOptional true withDefaultValue "0",
                    "size" queryMeans "페이지 크기" isOptional true withDefaultValue "10" example "20",
                )

                responseFields<List<UserResponse>>()

                // 본문 없이 상태와 설명만
                errorResponse(HttpStatus.BAD_REQUEST, "`page`·`size` 가 숫자가 아님")
            }
    }

    @Test
    @DisplayName("잘못된 요청은 400")
    fun createUserWithBlankNickname() {
        mockMvc.perform(
            post("/api/v1/users").jsonContent(CreateUserRequest("", "hong@example.com")),
        ).andStatus(HttpStatus.BAD_REQUEST)
    }
}
