package com.example.restdocs.controller

import com.example.restdocs.dto.CreateUserRequest
import com.example.restdocs.dto.UserResponse
import com.example.restdocs.dto.UserStatus
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    /**
     * 사용자 조회.
     *
     * ID 로 사용자를 조회한다. 응답의 `ETag` 로 캐시를 검증할 수 있다.
     */
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): ResponseEntity<UserResponse> = ResponseEntity.ok()
        .eTag("\"v1\"")
        .body(UserResponse(id, "홍길동", "hong@example.com".maskEmail(), UserStatus.ACTIVE))

    @PostMapping
    fun createUser(@RequestBody @Valid request: CreateUserRequest): UserResponse =
        UserResponse(1L, request.nickname, request.email.maskEmail(), UserStatus.ACTIVE)

    /**
     * 사용자 목록 조회.
     *
     * 가입한 사용자를 [page]·[size] 로 나눠 돌려준다.
     */
    @GetMapping
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): List<UserResponse> = USERS.drop(page * size).take(size)

    /**
     * 닉네임으로 사용자 검색.
     *
     * 닉네임이 같은 사용자를 모두 돌려준다.
     */
    // Kotest 문서화 예제(UserSearchDocsSpec)가 문서화한다
    @GetMapping("/search")
    fun searchUsers(@RequestParam nickname: String): List<UserResponse> = USERS.filter { it.nickname == nickname }

    private companion object {
        val USERS = listOf(
            UserResponse(1L, "홍길동", "hong@example.com".maskEmail(), UserStatus.ACTIVE),
            UserResponse(2L, "김철수", "kim@example.com".maskEmail(), UserStatus.INACTIVE),
        )
    }
}
