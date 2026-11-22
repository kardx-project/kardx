package io.github.kardx.internal.documentation

import io.github.kardx.internal.http.FileBody
import io.github.kardx.internal.i18n.DocsText
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.TemplatedSnippet

/**
 * 파일 본문을 설명하는 `request-file`·`response-file` 스니펫. 바이너리 본문은 스니펫에 `<binary: N bytes>` 로만 남으므로
 * 그 본문이 무엇인지 표로 알려 준다.
 *
 * ```asciidoc
 * [cols="1h,3"]
 * |===
 * |Description
 * |Product manual PDF
 *
 * |Media type
 * |`+application/pdf+`
 * ...
 * |===
 * ```
 *
 * 행은 설명([description], 적었을 때만), `charset` 을 뺀 미디어 타입, `Content-Disposition` 의 파일 이름(있을 때만),
 * 테스트 본문의 원래 크기다. 머리글은 [DocsText] (설정 언어)를 따른다. 본문이 바이너리가 아니고 설명도 없으면(JSON 응답 등)
 * 파일을 만들지 않는다. 템플릿은 라이브러리에 포함된 `org/springframework/restdocs/templates/<형식>/file-body.snippet` 이다.
 *
 * @param body 문서화할 쪽(요청·응답)의 본문과 헤더
 */
internal class FileBodySnippet private constructor(
    snippetName: String,
    private val description: String?,
    private val body: (Operation) -> Pair<ByteArray, HttpHeaders>,
) : TemplatedSnippet(snippetName, TEMPLATE_NAME, null) {

    override fun document(operation: Operation) {
        val (content, headers) = body(operation)

        if (description == null && !FileBody.isBinary(content, headers)) {
            return
        }

        super.document(operation)
    }

    override fun createModel(operation: Operation): MutableMap<String, Any> {
        val (content, headers) = body(operation)
        val file = FileBody.of(content, headers)
        val rows = listOfNotNull(
            description?.let { row(DocsText.FILE_DESCRIPTION, it, code = false) },
            file.mediaType?.let { row(DocsText.FILE_MEDIA_TYPE, it, code = true) },
            file.fileName?.let { row(DocsText.FILE_NAME, it, code = true) },
            row(DocsText.FILE_SIZE, size(file.size), code = false),
        )

        return hashMapOf("rows" to rows)
    }

    private fun row(label: DocsText, value: String, code: Boolean): Map<String, Any> =
        mapOf("label" to label.text(), "value" to value, "code" to code)

    private fun size(bytes: Long): String =
        if (bytes == 1L) DocsText.FILE_ONE_BYTE.text() else DocsText.FILE_BYTES.text(bytes)

    companion object {
        private const val REQUEST_SNIPPET_NAME = "request-file"
        private const val RESPONSE_SNIPPET_NAME = "response-file"

        private const val TEMPLATE_NAME = "file-body"

        /** 요청 본문 표. [description] 은 `requestFile(...)` 의 설명 */
        fun request(description: String?): FileBodySnippet = FileBodySnippet(REQUEST_SNIPPET_NAME, description) {
            it.request.content to it.request.headers
        }

        /** 응답 본문 표. [description] 은 `responseFile(...)` 의 설명 */
        fun response(description: String?): FileBodySnippet = FileBodySnippet(RESPONSE_SNIPPET_NAME, description) {
            it.response.content to it.response.headers
        }
    }
}
