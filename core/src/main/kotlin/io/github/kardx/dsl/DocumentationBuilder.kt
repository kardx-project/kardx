package io.github.kardx.dsl

import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.openapi.SchemaNames
import io.github.kardx.openapi.isValidOperationId
import org.springframework.http.HttpStatusCode
import org.springframework.restdocs.snippet.Snippet
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

// 문서화할 수 있는 REST Docs 항목마다 목록·가변 인자 두 형태를 제공하므로 함수가 많다.
/**
 * `andDocument` 블록. 같은 함수를 여러 번 부르면 항목이 이어 붙는다.
 * 각 함수는 목록(`List`)과 가변 인자 두 형태를 받는다.
 *
 * 타입에서 추출한 목록을 그대로 등록하는 단축형(`requestFields<T>()`, `responseFields<T>()`,
 * `wrappedResponseFields<T>()`, `queryParameters<T>()`, `formParameters<T>()`, `requestPartFields<T>(part)`)은
 * `io.github.kardx.extractor` 패키지의 확장 함수다.
 */
@Suppress("TooManyFunctions")
@RestDocsDsl
public class DocumentationBuilder internal constructor() {
    /** API 설명. `api-summary` 스니펫과 OpenAPI description 에 들어간다. */
    public var description: String? = null

    /**
     * true 이면 모든 항목([RelaxedSection] 전부: 파라미터·본문 필드·파트·쿠키·링크·SSE 이벤트)을 relaxed 모드로 검증한다
     * (문서화하지 않은 항목 허용). 항목 일부만 느슨하게 하려면 [relaxed] 함수를 쓴다. false 로 정하면 모두 엄격하게 되돌린다.
     * epages 는 relaxed 검증을 지원하지 않으므로 OpenAPI 스펙에는 요청·응답 필드 설명이 들어가지 않는다.
     *
     * ```kotlin
     * .andDocument {
     *     responseFields("id" type NUMBER means "ID")
     *     relaxed = true
     * }
     * ```
     */
    public var relaxed: Boolean
        get() = relaxedSections.containsAll(RelaxedSection.entries)
        set(value) {
            relaxedSections.clear()

            if (value) {
                relaxedSections += RelaxedSection.entries
            }
        }

    internal val relaxedSections = mutableSetOf<RelaxedSection>()

    /**
     * [sections] 만 relaxed 모드로 검증한다 (문서화하지 않은 항목 허용). 나머지 항목은 엄격하게 검증한다.
     * 다시 부르면 이어 붙는다. 모든 항목은 `relaxed = true` 와 같다.
     * [RelaxedSection.REQUEST_FIELDS]·[RelaxedSection.RESPONSE_FIELDS] 는 OpenAPI 스펙에 그 필드 설명이 들어가지 않는다.
     *
     * ```kotlin
     * .andDocument {
     *     queryParameters<ProductSearchCondition>()
     *     responseFields<ProductPage>()
     *     relaxed(RelaxedSection.RESPONSE_FIELDS, RelaxedSection.EVENTS)
     * }
     * ```
     */
    public fun relaxed(vararg sections: RelaxedSection) {
        relaxedSections += sections
    }

    /**
     * 더 이상 쓰지 않는 API 로 표시한다. `api-summary` 스니펫에 `*Deprecated*` 줄이 생기고 OpenAPI operation 에
     * `deprecated: true` 가 붙는다. [OpenApiBuilder.deprecated] 와 같은 값이다 (어느 쪽에서 바꿔도 같다).
     *
     * 블록에서 정하지 않으면 요청을 처리한 핸들러 메서드나 컨트롤러 클래스의 `@Deprecated`(Kotlin·Java)를 따르고, Kotlin
     * `@Deprecated` 의 message 를 이유로 쓴다. `deprecated = false` 로 정하면 핸들러가 `@Deprecated` 여도 표시하지 않는다.
     * 핸들러는 MockMvc 와 핸들러를 기록하는 WebTestClient(`restDocsWebTestClient`, `recordHandlers()`)에서 안다.
     * 블록 안에서 읽은 값은 블록에서 정한 값이다 (핸들러의 `@Deprecated` 는 문서화할 때 반영되므로 읽은 값에는 없다).
     */
    public var deprecated: Boolean
        get() = openApi.deprecated
        set(value) {
            openApi.deprecated = value
        }

    internal val pathParameters = mutableListOf<RestDocsParam>()
    internal val queryParameters = mutableListOf<RestDocsParam>()
    internal val formParameters = mutableListOf<RestDocsParam>()
    internal val requestHeaders = mutableListOf<RestDocsHeader>()
    internal val responseHeaders = mutableListOf<RestDocsHeader>()
    internal val requestCookies = mutableListOf<RestDocsCookie>()
    internal val responseCookies = mutableListOf<RestDocsCookie>()
    internal val requestParts = mutableListOf<RestDocsPart>()
    internal val requestPartFields = linkedMapOf<String, MutableList<RestDocsField>>()
    internal val requestFields = mutableListOf<RestDocsField>()
    internal val responseFields = mutableListOf<RestDocsField>()
    internal val links = mutableListOf<RestDocsLink>()
    internal val events = mutableListOf<RestDocsEvent>()
    internal val snippets = mutableListOf<Snippet>()
    internal val errorResponses = mutableListOf<DeclaredErrorResponse>()

