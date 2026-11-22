package io.github.kardx.internal.asyncapi
import io.github.kardx.config.MaskingSettings
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.dsl.RestDocsHeader
import io.github.kardx.internal.http.HeaderMasking
import io.github.kardx.internal.jackson.serializeJson
import io.github.kardx.internal.output.OperationTitles
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.output.writeOperationFile
import io.github.kardx.internal.schema.SchemaField
import org.springframework.restdocs.RestDocumentationContext

/**
 * 테스트 중 문서화한 메시지·이벤트 스트림을 [io.github.kardx.asyncapi.AsyncApiGenerator] 가 읽을 수 있게
 * 스니펫 디렉터리에 기록한다.
 * 파일 이름은 `*.asyncapi.json` 이다. `.adoc` 이 아니므로 AsciiDoc 문서 조립에는 영향이 없다.
 */
internal object AsyncApiMetadata {
    const val FILE_SUFFIX = ".asyncapi.json"

    fun write(context: RestDocumentationContext, name: String, record: AsyncApiRecord) {
        if (!RestDocsConfiguration.current.asyncApi.enabled) return

        val directory = SnippetPaths.snippetDirectory(
            context.outputDirectory,
            context.testClass,
            context.testMethodName,
        )

        val title = OperationTitles.of(context)
        val origin = AsyncApiOrigin(
            tag = title.tag,
            summary = title.summary,
            operation = directory.relativeTo(context.outputDirectory).invariantSeparatorsPath,
        )

        val json = AsyncApiRecordCodec.encode(AsyncApiEntry(origin, record))

        directory.resolve("$name$FILE_SUFFIX")
            .writeOperationFile(serializeJson(RestDocsConfiguration.objectMapper(), json))
    }

    fun fields(fields: List<RestDocsField>): List<SchemaField> = fields.map(SchemaField::of)

    fun headers(headers: List<RestDocsHeader>, actual: Map<String, String>): List<HeaderRecord> {
        val values = actual.mapKeys { it.key.lowercase() }

        return headers.map { header ->
            val name = header.descriptor.name

            HeaderRecord(
                name = name,
                description = header.descriptor.description?.toString(),
                optional = header.descriptor.isOptional,
                example = values[name.lowercase()]?.let { masked(name, it) },
            )
        }
    }

    /**
     * AsyncAPI 예시에 넣을 헤더 값. 설정의 마스킹 규칙에 더해, `Authorization`·`Cookie` 같은 민감 헤더는 설정과 상관없이
     * 가린다. AsyncAPI 문서와 Try it out 뷰어는 배포되는 산출물이라 실제 토큰이 들어가면 안 된다.
     */
    fun masked(name: String, value: String): String =
        if (HeaderMasking.isMasked(name) || name.lowercase() in SENSITIVE) {
            HeaderMasking.maskAlways(name, value)
        } else {
            value
        }

    /** 소문자로 바꾼 민감 헤더 이름 */
    private val SENSITIVE = MaskingSettings.SENSITIVE_HEADERS.map { it.lowercase() }
        .toSet()
}
