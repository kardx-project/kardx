package com.example.restdocs.webflux.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException

/** 오류를 모두 [ErrorResponse] 로 돌려준다. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse(HttpStatus.NOT_FOUND.value(), e.message.orEmpty()))

    /**
     * WebFlux 에서 `@Valid @RequestBody` 검증에 실패하면 [WebExchangeBindException] 이 난다
     * (Spring MVC 는 `MethodArgumentNotValidException`).
     */
    @ExceptionHandler(WebExchangeBindException::class)
    fun invalid(e: WebExchangeBindException): ResponseEntity<ErrorResponse> = ResponseEntity.badRequest()
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
