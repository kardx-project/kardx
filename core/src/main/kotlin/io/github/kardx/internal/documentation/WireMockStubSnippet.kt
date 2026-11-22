package io.github.kardx.internal.documentation

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.http.HttpHeadersCompat
import io.github.kardx.internal.http.SensitiveParameterMasking
import io.github.kardx.internal.http.TextualContent
import io.github.kardx.internal.jackson.prettyJsonOrNull
import io.github.kardx.internal.jackson.serializeJson
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.output.writeOperationFile
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.operation.OperationRequest
import org.springframework.restdocs.operation.OperationResponse
import org.springframework.restdocs.snippet.Snippet
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

/**
 * 문서화한 요청·응답으로 WireMock 매핑(stub) JSON 을 쓰는 스니펫. WireMock 라이브러리 없이 매핑 형식만 맞춘다.
 * 위치는 `<스니펫 디렉터리>/wiremock/mappings/<class-name>/<method-name>.json` 이라서
 * `wiremock --root-dir build/generated-snippets/wiremock` 으로 바로 띄울 수 있다.
 *
 * - 요청: 메서드, 경로(`urlPath`, 실제 값), 쿼리 파라미터(`equalTo`), JSON 본문(`equalToJson`, 배열 순서·추가 필드 무시).
 *   헤더는 맞추지 않는다 (인증 헤더는 마스킹된 값이라 맞출 수 없다)
 * - 응답: 상태, 헤더(`Content-Length`·`Transfer-Encoding` 제외), 본문 (글자 형식이면 `body`, 아니면 `base64Body`).
 *   글자가 아닌 본문은 문서 스니펫에서 `<binary: N bytes>` 로 바뀌므로([io.github.kardx.internal.http.BinaryBodyPlaceholder])
 *   [rawResponseBody] 의 원래 바이트를 쓴다
 * - `id` 는 문서 식별자로 만든 UUID 라 다시 만들어도 같다
 *
 * [io.github.kardx.config.OutputSettings.output.wireMockStubs] 가 켜져 있을 때만 `andDocument` 가 넣는다.
 *
 * @param rawResponseBody 전처리 전의 응답 본문 (MockMvc `MockHttpServletResponse` 등). 모르면 null 이고 문서화한 본문을 쓴다
 */
internal class WireMockStubSnippet(private val rawResponseBody: () -> ByteArray? = { null }) : Snippet {
    override fun document(operation: Operation) {
        val context = SnippetPaths.documentationContext(operation) ?: return
        val identifier = SnippetPaths.documentIdentifier(operation, context)
        val mapping = linkedMapOf(
            "id" to UUID.nameUUIDFromBytes(identifier.toByteArray(StandardCharsets.UTF_8)).toString(),
            "name" to identifier,
            "request" to request(operation.request),
            "response" to response(operation.response),
        )

        val mapper = RestDocsConfiguration.objectMapper()
        val json = serializeJson(mapper, mapping)

        File(context.outputDirectory, "$DIRECTORY/$identifier.json")
            .writeOperationFile((prettyJsonOrNull(json, mapper) ?: json) + "\n")
    }

    private fun request(request: OperationRequest): Map<String, Any> {
        val body = String(request.content, StandardCharsets.UTF_8)

        return linkedMapOf<String, Any>(
            "method" to request.method.name(),
            "urlPath" to request.uri.rawPath,
        ).apply {
            queryParameters(request.uri.rawQuery).takeIf { it.isNotEmpty() }
                ?.let { put("queryParameters", it) }

            if (body.isNotBlank() && isJson(request.headers)) {
                put(
                    "bodyPatterns",
                    listOf(mapOf("equalToJson" to body, "ignoreArrayOrder" to true, "ignoreExtraElements" to true)),
                )
            }
        }
    }

    private fun response(response: OperationResponse): Map<String, Any> {
        val headers = HttpHeadersCompat.names(response.headers)
            .filterNot { it.equals(HttpHeaders.CONTENT_LENGTH, true) || it.equals(HttpHeaders.TRANSFER_ENCODING, true) }
            .associateWith { name ->
                val values = HttpHeadersCompat.values(response.headers, name)

                values.singleOrNull() ?: values
            }

        return linkedMapOf<String, Any>("status" to response.status.value()).apply {
            if (headers.isNotEmpty()) {
                put("headers", headers)
            }

            if (response.content.isNotEmpty()) {
                if (TextualContent.isTextual(contentType(response.headers))) {
                    put("body", String(response.content, StandardCharsets.UTF_8))
                } else {
                    val bytes = rawResponseBody() ?: response.content

                    put("base64Body", Base64.getEncoder().encodeToString(bytes))
                }
            }
        }
    }

    /**
     * `a=1&b=x%20y&a=2` → `{a: {hasExactly: [...]}, b: {equalTo: "x y"}}`.
     * 가리는 파라미터([SensitiveParameterMasking])는 가린 값과 맞추면 스텁이 쓸모없으므로 아무 값이나 맞춘다.
     */
    private fun queryParameters(rawQuery: String?): Map<String, Any> = rawQuery.orEmpty()
        .split('&')
        .filter { it.isNotEmpty() }
        .map { pair -> pair.substringBefore('=').decoded() to pair.substringAfter('=', "").decoded() }
        .groupBy({ it.first }, { it.second })
        .mapValues { (name, values) ->
            when {
                SensitiveParameterMasking.isMasked(name) -> mapOf("matches" to ".*")

                else -> values.singleOrNull()
                    ?.let { mapOf("equalTo" to it) }
                    ?: mapOf("hasExactly" to values.map { mapOf("equalTo" to it) })
            }
        }

    private fun isJson(headers: HttpHeaders): Boolean = contentType(headers).contains("json")

    private fun contentType(headers: HttpHeaders): String = HttpHeadersCompat.values(headers, HttpHeaders.CONTENT_TYPE)
        .firstOrNull()
        .orEmpty()
        .lowercase()

    private fun String.decoded(): String = URLDecoder.decode(this, StandardCharsets.UTF_8)

    companion object {
        /** 스니펫 디렉터리 안의 매핑 위치. Gradle 플러그인의 `PluginConstants.WIRE_MOCK_DIR` 과 맞춘다. */
        const val DIRECTORY: String = "wiremock/mappings"
    }
}
