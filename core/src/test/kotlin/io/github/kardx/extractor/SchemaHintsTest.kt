package io.github.kardx.extractor

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue
import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.CONSTRAINTS_ATTRIBUTE
import io.github.kardx.dsl.CONSTRAINT_NOTE_ATTRIBUTE
import io.github.kardx.dsl.ITEMS_TYPE_ATTRIBUTE
import io.github.kardx.dsl.NULLABLE_ATTRIBUTE
import io.github.kardx.dsl.NULLABLE_ITEMS_ATTRIBUTE
import io.github.kardx.dsl.REQUIRED_BY_CONSTRAINT_ATTRIBUTE
import io.github.kardx.dsl.REQUIRED_IN_PARENT_ATTRIBUTE
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.SCHEMA_ATTRIBUTE
import io.github.kardx.support.verifyResponseFields
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.restdocs.payload.JsonFieldType
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

enum class Priority { LOW, HIGH }

enum class NumericCode(@get:JsonValue val code: Int) { A(1), B(2) }

interface OnCreate

data class Scalars(
    val count: Int,
    val total: Long,
    val big: BigInteger,
    val ratio: Double,
    val price: BigDecimal,
    val flag: Boolean,
    val at: Instant,
    val offset: OffsetDateTime,
    val local: LocalDateTime,
    val day: LocalDate,
    val time: LocalTime,
    val id: UUID,
    val link: URI,
    val raw: ByteArray,
    val ttl: Duration,
    val code: NumericCode,
)

data class Values(
    val names: List<String>,
    val ids: Set<Long>,
    val levels: List<Priority>,
    val numbers: IntArray,
    val counts: Map<String, Int>,
)

data class Shaped(
    @get:JsonFormat(shape = JsonFormat.Shape.NUMBER) val at: Instant,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val price: BigDecimal,
    @get:JsonFormat(pattern = "yyyy/MM/dd") val day: LocalDate,
)

data class ConstrainedRequest(
    @field:NotBlank @field:Size(max = 50) val name: String,
    @field:Min(1) @field:Max(100) val quantity: Int,
    @field:DecimalMin("0.5", inclusive = false) val rate: BigDecimal,
    @field:Positive val score: Long,
    @field:Pattern(regexp = "^[A-Z]+$") val code: String,
    @field:Email val email: String,
    @field:NotEmpty @field:Size(max = 5) val tags: List<String>,
    @field:Past val birthday: LocalDate,
    val labels: List<
        @NotBlank
        @Size(max = 5)
        String,
        >,
)

data class NullableRequired(
    @field:NotNull val required: String?,
    @field:NotNull(groups = [OnCreate::class]) val onCreate: String?,
    val nested: Inner?,
)

data class Inner(@field:NotBlank val name: String?)

data class OrderLine(val id: Long, val note: String?)

data class OptionalParents(
    val tags: List<String> = emptyList(),
    val memo: String? = null,
    val child: OrderLine? = null,
    val lines: List<OrderLine> = emptyList(),
    val holes: List<OrderLine?> = emptyList(),
)

data class Levelled(val level: Priority, val code: NumericCode, val levels: List<Priority>)

private fun List<RestDocsField>.schemaOf(path: String): Map<*, *>? =
    single { it.descriptor.path == path }.descriptor.attributes[SCHEMA_ATTRIBUTE] as? Map<*, *>

private fun List<RestDocsField>.attribute(path: String, key: String): Any? =
    single { it.descriptor.path == path }.descriptor.attributes[key]

