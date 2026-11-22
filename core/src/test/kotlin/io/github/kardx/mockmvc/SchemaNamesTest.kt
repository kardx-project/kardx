package io.github.kardx.mockmvc
import io.github.kardx.config.ResponseWrapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.OpenApiBuilder
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.type
import io.github.kardx.extractor.requestFieldsOf
import io.github.kardx.extractor.responseFieldsOf
import io.github.kardx.extractor.wrappedResponseFieldsOf
import io.github.kardx.internal.documentation.toOptions
import io.github.kardx.internal.documentation.toSpec
import io.github.kardx.internal.documentation.withSchemaNames
import io.github.kardx.internal.documentation.withStreamExclusion
import io.github.kardx.openapi.SchemaNameResolver
import io.github.kardx.openapi.SchemaNames
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.reflect.typeOf

class Envelope2<T>(val data: T)

// 필드가 없는 본문에는 자동 이름을 붙이지 않으므로 프로퍼티가 하나 있어야 한다
class Todo2(val title: String)

class SchemaNamesTest :
    BehaviorSpec({
        given("스키마 이름") {
            then("클래스 이름, 제네릭은 타입 인자를 이어 붙인다") {
                SchemaNames.of(Todo2::class) shouldBe "Todo2"
                SchemaNames.of(Envelope2::class) shouldBe "Envelope2"
                SchemaNames.of(typeOf<Envelope2<Todo2>>()) shouldBe "Envelope2Todo2"
                SchemaNames.of(typeOf<Envelope2<List<Todo2>>>()) shouldBe "Envelope2ListTodo2"
            }

            then("OpenApiBuilder 의 클래스·타입 지정") {
                val builder = OpenApiBuilder()

                builder.requestSchema(Todo2::class)
                builder.responseSchema<Envelope2<Todo2>>()

                builder.toOptions().requestSchema shouldBe "Todo2"
                builder.toOptions().responseSchema shouldBe "Envelope2Todo2"
            }
        }

        given("공통 오류 스키마") {
            fun spec(responseSchema: String? = null) = DocumentationBuilder().apply {
                if (responseSchema != null) openApi { this.responseSchema = responseSchema }
            }.toSpec()

            then("4xx·5xx 응답에 스키마가 없으면 설정 값을 쓴다") {
                RestDocsConfiguration.configure { openApi.errorSchema(Todo2::class) }

                spec().withSchemaNames(404).openApi.responseSchema shouldBe "Todo2"
                spec().withSchemaNames(500).openApi.responseSchema shouldBe "Todo2"
            }

            then("성공 응답이나 직접 정한 이름은 그대로 둔다") {
                RestDocsConfiguration.configure { openApi.errorSchema = "ErrorResponse" }

                spec().withSchemaNames(200).openApi.responseSchema shouldBe null
                spec("NotFound").withSchemaNames(404).openApi.responseSchema shouldBe "NotFound"
            }

            then("설정하지 않으면 아무것도 붙이지 않는다") {
                spec().withSchemaNames(404).openApi.responseSchema shouldBe null
            }
        }

        given("추출한 타입으로 스키마 이름 기본값") {
            then("responseFieldsOf, requestFieldsOf 의 타입 이름을 쓴다") {
                val spec =
                    DocumentationBuilder().apply {
                        requestFields(requestFieldsOf<Todo2>())
                        responseFields(responseFieldsOf<Envelope2<Todo2>>())
                    }.toSpec()
                        .withSchemaNames(200)

                spec.openApi.requestSchema shouldBe "Todo2"
                spec.openApi.responseSchema shouldBe "Envelope2Todo2"
            }

            then("[] 경로는 목록 이름이고, 다른 경로로 추출하면 본문 타입을 몰라 이름을 붙이지 않는다") {
                fun nameOf(path: String) = DocumentationBuilder().apply {
                    responseFields(responseFieldsOf<Todo2>(path))
                }.toSpec()
                    .withSchemaNames(200)
                    .openApi
                    .responseSchema

                nameOf("[]") shouldBe "ListTodo2"
                nameOf("data") shouldBe null
            }

            then("래퍼를 등록하지 않고 dataPath 를 주면 본문이 데이터 타입이 아니므로 이름을 붙이지 않는다") {
                fun nameOf(fields: List<RestDocsField>) = DocumentationBuilder().apply {
                    responseFields(fields)
                }.toSpec()
                    .withSchemaNames(200)
                    .openApi.responseSchema

                // 본문은 { "data": { "title": ... } } 인데 진짜 Todo2 스키마와 이름이 겹치면 안 된다
                nameOf(wrappedResponseFieldsOf<Todo2>(dataPath = "data")) shouldBe null
                nameOf(wrappedResponseFieldsOf<Todo2>(dataPath = "data[]")) shouldBe null
                RestDocsConfiguration.withSettings({ openApi.schemaNamesWithoutWrapper = true }) {
                    nameOf(wrappedResponseFieldsOf<Todo2>(dataPath = "data"))
                } shouldBe null
                // dataPath 가 없으면 본문 자체가 데이터라 지금처럼 타입 이름이다
                nameOf(wrappedResponseFieldsOf<Todo2>()) shouldBe "Todo2"
            }

            then("래퍼를 쓰면 래퍼 + 데이터 이름 (List 는 [] 경로로)") {
                RestDocsConfiguration.configure {
                    extraction.responseWrapper = ResponseWrapper(Envelope2::class)
                }

                fun nameOf(fields: List<RestDocsField>) = DocumentationBuilder().apply {
                    responseFields(fields)
                }.toSpec()
                    .withSchemaNames(200)
                    .openApi.responseSchema

                nameOf(wrappedResponseFieldsOf<Todo2>()) shouldBe "Envelope2Todo2"
                nameOf(wrappedResponseFieldsOf<List<Todo2>>()) shouldBe "Envelope2ListTodo2"
                nameOf(wrappedResponseFieldsOf<Todo2>("data[]")) shouldBe "Envelope2ListTodo2"
            }

            then("래퍼 이름을 떼는 설정이면 데이터 타입 이름이고, 겹치면 쓸 다음 후보로 래퍼 이름을 남긴다") {
                RestDocsConfiguration.configure {
                    extraction.responseWrapper = ResponseWrapper(Envelope2::class)
                    openApi.schemaNamesWithoutWrapper = true
                }

                fun originOf(fields: List<RestDocsField>) = DocumentationBuilder().apply {
                    responseFields(fields)
                }.toSpec()
                    .withSchemaNames(200)
                    .openApi
                    .responseOrigin

                val todo = originOf(wrappedResponseFieldsOf<Todo2>())

                todo?.name shouldBe "Todo2"
                todo?.candidates?.take(2) shouldBe listOf("Todo2", "Envelope2Todo2")
                todo?.type shouldBe typeOf<Envelope2<Todo2>>().toString()
                originOf(wrappedResponseFieldsOf<List<Todo2>>())?.name shouldBe "ListTodo2"
                originOf(wrappedResponseFieldsOf<Unit>())?.name shouldBe "Envelope2Unit"
                originOf(responseFieldsOf<Todo2>())?.name shouldBe "Todo2"
            }

            then("우선순위: 직접 정한 이름 > 오류 스키마 > 추출 타입") {
                RestDocsConfiguration.configure { openApi.errorSchema = "ErrorResponse" }

                fun build(explicit: String?) = DocumentationBuilder().apply {
                    responseFields(responseFieldsOf<Todo2>())

                    if (explicit != null) openApi { responseSchema = explicit }
                }.toSpec()

                build("Mine").withSchemaNames(404).openApi.responseSchema shouldBe "Mine"
                build(null).withSchemaNames(404).openApi.responseSchema shouldBe "ErrorResponse"
                build(null).withSchemaNames(200).openApi.responseSchema shouldBe "Todo2"
            }

            then("직접 정한 이름도 본문 타입을 출처에 남긴다 (이름은 그대로, 플러그인이 클래스 KDoc 을 설명으로 쓴다)") {
                RestDocsConfiguration.configure { openApi.errorSchema = "ErrorResponse" }

                fun originOf(explicit: String?, status: Int) = DocumentationBuilder().apply {
                    requestFields(requestFieldsOf<Todo2>())
                    responseFields(responseFieldsOf<Todo2>())

                    if (explicit != null) {
                        openApi {
                            requestSchema = explicit
                            responseSchema = explicit
                        }
                    }
                }.toSpec()
                    .withSchemaNames(status)
                    .openApi

                val todoType = typeOf<Todo2>().toString()
                val explicit = originOf("TodoDetail", 200)
                val error = originOf(null, 404).responseOrigin

                explicit.responseOrigin?.automatic shouldBe false
                explicit.responseOrigin?.type shouldBe todoType
                explicit.requestOrigin?.type shouldBe todoType
                error?.name shouldBe "ErrorResponse"
                error?.automatic shouldBe false
                error?.type shouldBe todoType
                DocumentationBuilder().apply {
                    openApi { responseSchema = "Raw" }
                }.toSpec()
                    .withSchemaNames(200)
                    .openApi.responseOrigin?.type shouldBe null
                RestDocsConfiguration.withSettings({
                    openApi.schemaNameResolver =
                        SchemaNameResolver { "Dto" + SchemaNames.of(it.type) }
                }) {
                    originOf(null, 200).responseOrigin
                }?.type shouldBe todoType
            }

            then("끄거나 이름 규칙을 바꿀 수 있다") {
                fun name() = DocumentationBuilder().apply {
                    responseFields(responseFieldsOf<Todo2>())
                }.toSpec()
                    .withSchemaNames(200)
                    .openApi.responseSchema

                RestDocsConfiguration.withSettings({ openApi.schemaNames = false }) { name() } shouldBe null
                RestDocsConfiguration.withSettings({
                    openApi.schemaNameResolver =
                        SchemaNameResolver { "Dto" + SchemaNames.of(it.type) }
                }) {
                    name()
                } shouldBe "DtoTodo2"
            }

            then("걸러 내거나 직접 쓴 필드를 더한 목록도 추출한 타입 이름을 유지한다") {
                DocumentationBuilder().apply {
                    responseFields(
                        responseFieldsOf<Todo2>()
                            .filter { true } + ("extra" type STRING means "직접 쓴 필드"),
                    )
                }.toSpec()
                    .withSchemaNames(200)
                    .openApi.responseSchema shouldBe "Todo2"
            }

            then("다른 타입에서 추출한 목록을 섞으면 기본값을 붙이지 않는다") {
                DocumentationBuilder().apply {
                    responseFields(responseFieldsOf<Todo2>() + responseFieldsOf<Envelope2<Todo2>>("wrapped"))
                }.toSpec()
                    .withSchemaNames(200)
                    .openApi.responseSchema shouldBe null
            }
        }

        given("끝나지 않는 스트림") {
            then("기본으로 OpenAPI 에서 빼고, 설정으로 넣을 수 있다") {
                val spec = DocumentationBuilder().toSpec()

                spec.withStreamExclusion(stream = true).openApi.excluded shouldBe true
                spec.withStreamExclusion(stream = false).openApi.excluded shouldBe false
                RestDocsConfiguration.withSettings({ openApi.includeStreams = true }) {
                    spec.withStreamExclusion(stream = true).openApi.excluded
                } shouldBe false
            }
        }
    })
