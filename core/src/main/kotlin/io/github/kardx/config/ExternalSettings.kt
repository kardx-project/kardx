package io.github.kardx.config

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.diagnostics.RestDocsErrorCode.INVALID_ANNOTATION_SETTING
import io.github.kardx.diagnostics.RestDocsErrorCode.INVALID_PROPERTY
import io.github.kardx.internal.reflect.loadClassCatching
import io.github.kardx.openapi.OperationIdRule
import io.github.kardx.openapi.SchemaNames

/**
 * `kardx.*` 속성(application.yml, 시스템 속성, 환경 변수)을 [RestDocsSettings] 에 적용한다.
 *
 * 이름은 kebab-case 다. 목록은 쉼표로 구분하거나 YAML 목록(`key[0]`, `key[1]` …)으로 쓴다.
 * 함수로 주는 설정(설명 함수, 매퍼, 이름 변환)은 코드에서만 바꿀 수 있다. 설명을 읽을 어노테이션은 `description-annotations`,
 * 마스킹 표시로 읽을 어노테이션은 `masked-annotations`.
 *
 * ```yaml
 * restdocs:
 *   kotlin:
 *     snippets:
 *       directory: build/docs/snippets
 *     common-include-files: [landing.adoc]
 *     response-wrapper:
 *       class: com.example.ApiResponse
 *       data-path: data
 * ```
 */
internal object ExternalSettings {
    /** Gradle 플러그인의 `PluginConstants.PROPERTY_PREFIX`, boot 모듈의 `ApiDocsLocations.PREFIX` 와 같다. */
    const val PREFIX: String = "kardx."

    /** 목록·링크 속성에서 "없음"을 뜻하는 값 (`index-link: none`, `removed-response-headers: none`). */
    private const val NONE = "none"

    /** 속성 이름 (접두사 제외) → 설정에 적용하는 함수. 값이 없는 속성은 호출하지 않는다. */
    private val BINDINGS: Map<String, RestDocsSettings.(Reader, String) -> Unit> = linkedMapOf(
        "snippets.directory" to { r, k -> r.text(k)?.let { output.snippetsDirectory = it } },
        "asciidoc-directory" to { r, k -> r.text(k)?.let { output.asciidocDirectory = it } },
        "snippets.include-prefix" to { r, k -> r.text(k)?.let { document.includePrefix = it } },
        "common-include-files" to { r, k -> r.list(k)?.let { document.commonIncludeFiles = it } },
        "index-link" to { r, k -> r.text(k)?.let { document.indexLink = it.takeUnless { v -> v.equals(NONE, true) } } },
        "index-link-text" to { r, k -> r.text(k)?.let { document.indexLinkText = it } },
        "language" to { r, k -> r.text(k)?.let { language = docsLanguage(it, k) } },
        "document-snippets" to
            { r, k -> r.list(k)?.let { names -> document.snippets = names.map(::snippetFile).toSet() } },
        "document-section-order" to { r, k -> r.text(k)?.let { document.sectionOrder = sectionOrder(it, k) } },
        "masked-headers" to { r, k -> r.list(k)?.let { masking.headers = it.toSet() } },
        "masked-query-parameters" to { r, k -> r.list(k)?.let { masking.queryParameters = it.toSet() } },
        "mask-sensitive-headers" to { r, k -> if (r.boolean(k) == true) masking.maskSensitive() },
        "masked-header-value" to { r, k -> r.text(k)?.let { masking.replacement = it } },
        "removed-response-headers" to { r, k -> r.list(k)?.let { http.removedResponseHeaders = headerNames(it) } },
        "print-requests" to { r, k -> r.boolean(k)?.let { http.printRequests = it } },
        "apply-spring-security" to { r, k -> r.boolean(k)?.let { http.applySpringSecurity = it } },
        "open-api.enabled" to { r, k -> r.boolean(k)?.let { openApi.enabled = it } },
        "open-api.security-schemes" to { r, k -> r.boolean(k)?.let { openApi.securitySchemes = it } },
        "open-api.global-headers" to { r, k -> r.list(k)?.let { openApi.globalHeaders = it.toSet() } },
        "open-api.include-streams" to { r, k -> r.boolean(k)?.let { openApi.includeStreams = it } },
        "open-api.schema-names" to { r, k -> r.boolean(k)?.let { openApi.schemaNames = it } },
        "open-api.schema-names-without-wrapper" to
            { r, k -> r.boolean(k)?.let { openApi.schemaNamesWithoutWrapper = it } },
        "open-api.error-schema" to { r, k -> r.text(k)?.let { openApi.errorSchema = schemaName(it) } },
        "open-api.operation-id-rule" to
            { r, k -> r.text(k)?.let { openApi.operationIdRule = operationIdRule(it, k) } },
        "async-api.enabled" to { r, k -> r.boolean(k)?.let { asyncApi.enabled = it } },
        "wire-mock-stubs" to { r, k -> r.boolean(k)?.let { output.wireMockStubs = it } },
        "array-element-fields-optional" to { r, k -> r.boolean(k)?.let { extraction.arrayElementFieldsOptional = it } },
        "kotlinx-serialization" to { r, k -> r.boolean(k)?.let { extraction.kotlinxSerialization = it } },
        "max-extraction-depth" to { r, k -> r.int(k)?.let { extraction.maxDepth = it } },
        "enum-description-property" to { r, k -> r.text(k)?.let { extraction.enumDescriptionProperty = it } },
        "description-annotations" to { r, k ->
            r.list(k)?.let { specs ->
                descriptions.annotations = specs.map { parse(it, k, DescriptionAnnotation::parse) }
            }
        },
        "source-docs.enabled" to { r, k -> r.boolean(k)?.let { descriptions.sourceDocs = it } },
        "masked-annotations" to { r, k ->
            r.list(k)?.let { specs -> masking.annotations = specs.map { parse(it, k, MaskedAnnotation::parse) } }
        },
        "response-wrapper.class" to { r, k ->
            val className = r.text(k)
            val dataPath = r.text("response-wrapper.data-path")

            require(className != null || dataPath == null) {
                RestDocsErrorCode.WRAPPER_DATA_PATH_WITHOUT_CLASS.message(
                    "${PREFIX}response-wrapper.data-path needs response-wrapper.class",
                )
            }

            className?.let {
                extraction.responseWrapper =
                    ResponseWrapper(loadClass(it, k), dataPath ?: ResponseWrapper.DEFAULT_DATA_PATH)
            }
        },
    )

