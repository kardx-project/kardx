package io.github.kardx.internal.documentation

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.dsl.DeclaredAuthentication
import io.github.kardx.dsl.DeclaredErrorResponse
import io.github.kardx.dsl.ParameterSection
import io.github.kardx.dsl.RelaxedSection
import io.github.kardx.dsl.RestDocsCookie
import io.github.kardx.dsl.RestDocsEvent
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.RestDocsHeader
import io.github.kardx.dsl.RestDocsLink
import io.github.kardx.dsl.RestDocsParam
import io.github.kardx.dsl.RestDocsPart
import io.github.kardx.internal.diagnostics.diagnosed
import io.github.kardx.internal.introspection.TypeSchemas
import io.github.kardx.internal.openapi.BodyPolymorphism
import io.github.kardx.internal.openapi.DocumentedHandler
import io.github.kardx.internal.openapi.OpenApiDescriptors
import io.github.kardx.internal.openapi.OpenApiResource
import io.github.kardx.internal.openapi.SchemaOrigin
import io.github.kardx.internal.sse.EventStreamSnippet
import io.github.kardx.openapi.SchemaNameInput
import io.github.kardx.openapi.SchemaNames
import org.springframework.restdocs.cookies.CookieDocumentation
import org.springframework.restdocs.headers.HeaderDocumentation
import org.springframework.restdocs.hypermedia.HypermediaDocumentation
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.request.RequestDocumentation
import org.springframework.restdocs.snippet.Snippet
import kotlin.reflect.KType

/** OpenAPI(epages) 옵션. [io.github.kardx.dsl.OpenApiBuilder] 가 만든다. */
internal data class OpenApiOptions(
    val requestSchema: String? = null,
    val responseSchema: String? = null,
    val deprecated: Boolean = false,
    val excluded: Boolean = false,
    val tags: List<String> = emptyList(),
    /** 테스트에서 정한 operationId (`openApi { operationId }`) */
    val operationId: String? = null,
    /** 헤더 없이 선언한 인증 방식 (`openApi { bearerAuth() }`) */
    val authentication: DeclaredAuthentication? = null,
    /** 스키마 이름의 출처. Gradle 플러그인이 이름 충돌을 풀 때 쓴다 ([withSchemaNames]) */
    val requestOrigin: SchemaOrigin? = null,
    val responseOrigin: SchemaOrigin? = null,
    /** [deprecated] 의 이유·대안. `api-summary` 스니펫에 나간다 */
    val deprecationNote: String? = null,
    /** 테스트가 [deprecated] 를 정했는지. 정하지 않았으면 핸들러의 `@Deprecated` 를 따른다 ([withHandlerDefaults]) */
    val deprecationDeclared: Boolean = false,
    /** 선언한 OAuth 2.0·OpenID Connect 보안 요구사항 (`openApi { oauth2(...) }`). 스키마 이름 → scope */
    val security: Map<String, List<String>> = emptyMap(),
    /** OpenAPI operation summary (`openApi { summary }`). 없으면 테스트 이름 */
    val summary: String? = null,
    /** OpenAPI operation description (`openApi { description }`). 없으면 블록의 description */
    val description: String? = null,
)

