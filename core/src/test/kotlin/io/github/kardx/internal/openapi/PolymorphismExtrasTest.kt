package io.github.kardx.internal.openapi

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import io.github.kardx.config.ResponseWrapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.type
import io.github.kardx.extractor.requestFieldsOf
import io.github.kardx.extractor.responseFields
import io.github.kardx.extractor.responseFieldsOf
import io.github.kardx.extractor.wrappedResponseFieldsOf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = CardPayment::class, name = "CARD"),
    JsonSubTypes.Type(value = BankTransfer::class, name = "BANK"),
)
sealed interface Payment

data class CardPayment(val cardNumber: String, val installments: Int) : Payment

data class BankTransfer(val bank: String, val account: Account) : Payment

data class Account(val number: String, val holder: String?)

data class Order(val id: Long, val payment: Payment, val history: List<Payment>)

/** `@JsonTypeInfo` 없는 sealed 클래스. 판별자 없이 하위 타입 중 하나다 */
sealed class Shape

data class Circle(val radius: Double) : Shape()

data class Square(val side: Double) : Shape()

/** sealed 가 아닌 Jackson 다형 타입. 판별자는 하위 타입에 이미 있는 프로퍼티다 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
@JsonSubTypes(JsonSubTypes.Type(DogPet::class), JsonSubTypes.Type(CatPet::class))
abstract class Pet {
    abstract val kind: String
}

@JsonTypeName("dog")
class DogPet(override val kind: String, val barks: Boolean) : Pet()

@JsonTypeName("cat")
class CatPet(override val kind: String, val lives: Int) : Pet()

data class Adoption(val pet: Pet)

/** 공통 응답 래퍼 */
data class PolyEnvelope<T>(val code: Int, val data: T?)

data class CartItem(val name: String, val method: Payment)

data class Cart(val items: List<CartItem>)

@RestController
class PolymorphicController {
    @GetMapping("/orders/1")
    fun order(): Order = Order(
        1,
        CardPayment("1234", 3),
        listOf(BankTransfer("KB", Account("001", null))),
    )

    @PostMapping("/shapes")
    fun shape(@RequestBody shape: Map<String, Any>): Map<String, Any> = shape

    @PostMapping("/adoptions")
    fun adopt(@RequestBody adoption: Map<String, Any>): Map<String, Any> = adoption
}

