package io.github.kardx.extractor

import io.github.kardx.annotation.Masked
import io.github.kardx.config.MaskedAnnotation
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.dsl.MASKED_ATTRIBUTE
import io.github.kardx.dsl.MASKING_RULE_ATTRIBUTE
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.type
import io.github.kardx.internal.schema.JsonSchemaBuilder
import io.github.kardx.internal.schema.SchemaDialect
import io.github.kardx.internal.schema.SchemaField
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.support.PayloadController
import io.kotest.assertions.throwables.shouldThrow
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

/** 회사 마스킹 어노테이션 흉내: 속성 없음. */
@Retention(AnnotationRetention.RUNTIME)
annotation class MaskName

/** 회사 마스킹 어노테이션 흉내: String 규칙 속성. */
@Retention(AnnotationRetention.RUNTIME)
annotation class Masking(val rule: String, val count: Int = 0)

enum class MaskKind { PHONE, EMAIL }

/** 회사 마스킹 어노테이션 흉내: enum 규칙 속성. */
@Retention(AnnotationRetention.RUNTIME)
annotation class MaskType(val type: MaskKind)

@Suppress("unused")
class MaskedPositions(
    @Masked("가운데 글자를 *") val constructorProperty: String,
    @property:Masked val onProperty: String,
    @field:Masked("필드") val onField: String,
    @get:Masked("getter") val onGetter: String,
    val plain: String,
)

@Suppress("unused")
class MaskedLine(@Masked("뒤 4자리") val phone: String)

@Suppress("unused")
class MaskedNested(val owner: MaskedLine, val contacts: List<MaskedLine>)

@Suppress("unused")
class CompanyMasked(
    @param:MaskName val name: String,
    @param:Masking("가운데 4자리") val phone: String,
    @param:MaskType(MaskKind.EMAIL) val email: String,
)

@Suppress("unused")
class MaskedAndDeprecated(
    @Deprecated("nickname 을 쓴다")
    @Masked("가운데 글자를 *")
    val name: String,
)

@Suppress("unused")
class MaskedQuery(@Masked val keyword: String?)

private fun RestDocsField.attributes(): Map<String, Any> = descriptor.attributes

private fun byPath(fields: List<RestDocsField>): Map<String, RestDocsField> = fields.associateBy { it.descriptor.path }

private fun <T> withSettings(block: RestDocsSettings.() -> Unit, action: () -> T): T =
    RestDocsConfiguration.withSettings(block, action)

private fun responseSnippet(response: Any, fields: List<RestDocsField>): String {
    val output = Files.createTempDirectory("masked").toFile()
    val documentation = ManualRestDocumentation(output.path)
    val mockMvc = MockMvcBuilders.standaloneSetup(PayloadController(response))
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()

    documentation.beforeTest(MaskedFieldsTest::class.java, "response")

    try {
        mockMvc.perform(get("/payload"))
            .andDocument { responseFields(fields) }
    } finally {
        documentation.afterTest()
    }

    return File(output, "masked-fields-test/response/response-fields.adoc").readText()
}

