package com.example.restdocs.kotest.showcase

import com.example.restdocs.common.PageResponse
import com.example.restdocs.common.RestResponse
import com.example.restdocs.kotest.support.MirrorDocsConfig
import com.example.restdocs.product.ProductResponse
import com.example.restdocs.product.ProductSummary
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
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.web.context.WebApplicationContext

/**
 * [com.example.restdocs.showcase.DslShowcaseDocsTest] 를 Kotest 로 옮긴 스펙. MockMvc Kotlin DSL·`MockMvcTester` 결과도
 * 같은 `andDocument { }` 로 문서화한다. JUnit 판이 `@BeforeEach` 에서 끄는 OpenAPI 는 [MirrorDocsConfig] 가 이미 끈다.
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("문서화 방법 모음")
class DslShowcaseDocsSpec(context: WebApplicationContext) :
    FunSpec({
        val restDocs = extension(MirrorDocsConfig.restDocsExtension())
        val mockMvc = restDocsMockMvc(context, restDocs.provider)

        beforeEach { MirrorDocsConfig.apply() }

        test("제네릭 타입 그대로 추출") {
            mockMvc.perform(
                get("/api/v1/products").param("keyword", "키보드"),
            ).andStatus(HttpStatus.OK)
                .andDocument {
                    description =
                        "`responseFields<RestResponse<PageResponse<ProductSummary>>>()` 로 타입 인자까지 추출한다."
                    queryParameters("keyword" queryMeans "검색어" isOptional true example "키보드")
                    responseFields<RestResponse<PageResponse<ProductSummary>>>()
                }
        }

        test("필드 직접 기술") {
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

        test("MockMvc Kotlin DSL") {
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

        test("MockMvcTester") {
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

        test("필요한 필드만 문서화") {
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

        // JUnit 판의 responseHelpers 와 같다. 문서화하지 않으므로 섹션이 생기지 않는다
        test("문서화 없이 응답 검증") {
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
    })
