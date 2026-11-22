package io.github.kardx.internal.sse

import io.github.kardx.dsl.MessageProtocol
import io.github.kardx.dsl.RestDocsEvent
import io.github.kardx.dsl.RestDocsHeader
import io.github.kardx.internal.asyncapi.AsyncApiMetadata
import io.github.kardx.internal.asyncapi.EventRecord
import io.github.kardx.internal.asyncapi.EventStreamRecord
import io.github.kardx.internal.diagnostics.diagnosed
import io.github.kardx.internal.http.HttpHeadersCompat
import io.github.kardx.internal.output.SnippetPaths
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.snippet.SnippetException
import org.springframework.restdocs.snippet.TemplatedSnippet

/**
 * SSE 응답을 문서화한다.
 *
 * - `sse-events` 스니펫: 이벤트 이름·필수 여부·설명 표
 * - `response-fields-event-<이름>` 스니펫: 이벤트 `data` 의 필드 표. REST Docs 필드 스니펫이 실제 데이터로 검증한다
 * - `sse-events.asyncapi.json`: AsyncAPI 문서용 메타데이터
 *
 * 문서화한 필수 이벤트가 스트림에 없거나, [relaxed] 가 아닌데 문서화하지 않은 이벤트가 있으면 실패한다.
 * 필드 검증은 스트림에서 그 이름의 첫 이벤트로 한다. 스트림에 없는 optional 이벤트는 필드 표를 만들지 않는다.
 */
internal class EventStreamSnippet(
    private val events: List<RestDocsEvent>,
    private val relaxed: Boolean,
    private val requestHeaders: List<RestDocsHeader> = emptyList(),
) : TemplatedSnippet(SNIPPET_NAME, null) {

    override fun document(operation: Operation) {
        super.document(operation)

        val received = receivedNames(operation)

        events.filter { it.fields.isNotEmpty() && it.name in received }
            .forEach { event ->
                val extractor = SseEventDataExtractor(event.name)
                val descriptors = event.fields.map { it.descriptor }
                val snippet = if (relaxed) {
                    PayloadDocumentation.relaxedResponseFields(extractor, descriptors)
                } else {
                    PayloadDocumentation.responseFields(extractor, descriptors)
                }

                snippet.diagnosed("event data fields", "event '${event.name}'") {
                    extractor.extractSubsection(it.response.content, null)
                }.document(operation)
            }

        // 모든 검증을 통과한 뒤에만 AsyncAPI 문서에 넣는다.
        writeAsyncApiMetadata(operation)
    }

    override fun createModel(operation: Operation): MutableMap<String, Any> {
        val received = receivedNames(operation)
        val missing = events.filter { !it.optional && it.name !in received }
            .map { it.name }
        val undocumented = if (relaxed) {
            emptyList()
        } else {
            val documented = events.map { it.name }
                .toSet()

            (received - documented).toList()
        }

        if (missing.isNotEmpty() || undocumented.isNotEmpty()) {
            val messages = listOfNotNull(
                undocumented.takeIf { it.isNotEmpty() }
                    ?.let { "Events with the following names were not documented: $it" },
                missing.takeIf { it.isNotEmpty() }
                    ?.let { "Events with the following names were not found in the stream: $it" },
            )

            throw SnippetException(messages.joinToString(". "))
        }

        val rows = events.map { event ->
            hashMapOf<String, Any>(
                "name" to event.name,
                "description" to event.description,
                "optional" to event.optional,
            )
        }

        return hashMapOf("events" to rows)
    }

    /** AsyncAPI 문서용 메타데이터. 이벤트마다 스트림의 첫 data 를 예시로 남긴다. */
    private fun writeAsyncApiMetadata(operation: Operation) {
        val context = SnippetPaths.documentationContext(operation) ?: return
        val stream = SseParser.parse(String(operation.response.content, Charsets.UTF_8))
        val address = operation.attributes[RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE] as? String
            ?: operation.request.uri.rawPath

        AsyncApiMetadata.write(
            context,
            SNIPPET_NAME,
            EventStreamRecord(
                protocol = MessageProtocol.SSE.displayName,
                address = address,
                requestHeaders = AsyncApiMetadata.headers(requestHeaders, requestHeaderValues(operation)),
                events = events.map { event ->
                    EventRecord(
                        name = event.name,
                        description = event.description,
                        optional = event.optional,
                        payload = stream.firstOrNull { it.name == event.name }?.data,
                        fields = AsyncApiMetadata.fields(event.fields),
                    )
                },
            ),
        )
    }

    private fun receivedNames(operation: Operation): Set<String> =
        SseParser.parse(String(operation.response.content, Charsets.UTF_8))
            .map { it.name }
            .toSet()

    companion object {
        const val SNIPPET_NAME = "sse-events"
    }
}

/** 요청 헤더의 첫 값 (이름 소문자). AsyncAPI 예시 값으로 쓰고, 마스킹 규칙을 따른다. */
private fun requestHeaderValues(operation: Operation): Map<String, String> {
    val headers = operation.request.headers

    return HttpHeadersCompat.names(headers)
        .associate {
            it.lowercase() to HttpHeadersCompat.values(headers, it)
                .first()
        }
}
