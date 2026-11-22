package io.github.kardx.config

import io.github.kardx.internal.reflect.loadClassCatching
import kotlin.reflect.KClass

/**
 * yml 목록의 한 항목 `이름` 또는 `이름#attribute`. [DescriptionAnnotation.parse] 와 [MaskedAnnotation.parse] 가 쓴다.
 *
 * @property name 어노테이션 FQCN (또는 별칭)
 * @property attribute `#` 뒤의 속성 이름. 없으면 null
 */
internal class AnnotationSpec private constructor(val name: String, val attribute: String?) {
    companion object {
        /** @throws IllegalArgumentException 이름이 비었거나 `#` 뒤가 비었을 때 */
        fun parse(spec: String): AnnotationSpec {
            val trimmed = spec.trim()
            val name = trimmed.substringBefore('#')
                .trim()
            val attribute = if ('#' in trimmed) {
                trimmed.substringAfter('#')
                    .trim()
            } else {
                null
            }

            require(name.isNotEmpty()) { "'$trimmed' has no annotation name" }
            require(attribute == null || attribute.isNotEmpty()) { "'$trimmed' has no attribute name after '#'" }

            return AnnotationSpec(name, attribute)
        }

        /**
         * [className] 어노테이션 클래스. [label] 은 오류 메시지에 쓸 이름(`'com.example.X'`).
         *
         * @throws IllegalArgumentException 클래스가 클래스패스에 없거나 어노테이션이 아닐 때
         */
        fun loadAnnotation(className: String, label: String): KClass<out Annotation> {
            val type = loadClassCatching(className)
                .getOrElse { throw IllegalArgumentException("class $label not found", it) }

            require(type.isAnnotation) { "class $label is not an annotation" }

            @Suppress("UNCHECKED_CAST")
            return (type as Class<out Annotation>).kotlin
        }
    }
}
