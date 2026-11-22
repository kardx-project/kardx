package io.github.kardx.internal.openapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class OpenApiPathTemplateTest :
    BehaviorSpec({
        given("정규식 경로 변수가 있는 URL 템플릿") {
            then("정규식을 빼고 변수 이름만 남긴다") {
                OpenApiPathTemplate.withoutPatterns("/files/{name:.+}") shouldBe "/files/{name}"
                OpenApiPathTemplate.withoutPatterns("/api/v{version:\\d+}/items/{id}") shouldBe
                    "/api/v{version}/items/{id}"
            }

            then("정규식 안의 중괄호도 짝을 맞춰 건너뛴다") {
                OpenApiPathTemplate.withoutPatterns("/codes/{code:[A-Z]{2}\\d{1,3}}/x") shouldBe "/codes/{code}/x"
            }
        }

        given("정규식이 없는 템플릿") {
            then("그대로 둔다") {
                OpenApiPathTemplate.withoutPatterns("/users/{id}/orders") shouldBe "/users/{id}/orders"
                OpenApiPathTemplate.withoutPatterns("/broken/{id") shouldBe "/broken/{id"
            }
        }

        given("쿼리가 붙은 템플릿 (WebTestClient 의 uri(\"/users/{id}?verbose={verbose}\"))") {
            then("OpenAPI 경로와 api-summary 에서 쿼리를 뗀다") {
                OpenApiPathTemplate.withoutPatterns("/users/{id:\\d+}?verbose={verbose}") shouldBe "/users/{id}"
                OpenApiPathTemplate.withoutQuery("/users/{id}?verbose={verbose}") shouldBe "/users/{id}"
            }

            then("경로 변수 정규식 안의 ? 는 쿼리로 보지 않는다") {
                OpenApiPathTemplate.withoutQuery("/files/{name:.+?}/raw") shouldBe "/files/{name:.+?}/raw"
                OpenApiPathTemplate.withoutQuery("/broken/{id") shouldBe "/broken/{id"
            }
        }
    })
