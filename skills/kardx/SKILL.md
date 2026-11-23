---
name: kardx
description: Use when writing or maintaining API documentation tests with kardx (Spring REST Docs Kotlin DSL, group io.github.kardx-project, package io.github.kardx) in a Spring Boot project - andDocument { } blocks, responseFields<T>() extraction, @Description DTOs, the io.github.kardx-project Gradle plugin, generating REST Docs HTML / OpenAPI (Swagger) / AsyncAPI from tests, apiDocs / apiDocsCheck / coverage tasks, or fixing kardx[KDX-xxx] errors and DocumentationMismatchException failures.
---

# kardx: documentation tests with the Spring REST Docs Kotlin DSL

kardx turns passing Spring tests into REST Docs snippets, one AsciiDoc/HTML document per test class, an
OpenAPI 3 spec (via epages restdocs-api-spec) and AsyncAPI for SSE/WebSocket. Documented fields are
**verified against the real request/response**: a mismatch fails the test. Tests are Kotlin (the DSL is
infix/reified/receiver lambdas); application code and DTOs may be Java.

Everything below is for kardx `0.1`, Spring Boot 4.1.2, Kotlin 2.4.21, and was compiled and run against the
published artifacts.

## 1. Setup (Gradle Kotlin DSL)

`settings.gradle.kts`: the plugin is on Maven Central, so add `mavenCentral()` to plugin repositories.

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

`build.gradle.kts` (Boot 4, MVC app):

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"   // Boot BOM decides the REST Docs version
    id("org.asciidoctor.jvm.convert") version "4.0.5"       // REST Docs HTML
    id("com.epages.restdocs-api-spec") version "0.20.2"     // OpenAPI (optional). Boot 3: 0.19.4
    id("io.github.kardx-project") version "0.1"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")          // or -webflux
    implementation("org.springframework.boot:spring-boot-starter-validation")   // constraints in docs
    implementation("tools.jackson.module:jackson-module-kotlin")                // Boot 4 = Jackson 3
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }
```

**Do NOT add these by hand - the plugin adds them** (a module you declare yourself is left alone):

| Added | When |
|---|---|
| `kardx-annotations` (`compileOnly`) | always (`@Description`, `@Masked`, `@EnumCode`, `@AsciiDescription`) |
| `kardx` (`testImplementation`) | always (unless a Kotest artifact below brings it) |
| `kardx-kotest` / `kardx-kotest5` | test deps declare an `io.kotest` 6.x / 5.x **version** |
| `spring-restdocs-mockmvc` / `-webtestclient` | MVC app / WebFlux app (version from Boot BOM) |
| `com.epages:restdocs-api-spec-mockmvc` / `-webtestclient` | epages plugin applied (same version) |
| `kardx-boot` (`developmentOnly`) | Boot apps: logs doc URLs on startup |

It also removes epages' `spring-boot-starter-web` from `testRuntimeClasspath` of WebFlux apps, wires
snippet paths, index, Asciidoctor, OpenAPI post-processing, and copies docs to `static/docs` (served at
`/docs/index.html`). `kardx { addDependencies = false }` turns the dependency part off.

| | Boot 4 | Boot 3 |
|---|---|---|
| Spring Boot plugin | `4.1.2` | `3.5.16` |
| epages plugin (must match the Boot line) | `0.20.2` | `0.19.4` |
| Jackson Kotlin module | `tools.jackson.module:jackson-module-kotlin` | `com.fasterxml.jackson.module:jackson-module-kotlin` |
| springdoc (optional Swagger UI) | `springdoc-openapi-starter-webmvc-ui:3.2.0` | `...:2.8.17` |
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, needs `testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")` | `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` (in starter-test) |

Requirements: Gradle 7.6+ on JDK 17+ (Boot 4 needs Gradle 8.14+). `x = y` on plugin properties needs Gradle 8.2+; older: `.set(...)`.
Maven: Maven plugin `io.github.kardx-project:kardx-maven-plugin` (goals `api-docs`, `check`, `source-docs`), output in `target/` - see the `maven` page.

