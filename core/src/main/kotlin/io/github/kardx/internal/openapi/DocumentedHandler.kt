package io.github.kardx.internal.openapi

import org.springframework.util.ClassUtils
import org.springframework.web.method.HandlerMethod

/**
 * 문서화한 요청을 처리한 핸들러(컨트롤러 메서드). operationId 의 기본 규칙(핸들러 메서드 이름)과
 * 핸들러 메서드의 KDoc·Javadoc 설명([io.github.kardx.internal.sourcedocs.SourceDocs.method])에 쓴다.
 *
 * @property controllerName 컨트롤러 클래스의 단순 이름. CGLIB 프록시면 원래 클래스 이름이다
 * @property methodName 핸들러 메서드 이름. Kotlin 이 바꾼 JVM 이름(`internal` 함수의 `name$module`, value class 인자 함수의
 *   `name-hash`)은 소스의 이름으로 되돌린다. `suspend` 함수와 브리지 메서드도 소스의 이름이다
 * @property classNames 설명을 찾을 클래스의 이진 이름. 컨트롤러 클래스, 메서드를 선언한 클래스(상위 클래스·인터페이스) 순
 * @property arity 소스에 선언한 인자 수. `suspend` 함수가 받는 `Continuation` 은 세지 않는다
 */
internal class DocumentedHandler(
    val controllerName: String,
    val methodName: String,
    val classNames: List<String> = emptyList(),
    val arity: Int = 0,
) {
    companion object {
        private const val CONTINUATION = "kotlin.coroutines.Continuation"

        /**
         * MockMvc `MvcResult.handler`, WebFlux `HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE` 의 값에서 만든다.
         * 어노테이션 컨트롤러 메서드([HandlerMethod])가 아니면(함수형 라우터, 정적 리소스, 핸들러 없음) null.
         */
        fun of(handler: Any?): DocumentedHandler? {
            val method = handler as? HandlerMethod ?: return null
            val controllerClass = ClassUtils.getUserClass(method.beanType)
            val controller = controllerClass.simpleName
            val javaMethod = method.method
            val name = javaMethod.name
                .substringBefore('$')
                .substringBefore('-')

            if (controller.isEmpty() || name.isEmpty()) return null

            val suspending = javaMethod.parameterTypes.lastOrNull()?.name == CONTINUATION
            val arity = javaMethod.parameterCount - if (suspending) 1 else 0
            val classNames = listOf(controllerClass.name, javaMethod.declaringClass.name).distinct()

            return DocumentedHandler(controller, name, classNames, arity)
        }
    }
}