    /** 파일 요청 본문의 설명 ([requestFile]) */
    internal var requestFile: String? = null
        private set

    /** 파일 응답 본문의 설명 ([responseFile]) */
    internal var responseFile: String? = null
        private set

    /** 넘겨받은 목록 그대로 (타입에서 추출했다면 스키마 이름 기본값에 쓴다) */
    internal val requestFieldGroups = mutableListOf<List<RestDocsField>>()
    internal val responseFieldGroups = mutableListOf<List<RestDocsField>>()
    internal val openApi = OpenApiBuilder()
    internal val parameterSections = mutableSetOf<ParameterSection>()

    /** 경로 변수. `path-parameters` 표와 OpenAPI `in: path` 파라미터가 된다. `"id" pathMeans "사용자 ID"` */
    public fun pathParameters(parameters: List<RestDocsParam>) {
        pathParameters += parameters
        parameterSections += ParameterSection.PATH
    }

    /** [pathParameters] 의 가변 인자 버전. */
    public fun pathParameters(vararg parameters: RestDocsParam): Unit = pathParameters(parameters.toList())

    /**
     * 설명 없이 이름만 적은 경로 변수. `pathParameters("id")`
     *
     * 설명은 요청을 처리한 핸들러 메서드의 KDoc·Javadoc `@param` 에서 가져온다 (Gradle 플러그인의 source docs 색인, 실험 기능,
     * `sourceDocs { enabled = true }` 로 켠다).
     * 이름은 `@PathVariable` 의 `name`/`value`, 없으면 인자 이름으로 맞춘다. 수식어(`isOptional`, `example` 등)가 필요하면
     * `"id" pathMeans FromParamTag isOptional true` 처럼 [FromParamTag] 를 쓴다. 다시 부르면 이어 붙으므로 설명을 적은 파라미터와
     * 섞어 쓸 수 있다.
     * 핸들러를 모르거나 `@param` 이 없으면 테스트가 실패한다.
     */
    @ExperimentalRestDocsApi
    public fun pathParameters(name: String, vararg names: String): Unit =
        pathParameters((listOf(name) + names).map(::undescribedParameter))

    /**
     * 쿼리 파라미터. `query-parameters` 표와 OpenAPI `in: query` 파라미터가 된다.
     * `"page" queryMeans "페이지"` 또는 [io.github.kardx.extractor.parametersOf].
     * 타입에서 추출해 그대로 등록할 때는 단축형 [io.github.kardx.extractor.queryParameters] (`queryParameters<T>()`)
     */
    public fun queryParameters(parameters: List<RestDocsParam>) {
        queryParameters += parameters
        parameterSections += ParameterSection.QUERY
    }

    /** [queryParameters] 의 가변 인자 버전. */
    public fun queryParameters(vararg parameters: RestDocsParam): Unit = queryParameters(parameters.toList())

    /**
     * 설명 없이 이름만 적은 쿼리 파라미터. `queryParameters("keyword", "page")`
     *
     * 설명은 핸들러 메서드 KDoc·Javadoc 의 `@param` 에서 가져온다 (`@RequestParam` 의 이름, 없으면 인자 이름으로 맞춘다).
     * 나머지 규칙은 [pathParameters] 의 이름만 받는 버전과 같다.
     */
    @ExperimentalRestDocsApi
    public fun queryParameters(name: String, vararg names: String): Unit =
        queryParameters((listOf(name) + names).map(::undescribedParameter))

    /**
     * `application/x-www-form-urlencoded` 폼 파라미터. `form-parameters` 표와 OpenAPI 에 들어간다. `"name" formMeans "이름"`
     * 타입에서 추출해 그대로 등록할 때는 단축형 [io.github.kardx.extractor.formParameters] (`formParameters<T>()`)
     */
    public fun formParameters(parameters: List<RestDocsParam>) {
        formParameters += parameters
        parameterSections += ParameterSection.FORM
    }

    /** [formParameters] 의 가변 인자 버전. */
    public fun formParameters(vararg parameters: RestDocsParam): Unit = formParameters(parameters.toList())

    /**
     * 설명 없이 이름만 적은 폼 파라미터. `formParameters("name")`
     *
     * 설명은 핸들러 메서드 KDoc·Javadoc 의 `@param` 에서 가져온다 (`@RequestParam` 의 이름, 없으면 인자 이름으로 맞춘다).
     * 나머지 규칙은 [pathParameters] 의 이름만 받는 버전과 같다.
     */
    @ExperimentalRestDocsApi
    public fun formParameters(name: String, vararg names: String): Unit =
        formParameters((listOf(name) + names).map(::undescribedParameter))

