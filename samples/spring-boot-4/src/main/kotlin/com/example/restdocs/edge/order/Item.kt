package com.example.restdocs.edge.order

import io.github.kardx.annotation.Description

/** 주문 품목. 장바구니의 `Item` 과 이름이 같아 OpenAPI 스키마 이름은 `OrderItem` 이 된다. */
data class Item(@Description("품목 ID") val id: Long, @Description("상품 이름") val name: String)
