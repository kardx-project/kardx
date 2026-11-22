package io.github.kardx.extractor

import io.github.kardx.dsl.DEFAULT_VALUE_ATTRIBUTE
import io.github.kardx.dsl.RestDocsField
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import org.springframework.restdocs.payload.JsonFieldType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Java bean 상위 클래스의 getter 프로퍼티(`data`)를 물려받는 Kotlin 클래스 */
class KotlinOnJavaBase(val total: Int) : GenericBean<String>()

/** 타입 인자 없이 원소 타입을 정하는 컬렉션 하위 클래스 */
class BoxList : ArrayList<Box>()

data class BoxListHolder(val boxes: BoxList)

data class DatedSearch(val from: LocalDate, val at: Instant, val id: UUID, val codes: IntArray, val page: Int = 3)

data class CharsHolder(val chars: CharArray)

private fun List<RestDocsField>.paths() = map { it.descriptor.path }

private fun List<RestDocsField>.typeOf(path: String) = first { it.descriptor.path == path }.descriptor.type

class InheritedShapesTest :
    BehaviorSpec({
        given("상위 클래스와 같은 이름의 타입 파라미터를 다르게 넘기는 Java bean") {
            then("상속한 getter 의 T 는 상위 클래스의 바인딩(List<T>)으로 풀린다") {
                val fields = responseFieldsOf<ListPageBean<Box>>()

                fields.paths() shouldContainExactlyInAnyOrder listOf("data", "data[].length", "data[].width", "total")
                fields.typeOf("data") shouldBe JsonFieldType.ARRAY
            }
        }

        given("Java bean 을 상속한 Kotlin 클래스") {
            then("상위 Java 클래스의 getter 프로퍼티도 추출한다") {
                val fields = responseFieldsOf<KotlinOnJavaBase>()

                fields.paths() shouldContainExactlyInAnyOrder listOf("total", "data")
                fields.typeOf("data") shouldBe JsonFieldType.STRING
            }
        }

        given("타입 인자 없는 컬렉션 하위 클래스") {
            then("프로퍼티면 상위 타입에서 원소 타입을 찾아 펼친다") {
                responseFieldsOf<BoxListHolder>().paths() shouldContainExactly
                    listOf("boxes", "boxes[].length", "boxes[].width")
            }

            then("본문 전체여도 원소 필드를 펼친다") {
                responseFieldsOf<BoxList>().paths() shouldContainExactly listOf("[].length", "[].width")
            }
        }

        given("필수 파라미터가 java.time·UUID·원시 배열인 요청 DTO") {
            then("다른 파라미터의 기본값을 잃지 않는다") {
                val page = requestFieldsOf<DatedSearch>().first { it.descriptor.path == "page" }

                page.descriptor.attributes shouldContain (DEFAULT_VALUE_ATTRIBUTE to "3")
            }
        }

        given("CharArray 프로퍼티") {
            then("Jackson 이 문자열로 쓰므로 STRING 이다") {
                responseFieldsOf<CharsHolder>().typeOf("chars") shouldBe JsonFieldType.STRING
            }
        }
    })
