package io.github.kardx.internal.documentation

import org.springframework.http.MediaType
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.ResponseFieldsSnippet

/**
 * 선언한 오류 응답의 [example] 을 그 필드로 검증한다. REST Docs 응답 필드 스니펫의 검증을 실제 응답 대신 예시 본문에 쓰고,
 * 파일은 쓰지 않는다. 문서화하지 않은 필드나 빠진 필수 필드가 있으면 REST Docs 가 `SnippetException` 을 던진다.
 */
internal class ErrorExampleCheck(fields: List<FieldDescriptor>, private val example: String) :
    ResponseFieldsSnippet(fields, false) {
    override fun document(operation: Operation) {
        createModel(operation)
    }

    override fun getContent(operation: Operation): ByteArray = example.toByteArray(Charsets.UTF_8)

    override fun getContentType(operation: Operation): MediaType = MediaType.APPLICATION_JSON
}
