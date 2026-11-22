package io.github.kardx.internal.introspection

import io.github.kardx.internal.reflect.loadClassOrNull
import io.github.kardx.internal.reflect.memberValue
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

/**
 * Spring HATEOAS 모델을 HAL(Spring HATEOAS 의 기본 미디어 타입)로 직렬화한 모양의 타입으로 바꾼다.
 * spring-hateoas 에 컴파일 의존하지 않도록 클래스 이름으로 판별하고, 판별 결과는 클래스마다 한 번만 계산한다.
 *
 * | 타입 | 본문 |
 * |---|---|
 * | `EntityModel<T>` | `T` 의 필드 + `_links` |
 * | `RepresentationModel` 을 상속한 클래스 | 자기 필드 + `_links` (`links` 프로퍼티 대신) |
 * | `CollectionModel<T>` | `_embedded.<관계>[]` + `_links` |
 * | `PagedModel<T>` | `_embedded.<관계>[]` + `_links` + `page` (`size`, `totalElements`, `totalPages`, `number`) |
 *
 * `_links` 는 관계 이름이 키인 링크 객체(subsection)다. 어떤 관계가 오는지는 타입으로 알 수 없어 하위 필드를 검증하지 않는다.
 * `_links`·`_embedded` 는 비어 있으면 빠지므로(`@JsonInclude(NON_EMPTY)`) optional 이다.
 *
 * 컬렉션의 관계 이름은 Spring HATEOAS 와 같은 순서로 정한다. 원소(`EntityModel<T>` 면 `T`) 클래스의
 * `@Relation(collectionRelation)` → evo-inflector 가 클래스패스에 있으면 복수형(`books`) → `<타입 이름>List`(`bookList`).
 */
internal object HateoasTypes {
    private enum class Kind { NONE, MODEL, ENTITY, COLLECTION, PAGED }

    private val kinds = object : ClassValue<Kind>() {
        override fun computeValue(type: Class<*>): Kind = kindOf(type)
    }

    private val relations = object : ClassValue<String>() {
        override fun computeValue(type: Class<*>): String = collectionRelation(type)
    }

    /** [type] 이 HATEOAS 모델(`EntityModel`, `CollectionModel`, `PagedModel`)이면 HAL 모양 타입, 아니면 null. */
    fun bodyType(type: KType): KType? {
        val k = type.classifier as? KClass<*> ?: return null

        return when (kinds.get(k.java)) {
            Kind.ENTITY -> shape(HalEntityJson::class, typeArgumentOf(type, k, ENTITY_MODEL))
            Kind.COLLECTION -> shape(HalCollectionJson::class, embeddedOf(typeArgumentOf(type, k, COLLECTION_MODEL)))
            Kind.PAGED -> shape(HalPagedJson::class, embeddedOf(typeArgumentOf(type, k, COLLECTION_MODEL)))
            else -> null
        }
    }

    /**
     * [type] 의 프로퍼티를 정해진 것과 다르게 계산해야 하면 그 목록. `_embedded` 의 원소 목록은 원소 타입마다 관계 이름이
     * 달라 클래스 단위 캐시([PropertyIntrospector])에 둘 수 없다. 그 밖에는 null.
     */
    fun properties(type: KType, k: KClass<*>): List<PropertyMeta>? {
        if (k != HalEmbeddedJson::class) {
            return null
        }

        val relation = type.arguments.firstOrNull()?.type?.let(::relationOf) ?: return null

        return PropertyIntrospector.properties(k).map { it.copy(name = relation, nameFixed = true) }
    }

    /**
     * [PropertyIntrospector] 가 읽은 [k] 의 프로퍼티를 HAL 모양으로 고친다. HAL 모양 클래스의 키(`_links`, `_embedded`,
     * `page.*`)는 앱의 이름 규칙을 따르지 않고, `EntityModel` 의 내용은 펼친다. `RepresentationModel` 을 상속한 클래스는
     * `links` 프로퍼티(`Links`) 대신 `_links` 가 붙는다.
     */
    fun adjust(k: KClass<*>, properties: List<PropertyMeta>): List<PropertyMeta> = when {
        k in HAL_SHAPES -> properties.map { meta ->
            if (k == HalEntityJson::class && meta.name == CONTENT) {
                meta.copy(unwrapped = JsonUnwrapped("", ""))
            } else {
                meta.copy(name = HAL_KEYS[meta.name] ?: meta.name, nameFixed = true)
            }
        }

        kinds.get(k.java) == Kind.MODEL ->
            properties.filterNot { it.name == LINKS && (it.type.classifier as? KClass<*>)?.java?.name == LINKS_CLASS } +
                PropertyIntrospector.properties(HalLinksJson::class)

        else -> properties
    }

