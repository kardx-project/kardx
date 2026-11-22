package io.github.kardx.config

import kotlin.reflect.KClass

/**
 * 타입에서 필드·파라미터를 뽑는 방식 (`responseFieldsOf<T>()`, `requestFieldsOf<T>()` 등).
 * [RestDocsSettings.extraction] 로 읽고 `extraction { }` 블록으로 바꾼다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     extraction { maxDepth = 5 }
 * }
 * ```
 */
@RestDocsSettingsDsl
public class ExtractionSettings internal constructor() {
    /**
     * 공통 응답 래퍼. 없으면 `null`. yml 로는 `kardx.response-wrapper.class`,
     * `kardx.response-wrapper.data-path` (기본값 `data`).
     * [io.github.kardx.extractor.wrappedResponseFieldsOf] 가 래퍼 필드와 데이터 필드를 합칠 때 쓴다.
     *
     * ```kotlin
     * responseWrapper = ResponseWrapper(ApiResponse::class, dataPath = "data")
     * responseWrapper<ApiResponse<*>>(dataPath = "data")
     * ```
     */
    public var responseWrapper: ResponseWrapper? = null

    /** [responseWrapper] 를 클래스로 정한다. `responseWrapper(ApiResponse::class, "data")` */
    public fun responseWrapper(wrapperClass: KClass<*>, dataPath: String = ResponseWrapper.DEFAULT_DATA_PATH) {
        responseWrapper = ResponseWrapper(wrapperClass, dataPath)
    }

    /** [responseWrapper] 를 타입으로 정한다. `responseWrapper<ApiResponse<*>>("data")` */
    public inline fun <reified T : Any> responseWrapper(dataPath: String = ResponseWrapper.DEFAULT_DATA_PATH) {
        responseWrapper(T::class, dataPath)
    }

    /**
     * Jackson 매퍼(Jackson 2 `ObjectMapper` 또는 Jackson 3 `JsonMapper`)를 제공하는 함수. `null` 이면 다음 순서로 고른다.
     * 1. 이 스레드에서 [io.github.kardx.mockmvc.restDocsMockMvc] 나
     *    [io.github.kardx.webtestclient.restDocsWebTestClient] 가 애플리케이션 컨텍스트에서 찾은 매퍼 빈
     * 2. 클래스패스의 Jackson 3, 없으면 Jackson 2 로 모듈을 등록해 만든 매퍼
     */
    public var objectMapper: ObjectMapperProvider? = null

    /**
     * 필드 이름 변환 규칙 (예: camelCase → snake_case). `null` 이면 매퍼의 Jackson `PropertyNamingStrategy` 를 따른다.
     * `@JsonProperty` 로 명시한 이름과 클래스의 `@JsonNaming` 이 우선한다.
     */
    public var propertyNameStrategy: PropertyNameStrategy? = null

    /**
     * Enum 상수의 설명으로 쓸 프로퍼티 이름. 기본값 `description`. yml: `kardx.enum-description-property`.
     * 필드 → getter(`getDescription()`) → 같은 이름의 인자 없는 메서드 순서로 찾는다.
     * 자동 추출한 Enum 필드의 설명 뒤에 `값 : 설명` 목록이 붙는다. 값은 JSON 에 실제로 나가는 값이고,
     * 이 프로퍼티가 없는 Enum 은 값만 나열한다.
     * `@EnumCode` 코드 문서에서도 `descriptionProperty` 를 비워 두면 이 프로퍼티(없으면 `description`)를 설명 열에 쓴다.
     */
    public var enumDescriptionProperty: String = "description"

    /**
     * 자동 추출의 최대 중첩 깊이. 넘어서면 subsection 으로 문서화한다. 기본값 10.
     * yml: `kardx.max-extraction-depth`.
     */
    public var maxDepth: Int = DEFAULT_MAX_DEPTH

    /**
     * 배열 원소의 하위 필드(`items[].name`)를 모두 optional 로 표시할지 여부.
     * REST Docs 는 빈 배열 아래의 필수 필드를 "payload 에서 찾을 수 없음" 으로 처리하므로 빈 배열을 문서화할 때 켠다.
     * 기본값 false. yml: `kardx.array-element-fields-optional`.
     */
    public var arrayElementFieldsOptional: Boolean = false

    /**
     * kotlinx.serialization 의 `@Serializable` 클래스를 kotlinx 가 쓰는 모양(`@SerialName` 이름, 기본값이 있는 프로퍼티는
     * 생략 가능, sealed 판별자 `type`)으로 추출할지 여부. 기본값 true.
     * 테스트 클래스패스에 kotlinx-serialization-json 이 있을 때만 적용된다. 그때 Spring MVC·WebFlux 는 `@Serializable`
     * 클래스를 Jackson 보다 먼저 kotlinx 로 직렬화한다. 앱이 그런 클래스도 Jackson 으로 직렬화하도록 바꿨으면 false 로 둔다.
     * yml: `kardx.kotlinx-serialization`.
     */
    public var kotlinxSerialization: Boolean = true

    internal fun copy(): ExtractionSettings = ExtractionSettings().also {
        it.responseWrapper = responseWrapper
        it.objectMapper = objectMapper
        it.propertyNameStrategy = propertyNameStrategy
        it.enumDescriptionProperty = enumDescriptionProperty
        it.maxDepth = maxDepth
        it.arrayElementFieldsOptional = arrayElementFieldsOptional
        it.kotlinxSerialization = kotlinxSerialization
    }

    private companion object {
        private const val DEFAULT_MAX_DEPTH = 10
    }
}
