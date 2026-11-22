package io.github.kardx.internal.introspection

import io.github.kardx.config.PropertyContext
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.i18n.LocalizedText
import io.github.kardx.internal.reflect.annotationsWithParameters
import io.github.kardx.internal.reflect.declaredFieldOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter

/**
 * JSON 으로 직렬화되는 프로퍼티 하나.
 *
 * @property name JSON 필드 이름 (`@JsonProperty` 반영)
 * @property type 선언 타입 (타입 파라미터 치환 전)
 * @property forceOptional sealed 타입에서 일부 하위 타입에만 있는 프로퍼티
 * @property hasDefault 요청에서 생략할 수 있는 프로퍼티. 기본값이 있는 주 생성자 파라미터, 생성자 밖의 `var`
 * @property nameFixed `@JsonProperty` 나 클래스 `@JsonNaming` 으로 이름이 정해져 전역 이름 전략을 적용하지 않는 프로퍼티
 * @property writeOnly 요청에만 있는 프로퍼티 (`@JsonProperty(access = WRITE_ONLY)`). 응답 추출에서 뺀다
 * @property readOnly 응답에만 있는 프로퍼티 (`access = READ_ONLY`, 생성자 밖의 읽기 전용 `val`). 요청 추출에서 뺀다
 * @property fixedValues 값이 정해진 목록이면 그 값들 (sealed 타입의 판별자)
 * @property description 어노테이션으로 정한 설명이 없을 때 쓸 설명. 프로퍼티 정보는 캐시되므로 쓸 때의 언어로 고른다
 * @property unwrapped `@JsonUnwrapped`: 하위 필드를 이 프로퍼티 대신 부모 수준에 펼친다
 * @property declaredName Jackson 이름 규칙을 적용하기 전의 선언 이름. 파라미터 바인딩(@ModelAttribute)에 쓴다
 * @property elementAnnotations Java 선언의 컬렉션·배열 원소(맵은 값)에 붙은 타입 어노테이션 (`List<@NotBlank String>`).
 *   Kotlin 은 [type] 의 타입 인자에서 읽으므로 비어 있다
 * @property defaultValue 요청 추출에서 읽은 생성자 기본값의 JSON 표현 ([KotlinDefaults]). 캐시한 프로퍼티 정보에는 없고
 *   추출기가 요청 본문·파라미터를 만들 때만 채운다
 * @property owner 프로퍼티를 가진 클래스 (sealed 타입의 프로퍼티는 그것을 가진 하위 타입). KDoc·Javadoc 설명을 찾는 데 쓴다
 */
internal data class PropertyMeta(
    val name: String,
    val type: KType,
    val annotations: List<Annotation>,
    val property: KProperty1<*, *>?,
    val forceOptional: Boolean = false,
    val hasDefault: Boolean = false,
    val nameFixed: Boolean = false,
    val writeOnly: Boolean = false,
    val readOnly: Boolean = false,
    val fixedValues: List<String>? = null,
    val description: LocalizedText? = null,
    val unwrapped: JsonUnwrapped? = null,
    val declaredName: String = name,
    val elementAnnotations: List<Annotation> = emptyList(),
    val defaultValue: String? = null,
    val owner: Class<*>? = null,
) {
    val context: PropertyContext get() = PropertyContext(name, annotations, property, declaredName, owner)
}

/** `@JsonUnwrapped(prefix, suffix)`. 펼친 하위 필드 이름 앞뒤에 붙인다. */
internal data class JsonUnwrapped(val prefix: String, val suffix: String) {
    fun decorate(name: String): String = prefix + name + suffix
}

