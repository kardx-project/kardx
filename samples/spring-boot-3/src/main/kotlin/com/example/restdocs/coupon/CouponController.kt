package com.example.restdocs.coupon

import com.example.restdocs.common.RestResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 기존 코드베이스가 쓰던 설명 어노테이션 예. `@Description` 대신 이 어노테이션으로 문서를 만들 수 있다.
 * Java record 에도 붙일 수 있도록 필드·파라미터·메서드 타깃을 연다.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiField(val value: String)

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController {

    @GetMapping("/{code}")
    fun get(@PathVariable code: String): RestResponse<CouponResponse> =
        RestResponse.ok(CouponResponse(code, 15, LocalDate.of(2026, 12, 31), true))
}
