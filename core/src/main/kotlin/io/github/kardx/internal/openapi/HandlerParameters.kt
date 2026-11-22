package io.github.kardx.internal.openapi

import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.MethodParameter
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.method.HandlerMethod
import java.lang.reflect.Type
import java.util.Locale

/**
 * 요청을 처리한 핸들러(컨트롤러 메서드)가 받는 경로 변수·요청 파라미터·헤더·파트. 요청 이름 → 핸들러 인자([BoundParameter]).
 * 블록에서 타입을 정하지 않은 `pathMeans`·`queryMeans`·`formMeans` 파라미터의 OpenAPI 타입 기본값과, 설명 없이 이름만
 * 적은 항목의 KDoc·Javadoc `@param` 설명에 쓴다.
 *
 * - [path]: `@PathVariable` 인자. 이름은 어노테이션의 `name`/`value`, 없으면 인자 이름
 * - [request]: `@RequestParam` 인자와, Spring 바인딩 어노테이션이 없는 인자(Spring MVC·WebFlux 가 단순 타입이면 요청
 *   파라미터로 바인딩한다). 쿼리와 폼 파라미터가 함께 쓴다. 객체 타입은 쓰는 쪽이 걸러 낸다
 * - [header]: `@RequestHeader` 인자. 헤더 이름은 대소문자를 가리지 않는다
 * - [parts]: `@RequestPart` 인자. `@RequestParam`·어노테이션 없는 `MultipartFile` 인자는 [request] 에 있다
 *
 * Kotlin·Java 핸들러 모두 JVM 의 제네릭 인자 타입(`List<Long>`)을 그대로 둔다. 인자 이름은 Spring 의
 * [DefaultParameterNameDiscoverer] 로 찾는다 (Kotlin 리플렉션 또는 `-parameters`). 어노테이션에 이름이 없는데 인자 이름도
 * 모르면 그 인자는 빠진다. `suspend` 함수가 받는 `Continuation` 은 인자로 보지 않는다.
 */
internal class HandlerParameters(
    val path: Map<String, BoundParameter>,
    val request: Map<String, BoundParameter>,
    val parts: Map<String, BoundParameter>,
    private val headers: Map<String, BoundParameter>,
) {
    /** 이름이 [name] 인 헤더(대소문자 무시)를 받는 인자. 없으면 null. */
    fun header(name: String): BoundParameter? = headers[name.lowercase(Locale.ROOT)]

    companion object {
        private val DISCOVERER = DefaultParameterNameDiscoverer()

        /** Spring 이 다른 방식으로 바인딩하는 인자(`@RequestBody`, `@AuthenticationPrincipal` 등)의 표시 */
        private const val SPRING_ANNOTATION_PREFIX = "org.springframework."

        private const val CONTINUATION = "kotlin.coroutines.Continuation"

        /** [handler] 가 어노테이션 컨트롤러 메서드([HandlerMethod])면 그 인자들. 아니면(함수형 라우터, 핸들러 없음) null. */
        fun of(handler: Any?): HandlerParameters? {
            val method = handler as? HandlerMethod ?: return null
            val path = linkedMapOf<String, BoundParameter>()
            val request = linkedMapOf<String, BoundParameter>()
            val parts = linkedMapOf<String, BoundParameter>()
            val headers = linkedMapOf<String, BoundParameter>()

            method.methodParameters
                .filter { it.parameterType.name != CONTINUATION }
                .forEach { parameter ->
                    parameter.initParameterNameDiscovery(DISCOVERER)

                    val bound = BoundParameter(parameter.genericParameterType, parameter.parameterName)
                    val pathVariable = parameter.getParameterAnnotation(PathVariable::class.java)
                    val requestParam = parameter.getParameterAnnotation(RequestParam::class.java)
                    val requestHeader = parameter.getParameterAnnotation(RequestHeader::class.java)
                    val requestPart = parameter.getParameterAnnotation(RequestPart::class.java)

                    when {
                        pathVariable != null -> nameOf(pathVariable.name, pathVariable.value, bound)
                            ?.let { path[it] = bound }

                        requestParam != null -> nameOf(requestParam.name, requestParam.value, bound)
                            ?.let { request[it] = bound }

                        requestHeader != null -> nameOf(requestHeader.name, requestHeader.value, bound)
                            ?.let { headers[it.lowercase(Locale.ROOT)] = bound }

                        requestPart != null -> nameOf(requestPart.name, requestPart.value, bound)
                            ?.let { parts[it] = bound }

                        !isBoundBySpring(parameter) -> bound.sourceName?.let { request[it] = bound }
                    }
                }

            return HandlerParameters(path, request, parts, headers)
        }

        private fun isBoundBySpring(parameter: MethodParameter): Boolean = parameter.parameterAnnotations.any {
            it.annotationClass.java.name.startsWith(SPRING_ANNOTATION_PREFIX)
        }

        private fun nameOf(name: String, value: String, parameter: BoundParameter): String? = name.ifEmpty { value }
            .ifEmpty { parameter.sourceName }
    }
}

/**
 * 요청의 한 항목을 받는 핸들러 인자.
 *
 * @property type 인자의 JVM 제네릭 타입
 * @property sourceName 소스의 인자 이름 (KDoc·Javadoc `@param` 의 이름). 모르면 null
 */
internal class BoundParameter(val type: Type, val sourceName: String?)
