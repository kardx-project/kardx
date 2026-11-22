package io.github.kardx.config

import io.github.kardx.internal.reflect.hasName
import io.github.kardx.internal.reflect.memberValue
import kotlin.reflect.KClass

/**
 * 필드 값이 마스킹되어 나간다는 표시로 읽을 어노테이션. [MaskingSettings.annotations] 의 원소다.
 * [@Masked][io.github.kardx.annotation.Masked] 는 설정과 상관없이 항상 읽는다.
 *
 * 이 어노테이션이 붙은 본문 필드는 AsciiDoc 필드 표에 `*Masked*`, OpenAPI·AsyncAPI 스키마에 `x-masked: true` 로 표시된다.
 * [attribute] 를 주면 그 속성 값(String, enum 이면 상수 이름)이 규칙 설명(`*Masked*: <값>`, `x-masked-rule`)이 된다.
 * 값을 바꾸지 않는 문서 표시일 뿐이다.
 *
 * - [parse]: yml(`kardx.masked-annotations`)과 같은 문법 (`FQCN`, `FQCN#attribute`)
 * - [of]: 어노테이션 클래스와 속성 이름
 *
 * 어노테이션 클래스에 컴파일 의존하지 않아도 되도록 속성 값은 리플렉션으로 읽는다.
 *
 * @property annotationClass 마스킹을 나타내는 어노테이션
 * @property attribute 규칙 설명을 읽을 속성 이름. null 이면 규칙 없이 표시만 한다
 */
public class MaskedAnnotation private constructor(
    public val annotationClass: KClass<out Annotation>,
    public val attribute: String?,
) {
    /**
     * [annotations] 에 이 어노테이션이 붙었으면 규칙 설명, 규칙이 없거나 비어 있으면 빈 문자열.
     * 붙어 있지 않으면 null.
     */
    internal fun read(annotations: List<Annotation>): String? {
        val found = annotations.firstOrNull { it.hasName(annotationClass.java.name) }
            ?: return null

        return attribute?.let { rule(found.memberValue(it)) }.orEmpty()
    }

    /** 같은 어노테이션의 같은 속성을 읽으면 같다. */
    override fun equals(other: Any?): Boolean =
        other is MaskedAnnotation && annotationClass == other.annotationClass && attribute == other.attribute

    override fun hashCode(): Int = HASH_MULTIPLIER * annotationClass.hashCode() + attribute.hashCode()

    override fun toString(): String = annotationClass.java.name + attribute?.let { "#$it" }.orEmpty()

    /** [MaskedAnnotation] 을 만드는 함수와 설정 문자열 파싱. */

    public companion object {
        private const val HASH_MULTIPLIER = 31

        /**
         * [annotationClass] 를 마스킹 표시로 읽는다. [attribute] 를 주면 그 속성 값이 규칙 설명이 된다.
         *
         * @throws IllegalArgumentException [attribute] 가 String 이나 enum 을 돌려주는 속성이 아닐 때
         */
        public fun of(annotationClass: KClass<out Annotation>, attribute: String? = null): MaskedAnnotation {
            val type = annotationClass.java

            require(attribute == null || hasRuleAttribute(type, attribute)) {
                "annotation '${type.name}' has no String or enum attribute '$attribute'"
            }

            return MaskedAnnotation(annotationClass, attribute)
        }

        /**
         * yml 과 같은 문법으로 만든다.
         * - `FQCN`: 붙어 있으면 마스킹됨 (규칙 설명 없음)
         * - `FQCN#attribute`: 그 속성 값(String, enum 이면 상수 이름)을 규칙 설명으로
         *
         * @throws IllegalArgumentException 클래스가 클래스패스에 없거나, 어노테이션이 아니거나, 속성이 없을 때
         */
        public fun parse(spec: String): MaskedAnnotation {
            val parsed = AnnotationSpec.parse(spec)

            return of(AnnotationSpec.loadAnnotation(parsed.name, "'${parsed.name}'"), parsed.attribute)
        }

        /** 어노테이션이 직접 선언한, 인자 없이 String 이나 enum 을 돌려주는 속성인지. */
        private fun hasRuleAttribute(type: Class<*>, name: String): Boolean = type.declaredMethods.any {
            it.name == name && it.parameterCount == 0 && (it.returnType == String::class.java || it.returnType.isEnum)
        }

        private fun rule(value: Any?): String = when (value) {
            is String -> value.trim()
            is Enum<*> -> value.name
            else -> ""
        }
    }
}
