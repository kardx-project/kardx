**English** | [한국어](ko/http-api.md)

# Documenting HTTP APIs

[Document list](README.md)

<!-- toc -->
**Contents**

- [Viewing in a browser](#viewing-in-a-browser)
- [Documentation DSL](#documentation-dsl)
  - [andDocument](#anddocument)
  - [Entry syntax](#entry-syntax)
  - [Parameter types come from the handler](#parameter-types-come-from-the-handler)
  - [Deprecated APIs](#deprecated-apis)
  - [Relaxed verification](#relaxed-verification)
- [Automatic field extraction](#automatic-field-extraction)
  - [Extraction rules](#extraction-rules)
  - [Empty extraction results](#empty-extraction-results)
  - [Spring Data pages](#spring-data-pages)
  - [Spring HATEOAS](#spring-hateoas)
  - [kotlinx.serialization](#kotlinxserialization)
  - [Unsupported shapes](#unsupported-shapes)
  - [Reading descriptions from other annotations](#reading-descriptions-from-other-annotations)
  - [Descriptions from KDoc/Javadoc](#descriptions-from-kdocjavadoc)
  - [Replacing the description source](#replacing-the-description-source)
  - [Mixing extracted and hand-written fields](#mixing-extracted-and-hand-written-fields)
  - [Previewing extraction results](#previewing-extraction-results)
  - [Deprecated fields](#deprecated-fields)
  - [Marking masked fields](#marking-masked-fields)
- [Verification failure messages](#verification-failure-messages)
- [Headers and authentication](#headers-and-authentication)
  - [Spring Security](#spring-security)
- [Common response wrapper](#common-response-wrapper)
- [Documenting multiple status codes](#documenting-multiple-status-codes)
  - [One class for error responses](#one-class-for-error-responses)
  - [Declared error responses](#declared-error-responses)
  - [Common error responses](#common-error-responses)
- [OpenAPI names and metadata](#openapi-names-and-metadata)
  - [Schema names](#schema-names)
  - [operationId](#operationid)
  - [OpenAPI tags and summaries](#openapi-tags-and-summaries)
  - [Enums as dropdowns](#enums-as-dropdowns)
  - [Regex path variables](#regex-path-variables)
  - [Single-value JSON bodies](#single-value-json-bodies)
- [File downloads](#file-downloads)
  - [What goes into snippets](#what-goes-into-snippets)
  - [File uploads in the request body](#file-uploads-in-the-request-body)
  - [OpenAPI output](#openapi-output)
- [What goes into the OpenAPI spec](#what-goes-into-the-openapi-spec)
  - [Types and formats](#types-and-formats)
  - [Constraints](#constraints)
  - [Sealed types and oneOf](#sealed-types-and-oneof)
  - [Defaults, examples, and constraint notes](#defaults-examples-and-constraint-notes)
  - [Multipart and cookies](#multipart-and-cookies)
  - [Other refinements](#other-refinements)
  - [Without the Gradle plugin](#without-the-gradle-plugin)
- [MockMvc helpers](#mockmvc-helpers)
<!-- /toc -->

This guide shows how to document requests and responses in MockMvc tests. WebTestClient (WebFlux apps) uses the same block ([WebTestClient](webtestclient.md)). You write fields by hand or extract them from types. For APIs with several status codes, such as success and error, see [Documenting multiple status codes](#documenting-multiple-status-codes).

## Viewing in a browser

If the app is running (`bootRun`, an IntelliJ run, `java -jar`), open the URL; otherwise open the file.

| What you want to see | App URL | File |
|---|---|---|
| Full API list (index) | `http://localhost:8080/docs/index.html` | `build/api-docs/index.html` |
| Document for one test class | `http://localhost:8080/docs/product-api-docs-test.html` | `build/api-docs/product-api-docs-test.html` |
| One API (one test method) | `http://localhost:8080/docs/product-api-docs-test.html#get-product-not-found` | Same file + `#get-product-not-found` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` | None (needs the app) |
| OpenAPI spec | `http://localhost:8080/docs/openapi3.yaml` | `build/api-docs/openapi3.yaml` |
| OpenAPI static viewer (Redoc · Scalar, when enabled) | `http://localhost:8080/docs/openapi.html` | `build/api-docs/openapi.html` (opens without the app) |

The `/docs` in the URL comes from the Gradle plugin's `bootJar.path` (default `static/docs`), and the port comes from the app settings. File paths assume `./gradlew apiDocs`; you can change them with the plugin's `outputDirectory`. The document name is the test class name, and the part after `#` is the test method name, both converted to kebab-case (`ProductApiDocsTest.getProductNotFound` → `product-api-docs-test.html#get-product-not-found`).

- Swagger UI shows this spec only if you have the springdoc dependency and the `springdoc.swagger-ui.url: /docs/openapi3.yaml` setting ([Getting started](getting-started.md#4-applicationyml-production)). To view it without the app, paste `openapi3.yaml` into the [Swagger Editor](https://editor.swagger.io).
- The OpenAPI static viewer is off by default; turn it on with the Gradle plugin's `kardx { openApiViewer { enabled = true } }` ([OpenAPI viewer](outputs.md#openapi-viewer-redoc--scalar)). It embeds the spec in the page, so it works without springdoc.
- Swagger UI's Try it out sends requests to the app that served the docs (`openapi3 { setServer("/") }`).
- For an API with several status codes, expand its operation in Swagger UI to see all of them in the response list.

## Documentation DSL

### andDocument

`ResultActions.andDocument { }` creates a REST Docs snippet for each entry you specify in the block. Calling the same function more than once appends entries. Every entry-list function (`pathParameters`, `requestFields`, …) accepts both varargs and a `List`.

You can use the same block when you send the request with the MockMvc Kotlin DSL or `MockMvcTester` instead of `mockMvc.perform(...)`. All three entry points produce the same snippets and OpenAPI fragments.

```kotlin
// MockMvc Kotlin DSL: ResultActionsDsl.andDocument
mockMvc.get("/api/users/{id}", 1L) {
    accept = MediaType.APPLICATION_JSON
}.andExpect {
    status { isOk() }
}.andDocument {
    pathParameters("id" pathMeans "User ID")
    responseFields<UserResponse>()
}

// MockMvcTester (Spring Framework 6.2+, Boot 3.4+): MvcTestResult.andDocument. Chain AssertJ assertions after it
val tester = MockMvcTester.create(mockMvc)       // mockMvc is restDocsMockMvc(context, provider)
val result = tester.get().uri("/api/users/{id}", 1L).exchange()
    .andDocument { pathParameters("id" pathMeans "User ID") }

assertThat(result).hasStatusOk()
```

All three functions use a single `import io.github.kardx.mockmvc.andDocument`. On Spring 6.1 and earlier, which have no `MockMvcTester`, the other two entry points work as usual.

With WebTestClient, attach the same block after `exchange()` (`import io.github.kardx.webtestclient.andDocument`); RestTestClient (Boot 4) works the same way (`import io.github.kardx.resttestclient.andDocument`). Setup helpers and supported features are covered in [WebTestClient · RestTestClient](webtestclient.md).

```kotlin
webTestClient.get()
    .uri("/api/users/{id}", 1L)
    .exchange()
    .expectStatus().isOk
    .andDocument { pathParameters("id" pathMeans "User ID") }
```

If fields or parameters don't match the actual request or response, the test fails with a message that explains the cause ([Verification failure messages](#verification-failure-messages)).

| Block member | Snippet it creates |
|---|---|
| `description = "..."` | `api-summary` (method, URL template, response status, description). Always created. If the template has a query (`?q={q}`), it is shown without it |
| `deprecated = true`, `deprecated("reason")` | A `*Deprecated*` line in `api-summary`, OpenAPI `deprecated: true` ([Deprecated APIs](#deprecated-apis)) |
| `pathParameters(...)` | `path-parameters` |
| `pathParameters("id")`, `queryParameters("page", "size")`, also `formParameters`, `requestHeaders`, `requestParts` with names only | The same snippets; each description comes from the handler method's `@param` tag. Needs the Gradle plugin and a known handler (MockMvc, or WebTestClient with handler recording) ([Parameters declared by name → method `@param`](#parameters-declared-by-name--method-param)) |
| `queryParameters(...)` | `query-parameters` |
| `formParameters(...)` | `form-parameters` |
| `requestHeaders(...)`, `responseHeaders(...)` | `request-headers`, `response-headers` |
| `requestCookies(...)`, `responseCookies(...)` | `request-cookies`, `response-cookies` |
| `requestParts(...)` | `request-parts` |
| `requestPartFields("meta", ...)` | `request-part-meta-fields` |
| `requestFields(...)`, `responseFields(...)` | `request-fields`, `response-fields` |
| `requestFile("...")`, `responseFile("...")` | `request-file`, `response-file`: what a file body is (description, media type, file name, size), and the OpenAPI body description ([File downloads](#file-downloads)) |
| `links(...)` | `links` |
| `events(...)` | `sse-events`, `response-fields-event-<name>` ([SSE](messaging.md#server-sent-events)) |
| `relaxed = true`, `relaxed(RelaxedSection...)` | Verifies every section, or only the listed ones, in relaxed mode (undocumented entries are allowed) ([Relaxed verification](#relaxed-verification)) |
| `errorResponse(status, "...")` | `error-responses`, and an OpenAPI response for an error the test doesn't produce ([Declared error responses](#declared-error-responses)) |
| `snippets(...)` | Adds REST Docs snippets that the members above can't express, or your own snippets, as is ([Adding custom snippets](customizing-snippets.md#adding-your-own-snippets)) |
| `openApi { ... }` | [OpenAPI settings](openapi.md#openapi--swagger-ui) |

### Entry syntax

```kotlin
"id" pathMeans "User ID" ofType Long::class
"page" queryMeans "Page number" ofType Int::class isOptional true withDefaultValue "0" example "2"
"name" formMeans "Name" constraint "2-10 characters"
"Authorization" headerMeans "Bearer token"
"SESSION" cookieMeans "Session ID"
"file" partMeans "Uploaded file"
"self" linkMeans "This resource"
"nickname" type STRING means "Nickname" isOptional true example "Jane Doe"
"metadata" subsection OBJECT means "Free-form metadata"    // child fields are not verified
"internal" type STRING means "Internal use" isIgnored true         // left out of the table and not verified
```

| Operator | Applies to | Meaning |
|---|---|---|
| `means` | Fields | Description |
| `pathMeans FromParamTag` (also `queryMeans`, `formMeans`, `headerMeans`, `partMeans`) | Parameters, headers, parts | Takes the description from the handler's `@param` tag while you still add modifiers: `"page" queryMeans FromParamTag isOptional true` ([Parameters declared by name → method `@param`](#parameters-declared-by-name--method-param)) |
| `isOptional true`, `optional()` | All (fields, parameters, headers, cookies, links, parts, SSE events) | The entry may be absent |
| `constraint` | Fields, parameters, headers | Constraints column. In OpenAPI and AsyncAPI, `Constraints: ...` after the description |
| `example`, `withDefaultValue` | Fields, parameters, headers | Shows the example or default in the description cell. OpenAPI `example` / `default` (as a number or boolean to match the type) |
| `enumOf TodoStatus::class` | Fields, parameters | Allowed values. OpenAPI `enum` (a Swagger UI dropdown) |
| `ofType Long::class`, `ofType typeOf<List<Long>>()` | Parameters | Value type. OpenAPI parameter `type` / `format` (if absent, the controller parameter type; if that is unknown too, `string`. See [Parameter types come from the handler](#parameter-types-come-from-the-handler)). It uses type analysis, so it lives in `io.github.kardx.extractor`, like `parametersOf` |
| `isIgnored true`, `ignored()` | Fields | Excluded from the documentation and from verification |
| `deprecated()`, `deprecated "reason"` | Fields, parameters | `Deprecated: reason` at the start of the description cell. OpenAPI and AsyncAPI `deprecated: true` ([Deprecated fields](#deprecated-fields)) |
| `masked()`, `masked "rule"` | Fields | Marks that the value is sent masked. `Masked: rule` at the start of the description cell. OpenAPI and AsyncAPI `x-masked: true` ([Marking masked fields](#marking-masked-fields)) |
| `withFields Event::class`, `withFields field list` | SSE events | Fields of the event `data`. With a class, extracted using the response rules ([SSE](messaging.md#server-sent-events)) |

`isOptional false` and `isIgnored false` change nothing. They don't revert an entry that is already optional or ignored, so when you turn it on conditionally you can pass the condition directly, as in `isOptional (page == null)`.

Modifiers (`means`, `isOptional`, `optional()`, `constraint`, `example`, `withDefaultValue`, `enumOf`, `ofType`, `deprecated`, `masked`, …) **return a copy** and leave the original entry alone. So you can keep shared entries in `val` constants: a modifier added in one test applies only to that test.

```kotlin
val AUTH = "Authorization" headerMeans "Bearer access token"

requestHeaders(AUTH)                       // required
requestHeaders(AUTH isOptional true)       // optional only in this test. AUTH itself stays required
```

Because the original isn't changed, a modifier whose result you don't use does nothing. Pass the result on, as above.

```kotlin
val nickname = "nickname" type STRING means "Nickname"
nickname isOptional true                   // no effect: the copy is thrown away
responseFields(nickname isOptional true)   // use the result
```

Changing the Spring REST Docs descriptor directly (`AUTH.descriptor.optional()`) still changes the shared object, so use the modifiers instead. Extracted lists (`responseFieldsOf<T>()`) are created anew on each call. To make only part of a list optional, use [`optionalAt`](#mixing-extracted-and-hand-written-fields).

Field types are `STRING`, `NUMBER`, `BOOLEAN`, `OBJECT`, `ARRAY`, `NULL`, `VARIES`, plus the more descriptive `ENUM`, `DATE`, and `DATETIME` (strings in JSON). `DATE` and `DATETIME` get `format: date` and `format: date-time` in OpenAPI and AsyncAPI. Without `ofType`, the OpenAPI type of a hand-written parameter comes from the controller parameter (next section).

### Parameter types come from the handler

If you don't give a type to a hand-written path, query, or form parameter (`pathMeans`, `queryMeans`, `formMeans`), its OpenAPI type is taken from the parameter with the same name on the controller method that handled the request. This covers `@PathVariable`, `@RequestParam`, and un-annotated simple-type parameters (the ones Spring binds to request parameters).

```kotlin
@GetMapping("/api/v1/products/{id}")
fun get(@PathVariable id: Long, @RequestParam(required = false) status: ProductStatus?) = ...

// Test: no ofType
pathParameters("id" pathMeans "Product ID")                                // integer, int64
queryParameters("status" queryMeans "Status" isOptional true)              // string + enum [SALE, SOLD_OUT, ...]
```

| Controller parameter | OpenAPI |
|---|---|
| `Long` | `integer` (`int64`) |
| `LocalDate` | `string` (`date`) |
| Enum | `string` + list of values (`enum`) |
| `List<Long>` in a query or form | `array` (`items: {type: integer, format: int64}`) |

- The rules are the same as the [Types and formats](#types-and-formats) table. Types that a single parameter can't represent (objects) stay `string`. Path variables don't use arrays.
- `ofType` and `enumOf` take precedence when given. Use them when you document a type different from the handler's, or when the handler is unknown.
- Parameters extracted with `parametersOf<T>()` keep the extracted type.
- The handler is known only with MockMvc and with a WebTestClient that records handlers (`restDocsWebTestClient`, `recordHandlers()`). Functional routes (`RouterFunction`), `bindToServer` and RestTestClient don't know the handler, so such parameters are `string`.
- The AsciiDoc tables don't change. Only the OpenAPI type is set.
- Java controllers must be compiled with `-parameters` for the names of `@RequestParam` and `@PathVariable` parameters without an explicit name to be found (the Spring Boot Gradle plugin turns it on by default). Parameters whose name can't be found are skipped.

If a path variable of the URL template isn't documented in the block, a warning is logged once per test when the OpenAPI fragment is built. An undocumented path variable appears in OpenAPI without a description, so paste the line from the warning into the block.

```
kardx[KDX-204]: GET /api/v1/products/{id} in ProductApiDocsTest.getProduct does not document path variable(s) id, so OpenAPI shows them without description. Add to the andDocument block: pathParameters("id" pathMeans "...")
```

### Deprecated APIs

Mark an API that should no longer be used in the block. The `api-summary` snippet gets a `*Deprecated*: <reason>` line (before the description), and the OpenAPI operation gets `deprecated: true`.

```kotlin
.andDocument {
    deprecated("Use GET /api/v2/users")   // with a reason
    // deprecated = true                  // without a reason
}
```

- It's the same value as `openApi { deprecated = true }`. Changing either one changes both.
- If you don't set it, it follows `@Deprecated` (Kotlin or Java) on the controller method or controller class. The `message` of Kotlin `@Deprecated` becomes the reason; Java `@Deprecated` is shown without a reason. Requests whose handler is unknown don't follow it (same conditions as the previous section).
- To leave it out of the documentation even though the handler is `@Deprecated`, turn it off with `deprecated = false`.
- If you write your own `api-summary` template, the model keys `deprecated` (true) and `deprecationNote` are present only for a deprecated API ([Templates provided by the library](customizing-snippets.md#templates-provided-by-the-library)).
- To mark a single field or parameter, see [Deprecated fields](#deprecated-fields).

```kotlin
@Deprecated("Use the hide API of the next version")
@DeleteMapping("/{id}")
fun delete(@PathVariable id: Long): ResponseEntity<Unit> = ...   // documented as deprecated without anything in the block
```

### Relaxed verification

Every documented section is verified strictly by default: an entry in the actual request or response that you didn't document fails the test. To allow undocumented entries in some sections only, list those sections with `relaxed(...)` (`import io.github.kardx.dsl.RelaxedSection`).

```kotlin
.andDocument {
    queryParameters<ProductSearchCondition>()
    responseFields<ProductResponse>()
    relaxed(RelaxedSection.RESPONSE_FIELDS)   // only the response fields are relaxed; query parameters stay strict
}
```

| `RelaxedSection` | Section |
|---|---|
| `PATH_PARAMETERS`, `QUERY_PARAMETERS`, `FORM_PARAMETERS` | `path-parameters`, `query-parameters`, `form-parameters` |
| `REQUEST_FIELDS`, `RESPONSE_FIELDS` | `request-fields`, `response-fields` |
| `REQUEST_PARTS`, `REQUEST_PART_FIELDS` | `request-parts`, `request-part-<part>-fields` (every part) |
| `REQUEST_COOKIES`, `RESPONSE_COOKIES` | `request-cookies`, `response-cookies` |
| `LINKS` | `links` |
| `EVENTS` | `sse-events` and the event `data` fields: undocumented event names and fields are allowed |

- Headers have no entry, because header verification always allows undocumented headers (it only checks that the documented ones are present).
- A relaxed section still fails when a documented required entry is missing.
- Calling `relaxed(...)` again adds sections. `relaxed = true` relaxes every section, and `relaxed = false` makes every section strict again (including those added with `relaxed(...)`).
- With `REQUEST_FIELDS` or `RESPONSE_FIELDS` relaxed, the OpenAPI schema of that body has no field descriptions, because epages has no relaxed verification.

`relaxed = true` relaxes **every** section, including path, query and form parameters, parts, cookies and links. To relax only the body and keep parameters checked, write `relaxed(RelaxedSection.REQUEST_FIELDS, RelaxedSection.RESPONSE_FIELDS)` instead.

## Automatic field extraction

In the `andDocument { }` block, register fields directly with the shortcuts that take a type argument.

| Shortcut in the block | Equivalent to | Use |
|---|---|---|
| `responseFields<T>(path = "")` | `responseFields(responseFieldsOf<T>(path))` | Response body fields |
| `requestFields<T>(path = "")` | `requestFields(requestFieldsOf<T>(path))` | Request body fields. Constructor parameters with default values are optional too |
| `wrappedResponseFields<T>(dataPath, dataOptional)` | `responseFields(wrappedResponseFieldsOf<T>(...))` | Common response wrapper + data fields ([Common response wrapper](#common-response-wrapper)) |
| `pathParameters<T>(path = "")` | `pathParameters(parametersOf<T>(path))` | Path parameters. Property names must match the URI template variables (`{id}`) |
| `queryParameters<T>(path = "")` · `formParameters<T>(path = "")` | `queryParameters(parametersOf<T>(path))` · `formParameters(parametersOf<T>(path))` | Query and form parameters. Nested objects become `a.b`; arrays become a single name |
| `requestPartFields<T>(part, path = "")` | `requestPartFields(part, requestFieldsOf<T>(path))` | Body fields of a multipart part |

```kotlin
responseFields<UserResponse>()            // id, nickname, status
responseFields<UserResponse>("data")      // data.id, data.nickname, ...
responseFields<List<UserResponse>>()      // [].id, [].nickname, ...
responseFields<Page<UserResponse>>()      // Spring Data Page: content[], totalElements, ... (see the section below)
queryParameters<UserSearchCondition>()
```

The shortcuts are extension functions in the `io.github.kardx.extractor` package (`import io.github.kardx.extractor.responseFields`). Called without a type argument (`responseFields("id" type NUMBER means "ID")`, `queryParameters()`), they are the regular hand-written list and vararg versions.

To modify or combine lists, use the `…Of` functions that return a list. `responseFieldsOf<T>(path)`, `requestFieldsOf<T>(path)`, `parametersOf<T>(path)`, and `wrappedResponseFieldsOf<T>(...)` also have forms that take a `KClass` or `KType` (`responseFieldsOf(UserResponse::class)`).

```kotlin
responseFields(responseFieldsOf<UserResponse>().except("internalNote"))            // removes that field and its children
responseFields(responseFieldsOf<UserResponse>().optionalAt("nickname", "profile.bio")) // only those fields become optional
responseFields(responseFieldsOf<OrderResponse>().optionalUnder("items[]"))         // that path and all its children become optional
queryParameters(parametersOf<UserSearchCondition>().except("debug"))               // removes the parameter with that name
queryParameters(parametersOf<UserSearchCondition>().optionalAt("keyword"))         // only that parameter becomes optional
queryParameters(parametersOf<UserSearchCondition>() + ("debug" queryMeans "Debug output").optional())
```

| Function | List it takes | What it does |
|---|---|---|
| `except(vararg paths)` | `List<RestDocsField>` | Removes the fields at those paths and their children (`path.x`, `path[]`, `path[].x`) |
| `optionalAt(vararg paths)` | `List<RestDocsField>` | Makes only the fields at those paths optional. Children stay as they are |
| `optionalUnder(vararg paths)` | `List<RestDocsField>` | Makes the fields at those paths and all their children optional. `"items[]"` keeps the array `items` required and makes only the element fields optional (for when the array in the test data is empty) |
| `except(vararg names)` | `List<RestDocsParam>` | Removes the parameters with those names |
| `optionalAt(vararg names)` | `List<RestDocsParam>` | Makes the parameters with those names optional (Required `false` in the table, `required: false` in OpenAPI) |

All of them live in the `io.github.kardx.extractor` package, return a new list without changing the original, and fail if you pass a path or name that isn't in the list (so a typo doesn't leave a field behind). You can also select parameters by `name` (`parametersOf<T>().filter { it.name != "debug" }`). The default OpenAPI schema name of an extraction result (`UserResponse`) survives these changes, and also survives adding hand-written fields with `+` or filtering with `filterNot` afterwards. If you mix lists extracted from another type or another path (`responseFieldsOf<X>("data")`), the body is no longer a single type, so no automatic name is assigned. In that case, set it with `openApi { responseSchema<T>() }`.

### Extraction rules

- **Properties**: Extracts the public properties that Jackson serializes, in primary-constructor order (properties outside the constructor in declaration order). Java records use components and Java beans use getters (with Jackson 2, `getURL` → `url`; with Jackson 3, `URL`).
- **Requests and responses**: For requests (`requestFieldsOf`, `parametersOf`), properties that can't be received — read-only `val`s outside the constructor and `@JsonProperty(access = READ_ONLY)` — are removed, and `var`s outside the constructor are optional. For responses, `access = WRITE_ONLY` (passwords and the like) is removed.
- **Names**: Determined in the same order as Jackson. `@JsonProperty` comes first, then `@JsonNaming` on the class (or a superclass), and finally the global rule. The global rule is the `extraction.propertyNameStrategy` setting if present, otherwise the mapper's `PropertyNamingStrategy` (for example, `spring.jackson.property-naming-strategy=SNAKE_CASE`). `@JsonIgnore` and `@JsonIgnoreProperties` on the class are excluded. `parametersOf<T>()` uses property names as is, without naming rules, because Spring's parameter binding (`@ModelAttribute`) doesn't go through Jackson.
- **Jackson structural annotations**: `@JsonUnwrapped` flattens child fields into the parent level (honoring `prefix`/`suffix`), and `@JsonValue` on a class replaces it with the type of that value. For `@JsonTypeInfo` on a sealed type, `include = PROPERTY` adds a discriminator field (`type`, with the `@JsonSubTypes` names as its values), `EXISTING_PROPERTY` attaches the value list to that property, and `WRAPPER_OBJECT` produces an object keyed by subtype name (`vehicle.car.doors`, all optional). `@JsonFormat(shape = NUMBER / STRING / BOOLEAN)` on a property changes the field type (`Instant` → Number, `BigDecimal` → String), and an `@JsonAnyGetter` map is treated as entries flattened into the parent object rather than as a field (see the table below).
- **Nulls in Java**: Java records and beans carry no nullability in their types, so annotations decide. Primitive types are required, `@Nullable` and `@CheckForNull` are optional, and `@NonNull`, `@Nonnull`, and `@NotNull` are required. If the class, an enclosing class, or the package has `@NullMarked` (jspecify) or `@NonNullApi` (Spring), unannotated types are required. Other reference types are optional (as in springdoc). Annotations are matched by name, regardless of package.
- **Descriptions**: By default, the `@Description` value; without it, `No description`. `@Description` is read at any of the positions below. To also read other annotations such as Swagger `@Schema`, see [Reading descriptions from other annotations](#reading-descriptions-from-other-annotations). With the Gradle plugin's experimental source docs turned on, KDoc/Javadoc fills in what no annotation describes ([Descriptions from KDoc/Javadoc](#descriptions-from-kdocjavadoc)).
- **optional**: Nullable types and `Optional<T>` are optional, and so are all their child fields.
- **Constraints**: Found on the property, the field (`@field:`), the getter, or the constructor parameter. If there is a `message`, it is used with placeholders such as `{min}` filled in; otherwise the REST Docs default text is used. Element constraints (`List<@NotBlank String>`) are added as `Items: NotBlank - ...`, and validation groups, if any, follow the name (`NotNull [OnCreate] - ...`). If a nullable property or a property with a default value has `@NotNull`, `@NotBlank`, or `@NotEmpty` (default group), the Required column says `true`, but REST Docs verification still treats it as optional, so validation-failure tests that omit the value or send `null` still pass. In OpenAPI they become keywords ([Constraints](#constraints)).
- **Enums**: The value list (`ACTIVE : Active`) is appended to the description and exported as OpenAPI `enum`. Values are what actually appears in JSON (the `@JsonValue` method, `@JsonProperty` on the constant, otherwise the constant name). Constant descriptions are read from a `description` field.

| Type | Result |
|---|---|
| Object `address: Address` | `address` (Object) + `address.street` … |
| Array `boxes: List<Box>` | `boxes` (Array) + `boxes[].width` …. If the elements are nullable (`List<Box?>`), the child fields are optional and actual `null` elements also pass (an ignored row `boxes[]` that doesn't appear in the table) |
| Enum array `levels: List<Level>` | A single `levels` (Array) row. In OpenAPI and AsyncAPI, `type: array`, `items.enum` |
| Nested array `List<List<Box>>` | `cells` + `cells[][].width` … |
| Value array `List<String>` | A single `tags` (Array) row |
| `Map`, self-reference, maximum depth exceeded | A single subsection row (child fields are not verified) |
| Jackson tree types such as `JsonNode`, `Any`, unresolved type arguments | The structure is unknown, so a single VARIES subsection row. As an array element, the array becomes the subsection |
| Interface or abstract class `pet: Pet`, class with `@JsonAnyGetter` | `pet` (Object **subsection**) + properties of the declared type, `pet.name`. Fields that exist only on the implementation (`pet.age`) and flattened keys still pass. For an array, the array is the subsection |
| Kotlin `object` | Expanded like a regular object if it has properties. Otherwise a single empty-object row |
| Spring Data `Page`, `Slice`, `Sort`, `ProblemDetail` | The shape Jackson actually writes ([Spring Data pages](#spring-data-pages), [Unsupported shapes](#unsupported-shapes)) |
| `Pair`, `Triple` | Object with `first`, `second`(, `third`) |
| Spring HATEOAS `EntityModel`, `CollectionModel`, `PagedModel`, `RepresentationModel` subclasses | The HAL shape: `_links`, `_embedded.<rel>[]`, `page.*` ([Spring HATEOAS](#spring-hateoas)) |
| kotlinx `@Serializable` class | The serial descriptor: `@SerialName` names, defaults optional ([kotlinx.serialization](#kotlinxserialization)) |
| Sealed types | Union of the subtype fields. Fields present only in some subtypes are optional. In OpenAPI each subtype becomes a `oneOf` variant ([Sealed types and oneOf](#sealed-types-and-oneof)) |
| Value class, `Optional<T>` | A single row for the inner value |
| Top-level `List<User>` | `[].id` …. With the path `"data"`, `data` (Array) + `data[].id` |
| Top-level `String`, `Map`, `List<String>` | A single row only when there is a path. Otherwise an empty list and a warning log (`found nothing to document`) |

| Kotlin / JDK type | Documented type |
|---|---|
| `String`, `Char`, `ByteArray`, `UUID`, `URI`, `URL`, `Path`, `File`, `InetAddress`, `ByteBuffer`, `Class`, `Locale`, `Date`/`Calendar` (and subtypes such as `Timestamp`), `java.time.*` | String |
| `Number` types such as `Int`, `Long`, `BigDecimal` | Number |
| `Boolean` | Boolean |
| Enum | String (with the constant list) |
| Arrays, `Collection` | Array |
| `Map`, data classes, regular classes | Object |
| `Any`, generics without type arguments, other JDK classes | Varies (`Any` and type arguments are subsections) |

| `@Description` position | Example |
|---|---|
| Kotlin constructor property (no use-site target) | `@Description("ID") val id: Long` |
| Property, backing field | `@property:Description(...)`, `@field:Description(...)` |
| Getter, setter | `@get:Description(...)`, `@set:Description(...)` (`var`) |
| Property in the class body | `@Description("...") val total: Int = 0` |
| Java field, getter, setter | `@Description("ID") private Long id;` |
| Java record component | `record User(@Description("ID") Long id)` |

The constructor-parameter-only target (`@param:`) is not supported. Supporting it would cause compiler warnings on every property without a use-site target in Kotlin 2.2 and later. Without a target, the annotation goes on the property, so there is no functional difference.

### Empty extraction results

If extraction finds no fields or parameters (a single-value `String` or `Long` body, a `Map` without a path, a class without properties), it logs the warning `kardx[KDX-107]: responseFieldsOf<...>() found nothing to document`. An empty field list produces no table, so the body appears only in the `http-response` example. If you need a field table for a single-value body, write it by hand.

`pathParameters`, `queryParameters`, and `formParameters` create a table even for an empty list. So if `parametersOf<T>()` is empty but the request has parameters, REST Docs fails with `Query parameters with the following names were not documented: [sort]`, telling you what is missing.

### Spring Data pages

Spring Data's `Page`, `Slice`, and `Sort` are `Iterable`, but Jackson writes them as objects. The extractor extracts the shape Jackson actually writes (it detects them by class name, without depending on spring-data). The shapes are the same in Boot 3 (Spring Data 3.5) and Boot 4 (Spring Data 4.1); only the key order differs.

| Type | Extraction result |
|---|---|
| `Page<T>`, `PageImpl<T>` | `content[]`, `pageable` (`pageNumber`, `pageSize`, `sort.*`, `offset`, `paged`, `unpaged`), `totalElements`, `totalPages`, `last`, `first`, `size`, `number`, `sort` (`empty`, `sorted`, `unsorted`), `numberOfElements`, `empty` |
| `Page<T>` + `VIA_DTO` mode | `content[]`, `page.size`, `page.number`, `page.totalElements`, `page.totalPages` (the same shape as Spring Data `PagedModel`) |
| `Slice<T>` | The `Page` shape without `totalElements` and `totalPages` |
| `PagedModel<T>` (`org.springframework.data.web`) | `content[]`, `page.*`. Spring HATEOAS `PagedModel` is extracted in its HAL shape ([Spring HATEOAS](#spring-hateoas)) |
| `parametersOf<Pageable>()` | Query parameters `page`, `size`, `sort` (all optional) |
| `parametersOf<Sort>()` | `sort` |

```kotlin
mockMvc.perform(
    get("/api/articles").param("page", "0")
        .param("sort", "id,desc"),
).andDocument {
    queryParameters<Pageable>()
    responseFields<Page<ArticleResponse>>()
}
```

- `VIA_DTO` is the shape Spring Data uses with `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` or `spring.data.web.pageable.serialization-mode=via-dto`. It is detected by whether a mixin for `PageImpl` is registered on the mapper, so documenting with `restDocsMockMvc` (which uses the app's mapper) matches automatically.
- Parameter names are the Spring Data defaults. If you changed them with `spring.data.web.pageable.page-parameter`, `one-indexed-parameters`, or a `@Qualifier` prefix, write them by hand.
- Jackson can't serialize a `PageImpl(list)` created without a page request (`getOffset` on `Unpaged` throws). It fails in the real app too, so pass a `PageRequest`.
- For public API responses, Spring Data `PagedModel` (which Spring Data also recommends) or your own page DTO is more stable. Your own `PageResponse<T>` is extracted like any regular class.

### Spring HATEOAS

Spring HATEOAS models are extracted in their HAL shape (`application/hal+json`, the Spring HATEOAS default). They are detected by class name, so the library doesn't depend on spring-hateoas.

| Type | Extraction result |
|---|---|
| `EntityModel<T>` | The fields of `T` at the same level + `_links` |
| Class extending `RepresentationModel` | Its own fields + `_links` (instead of a `links` property) |
| `CollectionModel<T>` | `_embedded.<rel>[]` (the elements) + `_links` |
| `PagedModel<T>` (`org.springframework.hateoas`) | `_embedded.<rel>[]` + `_links` + `page.size`, `page.totalElements`, `page.totalPages`, `page.number` |

```kotlin
@Relation(collectionRelation = "books")
class BookResponse(val id: Long, val title: String)

mockMvc.perform(get("/api/books").param("page", "0"))
    .andDocument {
        responseFields<PagedModel<EntityModel<BookResponse>>>()
        // _embedded, _embedded.books[], _embedded.books[].id, _embedded.books[].title, _embedded.books[]._links,
        // _links, page, page.size, page.totalElements, page.totalPages, page.number
    }
```

- `_links` is a subsection keyed by relation name: which links appear can't be known from the type, so the links under it are not verified. `_links` and `_embedded` are optional, because Spring HATEOAS leaves them out when empty.
- The collection relation (`<rel>`) is chosen as Spring HATEOAS does: `@Relation(collectionRelation)` on the element class (`T` of `EntityModel<T>`), else the plural from evo-inflector when it is on the classpath (`books`), else `<type name>List` (`bookResponseList`).
- The HAL keys (`_links`, `_embedded`, `page.*`) don't follow naming rules (`@JsonNaming`, `extraction.propertyNameStrategy`); the fields of your classes do.
- Limits: HAL only (not HAL-FORMS, Collection+JSON or UBER); collections whose elements have different types are not supported; `CollectionModel<*>` (element type unknown) makes `_embedded` a subsection.
- Spring Data's `org.springframework.data.web.PagedModel` is a different class with a different shape (`content[]`, `page.*`): see [Spring Data pages](#spring-data-pages).

### kotlinx.serialization

When kotlinx-serialization-json is on the test classpath, Spring MVC and WebFlux serialize `@Serializable` classes with kotlinx instead of Jackson. The extractor does the same and reads those classes from their serial descriptor.

```kotlin
@Serializable
class UserResponse(
    @SerialName("user_id") val id: Long,
    val nickname: String? = null,    // has a default: optional
    @Transient val cache: String = "", // left out
)
```

- Names come from `@SerialName`; `@Transient` properties and properties without a backing field are left out. Jackson annotations and naming rules are not used for these classes.
- A property with a default value is optional in requests and in responses (with the `Json` default `encodeDefaults = false` it is not written). `@EncodeDefault` makes it required in responses.
- A custom serializer that writes a single value (`@Serializable(with = ...)` with a primitive descriptor) is documented as that value's type (String, Number, Boolean).
- Sealed types get the discriminator `type` (or the name from `@JsonClassDiscriminator`) with the subtypes' serial names as values; OpenAPI `oneOf` works as for Jackson ([Sealed types and oneOf](#sealed-types-and-oneof)).
- It is on by default. If your app serializes `@Serializable` classes with Jackson anyway, turn it off with `extraction.kotlinxSerialization = false` (yml `kardx.kotlinx-serialization: false`, [Configuration](configuration.md#yml-settings)).
- Limits: settings of the app's `Json { }` instance (`namingStrategy`, `classDiscriminator`, `encodeDefaults`) are not known to the extractor. If you changed them, adjust the list (`except`, `optionalAt`) or write the fields by hand.

### Unsupported shapes

The extractor looks only at types, so it can't follow the serialization-changing features below. REST Docs verifies against the actual response, so the documentation never comes out wrong; the test fails instead. Use the workarounds.

| Feature | Symptom | Workaround |
|---|---|---|
| `@JsonView` | Fields excluded from the view report `not found in the payload` | `responseFieldsOf<T>().except("secret")` |
| `@JsonFormat(shape = OBJECT / ARRAY)` (such as an enum as an object) | Child keys are undocumented | Change it to `"tier" subsection OBJECT means "..."` or write it by hand |
| `@JsonTypeInfo(include = WRAPPER_ARRAY)` | `["car", {...}]` can't be expressed as a path | `relaxed = true` and hand-written fields |
| `@JsonSerialize(using = ...)`, custom serializers | The shape differs | Write it by hand |
| Empty collections with `@JsonInclude(NON_EMPTY)` | `not found in the payload` | `responseFieldsOf<T>().optionalAt("tags")` |
| `@JsonAlias` | Aliases apply only to deserialization (requests), so the documentation shows the canonical name. Tests that send the alias fail | Send the canonical name in test requests, or write the alias field by hand |
| Top-level interface or `@JsonAnyGetter` type (`responseFieldsOf<Pet>()`), extension members of a top-level `ProblemDetail` | Implementation fields and extension keys are undocumented (the top level can't be a subsection) | Extract from the implementation class, or use `relaxed = true` |

`ProblemDetail` is extracted with the RFC 9457 fields `type`, `title`, `status`, `detail`, and `instance`, all optional. This is because Spring's Jackson mixin drops empty values (Spring 7 also drops `about:blank`, the default `type`) and flattens the `properties` map into the parent object. A `ProblemDetail` inside another object is a subsection, so its extension members also pass.

### Reading descriptions from other annotations

If your DTOs already have descriptions in Swagger `@Schema` or Jackson `@JsonPropertyDescription`, you don't need to add `@Description` again. Configure the library to read that annotation and the same descriptions appear in the documentation. It can read your own annotations too.

The shortest way to start is one line in the test settings yml.

```yaml
kardx:
  description-annotations: swagger
```

Now the `@Schema(description = "...")` value becomes the field description. Fields that have `@Description` keep using `@Description`.

#### Before and after the setting

Here is a DTO in the `com.example` package. Each field has its description in a different annotation.

```kotlin
package com.example

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import io.github.kardx.annotation.Description
import io.swagger.v3.oas.annotations.media.Schema

annotation class ApiField(val text: String)            // your own annotation

data class ProductResponse(
    @Description("Product ID") val id: Long,
    @Schema(description = "Product name") val name: String,
    @JsonPropertyDescription("Sale price (KRW)") val price: Long,
    @ApiField(text = "Stock quantity") val stock: Int,
    val memo: String?,
)
```

The test documents it with `responseFields<ProductResponse>()`. The tables below show the Path, Type, and Description columns of the generated `response-fields` table.

**Before** (no `description-annotations`): only `@Description` is read.

| Path | Type | Description |
|---|---|---|
| `id` | `Number` | Product ID |
| `name` | `String` | No description |
| `price` | `Number` | No description |
| `stock` | `Number` | No description |
| `memo` | `String` | No description |

**After**:

```yaml
kardx:
  description-annotations: swagger, jackson, com.example.ApiField#text
```

| Path | Type | Description | Where the description came from |
|---|---|---|---|
| `id` | `Number` | Product ID | `@Description` |
| `name` | `String` | Product name | `swagger` → `description` of `@Schema` |
| `price` | `Number` | Sale price (KRW) | `jackson` → `value` of `@JsonPropertyDescription` |
| `stock` | `Number` | Stock quantity | `com.example.ApiField#text` |
| `memo` | `String` | No description | Nowhere |

The last column is added here for explanation; it isn't in the actual documentation.

#### Writing entries

Each entry in the list says "which attribute of which annotation to read". There are five forms.

| Entry | Value read | Example |
|---|---|---|
| `swagger` | `description` of `io.swagger.v3.oas.annotations.media.Schema` | `@Schema(description = "Product name")` |
| `jackson` | `value` of `com.fasterxml.jackson.annotation.JsonPropertyDescription` | `@JsonPropertyDescription("Sale price (KRW)")` |
| `alias#attribute` | The `attribute` attribute of the annotation the alias refers to | `swagger#title` → `@Schema(title = "Product name")` |
| `FQCN` | The `value` attribute of that annotation; if absent, the `description` attribute | `com.example.Doc` → `@Doc("Product name")` |
| `FQCN#attribute` | The `attribute` attribute of that annotation | `com.example.ApiField#text` → `@ApiField(text = "Stock quantity")` |

- Aliases (`swagger`, `jackson`) are case-insensitive. Leading and trailing whitespace of every entry, and whitespace around `#`, is ignored (`swagger # title` is `swagger#title`).
- You can add `#attribute` after an alias too. `swagger#title` is the same as `io.swagger.v3.oas.annotations.media.Schema#title`.
- The attribute must be a `String` attribute declared by the annotation. Numeric or array attributes and inherited methods such as `toString` are errors.
- For annotations declared as nested classes, join with `$` (`com.example.Docs$ApiField`).

Write the list comma-separated or as a YAML list. The two are equivalent.

```yaml
kardx:
  description-annotations: swagger, jackson, com.example.ApiField#text
```

```yaml
kardx:
  description-annotations:
    - swagger
    - jackson
    - com.example.ApiField#text
```

Put the setting where your other `kardx.*` settings are, usually the test settings file `src/test/resources/application-docs.yml` ([Getting started](getting-started.md#5-application-docsyml-test)). Outside Spring tests, pass it as the system property `-Dkardx.description-annotations=swagger,jackson` or the environment variable `KARDX_DESCRIPTION_ANNOTATIONS=swagger,jackson` ([yml settings](configuration.md#yml-settings)).

#### Lookup order

For each field, the library looks in this order and uses the first value it finds.

1. `@Description`
2. The first entry in the list, then the second … in the order written
3. KDoc/Javadoc, with the Gradle plugin when `sourceDocs { enabled = true }` (experimental, off by default; [Descriptions from KDoc/Javadoc](#descriptions-from-kdocjavadoc))
4. If found nowhere, `No description`

An empty or whitespace-only value counts as absent, and the lookup moves on. The default of `@Schema`'s `description` is `""`, so a field like `@Schema(title = "...")` that leaves `description` empty falls through to the next entry.

When a field has several annotations, the results are as follows.

| Annotations on the field | List `swagger, jackson` | List `jackson, swagger` |
|---|---|---|
| `@Description("A") @Schema(description = "B")` | A | A |
| `@Description("  ") @Schema(description = "B")` | B | B |
| `@Schema(description = "B") @JsonPropertyDescription("C")` | B | C |
| `@Schema @JsonPropertyDescription("C")` | C | C |
| `@Schema(title = "T")` | No description | No description |

#### Examples by scenario

**Swagger-only projects**. If you use springdoc and your DTOs have `@Schema`, one alias is enough.

```yaml
kardx:
  description-annotations: swagger
```

If some fields leave `description` empty and use only `title`, add `title` after it. The `description` is used when present, and `title` when it is empty.

```yaml
kardx:
  description-annotations: swagger, swagger#title
```

**Jackson**. If you added `@JsonPropertyDescription` for JSON Schema generation or similar, use `jackson`. In both Jackson 2 and 3 this annotation is in the `com.fasterxml.jackson.annotation` package, so the setting is the same.

```yaml
kardx:
  description-annotations: jackson
```

**Your own annotation**. If the attribute holding the description is named `value` or `description`, write only the class name.

```kotlin
package com.example

annotation class Doc(val value: String)
```

```yaml
kardx:
  description-annotations: com.example.Doc
```

If the attribute has a different name, write it after `#`.

```kotlin
package com.example

annotation class ApiField(val text: String, val order: Int = 0)
```

```yaml
kardx:
  description-annotations: com.example.ApiField#text
```

Annotations written in Java need `@Retention(RetentionPolicy.RUNTIME)`. With the Java default (`CLASS`), they can't be read at runtime and you get `No description`. Kotlin annotations default to `RUNTIME`.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface ApiField {
    String text();
}
```

**Mixing several**. If different modules use different annotations, list them all. When a field has more than one, the one listed first wins.

```yaml
kardx:
  description-annotations:
    - swagger                       # new APIs
    - jackson                       # old DTOs that generated JSON Schema
    - com.example.ApiField#text     # in-house shared annotation
```

**Java records and beans**. Java DTOs are read with the same setting. Put the annotation on the component for records, and on the field or getter for beans.

```java
public record CodeResponse(@Schema(description = "Code") String code) {
}

public class LegacyResponse {
    @Schema(description = "Description on the field")
    private String onField;

    private String onGetter;

    public String getOnField() {
        return onField;
    }

    @Schema(description = "Description on the getter")
    public String getOnGetter() {
        return onGetter;
    }
}
```

**Annotation positions**. In Kotlin, annotations are found by the same rules as `@Description`. They are read whether or not you specify a use-site target.

```kotlin
class ProductRequest(
    @Schema(description = "Constructor parameter") val plain: String,     // Java annotations go on the constructor parameter
    @field:Schema(description = "Backing field") val onField: String,
    @get:Schema(description = "getter") val onGetter: String,
)
```

Annotations that can only go on constructor parameters (`@Target(AnnotationTarget.VALUE_PARAMETER)`) are read too.

**Non-public annotations**. Kotlin `private annotation class` and Java package-private `@interface` are read too.

**Repeatable annotations**. When you put a `@Repeatable` annotation on one element two or more times, they are grouped into a container annotation. The library unpacks it, checks them in the order applied, and uses the first non-empty value.

```kotlin
@Repeatable
annotation class Note(val value: String)

class ProductResponse(
    @Note("") @Note("Product name") val name: String,   // "Product name"
)
```

#### Configuring in code

Instead of yml, you can set this in `RestDocsConfiguration.configure { }` or `withSettings`.

| Code | When to use |
|---|---|
| `descriptions.annotation("swagger")` | When you use the same string as in yml (alias, `alias#attribute`, `FQCN`, `FQCN#attribute`) |
| `descriptions.annotation(ApiField::class, "text")` | When you reference the class directly so the compiler catches name typos. Without an attribute, `value`, or `description` if absent |
| `descriptions.from<ApiField> { it.text }` | When you read the attribute in code. You can also combine several attributes. If the function returns `null`, an empty value, or a whitespace-only value, the lookup moves on to the next entry |
| `descriptions.from(ApiField::class) { it.text }` | Same as above, when you pass the class as a value (from Java or generic code) |
| `descriptions.annotations = listOf(...)` | When you replace the whole list. The yml list is ignored too |

The first three functions **append to the end** of the list; assignment **replaces the whole list**.

```kotlin
RestDocsConfiguration.configure {
    descriptions {
        annotation("swagger")
        annotation(ApiField::class, "text")
        from<Schema> { it.description.ifBlank { it.title } }
    }
}
```

```kotlin
import io.github.kardx.config.DescriptionAnnotation

RestDocsConfiguration.configure {
    descriptions.annotations = listOf(
        DescriptionAnnotation.parse("jackson"),
        DescriptionAnnotation.of(ApiField::class, "text"),
        DescriptionAnnotation.from<Schema> { it.description },
    )
}
```

An assigned list is copied. Changing the `MutableList` you passed later doesn't affect the setting (the same holds for other list and set settings such as `masking.headers` and `document.commonIncludeFiles`).

To clear the list, assign `descriptions.annotations = emptyList()`. Then only `@Description` is read.

When you use yml and code together, the list is built in this order.

1. `description-annotations` from yml
2. Entries added in `configure { }`
3. Entries added in `withSettings` (only inside that block)

For example, if yml has `swagger`, `configure` adds `descriptions.annotation("jackson")`, and `withSettings` adds `descriptions.annotation(ApiField::class, "text")`, the list inside the block is `swagger, jackson, ApiField#text` and outside it is `swagger, jackson`. `reset()` removes only the entries added with `configure` and keeps the yml list.

To read a different annotation in just one test, wrap it in `withSettings`.

```kotlin
@Test
fun getLegacyProduct() {
    RestDocsConfiguration.withSettings({ descriptions.annotation("jackson") }) {
        mockMvc
            .perform(get("/api/legacy/products/{id}", 1L))
            .andDocument { responseFields<LegacyProductResponse>() }
    }
}
```

#### Scope

The configured list applies equally everywhere descriptions are generated automatically.

- Request and response body fields: `requestFieldsOf`, `responseFieldsOf`, `wrappedResponseFieldsOf` (including the wrapper's `data` row)
- Query, path, and form parameters: `parametersOf`
- WebSocket, STOMP, and SSE message payloads ([Messaging](messaging.md))
- OpenAPI and AsyncAPI schemas that reuse these descriptions

Enum fields still get the constant list after the description that was read. Hand-written entries (`"id" type NUMBER means "ID"`) are not affected.

#### Errors and fixes

If the list is invalid, it fails with an `IllegalArgumentException` that includes the property name when the yml is read (when the documentation test starts). If you set it in code (`descriptions.annotation(...)`), it fails on that line with the same message, minus the leading `kardx.description-annotations: `.

| Error message | Cause | Fix |
|---|---|---|
| `kardx.description-annotations: class 'io.swagger.v3.oas.annotations.media.Schema' (alias 'swagger') not found` | The annotation the alias refers to is not on the classpath | Add the dependency below |
| `kardx.description-annotations: class 'com.example.ApiField' not found` | Typo in the class name, not on the classpath, or a nested class joined with `.` | Fix the name; write nested classes as `com.example.Docs$ApiField` |
| `kardx.description-annotations: class 'com.example.ProductResponse' is not an annotation` | You listed a class that isn't an annotation | List an annotation class name |
| `kardx.description-annotations: annotation 'com.example.ApiField' has no String attribute 'value' or 'description'` | The attribute was omitted, and there is no String attribute named `value` or `description` | Name the attribute, as in `com.example.ApiField#text` |
| `kardx.description-annotations: annotation 'com.example.ApiField' has no String attribute 'order'` | No such attribute, or it isn't a `String` | Name a String attribute |
| `kardx.description-annotations: 'com.example.ApiField#' has no attribute name after '#'` | Nothing after `#` | Add the attribute name or remove the `#` |
| `kardx.description-annotations: '#text' has no annotation name` | Nothing before `#` (the annotation name) | Write an alias or FQCN |

The `swagger` and `jackson` aliases require the annotation on the test classpath. If your DTOs already use it, it is usually there.

| Alias | Required dependency | Usually comes from |
|---|---|---|
| `swagger` | `io.swagger.core.v3:swagger-annotations-jakarta` (or `swagger-annotations`) | springdoc-openapi |
| `jackson` | `com.fasterxml.jackson.core:jackson-annotations` | Jackson from the Spring Boot web starter |

```kotlin
dependencies {
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.47")
}
```

If there is no error but the description is `No description`, check the following.

- That the value isn't empty (`@Schema` needs `description`; `title` isn't read)
- That the Java annotation has `@Retention(RetentionPolicy.RUNTIME)`
- That a custom `descriptions.resolver` still includes `DescriptionResolver.DEFAULT`, for example `myRule orElse DescriptionResolver.DEFAULT` ([Composing the description chain](#composing-the-description-chain))

#### Caveats

- **Whitespace-only `@Description` is skipped.** `@Description("  ")` counts as no description, and the next annotation in the list is checked. If there is no other description, you get `No description`.
- **IDE completion**: In IntelliJ, typing a `description-annotations` value suggests `swagger` and `jackson` (from the Spring configuration metadata in the library jar).

### Descriptions from KDoc/Javadoc

> **Experimental, off by default.** The comments are read by a lightweight scanner, not by the Kotlin or Java compiler, so some declarations are missed ([Known limitations](#known-limitations)). The settings and the results may still change.

If your DTOs and controllers already have KDoc or Javadoc, those comments can become descriptions too. Turn it on in the Gradle plugin with `kardx { sourceDocs { enabled = true } }` ([Turning it on](#turning-it-on)).

The `apiDocsSourceDocs` task then reads the comments from the sources before `test` runs ([Gradle plugin](build.md#gradle-plugin)), and the tests use them wherever no annotation gives a description.

Once it's on, comments on types, properties and enum constants are used, and so are the `@param` tags of handler methods: they describe the parameters a test declares by name only, such as `pathParameters("id")` ([Parameters declared by name → method `@param`](#parameters-declared-by-name--method-param)). Controller method comments as OpenAPI summary and description also need `methodDocs = true` ([Controller methods → OpenAPI summary and description](#controller-methods--openapi-summary-and-description)).

```kotlin
/** A user. */
class UserResponse(
    /** User ID */
    val id: Long,
    val nickname: String,
    val status: UserStatus,
)

enum class UserStatus {
    /** Active user */
    ACTIVE,

    /** Withdrawn user */
    WITHDRAWN,
}

@RestController
class UserController(private val userService: UserService) {
    /**
     * Looks up a user. A withdrawn user returns 404.
     *
     * @param id the [UserResponse.id] to look up
     */
    @GetMapping("/api/users/{id}")
    fun getUser(@PathVariable id: Long): UserResponse = userService.getUser(id)
}
```

`responseFieldsOf<UserResponse>()` then documents `id` as `User ID`, and the value list of `status` reads `ACTIVE : Active user`, `WITHDRAWN : Withdrawn user`. `nickname` has no comment, so it stays `No description`. With `methodDocs = true`, the OpenAPI operation's summary is `Looks up a user` (the first sentence without its final period) and its description is the full text, which is also the description in the HTML document. A test that writes `pathParameters("id")` gets `the UserResponse.id to look up` from `@param id`.

The memo API in the samples, which turn the feature on with `sourceDocs { enabled = true; methodDocs = true }`, is documented from KDoc alone, with no description annotations and no descriptions in the test (the path variable is declared as `pathParameters("id")`, the optional query parameter as `"includeReminder" queryMeans FromParamTag isOptional true`): see [MemoController](../samples/spring-boot-4/src/main/kotlin/com/example/restdocs/memo/MemoController.kt) and [MemoApiDocsTest](../samples/spring-boot-4/src/test/kotlin/com/example/restdocs/memo/MemoApiDocsTest.kt).

#### Turning it on

The feature is set up in the Gradle plugin. `enabled` turns it on; the other switches pick what is read once it's on. The Groovy DSL uses the same names.

```kotlin
kardx {
    sourceDocs {
        enabled = true                    // default false (experimental)
        fieldDocs = true                  // default true
        enumDocs = true                   // default true
        schemaDescriptions = true         // default true
        paramDocs = true                  // default true
        methodDocs = false                // default false
        includeProjectDependencies = true // default true
    }
}
```

| Switch | Default | What it feeds |
|---|---|---|
| `enabled` | `false` | The whole feature. When `false`, `test` doesn't depend on `apiDocsSourceDocs` and gets no system property, running `apiDocsSourceDocs` directly is skipped (`SKIPPED`), and OpenAPI schemas get no class descriptions |
| `fieldDocs` | `true` | Property, field, getter and record component comments → field descriptions, and the parameters of `parametersOf<T>()` and `queryParameters<T>()` |
| `enumDocs` | `true` | Enum constant comments → value lists and `@EnumCode` tables |
| `schemaDescriptions` | `true` | Class comments → `description` of OpenAPI component schemas |
| `paramDocs` | `true` | Handler method `@param` → path, query and form parameters, request headers and parts declared by name only or with `FromParamTag` |
| `methodDocs` | `false` | Handler method comments → OpenAPI summary and description, and the description in `api-summary` (the HTML document) |
| `includeProjectDependencies` | `true` | Also reads the `main` sources of `project(...)` dependencies |

- A switched-off item is left out of `source-docs.json`, so neither the tests nor the OpenAPI step see it. Every switch is an input of the task, so changing one reruns the scan and the tests.
- With `paramDocs = false`, an item declared by name only has nowhere to get its description and fails the test ([KDX-101](troubleshooting.md#kdx-101)).
- The test-side kill switch is `kardx.source-docs.enabled: false` in the test yml (`descriptions.sourceDocs`, default `true`): the tests then ignore the file even when Gradle passed it. It can't turn the feature on, since without `sourceDocs { enabled = true }` there is no file. Class descriptions in OpenAPI schemas are added by the Gradle plugin and don't follow it; use `schemaDescriptions = false` for those ([yml settings](configuration.md#yml-settings)).

#### Order of precedence

KDoc/Javadoc is the last source. For each field and parameter:

1. `@Description`
2. The `description-annotations` list ([Reading descriptions from other annotations](#reading-descriptions-from-other-annotations))
3. KDoc/Javadoc
4. `No description`

So adding `@Description` or `@Schema` to a field still wins over its comment. A `descriptions.resolver` assigned on its own replaces all these steps, KDoc/Javadoc included; combine it with `DescriptionResolver.DEFAULT` (or `SOURCE_DOCS`) to keep them ([Composing the description chain](#composing-the-description-chain)). Enum constant comments and controller method comments don't go through `descriptions.resolver`, so they stay.

#### What is read

| Where the comment is | Used for |
|---|---|
| Kotlin property KDoc (`/** ... */` on a constructor property or a property in the class body) | That property |
| Class KDoc `@property name` | That property, when the property has no KDoc of its own |
| Class or constructor KDoc `@param name` | That property, when neither of the above exists |
| Java field Javadoc | That property |
| Java getter Javadoc (`getName`, `isActive`) | That property, when the field has no Javadoc |
| Java record Javadoc `@param name` | That record component |
| Enum constant KDoc/Javadoc | The constant in the value list and in `@EnumCode` tables, only when it has no other description: the `extraction.enumDescriptionProperty` value (or `description` for `@EnumCode`) is missing or `null`. Not used when `@EnumCode(descriptionProperty = ...)` is set explicitly |
| Class KDoc/Javadoc | `description` of the OpenAPI component schema made from that class (below) |
| Controller method KDoc/Javadoc | OpenAPI summary and description of the operation, and the description in `api-summary` (the HTML document), only with `methodDocs = true` (below) |
| Controller method KDoc/Javadoc `@param name` | The path, query and form parameter, request header or part declared by name only (`pathParameters("id")`), with `paramDocs` (default `true`), whatever `methodDocs` is (below) |

- Comments are matched by the property name in the source, so Jackson names (`@JsonProperty`, `@JsonNaming`, the global naming rule) and kotlinx `@SerialName` apply as they do for annotations.
- The same fields get the comments as get the list ([Scope](#scope)): request and response fields, `parametersOf`, and message payloads.
- Nested classes are matched too (`Outer.Inner`).
- Class comments become the `description` of OpenAPI component schemas (`components.schemas`). The class is the one the schema name came from: the outer type (`ApiResponseItem` → `ApiResponse`), or the type argument when the name drops the wrapper (`Item`); nested classes are matched too. Explicitly chosen names (`openApi { responseSchema }`, `openApi.errorSchema`, `openApi.schemaNameResolver`) are covered when the body type is known, that is, when the fields were extracted from a type. A schema that already has a description is left alone, and a name used by several different classes gets none. Off with `schemaDescriptions = false`.

#### Controller methods → OpenAPI summary and description

This part stays off even with `enabled = true`. Comments on handler methods are often implementation notes for the people who maintain the code ("checks the cache first", "returns 401 without the demo token"), and they would otherwise turn up as the public summary of the API. Turn it on in Gradle when your controller comments are written for API users:

```kotlin
kardx {
    sourceDocs {
        enabled = true
        methodDocs = true // default false. Field, type and enum constant comments and @param are used either way
    }
}
```

With `methodDocs = false`, `apiDocsSourceDocs` writes no method summary or description into `source-docs.json` (the `@param` texts follow `paramDocs`), so operations keep the default summary and description. The setting is an input of the task, so changing it reruns the scan and the tests.

With `methodDocs = true`, the KDoc/Javadoc of the handler method that served the request goes into the OpenAPI operation: the first sentence becomes the `summary`, the whole text the `description`. The first sentence ends at the first `. `, at a closing `.`, or at the first paragraph break (Korean sentences ending in `다.` count as well), and the summary drops that final period. Overloaded methods with the same name share one entry when their comments are the same; when they differ, they are told apart by the number of parameters declared in the source (the `Continuation` of a `suspend` function and an extension receiver are not counted).

| | summary | description |
|---|---|---|
| 1. `openApi { summary }`, `openApi { description }` | Wins | Wins |
| 2. Method KDoc/Javadoc | First sentence | Full text |
| 3. Default ([OpenAPI tags and summaries](#openapi-tags-and-summaries)) | Test name | Block `description`, or the summary |

- A value set in `openApi { }` always wins and there is no warning when it differs from the comment.
- The `###` titles in the HTTP Client export (`api.http`) follow the final summary.
- The method comment also comes before the block `description` in the OpenAPI operation description.
- In REST Docs output, the full text becomes the description in the `api-summary` snippet (and so in the HTML document) when the block sets no `description`. A block `description = "..."` always wins there. Section titles keep the test names.
- The handler is known with MockMvc and with a WebTestClient that records handlers (`restDocsWebTestClient`, `recordHandlers()`), as in [Parameter types come from the handler](#parameter-types-come-from-the-handler). RestTestClient and functional routes don't expose a handler method, so their operations get no method comments (field comments still work; [Known limitations](#known-limitations)).

#### Parameters declared by name → method `@param`

A path, query or form parameter, a request header or a request part can be declared by name only. Its description then comes from the `@param` tag of the handler method that served the request. This needs `sourceDocs { enabled = true }` and follows `paramDocs` (default `true`), not `methodDocs`: a `@param` text describes one parameter and doesn't leak implementation notes into a summary.

```kotlin
/**
 * Searches products.
 *
 * @param categoryId category to search in
 * @param keyword words in the product name
 */
@GetMapping("/api/categories/{categoryId}/products")
fun search(@PathVariable categoryId: Long, @RequestParam("q") keyword: String): List<ProductResponse> = ...

// In the test
.andDocument {
    pathParameters("categoryId")
    queryParameters("q") // @RequestParam("q") → the source parameter keyword → @param keyword
}
```

| Declaration | Matched handler parameter |
|---|---|
| `pathParameters("id")` | `@PathVariable` |
| `queryParameters("page")`, `formParameters("name")` | `@RequestParam`, or a simple-type parameter without a Spring annotation |
| `requestHeaders("X-Tenant")` | `@RequestHeader` (the header name ignores case) |
| `requestParts("file")` | `@RequestPart`, or `@RequestParam` |

- The request name is matched to the annotation's `name`/`value`, or to the parameter name when the annotation has none. The `@param` text is then looked up by the parameter's name in the source.
- A description written in the test always wins. Name-only and described items can be mixed: calling `pathParameters(...)` again appends.
- A name-only item that gets no description fails the test with `IllegalArgumentException` naming each item (`path parameter 'id' declared by name only (or with FromParamTag), but no description was found. …`). Add the `@param` tag and turn the feature on (`sourceDocs { enabled = true }`), or describe the item in the test ([KDX-101](troubleshooting.md#kdx-101)).
- To add modifiers (`isOptional`, `example`, `withDefaultValue` …) without writing a description, put `FromParamTag` where the description goes. The item reads its text from `@param` like a name-only item, and takes every modifier of the described form:

  ```kotlin
  queryParameters(
      "q" queryMeans FromParamTag,
      "page" queryMeans FromParamTag isOptional true withDefaultValue "0",
      "sort" queryMeans "sort order" isOptional true, // described items can sit in the same call
  )
  requestHeaders("Accept-Language" headerMeans FromParamTag isOptional true example "ko")
  ```

  `pathMeans`, `queryMeans`, `formMeans`, `headerMeans` and `partMeans` accept it. `@param` describes request items only, so response headers and message headers reject it.
- Fields of a `@ModelAttribute` object are not handler parameters: document them with `queryParameters<T>()` or `parametersOf<T>()`, which take their descriptions from the type's KDoc/Javadoc.
- The handler must be known: MockMvc, or a WebTestClient that records handlers. RestTestClient and functional routes (`RouterFunction`) have no handler method, so name-only items fail there ([Known limitations](#known-limitations)).
- The `@param` text is looked up by the parameter's name in the source, so Java handlers need the parameter names in the class files (`-parameters`, which Spring Boot's Gradle plugin turns on). Without them, and without a name in the annotation, the item is not matched at all; even with `@PathVariable("id")` the `@param` text can't be found. Either way the item fails. Kotlin handlers need nothing extra.

#### How the text is cleaned up

Descriptions go into table cells, so each comment becomes one line.

| In the comment | In the description |
|---|---|
| `*` at the start of lines, comment markers | Removed |
| Block tags (`@param`, `@return`, `@throws`, `@see`, `@since`, `@property`, `@constructor`, `@sample` …) | Removed from the main text |
| `[UserResponse]`, `[the user][UserResponse]` | `UserResponse`, `the user` |
| `{@link UserResponse}`, `{@link UserResponse the user}` | `UserResponse`, `the user` |
| `{@code id}` | `` `id` `` |
| HTML tags (`<p>`, `<b>` …) | Removed; `<p>` is a paragraph break |
| Several paragraphs | Joined with a single space |

#### Known limitations

- **Not a compiler.** The scanner is a lightweight reader, not the Kotlin or Java compiler. It skips a declaration whose syntax it doesn't recognize and keeps going; it never fails the build. A skipped declaration simply has no description.
- **Only sources the build can see.** The task reads the `main` source set of the project and, with `includeProjectDependencies`, of the projects it depends on with `project(...)`. DTOs that come from a jar (another repository, a published module, a library) have no sources here, so they get no comments: use `@Description` or `description-annotations` for those. Test sources and test fixtures are not read, and generated sources (kapt, KSP, annotation processors) only when their directory is part of the `main` source set.
- **Project dependencies need the Java plugin's main-sources variant.** `project(...)` dependencies are found through the variant the `java` plugin publishes for its main sources. Android and Kotlin Multiplatform modules and included builds (`includeBuild`) don't have it, so their sources are not read.
- **Only `/** ... */` comments.** Line comments (`//`), plain block comments (`/* ... */`) and Markdown doc comments (`///`, JDK 23) are not read.
- **Not collected**: top-level functions and properties (including handler functions of functional routes declared at the top level), extension properties, classes and objects declared inside a function body (local classes, anonymous objects and classes), and Java constructor `@param` (only record `@param` is read; Kotlin primary and secondary constructor `@param` is). The description of a sealed or `oneOf` variant type is not taken from its class comment either.
- **Handler comments come from the controller.** Method comments and `@param` tags are looked up on the controller class and on the class that declares the handler method (an abstract base controller, when the controller doesn't override the method). A comment on an interface method that the controller implements, such as an API interface generated from an OpenAPI file, is not used, and `{@inheritDoc}` becomes empty. Property comments, on the other hand, are also looked up in superclasses and interfaces.
- **Overloads with the same number of parameters.** Same-name handler methods with different comments are told apart only by the number of parameters; overloads with the same number and different comments get no method comment.
- **No handler method, no method comments.** RestTestClient and functional endpoints (`router { }`, `coRouter { }`, `RouterFunction`) don't give a handler method, and a WebTestClient gives one only when it records handlers. There the operation gets no method comment and items declared by name only fail; field, enum and schema comments still work.
- **Java bean names by name alone.** Getter names are turned into property names from the name only: `getURL` → `URL` (as `java.beans.Introspector` does) while Jackson uses `url`, and `isX` counts as a getter whatever it returns while Jackson only takes it for `boolean`. Such a getter's comment may be missed or land on the wrong property. Put the comment on the field instead.
- **Schema descriptions need a class name in the schema name.** Only the request and response body schemas are described, by the class whose simple name is in the schema name. Explicitly chosen names that contain no class name (`openApi { responseSchema = "ProductDetailResponse" }` for an `ApiResponse<ProductResponse>` body, or a name from `openApi.schemaNameResolver`), bodies written field by field (no type), and names used by several different classes get no description.
- **Markup is mostly kept as written.** KDoc links, Javadoc inline tags and HTML are converted ([How the text is cleaned up](#how-the-text-is-cleaned-up)). Other Markdown (`**bold**`, lists, code fences) stays as written, every comment becomes one line, and the summary is only the first sentence.
- **Sources are read as UTF-8.** A file in another encoding (`options.encoding = "MS949"`) is still read, but characters that aren't valid UTF-8 become `U+FFFD` in the descriptions, and the task logs one warning with the number of such files and the first one. Save the sources as UTF-8.
- **Gradle plugin only.** Without the plugin (Maven, or the library alone) there is no comments file, and descriptions come from annotations only.
- **Experimental API.** The test-side API of this feature is marked `@ExperimentalRestDocsApi`: `descriptions.sourceDocs`, `DescriptionResolver.SOURCE_DOCS`, `FromParamTag` with `pathMeans`/`queryMeans`/`formMeans`/`headerMeans`/`partMeans`, and parameters declared by name only (`pathParameters("id")` …). Using them without `@OptIn(ExperimentalRestDocsApi::class)` (or the module-wide opt-in) gives a compiler warning. They may change in a later release and are not covered by the 1.0 compatibility guarantee ([Experimental APIs](configuration.md#experimental-apis)).

If a comment isn't picked up, see [Troubleshooting](troubleshooting.md).

### Replacing the description source

`descriptions.resolver` is the rule that decides field descriptions, a `DescriptionResolver` (`io.github.kardx.config`). The default, `DescriptionResolver.DEFAULT`, looks at `@Description`, the `descriptions.annotations` list and KDoc/Javadoc, in that order. A rule you assign on its own decides every description, and those steps are no longer used; combine it with the default steps to keep them ([Composing the description chain](#composing-the-description-chain)).

If you read descriptions from annotations, the list is enough. Use a resolver when you need a rule that isn't based on annotations.

| | yml list | List in code | Your own `descriptions.resolver` |
|---|---|---|---|
| Code | Not needed | `configure`, `withSettings` | `configure`, `withSettings` |
| Checks `@Description` first | Yes | Yes | When you combine it with `DescriptionResolver.DEFAULT` (or `DESCRIPTION_ANNOTATION`) |
| What it can do | Read String attributes of annotations | Same as yml + reading with a function (`descriptions.from`) | Anything: field names, combinations of annotations, external glossaries |
| Only in one test | Not possible | `withSettings` | `withSettings` |

```kotlin
import io.github.kardx.config.DescriptionResolver
import io.github.kardx.config.orElse

RestDocsConfiguration.configure {
    descriptions {
        // Swagger @Schema first, then the default steps (@Description → list → KDoc/Javadoc)
        resolver = DescriptionResolver { it.findAnnotation<Schema>()?.description } orElse DescriptionResolver.DEFAULT
    }
}
```

`DescriptionResolver` is a fun interface, so a lambda assigned to the property needs the SAM constructor (`DescriptionResolver { ... }`). The `PropertyContext` it receives has the JSON field name (`name`), the name in the source before naming rules (`declaredName`), the declaring class (`declaringClass`, `null` when unknown), all attached annotations (`annotations`), and the Kotlin property (`property`, `null` for Java records and beans). `findAnnotation` finds annotations regardless of use-site target. Returning `null` means "no description here": the next step is tried, and when none is left you get `No description`.

#### Composing the description chain

The default steps are public, so you put your rule before or after them instead of rebuilding the chain.

| Step | Reads |
|---|---|
| `DescriptionResolver.DESCRIPTION_ANNOTATION` | `@Description` |
| `DescriptionResolver.CONFIGURED_ANNOTATIONS` | The `descriptions.annotations` list (yml and code) in effect |
| `DescriptionResolver.SOURCE_DOCS` | KDoc/Javadoc. Experimental (`@ExperimentalRestDocsApi`) |
| `DescriptionResolver.DEFAULT` | `firstOf(DESCRIPTION_ANNOTATION, CONFIGURED_ANNOTATIONS, SOURCE_DOCS)` |

`DescriptionResolver.firstOf(a, b, ...)` returns the first description found, and `a orElse b` (import `io.github.kardx.config.orElse`) does the same for two rules. `null`, empty and whitespace-only values count as not found.

**Name-based glossary**. Describe fields that repeat across many DTOs, such as `createdAt`, by name, after the default steps.

```kotlin
val glossary = mapOf(
    "createdAt" to "Created at",
    "updatedAt" to "Updated at",
)
val byGlossary = DescriptionResolver { glossary[it.name] }

RestDocsConfiguration.configure {
    descriptions.resolver = DescriptionResolver.DEFAULT orElse byGlossary
}
```

To pick steps, list them. This reads annotations and the glossary but not KDoc/Javadoc:

```kotlin
RestDocsConfiguration.configure {
    descriptions.resolver = DescriptionResolver.firstOf(
        DescriptionResolver.DESCRIPTION_ANNOTATION,
        DescriptionResolver.CONFIGURED_ANNOTATIONS,
        byGlossary,
    )
}
```

To build on whatever rule is already set (for example by a base class), use the current value: `descriptions.resolver = descriptions.resolver orElse byGlossary`. This replaces the 0.1 pattern `val previous = descriptionResolver; descriptionResolver = { previous(it) ?: ... }`.

### Mixing extracted and hand-written fields

An extraction result is a `List<RestDocsField>`, so you can filter it and add to it. Use `except` to remove fields and `optionalAt` to make them optional. Adding hand-written fields with `+` keeps the schema name (`ProductResource`).

```kotlin
responseFields(
    responseFieldsOf<ProductResource>().except("_links") +
        ("_links" subsection OBJECT means "HAL links"),
)
```

### Previewing extraction results

`describe()` returns a text table showing what a field or parameter list will produce in the documentation table. Use it to check extraction results without running the tests and opening the HTML. It works on the results of the extraction functions (`responseFieldsOf`, `requestFieldsOf`, `wrappedResponseFieldsOf`, `parametersOf`) and on hand-written lists.

```kotlin
println(requestFieldsOf<ProductRequest>().describe())
println(parametersOf<ProductSearchCondition>().describe())
```

```
| Path  | Type   | Required | Description  | Constraints |
|-------|--------|----------|--------------|-------------|
| name  | String | true     | Product name |             |
| price | Number | true     | Price        |             |
```

| Function | Result |
|---|---|
| `List<RestDocsField>.describe()` | Path, Type, Required, Description, Constraints table (the same columns as the `response-fields` table). Ignored fields are left out, and subsection fields have `(subsection)` in Type |
| `List<RestDocsParam>.describe()` | Parameter, Required, Description, Constraints table |
| `printDescription()` | Prints the table to standard output and returns the list unchanged. `responseFields(responseFieldsOf<T>().printDescription())` |

In the description cell, `[Deprecated: ...]`, `[Masked: ...]`, `(Default: ...)`, and `(Example: ...)` are added on one line. Wide characters such as Korean count as two columns, so the columns line up in a terminal. The functions are in the `io.github.kardx.extractor` package (`import io.github.kardx.extractor.describe`).

### Deprecated fields

Properties annotated with Kotlin `@Deprecated` or Java `@Deprecated` are marked automatically during extraction.

```kotlin
data class ProductSearchCondition(
    @Description("Minimum price") val minPrice: Long? = null,
    @Deprecated("Use minPrice")
    @Description("Minimum price (old name)")
    val priceFrom: Long? = null,
)
```

| Output | Result |
|---|---|
| AsciiDoc table | A `*Deprecated*: Use minPrice` line at the top of the Description cell (just `*Deprecated*` for Java `@Deprecated`) |
| OpenAPI (Gradle plugin) | `deprecated: true` on parameter and body field schemas |
| AsyncAPI | `deprecated: true` on payload schema properties |

When writing fields by hand, add `deprecated "Use nickname"` or `deprecated()`, as in `"nick" type STRING means "Nickname" deprecated "Use nickname"`. To change the wording in the table, edit the `{{#deprecated}}` part of the [snippet template](customizing-snippets.md#changing-snippet-templates). Verification is not affected. To deprecate a whole operation, use `deprecated("reason")` in the block ([Deprecated APIs](#deprecated-apis)).

In OpenAPI, response schemas are shared by several operations through `$ref`. So that a field marked with `deprecated()` in just one test doesn't spread to other operations using the same schema, `deprecated: true` is added only when every test that documented that schema property marked it. Marks that come from annotations are present in every test that extracts the type, so they stay. The same applies to [masked marks](#marking-masked-fields).

### Marking masked fields

If your app hides values in some fields before sending them, such as a name as `J*** D**` or a phone number as `010-****-1234`, you can record that in the documentation. Readers of the documentation (frontend developers, partners) then know the original value won't arrive and can adjust their screens or validation.

**This does not actually mask values.** The app (a serializer, for example) does the masking; this only marks it in the documentation.

#### `@Masked`

Put `@Masked` on the DTO property. The value describes the masking rule; leave it empty to show just `Masked` without a rule. It lives in the annotations module (`io.github.kardx.annotation.Masked`), and it goes in the same positions as `@Description` (constructor property, `@property:`, `@field:`, `@get:`, Java field, getter, or record component).

```kotlin
data class UserResponse(
    @Description("User name")
    @Masked("Middle characters as *")
    val name: String,

    @Description("Mobile phone number")
    @Masked("Middle 4 digits as *")
    val phone: String,
)
```

The mark is applied when **body fields** are extracted, as with `responseFieldsOf<UserResponse>()`, `requestFieldsOf<T>()`, and message and SSE payloads. It isn't applied to parameters (`parametersOf<T>()`), whose values don't appear in the documentation.

#### Modifiers

For hand-written fields, add `masked()` or `masked "rule"`.

```kotlin
responseFields(
    "name" type STRING means "User name" masked "Middle characters as *",
    ("phone" type STRING means "Mobile phone number").masked(),     // without a rule
)
```

#### Reading your company's masking annotations

If you already use masking annotations, such as custom annotations that attach a Jackson serializer, have the library read them instead of adding `@Masked` separately. The syntax is the same as for [`description-annotations`](#writing-entries).

```yaml
kardx:
  masked-annotations: com.example.MaskName, com.example.Masking#rule
```

| Entry | Result | Example |
|---|---|---|
| `FQCN` | Masked if present (no rule description) | `com.example.MaskName` → `@MaskName` gives `Masked` |
| `FQCN#attribute` | The attribute value is the rule description: the value for a String, the constant name for an enum | `com.example.Masking#rule` → `@Masking(rule = "Last 4 digits")` gives `Masked: Last 4 digits`; with `#type`, `@Masking(type = MaskType.PHONE)` gives `Masked: PHONE` |

- `@Masked` is always read first, regardless of settings. Then the list is checked in order, and the first annotation found is used.
- In code, use `masking.annotation("com.example.MaskName")`, `masking.annotation(Masking::class, "rule")`, or `masking.annotations = listOf(...)` to replace the whole list ([List of settings](configuration.md#list-of-settings)).
- Invalid values fail with errors in the same format as `description-annotations`: `kardx.masked-annotations: class 'com.example.MaskName' not found`, `... is not an annotation`, `... annotation 'com.example.Masking' has no String or enum attribute 'count'`, `... 'com.example.Masking#' has no attribute name after '#'`.

#### How it looks in docs and Swagger

The `phone` field of `UserResponse` above comes out like this.

| Output | Result |
|---|---|
| AsciiDoc field table | A `*Masked*: Middle 4 digits as *` line at the top of the Description cell (below the `@Deprecated` line if both are present). Without a rule, `*Masked*` |
| OpenAPI (Gradle plugin) | `Masked: Middle 4 digits as *` at the end of the schema property `description`, plus the extension properties `x-masked: true` and `x-masked-rule: Middle 4 digits as *` |
| AsyncAPI | The same description and extension properties as OpenAPI on payload schema properties |
| `describe()` | `[Masked: Middle 4 digits as *]` at the start of the description cell |

```
|`+phone+`
|`+String+`
|true
|*Masked*: Middle 4 digits as * +
Mobile phone number
```

```yaml
phone:
  type: string
  description: |-
    Mobile phone number

    Masked: Middle 4 digits as *
  x-masked: true
  x-masked-rule: Middle 4 digits as *
```

Swagger UI shows the `Masked: ...` at the end of the description as is. `x-masked` isn't shown on screen, but code generators and spec linters can read it. To change the wording in the table, edit the `{{#masked}}` part of the [snippet template](customizing-snippets.md#changing-snippet-templates). Verification is not affected.

#### Difference from header masking

| | Marking masked fields (`@Masked`) | Header masking (`masking.headers`) |
|---|---|---|
| What it does | Records in the documentation the **fact** that the app hides the value | Hides header **values** printed in the documentation with `****` |
| Target | Body fields | Request and response headers, cookies |
| Does the value change? | No | Only the value in the documentation (the app response is unchanged) |
| Settings | `@Masked`, `masked()`, `masked-annotations` | `mask-sensitive-headers`, `masked-headers` ([Masking sensitive information](configuration.md#masking-sensitive-data)) |

## Verification failure messages

Spring REST Docs fails with `SnippetException` when documented fields or parameters differ from the actual ones, and with `FieldTypesDoNotMatchException` when a field type differs (`FieldTypeRequiredException` when a field documented without a type is absent). `andDocument { }` (MockMvc, WebTestClient and RestTestClient), SSE `events(...)`, and `MessageDocumentation` wrap these in `DocumentationMismatchException` (a subclass of `SnippetException`, in `io.github.kardx.dsl`) that tells you the following. The original exception is kept as the `cause`. The field type exceptions were not originally `SnippetException`s, so code that caught them directly should look at the `cause`.

- Undocumented entries (present in the actual request or response) and documented entries that are missing, by path. Fields include their actual JSON type
- The documented type and the actual type of fields whose types don't match
- DSL lines you can paste right away (`"data.memo" type STRING means "..."`, `"page" queryMeans "..."`, `"tick" eventMeans "..."`)
- Common causes confirmed against the actual body (no hint is added if nothing is confirmed)

```
io.github.kardx.dsl.DocumentationMismatchException: Documented response fields do not match the actual response.

Not documented (present in the response):
  - data.memo (String)

Documented but not in the response:
  - data.nickname
  Remove them, or mark them `isOptional true` if they can be absent.

Add to the documentation (replace "..." with a description):
  "data.memo" type STRING means "..."

Hints:
  - Absent although the parent object is present: `data.nickname`. If the value was null and the mapper omits nulls (@JsonInclude(NON_NULL), spring.jackson.default-property-inclusion: non_null), mark it `isOptional true` or make the property nullable; otherwise fill it in the test data.

The original Spring REST Docs message is the cause of this exception.
```

| Hint | Detection condition | Fix |
|---|---|---|
| Empty array | The missing path is `items[].x` and the actual `items` is `[]` | Add elements to the test data, or use `responseFieldsOf<T>().optionalUnder("items[]")` (`extraction.arrayElementFieldsOptional` for all arrays) |
| Naming strategy | Names differing only in case, `_`, or `-` appear on both sides (`userName` ↔ `user_name`) | Extract with the same mapper as the app (`restDocsMockMvc` or the `objectMapper` setting) |
| Documented without the wrapper | The documented `id` is actually `data.id` | `wrappedResponseFields<T>()` + `response-wrapper`, or `responseFields<T>("data")` |
| Documented with the wrapper | The documented `data.id` is actually a top-level `id` | Use `responseFields<T>()` for this response |
| Field missing although the parent is present | The parent object exists but the key doesn't | If the null was dropped during serialization, `isOptional true` or a nullable property |

When a field type differs, the message shows types instead of the lists.

```
io.github.kardx.dsl.DocumentationMismatchException: Documented response fields do not match the actual response.

Type mismatch:
  - data.price: documented Number, actual String

Change the documentation (replace "..." with a description):
  "data.price" type STRING means "..."

Hints:
  - `data.price` is documented as a number but the payload has a string. Common causes: @JsonFormat(shape = JsonFormat.Shape.STRING) or @JsonSerialize(using = ToStringSerializer::class) on the property of a hand-documented field, or a mapper that writes numbers as strings (JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS). If the string is intended, document it as STRING.

The original Spring REST Docs message is the cause of this exception.
```

| Actual type | DSL line to paste | Hint |
|---|---|---|
| `Null` | `"x" type <documented type> means "..." isOptional true` (optional fields allow null) | Nullable property, `isOptional true`, or `optionalAt("x")` for extracted lists |
| `Varies` (differs between array elements) | `"items[].x" type VARIES means "..."` | If unintended, make the value types consistent in the app |
| Documented as a number but `String` | `"x" type STRING means "..."` | `@JsonFormat(shape = STRING)`, `ToStringSerializer`, `WRITE_NUMBERS_AS_STRINGS` |
| Documented as a string but `Number` or `Array` | `"x" type NUMBER means "..."` and so on | `WRITE_DATES_AS_TIMESTAMPS`, which writes dates as timestamps; enums with a numeric `@JsonValue` |
| Anything else | The line with the actual type | (none) |

Nulls or missing values in only some array elements are reported as "Documented but not in the response", not as a type mismatch.

## Headers and authentication

Document request and response headers with `requestHeaders` and `responseHeaders`. Authentication headers and custom headers work the same way.

```kotlin
mockMvc
    .perform(
        get("/api/todos/{id}", 1L)
            .bearerToken("access-token")                   // Authorization: Bearer access-token
            .header("X-Request-Id", "req-123"),            // custom header
    )
    .andDocument {
        requestHeaders(
            "Authorization" headerMeans "Bearer access token",
            "X-Request-Id" headerMeans "Request tracking ID. If sent, it is echoed back in the response" isOptional true,
        )
        responseHeaders("X-Request-Id" headerMeans "Tracking ID of the request" isOptional true)
    }
```

| Output | Where the headers appear |
|---|---|
| REST Docs | Request and response header tables, `http-request` and `http-response` examples |
| OpenAPI (Swagger) | Header parameters, response `headers` |
| OpenAPI security | `Authorization: Bearer ...` → `bearerAuthJWT`, `Basic ...` → `basic` security scheme. Swagger UI gets an Authorize button |
| OpenAPI global headers | Headers listed in `open-api.global-headers` → `apiKey` security scheme. Enter them once in the Authorize dialog and they are added to every request |

- **Modifiers**: `isOptional`, `constraint "UUID"`, `example "req-123"`, `withDefaultValue "ko"`. `example` and `withDefaultValue` become `Example:` and `Default:` lines below the description in the table, and `example` and `default` on the OpenAPI header ([Defaults, examples, and constraint notes](#defaults-examples-and-constraint-notes)).
- **Authorize button**: The security scheme is built from the request's `Authorization` value. For tests that authenticate without a header (`with(jwt())`), declare it with `openApi { bearerAuth() }` ([Security schemes for tests that authenticate without a header](#security-schemes-for-tests-that-authenticate-without-a-header)). Even if the token isn't a JWT or is masked, the library puts a fake JWT in the right format into the OpenAPI fragment only. The real token never goes into the spec. If you turn this off with `open-api.security-schemes: false`, `Authorization` is removed from the OpenAPI fragment and no security scheme is created (it stays in the REST Docs documentation).
- **Examples for sensitive headers**: The OpenAPI header parameters `Authorization`, `Proxy-Authorization`, and `Cookie` don't get the value sent in the request (the fake JWT for the security scheme, a masked value, a test token) as their example. Values you set explicitly with `example` or `withDefaultValue` are kept so that they work right away in a development Swagger (`"Authorization" headerMeans "Auth token" example "Bearer dev-token"`). The Postman Collection works the same way: only the fake token inserted by the library is replaced with `{{bearerToken}}`.
- **Tests without a token**: Even if tests sent without a token, such as checks for 401 or 404, are mixed into the same API, the Gradle plugin aligns the security requirements of fragments with the same method and path, so Authorize stays. Without the plugin, send a token in every test of the same API.
- **Headers sent on every request**: Headers attached to every request, such as `X-Tenant-Id` and `X-Client-Version`, appear by default as header parameters you fill in for each operation. If you list them in `open-api.global-headers`, you enter them only once in the Authorize dialog, like `Authorization` (an OpenAPI `apiKey` security scheme with `in: header`). This applies only to headers documented with `requestHeaders(...)` in tests; the REST Docs documentation stays the same. It is applied in post-processing after the Gradle plugin's `openapi3`, and you can also set it with the plugin extension `kardx { openApiGlobalHeaders = setOf("X-Tenant-Id") }`.

  ```yaml
  kardx:
    open-api:
      global-headers: X-Tenant-Id, X-Client-Version   # code: openApi.globalHeaders = setOf(...)
  ```
- **Masking**: If you turn on [masking](configuration.md#masking-sensitive-data), authentication headers keep the scheme and hide only the value (`Authorization: Bearer ****`).

### Spring Security

By default, `restDocsMockMvc` doesn't apply the Spring Security filter chain, because some projects authenticate without Spring Security, using interceptors, filters, and so on. To document APIs protected by Spring Security as they really behave, turn it on. When on, it applies the filter chain from the context (the `springSecurityFilterChain` bean) to MockMvc, as `@AutoConfigureMockMvc` does, so calling an API that requires authentication without authenticating returns 401 or 403, as in the real app.

```yaml
kardx:
  apply-spring-security: true   # code: http.applySpringSecurity = true
```

```kotlin
testImplementation("org.springframework.security:spring-security-test")
```

```kotlin
mockMvc.perform(get("/api/me").with(jwt()))                       // authenticated request
mockMvc.perform(post("/api/orders").with(user("hong")).with(csrf()))
mockMvc.perform(get("/api/me")).andStatus(HttpStatus.UNAUTHORIZED)  // a 401 response
```

- With spring-security-test, it is applied with `springSecurity()`, so you can use `with(user(...))`, `with(jwt())`, and `with(csrf())`. Without it, only the filter chain is applied, so you must send real tokens or sessions.
- If it is off, there is no filter chain, so requests sent without authentication are documented as 200 too. Turn it on if you want to document 401 responses for APIs protected by Spring Security, or if getting 200 without authentication looks wrong. To turn it on for just one test class, build that MockMvc inside `RestDocsConfiguration.withSettings({ http.applySpringSecurity = true }) { ... }`.
- Sending an `Authorization` header creates an OpenAPI security scheme (see the table above). Requests authenticated without a header, such as `with(jwt())`, have no header, so no security scheme is created as is. Declare it as shown below.
- `restDocsMockMvc` reads this setting once, when it builds the MockMvc. If the context has a `springSecurityFilterChain` bean and the setting isn't specified, it logs a warning once per JVM. If documenting without the filter chain is intended, turn it off explicitly with `apply-spring-security: false`.

#### Security schemes for tests that authenticate without a header

`openApi { bearerAuth() }` and `basicAuth()` mark the operation as requiring authentication in OpenAPI even when the request has no `Authorization` header. You get the same security scheme and operation `security` as when the header is sent, and Swagger UI shows the Authorize button.

```kotlin
mockMvc.perform(get("/api/me").with(jwt()))
    .andStatus(HttpStatus.OK)
    .andDocument {
        responseFields<MeResponse>()
        openApi { bearerAuth() }
    }
```

| Declaration | OpenAPI security scheme | Equivalent header |
|---|---|---|
| `bearerAuth()` | `bearerAuthJWT` (`type: http`, `scheme: bearer`, `bearerFormat: JWT`) | `Authorization: Bearer ...` |
| `basicAuth()` | `basic` (`type: http`, `scheme: basic`) | `Authorization: Basic ...` |

- A fake value in the right format goes into the OpenAPI fragment (`resource.json`) only. REST Docs snippets (`http-request`, `curl-request`, and so on) and the request header table don't change.
- Even if the request has an `Authorization` of a different scheme, OpenAPI follows the declaration. It is applied to declared operations even with `open-api.security-schemes: false`.
- MockMvc and WebTestClient (`mutateWith(mockJwt())` and so on) behave the same.
- To show `Authorization` as a request header parameter, document `requestHeaders("Authorization" headerMeans "...")` separately. In that case the actual request must contain the header.

#### OAuth2 and OpenID Connect

When the API takes tokens from an OAuth2 authorization server, declare the scopes the operation needs. Swagger UI then shows the OAuth2 login in Authorize instead of a token box.

```kotlin
mockMvc.perform(get("/api/users/{id}", 1L).with(jwt()))
    .andStatus(HttpStatus.OK)
    .andDocument {
        responseFields<UserResponse>()
        openApi { oauth2("users:read") }       // or openIdConnect("openid", "profile")
    }
```

| Declaration | Operation `security` | Security scheme |
|---|---|---|
| `oauth2("users:read", ...)` | `- oauth2: [users:read]` | `oauth2` (`type: oauth2`) with the flows from the build |
| `openIdConnect("openid", ...)` | `- openIdConnect: [openid]` | `openIdConnect` (`type: openIdConnect`) with the discovery URL from the build |

Scopes are optional (`oauth2()` requires a token without particular scopes). The scheme names are fixed, `oauth2` and `openIdConnect`.

A test doesn't know the token and authorization URLs, so they go in the Gradle plugin, once per project:

```kotlin
kardx {
    openApiSecurity {
        oauth2 {
            clientCredentials("https://auth.example.com/oauth/token")
            authorizationCode(
                "https://auth.example.com/oauth/authorize",
                "https://auth.example.com/oauth/token",
            )
            scope("users:read", "Read users")
        }
        openIdConnect("https://auth.example.com/.well-known/openid-configuration")
    }
}
```

- `scope(name, description)` fills the `scopes` map of every flow. A scope an operation uses but `scope()` doesn't declare is still listed, with an empty description, so declare scopes to describe them.
- The methods take positional arguments: `clientCredentials(tokenUrl)`, `authorizationCode(authorizationUrl, tokenUrl)`, `password(tokenUrl)`, `implicit(authorizationUrl)`. They are Java methods, so Kotlin named arguments (`tokenUrl = ...`) don't compile.
- The extension also works in a Groovy build script.
- If an operation uses a scheme that `openApiSecurity` doesn't configure, the build warns with the operation and the setting to add. OpenAPI requires the flows of `oauth2` and the URL of `openIdConnect`, so when no valid scheme can be written, that operation's security requirement is left out.
- As with `bearerAuth()`, nothing changes in the REST Docs snippets.

## Common response wrapper

If every API wraps its response, as in `{ "code": 200, "message": "OK", "data": { ... } }`, register the wrapper once, in yml or in code. `data-path` (`dataPath`) defaults to `data`.

```yaml
kardx:
  response-wrapper:
    class: com.example.common.ApiResponse
    data-path: data
```

```kotlin
RestDocsConfiguration.configure {
    extraction.responseWrapper<ApiResponse<*>>(dataPath = "data")
    // or: extraction.responseWrapper = ResponseWrapper(ApiResponse::class, dataPath = "data")
}
```

| Function | Result |
|---|---|
| `wrappedResponseFieldsOf<UserResponse>()` | `code`, `message`, `data` (Object), `data.id` … |
| `wrappedResponseFieldsOf<List<UserResponse>>()` | `data` (Array), `data[].id` … |
| `wrappedResponseFieldsOf<PageResponse<UserResponse>>()` (your own page class with `total` and `content`) | `data.total`, `data.content[].id` … |
| `wrappedResponseFieldsOf<Page<UserResponse>>()` (Spring Data `Page`) | `data.content[].id`, `data.totalElements`, `data.totalPages` … ([Spring Data pages](#spring-data-pages)) |
| `wrappedResponseFieldsOf<UserResponse>(dataOptional = true)` | A response that may have no data. All data fields are optional |
| `wrappedResponseFieldsOf(PageResponse::class, UserResponse::class, "data.content[]")` | A non-generic intermediate wrapper |
| `wrappedResponseFieldsOf("data.id" type NUMBER means "ID")` | Wrapper fields + hand-written data fields |

These functions return lists. To register directly in the `andDocument { }` block, use the shortcuts with the same arguments: `wrappedResponseFields<UserResponse>()`, `wrappedResponseFields<UserResponse>(dataOptional = true)`, `wrappedResponseFields("data.id" type NUMBER means "ID")` (equivalent to `responseFields(wrappedResponseFieldsOf<T>())`). The non-generic intermediate wrapper (the `KClass` version) has no shortcut, so write `responseFields(wrappedResponseFieldsOf(PageResponse::class, UserResponse::class, "data.content[]"))`.

```kotlin
.andDocument {
    wrappedResponseFields<UserResponse>()
}
```

The description of the `data` row is the description of the wrapper class's `data` property (`@Description` or [`description-annotations`](#reading-descriptions-from-other-annotations)). Without one, it uses the data type name (`UserResponse` for `wrappedResponseFieldsOf<UserResponse>()`) instead of `No description`. The same goes for the data property of an intermediate wrapper (`data.content`). If no wrapper is registered, only the data fields are extracted, from the root. The functions and shortcuts are in the `io.github.kardx.extractor` package, like `responseFieldsOf` (`import io.github.kardx.extractor.wrappedResponseFieldsOf`, `...extractor.wrappedResponseFields`).

## Documenting multiple status codes

Document the success and error responses of an API by writing one test per status code. For errors that aren't worth a test of their own, declare them in an existing test with `errorResponse(...)` ([Declared error responses](#declared-error-responses)).

- **OpenAPI (Swagger)**: Tests with the same path and method are merged into one operation, with a response for each status code. For example, `GET /products/{id}` gets both `200` and `404`.
- **REST Docs documentation**: Each test gets its own section, and the response status is added to the summary at the top of the section (`GET /products/{id}` → `404 Not Found`).

```kotlin
@Test
@DisplayName("Get product")
fun getProduct() {
    mockMvc
        .perform(get("/api/products/{id}", 1L))
        .andStatus(HttpStatus.OK)
        .andDocument {
            pathParameters("id" pathMeans "Product ID")
            wrappedResponseFields<ProductResponse>()
        }
}

@Test
@DisplayName("Get product - product not found (404)")
fun getProductNotFound() {
    mockMvc
        .perform(get("/api/products/{id}", 999L))
        .andStatus(HttpStatus.NOT_FOUND)
        .andDocument {
            description = "Returns 404 and an error message if the product doesn't exist."
            pathParameters("id" pathMeans "Product ID")
            responseFields<ErrorResponse>()        // shared error response class
        }
}

@Test
@DisplayName("Create product - invalid input (400)")
fun createProductInvalid() {
    mockMvc
        .perform(
            post("/api/products")
                .jsonContent(CreateProductRequest(name = "", price = BigDecimal("-1"))),
        )
        .andStatus(HttpStatus.BAD_REQUEST)
        .andDocument {
            requestFields<CreateProductRequest>()
            responseFields<ErrorResponse>()        // field errors listed in errors
        }
}
```

Don't set the schema name of error responses (`ErrorResponse`) in each test; specify it once in the settings ([One class for error responses](#one-class-for-error-responses)).

| Error response shape | Response fields in the block |
|---|---|
| Dedicated error class (recommended) | `responseFields<ErrorResponse>()` |
| Common wrapper, `data` is null | `wrappedResponseFields<Nothing?>()` |
| Common wrapper, `data` holds error details | `wrappedResponseFields<List<FieldErrorDetail>>()` |

- The operation summary and description come from the success response test (in the order 200, 201, 202, 204, then other 2xx) (Gradle plugin). Descriptions from error tests appear in that section of the REST Docs documentation.
- The response description (`description`) is the reason phrase of the status (`OK`, `Not Found`). If the summary of the test that produced that response differs from the operation summary, it is appended: `Not Found: Get product - product not found (404)` (Gradle plugin; epages leaves it as `"404"`).
- Each test adds one request and one response example, named after the snippet directory and summarized by the test's `@DisplayName`. The summaries appear in Swagger UI's example selector, and request examples from success response tests come first.
- For error-checking tests you don't want in the documentation, just don't call `andDocument`. They don't appear in the documentation or the spec. (Tests that actually **fail** are [left out automatically](asciidoc.md#automatic-document-generation).)
- In error tests too, documented request fields must match the actual request. Even if a value triggers a validation failure, the field itself must be sent.

### One class for error responses

Error responses (4xx, 5xx) usually use the same class across all APIs. If so, set the schema name once in the settings. It is applied automatically to error responses whose test doesn't set `responseSchema`.

```kotlin
data class ErrorResponse(
    @Description("Error code") val code: Int,
    @Description("Error message") val message: String,
    @Description("Invalid input fields. Only for validation errors") val errors: List<FieldErrorDetail>? = null,
)
```

You set it in **the project that uses the library**. The library has no default (without one, the extracted type name is used). Choose one of the two.

**In code** — Set it in your test base class before building MockMvc. The MVC samples (Boot 3 and 4) do it this way and keep their settings in a `DocsConfig` object (`samples/*/src/test/.../support/DocsConfig.kt`).

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
abstract class RestDocsTestSupport {
    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpRestDocs(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        RestDocsConfiguration.configure { openApi.errorSchema<ErrorResponse>() }
        // openApi.errorSchema(ErrorResponse::class) and openApi.errorSchema = "ErrorResponse" do the same
        mockMvc = restDocsMockMvc(context, provider)
    }
}
```

**In yml** — Write it in test settings such as `src/test/resources/application-docs.yml`. The WebFlux sample (`samples/spring-boot-4-webflux/src/test/resources/application-docs.yml`) uses this form.

```yaml
kardx:
  open-api:
    error-schema: com.example.common.ErrorResponse   # class name (FQCN) or schema name
```

Then error tests list only the fields. Both 404 and 400 point to `#/components/schemas/ErrorResponse`.

```kotlin
mockMvc
    .perform(get("/api/products/{id}", 999L))
    .andStatus(HttpStatus.NOT_FOUND)
    .andDocument { responseFields<ErrorResponse>() }
```

**Different structures under the same name are merged.** For example, even if you document 404 without `errors` and 400 with `errors`, the Gradle plugin combines the two shapes into one `ErrorResponse` (with `errors` optional) ([Schema names](#schema-names)). If the same path has different types (`data` is an object on one side and an array on the other), they can't be merged and you get a warning, so give each test a different name. A single dedicated error class with optional fields (`errors`), as above, describes everything accurately in one schema from the start.

### Declared error responses

A test per error (400, 401, 403, 409 …) often costs more than the error is worth: you need invalid input, a missing token or a conflicting record just to produce it. Instead, declare those errors in the block of a test you already have. They go into that test's document and into its OpenAPI operation.

```kotlin
@Test
@DisplayName("Create product")
fun createProduct() {
    mockMvc
        .perform(
            post("/api/v1/products")
                .jsonContent(CreateProductRequest(name = "Keyboard", price = BigDecimal("59000"))),
        )
        .andStatus(HttpStatus.CREATED)
        .andDocument {
            requestFields<CreateProductRequest>()
            wrappedResponseFields<ProductResponse>()

            errorResponse(HttpStatus.UNAUTHORIZED, "Token missing or expired")
            errorResponse(HttpStatus.BAD_REQUEST, "Invalid input") {
                fields<ErrorResponse>()
                example(ErrorResponse(400, "Invalid input", listOf(FieldErrorDetail("name", "must not be blank"))))
            }
            errorResponse(HttpStatus.CONFLICT, "Product name already registered", ErrorResponse(409, "Already registered"))
        }
}
```

| Form | What is documented |
|---|---|
| `errorResponse(409, "...")` | Status and description only |
| `errorResponse(409, "...") { fields(...) }`, `{ fields<ErrorResponse>() }` | Body fields: a field table and the OpenAPI response schema. `fields<T>()` extracts with the response rules (`import io.github.kardx.extractor.fields`) |
| `{ example("""{"code":409}""") }` | Body example, as JSON text |
| `{ example(ErrorResponse(...)) }` | Body example, serialized with the same Jackson mapper as response bodies (`@JsonProperty`, naming strategy and null handling apply). A `String` is taken as JSON text as is |
| `{ contentType = "application/problem+json" }` | Media type. Default `application/json`; types ending in `+json` are treated as JSON |
| `{ contentType = "text/plain"; example("Too many requests") }` | A body that isn't JSON (`text/plain`, `text/html` …). The example is taken as text |
| `errorResponse(409, "...", ErrorResponse(...))` | One-liner: fields extracted from the object's runtime class, and the object as the example |

The status is an `Int` or an `HttpStatusCode` (`HttpStatus.CONFLICT`).

**What comes out**

- REST Docs: an `error-responses` snippet, included in the test's section under the `Error Responses` subheading. It has a table of status and description (`409 Conflict`), then, for each declaration with fields or an example, a `409 Conflict response fields` table and a `409 Conflict response example` block. The headings follow the [documentation language](configuration.md#documentation-language).
- OpenAPI (Gradle plugin): a response per status with the description, the example, and a body schema. Declared fields become a schema in `components.schemas`: an identical existing schema (such as `ErrorResponse` from another test) is reused, otherwise it is named after the status (`ConflictResponse`, prefixed with the operationId if that name is taken). Without fields, a JSON body points to the common error schema described in [Common error responses](#common-error-responses), if there is one. A body that isn't JSON and has no fields gets `type: string` with the example as a string; the common JSON error schema is never attached to it.
- So the two outputs differ for a declaration without a body: `errorResponse(400, "Invalid input")` shows only the status and description in the HTML document, but its OpenAPI response still gets the common error body (the schema set with `open-api.error-schema`, or the one error responses use most, under `application/json` unless the error responses that use it say otherwise). Give fields or an example if the HTML should show the body too.
- Schemas are matched by shape only, not by `contentType`. A declared body with the same shape as an existing schema reuses that schema's name (for example `ErrorResponse`), even when you declare it as `application/problem+json` and the existing one came from `application/json` responses.
- If several tests of the same operation declare the same status, they are merged. If their descriptions differ, the first by snippet path wins and the build logs `OpenAPI declared responses: ... is declared with different descriptions ...`.

**Which response wins** for a status of one operation:

1. A response a test actually produced.
2. A declared response.
3. A common response from `openApiCommonResponses`.

A declaration for the status the test itself received is dropped without a warning, from both the snippet and OpenAPI, since the real response already documents it. Declared 401 and 403 are added whether or not the operation has a security requirement (unlike common responses).

**Limits**

- Only 400–599. A status outside that range, a blank description, a blank `contentType` or example, or the same status declared twice in one block throws `IllegalArgumentException`.
- **Declared, not verified.** Nothing checks that the API really returns this status or this body. The only check: when you give both fields and a JSON example, the example must match the fields (an undocumented or missing required field fails with `DocumentationMismatchException`), and an example for a JSON media type must be valid JSON.
- The one-liner can't extract fields from a collection, array or `Map`, a generic class (`RestResponse<ErrorBody>`), or a plain value such as a string. You get the example without fields. Use the block form and name the type: `{ fields<RestResponse<ErrorBody>>(); example(...) }`.
- Declarations are not wrapped with the [common response wrapper](#common-response-wrapper). If your error bodies are wrapped, pass the wrapper object as the example and extract fields from the wrapper type.

### Common error responses

Responses that almost every API can return but that you don't test for each API, such as 401 (authentication failed) or 500 (server error), are declared once in the Gradle plugin settings.

```kotlin
kardx {
    openApiCommonResponses.put(401, "Token missing or expired")
    openApiCommonResponses.put(500, "Server error")    // an empty description ("") uses the standard phrase (Internal Server Error)
    // openApiCommonResponseSchema = "ErrorResponse"   // default: the schema most error responses refer to
}
```

- They are added to each operation only for status codes its tests didn't document or [declare](#declared-error-responses). Responses documented or declared by tests stay as they are.
- 401 and 403 are added only to operations with a security requirement.
- The response body refers to the common error schema: the name set in `openApiCommonResponseSchema`, or if not set, the schema most error responses (4xx, 5xx) in the spec refer to (usually the `open-api.error-schema` above). If there is no such schema, or the name you set isn't in the spec (with a warning), only the description is added.
- In the REST Docs HTML, a `Common error responses` table (`공통 오류 응답` with `language = "ko"`) is added once at the end of the index document (anchor `common-error-responses`). It isn't added to the per-test-class documents.
- The Postman Collection isn't changed.

This setting and [`errorResponse(...)`](#declared-error-responses) are **the only places where content not verified by a test enters the documents**. Nobody checks that the responses declared here are really sent in that shape. That is why it's off by default. Document important error responses with tests.

## OpenAPI names and metadata

These settings shape how operations and schemas are named and grouped in the OpenAPI spec. They apply to every documented request, not only to multi-status APIs.

### Schema names

A schema name is the name under which OpenAPI registers a body structure in `components.schemas`. Requests and responses point to it with `$ref: "#/components/schemas/TodoResponse"`. It doesn't affect the REST Docs documentation or field verification.

Most of the time you don't need `openApi { requestSchema / responseSchema }`, because bodies extracted from types get a name automatically. There are only two cases for setting it yourself: when you want a different name than the automatic one (for example, `TodoResponse` instead of `ApiResponseTodoResponse`), and when there is no automatic name because you wrote every field by hand or mixed lists extracted from different types or paths. Editing an extracted list (`filterNot`, `except`, `+` with hand-written fields) keeps the automatic name.

**Names are assigned automatically by default.** Bodies extracted with `responseFieldsOf<T>()`, `requestFieldsOf<T>()`, or `wrappedResponseFieldsOf<T>()` use the name of that type. The block shortcuts (`responseFields<T>()`, `requestFields<T>()`, `wrappedResponseFields<T>()`) call these functions, so they behave the same.

| Extraction | Schema name |
|---|---|
| `requestFieldsOf<CreateTodoRequest>()` | `CreateTodoRequest` |
| `responseFieldsOf<ErrorResponse>()` | `ErrorResponse` |
| `wrappedResponseFieldsOf<TodoResponse>()` (wrapper `ApiResponse`) | `ApiResponseTodoResponse` |
| `wrappedResponseFieldsOf<PageResponse<TodoResponse>>()` | `ApiResponsePageResponseTodoResponse` |
| `responseFieldsOf<TodoResponse>("[]")` | `ListTodoResponse` |
| Extracted with another path (`responseFieldsOf<T>("data")`), hand-written fields only, lists extracted from different types or paths mixed together | None (epages generates a name like `api-todos-1290543316`) |
| Bodies without fields (single-value `String` or `Long`, files), `relaxed = true` | None (no type name is given to an empty object schema) |

The name is taken from the first of the following that is set.

1. `openApi { responseSchema ... }` in the test
2. For error responses (4xx, 5xx), `openApi.errorSchema` ([One class for error responses](#one-class-for-error-responses))
3. The name of the extracted type

| Setting | yml | Description |
|---|---|---|
| `openApi.schemaNames` | `open-api.schema-names` | Turns automatic names on or off. Default true |
| `openApi.schemaNamesWithoutWrapper` | `open-api.schema-names-without-wrapper` | Drops the common response wrapper's name (`ApiResponseTodoResponse` → `TodoResponse`). Default false. See below |
| `openApi.errorSchema` | `open-api.error-schema` | Shared name for error responses (a name or a class name) |
| `openApi.schemaNameResolver` | None (code only) | Type → name rule. For example, `SchemaNameResolver { "Dto" + SchemaNames.of(it.type) }` (`io.github.kardx.openapi.SchemaNames`) |

**To drop the wrapper name**, turn on one yml line. Only the automatic names of responses extracted with `wrappedResponseFieldsOf<T>()` change; the schema contents (the wrapper fields `code`, `message`, `data`) stay the same.

```yaml
kardx:
  open-api:
    schema-names-without-wrapper: true
```

| Extraction (wrapper `ApiResponse`) | Default | When on |
|---|---|---|
| `wrappedResponseFieldsOf<TodoResponse>()` | `ApiResponseTodoResponse` | `TodoResponse` |
| `wrappedResponseFieldsOf<List<TodoResponse>>()` | `ApiResponseListTodoResponse` | `ListTodoResponse` |
| `wrappedResponseFieldsOf(PageResponse::class, TodoResponse::class, "data.content[]")` | `ApiResponsePageResponseTodoResponse` | `PageResponseTodoResponse` |
| `wrappedResponseFieldsOf<Unit>()` | `ApiResponseUnit` | `ApiResponseUnit` (unchanged when the data is `Unit` or `Any`) |

If a shortened name collides with another schema of a different shape (when the same `TodoResponse` is also used as a request body, or another API returns it without the wrapper), the plugin splits them according to the rules below. In that case the next candidate for the wrapped response is the name with the wrapper (`ApiResponseTodoResponse`), and the changes are written to the build log.

**The Gradle plugin resolves name collisions.** Names don't include the package, so they can collide, and epages keeps only one of the schemas with the same name. Before generating OpenAPI (`apiDocsOpenApiSnippets`), the plugin finds schemas with the same name but a different structure (field paths, types, required flags), resolves them with the following rules, and writes the changes to the build log (`OpenAPI schema names: Item → OrderItem (...)`). The result is the same regardless of test execution order.

| Situation | Result |
|---|---|
| Automatic names of different types collide (`order.Item`, `cart.Item`) | Qualifies them at the shortest level that makes every type's name distinct: the enclosing class name (`OuterItem`), then package names from the end (`OrderItem`, `CartItem`, `ShopOrderItem` …). The same levels apply to type arguments |
| Request and response shapes of the same type differ (`@JsonProperty(access = WRITE_ONLY/READ_ONLY)`, `val` outside the constructor) | The response is `Account`, the request is `AccountRequest` |
| An explicitly set name (`responseSchema`, `openApi.errorSchema`) collides with an automatic name | The explicit one keeps the name, and the automatic name changes by the rules above |
| Explicit names collide with each other, or the same type in the same direction has different shapes | Merged into one schema with the combined fields. Only fields that are present and required everywhere stay required |
| In the case above, the same path has different types (`value` is a String and a Number) | They can't be merged, so it warns (`warn`) and leaves `VARIES`. Give each test a different name |

Explicit names are merged rather than renamed because other tools (client generators and so on) may be using them. The merged schema still describes every response accurately (fields present in only some are optional). Without the plugin there is no such cleanup, so give `responseSchema` names that don't collide.

To set the names in a test, write this.

```kotlin
openApi {
    requestSchema<CreateTodoRequest>()                 // CreateTodoRequest
    responseSchema<ApiResponse<TodoResponse>>()        // ApiResponseTodoResponse (generics are concatenated)
    // responseSchema(ErrorResponse::class) and responseSchema = "ErrorResponse" do the same
}
```

### operationId

The OpenAPI `operationId` becomes the Swagger UI deep link (`#/<tag>/<operationId>`) and the method name in client code generators (OpenAPI Generator and so on). By default, it is **the name of the controller (handler) method that handled the request**.

```kotlin
@RestController
class UserController {
    @GetMapping("/api/v1/users/search")
    fun searchUsers(...) = ...      // operationId: searchUsers
}
```

- MockMvc reads the name from `MvcResult.handler`; WebTestClient reads it from the handler recorded by `restDocsWebTestClient` or a client with `recordHandlers()` applied ([WebTestClient](webtestclient.md#operationid)). Kotlin `suspend` functions, `internal` functions (JVM name `name$module`), and CGLIB-proxied controllers all yield the name in the source.
- Even if several tests document the same operation (HTTP method + path), there is one id.
- If different operations end up with the same id (`get` or `create` in several controllers, overloads), **only the colliding ones** get the controller name as a prefix. The `RestController`, `Controller`, and `Api` suffixes are dropped from the controller name (`ProductController.get` → `productGet`, `CouponController.get` → `couponGet`). If they still collide, a number is appended in path order (`userGet2`). Processing is in path and HTTP method order, so the result is the same regardless of test execution order.
- Requests whose handler is unknown get an id from the test name (`test-name` below): functional routers (`RouterFunction`), static resources, WebTestClient clients that don't record handlers (`bindToServer`, `bindToController` or `bindToRouterFunction` without `recordHandlers()`), and RestTestClient.

**There are three ways to change it, and the earlier one wins.**

1. Directly in the test: `openApi { operationId = "searchUsersByNickname" }`. The Gradle plugin uses it as is, without changing characters or adding numbers. If it collides with another operation, the build log shows an `OpenAPI operationId: ...` warning. An empty string or one containing whitespace throws `IllegalArgumentException`.
2. Your own rule: `openApi.operationIdResolver = OperationIdResolver { input -> ... }`. It receives an `OperationIdInput` (HTTP method, path template, controller simple name, handler method name, test-based id) and returns an id. If it returns `null`, option 3 is used. The returned id is used as is, like option 1 (with a warning on collision).
3. Choosing a predefined rule: `openApi.operationIdRule` (yml `kardx.open-api.operation-id-rule`).

| Rule (yml value) | `GET /api/v1/users/search` of `UserController.searchUsers` | When the handler is unknown |
|---|---|---|
| `HANDLER_METHOD` (`handler-method`, default) | `searchUsers`. Only colliding ones become `userSearchUsers` | Test name |
| `CONTROLLER_AND_METHOD` (`controller-and-method`) | `userSearchUsers` | Test name |
| `PATH` (`path`) | `getApiV1UsersSearch`. Path variables become `By` + name (`/users/{id}` → `getUsersById`), and characters other than letters and digits are word boundaries | Same rule (no handler needed) |
| `TEST_NAME` (`test-name`) | `user-api-docs-test.search-users` (the snippet directory `class-name/method-name` with `/` replaced by `.`) | — |

```yaml
kardx:
  open-api:
    operation-id-rule: path
```

```kotlin
RestDocsConfiguration.configure {
    openApi.operationIdResolver = OperationIdResolver { input ->
        input.handlerMethodName?.let { "${input.controllerName?.removeSuffix("Controller")?.lowercase()}_$it" }
    }
}
```

With the `TEST_NAME` rule or the test-name fallback, non-ASCII test names (such as Korean names in Kotest or JUnit backtick methods) stay in the id as is. That is valid OpenAPI, but code generators may fail to create method names from it, so use option 1 for those tests or choose the `PATH` rule ([Kotest](kotest.md#index-and-openapi)).

The operationId is used only in the OpenAPI spec. Snippet directories, AsciiDoc section anchors, WireMock mapping file names, and schema names still come from the test name.

### OpenAPI tags and summaries

Every operation gets a tag and a summary. Swagger UI groups APIs by tag and shows the summary as the one-line description in the list.

| Item | Value |
|---|---|
| Tag | The document class's `@DisplayName`, or the simple class name if absent. It's the same as the AsciiDoc document title. The document class is the outermost class for JUnit `@Nested`, and the spec for Kotest |
| Summary (JUnit) | The test method's `@DisplayName`, or the method name if absent (`` fun `get user`() `` → `get user`, `fun getUser()` → `getUser`) |
| Summary (Kotest) | The section title ([Kotest](kotest.md#section-title)) |

- The invocations of a `@ParameterizedTest` share the method's summary (not the per-invocation display name such as `[1] 1`).
- Documenting from another thread created by the test gives the same values.
- `openApi { tags("My page") }` adds a tag. The default tag stays.
- AsyncAPI message tags and summaries follow the same rules ([Documenting messages](messaging.md)).

**Setting the summary and description yourself.** When several tests document the same operation (one per subtype, or success and error cases), the merged operation takes the summary of the representative test (the success test first, then by snippet directory name), which can read like one example rather than the API (`Pay by bank transfer`). Set the operation's own name with `openApi { summary }`, and an OpenAPI-only description with `openApi { description }`:

```kotlin
.andDocument {
    description = "Pays with a bank transfer."
    openApi {
        summary = "Pay for an order"            // operation summary instead of the test name
        description = "Pays for an order. Choose the method with method.type."  // instead of the block description
    }
}
```

- The Gradle plugin sets them on the operation after epages merges the fragments, and the per-version specs (`splitSpecs`) carry the same values. The `###` titles in the HTTP Client export (`api.http`) follow the spec's summary.
- If only `summary` is set and the operation had no description of its own (it was showing the summary), the description follows the new summary.
- If tests of the same operation set different values, the first one in snippet directory order wins and the build logs an `OpenAPI summary/description: POST /api/v1/payments: ...` warning. Setting the same value in each test is fine.
- REST Docs output doesn't change: `api-summary`, the section titles and the OpenAPI example names (`summary` of each example) keep the test names. Response descriptions such as `Created` are still worked out from the test names.
- Without the Gradle plugin, the values only stay in `openapi-extras.json`.
- Without `openApi { summary }`, the KDoc/Javadoc of the controller method comes before the test name when `sourceDocs { enabled = true; methodDocs = true }` is set ([Controller methods → OpenAPI summary and description](#controller-methods--openapi-summary-and-description)).

### Enums as dropdowns

For enum-typed fields and parameters, the constant list is exported as OpenAPI `enum`. In Swagger UI's Try it out, you pick query and path parameters from a dropdown, and schemas show the allowed values.

```yaml
- name: status
  in: query
  schema:
    type: string
    enum: [TODO, DOING, DONE]
```

- Extraction such as `parametersOf<T>()` and `responseFieldsOf<T>()` adds it automatically.
- When writing by hand, add `enumOf`: `"status" queryMeans "Status" enumOf TodoStatus::class`, `"status" type ENUM means "Status" enumOf TodoStatus::class`. For parameters, `ofType TodoStatus::class` does the same.
- The type column in the REST Docs table is the JSON type (`String`), and REST Docs verifies that the actual value is a string. Enums whose `@JsonValue` returns a number are verified as `Number`. Values are what actually appears in JSON (`@JsonValue`, `@JsonProperty` on the constant, otherwise the constant name).
- For enum lists (`List<Level>`), the type column is `Array`, and OpenAPI gets `type: array`, `items: {type: string, enum: [...]}`.
- AsyncAPI schemas get the same `enum`.

### Regex path variables

For Spring regex path variables (`@GetMapping("/files/{name:.+}")`), the regex is removed from the OpenAPI path. The OpenAPI path is `/files/{name}` and the parameter name is `name`, so the description from `pathParameters("name" pathMeans "...")` carries over. Braces inside the regex (`{code:[A-Z]{2}}`) are handled too. The URL in the REST Docs documentation (`api-summary`) is the template used in the test, as is.

```kotlin
mockMvc.perform(get("/files/{name:.+}", "report.final.pdf"))
    .andDocument { pathParameters("name" pathMeans "File name") }
```

### Single-value JSON bodies

For file bodies (`application/pdf`, `application/octet-stream` …) and `text/plain` or `text/csv` bodies, see [File downloads](#file-downloads).

A single-value JSON body (`Long`, `List<String>`) extracted from its type, as with `responseFields<Long>()`, gets that type's schema (`type: integer`, `format: int64`). There is no field table ([Empty extraction results](#empty-extraction-results)).

## File downloads

For APIs that return files, document the path and query parameters and the response headers (`Content-Type`, `Content-Disposition`), and say what the file is with `responseFile("...")`. The body has no fields, so `responseFields` can't be used with `responseFile` (the test fails with `responseFile cannot be used together with responseFields`). Check the received bytes with `andReturnEntity<ByteArray>()` (example: `downloadManual` in [ProductApiDocsTest](../samples/spring-boot-4/src/test/kotlin/com/example/restdocs/product/ProductApiDocsTest.kt)).

```kotlin
@Test
@DisplayName("Download product manual (PDF)")
fun downloadManual() {
    val response = mockMvc.perform(get("/api/v1/products/{id}/manual", 1L).param("lang", "ko"))
        .andStatus(HttpStatus.OK)
        .andDocument {
            description = "Downloads the product manual PDF as an attachment. The file name is in `Content-Disposition`."
            pathParameters("id" pathMeans "Product ID" ofType Long::class)
            queryParameters("lang" queryMeans "Manual language (ko, en)" isOptional true withDefaultValue "ko")
            responseHeaders(
                "Content-Type" headerMeans "File type (`application/pdf`)",
                "Content-Disposition" headerMeans "Attachment and file name (`attachment; filename=...`)",
            )
            responseFile("Product manual PDF")
        }
        .andReturnEntity<ByteArray>()

    assertThat(response.headers.contentDisposition.filename).isEqualTo("product-1-manual-ko.pdf")
    assertThat(response.body).startsWith(*"%PDF-".toByteArray())
}
```

### What goes into snippets

For non-text bodies, snippets show only the size instead of the bytes. Left as is, the bytes would print garbled characters into `http-response.adoc`, and large files would flood the documentation. Headers (including `Content-Length`) keep their actual values.

```http
HTTP/1.1 200 OK
Content-Type: application/pdf;charset=UTF-8
Content-Disposition: attachment; filename="product-1-manual-ko.pdf"
Content-Length: 45

<binary: 45 bytes>
```

Because the bytes aren't shown, a `response-file` snippet tells the reader what the body is: the `responseFile` description, the media type without `charset`, the file name from the actual `Content-Disposition`, and the size of the body the test received. Every binary body gets it, even without `responseFile` (then without the description row). The labels follow the [documentation language](configuration.md#documentation-language).

```asciidoc
[cols="1h,3"]
|===
|Description
|Product manual PDF

|Media type
|`+application/pdf+`

|File name (example)
|`+product-1-manual-ko.pdf+`

|Size (example)
|45 bytes

|===
```

The generated document includes it under the `Response File` subheading, after the response headers. If you add `response-body` to [`document.snippets`](customizing-snippets.md#choosing-snippets-for-the-document), the table takes its place for this operation. A text body (JSON, CSV …) gets the table only when you call `responseFile`, for example for a CSV export, and its content stays in the snippets as well.

| Output | Binary body |
|---|---|
| `http-response`, `response-body` | `<binary: N bytes>`. `response-body` is a plain `[source]` block (REST Docs would use the subtype, `[source,pdf]`) |
| `response-file`, `request-file` | Description, media type, file name, and size of the body |
| `http-request`, `request-body`, `curl-request`, `httpie-request` | The same for file uploads. `application/octet-stream` request bodies and multipart file parts become `<binary: N bytes>` |
| WireMock mappings | The original bytes are written as `base64Body` (the stub returns the real file) |
| Example responses in the Postman Collection | `<binary: N bytes>` |
| Your own preprocessors (`http.requestPreprocessors`, `http.responsePreprocessors`) | Receive the body already replaced with the size |

Content-Types treated as text and left as is are `text/*` (`text/csv`, `text/plain` …), those whose subtype contains `json` or `xml` (`application/json`, `application/hal+json`, `application/problem+json`, `application/x-ndjson`, `image/svg+xml` …), `application/x-www-form-urlencoded`, YAML, JavaScript, GraphQL, SQL, and `multipart/*` (each part is judged separately by its own Content-Type). Bodies without a Content-Type are left as is too. So a CSV export appears in the documentation with its content.

`restDocsMockMvc` forces the UTF-8 encoding filter onto responses too, so `;charset=UTF-8` is added to `Content-Type` (as in the example above). This may differ from the real app's response headers.

### File uploads in the request body

For a request whose whole body is a file (`application/octet-stream`, `image/png` …, not multipart), describe it with `requestFile("...")`. It creates a `request-file` table with the same rows (included under `Request File`) and the description of the OpenAPI request body. Multipart uploads document their file parts with `requestParts` instead ([Multipart and cookies](#multipart-and-cookies)), so `requestFile` can't be used with `requestParts` or `requestFields`.

```kotlin
mockMvc.perform(put("/api/v1/products/{id}/image", 1L).contentType(MediaType.IMAGE_PNG).content(png))
    .andStatus(HttpStatus.NO_CONTENT)
    .andDocument {
        pathParameters("id" pathMeans "Product ID")
        requestFile("Original image to upload")
    }
```

### OpenAPI output

The body has no fields, so epages creates an empty object schema. After `openapi3`, the Gradle plugin changes empty schemas of non-JSON bodies to `type: string`. For anything other than `text/*` it adds `format: binary`, removes the examples (`examples`), and drops the `charset` parameter from the media type (`application/pdf;charset=UTF-8` → `application/pdf`, since a binary body has no character set). `text/plain` and `text/csv` become `type: string` and keep their examples and media type.

The `responseFile` and `requestFile` descriptions become the `description` of the body schema, and a documented `Content-Disposition` header of a file response gets the actual value as its `example`. Documented response headers become the response `headers`.

```yaml
/api/v1/products/{id}/manual:
  get:
    operationId: manual
    parameters:
    - name: id
      in: path
      description: Product ID
      required: true
      schema:
        type: integer
        format: int64
    - name: lang
      in: query
      description: Manual language (ko, en)
      required: false
      schema:
        type: string
        default: ko
    responses:
      "200":
        description: OK
        headers:
          Content-Type:
            description: File type (`application/pdf`)
            schema:
              type: string
          Content-Disposition:
            description: Attachment and file name (`attachment; filename=...`)
            schema:
              type: string
            example: attachment; filename="product-1-manual-ko.pdf"
        content:
          application/pdf:
            schema:
              type: string
              format: binary
              description: Product manual PDF
```

If you use only the epages plugin without the Gradle plugin, the empty object schema, the `charset` in the media type, and the `<binary: N bytes>` example stay as they are, and the file description doesn't appear.

## What goes into the OpenAPI spec

Extractors and modifiers record more detail than the REST Docs table shows (whether a number is an integer, the date format, length limits, and so on) as descriptor attributes. epages doesn't read these attributes, so the Gradle plugin (`io.github.kardx-project`) merges them into the generated spec after `openapi3`. The result is the same regardless of test execution order, and the same for Boot 3 (epages 0.19) and Boot 4 (0.20). If you use epages alone without the plugin, only part of it comes through, as described [below](#without-the-gradle-plugin).

### Types and formats

`responseFieldsOf`, `requestFieldsOf`, and `parametersOf` take them from property types; hand-written parameters take them from `ofType`, or from the controller parameter type when it's absent ([Parameter types come from the handler](#parameter-types-come-from-the-handler)). The Type column of the REST Docs table stays the JSON type (`Number`, `String`).

| Kotlin / JDK type | OpenAPI |
|---|---|
| `Int`, `Short`, `Byte` / `Long` / `BigInteger` | `integer` + `int32` / `int64` / none |
| `Float` / `Double` / `BigDecimal` | `number` + `float` / `double` / none |
| `Boolean` | `boolean` |
| `Instant`, `OffsetDateTime`, `ZonedDateTime`, `LocalDateTime`, `Date` | `string` + `date-time` |
| `LocalDate` / `LocalTime` | `string` + `date` / `time` |
| `Duration`, `Period` | `string` + `duration` (Boot's default serialization, `PT1H30M`) |
| `UUID` / `URI`·`URL` / `ByteArray` | `string` + `uuid` / `uri` / `byte` (base64) |
| Enum | `string` + `enum` (`integer` for a numeric `@JsonValue`) |
| Value arrays `List<String>`, `Set<Long>`, `IntArray` | `array` + element `items` (`items: {type: integer, format: int64}`) |
| Enum array `List<Level>` | `array` + `items: {type: string, enum: [...]}` |
| Value map `Map<String, Int>` | `object` + `additionalProperties` |
| `@JsonFormat(shape = NUMBER / STRING)` | The changed JSON type (`Instant` → `number`, `BigDecimal` → `string`) |
| `@JsonFormat(pattern = ...)` | `string` without `format` |

```kotlin
pathParameters("id" pathMeans "Product ID" ofType Long::class)                 // integer, int64
queryParameters("ids" queryMeans "List of IDs" ofType typeOf<List<Long>>())    // array, items integer
```

### Constraints

Bean Validation annotations (jakarta, javax, Hibernate Validator) become schema keywords in addition to the Constraints column of the table. The keyword is chosen to fit the property type.

| Constraint | String | Number | Array / collection |
|---|---|---|---|
| `@Size(min, max)` | `minLength`·`maxLength` | | `minItems`·`maxItems` |
| `@Length` (Hibernate) | `minLength`·`maxLength` | | |
| `@NotBlank` | `minLength: 1` + required | | |
| `@NotEmpty` | `minLength: 1` + required | | `minItems: 1` + required |
| `@NotNull` | required | required | required |
| `@Min`, `@Max`, `@Range` | | `minimum`·`maximum` | |
| `@DecimalMin`, `@DecimalMax` | | `minimum`·`maximum` (`exclusiveMinimum: true` when `inclusive = false`) | |
| `@Positive`, `@PositiveOrZero`, `@Negative`, `@NegativeOrZero` | | `minimum`·`maximum` relative to 0 | |
| `@Pattern` | `pattern` | | |
| `@Email`, Hibernate `@URL` | `format: email`, `format: uri` | | |

- Element constraints such as `List<@NotBlank @Size(max = 5) String>` become keywords of `items` (Kotlin type arguments, Java type annotations).
- If a nullable property or a property with a default value has `@NotNull`, `@NotBlank`, or `@NotEmpty`, it is added to `required` and `nullable` is removed.
- Child fields of an optional parent (a nullable object, a list with a default value) are optional in the REST Docs table, but in the schema they are added to `required` inside the parent object and `nullable` is removed (when `name` in `options: [{name}]` is non-null or `@NotBlank`). If the child field itself is nullable, it stays optional. Properties that exist only in sealed subtypes and element fields with `extraction.arrayElementFieldsOptional = true` are optional inside the parent too.
- `nullable` follows the nullability of the Kotlin or Java type. epages adds `nullable: true` to every optional value field and none to objects and arrays with child fields; the plugin corrects this: non-null request properties with a default value (`tags: List<String> = emptyList()`) are not added to `required` and only lose `nullable`, while nullable objects and arrays (`errors: List<FieldError>?`) and nullable elements (`items` of `List<Box?>`) get `nullable: true` (so the response examples `errors: null` and `[{...}, null]` match the schema). Hand-written fields keep the epages result as is.
- Constraints without a keyword (`@Past`, `@Future`, `@Digits`, `@AssertTrue`, custom constraints) are appended to the description as `Constraints: Past - Must be a date in the past`.
- **Validation groups**: For constraints with `groups`, the documentation alone can't tell which requests validate them, so they are not turned into keywords or `required` and are kept only in the description (`Constraints: NotNull [OnCreate] - ...`). Only constraints without a group or with `Default` become keywords.

### Sealed types and oneOf

A Kotlin sealed class or interface, or a type with Jackson `@JsonTypeInfo` and `@JsonSubTypes`, has one of several shapes. The REST Docs field table shows the union of the subtype fields (fields of only some subtypes are optional), and verification works on that union. In OpenAPI the Gradle plugin turns the property into a `oneOf` of one schema per subtype.

```kotlin
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(CardPayment::class, name = "CARD"),
    JsonSubTypes.Type(BankTransfer::class, name = "BANK"),
)
sealed interface Payment

data class CardPayment(val cardNumber: String) : Payment

data class BankTransfer(val bankCode: String, val accountNumber: String) : Payment

data class CreateOrderRequest(val productId: Long, val payment: Payment)
```

```yaml
CreateOrderRequest:
  type: object
  properties:
    payment:
      oneOf:
        - $ref: '#/components/schemas/CardPayment'
        - $ref: '#/components/schemas/BankTransfer'
      discriminator:
        propertyName: type
        mapping:
          CARD: '#/components/schemas/CardPayment'
          BANK: '#/components/schemas/BankTransfer'
```

- It applies to request and response bodies extracted from types (`requestFields<T>()`, `responseFields<T>()` and the `Of` variants), at any depth: a property, the body itself, or the elements of an array (`payments[]`).
- Each subtype becomes a component schema named after the subtype. If that name is already taken by a different schema, it becomes `<base type><subtype>` (`PaymentCardPayment`).
- `discriminator` is added only for `@JsonTypeInfo(use = NAME | SIMPLE_NAME | CLASS)` with `include = PROPERTY` (the default) or `EXISTING_PROPERTY`; `mapping` comes from the `@JsonSubTypes` names (the class name with `use = CLASS`). Other `use` values (`MINIMAL_CLASS`, `DEDUCTION`) and a sealed type without `@JsonTypeInfo` get `oneOf` without a discriminator.
- `include = WRAPPER_OBJECT` and `WRAPPER_ARRAY` can't be written as one of the subtype schemas, so they get no `oneOf`; the flattened schema stays.
- It also works below a prefix path and inside the common response wrapper. Each extracted list remembers its type and the path it was extracted at, so the plugin gets the full path: `requestFieldsOf<T>("data")` → `data.payment`, `wrappedResponseFields<T>()` → `data.payment` (or `data` when the wrapper's data is itself the sealed type, `data[]` for a list of them), and a property inside an array → `data.items[].payment`. A body put together from several lists (`requestFieldsOf<A>("a") + requestFieldsOf<B>("b")`) is handled list by list. The plugin follows `$ref`s and arrays down to the property, so a shared component (such as the wrapper's data schema) changes everywhere it's used.

```kotlin
.andDocument {
    requestFields<CreateOrderRequest>()
    wrappedResponseFields<OrderResponse>()   // { "code": 200, "data": { "payment": { "type": "CARD", ... } } }
}
```

```yaml
OrderResponse:
  type: object
  properties:
    code: {type: integer}
    data:
      type: object
      properties:
        payment:
          oneOf:
            - $ref: '#/components/schemas/CardPayment'
            - $ref: '#/components/schemas/BankTransfer'
          discriminator:
            propertyName: type
```

- What still gets no `oneOf`: hand-written fields (`"payment" type OBJECT`, there is no subtype information to use), and properties you removed from the list (`except("payment")`). A field list whose type comes only from a generic wrapper written by hand (`responseFieldsOf<ApiResponse<*>>()` + hand-written data fields) has no subtype to find either.

### Defaults, examples, and constraint notes

| Modifier | OpenAPI |
|---|---|
| `withDefaultValue "20"` | Schema `default`. If the type is `integer`, `number`, or `boolean`, the value is converted to it (`default: 20`); for arrays, it is split on commas |
| `example "10"` | Schema `example` for fields; parameter or header `example` for parameters and headers (the default value in Swagger UI's input box) |
| `constraint "2-10 characters"` | `Constraints: 2-10 characters` after the description. On an extracted field, only the table wording changes, and the keywords generated from annotations stay |

**Kotlin default values.** A constructor parameter with a default value (`val size: Int = 20`) gives a default without `withDefaultValue`:

```kotlin
data class ProductSearchRequest(
    val keyword: String? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sort: SortOrder = SortOrder.LATEST,
)

queryParameters(parametersOf<ProductSearchRequest>())   // page Default: 0, size Default: 20, sort Default: LATEST
```

- Parameters extracted with `parametersOf<T>()` (path, query, form) get it as if you had written `withDefaultValue`, so it shows in the description cell of the table and as `default` in OpenAPI. `queryParameters<T>()` and the other shortcuts behave the same. A `withDefaultValue` you write yourself wins.
- Fields extracted with `requestFields<T>()` get it in the description cell of the field table (`(Default: 20)`) and as `default` on the OpenAPI schema property (through the Gradle plugin). Defaults are read only for request extraction; `responseFields<T>()` doesn't use them, because a response always carries the value.
- Only strings, numbers, booleans and enums are used. `null`, empty strings, collections, maps and objects are skipped.
- Defaults of masked fields (`@Masked` or an annotation in `masking.annotations`) are never published.
- The library reads the values by creating an instance with the defaults twice (only once per class) and publishes a default only if both instances have the same value. Defaults that change on every evaluation (`UUID.randomUUID().toString()`, the current time) are therefore left out, instead of changing the snippets on every run. A default read from an environment variable or configuration (`System.getenv("CLIENT_ID") ?: "local"`) returns the same value both times, so it can't be told apart from a constant and **is published** with the value present when the tests ran. Don't read secrets into constructor defaults of request classes, or mark such a field `@Masked`.
- Placeholders and failures: parameters without a default get a placeholder: `null`, `""`, `0`, `false`, the first enum constant, an empty collection, or, for a Kotlin class, a placeholder object built the same way (a sealed type uses its first subtype that can be built). If that can't be done (an interface or Java class parameter, an `init` block that throws), the defaults of that class are skipped without an error, and nothing else changes. Java classes have no default values to read.

A parameter of unknown type (hand-written without `ofType`, and whose type can't be found from the handler either) is a `string`, so its default is a string too (`default: "20"`). Request and response headers (`headerMeans`) also accept `example`, `withDefaultValue`, and `constraint`. Without `example`, the OpenAPI example of a request header is the actual request header value. However, `Authorization`, `Proxy-Authorization`, `Cookie`, and `Set-Cookie` get no example (so that masked values and the fake token for the security scheme don't show).

### Multipart and cookies

This section covers multipart requests that upload files; for a request whose whole body is a file, see [File uploads in the request body](#file-uploads-in-the-request-body), and for responses that return files, [File downloads](#file-downloads). The bytes of file parts appear in snippets only as `<binary: N bytes>`.

epages doesn't put multipart parts and cookies into the spec. The library writes them to `openapi-extras.json` next to the OpenAPI fragment, and the Gradle plugin adds them.

| Documentation | OpenAPI |
|---|---|
| `requestParts("file" partMeans "...")` (a part with a file name in the actual request) | `file: {type: string, format: binary}` in the `multipart/form-data` request body |
| `requestPartFields<ImageMeta>("meta")` | `meta` is an object schema built from the fields; `encoding.meta.contentType` is the actual part's Content-Type |
| Other parts | `type: string` |
| `requestCookies("SESSION" cookieMeans "...")` | `in: cookie` parameter (`required: false` if `isOptional`) |
| `responseCookies("SESSION" cookieMeans "...")` | A `` Cookies: `SESSION`: ... `` list in the description of the response `Set-Cookie` header (OpenAPI 3.0 has no place for response cookies) |

### Other refinements

| Item | Result |
|---|---|
| operationId | Decided from the candidates the library records ([operationId](#operationid)). Details below the table |
| Top-level `tags` | Collects the operations' tags in order. If the test class has `@AsciiDescription`, it becomes that tag's `description` |
| JSON examples | JSON values rather than strings, so schema validation tools can check the examples |
| Security requirements | In documents with security schemes, operations documented without an authentication header get `security: []` (no authentication). Documents without security schemes get a single top-level `security: []` (Redocly `security-defined`) |
| API version (Spring Framework 7) | `x-api-version` and, depending on the style, a version header or query parameter ([API versioning](openapi.md#api-versioning)) |

How the operationId is decided:

- Explicit ids are used as is, and rule-based ids get a controller name or number only when they collide.
- Test-name ids (the `test-name` rule, or when the handler is unknown) have `/` replaced by `.` and characters other than letters (including Korean and other non-ASCII letters), digits, `.`, `_`, and `-` replaced by `-` (`product-api-docs-test.get-product`, Kotest `user-spec.사용자-조회`), with `-2`, `-3` on collision.
- An operation merged from several tests uses the candidate of the success response test. For test-name ids, it uses the part of the test names shared up to a `-` boundary (`get-product` + `get-product-not-found` → `get-product`), or the success response test's id if there is none (ids are not concatenated as in epages 0.19).

### Without the Gradle plugin

With only the epages plugin, none of the enhancements above apply. Only what epages reads directly comes through: parameter types `integer`, `number`, and `boolean` and their defaults (when the type is known), enum `enum`, and element types of value arrays (`string`, `number`, `boolean`). You can check the generated spec with `node scripts/validate-openapi.mjs` ([Development](development.md)).

## MockMvc helpers

| Function | Description |
|---|---|
| `restDocsMockMvc(context, provider)` | MockMvc with the REST Docs setup and a UTF-8 filter applied. It uses the app's Jackson mapper bean as the default mapper for the current thread. Notes on preprocessors below the table |
| `jsonContent(body)` | Serializes an object to JSON and sets `Content-Type: application/json`. `String` and `CharSequence` (`StringBuilder` and so on) are treated as bodies that are already JSON and sent as is (`jsonContent("""{"name":"Jane Doe"}""")`), and `ByteArray` is sent as a UTF-8 string (the same rule as WebSocket message payloads). Works with both `post(...)` and `multipart(...)` builders |
| `ResultActions.andPrint()` | Prints the request and response to the console. Same as `andDo(MockMvcResultHandlers.print())`. To print every request, use `print-requests: true` |
| `ResultActions.andLog()` | Logs the request and response (DEBUG). Same as `andDo(MockMvcResultHandlers.log())` |
| `ResultActions.andStatus(HttpStatus.NOT_FOUND)`, `andStatus(404)` | Verifies the response status code. Same as `andExpect(status().isNotFound)`, without needing the `MockMvcResultMatchers.status` import. It returns the same `ResultActions`, so you can chain `andDocument { }`. More below the table |
| `ResultActions.andExpectJsonPath("$.code", 404)` | Verifies a value in the response JSON. Same as `andExpect(jsonPath("$.code").value(404))` |
| `ResultActions.andReturnContent()` | Returns the response body as a UTF-8 string. `contentAsString` garbles non-ASCII text such as Korean when there is no charset |
| `bearerToken(token)` | An `Authorization: Bearer <token>` header. Hidden in the documentation when masking is on |
| `MockMvc.performStream(request, eventCount = 2) { ... }` | Receives the first N events from a stream that never ends (SSE) and pins them. The package is `io.github.kardx.sse` ([Streams that never end](messaging.md#streams-that-never-end)) |
| `MockMvc.performAsync(request)` | Receives asynchronous responses such as SSE and `DeferredResult` to the end |
| `ResultActions.andReturnAsObject<T>()` | Deserializes the response body into the generic type as is (UTF-8 and the app mapper, as with `andReturnContent`). If `T` is `String`, you get the raw text; if `ByteArray`, the response bytes as is. An empty or whitespace-only body throws `IllegalStateException`, so use `andReturnEntity` for such responses |
| `ResultActions.andReturnEntity<T>()` | Returns the status code, all response headers, and the body as a Spring `ResponseEntity<T>`. Body handling is the same as `andReturnAsObject`; an empty or whitespace-only body (204 and so on) gives a null `body`, `T` of `String` gives the raw text, and `ByteArray` gives the response bytes as is. `response.statusCode`, `response.headers.location`, `response.body` |
| `ResultActionsDsl.andDocument { }`, `MvcTestResult.andDocument { }` | Documents results from the MockMvc Kotlin DSL and `MockMvcTester` ([andDocument](#anddocument)) |

Notes:

- **`restDocsMockMvc`**: pretty-printing of request and response bodies and your own preprocessors ([Processing requests and responses](customizing-snippets.md#request-and-response-preprocessing)) are handled by `andDocument`, so they are not registered as MockMvc default preprocessors. If you call REST Docs' `document()` directly with this MockMvc, bodies are not pretty-printed, so also pass `preprocessRequest(prettyPrint())` and `preprocessResponse(prettyPrint())`.
- **`andStatus`**: accepts an `HttpStatusCode` (including `HttpStatus` constants) or a number, and on mismatch fails with the same message as Spring's `status()` (`Response status expected:<404> but was:<200>`). With WebTestClient, keep using `expectStatus()`.

Both Jackson 2 and 3 are supported, and the library handles the binary differences between Spring 6 and 7.
