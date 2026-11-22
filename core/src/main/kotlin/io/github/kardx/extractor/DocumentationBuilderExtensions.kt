package io.github.kardx.extractor

import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.RestDocsField

// 단축형은 DocumentationBuilder 멤버가 아니라 이 패키지의 확장 함수다.
// 추출기(extractor)가 dsl 에 기대므로 dsl 이 추출기를 부르면 패키지 순환이 생긴다.
// 타입 인자를 준 호출(`responseFields<T>()`)만 이 함수로 오고, 타입 인자가 없는 호출은 멤버(목록·가변 인자)로 간다.

/**
 * 요청 본문 필드를 타입에서 추출해 등록한다. `requestFields(requestFieldsOf<T>(path))` 와 같다.
 * `request-fields` 표와 OpenAPI 요청 본문 스키마가 된다. 목록을 고치거나 합칠 때는 [requestFieldsOf] 를 쓴다.
 *
 * ```kotlin
 * .andDocument {
 *     requestFields<CreateUserRequest>()
 * }
 * ```
 *
 * @param path 추출한 필드 앞에 붙일 경로. 기본값은 본문 루트
 */
public inline fun <reified T> DocumentationBuilder.requestFields(path: String = ""): Unit =
    requestFields(requestFieldsOf<T>(path))

/**
 * 응답 본문 필드를 타입에서 추출해 등록한다. `responseFields(responseFieldsOf<T>(path))` 와 같다.
 * `response-fields` 표와 OpenAPI 응답 본문 스키마가 된다. 목록을 고치거나 합칠 때는 [responseFieldsOf] 를 쓴다.
 *
 * ```kotlin
 * .andDocument {
 *     responseFields<UserResponse>()
 *     responseFields<List<UserResponse>>()   // 배열 본문: [].id, [].nickname, ...
 * }
 * ```
 *
 * @param path 추출한 필드 앞에 붙일 경로. 기본값은 본문 루트
 */
public inline fun <reified T> DocumentationBuilder.responseFields(path: String = ""): Unit =
    responseFields(responseFieldsOf<T>(path))

/**
 * 공통 응답 래퍼 필드와 [T] 의 데이터 필드를 합쳐 응답 필드로 등록한다.
 * `responseFields(wrappedResponseFieldsOf<T>(dataPath, dataOptional))` 와 같다. 인자의 뜻은 [wrappedResponseFieldsOf] 와 같다.
 *
 * 래퍼는 프로젝트에서 한 번 등록한다 ([io.github.kardx.config.ExtractionSettings.responseWrapper]).
 * 등록하지 않았으면 래퍼 필드 없이 [T] 의 필드만 등록한다.
 *
 * ```yaml
 * # 테스트가 읽는 application.yml
 * restdocs:
 *   kotlin:
 *     response-wrapper:
 *       class: com.example.ApiResponse
 *       data-path: data          # 생략하면 data
 * ```
 *
 * ```kotlin
 * // 코드로 등록할 때 (`@BeforeAll`, `companion object`, Kotest 프로젝트 설정 등)
 * RestDocsConfiguration.configure {
 *     extraction { responseWrapper<ApiResponse<*>>(dataPath = "data") }
 * }
 * ```
 *
 * 래퍼 필드의 설명은 래퍼 클래스의 프로퍼티에 붙인 `@Description` 에서 가져온다.
 *
 * ```kotlin
 * .andDocument {
 *     wrappedResponseFields<UserResponse>()                      // { "code": 200, "data": { "id": 1, ... } }
 *     wrappedResponseFields<ProductSummary>(dataOptional = true) // 실패 응답처럼 data 가 없을 수 있을 때
 * }
 * ```
 *
 * @param dataPath 데이터 위치. 기본값은 [io.github.kardx.config.ResponseWrapper.dataPath]
 * @param dataOptional true 이면 데이터 필드와 그 하위 필드를 모두 optional 로 표시한다
 */
public inline fun <reified T> DocumentationBuilder.wrappedResponseFields(
    dataPath: String? = null,
    dataOptional: Boolean = false,
): Unit = responseFields(wrappedResponseFieldsOf<T>(dataPath, dataOptional))

