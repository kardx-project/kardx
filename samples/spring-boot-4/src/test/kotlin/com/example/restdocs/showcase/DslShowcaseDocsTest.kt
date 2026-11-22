package com.example.restdocs.showcase

import com.example.restdocs.common.PageResponse
import com.example.restdocs.common.RestResponse
import com.example.restdocs.product.ProductResponse
import com.example.restdocs.product.ProductSummary
import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.ARRAY
import io.github.kardx.dsl.DATETIME
import io.github.kardx.dsl.ENUM
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.pathMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.dsl.type
import io.github.kardx.extractor.ofType
import io.github.kardx.extractor.responseFields
import io.github.kardx.extractor.wrappedResponseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andReturnAsObject
import io.github.kardx.mockmvc.andReturnEntity
import io.github.kardx.mockmvc.andStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

/**
 * 자동 추출 대신, 또는 함께 쓰는 문서화 방법 모음.
 *
 * | 테스트 | 보여 주는 기능 |
 * |---|---|
 * | handWritten | 모든 필드 직접 기술 (`example`, `constraint`, `withDefaultValue`, `isIgnored`), 래퍼만 자동 |
 * | relaxedFields | 필요한 필드만 문서화 (`relaxed = true`) |
 * | genericType | 래퍼 헬퍼 없이 제네릭 타입 그대로 추출 (`responseFields<T>()`) |
 * | responseHelpers | 문서화 없이 응답 검증 (`andReturnAsObject`, `andReturnEntity`, Spring `andExpectAll`). 문서에는 섹션이 생기지 않는다 |
 * | kotlinDsl | MockMvc Kotlin DSL(`mockMvc.get { }`) 결과에 같은 `andDocument { }` |
 * | mockMvcTester | `MockMvcTester` 결과에 같은 `andDocument { }`, AssertJ 검증 이어 쓰기 |
 *
 * 상품 API 와 같은 엔드포인트를 다른 방식으로 문서화하므로 OpenAPI 스펙에는 넣지 않는다.
 * (epages 는 같은 경로·메서드의 문서를 하나로 합치므로 상품 API 문서를 덮어쓴다)
 */
@DisplayName("문서화 방법 모음")
class DslShowcaseDocsTest : RestDocsTestSupport() {

    @BeforeEach
    fun excludeFromOpenApi() {
        RestDocsConfiguration.configure { openApi.enabled = false }
    }

    @Test
    @DisplayName("필드 직접 기술")
    fun handWritten() {
        mockMvc.perform(get("/api/v1/products/{id}", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument {
                // 타입은 보통 컨트롤러 인자(id: Long)에서 정해진다. ofType 은 핸들러를 모를 때(함수형 라우터, bindToServer)나
                // 핸들러와 다른 타입으로 문서화할 때 적고, 적으면 핸들러보다 우선한다
                pathParameters("id" pathMeans "상품 ID" ofType Long::class example "1")
                responseHeaders("ETag" headerMeans "상품 버전")
                wrappedResponseFields(
                    "data.id" type NUMBER means "상품 ID" example "1",
                    "data.name" type STRING means "상품명" constraint "1~50자",
                    "data.price" type NUMBER means "가격 (원)",
                    "data.status" type ENUM means "판매 상태 (SALE, SOLD_OUT, HIDDEN)",
                    "data.tags" type ARRAY means "태그 목록",
                    "data.options" type ARRAY means "옵션 목록",
                    "data.options[].name" type STRING means "옵션 이름",
                    "data.options[].additionalPrice" type NUMBER means "추가 금액" withDefaultValue "0",
                    "data.category" type OBJECT means "카테고리" isOptional true,
                    "data.category.id" type NUMBER means "카테고리 ID" isOptional true,
                    "data.category.name" type STRING means "카테고리 이름" isOptional true,
                    "data.createdAt" type DATETIME means "내부 관리용" isIgnored true,
                )
            }
    }

    @Test
    @DisplayName("필요한 필드만 문서화")
    fun relaxedFields() {
        mockMvc.perform(get("/api/v1/products/{id}", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "목록 화면에서 쓰는 필드만 문서화한다. 나머지 필드는 검증하지 않는다."
                pathParameters("id" pathMeans "상품 ID")
                responseFields(
                    "data.id" type NUMBER means "상품 ID",
                    "data.name" type STRING means "상품명",
                    "data.price" type NUMBER means "가격",
                )

                relaxed = true
            }
    }

    @Test
    @DisplayName("제네릭 타입 그대로 추출")
    fun genericType() {
        mockMvc.perform(
            get("/api/v1/products").param("keyword", "키보드"),
        ).andStatus(HttpStatus.OK)
            .andDocument {
                description = "`responseFields<RestResponse<PageResponse<ProductSummary>>>()` 로 타입 인자까지 추출한다."
                queryParameters("keyword" queryMeans "검색어" isOptional true example "키보드")
                responseFields<RestResponse<PageResponse<ProductSummary>>>()
            }
    }

    @Test
    @DisplayName("MockMvc Kotlin DSL")
    fun kotlinDsl() {
        mockMvc.get("/api/v1/products/{id}", 1L) {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }.andDocument {
            description = "`mockMvc.get(...) { }` 가 돌려주는 `ResultActionsDsl` 에서도 같은 블록으로 문서화한다."
            pathParameters("id" pathMeans "상품 ID")
            wrappedResponseFields<ProductResponse>()
        }
    }

    @Test
    @DisplayName("MockMvcTester")
    fun mockMvcTester() {
        val result = MockMvcTester.create(mockMvc)
            .get()
            .uri("/api/v1/products/{id}", 1L)
            .exchange()
            .andDocument {
                description = "`MockMvcTester` 의 `MvcTestResult` 에서도 같은 블록으로 문서화하고 AssertJ 검증을 이어 쓴다."
                pathParameters("id" pathMeans "상품 ID")
                wrappedResponseFields<ProductResponse>()
            }

        assertThat(result).hasStatusOk()
    }

    @Test
    fun responseHelpers() {
        val product =
            mockMvc.perform(get("/api/v1/products/{id}", 1L))
                .andExpectAll(
                    jsonPath("$.code").value(200),
                    jsonPath("$.data.name").value("기계식 키보드"),
                ).andReturnAsObject<RestResponse<ProductResponse>>()

        check(product.data?.options?.size == 2)

        // 상태 코드·헤더까지 볼 때는 ResponseEntity 로 받는다
        val response = mockMvc.perform(get("/api/v1/products/{id}", 1L))
            .andReturnEntity<RestResponse<ProductResponse>>()

        check(response.statusCode.is2xxSuccessful)
        check(response.headers.contentType?.isCompatibleWith(MediaType.APPLICATION_JSON) == true)
        check(response.body?.data?.name == product.data.name)
    }
}
