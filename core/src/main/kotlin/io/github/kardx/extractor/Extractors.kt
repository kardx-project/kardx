package io.github.kardx.extractor

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.RestDocsParam
import io.github.kardx.internal.introspection.FieldExtractor
import io.github.kardx.internal.introspection.FieldExtractor.Mode
import io.github.kardx.internal.introspection.FieldOrigins
import io.github.kardx.internal.introspection.FieldSources
import io.github.kardx.internal.introspection.TypedFields
import io.github.kardx.internal.introspection.toRestDocsField
import io.github.kardx.internal.introspection.toRestDocsParam
import io.github.kardx.internal.provenance.FieldOrigin
import io.github.kardx.internal.provenance.FieldSource
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

/**
 * 응답 본문 필드를 타입에서 추출한 목록을 돌려준다. 제네릭·컬렉션 타입도 그대로 쓴다.
 * 목록을 그대로 등록할 때는 `andDocument` 블록의 단축형 [responseFields]
 * (`responseFields<UserResponse>()`)를 쓰고, 이 함수는 목록을 고치거나 합칠 때 쓴다.
 *
 * ```kotlin
 * responseFieldsOf<UserResponse>()              // id, nickname, ...
 * responseFieldsOf<UserResponse>("data")        // data.id, data.nickname, ...
 * responseFieldsOf<List<UserResponse>>()        // [].id, [].nickname, ...
 * responseFieldsOf<Page<UserResponse>>()        // content, content[].id, ..., totalElements, totalPages
 *
 * responseFields(responseFieldsOf<UserResponse>().except("internalNote"))
 * ```
 *
 * 경로·optional·subsection 규칙은 문서 docs/http-api.md 의 "필드 자동 추출 › 추출 규칙" 을 참고한다.
 *
 * @param path 추출한 필드 앞에 붙일 경로
 */
public inline fun <reified T> responseFieldsOf(path: String = ""): List<RestDocsField> =
    responseFieldsOf(typeOf<T>(), path)

/** [responseFieldsOf] 의 [KClass] 버전. 타입 인자는 star projection 으로 본다. */
public fun responseFieldsOf(kClass: KClass<*>, path: String = ""): List<RestDocsField> =
    responseFieldsOf(kClass.starProjectedType, path)

/**
 * [responseFieldsOf] 의 [KType] 버전.
 * 추출한 필드가 없으면(값 하나인 `String`·`Long` 본문, 경로 없는 `Map`, 프로퍼티 없는 클래스) 경고 로그를 남긴다.
 */
public fun responseFieldsOf(type: KType, path: String = ""): List<RestDocsField> = typed(
    type,
    path,
    FieldExtractor(Mode.BODY).extract(type, path)
        .map { it.toRestDocsField() }
        .warnIfEmpty("responseFieldsOf", type),
)

/**
 * 요청 본문 필드를 타입에서 추출한 목록을 돌려준다. 규칙은 [responseFieldsOf] 와 같고,
 * 기본값이 있는 생성자 파라미터(`val size: Int = 20`)와 생성자 밖 `var` 는 생략할 수 있으므로 optional 로 표시한다.
 * 요청으로 받지 않는 프로퍼티(생성자 밖 `val`, `@JsonProperty(access = READ_ONLY)`)는 뺀다.
 * 목록을 그대로 등록할 때는 `andDocument` 블록의 단축형 [requestFields]
 * (`requestFields<CreateUserRequest>()`)를 쓴다.
 */
public inline fun <reified T> requestFieldsOf(path: String = ""): List<RestDocsField> =
    requestFieldsOf(typeOf<T>(), path)

/** [requestFieldsOf] 의 [KClass] 버전. */
public fun requestFieldsOf(kClass: KClass<*>, path: String = ""): List<RestDocsField> =
    requestFieldsOf(kClass.starProjectedType, path)

