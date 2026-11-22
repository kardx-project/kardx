package io.github.kardx.webtestclient

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.documentation.CachedTemplateEngine
import io.github.kardx.internal.output.OpenRuns
import io.github.kardx.internal.spring.applyApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * 앱 컨텍스트에 서버 없이 묶은(`bindToApplicationContext`) REST Docs 용 [WebTestClient] 를 만든다.
 * MockMvc 의 `restDocsMockMvc` 에 해당한다. 설정 내용은 [restDocs] 와 같고, 더해서
 *
 * - 컨텍스트의 `kardx.*` 속성을 읽는다.
 * - 컨텍스트에 Jackson 매퍼 빈(Jackson 3 `JsonMapper`, 없으면 Jackson 2 `ObjectMapper`)이 있으면 현재 스레드의 기본 매퍼로
 *   쓴다. 필드 이름 규칙이 앱과 같은 설정을 따른다.
 * - 요청을 처리한 컨트롤러 메서드를 기록해 OpenAPI operationId 기본값(핸들러 메서드 이름)에 쓴다. 다른 바인딩으로 직접 만든
 *   클라이언트에서는 핸들러를 알 수 없어 테스트 이름으로 id 를 만든다.
 *
 * 컨텍스트의 `WebFilter` 빈(Spring Security 포함)은 WebFlux 가 그대로 건다. `mutateWith(mockUser())` 같은
 * spring-security-test 기능이 필요하면 `WebTestClient.bindToApplicationContext(context).apply(springSecurity())` 뒤에
 * `.configureClient().restDocs(provider).build()` 로 직접 만든다.
 *
 * ```kotlin
 * @BeforeEach
 * fun setUp(context: ApplicationContext, provider: RestDocumentationContextProvider) {
 *     webTestClient = restDocsWebTestClient(context, provider)
 * }
 * ```
 */
public fun restDocsWebTestClient(
    context: ApplicationContext,
    provider: RestDocumentationContextProvider,
): WebTestClient {
    applyApplicationContext(context)

    return WebTestClient.bindToApplicationContext(context)
        .recordHandlers()
        .configureClient()
        .restDocs(provider)
        .build()
}

/**
 * 지금 실행 중인 테스트의 스니펫 자리로 문서화하는 [WebTestClient] 를 만든다. `restDocsWebTestClient(context, provider)` 와 같고,
 * provider 대신 요청마다 그때 실행 중인 테스트(JUnit `AutoRestDocumentationExtension` 의 테스트 메서드, Kotest
 * `RestDocsExtension` 의 리프 테스트)를 찾는다. 찾는 규칙과 실패는 `restDocsMockMvc(context)` 와 같다 (`KDX-013`).
 *
 * ```kotlin
 * @SpringBootTest
 * @ExtendWith(AutoRestDocumentationExtension::class)
 * class UserApiDocsTest(@Autowired context: ApplicationContext) {
 *     private val webTestClient = restDocsWebTestClient(context)
 *     ...
 * }
 * ```
 */
public fun restDocsWebTestClient(context: ApplicationContext): WebTestClient =
    restDocsWebTestClient(context, OpenRuns.currentProvider)

/**
 * 서버 없이 묶은 바인딩(`bindToController`, `bindToApplicationContext`, `bindToWebHandler` 등)이 요청을 처리한 컨트롤러 메서드를
 * 기록하게 한다. `andDocument` 가 그 이름을 OpenAPI operationId 기본값(핸들러 메서드 이름, `searchUsers`)에 쓴다.
 * 기록하지 않으면 핸들러를 알 수 없어 테스트 이름으로 id 를 만든다. [restDocsWebTestClient] 는 이미 기록한다.
 * 실제 서버(`bindToServer`)에는 걸 수 없다.
 *
 * ```kotlin
 * val client = WebTestClient.bindToController(ProductController())
 *     .recordHandlers()
 *     .configureClient()
 *     .restDocs(provider)
 *     .build()
 * ```
 */
public fun WebTestClient.MockServerSpec<*>.recordHandlers(): WebTestClient.MockServerSpec<*> = withHandlerRecorder()

// MockServerSpec<B> 의 B 가 공개되지 않은 클래스(ApplicationContextSpec 등)일 수 있어 스타 프로젝션으로는 webFilter 의 반환 타입을
// 정할 수 없다. 자기 타입 B 를 타입 파라미터로 받아 그대로 돌려준다
private fun <B : WebTestClient.MockServerSpec<B>> WebTestClient.MockServerSpec<B>.withHandlerRecorder(): B =
    webFilter(HandlerRecorder)

/**
 * 이미 고른 바인딩(`bindToServer`, `bindToController`, `bindToRouterFunction`, 주입받은 클라이언트의 `mutate()`)에
 * REST Docs 설정을 더한다. MockMvc 쪽 기본 동작과 같다.
 *
 * - REST Docs 문서화 필터를 건다. 이 필터가 없으면 `andDocument` 가 설정을 찾지 못해 실패한다.
 * - 요청·응답 본문 pretty print, 헤더 마스킹, URI 를 `http://localhost` 로 맞추는 것과 사용자 전처리기
 *   ([io.github.kardx.config.HttpSettings.http.requestPreprocessors]·`responsePreprocessors`)는 `andDocument` 의 전처리가
 *   한다(클라이언트와 무관).
 * - [io.github.kardx.config.HttpSettings.http.printRequests] 가 true 이면 요청·응답을 콘솔에 출력한다. 요청마다 읽으므로
 *   클라이언트를 만든 뒤에 켜도 된다. 본문까지 받은 교환(`expectBody`)만 출력한다.
 *
 * ```kotlin
 * webTestClient = WebTestClient.bindToController(UserController())
 *     .configureClient()
 *     .restDocs(provider)
 *     .build()
 *
 * // @AutoConfigureWebTestClient 로 주입받은 클라이언트
 * webTestClient = injected.mutate()
 *     .restDocs(provider)
 *     .build()
 * ```
 */
public fun WebTestClient.Builder.restDocs(provider: RestDocumentationContextProvider): WebTestClient.Builder {
    // 요청·응답 가공(pretty print 등)은 andDocument 의 전처리가 한다. 사용자 전처리기를 그 뒤에 두려면 기본 전처리기를 쓰지 않는다
    val documentation = WebTestClientRestDocumentation.documentationConfiguration(provider)
        .templateEngine(CachedTemplateEngine)

    return filter(documentation).entityExchangeResultConsumer { result ->
        if (RestDocsConfiguration.current.http.printRequests) {
            println(result)
        }
    }
}
