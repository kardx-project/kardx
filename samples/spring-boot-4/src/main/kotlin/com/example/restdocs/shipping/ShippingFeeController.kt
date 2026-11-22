package com.example.restdocs.shipping

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 배송비 조회. Boot 4 샘플에만 있다: 같은 경로를 API 버전(`X-API-Version`)으로 나누는 Spring Framework 7 예제다.
 * 1.1 부터 제주·도서 산간 추가 요금을 더한다.
 *
 * 두 버전의 응답 모양은 같다. epages 가 같은 경로·메서드의 문서를 operation 하나로 합치므로, 버전마다 본문 모양이 다르면
 * OpenAPI 본문 스키마는 두 버전의 합이 된다 (버전별 스펙에서도 같다).
 */
@RestController
@RequestMapping("/api/shipping-fees")
class ShippingFeeController {

    @GetMapping(version = "1.0")
    fun feeV1(@RequestParam region: String): ShippingFeeResponse = ShippingFeeResponse(region, BASE_FEE)

    // "1.1+" 는 1.1 과 그 뒤 버전을 모두 받는다. 문서에는 1.1 로 나간다
    @GetMapping(version = "1.1+")
    fun fee(@RequestParam region: String): ShippingFeeResponse =
        ShippingFeeResponse(region, BASE_FEE + if (region in REMOTE_REGIONS) REMOTE_SURCHARGE else 0)

    private companion object {
        const val BASE_FEE = 3000L
        const val REMOTE_SURCHARGE = 3000L
        val REMOTE_REGIONS = setOf("JEJU", "ULLEUNG")
    }
}
