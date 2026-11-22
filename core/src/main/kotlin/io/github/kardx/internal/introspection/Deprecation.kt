package io.github.kardx.internal.introspection

import io.github.kardx.internal.reflect.hasName

/**
 * 프로퍼티에 붙은 `@Deprecated` 의 이유. Kotlin `@Deprecated` 는 message, Java `@Deprecated` 는 빈 문자열이다.
 * 붙어 있지 않으면 null. 추출한 필드·파라미터에 [io.github.kardx.dsl.DEPRECATED_ATTRIBUTE] 로 남는다.
 */
internal fun deprecationOf(annotations: List<Annotation>): String? = annotations.firstNotNullOfOrNull {
    when {
        it is Deprecated -> it.message
        it.hasName(JAVA_DEPRECATED) -> ""
        else -> null
    }
}

private const val JAVA_DEPRECATED = "java.lang.Deprecated"