    /**
     * 요청 헤더. `request-headers` 표와 OpenAPI `in: header` 파라미터가 된다. `"Authorization" headerMeans "Bearer 토큰"`
     * [io.github.kardx.config.OpenApiSettings.globalHeaders] 에 있는 헤더는 헤더 파라미터 대신
     * `apiKey` 보안 스키마가 된다.
     */
    public fun requestHeaders(headers: List<RestDocsHeader>) {
        requestHeaders += headers
    }

    /** [requestHeaders] 의 가변 인자 버전. */
    public fun requestHeaders(vararg headers: RestDocsHeader): Unit = requestHeaders(headers.toList())

    /**
     * 설명 없이 이름만 적은 요청 헤더. `requestHeaders("X-Request-Id")`
     *
     * 설명은 핸들러 메서드 KDoc·Javadoc 의 `@param` 에서 가져온다 (`@RequestHeader` 의 이름, 없으면 인자 이름으로 맞춘다.
     * 대소문자는 가리지 않는다). 나머지 규칙은 [pathParameters] 의 이름만 받는 버전과 같다.
     */
    @ExperimentalRestDocsApi
    public fun requestHeaders(name: String, vararg names: String): Unit =
        requestHeaders((listOf(name) + names).map(::undescribedHeader))

    /** 응답 헤더. `response-headers` 표와 OpenAPI 응답 `headers` 가 된다. `"ETag" headerMeans "리소스 버전"` */
    public fun responseHeaders(headers: List<RestDocsHeader>) {
        responseHeaders += headers.requireDescribed("response headers")
    }

    /** [responseHeaders] 의 가변 인자 버전. */
    public fun responseHeaders(vararg headers: RestDocsHeader): Unit = responseHeaders(headers.toList())

    /**
     * 요청 쿠키. `request-cookies` 표가 되고, Gradle 플러그인이 OpenAPI `in: cookie` 파라미터로 넣는다.
     * `"SESSION" cookieMeans "세션"`
     */
    public fun requestCookies(cookies: List<RestDocsCookie>) {
        requestCookies += cookies
    }

    /** [requestCookies] 의 가변 인자 버전. */
    public fun requestCookies(vararg cookies: RestDocsCookie): Unit = requestCookies(cookies.toList())

    /** 응답 쿠키. `response-cookies` 표가 되고, Gradle 플러그인이 OpenAPI 응답 `Set-Cookie` 헤더 설명에 목록으로 넣는다. */
    public fun responseCookies(cookies: List<RestDocsCookie>) {
        responseCookies += cookies
    }

    /** [responseCookies] 의 가변 인자 버전. */
    public fun responseCookies(vararg cookies: RestDocsCookie): Unit = responseCookies(cookies.toList())

    /**
     * 멀티파트 파트. `request-parts` 표가 되고, Gradle 플러그인이 OpenAPI `multipart/form-data` 요청 본문으로 넣는다
     * (실제 요청에 파일 이름이 있는 파트는 `string`/`binary`). `"file" partMeans "업로드 파일"`
     */
    public fun requestParts(parts: List<RestDocsPart>) {
        requestParts += parts
    }

    /** [requestParts] 의 가변 인자 버전. */
    public fun requestParts(vararg parts: RestDocsPart): Unit = requestParts(parts.toList())

    /**
     * 설명 없이 이름만 적은 멀티파트 파트. `requestParts("file")`
     *
     * 설명은 핸들러 메서드 KDoc·Javadoc 의 `@param` 에서 가져온다 (`@RequestPart`·`@RequestParam` 의 이름, 없으면 인자
     * 이름으로 맞춘다). 나머지 규칙은 [pathParameters] 의 이름만 받는 버전과 같다.
     */
    @ExperimentalRestDocsApi
    public fun requestParts(name: String, vararg names: String): Unit =
        requestParts((listOf(name) + names).map(::undescribedPart))

    /**
     * 멀티파트 파트 [part] 의 본문 필드. 파트마다 `request-part-<part>-fields` 표가 되고, 그 파트의 실제 본문으로 검증한다.
     * OpenAPI(Gradle 플러그인)에서는 multipart 요청 본문의 그 파트가 이 필드로 만든 스키마가 되고, 실제 파트의 Content-Type 이
     * `encoding` 에 들어간다. `requestPartFields("metadata", "id" type NUMBER means "ID")`
     * 타입에서 추출해 그대로 등록할 때는 단축형 [io.github.kardx.extractor.requestPartFields]
     * (`requestPartFields<UploadMetadata>("metadata")`)
     */
    public fun requestPartFields(part: String, fields: List<RestDocsField>) {
        requestPartFields.getOrPut(part) { mutableListOf() } += fields
    }

