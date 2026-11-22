package io.github.kardx.config

import io.github.kardx.ExperimentalRestDocsApi

/**
 * 프로퍼티 설명을 정하는 규칙. [RestDocsSettings] 의 `descriptions.resolver` 로 정한다. `null` 을 돌려주면 설명이 없다.
 *
 * 기본값 [DEFAULT] 는 다음 단계를 차례로 보고 처음 나온 값을 쓴다 (비어 있거나 공백뿐인 값은 건너뛴다).
 * 1. [DESCRIPTION_ANNOTATION]: [@Description][io.github.kardx.annotation.Description]
 * 2. [CONFIGURED_ANNOTATIONS]: 설정의 `descriptions.annotations` (`swagger`, `jackson`, FQCN …)
 * 3. [SOURCE_DOCS]: KDoc·Javadoc (실험 기능, `descriptions.sourceDocs`)
 *
 * 단계는 공개되어 있으므로 자기 규칙을 앞이나 뒤에 끼워 조합한다. [orElse] 는 앞 규칙이 설명을 못 찾을 때 뒤 규칙을 본다.
 *
 * ```kotlin
 * descriptions {
 *     // 내 규칙을 먼저 보고, 없으면 기본 단계 (@Description → 어노테이션 → KDoc)
 *     resolver = DescriptionResolver { it.findAnnotation<Schema>()?.description } orElse DescriptionResolver.DEFAULT
 *     // 단계를 골라 조합한다 (KDoc 은 쓰지 않는다)
 *     resolver = DescriptionResolver.firstOf(DescriptionResolver.DESCRIPTION_ANNOTATION, myResolver)
 * }
 * ```
 */
public fun interface DescriptionResolver {
    /** [context] 프로퍼티의 설명. 없으면 null. */
    public fun resolve(context: PropertyContext): String?

    /** 기본 단계와 조합 함수. */
    public companion object {
        /** [@Description][io.github.kardx.annotation.Description] 의 값. */
        public val DESCRIPTION_ANNOTATION: DescriptionResolver = DescriptionResolvers.DescriptionAnnotationStep

        /** 지금 적용 중인 설정의 `descriptions.annotations` 를 순서대로 읽은 값. */
        public val CONFIGURED_ANNOTATIONS: DescriptionResolver = DescriptionResolvers.ConfiguredAnnotationsStep

        /**
         * KDoc·Javadoc 설명. Gradle 플러그인의 `sourceDocs` 색인이 있고 설정의 `descriptions.sourceDocs` 가 켜져 있을 때만 값이
         * 있다. 실험 기능이다.
         */
        @ExperimentalRestDocsApi
        public val SOURCE_DOCS: DescriptionResolver = DescriptionResolvers.SourceDocsStep

        /** 기본 규칙: [DESCRIPTION_ANNOTATION] → [CONFIGURED_ANNOTATIONS] → [SOURCE_DOCS]. */
        public val DEFAULT: DescriptionResolver = DescriptionResolvers.Default

        /** [resolvers] 를 차례로 보고 처음 나온 설명 (비어 있거나 공백뿐인 값은 건너뛴다). */
        public fun firstOf(vararg resolvers: DescriptionResolver): DescriptionResolver =
            DescriptionResolvers.FirstOf(resolvers.toList())
    }
}

/** 이 규칙이 설명을 못 찾으면(null·빈 값) [next] 를 본다. `myResolver orElse DescriptionResolver.DEFAULT` */
public infix fun DescriptionResolver.orElse(next: DescriptionResolver): DescriptionResolver =
    DescriptionResolver.firstOf(this, next)
