package com.example.restdocs.edge

import com.example.restdocs.support.RestDocsTestSupport
import io.github.kardx.dsl.pathMeans
import io.github.kardx.extractor.queryParameters
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import com.example.restdocs.edge.cart.Item as CartItem
import com.example.restdocs.edge.order.Item as OrderItem

/**
 * 문서화가 까다로운 모양. 스니펫·OpenAPI 결과는 `scripts/verify-docs-artifacts.py` 가 확인한다.
 *
 * - 정규식 경로 변수: OpenAPI 경로는 `/files/{name}`, 파라미터 이름은 `name`
 * - 이름이 같은 다른 패키지의 클래스: OpenAPI 스키마 `OrderItem`, `CartItem`
 * - 요청·응답 모양이 다른 DTO: 응답 `Account`, 요청 `AccountRequest`
 * - `@Nested`·`@ParameterizedTest`·오버로드: 이 클래스 문서의 섹션이 되고 스니펫이 겹치지 않는다
 */
@DisplayName("엣지 케이스 API")
class EdgeCaseDocsTest : RestDocsTestSupport() {

    @ParameterizedTest(name = "주문 {0}")
    @ValueSource(longs = [1, 2])
    @DisplayName("주문 품목")
    fun orderItem(id: Long) {
        mockMvc.perform(get("/api/v1/edge/orders/{id}/item", id))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "주문 품목을 조회한다. 실행마다 섹션이 따로 생긴다."
                pathParameters("id" pathMeans "주문 ID")
                responseFields<OrderItem>()
            }
    }

    @Test
    @DisplayName("장바구니 품목")
    fun cartItem() {
        mockMvc.perform(get("/api/v1/edge/carts/{id}/item", 7L))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "장바구니 품목을 조회한다. 주문 품목과 클래스 이름이 같지만 스키마 이름이 다르다."
                pathParameters("id" pathMeans "장바구니 ID")
                responseFields<CartItem>()
            }
    }

    @Test
    @DisplayName("계정 만들기")
    fun overloaded() {
        // DTO 를 직렬화하면 응답 전용 createdAt 도 나가므로, 클라이언트가 보내는 JSON 문자열을 그대로 쓴다 (비밀번호는 문서 예시용 가짜 값)
        mockMvc.perform(post("/api/v1/edge/accounts").jsonContent("""{"username":"hong","password":"p@ssw0rd"}"""))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "요청에는 비밀번호, 응답에는 가입 시각이 있다."
                requestFields<Account>()
                responseFields<Account>()
            }
    }

    @Test
    @DisplayName("계정 만들기 (TestInfo 오버로드)")
    fun overloaded(info: TestInfo) {
        val username = info.testMethod.map { it.name }
            .orElse("kim")

        mockMvc.perform(post("/api/v1/edge/accounts").jsonContent("""{"username":"$username","password":"pw"}"""))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "이름이 같은 테스트 메서드도 스니펫이 겹치지 않는다."
                requestFields<Account>()
                responseFields<Account>()
            }
    }

    @Test
    @DisplayName("글 목록 (Spring Data Page)")
    fun articles() {
        mockMvc.perform(
            get("/api/v1/edge/articles").param("page", "0")
                .param("size", "2")
                .param("sort", "id,desc"),
        ).andStatus(HttpStatus.OK)
            .andDocument {
                description = "Spring Data `Page` 를 그대로 돌려준다."
                queryParameters<Pageable>()
                responseFields<Page<Article>>()
            }
    }

    @Nested
    @DisplayName("파일")
    inner class Files {
        @Test
        @DisplayName("점이 든 파일 이름")
        fun dottedName() {
            mockMvc.perform(get("/api/v1/edge/files/{name:.+}", "report.final.pdf"))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "경로 변수에 정규식(`{name:.+}`)을 쓴다."
                    pathParameters("name" pathMeans "파일 이름 (점 포함)")
                    responseFields<FileInfo>()
                }
        }
    }
}