/**
 * 클래스에서 Jackson 이 직렬화할 프로퍼티를 선언 순서대로 수집한다.
 *
 * - Kotlin 클래스: public 프로퍼티, 주 생성자 파라미터 순서. 생성자 밖 프로퍼티는 백킹 필드 선언 순서
 * - sealed 클래스: 하위 타입 프로퍼티의 합집합. 일부 하위 타입에만 있으면 optional. `@JsonTypeInfo` 판별자 필드 포함.
 *   `include = WRAPPER_OBJECT` 면 하위 타입 이름마다 그 하위 타입 객체 프로퍼티 하나 ([SealedTypeDiscriminator])
 * - Java record·bean: [JavaProperties]
 *
 * `@JsonIgnore`, 클래스의 `@JsonIgnoreProperties` 프로퍼티는 제외하고 `@JsonProperty` 이름·access,
 * `@JsonUnwrapped`, 클래스(와 상위 타입)의 `@JsonNaming` 전략을 반영한다.
 * Jackson 2/3 모두 `com.fasterxml.jackson.annotation` 을 쓰므로 이름으로 비교한다.
 */
internal object PropertyIntrospector {
    /** Jackson 2 이름 규칙 여부(매퍼가 바뀌면 Java bean 프로퍼티 이름이 달라진다)와 kotlinx 규칙 여부도 키에 넣는다 */
    private val cache = ConcurrentHashMap<CacheKey, List<PropertyMeta>>()

    private data class CacheKey(val k: KClass<*>, val legacyNaming: Boolean, val kotlinx: Boolean)

    /** [type] 의 프로퍼티. 타입 인자에 따라 이름이 달라지는 HAL `_embedded` 를 빼면 [properties] 와 같다. */
    fun properties(type: KType, k: KClass<*>): List<PropertyMeta> = HateoasTypes.properties(type, k) ?: properties(k)

    fun properties(k: KClass<*>): List<PropertyMeta> {
        val key = CacheKey(k, isJackson2(), KotlinxSerialization.enabled())

        return cache[key] ?: compute(k, key.legacyNaming).also { cache[key] = it }
    }

    private fun compute(k: KClass<*>, legacyNaming: Boolean): List<PropertyMeta> {
        kotlinxProperties(k, legacyNaming)?.let { return it }

        val raw = when {
            k.java.isRecord -> JavaProperties.ofRecord(k.java)
            !k.java.isAnnotationPresent(Metadata::class.java) -> JavaProperties.ofBean(k.java, legacyNaming)
            k.isSealed -> sealedProperties(k, legacyNaming)
            else -> kotlinProperties(k, legacyNaming)
        }

        val classNaming = PropertyNaming.ofClass(k)
        val ignored = JacksonAnnotations.ignoredProperties(k.java)

        // @JsonAnyGetter 맵은 이름 있는 프로퍼티가 아니라 부모 객체에 펼쳐지는 항목이다 ([isOpenType])
        val properties = raw.filterNot { JacksonAnnotations.isJsonIgnored(it.annotations) }
            .filterNot { JacksonAnnotations.isAnyGetter(it.annotations) }
            .mapNotNull { withJacksonAnnotations(it, classNaming, ignored) }
            .map { if (it.owner == null) it.copy(owner = k.java) else it }

        return HateoasTypes.adjust(k, properties)
    }

    /** `@JsonProperty` 이름·access, 클래스 이름 전략, `@JsonIgnoreProperties`, `@JsonUnwrapped` 를 반영한다. 빠지면 null. */
    private fun withJacksonAnnotations(
        meta: PropertyMeta,
        classNaming: ((String) -> String)?,
        ignored: IgnoredProperties,
    ): PropertyMeta? {
        val name = jsonName(meta, classNaming)
        val access = JacksonAnnotations.jsonPropertyAccess(meta.annotations)
        val isIgnored = meta.name in ignored.names || name in ignored.names

        // @JsonIgnoreProperties(allowSetters) 는 요청에만, allowGetters 는 응답에만 남는다
        if (isIgnored && !ignored.keepsOneSide) {
            return null
        }

        return meta.copy(
            name = name ?: meta.name,
            nameFixed = meta.nameFixed || name != null,
            writeOnly = access == WRITE_ONLY || (isIgnored && ignored.allowSetters),
            readOnly = meta.readOnly || access == READ_ONLY || (isIgnored && ignored.allowGetters),
            unwrapped = JacksonAnnotations.jsonUnwrapped(meta.annotations),
        )
    }

