package io.github.kardx.internal.openapi

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.output.SnippetPaths
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.Snippet
import java.util.concurrent.ConcurrentHashMap

/**
 * URL 템플릿의 경로 변수 중 블록에서 문서화하지 않은 것이 있으면 경고하는 스니펫. 파일은 쓰지 않는다.
 * 문서화하지 않은 경로 변수는 OpenAPI 에 타입·설명 없이 나가므로, 붙여 넣을 수 있는 `pathParameters(...)` 줄을 알려 준다.
 * OpenAPI 조각을 만들 때만 넣는다. 테스트 실행(REST Docs 문맥의 문서 클래스·스니펫 자리)마다 한 번만 경고한다.
 *
 * `pathParameters(...)` 를 부른 블록은 REST Docs 가 빠진 변수를 실패로 알리므로 여기까지 오지 않는다.
 *
 * @param documented 블록에서 문서화한 경로 변수 이름
 */
internal class UndocumentedPathVariables(private val documented: Set<String>) : Snippet {
    override fun document(operation: Operation) {
        val template = operation.attributes[RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE] as? String ?: return
        val missing = OpenApiPathTemplate.variables(template) - documented

        if (missing.isEmpty()) {
            return
        }

        val context = SnippetPaths.documentationContext(operation)
        val test = context?.let { "${it.testClass.simpleName}.${it.testMethodName}" } ?: operation.name
        val key = context?.let { "${it.testClass.name}/${it.testMethodName}" } ?: operation.name

        if (WARNED.add(key)) {
            LOGGER.log(System.Logger.Level.WARNING, message(operation, template, test, missing))
        }
    }

    private fun message(operation: Operation, template: String, test: String, missing: List<String>): String {
        val names = missing.joinToString(", ")
        val line = missing.joinToString(", ") { "\"$it\" pathMeans \"...\"" }

        return RestDocsErrorCode.UNDOCUMENTED_PATH_VARIABLE.message(
            "${operation.request.method.name()} ${OpenApiPathTemplate.withoutQuery(template)} " +
                "in $test does not document path variable(s) $names, so OpenAPI shows them without description. " +
                "Add to the andDocument block: pathParameters($line)",
        )
    }

    private companion object {
        /** 이미 경고한 테스트 실행 */
        val WARNED: MutableSet<String> = ConcurrentHashMap.newKeySet()

        val LOGGER: System.Logger = System.getLogger(UndocumentedPathVariables::class.java.name)
    }
}