class SchemaHintsTest :
    BehaviorSpec({
        given("값 타입") {
            val fields = responseFieldsOf<Scalars>()

            then("정수·실수·날짜·식별자 타입을 OpenAPI type·format 으로 남긴다") {
                fields.schemaOf("count") shouldBe mapOf("type" to "integer", "format" to "int32")
                fields.schemaOf("total") shouldBe mapOf("type" to "integer", "format" to "int64")
                fields.schemaOf("big") shouldBe mapOf("type" to "integer")
                fields.schemaOf("ratio") shouldBe mapOf("type" to "number", "format" to "double")
                fields.schemaOf("price") shouldBe mapOf("type" to "number")
                fields.schemaOf("flag") shouldBe mapOf("type" to "boolean")
                fields.schemaOf("at") shouldBe mapOf("type" to "string", "format" to "date-time")
                fields.schemaOf("offset") shouldBe mapOf("type" to "string", "format" to "date-time")
                fields.schemaOf("local") shouldBe mapOf("type" to "string", "format" to "date-time")
                fields.schemaOf("day") shouldBe mapOf("type" to "string", "format" to "date")
                fields.schemaOf("time") shouldBe mapOf("type" to "string", "format" to "time")
                fields.schemaOf("id") shouldBe mapOf("type" to "string", "format" to "uuid")
                fields.schemaOf("link") shouldBe mapOf("type" to "string", "format" to "uri")
                fields.schemaOf("raw") shouldBe mapOf("type" to "string", "format" to "byte")
                fields.schemaOf("ttl") shouldBe mapOf("type" to "string", "format" to "duration")
            }

            then("숫자 @JsonValue Enum 은 숫자로 검증하고 integer 로 남긴다") {
                fields.single { it.descriptor.path == "code" }.descriptor.type shouldBe JsonFieldType.NUMBER
                fields.schemaOf("code") shouldBe mapOf("type" to "integer")
            }
        }

        given("값 배열과 값 맵") {
            val fields = responseFieldsOf<Values>()

            then("원소 타입을 items 로 남긴다 (Enum 은 items.enum)") {
                fields.schemaOf("names") shouldBe mapOf("type" to "array", "items" to mapOf("type" to "string"))
                fields.schemaOf("ids") shouldBe
                    mapOf("type" to "array", "items" to mapOf("type" to "integer", "format" to "int64"))
                fields.schemaOf("levels") shouldBe mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string", "enum" to listOf("LOW", "HIGH")),
                )

                fields.schemaOf("numbers") shouldBe
                    mapOf("type" to "array", "items" to mapOf("type" to "integer", "format" to "int32"))
                fields.schemaOf("counts") shouldBe mapOf(
                    "type" to "object",
                    "additionalProperties" to mapOf("type" to "integer", "format" to "int32"),
                )
            }

            then("epages 만 써도 items 가 oneOf 로 뭉개지지 않게 itemsType 을 넣는다") {
                fields.attribute("names", ITEMS_TYPE_ATTRIBUTE) shouldBe "string"
                fields.attribute("ids", ITEMS_TYPE_ATTRIBUTE) shouldBe "number"
                fields.attribute("levels", ITEMS_TYPE_ATTRIBUTE) shouldBe "enum"
            }
        }

        given("@JsonFormat") {
            val fields = responseFieldsOf<Shaped>()

            then("shape 는 JSON 타입을, pattern 은 표준 format 을 뺀다") {
                fields.schemaOf("at") shouldBe mapOf("type" to "number")
                fields.schemaOf("price") shouldBe mapOf("type" to "string")
                fields.schemaOf("day") shouldBe mapOf("type" to "string")
            }
        }

        given("Bean Validation 제약") {
            val fields = requestFieldsOf<ConstrainedRequest>()

            then("길이·범위·패턴·형식을 스키마 키워드로 옮긴다") {
                fields.schemaOf("name") shouldBe mapOf("type" to "string", "minLength" to 1, "maxLength" to 50)
                fields.schemaOf("quantity") shouldBe
                    mapOf("type" to "integer", "format" to "int32", "minimum" to 1L, "maximum" to 100L)
                fields.schemaOf("rate") shouldBe
                    mapOf("type" to "number", "minimum" to BigDecimal("0.5"), "exclusiveMinimum" to true)
                fields.schemaOf("score") shouldBe
                    mapOf("type" to "integer", "format" to "int64", "minimum" to 0, "exclusiveMinimum" to true)
                fields.schemaOf("code") shouldBe mapOf("type" to "string", "pattern" to "^[A-Z]+$")
                fields.schemaOf("email") shouldBe mapOf("type" to "string", "format" to "email")
                fields.schemaOf("tags") shouldBe
                    mapOf("type" to "array", "items" to mapOf("type" to "string"), "minItems" to 1, "maxItems" to 5)
            }

            then("키워드로 옮길 수 없는 제약은 설명으로 남긴다") {
                fields.schemaOf("birthday") shouldBe mapOf("type" to "string", "format" to "date")
                fields.attribute("birthday", CONSTRAINT_NOTE_ATTRIBUTE).toString() shouldContain "Past"
                fields.attribute("name", CONSTRAINT_NOTE_ATTRIBUTE) shouldBe null
            }

            then("원소 제약은 items 키워드와 표의 `원소:` 문구가 된다") {
                fields.schemaOf("labels") shouldBe mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 5),
                )

                fields.attribute("labels", CONSTRAINTS_ATTRIBUTE).toString() shouldContain "Items: NotBlank"
            }

            then("language 가 ko 면 원소 제약 문구는 `원소:` 다") {
                val korean = RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    requestFieldsOf<ConstrainedRequest>()
                }

                korean.attribute("labels", CONSTRAINTS_ATTRIBUTE).toString() shouldContain "원소: NotBlank"
            }
        }

        given("nullable 프로퍼티의 @NotNull") {
            val fields = requestFieldsOf<NullableRequired>()

            then("REST Docs 검증은 optional 로 두고 표·스키마에는 required 로 표시한다") {
                val required = fields.single { it.descriptor.path == "required" }.descriptor

                required.isOptional shouldBe true
                required.attributes[REQUIRED_BY_CONSTRAINT_ATTRIBUTE] shouldBe true
            }

            then("검증 그룹이 있는 제약은 그룹 이름을 붙이고 required 에 넣지 않는다") {
                fields.attribute("onCreate", CONSTRAINTS_ATTRIBUTE).toString() shouldContain "NotNull [OnCreate]"
                fields.attribute("onCreate", REQUIRED_BY_CONSTRAINT_ATTRIBUTE) shouldBe null
                fields.attribute("onCreate", CONSTRAINT_NOTE_ATTRIBUTE).toString() shouldContain "NotNull [OnCreate]"
            }

            then("부모가 null 일 수 있어 optional 인 하위 필드는 표에 표시하지 않고 스키마에만 부모 안의 required 로 남긴다") {
                fields.attribute("nested.name", REQUIRED_BY_CONSTRAINT_ATTRIBUTE) shouldBe null
                fields.attribute("nested.name", REQUIRED_IN_PARENT_ATTRIBUTE) shouldBe true
                fields.attribute("nested.name", NULLABLE_ATTRIBUTE) shouldBe false
                fields.schemaOf("nested.name") shouldBe mapOf("type" to "string", "minLength" to 1)
            }
        }

        given("optional 부모의 하위 필드와 기본값이 있는 요청 프로퍼티") {
            val fields = requestFieldsOf<OptionalParents>()

            then("non-null 하위 필드는 부모 안에서 required, nullable 하위 필드는 그대로 optional 이다") {
                fields.attribute("child.id", REQUIRED_IN_PARENT_ATTRIBUTE) shouldBe true
                fields.attribute("child.note", REQUIRED_IN_PARENT_ATTRIBUTE) shouldBe null
                fields.attribute("child.note", NULLABLE_ATTRIBUTE) shouldBe true
                fields.attribute("lines[].id", REQUIRED_IN_PARENT_ATTRIBUTE) shouldBe true
            }

            then("nullable 원소(`List<OrderLine?>`)의 배열 줄에 원소가 null 일 수 있다고 남긴다") {
                fields.attribute("holes", NULLABLE_ITEMS_ATTRIBUTE) shouldBe true
                fields.attribute("lines", NULLABLE_ITEMS_ATTRIBUTE) shouldBe null
            }

            then("optional 필드에는 값이 null 일 수 있는지 남긴다 (기본값이 있는 non-null 은 false)") {
                fields.attribute("tags", NULLABLE_ATTRIBUTE) shouldBe false
                fields.attribute("tags", REQUIRED_IN_PARENT_ATTRIBUTE) shouldBe null
                fields.attribute("memo", NULLABLE_ATTRIBUTE) shouldBe true
                fields.attribute("child", NULLABLE_ATTRIBUTE) shouldBe true
                responseFieldsOf<OptionalParents>().attribute("tags", NULLABLE_ATTRIBUTE) shouldBe null
            }

            then("extraction.arrayElementFieldsOptional 로 풀어 둔 원소 필드는 부모 안에서도 optional 이다") {
                val loose = RestDocsConfiguration.withSettings({ extraction.arrayElementFieldsOptional = true }) {
                    requestFieldsOf<OptionalParents>()
                }

                loose.attribute("lines[].id", REQUIRED_IN_PARENT_ATTRIBUTE) shouldBe null
            }
        }

        given("파라미터 추출") {
            val parameters = parametersOf<ConstrainedRequest>()

            then("파라미터에도 같은 스키마 조각을 넣는다") {
                parameters.single { it.descriptor.name == "quantity" }
                    .descriptor
                    .attributes[SCHEMA_ATTRIBUTE] shouldBe
                    mapOf("type" to "integer", "format" to "int32", "minimum" to 1L, "maximum" to 100L)
            }
        }

        given("Enum 필드") {
            then("JSON 값 타입(문자열·숫자)으로 검증해 실제 응답과 맞는다") {
                verifyResponseFields(Levelled(Priority.HIGH, NumericCode.B, listOf(Priority.LOW))) {
                    responseFieldsOf<Levelled>()
                }
            }
        }
    })
