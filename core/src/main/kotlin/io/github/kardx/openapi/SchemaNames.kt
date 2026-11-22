package io.github.kardx.openapi

import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * 타입으로 OpenAPI 스키마 이름을 만든다. 제네릭은 springdoc 처럼 타입 인자 이름을 이어 붙인다.
 *
 * | 타입 | 이름 |
 * |---|---|
 * | `ErrorResponse` | `ErrorResponse` |
 * | `ApiResponse<TodoResponse>` | `ApiResponseTodoResponse` |
 * | `ApiResponse<List<TodoResponse>>` | `ApiResponseListTodoResponse` |
 *
 * 패키지는 이름에 들어가지 않는다. 이름이 같은데 구조가 다른 스키마(다른 패키지의 같은 이름 클래스, 요청과 응답의
 * 모양이 다른 클래스)는 Gradle 플러그인이 OpenAPI 를 만들기 전에 겹치지 않는 이름으로 바꾼다 (패키지 이름을 한 단계씩
 * 앞에 붙인 후보 순서).
 */
public object SchemaNames {
    /** 타입 인자까지 이어 붙인 이름. star projection(`*`)과 클래스가 아닌 타입은 `Object` 로 쓴다. */
    public fun of(type: KType): String {
        val k = type.classifier as? KClass<*> ?: return OBJECT
        val base = k.simpleName ?: OBJECT

        return base + type.arguments.joinToString("") { arg -> arg.type?.let(::of) ?: OBJECT }
    }

    /** 클래스 이름. 타입 인자는 알 수 없으므로 붙이지 않는다. */
    public fun of(type: KClass<*>): String = type.simpleName ?: OBJECT

    /**
     * 이름이 겹칠 때 차례로 시도할 이름. 첫 번째는 [of] 와 같고, 다음은 바깥 클래스 이름을 앞에 붙인 이름
     * (`Outer.Item` → `OuterItem`), 그다음부터는 패키지 이름을 뒤에서부터 하나씩 더 붙인 이름이다
     * (`com.shop.order.Item` → `OrderItem`, `ShopOrderItem` …). 타입 인자에도 같은 단계를 적용하고,
     * JDK·Kotlin·Spring 타입(`List`, `String`, `Page`)은 단순 이름 그대로 둔다.
     */
    internal fun candidates(type: KType): List<String> = (0..MAX_QUALIFICATION).map { qualified(type, it) }
        .distinct()

    private fun qualified(type: KType, level: Int): String {
        val k = type.classifier as? KClass<*> ?: return OBJECT

        val arguments = type.arguments.joinToString("") { arg -> arg.type?.let { qualified(it, level) } ?: OBJECT }

        return className(k, level) + arguments
    }

    private fun className(k: KClass<*>, level: Int): String {
        val simple = k.simpleName ?: OBJECT
        val c = k.java

        if (level == 0 || STANDARD_PACKAGES.any { c.name.startsWith(it) }) {
            return simple
        }

        val enclosing = generateSequence(c.enclosingClass) { it.enclosingClass }.map { it.simpleName }
            .toList()
            .asReversed()
            .joinToString("")
        val packages = c.packageName.split('.')
            .filter { it.isNotEmpty() }
            .takeLast(level - 1)
            .joinToString("") { segment -> segment.replaceFirstChar { it.uppercaseChar() } }

        return packages + enclosing + simple
    }

    private const val OBJECT = "Object"
    private const val MAX_QUALIFICATION = 8

    /** 이름을 한정하지 않는 패키지. 표준 타입과 Spring 타입(`Page`, `PagedModel`)은 단순 이름 그대로 둔다. */
    private val STANDARD_PACKAGES = listOf("java.", "javax.", "kotlin.", "org.springframework.")
}
