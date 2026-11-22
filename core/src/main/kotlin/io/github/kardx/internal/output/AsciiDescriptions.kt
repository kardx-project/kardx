package io.github.kardx.internal.output

import io.github.kardx.annotation.AsciiDescription
import java.lang.reflect.AnnotatedElement

/**
 * `@AsciiDescription` 값. 문서·섹션 제목 뒤에 붙는다. `autoBracket` 이면 괄호로 감싼다.
 * 어노테이션이 없거나 값이 비어 있거나 공백뿐이면 null (괄호도 붙이지 않는다).
 */
internal fun AnnotatedElement.asciiDescription(): String? {
    val annotation = getAnnotation(AsciiDescription::class.java) ?: return null

    if (annotation.value.isBlank()) return null

    return if (annotation.autoBracket) {
        annotation.bracket.join(annotation.value)
    } else {
        annotation.value
    }
}