    /**
     * 지원하는 속성 이름 (접두사 제외). 설정 메타데이터 `META-INF/additional-spring-configuration-metadata.json`
     * (jar 에는 `spring-configuration-metadata.json` 으로 들어간다)의 속성과 같아야 한다 (IDE 자동 완성).
     */
    val KEYS: List<String> = BINDINGS.keys.toList() + "response-wrapper.data-path"

    /**
     * [source] 에서 속성을 읽어 설정에 적용하는 블록을 만든다. 값이 없는 속성은 건드리지 않는다. [source] 는 이 함수에서 한 번만
     * 읽는다 (블록은 그때 읽은 값을 적용한다).
     * 값이 잘못되면 어떤 속성인지 알려 주는 [IllegalArgumentException] 을 던진다.
     */
    fun from(source: (String) -> String?): RestDocsSettings.() -> Unit {
        val values = HashMap<String, String?>()
        val recording = Reader { key -> if (key in values) values[key] else source(key).also { values[key] = it } }

        RestDocsSettings().apply { BINDINGS.forEach { (key, bind) -> bind(recording, key) } } // 값을 미리 검증한다

        // 설정을 다시 만들 때마다(configure, 테스트 끝의 되돌리기) 속성 원본을 다시 읽지 않도록, 검증할 때 읽은 값을 쓴다.
        // 같은 값으로 같은 키를 읽으므로 두 번째 적용이 읽는 키는 모두 여기 있다
        val snapshot = Reader(values.toMap()::get)

        return { BINDINGS.forEach { (key, bind) -> bind(snapshot, key) } }
    }

    /** 시스템 속성과 환경 변수 (`KARDX_SNIPPETS_DIRECTORY`). Spring 이 없는 테스트에서 쓴다. */
    fun systemSource(): (String) -> String? = { key ->
        System.getProperty(key) ?: System.getenv(
            key.uppercase()
                .replace('.', '_')
                .replace('-', '_'),
        )
    }

