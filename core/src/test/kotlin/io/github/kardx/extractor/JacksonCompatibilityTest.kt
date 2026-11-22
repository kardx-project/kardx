package io.github.kardx.extractor

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.RestDocsField
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.SubsectionDescriptor
import java.math.BigDecimal
import kotlin.reflect.KClass

// ---- fixtures: Jackson 이 실제로 직렬화하는 모양과 추출 결과가 같아야 한다 ----

data class WithNode(val id: Long, val payload: JsonNode)

data class Login(
    val username: String,
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY) val password: String,
    @get:JsonProperty(access = JsonProperty.Access.READ_ONLY) val createdAt: String,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(JsonSubTypes.Type(CardPay::class, name = "CARD"), JsonSubTypes.Type(BankPay::class, name = "BANK"))
sealed interface Pay

data class CardPay(val cardNumber: String) : Pay

data class BankPay(val bankCode: String) : Pay

data class PayDocument(val id: Long, val pay: Pay)

data class PostalAddress(val street: String, val zip: String)

data class Person(
    val id: Long,
    @get:JsonUnwrapped val address: PostalAddress,
    @get:JsonUnwrapped(prefix = "home_") val home: PostalAddress,
)

@JsonIgnoreProperties("secret")
data class Account(val id: Long, val secret: String)

enum class Code(@get:JsonValue val code: String) { A("a-code"), B("b-code") }

enum class Tier {
    @JsonProperty("gold")
    GOLD,
    SILVER,
}

data class Coded(val code: Code, val tier: Tier, val codes: List<Code>)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
open class SnakeBase(val baseName: String)

class SnakeChild(val childName: String) : SnakeBase("b")

data class Computed(val first: String, val last: String) {
    val full: String get() = "$first $last"
    var note: String = ""
}

data class NullableElements(val boxes: List<Box?>)

class Money(@get:JsonValue val amount: BigDecimal)

data class Priced(val price: Money)

data class Pairs(val pair: Pair<String, Int>)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Figure {
    data class Circle(val radius: Int) : Figure()

    data class Square(val side: Int) : Figure()
}

data class Drawing(val shape: Figure)

@JsonIgnoreProperties(value = ["password", "createdAt"], allowSetters = true)
data class SignUp(val email: String, val password: String, val createdAt: String)

class TypedId<T>(@get:JsonValue val value: T)

data class HasTypedId(val id: TypedId<Long>)

data class AddressSearch(@get:JsonUnwrapped val address: PostalAddress, val q: String)

private fun fields(k: KClass<*>): Map<String, RestDocsField> = responseFieldsOf(k)
    .associateBy { it.descriptor.path }

private fun List<RestDocsField>.paths() = map { it.descriptor.path }

@Suppress("UNCHECKED_CAST")
private fun RestDocsField.enumValues() = descriptor.attributes[ENUM_VALUES_ATTRIBUTE] as List<String>?

