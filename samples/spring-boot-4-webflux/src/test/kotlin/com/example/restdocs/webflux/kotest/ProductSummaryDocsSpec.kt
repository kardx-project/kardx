package com.example.restdocs.webflux.kotest

import com.example.restdocs.webflux.product.ProductSummary
import io.github.kardx.annotation.Description
import io.github.kardx.extractor.pathParameters
import io.github.kardx.extractor.responseFields
import io.github.kardx.kotest.RestDocsExtension
import io.github.kardx.webtestclient.andDocument
import io.github.kardx.webtestclient.restDocsWebTestClient
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

/** 경로 변수. `pathParameters<ProductPath>()` 가 프로퍼티 이름(`id`)과 설명으로 경로 파라미터 표를 만든다. */
private class ProductPath(@Description("상품 ID") val id: Long)

/**
 * Kotest + WebTestClient 로 문서화하는 예. JUnit 테스트(RestDocsTestSupport)와 같은 test 태스크에서 돌고, 문서는 index·HTML·
 * OpenAPI 에 나란히 들어간다.
 *
 * - Kotest Spring 확장이 생성자에 컨텍스트를 넣고, yml(`application-docs.yml`)의 `kardx.*` 은 확장이 읽는다
 * - WebTestClient 도 MockMvc 와 같이 `restDocsWebTestClient(context, restDocs.provider)` 로 만들고 `andDocument { }` 로 문서화한다.
 *   경로 파라미터는 `pathParameters<T>()` 로 타입에서 추출한다
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("상품 요약 API (Kotest)")
class ProductSummaryDocsSpec(context: ApplicationContext) :
    FunSpec({
        val restDocs = extension(RestDocsExtension())
        val webTestClient = restDocsWebTestClient(context, restDocs.provider)

        context("상품 요약") {
            test("상품 하나의 이름·가격·상태를 돌려준다") {
                webTestClient.get()
                    .uri("/api/v1/products/{id}/summary", 1L)
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("기계식 키보드")
                    .andDocument {
                        description = "상품 상세 대신 목록과 같은 요약을 돌려준다."
                        pathParameters<ProductPath>()
                        responseFields<ProductSummary>()
                    }
            }
        }
    })
