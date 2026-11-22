package io.github.kardx.extractor

import io.github.kardx.annotation.Description
import io.github.kardx.config.ResponseWrapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.type
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.restdocs.payload.JsonFieldType

data class Envelope<T>(@Description("코드") val code: Int, @Description("데이터") val data: T?)

data class Member(@Description("ID") val id: Long, @Description("이름") val name: String)

data class Paged<T>(val total: Long, val content: List<T>)

/** 데이터 프로퍼티에 설명이 없는 래퍼. */
data class Bare<T>(@Description("코드") val code: Int, val data: T?)

private fun List<RestDocsField>.paths() = map { it.descriptor.path }

class WrappedExtractorsTest :
    BehaviorSpec({
        beforeEach { RestDocsConfiguration.configure { extraction.responseWrapper = ResponseWrapper(Envelope::class) } }

        given("공통 래퍼가 있을 때") {
            then("래퍼 필드와 데이터 필드를 합치고, 데이터 줄에 래퍼의 설명을 쓴다") {
                val fields = wrappedResponseFieldsOf<Member>()

                fields.paths() shouldContainExactly listOf("code", "data", "data.id", "data.name")
                fields[1].descriptor.description shouldBe "데이터"
                fields[1].descriptor.type shouldBe JsonFieldType.OBJECT
            }

            then("데이터가 목록이면 배열로 문서화한다") {
                val fields = wrappedResponseFieldsOf<List<Member>>()

                fields.paths() shouldContainExactly listOf("code", "data", "data[].id", "data[].name")
                fields[1].descriptor.type shouldBe JsonFieldType.ARRAY
                fields[1].descriptor.description shouldBe "데이터"
                wrappedResponseFieldsOf<Member>("data[]").paths() shouldContainExactly
                    listOf("code", "data", "data[].id", "data[].name")
            }

            then("제네릭 페이지도 타입 인자로 추출한다") {
                wrappedResponseFieldsOf<Paged<Member>>().paths() shouldContainExactly
                    listOf("code", "data", "data.total", "data.content", "data.content[].id", "data.content[].name")
            }

            then("중간 래퍼 클래스 + 데이터 클래스 조합") {
                wrappedResponseFieldsOf(Paged::class, Member::class, "data.content[]").paths() shouldContainExactly
                    listOf("code", "data", "data.total", "data.content", "data.content[].id", "data.content[].name")
            }

            then("dataOptional 이면 데이터와 하위 필드가 optional") {
                wrappedResponseFieldsOf<Member>(dataOptional = true)
                    .filter { it.descriptor.path.startsWith("data") }
                    .all { it.descriptor.isOptional } shouldBe true
            }

            then("직접 기술한 데이터 필드에 래퍼 필드를 붙인다") {
                wrappedResponseFieldsOf("data.id" type NUMBER means "ID").paths() shouldContainExactly
                    listOf("code", "data.id")
            }
        }

        given("래퍼의 데이터 프로퍼티에 설명이 없을 때") {
            then("데이터 줄의 설명은 기본 설명 대신 데이터 타입 이름이다") {
                RestDocsConfiguration.withSettings({ extraction.responseWrapper = ResponseWrapper(Bare::class) }) {
                    val fields = wrappedResponseFieldsOf<Member>()

                    fields.paths() shouldContainExactly listOf("code", "data", "data.id", "data.name")
                    fields[1].descriptor.description shouldBe "Member"
                }
            }

            then("중간 래퍼의 데이터 프로퍼티도 같다") {
                RestDocsConfiguration.withSettings({ extraction.responseWrapper = null }) {
                    val fields = wrappedResponseFieldsOf(Paged::class, Member::class, "content[]")

                    fields.single { it.descriptor.path == "content" }
                        .descriptor.description shouldBe "Member"
                }
            }
        }

        given("공통 래퍼가 없을 때") {
            then("데이터 필드만 루트에서 추출한다") {
                RestDocsConfiguration.withSettings({ extraction.responseWrapper = null }) {
                    wrappedResponseFieldsOf<Member>().paths() shouldContainExactly listOf("id", "name")
                    wrappedResponseFieldsOf("id" type NUMBER means "ID").paths() shouldContainExactly listOf("id")
                }
            }
        }
    })
