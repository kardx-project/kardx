package io.github.kardx.internal.introspection

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.i18n.DocsText
import io.github.kardx.internal.i18n.LocalizedText
import io.github.kardx.internal.reflect.loadClassOrNull
import io.github.kardx.internal.reflect.memberValue
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.withNullability
import kotlin.reflect.typeOf

/**
 * kotlinx.serialization 으로 직렬화되는 클래스를 kotlinx 가 쓰는 모양으로 읽는다.
 *
 * Spring MVC·WebFlux 는 kotlinx-serialization-json 이 클래스패스에 있으면 `@Serializable` 클래스를 Jackson 보다 먼저
 * kotlinx 로 직렬화한다. 그래서 [applies] 는 같은 조건(클래스의 `@Serializable` + kotlinx-serialization-json)에
 * [io.github.kardx.config.ExtractionSettings.extraction.kotlinxSerialization] 설정을 더해 판단한다.
 *
 * - 필드: serial descriptor 의 필드 (`@SerialName` 이름, `@Transient`·백킹 필드 없는 프로퍼티 제외). 비공개 프로퍼티도 나간다.
 *   Jackson 어노테이션과 이름 규칙은 보지 않는다
 * - 생략: 기본값이 있는 프로퍼티는 요청에서 생략할 수 있고, `Json` 의 기본값 `encodeDefaults = false` 때문에 응답에서도 빠질 수
 *   있어 optional 이다. `@EncodeDefault`(`ALWAYS`) 프로퍼티는 응답에 늘 있다
 * - 값 하나로 쓰는 직렬화기(`PrimitiveSerialDescriptor`): 그 타입(문자열·숫자·불리언)
 * - sealed 타입: 판별자 `type` (`@JsonClassDiscriminator` 로 바꾼 이름)에 하위 타입의 serial 이름을 값으로 붙인다
 *
 * 앱이 `Json { namingStrategy / classDiscriminator / encodeDefaults }` 를 바꿨으면 그 설정은 알 수 없다.
 * kotlinx API 는 [KotlinxDescriptors] 에서만 부르므로 kotlinx 가 없는 클래스패스에서도 이 객체는 로드된다.
 */
internal object KotlinxSerialization {
    private const val SERIALIZABLE = "kotlinx.serialization.Serializable"
    private const val SERIAL_NAME = "kotlinx.serialization.SerialName"
    private const val ENCODE_DEFAULT = "kotlinx.serialization.EncodeDefault"
    private const val CLASS_DISCRIMINATOR = "kotlinx.serialization.json.JsonClassDiscriminator"
    private const val JSON = "kotlinx.serialization.json.Json"
    private const val DEFAULT_DISCRIMINATOR = "type"
    private const val NEVER = "NEVER"

    /** kotlinx-serialization-json 이 이 라이브러리와 같은 클래스패스에 있는지. 처음 물을 때 한 번만 확인한다 */
    private val available: Boolean by lazy { loadClassOrNull(JSON) != null }

    private val serializable = object : ClassValue<Boolean>() {
        override fun computeValue(type: Class<*>): Boolean =
            type.annotations.any { it.annotationClass.java.name == SERIALIZABLE }
    }

    /** 설정이 켜져 있고 kotlinx-serialization-json 이 클래스패스에 있는지. 프로퍼티 캐시의 키에 쓴다. */
    fun enabled(): Boolean = RestDocsConfiguration.current.extraction.kotlinxSerialization && available

    /** [k] 를 kotlinx 규칙으로 읽을지. */
    fun applies(k: KClass<*>): Boolean = serializable.get(k.java) && enabled()

    /** [k] 가 kotlinx 로 값 하나(문자열·숫자·불리언)가 되면 그 타입. 아니면 null. */
    fun valueType(k: KClass<*>): KType? = if (applies(k)) KotlinxDescriptors.of(k)?.valueType else null

