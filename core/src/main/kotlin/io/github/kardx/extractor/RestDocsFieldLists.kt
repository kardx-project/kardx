package io.github.kardx.extractor

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.isUnder
import io.github.kardx.internal.introspection.TypedFields

/**
 * [paths] 의 필드와 그 아래 필드(`path.x`, `path[]`, `path[].x`)를 뺀 목록을 돌려준다. 원래 목록은 바꾸지 않는다.
 * `@JsonView` 로 빠지는 필드나 직접 다시 기술할 필드를 추출 결과에서 뺄 때 쓴다.
 *
 * 추출 함수(`responseFieldsOf<T>()` 등)의 결과에 쓰면 타입 정보가 남아 OpenAPI 스키마 이름 기본값(`T` 의 이름)이 그대로다.
 * 이어서 `+` 로 직접 쓴 필드를 더해도 이름이 유지된다. 다른 타입이나 다른 경로(`responseFieldsOf<X>("data")`)에서 추출한
 * 필드를 섞으면 본문이 한 타입이 아니므로 자동 이름을 붙이지 않는다 (`openApi { responseSchema<T>() }` 로 직접 정한다).
 * 직접 쓴 목록에도 쓸 수 있다.
 *
 * ```kotlin
 * responseFields(
 *     responseFieldsOf<ProductResource>().except("_links") +
 *         ("_links" subsection OBJECT means "HAL 링크"),
 * )
 * ```
 *
 * @throws IllegalArgumentException [paths] 중에 목록에 없는 경로가 있을 때 (오타로 필드가 남지 않도록)
 */
public fun List<RestDocsField>.except(vararg paths: String): List<RestDocsField> {
    requireKnown(paths.toList(), "except") { path -> any { it.isUnder(path) } }

    return keepingType(filterNot { field -> paths.any { field.isUnder(it) } })
}

/**
 * [paths] 의 필드를 optional 로 바꾼 목록을 돌려준다. 하위 필드는 그대로다. 원래 목록과 필드는 바꾸지 않고 바꿀 필드만
 * 복사한다. AsciiDoc 표의 Required 열이 `false` 가 되고 OpenAPI·AsyncAPI 스키마의 `required` 에서 빠진다.
 * `@JsonInclude(NON_EMPTY)` 처럼 응답에 없을 수 있는 필드에 쓴다.
 *
 * [except] 와 같이 추출 결과의 OpenAPI 스키마 이름 기본값을 유지한다. 직접 쓴 목록에도 쓸 수 있다.
 *
 * ```kotlin
 * responseFields(responseFieldsOf<UserResponse>().optionalAt("tags", "profile.nickname"))
 * ```
 *
 * @throws IllegalArgumentException [paths] 중에 목록에 없는 경로가 있을 때
 */
public fun List<RestDocsField>.optionalAt(vararg paths: String): List<RestDocsField> {
    requireKnown(paths.toList(), "optionalAt") { path -> any { it.descriptor.path == path } }

    return optionalWhere { it.descriptor.path in paths }
}

/**
 * [paths] 의 필드와 그 아래 필드(`path.x`, `path[]`, `path[].x`)를 모두 optional 로 바꾼 목록을 돌려준다. 원래 목록과 필드는
 * 바꾸지 않고 바꿀 필드만 복사한다. [optionalAt] 은 적은 경로의 필드만 바꾸고, 이 함수는 그 아래까지 바꾼다.
 *
 * 테스트 데이터의 배열이 비어 원소 필드를 확인할 수 없을 때 `"items[]"` 로 원소 필드만 optional 로 둔다 (배열 `items` 자체는
 * 그대로 필수다). 모든 배열에 적용하려면 [io.github.kardx.config.ExtractionSettings.extraction.arrayElementFieldsOptional] 을 쓴다.
 * [except] 와 같이 추출 결과의 OpenAPI 스키마 이름 기본값을 유지한다.
 *
 * ```kotlin
 * responseFields(responseFieldsOf<OrderResponse>().optionalUnder("items[]"))  // items[].name, items[].price ...
 * responseFields(responseFieldsOf<UserResponse>().optionalUnder("profile"))   // profile, profile.nickname ...
 * ```
 *
 * @throws IllegalArgumentException [paths] 중에 그 경로이거나 그 아래인 필드가 하나도 없는 경로가 있을 때
 */
public fun List<RestDocsField>.optionalUnder(vararg paths: String): List<RestDocsField> {
    requireKnown(paths.toList(), "optionalUnder") { path -> any { it.isUnder(path) } }

    return optionalWhere { field -> paths.any { field.isUnder(it) } }
}

/** [selected] 필드만 optional 로 복사한 목록. 타입을 기억한 목록이면 그대로 기억한다. */
private fun List<RestDocsField>.optionalWhere(selected: (RestDocsField) -> Boolean): List<RestDocsField> {
    val fields = map { field ->
        if (selected(field)) {
            field.optional()
        } else {
            field
        }
    }

    return keepingType(fields)
}

/** 이 목록이 타입을 기억한 목록이면 [fields] 도 같은 타입을 기억한다. */
private fun List<RestDocsField>.keepingType(fields: List<RestDocsField>): List<RestDocsField> {
    if (this !is TypedFields) return fields

    return TypedFields(schemaType, fields, dataType)
}

private fun List<RestDocsField>.requireKnown(paths: List<String>, function: String, known: (String) -> Boolean) {
    val unknown = paths.filterNot(known)

    require(unknown.isEmpty()) {
        RestDocsErrorCode.UNKNOWN_ITEM.message(
            "$function: no field at ${unknown.joinToString()}. Fields: ${joinToString { it.descriptor.path }}",
        )
    }
}
