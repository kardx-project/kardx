package io.github.kardx.internal.output

import org.springframework.restdocs.RestDocumentationContext

/**
 * 문서화한 operation 의 태그와 요약. OpenAPI operation 의 `tags`·`summary` 와 AsyncAPI 메시지의 태그·요약이 같은 값을 쓴다.
 *
 * @property tag 문서 클래스의 제목 ([OperationTitles.tagOf])
 * @property summary 테스트의 표시 이름 ([OperationTitles.of])
 */
internal class OperationTitle(val tag: String, val summary: String)

/**
 * OpenAPI·AsyncAPI 가 함께 쓰는 태그·요약 규칙.
 *
 * - 태그: 문서 클래스(JUnit `@Nested` 면 가장 바깥 클래스, Kotest 스펙)의 `@DisplayName`, 없으면 클래스 단순 이름.
 *   AsciiDoc 문서 제목([io.github.kardx.lifecycle.DocumentOutline.title])과 같다
 * - 요약: 먼저 정해진 것을 쓴다
 *   1. 확장이 테스트 실행에 묶은 표시 이름([RunningTest]). 테스트 스레드가 아니어도 REST Docs 문맥으로 찾는다
 *   2. 호출 스택에서 찾은 `@DisplayName` 테스트 메서드 (이 라이브러리 확장 없이 REST Docs 확장만 쓴 JUnit 테스트)
 *   3. REST Docs 문맥의 테스트 메서드에 붙은 `@DisplayName`, 없으면 메서드 이름
 */
internal object OperationTitles {
    /** [context] 로 문서화하는 operation 의 태그·요약. */
    fun of(context: RestDocumentationContext): OperationTitle {
        val testClass = context.testClass
        val methodName = context.testMethodName
        val summary = (RunningTest.find(testClass, methodName) ?: RunningTest.current())?.sectionTitle
            ?: DisplayNames.methodFromCallStack()
            ?: DisplayNames.ofMethod(testClass, methodName)
            ?: methodName

        return OperationTitle(tagOf(testClass), summary)
    }

    /** [testClass] 가 들어가는 문서의 제목. OpenAPI·AsyncAPI 태그가 된다. */
    fun tagOf(testClass: Class<*>): String = DisplayNames.titleOf(SnippetNames.documentClass(testClass))
}
