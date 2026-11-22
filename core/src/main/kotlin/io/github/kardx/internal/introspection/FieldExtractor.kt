package io.github.kardx.internal.introspection

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.ARRAY
import io.github.kardx.dsl.DocsFieldType
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.VARIES
import io.github.kardx.internal.reflect.enumJsonValues
import kotlin.reflect.KClass
import kotlin.reflect.KType

/** 추출 결과 한 줄. REST Docs 디스크립터로 변환되기 전의 중립 모델이다. */
internal data class ExtractedField(
    val path: String,
    val type: DocsFieldType,
    val description: String,
    val optional: Boolean,
    val constraints: String?,
    /** true 이면 하위 필드를 문서화하지 않는 subsection (`Map`, 순환 참조, 최대 깊이) */
    val subsection: Boolean = false,
    /** Enum 타입이면 JSON 값 목록. OpenAPI `enum` (Swagger UI 드롭다운) 으로 나간다. 배열이면 원소의 값 목록이다 */
    val enumValues: List<String>? = null,
    /** true 이면 검증에서는 문서화한 것으로 보고 표·스키마에는 넣지 않는다 (배열의 `null` 원소) */
    val ignored: Boolean = false,
    /** 타입·제약에서 만든 JSON Schema 조각 ([PropertySchemas]). OpenAPI·AsyncAPI 에만 나간다 */
    val schema: Map<String, Any?>? = null,
    /** 스키마 키워드로 옮기지 못한 제약 설명 */
    val constraintNote: String? = null,
    /** optional 이지만 `@NotNull` 등으로 값이 꼭 있어야 한다 (표 Required 열·스키마 required) */
    val requiredByConstraint: Boolean = false,
    /** 부모 때문에 optional 이지만 부모가 있으면 꼭 있다 (스키마 required. 표에는 나타내지 않는다) */
    val requiredInParent: Boolean = false,
    /** optional 본문 필드의 값이 null 일 수 있는지 (스키마 nullable). 필수 필드와 파라미터는 null */
    val nullable: Boolean? = null,
    /** 배열 본문 필드의 원소가 null 일 수 있다 (`List<Box?>`, 스키마 `items.nullable`) */
    val nullableItems: Boolean = false,
    /** `@Deprecated` 프로퍼티면 이유(없으면 빈 문자열), 아니면 null ([deprecationOf]) */
    val deprecation: String? = null,
    /** 값이 마스킹되어 나가는 본문 필드면 규칙 설명(없으면 빈 문자열), 아니면 null ([maskingOf]). 파라미터는 null */
    val masking: String? = null,
    /** 요청 추출에서 읽은 Kotlin 생성자 기본값의 JSON 표현 ([KotlinDefaults]). 표의 `(Default: ...)`·OpenAPI `default` */
    val defaultValue: String? = null,
)

/**
 * 타입을 재귀적으로 따라가며 문서 필드를 만든다.
 *
 * BODY 경로 규칙 (Spring REST Docs JSON path):
 * - 객체: 부모 `a` (OBJECT) + 하위 `a.b`
 * - 배열: 부모 `items` (ARRAY) + 원소가 객체이면 `items[].x`, 중첩 배열은 `matrix[][].x`
 * - `Map`, 순환 참조, 최대 깊이 도달: subsection 한 줄 (하위 필드는 검증하지 않음).
 *   배열 원소가 그런 타입이면 배열 자체가 subsection 이 된다
 * - 구조를 알 수 없는 값(`JsonNode`): VARIES subsection 한 줄
 * - `@JsonUnwrapped` 프로퍼티: 그 줄 대신 하위 필드를 부모 수준에 펼친다
 * - nullable 부모의 하위 필드, nullable 원소(`List<Box?>`)의 하위 필드는 모두 optional.
 *   nullable 객체 원소는 `items[]` 를 ignored 로 더해 `null` 원소도 문서화한 것으로 본다
 * - 인터페이스·추상 클래스 타입([isOpenType])은 실제 하위 타입의 프로퍼티가 더 있을 수 있으므로 subsection 으로 두고,
 *   선언 타입의 프로퍼티는 하위 필드로 함께 문서화한다. 그런 원소의 배열도 subsection 이다
 * - 요청(본문·파라미터)에서는 기본값이 있는 생성자 파라미터와 생성자 밖 `var` 도 optional 이고,
 *   읽기 전용 프로퍼티(`access = READ_ONLY`, 생성자 밖 `val`)는 빠진다. 응답에서는 `WRITE_ONLY` 가 빠진다
 * - [io.github.kardx.config.ExtractionSettings.extraction.arrayElementFieldsOptional] 이면 배열 원소의 하위 필드도 optional
 *
 * PARAMETER 모드는 객체만 `a.b` 로 펼치고, 부모 줄과 배열 원소는 만들지 않는다.
 *
 * 최상위 타입이 객체가 아니면 다음처럼 문서화한다.
 * - 컬렉션·배열 (`List<User>`): 원소 필드 `[].id`. [prefix][extract] 가 있으면 `data` (ARRAY) + `data[].id`
 * - `Map`: [prefix][extract] 가 있을 때만 subsection 한 줄. 없으면 문서화할 고정 필드가 없다
 * - 값 (`String`, `Int` …): [prefix][extract] 가 있을 때만 한 줄
 */