    /** [requestPartFields] 의 가변 인자 버전. */
    public fun requestPartFields(part: String, vararg fields: RestDocsField): Unit =
        requestPartFields(part, fields.toList())

    /**
     * 요청 본문 필드. 직접 기술하거나 [io.github.kardx.extractor.requestFieldsOf] 로 추출한 목록을 준다.
     * 추출한 목록을 그대로 등록할 때는 단축형 [io.github.kardx.extractor.requestFields] (`requestFields<T>()`).
     * `request-fields` 표와 OpenAPI 요청 본문 스키마가 된다. 실제 요청 본문으로 검증한다 ([relaxed] 참고).
     */
    public fun requestFields(fields: List<RestDocsField>) {
        requestFields += fields
        requestFieldGroups += fields
    }

    /** [requestFields] 의 가변 인자 버전. */
    public fun requestFields(vararg fields: RestDocsField): Unit = requestFields(fields.toList())

    /**
     * 응답 본문 필드. 직접 기술하거나 [io.github.kardx.extractor.responseFieldsOf],
     * [io.github.kardx.extractor.wrappedResponseFieldsOf] 로 추출한 목록을 준다.
     * 추출한 목록을 그대로 등록할 때는 단축형 [io.github.kardx.extractor.responseFields] (`responseFields<T>()`),
     * [io.github.kardx.extractor.wrappedResponseFields] (`wrappedResponseFields<T>()`).
     * `response-fields` 표와 OpenAPI 응답 본문 스키마가 된다. 실제 응답 본문으로 검증한다 ([relaxed] 참고).
     */
    public fun responseFields(fields: List<RestDocsField>) {
        responseFields += fields
        responseFieldGroups += fields
    }

    /** [responseFields] 의 가변 인자 버전. */
    public fun responseFields(vararg fields: RestDocsField): Unit = responseFields(fields.toList())

    /**
     * 응답 본문이 파일(PDF, 이미지, `application/octet-stream`, CSV 내보내기 …)임을 [description] 으로 설명한다.
     * `response-file` 표(설명, 미디어 타입, `Content-Disposition` 의 파일 이름, 테스트 응답의 크기)와 OpenAPI 응답 본문
     * 스키마(`type: string`, `format: binary`)의 `description` 이 된다. 바이너리 응답은 이 함수가 없어도 표가 생기고 설명만 빈다.
     *
     * ```kotlin
     * mockMvc.perform(get("/api/v1/products/{id}/manual", 1L))
     *     .andDocument {
     *         responseHeaders("Content-Disposition" headerMeans "첨부 파일 이름")
     *         responseFile("상품 설명서 PDF")
     *     }
     * ```
     *
     * @throws IllegalArgumentException [description] 이 비었거나, 같은 블록에서 두 번 불렀으면.
     *   [responseFields] 와 함께 쓰면 문서화할 때 실패한다 (파일 본문에는 필드가 없다)
     */
    public fun responseFile(description: String) {
        require(description.isNotBlank()) {
            RestDocsErrorCode.INVALID_FILE_BODY.message("responseFile needs a non-blank description.")
        }

        require(responseFile == null) {
            RestDocsErrorCode.INVALID_FILE_BODY.message(
                "responseFile is declared more than once in this andDocument block.",
            )
        }

        responseFile = description
    }

    /**
     * 요청 본문이 파일(`application/octet-stream`, `image/png` 업로드 …)임을 [description] 으로 설명한다.
     * `request-file` 표와 OpenAPI 요청 본문 스키마(`type: string`, `format: binary`)의 `description` 이 된다.
     * 바이너리 요청 본문은 이 함수가 없어도 표가 생긴다. multipart 업로드의 파일 파트는 [requestParts] 로 적는다.
     *
     * ```kotlin
     * mockMvc.perform(put("/api/v1/products/{id}/image", 1L).contentType(MediaType.IMAGE_PNG).content(png))
     *     .andDocument {
     *         requestFile("업로드할 이미지 원본")
     *     }
     * ```
     *
     * @throws IllegalArgumentException [description] 이 비었거나, 같은 블록에서 두 번 불렀으면.
     *   [requestFields]·[requestParts] 와 함께 쓰면 문서화할 때 실패한다
     */
    public fun requestFile(description: String) {
        require(description.isNotBlank()) {
            RestDocsErrorCode.INVALID_FILE_BODY.message("requestFile needs a non-blank description.")
        }

        require(requestFile == null) {
            RestDocsErrorCode.INVALID_FILE_BODY.message(
                "requestFile is declared more than once in this andDocument block.",
            )
        }

        requestFile = description
    }