Test settings live in `src/test/resources/application-docs.yml` (activate with `@ActiveProfiles("docs")`).
To make Swagger UI show the test-verified spec, production `application.yml`:
`springdoc.swagger-ui.url: /docs/openapi3.yaml` and `server.mime-mappings.yaml: application/yaml`.

## 2. Test skeletons

### JUnit + MockMvc (default)

```kotlin
import io.github.kardx.dsl.pathMeans
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.junit.AutoRestDocumentationExtension
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("User API")                     // document title + OpenAPI tag
class UserApiDocsTest {

    @Test
    @DisplayName("Get user")                 // section title + OpenAPI summary
    fun getUser(mockMvc: MockMvc) {          // injected by the extension: REST Docs-enabled MockMvc
        mockMvc.perform(get("/api/users/{id}", 1L))      // always use a URI template
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "Gets a user by ID."
                pathParameters("id" pathMeans "User ID")
                responseFields<UserResponse>()
            }
    }

    @Test
    @DisplayName("Create user")
    fun createUser(mockMvc: MockMvc) {
        mockMvc.perform(post("/api/users").jsonContent(CreateUserRequest("Hong")))
            .andStatus(HttpStatus.CREATED)
            .andDocument {
                requestFields<CreateUserRequest>()
                responseFields<UserResponse>()
            }
    }
}
```

- Shared base class alternative (also where `RestDocsConfiguration.configure` goes):

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
abstract class ApiDocsTest {
    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpRestDocs(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        RestDocsConfiguration.configure { openApi.errorSchema<ErrorResponse>() }  // reverted after each test
        mockMvc = restDocsMockMvc(context, provider)   // io.github.kardx.mockmvc.restDocsMockMvc
    }
}
```

- Field style: `private val mockMvc = restDocsMockMvc(context)` (no provider; documents into the running test).
- Same block on the MockMvc Kotlin DSL and on `MockMvcTester` (one import, `io.github.kardx.mockmvc.andDocument`):

```kotlin
mockMvc.get("/api/users/{id}", 1L) {                  // org.springframework.test.web.servlet.get
    accept = MediaType.APPLICATION_JSON
}.andExpect {
    status { isOk() }
}.andDocument {
    pathParameters("id" pathMeans "User ID")
    responseFields<UserResponse>()
}
```

`MockMvcTester.create(mockMvc).get().uri("/api/users/{id}", 1L).exchange().andDocument { ... }` works the same (assert afterwards with `assertThat(result).hasStatusOk()`).

- `@WebMvcTest(UserController::class)` slice: same as above (take `MockMvc` as a method parameter); mock deps with `@MockitoBean`.

### WebTestClient (WebFlux app)

Import `io.github.kardx.webtestclient.andDocument`. Take `WebTestClient` as a test method parameter (or
`restDocsWebTestClient(context, provider)` from `io.github.kardx.webtestclient`). Suspend handlers need
`kotlinx-coroutines-reactor` in the app.

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("User API")
class UserApiDocsTest {
    @Test
    @DisplayName("Get user")
    fun getUser(webTestClient: WebTestClient) {
        webTestClient.get()
            .uri("/api/users/{id}", 1L)            // template, never uri { builder }
            .exchange()
            .expectStatus().isOk
            .andDocument {
                pathParameters("id" pathMeans "User ID")
                responseFields<UserResponse>()
            }
            .jsonPath("$.nickname").isEqualTo("Hong")
    }
}
```

Typed body: `.bodyValue(CreateUserRequest("Hong")).exchange().expectStatus().isCreated.expectBody<UserResponse>().andDocument { requestFields<CreateUserRequest>(); responseFields<UserResponse>() }.returnResult().responseBody`
(`expectBody` from `org.springframework.test.web.reactive.server`). Query in the template works: `.uri("/api/users?nickname={nickname}", "Hong")` + `queryParameters("nickname" queryMeans "Nickname")`.
RestTestClient (Boot 4): `RestTestClient.bindTo(restDocsMockMvc(context, provider)).restDocs(provider).build()`,
imports `io.github.kardx.resttestclient.andDocument` / `.restDocs`; no multipart parts, operationId = test name.

