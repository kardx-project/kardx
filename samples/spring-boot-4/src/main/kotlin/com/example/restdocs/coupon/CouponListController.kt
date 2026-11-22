package com.example.restdocs.coupon

import com.example.restdocs.common.RestResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 쿠폰 목록. Boot 4 샘플에만 있다: RestTestClient(Spring Framework 7) 문서화 예제
 * (`resttestclient/CouponListDocsTest`)가 문서화한다.
 */
@RestController
@RequestMapping("/api/v1/coupons")
class CouponListController {

    @GetMapping
    fun list(@RequestParam(defaultValue = "false") usableOnly: Boolean): RestResponse<List<CouponResponse>> =
        RestResponse.ok(COUPONS.filter { !usableOnly || it.usable })

    private companion object {
        val COUPONS = listOf(
            CouponResponse("WELCOME15", 15, LocalDate.of(2026, 12, 31), true),
            CouponResponse("SUMMER10", 10, LocalDate.of(2026, 8, 31), false),
        )
    }
}
