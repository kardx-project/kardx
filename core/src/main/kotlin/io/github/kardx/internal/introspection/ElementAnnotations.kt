package io.github.kardx.internal.introspection

import java.lang.reflect.AnnotatedArrayType
import java.lang.reflect.AnnotatedParameterizedType
import java.lang.reflect.AnnotatedType

/**
 * Java 선언 타입의 컬렉션 원소(맵은 값)·배열 원소에 붙은 타입 어노테이션 (`List<@NotBlank String>`).
 * Java 리플렉션 타입을 [kotlin.reflect.KType] 으로 바꾸면 사라지므로 [PropertyMeta.elementAnnotations] 로 따로 넘긴다.
 */
internal fun elementAnnotations(type: AnnotatedType): List<Annotation> = when (type) {
    is AnnotatedParameterizedType -> type.annotatedActualTypeArguments.lastOrNull()
        ?.annotations
        .orEmpty()
        .toList()

    is AnnotatedArrayType -> type.annotatedGenericComponentType.annotations.toList()

    else -> emptyList()
}
