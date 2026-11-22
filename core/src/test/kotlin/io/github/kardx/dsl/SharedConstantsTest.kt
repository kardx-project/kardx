package io.github.kardx.dsl

import io.github.kardx.extractor.except
import io.github.kardx.extractor.ofType
import io.github.kardx.extractor.optionalAt
import io.github.kardx.extractor.optionalUnder
import io.github.kardx.extractor.responseFieldsOf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.springframework.restdocs.cookies.CookieDescriptor
import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.hypermedia.LinkDescriptor
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestPartDescriptor
import org.springframework.restdocs.snippet.AbstractDescriptor
import org.springframework.restdocs.snippet.IgnorableDescriptor

private enum class SharedStatus { ON, OFF }

private class SharedProfile(val nickname: String)

private class SharedUser(val id: Long, val profile: SharedProfile, val tags: List<String>)

/** 여러 테스트가 같이 쓰는 상수. 아래 테스트가 수식어를 붙인 뒤에도 그대로여야 한다. */
private val SHARED_FIELD = "id" type NUMBER means "ID"
private val SHARED_PARAM = "page" queryMeans "페이지"
private val SHARED_HEADER = "X-Trace" headerMeans "추적 ID"
private val SHARED_COOKIE = "SESSION" cookieMeans "세션"
private val SHARED_PART = "file" partMeans "파일"
private val SHARED_LINK = "self" linkMeans "자기 자신"
private val SHARED_EVENT = "tick" eventMeans "주기"
private val SHARED_FIELDS = listOf("id" type NUMBER means "ID", "name" type STRING means "이름")

/** 디스크립터 상태: 설명, optional, ignored, 속성. */
private fun AbstractDescriptor<*>.state(optional: Boolean): List<Any?> =
    listOf(description, optional, (this as? IgnorableDescriptor<*>)?.isIgnored, attributes.toMap())

private fun FieldDescriptor.state() = state(isOptional)

private fun ParameterDescriptor.state() = state(isOptional)

private fun HeaderDescriptor.state() = state(isOptional)

private fun CookieDescriptor.state() = state(isOptional)

private fun RequestPartDescriptor.state() = state(isOptional)

private fun LinkDescriptor.state() = state(isOptional)

class SharedConstantsTest :
    BehaviorSpec({
        given("공유한 필드 상수") {
            val before = SHARED_FIELD.descriptor.state()

            val described = SHARED_FIELD means "다른 설명" isOptional true constraint "1 이상" example "1"
            val modified = described withDefaultValue "0" enumOf SharedStatus::class deprecated "쓰지 않음" masked "가림"
            val ignored = SHARED_FIELD.ignored()

            then("수식어는 새 필드에만 적용되고 상수는 그대로다") {
                SHARED_FIELD.descriptor.state() shouldBe before
                SHARED_FIELD.descriptor.isOptional shouldBe false
                SHARED_FIELD.descriptor.isIgnored shouldBe false

                modified shouldNotBeSameInstanceAs SHARED_FIELD
                modified.descriptor shouldNotBeSameInstanceAs SHARED_FIELD.descriptor
                modified.descriptor.description shouldBe "다른 설명"
                modified.descriptor.isOptional shouldBe true
                modified.descriptor.attributes[EXAMPLE_ATTRIBUTE] shouldBe "1"
                modified.descriptor.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe listOf("ON", "OFF")
                ignored.descriptor.isIgnored shouldBe true
            }

            then("deprecated()·masked()·subsection 도 상수를 바꾸지 않는다") {
                val subsection = "meta" subsection OBJECT means "메타"
                val subsectionBefore = subsection.descriptor.state()

                SHARED_FIELD.deprecated()
                SHARED_FIELD.masked()
                subsection.optional()

                SHARED_FIELD.descriptor.state() shouldBe before
                subsection.descriptor.state() shouldBe subsectionBefore
            }

            then("false 로 부르면 같은 객체를 돌려준다") {
                (SHARED_FIELD isOptional false) shouldBe SHARED_FIELD
                (SHARED_FIELD isIgnored false) shouldBe SHARED_FIELD
            }
        }

        given("공유한 필드 목록") {
            val pathsBefore = SHARED_FIELDS.map { it.descriptor.state() }

            SHARED_FIELDS.optionalAt("id")
            SHARED_FIELDS.optionalUnder("name")
            SHARED_FIELDS.except("name")
            SHARED_FIELDS.forEach { it isOptional true }

            then("목록과 원소가 그대로다") {
                SHARED_FIELDS.map { it.descriptor.state() } shouldBe pathsBefore
            }

            then("추출 결과도 수식어를 붙인 뒤 다시 추출한 것과 같다") {
                val extracted = responseFieldsOf<SharedUser>()
                val before = extracted.map { it.descriptor.state() }

                extracted.optionalUnder("profile")
                extracted.forEach { it means "x" isOptional true }

                extracted.map { it.descriptor.state() } shouldBe before
                responseFieldsOf<SharedUser>().map { it.descriptor.state() } shouldBe before
            }
        }

        given("공유한 파라미터·헤더·쿠키·파트·링크 상수") {
            val param = SHARED_PARAM.descriptor.state()
            val header = SHARED_HEADER.descriptor.state()
            val cookie = SHARED_COOKIE.descriptor.state()
            val part = SHARED_PART.descriptor.state()
            val link = SHARED_LINK.descriptor.state()

            val optionalParam = SHARED_PARAM isOptional true constraint "0 이상" example "1" withDefaultValue "0"
            val modifiedParam = optionalParam enumOf SharedStatus::class ofType Int::class deprecated "쓰지 않음"
            val modifiedHeader = SHARED_HEADER isOptional true constraint "UUID" example "a" withDefaultValue "b"
            val modifiedCookie = SHARED_COOKIE isOptional true
            val modifiedPart = SHARED_PART isOptional true
            val modifiedLink = SHARED_LINK isOptional true

            then("상수는 그대로이고 새 객체에만 적용된다") {
                SHARED_PARAM.descriptor.state() shouldBe param
                SHARED_HEADER.descriptor.state() shouldBe header
                SHARED_COOKIE.descriptor.state() shouldBe cookie
                SHARED_PART.descriptor.state() shouldBe part
                SHARED_LINK.descriptor.state() shouldBe link

                listOf(
                    modifiedParam.descriptor.isOptional,
                    modifiedHeader.descriptor.isOptional,
                    modifiedCookie.descriptor.isOptional,
                    modifiedPart.descriptor.isOptional,
                    modifiedLink.descriptor.isOptional,
                ) shouldContainExactly listOf(true, true, true, true, true)
                modifiedParam.descriptor.attributes[DEFAULT_VALUE_ATTRIBUTE] shouldBe "0"
                modifiedParam.descriptor.attributes[SCHEMA_ATTRIBUTE] shouldNotBe null
                modifiedHeader.descriptor.attributes[EXAMPLE_ATTRIBUTE] shouldBe "a"
            }
        }

        given("공유한 이벤트 상수") {
            val modified = SHARED_EVENT isOptional true withFields listOf(SHARED_FIELD)

            then("상수는 필드 없는 필수 이벤트 그대로다") {
                SHARED_EVENT.optional shouldBe false
                SHARED_EVENT.fields shouldBe emptyList()
                modified.optional shouldBe true
                modified.fields shouldBe listOf(SHARED_FIELD)
            }
        }
    })
