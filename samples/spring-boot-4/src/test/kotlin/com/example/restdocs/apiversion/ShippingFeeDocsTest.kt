package com.example.restdocs.apiversion

import com.example.restdocs.shipping.ApiVersionConfig
import com.example.restdocs.shipping.ShippingFeeResponse
import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.responseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andExpectJsonPath
import io.github.kardx.mockmvc.andStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * Spring Framework 7 API 버전 예제 (Boot 4 샘플에만 있다).
 *
 * 매핑의 `version` 을 읽어 OpenAPI operation 에 `x-api-version` 과 필수 헤더 `X-API-Version`(값은 그 버전)을 적는다.
 * build 파일의 `apiVersioning { splitSpecs = true }` 라 버전마다 스펙(`openapi3-v1.0.yaml`, `openapi3-v1.1.yaml`)도 나온다.
 * 같은 경로·메서드라 OpenAPI 에서는 operation 하나로 합쳐진다 (요약·설명은 한쪽 것, 예시는 두 버전 모두).
 */
@DisplayName("배송비 API (API 버전)")
class ShippingFeeDocsTest : RestDocsTestSupport() {

    @Test
    @DisplayName("배송비 조회 (1.0)")
    fun shippingFeeV1() {
        mockMvc.perform(
            get("/api/shipping-fees").param("region", "JEJU")
                .header(ApiVersionConfig.HEADER, "1.0"),
        ).andStatus(HttpStatus.OK)
            .andExpectJsonPath("$.fee", 3000)
            .andDocument {
                description = "지역의 배송비를 조회한다. 1.0 은 지역과 관계없이 기본 요금이다."
                requestHeaders(ApiVersionConfig.HEADER headerMeans "API 버전")
                queryParameters("region" queryMeans "배송 지역")
                responseFields<ShippingFeeResponse>()
            }
    }

    @Test
    @DisplayName("배송비 조회 (1.1)")
    fun shippingFee() {
        mockMvc.perform(
            get("/api/shipping-fees").param("region", "JEJU")
                .header(ApiVersionConfig.HEADER, "1.1"),
        ).andStatus(HttpStatus.OK)
            .andExpectJsonPath("$.fee", 6000)
            .andDocument {
                description = "지역의 배송비를 조회한다. 1.1 부터 제주·도서 산간 추가 요금을 더한다."
                requestHeaders(ApiVersionConfig.HEADER headerMeans "API 버전")
                queryParameters("region" queryMeans "배송 지역")
                responseFields<ShippingFeeResponse>()
            }
    }
}
