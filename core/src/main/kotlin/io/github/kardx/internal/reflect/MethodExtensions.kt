package io.github.kardx.internal.reflect

import java.lang.reflect.Method

/** 메서드와 첫 파라미터의 어노테이션 (`@set:`, `@setparam:`, Java setter). 메서드가 없으면 빈 목록. */
internal fun Method?.annotationsWithParameters(): List<Annotation> = this?.let {
    it.annotations.toList() +
        it.parameterAnnotations.firstOrNull()
            .orEmpty()
            .toList()
}.orEmpty()