    private fun kindOf(type: Class<*>): Kind {
        if (type.name.startsWith(JAVA_PACKAGE) || type.name.startsWith(KOTLIN_PACKAGE)) {
            return Kind.NONE
        }

        return generateSequence(type) { it.superclass }
            .firstNotNullOfOrNull { KINDS[it.name] }
            ?: Kind.NONE
    }

    /** [type] 이 선언한 HATEOAS 상위 타입([base])의 타입 인자. 알 수 없으면 `Any?`. */
    private fun typeArgumentOf(type: KType, k: KClass<*>, base: String): KType {
        val declared = (listOf(type) + k.allSupertypes).firstOrNull {
            (it.classifier as? KClass<*>)?.java?.name == base
        }

        return declared?.arguments?.firstOrNull()?.type?.substitute(bindingsOf(type)) ?: typeOf<Any?>()
    }

    /** 원소 타입의 관계 이름을 알면 [HalEmbeddedJson], 모르면 키를 정할 수 없는 맵(subsection). */
    private fun embeddedOf(element: KType): KType = if (relationOf(element) == null) {
        typeOf<Map<String, Any>>()
    } else {
        HalEmbeddedJson::class.createType(listOf(KTypeProjection.invariant(element)))
    }

    /** 원소 [element] 의 컬렉션 관계 이름. `EntityModel<T>` 는 `T` 로 정한다. 클래스를 알 수 없으면 null. */
    private fun relationOf(element: KType): String? {
        val k = element.classifier as? KClass<*> ?: return null
        val target = if (kinds.get(k.java) == Kind.ENTITY) {
            typeArgumentOf(element, k, ENTITY_MODEL).classifier as? KClass<*>
        } else {
            k
        }

        return target?.takeUnless { it == Any::class }
            ?.let { relations.get(it.java) }
    }

    private fun collectionRelation(type: Class<*>): String {
        val annotated = type.annotations.firstOrNull { it.annotationClass.java.name == RELATION }
            ?.let { it.memberValue(COLLECTION_RELATION) as? String }
            ?.takeIf { it.isNotEmpty() }
        val item = type.simpleName.replaceFirstChar { it.lowercaseChar() }

        return annotated ?: plural(type, item) ?: "${item}List"
    }

    /** evo-inflector 의 영어 복수형. 클래스패스에 없거나 부르지 못하면 null (Spring HATEOAS 도 그때는 쓰지 않는다). */
    private fun plural(type: Class<*>, word: String): String? {
        val english = loadClassOrNull(EVO_INFLECTOR, type.classLoader ?: return null) ?: return null

        return runCatching { english.getMethod(PLURAL, String::class.java).invoke(null, word) as? String }.getOrNull()
    }

    private fun shape(shape: KClass<*>, argument: KType): KType =
        shape.createType(listOf(KTypeProjection.invariant(argument)))

    private const val HATEOAS = "org.springframework.hateoas."
    private const val ENTITY_MODEL = "${HATEOAS}EntityModel"
    private const val COLLECTION_MODEL = "${HATEOAS}CollectionModel"
    private const val LINKS_CLASS = "${HATEOAS}Links"
    private const val RELATION = "${HATEOAS}server.core.Relation"
    private const val COLLECTION_RELATION = "collectionRelation"
    private const val EVO_INFLECTOR = "org.atteo.evo.inflector.English"
    private const val PLURAL = "plural"
    private const val CONTENT = "content"
    private const val LINKS = "links"
    private const val JAVA_PACKAGE = "java."
    private const val KOTLIN_PACKAGE = "kotlin."

    private val KINDS = mapOf(
        "${HATEOAS}PagedModel" to Kind.PAGED,
        COLLECTION_MODEL to Kind.COLLECTION,
        ENTITY_MODEL to Kind.ENTITY,
        "${HATEOAS}RepresentationModel" to Kind.MODEL,
    )

    private val HAL_KEYS = mapOf(LINKS to "_links", "embedded" to "_embedded")

    private val HAL_SHAPES: Set<KClass<*>> = setOf(
        HalEntityJson::class,
        HalCollectionJson::class,
        HalPagedJson::class,
        HalPageMetadataJson::class,
        HalLinksJson::class,
    )
}
