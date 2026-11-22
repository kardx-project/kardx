package io.github.kardx.internal.introspection

import io.github.kardx.dsl.ARRAY
import io.github.kardx.dsl.BOOLEAN
import io.github.kardx.dsl.DocsFieldType
import io.github.kardx.dsl.ENUM
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.VARIES
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.isSubclassOf

/**
 * Kotlin [KType]을 문서 타입([DocsFieldType])으로 변환한다.
 *
 * `Optional<T>` 와 `@JvmInline value class` 는 내부 타입으로 풀어서 판정한다.
 *
 * | 타입 | 결과 |
 * |---|---|
 * | `String`, `Char`, `ByteArray`(base64), `CharArray`, `UUID`, `URI`, `java.time.*`, `Date` 등 | STRING |
 * | `Number` 하위 타입 (`Int`, `Long`, `BigDecimal` …) | NUMBER |
 * | `Boolean` | BOOLEAN |
 * | Enum | ENUM |
 * | 배열, `Collection` 하위 타입 | ARRAY |
 * | `Map` 하위 타입, data class, 일반 클래스, Kotlin `object`, `Pair`, `Triple` | OBJECT |
 * | `Any`, 타입 파라미터, Jackson 트리(`JsonNode`), 그 외 JDK 클래스 | VARIES |
 */
internal fun docsFieldTypeOf(type: KType): DocsFieldType {
    val classifier = unwrap(type).type.classifier as? KClass<*> ?: return VARIES

    return docsFieldTypeOf(classifier)
}

internal fun docsFieldTypeOf(k: KClass<*>): DocsFieldType = FIELD_TYPES.get(k.javaObjectType)

/**
 * 클래스별 [docsFieldTypeOf] 결과. 필드 추출이 요청마다 모든 프로퍼티 타입에 묻는데, `isSubclassOf` 는 상위 타입을 모두 훑는다.
 * `KClass` 는 원시 타입과 래퍼 타입(`int`·`Integer`)을 같은 클래스로 보므로 래퍼 타입을 키로 쓴다.
 */
private val FIELD_TYPES = object : ClassValue<DocsFieldType>() {
    override fun computeValue(type: Class<*>): DocsFieldType = classify(type.kotlin)
}

private fun classify(k: KClass<*>): DocsFieldType = when {
    // JsonNode 는 Iterable 이라 컬렉션 판정보다 먼저 본다
    isJacksonTree(k) -> VARIES

    k.java.isEnum || k.isSubclassOf(Enum::class) -> ENUM

    // Jackson 은 ByteArray 를 base64 문자열로, CharArray 를 문자열로 쓴다
    k == String::class || k == Char::class || k == ByteArray::class || k == CharArray::class -> STRING

    k == Boolean::class -> BOOLEAN

    k.isSubclassOf(Number::class) -> NUMBER

    k.java.isArray -> ARRAY

    // Path 는 Iterable<Path> 지만 Jackson 은 문자열로 쓴다
    isStringLike(k) -> STRING

    k.isSubclassOf(Collection::class) || k.isSubclassOf(Iterable::class) -> ARRAY

    k.isSubclassOf(Map::class) -> OBJECT

    k == Any::class -> VARIES

    k == Pair::class || k == Triple::class -> OBJECT

    isPlatformClass(k) -> VARIES

    else -> OBJECT
}

/** 추출기가 타입을 다루는 방식. */
internal sealed interface Shape {
    /** 하위 필드가 없는 값 */
    data class Leaf(val type: DocsFieldType) : Shape

    /** 프로퍼티를 재귀 추출하는 객체 */
    data class Nested(val kClass: KClass<*>) : Shape

    /** 배열. 원소 타입을 알 수 없으면 [element] 는 null */
    data class Array(val element: KType?) : Shape

    /** 키가 정해지지 않은 객체 (`Map`) */
    data object MapLike : Shape

    /** 구조를 알 수 없는 값 (`JsonNode`, `Any`). 객체·배열·스칼라 어느 것이든 될 수 있어 VARIES subsection 으로 문서화한다 */
    data object Opaque : Shape
}

/**
 * 추출기가 [type] 을 다룰 방식.
 * 값의 구조를 타입으로 알 수 없는 `Any`·풀리지 않은 타입 파라미터·Jackson 트리는 [Shape.Opaque] 이고,
 * 프로퍼티가 없는 Kotlin `object` 는 빈 객체([Shape.Leaf])다.
 */
