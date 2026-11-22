package com.example.restdocs.webflux.event

import io.github.kardx.annotation.Description

data class PriceChangedEvent(
    @Description("상품 ID") val productId: Long,
    @Description("바뀌기 전 가격 (원)") val before: Long,
    @Description("바뀐 가격 (원)") val after: Long,
)

data class SoldOutEvent(@Description("상품 ID") val productId: Long)

data class RestockedEvent(@Description("상품 ID") val productId: Long, @Description("입고 수량") val quantity: Int)
