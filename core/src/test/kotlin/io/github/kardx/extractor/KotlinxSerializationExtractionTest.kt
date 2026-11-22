package io.github.kardx.extractor

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.introspection.PolymorphicTypes
import io.github.kardx.mockmvc.andDocument
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.serializer
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import kotlin.reflect.typeOf

@Serializable
enum class KxRole {
    @SerialName("member")
    MEMBER,

    @SerialName("admin")
    ADMIN,
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class KxUser(
    @SerialName("user_id") val id: Long,
    val name: String,
    val nickname: String? = null,
    val tags: List<String> = emptyList(),
    @EncodeDefault val role: KxRole = KxRole.MEMBER,
    @Transient val secret: String = "",
) {
    /** kotlinx 는 백킹 필드가 없는 프로퍼티를 쓰지 않는다 (Jackson 은 getter 로 쓴다) */
    val displayName: String get() = nickname ?: name
}

@Serializable
sealed interface KxPayment {
    @Serializable
    @SerialName("card")
    data class Card(val number: String) : KxPayment

    @Serializable
    @SerialName("bank")
    data class Bank(val account: String, val bank: String) : KxPayment
}

@Serializable
data class KxOrder(val id: Long, val payment: KxPayment)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed class KxShape {
    @Serializable
    @SerialName("circle")
    data class Circle(val radius: Double) : KxShape()

    @Serializable
    data object Empty : KxShape()
}

/** `"1000 KRW"` 같은 문자열로 직렬화되는 값. */
@Serializable(with = KxMoneySerializer::class)
data class KxMoney(val amount: Long, val currency: String)

object KxMoneySerializer : KSerializer<KxMoney> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("KxMoney", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: KxMoney) = encoder.encodeString("${value.amount} ${value.currency}")

    override fun deserialize(decoder: Decoder): KxMoney = decoder.decodeString()
        .split(" ")
        .let { KxMoney(it[0].toLong(), it[1]) }
}

@Serializable
data class KxPrice(val price: KxMoney, val history: List<KxMoney>)

@Serializable
data class KxPage<T>(val items: List<T>, val total: Long = 0)

/** kotlinx 가 쓴 JSON 문자열을 그대로 응답하는 컨트롤러. */
@RestController
class JsonTextController(private val json: String) {
    @GetMapping("/payload")
    fun payload(): ResponseEntity<String> = ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
}

/** [value] 를 kotlinx `Json` 기본 설정으로 직렬화해 응답하고, [fields] 로 REST Docs 응답 필드 검증을 돌린다. */
private inline fun <reified T> verifyKotlinx(value: T, noinline fields: () -> List<RestDocsField>) {
    val output = Files.createTempDirectory("kotlinx-contract").toFile()
    val documentation = ManualRestDocumentation(output.absolutePath)
    val json = Json.encodeToString(serializer(typeOf<T>()), value)
    val mockMvc = MockMvcBuilders.standaloneSetup(JsonTextController(json))
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()

    documentation.beforeTest(JsonTextController::class.java, "payload")

    try {
        mockMvc.perform(get("/payload")).andDocument { responseFields(fields()) }
    } finally {
        documentation.afterTest()
    }
}

private fun paths(fields: List<RestDocsField>) = fields.map { it.descriptor.path }

private fun field(fields: List<RestDocsField>, path: String) = fields.first { it.descriptor.path == path }

private fun enumValues(fields: List<RestDocsField>, path: String) =
    field(fields, path).descriptor.attributes[ENUM_VALUES_ATTRIBUTE]

