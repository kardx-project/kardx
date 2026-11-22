package io.github.kardx.internal.diagnostics

import io.github.kardx.dsl.DocumentationMismatchException
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.Snippet
import org.springframework.restdocs.snippet.SnippetException

/**
 * REST Docs 스니펫의 검증 실패를 [DocumentationMismatchException] 으로 바꾼다. 문서화 누락·초과([SnippetException])와
 * 필드 타입 불일치(`FieldTypesDoNotMatchException`, `FieldTypeRequiredException`)를 바꾼다.
 * 알아볼 수 없는 실패(검증이 아닌 오류)와 이미 바꾼 예외는 그대로 던진다.
 *
 * @param subject 메시지에 쓸 항목 이름 (`response fields`, `query parameters` …)
 * @param target 메시지에 쓸 실제 대상 (`response`, `request`, `message` …)
 * @param payload 힌트를 확인할 실제 본문. 필드 스니펫에만 준다
 */
internal class DiagnosedSnippet(
    private val snippet: Snippet,
    private val subject: String,
    private val target: String,
    private val payload: (Operation) -> ByteArray? = { null },
) : Snippet {
    // REST Docs 의 필드 타입 예외는 RuntimeException 을 바로 상속하고, 그중 하나는 패키지 밖에 공개되지 않아 이름으로 알아본다
    @Suppress("TooGenericExceptionCaught")
    override fun document(operation: Operation) {
        try {
            snippet.document(operation)
        } catch (e: DocumentationMismatchException) {
            throw e
        } catch (e: SnippetException) {
            val mismatch = SnippetMismatch.parse(e.message.orEmpty()) ?: throw e
            val actual = runCatching { payload(operation) }.getOrNull()
                ?.let { PayloadPaths.parse(String(it, Charsets.UTF_8)) }
            val message = MismatchMessage.render(subject, target, mismatch, MismatchHints.of(mismatch, actual, subject))

            throw DocumentationMismatchException(message, e)
        } catch (e: RuntimeException) {
            val mismatch = FieldTypeMismatch.of(e) ?: throw e

            throw DocumentationMismatchException(
                MismatchMessage.renderType(subject, target, mismatch, FieldTypeHints.of(mismatch)),
                e,
            )
        }
    }
}

/** [DiagnosedSnippet] 으로 감싼다. `responseFields(...).diagnosed("response fields", "response") { it.response.content }` */
internal fun Snippet.diagnosed(
    subject: String,
    target: String,
    payload: (Operation) -> ByteArray? = { null },
): Snippet = DiagnosedSnippet(this, subject, target, payload)
