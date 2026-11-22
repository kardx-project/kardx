package io.github.kardx.internal.introspection

import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.introspection.FieldExtractor.Mode
import kotlin.reflect.full.starProjectedType

/**
 * 예시 객체의 런타임 클래스에서 응답 본문 필드를 추출한다 (`responseFieldsOf(example::class)` 와 같은 규칙).
 * `errorResponse(status, description, example)` 단축형이 쓴다.
 *
 * 런타임 클래스만으로 구조를 알 수 없는 값은 추출하지 않고 빈 목록을 돌려준다: 문자열·숫자·불리언·Enum 같은 값, 컬렉션·배열·
 * `Map`(원소 타입이 지워졌다), 타입 파라미터가 있는 클래스(`ApiResponse<T>` 의 `T` 를 알 수 없다).
 */
internal object ExampleFields {
    fun of(example: Any): List<RestDocsField> {
        if (!isExtractable(example)) return emptyList()

        return FieldExtractor(Mode.BODY).extract(example::class.starProjectedType, "")
            .map { it.toRestDocsField() }
    }

    private fun isExtractable(example: Any): Boolean {
        val kClass = example::class
        val isValue = example is CharSequence || example is Number || example is Boolean || example is Enum<*>
        val isContainer = example is Collection<*> || example is Map<*, *> || kClass.java.isArray

        return !isValue && !isContainer && kClass.typeParameters.isEmpty()
    }
}
