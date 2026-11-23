**English** | [한국어](ko/migration.md)

# Migrating from Spring REST Docs

[Document list](README.md)

<!-- toc -->
**Contents**

- [1. Dependencies and plugins](#1-dependencies-and-plugins)
- [2. Test base class](#2-test-base-class)
- [3. Converting document() line by line](#3-converting-document-line-by-line)
  - [Mapping table](#mapping-table)
  - [Extracting fields from types](#extracting-fields-from-types)
- [4. Mixing with existing snippets and index.adoc](#4-mixing-with-existing-snippets-and-indexadoc)
- [5. If you used epages resource()](#5-if-you-used-epages-resource)
- [6. Reusing Swagger annotation descriptions](#6-reusing-swagger-annotation-descriptions)
- [7. Checking after migration](#7-checking-after-migration)
- [From Spring Auto REST Docs](#from-spring-auto-rest-docs)
<!-- /toc -->

How to bring this library into a project that has tests written with `document("id", requestFields(fieldWithPath(...)), ...)`. You don't have to switch all at once. Existing tests keep running, and you can move tests to `andDocument { }` starting with the ones you write or change.

The examples below are based on Boot 4 and were verified by compiling and running them as is in a scratch project.

## 1. Dependencies and plugins

You don't have to remove your existing `spring-restdocs-mockmvc` and `asciidoctor` setup. When you add the Gradle plugin, it also adds the library dependencies (annotations, core, epages' MockMvc module when the epages plugin is applied, and the startup log module). `spring-restdocs-mockmvc` and `restdocs-api-spec-mockmvc` that you already declared are left as they are.

The plugin configures the Asciidoctor inputs, the snippet path and the `spring-restdocs-asciidoctor` extension, so remove your own `asciidoctor { }` and `asciidoctorExt` configuration after migrating.

Also remove an epages `openapi3 { outputDirectory = ... }` that points into the app's resources (`build/resources/main/static/docs`, `src/main/resources/static/...`) and any task that copies the spec into `static/` (`copyOasToSwagger` and the like): the plugin copies the docs and the spec into the app at `kardx.bootJar.path` (default `static/docs`). The plugin warns while such an `outputDirectory` is set.

```kotlin
plugins {
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("com.epages.restdocs-api-spec") version "0.20.2"        // only when you generate OpenAPI. Boot 3: 0.19.4
    id("io.github.kardx-project") version "0.1"         // also adds the library dependencies
}
```

Without any settings, the index includes every test class that produced a document. If you move your existing `index.adoc` to the template (`src/docs/asciidoc/index-template.adoc`), the entries go at its marker line (section 4). If you want to tie together only the new documents while mixing with the old ones, narrow the scope with `kardx { index { packageName = "com.example.demo"; baseClass = "com.example.demo.ApiDocsTest" } }`.

The library and the Gradle plugin are on Maven Central, so `pluginManagement` needs `mavenCentral()` too. The full setup and the Boot 3 differences are in [Getting started](getting-started.md#installation). Documents are built with `./gradlew apiDocs` (change scripts that called the `asciidoctor` task directly).

## 2. Test base class

| Before | After |
|---|---|
| `@ExtendWith(RestDocumentationExtension::class)` | `@ExtendWith(AutoRestDocumentationExtension::class)` (`io.github.kardx.junit`). Creates an AsciiDoc document per test class |
| `MockMvcBuilders.webAppContextSetup(context).apply(documentationConfiguration(provider)).build()` | `restDocsMockMvc(context, provider)` (`io.github.kardx.mockmvc`). UTF-8, pretty print. The Spring Security filter chain only with `apply-spring-security: true` (unlike `@AutoConfigureMockMvc`, it is not applied by default) |
| `@AutoConfigureRestDocs` + injected `MockMvc` | Take `MockMvc` as a test method parameter (`fun getUser(mockMvc: MockMvc)`, filled by `AutoRestDocumentationExtension`) or use `restDocsMockMvc(context, provider)` as above, and remove `@AutoConfigureRestDocs`. Why the injected `MockMvc` has to go is explained below the table |
| `WebTestClient.bindToApplicationContext(context).configureClient().filter(documentationConfiguration(provider)).build()` | `restDocsWebTestClient(context, provider)` (`io.github.kardx.webtestclient`). For other bindings, `.restDocs(provider)` on the builder ([WebTestClient](webtestclient.md)) |

Why not keep the injected `MockMvc` from `@AutoConfigureRestDocs`:

- Spring Boot decides its snippet directory only from the test class and method names (`ManualRestDocumentation`), which differs from this library's directory rules.
- Plain test methods are the same. For `@Nested` classes (`success/get-user` ↔ `user-api-test/success-get-user`), `@ParameterizedTest` (the same directory for every invocation ↔ `get-user/1`, `get-user/2`) and test methods with the same name but different parameters, snippets land in a different place, and that section would disappear from the generated document.
- Documenting with such a client now fails with [KDX-011](troubleshooting.md#kdx-011) when the directory differs (a warning once when it is the same).
- An injected `MockMvc` without REST Docs setup fails with [KDX-012](troubleshooting.md#kdx-012).

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs") // src/test/resources/application-docs.yml
abstract class ApiDocsTest {
    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpRestDocs(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        mockMvc = restDocsMockMvc(context, provider)
    }
}
```

`AutoRestDocumentationExtension` replaces `RestDocumentationExtension`, so don't register both. Existing `document(...)` tests keep running on this base class as they are. New tests don't need the base class: annotate the test class and take `MockMvc` as a parameter ([Getting started](getting-started.md#8-documentation-test)).

## 3. Converting document() line by line

The existing test:

```kotlin
@Test
fun before() {
    mockMvc.perform(get("/api/users/{id}", 1L))
        .andExpect(status().isOk)
        .andDo(
            document(
                "users/get",
                preprocessResponse(prettyPrint()),
                pathParameters(parameterWithName("id").description("User ID")),
                responseFields(
                    fieldWithPath("id").type(JsonFieldType.NUMBER).description("User ID"),
                    fieldWithPath("nickname").type(JsonFieldType.STRING).description("Nickname").optional(),
                ),
            ),
        )
}
```

The converted test. You don't write the snippet name (`"users/get"`). It is decided as `<test class>/<method>` (`migration-docs-test/get-user`), and the method's `@DisplayName` becomes the document section title. Pretty print is the default.

```kotlin
@Test
@DisplayName("Get user")
fun getUser() {
    mockMvc.perform(get("/api/users/{id}", 1L))
        .andExpect(status().isOk)
        .andDocument {
            pathParameters("id" pathMeans "User ID")
            responseFields(
                "id" type NUMBER means "User ID",
                "nickname" type STRING means "Nickname" isOptional true,
            )
        }
}
```

The imports are `io.github.kardx.dsl.*` (`pathMeans`, `type`, `NUMBER` …) and `io.github.kardx.mockmvc.andDocument`.

### Mapping table

| Spring REST Docs | This library (inside `andDocument { }`) |
|---|---|
| `document("id", ...)` | `.andDocument { ... }` (identifier is automatic) |
| `preprocessRequest(prettyPrint())`, `preprocessResponse(prettyPrint())` | Applied by default |
| `pathParameters(parameterWithName("id").description("ID"))` | `pathParameters("id" pathMeans "ID")` |
| `queryParameters(parameterWithName("page").description("Page").optional())` | `queryParameters("page" queryMeans "Page" isOptional true)` |
| `formParameters(parameterWithName("name").description("Name"))` | `formParameters("name" formMeans "Name")` |
| `requestHeaders(headerWithName("Authorization").description("Token"))` | `requestHeaders("Authorization" headerMeans "Token")` |
| `responseHeaders(headerWithName("ETag").description("Version"))` | `responseHeaders("ETag" headerMeans "Version")` |
| `requestCookies(cookieWithName("SESSION").description("Session"))` | `requestCookies("SESSION" cookieMeans "Session")` |
| `requestParts(partWithName("file").description("File"))` | `requestParts("file" partMeans "File")` |
| `requestPartFields("meta", fieldWithPath("name")...)` | `requestPartFields("meta", "name" type STRING means "Name")` |
| `links(linkWithRel("self").description("This resource"))` | `links("self" linkMeans "This resource")` |
| `fieldWithPath("id").type(JsonFieldType.NUMBER).description("ID")` | `"id" type NUMBER means "ID"` |
| `.optional()` | `isOptional true` or `optional()` |
| `.ignored()` | `isIgnored true` or `ignored()` |
| `subsectionWithPath("meta").type(JsonFieldType.OBJECT).description("...")` | `"meta" subsection OBJECT means "..."` |
| `.attributes(key("constraints").value("2 to 10 characters"))` | `constraint "2 to 10 characters"` |
| `relaxedResponseFields(...)`, `relaxedRequestFields(...)` (and the other `relaxed*` variants) | `relaxed(RelaxedSection.RESPONSE_FIELDS)`, `relaxed(RelaxedSection.REQUEST_FIELDS)` in the block. `relaxed = true` relaxes every section ([Relaxed verification](http-api.md#relaxed-verification)) |
| Snippets that can't be expressed above (`requestBody()`, custom `Snippet`) | `snippets(requestBody())` |
| Repeating `requestFields(fieldWithPath(...)...)` for each DTO | `requestFields<CreateUserRequest>()` (next section) |

The DSL's other modifiers (`example`, `withDefaultValue`, `enumOf`, `ofType`, `deprecated`) are described in [Entry syntax](http-api.md#entry-syntax).

One difference from Spring REST Docs: `fieldWithPath("x").optional()` changes the descriptor in place, but the DSL modifiers (`isOptional true`, `optional()`, `means`, …) return a copy. Entries kept in shared `val` constants stay unchanged, and a modifier whose result you don't pass on has no effect: write `requestFields(NICKNAME isOptional true)`, not `NICKNAME isOptional true` on its own line.

### Extracting fields from types

Places where you wrote field lists by hand can be extracted from DTOs instead. Nullable properties become optional, Bean Validation constraints go into the Constraints column, and `@Description` becomes the description ([Automatic field extraction](http-api.md#automatic-field-extraction)).

```kotlin
.andDocument {
    pathParameters("id" pathMeans "User ID")
    responseFields<UserResponse>()
}
```

Shortcuts such as `responseFields<T>()` and `requestFields<T>()`, the `…Of` functions and `describe()` are extension functions in `io.github.kardx.extractor`, so import them separately (`import io.github.kardx.extractor.responseFields`). The `…Of` functions return a list, and the shortcuts register directly in the block.

Before running the tests, check with `describe()` whether the extraction result matches your existing table ([Previewing extraction results](http-api.md#previewing-extraction-results)).

```kotlin
println(requestFieldsOf<ProductRequest>().describe())
// | Path  | Type   | Required | Description  | Constraints |
// |-------|--------|----------|--------------|-------------|
// | name  | String | true     | Product name |             |
// | price | Number | true     | Price        |             |
```

If the extraction result differs from the actual response, the test fails and tells you the missing fields, DSL lines to paste and hints about the cause ([Verification failure messages](http-api.md#verification-failure-messages)). If you use a common response wrapper (`{ code, message, data }`), register the [common response wrapper](http-api.md#common-response-wrapper) first.

## 4. Mixing with existing snippets and index.adoc

Both approaches can coexist in one project while you migrate.

| Existing | How to keep using it |
|---|---|
| `document("users/get")` tests | Keep running on the same base class. Snippets are still created in `build/generated-snippets/users/get/`. These tests get no section in the automatically generated documents (because their snippet directory isn't `<class>/<method>`) |
| Hand-written `src/docs/asciidoc/*.adoc` (for example, `include::{snippets}/users/get/http-request.adoc[]` in `users.adoc`) | Still becomes HTML. The plugin sets the `{snippets}` attribute. To include snippets of migrated tests, change the path to `{snippets}/<test-class>/<method>/`, or link to the automatically generated document (`<test-class>.adoc`) |
| Hand-written `src/docs/asciidoc/index.adoc` | The plugin builds `index.html` from `index-template.adoc`, so the names collide. Rename the file to `index-template.adoc` and add a `// restdocs:api-index` line where the list of test classes should go. Existing content and includes stay as they are |
| A preamble included in every document (`include::_header.adoc[]`) | `kardx.common-include-files: _header.adoc` adds it to every automatically generated document |

```asciidoc
= Our Service API
:toc: left

== Old documents

include::users.adoc[]

== API list

// restdocs:api-index
```

Sections in automatically generated documents are ordered by method name. To keep a fixed order like your existing documents, use `@TestMethodOrder(MethodOrderer.OrderAnnotation::class)` and `@Order` ([Section order](asciidoc.md#section-order)). Once everything is migrated, remove the hand-written documents and includes and keep only the index template.

## 5. If you used epages resource()

```kotlin
@Test
fun epagesBefore() {
    mockMvc.perform(post("/api/products").jsonContent(ProductRequest("Keyboard", 10000)))
        .andExpect(status().isOk)
        .andDo(
            document(
                "products/create",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Products")
                        .summary("Create product")
                        .description("Creates a product.")
                        .requestSchema(Schema.schema("ProductRequest"))
                        .deprecated(true)
                        .build(),
                ),
            ),
        )
}
```

When epages is on the classpath, `andDocument { }` also creates the same OpenAPI fragment (`resource.json`). You don't pass `resource()` separately.

```kotlin
@Test
@DisplayName("Create product")                      // summary (the class @DisplayName is the tag)
fun createProduct() {
    mockMvc.perform(post("/api/products").jsonContent(ProductRequest("Keyboard", 10000)))
        .andExpect(status().isOk)
        .andDocument {
            description = "Creates a product."
            requestFields<ProductRequest>()   // descriptions from @Schema(description)
            responseFields<ProductRequest>()
            openApi {
                deprecated = true
                tags("Products")
            }
        }
}
```

| `ResourceSnippetParameters` | After |
|---|---|
| `.tag("Products")` | The test class `@DisplayName`. To add more, `openApi { tags("Products") }` |
| `.summary("Create product")` | The test method `@DisplayName` |
| `.description("...")` | `description = "..."` |
| `.requestSchema(Schema.schema("X"))`, `.responseSchema(...)` | Usually not needed. The extracted type name is applied automatically. To set it, `openApi { requestSchema = "X" }`, `responseSchema<ApiResponse<X>>()` ([Schema names](http-api.md#schema-names)) |
| `.deprecated(true)` | `openApi { deprecated = true }` |
| `.pathParameters(...)`, `.requestFields(...)`, `.responseFields(...)` | The functions with the same names in the block. They go into the REST Docs tables and OpenAPI at once |
| `MockMvcRestDocumentationWrapper.document(...)`, `WebTestClientRestDocumentationWrapper.document(...)` | `.andDocument { }` |
| Excluding only from OpenAPI | `openApi { excluded = true }` |
| operationId taken from the identifier of `document("products/create", ...)` (`products.create`) | Defaults to the controller method name (`create`). To keep the existing id, `openApi { operationId = "products.create" }`, or the rule `open-api.operation-id-rule: test-name` (test class and method name) ([operationId](http-api.md#operationid)) |

While migrating, if an existing test and a new test document the same path and method, epages merges them into one operation. The operationId comes from the test moved to `andDocument` (the controller method name), and operations still using only `document()` get theirs from the identifier as before. If schemas have the same name but different structures, the Gradle plugin splits the names and logs it in the build log (`OpenAPI schema names: ProductRequest → DemoProductRequest`). Removing the old tests after migrating cleans this up.

## 6. Reusing Swagger annotation descriptions

If your DTOs already have `@Schema(description = "...")` for springdoc, you don't write descriptions again. Add one line to the test yml, and descriptions for properties without `@Description` are read from `@Schema`.

```yaml
# src/test/resources/application-docs.yml
kardx:
  description-annotations: swagger       # use Swagger @Schema(description) as the description
```

```kotlin
data class ProductRequest(
    @field:Schema(description = "Product name") val name: String,
    @field:Schema(description = "Price") val price: Long,
)
```

`jackson` (`@JsonPropertyDescription`) and your own annotations (`com.example.ApiField#text`) also work ([Reading descriptions from other annotations](http-api.md#reading-descriptions-from-other-annotations)).

## 7. Checking after migration

1. Run `./gradlew apiDocs` and open the sections of the migrated tests in `build/api-docs/index.html`.
2. If a table differs from before, check the extraction result with `println(responseFieldsOf<T>().describe())`, and change or add only the lines you need as hand-written entries ([Mixing extracted and hand-written fields](http-api.md#mixing-extracted-and-hand-written-fields)).
3. Clean up hand-written documents that included the `document("...")` snippet directories of migrated tests.

## From Spring Auto REST Docs

[Spring Auto REST Docs](https://github.com/ScaCap/spring-auto-restdocs) (ScaCap) reads Javadoc and types to fill in REST Docs snippets. Most of its ideas have a counterpart here, so a project using it can move test by test like the sections above. The main difference: Auto REST Docs inspects the handler's parameter and return types, while this library documents the type you name (`responseFields<UserResponse>()`), and REST Docs verifies it against the actual response.

| Spring Auto REST Docs | This library |
|---|---|
| JSON doclet or Dokka JSON output (`javadoc` task with the doclet, `org.springframework.restdocs.javadocJsonDir`) | `apiDocsSourceDocs` of the Gradle plugin, turned on with `kardx { sourceDocs { enabled = true } }` (experimental, off by default). No doclet, Dokka or system property to set up ([Descriptions from KDoc/Javadoc](http-api.md#descriptions-from-kdocjavadoc)) |
| Javadoc on fields and getters → field descriptions | KDoc/Javadoc on properties, fields, getters and record `@param`, after `@Description` and `description-annotations` |
| Javadoc on the controller method → `description` snippet | OpenAPI summary (first sentence) and description, and the `api-summary` description when the block has no `description = "..."`, with `sourceDocs { enabled = true; methodDocs = true }` |
| `@title` Javadoc tag → section title | `@DisplayName` on the test method |
| Bean Validation constraints → Optional column and constraints | The same: Required column, Constraints column and OpenAPI keywords ([Extraction rules](http-api.md#extraction-rules)) |
| `@Deprecated` / Javadoc `@deprecated` | Kotlin or Java `@Deprecated` on a property or handler ([Deprecated fields](http-api.md#deprecated-fields), [Deprecated APIs](http-api.md#deprecated-apis)). The Javadoc tag isn't read |
| `AutoDocumentation.requestFields()`, `responseFields()` | `requestFields<T>()`, `responseFields<T>()` in the block |
| `AutoDocumentation.pathParameters()` | `pathParameters("id")` takes the description from the method's `@param id` ([Parameters declared by name](http-api.md#parameters-declared-by-name--method-param)), or `pathParameters("id" pathMeans "...")`. The OpenAPI type comes from the handler ([Parameter types come from the handler](http-api.md#parameter-types-come-from-the-handler)) |
| `AutoDocumentation.requestParameters()`, `modelAttribute(...)` | `queryParameters<T>()` or `parametersOf<T>()` for a parameter object, `queryParameters("page" queryMeans "...")` by hand |
| `AutoDocumentation.requestHeaders()`, `authorization(...)` | `requestHeaders("Authorization" headerMeans "...")`. The OpenAPI security scheme is built from the request ([Headers and authentication](http-api.md#headers-and-authentication)) |
| `AutoDocumentation.links()` | `links("self" linkMeans "...")` |
| `AutoDocumentation.methodAndPath()`, `description()` | The `api-summary` snippet (method, path, description) |
| `AutoDocumentation.sectionBuilder()`, `auto-section.adoc` | `AutoRestDocumentationExtension`: one document per test class, a section per test, tied together by the index. Choose the snippets with `document-snippets` ([Choosing snippets for the document](customizing-snippets.md#choosing-snippets-for-the-document)) |
| `JacksonResultHandlers.prepareJackson(objectMapper)` | Not needed. `restDocsMockMvc` uses the app's mapper |
| `alwaysDo(document("{class-name}/{method-name}", ...))` | Not needed. The identifier is automatic |

Steps:

1. Apply the Gradle plugin (section 1) and remove the doclet or Dokka setup, the `javadoc` dependency of `test`, and the `javadocJsonDir` system property. Turn on `kardx { sourceDocs { enabled = true } }`; comments are then read by `apiDocsSourceDocs`. The feature is experimental and its scanner misses some declarations, so check the result against [Known limitations](http-api.md#known-limitations).
2. Change the test base class (section 2): drop `prepareJackson` and the `alwaysDo(document(...))` and `withDefaults(AutoDocumentation...)` setup; use `restDocsMockMvc(context, provider)`.
3. In each test, write an `andDocument { }` block that names the types: `requestFields<CreateUserRequest>()`, `responseFields<UserResponse>()`, plus path parameters and headers. Check a table with `describe()` before running ([Previewing extraction results](http-api.md#previewing-extraction-results)).
4. Move section titles from `@title` to `@DisplayName`. For the method's Javadoc as the OpenAPI summary and description and the description in the AsciiDoc document, set `kardx { sourceDocs { enabled = true; methodDocs = true } }`; otherwise write `description = "..."`.
5. Replace hand-written includes of `auto-section.adoc` with the generated class documents and the index (section 4).
6. Run `./gradlew apiDocs` and compare. If a description is missing, see [Troubleshooting](troubleshooting.md) (DTOs from a jar have no sources to read).
