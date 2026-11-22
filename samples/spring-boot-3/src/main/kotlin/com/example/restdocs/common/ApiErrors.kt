package com.example.restdocs.common

import io.github.kardx.annotation.Description
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 요청한 리소스가 없을 때. 404 로 응답한다. */
class NotFoundException(message: String) : RuntimeException(message)

/** 모든 오류 응답의 형식. 오류 종류와 상관없이 이 클래스 하나를 쓴다. */
data class ErrorResponse(
    @Description("오류 코드 (HTTP 상태와 같다)") val code: Int,
    @Description("오류 메시지") val message: String,
    @Description("잘못된 입력 필드. 입력값 검증 오류(400)일 때만 있다") val errors: List<FieldErrorDetail>? = null,
)

/** 검증에 실패한 입력 필드 하나. */
data class FieldErrorDetail(@Description("잘못된 필드") val field: String, @Description("이유") val reason: String)

/** 오류를 모두 [ErrorResponse] 로 돌려준다. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse(HttpStatus.NOT_FOUND.value(), e.message.orEmpty()))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalid(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> = ResponseEntity.badRequest()
        .body(
            ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "입력값이 올바르지 않습니다",
                // 검증 순서는 실행마다 다르다. 같은 요청에 같은 응답(문서)이 나오도록 field 이름으로 정렬한다
                e.bindingResult.fieldErrors.map { FieldErrorDetail(it.field, it.defaultMessage.orEmpty()) }
                    .sortedWith(compareBy({ it.field }, { it.reason })),
            ),
        )
}