/** `andDocument` 입력을 모은 값 객체. 표준 스니펫과 OpenAPI 리소스가 같은 입력을 쓴다. */
internal data class DocumentationSpec(
    val description: String?,
    val pathParameters: List<RestDocsParam>,
    val requestHeaders: List<RestDocsHeader>,
    val queryParameters: List<RestDocsParam>,
    val formParameters: List<RestDocsParam>,
    val requestFields: List<RestDocsField>,
    val responseFields: List<RestDocsField>,
    val responseHeaders: List<RestDocsHeader> = emptyList(),
    val requestCookies: List<RestDocsCookie> = emptyList(),
    val responseCookies: List<RestDocsCookie> = emptyList(),
    val requestParts: List<RestDocsPart> = emptyList(),
    val requestPartFields: Map<String, List<RestDocsField>> = emptyMap(),
    val links: List<RestDocsLink> = emptyList(),
    val events: List<RestDocsEvent> = emptyList(),
    /** relaxed 모드로 검증하는 항목. `relaxed = true` 면 전부다 */
    val relaxedSections: Set<RelaxedSection> = emptySet(),
    val openApi: OpenApiOptions = OpenApiOptions(),
    val extraSnippets: List<Snippet> = emptyList(),
    /** 타입에서 추출한 요청·응답 본문의 타입. 스키마 이름 기본값에 쓴다 */
    val requestSchemaType: KType? = null,
    val responseSchemaType: KType? = null,
    /** 공통 응답 래퍼로 감싼 응답이면 래퍼를 뺀 데이터 타입. 래퍼 없는 스키마 이름에 쓴다 */
    val responseDataType: KType? = null,
    /** 블록에서 부른 파라미터 표. 빈 목록이어도 표를 만든다 (`parametersOf<T>()` 가 비면 요청의 파라미터가 미문서화로 실패) */
    val parameterSections: Set<ParameterSection> = emptySet(),
    /** 테스트가 실제로 받지 않고 선언만 한 오류 응답. 상태순이고 JSON 예시는 들여쓰기했다 */
    val errorResponses: List<DeclaredErrorResponse> = emptyList(),
    /** 파일 요청 본문의 설명 (`requestFile`) */
    val requestFile: String? = null,
    /** 파일 응답 본문의 설명 (`responseFile`) */
    val responseFile: String? = null,
    /** 핸들러 메서드 KDoc·Javadoc 의 전체 설명 ([withHandlerDefaults]). [description] 이 없을 때 `api-summary` 에 쓴다 */
    val sourceDescription: String? = null,
) {
    /** 블록에서 지정한 항목의 스니펫. 검증 실패는 원인을 알려 주는 [io.github.kardx.dsl.DocumentationMismatchException] 이 된다. */
    fun standardSnippets(): List<Snippet> =
        listOf(ApiSummarySnippet(description ?: sourceDescription, openApi.deprecated, openApi.deprecationNote)) +
            parameterSnippets() +
            requestSnippets() +
            responseSnippets() +
            errorResponseSnippets() +
            fileBodySnippets()

    private fun parameterSnippets(): List<Snippet> = buildList {
        pathParameters.ifDocumented(ParameterSection.PATH) {
            add(
                strictOrRelaxed(
                    RelaxedSection.PATH_PARAMETERS,
                    it.map(RestDocsParam::descriptor),
                    RequestDocumentation::pathParameters,
                    RequestDocumentation::relaxedPathParameters,
                ).diagnosed("path parameters", REQUEST),
            )
        }

        queryParameters.ifDocumented(ParameterSection.QUERY) {
            add(
                strictOrRelaxed(
                    RelaxedSection.QUERY_PARAMETERS,
                    it.map(RestDocsParam::descriptor),
                    RequestDocumentation::queryParameters,
                    RequestDocumentation::relaxedQueryParameters,
                ).diagnosed("query parameters", REQUEST),
            )
        }

        formParameters.ifDocumented(ParameterSection.FORM) {
            add(
                strictOrRelaxed(
                    RelaxedSection.FORM_PARAMETERS,
                    it.map(RestDocsParam::descriptor),
                    RequestDocumentation::formParameters,
                    RequestDocumentation::relaxedFormParameters,
                ).diagnosed("form parameters", REQUEST),
            )
        }
    }

    private fun requestSnippets(): List<Snippet> = buildList {
        requestHeaders.ifNotEmpty {
            add(
                HeaderDocumentation.requestHeaders(it.map(RestDocsHeader::descriptor))
                    .diagnosed("request headers", REQUEST),
            )
        }

        requestCookies.ifNotEmpty {
            add(
                strictOrRelaxed(
                    RelaxedSection.REQUEST_COOKIES,
                    it.map(RestDocsCookie::descriptor),
                    CookieDocumentation::requestCookies,
                    CookieDocumentation::relaxedRequestCookies,
                ).diagnosed("request cookies", REQUEST),
            )
        }

        requestParts.ifNotEmpty {
            add(
                strictOrRelaxed(
                    RelaxedSection.REQUEST_PARTS,
                    it.map(RestDocsPart::descriptor),
                    RequestDocumentation::requestParts,
                    RequestDocumentation::relaxedRequestParts,
                ).diagnosed("request parts", REQUEST),
            )
        }

        addAll(requestPartFieldSnippets())

        requestFields.ifNotEmpty { fields ->
            val snippet = strictOrRelaxed(
                RelaxedSection.REQUEST_FIELDS,
                fields.map(RestDocsField::descriptor),
                PayloadDocumentation::requestFields,
                PayloadDocumentation::relaxedRequestFields,
            )

            add(snippet.diagnosed("request fields", REQUEST) { it.request.content })
        }
    }

    private fun requestPartFieldSnippets(): List<Snippet> = buildList {
        requestPartFields.forEach { (part, fields) ->
            val snippet = strictOrRelaxed(
                RelaxedSection.REQUEST_PART_FIELDS,
                fields.map(RestDocsField::descriptor),
                { PayloadDocumentation.requestPartFields(part, it) },
                { PayloadDocumentation.relaxedRequestPartFields(part, it) },
            )

            add(
                snippet.diagnosed("request part fields", "request part '$part'") { operation ->
                    operation.request.parts.firstOrNull { it.name == part }?.content
                },
            )
        }
    }

    private fun responseSnippets(): List<Snippet> = buildList {
        responseHeaders.ifNotEmpty {
            add(
                HeaderDocumentation.responseHeaders(it.map(RestDocsHeader::descriptor))
                    .diagnosed("response headers", RESPONSE),
            )
        }

        responseCookies.ifNotEmpty {
            add(
                strictOrRelaxed(
                    RelaxedSection.RESPONSE_COOKIES,
                    it.map(RestDocsCookie::descriptor),
                    CookieDocumentation::responseCookies,
                    CookieDocumentation::relaxedResponseCookies,
                ).diagnosed("response cookies", RESPONSE),
            )
        }

        responseFields.ifNotEmpty { fields ->
            val snippet = strictOrRelaxed(
                RelaxedSection.RESPONSE_FIELDS,
                fields.map(RestDocsField::descriptor),
                PayloadDocumentation::responseFields,
                PayloadDocumentation::relaxedResponseFields,
            )

            add(snippet.diagnosed("response fields", RESPONSE) { it.response.content })
        }

        links.ifNotEmpty {
            add(
                strictOrRelaxed(
                    RelaxedSection.LINKS,
                    it.map(RestDocsLink::descriptor),
                    HypermediaDocumentation::links,
                    HypermediaDocumentation::relaxedLinks,
                ),
            )
        }

        events.ifNotEmpty {
            val relaxedEvents = RelaxedSection.EVENTS in relaxedSections

            add(EventStreamSnippet(it, relaxedEvents, requestHeaders).diagnosed("SSE events", "stream"))
        }
    }

    /** 선언한 오류 응답이 있을 때만 `error-responses` 스니펫. */
    private fun errorResponseSnippets(): List<Snippet> =
        if (errorResponses.isEmpty()) emptyList() else listOf(ErrorResponsesSnippet(errorResponses))

    /**
     * 파일 본문 표(`request-file`·`response-file`)와 바이너리 본문 블록의 언어 정리. 표는 본문이 바이너리거나 설명을 적었을 때만
     * 생긴다 ([FileBodySnippet]).
     */
    private fun fileBodySnippets(): List<Snippet> = listOf(
        FileBodySnippet.request(requestFile),
        FileBodySnippet.response(responseFile),
        BinaryBodyBlocks,
    )

    /** [section] 이 relaxed 면 문서화하지 않은 항목을 허용하는 스니펫([relaxed]), 아니면 엄격한 스니펫([strict]). */
    private fun <D> strictOrRelaxed(
        section: RelaxedSection,
        descriptors: List<D>,
        strict: (List<D>) -> Snippet,
        relaxed: (List<D>) -> Snippet,
    ): Snippet = if (section in relaxedSections) relaxed(descriptors) else strict(descriptors)

    /**
     * OpenAPI 리소스 입력. epages 는 relaxed 모드가 없으므로 요청·응답 필드가 relaxed 이면 그 필드를 넘기지 않는다.
     * [handler] 는 요청을 처리한 핸들러로, operationId 기본 규칙에 쓴다 (알 수 없으면 null). [apiVersion] 은 그 핸들러에
     * 선언한 API 버전이다.
     * Enum 필드는 epages 용 복사본으로 바꾸고([OpenApiDescriptors]), 필드 없이 타입만 아는 본문은 스키마를 따로 넘긴다.
     */
    fun openApiResource(
        securitySchemes: Boolean,
        handler: DocumentedHandler? = null,
        apiVersion: String? = null,
    ): OpenApiResource {
        val sentRequestFields = sent(requestFields, RelaxedSection.REQUEST_FIELDS)
        val sentResponseFields = sent(responseFields, RelaxedSection.RESPONSE_FIELDS)

        return OpenApiResource(
            description = description,
            pathParameters = pathParameters.map(RestDocsParam::descriptor),
            requestHeaders = requestHeaders.map(RestDocsHeader::descriptor),
            queryParameters = queryParameters.map(RestDocsParam::descriptor),
            formParameters = formParameters.map(RestDocsParam::descriptor),
            requestFields = sentRequestFields.map { OpenApiDescriptors.field(it.descriptor) },
            responseFields = sentResponseFields.map { OpenApiDescriptors.field(it.descriptor) },
            responseHeaders = responseHeaders.map(RestDocsHeader::descriptor),
            links = sent(links, RelaxedSection.LINKS).map(RestDocsLink::descriptor),
            deprecated = openApi.deprecated,
            tags = openApi.tags,
            requestSchema = openApi.requestSchema,
            responseSchema = openApi.responseSchema,
            securitySchemes = securitySchemes,
            requestParts = requestParts.map(RestDocsPart::descriptor),
            requestPartFields = requestPartFields,
            requestCookies = requestCookies.map(RestDocsCookie::descriptor),
            responseCookies = responseCookies.map(RestDocsCookie::descriptor),
            requestBodySchema = requestSchemaType?.takeIf { requestFields.isEmpty() }?.let { TypeSchemas.of(it) },
            responseBodySchema = responseSchemaType?.takeIf { responseFields.isEmpty() }?.let { TypeSchemas.of(it) },
            operationId = openApi.operationId,
            handler = handler,
            authentication = openApi.authentication,
            errorResponses = errorResponses,
            relaxedSections = relaxedSections,
            requestFile = requestFile,
            responseFile = responseFile,
            security = openApi.security,
            apiVersion = apiVersion,
            summary = openApi.summary,
            openApiDescription = openApi.description,
            requestPolymorphism = BodyPolymorphism.of(sentRequestFields, request = true),
            responsePolymorphism = BodyPolymorphism.of(sentResponseFields, request = false),
        )
    }

    /** epages 에 넘기는 항목. epages 는 relaxed 모드가 없으므로 [section] 이 relaxed 면 넘기지 않는다. */
    private fun <T> sent(items: List<T>, section: RelaxedSection): List<T> =
        if (section in relaxedSections) emptyList() else items

    private inline fun <T> List<T>.ifNotEmpty(block: (List<T>) -> Unit) {
        if (isNotEmpty()) block(this)
    }

    private inline fun List<RestDocsParam>.ifDocumented(
        section: ParameterSection,
        block: (List<RestDocsParam>) -> Unit,
    ) {
        if (isNotEmpty() || section in parameterSections) block(this)
    }
}