    /** 하이퍼미디어 링크 (HAL·Atom 자동 감지). `links` 표가 된다. `"self" linkMeans "이 리소스"` */
    public fun links(links: List<RestDocsLink>) {
        this.links += links
    }

    /** [links] 의 가변 인자 버전. */
    public fun links(vararg links: RestDocsLink): Unit = links(links.toList())

    /**
     * Server-Sent Events 이벤트. 스트림을 파싱해 이벤트 목록 표와 이벤트별 `data` 필드 표를 만든다.
     * 끝나는 SSE 응답은 [io.github.kardx.mockmvc.performAsync] 로, 끝나지 않는 스트림은
     * [io.github.kardx.sse.performStream] 으로 받는다.
     * AsyncAPI 메타데이터(`sse-events.asyncapi.json`)도 남긴다. OpenAPI 에는 이벤트 정보가 들어가지 않는다.
     */
    public fun events(events: List<RestDocsEvent>) {
        this.events += events
    }

    /** [events] 의 가변 인자 버전. */
    public fun events(vararg events: RestDocsEvent): Unit = events(events.toList())

    /**
     * 위 항목으로 표현할 수 없는 Spring REST Docs 스니펫을 그대로 더한다. OpenAPI 에는 들어가지 않는다.
     * 생성 문서에는 [io.github.kardx.config.DocumentSettings.snippets] 규칙대로 include 된다.
     * `requestBody()`·`responseBody()` 처럼 기본 제외 종류는 그 설정에 넣어야 문서에 나온다.
     */
    public fun snippets(snippets: List<Snippet>) {
        this.snippets += snippets
    }

    /** [snippets] 의 가변 인자 버전. */
    public fun snippets(vararg snippets: Snippet): Unit = snippets(snippets.toList())

    /**
     * [deprecated] 를 true 로 하고 이유·대안을 붙인다. `api-summary` 스니펫에 `*Deprecated*: <note>` 로 나간다.
     * 빈 문자열이면 이유 없이 표시한다. `deprecated("GET /api/v2/users 를 쓰세요")`
     */
    public fun deprecated(note: String) {
        openApi.deprecate(note)
    }

    /**
     * 테스트가 실제로 만들지 않는 오류 응답(400, 401, 403, 404, 409 …)을 선언한다. `error-responses` 스니펫(상태·설명 표와
     * 응답별 필드 표·예시)과 OpenAPI 응답에 들어간다. 테스트마다 실제로 받은 응답만 문서에 나오는 한계를 보완한다.
     *
     * 선언일 뿐 검증하지 않는다. 테스트가 이 상태를 실제로 받는지, 실제 오류 본문이 여기 적은 필드와 같은지 확인하지 않는다.
     * 같은 operation 에서 테스트가 실제로 만든 상태가 있으면 OpenAPI 에서는 그 실제 응답이 이 선언보다 우선한다.
     * 블록에서 본문 필드·예시·Content-Type 을 적는다 ([ErrorResponseBuilder]). 블록이 없으면 상태와 설명만 남는다.
     *
     * ```kotlin
     * .andDocument {
     *     responseFields<ProductResponse>()
     *
     *     errorResponse(400, "잘못된 요청")
     *     errorResponse(409, "이미 등록된 상품 코드") {
     *         fields<ErrorResponse>()
     *         example("""{"code":"DUPLICATE","message":"이미 등록된 상품 코드입니다"}""")
     *     }
     * }
     * ```
     *
     * @param status 400..599 상태 코드
     * @param description 표와 OpenAPI 응답 `description` 에 들어갈 설명
     * @throws IllegalArgumentException [status] 가 400..599 밖이거나, [description] 이 비었거나, 같은 블록에서 같은 상태를
     *   두 번 선언했거나, 블록의 Content-Type·예시가 비었으면
     */
    public fun errorResponse(status: Int, description: String, block: ErrorResponseBuilder.() -> Unit = {}) {
        require(status in ERROR_STATUS_RANGE) {
            RestDocsErrorCode.INVALID_ERROR_RESPONSE.message(
                "errorResponse status must be between 400 and 599, but was $status.",
            )
        }

        require(description.isNotBlank()) {
            RestDocsErrorCode.INVALID_ERROR_RESPONSE.message("errorResponse($status) needs a non-blank description.")
        }

        require(errorResponses.none { it.status == status }) {
            RestDocsErrorCode.INVALID_ERROR_RESPONSE.message(
                "errorResponse($status) is declared more than once in this andDocument block.",
            )
        }

        errorResponses += ErrorResponseBuilder().apply(block)
            .build(status, description)
    }

