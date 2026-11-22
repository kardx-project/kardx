package io.github.kardx.internal.openapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class OperationTextsController {
    @GetMapping("/texts")
    fun texts(): Map<String, Any> = mapOf("ok" to true)
}

class OperationTextsExtrasTest :
    BehaviorSpec({
        val docs = DocumentedExtras(OperationTextsController())

        given("openApi 블록에서 summary 와 description 을 정한 테스트") {
            val json = docs.run("declared", get("/texts")) {
                description = "블록 설명"
                relaxed = true
                openApi {
                    summary = "결제 요청"
                    description = "OpenAPI 전용 설명"
                }
            }

            then("extras 에 summary 와 description 이 남는다") {
                json["summary"] shouldBe "결제 요청"
                json["description"] shouldBe "OpenAPI 전용 설명"
            }
        }

        given("빈 값이거나 정하지 않은 테스트") {
            val json = docs.run("blank", get("/texts")) {
                description = "블록 설명"
                relaxed = true
                openApi { summary = " " }
            }

            then("키를 남기지 않는다") {
                json.containsKey("summary") shouldBe false
                json.containsKey("description") shouldBe false
            }
        }
    })
