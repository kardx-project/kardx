package io.github.kardx.internal.introspection

import io.github.kardx.dsl.ARRAY
import io.github.kardx.dsl.BOOLEAN
import io.github.kardx.dsl.DocsFieldType
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.STRING
import io.github.kardx.internal.i18n.DocsText
import kotlin.reflect.KType

/**
 * 추출한 프로퍼티 한 줄의 스키마 정보.
 *
 * @property schema 타입과 제약에서 만든 JSON Schema 조각 ([TypeSchemas], [ConstraintSchemas]). 없으면 null
 * @property constraints 표의 Constraints 열 내용. 원소 제약(`List<@NotBlank String>`)은 `Items: ` 를 앞에 붙인다
 * @property note 키워드로 옮기지 못한 제약 설명 (OpenAPI·AsyncAPI 설명 뒤 `Constraints: ...`)
 * @property required 기본 그룹의 `@NotNull`·`@NotBlank`·`@NotEmpty` 가 있는지
 */
internal class PropertySchema(
    val schema: Map<String, Any?>?,
    val constraints: String?,
    val note: String?,
    val required: Boolean,
)

/** 프로퍼티의 타입·제약 어노테이션·원소 제약 어노테이션을 [PropertySchema] 로 모은다. */
internal object PropertySchemas {
    fun of(meta: PropertyMeta, type: KType, docsType: DocsFieldType): PropertySchema {
        val schema = TypeSchemas.of(type, meta.annotations) ?: linkedMapOf()
        val own = ConstraintResolver.resolve(meta.annotations)
        val ownSchema = ConstraintSchemas.of(own, schema[TypeSchemas.TYPE] as? String ?: jsonTypeOf(docsType))

        schema.putAll(ownSchema.keywords)

        val element = ConstraintResolver.resolve(elementAnnotations(meta))
        val items = itemsOf(schema)
        val elementSchema = ConstraintSchemas.of(element, items?.get(TypeSchemas.TYPE) as? String)
        val elementPrefix = "${DocsText.ITEMS.text()}: "
        // 원소가 객체면 키워드를 둘 곳이 없으므로 설명으로 남긴다
        val elementNote = if (items == null) {
            ConstraintResolver.describe(element)
        } else {
            items.putAll(elementSchema.keywords)
            elementSchema.note
        }

        return PropertySchema(
            schema = schema.takeIf { it.isNotEmpty() },
            constraints = listOfNotNull(
                ConstraintResolver.describe(own),
                ConstraintResolver.describe(element)?.let { elementPrefix + it },
            ).joinToString(SEPARATOR)
                .ifEmpty { null },
            note = listOfNotNull(ownSchema.note, elementNote?.let { elementPrefix + it }).joinToString(SEPARATOR)
                .ifEmpty { null },
            required = ownSchema.required,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun itemsOf(schema: Map<String, Any?>): MutableMap<String, Any?>? =
        schema[TypeSchemas.ITEMS] as? MutableMap<String, Any?>

    /** 컬렉션·배열 원소, 맵 값에 붙은 제약 어노테이션. Kotlin 은 타입 인자, Java 는 [PropertyMeta.elementAnnotations]. */
    private fun elementAnnotations(meta: PropertyMeta): List<Annotation> = meta.elementAnnotations.ifEmpty {
        meta.type.arguments.lastOrNull()
            ?.type
            ?.annotations
            .orEmpty()
    }

    private fun jsonTypeOf(docsType: DocsFieldType): String? = when (docsType) {
        STRING -> JsonTypes.STRING
        NUMBER -> JsonTypes.NUMBER
        BOOLEAN -> JsonTypes.BOOLEAN
        ARRAY -> JsonTypes.ARRAY
        OBJECT -> JsonTypes.OBJECT
        else -> null
    }

    private const val SEPARATOR = "\n\n"
}
