**English** | [한국어](ko/configuration.md)

# Configuration

[Document list](README.md)

<!-- toc -->
**Contents**

- [How to change settings](#how-to-change-settings)
  - [Parallel execution](#parallel-execution)
- [List of settings](#list-of-settings)
- [yml settings](#yml-settings)
- [Documentation language](#documentation-language)
- [Masking sensitive data](#masking-sensitive-data)
- [Experimental APIs](#experimental-apis)
<!-- /toc -->

We recommend setting project-wide values in `application.yml` and per-test values in code. Paths are set in the [Gradle plugin](build.md#gradle-plugin).

## How to change settings

In code, you use `RestDocsConfiguration`. For yml, see [yml settings](#yml-settings).

| Function | Scope |
|---|---|
| `configure { ... }` | Changes the defaults. Call it in `@BeforeEach` of the test base class. How long it lasts depends on where you call it (table below) |
| `withSettings({ ... }) { ... }` | Only inside the second block, and only on the current thread. Safe in parallel tests |
| `current` | The values currently in effect |
| `reset()` | Removes every value added with `configure`, no matter who added it or when (including those from `@BeforeAll` and other tests). Removed values don't come back when tests end. Values read from yml (properties) remain. With `AutoRestDocumentationExtension`, you usually don't need to call it |

While a test runs (`@BeforeEach` → test method → `@AfterEach`), `AutoRestDocumentationExtension` remembers each `configure` made on the test thread as a block added by that test, and removes **only those blocks** when the test ends. So settings don't pile up from test to test even without `@AfterEach fun reset() = RestDocsConfiguration.reset()`, and it doesn't remove or restore settings of other tests (including tests running in parallel). Kotest's `RestDocsExtension` works the same way. Settings made in a leaf test and its `beforeEach`/`afterEach` are removed when the test ends, and settings made in the spec body or `beforeSpec` remain ([Kotest](kotest.md#same-behavior-as-the-junit-extension)).

| Where `configure` is called | How long it lasts |
|---|---|
| Test method, `@BeforeEach`, `@AfterEach` | That test only. Gone when the test ends (even if it fails) |
| Threads or thread pools the test created, server or async threads | Belongs to no test, so it stays (call it on the test thread) |
| `@BeforeAll`, `companion object`, outside the test class | Stays. It also applies to the next test classes, so to remove it, call `reset()` in `@AfterAll` |
| Test class constructor, `init` block, property initializers | Don't. JUnit creates a new instance for every test, and these run before the extension starts remembering, so they aren't reverted and pile up with every test. Move them to `@BeforeEach` |

The class document (`<class-name>.adoc`) is built with the settings in effect when the last test runs (after `@BeforeEach`, before the test body), so document settings such as `document.commonIncludeFiles` set in `@BeforeEach` are used in the document even after they are reverted at the end of the test. Tests that use REST Docs' `RestDocumentationExtension` directly don't revert settings, so call `reset()` in `@AfterEach`.

### Parallel execution

Even when tests run in parallel (JUnit `@Execution(CONCURRENT)` or parallel classes, parallel Kotest specs), each test removes only the `configure` blocks it added, so tests don't remove or restore each other's settings. The following is not guaranteed.

- The values built with `configure` are a single JVM-wide value. While two tests run at the same time, both see a value with both tests' blocks applied, so if they **document at the same time** with different `configure` values, the other test's settings can leak into snippets, OpenAPI and documents.
- A class document is built with the settings seen by the last test that ran in that class. If another test's blocks were present at that time, those values are included too, and if you run methods of the same class in parallel, which method is last is undefined.
- yml (`kardx.*`) values are also a single JVM-wide value, so if you run classes from contexts with different values in parallel, the values of the context read last are used.

Give per-test settings with `withSettings` (applies only to the current thread), or run such tests one after another with `@Execution(SAME_THREAD)` or `@ResourceLock`.

```kotlin
// Only in the one test that documents an empty array response
RestDocsConfiguration.withSettings({ extraction.arrayElementFieldsOptional = true }) {
    mockMvc
        .perform(get("/api/products?keyword=none"))
        .andDocument { ... }
}
```

## List of settings

Most settings are read when they are used (`andDocument` when documenting, `responseFieldsOf` when extracting, and so on), so changing them after creating the client still takes effect. Only settings marked **(at creation)** are read once when `restDocsMockMvc(...)` is called, so they must be set before that to apply to that MockMvc. `restDocsMockMvc` and `restDocsWebTestClient` read `kardx.*` from yml through the context.

Settings are grouped by what they affect. In `configure { }` you can open a group as a block or write the path; `language` stays at the top.

```kotlin
RestDocsConfiguration.configure {
    language = DocsLanguage.KO
    extraction { maxDepth = 5 }
    masking { maskSensitive() }
    openApi.enabled = false
    document { commonIncludeFiles = listOf("_landing.adoc") }
}
```

| Group | What it covers |
|---|---|
| `extraction` | Automatic field extraction: response wrapper, mapper, names, depth, kotlinx.serialization |
| `descriptions` | Where field descriptions come from: the description rule, annotations, KDoc/Javadoc |
| `masking` | Masking header, cookie and parameter values, and marking masked fields |
| `http` | Request and response processing, console output, MockMvc creation |
| `openApi` | OpenAPI resources: security schemes, schema names, operationId |
| `asyncApi` | AsyncAPI metadata |
| `output` | Snippet and document directories, WireMock stubs |
| `document` | Generated AsciiDoc documents: includes, index link, snippets and their order |

Settings given as callbacks are fun interfaces (`DescriptionResolver`, `ValueMasker`, `ObjectMapperProvider`, `PropertyNameStrategy`, `SchemaNameResolver`, `OperationIdResolver`). To assign a lambda to such a property, use the SAM constructor: `descriptions.resolver = DescriptionResolver { ... }`, `extraction.objectMapper = ObjectMapperProvider { mapper }`.

| Setting | Default | Description |
|---|---|---|
| `extraction.responseWrapper` | `null` | [Common response wrapper](http-api.md#common-response-wrapper) |
| `extraction.objectMapper` | Order below | The Jackson mapper used for serialization (`ObjectMapperProvider { mapper }`). Chosen in this order: the app mapper bean found by `restDocsMockMvc` or `restDocsWebTestClient`, Jackson 3 on the classpath, Jackson 2 |
| `descriptions.resolver` | `DescriptionResolver.DEFAULT`: `@Description`, then `descriptions.annotations`, then KDoc/Javadoc | The rule that decides field descriptions. Combine your rule with the default steps so they keep working ([Replacing the description source](http-api.md#replacing-the-description-source)) |
| `descriptions.annotations` | None | Annotations to read descriptions from after `@Description` ([Reading descriptions from other annotations](http-api.md#reading-descriptions-from-other-annotations)) |
| `masking.annotations` | None | Annotations read after `@Masked` as a "value is sent masked" marker. Only marks the documentation; values aren't changed ([Marking masked fields](http-api.md#marking-masked-fields)). Different from `masking.headers`, which masks header values |
| `extraction.propertyNameStrategy` | `null` (follows the mapper settings) | Field name conversion. Example: `PropertyNameStrategy { PropertyNamingStrategies.SNAKE_CASE.nameForField(null, null, it.name) }` (works for both Jackson 2 and 3. In Jackson 3, `translate` is protected, so `::translate` doesn't compile) |
| `extraction.enumDescriptionProperty` | `description` | The property to read enum constant descriptions from (field → getter → method with the same name). Used for the value list after field descriptions and as the default `Description` of `@EnumCode` tables |
| `descriptions.sourceDocs` | `true` | Experimental (`@ExperimentalRestDocsApi`). Test-side kill switch for KDoc/Javadoc descriptions: `false` ignores the file the Gradle plugin passed. It can't turn the feature on; that is `kardx { sourceDocs { enabled = true } }` in the Gradle plugin (experimental, off by default), and without it there is no file and this does nothing ([Descriptions from KDoc/Javadoc](http-api.md#descriptions-from-kdocjavadoc)) |
| `extraction.maxDepth` | `10` | Maximum depth of automatic extraction. Deeper levels become a subsection |
| `extraction.arrayElementFieldsOptional` | `false` | Marks sub-fields of array elements as optional. Turn it on when documenting empty arrays. For a single array, use `responseFieldsOf<T>().optionalUnder("items[]")` |
| `extraction.kotlinxSerialization` | `true` | Extracts kotlinx.serialization `@Serializable` classes from their serial descriptor (`@SerialName`, defaults optional), as Spring serializes them when kotlinx-serialization-json is on the classpath. Set `false` if your app serializes them with Jackson ([kotlinx.serialization](http-api.md#kotlinxserialization)) |
| `masking.headers` | None (nothing masked) | Headers whose values are masked. See [Masking sensitive data](#masking-sensitive-data) |
| `masking.replacement` | `****` | The string used in place of a masked value |
| `masking.masker` | `null` | A `ValueMasker` that changes how values are masked. It receives a `MaskTarget`: `location` (`HEADER`, `COOKIE`, `PARAMETER`), `name`, `value`, and `cookieName` for cookies |
| `masking.queryParameters` | None (nothing masked) | Query and form parameters whose values are masked (`?access_token=****`, `password=****` in an `application/x-www-form-urlencoded` body). `masking.maskSensitive()` adds `MaskingSettings.SENSITIVE_QUERY_PARAMETERS`. See [Masking sensitive data](#masking-sensitive-data) |
| `http.requestPreprocessors` | None | Additional Spring REST Docs `OperationPreprocessor`s applied to requests. Applied after the library's own processing (URI, masking, pretty print). Code only ([Request and response preprocessing](customizing-snippets.md#request-and-response-preprocessing)) |
| `http.responsePreprocessors` | None | Additional preprocessors applied to responses. Applied after the library's own processing. Code only |
| `http.removedResponseHeaders` | `HttpSettings.VOLATILE_RESPONSE_HEADERS` (`Date`) | Response headers left out of the documents because their values change on every run, so snippets and WireMock mappings stay identical between runs. Details below the table |
| `http.printRequests` | `false` | Prints requests and responses to the console. Read on every request, so you can also turn it on after creating the client with `configure { http.printRequests = true }` or `withSettings` |
| `http.applySpringSecurity` | `false` | **(at creation)** When on, `restDocsMockMvc` applies the context's Spring Security filter chain ([Spring Security](http-api.md#spring-security)). Off by default; details below the table |
| `openApi.enabled` | `true` | Whether to create OpenAPI resources when epages is present |
| `openApi.securitySchemes` | `true` | Creates OpenAPI security schemes (Swagger Authorize) from the request's `Authorization`. Actual tokens are not put into the spec |
| `openApi.globalHeaders` | None | Headers entered once in Swagger Authorize and attached to every request. They become `apiKey` security schemes instead of header parameters ([Headers and authentication](http-api.md#headers-and-authentication)). Applied by the Gradle plugin |
| `openApi.includeStreams` | `false` | Whether to include streams that never end (`performStream`) in OpenAPI. Swagger UI keeps loading on such responses |
| `openApi.schemaNames` | `true` | Automatically names OpenAPI schemas after the extracted types ([Schema names](http-api.md#schema-names)) |
| `openApi.schemaNamesWithoutWrapper` | `false` | Drops the wrapper name from automatic names of responses wrapped in the common response wrapper (`ApiResponseTodoResponse` → `TodoResponse`) |
| `openApi.schemaNameResolver` | `null` | Type → schema name rule, a `SchemaNameResolver` (code only) |
| `openApi.operationIdRule` | `HANDLER_METHOD` | OpenAPI operationId rule: `HANDLER_METHOD` (controller method name `searchUsers`), `CONTROLLER_AND_METHOD` (`userSearchUsers`), `PATH` (`getApiV1UsersById`), `TEST_NAME` (test class and method name, the previous behavior) ([operationId](http-api.md#operationid)) |
| `openApi.operationIdResolver` | `null` | An `OperationIdResolver` that builds the operationId yourself from an `OperationIdInput`. If it returns `null`, `openApi.operationIdRule` is used (code only) |
| `openApi.errorSchema` | `null` | The shared OpenAPI schema name for error responses (4xx, 5xx). Can also be set with `openApi.errorSchema(ErrorResponse::class)` ([One class for error responses](http-api.md#one-class-for-error-responses)) |
| `asyncApi.enabled` | `true` | Whether to record AsyncAPI metadata when documenting messages and SSE |
| `output.wireMockStubs` | `false` | Writes WireMock mapping JSON to `<snippet directory>/wiremock/mappings` for each `andDocument`. Turned on by the Gradle plugin's `wireMock { enabled = true }` ([WireMock stub](outputs.md#wiremock-stub)) |
| `output.snippetsDirectory` | `build/generated-snippets` (`target/...` for Maven) | Snippet directory. With the Gradle plugin, the plugin's value takes precedence, and the plugin empties the directory before tests, so it refuses project and source directories and, outside `build/`, directories with files it didn't write ([Directories the plugin empties](build.md#directories-the-plugin-empties)) |
| `output.asciidocDirectory` | `build/generated-asciidoc` (`target/...` for Maven) | Generated document directory. With the Gradle plugin, the plugin's value takes precedence and the same deletion rules as `output.snippetsDirectory` apply |
| `document.includePrefix` | `{snippets}` | Path prefix for snippet includes in documents |
| `document.commonIncludeFiles` | None | Files to include at the top of every document (relative to the document directory) |
| `document.indexLink` | `index.html` | Target of the "back to main" link placed at the start and end of generated documents and in the title slot of the left table of contents. `null` adds no link ([AsciiDoc](asciidoc.md)) |
| `document.indexLinkText` | `null` (language default: `Back to index`, `메인으로 돌아가기` for `ko`) | Text of that link |
| `language` | `DocsLanguage.EN` | Language of the texts the library puts into generated documents (`EN`, `KO`). [Documentation language](#documentation-language) |
| `document.snippets` | `SnippetFile.defaults()` | Snippet types to include in generated documents ([Choosing snippets for the document](customizing-snippets.md#choosing-snippets-for-the-document)) |
| `document.sectionOrder` | `METHOD_NAME` | Section order of generated documents when there is no JUnit `@TestMethodOrder`. `DISPLAY_NAME` orders by `@DisplayName` ([Section order](asciidoc.md#section-order)) |

Details for two of the rows above:

- **`http.removedResponseHeaders`** ([Request and response preprocessing](customizing-snippets.md#request-and-response-preprocessing))
  - Case-insensitive. Headers you document with `responseHeaders(...)` are always kept.
  - While it is not empty, `Expires` is also dropped from `Set-Cookie` values that have `Max-Age` (it is computed from the current time; `Max-Age` takes precedence anyway).
  - `emptySet()` keeps every header as received; `http.removedResponseHeaders += "X-Request-Id"` removes more.
  - Applied before `http.responsePreprocessors`.
- **`http.applySpringSecurity`**
  - It is not applied by default because some projects authenticate in other ways, such as interceptors.
  - If a filter chain bean exists and this isn't set, a warning is logged once per JVM; setting it to `false` explicitly silences the warning.
  - Not used for WebTestClient (WebFlux applies the `WebFilter` beans).

Maven detection is the same as in Spring REST Docs: if `pom.xml` exists in the working directory, `target` is used.

## yml settings

You can set defaults with `kardx.*` in `application.yml` (or a profile yml, `-D` system properties, environment variables). In Spring tests such as `@SpringBootTest`, the library reads the settings from the test context.

The library jar contains Spring configuration metadata (`META-INF/spring-configuration-metadata.json`), so IntelliJ autocompletes the keys and shows their descriptions. In the repository sources, `additional-spring-configuration-metadata.json` is the source, and the build renames it to the standard name in the jar. If you see "Cannot resolve configuration property", sync Gradle again (so that the new version is downloaded).

```yaml
kardx:
  common-include-files: _landing.adoc
  mask-sensitive-headers: true
  response-wrapper:
    class: com.example.ApiResponse
    data-path: data
```

Keys are grouped by feature. OpenAPI settings are under `open-api.*`, AsyncAPI under `async-api.*`, snippet locations under `snippets.*` and the response wrapper under `response-wrapper.*` (the same shape as `startup-log.*` in the boot module); the rest are keys directly under `kardx.`. In yml, nesting (`enabled: false` under `open-api:`) and dotted keys (`open-api.enabled: false`) are equivalent. The yml keys did not change when the code settings were grouped; the second column gives the code path.

| Property | Equivalent setting |
|---|---|
| `snippets.directory`, `asciidoc-directory` | `output.snippetsDirectory`, `output.asciidocDirectory` |
| `snippets.include-prefix` | `document.includePrefix` |
| `common-include-files` | `document.commonIncludeFiles` (comma-separated list or YAML list) |
| `index-link`, `index-link-text` | `document.indexLink`, `document.indexLinkText`. `index-link: none` adds no link |
| `document-snippets` | `document.snippets`. Snippet file names (`api-summary, http-request, response-fields`) or constant names (`request-info`) of built-in snippets |
| `document-section-order` | `document.sectionOrder` (`method-name`, `display-name`) |
| `language` | `language` (`en`, `ko`, case-insensitive) |
| `masked-headers`, `masked-header-value` | `masking.headers`, `masking.replacement` |
| `masked-query-parameters` | `masking.queryParameters` (comma-separated list or YAML list) |
| `mask-sensitive-headers` | `masking.maskSensitive()` (sensitive headers and `SENSITIVE_QUERY_PARAMETERS`) |
| `removed-response-headers` | `http.removedResponseHeaders` (comma-separated list or YAML list). The list replaces the default (like `=` in code), so include `Date` to keep removing it: `Date, X-Request-Id`. `none` keeps every header |
| `print-requests` | `http.printRequests` |
| `apply-spring-security` | `http.applySpringSecurity` |
| `wire-mock-stubs` | `output.wireMockStubs` |
| `array-element-fields-optional`, `max-extraction-depth`, `enum-description-property` | `extraction.arrayElementFieldsOptional`, `extraction.maxDepth`, `extraction.enumDescriptionProperty` |
| `kotlinx-serialization` | `extraction.kotlinxSerialization` (default `true`) |
| `description-annotations` | `descriptions.annotations` (comma-separated list or YAML list). Aliases (`swagger`, `jackson`), `alias#attribute` (`swagger#title`), `FQCN`, `FQCN#attribute`. For details, see [Reading descriptions from other annotations](http-api.md#reading-descriptions-from-other-annotations) |
| `source-docs.enabled` | `descriptions.sourceDocs`: the test-side kill switch for KDoc/Javadoc descriptions (default `true`). `false` makes the tests ignore the file even when the Gradle plugin passed it. The feature itself is turned on in the Gradle plugin with `kardx { sourceDocs { enabled = true } }` (experimental, off by default) ([Descriptions from KDoc/Javadoc](http-api.md#descriptions-from-kdocjavadoc)) |
| `masked-annotations` | `masking.annotations` (comma-separated list or YAML list). `FQCN` (no rule description), `FQCN#attribute` (a String value or enum name used as the rule description). See [Marking masked fields](http-api.md#marking-masked-fields) |
| `response-wrapper.class`, `response-wrapper.data-path` | `extraction.responseWrapper` |
| `open-api.enabled` | `openApi.enabled` |
| `open-api.schema-names` | `openApi.schemaNames` |
| `open-api.schema-names-without-wrapper` | `openApi.schemaNamesWithoutWrapper` |
| `open-api.operation-id-rule` | `openApi.operationIdRule` (`handler-method`, `controller-and-method`, `path`, `test-name`) |
| `open-api.include-streams` | `openApi.includeStreams` |
| `open-api.security-schemes` | `openApi.securitySchemes` |
| `open-api.global-headers` | `openApi.globalHeaders` (comma-separated list or YAML list) |
| `open-api.error-schema` | `openApi.errorSchema`. If you give a class name (FQCN), the class name becomes the schema name |
| `async-api.enabled` | `asyncApi.enabled` |

Environment variable names replace both dots and `-` with `_` (`KARDX_OPEN_API_ENABLED`, `KARDX_SNIPPETS_DIRECTORY`).

- The precedence is library defaults, yml, `configure { }`, `withSettings`. Later ones win.
- Everything you can set in yml can also be set in code. For example, the two below are equivalent.

```yaml
kardx:
  response-wrapper:
    class: com.example.ApiResponse
    data-path: data
  open-api:
    error-schema: com.example.ErrorResponse
  description-annotations: swagger, com.example.ApiField#text
```

```kotlin
RestDocsConfiguration.configure {
    extraction.responseWrapper<ApiResponse<*>>(dataPath = "data")   // or extraction.responseWrapper(ApiResponse::class, "data")
    openApi.errorSchema<ErrorResponse>()                             // or openApi.errorSchema(ErrorResponse::class)
    descriptions {
        annotation("swagger")                                        // or from<Schema> { it.description }
        annotation(ApiField::class, "text")                          // or annotation("com.example.ApiField#text")
    }
}
```
- `reset()` removes only values changed with `configure` and keeps yml values. The automatic revert of `AutoRestDocumentationExtension` doesn't touch yml values either.
- yml values are read from the test's Spring context and applied to **a single JVM-wide default**. If you run test classes whose contexts have different `kardx.*` values (profiles, `@TestPropertySource`, `properties = [...]`) **in parallel**, another class's documents may be built with the values of the context read last. Run such classes one after another (`@Execution(ExecutionMode.SAME_THREAD)`, `@ResourceLock`), or give per-class values with `withSettings` or `configure` instead of yml. The default JUnit run (no parallelism) rereads each class's own context, so it is not affected.
- `AutoRestDocumentationExtension` uses the settings of the last method that ran when it builds a class document. If you run methods of the same class in parallel (`@Execution(CONCURRENT)`), per-method document settings (`document.commonIncludeFiles` and so on) aren't guaranteed, and the setting reverts at the end of each test overlap. Give per-method values with `withSettings`. Parallel execution of tests inside a Kotest spec is not supported for the same reason.
- `configure { }` blocks are remembered and reapplied whenever yml is reread or a block is added. Only change setting values inside the block; don't put side effects such as incrementing counters or writing files there.
- Values assigned to list and set settings (`descriptions.annotations`, `masking.headers`, `document.commonIncludeFiles`, and so on) are copied. Changing the `MutableList` you passed later has no effect.
- Settings given as fun interfaces (`descriptions.resolver`, `extraction.objectMapper`, `extraction.propertyNameStrategy`, `masking.masker`, `openApi.schemaNameResolver`, `openApi.operationIdResolver`) can only be changed in code.
- App runtime settings (the startup log's `docs-path`, `startup-log.*`) go in the production `application.yml`: [Startup log](build.md#startup-log). Document paths and file names default to the actual values recorded by the Gradle plugin, so you usually don't set them.
- **Unknown keys produce a warning.** When a Spring test reads the context settings and finds a key that starts with `kardx.` but is not in the table above (or the startup log's `docs-path` and `startup-log.*`), it logs a warning once with the closest key, because a misspelled key is silently ignored.
  - Relaxed binding forms (`maskSensitiveHeaders`, `mask_sensitive_headers`, the environment variable `KARDX_MASK_SENSITIVE_HEADERS`, list indexes `common-include-files[0]`) are treated as the same key.
  - Only property sources whose names can all be enumerated (yml, properties, system properties, environment variables) are checked.
  - Startup log keys (`startup-log.*`) are read by the boot module, so typos in their sub-keys are not checked.
  - Flat names from before keys were grouped by feature (`open-api-enabled`, `open-api-schema-names`, `async-api-enabled`, `snippets-directory`, `snippets-include-prefix`, and so on) are not read, and instead of the closest key, the warning tells you the exact new key (`... is ignored. It was renamed to 'kardx.open-api.enabled'.`). Environment variable names have not changed.

```
WARN ... io.github.kardx.config : kardx[KDX-001]: unknown property 'kardx.mask-sensitive-header' in [Config resource 'class path resource [application-docs.yml]' via location 'optional:classpath:/'] is ignored. Did you mean 'kardx.mask-sensitive-headers'? Supported properties are listed in docs/configuration.md.
```
- Functions that add to lists (`descriptions.annotation`, `descriptions.from`) append after the yml list, and when called in `withSettings`, after that. To replace the whole list, assign it, as in `descriptions.annotations = listOf(...)` ([Configuring in code](http-api.md#configuring-in-code)).
- Invalid values fail with an error that names the property (`kardx.print-requests: expected true or false`, `kardx.description-annotations: class 'com.example.ApiField' not found`).
- Outside Spring tests, system properties (`-Dkardx.masked-headers=Authorization`) and environment variables (`KARDX_MASKED_HEADERS`) are read.

## Documentation language

The texts the library puts into generated documents (list headings, link texts, description prefixes and so on) are English by default, and one setting switches them to Korean. Your own descriptions, `@DisplayName`s and templates are not changed.

**With the Gradle plugin, set it in the plugin.**

```kotlin
// build.gradle.kts
kardx {
    language = "ko"
}
```

The plugin passes this value to the test JVM and the document tasks (`apiDocsIndex`, `apiDocsAsyncApi`) as the system property `kardx.language`, records it in the app resources (`docs-locations.properties`) so that the [startup log](build.md#startup-log) uses the same language, and uses it for the texts the plugin writes itself. If you set it only in yml, the texts written by tests change but the plugin's texts (constraint and cookie notes in OpenAPI descriptions, the common error responses table, the OpenAPI viewer's offline note, the aggregate page) stay English, and the languages are mixed. An unsupported value fails the build (`kardx.language must be en or ko, got 'fr'`).

When the tests run with the plugin and the documentation language (from `kardx.language` or `RestDocsConfiguration.configure`) differs from the plugin's, the library logs one WARN per JVM on the `io.github.kardx.config` logger: `kardx[KDX-005]: the documentation language is 'ko' (kardx.language or RestDocsConfiguration.configure) but the Gradle plugin uses 'en', so plugin-made pages and test-made snippets will mix languages. Set kardx { language = "ko" } in the build file.` Set the language in `kardx { }` as the message says. Without the plugin there is nothing to compare, so there is no warning.

Without the plugin, set it as follows. The precedence is the same as for other settings: system property, environment variable or yml < `configure { }` < `withSettings`.

```yaml
kardx:
  language: ko            # en (default), ko. Case-insensitive. System property -Dkardx.language, env KARDX_LANGUAGE
```

```kotlin
RestDocsConfiguration.configure { language = DocsLanguage.KO }   // io.github.kardx.config.DocsLanguage
```

| Follows the language | `en` (default) | `ko` |
|---|---|---|
| List heading and document link of the built-in index template | `API list`, `Open document` | `API 목록`, `문서 열기` |
| Index link in generated documents ([AsciiDoc](asciidoc.md)) | `← Back to index` | `← 메인으로 돌아가기` |
| Prefixes in OpenAPI and AsyncAPI schema descriptions | `Constraints: `, `Items: ` | `제약: `, `원소: ` |
| Field descriptions added automatically: Spring Data `Page`, `Slice`, `Sort`, `Pageable`, `ProblemDetail` | `Items on this page`, `HTTP status code` … | `이 페이지의 항목`, `HTTP 상태 코드` … |
| Sealed type discriminator descriptions | `Type discriminator`, `Value when the type is …` | `타입 구분자`, `… 타입일 때의 값` |
| AsyncAPI channel notes (connect and subscribe headers, optional events) | `Headers sent when connecting (…)` … | `연결할 때 헤더를 보낸다 (…)` … |
| AsyncAPI viewer UI (including `<html lang>`) | `Connection`, `Not connected` … | `연결 설정`, `연결 안 됨` … |
| Common error responses table (plugin `openApiCommonResponses`) | `Common error responses`, `Status code` | `공통 오류 응답`, `상태 코드` |
| Cookie list and optional marker in OpenAPI descriptions (plugin) | `Cookies:`, `(optional)` | `쿠키:`, `(선택)` |
| OpenAPI viewer offline note and `<html lang>` (plugin) | `Could not load the Redoc script (unpkg.com). …` | `Redoc 스크립트(unpkg.com)를 불러오지 못했습니다. …` |
| Aggregate page (plugin `apiDocsAggregate`) | `Merged OpenAPI: `, `Project` | `전체 OpenAPI: `, `프로젝트` |
| Line prefix and item names of the app startup log ([Startup log](build.md#startup-log)) | `API docs (OpenAPI spec)` … | `API 문서 (OpenAPI 스펙)` … |

**Always English**, whatever the language: logs, warnings and exception messages (except the app startup log above, whose lines and missing-documents warning follow the language), the console output of Gradle tasks (the `Generated API docs` block and so on), the `apiDocsCheck` report, and the descriptions in the IDE configuration metadata. Table headers (`Path`, `Type`, `Description` and so on) come from the snippet templates, so the language setting doesn't affect them ([Changing snippet templates](customizing-snippets.md#changing-snippet-templates)).

Individual settings come before the language default. If you set `document.indexLinkText`, the startup log's `startup-log.title` or `*.label`, or the plugin's `index.title` (default: the project name), that text is used as is.

## Masking sensitive data

By default nothing is masked. Values actually present in requests, responses and messages remain in the documents as is. Choose the headers whose tokens or session values must not remain, and turn masking on.

```kotlin
RestDocsConfiguration.configure {
    masking {
        maskSensitive()                              // Authorization, Proxy-Authorization, Cookie, Set-Cookie and credential query/form parameters
        headers = headers + "X-Api-Key"              // add your own
        queryParameters = queryParameters + "ticket" // query and form parameters too
        masker = ValueMasker { target ->             // change how values are masked
            val auth = target.location == MaskLocation.HEADER && target.name.equals("Authorization", ignoreCase = true)
            if (auth) target.value.substringBefore(' ') + " ****" else "****"
        }
    }
}
```

In yml: `mask-sensitive-headers: true`, `masked-headers: X-Api-Key`, `masked-query-parameters: ticket`, `masked-header-value: "[hidden]"`.

- Header names remain, so you can still document them with `requestHeaders`. Names are case-insensitive.
- For authentication headers (`Authorization`, `Proxy-Authorization`), the scheme is kept and only the value is masked: `Bearer ****`. If you set `masking.masker`, it receives a `MaskTarget` with the whole value. It is also called for each masked cookie (`location = COOKIE`, with `cookieName`) and for masked query and form parameters (`location = PARAMETER`, `name` is the parameter name), whose values have no scheme prefix, so a masker that keeps the first characters of a value would reveal part of a parameter token. Branch on `location` and `name` as in the example above.
- For `Cookie` and `Set-Cookie`, only each cookie's value is masked; names and attributes remain (`SESSION=****; Path=/; HttpOnly`). `--cookie` in curl snippets is masked too.
- The same rules apply to HTTP snippets and WebSocket and STOMP message snippets.
- **Query and form parameters.** Clients that can't send headers, such as the browser `EventSource` and WebSocket, put tokens in the query (`?access_token=...`), and OAuth token requests and login forms send `password` and `client_secret` in an `application/x-www-form-urlencoded` body (MockMvc `.param(...)` on a POST becomes that body).
  - Values of the parameters in `masking.queryParameters` are masked in the request URI, the form body and non-file multipart parts of HTTP documentation (`andDocument`), so curl, httpie, http-request and request-body snippets and OpenAPI examples show `access_token=****` and `password=****`.
  - The parameter name, the order and the other parameters stay, and `Content-Length` follows the masked body.
  - Fields of a JSON body are masked with `@Masked` instead.
  - In WireMock mappings a masked parameter matches any value (`"matches": ".*"`) so the stub stays usable.
  - Names are case-insensitive. If you set `masking.masker`, it is called with `location = PARAMETER`, the parameter name and the value.
- `masking.maskSensitive()` (yml `mask-sensitive-headers: true`) also adds `MaskingSettings.SENSITIVE_QUERY_PARAMETERS`: `access_token`, `id_token`, `refresh_token`, `token`, `api_key`, `apikey`, `client_secret`, `client_assertion`, `password`, `passwd`, `current_password`, `new_password`, `signature`, `sig`, `X-Amz-Signature`, `X-Amz-Security-Token`, `X-Goog-Signature`. So a test that sends `?access_token=demo-token` or a login form with `password=...` produces `access_token=****` and `password=****` once it is on. Despite its name, the yml key `mask-sensitive-headers` therefore masks these parameters too.
- AsyncAPI documents and the viewer are distributed outputs, so `Authorization`, `Proxy-Authorization`, `Cookie` and `Set-Cookie` values are always masked there, regardless of this setting.
- This feature masks header values written to the documents. To state in the documents that the app masks values in the response body (a name such as `J*n Doe`), use [Marking masked fields](http-api.md#marking-masked-fields) (`@Masked`).
- **Kotlin default values.** Request fields and parameters extracted from a Kotlin class publish constructor default values (`val size: Int = 20` → `Default: 20`, OpenAPI `default`).
  - The defaults of `@Masked` fields (and fields with `masking.annotations`) are never published.
  - Defaults that differ between two evaluations are left out: random IDs and the current time, and defaults computed from another constructor parameter (`val pageSize: Int = limit`), because the second evaluation fills required parameters with different placeholder values. The class is constructed twice to do this, so side effects in its `init` block run twice (once per class).
  - A default computed from a collection parameter (`val count: Int = items.size`) can't be detected, because collections are empty both times.
  - A default read from an environment variable or configuration (`val clientId: String = System.getenv("CLIENT_ID") ?: "local"`) can't be told apart from a constant and **is published** with the value present when the tests ran. Don't put secrets in constructor defaults of request classes, or mark the field `@Masked` ([Kotlin default values](http-api.md#defaults-examples-and-constraint-notes)).

## Experimental APIs

APIs that are still being shaped are marked `@ExperimentalRestDocsApi` (`io.github.kardx`). They may change or be removed in a later release and are not covered by the 1.0 compatibility guarantee. Using one without opting in gives a compiler warning, not an error.

Currently marked:

- KDoc/Javadoc descriptions: `descriptions.sourceDocs`, `DescriptionResolver.SOURCE_DOCS`, `FromParamTag` with `pathMeans`/`queryMeans`/`formMeans`/`headerMeans`/`partMeans(FromParamTag)`, and parameters declared by name only (`pathParameters("id")`, `queryParameters(...)`, `formParameters(...)`, `requestHeaders(...)`, `requestParts(...)`) ([Descriptions from KDoc/Javadoc](http-api.md#descriptions-from-kdocjavadoc))
- Messaging test tools: `StompTestClient`, `WebSocketHandlerTester` ([Messaging](messaging.md))
- The client adapter SPI `io.github.kardx.client.ClientDocumentation` ([Client adapter SPI](architecture.md#client-adapter-spi))

Opt in where you use them, or for the whole module:

```kotlin
@OptIn(ExperimentalRestDocsApi::class)
class OrderApiDocsTest { ... }
```

```kotlin
// build.gradle.kts
kotlin {
    compilerOptions {
        optIn.add("io.github.kardx.ExperimentalRestDocsApi")
    }
}
```
