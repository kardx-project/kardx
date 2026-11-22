package io.github.kardx.internal.openapi

import io.github.kardx.dsl.DeclaredAuthentication
import io.github.kardx.dsl.DeclaredErrorResponse
import io.github.kardx.dsl.RelaxedSection
import io.github.kardx.dsl.RestDocsField
import org.springframework.restdocs.cookies.CookieDescriptor
import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.hypermedia.LinkDescriptor
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestPartDescriptor

/**
 * OpenAPI 리소스(`resource.json`) 한 건의 입력. 문서화 진입점(MockMvc 등)이 자기 입력을 이 값으로 바꿔 넘긴다.
 *
 * 필드 목록은 epages 에 넘긴다. epages 는 필드·링크를 항상 엄격하게 검증하므로 그 항목이 relaxed 라면 비워서 넘긴다.
 * epages 가 담지 못하는 항목(파트, 쿠키, 필드 없는 본문의 스키마, operationId 후보)은 [OpenApiOperationExtras] 가 옆 파일에 남긴다.
 */
internal data class OpenApiResource(
    val description: String?,
    val pathParameters: List<ParameterDescriptor>,
    val requestHeaders: List<HeaderDescriptor>,
    val queryParameters: List<ParameterDescriptor>,
    val formParameters: List<ParameterDescriptor>,
    val requestFields: List<FieldDescriptor>,
    val responseFields: List<FieldDescriptor>,
    val responseHeaders: List<HeaderDescriptor>,
    val links: List<LinkDescriptor>,
    val deprecated: Boolean,
    val tags: List<String>,
    val requestSchema: String?,
    val responseSchema: String?,
    /** false 이면 `Authorization` 을 빼서 보안 스키마를 만들지 않는다 */
    val securitySchemes: Boolean,
    val requestParts: List<RequestPartDescriptor> = emptyList(),
    val requestPartFields: Map<String, List<RestDocsField>> = emptyMap(),
    val requestCookies: List<CookieDescriptor> = emptyList(),
    val responseCookies: List<CookieDescriptor> = emptyList(),
    /** 필드가 없는 요청 본문(값 하나인 JSON, 값 배열)의 스키마. 타입에서 추출했을 때만 있다 */
    val requestBodySchema: Map<String, Any?>? = null,
    /** 필드가 없는 응답 본문의 스키마 */
    val responseBodySchema: Map<String, Any?>? = null,
    /** 테스트에서 정한 operationId. 없으면 규칙으로 만든다 ([OperationIdCandidates]) */
    val operationId: String? = null,
    /** 요청을 처리한 핸들러. operationId 기본 규칙에 쓴다. 알 수 없으면 null */
    val handler: DocumentedHandler? = null,
    /** 헤더 없이 선언한 인증 방식. 있으면 [securitySchemes] 와 실제 `Authorization` 보다 우선한다 */
    val authentication: DeclaredAuthentication? = null,
    /** 테스트가 받지 않고 선언만 한 오류 응답. [OpenApiOperationExtras] 가 `errorResponses` 로 남긴다 */
    val errorResponses: List<DeclaredErrorResponse> = emptyList(),
    /** relaxed 로 검증하는 항목. 파라미터 항목이면 문서화하지 않은 실제 파라미터를 ignored 로 더한다 ([RelaxedParameters]) */
    val relaxedSections: Set<RelaxedSection> = emptySet(),
    /** 파일 요청 본문의 설명. [OpenApiOperationExtras] 가 `requestFile` 로 남긴다 */
    val requestFile: String? = null,
    /** 파일 응답 본문의 설명. [OpenApiOperationExtras] 가 `responseFile` 로 남긴다 */
    val responseFile: String? = null,
    /**
     * 선언한 OAuth 2.0·OpenID Connect 보안 요구사항 (스키마 이름 → scope). [OpenApiOperationExtras] 가 `security` 로 남긴다.
     * 있으면 실제 `Authorization` 헤더로 보안 스키마를 만들지 않는다
     */
    val security: Map<String, List<String>> = emptyMap(),
    /** 핸들러에 선언한 API 버전 ([HandlerApiVersion]). [OpenApiOperationExtras] 가 `apiVersion` 으로 남긴다 */
    val apiVersion: String? = null,
    /** 테스트가 정한 operation summary (`openApi { summary }`). [OpenApiOperationExtras] 가 `summary` 로 남긴다 */
    val summary: String? = null,
    /** 테스트가 정한 operation description (`openApi { description }`). [OpenApiOperationExtras] 가 `description` 으로 남긴다 */
    val openApiDescription: String? = null,
    /** 요청 본문의 다형 프로퍼티 ([BodyPolymorphism]). [OpenApiOperationExtras] 가 `requestBodyPolymorphism` 으로 남긴다 */
    val requestPolymorphism: List<Map<String, Any?>> = emptyList(),
    /** 응답 본문의 다형 프로퍼티. [OpenApiOperationExtras] 가 `responseBodyPolymorphism` 으로 남긴다 */
    val responsePolymorphism: List<Map<String, Any?>> = emptyList(),
)