    /**
     * kotlinx.serialization 으로 직렬화되는 클래스의 프로퍼티 ([KotlinxSerialization]). Jackson 어노테이션과 이름 규칙은
     * 적용하지 않는다. kotlinx 규칙이 아니거나 descriptor 를 읽지 못하면 null.
     */
    private fun kotlinxProperties(k: KClass<*>, legacyNaming: Boolean): List<PropertyMeta>? {
        if (!KotlinxSerialization.applies(k)) {
            return null
        }

        val properties = if (k.isSealed) {
            sealedProperties(k, legacyNaming)
        } else {
            KotlinxSerialization.properties(k, kotlinDeclaredProperties(k, publicOnly = false))
        }

        return properties?.map { if (it.owner == null) it.copy(owner = k.java) else it }
    }

    /**
     * `@JsonProperty` 이름. 없으면 클래스 이름 전략으로 바꾼 이름, 바꿀 것이 없으면 null.
     * 이미 정해진 이름(판별자, 하위 타입에서 변환한 이름)에는 클래스 전략을 다시 적용하지 않는다.
     */
    private fun jsonName(meta: PropertyMeta, classNaming: ((String) -> String)?): String? =
        JacksonAnnotations.jsonPropertyName(meta.annotations)
            ?: classNaming?.takeUnless { meta.nameFixed }
                ?.invoke(meta.name)

    /**
     * Kotlin 클래스의 프로퍼티. Java bean 을 상속했으면 그 getter 프로퍼티([javaBaseProperties])를 뒤에 붙인다.
     * Kotlin 리플렉션은 Java getter 만 있는 프로퍼티(`getData()`)를 `KProperty` 로 보여 주지 않는다.
     */
    private fun kotlinProperties(k: KClass<*>, legacyNaming: Boolean): List<PropertyMeta> {
        val own = kotlinDeclaredProperties(k)
        val ownNames = own.map { it.name }.toSet()

        return own + javaBaseProperties(k, legacyNaming).filterNot { it.name in ownNames }
    }

    /**
     * 가장 가까운 Java 상위 클래스(Kotlin 메타데이터가 없는 클래스)의 bean 프로퍼티. JDK·Kotlin 표준 클래스(`Object`,
     * `Exception` …)는 DTO 의 상위 클래스로 보지 않아 빈 목록이다.
     */
    private fun javaBaseProperties(k: KClass<*>, legacyNaming: Boolean): List<PropertyMeta> {
        val base = generateSequence(k.java.superclass) { it.superclass }
            .firstOrNull { !it.isAnnotationPresent(Metadata::class.java) }
            ?.takeUnless { base -> PLATFORM_PACKAGES.any { base.name.startsWith(it) } }
            ?: return emptyList()

        return JavaProperties.ofBean(base, legacyNaming)
    }

    /** [publicOnly] 가 false 면 비공개 프로퍼티도 읽는다 (kotlinx 는 백킹 필드가 있는 프로퍼티를 공개 여부와 상관없이 쓴다). */
    private fun kotlinDeclaredProperties(k: KClass<*>, publicOnly: Boolean = true): List<PropertyMeta> {
        val parameters = k.primaryConstructor?.parameters.orEmpty()
        val constructorOrder = parameters.mapNotNull { it.name }
            .withIndex()
            .associate { it.value to it.index }
        val fieldOrder = declaredFieldOrder(k.java)
        val hierarchy = listOf(k) + k.allSuperclasses.filter { it.java.isAnnotationPresent(Metadata::class.java) }

        return k.memberProperties.filter { !publicOnly || it.visibility == KVisibility.PUBLIC }
            .map { property ->
                val annotations = hierarchy.flatMap { declaredAnnotations(it, property.name) }
                    .distinct()
                val parameter = parameters.firstOrNull { it.name == property.name }
                val mutable = property is KMutableProperty1<*, *>

                PropertyMeta(
                    property.name,
                    property.returnType,
                    annotations,
                    property,
                    hasDefault = parameter?.isOptional == true || (parameter == null && mutable),
                    readOnly = parameter == null && !mutable,
                    description = annotations.firstNotNullOfOrNull { (it as? BuiltInDescription)?.text }
                        ?.let(::LocalizedText),
                )
            }
            .sortedWith(
                compareBy(
                    { constructorOrder[it.name] ?: Int.MAX_VALUE },
                    { fieldOrder[it.name] ?: Int.MAX_VALUE },
                    { it.name },
                ),
            )
    }

