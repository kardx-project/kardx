**English** | [한국어](ko/webtestclient.md)

# WebTestClient · RestTestClient

[Document list](README.md)

<!-- toc -->
**Contents**

- [Supported features](#supported-features)
- [Dependencies](#dependencies)
  - [When the docs YAML downloads instead of opening (WebFlux)](#when-the-docs-yaml-downloads-instead-of-opening-webflux)
- [Test base class](#test-base-class)
- [Documentation tests](#documentation-tests)
- [operationId](#operationid)
- [RestTestClient](#resttestclient)
<!-- /toc -->

How to generate documents in projects that test with WebTestClient instead of MockMvc (WebFlux apps and so on). A complete example is [samples/spring-boot-4-webflux](../samples/spring-boot-4-webflux) (a Boot 4 WebFlux app without the servlet stack, with `suspend`, `Flow` and `Mono` handlers and SSE). The `andDocument { }` block is **the same DSL** as with MockMvc, and the snippets, AsciiDoc documents and OpenAPI fragments it produces are the same too. Everything in [Documenting HTTP APIs](http-api.md) (field extraction, multiple status codes, schema names, verification failure messages) applies regardless of the client.

## Supported features

| Feature | MockMvc | WebTestClient | RestTestClient |
|---|---|---|---|
| `andDocument { }` block (parameters, headers, cookies, parts, fields, links, `openApi { }`, `snippets(...)`) | Supported | Supported (same block) | Supported (same block) |
| Entry points | `ResultActions`, `ResultActionsDsl`, `MvcTestResult` | `ResponseSpec`, `BodyContentSpec`, `BodySpec` (`expectBody<T>()`) | `ResponseSpec`, `BodyContentSpec`, `BodySpec` (`expectBody(T::class.java)`) |
| Client setup helper | `restDocsMockMvc(context, provider)` | `restDocsWebTestClient(context, provider)`, `WebTestClient.Builder.restDocs(provider)` | `RestTestClient.Builder.restDocs(provider)` |
| Request pretty print, URI rewritten to `http://localhost`, response pretty print | Supported | Supported | Supported (relative URIs are resolved against `http://localhost`) |
| Header masking (`mask-sensitive-headers`) | Supported | Supported | Supported |
| Verification failure diagnostics (`DocumentationMismatchException`) | Supported | Supported | Supported |
| OpenAPI (epages `resource.json`, security schemes, schema names, global headers, `openapi-extras.json`) | Supported | Supported | Supported, except multipart: parts are not split, so there is no multipart request body schema |
| WireMock stub (`wire-mock-stubs`) | Supported | Supported | Supported |
| `@Deprecated`, description annotations, response wrapper | Supported | Supported | Description annotations and response wrapper supported. The controller's `@Deprecated` is not read (handler unknown) |
| The app's Jackson mapper bean, `kardx.*` yml | Supported | Supported by `restDocsWebTestClient`. With other bindings, `AutoRestDocumentationExtension` reads only the yml | Bound to `restDocsMockMvc(context, provider)`: as MockMvc. Other bindings: `AutoRestDocumentationExtension` reads only the yml |
| Spring Security | With `apply-spring-security: true`, applies the filter chain, and `springSecurity()` if spring-security-test is present (not applied by default) | WebFlux applies the `WebFilter` beans. For `mutateWith(mockUser())`, `apply(springSecurity())` yourself | Whatever the bound `MockMvc` applies |
| `http.printRequests` | Supported | Supported (prints the `EntityExchangeResult`; only exchanges whose body you read with `expectBody`) | When bound to `restDocsMockMvc` |
| SSE that ends (`events(...)`) | `performAsync` | Supported (when the response `Flux` completes) | Not covered (use MockMvc) |
| SSE that never ends | `performStream` | Not supported | Not supported |
| AsciiDoc per test class, excluding failed tests, cleaning snippets (`AutoRestDocumentationExtension`) | Supported | Supported (independent of the client) | Supported (independent of the client) |
| Helper functions such as `jsonContent`, `bearerToken`, `andStatus` | Supported | WebTestClient's own API (`bodyValue`, `headers { it.setBearerAuth(...) }`, `expectStatus()`) | RestTestClient's own API (`body(...)`, `headers { it.setBearerAuth(...) }`, `expectStatus()`) |

## Dependencies

There is no separate artifact for WebTestClient. It is included in the core library (`kardx`), and the core library doesn't pull in MockMvc or servlet modules. With the Gradle plugin, a WebFlux app (one that declares `spring-boot-starter-webflux`) gets the core library, the REST Docs WebTestClient module and (if the epages plugin is present) the epages WebTestClient module automatically ([Dependencies added automatically](build.md#dependencies-added-automatically)).

```kotlin
plugins {
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("com.epages.restdocs-api-spec") version "0.20.2"   // only when you generate OpenAPI. Boot 3: 0.19.4
    id("io.github.kardx-project") version "0.1"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

Without the plugin, declare them yourself.

```kotlin
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.restdocs:spring-restdocs-webtestclient")   // version from the Boot BOM
    testImplementation("io.github.kardx-project:kardx:0.1")
    testImplementation("com.epages:restdocs-api-spec-webtestclient:0.20.2") {        // only when you generate OpenAPI. Boot 3: 0.19.4
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-web") // see the table below
    }
}
```

**In a WebFlux app, keep Spring MVC off the test classpath.** If Spring MVC is present, `@SpringBootTest` starts the WebFlux app as a servlet app, and the tests fail with `No bean named 'webHandler' available`.

| What comes along | Boot 3 | Boot 4 | Action |
|---|---|---|---|
| `spring-boot-starter-web` from the epages core (a runtime dependency in both epages 0.19 and 0.20) | Becomes a servlet app | Becomes a servlet app | The Gradle plugin excludes it. Without the plugin, `exclude` it |
| The core library (`kardx`) | Doesn't come along | Doesn't come along | Nothing. Older versions pulled in `spring-restdocs-mockmvc` and needed an `exclude` on Boot 3 |

- `spring-boot-starter-web` is **excluded automatically by the Gradle plugin.** If the project is a WebFlux app (it declares `spring-boot-starter-webflux` and doesn't directly declare `spring-boot-starter-web`, `-websocket` or `spring-webmvc`), it is excluded from `testRuntimeClasspath`. To turn this off, use `kardx { excludeServletStarterForWebFlux = false }`.
  - The check looks only at dependencies **declared directly** by the project in the configurations that `testRuntimeClasspath` inherits from (`implementation`, `testImplementation`, and so on). A `spring-boot-starter-webflux` that arrives transitively through another module (`project(":common")`) or an in-house starter is not seen, so nothing is excluded; a `spring-boot-starter-web` that arrives transitively is not seen either, so if you declared WebFlux directly, it gets excluded (apps that use both MVC and WebFlux). In such setups, turn it off with `excludeServletStarterForWebFlux = false` and `exclude` it yourself.
  - Only `testRuntimeClasspath` is affected. It is not excluded from the runtime classpaths of other source sets (`integrationTest` and so on) or JVM Test Suites (`testing.suites`), so add `exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")` to those configurations yourself.
- Documenting WebSocket and STOMP messages (`MessageDocumentation`) also works without MockMvc.

If you use WebTestClient in an MVC app (`MockMvcWebTestClient`, `bindToServer` against a running server), you don't need to exclude anything (if you didn't declare `spring-boot-starter-webflux`, the plugin doesn't exclude anything either). In this case the plugin treats it as an MVC app and adds `spring-restdocs-mockmvc`, so declare `spring-restdocs-webtestclient` yourself.

### When the docs YAML downloads instead of opening (WebFlux)

WebFlux (Netty) doesn't know the media type of `.yaml`, so it sends `/docs/openapi3.yaml` and `/docs/asyncapi.yaml` as `application/octet-stream`, and opening them in a browser downloads them. The servlet app's `server.mime-mappings` isn't used by WebFlux. Put a resource handler on the documents directory yourself and set the media types (`DocsResourceConfig` in the example).

```kotlin
@Configuration(proxyBeanMethods = false)
class DocsResourceConfig : WebFluxConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/docs/**")
            .addResourceLocations("classpath:/static/docs/")       // plugin bootJar.path default: static/docs
            .setMediaTypes(mapOf("yaml" to YAML, "yml" to YAML))
    }

    private companion object {
        val YAML = MediaType("text", "yaml", Charsets.UTF_8)
    }
}
```

The settings for the Gradle plugin (`io.github.kardx-project`) and the Asciidoctor and epages plugins are the same as in MockMvc projects ([Getting started](getting-started.md#3-buildgradlekts)).

## Test base class

`restDocsWebTestClient(context, provider)` creates a client bound to the application context without a server (`WebTestClient.bindToApplicationContext`). Like MockMvc's `restDocsMockMvc`, it applies the REST Docs filter, request pretty print, `http.printRequests`, the app's Jackson mapper and the `kardx.*` properties, and it also records the handler method used for the OpenAPI operationId ([operationId](#operationid)).

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

A base class is optional: `AutoRestDocumentationExtension` also passes this client to test methods that declare a `WebTestClient` parameter (`fun getUser(webTestClient: WebTestClient)`), and `restDocsWebTestClient(context)` without a provider documents into whichever test is running ([Getting started](getting-started.md#8-documentation-test)).

For other bindings, add `WebTestClient.Builder.restDocs(provider)`. Without this filter, `andDocument` fails with `REST Docs configuration not found`. To get the controller method name as the operationId with a binding that has no server, also add `.recordHandlers()` ([operationId](#operationid)).

```kotlin
// A single controller (without a Spring context)
webTestClient = WebTestClient.bindToController(UserController())
    .configureClient()
    .restDocs(provider)
    .build()

// A client injected with @SpringBootTest(webEnvironment = RANDOM_PORT) + @AutoConfigureWebTestClient
webTestClient = injected.mutate()
    .restDocs(provider)
    .build()

// When using mutateWith(mockUser()) from spring-security-test
webTestClient = WebTestClient.bindToApplicationContext(context)
    .apply(springSecurity())
    .configureClient()
    .restDocs(provider)
    .build()
```

## Documentation tests

Attach `andDocument { }` to the result of `exchange()` (`ResponseSpec`), to a `BodyContentSpec` during body assertions, or to a body received as a type (`BodySpec`). The first two return a `BodyContentSpec`, so you can continue with `jsonPath` assertions. The import is `io.github.kardx.webtestclient.andDocument`.

```kotlin
import io.github.kardx.webtestclient.andDocument

@DisplayName("User API")
class UserApiDocsTest : ApiDocsTest() {
    @Test
    @DisplayName("Get user")
    fun getUser() {
        webTestClient.get()
            .uri("/api/users/{id}", 1L)
            .exchange()
            .expectStatus().isOk
            .andDocument {
                description = "Gets a user by ID."
                pathParameters("id" pathMeans "User ID")
                responseFields<UserResponse>()
            }
            .jsonPath("$.nickname").isEqualTo("홍길동")
    }

    @Test
    @DisplayName("Search users")
    fun searchUsers() {
        webTestClient.get()
            .uri("/api/users?nickname={nickname}", "홍길동")
            .exchange()
            .andDocument {
                queryParameters("nickname" queryMeans "Nickname")
                responseFields<List<UserResponse>>()
            }
    }
}
```

To receive the body as an object, attach `andDocument { }` after `expectBody<T>()` (`BodySpec`). The document contains the body actually received, not the deserialized object, and the same `BodySpec` is returned, so you can continue with `returnResult()` or `value { }`.

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

- **Write URIs as templates** (`uri("/api/users/{id}", 1L)`). Path variable documentation and the OpenAPI path come from the template. If you build them with a builder, as in `uri { it.path(...).build() }`, there is no template, so `api-summary` shows the actual path and epages fails to create the resource.
- You can put the query in the template too (`?nickname={nickname}`). The query is stripped from `api-summary` and the OpenAPI path, and the values go into the `query-parameters` table. Korean values are percent-encoded in the `http-request` snippet.
- SSE whose response `Flux` completes is documented with `andDocument { events(...) }` and, as with MockMvc, goes into AsyncAPI too. Streams that never end are not documented with WebTestClient (because it reads the body to the end). Use MockMvc's `performStream`, or make the stream end in the test.

## operationId

The OpenAPI operationId defaults to the name of the controller method that handled the request ([operationId](http-api.md#operationid)). WebTestClient's result (`ExchangeResult`) doesn't include the handler the server chose, so a recording `WebFilter` is placed on the server side of a client bound without a server to find it out. Right before the response is sent, it records the handler WebFlux chose (`HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE`) under the request id (`WebTestClient-Request-Id` header), and `andDocument` reads it.

| Client | Handler | operationId |
|---|---|---|
| `restDocsWebTestClient(context, provider)` | Recorded | Controller method name |
| `bindToController`, `bindToApplicationContext`, `bindToWebHandler` + `.recordHandlers()` | Recorded | Controller method name |
| The bindings above without `.recordHandlers()`, `bindToServer` (real server), `mutate()` of an injected client | Unknown | Test name (same as the `test-name` rule) |
| Functional routers (`RouterFunction`, `bindToRouterFunction`) | Not an annotated controller method | Test name |

```kotlin
webTestClient = WebTestClient.bindToController(UserController())
    .recordHandlers()          // operationId from the controller method name
    .configureClient()
    .restDocs(provider)
    .build()
```

Even for tests whose handler is unknown, you get an id independent of the test name by setting it yourself with `openApi { operationId = "..." }` or choosing the `path` rule (HTTP method + path).

Besides the operationId, the recorded handler is used for two more things: the OpenAPI type of path, query, and form parameters written without a type ([Parameter types come from the handler](http-api.md#parameter-types-come-from-the-handler)), and the deprecation mark not set in the block (the controller's `@Deprecated`, [Deprecated APIs](http-api.md#deprecated-apis)). In tests whose handler is unknown, add `ofType` to the parameters and set `deprecated(...)` in the block.

## RestTestClient

On Spring Boot 4 (Spring Framework 7) you can document tests that use `RestTestClient`. Build the client with `restDocs(provider)` and attach the same `andDocument { }` block after `exchange()`.

```kotlin
import io.github.kardx.resttestclient.andDocument
import io.github.kardx.resttestclient.restDocs

@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@DisplayName("User API")
class UserRestTestClientDocsTest {
    lateinit var client: RestTestClient

    @BeforeEach
    fun setUp(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        client = RestTestClient.bindTo(restDocsMockMvc(context, provider))
            .restDocs(provider)
            .build()
    }

    @Test
    @DisplayName("Get user")
    fun getUser() {
        client.get()
            .uri("/api/users/{id}", 1L)
            .exchange()
            .expectStatus().isOk
            .andDocument {
                pathParameters("id" pathMeans "User ID")
                responseFields<UserResponse>()
            }
    }
}
```

- `andDocument` exists on `ResponseSpec` (same as `expectBody().andDocument { }`), `BodyContentSpec` (after `jsonPath(...)` checks) and `BodySpec` (`expectBody(UserResponse::class.java)`, then `returnResult()`). On `ResponseSpec` it returns a `BodyContentSpec` (like `expectBody()`), so you can continue with `jsonPath`; on `BodyContentSpec` and `BodySpec` it returns the same spec.
- Binding to `restDocsMockMvc(context, provider)` gives the same request path as MockMvc tests: the app's Jackson mapper, `kardx.*` settings and, if turned on, Spring Security. `bindToController(...)` and other bindings work too.
- Without `restDocs(provider)` on the builder, `andDocument` fails with `this RestTestClient has no REST Docs configuration. Build it with restDocs(provider).`
- It doesn't need Spring REST Docs' own `spring-restdocs-resttestclient` module (REST Docs 4.1): the library converts the exchange itself, so it works with the REST Docs 4.0 that Boot 4.0 and 4.1 manage. The Gradle plugin adds nothing extra; `RestTestClient` comes with `spring-test`.

Limits:

- **Boot 4 / Spring Framework 7 only.** The classes are in the same jar, compiled separately against Spring 7, and loaded only when you call them. Boot 3 has no `RestTestClient`.
- **Multipart parts are not split.** A multipart request is documented as its raw body: `requestParts(...)` and `requestPartFields(...)` find no parts, and OpenAPI gets no multipart schema. Document multipart APIs with MockMvc.
- **No handler-based defaults.** The library doesn't learn which controller method handled the request, so the operationId falls back to the test name, `<class-dash>.<method-dash>` (`coupon-list-docs-test.list-coupons`).
  - Set `openApi { operationId = "..." }` in the block (or use the `path` rule, see [operationId](http-api.md#operationid)).
  - For the same reason, `deprecated` isn't inferred from the controller's `@Deprecated` (set `deprecated(...)` in the block).
  - Parameter types aren't taken from the handler (parameters without `ofType` are `string`).
  - There is no Spring Framework 7 API version either (no `x-api-version`, [API versioning](openapi.md#api-versioning)).
  - These tests add nothing to the endpoint inventory of [documentation coverage](checks.md#documentation-coverage).
  - The Boot 4 sample `samples/spring-boot-4/src/test/kotlin/com/example/restdocs/resttestclient/CouponListDocsTest.kt` shows a client bound to the running server (`bindToServer`) with its operationId set.
- **Relative URIs** (a client bound to MockMvc without `baseUrl`) are resolved against `http://localhost`, as MockMvc documents do.