### Kotest (6.x)

Declare `testImplementation("io.kotest:kotest-runner-junit5:6.2.7")` and
`testImplementation("io.kotest:kotest-extensions-spring:6.2.7")`; the plugin adds `kardx-kotest`.

```kotlin
import io.github.kardx.kotest.RestDocsExtension
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
// + pathMeans, responseFields, andDocument, andStatus, SpringBootTest, ActiveProfiles, HttpStatus, get as above

@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("Product API")
class ProductApiDocsSpec(context: WebApplicationContext) : BehaviorSpec({
    val restDocs = extension(RestDocsExtension())
    val mockMvc = restDocsMockMvc(context, restDocs.provider)

    given("a product exists") {
        `when`("fetched by id") {
            then("returns it") {                  // document ONLY inside leaf tests (KDX-401)
                mockMvc.perform(get("/api/products/{id}", 1L))
                    .andStatus(HttpStatus.OK)
                    .andDocument {
                        pathParameters("id" pathMeans "Product ID")
                        responseFields<ProductResponse>()
                    }
            }
        }
    }
})
```

Section title = `given - when - then` names. Project-wide: `ProjectConfig` with `extensions = listOf(SpringExtension(), RestDocsExtension())`
and `restDocsMockMvc(context, RestDocsExtension.currentTestProvider)`. Kotest 5.x: `kardx-kotest5`, keep the extension instance in a property.

## 3. `andDocument { }` reference

Imports: entry operators from `io.github.kardx.dsl` (`pathMeans`, `queryMeans`, `formMeans`, `headerMeans`,
`cookieMeans`, `partMeans`, `linkMeans`, `eventMeans`, `type`, `subsection`, `STRING`, `NUMBER`, `BOOLEAN`,
`OBJECT`, `ARRAY`, `NULL`, `VARIES`, `ENUM`, `DATE`, `DATETIME`, `RelaxedSection`). **Type-argument shortcuts
are extension functions in `io.github.kardx.extractor`** (`responseFields<T>()`, `requestFields<T>()`,
`queryParameters<T>()`, `wrappedResponseFields<T>()`, `requestPartFields<T>()`, `fields<T>()`, `ofType`,
`withFields`, `responseFieldsOf`, `except`, `optionalAt`, `optionalUnder`, `describe`). Without the import
only the non-generic member overload is found and `responseFields<T>()` doesn't compile.

| Block member | Notes |
|---|---|
| `description = "..."` | `api-summary` snippet; also default OpenAPI description |
| `pathParameters(...)`, `queryParameters(...)`, `formParameters(...)` | vararg or `List` |
| `requestHeaders(...)`, `responseHeaders(...)`, `requestCookies(...)`, `responseCookies(...)` | headers allow undocumented extras |
| `requestFields(...)`, `responseFields(...)`, `requestParts(...)`, `requestPartFields("part", ...)`, `links(...)` | |
| `requestFile("...")`, `responseFile("...")` | whole body is a file; not combinable with fields/parts |
| `errorResponse(status, "...") { }` | declared, unverified 4xx/5xx |
| `deprecated = true` / `deprecated("reason")` | else follows controller `@Deprecated` |
| `relaxed = true` / `relaxed(RelaxedSection.X, ...)` | allow undocumented entries |
| `events(...)` | SSE |
| `openApi { }` | OpenAPI-only metadata |
| `snippets(...)` | raw REST Docs snippets |

Calling a member twice appends. Every section is **strict** by default.

### Entries and modifiers

