package com.example.restdocs.payment

import com.example.restdocs.common.RestResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/**
 * 결제 승인. 가맹점 서버가 OAuth2 client credentials 로 받은 토큰(`payments:write`)으로 부른다는 설정의 예제다.
 * 샘플 앱은 토큰을 검사하지 않는다.
 *
 * 응답은 다른 API 처럼 공통 래퍼(`RestResponse`)로 감싼다. 래퍼 안의 다형 프로퍼티(`data.method`)도 OpenAPI `oneOf` 가 된다.
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {

    /**
     * 주문 결제.
     *
     * 주문 금액을 결제한다. 결제 수단은 `method.type` 으로 고른다 (`CARD`, `BANK_TRANSFER`).
     */
    // 테스트의 openApi { summary = "결제 요청" } 이 이 KDoc 의 첫 문장(주문 결제)보다 먼저다. description 은 이 KDoc 이다
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun pay(@RequestBody @Valid request: PaymentRequest): RestResponse<PaymentResponse> = RestResponse.ok(
        PaymentResponse(
            id = PAYMENT_ID,
            orderId = request.orderId,
            amount = request.amount,
            currency = request.currency,
            method = request.method,
            approvedAt = LocalDateTime.of(2026, 11, 19, 14, 30),
        ),
    )

    private companion object {
        const val PAYMENT_ID = 501L
    }
}
