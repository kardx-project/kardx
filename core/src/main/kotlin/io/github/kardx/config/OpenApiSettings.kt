package io.github.kardx.config

import io.github.kardx.openapi.OperationIdResolver
import io.github.kardx.openapi.OperationIdRule
import io.github.kardx.openapi.SchemaNameResolver
import io.github.kardx.openapi.SchemaNames
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

/**
 * OpenAPI 리소스(`resource.json`)와 스펙 생성.
 * [RestDocsSettings.openApi] 로 읽고 `openApi { }` 블록으로 바꾼다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     openApi { errorSchema<ErrorResponse>() }
 * }
 * ```
 */
@RestDocsSettingsDsl
public class OpenApiSettings internal constructor() {
    /**
     * epages restdocs-api-spec 이 클래스패스에 있을 때 OpenAPI 리소스(`resource.json`)도 만들지 여부. 기본값 true.
     * epages 가 없으면 이 값과 상관없이 만들지 않는다. yml: `kardx.open-api.enabled`.
     */
    public var enabled: Boolean = true

    /**
     * 끝나지 않는 스트림([io.github.kardx.sse.performStream] 으로 문서화한 SSE)도 OpenAPI 에 넣을지 여부. 기본값 false.
     * Swagger UI 는 응답이 끝나야 결과를 보여 주므로 Try it out 이 계속 로딩만 돈다. 이런 API 는 AsyncAPI 에서 본다.
     * yml: `kardx.open-api.include-streams`.
     */
    public var includeStreams: Boolean = false

    /**
     * 요청의 `Authorization` 헤더로 OpenAPI 보안 스키마를 만들지 여부. 기본값 true.
     * `Bearer` 는 HTTP bearer(JWT), `Basic` 은 HTTP basic 스키마가 되어 Swagger UI 에 Authorize 버튼이 생긴다.
     * 실제 토큰은 스펙에 들어가지 않는다 (OpenAPI 조각에만 형식을 맞춘 가짜 JWT 를 쓴다).
     * yml: `kardx.open-api.security-schemes`.
     */
    public var securitySchemes: Boolean = true

    /**
     * Swagger UI 의 Authorize 창에서 한 번만 입력해 모든 요청에 붙이는 헤더 이름. 기본값 없음.
     * 이 헤더는 OpenAPI 에서 헤더 파라미터 대신 `apiKey`(in: header) 보안 스키마가 된다.
     * 테스트에서 `requestHeaders(...)` 로 문서화한 헤더에만 적용하며, Gradle 플러그인이 `openapi3` 결과를 고친다.
     * REST Docs 문서에는 영향이 없다. yml: `kardx.open-api.global-headers` (Gradle 의 같은 이름 설정과 합쳐진다).
     *
     * ```kotlin
     * globalHeaders = setOf("X-Tenant-Id", "X-Client-Version")
     * ```
     */
    public var globalHeaders: Set<String> = emptySet()
        set(value) {
            field = value.toSet()
        }

    /**
     * 오류 응답(4xx·5xx)의 공통 OpenAPI 스키마 이름. 테스트에서 `openApi { responseSchema }` 를 정하지 않은 오류 응답에 쓴다.
     * 오류 응답이 모두 같은 구조(같은 클래스)일 때 쓴다. 구조가 다르면 epages 가 하나만 남기므로 테스트마다 이름을 정한다.
     * yml: `kardx.open-api.error-schema` (스키마 이름, 또는 클래스 FQCN 이면 그 클래스의 스키마 이름).
     *
     * ```kotlin
     * errorSchema = "ErrorResponse"
     * errorSchema(ErrorResponse::class)
     * ```
     */
    public var errorSchema: String? = null

    /**
     * true 이면 `responseFieldsOf<T>()`, `requestFieldsOf<T>()`, `wrappedResponseFieldsOf<T>()` 로 추출한 본문에
     * 그 타입의 OpenAPI 스키마 이름을 붙인다 (`ApiResponse<TodoResponse>` → `ApiResponseTodoResponse`).
     * 테스트의 `openApi { responseSchema }` 와 [errorSchema] 가 우선한다. 기본값 true.
     * 끄면 epages 가 경로와 해시로 만든 이름을 쓴다. yml: `kardx.open-api.schema-names`.
     */
    public var schemaNames: Boolean = true

