package io.github.kardx.extractor

import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.ITEMS_TYPE_ATTRIBUTE
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.openapi.OpenApiDescriptors
import io.github.kardx.support.verifyResponseFields
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.SubsectionDescriptor

object VersionMarker {
    // Jackson 이 getter 로 내보내는 프로퍼티 (const 는 정적 필드라 나가지 않는다)
    val version: Int get() = 1
}

object EmptyMarker

data class Marked(val marker: VersionMarker, val empty: EmptyMarker)

interface Pet {
    val name: String
}

data class Dog(override val name: String, val age: Int) : Pet

abstract class Shape2 {
    abstract val sides: Int
}

class Triangle : Shape2() {
    override val sides: Int = 3
    val color: String = "red"
}

data class Owner(val pet: Pet, val pets: List<Pet>, val shape: Shape2)

data class Untyped(val anything: Any, val list: List<Any>, val maybe: Any?)

enum class Level { LOW, HIGH }

data class Levels(val levels: List<Level>, val level: Level)

data class Tag(val id: Long)

data class SparseTags(val tags: List<Tag?>, val matrix: List<List<Tag?>>)

private fun List<RestDocsField>.byPath() = associateBy { it.descriptor.path }

class OpenAndUntypedShapesTest :
    BehaviorSpec({
        given("프로퍼티가 있는 Kotlin object") {
            then("하위 필드를 펼치고, 프로퍼티 없는 object 는 빈 객체 한 줄이다") {
                responseFieldsOf<Marked>().map { it.descriptor.path } shouldContainExactly
                    listOf("marker", "marker.version", "empty")
                verifyResponseFields(Marked(VersionMarker, EmptyMarker)) { responseFieldsOf<Marked>() }
            }
        }

        given("인터페이스·추상 클래스 타입 필드") {
            val fields = responseFieldsOf<Owner>().byPath()

            then("subsection 으로 두고 선언 타입의 프로퍼티는 하위 필드로 함께 문서화한다") {
                fields.keys.toList() shouldContainExactly
                    listOf("pet", "pet.name", "pets", "pets[].name", "shape", "shape.sides")
                fields.getValue("pet").descriptor.shouldBeInstanceOf<SubsectionDescriptor>()
                fields.getValue("pets").descriptor.shouldBeInstanceOf<SubsectionDescriptor>()
                fields.getValue("pet.name").descriptor.shouldNotBeInstanceOf<SubsectionDescriptor>()
            }

            then("구현체에만 있는 필드가 응답에 있어도 통과한다") {
                verifyResponseFields(Owner(Dog("멍", 3), listOf(Dog("왈", 1)), Triangle())) { responseFieldsOf<Owner>() }
            }
        }

        given("경로를 준 최상위 인터페이스 타입") {
            then("객체·배열 모두 subsection 이라 구현체 필드가 있어도 통과한다") {
                val pets = responseFieldsOf<List<Pet>>("data").associateBy { it.descriptor.path }
                val pet = responseFieldsOf<Pet>("data").associateBy { it.descriptor.path }

                pets.getValue("data").descriptor.shouldBeInstanceOf<SubsectionDescriptor>()
                pet.keys.toList() shouldContainExactly listOf("data", "data.name")
                pet.getValue("data").descriptor.shouldBeInstanceOf<SubsectionDescriptor>()
                verifyResponseFields(mapOf("data" to listOf(Dog("멍", 3)))) { responseFieldsOf<List<Pet>>("data") }
            }
        }

        given("Any 타입 필드") {
            val fields = responseFieldsOf<Untyped>().byPath()

            then("구조를 알 수 없으므로 VARIES subsection 이다 (배열이면 배열 자체가 subsection)") {
                fields.keys.toList() shouldContainExactly listOf("anything", "list", "maybe")
                fields.getValue("anything").descriptor.shouldBeInstanceOf<SubsectionDescriptor>()
                fields.getValue("anything").descriptor.type shouldBe JsonFieldType.VARIES
                fields.getValue("list").descriptor.shouldBeInstanceOf<SubsectionDescriptor>()
            }

            then("객체 값과 객체 원소가 있어도 통과한다") {
                val payload = Untyped(mapOf("k" to mapOf("n" to 1)), listOf(mapOf("three" to 3), "s"), null)

                verifyResponseFields(payload) { responseFieldsOf<Untyped>() }
            }
        }

        given("Enum 목록 필드") {
            val fields = responseFieldsOf<Levels>().byPath()

            then("타입은 배열 그대로이고 원소의 값 목록을 itemsType=enum 과 함께 넣는다") {
                val levels = fields.getValue("levels").descriptor

                levels.type shouldBe JsonFieldType.ARRAY
                levels.attributes[ITEMS_TYPE_ATTRIBUTE] shouldBe "enum"
                levels.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe listOf("LOW", "HIGH")
                fields.getValue("level").descriptor.type shouldBe JsonFieldType.STRING
                OpenApiDescriptors.field(fields.getValue("level").descriptor).type shouldBe "enum"
            }

            then("실제 배열 응답과 맞는다") {
                verifyResponseFields(Levels(listOf(Level.HIGH), Level.LOW)) { responseFieldsOf<Levels>() }
            }
        }

        given("nullable 객체 원소의 배열에 실제 null 이 있을 때") {
            then("원소 경로를 ignored 로 더해 통과하고, 표에는 나가지 않는다") {
                val fields = responseFieldsOf<SparseTags>().byPath()

                fields.getValue("tags[]").descriptor.isIgnored shouldBe true
                fields.getValue("matrix[][]").descriptor.isIgnored shouldBe true
                verifyResponseFields(SparseTags(listOf(Tag(1), null), listOf(listOf(null, Tag(2))))) {
                    responseFieldsOf<SparseTags>()
                }
            }
        }

        given("Java record 의 null 가능 여부") {
            then("어노테이션 없는 참조 타입은 optional, 원시 타입과 @NonNull 은 필수다") {
                val fields = responseFieldsOf<PlainJavaRecord>().byPath()

                fields.mapValues { it.value.descriptor.isOptional } shouldBe
                    mapOf("id" to true, "memo" to true, "count" to false, "name" to false)
                verifyResponseFields(PlainJavaRecord(1L, null, 2L, "n")) { responseFieldsOf<PlainJavaRecord>() }
            }

            then("@NullMarked 범위에서는 @Nullable 만 optional 이다") {
                val fields = responseFieldsOf<MarkedJavaRecord>().byPath()

                fields.mapValues { it.value.descriptor.isOptional } shouldBe mapOf("id" to false, "memo" to true)
            }
        }
    })