```kotlin
queryParameters(
    "keyword" queryMeans "Search word" isOptional true,
    "size" queryMeans "Page size" ofType Int::class isOptional true withDefaultValue "20" example "10",
)
formParameters("username" formMeans "Login ID" constraint "4-20 chars", "password" formMeans "Password")
requestCookies("SESSION" cookieMeans "Session ID")
responseFields(
    "userId" type NUMBER means "User ID",
    "role" type ENUM means "Role" enumOf Role::class,                // OpenAPI enum
    "metadata" subsection OBJECT means "Free-form; children not verified",
    "internal" type STRING means "Internal" isIgnored true,           // not shown, not verified
    "nick" type STRING means "Nickname" deprecated "Use nickname",
    "phone" type STRING means "Phone" masked "middle 4 digits as *",  // marks only; app does masking
    "warehouse" type STRING means "Warehouse code, null if none" isOptional true,  // optional allows null
)
```

- Modifiers **return a copy**: keep shared entries in `val`s (`private val AUTH = "Authorization" headerMeans "Bearer token"`)
  and pass the result (`requestHeaders(AUTH isOptional true)`); `AUTH isOptional true` on its own line does nothing.
- Hand-written path/query/form params get their OpenAPI type from the controller parameter; use `ofType` when the
  handler is unknown (RestTestClient, functional routes, `bindToServer`).
- Hand-written bodies get no schema name (`api-sessions-12345`); set `openApi { responseSchema = "SessionResponse" }`.

### Extraction from types

```kotlin
responseFields<ProductResponse>()            // id, name, ...
responseFields<List<ProductResponse>>()      // [].id, ...
requestFields<CreateProductRequest>()        // ctor params with defaults are optional
queryParameters<ProductSearchCondition>()    // @ModelAttribute DTO; nullable/defaulted -> optional, defaults -> Default
responseFields(responseFieldsOf<ProductResponse>().optionalAt("tags"))
println(responseFieldsOf<ProductResponse>().except("sellerPhone").describe()) // preview the table
```

- Also: `responseFields<Page<T>>()` (Spring Data shape), `queryParameters<Pageable>()` (page, size, sort),
  `responseFieldsOf<T>().optionalUnder("items[]")` (array empty in test data), `responseFieldsOf<T>("data")` (prefix path).
- Jackson rules apply: `@JsonProperty`, `@JsonNaming`, mapper naming strategy, `@JsonIgnore`, `@JsonUnwrapped`,
  sealed `@JsonTypeInfo` (OpenAPI `oneOf`). Nullable / `Optional` -> optional. Java records/beans: unannotated
  reference types are optional (add `@NonNull`/`@NotNull` or `@NullMarked`).
- `except`/`optionalAt` fail on unknown paths (KDX-108). `except` on a field that IS in the response still fails strict verification.
- Not followed (fails verification -> hand-write or relax): `@JsonView`, `@JsonFormat(shape = OBJECT)`,
  custom serializers, `@JsonInclude(NON_EMPTY)` on empty collections (use `optionalAt`).
- Editing an extracted list (`optionalAt`, `+ hand-written`) keeps the schema name; the plugin merges differing
  shapes of the same name (a field is `required` only where required everywhere).

### Relaxed verification

`relaxed(RelaxedSection.RESPONSE_FIELDS)` in the block relaxes only response fields (document a subset by hand; path params stay strict).
`relaxed = true` relaxes **every** section (params, cookies, links too). Relaxed bodies lose OpenAPI field descriptions.

### Headers and auth

```kotlin
mockMvc.perform(get("/api/me").bearerToken("access-token").header("X-Request-Id", "req-123"))  // io.github.kardx.mockmvc.bearerToken
    .andStatus(HttpStatus.OK)
    .andDocument {
        requestHeaders(AUTH, "X-Request-Id" headerMeans "Tracking ID" isOptional true example "req-123")
        responseHeaders("X-Request-Id" headerMeans "Tracking ID" isOptional true)
        responseFields<MeResponse>()
    }
```

`Authorization: Bearer ...` creates the OpenAPI `bearerAuthJWT` scheme (real token never goes into the spec).
Tests authenticating without a header (`with(jwt())`, cookies): `openApi { bearerAuth() }` / `basicAuth()` / `oauth2("scope")`.
Spring Security filter chain is **not** applied by default: yml `kardx.apply-spring-security: true` + `spring-security-test`.

