[English](../webtestclient.md) | **한국어**

# WebTestClient · RestTestClient

[문서 목록](README.md)

<!-- toc -->
**목차**

- [지원 범위](#지원-범위)
- [의존성](#의존성)
  - [문서 YAML 이 내려받기로 열릴 때 (WebFlux)](#문서-yaml-이-내려받기로-열릴-때-webflux)
- [테스트 기본 클래스](#테스트-기본-클래스)
- [문서화 테스트](#문서화-테스트)
- [operationId](#operationid)
- [RestTestClient](#resttestclient)
<!-- /toc -->

MockMvc 대신 WebTestClient 로 테스트하는 프로젝트(WebFlux 앱 등)에서 문서를 만드는 방법입니다. 전체 예제는 [samples/spring-boot-4-webflux](../../samples/spring-boot-4-webflux) 입니다(서블릿 스택 없는 Boot 4 WebFlux 앱, `suspend`·`Flow`·`Mono` 핸들러, SSE). `andDocument { }` 블록은 MockMvc 와 **같은 DSL** 이고, 만드는 스니펫·AsciiDoc 문서·OpenAPI 조각도 같습니다. [HTTP API 문서화](http-api.md)의 내용(필드 추출, 여러 상태 코드, 스키마 이름, 검증 실패 메시지)은 클라이언트와 상관없이 그대로 적용됩니다.

## 지원 범위

| 기능 | MockMvc | WebTestClient | RestTestClient |
|---|---|---|---|
| `andDocument { }` 블록 (파라미터·헤더·쿠키·파트·필드·링크·`openApi { }`·`snippets(...)`) | 지원 | 지원 (같은 블록) | 지원 (같은 블록) |
| 진입점 | `ResultActions`, `ResultActionsDsl`, `MvcTestResult` | `ResponseSpec`, `BodyContentSpec`, `BodySpec` (`expectBody<T>()`) | `ResponseSpec`, `BodyContentSpec`, `BodySpec` (`expectBody(T::class.java)`) |
| 클라이언트 설정 헬퍼 | `restDocsMockMvc(context, provider)` | `restDocsWebTestClient(context, provider)`, `WebTestClient.Builder.restDocs(provider)` | `RestTestClient.Builder.restDocs(provider)` |
| 요청 pretty print, URI 를 `http://localhost` 로, 응답 pretty print | 지원 | 지원 | 지원 (상대 URI 는 `http://localhost` 기준으로 풂) |
| 헤더 마스킹 (`mask-sensitive-headers`) | 지원 | 지원 | 지원 |
| 검증 실패 진단 (`DocumentationMismatchException`) | 지원 | 지원 | 지원 |
| OpenAPI (epages `resource.json`, 보안 스키마, 스키마 이름, 전역 헤더, `openapi-extras.json`) | 지원 | 지원 | 지원. 단 multipart 는 파트를 나누지 않아 multipart 요청 본문 스키마가 없습니다 |
| WireMock stub (`wire-mock-stubs`) | 지원 | 지원 | 지원 |
| `@Deprecated`·설명 어노테이션·응답 래퍼 | 지원 | 지원 | 설명 어노테이션·응답 래퍼 지원. 컨트롤러의 `@Deprecated` 는 읽지 않습니다 (핸들러를 모름) |
| 앱의 Jackson 매퍼 빈, `kardx.*` yml | 지원 | `restDocsWebTestClient` 가 지원. 다른 바인딩은 `AutoRestDocumentationExtension` 이 yml 만 읽습니다 | `restDocsMockMvc(context, provider)` 에 묶으면 MockMvc 와 같음. 다른 바인딩은 `AutoRestDocumentationExtension` 이 yml 만 읽습니다 |
| Spring Security | `apply-spring-security: true` 면 필터 체인을 걸고, spring-security-test 가 있으면 `springSecurity()` (기본은 걸지 않음) | `WebFilter` 빈은 WebFlux 가 겁니다. `mutateWith(mockUser())` 는 직접 `apply(springSecurity())` | 묶은 `MockMvc` 가 거는 그대로 |
| `http.printRequests` | 지원 | 지원 (`EntityExchangeResult` 출력. `expectBody` 로 본문을 읽은 교환만) | `restDocsMockMvc` 에 묶었을 때 |
| 끝나는 SSE (`events(...)`) | `performAsync` | 지원 (응답 `Flux` 가 끝나면) | 다루지 않음 (MockMvc 사용) |
| 끝나지 않는 SSE | `performStream` | 미지원 | 미지원 |
| 테스트 클래스별 AsciiDoc, 실패 테스트 제외, 스니펫 정리 (`AutoRestDocumentationExtension`) | 지원 | 지원 (클라이언트와 무관) | 지원 (클라이언트와 무관) |
| 보조 함수 `jsonContent`·`bearerToken`·`andStatus` 등 | 지원 | WebTestClient 자체 API (`bodyValue`, `headers { it.setBearerAuth(...) }`, `expectStatus()`) | RestTestClient 자체 API (`body(...)`, `headers { it.setBearerAuth(...) }`, `expectStatus()`) |

## 의존성

WebTestClient 용 아티팩트는 따로 없습니다. 본체(`kardx`)에 들어 있고, 본체는 MockMvc·서블릿 쪽 모듈을 끌고 오지 않습니다. Gradle 플러그인을 쓰면 WebFlux 앱(`spring-boot-starter-webflux` 선언)에 본체, REST Docs 의 WebTestClient 모듈, (epages 플러그인이 있으면) epages 의 WebTestClient 모듈을 알아서 넣습니다 ([자동으로 넣는 의존성](build.md#자동으로-넣는-의존성)).

```kotlin
plugins {
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("com.epages.restdocs-api-spec") version "0.20.2"   // OpenAPI 를 만들 때만. Boot 3: 0.19.4
    id("io.github.kardx-project") version "0.1"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

플러그인 없이 쓰면 직접 선언합니다.

```kotlin
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.restdocs:spring-restdocs-webtestclient")   // 버전은 Boot BOM
    testImplementation("io.github.kardx-project:kardx:0.1")
    testImplementation("com.epages:restdocs-api-spec-webtestclient:0.20.2") {        // OpenAPI 를 만들 때만. Boot 3: 0.19.4
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-web") // 아래 표
    }
}
```

**WebFlux 앱이면 테스트 클래스패스에 Spring MVC 가 딸려 오지 않게 합니다.** Spring MVC 가 있으면 `@SpringBootTest` 가 WebFlux 앱을 서블릿 앱으로 띄우고, 테스트가 `No bean named 'webHandler' available` 로 실패합니다.

| 딸려 오는 것 | Boot 3 | Boot 4 | 조치 |
|---|---|---|---|
| epages 본체의 `spring-boot-starter-web` (epages 0.19·0.20 모두 runtime 의존) | 서블릿 앱이 됩니다 | 서블릿 앱이 됩니다 | Gradle 플러그인이 뺍니다. 플러그인 없이 쓰면 `exclude` |
| 본체(`kardx`) | 딸려 오지 않음 | 딸려 오지 않음 | 없음. 예전 버전은 `spring-restdocs-mockmvc` 를 끌고 와 Boot 3 에서 `exclude` 가 필요했습니다 |

- `spring-boot-starter-web` 은 **Gradle 플러그인이 자동으로 뺍니다.** 프로젝트가 WebFlux 앱이면(`spring-boot-starter-webflux` 를 선언했고 `spring-boot-starter-web`·`-websocket`, `spring-webmvc` 는 직접 선언하지 않았으면) `testRuntimeClasspath` 에서 제외합니다. 끄려면 `kardx { excludeServletStarterForWebFlux = false }` 입니다.
  - 판정은 `testRuntimeClasspath` 가 상속한 configuration(`implementation`·`testImplementation` 등)에 프로젝트가 **직접 선언한** 의존성만 봅니다. 다른 모듈(`project(":common")`)이나 사내 스타터를 거쳐 전이로 들어온 `spring-boot-starter-webflux` 는 보지 못해 빼지 않고, 전이로 들어온 `spring-boot-starter-web` 도 보지 못해 WebFlux 를 직접 선언했으면 빼 버립니다(MVC 와 WebFlux 를 함께 쓰는 앱). 이런 구성이면 `excludeServletStarterForWebFlux = false` 로 끄고 직접 `exclude` 합니다.
  - 대상은 `testRuntimeClasspath` 하나입니다. 다른 소스셋(`integrationTest` 등)이나 JVM Test Suites(`testing.suites`)의 런타임 클래스패스에서는 빼지 않으므로 그 configuration 에 직접 `exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")` 를 넣습니다.
- WebSocket·STOMP 메시지 문서화(`MessageDocumentation`)도 MockMvc 없이 동작합니다.

MVC 앱에서 WebTestClient 를 쓰는 경우(`MockMvcWebTestClient`, 서버에 붙는 `bindToServer`)에는 아무것도 뺄 필요가 없습니다 (`spring-boot-starter-webflux` 를 선언하지 않았으면 플러그인도 빼지 않습니다). 이때 플러그인은 MVC 앱으로 보고 `spring-restdocs-mockmvc` 를 넣으므로, `spring-restdocs-webtestclient` 는 직접 선언합니다.

### 문서 YAML 이 내려받기로 열릴 때 (WebFlux)

WebFlux(Netty)는 `.yaml` 의 미디어 타입을 몰라 `/docs/openapi3.yaml`·`/docs/asyncapi.yaml` 을 `application/octet-stream` 으로 보내므로, 브라우저에서 열면 내려받기가 됩니다. 서블릿 앱의 `server.mime-mappings` 는 WebFlux 에 쓰이지 않습니다. 문서 디렉터리에 리소스 핸들러를 직접 두고 미디어 타입을 정합니다 (예제의 `DocsResourceConfig`).

```kotlin
@Configuration(proxyBeanMethods = false)
class DocsResourceConfig : WebFluxConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/docs/**")
            .addResourceLocations("classpath:/static/docs/")       // 플러그인 bootJar.path 기본값 static/docs
            .setMediaTypes(mapOf("yaml" to YAML, "yml" to YAML))
    }

    private companion object {
        val YAML = MediaType("text", "yaml", Charsets.UTF_8)
    }
}
```

Gradle 플러그인(`io.github.kardx-project`), Asciidoctor·epages 플러그인 설정은 MockMvc 프로젝트와 같습니다 ([시작하기](getting-started.md#3-buildgradlekts)).

## 테스트 기본 클래스

`restDocsWebTestClient(context, provider)` 는 앱 컨텍스트에 서버 없이 묶은 클라이언트를 만듭니다(`WebTestClient.bindToApplicationContext`). MockMvc 의 `restDocsMockMvc` 와 같이 REST Docs 필터, 요청 pretty print, `http.printRequests`, 앱 Jackson 매퍼, `kardx.*` 속성을 적용하고, OpenAPI operationId 에 쓸 핸들러 메서드도 기록합니다([operationId](#operationid)).

```kotlin
import io.github.kardx.junit.AutoRestDocumentationExtension
import io.github.kardx.webtestclient.restDocsWebTestClient

@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
abstract class ApiDocsTest {
    protected lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUpRestDocs(context: ApplicationContext, provider: RestDocumentationContextProvider) {
        webTestClient = restDocsWebTestClient(context, provider)
    }
}
```

기본 클래스는 꼭 필요하지 않습니다. `AutoRestDocumentationExtension` 은 `WebTestClient` 파라미터를 받는 테스트 메서드(`fun getUser(webTestClient: WebTestClient)`)에도 이 클라이언트를 넣고, provider 없는 `restDocsWebTestClient(context)` 는 그때 실행 중인 테스트로 문서화합니다 ([시작하기](getting-started.md#8-문서화-테스트)).

다른 바인딩에는 `WebTestClient.Builder.restDocs(provider)` 를 붙입니다. 이 필터가 없으면 `andDocument` 가 `REST Docs configuration not found` 로 실패합니다. 서버 없이 묶은 바인딩에서 operationId 를 컨트롤러 메서드 이름으로 받으려면 `.recordHandlers()` 도 붙입니다([operationId](#operationid)).

```kotlin
// 컨트롤러 하나만 (스프링 컨텍스트 없이)
webTestClient = WebTestClient.bindToController(UserController())
    .configureClient()
    .restDocs(provider)
    .build()

// @SpringBootTest(webEnvironment = RANDOM_PORT) + @AutoConfigureWebTestClient 로 주입받은 클라이언트
webTestClient = injected.mutate()
    .restDocs(provider)
    .build()

// spring-security-test 의 mutateWith(mockUser()) 를 쓸 때
webTestClient = WebTestClient.bindToApplicationContext(context)
    .apply(springSecurity())
    .configureClient()
    .restDocs(provider)
    .build()
```

## 문서화 테스트

`exchange()` 결과(`ResponseSpec`)나 본문 검증 중의 `BodyContentSpec`, 타입으로 받은 본문(`BodySpec`) 에 `andDocument { }` 를 붙입니다. 앞의 둘은 `BodyContentSpec` 을 돌려주므로 `jsonPath` 검증을 이어 씁니다. import 는 `io.github.kardx.webtestclient.andDocument` 입니다.

```kotlin
import io.github.kardx.webtestclient.andDocument

@DisplayName("사용자 API")
class UserApiDocsTest : ApiDocsTest() {
    @Test
    @DisplayName("사용자 조회")
    fun getUser() {
        webTestClient.get()
            .uri("/api/users/{id}", 1L)
            .exchange()
            .expectStatus().isOk
            .andDocument {
                description = "ID로 사용자를 조회한다."
                pathParameters("id" pathMeans "사용자 ID")
                responseFields<UserResponse>()
            }
            .jsonPath("$.nickname").isEqualTo("홍길동")
    }

    @Test
    @DisplayName("사용자 검색")
    fun searchUsers() {
        webTestClient.get()
            .uri("/api/users?nickname={nickname}", "홍길동")
            .exchange()
            .andDocument {
                queryParameters("nickname" queryMeans "닉네임")
                responseFields<List<UserResponse>>()
            }
    }
}
```

본문을 객체로 받으려면 `expectBody<T>()`(`BodySpec`) 뒤에 `andDocument { }` 를 붙입니다. 문서에는 역직렬화한 객체가 아니라 실제로 받은 본문이 들어가고, 같은 `BodySpec` 을 돌려주므로 `returnResult()`·`value { }` 를 이어 씁니다.

```kotlin
import org.springframework.test.web.reactive.server.expectBody

val created = webTestClient.post()
    .uri("/api/users")
    .bodyValue(CreateUserRequest("홍길동"))
    .exchange()
    .expectStatus().isCreated
    .expectBody<UserResponse>()
    .andDocument {
        requestFields<CreateUserRequest>()
        responseFields<UserResponse>()
    }
    .returnResult()
    .responseBody
```

- **URI 는 템플릿으로 씁니다** (`uri("/api/users/{id}", 1L)`). 경로 변수 문서와 OpenAPI 경로가 템플릿에서 나옵니다. `uri { it.path(...).build() }` 처럼 빌더로 만들면 템플릿이 없어 `api-summary` 는 실제 경로를 보여 주고, epages 는 리소스를 만들지 못하고 실패합니다.
- 쿼리를 템플릿에 넣어도 됩니다(`?nickname={nickname}`). `api-summary` 와 OpenAPI 경로에서는 쿼리를 떼고, 값은 `query-parameters` 표에 들어갑니다. 한글 값은 `http-request` 스니펫에서 퍼센트 인코딩됩니다.
- 응답 `Flux` 가 끝나는 SSE 는 `andDocument { events(...) }` 로 문서화하고, MockMvc 와 같이 AsyncAPI 에도 들어갑니다. 끝나지 않는 스트림은 WebTestClient 에서 문서화하지 않습니다(본문을 끝까지 읽기 때문입니다). MockMvc 의 `performStream` 을 쓰거나 테스트에서 스트림이 끝나게 합니다.

## operationId

OpenAPI operationId 기본값은 요청을 처리한 컨트롤러 메서드 이름입니다([operationId](http-api.md#operationid)). WebTestClient 의 결과(`ExchangeResult`)에는 서버가 고른 핸들러가 없으므로, 서버 없이 묶은 클라이언트의 서버 쪽에 기록용 `WebFilter` 를 걸어 알아냅니다. 응답을 보내기 직전에 WebFlux 가 고른 핸들러(`HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE`)를 요청 id(`WebTestClient-Request-Id` 헤더)로 남기고 `andDocument` 가 그것을 읽습니다.

| 클라이언트 | 핸들러 | operationId |
|---|---|---|
| `restDocsWebTestClient(context, provider)` | 기록한다 | 컨트롤러 메서드 이름 |
| `bindToController`·`bindToApplicationContext`·`bindToWebHandler` + `.recordHandlers()` | 기록한다 | 컨트롤러 메서드 이름 |
| 위 바인딩에 `.recordHandlers()` 가 없음, `bindToServer`(실제 서버), 주입받은 클라이언트의 `mutate()` | 모른다 | 테스트 이름 (`test-name` 규칙과 같음) |
| 함수형 라우터(`RouterFunction`, `bindToRouterFunction`) | 어노테이션 컨트롤러 메서드가 아니다 | 테스트 이름 |

```kotlin
webTestClient = WebTestClient.bindToController(UserController())
    .recordHandlers()          // operationId 를 컨트롤러 메서드 이름으로
    .configureClient()
    .restDocs(provider)
    .build()
```

핸들러를 알 수 없는 테스트도 `openApi { operationId = "..." }` 로 직접 정하거나 `path` 규칙(HTTP 메서드 + 경로)을 고르면 테스트 이름과 상관없는 id 가 됩니다.

기록한 핸들러는 operationId 말고도 두 가지에 쓰입니다. 타입을 적지 않은 경로·쿼리·폼 파라미터의 OpenAPI 타입([파라미터 타입은 핸들러에서 정해진다](http-api.md#파라미터-타입은-핸들러에서-정해진다))과, 블록에서 정하지 않은 deprecated 표시(컨트롤러의 `@Deprecated`, [deprecated API](http-api.md#deprecated-api))입니다. 핸들러를 모르는 테스트는 파라미터에 `ofType` 을 적고 `deprecated(...)` 를 블록에서 정합니다.

## RestTestClient

Spring Boot 4(Spring Framework 7)에서는 `RestTestClient` 로 쓴 테스트도 문서화할 수 있습니다. 클라이언트를 `restDocs(provider)` 로 만들고, `exchange()` 뒤에 같은 `andDocument { }` 블록을 붙입니다.

```kotlin
import io.github.kardx.resttestclient.andDocument
import io.github.kardx.resttestclient.restDocs

@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@DisplayName("사용자 API")
class UserRestTestClientDocsTest {
    lateinit var client: RestTestClient

    @BeforeEach
    fun setUp(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        client = RestTestClient.bindTo(restDocsMockMvc(context, provider))
            .restDocs(provider)
            .build()
    }

    @Test
    @DisplayName("사용자 조회")
    fun getUser() {
        client.get()
            .uri("/api/users/{id}", 1L)
            .exchange()
            .expectStatus().isOk
            .andDocument {
                pathParameters("id" pathMeans "사용자 ID")
                responseFields<UserResponse>()
            }
    }
}
```

- `andDocument` 는 `ResponseSpec`(`expectBody().andDocument { }` 와 같음), `BodyContentSpec`(`jsonPath(...)` 검증 뒤), `BodySpec`(`expectBody(UserResponse::class.java)` 뒤, 이어서 `returnResult()`)에 있습니다. `ResponseSpec` 에서는 `expectBody()` 처럼 `BodyContentSpec` 을 돌려주므로 `jsonPath` 로 이어 갈 수 있고, `BodyContentSpec`·`BodySpec` 에서는 받은 spec 을 그대로 돌려줍니다.
- `restDocsMockMvc(context, provider)` 에 묶으면 MockMvc 테스트와 요청 경로가 같습니다: 앱의 Jackson 매퍼, `kardx.*` 설정, 켰다면 Spring Security 까지. `bindToController(...)` 등 다른 바인딩도 됩니다.
- 빌더에 `restDocs(provider)` 가 없으면 `andDocument` 가 `this RestTestClient has no REST Docs configuration. Build it with restDocs(provider).` 로 실패합니다.
- Spring REST Docs 의 `spring-restdocs-resttestclient` 모듈(REST Docs 4.1)이 필요 없습니다. 라이브러리가 교환을 직접 변환하므로 Boot 4.0·4.1 이 관리하는 REST Docs 4.0 에서 동작합니다. Gradle 플러그인이 더 넣는 것은 없고, `RestTestClient` 는 `spring-test` 에 들어 있습니다.

제한:

- **Boot 4 / Spring Framework 7 전용입니다.** 클래스는 같은 jar 에 들어 있지만 Spring 7 로 따로 컴파일되고, 부를 때만 로드됩니다. Boot 3 에는 `RestTestClient` 가 없습니다.
- **multipart 파트를 나누지 않습니다.** multipart 요청은 본문 그대로 문서화되어 `requestParts(...)`·`requestPartFields(...)` 가 파트를 찾지 못하고, OpenAPI 에 multipart 스키마도 없습니다. multipart API 는 MockMvc 로 문서화합니다.
- **핸들러 기반 기본값이 없습니다.** 요청을 처리한 컨트롤러 메서드를 알 수 없어 operationId 는 테스트 이름 `<클래스-대시>.<메서드-대시>`(`coupon-list-docs-test.list-coupons`)로 대체됩니다.
  - 블록에서 `openApi { operationId = "..." }` 로 정합니다(또는 `path` 규칙, [operationId](http-api.md#operationid)).
  - 같은 이유로 `deprecated` 를 컨트롤러의 `@Deprecated` 에서 추론하지 않습니다(블록에서 `deprecated(...)` 를 씁니다).
  - 파라미터 타입도 핸들러에서 가져오지 않습니다(`ofType` 없는 파라미터는 `string`).
  - Spring Framework 7 API 버전도 없습니다(`x-api-version` 없음, [API 버전](openapi.md#api-버전)).
  - 이 테스트들은 [문서화 커버리지](checks.md#문서화-커버리지)의 엔드포인트 목록에 아무것도 더하지 않습니다.
  - Boot 4 샘플 `samples/spring-boot-4/src/test/kotlin/com/example/restdocs/resttestclient/CouponListDocsTest.kt` 가 실행 중인 서버에 붙인 클라이언트(`bindToServer`)에서 operationId 를 정하는 예입니다.
- **상대 URI**(`baseUrl` 없이 MockMvc 에 묶은 클라이언트)는 MockMvc 문서처럼 `http://localhost` 기준으로 풉니다.