    /** 상수 이름(`request-info`)이나 스니펫 파일 이름(`http-request`, `http-request.adoc`) 둘 다 받는다. */
    private fun snippetFile(name: String): SnippetFile {
        val trimmed = name.trim()

        return SnippetFile.builtInNamed(trimmed)
            ?: SnippetFile.BUILT_IN.firstOrNull { it.matches(trimmed.removeSuffix(".adoc") + ".adoc") }
            ?: throw IllegalArgumentException(
                INVALID_PROPERTY.message(
                    "${PREFIX}document-snippets: unknown snippet '$name'. " +
                        "Use one of ${SnippetFile.BUILT_IN.joinToString()}",
                ),
            )
    }

    /** 헤더 이름 목록. `none` 하나뿐이면 빈 집합 (하나도 빼지 않는다). */
    private fun headerNames(names: List<String>): Set<String> = names.filterNot { it.equals(NONE, ignoreCase = true) }
        .toSet()

    /** `method-name`·`display-name` (대소문자, `_`·`-` 구분 없음). */
    private fun sectionOrder(value: String, key: String): DocumentSectionOrder =
        DocumentSectionOrder.entries.firstOrNull { it.name.equals(value.replace('-', '_'), ignoreCase = true) }
            ?: throw IllegalArgumentException(
                INVALID_PROPERTY.message("$PREFIX$key: unknown order '$value'. Use method-name or display-name"),
            )

    /** `en`·`ko` (대소문자 구분 없음). */
    private fun docsLanguage(value: String, key: String): DocsLanguage {
        val supported = DocsLanguage.entries.joinToString { it.name.lowercase() }

        return DocsLanguage.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                INVALID_PROPERTY.message("$PREFIX$key: unsupported language '$value'. Use one of $supported"),
            )
    }

    /** `handler-method`·`controller-and-method`·`path`·`test-name` (대소문자, `_`·`-` 구분 없음). */
    private fun operationIdRule(value: String, key: String): OperationIdRule {
        val rules = OperationIdRule.entries.joinToString { it.propertyValue }

        return OperationIdRule.fromPropertyValue(value)
            ?: throw IllegalArgumentException(
                INVALID_PROPERTY.message("$PREFIX$key: unknown rule '$value'. Use one of $rules"),
            )
    }

    /** 어노테이션 목록의 한 항목 (`swagger`, `FQCN`, `FQCN#attribute`). 잘못되면 속성 이름을 앞에 붙여 알려 준다. */
    private fun <T> parse(spec: String, key: String, parser: (String) -> T): T = try {
        parser(spec)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(INVALID_ANNOTATION_SETTING.message("$PREFIX$key: ${e.message}"), e)
    }

    /** 클래스 이름(FQCN)이면 그 클래스의 스키마 이름, 아니면 그대로. */
    private fun schemaName(value: String): String = if ('.' in value) {
        SchemaNames.of(loadClass(value, "open-api.error-schema"))
    } else {
        value
    }

    private fun loadClass(name: String, key: String) = loadClassCatching(name)
        .getOrElse {
            throw IllegalArgumentException(INVALID_PROPERTY.message("$PREFIX$key: class '$name' not found"), it)
        }
        .kotlin

    private class Reader(private val source: (String) -> String?) {
        fun text(key: String): String? = source(PREFIX + key)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        /** 쉼표 목록 또는 YAML 목록(`key[0]` …). */
        fun list(key: String): List<String>? {
            text(key)?.let { value ->
                return value.split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
            }

            val indexed = generateSequence(0) { it + 1 }
                .map { source("$PREFIX$key[$it]")?.trim() }
                .takeWhile { it != null }
                .filterNotNull()
                .toList()

            return indexed.ifEmpty { null }
        }

        fun boolean(key: String): Boolean? = text(key)?.let {
            it.lowercase()
                .toBooleanStrictOrNull()
                ?: throw IllegalArgumentException(
                    INVALID_PROPERTY.message("$PREFIX$key: expected true or false, got '$it'"),
                )
        }

        fun int(key: String): Int? = text(key)?.let {
            it.toIntOrNull() ?: throw IllegalArgumentException(
                INVALID_PROPERTY.message("$PREFIX$key: expected a number, got '$it'"),
            )
        }
    }
}
