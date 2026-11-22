package com.example.restdocs.product

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import io.github.kardx.annotation.Description
import io.github.kardx.annotation.EnumCode
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

@EnumCode("상품 상태")
enum class ProductStatus(val description: String) {
    SALE("판매중"),
    SOLD_OUT("품절"),
    HIDDEN("숨김"),
}

data class Category(
    @Description("카테고리 ID") val id: Long,
    // @Description 대신 Jackson 어노테이션에서 설명을 읽는 예 (application-docs.yml 의 description-annotations: jackson)
    @param:JsonPropertyDescription("카테고리 이름") val name: String,
)

data class ProductOption(
    @Description("옵션 이름") @field:NotBlank val name: String,
    @Description("추가 금액") val additionalPrice: Long,
)

data class ProductResponse(
    @Description("상품 ID") val id: Long,
    @Description("상품명") val name: String,
    @Description("가격") val price: BigDecimal,
    @Description("상태") val status: ProductStatus,
    @Description("태그") val tags: List<String>,
    @Description("옵션") val options: List<ProductOption>,
    @Description("카테고리 (미분류면 null)") val category: Category?,
    @Description("등록 일시") val createdAt: LocalDateTime,
)

data class ProductSummary(
    @Description("상품 ID") val id: Long,
    @Description("상품명") val name: String,
    @Description("가격") val price: BigDecimal,
    @Description("상태") val status: ProductStatus,
)

data class CreateProductRequest(
    @Description("상품명") @field:NotBlank @field:Size(max = 50) val name: String,
    @Description("가격") @field:DecimalMin("0") val price: BigDecimal,
    @Description("태그") @field:Size(max = 5, message = "태그는 최대 {max}개") val tags: List<String> = emptyList(),
    @Description("옵션") val options: List<ProductOption> = emptyList(),
    @Description("카테고리 ID") val categoryId: Long? = null,
)

/** 쿼리 파라미터로 받는 검색 조건. `queryParameters<T>()` 로 문서화한다. */
data class ProductSearchCondition(
    @Description("검색어 (상품명 부분 일치)") val keyword: String? = null,
    @Description("상태") val status: ProductStatus? = null,
    @Description("최소 가격") val minPrice: Long? = null,
    // @Deprecated 프로퍼티는 문서 표에 Deprecated, OpenAPI 에 deprecated: true 로 나간다
    @Deprecated("minPrice 를 쓴다")
    @Description("최소 가격 (예전 이름)")
    val priceFrom: Long? = null,
    @Description("페이지 번호") val page: Int = 0,
    @Description("페이지 크기") val size: Int = 20,
)

data class ImageMeta(
    @Description("대체 텍스트") @field:NotBlank val alt: String,
    @Description("대표 이미지 여부") val primary: Boolean,
)

data class ImageResponse(
    @Description("이미지 URL") val url: String,
    @Description("대체 텍스트") val alt: String,
    @Description("파일 크기 (byte)") val size: Long,
)

data class Href(@Description("링크 URL") val href: String)

/** HAL 형식 응답. */
data class ProductResource(
    @Description("상품 ID") val id: Long,
    @Description("상품명") val name: String,
    @get:JsonProperty("_links") @Description("HAL 링크") val links: Map<String, Href>,
)
