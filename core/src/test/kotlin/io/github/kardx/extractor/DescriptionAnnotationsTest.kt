package io.github.kardx.extractor
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import io.github.kardx.annotation.Description
import io.github.kardx.config.DescriptionAnnotation
import io.github.kardx.config.DescriptionResolver
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.config.orElse
import io.github.kardx.internal.introspection.FieldExtractor
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.mock.env.MockEnvironment
import kotlin.reflect.KClass

/** 생성자 파라미터에만 붙는 테스트용 어노테이션. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ParamText(val text: String)

/** public 이 아닌 Kotlin 어노테이션. 바이트코드에서 package-private 이 된다. */
@Retention(AnnotationRetention.RUNTIME)
private annotation class HiddenDoc(val text: String)

/** 반복 가능한 Kotlin 어노테이션. 두 번 이상 붙이면 컨테이너(`Note.Container`)로 묶인다. */
@Repeatable
@Retention(AnnotationRetention.RUNTIME)
annotation class Note(val value: String)

@Suppress("unused")
class HiddenDocumented(@param:HiddenDoc("숨은 설명") val hidden: String)

@Suppress("unused")
class NoteDocumented(
    @param:Note("") @param:Note("둘째 노트") val notes: String,
    @param:Note("한 노트") val note: String,
    @param:Note(" ") @param:Note("") val blankNotes: String,
)

@Suppress("unused")
class SwaggerDocumented(
    @param:Schema(description = "기본 위치") val plain: String,
    @field:Schema(description = "필드") val onField: String,
    @get:Schema(description = "getter") val onGetter: String,
    @param:Schema(title = "설명 없음") val noDescription: String,
)

@Suppress("unused")
class MixedDocumented(
    @Description("Description") @param:Schema(description = "스키마") val both: String,
    @Description("  ") @param:Schema(description = "공백 Description 다음") val blankDescription: String,
    @param:Schema(description = "스키마") @param:JsonPropertyDescription("잭슨") val schemaAndJackson: String,
    @param:Schema @param:JsonPropertyDescription("빈 Schema 다음") val emptySchema: String,
    @ParamText("파라미터 글") val param: String,
)

@Suppress("unused")
class SchemaQuery(@param:Schema(description = "검색어") val keyword: String?, val page: Int = 0)

private fun descriptions(type: KClass<*>): Map<String, Any?> = responseFieldsOf(type)
    .associate { it.descriptor.path to it.descriptor.description }

private fun <T> withAnnotations(block: RestDocsSettings.() -> Unit, action: () -> T): T =
    RestDocsConfiguration.withSettings(block, action)

