package io.github.kardx.mockmvc

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.jackson.serializeJson
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder

/**
 * 요청 본문을 JSON 으로 넣고 `Content-Type: application/json` 을 설정한다. Jackson 2/3 모두 지원한다.
 *
 * 본문은 WebSocket 메시지 payload 와 같은 규칙으로 바뀐다.
 *
 * | `body` | 보내는 본문 |
 * |---|---|
 * | `String`·`CharSequence`(`StringBuilder` 등) | 문자열 그대로 (이미 JSON 인 문자열. 다시 직렬화하지 않는다) |
 * | `ByteArray` | UTF-8 문자열 |
 * | 그 밖의 객체, `null` | 현재 매퍼로 JSON 직렬화 |
 *
 * ```kotlin
 * mockMvc.perform(post("/api/users").jsonContent(CreateUserRequest("홍길동")))
 * mockMvc.perform(post("/api/users").jsonContent("""{"name":"홍길동"}"""))
 * ```
 *
 * 문자열을 JSON 문자열 값(`"\"홍길동\""`)으로 보내야 하면 직접 따옴표를 넣는다.
 *
 * 수신자가 공통 상위 타입이라 `post(...)` 뿐 아니라 Spring 7 의 `multipart(...)` 빌더에도 쓸 수 있다.
 * (Spring 6 에서는 multipart 빌더에 쓰면 반환 타입이 `MockHttpServletRequestBuilder` 가 되므로 `.file(...)` 뒤에 붙인다.)
 * 빌더 클래스 계층이 Spring 6 과 7 에서 달라 컴파일한 호출이 어긋나지 않도록 리플렉션으로 호출한다.
 */
public fun <B : AbstractMockHttpServletRequestBuilder<B>> B.jsonContent(body: Any?): B {
    val json = when (body) {
        is CharSequence -> body.toString()
        is ByteArray -> String(body, Charsets.UTF_8)
        else -> serializeJson(RestDocsConfiguration.objectMapper(), body)
    }

    javaClass.getMethod("contentType", MediaType::class.java)
        .invoke(this, MediaType.APPLICATION_JSON)
    javaClass.getMethod("content", String::class.java)
        .invoke(this, json)

    return this
}

/**
 * `Authorization: Bearer <token>` 헤더를 넣는다.
 *
 * ```kotlin
 * mockMvc.perform(get("/api/me").bearerToken("access-token"))
 *     .andDocument { requestHeaders("Authorization" headerMeans "Bearer 액세스 토큰") }
 * ```
 *
 * `header(String, Object...)` 의 반환 타입이 Spring 6 과 7 에서 달라 리플렉션으로 호출한다.
 * 토큰 값은 [마스킹][io.github.kardx.config.MaskingSettings.maskSensitive]을 켜면 문서에서 가려진다
 * (yml `kardx.mask-sensitive-headers: true`, 코드는 `RestDocsConfiguration.configure { masking { maskSensitive() } }`).
 */
public fun <B : AbstractMockHttpServletRequestBuilder<B>> B.bearerToken(token: String): B {
    javaClass.getMethod("header", String::class.java, Array<Any>::class.java)
        .invoke(this, HttpHeaders.AUTHORIZATION, arrayOf<Any>("Bearer $token"))

    return this
}
