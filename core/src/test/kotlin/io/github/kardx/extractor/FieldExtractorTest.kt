package io.github.kardx.extractor
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.kardx.annotation.Description
import io.github.kardx.config.DescriptionResolver
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.introspection.FieldExtractor
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.SubsectionDescriptor
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.reflect.KClass

// ---- fixtures ----

data class Box(@Description("길이") val length: Int, @Description("폭") val width: Int)

data class Leaves(
    @Description("가격") val price: BigDecimal,
    @Description("식별자") val uid: UUID,
    @Description("날짜들") val dates: List<LocalDate>,
    @Description("태그") val tags: List<String>,
    @Description("박스") val boxes: List<Box>,
    @Description("메모") @field:NotBlank val memo: String?,
)

data class Node(@Description("이름") val name: String, @Description("자식") val children: List<Node>)

data class Parent(val name: String, val parent: Parent?)

data class WithMap(val config: Map<String, String>, val nested: List<Map<String, Int>>)

data class Address(val street: String, val zip: String)

data class NullableNested(val address: Address?, val tags: List<String>?, val boxes: List<Box>?)

data class Matrix(val cells: List<List<Box>>)

@JvmInline
value class UserId(val value: Long)

data class WithValueClass(val id: UserId, val maybe: UserId?)

data class JsonNames(
    @param:JsonProperty("user_name") val userName: String,
    @field:JsonIgnore val secret: String,
    val first: String,
) {
    @Suppress("unused")
    private val hidden: String = "h"
    val fullName: String get() = "$userName $first"
}

data class Order(val zeta: Int, val alpha: Int, val mid: Int)

sealed interface Payment {
    val amount: Long
}

data class Card(override val amount: Long, val cardNo: String) : Payment

data class Bank(override val amount: Long, val account: String) : Payment

data class WithSealed(val payment: Payment)

object Marker

data class WithObject(val marker: Marker, val name: String)

data class Page<T>(val content: List<T>, val total: Long, val first: T?)

data class WithOptional(val nickname: Optional<String>)

/** private 프로퍼티라도 설명으로 읽는지 보는 픽스처. `description` 은 리플렉션으로만 읽힌다. */
enum class Grade(private val description: String) { GOLD("골드"), SILVER("실버") }

data class WithEnum(@Description("등급") val grade: Grade)

/** 설명이 필드 없이 getter(`getDescription()`)로만 있는 Enum. 계산 프로퍼티라 `description` 필드가 생기지 않는다. */
enum class Membership(private val months: Int) {
    MONTHLY(1),
    YEARLY(12),
    ;

    val description: String
        get() = "${months}개월"
}

data class WithMembership(@Description("멤버십") val membership: Membership)

data class Constrained(
    @field:Size(min = 2, max = 10, message = "{min}~{max}자") val nickname: String,
    @field:NotBlank @field:Size(max = 5) val code: String,
)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class ParamOnlyDoc(val text: String)

data class ParamAnnotated(@ParamOnlyDoc("생성자 파라미터 설명") val name: String)

interface HasId {
    val id: Long
}

open class Base(@Description("이름") @field:NotBlank open val name: String)

class Child(override val id: Long, name: String, @Description("자식 설명") val extra: String) :
    Base(name),
    HasId

data class Recursive(val self: Recursive?)

data class WithDefaults(val size: Int = 20, val keyword: String)

data class BeanHolder(val bean: JavaBean, val point: JavaPoint)

// ---- helpers ----

private fun List<FieldDescriptor>.paths() = map { it.path }
private fun List<FieldDescriptor>.byPath() = associateBy { it.path }
private fun response(kClass: KClass<*>, prefix: String = "") = responseFieldsOf(kClass, prefix)
    .map { it.descriptor }

