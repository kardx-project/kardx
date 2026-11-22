package io.github.kardx.internal.openapi

import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.introspection.FieldExtractor
import io.github.kardx.internal.introspection.FieldSources
import io.github.kardx.internal.introspection.PolymorphicSite
import io.github.kardx.internal.introspection.PolymorphicSites
import io.github.kardx.internal.introspection.TypeDiscriminator
import io.github.kardx.internal.introspection.toRestDocsField
import io.github.kardx.internal.provenance.FieldSource
import io.github.kardx.internal.schema.JsonSchemaBuilder
import io.github.kardx.internal.schema.SchemaDialect
import io.github.kardx.internal.schema.SchemaField
import io.github.kardx.openapi.SchemaNames
import kotlin.reflect.KClass
import kotlin.reflect.full.starProjectedType

/**
 * 본문의 다형 프로퍼티(sealed 타입, `@JsonTypeInfo`·`@JsonSubTypes`)를 Gradle 플러그인이 `oneOf` 로 바꿀 수 있게 남기는 정보.
 * [OpenApiOperationExtras] 가 `requestBodyPolymorphism`·`responseBodyPolymorphism` 으로 쓴다. 항목 하나는
 *
 * ```
 * { "path": "payment", "baseName": "Payment",
 *   "discriminator": { "property": "type", "mapping": { "CARD": "CardPayment" } },
 *   "variants": [ { "name": "CardPayment", "schema": { "type": "object", "properties": { ... } } } ] }
 * ```
 *
 * - `path`: REST Docs 필드 경로. 본문 자체면 `""`, 배열 원소면 `payments[]`. 경로를 붙여 추출했으면 그 경로부터다
 *   (`requestFieldsOf<T>("data")`·`wrappedResponseFields<T>()` 의 `data.method`, 데이터 자체가 다형이면 `data`)
 * - `discriminator`: 판별자를 나타낼 수 없으면 null. 값은 하위 타입 스키마 이름(`variants[].name`)이다
 * - `variants[].schema`: 하위 타입 필드로 만든 OpenAPI 3.0 스키마. 중첩 객체도 모두 펼쳐 넣는다. 판별자 프로퍼티를
 *   Jackson 이 더하면(`include = PROPERTY`) 그 프로퍼티(`enum` 은 그 하위 타입의 값)를 맨 앞에 넣고 required 로 둔다
 *
 * 펼친 필드 표와 스니펫·필드 검증은 그대로다. 문서화한 필드에 그 경로가 없으면(목록에서 뺐으면) 남기지 않는다.
 * 여러 목록을 합친 본문도 목록마다 추출한 타입과 경로를 알므로 각각 찾는다. 직접 쓴 필드(`"x" type OBJECT`)는 타입을 몰라 보지 않는다.
 */
internal object BodyPolymorphism {
    private const val PROPERTIES = "properties"
    private const val REQUIRED = "required"

    /**
     * 본문 필드 [fields] 의 다형 프로퍼티 정보. 필드를 추출한 타입마다([FieldSources]) 다형 프로퍼티를 찾아 추출할 때 붙인
     * 경로를 앞에 붙인다(`requestFieldsOf<T>("data")` 의 `method` → `data.method`). 같은 경로는 처음 것만 남긴다.
     * 추출한 필드가 없으면 빈 목록.
     */
    fun of(fields: List<RestDocsField>, request: Boolean): List<Map<String, Any?>> {
        val paths = fields.map { it.descriptor.path }

        return FieldSources.of(fields)
            .flatMap { source -> sitesOf(source, request) }
            .distinctBy { (path, _) -> path }
            .filter { (path, _) -> isDocumented(path, paths) }
            .map { (path, site) -> entry(path, site, request) }
    }

    private fun sitesOf(source: FieldSource, request: Boolean): List<Pair<String, PolymorphicSite>> =
        PolymorphicSites.find(source.type, request)
            .map { site -> joinPath(source.prefix, site.path) to site }

    /** 추출할 때 붙인 경로와 그 타입 안의 경로를 잇는다. 배열 원소(`[]`)는 점 없이 붙인다. */
    private fun joinPath(prefix: String, path: String): String = when {
        prefix.isEmpty() -> path
        path.isEmpty() -> prefix
        path.startsWith("[") -> prefix + path
        else -> "$prefix.$path"
    }

    /** 본문 자체이거나, 그 경로나 하위 경로를 문서화했는지. 배열 원소(`payments[]`)는 배열 필드(`payments`)도 본다. */
    private fun isDocumented(path: String, paths: List<String>): Boolean = path.isEmpty() ||
        paths.any { it == path || it.startsWith("$path.") || it.startsWith("$path[") || it == path.removeSuffix("[]") }

    private fun entry(path: String, site: PolymorphicSite, request: Boolean): Map<String, Any?> {
        val type = site.type

        return linkedMapOf(
            "path" to path,
            "baseName" to SchemaNames.of(type.base),
            "discriminator" to type.discriminator?.let { discriminator(it, type.variants) },
            "variants" to type.variants.map { variant ->
                linkedMapOf(
                    "name" to SchemaNames.of(variant),
                    "schema" to variantSchema(variant, type.discriminator, request),
                )
            },
        )
    }

    private fun discriminator(discriminator: TypeDiscriminator, variants: List<KClass<*>>): Map<String, Any?> =
        linkedMapOf(
            "property" to discriminator.property,
            "mapping" to variants.mapNotNull { variant ->
                discriminator.names[variant]?.let { it to SchemaNames.of(variant) }
            }.toMap(),
        )

    private fun variantSchema(
        variant: KClass<*>,
        discriminator: TypeDiscriminator?,
        request: Boolean,
    ): Map<String, Any?> {
        val fields = FieldExtractor(FieldExtractor.Mode.BODY, request).extract(variant.starProjectedType, "")
            .map { SchemaField.of(it.toRestDocsField()) }
        val schema = JsonSchemaBuilder.fromFields(fields, SchemaDialect.OPENAPI_30)
        val added = discriminator?.takeUnless { it.existing } ?: return schema
        val value = added.names[variant] ?: return schema

        return withDiscriminator(schema, added.property, value)
    }

    /** Jackson 이 더하는 판별자 프로퍼티를 맨 앞에 넣고 required 로 둔다. */
    private fun withDiscriminator(schema: Map<String, Any?>, property: String, value: String): Map<String, Any?> {
        val properties = linkedMapOf<String, Any?>(property to linkedMapOf("type" to "string", "enum" to listOf(value)))
        val required = listOf(property) + (schema[REQUIRED] as? List<*>).orEmpty()

        (schema[PROPERTIES] as? Map<*, *>)?.forEach { (name, value) -> properties.putIfAbsent(name.toString(), value) }

        return LinkedHashMap(schema).apply {
            put("type", "object")
            put(PROPERTIES, properties)
            put(REQUIRED, required.distinct())
        }
    }
}
