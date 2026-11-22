package io.github.kardx.mockmvc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatusController {
    @GetMapping("/created")
    fun created(): ResponseEntity<String> = ResponseEntity.status(HttpStatus.CREATED)
        .body("created")
}

/** `andStatus` 가 `andExpect(status().is(...))` 와 같게 검증하고 같은 [org.springframework.test.web.servlet.ResultActions] 를 돌려주는지. */
class ResultActionsStatusTest :
    BehaviorSpec({
        val mockMvc = MockMvcBuilders.standaloneSetup(StatusController())
            .build()

        given("andStatus") {
            `when`("상태 코드가 같으면") {
                then("숫자로 검증한다") {
                    mockMvc.perform(get("/created"))
                        .andStatus(201)
                        .andReturnContent() shouldBe "created"
                }

                then("HttpStatus 상수로 검증한다") {
                    mockMvc.perform(get("/created"))
                        .andStatus(HttpStatus.CREATED)
                        .andReturnContent() shouldBe "created"
                }

                then("HttpStatusCode 로 검증한다") {
                    mockMvc.perform(get("/created"))
                        .andStatus(HttpStatusCode.valueOf(201))
                        .andReturnContent() shouldBe "created"
                }

                then("같은 ResultActions 를 돌려줘 이어 쓸 수 있다") {
                    val actions = mockMvc.perform(get("/created"))

                    actions.andStatus(201) shouldBeSameInstanceAs actions
                    actions.andStatus(HttpStatus.CREATED) shouldBeSameInstanceAs actions
                }
            }

            `when`("상태 코드가 다르면") {
                then("숫자는 Spring 의 기대값·실제값 메시지로 실패한다") {
                    val error = shouldThrow<AssertionError> {
                        mockMvc.perform(get("/created"))
                            .andStatus(404)
                    }

                    error.message shouldContain "expected:<404> but was:<201>"
                }

                then("HttpStatus 상수도 같은 메시지로 실패한다") {
                    val error = shouldThrow<AssertionError> {
                        mockMvc.perform(get("/created"))
                            .andStatus(HttpStatus.OK)
                    }

                    error.message shouldContain "expected:<200> but was:<201>"
                }
            }
        }
    })