internal class FieldExtractor(
    private val mode: Mode,
    /** true 이면 요청(본문·파라미터) 추출이다. 기본값이 있는 프로퍼티는 optional, 읽기 전용 프로퍼티는 제외한다 */
    private val request: Boolean = false,
) {
    enum class Mode { BODY, PARAMETER }

    private val maxDepth = RestDocsConfiguration.current.extraction.maxDepth
    private val arrayElementFieldsOptional = RestDocsConfiguration.current.extraction.arrayElementFieldsOptional
    private val globalNaming = PropertyNaming.global()

    fun extract(type: KType, prefix: String): List<ExtractedField> {
        val path = prefix.trim()
        val unwrapped = resolve(type)

        fun line(docsType: DocsFieldType, subsection: Boolean = false, enumValues: List<String>? = null) =
            listOfNotNull(
                path.takeIf { it.isNotEmpty() }
                    ?.let {
                        ExtractedField(
                            it,
                            docsType,
                            DEFAULT_DESCRIPTION,
                            unwrapped.nullable,
                            null,
                            subsection,
                            enumValues,
                            schema = TypeSchemas.of(unwrapped.type),
                        )
                    },
            )

        return when (val shape = shapeOf(unwrapped.type)) {
            // 경로가 있으면 열린 타입(인터페이스 등)은 subsection 줄을 둔다. 경로 없는 루트는 subsection 으로 둘 수 없다
            is Shape.Nested -> {
                val open = mode == Mode.BODY && isOpenType(shape.kClass)
                val parent = if (open) line(OBJECT, subsection = true) else emptyList()

                parent + fieldsOf(unwrapped.type, path, optional = false, stack = emptyList())
            }

            is Shape.Leaf -> line(valueDocsType(shape, unwrapped.type), enumValues = enumJsonValuesOf(unwrapped.type))

            Shape.MapLike -> if (mode == Mode.BODY) {
                line(OBJECT, subsection = true)
            } else {
                emptyList()
            }

            Shape.Opaque -> line(VARIES, subsection = mode == Mode.BODY)

            is Shape.Array -> when {
                mode == Mode.PARAMETER -> line(ARRAY, enumValues = elementEnumValues(shape))

                stopsAtElement(shape.element, emptyList()) ->
                    line(ARRAY, subsection = true)
                        .ifEmpty {
                            listOf(ExtractedField("[]", VARIES, DEFAULT_DESCRIPTION, false, null, subsection = true))
                        }

                else -> line(ARRAY, hasOpenElement(shape.element), elementEnumValues(shape)) +
                    elements(shape.element, "$path[]", arrayElementFieldsOptional, DEFAULT_DESCRIPTION, emptyList())
            }
        }
    }

    private fun fieldsOf(
        type: KType,
        prefix: String,
        optional: Boolean,
        stack: List<KClass<*>>,
        decorate: (String) -> String = { it },
    ): List<ExtractedField> {
        val k = type.classifier as? KClass<*> ?: return emptyList()
        val bindings = bindingsOf(type)
        val nextStack = stack + k

        return PropertyIntrospector.properties(type, k)
            .filterNot { if (request) it.readOnly else it.writeOnly }
            .map { withKotlinDefault(k, it) }
            .flatMap { meta ->
                // 파라미터 바인딩(@ModelAttribute)은 Jackson 을 거치지 않으므로 선언 이름 그대로다
                val name = when {
                    mode == Mode.PARAMETER -> meta.declaredName
                    meta.nameFixed -> meta.name
                    else -> globalNaming?.invoke(meta.name) ?: meta.name
                }

                property(
                    meta,
                    meta.type.substitute(bindings),
                    prefix,
                    decorate(name),
                    optional || meta.forceOptional,
                    nextStack,
                )
            }
    }

    private fun property(
        meta: PropertyMeta,
        type: KType,
        prefix: String,
        name: String,
        inheritedOptional: Boolean,
        stack: List<KClass<*>>,
    ): List<ExtractedField> {
        val path = joinPath(prefix, name)
        val unwrapped = resolve(type)
        val optional = inheritedOptional || ownOptional(meta, unwrapped)
        val shape = shapeOf(unwrapped.type)
        val lines = PropertyLines(meta, type, path, unwrapped, shape, inheritedOptional)

        fun line(docsType: DocsFieldType, subsection: Boolean = false) = lines.line(docsType, subsection)

        return when (shape) {
            // @JsonFormat(shape = NUMBER/STRING) 은 값의 JSON 타입을 바꾼다 (Instant → 숫자, BigDecimal → 문자열)
            is Shape.Leaf -> listOf(line(lines.valueType ?: shape.type))

            Shape.MapLike -> listOf(line(OBJECT, subsection = mode == Mode.BODY))

            Shape.Opaque -> listOf(line(VARIES, subsection = mode == Mode.BODY))

            is Shape.Array -> when {
                mode == Mode.PARAMETER -> listOf(line(ARRAY))

                stopsAtElement(shape.element, stack) -> listOf(line(ARRAY, subsection = true))

                else -> listOf(line(ARRAY, subsection = hasOpenElement(shape.element))) +
                    elements(shape.element, "$path[]", optional || arrayElementFieldsOptional, lines.description, stack)
            }

            is Shape.Nested -> when {
                isTooDeep(shape.kClass, stack) -> listOf(line(OBJECT, subsection = mode == Mode.BODY))

                // 파라미터 바인딩(@ModelAttribute)은 Jackson 어노테이션을 따르지 않으므로 본문에서만 펼친다
                meta.unwrapped != null && mode == Mode.BODY ->
                    fieldsOf(unwrapped.type, prefix, optional, stack, meta.unwrapped::decorate)

                mode == Mode.PARAMETER ->
                    fieldsOf(unwrapped.type, path, optional, stack)
                        .ifEmpty { listOf(line(OBJECT)) }

                else -> listOf(line(OBJECT, subsection = isOpenType(shape.kClass))) +
                    fieldsOf(unwrapped.type, path, optional, stack)
            }
        }
    }

    /**
     * 요청 추출이면 기본값이 있는 생성자 파라미터의 값([KotlinDefaults])을 붙인다. 기본값이 있는 프로퍼티가 있는 클래스만
     * 객체를 만들어 읽는다(클래스마다 한 번).
     */
    private fun withKotlinDefault(k: KClass<*>, meta: PropertyMeta): PropertyMeta {
        // 가리는 값(@Masked 등)의 기본값은 문서에 내지 않는다
        if (!request || !meta.hasDefault || maskingOf(meta.annotations) != null) {
            return meta
        }

        return KotlinDefaults.of(k)[meta.declaredName]?.let { meta.copy(defaultValue = it) } ?: meta
    }

    /** 프로퍼티 자신 때문에 optional 인지: nullable 이거나, 요청에서 기본값이 있다. */
    private fun ownOptional(meta: PropertyMeta, unwrapped: Unwrapped): Boolean =
        unwrapped.nullable || (request && meta.hasDefault)

    /**
     * 프로퍼티 한 줄의 공통 값(설명, Enum 값, 스키마 정보). 모양에 따라 타입·subsection 만 바꿔 [line] 으로 만든다.
     * 부모가 null 일 수 있어 optional 인 하위 필드는 부모가 있을 때의 required 를 표에 따로 나타내지 않는다.
     */
    private inner class PropertyLines(
        meta: PropertyMeta,
        type: KType,
        private val path: String,
        unwrapped: Unwrapped,
        shape: Shape,
        inheritedOptional: Boolean,
    ) {
        private val ownOptional = ownOptional(meta, unwrapped)
        private val optional = inheritedOptional || ownOptional
        private val enumClass = enumClassOf(unwrapped.type) ?: (shape as? Shape.Array)?.let { elementEnumClass(it) }
        val description = describe(meta, enumClass)
        private val formatType = JacksonAnnotations.jsonFormatType(meta.annotations)

        /** 값 타입의 REST Docs 타입. `@JsonFormat(shape)` 가 JSON 타입을 바꾼다 (Instant → 숫자, BigDecimal → 문자열) */
        val valueType = (shape as? Shape.Leaf)?.let { formatType ?: valueDocsType(it, unwrapped.type) }
        private val schema = PropertySchemas.of(meta, type, valueType ?: containerDocsType(shape))

        // shape = NUMBER 인 Enum 은 순번(숫자)으로 나가므로 상수 이름 목록을 붙이지 않는다
        private val enumValues = (meta.fixedValues ?: enumClass?.let(::enumJsonValues)).takeIf { formatType != NUMBER }
        private val requiredByConstraint = schema.required && ownOptional && !inheritedOptional
        private val presentWithParent = !ownOptional || schema.required

        // sealed 하위 타입에만 있는 프로퍼티와 arrayElementFieldsOptional 로 풀어 둔 원소 필드는 부모 안에서도 optional 이다
        private val requiredInParent = mode == Mode.BODY && inheritedOptional && presentWithParent &&
            !requiredByConstraint && !meta.forceOptional && !arrayElementFieldsOptional
        private val nullable = (unwrapped.nullable && !schema.required).takeIf { mode == Mode.BODY && optional }
        private val nullableItems = mode == Mode.BODY &&
            (shape as? Shape.Array)?.element?.let { unwrap(it).nullable } == true
        private val deprecation = deprecationOf(meta.annotations)
        private val masking = maskingOf(meta.annotations).takeIf { mode == Mode.BODY }
        private val defaultValue = meta.defaultValue

        fun line(docsType: DocsFieldType, subsection: Boolean) = ExtractedField(
            path,
            docsType,
            description,
            optional,
            schema.constraints,
            subsection,
            enumValues,
            schema = schema.schema,
            constraintNote = schema.note,
            requiredByConstraint = requiredByConstraint,
            requiredInParent = requiredInParent,
            nullable = nullable,
            nullableItems = nullableItems,
            deprecation = deprecation,
            masking = masking,
            defaultValue = defaultValue,
        )
    }

    private fun elements(
        element: KType?,
        path: String,
        optional: Boolean,
        description: String,
        stack: List<KClass<*>>,
    ): List<ExtractedField> {
        val unwrapped = element?.let(::unwrap) ?: return emptyList()
        val elementOptional = optional || unwrapped.nullable

        // 중첩 배열 안에서 멈추는 경우. 경로가 여러 값을 가리키므로 타입은 VARIES 로 둔다.
        fun subsection() = listOf(ExtractedField(path, VARIES, description, elementOptional, null, subsection = true))

        return when (val shape = shapeOf(unwrapped.type)) {
            is Shape.Leaf -> emptyList()

            Shape.MapLike, Shape.Opaque -> subsection()

            // 원소가 다시 배열인 타입이 끝없이 이어질 수 있다 (`class Grid : ArrayList<List<Grid>>()`)
            is Shape.Array -> if (arrayDepth(path) >= maxDepth) {
                subsection()
            } else {
                elements(shape.element, "$path[]", elementOptional, description, stack)
            }

            is Shape.Nested -> if (isTooDeep(shape.kClass, stack)) {
                subsection()
            } else {
                fieldsOf(unwrapped.type, path, elementOptional, stack) + nullElement(path, unwrapped.nullable)
            }
        }
    }

    /**
     * nullable 객체 원소(`List<Box?>`)의 `null` 은 하위 필드가 없어 REST Docs 가 "문서화하지 않은 값" 으로 본다.
     * 원소 경로(`items[]`)를 ignored 로 더해 검증만 통과시킨다. 표와 스키마에는 나가지 않는다 (epages 는 ignored 필드를 버린다).
     * REST Docs 는 하위 필드를 지운 뒤 스칼라만 남은 배열을 지우므로 원소의 하위 필드 뒤에 둔다.
     */
    private fun nullElement(path: String, nullable: Boolean): List<ExtractedField> = if (nullable) {
        listOf(ExtractedField(path, VARIES, DEFAULT_DESCRIPTION, true, null, ignored = true))
    } else {
        emptyList()
    }

    /** 배열(중첩 배열 포함, [maxDepth] 겹까지)의 원소가 [isOpenType] 객체인지. */
    private fun hasOpenElement(element: KType?, depth: Int = 0): Boolean {
        val shape = element?.let { shapeOf(unwrap(it).type) }

        return when (shape) {
            is Shape.Nested -> isOpenType(shape.kClass)
            is Shape.Array -> depth < maxDepth && hasOpenElement(shape.element, depth + 1)
            else -> false
        }
    }

    /** 파라미터 모드에서는 Spring Data `Pageable`·`Sort` 를 실제로 받는 쿼리 파라미터 모양으로 바꾼다. 모두 생략할 수 있다. */
    private fun resolve(type: KType): Unwrapped {
        val parameters = if (mode == Mode.PARAMETER) SpringDataTypes.parameterType(type) else null

        return parameters?.let { Unwrapped(it, nullable = true) } ?: unwrap(type)
    }

    private fun isTooDeep(k: KClass<*>, stack: List<KClass<*>>) = k in stack || stack.size >= maxDepth

    /** 배열 원소가 Map·불투명 타입이거나 순환·깊이 제한에 걸리면 배열 자체를 subsection 으로 문서화한다. */
    private fun stopsAtElement(element: KType?, stack: List<KClass<*>>): Boolean {
        val shape = element?.let { shapeOf(unwrap(it).type) } ?: return false

        return shape == Shape.MapLike || shape == Shape.Opaque ||
            (shape is Shape.Nested && isTooDeep(shape.kClass, stack))
    }

    private fun elementEnumClass(shape: Shape.Array): Class<*>? = shape.element?.let { enumClassOf(unwrap(it).type) }

    private fun elementEnumValues(shape: Shape.Array): List<String>? = elementEnumClass(shape)?.let(::enumJsonValues)

    private fun describe(meta: PropertyMeta, enumClass: Class<*>?): String {
        val base = RestDocsConfiguration.current.descriptions.resolver.resolve(meta.context)
            ?: meta.description?.resolve()
            ?: DEFAULT_DESCRIPTION

        return if (enumClass == null) {
            base
        } else {
            base + EnumDescriber.describe(enumClass)
        }
    }

    companion object {
        const val DEFAULT_DESCRIPTION = "No description"

        private const val ARRAY_SEGMENT = "[]"

        /** 경로가 몇 겹의 배열 안에 있는지 (`a[][]` → 2). */
        private fun arrayDepth(path: String) = path.split(ARRAY_SEGMENT).size - 1

        /** JSON path 조각을 잇는다. `.` 이나 괄호가 들어간 이름은 `['a.b']` 로 감싼다. */
        fun joinPath(prefix: String, name: String): String {
            val segment = if (name.any { it == '.' || it == '[' || it == ']' }) {
                "['$name']"
            } else {
                name
            }

            return when {
                prefix.isEmpty() -> segment
                segment.startsWith("[") -> prefix + segment
                else -> "$prefix.$segment"
            }
        }
    }
}
