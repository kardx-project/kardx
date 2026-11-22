package io.github.kardx.internal.introspection

import io.github.kardx.internal.reflect.enumJsonValues
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Kotlin 클래스 주 생성자의 기본값(`val size: Int = 20`). OpenAPI `default` 와 표의 `(Default: 20)` 에 쓴다.
 *
 * 기본값 식은 리플렉션으로 읽을 수 없으므로 기본값이 있는 파라미터를 비워 두고 생성자를 불러([KParameter] `callBy`)
 * 만들어진 객체의 프로퍼티 값을 읽는다. 기본값이 없는 파라미터에는 자리 값(nullable 은 `null`, 문자열 `""`, 숫자 `0`,
 * 불리언 `false`, Enum 첫 상수, 빈 컬렉션·배열, `java.time` 의 기준 시각, `UUID(0, 0)`)을 넣는다. Kotlin 클래스 객체는 같은 방법으로 만든 자리 객체를 넣고
 * ([MAX_DEPTH] 단계까지), sealed 타입은 만들 수 있는 첫 하위 타입의 자리 객체를 넣는다. 자리 값을 정할 수 없는 파라미터
 * (인터페이스·Java 클래스 등)가 있거나 생성이 실패하면(`init` 의 `require` 등) 그 클래스의 기본값은 모르는 것으로 본다.
 *
 * 값 타입(문자열·숫자·불리언·문자·Enum)의 기본값만 남긴다. 컬렉션·객체 기본값과 빈 문자열은 뺀다. Enum 은 JSON 값
 * (`@JsonValue`·`@JsonProperty` 반영)이다.
 *
 * 객체를 두 번 만든다. 두 번째는 필수 파라미터에 다른 자리 값(nullable 은 `null` 대신 값, `"x"`, `1`, `true`, Enum 마지막 상수,
 * 기준 시각 + 1 …)을 넣고, 두 번 다 같은 값만 남긴다. 그래서 다른 파라미터에서 계산한 기본값(`pageSize: Int = limit`)과
 * 실행마다 달라지는 기본값(`UUID.randomUUID()`, 현재 시각)은 빠진다. 빈 컬렉션·배열 자리 값은 두 번 다 같아 컬렉션에서 계산한
 * 기본값(`count: Int = items.size`)은 구별하지 못한다. 환경 변수·설정에서 읽는 기본값도 구별할 수 없어 그대로 나간다.
 *
 * 클래스마다 한 번만 계산하지만 그때 생성자를 두 번(자리 객체가 필요한 하위 클래스도 두 번씩) 부른다. `init` 에 부수 효과(카운터,
 * 로그, 외부 호출)가 있는 클래스는 그만큼 실행된다.
 */
internal object KotlinDefaults {
    private val cache = ConcurrentHashMap<KClass<*>, Map<String, String>>()

    /** 자리 값을 정할 수 없다는 표시. `null` 은 nullable 파라미터의 자리 값이라 따로 둔다 */
    private val UNKNOWN = Any()

    /** 자리 객체를 만드는 중첩 깊이 한도. 자기 자신을 필수 파라미터로 받는 클래스에서 멈추게 한다 */
    private const val MAX_DEPTH = 3

    /** [k] 의 주 생성자 파라미터 이름 → 기본값의 JSON 표현 문자열. 알 수 없으면 빈 맵. */
    fun of(k: KClass<*>): Map<String, String> = cache.computeIfAbsent(k, ::compute)

    /**
     * 필수 파라미터의 자리 값을 바꿔 객체를 두 번 만들고 두 번 다 같은 값만 남긴다. 다른 파라미터에서 계산한 기본값은 자리 값에
     * 따라 달라지고, `UUID.randomUUID()`·`System.currentTimeMillis()` 같은 기본값은 실행마다 달라진다. 둘 다 문서에 내면
     * 의미 없는 값이 "기본값" 으로 나간다.
     */
    private fun compute(k: KClass<*>): Map<String, String> {
        val constructor = k.primaryConstructor?.takeIf { isInstantiable(k) } ?: return emptyMap()
        val defaulted = constructor.parameters.mapNotNull { parameter ->
            parameter.name?.takeIf { parameter.isOptional }
        }

        if (defaulted.isEmpty()) {
            return emptyMap()
        }

        val first = valuesOf(k, defaulted, Variant.FIRST)
        val second = if (first.isEmpty()) emptyMap() else valuesOf(k, defaulted, Variant.SECOND)

        return first.filter { (name, value) -> second[name] == value }
    }

