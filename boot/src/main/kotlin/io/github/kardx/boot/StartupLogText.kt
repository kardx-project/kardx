package io.github.kardx.boot

/**
 * 시작 로그의 기본 문구 ([StartupLogLanguage] 를 따른다). yml 로 정한 제목·이름(`startup-log.title`, `*.label`)이 먼저다.
 * `{0}`, `{1}` 은 [text] 의 인자 자리다. 한국어 문구는 언어 설정을 넣기 전의 출력과 같다.
 */
internal enum class StartupLogText(private val english: String, private val korean: String) {
    TITLE("API docs", "API 문서"),
    REST_DOCS("REST Docs", "REST Docs"),
    SWAGGER_UI("Swagger UI", "Swagger UI"),
    OPEN_API_VIEWER("OpenAPI viewer", "OpenAPI 뷰어"),
    OPEN_API("OpenAPI spec", "OpenAPI 스펙"),
    ASYNC_API("WebSocket/SSE", "WebSocket·SSE"),
    MISSING(
        "{0} not found [KDX-602]: no documents in classpath:{1}. " +
            "Run ./gradlew apiDocs or restart the app (Gradle plugin generateOnRun)",
        "{0} 없음 [KDX-602]: classpath:{1} 에 문서가 없습니다. " +
            "./gradlew apiDocs 로 만들거나 앱을 다시 실행하세요 (Gradle 플러그인 generateOnRun)",
    ),
    ;

    /** [language] 의 문구. `{0}`, `{1}` … 자리에 [arguments] 를 넣는다. */
    fun text(language: StartupLogLanguage, vararg arguments: String): String {
        val template = when (language) {
            StartupLogLanguage.EN -> english
            StartupLogLanguage.KO -> korean
        }

        return ARGUMENT.replace(template) { match ->
            arguments.getOrNull(match.groupValues[1].toInt()) ?: match.value
        }
    }

    private companion object {
        val ARGUMENT = Regex("""\{(\d+)}""")
    }
}
