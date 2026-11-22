package com.example.restdocs.kotest.payment

import com.example.restdocs.kotest.support.MirrorDocsConfig
import com.example.restdocs.payment.BankTransfer
import com.example.restdocs.payment.CardPayment
import com.example.restdocs.payment.PaymentRequest
import com.example.restdocs.payment.PaymentResponse
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.wrappedResponseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.context.WebApplicationContext

/** [com.example.restdocs.payment.PaymentApiDocsTest] 를 Kotest 로 옮긴 스펙. */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("결제 API")
class PaymentApiDocsSpec(context: WebApplicationContext) :
    FunSpec({
        val restDocs = extension(MirrorDocsConfig.restDocsExtension())
        val mockMvc = restDocsMockMvc(context, restDocs.provider)

        beforeEach { MirrorDocsConfig.apply() }

        test("카드 결제") {
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

        test("계좌이체 결제") {
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
    })
