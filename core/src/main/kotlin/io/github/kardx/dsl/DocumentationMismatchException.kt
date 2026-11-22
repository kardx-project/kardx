package io.github.kardx.dsl

import io.github.kardx.diagnostics.RestDocsErrorCode
import org.springframework.restdocs.snippet.SnippetException

/**
 * 문서화한 필드·파라미터·헤더·쿠키·파트·SSE 이벤트가 실제 요청·응답·메시지와 맞지 않을 때 `andDocument { }`,
 * `MessageDocumentation` 이 던진다. Spring REST Docs 의 [SnippetException] 과 필드 타입 예외(`FieldTypesDoNotMatchException`,
 * `FieldTypeRequiredException`)를 감싸서 다음을 메시지에 더한다.
 *
 * - 문서화하지 않은 항목(실제로 있음)과, 문서화했지만 실제로 없는 항목을 경로별로
 * - 타입이 맞지 않는 필드의 문서화한 타입과 실제 타입
 * - 바로 붙여 넣을 수 있는 DSL 줄 (`"data.memo" type STRING means "..."`)
 * - 실제 페이로드로 확인되는 흔한 원인 (빈 배열, 이름 전략 차이, 응답 래퍼 불일치, 부모는 있는데 빠진 필드, null 값,
 *   문자열로 직렬화한 숫자, 타임스탬프로 직렬화한 날짜)
 *
 * [SnippetException] 의 하위 클래스라 기존 `catch`·`shouldThrow<SnippetException>` 은 그대로 동작하고,
 * REST Docs 의 원래 예외는 [cause] 로 남는다. 필드 타입 예외는 REST Docs 에서 [SnippetException] 이 아니었으므로,
 * 그 예외를 직접 잡던 코드는 [cause] 를 본다.
 *
 * 메시지는 `kardx[KDX-109]: ` 로 시작하고 문제 해결 문서 주소로 끝난다.
 */
public class DocumentationMismatchException internal constructor(message: String, cause: RuntimeException) :
    SnippetException(RestDocsErrorCode.DOCUMENTATION_MISMATCH.message(message)) {
    init {
        initCause(cause)
    }
}
