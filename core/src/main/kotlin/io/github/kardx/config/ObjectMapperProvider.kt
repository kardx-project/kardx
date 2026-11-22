package io.github.kardx.config

/**
 * 직렬화·추출에 쓸 Jackson 매퍼(Jackson 2 `ObjectMapper` 또는 Jackson 3 `JsonMapper`)를 주는 함수.
 * [RestDocsSettings] 의 `extraction.objectMapper` 로 정한다. 쓸 때마다 부르므로 같은 매퍼를 돌려주는 편이 좋다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     extraction { objectMapper = ObjectMapperProvider { myMapper } }
 * }
 * ```
 */
public fun interface ObjectMapperProvider {
    /** Jackson 매퍼. 다른 타입을 돌려주면 직렬화할 때 실패한다. */
    public fun objectMapper(): Any
}