    /**
     * true 이면 [ExtractionSettings.responseWrapper] 로 감싼 응답(`wrappedResponseFieldsOf<T>()`)의 자동 스키마 이름에서 래퍼 이름을 뺀다
     * (`RestResponse<ProductResponse>` → `ProductResponse`, `RestResponse<List<ProductResponse>>` → `ListProductResponse`).
     * 스키마 내용은 그대로 래퍼를 포함한다. 뗀 이름이 모양이 다른 다른 스키마(같은 타입의 요청 본문 등)와 겹치면 Gradle 플러그인이
     * 래퍼를 붙인 이름 등 다음 후보로 나누고 빌드 로그에 남긴다. 데이터가 `Unit`·`Any` 면 래퍼 이름을 둔다.
     * [schemaNames] 가 켜져 있을 때만 쓴다. 기본값 false. yml: `kardx.open-api.schema-names-without-wrapper`.
     */
    public var schemaNamesWithoutWrapper: Boolean = false

    /**
     * 타입 → 스키마 이름 규칙. `null` 이면 [SchemaNames.of] (클래스 이름, 제네릭은 이어 붙임). 코드로만 정한다.
     * [schemaNames] 가 켜져 있을 때 추출한 타입에만 쓴다. 테스트에서 직접 정한 이름에는 쓰지 않는다.
     */
    public var schemaNameResolver: SchemaNameResolver? = null

    /**
     * OpenAPI operationId 를 만드는 규칙. 기본값 [OperationIdRule.HANDLER_METHOD] (요청을 처리한 컨트롤러 메서드 이름,
     * `searchUsers`). 결과는 생성된 OpenAPI 의 `operationId` 와 Swagger UI·코드 생성기의 메서드 이름이 된다.
     * 테스트의 `openApi { operationId }` 와 [operationIdResolver] 가 우선한다.
     * yml: `kardx.open-api.operation-id-rule` (`handler-method`, `controller-and-method`, `path`, `test-name`).
     *
     * ```kotlin
     * operationIdRule = OperationIdRule.PATH // GET /api/v1/users/{id} → getApiV1UsersById
     * ```
     */
    public var operationIdRule: OperationIdRule = OperationIdRule.HANDLER_METHOD

    /**
     * operationId 를 직접 만드는 규칙. 요청마다 [OperationIdInput] (HTTP 메서드, 경로, 컨트롤러·메서드 이름, 테스트 기반 id)을 받아
     * id 를 돌려준다. `null` 을 돌려주면 [operationIdRule] 로 만든다. 돌려준 id 는 그대로 쓰이므로(겹치면 Gradle 빌드 경고)
     * 겹치지 않게 만든다. 비어 있거나 공백이 있으면 문서화가 실패한다. 테스트의 `openApi { operationId }` 가 우선한다.
     * 기본값 `null`. 코드로만 정한다.
     *
     * ```kotlin
     * operationIdResolver = { input -> input.handlerMethodName?.let { "${input.httpMethod.lowercase()}_$it" } }
     * ```
     */
    public var operationIdResolver: OperationIdResolver? = null

    /** [errorSchema] 를 클래스 이름으로 정한다. `errorSchema(ErrorResponse::class)` */
    public fun errorSchema(type: KClass<*>) {
        errorSchema = SchemaNames.of(type)
    }

    /** [errorSchema] 를 타입으로 정한다. `errorSchema<ErrorResponse>()` */
    public inline fun <reified T : Any> errorSchema() {
        errorSchema = SchemaNames.of(typeOf<T>())
    }

    internal fun copy(): OpenApiSettings = OpenApiSettings().also {
        it.enabled = enabled
        it.includeStreams = includeStreams
        it.securitySchemes = securitySchemes
        it.globalHeaders = globalHeaders
        it.errorSchema = errorSchema
        it.schemaNames = schemaNames
        it.schemaNamesWithoutWrapper = schemaNamesWithoutWrapper
        it.schemaNameResolver = schemaNameResolver
        it.operationIdRule = operationIdRule
        it.operationIdResolver = operationIdResolver
    }
}
