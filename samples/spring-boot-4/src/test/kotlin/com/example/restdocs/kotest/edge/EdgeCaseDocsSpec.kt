package com.example.restdocs.kotest.edge

import com.example.restdocs.edge.Account
import com.example.restdocs.edge.Article
import com.example.restdocs.edge.FileInfo
import com.example.restdocs.kotest.support.MirrorDocsConfig
import io.github.kardx.dsl.pathMeans
import io.github.kardx.extractor.queryParameters
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.context.WebApplicationContext
import com.example.restdocs.edge.cart.Item as CartItem
import com.example.restdocs.edge.order.Item as OrderItem

/**
 * [com.example.restdocs.edge.EdgeCaseDocsTest] 를 Kotest 로 옮긴 스펙.
 *
 * - `@ParameterizedTest(name = "주문 {0}")` → `withTests` 데이터 테스트. 행 이름을 `주문 품목 주문 1` 로 지어 JUnit 섹션 제목과 같다
 * - `@Nested` → `context("파일") { test(...) }`. 섹션 제목 `파일 - 점이 든 파일 이름` 이 JUnit 과 같다
 * - 오버로드한 테스트 메서드는 Kotest 에 없다. 이름이 다른 테스트 둘로 두고, `TestInfo` 대신 `testCase` 로 테스트 이름을 읽는다
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("엣지 케이스 API")
class EdgeCaseDocsSpec(context: WebApplicationContext) :
    FunSpec({
        val restDocs = extension(MirrorDocsConfig.restDocsExtension())
        val mockMvc = restDocsMockMvc(context, restDocs.provider)

        beforeEach { MirrorDocsConfig.apply() }

        test("글 목록 (Spring Data Page)") {
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

        test("장바구니 품목") {
            mockMvc.perform(get("/api/v1/edge/carts/{id}/item", 7L))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "장바구니 품목을 조회한다. 주문 품목과 클래스 이름이 같지만 스키마 이름이 다르다."
                    pathParameters("id" pathMeans "장바구니 ID")
                    responseFields<CartItem>()
                }
        }

        withTests(nameFn = { id -> "주문 품목 주문 $id" }, 1L, 2L) { id ->
            mockMvc.perform(get("/api/v1/edge/orders/{id}/item", id))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "주문 품목을 조회한다. 실행마다 섹션이 따로 생긴다."
                    pathParameters("id" pathMeans "주문 ID")
                    responseFields<OrderItem>()
                }
        }

        test("계정 만들기") {
            // DTO 를 직렬화하면 응답 전용 createdAt 도 나가므로, 클라이언트가 보내는 JSON 문자열을 그대로 쓴다 (비밀번호는 문서 예시용 가짜 값)
            mockMvc.perform(post("/api/v1/edge/accounts").jsonContent("""{"username":"hong","password":"p@ssw0rd"}"""))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "요청에는 비밀번호, 응답에는 가입 시각이 있다."
                    requestFields<Account>()
                    responseFields<Account>()
                }
        }

        test("계정 만들기 (TestInfo 오버로드)") {
            val username = testCase.name.name

            mockMvc.perform(post("/api/v1/edge/accounts").jsonContent("""{"username":"$username","password":"pw"}"""))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "이름이 같은 테스트 메서드도 스니펫이 겹치지 않는다."
                    requestFields<Account>()
                    responseFields<Account>()
                }
        }

        context("파일") {
            test("점이 든 파일 이름") {
                mockMvc.perform(get("/api/v1/edge/files/{name:.+}", "report.final.pdf"))
                    .andStatus(HttpStatus.OK)
                    .andDocument {
                        description = "경로 변수에 정규식(`{name:.+}`)을 쓴다."
                        pathParameters("name" pathMeans "파일 이름 (점 포함)")
                        responseFields<FileInfo>()
                    }
            }
        }
    })
