package io.github.kardx.internal.openapi

import io.github.kardx.annotation.AsciiDescription
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.DeclaredErrorResponse
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.output.OperationTitles
import io.github.kardx.internal.output.SnippetNames
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.schema.JsonSchemaBuilder
import io.github.kardx.internal.schema.SchemaDialect
import io.github.kardx.internal.schema.SchemaField
import io.github.kardx.internal.sourcedocs.SourceDocs
import org.springframework.restdocs.cookies.CookieDescriptor
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.operation.OperationRequestPart
import org.springframework.restdocs.snippet.Snippet

/**
 * epages 의 OpenAPI 조각(`resource.json`)에 담기지 않는 정보를 옆의 [FILE_NAME] 에 남기는 스니펫.
 * Gradle 플러그인이 읽어 생성된 스펙에 넣는다. `.adoc` 이 아니므로 AsciiDoc 문서에는 들어가지 않는다.
 *
 * - `requestParts`: multipart 파트. 파일 파트(실제 요청에 파일 이름이 있음)는 `string`/`binary`, 본문 필드를 적은 파트는
 *   그 필드로 만든 스키마와 실제 Content-Type
 * - `requestCookies`·`responseCookies`: 쿠키 이름·설명·optional
 * - `requestBody`·`responseBody`: 필드가 없는 본문(`Long`, `List<String>` 같은 값 본문)의 스키마
 * - `requestFile`·`responseFile`: 파일 본문의 설명(`requestFile(...)`·`responseFile(...)`). `{"description": ...}`.
 *   플러그인이 본문 스키마(`string`/`binary`)의 `description` 으로 넣는다
 * - `tag`: 문서 클래스 제목 태그와 그 클래스의 `@AsciiDescription` (OpenAPI 최상위 `tags` 설명)
 * - `operationId`: operationId 후보 ([OperationIdCandidates]). 플러그인이 스펙 전체를 보고 최종 id 를 정한다
 * - `errorResponses`: 테스트가 받지 않고 선언만 한 오류 응답 (상태순). `status`·`description`·`contentType` 은 항상,
 *   `schema`(필드로 만든 스키마)·`example`(들여쓰기한 예시)은 선언했을 때만 있다
 * - `security`: `openApi { oauth2(...) / openIdConnect(...) }` 로 선언한 보안 요구사항.
 *   `[{"type": "oauth2" | "openIdConnect", "scopes": ["users:read"]}]`
 * - `apiVersion`: 핸들러에 선언한 API 버전 ([HandlerApiVersion]). `"1.1"`
 * - `requestBodyPolymorphism`·`responseBodyPolymorphism`: 본문의 다형 프로퍼티 ([BodyPolymorphism])
 * - `fieldDefaults`: 본문 필드의 기본값 ([fieldDefaults]). `{"requestBody": {"page.size": 20}}`
 * - `summary`·`description`: `openApi { summary / description }` 로 정한 operation 의 summary·description. 플러그인이 조각을
 *   합친 뒤 operation 에 넣는다
 * - `sourceSummary`·`sourceDescription`: 핸들러 메서드의 KDoc·Javadoc 첫 문장과 전체 설명 ([SourceDocs]). 플러그인이
 *   `summary`·`description` 이 없을 때 쓴다
 */
