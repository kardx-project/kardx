package com.example.restdocs.resttestclient

import com.example.restdocs.coupon.CouponResponse
import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.wrappedResponseFields
import io.github.kardx.resttestclient.andDocument
import io.github.kardx.resttestclient.restDocs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * RestTestClient(Spring Framework 7 / Boot 4) 문서화 예제. 실제로 띄운 서버에 붙은 클라이언트(`bindToServer`)에
 * `restDocs(provider)` 를 달면 MockMvc·WebTestClient 와 같은 `andDocument { }` 로 스니펫·문서·OpenAPI 가 만들어진다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("쿠폰 목록 API (RestTestClient)")
class CouponListDocsTest : RestDocsTestSupport() {

    @LocalServerPort
    var port: Int = 0

    private lateinit var client: RestTestClient

    @BeforeEach
    fun setUpClient(provider: RestDocumentationContextProvider) {
        client = RestTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .restDocs(provider)
            .build()
    }

    @Test
    @DisplayName("쿠폰 목록")
    fun listCoupons() {
        client.get()
            .uri("/api/v1/coupons?usableOnly={usableOnly}", true)
            .exchange()
            .expectStatus().isOk
            .andDocument {
                description = "쿠폰 목록을 조회한다. RestTestClient 로 실제 서버에 보낸 요청에서 문서를 만들었다."
                queryParameters("usableOnly" queryMeans "사용 가능한 쿠폰만" isOptional true withDefaultValue "false")
                wrappedResponseFields<List<CouponResponse>>()
                // RestTestClient 는 요청을 처리한 컨트롤러 메서드를 모르므로 operationId 를 핸들러 이름으로 만들지 못한다
                // (규칙의 대체 이름은 `coupon-list-docs-test.list-coupons`). 그래서 직접 정한다
                openApi { operationId = "listCoupons" }
            }
            .jsonPath("$.data.length()").isEqualTo(1)
            .jsonPath("$.data[0].code").isEqualTo("WELCOME15")
    }
}
