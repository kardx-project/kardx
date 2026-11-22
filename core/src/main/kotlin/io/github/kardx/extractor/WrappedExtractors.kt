package io.github.kardx.extractor

import io.github.kardx.config.ResponseWrapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.dsl.ARRAY
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.isUnder
import io.github.kardx.dsl.type
import io.github.kardx.internal.introspection.FieldExtractor
import io.github.kardx.internal.introspection.FieldOrigins
import io.github.kardx.internal.introspection.TypedFields
import io.github.kardx.internal.provenance.FieldOrigin
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

/**
 * 공통 응답 래퍼([io.github.kardx.config.ExtractionSettings.responseWrapper]) 필드와 데이터 필드를 합친 목록을 돌려준다.
 * 목록을 그대로 등록할 때는 `andDocument` 블록의 단축형
 * [wrappedResponseFields] 를 쓰고, 이 함수는 목록을 고치거나 합칠 때 쓴다.
 *
 * ```kotlin
 * // { "code": 200, "message": "OK", "data": { "id": 1, ... } }
 * wrappedResponseFields<UserResponse>()                                  // andDocument 블록 안
 * // { ..., "data": [ { "id": 1 }, ... ] }
 * responseFields(wrappedResponseFieldsOf<List<UserResponse>>() + extraFields)
 * ```
 *
 * 래퍼 필드 중 데이터 경로 아래는 빼고 [T] 의 필드로 채운다. 래퍼를 등록하지 않았으면 [T] 의 필드만 돌려준다
 * ([dataPath] 를 주면 그 경로 아래에 둔다).
 *
 * OpenAPI 스키마 이름 기본값([io.github.kardx.config.OpenApiSettings.schemaNames])은 래퍼를 등록했으면 `ApiResponse<T>` 의 이름이다.
 * 래퍼 없이 [dataPath] 를 주면 본문(`{ "data": { ... } }`)이 [T] 가 아니므로 이름을 붙이지 않고 epages 가 정한 이름을 쓴다
 * (진짜 [T] 스키마와 이름이 겹치지 않게). 이름이 필요하면 `openApi { responseSchema = "..." }` 로 정한다.
 *
 * @param dataPath 데이터 위치. 기본값은 [ResponseWrapper.dataPath]. `[]` 로 끝나면 배열 원소 타입으로 본다
 * @param dataOptional true 이면 데이터 필드와 그 하위 필드를 모두 optional 로 표시한다 (실패 응답처럼 데이터가 없을 수 있을 때)
 */
public inline fun <reified T> wrappedResponseFieldsOf(
    dataPath: String? = null,
    dataOptional: Boolean = false,
): List<RestDocsField> = wrappedResponseFieldsOf(typeOf<T>(), dataPath, dataOptional)

/** [wrappedResponseFieldsOf] 의 [KClass] 버전. */
public fun wrappedResponseFieldsOf(
    kClass: KClass<*>,
    dataPath: String? = null,
    dataOptional: Boolean = false,
): List<RestDocsField> = wrappedResponseFieldsOf(kClass.starProjectedType, dataPath, dataOptional)

/** [wrappedResponseFieldsOf] 의 [KType] 버전. */
public fun wrappedResponseFieldsOf(
    type: KType,
    dataPath: String? = null,
    dataOptional: Boolean = false,
): List<RestDocsField> {
    val wrapper = RestDocsConfiguration.current.extraction.responseWrapper
    val path = dataPath
        ?: wrapper?.dataPath
        ?: return TypedFields(
            type,
            responseFieldsOf(type)
                .optionalIf(dataOptional),
        )

    val dataType = if (path.endsWith("[]")) {
        List::class.createType(listOf(KTypeProjection.invariant(type)))
    } else {
        type
    }

    val wrapperFields = wrapperFields(wrapper)
    val fields = wrapperFields.exceptData(path) +
        dataFields(type, path, dataOptional, wrapperFields.descriptionOf(path))

    if (wrapper == null) {
        // 본문은 `{ "data": { ... } }` 라 데이터 타입이 아니다. 데이터 타입 이름을 붙이면 진짜 그 타입의 스키마와 겹친다
        FieldOrigins.mark(fields, FieldOrigin.UNTYPED)

        return fields
    }

    return TypedFields(wrapped(wrapper.wrapperClass, dataType), fields, dataType)
}

/**
 * 공통 응답 래퍼 + 중간 래퍼 + 데이터 필드를 합친다. 페이지 응답처럼 2단으로 중첩된 경우에 쓴다.
 *
 * ```kotlin
 * // { "code": 200, "data": { "total": 10, "content": [ { "id": 1 } ] } }
 * responseFields(wrappedResponseFieldsOf(PageResponse::class, UserResponse::class, "data.content[]"))
 * ```
 *
 * 제네릭 페이지 클래스라면 `wrappedResponseFieldsOf<PageResponse<UserResponse>>()` 로도 같은 결과를 얻는다.
 * 공통 래퍼를 등록하지 않았으면 [middleWrapperClass] 가 응답의 루트다 (`total`, `content[].id`).
 * 래퍼는 yml `kardx.response-wrapper.*` 나 `RestDocsConfiguration.configure { extraction { responseWrapper<...>() } }` 로
 * 등록한다 ([io.github.kardx.config.ExtractionSettings.responseWrapper]).
 *
 * @param dataPath [dataClass] 필드의 절대 경로. [middleWrapperClass] 의 해당 프로퍼티는 이 필드로 대체된다
 */
