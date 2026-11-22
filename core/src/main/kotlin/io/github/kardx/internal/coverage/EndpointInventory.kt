package io.github.kardx.internal.coverage

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.jackson.serializeJson
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.output.writeTextAtomically
import org.springframework.context.ApplicationContext
import org.springframework.restdocs.snippet.Snippet
import java.io.File
import java.security.MessageDigest
import java.util.WeakHashMap

/**
 * 앱에 매핑된 엔드포인트 목록(인벤토리)을 스니펫 디렉터리에 남긴다. Gradle 플러그인(`apiDocsCoverage`)이 문서화한 operation 과
 * 비교해 문서화하지 않은 엔드포인트를 알린다.
 *
 * 파일은 `<스니펫 디렉터리>/.kardx/endpoints/<id>.json` 이고 내용은
 * `{"endpoints": [{"method": "GET", "path": "/api/v1/users/{id}", "handler": "com.example.UserController#getUser",
 * "deprecated": false, "apiVersion": null}]}` 이다. id 는 정렬한 엔드포인트 목록의 해시라 같은 앱의 컨텍스트는 같은 파일을 쓴다.
 *
 * 앱 컨텍스트마다(같은 스니펫 디렉터리 기준) 첫 문서화 때 한 번만 만든다. 컨텍스트를 모르는 요청과 standalone 설정
 * (`MockMvcBuilders.standaloneSetup`, `WebTestClient.bindToController`)은 앱 전체가 아니라 건너뛴다.
 * 이전 실행이 남긴 다른 id 의 파일은 지우지 않는다 (여러 테스트 JVM 이 함께 쓰므로). 스니펫 디렉터리 정리는 빌드가 맡는다.
 *
 * 인벤토리는 부산물이라, 만들거나 쓰다가 실패해도 문서화 테스트를 실패시키지 않고 경고만 남긴다. 실패한 컨텍스트는 남긴 것으로
 * 보지 않으므로 다음 문서화 때 한 번 더 시도한다 ([MAX_ATTEMPTS]). 계속 실패하면 문서화 요청마다 매핑을 다시 훑고 스택 트레이스를
 * 남기지 않도록 그만둔다. 스택 트레이스는 첫 실패에만 남긴다.
 */
internal object EndpointInventory {
    /** 스니펫 디렉터리 기준 경로. Gradle 플러그인과 같은 값이다 */
    const val DIRECTORY: String = ".kardx/endpoints"

    private const val ID_LENGTH = 16

    /** 컨텍스트·디렉터리마다 인벤토리를 남기려고 시도하는 최대 횟수 */
    private const val MAX_ATTEMPTS = 2

    private val LOGGER: System.Logger = System.getLogger(EndpointInventory::class.java.name)

    /** 컨텍스트 → 이미 남긴 스니펫 디렉터리. 컨텍스트가 닫혀 버려지면 함께 사라진다 */
    private val recorded = WeakHashMap<ApplicationContext, MutableSet<File>>()

    /** 컨텍스트 → 스니펫 디렉터리별 실패 횟수. [recorded] 와 같은 잠금을 쓴다 */
    private val failures = WeakHashMap<ApplicationContext, MutableMap<File, Int>>()

    /** MockMvc 의 standalone 컨텍스트 */
    private const val MOCK_MVC_STANDALONE = "org.springframework.test.web.servlet.setup.StubWebApplicationContext"

    /** `WebTestClient.bindToController` 가 만든 컨텍스트에만 있는 빈의 클래스 이름 앞부분 */
    private const val WEB_TEST_CLIENT_STANDALONE = "org.springframework.test.web.reactive.server.DefaultControllerSpec"

    /** `WebTestClient.bindToController` 가 만드는 컨텍스트의 클래스. 이 클래스일 때만 빈을 살펴본다 */
    private const val PLAIN_CONTEXT = "org.springframework.context.annotation.AnnotationConfigApplicationContext"

