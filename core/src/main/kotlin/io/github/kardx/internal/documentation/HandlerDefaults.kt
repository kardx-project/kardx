package io.github.kardx.internal.documentation

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.RestDocsParam
import io.github.kardx.dsl.SCHEMA_ATTRIBUTE
import io.github.kardx.dsl.copied
import io.github.kardx.internal.introspection.JsonTypes
import io.github.kardx.internal.introspection.TypeSchemas
import io.github.kardx.internal.introspection.putValueType
import io.github.kardx.internal.introspection.toKType
import io.github.kardx.internal.openapi.BoundParameter
import io.github.kardx.internal.openapi.DocumentedHandler
import io.github.kardx.internal.openapi.HandlerParameters
import io.github.kardx.internal.openapi.handlerDeprecation
import io.github.kardx.internal.sourcedocs.SourceDocs

/**
 * 블록에서 정하지 않은 값을 요청을 처리한 [handler](MockMvc `MvcResult.handler` 등)에서 채운다. 핸들러를 모르면 그대로다.
 *
 * - 경로·쿼리·폼 파라미터의 타입: 타입이 없는(`ofType`·`enumOf`·추출 타입이 없는) 파라미터는 핸들러의 같은 이름 인자
 *   ([HandlerParameters]) 타입으로 OpenAPI 타입을 정한다. 값 타입(문자열·숫자·불리언·날짜·Enum)만 쓰고, 쿼리·폼은 그 값의
 *   목록·배열도 쓴다. 객체처럼 파라미터 하나로 나타낼 수 없는 타입은 그대로 `string` 이다. 사용자의 파라미터 객체는 바꾸지
 *   않고 복사본에 넣는다
 * - deprecated: 블록에서 정하지 않았으면 핸들러 메서드·컨트롤러의 `@Deprecated` ([handlerDeprecation])
 * - 설명 없이 이름만 적은 경로·쿼리·폼 파라미터, 요청 헤더, 파트의 설명: 핸들러 메서드 KDoc·Javadoc 의 `@param`
 *   ([withSourceParameterDescriptions]). 테스트에 적은 설명이 항상 앞선다
 * - `api-summary` 설명: 블록에 `description` 이 없으면 핸들러 메서드 KDoc·Javadoc 의 전체 설명
 *   ([DocumentationSpec.sourceDescription]). OpenAPI 의 description 은 Gradle 플러그인이 따로 정한다
 */
internal fun DocumentationSpec.withHandlerDefaults(handler: Any?): DocumentationSpec {
    if (handler == null) {
        return this
    }

    val parameters = HandlerParameters.of(handler)
    val deprecation = handlerDeprecation(handler).takeUnless { openApi.deprecationDeclared }
    val sourceDocs = DocumentedHandler.of(handler)
        ?.let {
            SourceDocs.method(
                RestDocsConfiguration.current.descriptions.sourceDocs,
                it.classNames,
                it.methodName,
                it.arity,
            )
        }

    return copy(
        pathParameters = pathParameters.typedBy(parameters?.path, arrays = false),
        queryParameters = queryParameters.typedBy(parameters?.request, arrays = true),
        formParameters = formParameters.typedBy(parameters?.request, arrays = true),
        openApi = deprecation?.let { openApi.copy(deprecated = true, deprecationNote = it.ifBlank { null }) }
            ?: openApi,
        sourceDescription = sourceDocs?.description,
    ).withSourceParameterDescriptions(parameters, sourceDocs?.params.orEmpty())
}

private fun List<RestDocsParam>.typedBy(bound: Map<String, BoundParameter>?, arrays: Boolean): List<RestDocsParam> =
    if (bound.isNullOrEmpty()) this else map { it.typedBy(bound[it.name], arrays) }

private fun RestDocsParam.typedBy(bound: BoundParameter?, arrays: Boolean): RestDocsParam {
    val attributes = descriptor.attributes
    val type = bound?.type

    if (type == null || SCHEMA_ATTRIBUTE in attributes || ENUM_VALUES_ATTRIBUTE in attributes) {
        return this
    }

    val kType = type.toKType()
    val schema = TypeSchemas.of(kType)?.takeIf { isParameterValue(it, arrays) } ?: return this

    return copied().apply { descriptor.attributes.putValueType(kType, schema) }
}

/** 파라미터 하나로 나타낼 수 있는 스키마인지. 값 타입이거나, [arrays] 면 값 타입의 배열이다. */
private fun isParameterValue(schema: Map<String, Any?>, arrays: Boolean): Boolean = when (schema[TYPE]) {
    in VALUE_TYPES -> true
    JsonTypes.ARRAY -> arrays && (schema[ITEMS] as? Map<*, *>)?.get(TYPE) in VALUE_TYPES
    else -> false
}

private const val TYPE = "type"
private const val ITEMS = "items"
private val VALUE_TYPES = setOf(JsonTypes.STRING, JsonTypes.INTEGER, JsonTypes.NUMBER, JsonTypes.BOOLEAN)
