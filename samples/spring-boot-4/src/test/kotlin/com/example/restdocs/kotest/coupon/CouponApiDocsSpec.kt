package com.example.restdocs.kotest.coupon

import com.example.restdocs.coupon.ApiField
import com.example.restdocs.coupon.CouponResponse
import com.example.restdocs.kotest.support.MirrorDocsConfig
import io.github.kardx.config.DescriptionResolver
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.orElse
import io.github.kardx.dsl.pathMeans
import io.github.kardx.extractor.wrappedResponseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andPrint
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.context.WebApplicationContext

/**
 * [com.example.restdocs.coupon.CouponApiDocsTest] 를 Kotest 로 옮긴 스펙. 설명 어노테이션을 `@ApiField` 먼저 읽고,
 * 없으면 바꾸기 전의 기본 함수(KDoc/Javadoc 포함)를 부르도록 바꾼다.
 * `beforeEach` 에서 한 설정은 테스트가 끝나면 확장이 되돌린다.
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("쿠폰 API")
class CouponApiDocsSpec(context: WebApplicationContext) :
    FunSpec({
        val restDocs = extension(MirrorDocsConfig.restDocsExtension())
        val mockMvc = restDocsMockMvc(context, restDocs.provider)

        beforeEach {
            MirrorDocsConfig.apply()
            RestDocsConfiguration.configure {
                descriptions.resolver =
                    DescriptionResolver { it.findAnnotation<ApiField>()?.value } orElse descriptions.resolver
            }
        }

        test("쿠폰 조회") {
            mockMvc.perform(get("/api/v1/coupons/{code}", "WELCOME15"))
                .andStatus(HttpStatus.OK)
                .andDocument {
                    description = "쿠폰 코드로 할인 정보를 조회한다."
                    pathParameters("code" pathMeans "쿠폰 코드")
                    wrappedResponseFields<CouponResponse>()
                }
                .andPrint()
        }
    })
