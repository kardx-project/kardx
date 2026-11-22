package io.github.kardx.config

import io.github.kardx.internal.reflect.hasName
import io.github.kardx.internal.reflect.memberValue
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * 필드 설명을 읽어 올 어노테이션과 그 속성. [DescriptionSettings.annotations] 의 원소다.
 *
 * - [parse]: yml(`kardx.description-annotations`)과 같은 문법
 *   (`swagger`, `swagger#title`, `FQCN`, `FQCN#attribute`)
 * - [of]: 어노테이션 클래스와 속성 이름
 * - [from]: 어노테이션을 받아 설명을 돌려주는 함수 (타입 안전)
 *
 * 어노테이션 클래스에 컴파일 의존하지 않아도 되도록 속성 값은 리플렉션으로 읽는다.
 * public 이 아닌 어노테이션(Kotlin `private annotation class`, Java package-private `@interface`)도 읽는다.
 *
 * 반복 가능한 어노테이션(`@Repeatable`)을 한 곳에 두 번 이상 붙이면 컨테이너 어노테이션으로 묶인다.
 * 그때는 컨테이너 안의 어노테이션을 붙은 순서대로 보고 처음으로 비어 있지 않은 값을 쓴다.
 *
 * @property annotationClass 설명이 붙은 어노테이션
 * @property attribute 설명을 읽을 속성 이름. [from] 처럼 함수로 읽으면 null
 */
public class DescriptionAnnotation private constructor(
    public val annotationClass: KClass<out Annotation>,
    public val attribute: String?,
    private val reader: (Annotation) -> String?,
) {
    /** 이 어노테이션이 `@Repeatable` 이면 그 컨테이너 어노테이션의 클래스 이름. */
    private val containerName: String? = annotationClass.java.getAnnotation(java.lang.annotation.Repeatable::class.java)
        ?.value
        ?.java
        ?.name

    /**
     * [annotations] 에 붙은 이 어노테이션의 설명. 없거나 비어 있으면(공백뿐이면) null.
     * 반복해 붙여 컨테이너로 묶인 것은 펼쳐서 차례로 본다.
     */
    internal fun read(annotations: List<Annotation>): String? = annotations.asSequence()
        .flatMap(::unwrapContainer)
        .filter { it.hasName(annotationClass.java.name) }
        .mapNotNull(reader)
        .firstOrNull { it.isNotBlank() }

    private fun unwrapContainer(annotation: Annotation): Sequence<Annotation> {
        if (containerName == null || !annotation.hasName(containerName)) {
            return sequenceOf(annotation)
        }

        return (annotation.memberValue() as? Array<*>).orEmpty()
            .asSequence()
            .filterIsInstance<Annotation>()
    }

    /** 같은 어노테이션의 같은 속성을 읽으면 같다. 함수로 읽는 것은 자기 자신과만 같다. */
    override fun equals(other: Any?): Boolean {
        val sameAttribute = other is DescriptionAnnotation &&
            attribute != null &&
            annotationClass == other.annotationClass &&
            attribute == other.attribute

        return this === other || sameAttribute
    }

    override fun hashCode(): Int = HASH_MULTIPLIER * annotationClass.hashCode() + attribute.hashCode()

    override fun toString(): String = annotationClass.java.name + attribute?.let { "#$it" }.orEmpty()

    /** [DescriptionAnnotation] 을 만드는 함수와 설정 문자열 파싱. */

    public companion object {
        /** 속성 이름을 생략했을 때 차례로 찾는 속성. */
        private val DEFAULT_ATTRIBUTES = listOf("value", "description")
        private const val HASH_MULTIPLIER = 31

        /**
         * [annotationClass] 의 [attribute] 속성을 설명으로 읽는다.
         * [attribute] 가 null 이면 `value` 속성, 없으면 `description` 속성을 읽는다.
         *
         * @throws IllegalArgumentException 그런 String 속성이 없을 때
         */
        public fun of(annotationClass: KClass<out Annotation>, attribute: String? = null): DescriptionAnnotation {
            val type = annotationClass.java
            val name = attribute
                ?: DEFAULT_ATTRIBUTES.firstOrNull { hasStringAttribute(type, it) }
                ?: throw IllegalArgumentException(
                    "annotation '${type.name}' has no String attribute 'value' or 'description'",
                )

            require(hasStringAttribute(type, name)) { "annotation '${type.name}' has no String attribute '$name'" }

            return DescriptionAnnotation(annotationClass, name) { it.memberValue(name) as? String }
        }

        /**
         * 어노테이션 [annotationClass] 를 [read] 로 읽어 설명을 얻는다.
         * `DescriptionAnnotation.from(Schema::class) { it.description }`
         */
        public fun <A : Annotation> from(annotationClass: KClass<A>, read: (A) -> String?): DescriptionAnnotation =
            DescriptionAnnotation(annotationClass, null) { annotation ->
                annotationClass.safeCast(annotation)
                    ?.let(read)
            }

        /**
         * 어노테이션 [A] 를 [read] 로 읽어 설명을 얻는다. `DescriptionAnnotation.from<Schema> { it.description }`
         */
        public inline fun <reified A : Annotation> from(noinline read: (A) -> String?): DescriptionAnnotation =
            from(A::class, read)

        /**
         * yml 과 같은 문법으로 만든다.
         * - 별칭: `swagger` (`@Schema` 의 `description`), `jackson` (`@JsonPropertyDescription` 의 `value`)
         * - `별칭#attribute`: 그 어노테이션의 다른 속성 (`swagger#title`)
         * - `FQCN`: [of] 처럼 `value`, 없으면 `description` 속성
         * - `FQCN#attribute`: 그 속성
         *
         * @throws IllegalArgumentException 클래스가 클래스패스에 없거나, 어노테이션이 아니거나, 속성이 없을 때
         */
        public fun parse(spec: String): DescriptionAnnotation {
            val parsed = AnnotationSpec.parse(spec)
            val alias = Aliases.resolve(parsed.name)
            val className = alias?.substringBefore('#') ?: parsed.name
            val attribute = parsed.attribute ?: alias?.substringAfter('#')
            val label = alias?.let { "'$className' (alias '${parsed.name}')" } ?: "'$className'"

            return of(AnnotationSpec.loadAnnotation(className, label), attribute)
        }

        /** 어노테이션이 직접 선언한, 인자 없이 String 을 돌려주는 속성인지. `toString` 같은 상속 메서드는 아니다. */
        private fun hasStringAttribute(type: Class<*>, name: String): Boolean = type.declaredMethods.any {
            it.name == name && it.parameterCount == 0 && it.returnType == String::class.java
        }
    }
}

/**
 * [DescriptionAnnotation.parse] 가 받는 별칭. 값은 `FQCN#attribute` 이다. 별칭을 늘릴 때는 여기에만 더한다
 * (문서의 별칭 표와 `additional-spring-configuration-metadata.json` 의 hints 도 함께 고친다).
 */
private object Aliases {
    private val targets: Map<String, String> = mapOf(
        "swagger" to "io.swagger.v3.oas.annotations.media.Schema#description",
        "jackson" to "com.fasterxml.jackson.annotation.JsonPropertyDescription#value",
    )

    /** 별칭이면 그 `FQCN#attribute`, 아니면 null. 대소문자는 가리지 않는다. */
    fun resolve(name: String): String? = targets[name.lowercase()]
}
