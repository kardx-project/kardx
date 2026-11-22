package io.github.kardx.internal.documentation

import io.github.kardx.diagnostics.RestDocsErrorCode
import org.springframework.restdocs.snippet.AbstractDescriptor
import org.springframework.restdocs.snippet.IgnorableDescriptor

/**
 * 이름만 적은 항목(`pathParameters("id")`, `"id" pathMeans FromParamTag` 등) 중 핸들러의 `@param` 으로도 설명을 찾지
 * 못한 것이 있으면 실패한다.
 * REST Docs 의 일반적인 오류("must either have a description") 대신 설명을 채우는 방법을 알려 준다.
 * 무시한(`ignored`) 항목은 설명이 없어도 된다.
 *
 * @throws IllegalArgumentException 설명이 없는 항목이 있을 때
 */
internal fun DocumentationSpec.requireDescriptions(): DocumentationSpec {
    val missing = pathParameters.map { it.name to it.descriptor }.undescribed(PATH_PARAMETER) +
        queryParameters.map { it.name to it.descriptor }.undescribed(QUERY_PARAMETER) +
        formParameters.map { it.name to it.descriptor }.undescribed(FORM_PARAMETER) +
        requestHeaders.map { it.descriptor.name to it.descriptor }.undescribed(REQUEST_HEADER) +
        requestParts.map { it.descriptor.name to it.descriptor }.undescribed(REQUEST_PART)

    require(missing.isEmpty()) {
        RestDocsErrorCode.MISSING_DESCRIPTION.message(
            "${missing.joinToString()} declared by name only (or with FromParamTag), " +
                "but no description was found. " +
                "Add a `@param <name>` tag to the KDoc/Javadoc of the handler method and turn on the experimental " +
                "source docs of the kardx Gradle plugin " +
                "(`kardx { sourceDocs { enabled = true } }`; " +
                "the handler is known with MockMvc and WebTestClient), or describe it in the test, " +
                "e.g. \"id\" pathMeans \"...\".",
        )
    }

    return this
}

/** 설명이 없고 무시하지도 않은 항목. `<kind> '<name>'` */
private fun List<Pair<String, AbstractDescriptor<*>>>.undescribed(kind: String): List<String> =
    filter { (_, descriptor) -> descriptor.description == null && !descriptor.isIgnoredItem() }
        .map { (name, _) -> "$kind '$name'" }

private fun AbstractDescriptor<*>.isIgnoredItem(): Boolean = this is IgnorableDescriptor<*> && isIgnored

private const val PATH_PARAMETER = "path parameter"
private const val QUERY_PARAMETER = "query parameter"
private const val FORM_PARAMETER = "form parameter"
private const val REQUEST_HEADER = "request header"
private const val REQUEST_PART = "request part"