internal class OpenApiOperationExtras(private val resource: OpenApiResource) : Snippet {
    override fun document(operation: Operation) {
        val context = SnippetPaths.documentationContext(operation)
        val sourceDocs = resource.handler?.let {
            SourceDocs.method(
                RestDocsConfiguration.current.descriptions.sourceDocs,
                it.classNames,
                it.methodName,
                it.arity,
            )
        }

        val json = linkedMapOf(
            "requestParts" to parts(operation.request.parts),
            "requestCookies" to cookies(resource.requestCookies),
            "responseCookies" to cookies(resource.responseCookies),
            "requestBody" to resource.requestBodySchema,
            "responseBody" to resource.responseBodySchema,
            "requestFile" to resource.requestFile?.let { mapOf(DESCRIPTION to it) },
            "responseFile" to resource.responseFile?.let { mapOf(DESCRIPTION to it) },
            "tag" to context?.let { tag(it.testClass) },
            OperationIdCandidates.KEY to OperationIdCandidates.of(
                operation,
                resource.operationId,
                resource.handler,
                RestDocsConfiguration.current,
            ),
            "errorResponses" to resource.errorResponses.map(::errorResponse),
            "security" to resource.security.map { (type, scopes) -> linkedMapOf("type" to type, "scopes" to scopes) },
            "apiVersion" to resource.apiVersion,
            "requestBodyPolymorphism" to resource.requestPolymorphism,
            "responseBodyPolymorphism" to resource.responsePolymorphism,
            "fieldDefaults" to fieldDefaults(resource.requestFields, resource.responseFields),
            "summary" to resource.summary,
            DESCRIPTION to resource.openApiDescription,
            "sourceSummary" to sourceDocs?.summary,
            "sourceDescription" to sourceDocs?.description,
        ).filterValues { it != null && it != emptyList<Any>() }

        if (json.isNotEmpty()) {
            writeBesideResource(operation, FILE_NAME, json)
        }
    }

    /** 문서화한 파트와 본문 필드만 적은 파트. 순서는 문서화한 순서다. */
    private fun parts(actual: Collection<OperationRequestPart>): List<Map<String, Any?>> {
        val described = resource.requestParts.associateBy { it.name }
        val names = (described.keys + resource.requestPartFields.keys).distinct()

        return names.map { name ->
            val descriptor = described[name]
            val part = actual.firstOrNull { it.name == name }
            val fields = resource.requestPartFields[name].orEmpty()

            linkedMapOf(
                NAME to name,
                DESCRIPTION to descriptor?.description?.toString(),
                "optional" to (descriptor?.isOptional ?: false),
                "contentType" to part?.headers?.contentType?.toString(),
                "schema" to partSchema(part, fields),
            )
        }
    }

    private fun partSchema(part: OperationRequestPart?, fields: List<RestDocsField>) = when {
        fields.isNotEmpty() -> JsonSchemaBuilder.fromFields(fields.map(SchemaField::of), SchemaDialect.OPENAPI_30)
        !part?.submittedFileName.isNullOrBlank() -> mapOf("type" to "string", "format" to "binary")
        else -> mapOf("type" to "string")
    }

    private fun cookies(cookies: List<CookieDescriptor>): List<Map<String, Any?>> = cookies.map {
        linkedMapOf(
            NAME to it.name,
            DESCRIPTION to it.description?.toString(),
            "optional" to it.isOptional,
        )
    }

    /** 선언한 오류 응답 하나. 필드·예시가 없으면 그 키를 빼서 플러그인이 공통 오류 스키마를 쓰게 한다. */
    private fun errorResponse(response: DeclaredErrorResponse): Map<String, Any?> = buildMap {
        put("status", response.status)
        put(DESCRIPTION, response.description)
        put("contentType", response.contentType)

        if (response.fields.isNotEmpty()) {
            put("schema", JsonSchemaBuilder.fromFields(response.fields.map(SchemaField::of), SchemaDialect.OPENAPI_30))
        }

        response.example?.let { put("example", it) }
    }

    /** 태그([OperationTitles.tagOf])와 문서 클래스의 `@AsciiDescription` 설명. 설명이 없으면 null. */
    private fun tag(testClass: Class<*>): Map<String, String>? {
        val documentClass = SnippetNames.documentClass(testClass)
        val name = OperationTitles.tagOf(testClass)
        val description = documentClass.getAnnotation(AsciiDescription::class.java)
            ?.value
            ?.takeIf { it.isNotBlank() } ?: return null

        return mapOf(NAME to name, DESCRIPTION to description)
    }

    companion object {
        /** Gradle 플러그인의 `OpenApiResources.EXTRAS_FILE` 과 같다. */
        const val FILE_NAME: String = "openapi-extras.json"

        private const val NAME = "name"
        private const val DESCRIPTION = "description"
    }
}
