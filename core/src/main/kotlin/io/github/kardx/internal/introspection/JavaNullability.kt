package io.github.kardx.internal.introspection

import java.lang.reflect.AnnotatedElement
import kotlin.reflect.KType
import kotlin.reflect.full.withNullability

/**
 * Java 선언(record component, getter, public 필드)의 null 가능 여부. Java 타입은 Kotlin 처럼 null 가능 여부를 타입에
 * 담지 않으므로 어노테이션으로 정한다. 어노테이션은 패키지와 상관없이 단순 이름으로 본다 (jspecify, Spring,
 * Jakarta, JSR-305, JetBrains 등).
 *
 * 1. 원시 타입(`long`, `boolean` …)은 null 이 될 수 없다
 * 2. `@Nullable`·`@CheckForNull` 이면 null 가능
 * 3. `@NonNull`·`@Nonnull`·`@NotNull` 이면 null 이 아니다 (Bean Validation `@NotNull` 포함)
 * 4. 클래스·바깥 클래스·패키지에 `@NullMarked`(jspecify)나 `@NonNullApi`(Spring 6)가 있으면 null 이 아니다
 * 5. 그 밖에는 null 가능. springdoc 처럼 어노테이션 없는 Java 프로퍼티는 required 로 보지 않는다
 */
internal object JavaNullability {
    /** [type] 을 [declaration] 의 어노테이션에 맞춰 nullable 로 바꾼다. [owner] 는 선언한 클래스. */
    fun apply(type: KType, javaType: Class<*>, declaration: List<Annotation>, owner: Class<*>): KType =
        type.withNullability(isNullable(javaType, declaration, owner))

    private fun isNullable(javaType: Class<*>, declaration: List<Annotation>, owner: Class<*>): Boolean {
        val names = declaration.map { it.annotationClass.java.simpleName }

        return when {
            javaType.isPrimitive -> false
            names.any { it in NULLABLE } -> true
            names.any { it in NON_NULL } -> false
            else -> !isNullMarked(owner)
        }
    }

    /** 가장 가까운 선언(클래스 → 바깥 클래스 → 패키지)의 `@NullMarked`·`@NullUnmarked` 를 따른다. */
    private fun isNullMarked(owner: Class<*>): Boolean {
        val scopes = generateSequence<AnnotatedElement>(owner) { (it as? Class<*>)?.enclosingClass }
            .plus(listOfNotNull(owner.`package`))

        val marker = scopes.flatMap { scope -> scope.annotations.asSequence() }
            .map { it.annotationClass.java.simpleName }
            .firstOrNull { it in NULL_MARKED || it == NULL_UNMARKED }

        return marker != null && marker != NULL_UNMARKED
    }

    private val NULLABLE = setOf("Nullable", "CheckForNull")
    private val NON_NULL = setOf("NonNull", "Nonnull", "NotNull")
    private val NULL_MARKED = setOf("NullMarked", "NonNullApi")
    private const val NULL_UNMARKED = "NullUnmarked"
}