### Multiple status codes and errors

One test per status (`@DisplayName("Get product - not found (404)")`, `get("/api/products/{id}", 999L)`,
`.andStatus(HttpStatus.NOT_FOUND)`, `responseFields<ErrorResponse>()`); tests with the same method+path merge into one
OpenAPI operation with a response per status. Error-checking tests that shouldn't be documented: just don't call `andDocument`. In a 400 validation test the request fields must still be sent (`CreateProductRequest("", -1)` + `requestFields<CreateProductRequest>()`).
Name the error schema once: yml `kardx.open-api.error-schema: com.example.ErrorResponse` or `RestDocsConfiguration.configure { openApi.errorSchema<ErrorResponse>() }`.

Declared (unverified) errors in an existing test (`import io.github.kardx.extractor.fields`):

```kotlin
.andDocument {
    requestFields<CreateProductRequest>()
    responseFields<ProductResponse>()
    errorResponse(HttpStatus.UNAUTHORIZED, "Token missing or expired")
    errorResponse(HttpStatus.CONFLICT, "Name already registered", ErrorResponse(409, "Already registered"))
    errorResponse(422, "Prohibited item") {
        fields<ErrorResponse>()
        example(ErrorResponse(422, "Prohibited item", listOf(FieldErrorDetail("name", "banned word"))))
    }
}
```

Only 400-599, one per status per block, non-blank description; a declaration for the status the test actually got is dropped.
Generic/collection bodies need the block form (`fields<RestResponse<ErrorBody>>()`). Not wrapped by the response wrapper.
Project-wide: `kardx { openApiCommonResponses.put(500, "Server error") }` (401/403 only on secured operations).

### Common response wrapper

```yaml
kardx:
  response-wrapper:
    class: com.example.common.ApiResponse   # data class ApiResponse<T>(val code: Int, val message: String, val data: T?)
    data-path: data
```

```kotlin
.andDocument { wrappedResponseFields<ProductResponse>() }   // code, message, data, data.id ... schema ApiResponseProductResponse
```

`wrappedResponseFields<List<T>>()`, `wrappedResponseFields<T>(dataOptional = true)`, error with null data: `wrappedResponseFields<Nothing?>()`.
`open-api.schema-names-without-wrapper: true` names it `ProductResponse`.

### Files

```kotlin
// download: no responseFields; bytes show as <binary: N bytes>
val response = mockMvc.perform(get("/api/products/{id}/manual", 1L).param("lang", "ko"))
    .andStatus(HttpStatus.OK)
    .andDocument {
        pathParameters("id" pathMeans "Product ID")
        queryParameters("lang" queryMeans "Language (ko, en)" isOptional true withDefaultValue "ko")
        responseHeaders("Content-Type" headerMeans "File type", "Content-Disposition" headerMeans "Attachment name")
        responseFile("Product manual PDF")
    }
    .andReturnEntity<ByteArray>()                      // io.github.kardx.mockmvc.andReturnEntity

// multipart: file part + JSON part
val file = MockMultipartFile("file", "keyboard.png", MediaType.IMAGE_PNG_VALUE, ByteArray(128))
val meta = MockPart("meta", """{"alt":"front","primary":true}""".toByteArray())
meta.headers.contentType = MediaType.APPLICATION_JSON
mockMvc.perform(multipart("/api/products/{id}/images", 1L).file(file).part(meta))
    .andStatus(HttpStatus.OK)
    .andDocument {
        pathParameters("id" pathMeans "Product ID")
        requestParts("file" partMeans "Image file (PNG)", "meta" partMeans "Image metadata (JSON)")
        requestPartFields<ImageMeta>("meta")
        responseFields<ImageResponse>()
    }
```

Whole body is a file (not multipart): `put(...).contentType(MediaType.IMAGE_PNG).content(bytes)` + `requestFile("New main image (PNG)")`.

### SSE (stream that ends)

