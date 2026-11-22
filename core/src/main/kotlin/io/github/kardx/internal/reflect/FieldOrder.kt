package io.github.kardx.internal.reflect

import java.lang.reflect.Modifier

/** 상위 클래스부터 선언 순서대로 매긴 인스턴스 필드 순서. */
internal fun declaredFieldOrder(c: Class<*>): Map<String, Int> = generateSequence(c) { it.superclass }
    .toList()
    .asReversed()
    .flatMap { it.declaredFields.toList() }
    .filterNot { Modifier.isStatic(it.modifiers) }
    .withIndex()
    .associate { it.value.name to it.index }
