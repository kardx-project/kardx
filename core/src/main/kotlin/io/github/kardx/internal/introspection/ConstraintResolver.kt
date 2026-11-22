package io.github.kardx.internal.introspection

import io.github.kardx.internal.reflect.loadClassOrNull
import org.springframework.restdocs.constraints.Constraint
import org.springframework.restdocs.constraints.ResourceBundleConstraintDescriptionResolver

/**
 * 프로퍼티에 붙은 Bean Validation 제약조건 하나.
 *
 * @property name 어노테이션의 단순 이름 (`Size`, `NotBlank`). jakarta·javax·Hibernate Validator 를 같은 이름으로 다룬다
 * @property attributes 어노테이션 속성 값 (`min`, `max`, `regexp`, `value`, `inclusive` …)
 * @property text 문서 표의 Constraints 열에 나가는 설명 (`Size - Size must be between 0 and 50 inclusive`)
 * @property defaultGroup 검증 그룹이 없거나 `Default` 뿐인지. 그룹이 있으면 어느 요청에 적용되는지 알 수 없다
 */
internal class ResolvedConstraint(
    val name: String,
    val attributes: Map<String, Any?>,
    val text: String,
    val defaultGroup: Boolean,
)

/**
 * 프로퍼티에 붙은 Bean Validation 제약조건을 읽어 `"NotBlank - Must not be blank"` 형태의 설명을 만든다.
 *
 * - `@Constraint` 메타 어노테이션으로 판별하므로 사용자 정의 제약조건도 인식한다.
 * - `@Pattern.List` 같은 반복 컨테이너는 풀어서 처리한다.
 * - `message` 를 지정했으면 그 값을(`{min}` 등 속성 플레이스홀더 치환), 아니면 Spring REST Docs 의
 *   기본 설명을 쓴다. 설명을 찾지 못하면 어노테이션 이름만 남긴다.
 * - 검증 그룹(`groups`)이 있으면 이름 뒤에 그룹을 붙인다: `NotNull [OnCreate] - Must not be null`.
 * - jakarta.validation 이 클래스패스에 없으면 아무것도 반환하지 않는다.
 */
internal object ConstraintResolver {
    private val constraintMarker: Class<out Annotation>? by lazy {
        loadClassOrNull("jakarta.validation.Constraint", ConstraintResolver::class.java.classLoader)
            ?.takeIf { it.isAnnotation }
            ?.asSubclass(Annotation::class.java)
    }

    private val descriptionResolver by lazy { ResourceBundleConstraintDescriptionResolver() }

    fun resolve(annotations: List<Annotation>): List<ResolvedConstraint> {
        val marker = constraintMarker ?: return emptyList()

        return annotations.flatMap(::expandContainer)
            .filter { it.annotationClass.java.isAnnotationPresent(marker) }
            .distinct()
            .map(::resolved)
    }

    /** 표의 Constraints 열 내용. 제약이 없으면 null. */
    fun describe(constraints: List<ResolvedConstraint>): String? = constraints.takeIf { it.isNotEmpty() }
        ?.joinToString(SEPARATOR) { it.text }

    private fun resolved(annotation: Annotation): ResolvedConstraint {
        val attributes = attributes(annotation)
        val groups = (attributes["groups"] as? Array<*>).orEmpty()
            .filterIsInstance<Class<*>>()
            .filterNot { it.name in DEFAULT_GROUPS }
        val name = annotation.annotationClass.java.simpleName
        val label = if (groups.isEmpty()) {
            name
        } else {
            "$name [${groups.joinToString(", ") { it.simpleName }}]"
        }

        val text = "$label - ${messageOf(annotation, attributes) ?: ""}".removeSuffix(" - ")

        return ResolvedConstraint(name, attributes, text, groups.isEmpty())
    }

    private fun expandContainer(annotation: Annotation): List<Annotation> {
        val value = attributes(annotation)["value"]

        return if (value is Array<*> && value.isNotEmpty() && value.all { it is Annotation }) {
            value.filterIsInstance<Annotation>()
        } else {
            listOf(annotation)
        }
    }

    private fun messageOf(annotation: Annotation, attributes: Map<String, Any?>): String? {
        val message = attributes["message"] as? String

        if (message != null && !RESOURCE_KEY.matches(message)) {
            return PLACEHOLDER.replace(message) { match ->
                attributes[match.groupValues[1]]?.toString() ?: match.value
            }
        }

        // 기본 설명이 없는 제약조건(사용자 정의 등)이면 리소스 번들이 MissingResourceException 을 던진다
        return runCatching {
            descriptionResolver.resolveDescription(Constraint(annotation.annotationClass.java.name, attributes))
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** 어노테이션 속성 값. 읽지 못하는 속성(접근할 수 없는 어노테이션 등)은 null 로 둔다. */
    private fun attributes(annotation: Annotation): Map<String, Any?> =
        annotation.annotationClass.java.declaredMethods.filter { it.parameterCount == 0 }
            .associate { method -> method.name to runCatching { method.invoke(annotation) }.getOrNull() }

    private const val SEPARATOR = "\n\n"
    private val RESOURCE_KEY = Regex("""^\{[\w.]+}$""")
    private val PLACEHOLDER = Regex("""\{(\w+)}""")
    private val DEFAULT_GROUPS = setOf("jakarta.validation.groups.Default", "javax.validation.groups.Default")
}
