package io.github.kardx.internal.documentation

import io.github.kardx.dsl.RestDocsParam
import io.github.kardx.internal.openapi.BoundParameter
import io.github.kardx.internal.openapi.HandlerParameters

/**
 * 설명 없이 이름만 적은 항목(`pathParameters("id")` 등)에 핸들러 메서드 KDoc·Javadoc 의 `@param` 설명을 넣는다.
 * 요청 이름으로 그 항목을 받는 핸들러 인자([HandlerParameters])를 찾고, 그 인자의 소스 이름으로 [params] 를 본다.
 * 테스트에 적은 설명(빈 문자열 포함)은 그대로 둔다. 찾지 못한 항목은 설명 없이 남는다 ([requireDescriptions]).
 * 사용자의 항목 객체는 바꾸지 않고 복사본에 넣는다.
 *
 * @param params 소스의 인자 이름 → `@param` 설명 ([io.github.kardx.internal.sourcedocs.MethodDocs.params])
 */
internal fun DocumentationSpec.withSourceParameterDescriptions(
    parameters: HandlerParameters?,
    params: Map<String, String>,
): DocumentationSpec {
    if (parameters == null || params.isEmpty()) {
        return this
    }

    return copy(
        pathParameters = pathParameters.describedBy(params) { parameters.path[it] },
        queryParameters = queryParameters.describedBy(params) { parameters.request[it] },
        formParameters = formParameters.describedBy(params) { parameters.request[it] },
        requestHeaders = requestHeaders.map { header ->
            sourceText(header.descriptor.description, parameters.header(header.descriptor.name), params)
                ?.let { text -> header.modified { it.description(text) } }
                ?: header
        },
        requestParts = requestParts.map { part ->
            val name = part.descriptor.name
            val bound = parameters.parts[name] ?: parameters.request[name]

            sourceText(part.descriptor.description, bound, params)
                ?.let { text -> part.modified { it.description(text) } }
                ?: part
        },
    )
}

private inline fun List<RestDocsParam>.describedBy(
    params: Map<String, String>,
    bound: (String) -> BoundParameter?,
): List<RestDocsParam> = map { param ->
    sourceText(param.descriptor.description, bound(param.name), params)
        ?.let { text -> param.modified { it.description(text) } }
        ?: param
}

/** 테스트에 설명이 없을 때([description] 이 null) [bound] 인자의 `@param` 설명. 쓸 설명이 없으면 null. */
private fun sourceText(description: Any?, bound: BoundParameter?, params: Map<String, String>): String? =
    if (description == null) bound?.sourceName?.let(params::get) else null
