package io.github.kardx.config

import java.util.Objects
import kotlin.reflect.KClass

/**
 * 모든 API 가 공통으로 쓰는 응답 래퍼.
 *
 * `{ "code": 200, "message": "OK", "data": { ... } }` 처럼 응답을 감싸는 클래스가 있으면 등록한다.
 * [io.github.kardx.extractor.wrappedResponseFieldsOf] 가 래퍼 필드와 데이터 필드를 합쳐 문서화한다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     extraction { responseWrapper = ResponseWrapper(ApiResponse::class, dataPath = "data") }
 * }
 * ```
 *
 * @property wrapperClass 래퍼 클래스. 이 클래스의 필드가 응답 필드 표 앞쪽에 오고, OpenAPI 자동 스키마 이름의
 *   앞부분이 된다 (예: `ApiResponseTodoResponse`). yml 키 `kardx.response-wrapper.class` (FQCN)
 * @property dataPath 래퍼 안에서 실제 데이터가 있는 필드 경로. 데이터 타입의 필드가 이 경로 아래(`data.id` 등)에
 *   붙는다. 빈 값은 허용하지 않는다. yml 키 `kardx.response-wrapper.data-path`, 기본값 [DEFAULT_DATA_PATH]
 */
public class ResponseWrapper(public val wrapperClass: KClass<*>, public val dataPath: String = DEFAULT_DATA_PATH) {
    init {
        require(dataPath.isNotBlank()) { "dataPath must not be blank" }
    }

    override fun equals(other: Any?): Boolean = other is ResponseWrapper &&
        wrapperClass == other.wrapperClass &&
        dataPath == other.dataPath

    override fun hashCode(): Int = Objects.hash(wrapperClass, dataPath)

    override fun toString(): String = "ResponseWrapper(wrapperClass=$wrapperClass, dataPath=$dataPath)"

    /** 기본값 상수를 담는다. */
    public companion object {
        /** [dataPath] 의 기본값 `data`. yml 에서 `response-wrapper.data-path` 를 생략했을 때도 이 값을 쓴다. */
        public const val DEFAULT_DATA_PATH: String = "data"
    }
}