/**
 * OpenAPI 스키마 이름을 정한다. 먼저 정해진 것을 쓴다.
 * 1. 테스트의 `openApi { requestSchema / responseSchema }`
 * 2. 오류 응답(4xx·5xx)이면 [io.github.kardx.config.OpenApiSettings.errorSchema]
 * 3. [io.github.kardx.config.OpenApiSettings.schemaNames] 가 켜져 있으면 추출한 타입의 이름.
 *    epages 에 넘기는 필드가 없으면(값 하나인 본문, relaxed) 빈 객체 스키마에 타입 이름이 붙지 않도록 정하지 않는다.
 *    [io.github.kardx.config.OpenApiSettings.schemaNamesWithoutWrapper] 면 공통 응답 래퍼를 뺀 데이터 타입의 이름
 *
 * 이름의 출처([SchemaOrigin])는 OpenAPI 조각 옆에 남겨 Gradle 플러그인이 같은 이름·다른 구조를 풀게 한다.
 */
internal fun DocumentationSpec.withSchemaNames(status: Int): DocumentationSpec {
    val settings = RestDocsConfiguration.current
    val response = givenSchema(openApi.responseSchema, responseSchemaType)
        ?: givenSchema(settings.openApi.errorSchema?.takeIf { status >= ERROR_STATUS }, responseSchemaType)
        ?: automaticSchema(
            settings,
            responseSchemaType,
            responseFields,
            RelaxedSection.RESPONSE_FIELDS,
            responseDataType,
        )
    val request = givenSchema(openApi.requestSchema, requestSchemaType)
        ?: automaticSchema(settings, requestSchemaType, requestFields, RelaxedSection.REQUEST_FIELDS)

    return copy(
        openApi = openApi.copy(
            requestSchema = request?.name,
            responseSchema = response?.name,
            requestOrigin = request,
            responseOrigin = response,
        ),
    )
}

