package com.example.restdocs.product

import com.example.restdocs.common.NotFoundException
import com.example.restdocs.common.PageResponse
import com.example.restdocs.common.RestResponse
import jakarta.validation.Valid
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/products")
class ProductController {

    // 예제에서는 ID 가 MAX_PRODUCT_ID 보다 크면 없는 상품이다.
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        @RequestHeader("X-Request-Id", required = false) requestId: String?,
    ): ResponseEntity<RestResponse<ProductResponse>> {
        if (id > MAX_PRODUCT_ID) throw NotFoundException("상품을 찾을 수 없습니다: $id")

        return ResponseEntity.ok()
            .eTag("\"3\"")
            .apply { requestId?.let { header("X-Request-Id", it) } }
            .body(RestResponse.ok(sample(id)))
    }

    @GetMapping
    fun search(@ModelAttribute condition: ProductSearchCondition): RestResponse<PageResponse<ProductSummary>> =
        RestResponse.ok(
            PageResponse(
                total = 1,
                page = condition.page,
                size = condition.size,
                content = listOf(ProductSummary(1, "키보드", BigDecimal("59000"), ProductStatus.SALE)),
            ),
        )

    @GetMapping("/recent")
    fun recent(): RestResponse<List<ProductSummary>> = RestResponse.ok(
        listOf(
            ProductSummary(1, "기계식 키보드", BigDecimal("59000"), ProductStatus.SALE),
            ProductSummary(2, "무선 마우스", BigDecimal("32000"), ProductStatus.SOLD_OUT),
        ),
    )

    // 추천 상품이 없으면 data 가 null 이다. 예제에서는 항상 없다.
    @GetMapping("/{id}/recommendation")
    fun recommendation(@PathVariable id: Long): RestResponse<ProductSummary?> =
        RestResponse(200, "OK", RECOMMENDATIONS[id])

    @PostMapping
    fun create(@RequestBody @Valid request: CreateProductRequest): ResponseEntity<RestResponse<ProductResponse>> =
        ResponseEntity.created(URI.create("/api/v1/products/10"))
            .body(
                RestResponse.ok(
                    sample(10).copy(name = request.name, price = request.price, tags = request.tags),
                ),
            )

    @PostMapping("/{id}/images")
    fun uploadImage(
        @PathVariable id: Long,
        @RequestPart("file") file: MultipartFile,
        @RequestPart("meta") @Valid meta: ImageMeta,
    ): RestResponse<ImageResponse> = RestResponse.ok(
        ImageResponse("https://cdn.example.com/products/$id/${file.originalFilename}", meta.alt, file.size),
    )

    @GetMapping("/{id}/resource", produces = ["application/hal+json"])
    fun resource(@PathVariable id: Long): ProductResource = ProductResource(
        id = id,
        name = "키보드",
        links = mapOf(
            "self" to Href("/api/v1/products/$id/resource"),
            "images" to Href("/api/v1/products/$id/images"),
        ),
    )

    // 상품 설명서 PDF 를 첨부 파일로 내려준다. 본문은 바이너리라 문서에는 크기만 나간다.
    @GetMapping("/{id}/manual")
    fun manual(@PathVariable id: Long, @RequestParam(defaultValue = "ko") lang: String): ResponseEntity<ByteArray> {
        val disposition = ContentDisposition.attachment()
            .filename("product-$id-manual-$lang.pdf")
            .build()

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(ProductManual.pdf(id, lang))
    }

    // 문서에는 deprecated API 로 나간다 (api-summary 의 *Deprecated* 줄, OpenAPI deprecated: true). 메시지가 이유가 된다
    @Deprecated("다음 버전의 숨김 처리 API 를 쓰세요")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Unit> =
        if (id > 0) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    private fun sample(id: Long) = ProductResponse(
        id = id,
        name = "기계식 키보드",
        price = BigDecimal("59000.00"),
        status = ProductStatus.SALE,
        tags = listOf("키보드", "기계식"),
        options = listOf(ProductOption("갈축", 0), ProductOption("적축", 3000)),
        category = Category(3, "주변기기"),
        createdAt = LocalDateTime.of(2026, 11, 5, 10, 0),
    )

    private companion object {
        const val MAX_PRODUCT_ID = 100L
        val RECOMMENDATIONS: Map<Long, ProductSummary> = emptyMap()
    }
}
