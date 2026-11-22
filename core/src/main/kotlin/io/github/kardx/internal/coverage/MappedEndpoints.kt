package io.github.kardx.internal.coverage

import io.github.kardx.internal.openapi.HandlerApiVersion
import io.github.kardx.internal.openapi.handlerDeprecation
import io.github.kardx.internal.reflect.defaultClassLoader
import io.github.kardx.internal.reflect.loadClassOrNull
import org.springframework.beans.factory.BeanFactoryUtils
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils
import org.springframework.web.method.HandlerMethod

/**
 * 앱 컨텍스트의 어노테이션 컨트롤러 매핑(`@RequestMapping` 류). Spring MVC 와 WebFlux 의 `RequestMappingHandlerMapping` 빈을
 * 모두(상위 컨텍스트 포함) 읽는다. 함수형 라우트(`RouterFunction`)는 경로를 알 수 없어 넣지 않는다.
 *
 * 서블릿·리액티브 클래스는 컨텍스트에 있는 쪽만 이름으로 로드하고 매핑 정보(`RequestMappingInfo`)는 리플렉션으로 읽는다.
 * 그래서 MockMvc 없는 WebFlux 프로젝트와 Spring 6·7 모두에서 같은 코드가 돈다.
 */
internal object MappedEndpoints {
    private val MAPPING_TYPES = listOf(
        "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
        "org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping",
    )

    /** [context] 의 엔드포인트. 경로·메서드 순이다. */
    fun of(context: ApplicationContext): List<MappedEndpoint> {
        val loader = context.classLoader ?: defaultClassLoader()

        return MAPPING_TYPES.mapNotNull { loadClassOrNull(it, loader) }
            .flatMap { BeanFactoryUtils.beansOfTypeIncludingAncestors(context, it).values }
            .distinct()
            .flatMap(::endpointsOf)
            .distinctBy { it.key }
            .sortedBy { it.key }
    }

    private fun endpointsOf(mapping: Any): List<MappedEndpoint> {
        val handlerMethods = call(mapping, "getHandlerMethods") as? Map<*, *> ?: return emptyList()

        return handlerMethods.flatMap { (info, handler) ->
            (handler as? HandlerMethod)?.let { endpoints(info, it) }.orEmpty()
        }
    }

    /** 매핑 하나의 메서드 × 경로 패턴. */
    private fun endpoints(info: Any?, handler: HandlerMethod): List<MappedEndpoint> {
        val methods = (call(call(info, "getMethodsCondition"), "getMethods") as? Collection<*>).orEmpty()
            .map { it.toString() }
            .ifEmpty { listOf(MappedEndpoint.ANY_METHOD) }
        val name = "${ClassUtils.getUserClass(handler.beanType).name}#${sourceName(handler)}"
        val deprecated = handlerDeprecation(handler) != null
        val version = HandlerApiVersion.of(handler)

        return patterns(info).flatMap { path ->
            methods.map { method -> MappedEndpoint(method, path, name, deprecated, version) }
        }
    }

    /**
     * 경로 패턴. 서블릿 매핑은 `getPatternValues()` (PathPattern·AntPath 공통), 리액티브 매핑은
     * `getPatternsCondition().getPatterns()` 의 `PathPattern` 이다.
     */
    private fun patterns(info: Any?): List<String> {
        val patterns = call(info, "getPatternValues") as? Collection<*>
            ?: call(call(info, "getPatternsCondition"), "getPatterns") as? Collection<*>

        return patterns.orEmpty()
            .map { (call(it, "getPatternString") ?: it).toString() }
    }

    /** Kotlin 이 바꾼 JVM 이름(`name$module`, `name-hash`)을 소스의 이름으로 되돌린다. */
    private fun sourceName(handler: HandlerMethod): String = handler.method
        .name
        .substringBefore('$')
        .substringBefore('-')

    /** 인자 없는 public 메서드를 부른다. 메서드가 없거나 실패하면 null. */
    private fun call(target: Any?, name: String): Any? {
        val method = target?.javaClass
            ?.methods
            ?.firstOrNull { it.name == name && it.parameterCount == 0 } ?: return null

        // 접근할 수 없는 구현 클래스거나 호출이 실패하면 그 정보만 모르는 것으로 본다
        return runCatching { method.invoke(target) }.getOrNull()
    }
}