    /** 새로 만든 [k] 객체에서 [names] 프로퍼티 값의 JSON 표현. 만들 수 없으면 빈 맵. */
    private fun valuesOf(k: KClass<*>, names: List<String>, variant: Variant): Map<String, String> {
        val instance = instantiate(k, depth = 0, variant)

        if (instance === UNKNOWN) {
            return emptyMap()
        }

        val properties = k.memberProperties.associateBy { it.name }

        return names.mapNotNull { name ->
            // 게터가 예외를 던지면 그 프로퍼티의 기본값만 모르는 것으로 본다
            val value = properties[name]?.let { property ->
                runCatching {
                    property.isAccessible = true
                    property.getter.call(instance)
                }.getOrNull()
            }

            jsonText(value)?.let { name to it }
        }.toMap()
    }

    /** 객체를 만들 수 있는 Kotlin 클래스인지. 추상·sealed·인터페이스·Java 클래스는 아니다. */
    private fun isInstantiable(k: KClass<*>): Boolean = !k.isAbstract && !k.isSealed && !k.java.isInterface &&
        k.java.isAnnotationPresent(Metadata::class.java)

    /**
     * 기본값이 있는 파라미터는 비우고 나머지는 자리 값으로 채워 [k] 의 객체를 만든다. 만들 수 없으면 [UNKNOWN].
     * 생성자에 접근할 수 없거나 생성이 실패하면(init 의 require, 기본값 식의 예외) 만들 수 없는 것으로 본다.
     */
    private fun instantiate(k: KClass<*>, depth: Int, variant: Variant): Any {
        val constructor = k.primaryConstructor?.takeIf { isInstantiable(k) } ?: return UNKNOWN
        val arguments = placeholders(constructor.parameters, depth, variant) ?: return UNKNOWN

        return runCatching {
            constructor.isAccessible = true
            constructor.callBy(arguments)
        }.getOrDefault(UNKNOWN)
    }

    /** 기본값이 없는 파라미터의 자리 값. 하나라도 정할 수 없으면 null. */
    private fun placeholders(parameters: List<KParameter>, depth: Int, variant: Variant): Map<KParameter, Any?>? {
        val arguments = linkedMapOf<KParameter, Any?>()

        parameters.filterNot { it.isOptional }
            .forEach { parameter ->
                val value = placeholder(parameter.type, depth, variant)

                if (value === UNKNOWN) return null

                arguments[parameter] = value
            }

        return arguments
    }

    /**
     * [variant] 의 자리 값. nullable 은 첫 번째에 `null`, 두 번째에 값을 정할 수 있으면 그 값(없으면 `null`)이다.
     */
    private fun placeholder(type: KType, depth: Int, variant: Variant): Any? {
        val k = type.classifier as? KClass<*>

        if (type.isMarkedNullable && (variant == Variant.FIRST || k == null)) {
            return null
        }

        if (type.isMarkedNullable && k != null) {
            return valuePlaceholder(k, depth, variant).takeUnless { it === UNKNOWN }
        }

        return if (k == null) UNKNOWN else valuePlaceholder(k, depth, variant)
    }

    private fun valuePlaceholder(k: KClass<*>, depth: Int, variant: Variant): Any? = when {
        k.java.isEnum -> enumPlaceholder(k, variant)
        k.java.isArray -> java.lang.reflect.Array.newInstance(k.java.componentType, 0)
        else -> variant.placeholders[k.javaObjectType.name]?.invoke() ?: objectPlaceholder(k, depth + 1, variant)
    }

    private fun enumPlaceholder(k: KClass<*>, variant: Variant): Any {
        val constants = k.java.enumConstants.orEmpty()
        val constant = if (variant == Variant.FIRST) constants.firstOrNull() else constants.lastOrNull()

        return constant ?: UNKNOWN
    }

    /**
     * 객체 파라미터의 자리 객체. `object` 는 그 객체, sealed 타입은 만들 수 있는 첫 하위 타입이다.
     * 너무 깊으면(순환 참조 등) [UNKNOWN].
     */
    private fun objectPlaceholder(k: KClass<*>, depth: Int, variant: Variant): Any? = when {
        depth > MAX_DEPTH -> UNKNOWN
        k.objectInstance != null -> k.objectInstance
        k.isSealed -> sealedPlaceholder(k, depth, variant)
        else -> instantiate(k, depth, variant)
    }

    private fun sealedPlaceholder(k: KClass<*>, depth: Int, variant: Variant): Any = k.sealedSubclasses.asSequence()
        .map { objectPlaceholder(it, depth, variant) }
        .firstOrNull { it !== UNKNOWN } ?: UNKNOWN

    /** 값 타입 기본값의 JSON 표현. 그 밖의 값이나 빈 문자열이면 null. */
    private fun jsonText(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.isNotEmpty() }
        is Boolean, is Char -> value.toString()
        is BigDecimal -> value.toPlainString()
        is Number -> value.toString()
        is Enum<*> -> enumJsonValues(value.declaringJavaClass).getOrNull(value.ordinal)
        else -> null
    }

    /**
     * 두 번의 생성에 쓰는 자리 값. 두 번째([SECOND])는 값 타입마다 첫 번째와 다른 값을 넣어, 다른 파라미터에서 계산한 기본값이
     * 달라지게 한다. 컬렉션은 두 번 다 빈 값이다.
     */
    private enum class Variant(val placeholders: Map<String, () -> Any>) {
        FIRST(COMMON_PLACEHOLDERS + FIRST_VALUES),
        SECOND(COMMON_PLACEHOLDERS + SECOND_VALUES),
    }
}

