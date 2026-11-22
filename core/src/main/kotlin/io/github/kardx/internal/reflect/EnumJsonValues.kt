package io.github.kardx.internal.reflect

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type

/**
 * Enum 상수가 JSON 으로 직렬화되는 값. Jackson 과 같은 순서로 정한다.
 * `@JsonValue` 가 붙은 메서드·필드 값 → 상수의 `@JsonProperty("...")` → 상수의 kotlinx `@SerialName("...")` → 상수 이름.
 * `@SerialName` 은 kotlinx.serialization 으로 직렬화하는 Enum 에만 붙이므로 설정과 상관없이 읽는다.
 * `@Deprecated` 상수도 JSON 값이 될 수 있으므로 포함한다.
 *
 * DSL(`enumOf`)과 필드 추출기가 함께 쓰므로 어느 쪽에도 의존하지 않는 이 패키지에 둔다.
 */
internal fun enumJsonValues(enumClass: Class<*>): List<String> {
    val jsonValue = jsonValueMember(enumClass)

    return enumClass.enumConstants.orEmpty()
        .map { constant ->
            val name = (constant as Enum<*>).name

            // @JsonValue 메서드가 예외를 던지면 Jackson 의 다음 규칙(@JsonProperty, 상수 이름)으로 넘어간다
            jsonValue?.let { runCatching { it(constant)?.toString() }.getOrNull() }
                ?: constantJsonProperty(enumClass, name)
                ?: name
        }
}

/** Enum 의 `@JsonValue` 메서드·필드가 돌려주는 값의 클래스. 없으면 null (상수 이름·`@JsonProperty` 문자열을 쓴다). */
internal fun enumJsonValueClass(enumClass: Class<*>): Class<*>? =
    jsonValueMethod(enumClass)?.returnType ?: jsonValueField(enumClass)?.type

/** 클래스의 `@JsonValue` 메서드나 필드. `enabled = false` 는 없는 것으로 본다. */
internal fun jsonValueMember(c: Class<*>): ((Any) -> Any?)? {
    val method = jsonValueMethod(c)

    if (method != null) {
        return { target -> method.invoke(target) }
    }

    val field = jsonValueField(c)?.apply { isAccessible = true }

    return field?.let { f -> { target -> f.get(target) } }
}

/** `@JsonValue` 메서드의 반환 타입. 없으면 null. Enum 은 값 목록에서 따로 처리하므로 제외한다. */
internal fun jsonValueType(c: Class<*>): Type? {
    if (c.isEnum) return null

    return jsonValueMethod(c)?.genericReturnType ?: jsonValueField(c)?.genericType
}

private fun jsonValueMethod(c: Class<*>): Method? = JSON_VALUE_MEMBERS.get(c).method

private fun jsonValueField(c: Class<*>): Field? = JSON_VALUE_MEMBERS.get(c).field

/** 클래스의 `@JsonValue` 메서드, 없으면 필드. 필드 추출이 요청마다 모든 값 타입에 물으므로 클래스별로 한 번만 찾는다. */
private class JsonValueMembers(val method: Method?, val field: Field?)

private val JSON_VALUE_MEMBERS = object : ClassValue<JsonValueMembers>() {
    override fun computeValue(type: Class<*>): JsonValueMembers {
        val method = type.methods.firstOrNull {
            it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) && it.hasJsonValue()
        }
        val field = if (method == null) findJsonValueField(type) else null

        return JsonValueMembers(method, field)
    }
}

private fun findJsonValueField(c: Class<*>): Field? = generateSequence(c) { it.superclass }
    .flatMap { it.declaredFields.asSequence() }
    .firstOrNull { !Modifier.isStatic(it.modifiers) && it.hasJsonValue() }

private fun Method.hasJsonValue(): Boolean = annotations.any { it.isEnabledJsonValue() }

private fun Field.hasJsonValue(): Boolean = annotations.any { it.isEnabledJsonValue() }

private fun Annotation.isEnabledJsonValue(): Boolean = hasName(JSON_VALUE) && memberValue() as? Boolean != false

/** 상수 필드의 `@JsonProperty`, 없으면 `@SerialName` 값. 필드를 읽지 못하면(NoSuchFieldException·SecurityException) null. */
private fun constantJsonProperty(enumClass: Class<*>, name: String): String? {
    val annotations = runCatching { enumClass.getField(name) }.getOrNull()
        ?.annotations
        .orEmpty()

    return CONSTANT_NAME_ANNOTATIONS.firstNotNullOfOrNull { annotationName ->
        annotations.firstOrNull { it.hasName(annotationName) }
            ?.let { it.memberValue() as? String }
            ?.takeIf { it.isNotEmpty() }
    }
}

private const val JSON_VALUE = "com.fasterxml.jackson.annotation.JsonValue"
private const val JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty"
private val CONSTANT_NAME_ANNOTATIONS = listOf(JSON_PROPERTY, "kotlinx.serialization.SerialName")