class JacksonCompatibilityTest :
    BehaviorSpec({
        given("JsonNode 필드") {
            then("구조를 펼치지 않고 VARIES subsection 한 줄이다") {
                val fields = fields(WithNode::class)

                fields.keys.toList() shouldContainExactly listOf("id", "payload")
                fields.getValue("payload").descriptor.shouldBeInstanceOf<SubsectionDescriptor>()
                fields.getValue("payload").descriptor.type shouldBe JsonFieldType.VARIES
            }
        }

        given("제네릭 Java record 와 bridge 메서드가 있는 Java bean") {
            then("record 의 타입 파라미터가 치환된다") {
                responseFieldsOf<GenericRecord<Box>>().paths() shouldContainExactly
                    listOf("data", "data.length", "data.width", "items", "items[].length", "items[].width")
            }

            then("bridge 메서드는 중복 필드를 만들지 않고, 축약어 getter 는 Jackson 2 규칙(url)으로 이름 짓는다") {
                val fields = fields(StringBean::class)

                fields.keys.toList() shouldContainExactly listOf("data", "url")
                fields.getValue("data").descriptor.type shouldBe JsonFieldType.STRING
            }
        }

        given("@JsonProperty(access)") {
            then("응답에서는 WRITE_ONLY 가, 요청에서는 READ_ONLY 가 빠진다") {
                responseFieldsOf<Login>().paths() shouldContainExactly listOf("username", "createdAt")
                requestFieldsOf<Login>().paths() shouldContainExactly listOf("username", "password")
            }
        }

        given("@JsonTypeInfo 가 붙은 sealed 타입") {
            then("판별자 필드가 하위 타입 이름 목록과 함께 들어간다") {
                val fields = fields(PayDocument::class)

                fields.keys.toList() shouldContainExactlyInAnyOrder
                    listOf("id", "pay", "pay.type", "pay.cardNumber", "pay.bankCode")
                fields.keys.toList().take(3) shouldContainExactly listOf("id", "pay", "pay.type")
                fields.getValue("pay.type").enumValues() shouldBe listOf("CARD", "BANK")
                fields.getValue("pay.type").descriptor.isOptional shouldBe false
                fields.getValue("pay.cardNumber").descriptor.isOptional shouldBe true
            }
        }

        given("@JsonUnwrapped 와 @JsonIgnoreProperties") {
            then("펼친 필드는 부모 수준에 prefix 를 붙여 들어가고, 무시한 프로퍼티는 빠진다") {
                responseFieldsOf<Person>().paths() shouldContainExactly
                    listOf("id", "street", "zip", "home_street", "home_zip")
                responseFieldsOf<Account>().paths() shouldContainExactly listOf("id")
            }
        }

        given("Enum 의 @JsonValue 와 상수 @JsonProperty") {
            val fields = fields(Coded::class)

            then("값 목록과 설명에 JSON 값을 쓴다") {
                fields.getValue("code").enumValues() shouldBe listOf("a-code", "b-code")
                fields.getValue("code").descriptor.description.toString() shouldContain "a-code"
                fields.getValue("tier").enumValues() shouldBe listOf("gold", "SILVER")
            }

            then("Enum 배열도 값 목록을 가진다") {
                fields.getValue("codes").enumValues() shouldBe listOf("a-code", "b-code")
                responseFieldsOf<List<Code>>("codes")
                    .single()
                    .enumValues() shouldBe listOf("a-code", "b-code")
            }
        }

        given("상위 클래스의 @JsonNaming") {
            then("하위 클래스 프로퍼티에도 적용된다") {
                responseFieldsOf<SnakeChild>().paths() shouldContainExactlyInAnyOrder listOf("child_name", "base_name")
            }
        }

        given("생성자 밖 프로퍼티") {
            then("응답에는 모두 있고, 요청에는 읽기 전용 val 이 빠지며 var 는 optional 이다") {
                responseFieldsOf<Computed>().paths() shouldContainExactly listOf("first", "last", "note", "full")

                val request =
                    requestFieldsOf<Computed>()
                        .associateBy { it.descriptor.path }

                request.keys.toList() shouldContainExactly listOf("first", "last", "note")
                request.getValue("note").descriptor.isOptional shouldBe true
                request.getValue("first").descriptor.isOptional shouldBe false
            }
        }

        given("재점검에서 찾은 경우") {
            then("use = NAME 의 기본 판별자 값은 Jackson 처럼 Outer\$Inner 다") {
                fields(Drawing::class)
                    .getValue("shape.@type")
                    .enumValues() shouldContainExactlyInAnyOrder listOf("Figure\$Circle", "Figure\$Square")
            }

            then("@JsonIgnoreProperties(allowSetters) 프로퍼티는 요청에는 남고 응답에서는 빠진다") {
                requestFieldsOf<SignUp>().paths() shouldContainExactly listOf("email", "password", "createdAt")
                responseFieldsOf<SignUp>().paths() shouldContainExactly listOf("email")
            }

            then("제네릭 @JsonValue 클래스는 타입 인자로 풀린다") {
                val id = fields(HasTypedId::class).getValue("id")

                id.descriptor.type shouldBe JsonFieldType.NUMBER
                id.descriptor.isOptional shouldBe false
            }

            then("@JsonUnwrapped 는 파라미터 추출에는 적용하지 않는다") {
                parametersOf<AddressSearch>()
                    .map { it.descriptor.name } shouldContainExactly listOf("address.street", "address.zip", "q")
            }
        }

        given("nullable 원소, @JsonValue 클래스, Pair") {
            then("nullable 원소의 하위 필드는 optional 이다") {
                fields(NullableElements::class).getValue("boxes[].length").descriptor.isOptional shouldBe true
            }

            then("@JsonValue 클래스는 그 값의 타입으로 문서화된다") {
                val price = fields(Priced::class)

                price.keys.toList() shouldContainExactly listOf("price")
                price.getValue("price").descriptor.type shouldBe JsonFieldType.NUMBER
            }

            then("Pair 는 first/second 객체다") {
                responseFieldsOf<Pairs>().paths() shouldContainExactly listOf("pair", "pair.first", "pair.second")
            }
        }
    })
