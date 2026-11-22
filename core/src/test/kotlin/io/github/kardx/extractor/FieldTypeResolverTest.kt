package io.github.kardx.extractor

import io.github.kardx.dsl.ARRAY
import io.github.kardx.dsl.BOOLEAN
import io.github.kardx.dsl.DocsFieldType
import io.github.kardx.dsl.ENUM
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.VARIES
import io.github.kardx.internal.introspection.Shape
import io.github.kardx.internal.introspection.docsFieldTypeOf
import io.github.kardx.internal.introspection.shapeOf
import io.github.kardx.internal.introspection.unwrap
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

private enum class Color { RED, BLUE }

private data class Nested(val id: Long)

private class PlainClass(val value: String)

/** 프로퍼티 타입만 읽는 픽스처. equals 는 쓰지 않는다 (배열 프로퍼티가 있어도 상관없다). */
private data class Sample(
    val text: String,
    val char: Char,
    val int: Int,
    val short: Short,
    val byte: Byte,
    val decimal: BigDecimal,
    val bigInt: BigInteger,
    val flag: Boolean,
    val color: Color,
    val list: List<String>,
    val set: Set<Int>,
    val array: Array<String>,
    val intArray: IntArray,
    val map: Map<String, Any>,
    val any: Any,
    val nested: Nested,
    val nestedList: List<Nested>,
    val date: LocalDate,
    val dates: List<LocalDate>,
    val instant: Instant,
    val uuid: UUID,
    val uri: URI,
    val plain: PlainClass,
)

class FieldTypeResolverTest :
    BehaviorSpec({
        fun typeOf(name: String) = Sample::class.memberProperties.first { it.name == name }.returnType

        fun elementTypeOf(name: String) = requireNotNull(typeOf(name).arguments.first().type)

        given("Kotlin 프로퍼티 타입이 주어졌을 때") {
            val expected: Map<String, DocsFieldType> = mapOf(
                "text" to STRING,
                "char" to STRING,
                "int" to NUMBER,
                "short" to NUMBER,
                "byte" to NUMBER,
                "decimal" to NUMBER,
                "bigInt" to NUMBER,
                "flag" to BOOLEAN,
                "color" to ENUM,
                "list" to ARRAY,
                "set" to ARRAY,
                "array" to ARRAY,
                "intArray" to ARRAY,
                "map" to OBJECT,
                "any" to VARIES,
                "nested" to OBJECT,
                "nestedList" to ARRAY,
                "date" to STRING,
                "dates" to ARRAY,
                "instant" to STRING,
                "uuid" to STRING,
                "uri" to STRING,
                "plain" to OBJECT,
            )

            expected.forEach { (name, type) ->
                `when`("$name 프로퍼티를 변환하면") {
                    then("$type 으로 매핑된다") {
                        docsFieldTypeOf(typeOf(name)) shouldBe type
                    }
                }
            }
        }

        given("원시 타입과 래퍼 타입의 KClass") {
            then("같은 문서 타입이 된다 (클래스별 캐시는 래퍼 타입을 키로 쓴다)") {
                docsFieldTypeOf(Int::class) shouldBe NUMBER
                docsFieldTypeOf(Class.forName("java.lang.Integer").kotlin) shouldBe NUMBER
                docsFieldTypeOf(Integer.TYPE.kotlin) shouldBe NUMBER
                docsFieldTypeOf(Boolean::class) shouldBe BOOLEAN
                docsFieldTypeOf(java.lang.Boolean.TYPE.kotlin) shouldBe BOOLEAN
                docsFieldTypeOf(Char::class) shouldBe STRING
                docsFieldTypeOf(Color::class) shouldBe ENUM
            }
        }

        given("배열 원소 타입을 재귀 대상인지 판정할 때") {
            `when`("원소가 data class 이면") {
                then("재귀 대상이다") {
                    isNestedObjectType(elementTypeOf("nestedList")) shouldBe true
                }
            }

            `when`("원소가 String 이면") {
                then("재귀 대상이 아니다") {
                    isNestedObjectType(elementTypeOf("list")) shouldBe false
                }
            }

            `when`("원소가 java.time 타입이면") {
                then("재귀 대상이 아니다") {
                    isNestedObjectType(elementTypeOf("dates")) shouldBe false
                }
            }

            `when`("원소가 Any 이면") {
                then("재귀 대상이 아니다") {
                    isNestedObjectType(typeOf("any")) shouldBe false
                }
            }
        }
    })

/** 하위 필드를 재귀 추출하는 객체 타입인지. `Map`, Kotlin `object`, JDK 클래스, 리프 타입은 재귀 대상이 아니다. */
private fun isNestedObjectType(type: KType): Boolean = shapeOf(unwrap(type).type) is Shape.Nested
