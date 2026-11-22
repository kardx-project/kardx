package com.example.restdocs.webflux.product

import com.example.restdocs.webflux.common.ErrorResponse
import com.example.restdocs.webflux.support.RestDocsTestSupport
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.pathMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.fields
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.webtestclient.andDocument
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.test.web.reactive.server.expectBody

/**
 * WebFlux 앱을 WebTestClient 로 문서화하는 예제.
 *
 * | 테스트 | 보여 주는 기능 |
 * |---|---|
 * | listProducts | `Flow` 응답, 쿼리 파라미터 (Enum 값은 핸들러 인자), `ResponseSpec.andDocument` 뒤 `jsonPath` 검증, 400 선언 |
 * | getProduct | `suspend` 핸들러, `@Masked` 필드, `jsonPath` 검증 뒤 `BodyContentSpec.andDocument` |
 * | getProductNotFound | 같은 API 의 오류 응답 (404). 오류 스키마는 공통 `ErrorResponse` (yml `open-api.error-schema`) |
 * | createProduct | 제약조건, 201 + Location, `Authorization` 마스킹, `expectBody<T>()` 뒤 `BodySpec.andDocument`, 409 선언 |
 * | createProductInvalid | 검증 실패 응답 (400, WebFlux 의 `WebExchangeBindException`) |
 * | deleteProduct | `Mono` 핸들러, 본문 없는 응답 (204), 예시 객체로 오류 응답 선언 (404) |
 */
// 문서 섹션은 @Order 순서다. 등록 뒤 삭제하므로 실행 순서도 이 순서여야 한다
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DisplayName("상품 API")
class ProductApiDocsTest : RestDocsTestSupport() {

    @Test
    @Order(1)
    @DisplayName("상품 목록")
    fun listProducts() {
        webTestClient.get()
            .uri("/api/v1/products?status={status}", "SALE")
            .exchange()
            .expectStatus().isOk
            .andDocument {
                description = "상품 목록을 조회한다. 상태로 거를 수 있다."
                queryParameters("status" queryMeans "상태. 없으면 전체" isOptional true)
                responseFields<List<ProductSummary>>()

                // 테스트가 만들지 않는 오류 응답 선언. 상태와 설명만 (본문 없음)
                errorResponse(400, "상태 값이 정의되지 않은 값")
            }
            .jsonPath("$[0].name").isEqualTo("기계식 키보드")
    }

    @Test
    @Order(2)
    @DisplayName("상품 조회")
    fun getProduct() {
        webTestClient.get()
            .uri("/api/v1/products/{id}", 1L)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.sellerPhone").isEqualTo("010-****-5678")
            .andDocument {
                description = "상품 상세를 조회한다. 판매자 연락처는 가운데 자리를 가려서 돌려준다."
                pathParameters("id" pathMeans "상품 ID")
                responseFields<ProductResponse>()
            }
    }

    @Test
    @Order(3)
    @DisplayName("상품 조회 - 없는 상품 (404)")
    fun getProductNotFound() {
        webTestClient.get()
            .uri("/api/v1/products/{id}", 999L)
            .exchange()
            .expectStatus().isNotFound
            .andDocument {
                description = "상품이 없으면 404 와 오류 메시지를 돌려준다."
                pathParameters("id" pathMeans "상품 ID")
                responseFields<ErrorResponse>()
            }
            .jsonPath("$.code").isEqualTo(404)
    }

    @Test
    @Order(4)
    @DisplayName("상품 등록")
    fun createProduct() {
        val created = webTestClient.post()
            .uri("/api/v1/products")
            .headers { it.setBearerAuth("real-token") }
            .bodyValue(CreateProductRequest(name = "무선 키보드", price = 45000, sellerPhone = "010-1234-5678"))
            .exchange()
            .expectStatus().isCreated
            .expectBody<ProductResponse>()
            .andDocument {
                description = "상품을 등록한다. 생성된 상품 위치는 `Location` 헤더로 돌려준다."
                requestHeaders("Authorization" headerMeans "Bearer 액세스 토큰")
                requestFields<CreateProductRequest>()
                responseHeaders("Location" headerMeans "생성된 상품 URI")
                responseFields<ProductResponse>()

                // 본문 필드는 타입에서, Content-Type 은 직접 정한다 (기본값 application/json)
                errorResponse(409, "같은 이름의 상품이 이미 있음") {
                    fields<ErrorResponse>()
                    contentType = "application/problem+json"
                }
            }
            .returnResult()
            .responseBody

        check(created?.sellerPhone == "010-****-5678") { "seller phone should be masked: ${created?.sellerPhone}" }
    }

    @Test
    @Order(5)
    @DisplayName("상품 등록 - 잘못된 입력 (400)")
    fun createProductInvalid() {
        webTestClient.post()
            .uri("/api/v1/products")
            .headers { it.setBearerAuth("real-token") }
            .bodyValue(CreateProductRequest(name = "", price = -1, sellerPhone = "01012345678"))
            .exchange()
            .expectStatus().isBadRequest
            .andDocument {
                description = "입력값 검증에 실패하면 400 과 잘못된 필드 목록(`errors`)을 돌려준다."
                requestHeaders("Authorization" headerMeans "Bearer 액세스 토큰")
                requestFields<CreateProductRequest>()
                responseFields<ErrorResponse>()
            }
            .jsonPath("$.errors.length()").isEqualTo(3)
    }

    @Test
    @Order(6)
    @DisplayName("상품 삭제")
    fun deleteProduct() {
        webTestClient.delete()
            .uri("/api/v1/products/{id}", 3L)
            .exchange()
            .expectStatus().isNoContent
            .andDocument {
                description = "상품을 삭제한다."
                pathParameters("id" pathMeans "상품 ID")

                // 테스트가 만들지 않는 404 를 예시 객체 하나로 선언한다. 필드는 객체의 클래스에서, 예시는 객체를 직렬화한 JSON
                errorResponse(404, "상품이 없음", ErrorResponse(404, "상품을 찾을 수 없습니다"))
            }
    }
}
