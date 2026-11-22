package io.github.kardx.extractor

import io.github.kardx.dsl.DEPRECATED_ATTRIBUTE
import io.github.kardx.dsl.DEPRECATION_NOTE_ATTRIBUTE
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.queryMeans
import io.github.kardx.dsl.type
import io.github.kardx.internal.schema.JsonSchemaBuilder
import io.github.kardx.internal.schema.SchemaDialect
import io.github.kardx.internal.schema.SchemaField
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.support.PayloadController
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.io.File
import java.nio.file.Files

class DeprecatedResponse(
    val id: Long,
    @Deprecated("nickname 을 쓴다")
    val name: String,
    val nickname: String,
)

class DeprecatedCondition(
    @Deprecated("order 를 쓴다")
    val sort: String?,
    val order: String?,
)

class DeprecatedFieldsTest :
    BehaviorSpec({
        given("Kotlin @Deprecated 프로퍼티") {
            val fields = responseFieldsOf<DeprecatedResponse>().associateBy { it.descriptor.path }

            then("deprecated 표시와 message 를 남기고, 붙지 않은 필드는 그대로다") {
                fields.getValue("name").descriptor.attributes shouldContain (DEPRECATED_ATTRIBUTE to true)
                fields.getValue("name").descriptor.attributes shouldContain
                    (DEPRECATION_NOTE_ATTRIBUTE to "nickname 을 쓴다")
                fields.getValue("nickname").descriptor.attributes shouldNotContainKey DEPRECATED_ATTRIBUTE
            }

            then("파라미터도 같다") {
                val sort = parametersOf<DeprecatedCondition>().single { it.descriptor.name == "sort" }

                sort.descriptor.attributes shouldContain (DEPRECATED_ATTRIBUTE to true)
            }
        }

        given("Java @Deprecated 프로퍼티") {
            then("이유 없이 deprecated 로 표시한다") {
                val descriptor = responseFieldsOf<JavaDeprecatedBean>().single { it.descriptor.path == "legacy" }
                    .descriptor

                descriptor.attributes shouldContain (DEPRECATED_ATTRIBUTE to true)
                descriptor.attributes shouldNotContainKey DEPRECATION_NOTE_ATTRIBUTE
            }
        }

        given("직접 쓰는 DSL") {
            then("deprecated() 와 deprecated(이유) 수식어") {
                ("a" type STRING means "A" deprecated "b 를 쓴다").descriptor.attributes shouldContain
                    (DEPRECATION_NOTE_ATTRIBUTE to "b 를 쓴다")
                ("page" queryMeans "페이지").deprecated()
                    .descriptor
                    .attributes shouldContain (DEPRECATED_ATTRIBUTE to true)
            }
        }

        given("AsyncAPI 페이로드 스키마") {
            then("deprecated 필드의 속성에 deprecated: true 를 넣는다") {
                val fields = responseFieldsOf<DeprecatedResponse>().map(SchemaField::of)
                val schema = JsonSchemaBuilder.fromFields(fields, SchemaDialect.JSON_SCHEMA)
                val properties = schema["properties"] as Map<*, *>

                (properties["name"] as Map<*, *>)["deprecated"] shouldBe true
                (properties["nickname"] as Map<*, *>).containsKey("deprecated") shouldBe false
            }
        }

        given("응답 필드 스니펫") {
            then("설명 앞에 Deprecated 줄을 넣는다") {
                val output = Files.createTempDirectory("deprecated").toFile()
                val documentation = ManualRestDocumentation(output.path)
                val mockMvc = MockMvcBuilders.standaloneSetup(PayloadController(DeprecatedResponse(1, "kim", "kim")))
                    .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
                    .build()

                documentation.beforeTest(DeprecatedFieldsTest::class.java, "response")

                try {
                    mockMvc.perform(get("/payload"))
                        .andDocument { responseFields(responseFieldsOf<DeprecatedResponse>()) }
                } finally {
                    documentation.afterTest()
                }

                val snippet = File(output, "deprecated-fields-test/response/response-fields.adoc").readText()

                snippet shouldContain "*Deprecated*: nickname 을 쓴다 +\nNo description"
            }
        }
    })
