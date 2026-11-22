package io.github.kardx.internal.openapi

import org.springframework.core.annotation.MergedAnnotations
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY
import org.springframework.util.ClassUtils
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.method.HandlerMethod
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * 핸들러에 선언한 API 버전 (Spring Framework 7 의 `@GetMapping(version = "1.1")` 등). 메서드의 선언을 먼저 보고, 없으면
 * 컨트롤러 클래스의 `@RequestMapping(version = ...)` 이다. 기준 버전 표기(`"1.1+"`, 그 버전 이상)는 끝의 `+` 를 뗀다.
 *
 * `version` 속성은 Spring 7 에만 있으므로 어노테이션 속성을 이름으로 읽는다. Spring 6 에서는 속성이 없어 항상 null 이다.
 * 핸들러 메서드마다 한 번만 읽는다.
 */
internal object HandlerApiVersion {
    private const val VERSION = "version"

    private const val BASELINE_SUFFIX = "+"

    private val cache = ConcurrentHashMap<Pair<Class<*>, Method>, Optional<String>>()

    /** [handler] 가 어노테이션 컨트롤러 메서드([HandlerMethod])면 그 API 버전. 선언이 없거나 핸들러가 아니면 null. */
    fun of(handler: Any?): String? {
        val method = handler as? HandlerMethod ?: return null
        val controller = ClassUtils.getUserClass(method.beanType)

        return cache.computeIfAbsent(controller to method.method) { (type, javaMethod) ->
            Optional.ofNullable(declared(javaMethod) ?: declared(type))
        }.orElse(null)
    }

    private fun declared(element: AnnotatedElement): String? = MergedAnnotations.from(element, TYPE_HIERARCHY)
        .get(RequestMapping::class.java)
        .takeIf { it.isPresent }
        ?.getValue(VERSION, String::class.java)
        ?.orElse(null)
        ?.trim()
        ?.removeSuffix(BASELINE_SUFFIX)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
