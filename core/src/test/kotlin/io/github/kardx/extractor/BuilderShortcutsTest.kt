package io.github.kardx.extractor

import io.github.kardx.annotation.Description
import io.github.kardx.config.ResponseWrapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.MessageDirection
import io.github.kardx.dsl.MessageDocumentationBuilder
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.ParameterSection
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.RestDocsParam
import io.github.kardx.dsl.type
import io.github.kardx.internal.introspection.TypedFields
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private data class ShortcutEnvelope<T>(@Description("코드") val code: Int, @Description("데이터") val data: T?)

private data class ShortcutItem(@Description("ID") val id: Long, @Description("크기") val size: Int = 20)

private fun List<RestDocsField>.paths() = map { it.descriptor.path }

private fun List<RestDocsField>.optionalPaths() = filter { it.descriptor.isOptional }
    .map { it.descriptor.path }

private fun List<RestDocsParam>.names() = map { it.descriptor.name }

/** `andDocument`·메시지 블록의 단축형이 `…Of` 목록 버전과 같은 결과를 등록하는지, 오버로드가 원래대로 풀리는지. */
class BuilderShortcutsTest :
    BehaviorSpec({
        afterEach { RestDocsConfiguration.reset() }

        given("본문 필드 단축형") {
            `when`("requestFields<T>() 와 responseFields<T>() 를 부르면") {
                val builder = DocumentationBuilder().apply {
                    requestFields<ShortcutItem>()
                    responseFields<ShortcutItem>("data")
                }

                then("…Of 로 추출한 목록과 같은 필드를 등록한다") {
                    builder.requestFields.paths() shouldContainExactly requestFieldsOf<ShortcutItem>().paths()
                    builder.requestFields.optionalPaths() shouldContainExactly listOf("size")
                    builder.responseFields.paths() shouldContainExactly listOf("data.id", "data.size")
                }

                then("타입을 기억한 목록 그대로 넘겨 스키마 이름 기본값을 유지한다") {
                    builder.requestFieldGroups.single()
                        .shouldBeInstanceOf<TypedFields>()
                }
            }

            `when`("타입 인자 없이 인자 없이 부르면") {
                val builder = DocumentationBuilder().apply {
                    responseFields()
                    requestFields()
                }

                then("가변 인자 버전으로 가서 빈 목록을 등록한다") {
                    builder.responseFields.shouldBeEmpty()
                    builder.responseFieldGroups shouldContainExactly listOf(emptyList())
                    builder.requestFieldGroups shouldContainExactly listOf(emptyList())
                }
            }

            `when`("배열 본문을 List<T> 로 적으면") {
                then("경로 \"[]\" 로 추출한 것과 같은 필드다") {
                    responseFieldsOf<List<ShortcutItem>>().paths() shouldContainExactly
                        responseFieldsOf<ShortcutItem>("[]").paths()
                    responseFieldsOf<List<ShortcutItem>>().optionalPaths() shouldContainExactly
                        responseFieldsOf<ShortcutItem>("[]").optionalPaths()
                }
            }
        }

        given("래퍼 단축형") {
            beforeEach {
                RestDocsConfiguration.configure {
                    extraction.responseWrapper = ResponseWrapper(ShortcutEnvelope::class)
                }
            }

            `when`("wrappedResponseFields<T>() 를 부르면") {
                then("wrappedResponseFieldsOf<T>() 와 같은 응답 필드를 등록한다") {
                    val builder = DocumentationBuilder().apply {
                        wrappedResponseFields<ShortcutItem>(dataOptional = true)
                    }

                    builder.responseFields.paths() shouldContainExactly
                        listOf("code", "data", "data.id", "data.size")
                    builder.responseFields.optionalPaths() shouldContainExactly listOf("data", "data.id", "data.size")
                }
            }

            `when`("직접 기술한 필드로 wrappedResponseFields(...) 를 부르면") {
                then("래퍼 필드를 붙여 등록한다") {
                    val builder = DocumentationBuilder().apply {
                        wrappedResponseFields("data.id" type NUMBER means "ID")
                    }

                    builder.responseFields.paths() shouldContainExactly listOf("code", "data.id")
                }
            }
        }

        given("파라미터 단축형") {
            `when`("queryParameters<T>() 와 formParameters<T>(path) 를 부르면") {
                val builder = DocumentationBuilder().apply {
                    queryParameters<ShortcutItem>()
                    formParameters<ShortcutItem>("item")
                }

                then("parametersOf 와 같은 파라미터와 표 종류를 등록한다") {
                    builder.queryParameters.names() shouldContainExactly parametersOf<ShortcutItem>().names()
                    builder.formParameters.names() shouldContainExactly listOf("item.id", "item.size")
                    builder.parameterSections shouldContainExactly setOf(ParameterSection.QUERY, ParameterSection.FORM)
                }
            }

            `when`("pathParameters<T>() 를 부르면") {
                val builder = DocumentationBuilder().apply { pathParameters<ShortcutItem>() }

                then("parametersOf 와 같은 파라미터를 경로 파라미터 표로 등록한다") {
                    builder.pathParameters.names() shouldContainExactly listOf("id", "size")
                    builder.parameterSections shouldContainExactly setOf(ParameterSection.PATH)
                }
            }

            `when`("queryParameters() 를 인자 없이 부르면") {
                then("가변 인자 버전으로 가서 빈 표를 만든다") {
                    val builder = DocumentationBuilder().apply { queryParameters() }

                    builder.queryParameters.shouldBeEmpty()
                    builder.parameterSections shouldContain ParameterSection.QUERY
                }
            }
        }

        given("파트 필드 단축형") {
            then("requestPartFields<T>(part) 는 그 파트에 요청 규칙으로 추출한 필드를 등록한다") {
                val builder = DocumentationBuilder().apply { requestPartFields<ShortcutItem>("metadata") }

                builder.requestPartFields.getValue("metadata")
                    .optionalPaths() shouldContainExactly listOf("size")
            }
        }

        given("메시지 필드 단축형") {
            then("send 는 요청 규칙(기본값이 있으면 optional)으로 추출한다") {
                val builder = MessageDocumentationBuilder(MessageDirection.SEND).apply { fields<ShortcutItem>() }

                builder.fields.optionalPaths() shouldContainExactly listOf("size")
            }

            then("connect 도 클라이언트가 보내므로 요청 규칙이다") {
                val builder = MessageDocumentationBuilder(MessageDirection.CONNECT).apply { fields<ShortcutItem>() }

                builder.fields.optionalPaths() shouldContainExactly listOf("size")
            }

            then("subscribe 는 응답 규칙으로 추출한다") {
                val builder = MessageDocumentationBuilder(MessageDirection.SUBSCRIBE).apply {
                    fields<ShortcutItem>("payload")
                }

                builder.fields.paths() shouldContainExactly listOf("payload.id", "payload.size")
                builder.fields.optionalPaths().shouldBeEmpty()
            }

            then("타입 인자 없이 부르면 가변 인자 버전이다") {
                val builder = MessageDocumentationBuilder(MessageDirection.SEND).apply { fields() }

                builder.fields.shouldBeEmpty()
            }
        }

        given("이름을 바꾼 wrappedResponseFieldsOf") {
            then("필드 인자는 가변 인자 버전, 타입 인자는 reified 버전으로 간다") {
                RestDocsConfiguration.withSettings({ extraction.responseWrapper = null }) {
                    wrappedResponseFieldsOf("id" type NUMBER means "ID").paths() shouldBe listOf("id")
                    wrappedResponseFieldsOf<ShortcutItem>().paths() shouldBe listOf("id", "size")
                }
            }
        }
    })
