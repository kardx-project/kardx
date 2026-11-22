package com.example.restdocs.edge.cart

import io.github.kardx.annotation.Description

/** 장바구니 품목. 주문의 `Item` 과 이름이 같아 OpenAPI 스키마 이름은 `CartItem` 이 된다. */
data class Item(@Description("상품 코드") val code: String, @Description("수량") val quantity: Int)
