package io.github.kardx.boot

import org.springframework.core.env.PropertyResolver

/**
 * 앱 설정과 클래스패스로 API 문서 주소를 계산한다. [ApiDocsStartupLogger] 가 로그로 남긴다.
 *
 * 모든 이름과 파일은 `kardx.startup-log.*` 로 바꿀 수 있다.
 *
 * ```yaml
 * restdocs:
 *   kotlin:
 *     language: en                      # 기본 문구의 언어 (en, ko)
 *     docs-path: docs                   # 정적 리소스 안의 문서 경로 (플러그인 bootJar.path = static/docs)
 *     startup-log:
 *       enabled: true
 *       title: API docs                  # 줄 머리
 *       rest-docs:  { label: REST Docs,     file: index.html }
 *       swagger-ui: { label: Swagger UI }   # 주소는 springdoc.swagger-ui.path
 *       open-api-viewer: { label: OpenAPI viewer, file: openapi.html }   # 플러그인 openApiViewer (Redoc·Scalar)
 *       open-api:   { label: OpenAPI spec,  file: openapi3.yaml }
 *       async-api:  { label: WebSocket/SSE, file: asyncapi.html }
 * ```
 *
 * 제목·이름을 적지 않으면 `kardx.language` (없으면 플러그인이 남긴 값, 그것도 없으면 `en`)의 기본 문구를 쓴다
 * ([StartupLogText]). `ko` 면 `API 문서`, `OpenAPI 뷰어` 처럼 한국어다.
 *
 * 문서 경로(`docs-path`)와 파일 이름(`*.file`)은 적지 않아도 된다. io.github.kardx-project Gradle 플러그인이 문서와 함께 실제
 * 경로·파일 이름을 [DEFAULTS_RESOURCE] 에 남기고, 여기서 그것을 기본값으로 읽는다 (yml 에 적으면 yml 이 이긴다).
 *
 * 항목마다 `enabled: false` 로 뺄 수 있다. 문서는 Spring Boot 정적 리소스 위치(`static/`, `public/`, `resources/`,
 * `META-INF/resources/`)에서 찾고, 실제로 있는 것만 보여 준다. 주소는 `server.ssl.enabled`, `server.address`,
 * 실제 포트(`local.server.port`), 앱 경로 접두사(서블릿 앱은 `server.servlet.context-path`, WebFlux 앱은
 * `spring.webflux.base-path`)로 만든다. 서블릿 앱의 정적 문서와 springdoc Swagger UI 는 DispatcherServlet 의 리소스 핸들러가
 * 서빙하므로 `spring.mvc.servlet.path` 도 붙인다.
 */
internal object ApiDocsLocations {
    /** core 의 `ExternalSettings.PREFIX` 와 같다 (boot 는 core 에 의존하지 않는다). */
    private const val PREFIX = "kardx."

    /**
     * Gradle 플러그인이 남기는 문서 위치 파일 (앱 클래스패스 기준). 키는 yml 키에서 [PREFIX] 를 뗀 것이다
     * (`docs-path`, `startup-log.open-api.file` …). 정적 리소스 위치가 아니라 웹으로 서빙되지 않는다.
     * 플러그인의 `AppDocsProperties.RESOURCE` 와 같다.
     */
    const val DEFAULTS_RESOURCE: String = "META-INF/kardx/docs-locations.properties"
    private const val LOG = "${PREFIX}startup-log."
    private val STATIC_LOCATIONS = listOf("static/", "public/", "resources/", "META-INF/resources/")
    private const val DEFAULT_SWAGGER_PATH = "/swagger-ui.html"
    private const val SSL_ENABLED = "server.ssl.enabled"
    private const val SERVLET_CONTEXT_PATH = "server.servlet.context-path"
    private const val WEBFLUX_BASE_PATH = "spring.webflux.base-path"
    private const val MVC_SERVLET_PATH = "spring.mvc.servlet.path"

    /** 문구 언어 키 (접두사 제외). core 의 `kardx.language` 와 같은 키이고, 플러그인 기본값 파일에도 이 이름으로 남는다. */
    private const val LANGUAGE = "language"

    /** 로그 항목 종류와 기본값. 설정 키는 `kardx.startup-log.<key>.label|file|enabled`. */
    enum class Kind(val key: String, val label: StartupLogText, val file: String?) {
        REST_DOCS("rest-docs", StartupLogText.REST_DOCS, "index.html"),
        SWAGGER_UI("swagger-ui", StartupLogText.SWAGGER_UI, null),
        OPEN_API_VIEWER("open-api-viewer", StartupLogText.OPEN_API_VIEWER, "openapi.html"),
        OPEN_API("open-api", StartupLogText.OPEN_API, "openapi3.yaml"),
        ASYNC_API("async-api", StartupLogText.ASYNC_API, "asyncapi.html"),
    }