class DescriptionAnnotationsTest :
    BehaviorSpec({
        given("description-annotations 를 정하지 않았을 때") {
            then("@Schema 는 읽지 않는다") {
                descriptions(SwaggerDocumented::class)["plain"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
            }
        }

        given("swagger 별칭") {
            then("Java 어노테이션 @Schema 를 어느 위치에 붙여도 읽는다") {
                withAnnotations({ descriptions.annotation("swagger") }) {
                    descriptions(SwaggerDocumented::class) shouldBe mapOf(
                        "plain" to "기본 위치",
                        "onField" to "필드",
                        "onGetter" to "getter",
                        "noDescription" to FieldExtractor.DEFAULT_DESCRIPTION,
                    )
                }
            }

            then("Java record component 와 Java bean 필드·getter 에서도 읽는다") {
                withAnnotations({ descriptions.annotation("swagger") }) {
                    descriptions(SchemaJavaRecord::class) shouldBe mapOf("code" to "record 스키마 설명")
                    descriptions(SchemaJavaBean::class) shouldBe mapOf(
                        "onField" to "필드 스키마 설명",
                        "onGetter" to "getter 스키마 설명",
                    )
                }
            }

            then("요청 본문과 쿼리 파라미터에도 같이 적용한다") {
                withAnnotations({ descriptions.annotation("swagger") }) {
                    requestFieldsOf(SwaggerDocumented::class)
                        .first()
                        .descriptor.description shouldBe "기본 위치"
                    parametersOf<SchemaQuery>()
                        .first { it.descriptor.name == "keyword" }
                        .descriptor.description shouldBe "검색어"
                }
            }
        }

        given("찾는 순서") {
            then("@Description 이 항상 먼저이고, 공백뿐인 @Description 은 건너뛴다") {
                withAnnotations({ descriptions.annotation("swagger") }) {
                    val fields = descriptions(MixedDocumented::class)

                    fields["both"] shouldBe "Description"
                    fields["blankDescription"] shouldBe "공백 Description 다음"
                }
            }

            then("그다음은 목록 순서다") {
                withAnnotations({
                    descriptions.annotation("swagger")
                    descriptions.annotation("jackson")
                }) {
                    descriptions(MixedDocumented::class)["schemaAndJackson"] shouldBe "스키마"
                }

                withAnnotations({
                    descriptions.annotation("jackson")
                    descriptions.annotation("swagger")
                }) {
                    descriptions(MixedDocumented::class)["schemaAndJackson"] shouldBe "잭슨"
                }
            }

            then("빈 값(@Schema 의 description 기본값 \"\")은 없는 것으로 보고 다음 어노테이션으로 넘어간다") {
                withAnnotations({ descriptions.annotations = listOf("swagger", "jackson").map(::parse) }) {
                    descriptions(MixedDocumented::class)["emptySchema"] shouldBe "빈 Schema 다음"
                }
            }
        }

        given("직접 만든 어노테이션") {
            then("FQCN#attribute, 클래스와 속성 이름, 함수 모두 같은 설명을 읽는다") {
                val specs: List<RestDocsSettings.() -> Unit> = listOf(
                    { descriptions.annotation("${ParamText::class.java.name}#text") },
                    { descriptions.annotation(ParamText::class, "text") },
                    { descriptions.from<ParamText> { it.text } },
                )

                specs.forEach { spec ->
                    withAnnotations(spec) {
                        descriptions(MixedDocumented::class)["param"] shouldBe "파라미터 글"
                    }
                }
            }
        }

        given("public 이 아닌 어노테이션") {
            then("Kotlin private annotation class 도 FQCN, 클래스와 속성 이름, 함수로 읽는다") {
                val specs: List<RestDocsSettings.() -> Unit> = listOf(
                    { descriptions.annotation("${HiddenDoc::class.java.name}#text") },
                    { descriptions.annotation(HiddenDoc::class, "text") },
                    { descriptions.from<HiddenDoc> { it.text } },
                )

                specs.forEach { spec ->
                    withAnnotations(spec) {
                        descriptions(HiddenDocumented::class)["hidden"] shouldBe "숨은 설명"
                    }
                }
            }

            then("Java package-private @interface 도 읽는다") {
                withAnnotations({ descriptions.annotation("io.github.kardx.extractor.PackagePrivateDoc") }) {
                    descriptions(OddlyDocumentedJavaRecord::class)["hidden"] shouldBe "package-private 설명"
                }
            }
        }

        given("반복 가능한 어노테이션(@Repeatable)") {
            then("두 번 이상 붙여 컨테이너로 묶여도 처음으로 비어 있지 않은 값을 읽는다 (Kotlin)") {
                withAnnotations({ descriptions.annotation(Note::class) }) {
                    val fields = descriptions(NoteDocumented::class)

                    fields["notes"] shouldBe "둘째 노트"
                    fields["note"] shouldBe "한 노트"
                    fields["blankNotes"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
                }
            }

            then("Java 반복 어노테이션도 같다") {
                withAnnotations({ descriptions.annotation(RepeatedDoc::class) }) {
                    val fields = descriptions(OddlyDocumentedJavaRecord::class)

                    fields["repeated"] shouldBe "반복 둘째"
                    fields["single"] shouldBe "반복 하나"
                }
            }
        }

        given("descriptions.from 의 함수가 빈 값을 돌려주면") {
            then("null·빈 문자열·공백뿐인 값은 없는 것으로 보고 다음 어노테이션으로 넘어간다") {
                val readers: List<(Schema) -> String?> = listOf({ null }, { "" }, { "  " })

                readers.forEach { reader ->
                    withAnnotations({
                        descriptions.from(Schema::class, reader)
                        descriptions.annotation("jackson")
                    }) {
                        descriptions(MixedDocumented::class)["schemaAndJackson"] shouldBe "잭슨"
                    }
                }
            }
        }

        given("사용자가 descriptions.resolver 를 바꾸면") {
            then("그 함수가 설명을 모두 정하고 description-annotations 는 쓰지 않는다") {
                withAnnotations({
                    descriptions.annotation("swagger")
                    descriptions.resolver = DescriptionResolver { it.findAnnotation<JsonPropertyDescription>()?.value }
                }) {
                    val fields = descriptions(MixedDocumented::class)

                    fields["both"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
                    fields["schemaAndJackson"] shouldBe "잭슨"
                }
            }

            then("orElse DEFAULT 로 조합하면 내 규칙이 없을 때 @Description·설정 어노테이션을 이어 본다") {
                val jackson = DescriptionResolver { it.findAnnotation<JsonPropertyDescription>()?.value }

                withAnnotations({
                    descriptions.annotation("swagger")
                    descriptions.resolver = jackson orElse DescriptionResolver.DEFAULT
                }) {
                    val fields = descriptions(MixedDocumented::class)

                    fields["both"] shouldBe "Description"
                    fields["schemaAndJackson"] shouldBe "잭슨"
                    fields["blankDescription"] shouldBe "공백 Description 다음"
                }
            }

            then("firstOf 로 기본 단계를 골라 순서를 바꿀 수 있고, 빈 값은 건너뛴다") {
                val blank = DescriptionResolver { " " }

                withAnnotations({
                    descriptions.annotation("swagger")
                    descriptions.resolver = DescriptionResolver.firstOf(
                        blank,
                        DescriptionResolver.CONFIGURED_ANNOTATIONS,
                        DescriptionResolver.DESCRIPTION_ANNOTATION,
                    )
                }) {
                    descriptions(MixedDocumented::class)["both"] shouldBe "스키마"
                }
            }

            then("PropertyContext 는 선언 이름과 선언 클래스를 준다") {
                val seen = mutableListOf<Pair<String, Class<*>?>>()

                withAnnotations({
                    descriptions.resolver = DescriptionResolver { context ->
                        seen += context.declaredName to context.declaringClass
                        null
                    }
                }) {
                    descriptions(SchemaQuery::class)
                }

                seen shouldContain ("keyword" to SchemaQuery::class.java)
            }
        }

        given("설정 계층") {
            val environment = MockEnvironment().withProperty("kardx.description-annotations", "swagger")

            then("yml 목록 뒤에 configure, 그 뒤에 withSettings 가 더한다. withSettings 는 범위를 벗어나지 않는다") {
                RestDocsConfiguration.applyEnvironment(environment)
                RestDocsConfiguration.configure { descriptions.annotation("jackson") }

                val expected = listOf(parse("swagger"), parse("jackson"))

                RestDocsConfiguration.current.descriptions.annotations shouldBe expected

                withAnnotations({ descriptions.annotation(ParamText::class, "text") }) {
                    RestDocsConfiguration.current.descriptions.annotations shouldBe
                        expected + parse("${ParamText::class.java.name}#text")
                    descriptions(MixedDocumented::class)["param"] shouldBe "파라미터 글"
                }

                RestDocsConfiguration.current.descriptions.annotations shouldBe expected
                descriptions(MixedDocumented::class)["param"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
            }
        }
    })

private fun parse(spec: String): DescriptionAnnotation = DescriptionAnnotation.parse(spec)