    /**
     * [errorResponse] 의 [HttpStatusCode] 버전. [org.springframework.http.HttpStatus] 상수를 그대로 넘긴다.
     * 선언일 뿐 검증하지 않으며, 테스트가 실제로 만든 상태가 이 선언보다 우선한다.
     *
     * ```kotlin
     * .andDocument {
     *     errorResponse(HttpStatus.NOT_FOUND, "상품 없음")
     *     errorResponse(HttpStatus.CONFLICT, "이미 등록된 상품 코드") {
     *         fields("code" type STRING means "오류 코드")
     *         contentType = "application/problem+json"
     *     }
     * }
     * ```
     */
    public fun errorResponse(
        status: HttpStatusCode,
        description: String,
        block: ErrorResponseBuilder.() -> Unit = {},
    ): Unit = errorResponse(status.value(), description, block)

    /**
     * 예시 객체 하나로 오류 응답을 선언한다. 본문 필드는 [example] 의 런타임 클래스에서 추출하고(`responseFieldsOf` 와 같은
     * 규칙), 본문 예시는 [example] 을 직렬화한 JSON 이다 ([ErrorResponseBuilder.example]). 아래 블록과 같다.
     *
     * ```kotlin
     * errorResponse(409, "이미 등록된 상품 코드") {
     *     fields(responseFieldsOf(example::class))
     *     example(example)
     * }
     * ```
     *
     * 런타임 클래스만으로 구조를 알 수 없는 값은 필드 없이 예시만 남긴다: 컬렉션·배열·`Map`, 타입 파라미터가 있는 클래스
     * (`ApiResponse<ErrorBody>` 처럼 제네릭 래퍼), 문자열·숫자 같은 값. 이때 필드가 필요하면 블록 형태에서
     * `fields<ApiResponse<ErrorBody>>()` 로 타입을 적는다. 문자열은 JSON 으로 보고 그대로 예시가 된다.
     * nullable 프로퍼티는 optional 필드가 되므로 예시에서 null 이어도 검사에 걸리지 않는다.
     *
     * ```kotlin
     * .andDocument {
     *     errorResponse(409, "이미 등록된 상품 코드", ErrorResponse(code = "DUPLICATE", message = "중복"))
     * }
     * ```
     *
     * @param status 400..599 상태 코드
     * @param description 표와 OpenAPI 응답 `description` 에 들어갈 설명
     * @param example 본문 예시 객체
     * @throws IllegalArgumentException [errorResponse] 와 같은 경우
     */
    public fun errorResponse(status: Int, description: String, example: Any) {
        errorResponse(status, description) {
            fieldsFrom = example
            this.example(example)
        }
    }

    /**
     * [errorResponse] (예시 객체) 의 [HttpStatusCode] 버전.
     *
     * ```kotlin
     * .andDocument {
     *     errorResponse(HttpStatus.CONFLICT, "이미 등록된 상품 코드", ErrorResponse(code = "DUPLICATE", message = "중복"))
     * }
     * ```
     */
    public fun errorResponse(status: HttpStatusCode, description: String, example: Any): Unit =
        errorResponse(status.value(), description, example)

    /** OpenAPI(epages) 전용 설정. epages 가 없으면 무시된다. */
    public fun openApi(block: OpenApiBuilder.() -> Unit) {
        openApi.apply(block)
    }

    private companion object {
        val ERROR_STATUS_RANGE = 400..599
    }
}

/** [DocumentationBuilder.openApi] 블록. */
@RestDocsDsl
public class OpenApiBuilder internal constructor() {
    /**
     * 요청 본문 스키마 이름. OpenAPI `components.schemas` 에 이 이름으로 등록되고 요청이 `$ref` 로 가리킨다.
     * 없으면 타입에서 추출한 필드(`requestFields<T>()` 등)는 그 타입의 이름
     * ([io.github.kardx.config.OpenApiSettings.schemaNames], 기본 켜짐), 직접 적은 필드는 epages 가 경로와 해시로
     * 만든 이름이다. 클래스로 정하려면 [requestSchema] 함수를 쓴다.
     */
    public var requestSchema: String? = null

    /**
     * 응답 본문 스키마 이름. 없으면 4xx·5xx 응답은 [io.github.kardx.config.OpenApiSettings.errorSchema],
     * 그 밖에 타입에서 추출한 필드(`responseFields<T>()`, `responseFieldsOf<T>()` 등)는 그 타입의 이름
     * ([io.github.kardx.config.OpenApiSettings.schemaNames], 기본 켜짐. 래퍼를 뺀 이름은
     * [io.github.kardx.config.OpenApiSettings.schemaNamesWithoutWrapper]), 직접 적은 필드는 epages 가 경로와
     * 해시로 만든 이름이다. 클래스로 정하려면 [responseSchema] 함수를 쓴다.
     *
     * 같은 이름을 구조가 다른 응답에 주면 Gradle 플러그인이 필드를 합친 스키마 하나로 맞춘다 (같은 경로의 타입이 다르면 경고).
     * 플러그인 없이 쓰면 epages 가 그중 하나만 남기므로 구조가 다르면 다른 이름을 준다.
     */
    public var responseSchema: String? = null