```kotlin
mockMvc.performAsync(get("/api/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))  // io.github.kardx.mockmvc.performAsync
    .andStatus(HttpStatus.OK)
    .andDocument {
        events(
            "connected" eventMeans "Connected" withFields ConnectedEvent::class,
            "notification" eventMeans "New notification" withFields NotificationEvent::class,
            "maintenance" eventMeans "Maintenance notice" isOptional true,
        )
    }
```

Controller must `complete()` the emitter. Never-ending streams: `io.github.kardx.sse.performStream(request, eventCount = 2)`.
WebSocket/STOMP: `MessageDocumentation`, `StompTestClient` (experimental) - see the `messaging` page.

### `openApi { }` per test

```kotlin
openApi {
    tags("Catalog")                  // added to the default tag (class @DisplayName)
    summary = "Get product"          // operation summary for all tests of this operation (default: test @DisplayName)
    description = "OpenAPI-only description"
    operationId = "getProductById"   // default: controller method name; colliding ones get a controller prefix
    requestSchema = "CreateProductRequest"
    responseSchema = "ProductEnvelope"   // instead of the automatic ApiResponseProductResponse
    bearerAuth()                     // security scheme without an Authorization header
    excluded = true                  // keep in REST Docs, drop from OpenAPI (e.g. alt. docs of the same op)
}
```

