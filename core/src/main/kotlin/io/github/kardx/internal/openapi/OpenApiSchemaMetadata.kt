package io.github.kardx.internal.openapi

import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.Snippet

/**
 * OpenAPI 스키마 이름이 어디서 왔는지. Gradle 플러그인이 이름 충돌을 풀 때 읽는다.
 *
 * @property name 리소스(`resource.json`)에 넣은 스키마 이름
 * @property automatic 추출한 타입에서 자동으로 정한 이름이면 true. 테스트·설정에서 직접 정한 이름이면 false
 * @property type 추출한 타입 (`com.shop.ApiResponse<com.shop.order.Item>`). 모르면 null
 * @property candidates 자동 이름이 겹칠 때 차례로 쓸 이름 ([io.github.kardx.openapi.SchemaNames.candidates])
 */
internal class SchemaOrigin(
    val name: String,
    val automatic: Boolean,
    val type: String?,
    val candidates: List<String>,
) {
    fun toJson(): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "automatic" to automatic,
        "type" to type,
        "candidates" to candidates,
    )
}

/**
 * 요청·응답 스키마 이름의 출처를 OpenAPI 조각(`resource.json`) 옆의 [FILE_NAME] 에 남기는 스니펫.
 * `.adoc` 이 아니므로 AsciiDoc 문서에는 들어가지 않는다.
 */
internal class OpenApiSchemaMetadata(private val request: SchemaOrigin?, private val response: SchemaOrigin?) :
    Snippet {
    override fun document(operation: Operation) {
        if (request == null && response == null) return

        writeBesideResource(
            operation,
            FILE_NAME,
            linkedMapOf("request" to request?.toJson(), "response" to response?.toJson()),
        )
    }

    companion object {
        /** Gradle 플러그인의 `OpenApiSchemaNames.METADATA_FILE` 과 같다. */
        const val FILE_NAME: String = "openapi-schemas.json"
    }
}