/** [requestFieldsOf] 의 [KType] 버전. 추출한 필드가 없으면 경고 로그를 남긴다. */
public fun requestFieldsOf(type: KType, path: String = ""): List<RestDocsField> = typed(
    type,
    path,
    FieldExtractor(Mode.BODY, request = true).extract(type, path)
        .map { it.toRestDocsField() }
        .warnIfEmpty("requestFieldsOf", type),
)

/**
 * 본문 타입을 기억한 목록 (OpenAPI 스키마 이름 기본값). 경로 없이 추출했으면 본문이 [type], `[]` 면 [type] 의 목록이다.
 * 다른 경로(`data`)면 본문 전체의 타입을 알 수 없으므로 타입을 기억하지 않고, 다른 타입의 목록과 합쳐도 그 타입 이름이
 * 붙지 않도록 출처를 [FieldOrigin.UNTYPED] 로 적는다. 경로와 상관없이 추출한 타입과 경로는 [FieldSources] 에 적는다
 * (다형 프로퍼티의 위치).
 */
private fun typed(type: KType, path: String, fields: List<RestDocsField>): List<RestDocsField> {
    val prefix = path.trim()

    FieldSources.mark(fields, FieldSource(type, prefix))

    return when (prefix) {
        "" -> TypedFields(type, fields)
        "[]" -> TypedFields(List::class.createType(listOf(KTypeProjection.invariant(type))), fields)
        else -> fields.also { FieldOrigins.mark(it, FieldOrigin.UNTYPED) }
    }
}

/**
 * 쿼리 / 경로 / 폼 파라미터를 타입에서 추출한 목록을 돌려준다.
 * 중첩 객체는 `a.b` 로 펼치고, 배열은 파라미터 이름 하나로 문서화한다.
 * nullable 이거나 기본값이 있는 프로퍼티는 optional 이다.
 * 목록을 그대로 등록할 때는 `andDocument` 블록의 단축형 [pathParameters]·[queryParameters]·[formParameters]
 * (`pathParameters<ProductKey>()`, `queryParameters<ProductSearchCondition>()`)를 쓰고, 이 함수는 목록을 고치거나 합칠 때 쓴다.
 *
 * ```kotlin
 * pathParameters<ProductKey>()                                                          // andDocument 블록 안
 * queryParameters(parametersOf<ProductSearchCondition>().except("debug"))              // 목록을 고칠 때
 * ```
 *
 * @param path 추출한 파라미터 이름 앞에 붙일 경로 (`"filter"` → `filter.status`)
 */
public inline fun <reified T> parametersOf(path: String = ""): List<RestDocsParam> = parametersOf(typeOf<T>(), path)

/** [parametersOf] 의 [KClass] 버전. */
public fun parametersOf(kClass: KClass<*>, path: String = ""): List<RestDocsParam> =
    parametersOf(kClass.starProjectedType, path)

/** [parametersOf] 의 [KType] 버전. 추출한 파라미터가 없으면 경고 로그를 남긴다. */
public fun parametersOf(type: KType, path: String = ""): List<RestDocsParam> =
    FieldExtractor(Mode.PARAMETER, request = true).extract(type, path)
        .map { it.toRestDocsParam() }
        .warnIfEmpty("parametersOf", type)

/**
 * 추출 결과가 비면 알린다. 빈 목록을 넘긴 표는 만들어지지 않으므로(파라미터는 REST Docs 가 검증한다) 문서에서 조용히 빠지기 쉽다.
 */
private fun <T> List<T>.warnIfEmpty(function: String, type: KType): List<T> = also {
    if (isEmpty()) {
        LOGGER.log(
            System.Logger.Level.WARNING,
            RestDocsErrorCode.NOTHING_EXTRACTED.message(
                "$function<$type>() found nothing to document. A single-value body (String, Long ...) or a Map " +
                    "has no fixed fields: pass a path (e.g. \"data\") or leave the section out.",
            ),
        )
    }
}

private val LOGGER: System.Logger = System.getLogger("io.github.kardx.extractor")
