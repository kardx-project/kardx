package io.github.kardx.config

import io.github.kardx.annotation.Description
import io.github.kardx.internal.sourcedocs.SourceDocs

/**
 * [DescriptionResolver] 의 기본 단계와 조합. 단계마다 설정은 부를 때 [RestDocsConfiguration.current] 에서 읽는다
 * (`withSettings` 범위의 목록도 반영된다).
 */
internal object DescriptionResolvers {
    private val DESCRIPTION = DescriptionAnnotation.of(Description::class)

    /** `@Description` */
    object DescriptionAnnotationStep : DescriptionResolver {
        override fun resolve(context: PropertyContext): String? = DESCRIPTION.read(context.annotations)

        override fun toString(): String = "DescriptionResolver.DESCRIPTION_ANNOTATION"
    }

    /** 설정의 설명 어노테이션 목록 */
    object ConfiguredAnnotationsStep : DescriptionResolver {
        override fun resolve(context: PropertyContext): String? = RestDocsConfiguration.current.descriptions.annotations
            .firstNotNullOfOrNull { it.read(context.annotations) }

        override fun toString(): String = "DescriptionResolver.CONFIGURED_ANNOTATIONS"
    }

    /** KDoc·Javadoc */
    object SourceDocsStep : DescriptionResolver {
        override fun resolve(context: PropertyContext): String? = context.declaringClass?.let {
            SourceDocs.propertyDescription(
                RestDocsConfiguration.current.descriptions.sourceDocs,
                it,
                context.declaredName,
            )
        }

        override fun toString(): String = "DescriptionResolver.SOURCE_DOCS"
    }

    /** 기본 규칙 */
    object Default : DescriptionResolver {
        private val steps = FirstOf(listOf(DescriptionAnnotationStep, ConfiguredAnnotationsStep, SourceDocsStep))

        override fun resolve(context: PropertyContext): String? = steps.resolve(context)

        override fun toString(): String = "DescriptionResolver.DEFAULT"
    }

    /** 차례로 보고 처음 나온(비어 있지 않은) 설명 */
    class FirstOf(private val resolvers: List<DescriptionResolver>) : DescriptionResolver {
        override fun resolve(context: PropertyContext): String? = resolvers.firstNotNullOfOrNull { resolver ->
            resolver.resolve(context)?.takeIf { it.isNotBlank() }
        }

        override fun toString(): String = "DescriptionResolver.firstOf(${resolvers.joinToString()})"
    }
}