    /** 요청 스키마 이름을 타입에서 정한다. `requestSchema<CreateTodoRequest>()` */
    public inline fun <reified T> requestSchema() {
        requestSchema = SchemaNames.of(typeOf<T>())
    }

    /** 요청 스키마 이름을 클래스에서 정한다. `requestSchema(CreateTodoRequest::class)` */
    public fun requestSchema(type: KClass<*>) {
        requestSchema = SchemaNames.of(type)
    }

    /** 응답 스키마 이름을 타입에서 정한다. `responseSchema<ApiResponse<TodoResponse>>()` → `ApiResponseTodoResponse` */
    public inline fun <reified T> responseSchema() {
        responseSchema = SchemaNames.of(typeOf<T>())
    }

    /** 응답 스키마 이름을 클래스에서 정한다. `responseSchema(ErrorResponse::class)` */
    public fun responseSchema(type: KClass<*>) {
        responseSchema = SchemaNames.of(type)
    }

    /**
     * OpenAPI 에 deprecated 로 표시한다. 블록의 [DocumentationBuilder.deprecated] 와 같은 값이라 `api-summary` 스니펫에도
     * 나간다. 정하지 않으면 핸들러의 `@Deprecated` 를 따른다 (읽은 값은 블록에서 정한 값이다).
     */
    public var deprecated: Boolean = false
        set(value) {
            field = value
            deprecationDeclared = true

            if (!value) deprecationNote = null
        }

    /** 테스트가 [deprecated] 를 정했는지. 정하지 않았으면 핸들러의 `@Deprecated` 를 따른다 */
    internal var deprecationDeclared: Boolean = false
        private set

    /** [deprecated] 의 이유·대안. [DocumentationBuilder.deprecated] 의 `note` */
    internal var deprecationNote: String? = null
        private set

    /** deprecated 로 표시하고 이유를 붙인다. 빈 이유는 넣지 않는다. */
    internal fun deprecate(note: String) {
        deprecated = true
        deprecationNote = note.takeIf { it.isNotBlank() }
    }

    /**
     * 이 API 의 OpenAPI operationId. 정하면 이름 규칙([io.github.kardx.config.OpenApiSettings.operationIdRule])과
     * 사용자 규칙([io.github.kardx.config.OpenApiSettings.operationIdResolver])보다 우선하고, Gradle 플러그인이
     * 글자를 바꾸거나 번호를 붙이지 않고 그대로 쓴다. 다른 operation 과 겹치면 Gradle 빌드가 경고한다.
     * 생성된 OpenAPI 의 `operationId` 와 Swagger UI 딥 링크·코드 생성기의 메서드 이름에 나타난다. 기본값 null (규칙으로 만든다).
     *
     * ```kotlin
     * .andDocument {
     *     openApi { operationId = "searchUsers" }
     * }
     * ```
     *
     * @throws IllegalArgumentException 빈 문자열이거나 공백 글자가 있으면
     */
    public var operationId: String? = null
        set(value) {
            require(value == null || isValidOperationId(value)) {
                RestDocsErrorCode.INVALID_OPERATION_ID.message(
                    "invalid operationId '$value'. An operationId must not be empty or contain whitespace.",
                )
            }

            field = value
        }

    /**
     * 이 API 의 OpenAPI operation `summary`. 정하지 않으면(null) 테스트 이름(`@DisplayName`)을 쓴다. 같은 operation 을
     * 여러 테스트(성공·실패 예시)가 문서화하면 테스트 이름이 대표 테스트에 따라 정해지므로, operation 자체를 가리키는 이름을
     * 여기에 정한다. Gradle 플러그인이 조각을 합친 뒤 넣고, `.http` 내보내기의 요청 제목도 이 값을 따른다.
     * REST Docs 스니펫(`api-summary`)과 예시 이름은 테스트 이름 그대로다.
     *
     * 같은 operation 의 테스트들이 서로 다른 값을 정하면 스니펫 디렉터리 이름순으로 처음 값을 쓰고 빌드가 경고한다.
     *
     * ```kotlin
     * .andDocument {
     *     openApi { summary = "결제 요청" }
     * }
     * ```
     */
    public var summary: String? = null

    /**
     * 이 API 의 OpenAPI operation `description`. 정하지 않으면(null) 블록의 [DocumentationBuilder.description] 을 쓴다.
     * OpenAPI 에만 다른 설명을 넣을 때 쓴다 (`api-summary` 스니펫은 [DocumentationBuilder.description] 그대로다).
     * 병합·경고 규칙은 [summary] 와 같다.
     */
    public var description: String? = null

    /**
     * true 이면 이 API 를 OpenAPI 스펙에서 뺀다 (`resource.json` 을 만들지 않는다). REST Docs 문서에는 그대로 들어간다.
     * 내부 API 나 예시·검증용 테스트에 쓴다.
     */
    public var excluded: Boolean = false