Also `basicAuth()`, `oauth2("users:read")` / `openIdConnect("openid")` (flows go in `kardx { openApiSecurity { } }`),
`deprecated = true` (same value as the block's `deprecated`), `responseSchema<T>()` / `requestSchema<T>()`.

Every test class that documents an operation adds its `@DisplayName` as a tag of that operation.

## 4. DTO annotations (production code, `io.github.kardx.annotation`)

```kotlin
@EnumCode("Product status")                       // + EnumDocumentationExtension -> code/enum-code.html
enum class ProductStatus(val description: String) { ON_SALE("On sale"), SOLD_OUT("Sold out") }

data class ProductResponse(
    @Description("Product ID") val id: Long,
    @Description("Status") val status: ProductStatus,              // value list "ON_SALE : On sale" + OpenAPI enum
    @Description("Seller phone") @Masked("middle 4 digits as *") val sellerPhone: String,
)

data class CreateProductRequest(
    @field:NotBlank @field:Size(max = 50) @Description("Name") val name: String,   // minLength 1, maxLength 50, required
    @field:Positive @Description("Price") val price: Long,                       // minimum 0 exclusive
    @Description("Tags") val tags: List<String> = emptyList(),                   // default -> optional
)
```

- `@Description` without use-site target (or `@field:`/`@get:`/`@property:`); `@param:` is NOT supported. Missing -> `No description`.
- Write Bean Validation as `@field:NotBlank` so Spring validates it; constraints fill the Constraints column and OpenAPI keywords
  (`@Size`, `@NotBlank`, `@Min/@Max`, `@Positive`, `@Pattern`, `@Email` ...). Grouped constraints stay text only.
- Enum constant descriptions come from a `description` property (`kardx.enum-description-property` to change).
- Kotlin `@Deprecated` on a property -> deprecated field; on a controller method -> deprecated operation.
- Enum code document: `class EnumDocs : EnumDocumentationExtension(packageName = "com.example", title = "Codes")`
  (`io.github.kardx.junit`), then `@ExtendWith(EnumDocs::class, AutoRestDocumentationExtension::class)` on one test class/base class.
- `@AsciiDescription("...")` on a test class/method adds a note next to the title.
- Already using Swagger `@Schema`? yml `kardx.description-annotations: swagger` (also `jackson`, `com.x.Ann#attr`).

## 5. Configuration

`build.gradle.kts` (most used; all optional):

```kotlin
kardx {
    language = "ko"                                   // texts the library writes (en default). Set here, not only in yml (KDX-005)
    openApiViewer { enabled = true }                  // build/api-docs/openapi.html (Redoc, no server needed)
    openApiCommonResponses.put(500, "Server error")   // unverified common responses
    coverage { failOnUndocumented = false }           // true: fail when an endpoint has no doc test
    openApiCheck { failOn = "breaking" }              // any (default) | breaking | none
}
openapi3 { title = "My API"; version = "1.0.0" }      // epages; defaults: project name/version, server "/"
```

Others: `index { title = "..." }`, `openApiInfo { description/contact/license/tag(...) }`, `openApiSecurity { oauth2 { ... } }`,
`apiVersioning { header("X-API-Version") }`, `html { enabled = false }` (skip asciidoctor locally), `postmanCollection { enabled = true }`,
`httpClient { enabled = true }`, `wireMock { enabled = true }`, `bootJar { enabled; path = "static/docs" }`, `generateOnRun`,
`sourceDocs { enabled = true }` (experimental: KDoc/Javadoc as descriptions; needs `@OptIn(ExperimentalRestDocsApi::class)` for name-only params).

`application-docs.yml` (`kardx.*`; unknown keys warn KDX-001):

```yaml
kardx:
  mask-sensitive-headers: true            # Authorization/Cookie -> ****, also password/access_token params
  common-include-files: _landing.adoc     # in src/docs/asciidoc, included at the top of every doc
  response-wrapper:
    class: com.example.common.ApiResponse
    data-path: data
  open-api:
    error-schema: com.example.common.ErrorResponse
    # operation-id-rule: handler-method | controller-and-method | path | test-name
    # global-headers: X-Tenant-Id
  # description-annotations: swagger
  # apply-spring-security: true
  # array-element-fields-optional: true
  # print-requests: true
```

Code equivalents: `RestDocsConfiguration.configure { ... }` (in `@BeforeEach`; auto-reverted per test) or
`RestDocsConfiguration.withSettings({ extraction.arrayElementFieldsOptional = true }) { ...request... }` for one test.
Never call `configure` in a test constructor/`init`/`@BeforeAll` (piles up / leaks).

## 6. Commands and outputs

```bash
./gradlew test             # snippets + AsciiDoc only
./gradlew apiDocs          # + index, HTML, OpenAPI, AsyncAPI, coverage; prints file:/// links
./gradlew apiDocsBaseline  # save spec to docs/openapi/openapi3.yaml (commit it)
./gradlew apiDocsCheck     # compare spec with baseline (fails per failOn)
./gradlew bootJar          # packages docs at /docs in the jar (bootRun / IDE run regenerate docs first)
```

| Output | Path |
|---|---|
| Everything gathered | `build/api-docs/` (`index.html`, `<test-class-kebab>.html`, `openapi3.yaml`, `asyncapi.html`, `coverage/coverage.md`) |
| Snippets | `build/generated-snippets/<class-kebab>/<method-kebab>/*.adoc`, `resource.json` |
| Generated AsciiDoc | `build/generated-asciidoc/<class-kebab>.adoc`, `code/enum-code.adoc` |
| OpenAPI (raw) | `build/api-spec/openapi3.yaml` |
| Spec-check report | `build/reports/kardx/openapi-changes.txt` |
| Served by the app | `http://localhost:8080/docs/index.html`, `/docs/openapi3.yaml` |

Hand-written docs: `src/docs/asciidoc/index-template.adoc` (marker line `// restdocs:api-index`); files starting with `_` are include-only.
Failed tests are dropped from docs; tests without `andDocument` produce nothing. Section order = method name (use `@TestMethodOrder` + `@Order`).

## 7. Rules and common mistakes

- **Never document with Spring's injected `MockMvc`** (`@Autowired` + `@AutoConfigureMockMvc`/`@WebMvcTest`) -> KDX-012;
  with `@AutoConfigureRestDocs` -> KDX-011. Take `MockMvc`/`WebTestClient` as a test parameter or use `restDocsMockMvc(...)`.
- Always `@ExtendWith(AutoRestDocumentationExtension::class)` (not REST Docs' `RestDocumentationExtension`) and a Spring test context.
- Always URI templates (`get("/api/x/{id}", 1)`), never pre-built strings or `uri { }` builders - otherwise no path params / epages fails.
- Import `responseFields<T>()` etc. from `io.github.kardx.extractor`; import `andDocument` from the client's package
  (`mockmvc`, `webtestclient`, `resttestclient`) - a wrong one leaves `andDocument` unresolved.
- Use `andStatus(HttpStatus.X)` (MockMvc) / `expectStatus()` (WebTestClient) so a wrong status fails before documenting.
- Empty arrays in test data fail `items[].x` -> fill data or `optionalUnder("items[]")`.
- Null values with `NON_NULL` mapper -> property must be nullable or `isOptional true`.
- `responseFile`/`requestFile` cannot be combined with fields/parts. `errorResponse` is not for 2xx/3xx.
- Don't add kardx / restdocs / epages client deps manually with the plugin; don't put both `kardx-kotest` and `kardx-kotest5`.
- Boot 4 + epages 0.19 (or Boot 3 + 0.20) only warns; always match the lines (0.20.x / 0.19.x).
- WebFlux app failing with `No bean named 'webHandler'`: Spring MVC is on the test classpath (declare `spring-boot-starter-webflux` directly so the plugin excludes it).

When a field test fails, read `DocumentationMismatchException`: it lists undocumented/missing paths and prints DSL lines to paste
(`"data.memo" type STRING means "..."`). Fix the docs, not the assertion.

| Code | Cause | Fix |
|---|---|---|
| KDX-001 | unknown `kardx.*` yml key | use the key after `Did you mean` |
| KDX-005 | language set only in yml | `kardx { language = "ko" }` in Gradle |
| KDX-006 | `springSecurityFilterChain` present but not applied | `kardx.apply-spring-security: true` (or `false` to silence) |
| KDX-011 | client built by `@AutoConfigureRestDocs` | remove it; use parameter / `restDocsMockMvc` |
| KDX-012 | client has no kardx REST Docs config | parameter injection or `restDocsMockMvc(context)`; RestTestClient `.restDocs(provider)` |
| KDX-013 | documented outside a running documented test | add the extension; call inside test methods |
| KDX-014 | client parameter without Spring test context | `@SpringBootTest` / `@WebMvcTest` / `@WebFluxTest` |
| KDX-101 | name-only param (`pathParameters("id")`) without `@param` doc | describe it: `"id" pathMeans "..."` |
| KDX-103/104 | file body with fields/parts, or blank/duplicate file decl | keep one of them |
| KDX-105/106 | invalid `errorResponse` / example not JSON | 400-599, non-blank, one per status, valid JSON |
| KDX-107 | extracted type has nothing to document (String, Map) | give a path or hand-write |
| KDX-108 | `except`/`optionalAt` path not in the type | fix the name (message lists valid ones) |
| KDX-109 | documented != actual | add/remove entries, or `isOptional true` |
| KDX-204 (warn) | path variable not documented | add `pathParameters("id" pathMeans "...")` |
| KDX-401 | Kotest request outside a leaf test / extension missing | move into `then`/`it`/`test`; register `RestDocsExtension` |
| KDX-402 | Kotest artifact vs version mismatch | `-kotest` for 6.x, `-kotest5` for 5.x |
| KDX-501 (warn) | two test classes write the same document | rename one |

## 8. Full documentation

Pages: `https://kardx-project.github.io/kardx/<page>/` - `getting-started`, `http-api` (DSL, extraction, errors, files,
OpenAPI names), `webtestclient`, `kotest`, `messaging` (SSE, WebSocket, STOMP, AsyncAPI), `configuration` (all settings, masking),
`build` (plugin tasks, outputs), `openapi`, `checks` (baseline, coverage), `outputs` (viewer, Postman, WireMock, export),
`asciidoc`, `customizing-snippets`, `maven`, `migration` (from plain Spring REST Docs), `ci`, `troubleshooting` (all KDX codes).
Root: `https://kardx-project.github.io/kardx/`. Everything in one file for LLMs: `https://kardx-project.github.io/kardx/llms-full.txt`.
