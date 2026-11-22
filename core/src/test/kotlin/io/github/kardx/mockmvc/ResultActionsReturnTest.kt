package io.github.kardx.mockmvc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** Jackson Kotlin 모듈 없이 역직렬화되도록 기본값·var 로 둔다 */
data class ReturnedItem(var id: Long = 0, var name: String = "")

@RestController
class ReturnController {
    @GetMapping("/items/1")
    fun item(): ResponseEntity<ReturnedItem> = ResponseEntity.status(HttpStatus.CREATED)
        .location(URI.create("/items/1"))
        .header("X-Trace", "a", "b")
        .body(ReturnedItem(1, "홍길동"))

    @GetMapping("/items")
    fun items(): List<ReturnedItem> = listOf(ReturnedItem(1, "홍길동"), ReturnedItem(2, "김철수"))

    @DeleteMapping("/items/1")
    fun remove(): ResponseEntity<Unit> = ResponseEntity.noContent()
        .build()

    /** charset 없는 JSON 문자열. 원문 그대로 받아야 한다 */
    @GetMapping("/raw", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun raw(): String = RAW_JSON

    /** 공백뿐인 본문. JSON 으로 해석하지 않고 빈 본문으로 본다 */
    @GetMapping("/blank", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun blank(): String = BLANK_BODY

    /** JSON 이 아닌 바이너리 본문. ByteArray 로 받으면 그대로여야 한다 */
    @GetMapping("/bytes", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun bytes(): ByteArray = BINARY
}

private val BINARY = byteArrayOf(0, 1, 2, -1, 0x7b)

private const val RAW_JSON = """{"name":"홍길동"}"""

private const val BLANK_BODY = "  \n"

/** `andReturnContent` / `andReturnAsObject` / `andReturnEntity` 가 같은 규칙(UTF-8, 같은 매퍼)으로 응답을 돌려주는지. */
class ResultActionsReturnTest :
    BehaviorSpec({
        val mockMvc = MockMvcBuilders.standaloneSetup(ReturnController())
            .build()

        given("andReturnEntity") {
            `when`("본문·헤더가 있는 응답이면") {
                val entity = mockMvc.perform(get("/items/1"))
                    .andReturnEntity<ReturnedItem>()

                then("상태 코드, 모든 헤더 값, 역직렬화한 본문을 담는다") {
                    entity.statusCode shouldBe HttpStatus.CREATED
                    entity.headers.location shouldBe URI.create("/items/1")
                    entity.headers["X-Trace"] shouldBe listOf("a", "b")
                    entity.body shouldBe ReturnedItem(1, "홍길동")
                }
            }

            `when`("제네릭 본문이면") {
                then("원소 타입을 유지한다") {
                    val entity = mockMvc.perform(get("/items"))
                        .andReturnEntity<List<ReturnedItem>>()

                    entity.body shouldBe listOf(ReturnedItem(1, "홍길동"), ReturnedItem(2, "김철수"))
                }
            }

            `when`("본문이 없는 204 응답이면") {
                then("body 는 null 이다") {
                    val entity = mockMvc.perform(delete("/items/1"))
                        .andReturnEntity<ReturnedItem>()

                    entity.statusCode shouldBe HttpStatus.NO_CONTENT
                    entity.body.shouldBeNull()
                }
            }

            `when`("T 가 String 이면") {
                then("역직렬화하지 않고 UTF-8 원문 그대로 담는다") {
                    val entity = mockMvc.perform(get("/raw"))
                        .andReturnEntity<String>()

                    entity.body shouldBe RAW_JSON
                }
            }

            `when`("본문이 공백뿐이면") {
                then("JSON 으로 해석하지 않고 body 는 null 이다") {
                    mockMvc.perform(get("/blank"))
                        .andReturnEntity<ReturnedItem>()
                        .body
                        .shouldBeNull()
                }
            }

            `when`("T 가 ByteArray 면") {
                then("JSON 해석 없이 응답 바이트 그대로 담는다") {
                    mockMvc.perform(get("/bytes"))
                        .andReturnEntity<ByteArray>()
                        .body shouldBe BINARY
                }
            }
        }

        given("andReturnAsObject") {
            then("역직렬화한 객체를 돌려준다 (제네릭 유지)") {
                mockMvc.perform(get("/items"))
                    .andReturnAsObject<List<ReturnedItem>>() shouldBe
                    listOf(ReturnedItem(1, "홍길동"), ReturnedItem(2, "김철수"))
            }

            then("T 가 String 이면 원문, ByteArray 면 응답 바이트 그대로다") {
                mockMvc.perform(get("/raw"))
                    .andReturnAsObject<String>() shouldBe RAW_JSON
                mockMvc.perform(get("/bytes"))
                    .andReturnAsObject<ByteArray>() shouldBe BINARY
            }

            then("본문이 비었거나 공백뿐이면 andReturnEntity 를 쓰라고 알려 준다") {
                listOf(delete("/items/1"), get("/blank")).forEach { request ->
                    shouldThrow<IllegalStateException> {
                        mockMvc.perform(request)
                            .andReturnAsObject<ReturnedItem>()
                    }.message shouldContain "andReturnEntity"
                }
            }
        }
    })
