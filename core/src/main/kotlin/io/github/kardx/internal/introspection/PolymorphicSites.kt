package io.github.kardx.internal.introspection

import io.github.kardx.config.RestDocsConfiguration
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * 본문 안의 다형 프로퍼티 하나.
 *
 * @property path REST Docs 필드 경로 (`payment`, `payments[]`). 본문 자체면 빈 문자열이다
 */
internal class PolymorphicSite(val path: String, val type: PolymorphicType)

/**
 * 본문 타입을 따라가며 다형 타입([PolymorphicTypes])인 프로퍼티의 경로를 찾는다. 경로 규칙은 [FieldExtractor] 와 같다
 * (Jackson 이름 규칙, `@JsonUnwrapped`, 배열 `[]`, 요청이면 읽기 전용·응답이면 쓰기 전용 프로퍼티 제외).
 * 다형 타입 안(하위 타입의 프로퍼티)은 더 찾지 않는다. 순환 참조와 최대 깊이에서 멈춘다.
 *
 * 다형 타입이 없는 본문 타입은 기억해 두고 다시 따라가지 않는다.
 */
internal object PolymorphicSites {
    private val absent: MutableSet<Pair<KType, Boolean>> = ConcurrentHashMap.newKeySet()

    /** [type] 본문의 다형 프로퍼티. [request] 면 요청 본문 규칙이다. */
    fun find(type: KType, request: Boolean): List<PolymorphicSite> {
        val key = type to request

        if (key in absent) {
            return emptyList()
        }

        val sites = Walk(request).apply { type(type, "", emptyList()) }
            .sites

        if (sites.isEmpty()) {
            absent += key
        }

        return sites
    }

    private class Walk(private val request: Boolean) {
        val sites = mutableListOf<PolymorphicSite>()

        private val naming = PropertyNaming.global()

        private val maxDepth = RestDocsConfiguration.current.extraction.maxDepth

        fun type(type: KType, path: String, stack: List<KClass<*>>) {
            val unwrapped = unwrap(type).type

            when (val shape = shapeOf(unwrapped)) {
                is Shape.Nested -> nested(unwrapped, shape.kClass, path, stack)

                // 원소가 다시 배열인 타입이 끝없이 이어질 수 있어 배열 겹수도 깊이로 센다
                is Shape.Array -> if (path.split("[]").size <= maxDepth) {
                    shape.element?.let { type(it, "$path[]", stack) }
                }

                else -> Unit
            }
        }

        private fun nested(type: KType, k: KClass<*>, path: String, stack: List<KClass<*>>) {
            val polymorphic = PolymorphicTypes.of(k)

            when {
                polymorphic != null -> sites += PolymorphicSite(path, polymorphic)
                k in stack || stack.size >= maxDepth -> Unit
                else -> properties(type, k, path, stack + k) { it }
            }
        }

        private fun properties(
            type: KType,
            k: KClass<*>,
            prefix: String,
            stack: List<KClass<*>>,
            decorate: (String) -> String,
        ) {
            val bindings = bindingsOf(type)

            PropertyIntrospector.properties(type, k)
                .filterNot { if (request) it.readOnly else it.writeOnly }
                .forEach { meta ->
                    val name = if (meta.nameFixed) meta.name else naming?.invoke(meta.name) ?: meta.name
                    val propertyType = meta.type.substitute(bindings)
                    val unwrapping = meta.unwrapped

                    if (unwrapping == null) {
                        type(propertyType, FieldExtractor.joinPath(prefix, decorate(name)), stack)
                    } else {
                        unwrapped(propertyType, prefix, stack, unwrapping)
                    }
                }
        }

        /** `@JsonUnwrapped` 프로퍼티는 하위 프로퍼티가 부모 수준에 펼쳐진다. 다형 타입이면 펼칠 수 없어 보지 않는다. */
        private fun unwrapped(type: KType, prefix: String, stack: List<KClass<*>>, unwrapping: JsonUnwrapped) {
            val inner = unwrap(type).type
            val k = (shapeOf(inner) as? Shape.Nested)?.kClass ?: return

            if (k !in stack && stack.size < maxDepth && PolymorphicTypes.of(k) == null) {
                properties(inner, k, prefix, stack + k, unwrapping::decorate)
            }
        }
    }
}
