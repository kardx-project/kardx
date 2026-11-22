package com.example.restdocs.webflux.product

import io.github.kardx.annotation.Description
import io.github.kardx.annotation.EnumCode
import io.github.kardx.annotation.Masked
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@EnumCode("상품 상태")
enum class ProductStatus(val description: String) {
    SALE("판매중"),
    SOLD_OUT("품절"),
}

data class ProductResponse(
    @Description("상품 ID") val id: Long,
    @Description("상품명") val name: String,
    @Description("가격 (원)") val price: Long,
    @Description("상태") val status: ProductStatus,
    // 앱이 가려서 내보낸다는 사실을 문서에 표시한다. 가리는 것은 앱이 한다 (PhoneMasking.kt)
    @Description("판매자 연락처")
    @Masked("가운데 자리를 *")
    val sellerPhone: String,
)

data class ProductSummary(
    @Description("상품 ID") val id: Long,
    @Description("상품명") val name: String,
    @Description("가격 (원)") val price: Long,
    @Description("상태") val status: ProductStatus,
)

data class CreateProductRequest(
    @Description("상품명") @field:NotBlank @field:Size(max = 50) val name: String,
    @Description("가격 (원)") @field:Min(0) val price: Long,
    @Description("판매자 연락처")
    @field:Pattern(regexp = "010-\\d{4}-\\d{4}", message = "010-0000-0000 형식")
    val sellerPhone: String,
)

fun ProductResponse.toSummary(): ProductSummary = ProductSummary(id, name, price, status)
