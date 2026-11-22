package io.github.kardx.extractor

import io.github.kardx.annotation.Description
import io.github.kardx.config.ResponseWrapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.subsection
import io.github.kardx.dsl.type
import io.github.kardx.internal.introspection.dataTypeOf
import io.github.kardx.internal.introspection.schemaTypeOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.reflect.typeOf

private class EditLink(@Description("주소") val href: String)

private class EditResource(
    @Description("ID") val id: Long,
    @Description("이름") val name: String,
    @Description("태그") val tags: List<String>,
    @Description("링크") val links: Map<String, EditLink>,
    @Description("작성자") val author: EditLink,
)

private class EditOrder(@Description("번호") val no: Long, @Description("품목") val items: List<EditLink>)

private class EditEnvelope<T>(@Description("코드") val code: Int, @Description("데이터") val data: T?)

private fun List<RestDocsField>.paths() = map { it.descriptor.path }

private fun List<RestDocsField>.optionalPaths() = filter { it.descriptor.isOptional }
    .map { it.descriptor.path }

/** `except`·`optionalAt` 과 목록을 고친 뒤의 OpenAPI 스키마 이름 기본값. */
class FieldListEditingTest :
    BehaviorSpec({
        afterEach { RestDocsConfiguration.reset() }

        given("except") {
            `when`("경로를 주면") {
                val fields = responseFieldsOf<EditResource>().except("links", "author")

                then("그 필드와 하위 필드를 뺀다") {
                    fields.paths() shouldContainExactly listOf("id", "name", "tags")
                }

                then("타입 정보가 남는다") {
                    schemaTypeOf(fields) shouldBe typeOf<EditResource>()
                }
            }

            `when`("직접 쓴 목록에 쓰면") {
                val fields = listOf("a" type NUMBER means "A", "a.b" type STRING means "B", "c" type STRING means "C")
                    .except("a")

                then("타입 없이 걸러 낸다") {
                    fields.paths() shouldContainExactly listOf("c")
                    schemaTypeOf(fields).shouldBeNull()
                }
            }

            `when`("목록에 없는 경로를 주면") {
                then("오타로 필드가 남지 않도록 실패한다") {
                    val message = shouldThrow<IllegalArgumentException> {
                        responseFieldsOf<EditResource>().except("link")
                    }.message

                    message shouldContain "kardx[KDX-108]: "
                    message shouldContain "except: no field at link"
                }
            }
        }

        given("optionalAt") {
            `when`("경로를 주면") {
                val original = responseFieldsOf<EditResource>()
                val fields = original.optionalAt("tags", "author.href")

                then("그 필드만 optional 이다") {
                    fields.optionalPaths() shouldContainExactly listOf("tags", "author.href")
                }

                then("원래 목록의 필드는 바꾸지 않는다") {
                    original.optionalPaths() shouldContainExactly emptyList()
                }

                then("타입 정보가 남는다") {
                    schemaTypeOf(fields) shouldBe typeOf<EditResource>()
                }
            }

            `when`("목록에 없는 경로를 주면") {
                then("실패한다") {
                    shouldThrow<IllegalArgumentException> {
                        responseFieldsOf<EditResource>().optionalAt("tag")
                    }
                }
            }
        }

        given("optionalUnder") {
            `when`("배열 원소 경로(items[])를 주면") {
                val original = responseFieldsOf<EditOrder>()
                val fields = original.optionalUnder("items[]")

                then("원소 필드만 optional 이고 배열 자체는 그대로다") {
                    fields.optionalPaths() shouldContainExactly listOf("items[].href")
                }

                then("원래 목록은 바꾸지 않고 타입 정보가 남는다") {
                    original.optionalPaths() shouldContainExactly emptyList()
                    schemaTypeOf(fields) shouldBe typeOf<EditOrder>()
                }
            }

            `when`("객체 경로를 주면") {
                then("그 필드와 하위 필드가 모두 optional 이다") {
                    responseFieldsOf<EditResource>().optionalUnder("author")
                        .optionalPaths() shouldContainExactly listOf("author", "author.href")
                }
            }

            `when`("그 아래 필드가 없는 경로를 주면") {
                then("실패한다") {
                    shouldThrow<IllegalArgumentException> {
                        responseFieldsOf<EditOrder>().optionalUnder("item[]")
                    }.message shouldContain "optionalUnder: no field at item[]"
                }
            }
        }

        given("Kotlin 표준 함수로 고친 추출 목록") {
            `when`("직접 쓴 필드를 더하거나 걸러 내면") {
                val plus = responseFieldsOf<EditResource>().except("links") +
                    ("links" subsection OBJECT means "링크")
                val filtered = responseFieldsOf<EditResource>().filterNot { it.descriptor.path == "tags" }

                then("추출한 타입 이름을 유지한다") {
                    schemaTypeOf(plus) shouldBe typeOf<EditResource>()
                    schemaTypeOf(filtered) shouldBe typeOf<EditResource>()
                }
            }

            `when`("다른 타입이나 다른 경로의 추출 목록을 섞으면") {
                val twoTypes = responseFieldsOf<EditResource>() + responseFieldsOf<EditLink>()
                val withPath = responseFieldsOf<EditLink>() + responseFieldsOf<EditResource>("content[]")

                then("본문이 한 타입이 아니므로 이름을 정하지 않는다") {
                    schemaTypeOf(twoTypes).shouldBeNull()
                    schemaTypeOf(withPath).shouldBeNull()
                }
            }

            `when`("공통 응답 래퍼로 감싼 목록을 고치면") {
                RestDocsConfiguration.configure {
                    extraction.responseWrapper =
                        ResponseWrapper(EditEnvelope::class, "data")
                }

                val fields = wrappedResponseFieldsOf<EditResource>().except("data.links")

                then("래퍼 타입과 데이터 타입을 모두 유지한다") {
                    schemaTypeOf(fields) shouldBe typeOf<EditEnvelope<EditResource>>()
                    dataTypeOf(fields) shouldBe typeOf<EditResource>()
                }
            }

            `when`("직접 쓴 데이터 필드를 래퍼로 감싸면") {
                RestDocsConfiguration.configure {
                    extraction.responseWrapper =
                        ResponseWrapper(EditEnvelope::class, "data")
                }

                val fields = wrappedResponseFieldsOf("data.id" type NUMBER means "ID")

                then("래퍼 타입 이름을 붙이지 않는다 (여러 응답이 한 스키마로 합쳐지지 않게)") {
                    schemaTypeOf(fields).shouldBeNull()
                }
            }
        }
    })