internal fun shapeOf(type: KType): Shape {
    val k = type.classifier as? KClass<*> ?: return Shape.Opaque

    return when (val docsType = docsFieldTypeOf(k)) {
        ARRAY -> Shape.Array(elementTypeOf(type, k))

        OBJECT -> when {
            k.isSubclassOf(Map::class) -> Shape.MapLike
            k.objectInstance != null && PropertyIntrospector.properties(k).isEmpty() -> Shape.Leaf(OBJECT)
            else -> Shape.Nested(k)
        }

        VARIES -> if (isJacksonTree(k) || k == Any::class) Shape.Opaque else Shape.Leaf(VARIES)

        else -> Shape.Leaf(docsType)
    }
}

/** 값 [type] 의 REST Docs 타입. 숫자·불리언 `@JsonValue` Enum 은 그 JSON 타입이다 ([enumDocsFieldType]). */
internal fun valueDocsType(shape: Shape.Leaf, type: KType): DocsFieldType =
    enumClassOf(type)?.let(::enumDocsFieldType) ?: shape.type

/** 값이 아닌 모양의 REST Docs 타입. 제약 키워드를 고를 때만 쓴다. */
internal fun containerDocsType(shape: Shape): DocsFieldType = when (shape) {
    is Shape.Array -> ARRAY
    Shape.Opaque -> VARIES
    else -> OBJECT
}

/**
 * 선언 타입의 프로퍼티만으로는 하위 필드를 다 알 수 없는 타입.
 * - 인터페이스·추상 클래스: 실제 값이 하위 타입이고 Jackson 은 실제 타입의 프로퍼티를 모두 내보낸다. sealed 타입은 하위 타입을 모두 알므로 아니다
 * - `@JsonAnyGetter` 가 있는 클래스: 맵의 항목이 정해지지 않은 키로 펼쳐진다
 */
internal fun isOpenType(k: KClass<*>): Boolean =
    ((k.java.isInterface || k.isAbstract) && !k.isSealed) || JacksonAnnotations.hasAnyGetter(k.java)

/**
 * 배열·컬렉션의 원소 타입. 타입 인자가 없는 컬렉션 하위 클래스(`class UserList : ArrayList<User>()`)는 상위 타입 `Iterable<T>` 의
 * 바인딩에서 찾는다. 원시 배열이거나 찾지 못하면 null. 자기 자신을 원소로 갖는 타입(`class Node : Iterable<Node>`)도 null 이다
 * (원소를 따라가면 끝나지 않는다).
 */
private fun elementTypeOf(type: KType, k: KClass<*>): KType? {
    if (k.java.isArray && k.java.componentType.isPrimitive) {
        return null
    }

    if (type.arguments.isNotEmpty() || k.java.isArray) {
        return type.arguments.firstOrNull()?.type
    }

    return k.allSupertypes.firstOrNull { it.classifier == Iterable::class }
        ?.arguments
        ?.firstOrNull()
        ?.type
        ?.substitute(bindingsOf(type))
        ?.takeUnless { it.classifier is KTypeParameter || it.classifier == k }
}

private fun isStringLike(k: KClass<*>): Boolean =
    k.java.name.startsWith("java.time.") || STRING_LIKE_TYPES.any { it.isAssignableFrom(k.java) }

/** Jackson 의 트리·토큰 타입 (`JsonNode`, `ObjectNode`, `TreeNode` …). 값의 구조를 타입으로 알 수 없다. */
private fun isJacksonTree(k: KClass<*>): Boolean = JACKSON_PACKAGES.any { k.java.name.startsWith(it) }

private fun isPlatformClass(k: KClass<*>): Boolean {
    val name = k.java.name

    return PLATFORM_PACKAGES.any { name.startsWith(it) }
}

/**
 * Jackson 이 JSON 문자열로 쓰는 JDK 타입. 하위 타입(`java.sql.Timestamp`, `GregorianCalendar`, 실제 `Path` 구현 …)도 포함한다.
 * `ByteBuffer` 는 base64 문자열이다.
 */
private val STRING_LIKE_TYPES: List<Class<*>> = listOf(
    UUID::class.java,
    Locale::class.java,
    Currency::class.java,
    TimeZone::class.java,
    Date::class.java,
    Calendar::class.java,
    Pattern::class.java,
    URI::class.java,
    URL::class.java,
    InetAddress::class.java,
    InetSocketAddress::class.java,
    Path::class.java,
    ByteBuffer::class.java,
    File::class.java,
    Class::class.java,
)

private val PLATFORM_PACKAGES = listOf("java.", "javax.", "jdk.", "sun.", "kotlin.")
private val JACKSON_PACKAGES = listOf("com.fasterxml.jackson.", "tools.jackson.")
