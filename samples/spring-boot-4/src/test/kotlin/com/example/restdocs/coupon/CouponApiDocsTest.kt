package com.example.restdocs.coupon

import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.config.DescriptionResolver
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.orElse
import io.github.kardx.dsl.pathMeans
import io.github.kardx.extractor.wrappedResponseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andPrint
import io.github.kardx.mockmvc.andStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * 설명 어노테이션 교체 예. 기존 코드의 `@ApiField` 를 먼저 보고, 없으면 바꾸기 전의 기본 함수
 * (`@Description` → `description-annotations` → KDoc/Javadoc)를 부른다. `usable` 은 Javadoc `@param` 에서 읽는다.
 * 응답 DTO 는 Java record 다. `@BeforeEach` 에서 한 설정은 테스트가 끝나면 문서화 확장이 되돌린다 (RestDocsTestSupport 참고).
 */
@DisplayName("쿠폰 API")
class CouponApiDocsTest : RestDocsTestSupport() {

    @BeforeEach
    fun useApiFieldAnnotation() {
        RestDocsConfiguration.configure {
            // 내 규칙을 먼저 보고, 없으면 지금 규칙(@Description → 설명 어노테이션 → KDoc)으로 넘긴다
            descriptions.resolver =
                DescriptionResolver { it.findAnnotation<ApiField>()?.value } orElse descriptions.resolver
        }
    }

    @Test
    @DisplayName("쿠폰 조회")
    fun getCoupon() {
        mockMvc.perform(get("/api/v1/coupons/{code}", "WELCOME15"))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "쿠폰 코드로 할인 정보를 조회한다."
                pathParameters("code" pathMeans "쿠폰 코드")
                wrappedResponseFields<CouponResponse>()
            }
            .andPrint()
    }
}
