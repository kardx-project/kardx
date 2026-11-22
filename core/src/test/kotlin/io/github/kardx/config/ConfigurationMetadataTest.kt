package io.github.kardx.config

import io.github.kardx.internal.jackson.DefaultObjectMapper
import io.github.kardx.internal.jackson.deserializeJson
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

// 소스는 additional-spring-configuration-metadata.json 이고, processResources 가 표준 이름으로 바꿔 넣는다
private val METADATA_JSON: String by lazy {
    requireNotNull(
        ConfigurationMetadataTest::class.java.classLoader.getResource("META-INF/spring-configuration-metadata.json"),
    ).readText()
}

/** 메타데이터의 `properties` 항목들. */
private fun metadataProperties(): List<Map<*, *>> = metadataList("properties")

/** 메타데이터의 [key] 배열(`properties`, `hints`)의 항목들. */
private fun metadataList(key: String): List<Map<*, *>> {
    val metadata = deserializeJson<Map<*, *>>(DefaultObjectMapper.instance, METADATA_JSON, Map::class.java)

    return (metadata[key] as List<*>).filterIsInstance<Map<*, *>>()
}

/** 속성 이름(접두사 제외)에 해당하는 [settings] 의 값. */
private fun settingValue(settings: RestDocsSettings, key: String): Any? = when (key) {
    "mask-sensitive-headers" -> settings.masking.headers.containsAll(MaskingSettings.SENSITIVE_HEADERS)
    "response-wrapper.class" -> settings.extraction.responseWrapper?.wrapperClass?.java?.name
    "response-wrapper.data-path" -> settings.extraction.responseWrapper?.dataPath ?: ResponseWrapper.DEFAULT_DATA_PATH
    "document-section-order" -> settings.document.sectionOrder.name.lowercase().replace('_', '-')
    "open-api.operation-id-rule" -> settings.openApi.operationIdRule.propertyValue
    "language" -> settings.language.name.lowercase()
    else -> requireNotNull(SETTING_VALUES[key]) { "$key 의 설정 값 규칙이 없다. SETTING_VALUES 에 더한다" }(settings)
}

/** yml 키 → 묶음 설정의 값. yml 키는 묶음과 상관없이 그대로이므로 여기서 짝을 맞춘다. */
private val SETTING_VALUES: Map<String, (RestDocsSettings) -> Any?> = mapOf(
    "snippets.directory" to { it.output.snippetsDirectory },
    "asciidoc-directory" to { it.output.asciidocDirectory },
    "wire-mock-stubs" to { it.output.wireMockStubs },
    "snippets.include-prefix" to { it.document.includePrefix },
    "common-include-files" to { it.document.commonIncludeFiles },
    "index-link" to { it.document.indexLink },
    "index-link-text" to { it.document.indexLinkText },
    "document-snippets" to { it.document.snippets },
    "masked-headers" to { it.masking.headers },
    "masked-query-parameters" to { it.masking.queryParameters },
    "masked-header-value" to { it.masking.replacement },
    "masked-annotations" to { it.masking.annotations },
    "removed-response-headers" to { it.http.removedResponseHeaders },
    "print-requests" to { it.http.printRequests },
    "apply-spring-security" to { it.http.applySpringSecurity },
    "open-api.enabled" to { it.openApi.enabled },
    "open-api.security-schemes" to { it.openApi.securitySchemes },
    "open-api.global-headers" to { it.openApi.globalHeaders },
    "open-api.include-streams" to { it.openApi.includeStreams },
    "open-api.schema-names" to { it.openApi.schemaNames },
    "open-api.schema-names-without-wrapper" to { it.openApi.schemaNamesWithoutWrapper },
    "open-api.error-schema" to { it.openApi.errorSchema },
    "async-api.enabled" to { it.asyncApi.enabled },
    "array-element-fields-optional" to { it.extraction.arrayElementFieldsOptional },
    "kotlinx-serialization" to { it.extraction.kotlinxSerialization },
    "max-extraction-depth" to { it.extraction.maxDepth },
    "enum-description-property" to { it.extraction.enumDescriptionProperty },
    "description-annotations" to { it.descriptions.annotations },
    "source-docs.enabled" to { it.descriptions.sourceDocs },
)

class ConfigurationMetadataTest :
    BehaviorSpec({
        given("IDE 용 설정 메타데이터 (META-INF/spring-configuration-metadata.json)") {
            then("빌드 결과에는 표준 이름만 있고 additional- 이름은 없다 (자동 완성 중복 방지)") {
                val additional = ConfigurationMetadataTest::class.java.classLoader
                    .getResource("META-INF/additional-spring-configuration-metadata.json")

                additional shouldBe null
            }

            then("지원하는 kardx.* 속성을 모두, 그리고 그것만 담는다") {
                val names =
                    Regex(""""name"\s*:\s*"kardx\.([^"]+)"""")
                        .findAll(METADATA_JSON)
                        .map { it.groupValues[1] }
                        .toSet()

                names shouldContainExactlyInAnyOrder ExternalSettings.KEYS
            }

            then("defaultValue 는 RestDocsSettings() 의 실제 기본값과 같고, 스칼라 기본값은 빠짐없이 적혀 있다") {
                val defaults = RestDocsSettings()

                metadataProperties().forEach { property ->
                    val key = (property["name"] as String).removePrefix(ExternalSettings.PREFIX)
                    val actual = settingValue(defaults, key)

                    withClue(key) {
                        if (property.containsKey("defaultValue")) {
                            property["defaultValue"] shouldBe actual
                        } else {
                            // 목록·null 기본값은 메타데이터에 쓰지 않는다. 문자열·숫자·불리언 기본값은 있어야 한다
                            (actual == null || actual is Collection<*>) shouldBe true
                        }
                    }
                }
            }

            then("description-annotations 의 hints 는 별칭마다 parse 가 읽는 대상(FQCN#attribute)을 적는다") {
                val hint = metadataList("hints")
                    .single { it["name"] == "${ExternalSettings.PREFIX}description-annotations" }
                val values = (hint["values"] as List<*>).filterIsInstance<Map<*, *>>()
                    .associate { it["value"] as String to it["description"] }

                values.keys shouldBe setOf("swagger", "jackson")
                values.forEach { (alias, target) ->
                    withClue(alias) { DescriptionAnnotation.parse(alias).toString() shouldBe target }
                }
            }
        }
    })
