package io.github.kardx.internal.introspection

import io.github.kardx.internal.i18n.DocsText
import io.github.kardx.internal.i18n.LocalizedText
import io.github.kardx.internal.introspection.JacksonAnnotations.JSON_SUB_TYPES
import io.github.kardx.internal.introspection.JacksonAnnotations.JSON_TYPE_INFO
import io.github.kardx.internal.introspection.JacksonAnnotations.JSON_TYPE_NAME
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

/**
 * `@JsonTypeInfo` 가 붙은 sealed 타입이 JSON 에 넣는 판별자.
 *
 * - `include = PROPERTY`(기본값): 판별자 필드(`"type": "CARD"`)를 더한다
 * - `EXISTING_PROPERTY`: 이미 있는 프로퍼티에 하위 타입 이름 목록을 붙인다 ([existingProperty])
 * - `WRAPPER_OBJECT`: 하위 타입 이름이 키인 객체로 감싼다 (`{"car": {"doors": 4}}`, [wrapperProperties])
 * - `WRAPPER_ARRAY`(`["car", {...}]`)는 경로로 표현할 수 없어 다루지 않는다
 */
internal object SealedTypeDiscriminator {
    private val DEFAULT_PROPERTY =
        mapOf("CLASS" to "@class", "MINIMAL_CLASS" to "@c", "NAME" to "@type", "SIMPLE_NAME" to "@type")
    private const val SIMPLE_NAME = "SIMPLE_NAME"
    private const val INCLUDE = "include"
    private const val USE = "use"
    private val NAMED = setOf("NAME", SIMPLE_NAME)
    private const val PROPERTY = "PROPERTY"
    private const val EXISTING_PROPERTY = "EXISTING_PROPERTY"
    private const val WRAPPER_OBJECT = "WRAPPER_OBJECT"
    private const val WRAPPER_ARRAY = "WRAPPER_ARRAY"
    private const val CLASS = "CLASS"

    /** 판별자 필드. `@JsonTypeInfo` 가 없거나 표현할 수 없는 형태면 null. */
    fun property(sealed: KClass<*>, leaves: List<KClass<*>>): PropertyMeta? {
        val typeInfo = JacksonAnnotations.findOnHierarchy(sealed.java, JSON_TYPE_INFO) ?: return null
        val use = JacksonAnnotations.enumName(typeInfo, USE)
        val include = JacksonAnnotations.enumName(typeInfo, INCLUDE)
        val name = (JacksonAnnotations.value(typeInfo, "property") as? String)?.takeIf { it.isNotEmpty() }
            ?: use?.let(DEFAULT_PROPERTY::get)

        if (name == null || include !in setOf(null, PROPERTY)) {
            return null
        }

        return PropertyMeta(
            name,
            typeOf<String>(),
            emptyList(),
            null,
            nameFixed = true,
            fixedValues = if (use in NAMED) {
                typeNames(sealed, leaves, simple = use == SIMPLE_NAME)
            } else {
                null
            },
            description = LocalizedText(DocsText.TYPE_DISCRIMINATOR),
        )
    }

    /**
     * `WRAPPER_OBJECT` 면 하위 타입마다 `"<타입 이름>": { 하위 타입의 필드 }` 프로퍼티. 한 번에 하나만 오므로 모두 optional 이다.
     * 다른 include 면 null.
     */
    fun wrapperProperties(sealed: KClass<*>, leaves: List<KClass<*>>): List<PropertyMeta>? {
        val typeInfo = JacksonAnnotations.findOnHierarchy(sealed.java, JSON_TYPE_INFO) ?: return null

        if (JacksonAnnotations.enumName(typeInfo, INCLUDE) != WRAPPER_OBJECT) {
            return null
        }

        val use = JacksonAnnotations.enumName(typeInfo, USE)
        val declared = subTypes(sealed)

        return inDeclaredOrder(declared, leaves).map { leaf ->
            val name = if (use in NAMED) typeName(declared, leaf, simple = use == SIMPLE_NAME) else leaf.java.name

            PropertyMeta(
                name,
                leaf.starProjectedType,
                emptyList(),
                null,
                forceOptional = true,
                nameFixed = true,
                description = LocalizedText(DocsText.SUBTYPE_VALUE, listOf(name)),
            )
        }
    }

    /** `EXISTING_PROPERTY` 판별자 프로퍼티 이름과 하위 타입 이름 목록. 다른 include 면 null. */
    fun existingProperty(sealed: KClass<*>, leaves: List<KClass<*>>): Pair<String, List<String>>? {
        val typeInfo = JacksonAnnotations.findOnHierarchy(sealed.java, JSON_TYPE_INFO) ?: return null
        val name = (JacksonAnnotations.value(typeInfo, "property") as? String)?.takeIf { it.isNotEmpty() }
        val existing = JacksonAnnotations.enumName(typeInfo, INCLUDE) == EXISTING_PROPERTY
        val use = JacksonAnnotations.enumName(typeInfo, USE)

        if (name == null || !existing || use !in NAMED) {
            return null
        }

        return name to typeNames(sealed, leaves, simple = use == SIMPLE_NAME)
    }

    /** [existingProperty] 판별자면 [meta] 에 하위 타입 이름 목록을 붙인다. 이미 있는 프로퍼티라 새로 더하지 않는다. */
    fun markExisting(meta: PropertyMeta, existing: Pair<String, List<String>>?): PropertyMeta =
        if (existing?.first == meta.name) {
            meta.copy(
                fixedValues = existing.second,
                description = meta.description ?: LocalizedText(DocsText.TYPE_DISCRIMINATOR),
            )
        } else {
            meta
        }

