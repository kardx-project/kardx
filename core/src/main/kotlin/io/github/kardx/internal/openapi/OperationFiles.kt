package io.github.kardx.internal.openapi

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.jackson.serializeJson
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.output.writeOperationFile
import org.springframework.restdocs.operation.Operation
import java.io.File

/**
 * OpenAPI 조각(`resource.json`) 옆에 [fileName] 으로 [json] 을 쓴다. Gradle 플러그인이 같은 디렉터리에서 함께 읽는다.
 * 문서 식별자의 `{class-name}/{method-name}` 은 REST Docs·epages 와 같은 규칙으로 바꾼다. 문서화 문맥이 없으면 쓰지 않는다.
 */
internal fun writeBesideResource(operation: Operation, fileName: String, json: Map<String, Any?>) {
    val context = SnippetPaths.documentationContext(operation) ?: return
    val directory = SnippetPaths.documentIdentifier(operation, context)

    File(File(context.outputDirectory, directory), fileName).toPath()
        .writeOperationFile(serializeJson(RestDocsConfiguration.objectMapper(), json))
}
