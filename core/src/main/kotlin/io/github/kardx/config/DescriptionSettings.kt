package io.github.kardx.config

import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.annotation.Description
import kotlin.reflect.KClass

/**
 * 필드·파라미터 설명을 정하는 방식.
 * [RestDocsSettings.descriptions] 로 읽고 `descriptions { }` 블록으로 바꾼다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     descriptions { annotation("swagger") }
 * }
 * ```
 */
@RestDocsSettingsDsl
public class DescriptionSettings internal constructor() {
    /**
     * 프로퍼티 설명을 정하는 규칙. 기본값 [DescriptionResolver.DEFAULT] 는 [@Description][Description] 의 값, 없으면 [annotations] 를
     * 순서대로 찾은 값, 그것도 없으면 KDoc·Javadoc 설명([sourceDocs])이다 (비어 있거나 공백뿐인 값은 건너뛴다).
     * 바꾸면 이 규칙이 설명을 모두 정한다. 기본 단계는 [DescriptionResolver] 의 상수로 공개되어 있으므로 내 규칙과 조합해
     * `@Description`·어노테이션·KDoc 을 계속 쓸 수 있다.
     *
     * ```kotlin
     * // 내 규칙 → 없으면 기본 단계
     * resolver = DescriptionResolver { it.findAnnotation<Schema>()?.description } orElse DescriptionResolver.DEFAULT
     * // 단계를 골라 순서를 정한다
     * resolver = DescriptionResolver.firstOf(DescriptionResolver.DESCRIPTION_ANNOTATION, mySchemaResolver)
     * ```
     */
    public var resolver: DescriptionResolver = DescriptionResolver.DEFAULT

    /**
     * 설명을 읽을 어노테이션. 기본 규칙([DescriptionResolver.CONFIGURED_ANNOTATIONS] 단계)이 [@Description][Description] 다음에
     * 이 순서로 찾는다.
     * 비어 있거나 공백뿐인 값은 없는 것으로 보고 다음 어노테이션으로 넘어간다.
     * yml 로는 `kardx.description-annotations` (예: `swagger, jackson, com.example.ApiField#text`).
     *
     * ```kotlin
     * annotation("swagger")                       // yml 과 같은 문법: 별칭, FQCN, FQCN#attribute
     * annotation(ApiField::class, "text")         // 속성을 생략하면 value, 없으면 description
     * from<Schema> { it.description }             // 타입 안전하게
     * annotations = emptyList()                   // 목록을 통째로 바꾼다
     * ```
     */
    public var annotations: List<DescriptionAnnotation> = emptyList()
        set(value) {
            field = value.toList()
        }

    /**
     * KDoc·Javadoc 을 설명으로 쓸지 여부 (테스트 쪽 차단 스위치). 기본값 true.
     * **실험 기능** ([ExperimentalRestDocsApi]). Gradle 플러그인(`kardx { sourceDocs { enabled = true } }`, 기본 꺼짐)이
     * 소스의 KDoc·Javadoc 색인을 만들어 테스트 JVM 에 넘기면, 기본 규칙이 [@Description][Description]·[annotations] 다음
     * 마지막 단계([DescriptionResolver.SOURCE_DOCS])로 읽는다. Enum 상수 설명([ExtractionSettings.enumDescriptionProperty] 다음),
     * 핸들러 메서드 설명(OpenAPI operation 의 summary·description 기본값)에도 쓴다. 색인이 없으면(플러그인에서 끄거나 플러그인 없이 실행) 켜 두어도 아무 일도 하지 않는다. false 면 색인을 받아도
     * 쓰지 않는다. 어떤 항목을 색인에 넣을지는 플러그인의 항목별 스위치가 정한다. yml: `kardx.source-docs.enabled`.
     */
    @ExperimentalRestDocsApi
    public var sourceDocs: Boolean = true

    /** [spec] (`swagger`, `jackson`, `swagger#title`, `FQCN`, `FQCN#attribute`) 을 [annotations] 끝에 더한다. */
    public fun annotation(spec: String) {
        annotations = annotations + DescriptionAnnotation.parse(spec)
    }

    /** [annotationClass] 의 [attribute] (생략하면 `value`, 없으면 `description`) 를 [annotations] 끝에 더한다. */
    public fun annotation(annotationClass: KClass<out Annotation>, attribute: String? = null) {
        annotations = annotations + DescriptionAnnotation.of(annotationClass, attribute)
    }

    /** 어노테이션 [annotationClass] 를 [read] 로 읽도록 [annotations] 끝에 더한다. */
    public fun <A : Annotation> from(annotationClass: KClass<A>, read: (A) -> String?) {
        annotations = annotations + DescriptionAnnotation.from(annotationClass, read)
    }

    /** 어노테이션 [A] 를 [read] 로 읽도록 [annotations] 끝에 더한다. `from<Schema> { it.description }` */
    public inline fun <reified A : Annotation> from(noinline read: (A) -> String?) {
        from(A::class, read)
    }

    internal fun copy(): DescriptionSettings = DescriptionSettings().also {
        it.resolver = resolver
        it.annotations = annotations
        it.sourceDocs = sourceDocs
    }
}