    /**
     * [owner] 에 선언된 [name] 프로퍼티의 어노테이션. 프로퍼티·백킹 필드·getter·setter(와 그 파라미터)·
     * 주 생성자 파라미터를 모두 본다.
     * 상속된 프로퍼티는 어노테이션이 상위 클래스의 생성자 파라미터에 붙어 있으므로 계층 전체에 대해 호출한다.
     */
    private fun declaredAnnotations(owner: KClass<*>, name: String): List<Annotation> {
        val property = owner.declaredMemberProperties.firstOrNull { it.name == name }
        val parameter = owner.primaryConstructor?.parameters?.firstOrNull { it.name == name }

        return property?.annotations.orEmpty() +
            property?.javaField?.annotations.orEmpty() +
            property?.javaGetter?.annotations.orEmpty() +
            (property as? KMutableProperty1<*, *>)?.javaSetter.annotationsWithParameters() +
            parameter?.annotations.orEmpty()
    }

    private fun sealedProperties(k: KClass<*>, legacyNaming: Boolean): List<PropertyMeta> {
        val leaves = leafSubclasses(k)

        if (leaves.isEmpty()) {
            return kotlinProperties(k, legacyNaming)
        }

        val kotlinx = KotlinxSerialization.applies(k)
        val wrapped = if (kotlinx) null else SealedTypeDiscriminator.wrapperProperties(k, leaves)

        wrapped?.let { return it }

        val perLeaf = leaves.map { leaf -> properties(leaf).associateBy { it.name } }
        val merged = LinkedHashMap<String, PropertyMeta>()

        perLeaf.flatMap { it.values }
            .forEach { merged.putIfAbsent(it.name, it) }

        val existing = if (kotlinx) null else SealedTypeDiscriminator.existingProperty(k, leaves)
        val properties = merged.values.map { meta ->
            val inAll = perLeaf.all { meta.name in it }

            SealedTypeDiscriminator.markExisting(if (inAll) meta else meta.copy(forceOptional = true), existing)
        }

        val discriminator = discriminatorOf(k, leaves, kotlinx)?.takeIf { it.name !in merged }

        return listOfNotNull(discriminator) + properties
    }

    /** sealed 타입이 더하는 판별자 필드. kotlinx 는 늘 더하고, Jackson 은 `@JsonTypeInfo` 에 따른다. */
    private fun discriminatorOf(k: KClass<*>, leaves: List<KClass<*>>, kotlinx: Boolean): PropertyMeta? = if (kotlinx) {
        KotlinxSerialization.discriminator(k, leaves)
    } else {
        SealedTypeDiscriminator.property(k, leaves)
    }

    /** sealed 타입의 실제 하위 타입. 중간의 sealed 타입은 그 하위 타입으로 펼친다. */
    fun leafSubclasses(k: KClass<*>): List<KClass<*>> =
        k.sealedSubclasses.flatMap { if (it.isSealed) leafSubclasses(it) else listOf(it) }

    /** 지금 매퍼가 Jackson 2 `ObjectMapper`(하위 클래스 포함)인지. 매퍼를 알 수 없으면 Jackson 2 로 본다. */
    private fun isJackson2(): Boolean = runCatching {
        generateSequence<Class<*>>(RestDocsConfiguration.objectMapper().javaClass) { it.superclass }
            .any { it.name == JACKSON_2_MAPPER }
    }.getOrDefault(true)

    private const val JACKSON_2_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper"
    private const val READ_ONLY = "READ_ONLY"
    private const val WRITE_ONLY = "WRITE_ONLY"

    /** 상위 클래스여도 bean 프로퍼티를 보지 않는 JDK·Kotlin 패키지 */
    private val PLATFORM_PACKAGES = listOf("java.", "javax.", "jdk.", "sun.", "kotlin.")
}
