package com.example.restdocs.payment

import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.wrappedResponseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * sealed 타입(다형 본문)과 OAuth2 를 보여 주는 예제.
 *
 * | 테스트 | 보여 주는 기능 |
 * |---|---|
 * | payByCard | `@JsonTypeInfo` sealed → `oneOf` + `discriminator` (요청 `method`, 래퍼 안 `data.method`), 기본값, OAuth2 |
 * | payByBankTransfer | 같은 operation 의 다른 하위 타입 예시. `openApi { summary }` 로 operation 이름을 정한다 |
 *
 * OAuth2 scheme(토큰 URL·scope 설명)은 build 파일의 `kardx { openApiSecurity { oauth2 { } } }` 에 있다.
 */
@DisplayName("결제 API")
class PaymentApiDocsTest : RestDocsTestSupport() {

    @Test
    @DisplayName("카드 결제")
    fun payByCard() {
        mockMvc.perform(
            post("/api/v1/payments").jsonContent(
                PaymentRequest(
                    orderId = 3001,
                    amount = 59000,
                    method = CardPayment("1234-****-****-5678", installmentMonths = 3),
                ),
            ),
        ).andStatus(HttpStatus.CREATED)
            .andDocument {
                description = "주문을 결제한다. 결제 수단은 `method.type` 으로 고른다 (`CARD`, `BANK_TRANSFER`)."
                requestFields<PaymentRequest>()
                wrappedResponseFields<PaymentResponse>()
                openApi {
                    // 같은 operation 의 두 테스트(카드·계좌이체)가 합쳐지므로 operation 이름을 직접 정한다
                    summary = "결제 요청"
                    oauth2("payments:write")
                }
            }
    }

    @Test
    @DisplayName("계좌이체 결제")
    fun payByBankTransfer() {
        mockMvc.perform(
            post("/api/v1/payments").jsonContent(
                PaymentRequest(orderId = 3002, amount = 32000, method = BankTransfer("004", "홍길동")),
            ),
        ).andStatus(HttpStatus.CREATED)
            .andDocument {
                description = "계좌이체로 결제한다."
                requestFields<PaymentRequest>()
                wrappedResponseFields<PaymentResponse>()
                openApi {
                    // 같은 operation 의 두 테스트(카드·계좌이체)가 합쳐지므로 operation 이름을 직접 정한다
                    summary = "결제 요청"
                    oauth2("payments:write")
                }
            }
    }
}
