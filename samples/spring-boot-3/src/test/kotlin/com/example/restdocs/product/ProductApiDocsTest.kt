package com.example.restdocs.product

import com.example.restdocs.common.ErrorResponse
import com.example.restdocs.common.PageResponse
import com.example.restdocs.support.RestDocsTestSupport
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockPart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.math.BigDecimal

/**
 * 라이브러리 기능을 한눈에 보여 주는 예제.
 *
 * | 테스트 | 보여 주는 기능 |
 * |---|---|
 * | getProduct | 공통 응답 래퍼, 요청/응답 헤더, 중첩 객체·배열·enum·nullable 자동 추출, 경로 변수 타입 (컨트롤러의 `id: Long` 에서 정해진다) |
 * | getProductNotFound | 같은 API 의 오류 응답 (404). OpenAPI 에서 getProduct 와 한 operation 이 되고, 오류 스키마는 공통 `ErrorResponse` |
 * | searchProducts | data class 에서 쿼리 파라미터 추출, 제네릭 페이지 응답 (`PageResponse<T>`), 오류 응답 선언 (400) |
 * | createProduct | 제약조건 문서화, 201 + Location, OpenAPI 스키마 이름, 필드·Content-Type 을 적은 오류 응답 선언 (409) |
 * | createProductInvalid | 검증 실패 응답 (400). 같은 `ErrorResponse` 의 `errors` 에 필드 오류 목록 |
 * | uploadImage | multipart 파트와 파트 본문 필드 (OpenAPI `multipart/form-data` 본문), 오류 응답 선언 (413 예시 객체, 415 `HttpStatus`) |
 * | productResource | HAL 링크, Map 필드 subsection |
 * | deleteProduct | 본문 없는 응답, deprecated API 표시 (컨트롤러의 `@Deprecated` 를 따른다), 오류 응답 선언 (409) |
 * | arrayData | 래퍼의 data 가 배열 (`wrappedResponseFields<List<T>>()`), OpenAPI 태그 추가 |
 * | nullableData | 래퍼의 data 가 null 일 수 있음 (`dataOptional`), OpenAPI 스펙에서 제외 (`openApi { excluded = true }`) |
 * | downloadManual | 파일 다운로드 (`Content-Disposition`, PDF 본문). 스니펫에는 본문 대신 크기만, OpenAPI 는 `format: binary` |
 */
// 문서 섹션은 @Order 순서(조회 → 검색 → 등록 → ...)다. 없으면 메서드 이름순이다
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DisplayName("상품 API")
class ProductApiDocsTest : RestDocsTestSupport() {

    @Test
    @Order(1)
    @DisplayName("상품 조회")
    fun getProduct() {
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

    @Test
    @Order(2)
    @DisplayName("상품 조회 - 없는 상품 (404)")
    fun getProductNotFound() {
        mockMvc.perform(
            get("/api/v1/products/{id}", 999L).bearerToken("real-token"),
        ).andStatus(HttpStatus.NOT_FOUND)
            .andExpectJsonPath("$.code", 404)
            .andDocument {
                description = "상품이 없으면 404 와 오류 메시지를 돌려준다."
                pathParameters("id" pathMeans "상품 ID")
                requestHeaders("Authorization" headerMeans "Bearer 액세스 토큰")
                // 오류 응답은 모두 ErrorResponse. 스키마 이름은 DocsConfig 의 openApi.errorSchema 가 붙인다.
                responseFields<ErrorResponse>()
            }
    }

    @Test
    @Order(3)
    @DisplayName("상품 검색")
    fun searchProducts() {
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

    @Test
    @Order(4)
    @DisplayName("상품 등록")
    fun createProduct() {
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
                // 요청 스키마는 자동 이름(CreateProductRequest) 그대로 쓴다. 응답의 자동 이름도 application-docs.yml 의
                // schema-names-without-wrapper: true 덕에 이미 ProductResponse 다. 여기서 직접 정하면 이름이 겹칠 때 이 이름이 남는다
                openApi { responseSchema = "ProductResponse" }

                // 본문 필드는 타입에서, Content-Type 은 직접 정한다 (기본값 application/json)
                errorResponse(409, "같은 이름의 상품이 이미 있음") {
                    fields<ErrorResponse>()
                    contentType = "application/problem+json"
                }
            }
    }

    @Test
    @Order(5)
    @DisplayName("상품 등록 - 잘못된 입력 (400)")
    fun createProductInvalid() {
        mockMvc.perform(
            post("/api/v1/products").jsonContent(CreateProductRequest(name = "", price = BigDecimal("-1"))),
        ).andStatus(HttpStatus.BAD_REQUEST)
            .andDocument {
                description = "입력값 검증에 실패하면 400 과 잘못된 필드 목록(`errors`)을 돌려준다."
                requestFields<CreateProductRequest>()
                responseFields<ErrorResponse>()
            }
    }

    @Test
    @Order(6)
    @DisplayName("상품 이미지 업로드")
    fun uploadImage() {
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

    @Test
    @Order(7)
    @DisplayName("상품 리소스 (HAL)")
    fun productResource() {
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

    @Test
    @Order(8)
    @DisplayName("상품 삭제")
    fun deleteProduct() {
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

    @Test
    @Order(9)
    @DisplayName("최근 본 상품 (배열 데이터)")
    fun arrayData() {
        mockMvc.perform(get("/api/v1/products/recent"))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "최근 본 상품 목록. data 가 배열인 응답이다."
                wrappedResponseFields<List<ProductSummary>>()
                openApi { tags("마이페이지") }
            }
    }

    @Test
    @Order(10)
    @DisplayName("추천 상품 (없으면 null)")
    fun nullableData() {
        mockMvc.perform(get("/api/v1/products/{id}/recommendation", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "추천할 상품이 없으면 data 가 null 이다. 내부 실험 API 라 OpenAPI 스펙에서는 뺀다."
                pathParameters("id" pathMeans "기준 상품 ID")
                wrappedResponseFields<ProductSummary>(dataOptional = true)
                openApi { excluded = true }
            }
    }

    @Test
    @Order(11)
    @DisplayName("상품 설명서 다운로드 (PDF)")
    fun downloadManual() {
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
}
