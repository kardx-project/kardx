package io.github.kardx.internal.openapi

import io.github.kardx.dsl.RelaxedSection
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.operation.FormParameters
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.operation.QueryParameters
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestDocumentation

/**
 * relaxed 인 파라미터 항목(경로·쿼리·폼)에, 실제 요청에는 있지만 문서화하지 않은 파라미터를 ignored 로 더한다.
 * epages 는 파라미터를 항상 엄격하게 검증하므로 이렇게 해야 relaxed 문서화가 epages 에서 실패하지 않는다.
 * ignored 파라미터는 epages 가 OpenAPI 출력에서 뺀다.
 */
internal object RelaxedParameters {
    fun withUndocumented(resource: OpenApiResource, operation: Operation): OpenApiResource {
        val sections = resource.relaxedSections

        if (PARAMETER_SECTIONS.none { it in sections }) return resource

        return resource.copy(
            pathParameters = completed(resource.pathParameters, RelaxedSection.PATH_PARAMETERS in sections) {
                pathVariables(operation)
            },
            queryParameters = completed(resource.queryParameters, RelaxedSection.QUERY_PARAMETERS in sections) {
                QueryParameters.from(operation.request).keys
            },
            formParameters = completed(resource.formParameters, RelaxedSection.FORM_PARAMETERS in sections) {
                FormParameters.from(operation.request).keys
            },
        )
    }

    private inline fun completed(
        documented: List<ParameterDescriptor>,
        relaxed: Boolean,
        actual: () -> Collection<String>,
    ): List<ParameterDescriptor> {
        if (!relaxed) return documented

        val names = documented.mapTo(mutableSetOf()) { it.name }
        val undocumented = actual().filterNot { it in names }
            .distinct()
            .map { RequestDocumentation.parameterWithName(it).ignored() }

        return documented + undocumented
    }

    private fun pathVariables(operation: Operation): List<String> {
        val template = operation.attributes[RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE] as? String

        return template?.let(OpenApiPathTemplate::variables)
            .orEmpty()
    }

    private val PARAMETER_SECTIONS = listOf(
        RelaxedSection.PATH_PARAMETERS,
        RelaxedSection.QUERY_PARAMETERS,
        RelaxedSection.FORM_PARAMETERS,
    )
}
