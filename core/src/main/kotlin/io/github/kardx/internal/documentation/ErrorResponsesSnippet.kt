package io.github.kardx.internal.documentation

import io.github.kardx.dsl.DeclaredErrorResponse
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.diagnostics.diagnosed
import io.github.kardx.internal.http.statusText
import io.github.kardx.internal.i18n.DocsText
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.TemplatedSnippet

/**
 * 테스트가 받지 않고 선언만 한 오류 응답의 `error-responses` 스니펫. 상태·설명 표와, 필드나 예시가 있는 응답마다 제목을 단
 * 필드 표·예시 블록이다. 표 머리글과 제목은 [DocsText] (설정 언어)를 따른다.
 * 템플릿은 라이브러리에 포함된 `org/springframework/restdocs/templates/<형식>/error-responses.snippet` 이다.
 *
 * 쓰기 전에 필드와 JSON 예시를 함께 적은 응답은 예시를 그 필드로 검증한다 ([ErrorExampleCheck]).
 *
 * @param responses 상태순으로 정렬하고 JSON 예시를 들여쓰기한 선언
 */
internal class ErrorResponsesSnippet(private val responses: List<DeclaredErrorResponse>) :
    TemplatedSnippet(SNIPPET_NAME, null) {

    override fun document(operation: Operation) {
        responses.forEach { checkExample(it, operation) }

        super.document(operation)
    }

    override fun createModel(operation: Operation): MutableMap<String, Any> = hashMapOf(
        "statusHeader" to DocsText.ERROR_STATUS.text(),
        "descriptionHeader" to DocsText.ERROR_DESCRIPTION.text(),
        "pathHeader" to DocsText.ERROR_FIELD_PATH.text(),
        "typeHeader" to DocsText.ERROR_FIELD_TYPE.text(),
        "responses" to responses.map { mapOf("status" to statusText(it.status), DESCRIPTION to it.description) },
        "details" to responses.filter { it.fields.isNotEmpty() || it.example != null }
            .map(::details),
    )

    /** 응답 하나의 필드 표·예시 블록. 값이 없는 키는 넣지 않아 템플릿 섹션이 빠진다. */
    private fun details(response: DeclaredErrorResponse): Map<String, Any> {
        val status = statusText(response.status)
        val fields = response.fields.filterNot { it.descriptor.isIgnored }
            .map(::fieldRow)

        return buildMap {
            put("hasFields", fields.isNotEmpty())
            put("fields", fields)
            put("fieldsTitle", DocsText.ERROR_RESPONSE_FIELDS.text(status))
            put("exampleTitle", DocsText.ERROR_RESPONSE_EXAMPLE.text(status))
            response.example?.let { put("example", it) }

            if (response.isJson) {
                put("language", JSON)
            }
        }
    }

    private fun fieldRow(field: RestDocsField): Map<String, Any> = buildMap {
        val descriptor = field.descriptor

        put("path", descriptor.path)
        put(DESCRIPTION, descriptor.description?.toString().orEmpty())
        descriptor.type?.let { put("type", it.toString()) }
    }

    /** 필드와 JSON 예시를 함께 적었으면 예시가 필드와 맞는지 본다. 맞지 않으면 진단 메시지를 단 예외로 실패한다. */
    private fun checkExample(response: DeclaredErrorResponse, operation: Operation) {
        val example = response.example

        if (example == null || !response.isJson || response.fields.isEmpty()) {
            return
        }

        ErrorExampleCheck(response.fields.map(RestDocsField::descriptor), example)
            .diagnosed("errorResponse(${response.status}) fields", "example") { example.toByteArray(Charsets.UTF_8) }
            .document(operation)
    }

    companion object {
        const val SNIPPET_NAME: String = "error-responses"

        private const val DESCRIPTION = "description"
        private const val JSON = "json"
    }
}
