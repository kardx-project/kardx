package io.github.kardx.internal.spring

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

/** 주어진 속성만 가진 환경. 시스템 속성·환경 변수 소스는 뺀다. */
private fun environmentOf(vararg properties: Pair<String, Any>): StandardEnvironment = StandardEnvironment().apply {
    propertySources.toList()
        .forEach { propertySources.remove(it.name) }
    propertySources.addFirst(MapPropertySource("application.yml", properties.toMap()))
}

private fun unknownNames(vararg properties: Pair<String, Any>): List<String> =
    UnknownPropertyWarnings.unknownProperties(environmentOf(*properties))
        .map { it.first }

class UnknownPropertyWarningsTest :
    BehaviorSpec({
        given("알려진 키") {
            then("kebab-case·camelCase·밑줄·환경 변수·목록 색인 형태 모두 오탐하지 않는다") {
                unknownNames(
                    "kardx.mask-sensitive-headers" to true,
                    "kardx.maskSensitiveHeaders" to true,
                    "kardx.mask_sensitive_headers" to true,
                    "KARDX_MASK_SENSITIVE_HEADERS" to "true",
                    "kardx.common-include-files[0]" to "a.adoc",
                    "KARDX_COMMON_INCLUDE_FILES_0_" to "a.adoc",
                    "kardx.response-wrapper.class" to "com.example.Wrapper",
                    "kardx.responseWrapper.dataPath" to "data",
                    "KARDX_RESPONSE_WRAPPER_DATA_PATH" to "data",
                ).shouldBeEmpty()
            }

            then("boot 모듈 키(docs-path, startup-log.*)는 넘긴다") {
                unknownNames(
                    "kardx.docs-path" to "docs",
                    "kardx.startup-log.async-api.file" to "async/index.html",
                    "KARDX_STARTUP_LOG_ENABLED" to "false",
                ).shouldBeEmpty()
            }

            then("Gradle 플러그인이 넘기는 키(plugin-language)는 넘긴다") {
                unknownNames(
                    "kardx.plugin-language" to "en",
                    "KARDX_PLUGIN_LANGUAGE" to "en",
                ).shouldBeEmpty()
            }

            then("다른 접두사의 속성은 보지 않는다") {
                unknownNames("spring.application.name" to "demo", "restdocs.other" to "x").shouldBeEmpty()
            }
        }

        given("모르는 키") {
            then("찾아낸다") {
                unknownNames(
                    "kardx.mask-sensitive-header" to true,
                    "kardx.snippets.directory" to "out",
                    "KARDX_PRINT_REQUEST" to "true",
                ) shouldContainExactly listOf("kardx.mask-sensitive-header", "KARDX_PRINT_REQUEST")
            }

            then("경고에 가장 가까운 키를 제안한다") {
                val message = UnknownPropertyWarnings.message(
                    "kardx.mask-sensitive-header",
                    "application.yml",
                )

                message shouldContain "unknown property 'kardx.mask-sensitive-header' in [application.yml]"
                message shouldContain "Did you mean 'kardx.mask-sensitive-headers'?"
                UnknownPropertyWarnings.message("KARDX_PRINT_REQUEST", "systemEnvironment") shouldContain
                    "Did you mean 'kardx.print-requests'?"
                UnknownPropertyWarnings.message("kardx.snippetDirectory", "application.yml") shouldContain
                    "Did you mean 'kardx.snippets.directory'?"
            }

            then("가까운 키가 없으면 제안하지 않는다") {
                val message = UnknownPropertyWarnings.message("kardx.completely-different", "application.yml")

                message shouldNotContain "Did you mean"
                message shouldNotContain "renamed"
            }

            then("PropertyKeys 가 이 라이브러리 속성인지 가린다") {
                PropertyKeys.isOurs("kardx.x") shouldBe true
                PropertyKeys.isOurs("KARDX_X") shouldBe true
                PropertyKeys.isOurs("restdocs.x") shouldBe false
            }
        }

        given("기능별 묶음으로 옮기기 전의 평평한 키") {
            then("무시하지 않고 모르는 키로 찾아낸다") {
                unknownNames(
                    "kardx.open-api-enabled" to false,
                    "kardx.snippets-directory" to "out",
                    "kardx.asyncApiEnabled" to false,
                ) shouldContainExactly listOf(
                    "kardx.open-api-enabled",
                    "kardx.snippets-directory",
                    "kardx.asyncApiEnabled",
                )
            }

            then("경고가 표에 적힌 새 키를 그대로 알려 준다") {
                PropertyKeys.RENAMED.forEach { (old, new) ->
                    val message = UnknownPropertyWarnings.message("kardx.$old", "application.yml")

                    message shouldContain "It was renamed to 'kardx.$new'."
                    message shouldNotContain "Did you mean"
                }

                UnknownPropertyWarnings.message("kardx.openApiSchemaNames", "application.yml") shouldContain
                    "It was renamed to 'kardx.open-api.schema-names'."
            }

            then("새 키는 모두 지원하는 키이고, 환경 변수 이름은 옛 키와 같아 경고하지 않는다") {
                PropertyKeys.RENAMED.values.forEach { PropertyKeys.isKnown("kardx.$it") shouldBe true }

                unknownNames(
                    "kardx.open-api.enabled" to false,
                    "kardx.openApi.schemaNamesWithoutWrapper" to true,
                    "KARDX_OPEN_API_ENABLED" to "false",
                    "KARDX_SNIPPETS_DIRECTORY" to "out",
                ).shouldBeEmpty()
            }
        }
    })