/**
 * Java 객체 타입 이름 → 자리 값을 만드는 함수. 원시 타입은 [KClass.javaObjectType] 으로 찾는다.
 * 바뀔 수 있는 값(`ArrayList`, `Date` …)도 있어 쓸 때마다 새로 만든다.
 */
private val FIRST_VALUES: Map<String, () -> Any> = mapOf(
    "java.lang.String" to { "" },
    "java.lang.Boolean" to { false },
    "java.lang.Integer" to { 0 },
    "java.lang.Long" to { 0L },
    "java.lang.Short" to { 0.toShort() },
    "java.lang.Byte" to { 0.toByte() },
    "java.lang.Double" to { 0.0 },
    "java.lang.Float" to { 0f },
    "java.lang.Character" to { ' ' },
    "java.math.BigDecimal" to { BigDecimal.ZERO },
    "java.math.BigInteger" to { BigInteger.ZERO },
    "java.util.UUID" to { UUID(0, 0) },
    "java.util.Date" to { Date(0) },
    "java.time.Instant" to { Instant.EPOCH },
    "java.time.LocalDate" to { LocalDate.EPOCH },
    "java.time.LocalTime" to { LocalTime.MIDNIGHT },
    "java.time.LocalDateTime" to { LocalDateTime.of(LocalDate.EPOCH, LocalTime.MIDNIGHT) },
    "java.time.OffsetDateTime" to { OffsetDateTime.of(LocalDate.EPOCH, LocalTime.MIDNIGHT, ZoneOffset.UTC) },
    "java.time.ZonedDateTime" to { ZonedDateTime.of(LocalDate.EPOCH, LocalTime.MIDNIGHT, ZoneOffset.UTC) },
    "java.time.Duration" to { Duration.ZERO },
    "java.time.Period" to { Period.ZERO },
)

/** [FIRST_VALUES] 와 타입마다 다른 값. */
private val SECOND_VALUES: Map<String, () -> Any> = mapOf(
    "java.lang.String" to { "x" },
    "java.lang.Boolean" to { true },
    "java.lang.Integer" to { 1 },
    "java.lang.Long" to { 1L },
    "java.lang.Short" to { 1.toShort() },
    "java.lang.Byte" to { 1.toByte() },
    "java.lang.Double" to { 1.0 },
    "java.lang.Float" to { 1f },
    "java.lang.Character" to { 'x' },
    "java.math.BigDecimal" to { BigDecimal.ONE },
    "java.math.BigInteger" to { BigInteger.ONE },
    "java.util.UUID" to { UUID(0, 1) },
    "java.util.Date" to { Date(SECOND_MILLIS) },
    "java.time.Instant" to { Instant.EPOCH.plusSeconds(1) },
    "java.time.LocalDate" to { LocalDate.EPOCH.plusDays(1) },
    "java.time.LocalTime" to { LocalTime.MIDNIGHT.plusSeconds(1) },
    "java.time.LocalDateTime" to { LocalDateTime.of(LocalDate.EPOCH, LocalTime.MIDNIGHT).plusSeconds(1) },
    "java.time.OffsetDateTime" to
        { OffsetDateTime.of(LocalDate.EPOCH, LocalTime.MIDNIGHT, ZoneOffset.UTC).plusSeconds(1) },
    "java.time.ZonedDateTime" to
        { ZonedDateTime.of(LocalDate.EPOCH, LocalTime.MIDNIGHT, ZoneOffset.UTC).plusSeconds(1) },
    "java.time.Duration" to { Duration.ofSeconds(1) },
    "java.time.Period" to { Period.ofDays(1) },
)

private const val SECOND_MILLIS = 1_000L

/** 두 번 다 같은 자리 값 (빈 컬렉션). */
private val COMMON_PLACEHOLDERS: Map<String, () -> Any> = mapOf(
    "java.util.List" to { emptyList<Any>() },
    "java.util.Collection" to { emptyList<Any>() },
    "java.lang.Iterable" to { emptyList<Any>() },
    "java.util.ArrayList" to { ArrayList<Any>() },
    "java.util.Set" to { emptySet<Any>() },
    "java.util.HashSet" to { HashSet<Any>() },
    "java.util.LinkedHashSet" to { LinkedHashSet<Any>() },
    "java.util.Map" to { emptyMap<Any, Any>() },
    "java.util.HashMap" to { HashMap<Any, Any>() },
    "java.util.LinkedHashMap" to { LinkedHashMap<Any, Any>() },
)