/**
 * 직접 정한 이름(테스트·설정·이름 규칙). 이름은 바꾸지 않으므로 [SchemaOrigin.automatic] 은 false 다.
 * 본문 타입을 알면 [SchemaOrigin.type] 에 남겨 Gradle 플러그인이 그 클래스의 KDoc 을 스키마 설명으로 쓰게 한다.
 */
private fun givenSchema(name: String?, type: KType?): SchemaOrigin? =
    name?.let { SchemaOrigin(it, automatic = false, type = type?.toString(), candidates = listOf(it)) }

private fun DocumentationSpec.automaticSchema(
    settings: RestDocsSettings,
    type: KType?,
    fields: List<RestDocsField>,
    section: RelaxedSection,
    dataType: KType? = null,
): SchemaOrigin? {
    val sendsFields = fields.isNotEmpty() && section !in relaxedSections

    if (type == null || !settings.openApi.schemaNames || !sendsFields) {
        return null
    }

    val nameType = dataType?.takeIf { settings.openApi.schemaNamesWithoutWrapper && namesData(it) } ?: type
    val resolver = settings.openApi.schemaNameResolver

    // 사용자가 정한 이름 규칙(openApi.schemaNameResolver)의 결과는 바꾸지 않는다.
    // 래퍼를 뗀 이름이 다른 스키마와 겹치면 Gradle 플러그인이 그다음 후보(래퍼를 붙인 이름, 그 한정 이름)로 나눈다
    if (resolver != null) {
        return givenSchema(resolver.resolve(SchemaNameInput(nameType)), type)
    }

    val name = SchemaNames.of(nameType)
    val candidates = (listOf(name) + SchemaNames.candidates(type)).distinct()

    return SchemaOrigin(name, automatic = true, type.toString(), candidates)
}

/** 래퍼를 떼도 이름이 되는 데이터 타입인지. `Unit`·`Any`·`Nothing` 이면 래퍼 이름을 그대로 둔다 (`ApiResponseUnit`). */
private fun namesData(type: KType): Boolean = type.classifier !in setOf(Unit::class, Any::class, Nothing::class)

/**
 * 끝나지 않는 스트림([io.github.kardx.sse.performStream])은 OpenAPI 에서 뺀다. Swagger UI 는 응답이 끝나야 결과를 보여 주므로 계속 로딩만 돈다.
 * 이런 API 는 AsyncAPI 와 REST Docs 문서에 남는다. [io.github.kardx.config.OpenApiSettings.includeStreams] 로 바꾼다.
 */
internal fun DocumentationSpec.withStreamExclusion(stream: Boolean): DocumentationSpec =
    if (stream && !RestDocsConfiguration.current.openApi.includeStreams) {
        copy(openApi = openApi.copy(excluded = true))
    } else {
        this
    }

private const val ERROR_STATUS = 400
private const val REQUEST = "request"
private const val RESPONSE = "response"
