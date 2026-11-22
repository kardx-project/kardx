package io.github.kardx.internal.openapi

import io.github.kardx.internal.introspection.deprecationOf
import org.springframework.util.ClassUtils
import org.springframework.web.method.HandlerMethod

/**
 * 요청을 처리한 핸들러 메서드나 그 컨트롤러 클래스의 `@Deprecated`(Kotlin·Java) 이유. Kotlin `@Deprecated` 는 message,
 * Java `@Deprecated` 는 빈 문자열이다. 메서드에 붙은 것을 먼저 본다. 둘 다 없거나 어노테이션 컨트롤러 메서드가 아니면 null.
 * 블록에서 `deprecated` 를 정하지 않은 API 의 기본값이다.
 */
internal fun handlerDeprecation(handler: Any?): String? {
    val method = handler as? HandlerMethod ?: return null

    return deprecationOf(method.method.annotations.toList())
        ?: deprecationOf(ClassUtils.getUserClass(method.beanType).annotations.toList())
}
