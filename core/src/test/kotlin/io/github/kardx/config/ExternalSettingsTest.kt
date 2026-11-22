package io.github.kardx.config

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.openapi.OperationIdRule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.mock.env.MockEnvironment

class WrapperForYaml(val code: Int, val data: Any?)

private fun settingsOf(vararg properties: Pair<String, String>): RestDocsSettings {
    val map = properties.associate { (key, value) -> "kardx.$key" to value }

    return RestDocsSettings().apply(ExternalSettings.from { map[it] })
}

/** yml 어노테이션 목록 항목이 잘못됐을 때의 메시지 (`KDX-004`, 속성 이름이 앞에 붙는다) */
private fun annotationError(detail: String): String =
    RestDocsErrorCode.INVALID_ANNOTATION_SETTING.message("kardx.$detail")

class ExternalSettingsTest :
    BehaviorSpec({
        given("kardx.* 속성") {
            then("모든 속성을 설정에 적용한다") {
                val settings = settingsOf(
                    "snippets.directory" to "out/snippets",
                    "asciidoc-directory" to "out/asciidoc",
                    "snippets.include-prefix" to "{custom}",
                    "common-include-files" to "landing.adoc, notice.adoc",
                    "index-link" to "../index.html",
                    "index-link-text" to "Back",
                    "language" to "Ko",
                    "open-api.global-headers" to "X-Tenant-Id, X-Client-Version",
                    "document-snippets" to "api-summary, response-fields",
                    "masked-headers" to "Authorization,X-Api-Key",
                    "masked-header-value" to "[hidden]",
                    "removed-response-headers" to "Date, X-Request-Id",
                    "print-requests" to "True",
                    "open-api.enabled" to "false",
                    "open-api.security-schemes" to "false",
                    "open-api.include-streams" to "true",
                    "open-api.schema-names" to "false",
                    "open-api.schema-names-without-wrapper" to "true",
                    "open-api.operation-id-rule" to "Controller_And_Method",
                    "document-section-order" to "display-name",
                    "async-api.enabled" to "false",
                    "wire-mock-stubs" to "true",
                    "array-element-fields-optional" to "true",
                    "kotlinx-serialization" to "false",
                    "max-extraction-depth" to "4",
                    "enum-description-property" to "label",
                    "description-annotations" to "swagger, jackson",
                    "masked-annotations" to "${TextDoc::class.java.name}, ${TextDoc::class.java.name}#text",
                    "response-wrapper.class" to WrapperForYaml::class.java.name,
                    "response-wrapper.data-path" to "result",
                )

                settings.output.snippetsDirectory shouldBe "out/snippets"
                settings.output.asciidocDirectory shouldBe "out/asciidoc"
                settings.document.includePrefix shouldBe "{custom}"
                settings.document.commonIncludeFiles shouldBe listOf("landing.adoc", "notice.adoc")
                settings.document.indexLink shouldBe "../index.html"
                settings.document.indexLinkText shouldBe "Back"
                settings.language shouldBe DocsLanguage.KO
                settings.openApi.globalHeaders shouldBe setOf("X-Tenant-Id", "X-Client-Version")
                settings.document.snippets shouldBe setOf(SnippetFile.API_SUMMARY, SnippetFile.RESPONSE_FIELDS)
                settings.masking.headers shouldBe setOf("Authorization", "X-Api-Key")
                settings.masking.replacement shouldBe "[hidden]"
                settings.http.removedResponseHeaders shouldBe setOf("Date", "X-Request-Id")
                settings.http.printRequests shouldBe true
                settings.openApi.enabled shouldBe false
                settings.openApi.securitySchemes shouldBe false
                settings.openApi.schemaNamesWithoutWrapper shouldBe true
                settings.document.sectionOrder shouldBe DocumentSectionOrder.DISPLAY_NAME
                settings.openApi.includeStreams shouldBe true
                settings.openApi.schemaNames shouldBe false
                settings.openApi.operationIdRule shouldBe OperationIdRule.CONTROLLER_AND_METHOD
                settings.asyncApi.enabled shouldBe false
                settings.output.wireMockStubs shouldBe true
                settings.extraction.arrayElementFieldsOptional shouldBe true
                settings.extraction.kotlinxSerialization shouldBe false
                settings.extraction.maxDepth shouldBe 4
                settings.extraction.enumDescriptionProperty shouldBe "label"
                settings.descriptions.annotations shouldBe
                    listOf(DescriptionAnnotation.parse("swagger"), DescriptionAnnotation.parse("jackson"))
                settings.masking.annotations shouldBe
                    listOf(MaskedAnnotation.of(TextDoc::class), MaskedAnnotation.of(TextDoc::class, "text"))
                settings.extraction.responseWrapper shouldBe ResponseWrapper(WrapperForYaml::class, "result")
            }

            then("language 는 en·ko 를 대소문자 없이 받고, 기본값은 en 이다") {
                settingsOf("language" to "EN").language shouldBe DocsLanguage.EN
                settingsOf("language" to " ko ").language shouldBe DocsLanguage.KO
                settingsOf().language shouldBe DocsLanguage.EN
                settingsOf().document.indexLinkText shouldBe null

                shouldThrow<IllegalArgumentException> { settingsOf("language" to "fr") }
                    .message shouldBe RestDocsErrorCode.INVALID_PROPERTY.message(
                    "kardx.language: unsupported language 'fr'. Use one of en, ko",
                )
            }

            then("language 는 시스템 속성으로도 받는다 (Gradle 플러그인이 테스트·CLI JVM 에 넘긴다)") {
                val previous = System.getProperty("kardx.language")

                System.setProperty("kardx.language", "ko")

                try {
                    RestDocsSettings().apply(ExternalSettings.from(ExternalSettings.systemSource()))
                        .language shouldBe DocsLanguage.KO
                } finally {
                    if (previous == null) {
                        System.clearProperty("kardx.language")
                    } else {
                        System.setProperty("kardx.language", previous)
                    }
                }
            }

            then("index-link: none 은 링크를 끈다") {
                settingsOf("index-link" to "none").document.indexLink shouldBe null
                settingsOf().document.indexLink shouldBe "index.html"
            }

            then("removed-response-headers 의 기본값은 Date 이고, none 이면 아무 헤더도 빼지 않는다") {
                settingsOf().http.removedResponseHeaders shouldBe setOf("Date")
                settingsOf("removed-response-headers" to "none").http.removedResponseHeaders shouldBe emptySet()
                settingsOf("removed-response-headers" to "Keep-Alive").http.removedResponseHeaders shouldBe
                    setOf("Keep-Alive")
            }

            then("속성 원본은 블록을 만들 때 한 번만 읽고, 블록을 여러 번 적용해도 다시 읽지 않는다") {
                var reads = 0
                val values = mapOf("kardx.print-requests" to "true")
                val block = ExternalSettings.from { key -> values[key].also { reads++ } }
                val readsWhenCreated = reads

                repeat(3) { RestDocsSettings().apply(block).http.printRequests shouldBe true }

                reads shouldBe readsWhenCreated
            }

            then("mask-sensitive-headers 는 흔한 민감 헤더를 켠다") {
                settingsOf("mask-sensitive-headers" to "true").masking.headers shouldBe
                    MaskingSettings.SENSITIVE_HEADERS
                settingsOf().masking.headers shouldBe emptySet()
            }

            then("open-api.error-schema 는 이름이나 클래스 이름(FQCN)을 받는다") {
                settingsOf("open-api.error-schema" to "ErrorResponse").openApi.errorSchema shouldBe "ErrorResponse"
                settingsOf("open-api.error-schema" to WrapperForYaml::class.java.name).openApi.errorSchema shouldBe
                    "WrapperForYaml"
            }

            then("document-snippets 는 상수 이름과 스니펫 파일 이름 둘 다 받는다") {
                settingsOf(
                    "document-snippets" to "request-info, curl-request, http-response.adoc",
                ).document.snippets shouldBe
                    setOf(SnippetFile.REQUEST_INFO, SnippetFile.CURL_INFO, SnippetFile.RESPONSE_INFO)
            }

            then("response-wrapper.data-path 만 있으면 class 가 필요하다고 알려 준다") {
                shouldThrow<IllegalArgumentException> { settingsOf("response-wrapper.data-path" to "result") }
                    .message shouldContain "response-wrapper.class"
            }

            then("description-annotations 는 별칭, FQCN, FQCN#attribute 를 YAML 목록으로도 받는다") {
                val annotations = settingsOf(
                    "description-annotations[0]" to "SWAGGER",
                    "description-annotations[1]" to "com.fasterxml.jackson.annotation.JsonPropertyDescription",
                    "description-annotations[2]" to "io.swagger.v3.oas.annotations.media.Schema#title",
                ).descriptions.annotations

                annotations.map { it.toString() } shouldBe listOf(
                    "io.swagger.v3.oas.annotations.media.Schema#description",
                    "com.fasterxml.jackson.annotation.JsonPropertyDescription#value",
                    "io.swagger.v3.oas.annotations.media.Schema#title",
                )
            }

            then("YAML 목록(key[0], key[1])도 읽는다") {
                settingsOf("common-include-files[0]" to "a.adoc", "common-include-files[1]" to "b.adoc")
                    .document.commonIncludeFiles shouldBe listOf("a.adoc", "b.adoc")
            }

            then("없는 속성은 기본값을 그대로 둔다") {
                settingsOf().masking.replacement shouldBe "****"
                settingsOf(
                    "response-wrapper.class" to WrapperForYaml::class.java.name,
                ).extraction.responseWrapper?.dataPath shouldBe "data"
            }

            then("잘못된 값은 속성 이름과 함께 알려 준다") {
                shouldThrow<IllegalArgumentException> { settingsOf("print-requests" to "yes") }
                    .message shouldContain "kardx.print-requests"
                shouldThrow<IllegalArgumentException> { settingsOf("document-snippets" to "nope") }
                    .message shouldContain "unknown snippet 'nope'"
                shouldThrow<IllegalArgumentException> { settingsOf("document-section-order" to "declaration") }
                    .message shouldContain "unknown order 'declaration'"
                shouldThrow<IllegalArgumentException> { settingsOf("open-api.operation-id-rule" to "method") }
                    .message shouldContain "unknown rule 'method'. Use one of handler-method, controller-and-method"
                shouldThrow<IllegalArgumentException> { settingsOf("response-wrapper.class" to "com.nope.Missing") }
                    .message shouldContain "com.nope.Missing"
                shouldThrow<IllegalArgumentException> {
                    settingsOf("description-annotations" to "jackson, com.nope.Doc")
                }.message shouldBe annotationError("description-annotations: class 'com.nope.Doc' not found")

                shouldThrow<IllegalArgumentException> {
                    settingsOf("description-annotations" to "${WrapperForYaml::class.java.name}#code")
                }.message shouldBe annotationError(
                    "description-annotations: class '${WrapperForYaml::class.java.name}' is not an annotation",
                )

                shouldThrow<IllegalArgumentException> {
                    settingsOf("masked-annotations" to "com.nope.Mask")
                }.message shouldBe annotationError("masked-annotations: class 'com.nope.Mask' not found")

                shouldThrow<IllegalArgumentException> {
                    settingsOf("masked-annotations" to "${TextDoc::class.java.name}#")
                }.message shouldBe annotationError(
                    "masked-annotations: '${TextDoc::class.java.name}#' has no attribute name after '#'",
                )
            }
        }

        given("코드로 같은 설정") {
            then("responseWrapper, openApi.errorSchema 를 클래스·타입으로 정한다") {
                RestDocsConfiguration.configure {
                    extraction.responseWrapper<WrapperForYaml>(dataPath = "result")
                    openApi.errorSchema<WrapperForYaml>()
                }

                RestDocsConfiguration.current.extraction.responseWrapper shouldBe
                    ResponseWrapper(WrapperForYaml::class, "result")
                RestDocsConfiguration.current.openApi.errorSchema shouldBe "WrapperForYaml"
            }

            then("yml 과 코드가 같은 결과를 낸다") {
                val fromYaml = settingsOf(
                    "response-wrapper.class" to WrapperForYaml::class.java.name,
                    "response-wrapper.data-path" to "result",
                    "open-api.error-schema" to WrapperForYaml::class.java.name,
                    "description-annotations" to "swagger, ${TextDoc::class.java.name}#text",
                )

                val fromCode = RestDocsSettings().apply {
                    extraction.responseWrapper(WrapperForYaml::class, "result")
                    openApi.errorSchema(WrapperForYaml::class)
                    descriptions.annotation(Schema::class, "description")
                    descriptions.annotation(TextDoc::class, "text")
                }

                fromYaml.extraction.responseWrapper shouldBe fromCode.extraction.responseWrapper
                fromYaml.openApi.errorSchema shouldBe fromCode.openApi.errorSchema
                fromYaml.descriptions.annotations shouldBe fromCode.descriptions.annotations
            }
        }

        given("처음 호출하는 configure") {
            then("블록을 한 번만 적용한다") {
                RestDocsConfiguration.configure {
                    document.commonIncludeFiles =
                        document.commonIncludeFiles + "landing.adoc"
                }

                RestDocsConfiguration.current.document.commonIncludeFiles shouldBe listOf("landing.adoc")
            }
        }

        given("우선순위") {
            val environment =
                MockEnvironment().withProperty("kardx.masked-header-value", "[yml]")
                    .withProperty("kardx.max-extraction-depth", "3")

            then("yml 은 기본값을 바꾸고, configure 가 yml 보다 우선한다") {
                RestDocsConfiguration.configure { extraction.maxDepth = 7 }
                RestDocsConfiguration.applyEnvironment(environment)

                RestDocsConfiguration.current.masking.replacement shouldBe "[yml]"
                RestDocsConfiguration.current.extraction.maxDepth shouldBe 7
            }

            then("reset 은 configure 값만 지우고 yml 값은 남긴다") {
                RestDocsConfiguration.applyEnvironment(environment)
                RestDocsConfiguration.configure { extraction.maxDepth = 7 }
                RestDocsConfiguration.reset()

                RestDocsConfiguration.current.extraction.maxDepth shouldBe 3
                RestDocsConfiguration.current.masking.replacement shouldBe "[yml]"
            }

            then("withSettings 가 가장 우선한다") {
                RestDocsConfiguration.applyEnvironment(environment)

                RestDocsConfiguration.withSettings({ masking.replacement = "[scoped]" }) {
                    RestDocsConfiguration.current.masking.replacement shouldBe "[scoped]"
                }
            }
        }
    })