    internal val tags = mutableListOf<String>()

    /** 추가 태그. 테스트 클래스 `@DisplayName` 태그에 더해진다. */
    public fun tags(vararg tags: String) {
        this.tags += tags
    }

    internal var authentication: DeclaredAuthentication? = null

    /**
     * 요청에 `Authorization` 헤더가 없어도 이 API 를 OpenAPI 에서 Bearer(JWT) 인증이 필요한 API 로 표시한다.
     * spring-security-test 의 `with(jwt())`·`@WithMockUser` 처럼 헤더 없이 인증한 테스트에 쓴다.
     *
     * 헤더를 보냈을 때와 같은 보안 스키마 `bearerAuthJWT` 와 operation 의 `security` 가 생겨 Swagger UI 에 Authorize 버튼이 나온다.
     * OpenAPI 조각(`resource.json`)에만 형식을 맞춘 가짜 JWT 를 넣으므로 REST Docs 스니펫(`http-request` 등)과 요청 헤더 표는
     * 바뀌지 않는다. 요청에 다른 `Authorization` 이 있어도 OpenAPI 에서는 이 선언을 따르고,
     * [io.github.kardx.config.OpenApiSettings.securitySchemes] 가 false 여도 선언한 operation 에는 붙는다.
     *
     * ```kotlin
     * mockMvc.perform(get("/api/me").with(jwt()))
     *     .andDocument {
     *         openApi { bearerAuth() }
     *     }
     * ```
     */
    public fun bearerAuth() {
        authentication = DeclaredAuthentication.BEARER
    }

    /**
     * [bearerAuth] 의 HTTP Basic 판. 요청에 `Authorization` 헤더가 없어도 OpenAPI 보안 스키마 `basic` 이 필요한 API 로 표시한다.
     * `@WithMockUser`·`with(httpBasic(...))` 없이 인증한 테스트에 쓴다. REST Docs 스니펫은 바뀌지 않는다.
     */
    public fun basicAuth() {
        authentication = DeclaredAuthentication.BASIC
    }

    /** 선언한 OAuth 2.0·OpenID Connect 보안 요구사항. 스키마 이름(`oauth2`, `openIdConnect`) → 필요한 scope (선언 순서) */
    internal val securityRequirements = linkedMapOf<String, LinkedHashSet<String>>()

    /**
     * 이 API 를 OpenAPI 에서 OAuth 2.0(보안 스키마 `oauth2`)으로 인증하는 API 로 표시한다. [scopes] 는 이 API 에 필요한 scope 다.
     * 여러 번 부르면 scope 를 합친다. 흐름(token URL 등)과 scope 설명은 Gradle 플러그인의
     * `kardx { openApiSecurity { oauth2 { ... } } }` 에서 정한다.
     *
     * 선언하면 OpenAPI 조각은 요청의 `Authorization` 헤더로 보안 스키마를 만들지 않는다 ([bearerAuth]·[basicAuth] 를 함께
     * 선언하면 그것은 남는다). REST Docs 스니펫은 바뀌지 않는다.
     *
     * ```kotlin
     * .andDocument {
     *     openApi { oauth2("users:read") }
     * }
     * ```
     *
     * @throws IllegalArgumentException scope 가 비었거나 공백 글자가 있으면
     */
    public fun oauth2(vararg scopes: String) {
        declareSecurity(OAUTH2, scopes)
    }

    /**
     * [oauth2] 의 OpenID Connect 판. 보안 스키마 `openIdConnect` 로 인증하는 API 로 표시한다. discovery URL 은 Gradle 플러그인의
     * `kardx { openApiSecurity { openIdConnect("https://.../.well-known/openid-configuration") } }` 에서 정한다.
     *
     * @throws IllegalArgumentException scope 가 비었거나 공백 글자가 있으면
     */
    public fun openIdConnect(vararg scopes: String) {
        declareSecurity(OPEN_ID_CONNECT, scopes)
    }

    private fun declareSecurity(scheme: String, scopes: Array<out String>) {
        require(scopes.all { it.isNotEmpty() && it.none(Char::isWhitespace) }) {
            RestDocsErrorCode.INVALID_SCOPE.message(
                "invalid $scheme scope in ${scopes.toList()}. A scope must not be empty or contain whitespace.",
            )
        }

        securityRequirements.getOrPut(scheme) { linkedSetOf() } += scopes
    }

    private companion object {
        /** Gradle 플러그인이 만드는 보안 스키마 이름. 플러그인과 같은 값이다 */
        const val OAUTH2 = "oauth2"
        const val OPEN_ID_CONNECT = "openIdConnect"
    }
}

/** 파라미터 표의 종류. 빈 목록으로 부른 것도 표를 만들어 REST Docs 가 실제 요청과 맞춰 보게 한다. */
internal enum class ParameterSection { PATH, QUERY, FORM }