    /**
     * [applicationContext] 의 인벤토리를 남기는 스니펫. 컨텍스트가 아니면(모르면) null.
     * 스니펫이라 그 요청의 REST Docs 출력 디렉터리를 쓴다.
     */
    fun snippet(applicationContext: Any?): Snippet? {
        val context = applicationContext as? ApplicationContext ?: return null

        return Snippet { operation ->
            SnippetPaths.documentationContext(operation)?.outputDirectory?.let { record(context, it) }
        }
    }

    /**
     * [context] 의 인벤토리를 [outputDirectory] 에 남긴다. 이미 남겼거나 standalone 이거나 엔드포인트가 없으면 아무것도 하지 않는다.
     * 실패하면 경고만 남긴다.
     */
    fun record(context: ApplicationContext, outputDirectory: File) {
        if (isRecorded(context, outputDirectory) || failureCount(context, outputDirectory) >= MAX_ATTEMPTS ||
            isStandalone(context)
        ) {
            return
        }

        runCatching { write(context, outputDirectory) }
            .onSuccess { markRecorded(context, outputDirectory) }
            .onFailure { warn(it, markFailed(context, outputDirectory), outputDirectory) }
    }

    private fun warn(error: Throwable, failureCount: Int, outputDirectory: File) {
        val message = "could not write the endpoint inventory to ${File(outputDirectory, DIRECTORY)}, " +
            "so apiDocsCoverage may miss endpoints: ${error.message}"
        val code = RestDocsErrorCode.ENDPOINT_INVENTORY_WRITE_FAILED

        if (failureCount == 1) {
            LOGGER.log(System.Logger.Level.WARNING, code.message(message), error)
        } else {
            LOGGER.log(System.Logger.Level.WARNING, code.message("$message (giving up for this application context)"))
        }
    }

    private fun write(context: ApplicationContext, outputDirectory: File) {
        val endpoints = MappedEndpoints.of(context)

        if (endpoints.isEmpty()) {
            return
        }

        val file = File(File(outputDirectory, DIRECTORY), "${idOf(endpoints)}.json")

        if (!file.isFile) {
            val json = mapOf("endpoints" to endpoints.map(MappedEndpoint::toJson))

            file.toPath()
                .writeTextAtomically(serializeJson(RestDocsConfiguration.objectMapper(), json))
        }
    }

    private fun isRecorded(context: ApplicationContext, outputDirectory: File): Boolean = synchronized(recorded) {
        recorded[context]?.contains(outputDirectory.absoluteFile) == true
    }

    private fun failureCount(context: ApplicationContext, outputDirectory: File): Int = synchronized(recorded) {
        failures[context]?.get(outputDirectory.absoluteFile) ?: 0
    }

    /** 실패를 하나 더 세고 지금까지의 횟수를 돌려준다. */
    private fun markFailed(context: ApplicationContext, outputDirectory: File): Int = synchronized(recorded) {
        failures.getOrPut(context) { mutableMapOf() }
            .merge(outputDirectory.absoluteFile, 1, Int::plus) ?: 1
    }

    private fun markRecorded(context: ApplicationContext, outputDirectory: File) {
        synchronized(recorded) {
            recorded.getOrPut(context) { mutableSetOf() }
                .add(outputDirectory.absoluteFile)
        }
    }

    /** 앱 전체가 아니라 컨트롤러 몇 개만 묶은 standalone 컨텍스트인지. */
    private fun isStandalone(context: ApplicationContext): Boolean = when (context.javaClass.name) {
        MOCK_MVC_STANDALONE -> true

        PLAIN_CONTEXT -> context.beanDefinitionNames.any { name ->
            // 정의만 있고 타입을 알 수 없는 빈은 standalone 표식이 아니다
            runCatching { context.getType(name, false) }.getOrNull()
                ?.name
                ?.startsWith(WEB_TEST_CLIENT_STANDALONE) == true
        }

        else -> false
    }

    /** 정렬한 엔드포인트 목록의 SHA-256 앞 [ID_LENGTH] 글자. */
    private fun idOf(endpoints: List<MappedEndpoint>): String = MessageDigest.getInstance("SHA-256")
        .digest(endpoints.joinToString("\n") { it.key }.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(ID_LENGTH)
}
