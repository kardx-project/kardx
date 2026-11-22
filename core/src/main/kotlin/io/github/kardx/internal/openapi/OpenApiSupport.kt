package io.github.kardx.internal.openapi

import com.epages.restdocs.apispec.ParameterDescriptorWithType
import com.epages.restdocs.apispec.ResourceDocumentation
import com.epages.restdocs.apispec.ResourceSnippetParametersBuilder
import com.epages.restdocs.apispec.Schema
import com.epages.restdocs.apispec.SimpleType
import io.github.kardx.dsl.DEFAULT_VALUE_ATTRIBUTE
import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.SCHEMA_ATTRIBUTE
import io.github.kardx.internal.output.OperationTitle
import io.github.kardx.internal.output.OperationTitles
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.reflect.loadClassOrNull
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.snippet.Snippet

/**
 * epages restdocs-api-spec 연동. epages 클래스는 [EpagesDocumenter] 안에서만 참조하므로
 * epages 가 클래스패스에 없으면 그 클래스는 로드되지 않는다.
 */
internal object OpenApiSupport {
    /** epages 가 클래스패스에 있는지. 설정(`openApi.enabled`)은 호출하는 쪽에서 본다. */
    val isAvailable: Boolean by lazy {
        loadClassOrNull(EPAGES_RESOURCE, OpenApiSupport::class.java.classLoader) != null
    }

    /** [resource] 로 epages 리소스 스니펫을 만든다. [isAvailable] 이 true 일 때만 부른다. */
    fun resourceSnippet(resource: OpenApiResource): Snippet = EpagesDocumenter.resourceSnippet(resource)

    /** epages 본체(restdocs-api-spec)의 클래스. MockMvc·WebTestClient 용 epages 모듈이 모두 이것을 가져온다 */
    private const val EPAGES_RESOURCE = "com.epages.restdocs.apispec.ResourceDocumentation"
}

private object EpagesDocumenter {
    /**
     * 태그·요약([OperationTitles])은 문서화할 때(operation 의 REST Docs 문맥으로) 정한다. 그래서 테스트가 만든 다른 스레드에서
     * `andDocument` 를 불러도 그 테스트의 요약이 들어간다.
     */
    fun resourceSnippet(resource: OpenApiResource): Snippet = Snippet { operation ->
        val title = SnippetPaths.documentationContext(operation)?.let(OperationTitles::of)

        resourceSnippet(RelaxedParameters.withUndocumented(resource, operation), title).document(operation)
    }

    private fun resourceSnippet(resource: OpenApiResource, title: OperationTitle?): Snippet {
        val parameters = ResourceSnippetParametersBuilder().description(resource.description)
            .pathParameters(*resource.pathParameters.map(::typed).toTypedArray())
            .requestHeaders(*resource.requestHeaders.toTypedArray())
            .queryParameters(*resource.queryParameters.map(::typed).toTypedArray())
            .formParameters(*resource.formParameters.map { typed(it, form = true) }.toTypedArray())
            .requestFields(*resource.requestFields.toTypedArray())
            .responseFields(*resource.responseFields.toTypedArray())
            .responseHeaders(*resource.responseHeaders.toTypedArray())
            .links(*resource.links.toTypedArray())
            .deprecated(resource.deprecated)

        title?.let { parameters.summary(it.summary) }
        (listOfNotNull(title?.tag) + resource.tags).forEach { parameters.tag(it) }
        resource.requestSchema?.let { parameters.requestSchema(Schema.schema(it)) }
        resource.responseSchema?.let { parameters.responseSchema(Schema.schema(it)) }

        // 필드·헤더·파라미터는 위에서 직접 넘기므로 epages 래퍼(MockMvcRestDocumentationWrapper)의 병합은 필요 없다.
        // 래퍼는 스니펫 목록에서 자기 리소스 스니펫을 찾는데, 보안 처리를 위해 감싼 스니펫은 찾지 못해 기본 리소스를
        // 하나 더 만들어 덮어쓴다. 그래서 호출하는 쪽은 REST Docs 기본 문서화에 이 스니펫을 바로 넘긴다.
        // epages 는 스니펫마다 JSON 매퍼를 새로 만든다. 직렬화기 캐시를 이어 쓰도록 매퍼 하나를 같이 쓰게 한다
        val resourceSnippet = SharedResourceMapper.share(ResourceDocumentation.resource(parameters.build()))
        val snippet = OpenApiPathTemplate.wrap(resourceSnippet)

        val authentication = resource.authentication

        // OAuth 2.0·OpenID Connect 를 선언했으면 실제 헤더의 Bearer 대신 그 스키마만 남도록 헤더를 뺀다 (플러그인이 extras 로 넣는다)
        return when {
            authentication != null -> OpenApiSecurity.declared(snippet, authentication)
            resource.security.isNotEmpty() -> OpenApiSecurity.withoutSecurity(snippet)
            resource.securitySchemes -> OpenApiSecurity.wrap(snippet)
            else -> OpenApiSecurity.withoutSecurity(snippet)
        }
    }

    /**
     * epages 파라미터. 스키마 조각([SCHEMA_ATTRIBUTE])이 정수·숫자·불리언이면 그 타입과 기본값을 넣어 Gradle 플러그인 없이도
     * `integer` 등이 나오게 한다. epages 는 Enum 값을 그 타입으로 바꾸므로 값 목록이 있는 파라미터는 문자열로 둔다.
     * 폼 파라미터는 epages 가 본문 필드로 바꾸는데 필드 타입에는 `integer` 가 없으므로 정수도 `number` 로 넘긴다.
     */
    private fun typed(descriptor: ParameterDescriptor, form: Boolean = false): ParameterDescriptorWithType {
        val typed = ParameterDescriptorWithType.fromParameterDescriptor(descriptor)
        val schema = descriptor.attributes[SCHEMA_ATTRIBUTE] as? Map<*, *>
        val type = SIMPLE_TYPES[schema?.get("type")]
            ?.takeIf { descriptor.attributes[ENUM_VALUES_ATTRIBUTE] == null }
            ?.let { if (form && it == SimpleType.INTEGER) SimpleType.NUMBER else it }
            ?: SimpleType.STRING
        val default = descriptor.attributes[DEFAULT_VALUE_ATTRIBUTE]?.toString()
            ?.let { defaultValue(it, type) }

        typed.type(type)
        default?.let { typed.defaultValue(it) }

        return typed
    }

    /** epages 가 타입별로 기대하는 기본값 객체. 바꿀 수 없으면 null (기본값을 넘기지 않는다). */
    private fun defaultValue(value: String, type: SimpleType): Any? = when (type) {
        SimpleType.INTEGER -> value.trim().toLongOrNull()
        SimpleType.NUMBER -> value.trim().toBigDecimalOrNull()
        SimpleType.BOOLEAN -> value.trim().toBooleanStrictOrNull()
        SimpleType.STRING -> value
    }

    private val SIMPLE_TYPES = mapOf(
        "integer" to SimpleType.INTEGER,
        "number" to SimpleType.NUMBER,
        "boolean" to SimpleType.BOOLEAN,
    )
}
