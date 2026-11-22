package io.github.kardx.internal.introspection

import io.github.kardx.internal.i18n.DocsText

/**
 * 라이브러리가 아는 JSON 모양(Spring Data 페이지, `ProblemDetail`)의 필드 설명. [@Description][io.github.kardx.annotation.Description]
 * 과 달리 문구를 쓰는 시점의 언어로 고른다 ([PropertyMeta.description]). 설명 함수·어노테이션이 정한 설명이 먼저다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
internal annotation class BuiltInDescription(val text: DocsText)
