package io.github.kardx.dsl

import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.diagnostics.RestDocsErrorCode
import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.headers.HeaderDocumentation

/**
 * 요청/응답 헤더 DSL.
 *
 * ```kotlin
 * "Authorization" headerMeans "Bearer JWT 토큰"
 * "X-Request-Id" headerMeans "추적 ID" isOptional true example "req-123"
 * ```
 *
 * 수식어(`isOptional`, `example`, `constraint` 등)는 이 헤더를 바꾸지 않고 디스크립터까지 복사한 새 헤더를 돌려준다.
 * 그래서 `val` 상수로 빼서 여러 테스트가 같이 써도, 한 테스트에서 붙인 수식어는 그 테스트에만 적용된다.
 * ([descriptor] 를 직접 바꾸면 공유한 상수도 바뀐다. 수식어를 쓴다.)
 *
 * ```kotlin
 * val AUTH = "Authorization" headerMeans "Bearer 액세스 토큰"
 * requestHeaders(AUTH isOptional true) // 이 테스트에만 optional. AUTH 는 그대로 필수다
 * ```
 */
public class RestDocsHeader(public val descriptor: HeaderDescriptor) {
    /** true 이면 [optional] 과 같다. false 는 아무것도 바꾸지 않는다 (이미 optional 이면 그대로 둔다). */
    public infix fun isOptional(value: Boolean): RestDocsHeader = if (value) optional() else this

    /**
     * 없어도 되는 항목으로 표시한다. 테스트 요청·응답에 없어도 실패하지 않는다.
     * AsciiDoc 표의 Required 열이 `false` 가 되고, OpenAPI 헤더 파라미터의 `required` 가 false 가 된다.
     */
    public fun optional(): RestDocsHeader = modified { it.optional() }

    /**
     * 제약조건 설명. `request-headers`·`response-headers` 표의 Constraints 열에 그대로 나가고, OpenAPI(Gradle 플러그인) 헤더
     * `description` 뒤에 `Constraints: ...`(`language = KO` 면 `제약: ...`) 으로 붙는다. 메시지 문서(`message-NN`, AsyncAPI)에는 나가지 않는다.
     * `"X-Request-Id" headerMeans "추적 ID" constraint "UUID"`
     */
    public infix fun constraint(value: String): RestDocsHeader = modified {
        it.attributes[CONSTRAINTS_ATTRIBUTE] = value
        it.attributes[CONSTRAINT_NOTE_ATTRIBUTE] = value
    }

    /**
     * 예시 값. `request-headers`·`response-headers` 표의 Description 칸에서 설명 아래 `Example: ...` 줄로 나가고,
     * OpenAPI(Gradle 플러그인) 헤더의 `example` 이 된다. 없으면 OpenAPI 예시는 실제 요청 헤더 값이다.
     * `Authorization`·`Cookie` 같은 민감 헤더는 OpenAPI 예시에서 빠진다. 검증에는 영향이 없다.
     * `"X-Request-Id" headerMeans "추적 ID" example "req-123"`
     */
    public infix fun example(value: String): RestDocsHeader = modified { it.attributes[EXAMPLE_ATTRIBUTE] = value }

    /**
     * 기본값 (보내지 않았을 때 쓰는 값). 표의 Description 칸에서 설명 아래 `Default: ...` 줄로 나가고, OpenAPI(Gradle 플러그인)
     * 헤더 스키마의 `default` 가 된다. 보통 [optional] 과 함께 쓴다.
     * `"Accept-Language" headerMeans "응답 언어" isOptional true withDefaultValue "ko"`
     */
    public infix fun withDefaultValue(value: String): RestDocsHeader =
        modified { it.attributes[DEFAULT_VALUE_ATTRIBUTE] = value }

    /** 디스크립터까지 복사한 헤더에 [change] 를 적용한다. 이 헤더는 그대로다. */
    internal inline fun modified(change: (HeaderDescriptor) -> Unit): RestDocsHeader {
        val copy = HeaderDocumentation.headerWithName(descriptor.name)
            .description(descriptor.description)

        copy.attributes.putAll(descriptor.attributes)

        if (descriptor.isOptional) copy.optional()

        change(copy)

        return RestDocsHeader(copy)
    }
}

/**
 * 헤더 DSL 의 시작점. 이름은 대소문자를 가리지 않는다.
 * REST 는 `requestHeaders`·`responseHeaders`, 메시지는
 * [io.github.kardx.dsl.MessageDocumentationBuilder.headers] 에 넘긴다.
 * OpenAPI 요청 헤더의 예시 값은 실제 요청의 헤더 값이고, [RestDocsHeader.example] 로 바꿀 수 있다.
 * `Authorization`·`Cookie` 같은 민감 헤더는 Gradle 플러그인이 OpenAPI 예시에서 뺀다.
 */
public infix fun String.headerMeans(description: String): RestDocsHeader = RestDocsHeader(
    HeaderDocumentation.headerWithName(this)
        .description(description),
)

/**
 * 설명을 핸들러의 `@param` 에서 가져오는 요청 헤더. [DocumentationBuilder.requestHeaders] 에만 넘긴다.
 * `"Accept-Language" headerMeans FromParamTag isOptional true withDefaultValue "ko"`
 */
@Suppress("UnusedParameter") // FromParamTag 는 오버로드를 고르는 표식이다
@ExperimentalRestDocsApi
public infix fun String.headerMeans(description: FromParamTag): RestDocsHeader = undescribedHeader(this)

/** 설명 없이 이름만 적은 헤더. 설명은 핸들러의 `@param` 에서 채운다 ([FromParamTag]). */
internal fun undescribedHeader(name: String): RestDocsHeader = RestDocsHeader(HeaderDocumentation.headerWithName(name))

/**
 * `@param` 으로 설명을 채울 수 없는 곳([section])에 설명 없는 헤더([FromParamTag])가 있으면 실패한다.
 *
 * @throws IllegalArgumentException 설명 없는 헤더가 있을 때
 */
internal fun List<RestDocsHeader>.requireDescribed(section: String): List<RestDocsHeader> {
    val undescribed = filter { it.descriptor.description == null }
        .map { it.descriptor.name }

    require(undescribed.isEmpty()) {
        RestDocsErrorCode.FROM_PARAM_TAG_ON_RESPONSE.message(
            "$section ${undescribed.joinToString { "'$it'" }} need a description. " +
                "FromParamTag reads the handler's `@param` tags, which describe request items only.",
        )
    }

    return this
}
