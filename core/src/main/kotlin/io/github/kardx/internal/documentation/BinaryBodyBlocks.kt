package io.github.kardx.internal.documentation

import io.github.kardx.internal.http.FileBody
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.payload.RequestBodySnippet
import org.springframework.restdocs.payload.ResponseBodySnippet
import org.springframework.restdocs.snippet.Snippet

/**
 * 바이너리 본문의 `request-body`·`response-body` 스니펫을 코드 블록 언어 없이 다시 쓴다.
 *
 * REST Docs 는 Content-Type 의 하위 타입을 언어로 쓰므로 `<binary: 45 bytes>` 가 `[source,pdf]` 블록이 된다. 그런 언어는
 * 문법 강조기가 모르고 본문도 그 형식이 아니므로 `[source]` 로 둔다. 기본 스니펫(`requestBody()`·`responseBody()`)이 설정에
 * 있을 때만 다시 쓰고, 글자 본문(JSON, CSV …)은 그대로 둔다. REST Docs 는 기본 스니펫을 먼저 실행하므로 같은 파일을 덮어쓴다.
 */
internal object BinaryBodyBlocks : Snippet {
    /** 템플릿의 `{{#language}}` 를 비우는 속성. 모델을 만든 뒤에 덮어쓴다 */
    private val NO_LANGUAGE: Map<String, Any?> = mapOf("language" to null)

    override fun document(operation: Operation) {
        val defaults = operation.attributes[RestDocumentationGenerator.ATTRIBUTE_NAME_DEFAULT_SNIPPETS] as? List<*>
            ?: return
        val request = operation.request
        val response = operation.response

        if (defaults.any { it is RequestBodySnippet } && FileBody.isBinary(request.content, request.headers)) {
            PayloadDocumentation.requestBody(NO_LANGUAGE)
                .document(operation)
        }

        if (defaults.any { it is ResponseBodySnippet } && FileBody.isBinary(response.content, response.headers)) {
            PayloadDocumentation.responseBody(NO_LANGUAGE)
                .document(operation)
        }
    }
}