class FieldExtractorTest :
    BehaviorSpec({
        given("리프 타입과 컬렉션") {
            val fields = response(Leaves::class).byPath()

            then("JDK 리프 타입은 하위 필드 없이 선언 순서대로 문서화된다") {
                response(Leaves::class).paths() shouldContainExactly listOf(
                    "price",
                    "uid",
                    "dates",
                    "tags",
                    "boxes",
                    "boxes[].length",
                    "boxes[].width",
                    "memo",
                )

                fields.getValue("price").type shouldBe JsonFieldType.NUMBER
                fields.getValue("uid").type shouldBe JsonFieldType.STRING
            }

            then("nullable 에 제약조건이 있으면 optional + 기본 제약 설명") {
                val memo = fields.getValue("memo")

                memo.isOptional shouldBe true
                (memo.attributes["constraints"] as String) shouldBe "NotBlank - Must not be blank"
            }

            then("요청 본문도 같은 경로 규칙을 쓴다") {
                requestFieldsOf(Leaves::class)
                    .map { it.descriptor.path } shouldBe response(Leaves::class).paths()
            }
        }

        given("자기참조 DTO (C1)") {
            then("순환 지점은 subsection 으로 끊긴다") {
                val node = response(Node::class)

                node.paths() shouldContainExactly listOf("name", "children")
                (node.byPath().getValue("children") is SubsectionDescriptor) shouldBe true

                val parent = response(Parent::class).byPath()

                parent.keys shouldBe setOf("name", "parent")
                (parent.getValue("parent") is SubsectionDescriptor) shouldBe true
                parent.getValue("parent").isOptional shouldBe true
            }
        }

        given("Map 필드 (C2)") {
            then("Map 은 subsection 한 줄이고 내부 멤버를 노출하지 않는다") {
                val fields = response(WithMap::class)

                fields.paths() shouldContainExactly listOf("config", "nested")
                (fields.byPath().getValue("nested") is SubsectionDescriptor) shouldBe true
                fields.all { it.path.contains("entries").not() && it.path.contains("size").not() } shouldBe true
                (fields.byPath().getValue("config") is SubsectionDescriptor) shouldBe true
            }
        }

        given("nullable 객체와 배열 (C3)") {
            val fields = response(NullableNested::class).byPath()

            then("부모 디스크립터가 생기고 optional 이 하위로 전파된다") {
                fields.keys.toList() shouldContainExactly listOf(
                    "address",
                    "address.street",
                    "address.zip",
                    "tags",
                    "boxes",
                    "boxes[].length",
                    "boxes[].width",
                )

                fields.values.all { it.isOptional } shouldBe true
                fields.getValue("address").type shouldBe JsonFieldType.OBJECT
            }
        }

        given("중첩 배열 (C4)") {
            then("원소 경로에 [] 가 단계마다 붙는다") {
                response(Matrix::class).paths() shouldContainExactly
                    listOf("cells", "cells[][].length", "cells[][].width")
            }

            then("최상위 배열 응답은 [] 접두사로 문서화한다") {
                response(Box::class, "[]").paths() shouldContainExactly listOf("[].length", "[].width")
            }
        }

        given("value class 와 Optional (H1)") {
            then("내부 값 하나로 문서화된다") {
                val fields = response(WithValueClass::class).byPath()

                fields.keys shouldBe setOf("id", "maybe")
                fields.getValue("id").type shouldBe JsonFieldType.NUMBER
                fields.getValue("maybe").isOptional shouldBe true

                val optional = response(WithOptional::class).single()

                optional.path shouldBe "nickname"
                optional.type shouldBe JsonFieldType.STRING
                optional.isOptional shouldBe true
            }
        }

        given("Java record 와 Java bean (H2)") {
            then("record 는 component 순서대로, bean 은 getter 로 추출된다") {
                response(JavaPoint::class).paths() shouldContainExactly listOf("x", "y", "label", "tags")
                response(JavaBean::class).paths() shouldContainExactly listOf("name", "active", "count")
                response(BeanHolder::class).paths() shouldContainExactly listOf(
                    "bean", "bean.name", "bean.active", "bean.count",
                    "point", "point.x", "point.y", "point.label", "point.tags",
                )
            }
        }

        given("Jackson 어노테이션과 가시성 (H3)") {
            then("private·@JsonIgnore 는 빠지고 @JsonProperty 이름을 쓴다") {
                response(JsonNames::class).paths() shouldContainExactly listOf("user_name", "first", "fullName")
            }
        }

        given("선언 순서 (H4)") {
            then("주 생성자 순서를 따른다") {
                response(Order::class).paths() shouldContainExactly listOf("zeta", "alpha", "mid")
            }
        }

        given("sealed, object, 제네릭 (M6)") {
            then("sealed 하위 타입 필드를 합치고 일부에만 있는 필드는 optional") {
                val fields = response(WithSealed::class).byPath()

                fields.keys shouldBe setOf("payment", "payment.amount", "payment.cardNo", "payment.account")
                fields.getValue("payment.amount").isOptional shouldBe false
                fields.getValue("payment.cardNo").isOptional shouldBe true
            }

            then("Kotlin object 는 OBJECT 한 줄") {
                val fields = response(WithObject::class).byPath()

                fields.keys shouldBe setOf("marker", "name")
                fields.getValue("marker").type shouldBe JsonFieldType.OBJECT
            }

            then("제네릭 타입 인자가 치환된다") {
                val fields =
                    responseFieldsOf<Page<Box>>()
                        .map { it.descriptor }

                fields.paths() shouldContainExactly listOf(
                    "content",
                    "content[].length",
                    "content[].width",
                    "total",
                    "first",
                    "first.length",
                    "first.width",
                )
            }

            then("타입 인자가 없으면 VARIES") {
                response(Page::class)
                    .byPath()
                    .getValue("first")
                    .type shouldBe JsonFieldType.VARIES
            }
        }

        given("enum 설명 (M5)") {
            then("private description 필드도 읽어 상수 목록을 붙인다") {
                val description = response(WithEnum::class).single().description as String

                description shouldContain "등급"
                description shouldContain "GOLD : 골드"
                description shouldContain "SILVER : 실버"
            }

            then("getter 로만 노출된 설명도 상수 목록에 붙는다") {
                val description = response(WithMembership::class).single().description as String

                description shouldContain "MONTHLY : 1개월"
                description shouldContain "YEARLY : 12개월"
            }
        }

        given("제약조건 메시지 (M3)") {
            val fields = response(Constrained::class).byPath()

            then("message 플레이스홀더를 치환한다") {
                fields.getValue("nickname").attributes["constraints"] shouldBe "Size - 2~10자"
            }

            then("어노테이션마다 자기 설명만 붙고 null 이 섞이지 않는다") {
                val text = fields.getValue("code").attributes["constraints"] as String

                text shouldContain "NotBlank - Must not be blank"
                text shouldContain "Size - Size must be between 0 and 5 inclusive"
                text shouldNotContain "null"
            }
        }

        given("생성자 파라미터에만 붙는 어노테이션 (L5)") {
            then("descriptions.resolver 가 찾을 수 있다") {
                RestDocsConfiguration.withSettings({
                    descriptions.resolver = DescriptionResolver { it.findAnnotation<ParamOnlyDoc>()?.text }
                }) {
                    response(ParamAnnotated::class).single().description shouldBe "생성자 파라미터 설명"
                }
            }
        }

        given("상속 (M4)") {
            then("슈퍼클래스 설명과 제약을 유지한다") {
                val params =
                    parametersOf(Child::class)
                        .associateBy { it.descriptor.name }

                params.keys shouldBe setOf("id", "name", "extra")
                params.getValue("name").descriptor.description shouldBe "이름"
                (params.getValue("name").descriptor.attributes["constraints"] as String) shouldContain "NotBlank"
            }
        }

        given("최대 깊이") {
            then("설정한 깊이를 넘으면 subsection 으로 끊긴다") {
                val address = RestDocsConfiguration.withSettings({ extraction.maxDepth = 1 }) {
                    response(NullableNested::class)
                        .byPath()
                        .getValue("address")
                }

                (address is SubsectionDescriptor) shouldBe true
            }
        }

        given("경로 이스케이프") {
            then("점이 들어간 이름은 대괄호로 감싼다") {
                FieldExtractor.joinPath("", "a.b") shouldBe "['a.b']"
                FieldExtractor.joinPath("root", "a.b") shouldBe "root['a.b']"
                FieldExtractor.joinPath("root", "c") shouldBe "root.c"
            }
        }

        given("기본값이 있는 생성자 파라미터") {
            then("요청과 파라미터에서는 optional, 응답에서는 필수") {
                requestFieldsOf(WithDefaults::class)
                    .associate { it.descriptor.path to it.descriptor.isOptional } shouldBe
                    mapOf("size" to true, "keyword" to false)
                parametersOf(WithDefaults::class)
                    .associate { it.descriptor.name to it.descriptor.isOptional } shouldBe
                    mapOf("size" to true, "keyword" to false)
                response(WithDefaults::class)
                    .associate { it.path to it.isOptional } shouldBe mapOf("size" to false, "keyword" to false)
            }
        }

        given("파라미터 추출") {
            then("객체는 a.b 로 펼치고 배열은 이름 하나") {
                parametersOf(NullableNested::class)
                    .map { it.descriptor.name } shouldContainExactly
                    listOf("address.street", "address.zip", "tags", "boxes")
            }
        }
    })
