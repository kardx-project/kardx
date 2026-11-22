package io.github.kardx.internal.documentation

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.OpenApiBuilder
import io.github.kardx.internal.introspection.dataTypeOf
import io.github.kardx.internal.introspection.schemaTypeOf

/**
 * 블록에서 모은 항목을 [DocumentationSpec] 으로 만든다.
 *
 * @throws IllegalArgumentException 파일 본문(`responseFile`·`requestFile`)과 본문 필드·파트를 함께 적었으면
 */
internal fun DocumentationBuilder.toSpec(): DocumentationSpec {
    checkFileBodies()

    return DocumentationSpec(
        description = description,
        pathParameters = pathParameters.toList(),
        requestHeaders = requestHeaders.toList(),
        queryParameters = queryParameters.toList(),
        formParameters = formParameters.toList(),
        requestFields = requestFields.toList(),
        responseFields = responseFields.toList(),
        responseHeaders = responseHeaders.toList(),
        requestCookies = requestCookies.toList(),
        responseCookies = responseCookies.toList(),
        requestParts = requestParts.toList(),
        requestPartFields = requestPartFields.mapValues { it.value.toList() },
        links = links.toList(),
        events = events.toList(),
        relaxedSections = relaxedSections.toSet(),
        openApi = openApi.toOptions(),
        extraSnippets = snippets.toList(),
        // 필드 목록을 여러 번 더했으면 본문이 한 타입과 같지 않으므로 자동 이름을 붙이지 않는다
        requestSchemaType = requestFieldGroups.singleOrNull()?.let(::schemaTypeOf),
        responseSchemaType = responseFieldGroups.singleOrNull()?.let(::schemaTypeOf),
        responseDataType = responseFieldGroups.singleOrNull()?.let(::dataTypeOf),
        parameterSections = parameterSections.toSet(),
        errorResponses = errorResponses.sortedBy { it.status }
            .map { it.withExampleFields().withPrettyExample() },
        requestFile = requestFile,
        responseFile = responseFile,
    )
}

/** 파일 본문에는 필드가 없으므로 본문 필드·파트와 함께 적을 수 없다. */
private fun DocumentationBuilder.checkFileBodies() {
    require(responseFile == null || responseFields.isEmpty()) {
        RestDocsErrorCode.CONFLICTING_BODY.message(
            "responseFile cannot be used together with responseFields. " +
                "A file response body has no fields; remove one of them.",
        )
    }

    require(requestFile == null || requestFields.isEmpty()) {
        RestDocsErrorCode.CONFLICTING_BODY.message(
            "requestFile cannot be used together with requestFields. " +
                "A file request body has no fields; remove one of them.",
        )
    }

    require(requestFile == null || requestParts.isEmpty()) {
        RestDocsErrorCode.CONFLICTING_BODY.message(
            "requestFile cannot be used together with requestParts. " +
                "Describe multipart file parts with requestParts only.",
        )
    }
}

/** `openApi { }` 블록에서 정한 값을 [OpenApiOptions] 로 만든다. */
internal fun OpenApiBuilder.toOptions(): OpenApiOptions = OpenApiOptions(
    requestSchema,
    responseSchema,
    deprecated,
    excluded,
    tags.toList(),
    operationId = operationId,
    authentication = authentication,
    deprecationNote = deprecationNote,
    deprecationDeclared = deprecationDeclared,
    security = securityRequirements.mapValues { it.value.toList() },
    summary = summary?.takeIf { it.isNotBlank() },
    description = description?.takeIf { it.isNotBlank() },
)