class PolymorphismExtrasTest :
    BehaviorSpec({
        val docs = DocumentedExtras(PolymorphicController())

        given("@JsonTypeInfo 가 붙은 sealed 인터페이스 프로퍼티와 그 배열") {
            val json = docs.run("order", get("/orders/1")) { responseFields<Order>() }
            val entries = json["responseBodyPolymorphism"].objects()

            then("프로퍼티와 배열 원소마다 항목이 생긴다") {
                entries.map { it["path"] } shouldContainExactly listOf("payment", "history[]")
                entries.map { it["baseName"] }.distinct() shouldBe listOf("Payment")
            }

            then("판별자 매핑은 판별자 값 → 하위 타입 이름이다") {
                entries[0]["discriminator"] shouldBe mapOf(
                    "property" to "type",
                    "mapping" to mapOf("CARD" to "CardPayment", "BANK" to "BankTransfer"),
                )
            }

            then("하위 타입 스키마는 판별자 프로퍼티를 앞에 두고 중첩 객체까지 펼친다") {
                val variants = entries[0]["variants"].objects()
                val card = variants[0]["schema"].obj()
                val bank = variants[1]["schema"].obj()
                val account = bank["properties"].obj()["account"].obj()

                variants.map { it["name"] } shouldContainExactly listOf("CardPayment", "BankTransfer")
                card["properties"].obj().keys.toList() shouldContainExactly listOf("type", "cardNumber", "installments")
                card["properties"].obj()["type"] shouldBe mapOf("type" to "string", "enum" to listOf("CARD"))
                card["required"] shouldBe listOf("type", "cardNumber", "installments")
                account["properties"].obj().keys.toList() shouldContainExactly listOf("number", "holder")
            }

            then("요청 본문 항목은 없다") {
                json.containsKey("requestBodyPolymorphism") shouldBe false
            }
        }

        given("판별자 없는 sealed 클래스가 요청 본문 자체") {
            val json = docs.run(
                "shape",
                post("/shapes").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"radius":1.5}"""),
            ) { requestFields(requestFieldsOf<Shape>()) }
            val entry = json["requestBodyPolymorphism"].objects()
                .single()

            then("경로는 빈 문자열이고 판별자는 null 이다") {
                entry["path"] shouldBe ""
                entry["discriminator"] shouldBe null
                entry["variants"].objects().map { it["name"] } shouldContainExactly listOf("Circle", "Square")
            }
        }

        given("sealed 가 아닌 @JsonSubTypes 추상 클래스와 EXISTING_PROPERTY 판별자") {
            val json = docs.run(
                "adoption",
                post("/adoptions").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"pet":{"kind":"dog","barks":true}}"""),
            ) { requestFields(requestFieldsOf<Adoption>()) }
            val entry = json["requestBodyPolymorphism"].objects()
                .single()

            then("@JsonTypeName 값으로 매핑하고 하위 타입의 프로퍼티를 그대로 둔다") {
                entry["path"] shouldBe "pet"
                entry["discriminator"].obj()["mapping"] shouldBe mapOf("dog" to "DogPet", "cat" to "CatPet")
                entry["variants"].objects()[0]["schema"].obj()["properties"].obj().keys.toList() shouldContainExactly
                    listOf("kind", "barks")
            }
        }

        given("다형 프로퍼티를 목록에서 뺀 필드") {
            val fields = responseFieldsOf<Order>().filterNot { it.descriptor.path.startsWith("payment") }
            val entries = BodyPolymorphism.of(fields, request = false)

            then("남은 경로의 항목만 남긴다") {
                entries.map { it["path"] } shouldContainExactly listOf("history[]")
            }
        }

        given("경로를 붙여 추출한 요청 필드") {
            val json = docs.run(
                "prefixed",
                post("/adoptions").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"data":{"pet":{"kind":"dog","barks":true}}}"""),
            ) { requestFields(requestFieldsOf<Adoption>("data")) }
            val entry = json["requestBodyPolymorphism"].objects()
                .single()

            then("경로 앞에 추출할 때 붙인 경로가 붙는다") {
                entry["path"] shouldBe "data.pet"
                entry["baseName"] shouldBe "Pet"
            }
        }

        given("공통 응답 래퍼로 감싼 응답 필드") {
            val wrapper = ResponseWrapper(PolyEnvelope::class)

            then("데이터 안의 다형 프로퍼티는 data 아래 경로다") {
                val entries = RestDocsConfiguration.withSettings({ extraction.responseWrapper = wrapper }) {
                    BodyPolymorphism.of(wrappedResponseFieldsOf<Order>(), request = false)
                }

                entries.map { it["path"] } shouldContainExactly listOf("data.payment", "data.history[]")
            }

            then("데이터 자체가 다형 타입이면 경로는 data 다") {
                val entries = RestDocsConfiguration.withSettings({ extraction.responseWrapper = wrapper }) {
                    BodyPolymorphism.of(wrappedResponseFieldsOf<Payment>(), request = false)
                }

                entries.map { it["path"] } shouldContainExactly listOf("data")
            }

            then("데이터 안 배열 원소의 다형 프로퍼티도 찾는다") {
                val entries = RestDocsConfiguration.withSettings({ extraction.responseWrapper = wrapper }) {
                    BodyPolymorphism.of(wrappedResponseFieldsOf<Cart>(), request = false)
                }

                entries.map { it["path"] } shouldContainExactly listOf("data.items[].method")
            }

            then("데이터가 배열이면 원소 경로다") {
                val entries = RestDocsConfiguration.withSettings({ extraction.responseWrapper = wrapper }) {
                    BodyPolymorphism.of(wrappedResponseFieldsOf<List<Payment>>(), request = false)
                }

                entries.map { it["path"] } shouldContainExactly listOf("data[]")
            }
        }

        given("여러 목록을 합친 본문") {
            val fields = requestFieldsOf<Adoption>("adoption") +
                requestFieldsOf<Order>("order").filterNot { it.descriptor.path.startsWith("order.history") } +
                ("memo" type STRING means "메모")
            val entries = BodyPolymorphism.of(fields, request = true)

            then("목록마다 추출한 타입과 경로로 찾는다") {
                entries.map { it["path"] } shouldContainExactly listOf("adoption.pet", "order.payment")
                entries.map { it["baseName"] } shouldContainExactly listOf("Pet", "Payment")
            }
        }

        given("직접 쓴 필드만 있는 본문") {
            val fields = listOf("payment" type OBJECT means "결제", "payment.type" type STRING means "종류")

            then("타입을 몰라 항목이 없다") {
                BodyPolymorphism.of(fields, request = true) shouldBe emptyList()
            }
        }
    })
