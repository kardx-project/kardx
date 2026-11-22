package io.github.kardx.internal.spring

import io.github.kardx.config.ExternalSettings
import io.github.kardx.internal.i18n.PluginLanguageWarning
import io.github.kardx.internal.sourcedocs.SourceDocs

/**
 * `kardx.*` 속성 이름을 알려진 키와 맞춰 본다. Spring 의 relaxed binding 과 같은 이름은 같은 키로 본다:
 * kebab-case(`mask-sensitive-headers`), camelCase(`maskSensitiveHeaders`), 밑줄(`mask_sensitive_headers`),
 * 환경 변수(`KARDX_MASK_SENSITIVE_HEADERS`), 목록 색인(`common-include-files[0]`, `..._FILES_0_`).
 */
internal object PropertyKeys {
    private const val ENV_PREFIX = "KARDX_"

    /**
     * boot 모듈(`kardx-boot`, 시작 로그)이 읽는 키. boot 는 core 에 의존하지 않고 키 목록은 boot 의
     * `ApiDocsLocations.KEYS` 에 있으므로, 여기서는 그 접두사만 알고 하위 키는 검사하지 않는다.
     */
    private val BOOT_KEYS = listOf("docs-path")
    private const val BOOT_PREFIX = "startup-log."

    /**
     * 기능별 묶음(`open-api.*`, `async-api.*`, `snippets.*`)으로 옮기기 전의 평평한 키 → 지금 키 (접두사 제외).
     * 옛 키는 읽지 않고, 경고가 새 키를 그대로 알려 준다. 환경 변수 이름은 두 키가 같아서
     * (`KARDX_OPEN_API_ENABLED`) 계속 동작하므로 여기서 다루지 않는다.
     */
    val RENAMED: Map<String, String> = mapOf(
        "snippets-directory" to "snippets.directory",
        "snippets-include-prefix" to "snippets.include-prefix",
        "open-api-enabled" to "open-api.enabled",
        "open-api-security-schemes" to "open-api.security-schemes",
        "open-api-global-headers" to "open-api.global-headers",
        "open-api-include-streams" to "open-api.include-streams",
        "open-api-schema-names" to "open-api.schema-names",
        "open-api-schema-names-without-wrapper" to "open-api.schema-names-without-wrapper",
        "open-api-error-schema" to "open-api.error-schema",
        "open-api-operation-id-rule" to "open-api.operation-id-rule",
        "async-api-enabled" to "async-api.enabled",
    )

    /**
     * 사용자가 쓰지 않고 Gradle 플러그인이 테스트 JVM 에 넘기는 키. 설정 메타데이터에는 없지만 모르는 키로 경고하지 않는다.
     */
    private val PLUGIN_KEYS = listOf(PluginLanguageWarning.PROPERTY, SourceDocs.PROPERTY)
        .map { it.removePrefix(ExternalSettings.PREFIX) }

    private val KNOWN: List<String> = ExternalSettings.KEYS
    private val KNOWN_ENV: Map<String, String> = KNOWN.associateBy(::envForm)

    private val INDEX = Regex("""\[\d+]""")
    private val ENV_INDEX = Regex("""_\d+(?=_|$)""")
    private val CAMEL_BOUNDARY = Regex("""(?<=[a-z0-9])(?=[A-Z])""")

    /** [name] 이 이 라이브러리 속성(`kardx.` 또는 `KARDX_` 로 시작)인가. */
    fun isOurs(name: String): Boolean = name.lowercase()
        .startsWith(ExternalSettings.PREFIX) || name.startsWith(ENV_PREFIX)

    /** 알려진 키(core 키, boot 모듈 키)인가. [isOurs] 인 이름만 넘긴다. */
    fun isKnown(name: String): Boolean {
        if (name.startsWith(ENV_PREFIX)) {
            val key = name.removePrefix(ENV_PREFIX)
                .replace(ENV_INDEX, "")
                .trimEnd('_')

            return key in KNOWN_ENV ||
                key in (BOOT_KEYS + PLUGIN_KEYS).map(::envForm) ||
                key.startsWith(envForm(BOOT_PREFIX))
        }

        val key = canonical(name)

        return key in KNOWN || key in BOOT_KEYS || key in PLUGIN_KEYS || key.startsWith(BOOT_PREFIX)
    }

    /** [name] 이 이름이 바뀐 옛 키이면 지금 키 (`kardx.` 포함), 아니면 null. [isOurs] 인 이름만 넘긴다. */
    fun renamedTo(name: String): String? {
        if (name.startsWith(ENV_PREFIX)) {
            return null
        }

        return RENAMED[canonical(name)]?.let { ExternalSettings.PREFIX + it }
    }

    /** 가장 가까운 core 키 (`kardx.` 포함). 충분히 가깝지 않으면 null. */
    fun suggestion(name: String): String? {
        val key = if (name.startsWith(ENV_PREFIX)) {
            name.removePrefix(ENV_PREFIX)
                .lowercase()
                .replace('_', '-')
        } else {
            canonical(name)
        }

        val (closest, distance) = (KNOWN + BOOT_KEYS).map { it to editDistance(key, it) }
            .minBy { it.second }
        val limit = maxOf(MIN_DISTANCE, closest.length / DISTANCE_RATIO)

        return closest.takeIf { distance <= limit }
            ?.let { ExternalSettings.PREFIX + it }
    }

    /** 접두사를 뺀 kebab-case 키. 목록 색인은 뺀다. */
    private fun canonical(name: String): String = name.substring(ExternalSettings.PREFIX.length)
        .replace(INDEX, "")
        .replace(CAMEL_BOUNDARY, "-")
        .replace('_', '-')
        .lowercase()

    private fun envForm(key: String): String = key.uppercase()
        .replace('.', '_')
        .replace('-', '_')

    /** 레벤슈타인 거리. 키는 짧으므로 단순한 동적 계획법으로 충분하다. */
    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }

        a.forEachIndexed { i, ca ->
            val current = IntArray(b.length + 1)

            current[0] = i + 1
            b.forEachIndexed { j, cb ->
                val substitution = previous[j] + if (ca == cb) 0 else 1

                current[j + 1] = minOf(previous[j + 1] + 1, current[j] + 1, substitution)
            }

            previous = current
        }

        return previous[b.length]
    }

    /** 제안할 최대 거리의 하한과, 키 길이에 비례하는 상한(길이 / 비율). */
    private const val MIN_DISTANCE = 2
    private const val DISTANCE_RATIO = 3
}
