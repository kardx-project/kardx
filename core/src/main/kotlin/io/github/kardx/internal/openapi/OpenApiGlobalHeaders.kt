package io.github.kardx.internal.openapi

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.jackson.serializeJson
import io.github.kardx.internal.output.writeTextAtomically
import java.io.File
import java.nio.file.Files

/**
 * [io.github.kardx.config.OpenApiSettings.globalHeaders] 를 Gradle 플러그인에 전달한다.
 *
 * OpenAPI 는 테스트가 끝난 뒤 플러그인(`openapi3`)이 만들므로, 테스트 JVM 의 설정을 스니펫 디렉터리의 파일로 남긴다.
 * 플러그인은 이 헤더 이름을 헤더 파라미터 대신 `apiKey` 보안 스키마로 바꿔 Swagger UI 의 Authorize 에 넣는다.
 */
internal object OpenApiGlobalHeaders {
    /** 스니펫 디렉터리 기준 경로. 플러그인과 같은 값을 쓴다. */
    const val FILE_NAME: String = "kardx/openapi-global-headers.json"

    /** 마지막으로 반영한 (파일, 내용). 같은 값이면 파일을 건드리지 않는다 */
    private var published: Pair<File, String>? = null

    /**
     * [globalHeaders] 를 [snippetsDirectory] 아래 파일로 남긴다.
     * 비었으면 지난 실행이 남긴 파일을 지운다 (지운 헤더가 계속 변환되지 않게).
     */
    @Synchronized
    fun publish(globalHeaders: Set<String>, snippetsDirectory: File) {
        val headers = globalHeaders.filter { it.isNotBlank() }
            .sorted()
        val file = File(snippetsDirectory, FILE_NAME)
        val content = serializeJson(RestDocsConfiguration.objectMapper(), headers)

        if (published == file to content && file.isFile == headers.isNotEmpty()) {
            return
        }

        if (headers.isEmpty()) {
            Files.deleteIfExists(file.toPath())
        } else {
            file.writeTextAtomically(content)
        }

        published = file to content
    }
}
