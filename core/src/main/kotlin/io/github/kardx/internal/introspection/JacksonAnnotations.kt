package io.github.kardx.internal.introspection

import io.github.kardx.dsl.BOOLEAN
import io.github.kardx.dsl.DocsFieldType
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.STRING
import io.github.kardx.internal.reflect.hasName
import io.github.kardx.internal.reflect.memberValue

/**
 * Jackson 어노테이션을 이름으로 읽는다. Jackson 2/3 모두 `com.fasterxml.jackson.annotation` 패키지를 쓰고,
 * 라이브러리는 Jackson 에 컴파일 의존하지 않으므로 리플렉션으로 값을 꺼낸다.
 */
internal object JacksonAnnotations {
    const val JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore"
    const val JSON_IGNORE_PROPERTIES = "com.fasterxml.jackson.annotation.JsonIgnoreProperties"
    const val JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty"
    const val JSON_UNWRAPPED = "com.fasterxml.jackson.annotation.JsonUnwrapped"
    const val JSON_TYPE_INFO = "com.fasterxml.jackson.annotation.JsonTypeInfo"
    const val JSON_TYPE_NAME = "com.fasterxml.jackson.annotation.JsonTypeName"
    const val JSON_SUB_TYPES = "com.fasterxml.jackson.annotation.JsonSubTypes"
    const val JSON_FORMAT = "com.fasterxml.jackson.annotation.JsonFormat"
    const val JSON_ANY_GETTER = "com.fasterxml.jackson.annotation.JsonAnyGetter"

    /** [annotations] 중 이름이 [name] 인 첫 어노테이션. */
    fun find(annotations: Iterable<Annotation>, name: String): Annotation? =
        annotations.firstOrNull { it.hasName(name) }

    /** 클래스와 상위 클래스·인터페이스에서 이름이 [name] 인 첫 어노테이션. */
    fun findOnHierarchy(c: Class<*>, name: String): Annotation? = hierarchy(c)
        .firstNotNullOfOrNull { type -> find(type.annotations.asIterable(), name) }

    /** 클래스, 상위 클래스, 그들의 인터페이스. */
    fun hierarchy(c: Class<*>): Sequence<Class<*>> = generateSequence(c) { it.superclass }
        .flatMap { sequenceOf(it) + it.interfaces.asSequence() }

    /** 어노테이션 멤버 값. 없으면 null. */
    fun value(annotation: Annotation, member: String = "value"): Any? = annotation.memberValue(member)

    /** Enum 멤버의 상수 이름. */
    fun enumName(annotation: Annotation, member: String): String? = (value(annotation, member) as? Enum<*>)?.name

    fun isJsonIgnored(annotations: List<Annotation>): Boolean =
        annotations.any { it.hasName(JSON_IGNORE) && (value(it) as? Boolean ?: true) }

    /** `@JsonProperty("name")` 의 이름. 비어 있으면 null. */
    fun jsonPropertyName(annotations: List<Annotation>): String? = find(annotations, JSON_PROPERTY)
        ?.let { value(it) as? String }
        ?.takeIf { it.isNotEmpty() }

    /** `@JsonProperty(access = ...)` 의 상수 이름 (`READ_ONLY`, `WRITE_ONLY`, `READ_WRITE`, `AUTO`). */
    fun jsonPropertyAccess(annotations: List<Annotation>): String? =
        find(annotations, JSON_PROPERTY)?.let { enumName(it, "access") }

    /** `@JsonUnwrapped` (enabled 가 false 면 null). */
    fun jsonUnwrapped(annotations: List<Annotation>): JsonUnwrapped? {
        val annotation = find(annotations, JSON_UNWRAPPED) ?: return null

        if (value(annotation, "enabled") == false) {
            return null
        }

        val prefix = value(annotation, "prefix") as? String ?: ""
        val suffix = value(annotation, "suffix") as? String ?: ""

        return JsonUnwrapped(prefix, suffix)
    }

    /**
     * 프로퍼티의 `@JsonFormat(shape = ...)` 가 바꾸는 JSON 값 타입. `NUMBER`·`NUMBER_INT`·`NUMBER_FLOAT` → NUMBER,
     * `STRING` → STRING, `BOOLEAN` → BOOLEAN. 그 밖의 shape(`OBJECT`, `ARRAY`, `ANY`, `NATURAL`)이나 어노테이션이 없으면 null.
     */
    fun jsonFormatType(annotations: List<Annotation>): DocsFieldType? =
        when (find(annotations, JSON_FORMAT)?.let { enumName(it, "shape") }) {
            "NUMBER", "NUMBER_INT", "NUMBER_FLOAT" -> NUMBER
            "STRING" -> STRING
            "BOOLEAN" -> BOOLEAN
            else -> null
        }

    /** 프로퍼티의 `@JsonFormat(pattern = ...)`. 비어 있거나 어노테이션이 없으면 null. */
    fun jsonFormatPattern(annotations: List<Annotation>): String? = find(annotations, JSON_FORMAT)
        ?.let { value(it, "pattern") as? String }
        ?.takeIf { it.isNotEmpty() }

    /** `@JsonAnyGetter` 가 붙은 선언인지. 그 맵의 항목은 이름 없이 부모 객체에 펼쳐진다. */
    fun isAnyGetter(annotations: List<Annotation>): Boolean = find(annotations, JSON_ANY_GETTER)
        ?.let { value(it, "enabled") != false } == true

    /** [c] 나 상위 타입이 `@JsonAnyGetter` 메서드를 가져, 정해지지 않은 키가 더 나올 수 있는지. */
    fun hasAnyGetter(c: Class<*>): Boolean = c.methods.any { isAnyGetter(it.annotations.toList()) }

    /** 클래스와 상위 타입의 `@JsonIgnoreProperties`. */
    fun ignoredProperties(c: Class<*>): IgnoredProperties {
        val annotations = hierarchy(c)
            .flatMap { type -> type.annotations.asSequence() }
            .filter { it.hasName(JSON_IGNORE_PROPERTIES) }
            .toList()

        return IgnoredProperties(
            names = annotations.flatMap {
                (value(it) as? Array<*>).orEmpty()
                    .filterIsInstance<String>()
            }.toSet(),
            allowGetters = annotations.any { value(it, "allowGetters") == true },
            allowSetters = annotations.any { value(it, "allowSetters") == true },
        )
    }
}

/**
 * `@JsonIgnoreProperties(value, allowGetters, allowSetters)`.
 * `allowGetters` 면 직렬화(응답)에는 남고, `allowSetters` 면 역직렬화(요청)에는 남는다.
 */
internal data class IgnoredProperties(val names: Set<String>, val allowGetters: Boolean, val allowSetters: Boolean) {
    /** 목록에 있는 프로퍼티가 요청이나 응답 한쪽에는 남는지. */
    val keepsOneSide: Boolean get() = allowGetters || allowSetters
}
