package io.github.kardx.mockmvc

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.http.MockResponseBody
import io.github.kardx.internal.jackson.TypeCapture
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.lang.reflect.Type

// ResultActions 확장 함수. 문서화(andDocument)는 빌더와 함께 Documentation.kt 에 있다.

/**
 * 요청과 응답을 콘솔에 출력한다. `andDo(MockMvcResultHandlers.print())` 와 같다.
 *
 * ```kotlin
 * mockMvc.perform(get("/api/users/{id}", 1L))
 *     .andPrint()
 *     .andStatus(HttpStatus.OK)
 *     .andDocument { ... }
 * ```
 *
 * 모든 요청을 출력하려면 [io.github.kardx.config.HttpSettings.http.printRequests] 를 켠다
 * (yml `kardx.print-requests: true`). 요청마다 읽으므로 MockMvc 를 만든 뒤에 켜도 된다.
 */
public fun ResultActions.andPrint(): ResultActions = andDo(MockMvcResultHandlers.print())

/** 요청과 응답을 로그(`org.springframework.test.web.servlet.result` DEBUG)로 남긴다. `andDo(MockMvcResultHandlers.log())` 와 같다. */
public fun ResultActions.andLog(): ResultActions = andDo(MockMvcResultHandlers.log())

/**
 * 응답 상태 코드를 검증한다. `andExpect(status().is(404))` 와 같고, `MockMvcResultMatchers.status` 를 import 하지 않아도 된다.
 * 다르면 Spring 의 `AssertionError`(`Response status expected:<404> but was:<200>`)로 실패한다. 같은 [ResultActions] 를 돌려주므로
 * `andExpect`·`andDocument` 를 이어 쓴다. 상수로 쓰려면 `andStatus(HttpStatus.NOT_FOUND)` 를 쓴다.
 *
 * ```kotlin
 * mockMvc.perform(get("/api/users/{id}", 999L))
 *     .andStatus(404)
 *     .andDocument { ... }
 * ```
 */
public fun ResultActions.andStatus(status: Int): ResultActions = andExpect(
    MockMvcResultMatchers.status()
        .`is`(status),
)

/**
 * 응답 상태 코드를 [HttpStatusCode] 로 검증한다. [HttpStatus] 상수(`HttpStatus.CREATED`)도 그대로 넘긴다.
 * `andStatus(status.value())` 와 같다.
 *
 * ```kotlin
 * mockMvc.perform(post("/api/users").jsonContent(request))
 *     .andStatus(HttpStatus.CREATED)
 *     .andDocument { ... }
 * ```
 */
public fun ResultActions.andStatus(status: HttpStatusCode): ResultActions = andStatus(status.value())

/**
 * 응답 JSON 의 [expression] 값을 검증한다. `andExpect(jsonPath(expression).value(expected))` 와 같다.
 * 테스트 클래스패스에 `json-path` 가 필요하다 (`spring-boot-starter-test` 에 들어 있다).
 *
 * ```kotlin
 * .andExpectJsonPath("$.code", 200)
 * .andExpectJsonPath("$.data.name", "홍길동")
 * ```
 */
public fun ResultActions.andExpectJsonPath(expression: String, expected: Any?): ResultActions = andExpect(
    MockMvcResultMatchers.jsonPath(expression)
        .value(expected),
)

/**
 * 응답 본문을 UTF-8 문자열로 돌려준다.
 * `andReturn().response.contentAsString` 은 응답에 charset 이 없으면 ISO-8859-1 로 읽어 한글이 깨진다.
 * 객체로 받으려면 [andReturnAsObject], 상태 코드·헤더까지 받으려면 [andReturnEntity] 를 쓴다.
 *
 * ```kotlin
 * val body: String = mockMvc.perform(get("/api/users/{id}", 1L))
 *     .andReturnContent()
 * ```
 */
public fun ResultActions.andReturnContent(): String = andReturn()
    .response
    .getContentAsString(Charsets.UTF_8)

/**
 * 응답 본문을 지정한 타입으로 역직렬화한다. 제네릭 타입(`List<UserResponse>`)도 그대로 유지된다.
 * Jackson 2 `ObjectMapper` 와 Jackson 3 `JsonMapper` 를 모두 지원한다
 * ([io.github.kardx.config.ExtractionSettings.objectMapper]).
 * [T] 가 `String` 이면 UTF-8 원문을, `ByteArray` 면 응답 바이트를 JSON 해석 없이 그대로 돌려준다.
 * 본문이 비었거나 공백뿐이면 [IllegalStateException] 이다. 빈 본문(204 등)을 받을 수 있으면 [andReturnEntity] 를 쓴다.
 * 문자열 그대로 받으려면 [andReturnContent], 상태 코드·헤더까지 받으려면 [andReturnEntity] 를 쓴다.
 *
 * ```kotlin
 * val users: List<UserResponse> =
 *     mockMvc.perform(get("/api/users"))
 *         .andReturnAsObject()
 * ```
 */
public inline fun <reified T> ResultActions.andReturnAsObject(): T = returnAsObject(object : TypeCapture<T>() {}.type)

/** [andReturnAsObject] 의 본체. 인라인 함수가 부르므로 `@PublishedApi` 다. */
@PublishedApi
internal fun <T> ResultActions.returnAsObject(type: Type): T =
    checkNotNull(MockResponseBody(andReturn().response).decode(type)) {
        RestDocsErrorCode.EMPTY_RESPONSE_BODY.message(
            "Response body is empty. Use andReturnEntity<T>() when the response may have no body.",
        )
    }

/**
 * 응답의 상태 코드, 헤더 전부, 본문을 Spring [ResponseEntity] 로 돌려준다.
 * 본문은 [andReturnAsObject] 와 같은 매퍼·UTF-8 로 [T] 로 역직렬화한다 (제네릭 타입 유지).
 * 본문이 비었거나 공백뿐이면(204 등) `body` 는 null 이다. [T] 가 `String` 이면 역직렬화하지 않고 원문 그대로(빈 문자열이면 null),
 * `ByteArray` 면 응답 바이트 그대로(비었으면 null) 담는다.
 * 본문만 필요하면 [andReturnAsObject], 문자열만 필요하면 [andReturnContent] 를 쓴다.
 *
 * ```kotlin
 * val response = mockMvc.perform(post("/api/users").jsonContent(request))
 *     .andReturnEntity<UserResponse>()
 *
 * response.statusCode        // 201 CREATED
 * response.headers.location  // /api/users/1
 * response.body?.nickname    // "홍길동"
 * ```
 */
public inline fun <reified T> ResultActions.andReturnEntity(): ResponseEntity<T> =
    returnEntity(object : TypeCapture<T>() {}.type)

/** [andReturnEntity] 의 본체. 인라인 함수가 부르므로 `@PublishedApi` 다. */
@PublishedApi
internal fun <T> ResultActions.returnEntity(type: Type): ResponseEntity<T> {
    val response = andReturn().response
    val body: T? = MockResponseBody(response).decode(type)

    return ResponseEntity.status(response.status)
        .headers { headers -> response.headerNames.forEach { headers.addAll(it, response.getHeaders(it)) } }
        .body(body)
}
