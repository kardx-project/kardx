package io.github.kardx.internal.i18n

import io.github.kardx.config.DocsLanguage
import io.github.kardx.diagnostics.RestDocsErrorCode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 테스트가 쓰는 문서 언어와 Gradle 플러그인이 쓰는 언어가 다르면 한 번 경고한다.
 *
 * 플러그인은 테스트 JVM 에 자기 언어를 [PROPERTY] 로 알린다 (`kardx.language`, 정하지 않았으면 `en`).
 * yml(`kardx.language`)이나 `configure { language = ... }` 로만 언어를 바꾸면 플러그인이 만드는 문구(OpenAPI 뷰어,
 * 문서 목록, AsyncAPI 페이지)와 테스트가 만드는 문구의 언어가 섞인다. 플러그인 없이 돌면 속성이 없으므로 경고하지 않는다.
 *
 * @param log 경고 문구를 받는 함수. 기본은 `io.github.kardx.config` 로거의 WARNING
 */
internal class PluginLanguageWarning(
    private val log: (String) -> Unit = { LOGGER.log(System.Logger.Level.WARNING, it) },
) {
    private val warned = AtomicBoolean(false)

    /** [language] 가 플러그인 언어([pluginLanguage])와 다르면 이 인스턴스에서 처음 한 번만 경고한다. */
    fun check(language: DocsLanguage, pluginLanguage: String? = System.getProperty(PROPERTY)) {
        val message = message(language, pluginLanguage) ?: return

        if (warned.compareAndSet(false, true)) {
            log(message)
        }
    }

    companion object {
        /** Gradle 플러그인의 `PluginConstants.PLUGIN_LANGUAGE_PROPERTY` 와 같다. */
        const val PROPERTY: String = "kardx.plugin-language"

        /** JVM 에 하나뿐인 경고. 문서를 쓸 때마다 확인하지만 경고는 한 번이다. */
        val JVM: PluginLanguageWarning = PluginLanguageWarning()

        private val LOGGER: System.Logger = System.getLogger("io.github.kardx.config")

        /** 경고 문구. 플러그인이 알린 언어가 없거나 같으면 null. */
        fun message(language: DocsLanguage, pluginLanguage: String?): String? {
            val plugin = pluginLanguage?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val code = language.name.lowercase()

            if (plugin == code) return null

            return RestDocsErrorCode.LANGUAGE_MISMATCH.message(
                "the documentation language is '$code' (kardx.language or " +
                    "RestDocsConfiguration.configure) but the Gradle plugin uses '$plugin', so plugin-made pages and " +
                    "test-made snippets will mix languages. " +
                    "Set kardx { language = \"$code\" } in the build file.",
            )
        }
    }
}
