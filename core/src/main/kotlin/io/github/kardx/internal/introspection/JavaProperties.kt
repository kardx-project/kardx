package io.github.kardx.internal.introspection

import io.github.kardx.internal.reflect.annotationsWithParameters
import io.github.kardx.internal.reflect.declaredFieldOrder
import java.beans.Introspector
import java.lang.reflect.AnnotatedType
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type

/**
 * Kotlin 메타데이터가 없는 Java 클래스의 프로퍼티 ([PropertyIntrospector] 가 쓴다).
 *
 * - record: record component
 * - bean: public getter(`getX`/`isX`)와 public 필드. 이름 규칙은 Jackson 2(`getURL` → `url`) 와 3(`URL`) 을 따른다
 * - null 가능 여부는 [JavaNullability] 규칙을 따른다 (어노테이션 없는 참조 타입은 nullable)
 */
internal object JavaProperties {
    fun ofRecord(c: Class<*>): List<PropertyMeta> = c.recordComponents.map { component ->
        // 어노테이션을 더 모으려는 것일 뿐이라 같은 이름의 필드를 찾지 못하면(NoSuchFieldException) 없는 것으로 본다
        val field = runCatching { c.getDeclaredField(component.name) }.getOrNull()
        val annotations = component.annotations.toList() +
            component.accessor.annotations.toList() +
            field?.annotations.orEmpty().toList()

        property(c, component.name, component.genericType, component.type, component.annotatedType, annotations)
    }

    /** @param legacyNaming Jackson 2 이름 규칙을 쓸지 */
    fun ofBean(c: Class<*>, legacyNaming: Boolean): List<PropertyMeta> {
        val fromGetters = getterProperties(c, legacyNaming)
        val getterNames = fromGetters.map { it.name }.toSet()
        val fromFields = c.fields.filter { it.isInstance() && it.name !in getterNames }
            .map { property(c, it.name, it.genericType, it.type, it.annotatedType, it.annotations.toList()) }
        val fieldOrder = declaredFieldOrder(c)

        return (fromGetters + fromFields).sortedBy { fieldOrder[it.name] ?: Int.MAX_VALUE }
    }

    private fun getterProperties(c: Class<*>, legacyNaming: Boolean): List<PropertyMeta> {
        val fieldsByName = generateSequence(c) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { it.isInstance() }
            .associateBy { it.name }
        val publicFields = c.fields.filter { it.isInstance() }
            .map { it.name }
            .toSet()
        val setters = c.methods.filter { it.name.startsWith(SET) && it.parameterCount == 1 && it.isInstance() }
            .distinctBy { it.name }
            .associateBy { it.name }
        // setter 가 하나라도 있으면 setter 로 채우는 bean 이다. 그런 bean 에서 setter 없는 getter 는 요청으로 받지 않는다.
        // (setter 가 전혀 없는 불변 클래스는 생성자로 받으므로 모든 프로퍼티를 요청에 둔다)
        val hasSetters = setters.isNotEmpty()

        return c.methods.filter(::isGetter)
            .map { getter ->
                val base = getter.name.removePrefix(if (getter.name.startsWith(IS)) IS else GET)
                val name = if (legacyNaming) legacyMangle(base) else Introspector.decapitalize(base)
                val setter = setters[SET + base]
                val annotations = getter.annotations.toList() +
                    fieldsByName[name]?.annotations.orEmpty().toList() +
                    setter.annotationsWithParameters()
                val readOnly = hasSetters && setter == null && name !in publicFields

                property(c, name, getter.genericReturnType, getter.returnType, getter.annotatedReturnType, annotations)
                    .copy(readOnly = readOnly)
            }
            .distinctBy { it.name }
    }

    /** 선언 타입에 [JavaNullability] 를 적용한 프로퍼티. 타입 사용 어노테이션(`@Nullable String`)도 null 판단에 쓴다. */
    private fun property(
        owner: Class<*>,
        name: String,
        genericType: Type,
        rawType: Class<*>,
        annotatedType: AnnotatedType,
        annotations: List<Annotation>,
    ): PropertyMeta {
        val type = JavaNullability.apply(genericType.toKType(), rawType, annotations + annotatedType.annotations, owner)

        return PropertyMeta(name, type, annotations, null, elementAnnotations = elementAnnotations(annotatedType))
    }

    /** Jackson 2 기본 이름 규칙: 앞의 연속된 대문자를 모두 소문자로 (`URL` → `url`, `XCoordinate` → `xcoordinate`). */
    private fun legacyMangle(base: String): String {
        val upperPrefix = base.takeWhile { it.lowercaseChar() != it }

        return upperPrefix.map { it.lowercaseChar() }.joinToString("") + base.drop(upperPrefix.length)
    }

    private fun isGetter(m: Method): Boolean {
        val accessor = m.parameterCount == 0 && m.isInstance() && m.declaringClass != Any::class.java &&
            m.returnType != Void.TYPE && !m.isBridge && !m.isSynthetic

        return accessor && (m.name.hasPropertyAfter(GET) || (m.name.hasPropertyAfter(IS) && m.returnsBoolean()))
    }

    private fun String.hasPropertyAfter(prefix: String): Boolean = startsWith(prefix) && length > prefix.length

    private fun Method.returnsBoolean(): Boolean =
        returnType == Boolean::class.javaPrimitiveType || returnType == Boolean::class.javaObjectType

    private fun Method.isInstance(): Boolean = !Modifier.isStatic(modifiers)

    private fun Field.isInstance(): Boolean = !Modifier.isStatic(modifiers)

    private const val GET = "get"
    private const val SET = "set"
    private const val IS = "is"
}
