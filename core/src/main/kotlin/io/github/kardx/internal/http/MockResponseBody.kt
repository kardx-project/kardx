package io.github.kardx.internal.http

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.jackson.deserializeJson
import org.springframework.mock.web.MockHttpServletResponse
import java.lang.reflect.Type

/**
 * MockMvc 응답 본문을 원하는 타입으로 바꾼다. `andReturnAsObject`·`andReturnEntity` 가 같은 규칙을 쓰게 한다.
 * - `ByteArray`: 응답 바이트 그대로 (비었으면 null)
 * - `String`: UTF-8 원문 그대로 (비었으면 null)
 * - 그 밖의 타입: 비었거나 공백뿐이면 null, 아니면 현재 매퍼로 JSON 역직렬화
 */
internal class MockResponseBody(private val response: MockHttpServletResponse) {
    fun <T> decode(type: Type): T? {
        // 타입을 확인한 뒤의 변환이라 안전하다 (T 가 ByteArray·String)
        @Suppress("UNCHECKED_CAST")
        return when (type) {
            ByteArray::class.java -> response.contentAsByteArray.takeIf { it.isNotEmpty() } as T?

            String::class.java -> text().takeIf { it.isNotEmpty() } as T?

            else -> text().takeIf { it.isNotBlank() }
                ?.let { deserializeJson(RestDocsConfiguration.objectMapper(), it, type) }
        }
    }

    private fun text(): String = response.getContentAsString(Charsets.UTF_8)
}