    /**
     * [k] 의 필드를 kotlinx 규칙으로 만든다. [declared] 는 [k] 의 Kotlin 프로퍼티(비공개 포함)다. descriptor 를 읽지 못하면
     * null 이고, 그때는 Jackson 규칙으로 돌아간다.
     */
    fun properties(k: KClass<*>, declared: List<PropertyMeta>): List<PropertyMeta>? {
        val serialClass = KotlinxDescriptors.of(k)?.takeIf { it.valueType == null } ?: return null
        val bySerialName = declared.associateBy { serialNameOf(it.annotations) ?: it.declaredName }

        return serialClass.elements.map { element ->
            val meta = bySerialName[element.name] ?: PropertyMeta(element.name, typeOf<Any?>(), emptyList(), null)

            meta.copy(
                name = element.name,
                type = element.valueType?.let { overriddenType(meta.type, it) } ?: meta.type,
                nameFixed = true,
                hasDefault = element.optional,
                readOnly = false,
                writeOnly = false,
                forceOptional = element.optional && !alwaysEncoded(meta.annotations),
            )
        }
    }

    /** kotlinx sealed 타입 [sealed] 의 판별자 프로퍼티. 값은 [leaves] 의 serial 이름이다. */
    fun discriminator(sealed: KClass<*>, leaves: List<KClass<*>>): PropertyMeta = PropertyMeta(
        discriminatorName(sealed),
        typeOf<String>(),
        emptyList(),
        null,
        nameFixed = true,
        fixedValues = leaves.map(::serialNameOf),
        description = LocalizedText(DocsText.TYPE_DISCRIMINATOR),
    )

    /** OpenAPI `discriminator` 용 판별자. kotlinx 는 판별자 필드를 직접 더한다. */
    fun typeDiscriminator(sealed: KClass<*>, leaves: List<KClass<*>>): TypeDiscriminator =
        TypeDiscriminator(discriminatorName(sealed), leaves.associateWith(::serialNameOf), existing = false)

    /** 클래스의 serial 이름. descriptor 를 읽을 수 있으면 그 이름, 아니면 `@SerialName` → 전체 이름이다. */
    private fun serialNameOf(k: KClass<*>): String = KotlinxDescriptors.of(k)?.serialName
        ?: serialNameOf(k.java.annotations.asList())
        ?: k.qualifiedName
        ?: k.java.name

    private fun serialNameOf(annotations: List<Annotation>): String? =
        annotations.firstOrNull { it.annotationClass.java.name == SERIAL_NAME }
            ?.let { it.memberValue() as? String }

    /** `@JsonClassDiscriminator` 는 상위 sealed 타입에서 물려받는다. 없으면 `Json` 기본값 `type`. */
    private fun discriminatorName(sealed: KClass<*>): String = generateSequence(listOf(sealed.java)) { level ->
        level.flatMap { listOfNotNull(it.superclass) + it.interfaces }
            .takeIf { it.isNotEmpty() }
    }
        .flatten()
        .flatMap { it.annotations.asSequence() }
        .firstOrNull { it.annotationClass.java.name == CLASS_DISCRIMINATOR }
        ?.let { it.memberValue("discriminator") as? String }
        ?: DEFAULT_DISCRIMINATOR

    /** `@EncodeDefault` 는 기본 모드가 `ALWAYS` 다. `NEVER` 면 기본값을 쓰지 않는다. */
    private fun alwaysEncoded(annotations: List<Annotation>): Boolean =
        annotations.firstOrNull { it.annotationClass.java.name == ENCODE_DEFAULT }
            ?.let { (it.memberValue("mode") as? Enum<*>)?.name != NEVER } == true

    /** 필드 직렬화기가 값 하나로 쓰는 타입. 선언 타입이 이미 같은 JSON 타입이면(Enum·값 클래스 포함) 그대로 둔다. */
    private fun overriddenType(declared: KType, serial: KType): KType {
        val same = docsFieldTypeOf(declared) == docsFieldTypeOf(serial)

        return if (same) declared else serial.withNullability(declared.isMarkedNullable)
    }
}