    /** `include` 가 하위 타입 객체를 감싸는 형태(`WRAPPER_OBJECT`·`WRAPPER_ARRAY`)인지. 판별자로 나타낼 수 없다. */
    fun isWrapped(base: KClass<*>): Boolean = JacksonAnnotations.findOnHierarchy(base.java, JSON_TYPE_INFO)
        ?.let { JacksonAnnotations.enumName(it, INCLUDE) in setOf(WRAPPER_OBJECT, WRAPPER_ARRAY) } == true

    /**
     * 하위 타입을 가리는 판별자 프로퍼티와 하위 타입마다의 값 (OpenAPI `discriminator`). `include` 가 `PROPERTY`(기본값)·
     * `EXISTING_PROPERTY` 이고 `use` 가 `NAME`·`SIMPLE_NAME`·`CLASS` 일 때만 있다. 그 밖(`DEDUCTION`, 외부 프로퍼티 등)은 null.
     */
    fun discriminator(base: KClass<*>, leaves: List<KClass<*>>): TypeDiscriminator? {
        val typeInfo = JacksonAnnotations.findOnHierarchy(base.java, JSON_TYPE_INFO) ?: return null
        val use = JacksonAnnotations.enumName(typeInfo, USE)
        val include = JacksonAnnotations.enumName(typeInfo, INCLUDE)
        val name = (JacksonAnnotations.value(typeInfo, "property") as? String)?.takeIf { it.isNotEmpty() }
            ?: use?.let(DEFAULT_PROPERTY::get)

        if (name == null || include !in setOf(null, PROPERTY, EXISTING_PROPERTY) || use !in NAMED + CLASS) {
            return null
        }

        val declared = subTypes(base)
        val names = leaves.associateWith { leaf ->
            if (use == CLASS) leaf.java.name else typeName(declared, leaf, simple = use == SIMPLE_NAME)
        }

        return TypeDiscriminator(name, names, existing = include == EXISTING_PROPERTY)
    }

    /** [leaves] 를 [base] 의 `@JsonSubTypes` 선언 순서로. 선언에 없는 하위 타입은 뒤에 붙는다. */
    fun ordered(base: KClass<*>, leaves: List<KClass<*>>): List<KClass<*>> = inDeclaredOrder(subTypes(base), leaves)

    /** [base] 의 `@JsonSubTypes` 로 선언한 하위 타입 중 객체를 만들 수 있는 것 (sealed 가 아닌 추상 타입·인터페이스). */
    fun declaredSubclasses(base: KClass<*>): List<KClass<*>> = subTypes(base)
        .mapNotNull { JacksonAnnotations.value(it) as? Class<*> }
        .filter {
            it != base.java && base.java.isAssignableFrom(it) && !it.isInterface &&
                !Modifier.isAbstract(it.modifiers)
        }
        .distinct()
        .map { it.kotlin }

    /** 하위 타입 이름을 `@JsonSubTypes` 선언 순서대로. 선언에 없는 하위 타입은 뒤에 붙는다. */
    private fun typeNames(sealed: KClass<*>, leaves: List<KClass<*>>, simple: Boolean): List<String> {
        val declared = subTypes(sealed)

        return inDeclaredOrder(declared, leaves).map { leaf -> typeName(declared, leaf, simple) }
    }

    /** [leaves] 를 `@JsonSubTypes` 선언 순서로. 선언에 없는 하위 타입은 뒤에 붙는다. */
    private fun inDeclaredOrder(declared: List<Annotation>, leaves: List<KClass<*>>): List<KClass<*>> {
        val order = declared.map { JacksonAnnotations.value(it) }

        return leaves.sortedBy { leaf -> order.indexOf(leaf.java).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    private fun subTypes(sealed: KClass<*>): List<Annotation> {
        val types = JacksonAnnotations.findOnHierarchy(sealed.java, JSON_SUB_TYPES)
            ?.let { JacksonAnnotations.value(it) } as? Array<*>

        return types?.filterIsInstance<Annotation>().orEmpty()
    }

    /**
     * `@JsonSubTypes.Type(name)` → 하위 타입의 `@JsonTypeName` → 클래스 이름 (Jackson 의 기본 규칙).
     * 기본 이름은 `use = NAME` 이면 패키지를 뗀 이진 이름이라 중첩 클래스는 `Outer$Inner` 가 되고,
     * `SIMPLE_NAME` 이면 단순 이름(`Inner`)이다.
     */
    private fun typeName(declared: List<Annotation>, leaf: KClass<*>, simple: Boolean): String {
        val fromSubTypes = declared.firstOrNull { JacksonAnnotations.value(it) == leaf.java }
            ?.let { JacksonAnnotations.value(it, "name") as? String }
        val fromTypeName = JacksonAnnotations.find(leaf.java.annotations.asIterable(), JSON_TYPE_NAME)
            ?.let { JacksonAnnotations.value(it) as? String }

        return listOfNotNull(fromSubTypes, fromTypeName)
            .firstOrNull { it.isNotEmpty() }
            ?: if (simple) {
                leaf.java.simpleName
            } else {
                leaf.java.name.substringAfterLast('.')
            }
    }
}