public fun wrappedResponseFieldsOf(
    middleWrapperClass: KClass<*>,
    dataClass: KClass<*>,
    dataPath: String,
    dataOptional: Boolean = false,
): List<RestDocsField> {
    val wrapper = RestDocsConfiguration.current.extraction.responseWrapper
    val middlePath = wrapper?.dataPath.orEmpty()
    val allMiddleFields = responseFieldsOf(middleWrapperClass, middlePath)
    val middleFields = allMiddleFields.exceptData(dataPath)
    val middle = if (wrapper == null) {
        middleFields
    } else {
        val wrapperFields = wrapperFields(wrapper)

        wrapperFields.exceptData(middlePath) +
            dataFields(
                middleWrapperClass.starProjectedType,
                middlePath,
                dataOptional,
                wrapperFields.descriptionOf(middlePath),
                children = middleFields,
            )
    }

    val middleType = wrapped(middleWrapperClass, dataClass.starProjectedType)

    return TypedFields(
        wrapper?.let { wrapped(it.wrapperClass, middleType) } ?: middleType,
        middle +
            dataFields(dataClass.starProjectedType, dataPath, dataOptional, allMiddleFields.descriptionOf(dataPath)),
        middleType.takeIf { wrapper != null },
    )
}

/** `ApiResponse` + `TodoResponse` → `ApiResponse<TodoResponse>`. 타입 파라미터가 하나가 아니면 star projection. */
private fun wrapped(wrapperClass: KClass<*>, dataType: KType): KType = if (wrapperClass.typeParameters.size == 1) {
    wrapperClass.createType(listOf(KTypeProjection.invariant(dataType)))
} else {
    wrapperClass.starProjectedType
}

/**
 * 공통 응답 래퍼 필드와 직접 기술한 데이터 필드를 합친 목록을 돌려준다. 래퍼를 등록하지 않았으면 [fields] 그대로다.
 * 래퍼는 yml `kardx.response-wrapper.*` 나 `RestDocsConfiguration.configure { extraction { responseWrapper<...>() } }` 로
 * 등록한다 ([io.github.kardx.config.ExtractionSettings.responseWrapper]).
 * 목록을 더 고치지 않는다면 `andDocument` 블록의 [wrappedResponseFields] 로
 * 바로 등록한다.
 *
 * ```kotlin
 * responseFields(wrappedResponseFieldsOf("data.id" type NUMBER means "ID") + extraFields)
 * ```
 */
public fun wrappedResponseFieldsOf(vararg fields: RestDocsField): List<RestDocsField> {
    val wrapper = RestDocsConfiguration.current.extraction.responseWrapper ?: return fields.toList()

    // 래퍼 필드만으로는 본문 타입을 정할 수 없다. 래퍼 타입(`ApiResponse`) 이름이 여러 응답에 붙지 않게 출처를 지운다
    val wrapperPart = wrapperFields(wrapper).exceptData(wrapper.dataPath)

    FieldOrigins.mark(wrapperPart, FieldOrigin.UNTYPED)

    return wrapperPart + fields
}

/** 공통 래퍼의 필드 전체. 래퍼가 없으면 빈 목록. */
private fun wrapperFields(wrapper: ResponseWrapper?): List<RestDocsField> =
    wrapper?.let { responseFieldsOf(it.wrapperClass) }
        .orEmpty()

/** [dataPath] 와 그 아래를 뺀 필드. 데이터 필드가 그 자리를 대신한다. */
private fun List<RestDocsField>.exceptData(dataPath: String): List<RestDocsField> {
    val path = dataPath.removeSuffix("[]")

    return filterNot { it.isUnder(path) }
}

/**
 * [dataPath] 필드의 설명 (`@Description` 이나 `description-annotations`).
 * 설명이 없어 추출기가 기본 설명([FieldExtractor.DEFAULT_DESCRIPTION])을 채웠으면 null 이다.
 * 그러면 [dataFields] 가 데이터 타입 이름으로 대신한다.
 */
private fun List<RestDocsField>.descriptionOf(dataPath: String): String? {
    val path = dataPath.removeSuffix("[]")
    val description = firstOrNull { it.descriptor.path == path }
        ?.descriptor
        ?.description as? String

    return description?.takeUnless { it == FieldExtractor.DEFAULT_DESCRIPTION }
}

/**
 * 데이터 부모 한 줄 + 하위 필드. 경로가 `[]` 로 끝나면 부모는 배열이다.
 * 부모 줄의 설명은 [description] (래퍼 클래스의 데이터 프로퍼티 설명), 없으면 데이터 타입의 이름(`TodoResponse`)이다.
 * 최상위가 컬렉션·Map 인 타입은 추출기가 부모 줄을 이미 만들므로 그 줄에 설명만 붙인다.
 */
private fun dataFields(
    type: KType,
    dataPath: String,
    optional: Boolean,
    description: String?,
    children: List<RestDocsField> = responseFieldsOf(type, dataPath),
): List<RestDocsField> {
    val parentPath = dataPath.removeSuffix("[]")
    val label = description ?: (type.classifier as? KClass<*>)?.simpleName.orEmpty()
    val existing = children.firstOrNull { it.descriptor.path == parentPath }

    val fields = if (existing != null) {
        existing.descriptor.description(label)
        children
    } else {
        listOf(parentPath type (if (dataPath.endsWith("[]")) ARRAY else OBJECT) means label) + children
    }

    return fields.optionalIf(optional)
}

private fun List<RestDocsField>.optionalIf(optional: Boolean): List<RestDocsField> =
    if (optional) map { it.optional() } else this
