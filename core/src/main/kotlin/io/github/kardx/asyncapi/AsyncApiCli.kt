@file:JvmName("AsyncApiCli")

package io.github.kardx.asyncapi

import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * [AsyncApiGenerator] 의 명령행 진입점. 사용자 코드 없이 Gradle `JavaExec` 로 실행한다.
 *
 * 빌드 도구용 진입점이며 호환성 보장 대상(공개 API)이 아니다. 지켜지는 것은 JVM 클래스 이름
 * `io.github.kardx.asyncapi.AsyncApiCli` 와 아래 명령행 옵션뿐이다. 코드에서는 [AsyncApiGenerator.generate] 를 쓴다.
 *
 * ```kotlin
 * val asyncapi by tasks.registering(JavaExec::class) {
 *     dependsOn(tasks.test)
 *     classpath = sourceSets.test.get().runtimeClasspath
 *     mainClass.set("io.github.kardx.asyncapi.AsyncApiCli")
 *     args(
 *         "--snippets", "build/generated-snippets",
 *         "--output", "build/api-spec/asyncapi.yaml",
 *         "--viewer", "build/api-spec/asyncapi.html",
 *         "--title", "My API", "--version", "1.0.0",
 *         "--server", "stomp=stomp://localhost:8080/ws",
 *         "--security", "bearerAuth=bearer",
 *     )
 * }
 * ```
 *
 * | 옵션 | 기본값 | 설명 |
 * |---|---|---|
 * | `--snippets` | `build/generated-snippets` | REST Docs 스니펫 디렉터리 |
 * | `--output` | `build/api-spec/asyncapi.yaml` | AsyncAPI 문서 |
 * | `--viewer` | (없음) | 함께 만들 HTML 뷰어 |
 * | `--title`, `--version`, `--description` | `API`, `1.0.0`, 없음 | 문서 정보 |
 * | `--server name=protocol://host[/path]` | 없음 | 서버. 여러 번 줄 수 있다 |
 * | `--security name=bearer\|basic\|apiKey:<header\|query\|cookie>:<name>` | 없음 | 인증 방식. 여러 번 줄 수 있다 |
 */
internal fun main(args: Array<String>) {
    val options = try {
        AsyncApiCliOptions.parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("asyncapi: ${e.message}")
        exitProcess(2)
    }

    val count = AsyncApiGenerator.generate(options.snippets, options.output, options.info, options.viewer)

    if (count == 0) {
        println("AsyncAPI: no documented WebSocket, STOMP or SSE messages, nothing generated")
    } else {
        println("AsyncAPI: $count messages -> ${options.output}")
    }
}

/** [main] 의 명령행 옵션. */
internal data class AsyncApiCliOptions(
    val snippets: Path,
    val output: Path,
    val viewer: Path?,
    val info: AsyncApiInfo,
) {

    internal companion object {

        private val SERVER = Regex("""^([A-Za-z0-9_\-]+)=([a-z0-9+.-]+)://([^/]+)(/.*)?$""")
        private val SECURITY = Regex("""^([A-Za-z0-9_\-.]+)=(.+)$""")
        private const val SECURITY_FORMAT = "name=bearer|basic|apiKey:<header|query|cookie>:<name>"

        fun parse(args: Array<String>): AsyncApiCliOptions {
            var snippets = Path.of("build/generated-snippets")
            var output = Path.of("build/api-spec/asyncapi.yaml")
            var viewer: Path? = null
            var title = "API"
            var version = "1.0.0"
            var description: String? = null
            val servers = mutableListOf<AsyncApiServer>()
            val securitySchemes = mutableListOf<AsyncApiSecurityScheme>()

            var index = 0
            fun value(name: String): String = requireNotNull(args.getOrNull(++index)) { "$name needs a value" }

            while (index < args.size) {
                when (val name = args[index]) {
                    "--snippets" -> snippets = Path.of(value(name))
                    "--output" -> output = Path.of(value(name))
                    "--viewer" -> viewer = Path.of(value(name))
                    "--title" -> title = value(name)
                    "--version" -> version = value(name)
                    "--description" -> description = value(name)
                    "--server" -> servers += server(value(name))
                    "--security" -> securitySchemes += security(value(name))
                    else -> throw IllegalArgumentException("unknown option $name")
                }

                index++
            }

            val info = AsyncApiInfo(title, version, description, servers, securitySchemes)

            return AsyncApiCliOptions(snippets, output, viewer, info)
        }

        /** `stomp=stomp://localhost:8080/ws` → AsyncApiServer(stomp, localhost:8080, stomp, /ws) */
        private fun server(spec: String): AsyncApiServer {
            val match = requireNotNull(SERVER.matchEntire(spec)) {
                "--server expects name=protocol://host[/path], got $spec"
            }

            val groups = match.groupValues

            return AsyncApiServer(
                name = groups[1],
                host = groups[3],
                protocol = groups[2],
                pathname = groups[4].ifEmpty { null },
            )
        }

        /** `bearerAuth=bearer`, `basicAuth=basic`, `apiKeyAuth=apiKey:header:X-API-KEY` */
        private fun security(spec: String): AsyncApiSecurityScheme {
            val match = requireNotNull(SECURITY.matchEntire(spec)) { "--security expects $SECURITY_FORMAT, got $spec" }
            val (name, kind) = match.destructured
            val parts = kind.split(':', limit = 3)

            return when (parts.first().lowercase()) {
                "bearer" -> AsyncApiSecurityScheme.bearer(name).takeIf { parts.size == 1 }
                "basic" -> AsyncApiSecurityScheme.basic(name).takeIf { parts.size == 1 }
                "apikey" -> apiKey(name, parts)
                else -> null
            } ?: throw IllegalArgumentException("--security expects $SECURITY_FORMAT, got $spec")
        }

        private fun apiKey(name: String, parts: List<String>): AsyncApiSecurityScheme? {
            val location = AsyncApiApiKeyLocation.entries.firstOrNull { it.value == parts.getOrNull(1) }
            val parameterName = parts.getOrNull(2)

            if (location == null || parameterName.isNullOrBlank()) return null

            return AsyncApiSecurityScheme.apiKey(name, location, parameterName)
        }
    }
}