    /** 설정 메타데이터·문서에 쓰는 전체 키 (접두사 `kardx.` 제외). */
    val KEYS: List<String> = listOf(LANGUAGE, "docs-path", "startup-log.enabled", "startup-log.title") +
        Kind.entries.flatMap { kind ->
            listOfNotNull("label", "enabled", "file".takeIf { kind.file != null })
                .map { "startup-log.${kind.key}.$it" }
        }

    /**
     * 남길 로그 줄. 웹 서버가 없거나 꺼져 있으면 빈 목록.
     *
     * @param reactive WebFlux 앱인지. 경로 접두사와 Swagger UI 기본 주소가 서블릿 앱과 다르다
     * @param defaults 문서 경로·파일 이름·언어의 기본값 ([DEFAULTS_RESOURCE], 키는 [PREFIX] 제외). yml 값이 먼저다
     * @param warn 설정 값이 잘못됐을 때 남길 경고 (지원하지 않는 언어). 앱 시작은 막지 않는다
     */
    fun lines(
        environment: PropertyResolver,
        resourceExists: (String) -> Boolean,
        swaggerUiAvailable: Boolean,
        reactive: Boolean = false,
        defaults: Map<String, String> = emptyMap(),
        warn: (String) -> Unit = {},
    ): List<String> {
        // server.port=-1 처럼 서버를 띄우지 않은 경우는 포트가 없거나 음수다
        val port = environment.getProperty("local.server.port")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

        if (!environment.flag("${LOG}enabled") || port == null) {
            return emptyList()
        }

        val language = language(environment, defaults, warn)
        val title = environment.getProperty("${LOG}title") ?: StartupLogText.TITLE.text(language)
        val base = baseUrl(environment, port, reactive)
        // 정적 문서와 springdoc Swagger UI 는 모두 DispatcherServlet 의 리소스 핸들러가 서빙한다
        val mvcBase = base + servletPath(environment, reactive)
        val docs = StaticDocs(environment, resourceExists, defaults)
        val entries = Kind.entries.mapNotNull { kind ->
            val label = environment.getProperty("${LOG}${kind.key}.label") ?: kind.label.text(language)
            val url = if (kind == Kind.SWAGGER_UI) {
                swaggerUrl(environment, swaggerUiAvailable, reactive)?.let { mvcBase + it }
            } else {
                docs.path(kind)?.let { mvcBase + it }
            }

            url?.let { label to it }
        }

        // 한글은 터미널에서 두 칸이므로 화면 폭으로 맞춘다
        val width = entries.maxOfOrNull { displayWidth(it.first) } ?: 0
        val lines = entries.map { (label, url) -> "$title ($label${" ".repeat(width - displayWidth(label))}) $url" }

        if (!docs.missing) {
            return lines
        }

        val missing = StartupLogText.MISSING.text(language, title, STATIC_LOCATIONS.first() + docs.docsPath)

        return lines + missing
    }

    /**
     * yml 의 `kardx.language`, 없으면 플러그인이 남긴 값, 그것도 없으면 영어.
     * 문서 설정의 오타로 앱이 뜨지 못하면 안 되므로 지원하지 않는 값은 [warn] 으로 한 번 알리고 영어로 쓴다.
     */
    private fun language(
        environment: PropertyResolver,
        defaults: Map<String, String>,
        warn: (String) -> Unit,
    ): StartupLogLanguage {
        val value = environment.getProperty(PREFIX + LANGUAGE)
            ?.takeIf { it.isNotBlank() }
            ?: defaults[LANGUAGE]
            ?: return StartupLogLanguage.EN
        val language = StartupLogLanguage.parse(value)

        if (language == null) {
            // boot 는 core 에 기대지 않으므로 코드를 글자로 쓴다 (core 의 RestDocsErrorCode.STARTUP_LANGUAGE_UNSUPPORTED)
            warn(
                "kardx[KDX-601]: $PREFIX$LANGUAGE: unsupported language '$value'. " +
                    "Use one of ${StartupLogLanguage.SUPPORTED}; falling back to en",
            )
        }

        return language ?: StartupLogLanguage.EN
    }