class MaskedFieldsTest :
    BehaviorSpec({
        given("@Masked 를 붙이는 위치") {
            val fields = byPath(responseFieldsOf<MaskedPositions>())

            then("생성자 프로퍼티·@property:·@field:·@get: 모두 마스킹으로 읽고 규칙을 남긴다") {
                fields.getValue("constructorProperty").attributes() shouldContain
                    (MASKING_RULE_ATTRIBUTE to "가운데 글자를 *")
                fields.getValue("onProperty").attributes() shouldContain (MASKED_ATTRIBUTE to true)
                fields.getValue("onProperty").attributes() shouldNotContainKey MASKING_RULE_ATTRIBUTE
                fields.getValue("onField").attributes() shouldContain (MASKING_RULE_ATTRIBUTE to "필드")
                fields.getValue("onGetter").attributes() shouldContain (MASKING_RULE_ATTRIBUTE to "getter")
                fields.getValue("plain").attributes() shouldNotContainKey MASKED_ATTRIBUTE
            }
        }

        given("Java record 와 bean") {
            then("record component, 필드, getter 의 @Masked 를 읽는다") {
                val record = byPath(responseFieldsOf<MaskedJavaRecord>())
                val bean = byPath(responseFieldsOf<MaskedJavaBean>())

                record.getValue("phone").attributes() shouldContain (MASKING_RULE_ATTRIBUTE to "뒤 4자리")
                record.getValue("name").attributes() shouldNotContainKey MASKED_ATTRIBUTE
                bean.getValue("onField").attributes() shouldContain (MASKED_ATTRIBUTE to true)
                bean.getValue("onGetter").attributes() shouldContain (MASKING_RULE_ATTRIBUTE to "getter 규칙")
                bean.getValue("plain").attributes() shouldNotContainKey MASKED_ATTRIBUTE
            }
        }

        given("중첩 객체와 배열 원소의 필드") {
            then("경로마다 마스킹을 표시한다") {
                val fields = byPath(responseFieldsOf<MaskedNested>())

                fields.getValue("owner.phone").attributes() shouldContain (MASKING_RULE_ATTRIBUTE to "뒤 4자리")
                fields.getValue("contacts[].phone").attributes() shouldContain (MASKING_RULE_ATTRIBUTE to "뒤 4자리")
                fields.getValue("owner").attributes() shouldNotContainKey MASKED_ATTRIBUTE
            }
        }

        given("파라미터") {
            then("값이 나가지 않으므로 표시하지 않는다") {
                parametersOf<MaskedQuery>().single()
                    .descriptor
                    .attributes shouldNotContainKey MASKED_ATTRIBUTE
            }
        }

        given("masked-annotations 를 정하지 않았을 때") {
            then("회사 어노테이션은 읽지 않는다") {
                responseFieldsOf<CompanyMasked>().forEach { it.attributes() shouldNotContainKey MASKED_ATTRIBUTE }
            }
        }

        given("yml 의 masked-annotations (FQCN, FQCN#attribute)") {
            val specs = listOf(
                MaskName::class.java.name,
                "${Masking::class.java.name}#rule",
                "${MaskType::class.java.name}#type",
            )

            then("FQCN 은 규칙 없이, #attribute 는 String 값이나 enum 이름을 규칙으로 읽는다") {
                val fields = withSettings({ specs.forEach { masking.annotation(it) } }) {
                    byPath(responseFieldsOf<CompanyMasked>())
                }

                fields.getValue("name").attributes() shouldContain (MASKED_ATTRIBUTE to true)
                fields.getValue("name").attributes() shouldNotContainKey MASKING_RULE_ATTRIBUTE
                fields.getValue("phone").attributes() shouldContain (MASKING_RULE_ATTRIBUTE to "가운데 4자리")
                fields.getValue("email").attributes() shouldContain (MASKING_RULE_ATTRIBUTE to "EMAIL")
            }

            then("코드 설정과 같은 목록이 된다") {
                val fromCode = RestDocsSettings().apply {
                    masking.annotation(MaskName::class)
                    masking.annotation(Masking::class, "rule")
                    masking.annotation(MaskType::class, "type")
                }

                specs.map(MaskedAnnotation::parse) shouldBe fromCode.masking.annotations
            }

            then("String·enum 이 아닌 속성, 없는 속성은 오류다") {
                shouldThrow<IllegalArgumentException> { MaskedAnnotation.parse("${Masking::class.java.name}#count") }
                    .message shouldBe "annotation '${Masking::class.java.name}' has no String or enum attribute 'count'"
                shouldThrow<IllegalArgumentException> { MaskedAnnotation.of(MaskName::class, "rule") }
                    .message shouldBe "annotation '${MaskName::class.java.name}' has no String or enum attribute 'rule'"
            }
        }

        given("직접 쓰는 DSL") {
            then("masked() 와 masked(규칙) 수식어") {
                ("phone" type STRING means "전화번호").masked()
                    .attributes() shouldContain (MASKED_ATTRIBUTE to true)
                ("name" type STRING means "이름" masked "가운데 글자를 *").attributes() shouldContain
                    (MASKING_RULE_ATTRIBUTE to "가운데 글자를 *")
            }
        }

        given("Deprecated 와 함께 붙은 필드") {
            val fields = responseFieldsOf<MaskedAndDeprecated>()

            then("응답 필드 표의 설명 칸에 Deprecated, Masked, 설명 순으로 쓴다") {
                val snippet = responseSnippet(MaskedAndDeprecated("kim"), fields)

                snippet shouldContain "*Deprecated*: nickname 을 쓴다 +\n*Masked*: 가운데 글자를 * +\nNo description"
            }

            then("describe() 미리보기에도 같은 순서로 나온다") {
                fields.describe() shouldContain "[Deprecated: nickname 을 쓴다] [Masked: 가운데 글자를 *] No description"
            }

            then("AsyncAPI 스키마에 x-masked, x-masked-rule, 설명 끝 Masked 를 넣는다") {
                val schema = JsonSchemaBuilder.fromFields(fields.map(SchemaField::of), SchemaDialect.JSON_SCHEMA)
                val name = (schema["properties"] as Map<*, *>)["name"] as Map<*, *>

                name["x-masked"] shouldBe true
                name["x-masked-rule"] shouldBe "가운데 글자를 *"
                name["deprecated"] shouldBe true
                name["description"] shouldBe "No description\n\nMasked: 가운데 글자를 *"
            }
        }

        given("규칙 없이 masked() 만 붙인 필드") {
            then("표에는 *Masked* 만, 스키마에는 x-masked 만 나간다") {
                val fields = listOf("phone" type STRING means "전화번호" masked "")
                val schema = JsonSchemaBuilder.fromFields(fields.map(SchemaField::of), SchemaDialect.JSON_SCHEMA)
                val phone = (schema["properties"] as Map<*, *>)["phone"] as Map<*, *>

                responseSnippet(MaskedLine("010"), listOf("phone" type STRING means "전화번호" masked "")) shouldContain
                    "*Masked* +\n전화번호"
                phone["x-masked"] shouldBe true
                phone.containsKey("x-masked-rule") shouldBe false
                phone["description"] shouldBe "전화번호\n\nMasked"
            }
        }
    })
