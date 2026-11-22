package io.github.kardx.internal.sourcedocs

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.kardx.annotation.Description
import io.github.kardx.config.ExternalSettings
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.extractor.parametersOf
import io.github.kardx.extractor.responseFieldsOf
import io.github.kardx.internal.introspection.FieldExtractor
import io.github.kardx.internal.openapi.DocumentedExtras
import io.github.kardx.internal.spring.PropertyKeys
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File
import kotlin.reflect.KClass

enum class SourceDocsStatus { ACTIVE, LEFT }

open class SourceDocsAudited(val createdBy: String)

@Suppress("unused")
class SourceDocsUser(
    val id: Long,
    val nickname: String,
    @Description("어노테이션 설명") val annotated: String,
    @param:JsonProperty("display_name") val renamedName: String,
    val status: SourceDocsStatus,
    val memo: String?,
) : SourceDocsAudited("system") {
    val blank: String = ""
}

@Suppress("unused")
class SourceDocsQuery(val keyword: String?, val page: Int = 0)

@Suppress("unused")
class SourceDocsOuter {
    class Inner(val value: String)
}

@RestController
class SourceDocsController {
    @GetMapping("/source-docs/users/{id}")
    fun getUser(@PathVariable id: Long): Map<String, Any> = mapOf("id" to id)

    @GetMapping("/source-docs/search")
    fun search(@RequestParam q: String): Map<String, Any> = mapOf("q" to q)

    @GetMapping("/source-docs/none")
    fun none(): Map<String, Any> = mapOf("ok" to true)
}

private fun descriptions(type: KClass<*>): Map<String, Any?> = responseFieldsOf(type)
    .associate { it.descriptor.path to it.descriptor.description }

class SourceDocsTest :
    BehaviorSpec({
        val previous = System.getProperty(SourceDocs.PROPERTY)

        beforeSpec { useSourceDocsIndex(sourceDocsFixture("source-docs.json")) }

        afterSpec { useSourceDocsIndex(previous) }

        given("플러그인이 넘긴 KDoc·Javadoc 색인") {
            `when`("어노테이션 설명이 없는 프로퍼티를 추출하면") {
                val fields = descriptions(SourceDocsUser::class)

                then("색인의 설명을 쓴다") {
                    fields["id"] shouldBe "사용자 ID"
                    fields["nickname"] shouldBe "닉네임"
                }

                then("@Description 이 색인보다 앞선다") {
                    fields["annotated"] shouldBe "어노테이션 설명"
                }

                then("@JsonProperty 로 바꾼 이름도 소스 이름으로 찾는다") {
                    fields["display_name"] shouldBe "이름을 바꾼 프로퍼티"
                }

                then("상속한 프로퍼티는 선언한 상위 클래스에서 찾는다") {
                    fields["createdBy"] shouldBe "만든 사람"
                }

                then("Enum 상수 설명을 값 목록에 붙인다") {
                    (fields["status"] as String) shouldContain "ACTIVE : 활성 사용자"
                    (fields["status"] as String) shouldContain "LEFT : 탈퇴한 사용자"
                }

                then("색인에 없거나 공백뿐인 설명은 설명 없음으로 남는다 (누락 검증 대상)") {
                    fields["memo"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
                    fields["blank"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
                }
            }

            `when`("Java record 와 중첩 클래스를 추출하면") {
                then("record component 와 이진 이름(Outer\$Inner)으로 찾는다") {
                    descriptions(SourceDocsRecord::class)["title"] shouldBe "제목"
                    descriptions(SourceDocsOuter.Inner::class)["value"] shouldBe "안쪽 값"
                }
            }

            `when`("DTO 에서 파라미터를 추출하면") {
                val params = parametersOf(SourceDocsQuery::class).associate {
                    it.descriptor.name to it.descriptor.description
                }

                then("같은 색인 설명을 쓴다") {
                    params["keyword"] shouldBe "검색어"
                    params["page"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
                }
            }

            `when`("설정으로 끄면") {
                val fields = RestDocsConfiguration.withSettings({ descriptions.sourceDocs = false }) {
                    descriptions(SourceDocsUser::class)
                }

                then("색인을 쓰지 않는다") {
                    fields["id"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
                    fields["status"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION + "\n\nACTIVE\n\nLEFT"
                }
            }

            `when`("핸들러 메서드를 문서화하면") {
                val docs = DocumentedExtras(SourceDocsController())

                then("메서드 설명을 extras 의 sourceSummary·sourceDescription 으로 남긴다") {
                    val json = docs.run("source-docs-user", get("/source-docs/users/1")) { relaxed = true }

                    json["sourceSummary"] shouldBe "사용자를 조회한다."
                    json["sourceDescription"] shouldBe "사용자를 조회한다. 탈퇴한 사용자는 404."
                }

                then("인자 수까지 맞춘 키를 먼저 찾는다") {
                    val json = docs.run("source-docs-search", get("/source-docs/search").param("q", "a")) {
                        relaxed = true
                    }

                    json["sourceSummary"] shouldBe "검색한다."
                    json["sourceDescription"] shouldBe "검색한다. 인자 하나짜리."
                }

                then("색인에 없는 메서드는 키를 남기지 않는다") {
                    val json = docs.run("source-docs-none", get("/source-docs/none")) { relaxed = true }

                    json.containsKey("sourceSummary") shouldBe false
                    json.containsKey("sourceDescription") shouldBe false
                }
            }
        }

        given("읽을 수 없는 색인") {
            then("지원하지 않는 version 이면 기능을 끄고 실패하지 않는다") {
                useSourceDocsIndex(sourceDocsFixture("broken.json"))

                descriptions(SourceDocsUser::class)["id"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
            }

            then("파일이 없으면 기능을 끄고 실패하지 않는다") {
                useSourceDocsIndex(File(sourceDocsFixture("broken.json")).resolveSibling("missing.json").absolutePath)

                descriptions(SourceDocsUser::class)["id"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
            }

            then("속성이 없으면 기능이 꺼진다") {
                useSourceDocsIndex(null)

                descriptions(SourceDocsUser::class)["id"] shouldBe FieldExtractor.DEFAULT_DESCRIPTION
            }
        }

        given("yml 속성 kardx.source-docs.enabled") {
            then("false 면 설정을 끈다") {
                val settings = RestDocsSettings().apply(
                    ExternalSettings.from { if (it == "kardx.source-docs.enabled") "false" else null },
                )

                settings.descriptions.sourceDocs shouldBe false
                RestDocsSettings().descriptions.sourceDocs shouldBe true
            }

            then("플러그인이 넘기는 색인 경로 키와 함께 써도 둘 다 알려진 키다 (모르는 키 경고 없음)") {
                PropertyKeys.isKnown(SourceDocs.PROPERTY) shouldBe true
                PropertyKeys.isKnown("KARDX_SOURCE_DOCS") shouldBe true
                PropertyKeys.isKnown("kardx.source-docs.enabled") shouldBe true
                PropertyKeys.isKnown("KARDX_SOURCE_DOCS_ENABLED") shouldBe true
            }
        }
    })
