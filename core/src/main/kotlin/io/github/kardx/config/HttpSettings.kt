package io.github.kardx.config

import org.springframework.restdocs.operation.preprocess.OperationPreprocessor

/**
 * HTTP 요청·응답을 문서에 남기기 전의 가공과 MockMvc·WebTestClient 동작.
 * [RestDocsSettings.http] 로 읽고 `http { }` 블록으로 바꾼다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     http { printRequests = true }
 * }
 * ```
 */
@RestDocsSettingsDsl
public class HttpSettings internal constructor() {
    /**
     * 요청에 더 적용할 Spring REST Docs 전처리기(`OperationPreprocessor`). 라이브러리 기본 가공(URI 를 `http://localhost` 로,
     * [MaskingSettings.headers] 가리기, 요청 본문 pretty print) **뒤에** 이 순서로 적용한다. 기본값 빈 목록. 코드로만 정한다(yml 키 없음).
     * MockMvc·WebTestClient·RestTestClient 의 `andDocument`(SSE `performStream` 포함)가 만드는 모든 스니펫(curl·http-request 등)과 OpenAPI 예시,
     * WireMock 매핑에 나타난다. 메시지 문서화(`MessageDocumentation`)는 HTTP 요청이 아니므로 쓰지 않는다.
     * 문서화할 때 읽으므로 `restDocsMockMvc` 를 만든 뒤에 바꿔도 되고, [RestDocsConfiguration.withSettings] 로 한 테스트에만 줄 수 있다.
     *
     * ```kotlin
     * requestPreprocessors = listOf(Preprocessors.modifyHeaders().remove("X-Request-Id"))
     * requestPreprocessors += Preprocessors.modifyUris().host("api.example.com")
     * ```
     */
    public var requestPreprocessors: List<OperationPreprocessor> = emptyList()
        set(value) {
            field = value.toList()
        }

    /**
     * 응답에 더 적용할 Spring REST Docs 전처리기. 라이브러리 기본 가공(응답 본문 pretty print, [MaskingSettings.headers] 가리기,
     * [removedResponseHeaders] 빼기) **뒤에** 이 순서로 적용한다. 기본값 빈 목록. 코드로만 정한다(yml 키 없음). 나타나는 곳과 읽는 시점은 [requestPreprocessors] 와 같다.
     *
     * ```kotlin
     * responsePreprocessors = listOf(Preprocessors.modifyHeaders().remove("X-Request-Id"))
     * ```
     */
    public var responsePreprocessors: List<OperationPreprocessor> = emptyList()
        set(value) {
            field = value.toList()
        }

    /**
     * 문서에서 뺄 응답 헤더 이름 (대소문자 무시). 기본값 [VOLATILE_RESPONSE_HEADERS] (`Date`).
     * 실제 서버로 보내는 클라이언트(`bindToServer` 로 만든 WebTestClient·RestTestClient)는 응답마다 시각이 다른 `Date` 를 받는다.
     * 그대로 두면 테스트를 다시 돌릴 때마다 스니펫(http-response 등)·WireMock 매핑이 바뀌어, 내용이 같은 문서도 Gradle 이
     * Asciidoctor 이후 태스크를 다시 실행한다. 그래서 기본으로 뺀다. 테스트에서 `responseHeaders(...)` 로 문서화한 헤더는 빼지
     * 않는다. 라이브러리 기본 가공 뒤, [responsePreprocessors] 앞에 적용한다.
     * 비어 있지 않으면 `Max-Age` 가 있는 `Set-Cookie` 의 `Expires` 속성(지금 시각에서 계산해 실행마다 바뀐다)도 뺀다.
     * 남기려면 빈 집합으로 둔다(그러면 응답을 받은 그대로 둔다).
     * yml: `kardx.removed-response-headers` (쉼표 목록, 하나도 빼지 않으려면 `none`). yml 목록은 기본값에 더하지
     * 않고 전체를 정한다 (코드의 `=` 와 같다). `Date` 도 계속 빼려면 목록에 넣는다: `removed-response-headers: Date, X-Request-Id`.
     *
     * ```kotlin
     * removedResponseHeaders = emptySet()                          // Date 도 남긴다
     * removedResponseHeaders += "X-Request-Id"                     // 더 뺀다
     * ```
     */
    public var removedResponseHeaders: Set<String> = VOLATILE_RESPONSE_HEADERS
        set(value) {
            field = value.toSet()
        }

    /**
     * true 이면 [io.github.kardx.mockmvc.restDocsMockMvc] 와 [io.github.kardx.webtestclient.restDocsWebTestClient]
     * (`restDocs(provider)` 로 만든 WebTestClient 포함)가 요청·응답을 콘솔에 출력한다. 기본값 false.
     * 요청마다 읽으므로 클라이언트를 만든 뒤에 바꿔도(`configure { printRequests = true }`, 한 테스트만 `withSettings`) 적용된다.
     * yml: `kardx.print-requests`.
     */
    public var printRequests: Boolean = false

    /**
     * true 이면 [io.github.kardx.mockmvc.restDocsMockMvc] 가 컨텍스트의 Spring Security 필터 체인
     * (`springSecurityFilterChain` 빈)을 MockMvc 에 건다. 기본값 false — 인터셉터·필터 등 Spring Security 가 아닌
     * 방식으로 인증하는 프로젝트도 있기 때문이다.
     * 켜면 실제 앱처럼 인증이 필요한 API 는 인증 없이 401·403 이 되므로, Spring Security 로 보호하는 API 를 실제와 같게
     * 문서화할 수 있다. spring-security-test 가 있으면 `springSecurity()` 로 걸어
     * `with(user(...))`·`with(jwt())`·`with(csrf())` 를 쓸 수 있고, 없으면 필터 체인만 건다.
     * yml: `kardx.apply-spring-security: true`.
     *
     * MockMvc 를 만들 때(`restDocsMockMvc` 호출) 한 번 읽는다. 바꾼 값은 그 뒤에 만든 MockMvc 부터 적용된다.
     * 컨텍스트에 `springSecurityFilterChain` 빈이 있는데 이 값을 정하지 않았으면(기본값 그대로) JVM 마다 한 번 경고를 남긴다.
     * 필터 체인 없이 문서화하는 것이 의도라면 false 로 명시해 경고를 끈다 (yml `apply-spring-security: false` 도 같다).
     */
    public var applySpringSecurity: Boolean = false
        set(value) {
            field = value
            applySpringSecurityDecided = true
        }

    /** [applySpringSecurity] 를 코드·yml 로 정했는지. 정하지 않았으면 보안 필터 체인을 걸지 않을 때 경고한다. */
    internal var applySpringSecurityDecided: Boolean = false

    internal fun copy(): HttpSettings = HttpSettings().also {
        it.requestPreprocessors = requestPreprocessors
        it.responsePreprocessors = responsePreprocessors
        it.removedResponseHeaders = removedResponseHeaders
        it.printRequests = printRequests
        it.applySpringSecurity = applySpringSecurity
        it.applySpringSecurityDecided = applySpringSecurityDecided
    }

    /** HTTP 가공 기본값 상수. */

    public companion object {
        /** 실행할 때마다 값이 바뀌어 문서에서 빼는 응답 헤더. [removedResponseHeaders] 의 기본값이다. */
        public val VOLATILE_RESPONSE_HEADERS: Set<String> = setOf("Date")
    }
}