/**
 * 공통 응답 래퍼 필드와 직접 기술한 데이터 필드를 합쳐 응답 필드로 등록한다.
 * `responseFields(wrappedResponseFieldsOf(*fields))` 와 같다. 래퍼를 등록하지 않았으면 [fields] 만 등록한다.
 * 래퍼는 yml `kardx.response-wrapper.*` 나 `RestDocsConfiguration.configure { extraction { responseWrapper<...>() } }` 로
 * 등록한다 ([io.github.kardx.config.ExtractionSettings.responseWrapper]).
 *
 * ```kotlin
 * .andDocument {
 *     wrappedResponseFields("data.id" type NUMBER means "상품 ID")
 * }
 * ```
 */
public fun DocumentationBuilder.wrappedResponseFields(vararg fields: RestDocsField): Unit =
    responseFields(wrappedResponseFieldsOf(*fields))

/**
 * 경로 파라미터(`/api/products/{category}/{id}`)를 타입에서 추출해 등록한다. `pathParameters(parametersOf<T>(path))` 와 같다.
 * `path-parameters` 표와 OpenAPI `in: path` 파라미터가 된다. 목록을 고치거나 합칠 때는 [parametersOf] 를 쓴다.
 * 프로퍼티 이름이 URI 템플릿의 변수 이름과 같아야 한다 (REST Docs 가 문서와 요청의 경로 변수를 대조한다).
 *
 * ```kotlin
 * .andDocument {
 *     pathParameters<ProductKey>()   // class ProductKey(val category: String, val id: Long)
 * }
 * ```
 *
 * @param path 추출한 파라미터 이름 앞에 붙일 경로. 기본값은 없음
 */
public inline fun <reified T> DocumentationBuilder.pathParameters(path: String = ""): Unit =
    pathParameters(parametersOf<T>(path))

/**
 * 쿼리 파라미터를 타입에서 추출해 등록한다. `queryParameters(parametersOf<T>(path))` 와 같다.
 * `query-parameters` 표와 OpenAPI `in: query` 파라미터가 된다. 목록을 고치거나 합칠 때는 [parametersOf] 를 쓴다.
 *
 * ```kotlin
 * .andDocument {
 *     queryParameters<ProductSearchCondition>()
 * }
 * ```
 *
 * @param path 추출한 파라미터 이름 앞에 붙일 경로. 기본값은 없음
 */
public inline fun <reified T> DocumentationBuilder.queryParameters(path: String = ""): Unit =
    queryParameters(parametersOf<T>(path))

/**
 * 폼 파라미터(`application/x-www-form-urlencoded`)를 타입에서 추출해 등록한다. `formParameters(parametersOf<T>(path))` 와 같다.
 * `form-parameters` 표와 OpenAPI 요청 본문이 된다. 목록을 고치거나 합칠 때는 [parametersOf] 를 쓴다.
 *
 * ```kotlin
 * .andDocument {
 *     formParameters<SignUpForm>()
 * }
 * ```
 *
 * @param path 추출한 파라미터 이름 앞에 붙일 경로. 기본값은 없음
 */
public inline fun <reified T> DocumentationBuilder.formParameters(path: String = ""): Unit =
    formParameters(parametersOf<T>(path))

/**
 * 멀티파트 파트 [part] 의 본문 필드를 타입에서 추출해 등록한다. `requestPartFields(part, requestFieldsOf<T>(path))` 와 같다.
 * `request-part-<part>-fields` 표가 된다. 목록을 고치거나 합칠 때는 [requestFieldsOf] 를 쓴다.
 *
 * ```kotlin
 * .andDocument {
 *     requestPartFields<UploadMetadata>("metadata")
 * }
 * ```
 *
 * @param path 추출한 필드 앞에 붙일 경로. 기본값은 파트 본문 루트
 */
public inline fun <reified T> DocumentationBuilder.requestPartFields(part: String, path: String = ""): Unit =
    requestPartFields(part, requestFieldsOf<T>(path))
