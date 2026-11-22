package com.example.restdocs.kotest.product

import com.example.restdocs.common.ErrorResponse
import com.example.restdocs.common.PageResponse
import com.example.restdocs.kotest.support.MirrorDocsConfig
import com.example.restdocs.product.CreateProductRequest
import com.example.restdocs.product.ImageMeta
import com.example.restdocs.product.ImageResponse
import com.example.restdocs.product.ProductOption
import com.example.restdocs.product.ProductResource
import com.example.restdocs.product.ProductResponse
import com.example.restdocs.product.ProductSearchCondition
import com.example.restdocs.product.ProductSummary
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.linkMeans
import io.github.kardx.dsl.partMeans
import io.github.kardx.dsl.pathMeans
import io.github.kardx.dsl.queryMeans
import io.github.kardx.dsl.subsection
import io.github.kardx.extractor.except
import io.github.kardx.extractor.fields
import io.github.kardx.extractor.queryParameters
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.requestPartFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.extractor.responseFieldsOf
import io.github.kardx.extractor.wrappedResponseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andExpectJsonPath
import io.github.kardx.mockmvc.andReturnEntity
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.bearerToken
import io.github.kardx.mockmvc.jsonContent
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockPart
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

/**
 * [com.example.restdocs.product.ProductApiDocsTest] 를 Kotest 로 옮긴 스펙.
 * JUnit 의 `@TestMethodOrder` + `@Order` 대신 선언 순서가 섹션 순서다. `openApi { }` 블록도 그대로 두지만,
 * 이 스펙은 OpenAPI 조각을 만들지 않으므로([MirrorDocsConfig]) 스펙에는 JUnit 판의 결과만 들어간다.
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("상품 API")
class ProductApiDocsSpec(context: WebApplicationContext) :
    FunSpec({
        val restDocs = extension(MirrorDocsConfig.restDocsExtension())
        val mockMvc = restDocsMockMvc(context, restDocs.provider)

        beforeEach { MirrorDocsConfig.apply() }

        test("상품 조회") {
            mockMvc.perform(
                get("/api/v1/products/{id}", 1L).bearerToken("real-token")
                    .header("X-Request-Id", "req-123"),
            ).andStatus(HttpStatus.OK)
                .andDocument {
                    description = "상품 상세를 조회한다. `ETag` 로 캐시를 검증할 수 있다."
                    pathParameters("id" pathMeans "상품 ID")
                    requestHeaders(
                        "Authorization" headerMeans "Bearer 액세스 토큰",
                        "X-Request-Id" headerMeans "요청 추적 ID. 보내면 응답에 그대로 돌려준다" isOptional true,
                    )

                    responseHeaders(
                        "ETag" headerMeans "상품 버전",
                        "X-Request-Id" headerMeans "요청의 추적 ID" isOptional true,
                    )

                    wrappedResponseFields<ProductResponse>()
                    openApi { responseSchema = "ProductDetailResponse" }
                }
        }

        test("상품 조회 - 없는 상품 (404)") {
            mockMvc.perform(
                get("/api/v1/products/{id}", 999L).bearerToken("real-token"),
            ).andStatus(HttpStatus.NOT_FOUND)
                .andExpectJsonPath("$.code", 404)
                .andDocument {
                    description = "상품이 없으면 404 와 오류 메시지를 돌려준다."
                    pathParameters("id" pathMeans "상품 ID")
                    requestHeaders("Authorization" headerMeans "Bearer 액세스 토큰")
                    responseFields<ErrorResponse>()
                }
        }

        test("상품 검색") {
            mockMvc.perform(
                get("/api/v1/products").param("keyword", "키보드")
                    .param("status", "SALE")
                    .param("minPrice", "10000")
                    .param("page", "0")
                    .param("size", "20"),
            ).andStatus(HttpStatus.OK)
                .andDocument {
                    description = "조건으로 상품을 검색한다. 모든 조건은 선택이다."
                    queryParameters<ProductSearchCondition>()
                    wrappedResponseFields<PageResponse<ProductSummary>>()

                    // 테스트가 만들지 않는 오류 응답 선언. 상태와 설명만 (본문 없음)
                    errorResponse(400, "검색 조건 형식이 잘못됨 (예: `minPrice` 가 숫자가 아님)")
                }
        }

        test("상품 등록") {
            mockMvc.perform(
                post("/api/v1/products").jsonContent(
                    CreateProductRequest(
                        name = "기계식 키보드",
                        price = BigDecimal("59000"),
                        tags = listOf("키보드"),
                        options = listOf(ProductOption("갈축", 0)),
                        categoryId = 3,
                    ),
                ),
            ).andStatus(HttpStatus.CREATED)
                .andDocument {
                    description = "상품을 등록한다. 생성된 상품 위치는 `Location` 헤더로 돌려준다."
                    requestFields<CreateProductRequest>()
                    responseHeaders("Location" headerMeans "생성된 상품 URI")
                    wrappedResponseFields<ProductResponse>()
                    openApi { responseSchema = "ProductResponse" }

                    // 본문 필드는 타입에서, Content-Type 은 직접 정한다 (기본값 application/json)
                    errorResponse(409, "같은 이름의 상품이 이미 있음") {
                        fields<ErrorResponse>()
                        contentType = "application/problem+json"
                    }
                }
        }

        test("상품 등록 - 잘못된 입력 (400)") {
            mockMvc.perform(
                post("/api/v1/products").jsonContent(CreateProductRequest(name = "", price = BigDecimal("-1"))),
            ).andStatus(HttpStatus.BAD_REQUEST)
                .andDocument {
                    description = "입력값 검증에 실패하면 400 과 잘못된 필드 목록(`errors`)을 돌려준다."
                    requestFields<CreateProductRequest>()
                    responseFields<ErrorResponse>()
                }
        }

        test("상품 이미지 업로드") {
            val file = MockMultipartFile("file", "keyboard.png", MediaType.IMAGE_PNG_VALUE, ByteArray(128))
            val meta = MockPart("meta", """{"alt":"키보드 정면","primary":true}""".toByteArray())

            meta.headers.contentType = MediaType.APPLICATION_JSON

            mockMvc.perform(
                multipart("/api/v1/products/{id}/images", 1L).file(file)
                    .part(meta),
            ).andStatus(HttpStatus.OK)
                .andDocument {
                    description = "이미지 파일과 메타데이터를 multipart 로 올린다."
                    pathParameters("id" pathMeans "상품 ID")
                    requestParts("file" partMeans "이미지 파일 (PNG, JPEG)", "meta" partMeans "이미지 메타데이터 (JSON)")
                    requestPartFields<ImageMeta>("meta")
                    wrappedResponseFields<ImageResponse>()

                    errorResponse(413, "파일이 5MB 를 넘음", ErrorResponse(413, "이미지는 5MB 까지 올릴 수 있습니다"))
                    errorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PNG·JPEG 가 아닌 파일")
                }
        }

        test("상품 리소스 (HAL)") {
            mockMvc.perform(get("/api/v1/products/{id}/resource", 1L))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "HAL 형식으로 상품과 관련 링크를 돌려준다."
                    pathParameters("id" pathMeans "상품 ID")
                    links("self" linkMeans "이 리소스", "images" linkMeans "상품 이미지 목록")
                    responseFields(
                        responseFieldsOf<ProductResource>().except("_links") +
                            ("_links" subsection OBJECT means "HAL 링크 (위 Links 표 참고)"),
                    )
                }
        }

        test("상품 삭제") {
            mockMvc.perform(delete("/api/v1/products/{id}", 1L))
                .andStatus(HttpStatus.NO_CONTENT)
                .andDocument {
                    // deprecated 표시는 컨트롤러 메서드의 @Deprecated 에서 온다. 블록에서 정하려면 deprecated("이유")
                    description = "상품을 삭제한다."
                    pathParameters("id" pathMeans "상품 ID")

                    errorResponse(
                        HttpStatus.CONFLICT,
                        "주문이 진행 중인 상품은 삭제할 수 없음",
                        ErrorResponse(409, "진행 중인 주문이 있어 삭제할 수 없습니다"),
                    )
                }
        }

        test("최근 본 상품 (배열 데이터)") {
            mockMvc.perform(get("/api/v1/products/recent"))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "최근 본 상품 목록. data 가 배열인 응답이다."
                    wrappedResponseFields<List<ProductSummary>>()
                    openApi { tags("마이페이지") }
                }
        }

        test("추천 상품 (없으면 null)") {
            mockMvc.perform(get("/api/v1/products/{id}/recommendation", 1L))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "추천할 상품이 없으면 data 가 null 이다. 내부 실험 API 라 OpenAPI 스펙에서는 뺀다."
                    pathParameters("id" pathMeans "기준 상품 ID")
                    wrappedResponseFields<ProductSummary>(dataOptional = true)
                    openApi { excluded = true }
                }
        }

        test("상품 설명서 다운로드 (PDF)") {
            val response = mockMvc.perform(get("/api/v1/products/{id}/manual", 1L).param("lang", "ko"))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "상품 설명서 PDF 를 첨부 파일로 내려받는다. 파일 이름은 `Content-Disposition` 에 있다."
                    pathParameters("id" pathMeans "상품 ID")
                    queryParameters("lang" queryMeans "설명서 언어 (ko, en)" isOptional true withDefaultValue "ko")
                    responseHeaders(
                        "Content-Type" headerMeans "파일 형식 (`application/pdf`)",
                        "Content-Disposition" headerMeans "첨부 파일과 파일 이름 (`attachment; filename=...`)",
                    )
                    responseFile("상품 설명서 PDF")
                }
                .andReturnEntity<ByteArray>()

            assertThat(response.headers.contentDisposition.filename).isEqualTo("product-1-manual-ko.pdf")
            assertThat(response.body).startsWith(*"%PDF-".toByteArray())
        }
    })
