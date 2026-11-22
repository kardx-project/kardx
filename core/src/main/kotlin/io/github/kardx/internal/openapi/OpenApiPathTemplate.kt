package io.github.kardx.internal.openapi

import io.github.kardx.internal.reflect.overrideGetter
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.Snippet

/**
 * OpenAPI 경로에 쓸 URL 템플릿. Spring 의 정규식 경로 변수(`{name:.+}`, `v{version:\d{1,3}}`)에서 정규식을 빼고,
 * 템플릿에 쿼리(`/users/{id}?verbose={verbose}`, WebTestClient 의 `uri(...)` 에서 흔하다)가 있으면 뗀다.
 *
 * OpenAPI 경로 템플릿은 `{name}` 만 허용하고, epages 는 템플릿 변수 이름을 경로 파라미터 이름으로 쓴다. 정규식을 그대로
 * 넘기면 경로가 `/files/{name:.+}`, 파라미터 이름이 `name:.+` 가 되어 `pathParameters("name" ...)` 의 설명과 이어지지 않는다.
 * REST Docs 스니펫(AsciiDoc)의 URL 은 바꾸지 않는다.
 */
internal object OpenApiPathTemplate {
    /** `/files/{name:.+}` → `/files/{name}`. 정규식 안의 중괄호(`\d{1,3}`)도 짝을 맞춰 건너뛴다. 쿼리는 뗀다. */
    fun withoutPatterns(template: String): String {
        val result = StringBuilder()
        var index = 0

        while (index < template.length && template[index] != '?') {
            val end = if (template[index] == '{') closingBrace(template, index) else -1

            if (end < 0) {
                result.append(template[index])
                index++
            } else {
                result.append('{')
                    .append(template.substring(index + 1, end).substringBefore(':').trim())
                    .append('}')
                index = end + 1
            }
        }

        return result.toString()
    }

    /**
     * `/users/{id}?verbose={verbose}` → `/users/{id}`. 경로 변수 정규식 안의 `?`(`{name:.+?}`)는 쿼리로 보지 않는다.
     * `api-summary` 스니펫의 URL 에 쓴다 (정규식은 그대로 둔다).
     */
    fun withoutQuery(template: String): String {
        var index = 0

        while (index < template.length) {
            when (template[index]) {
                '?' -> return template.substring(0, index)
                '{' -> index = closingBrace(template, index).takeIf { it >= 0 } ?: template.length
            }

            index++
        }

        return template
    }

    /** [template] 의 경로 변수 이름. `/users/{id}/files/{name:.+}?q={q}` → `[id, name]` (쿼리의 변수는 빼고) */
    fun variables(template: String): List<String> = VARIABLE.findAll(withoutPatterns(template))
        .map { it.groupValues[1] }
        .distinct()
        .toList()

    /** URL 템플릿의 정규식과 쿼리를 뺀 [Operation] 으로 [resource] 를 만든다. */
    fun wrap(resource: Snippet): Snippet = Snippet { operation -> resource.document(withoutPatterns(operation)) }

    private fun withoutPatterns(operation: Operation): Operation {
        val template = operation.attributes[URL_TEMPLATE] as? String

        if (template == null || (':' !in template && '?' !in template)) {
            return operation
        }

        val attributes = operation.attributes + (URL_TEMPLATE to withoutPatterns(template))

        return overrideGetter(Operation::class.java, operation, "getAttributes", attributes)
    }

    /** [start] 의 `{` 와 짝이 맞는 `}` 위치. 없으면 -1. */
    private fun closingBrace(template: String, start: Int): Int {
        var depth = 0

        for (index in start until template.length) {
            when (template[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return index
            }
        }

        return -1
    }

    private val VARIABLE = Regex("""\{([^{}]+)}""")

    /** `RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE`. */
    private const val URL_TEMPLATE = "org.springframework.restdocs.urlTemplate"
}