    /** 정적 리소스 위치([STATIC_LOCATIONS])에서 찾은 문서 파일. 처음 찾은 위치 하나만 쓴다. */
    private class StaticDocs(
        environment: PropertyResolver,
        private val resourceExists: (String) -> Boolean,
        defaults: Map<String, String>,
    ) {
        val docsPath: String = (environment.getProperty("${PREFIX}docs-path") ?: defaults["docs-path"] ?: "docs")
            .trim('/')

        // docs-path 가 비어 있으면(정적 리소스 루트에서 서빙) 슬래시 없이 잇는다
        private val directory = if (docsPath.isEmpty()) "" else "$docsPath/"
        private val files = Kind.entries.filter { it.file != null && environment.flag("${LOG}${it.key}.enabled") }
            .associateWith {
                val key = "startup-log.${it.key}.file"

                (environment.getProperty(PREFIX + key) ?: defaults[key] ?: it.file.orEmpty())
                    .trimStart('/')
            }

        private val location = STATIC_LOCATIONS.firstOrNull { loc ->
            files.values.any { resourceExists("$loc$directory$it") }
        }

        /** 켠 문서 파일이 어느 위치에도 없다. */
        val missing: Boolean get() = location == null && files.isNotEmpty()

        /** 앱 경로 기준 주소 (`/docs/index.html`). 파일이 없으면 null. */
        fun path(kind: Kind): String? {
            val file = files[kind] ?: return null

            return if (location != null && resourceExists("$location$directory$file")) "/$directory$file" else null
        }
    }

    /**
     * springdoc 이 있고 켜져 있으면 Swagger UI 경로. 기본 경로는 리다이렉트되므로 바로 열리는 주소를 준다.
     * springdoc 의 WebFlux UI 는 `/webjars` 아래에서 서빙한다.
     */
    private fun swaggerUrl(environment: PropertyResolver, available: Boolean, reactive: Boolean): String? {
        val enabled = available && environment.flag("${LOG}swagger-ui.enabled") &&
            environment.getProperty("springdoc.swagger-ui.enabled", "true") != "false"
        val path = environment.getProperty("springdoc.swagger-ui.path", DEFAULT_SWAGGER_PATH)

        return when {
            !enabled -> null
            path != DEFAULT_SWAGGER_PATH -> path
            reactive -> "/webjars/swagger-ui/index.html"
            else -> "/swagger-ui/index.html"
        }
    }

    private fun baseUrl(environment: PropertyResolver, port: Int, reactive: Boolean): String {
        val scheme = if (isSsl(environment)) "https" else "http"
        val address = environment.getProperty("server.address")
            ?.takeUnless { it == "0.0.0.0" || it == "::" }
        // IPv6 주소는 URL 에서 대괄호로 감싼다
        val host = address?.let { if (':' in it && !it.startsWith("[")) "[$it]" else it } ?: "localhost"
        val contextPath = environment.getProperty(if (reactive) WEBFLUX_BASE_PATH else SERVLET_CONTEXT_PATH)
            .orEmpty()
            .trimEnd('/')

        return "$scheme://$host:$port$contextPath"
    }

    /** 서블릿 앱의 DispatcherServlet 경로 (`/api`). 기본(`/`)이거나 WebFlux 앱이면 빈 문자열. */
    private fun servletPath(environment: PropertyResolver, reactive: Boolean): String {
        if (reactive) return ""

        val path = environment.getProperty(MVC_SERVLET_PATH)
            .orEmpty()
            .trim()
            .trim('/')

        return if (path.isEmpty()) "" else "/$path"
    }

    private fun displayWidth(text: String): Int = text.codePoints()
        .map { if (isWide(it)) 2 else 1 }
        .sum()

    /** 한글·한자·가나는 터미널에서 두 칸이다. */
    private fun isWide(codePoint: Int): Boolean = Character.UnicodeScript.of(codePoint) in WIDE_SCRIPTS

    private val WIDE_SCRIPTS = setOf(
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
    )

    /** Boot 는 `server.ssl.enabled` 를 끄지 않았고 인증서(bundle, key-store, certificate) 설정이 있으면 HTTPS 로 뜬다. */
    private fun isSsl(environment: PropertyResolver): Boolean {
        val explicitlyEnabled = environment.getProperty(SSL_ENABLED) == "true"
        val hasMaterial = SSL_MATERIAL.any { environment.getProperty("server.ssl.$it") != null }

        return environment.flag(SSL_ENABLED) && (explicitlyEnabled || hasMaterial)
    }

    private val SSL_MATERIAL = listOf("bundle", "key-store", "certificate")

    private fun PropertyResolver.flag(key: String): Boolean = getProperty(key, "true")
        .trim()
        .lowercase() != "false"
}
