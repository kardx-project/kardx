package com.example.restdocs.payment

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.github.kardx.annotation.Description
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

/**
 * 결제 수단. JSON 의 `type` 값으로 하위 타입을 고른다.
 *
 * 문서 표에는 하위 타입의 필드가 모두 펼쳐 나가고, OpenAPI 에서는 `oneOf`(하위 타입마다 스키마) + `discriminator`
 * (`type` 값 → 스키마)가 된다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = CardPayment::class, name = "CARD"),
    JsonSubTypes.Type(value = BankTransfer::class, name = "BANK_TRANSFER"),
)
sealed interface PaymentMethod

data class CardPayment(
    @Description("카드 번호 (가운데 8자리 마스킹)") @field:NotBlank val cardNumber: String,
    // Kotlin 기본값은 문서 표의 Default 와 OpenAPI 의 default 로 나간다
    @Description("할부 개월 수 (0 이면 일시불)") val installmentMonths: Int = 0,
) : PaymentMethod

data class BankTransfer(
    @Description("은행 코드") @field:NotBlank val bankCode: String,
    @Description("입금자명") @field:NotBlank val depositor: String,
) : PaymentMethod

data class PaymentRequest(
    @Description("주문 ID") val orderId: Long,
    @Description("결제 금액") @field:Positive val amount: Long,
    @Description("통화 (ISO 4217)") val currency: String = "KRW",
    @Description("결제 수단") val method: PaymentMethod,
)

data class PaymentResponse(
    @Description("결제 ID") val id: Long,
    @Description("주문 ID") val orderId: Long,
    @Description("결제 금액") val amount: Long,
    @Description("통화 (ISO 4217)") val currency: String,
    @Description("결제 수단") val method: PaymentMethod,
    @Description("승인 일시") val approvedAt: LocalDateTime,
)