class KotlinxSerializationExtractionTest :
    BehaviorSpec({
        given("@Serializable 클래스") {
            then("@SerialName 이름을 쓰고 @Transient·백킹 필드 없는 프로퍼티는 뺀다") {
                val expected = listOf("user_id", "name", "nickname", "tags", "role")

                paths(responseFieldsOf<KxUser>()) shouldContainExactly expected
            }

            then("기본값이 있는 프로퍼티는 응답에서도 optional 이다 (encodeDefaults = false). @EncodeDefault 는 필수다") {
                val fields = responseFieldsOf<KxUser>()

                field(fields, "tags").descriptor.isOptional shouldBe true
                field(fields, "nickname").descriptor.isOptional shouldBe true
                field(fields, "role").descriptor.isOptional shouldBe false
                field(fields, "user_id").descriptor.isOptional shouldBe false
            }

            then("요청에서는 기본값이 있는 프로퍼티가 모두 optional 이다") {
                val fields = requestFieldsOf<KxUser>()

                paths(fields) shouldContainExactly listOf("user_id", "name", "nickname", "tags", "role")
                field(fields, "role").descriptor.isOptional shouldBe true
            }

            then("Enum 값은 상수의 @SerialName 이다") {
                enumValues(responseFieldsOf<KxUser>(), "role") shouldBe listOf("member", "admin")
            }

            then("실제 kotlinx 직렬화 결과와 맞는다") {
                verifyKotlinx(KxUser(1, "evan")) { responseFieldsOf<KxUser>() }
                verifyKotlinx(KxUser(1, "evan", "e", listOf("a"), KxRole.ADMIN)) { responseFieldsOf<KxUser>() }
            }

            then("제네릭 클래스도 타입 인자로 펼친다") {
                paths(responseFieldsOf<KxPage<KxUser>>()) shouldContainExactly listOf(
                    "items",
                    "items[].user_id",
                    "items[].name",
                    "items[].nickname",
                    "items[].tags",
                    "items[].role",
                    "total",
                )
                verifyKotlinx(KxPage(listOf(KxUser(1, "evan")))) { responseFieldsOf<KxPage<KxUser>>() }
            }
        }

        given("직렬화기가 값 하나로 쓰는 클래스") {
            then("프로퍼티를 펼치지 않고 descriptor 의 타입(문자열)으로 둔다") {
                val fields = responseFieldsOf<KxPrice>()

                paths(fields) shouldContainExactly listOf("price", "history")
                field(fields, "price").descriptor.type shouldBe JsonFieldType.STRING
                val price = KxPrice(KxMoney(1000, "KRW"), listOf(KxMoney(900, "KRW")))

                verifyKotlinx(price) { responseFieldsOf<KxPrice>() }
            }
        }

        given("sealed 타입") {
            then("판별자 type 에 하위 타입의 @SerialName 을 값으로 붙인다") {
                val fields = responseFieldsOf<KxOrder>()

                paths(fields) shouldContainExactly listOf(
                    "id",
                    "payment",
                    "payment.type",
                    "payment.account",
                    "payment.bank",
                    "payment.number",
                )
                enumValues(fields, "payment.type") shouldBe listOf("bank", "card")
                verifyKotlinx(KxOrder(1, KxPayment.Card("1234"))) { responseFieldsOf<KxOrder>() }
                verifyKotlinx(KxOrder(2, KxPayment.Bank("1", "kb"))) { responseFieldsOf<KxOrder>() }
            }

            then("@JsonClassDiscriminator 로 판별자 이름을 바꾸고, 이름이 없는 하위 타입은 전체 이름이다") {
                val fields = responseFieldsOf<KxShape>()

                paths(fields) shouldContainExactly listOf("kind", "radius")
                enumValues(fields, "kind") shouldBe listOf("circle", KxShape.Empty::class.qualifiedName)
                verifyKotlinx<KxShape>(KxShape.Circle(1.0)) { responseFieldsOf<KxShape>() }
                verifyKotlinx<KxShape>(KxShape.Empty) { responseFieldsOf<KxShape>() }
            }

            then("OpenAPI oneOf 판별자도 kotlinx 규칙이다") {
                val discriminator = PolymorphicTypes.of(KxPayment::class)?.discriminator.shouldNotBeNull()

                discriminator.property shouldBe "type"
                discriminator.names.values shouldContainExactlyInAnyOrder listOf("card", "bank")
                discriminator.existing shouldBe false
            }
        }

        given("extraction.kotlinxSerialization = false") {
            then("@Serializable 클래스도 Jackson 규칙으로 추출한다") {
                RestDocsConfiguration.withSettings({ extraction.kotlinxSerialization = false }) {
                    val expected = listOf("id", "name", "nickname", "tags", "role", "secret", "displayName")

                    paths(responseFieldsOf<KxUser>()) shouldContainExactly expected
                }
            }
        }
    })
