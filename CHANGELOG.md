**English** | [한국어](CHANGELOG.ko.md)

# Changelog

This file records the notable changes to this project. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1] - 2026-11-23

The first public version (0.1). Spring Boot 3.5 (REST Docs 3.0) and 4.0/4.1 (REST Docs 4.0) are supported by the same artifacts.

The default language of the text the library puts into generated documents is English. To use the Korean text of the development versions (`메인으로 돌아가기`, `API 목록`, `제약:` and so on), set `kardx { language = "ko" }`. Accordingly, `document.indexLinkText` is `String?`, and `null` (the default) uses the language's default text. Log, warning and exception messages, Gradle console output and the `apiDocsCheck` report are in English regardless of the language.

### Breaking (0.1 → next)

The public API was cleaned up before 1.0. The yml keys (`kardx.*`) and the Gradle plugin DSL (`kardx { }`) did not change.

- **Settings are split into groups.** The flat properties of `RestDocsSettings` moved to `extraction`, `descriptions`, `masking`, `http`, `openApi`, `asyncApi`, `output` and `document` (only `language` stays where it was). Open them as a block or use the path ([List of settings](docs/configuration.md#list-of-settings))

  ```kotlin
  // 0.1
  configure { openApiEnabled = false; maskSensitiveHeaders(); maxExtractionDepth = 5 }
  // next
  configure {
      openApi.enabled = false
      masking { maskSensitive() }
      extraction.maxDepth = 5
  }
  ```

  | 0.1 | next |
  |---|---|
  | `responseWrapper`, `responseWrapper(...)`, `responseWrapper<T>()` | `extraction.responseWrapper` (the same functions) |
  | `objectMapper`, `propertyNamingStrategy` | `extraction.objectMapper`, `extraction.propertyNameStrategy` |
  | `enumDescriptionProperty`, `maxExtractionDepth`, `arrayElementFieldsOptional`, `kotlinxSerialization` | `extraction.enumDescriptionProperty`, `extraction.maxDepth`, `extraction.arrayElementFieldsOptional`, `extraction.kotlinxSerialization` |
  | `descriptionResolver`, `sourceDocsEnabled` | `descriptions.resolver`, `descriptions.sourceDocs` |
  | `descriptionAnnotations`, `descriptionAnnotation(...)`, `descriptionFrom<A> { }` | `descriptions.annotations`, `descriptions.annotation(...)`, `descriptions.from<A> { }` |
  | `maskedAnnotations`, `maskedAnnotation(...)` | `masking.annotations`, `masking.annotation(...)` |
  | `maskedHeaders`, `maskedQueryParameters`, `maskedHeaderValue` | `masking.headers`, `masking.queryParameters`, `masking.replacement` |
  | `headerMasker`, `maskSensitiveHeaders()` | `masking.masker`, `masking.maskSensitive()` |
  | `RestDocsSettings.SENSITIVE_HEADERS`, `RestDocsSettings.SENSITIVE_QUERY_PARAMETERS` | `MaskingSettings.SENSITIVE_HEADERS`, `MaskingSettings.SENSITIVE_QUERY_PARAMETERS` |
  | `requestPreprocessors`, `responsePreprocessors`, `removedResponseHeaders`, `printRequests`, `applySpringSecurity` | `http.` + the same name |
  | `RestDocsSettings.VOLATILE_RESPONSE_HEADERS` | `HttpSettings.VOLATILE_RESPONSE_HEADERS` |
  | `openApiEnabled`, `openApiIncludeStreams`, `openApiSecuritySchemes`, `openApiGlobalHeaders` | `openApi.enabled`, `openApi.includeStreams`, `openApi.securitySchemes`, `openApi.globalHeaders` |
  | `openApiErrorSchema`, `openApiErrorSchema(...)`, `openApiErrorSchema<T>()` | `openApi.errorSchema` (the same functions) |
  | `openApiSchemaNames`, `openApiSchemaNamesWithoutWrapper`, `openApiSchemaNameResolver` | `openApi.schemaNames`, `openApi.schemaNamesWithoutWrapper`, `openApi.schemaNameResolver` |
  | `openApiOperationIdRule`, `openApiOperationIdResolver` | `openApi.operationIdRule`, `openApi.operationIdResolver` |
  | `asyncApiEnabled` | `asyncApi.enabled` |
  | `snippetsDirectory`, `asciidocDirectory`, `wireMockStubs` | `output.` + the same name |
  | `snippetsIncludePrefix`, `commonIncludeFiles`, `indexLink`, `indexLinkText` | `document.includePrefix`, `document.commonIncludeFiles`, `document.indexLink`, `document.indexLinkText` |
  | `documentSnippets`, `documentSectionOrder` | `document.snippets`, `document.sectionOrder` |

- **Callback settings are now fun interfaces.** `descriptions.resolver` (`DescriptionResolver`), `extraction.objectMapper` (`ObjectMapperProvider`), `extraction.propertyNameStrategy` (`PropertyNameStrategy`, input `PropertyNameInput.name`), `openApi.schemaNameResolver` (`SchemaNameResolver`, input `SchemaNameInput.type`), `openApi.operationIdResolver` (`OperationIdResolver`). To assign a lambda to a property, use the SAM constructor. A plain lambda still works as a constructor argument

  ```kotlin
  // 0.1
  propertyNamingStrategy = { PropertyNamingStrategies.SNAKE_CASE.nameForField(null, null, it) }
  openApiSchemaNameResolver = { "Dto" + SchemaNames.of(it) }
  // next
  extraction.propertyNameStrategy = PropertyNameStrategy { PropertyNamingStrategies.SNAKE_CASE.nameForField(null, null, it.name) }
  openApi.schemaNameResolver = SchemaNameResolver { "Dto" + SchemaNames.of(it.type) }
  ```

- **`HeaderMasker` is removed.** `masking.masker` is a `ValueMasker` and receives a `MaskTarget` (`location`, `name`, `value`, `cookieName`). It tells headers, cookies and parameters apart by `location`

  ```kotlin
  // 0.1
  headerMasker = HeaderMasker { name, value -> if (name == "Authorization") value.substringBefore(' ') + " ****" else "****" }
  // next
  masking.masker = ValueMasker { target ->
      if (target.location == MaskLocation.HEADER && target.name == "Authorization") target.value.substringBefore(' ') + " ****" else "****"
  }
  ```

- **The way to extend the default description rule changed.** The default steps are public as `DescriptionResolver.DEFAULT`, so you compose your own rule with them without losing `@Description`, the annotation list and KDoc ([Composing the description chain](docs/http-api.md#composing-the-description-chain))

  ```kotlin
  // 0.1
  val previous = descriptionResolver
  descriptionResolver = { it.findAnnotation<Schema>()?.description ?: previous(it) }
  // next
  descriptions.resolver = DescriptionResolver { it.findAnnotation<Schema>()?.description } orElse descriptions.resolver
  ```

- **Constructors of input classes are now internal.** Only the library creates `PropertyContext` and `OperationIdInput`. Properties may be added to them later
- **The JUnit `AutoRestDocumentationExtension` is now final.** Instead of subclassing it and overriding `documentGenerator()`, pass the constructor argument `documentGenerator` (`DocumentGeneratorFactory`) and register it with `@RegisterExtension`. A static `@RegisterExtension` in an abstract base class also applies to subclasses. `@ExtendWith(AutoRestDocumentationExtension::class)` works as before

  ```kotlin
  // 0.1
  class MyDocsExtension : AutoRestDocumentationExtension() {
      override fun documentGenerator(): DocumentGenerator = MyGenerator()
  }
  // next
  companion object {
      @JvmField
      @RegisterExtension
      val restDocs = AutoRestDocumentationExtension(documentGenerator = DocumentGeneratorFactory { MyGenerator() })
  }
  ```

- **The format of `EnumDocumentationExtension` is given as the constructor argument `format`.** JUnit removed the `protected open fun format()` override (subclassing still works), and Kotest takes an instance instead of `() -> EnumDocumentFormat`

  ```kotlin
  // 0.1
  class MyEnumDocs : EnumDocumentationExtension(packageName = "com.example") {
      override fun format(): EnumDocumentFormat = KoreanEnumFormat()
  }
  // next
  class MyEnumDocs : EnumDocumentationExtension(packageName = "com.example", format = KoreanEnumFormat())
  ```

- **Argument types of the rules in Kotest `RestDocsExtension`.** `sectionTitle` (`SectionTitleProvider`) and `snippetName` (`SnippetNameProvider`) receive a `TestPath` (`names`, `specClass`), and `documentGenerator` (`DocumentGeneratorFactory`) receives a `DocumentGeneratorContext`. `{ MyGenerator() }` still compiles

  ```kotlin
  // 0.1
  RestDocsExtension(sectionTitle = { names -> names.last() })
  // next
  RestDocsExtension(sectionTitle = { test -> test.names.last() })
  ```

- **`SnippetFile` changed from an enum to a class.** The constant names (`SnippetFile.RESPONSE_BODY` …) are unchanged, and `SnippetFile.entries` became `SnippetFile.BUILT_IN`. A `when` over `SnippetFile` needs an `else`
- **Extension authoring API**: the generator argument of `DocumentationLifecycle.writeDocument` is now a `DocumentGeneratorFactory` (`writeDocument(documentClass, generator, outline)`)

### Added

#### `kardx` (core)

- Documentation DSL `andDocument { }`: path, query and form parameters, request and response headers, cookies, multipart parts, links, body fields, arbitrary snippets, OpenAPI settings. The same block works with MockMvc (`ResultActions`, the Kotlin DSL `ResultActionsDsl`, `MockMvcTester`) and WebTestClient (`ResponseSpec`, `BodyContentSpec`, `BodySpec`)
- Setup helpers `restDocsMockMvc` and `restDocsWebTestClient` (they apply the app's Jackson mapper and the `kardx.*` properties) and MockMvc helper functions (`andStatus`, `andReturnEntity<T>()`, `andReturnAsObject<T>()`, `andPrint()` and others)
- Extracting fields and parameters from types with `responseFieldsOf<T>()`, `requestFieldsOf<T>()`, `parametersOf<T>()`: generics, collections, sealed types, value classes, Map, circular references, Java records and beans, Jackson naming strategies, Bean Validation constraints, enum constant descriptions, Spring Data `Page` and `Slice`, `ProblemDetail`
- Common response wrapper (`ResponseWrapper`, `wrappedResponseFieldsOf<T>()`), list editing (fields: `except`, `optionalAt`, `optionalUnder`; parameters: `except`, `optionalAt`, `RestDocsParam.name`), previewing extraction results (`describe()`, `printDescription()`)
- Documentation markers: `@Description`, `@Masked`, `@Deprecated`, and reading description and masking rules from other annotations (`description-annotations`, `masked-annotations`)
- Verification failure diagnostics `DocumentationMismatchException`: undocumented and missing items, field type mismatches (`FieldTypesDoNotMatchException`, `FieldTypeRequiredException`), DSL lines to paste, hints about the cause
- Non-text request and response bodies and multipart file parts are kept in snippets only as `<binary: N bytes>` (WireMock mappings keep the original bytes)
- File body descriptions `responseFile("...")` and `requestFile("...")`: for each binary body they create a `response-file` or `request-file` table (description, media type without charset, file name from `Content-Disposition`, size) and put it where the raw body would go in the generated document, and the binary body in `response-body` and `request-body` is written as a `[source]` block without a language instead of `[source,pdf]`. The Gradle plugin puts the description into the OpenAPI body schema's `description`, removes `charset` from binary media types (`application/pdf`), and puts the actual value of the file response's `Content-Disposition` header into `example`
- Optional header masking (`masking.maskSensitive()`, `masking.headers`, `ValueMasker`) and adding Spring REST Docs preprocessors (`http.requestPreprocessors`, `http.responsePreprocessors`)
- Query and form parameter masking `masking.queryParameters` (yml `kardx.masked-query-parameters`, default an empty list): the values of parameters with a matching name are hidden in the request URI's query, `application/x-www-form-urlencoded` bodies and non-file multipart parts, and appear as `****` in the curl, httpie, http-request and request-body snippets, OpenAPI and WireMock mappings (when the body is masked, `Content-Length` is adjusted too). WireMock accepts masked parameters with `matches ".*"`, and calls `masking.masker` if one is set. Well-known credential names are in `MaskingSettings.SENSITIVE_QUERY_PARAMETERS` (`access_token`, `id_token`, `refresh_token`, `token`, `api_key`, `apikey`, `client_secret`, `client_assertion`, `password`, `passwd`, `current_password`, `new_password`, `signature`, `sig`, `X-Amz-Signature`, `X-Amz-Security-Token`, `X-Goog-Signature`). `masking.masker` is also called for parameters, with `MaskLocation.PARAMETER` and the parameter name
- Headers to remove from documented responses `http.removedResponseHeaders` (yml `kardx.removed-response-headers`, default `HttpSettings.VOLATILE_RESPONSE_HEADERS` = `Date`, `none` removes nothing). A yml list sets the whole list instead of adding to the default. Headers documented with `responseHeaders(...)` (the DSL, or a REST Docs `responseHeaders` passed through `snippets(...)`) are not removed. When the set is not empty, the `Expires` attribute of a `Set-Cookie` that has `Max-Age` (computed from the current time, so it changes on every run) is removed as well
- SSE documentation (`performAsync` + `events(...)`, `performStream` for streams that never end), WebSocket and STOMP message documentation (`MessageDocumentation`, `WebSocketHandlerTester`)
- AsyncAPI 3.0 generation (`AsyncApiGenerator`, `AsyncApiCli`) and a Try it out viewer
- OpenAPI fragments for the epages restdocs-api-spec integration: types, formats, constraints, examples, default values, enums, multipart and cookies, automatic schema names, a common error schema, security schemes (for tests that authenticate without a header, `openApi { bearerAuth() }` and `basicAuth()`) and global headers, operations per response status, operationId rules (`handler-method` by default)
- Filling OpenAPI from the handler: the types of path, query and form parameters declared without a type (controller arguments), deprecated (`@Deprecated`), tags (document class title) and summaries (test display name). `deprecated = true` and `deprecated("reason")` in the block also appear in `api-summary` as a `*Deprecated*` line. Undocumented path variables produce a warning
- Automatic AsciiDoc document generation (`AutoRestDocumentationExtension`), the documentation index (`DocumentationIndex`, `DocumentationIndexCli`), enum code documents (`EnumDocumentationExtension`), snippet templates with Required and Constraints columns, settings for the index link and section order in generated documents
- Settings `RestDocsConfiguration.configure { }`, `withSettings({ }) { }`, `current`, the `kardx.*` keys in `application.yml` (feature groups `open-api.*`, `async-api.*`, `snippets.*`, `response-wrapper.*`), configuration metadata for IDE completion, warnings for unknown keys
- WireMock mapping output (`wire-mock-stubs`)
- Opt-in API for building test framework extensions, `io.github.kardx.lifecycle` (`@RestDocsExtensionApi`)
- Document language `RestDocsSettings.language` (`DocsLanguage.EN` by default, `KO`) and the yml and system property `kardx.language`: index text, the index link, the schema description prefix, automatic descriptions for Spring Data, `ProblemDetail` and sealed types, AsyncAPI channel descriptions and the viewer screen
- Declared error responses `errorResponse(status, "description")`: declare in the block the errors (400–599) that the tests don't produce. The block form takes body fields (`fields(...)`, `fields<T>()`), an example (`example(json)`, `example(object)`) and `contentType`, and the one-line form `errorResponse(409, "...", exampleObject)` gives field extraction and the example at once. It creates an `error-responses` snippet (a status and description table, plus a field table and example per response) and becomes OpenAPI responses and schemas. It is only a declaration and is not verified; the only check is that the example matches the declared fields. A declaration for the same status the test actually received is dropped
- Per-section relaxed verification `relaxed(RelaxedSection...)`: allows undocumented items only in the chosen sections among path, query and form parameters, request and response fields, parts and part fields, request and response cookies, links and SSE events
- Document language mismatch warning: when tests run through the Gradle plugin and the document language set with `kardx.language` or `RestDocsConfiguration.configure` differs from the plugin's language, a WARN is logged once per JVM on the `io.github.kardx.config` logger (`kardx: the documentation language is '<core>' ... but the Gradle plugin uses '<plugin>' ...`) and it suggests `kardx { language = "<core>" }`. Without the plugin there is no warning
- `StompTestClient` (`StompSubscription`, `StompFrame`): in a test, connects to the running app's STOMP endpoint with CONNECT headers and handles subscribing, sending and waiting for frames (`sendAndReceive` resends until the subscription is registered). spring-messaging and spring-websocket must be on the test classpath
- RestTestClient entry point (Spring Boot 4 / Spring Framework 7 only, package `io.github.kardx.resttestclient`): `RestTestClient.Builder.restDocs(provider)` and `ResponseSpec`, `BodyContentSpec`, `BodySpec.andDocument { }`. Works with REST Docs 4.0 (`spring-restdocs-resttestclient` not needed). Multipart parts are not split, handler-based defaults (operationId, parameter types, `@Deprecated`) are not used, and relative URIs are resolved against `http://localhost`
- AsyncAPI authentication schemes `AsyncApiInfo.securitySchemes` (`AsyncApiSecurityScheme.bearer`, `basic`, `apiKey`, `AsyncApiApiKeyLocation`) and the CLI option `--security name=bearer|basic|apiKey:<header|query|cookie>:<name>`: they become `components.securitySchemes` and the server's `security`. The viewer's Try it out gets an authentication box; STOMP sends the credentials as CONNECT headers, and WebSocket and SSE send them as a query parameter if chosen (the browser can't add headers, and the page can't set cookies)
- Endpoint inventory: for each application context, the first documented request reads `RequestMappingHandlerMapping` (servlet and reactive) once and writes the methods, path patterns, handlers, deprecation and API versions to `.kardx/endpoints/<id>.json` in the snippets directory. Contexts with the same mappings write the same file only once, and standalone setups, RestTestClient (the context is unknown) and functional routes are skipped
- Subtype schemas of sealed and `@JsonTypeInfo`/`@JsonSubTypes` types are recorded in `openapi-extras.json` (`requestBodyPolymorphism`, `responseBodyPolymorphism`). The discriminator is recorded only when `use = NAME | SIMPLE_NAME | CLASS` and `include = PROPERTY | EXISTING_PROPERTY`; `WRAPPER_OBJECT` and `WRAPPER_ARRAY` are not recorded. Each field list remembers the extracted type and path, so extraction with a path (`requestFieldsOf<T>("data")`), the common response wrapper (`data.method` of `wrappedResponseFields<T>()`, or `data` when the data itself is polymorphic) and bodies that combine several lists are recorded with the path prefix. Bodies made only of hand-written fields are not recorded. Field tables, snippets and verification are unchanged
- `openApi { oauth2(vararg scopes) }` and `openIdConnect(vararg scopes)`: OAuth2 and OpenID Connect security requirements and scopes for tests that authenticate without a header
- `openApi { summary = "..."; description = "..." }`: sets the OpenAPI operation's summary and description instead of the test name and the block description. They are recorded as `summary` and `description` in `openapi-extras.json`; the REST Docs snippet (`api-summary`) and the example names are unchanged
- Reading Kotlin constructor default values (request extraction only): parameters extracted with `parametersOf<T>()` get them as with `withDefaultValue` (an explicitly written value wins), and body fields extracted with `requestFields<T>()` record them for `(Default: 20)` in the field table and for the OpenAPI `default` (`fieldDefaults`). Only strings, numbers, booleans and enums; read once per class, and skipped on failure
- Spring Framework 7 API versions (`@GetMapping(version = "1.1")` and so on) are read from the handler and recorded in the OpenAPI fragment and the endpoint inventory. The baseline version notation `"1.2+"` is written as `"1.2"`. RestTestClient and functional routes record no version, and on Spring Framework 6 nothing happens
- KDoc/Javadoc as descriptions: after `@Description` → `description-annotations`, the default description sources look at the source comments recorded by the Gradle plugin (`-Dkardx.source-docs`). They are used for field, `parametersOf` parameter and enum constant descriptions, and handler method comments are recorded as `sourceSummary` and `sourceDescription` in `openapi-extras.json` (MockMvc and WebTestClient; RestTestClient doesn't know the handler, so it has none). An enum constant comment is used only when there is no other description (`extraction.enumDescriptionProperty`), and not when `@EnumCode(descriptionProperty = ...)` is set explicitly. If `descriptions.resolver` is replaced without the default steps, field and parameter comments are dropped as well. The file is read once per JVM; if it can't be read, there is one warning and the feature turns off. Turn it off with yml `kardx.source-docs.enabled: false` (`descriptions.sourceDocs`)
- Name-only parameter declarations `pathParameters("id")`, `queryParameters("keyword", "page")`, `formParameters(...)`, `requestHeaders(...)`, `requestParts(...)`: the description comes from the `@param` in the KDoc or Javadoc of the handler method that handled the request (matched by the name in `@PathVariable`, `@RequestParam`, `@RequestHeader` or `@RequestPart`, otherwise by the argument name; independent of `methodDocs`). A description written in the test wins, and if no description is found it fails with an `IllegalArgumentException` that names the item. When you need modifiers, put `FromParamTag` in place of the description (`"page" queryMeans FromParamTag isOptional true example "0"`, `pathMeans`, `queryMeans`, `formMeans`, `headerMeans`, `partMeans`; not available for response and message headers). It finds nothing for RestTestClient, functional routes and Java handlers compiled without `-parameters`
- Parameter injection into JUnit test methods and `@BeforeEach`: `AutoRestDocumentationExtension` injects a REST Docs client (the same as `restDocsMockMvc(context, provider)`) into `MockMvc` and `WebTestClient` parameters. No base class or `@BeforeEach` is needed. `@Autowired`, `@Qualifier` and `@Value` parameters and constructor parameters are left to Spring, and outside a Spring test it fails with `KDX-014`
- `restDocsMockMvc(context)` and `restDocsWebTestClient(context)` without a provider: each request is documented under the test running at that moment (a JUnit test method, a Kotest leaf test), so you can create the client once in a field, a constructor or a Kotest spec body. With no running test, `KDX-013`
- Error codes `RestDocsErrorCode` (`io.github.kardx.diagnostics`, 45 codes): the library's error and warning messages start with `kardx[KDX-011]: …` and end with the address of that code's anchor in the troubleshooting document (`troubleshooting.md#kdx-011`). The hundreds digit is the stage (0xx setup and settings, 1xx documentation, 2xx OpenAPI, 3xx messages, 4xx Kotest, 5xx files, 6xx startup log)
- Reporting documentation with a different REST Docs configuration: when the configuration was not created by this library, as with a client obtained through Spring Boot `@AutoConfigureRestDocs`, it fails with `KDX-011` if the snippet location differs (previously the section silently disappeared from the document), and warns once per JVM if it is the same. A client without any REST Docs configuration (a `MockMvc` injected as is) gets `KDX-012`
- Extracting Spring HATEOAS models in HAL shape: `EntityModel<T>` (the fields of `T` + `_links`), `RepresentationModel` subclasses (their own fields + `_links`), `CollectionModel<T>` (`_embedded.<relation>[]` + `_links`), `PagedModel<T>` (+ `page.size`, `totalElements`, `totalPages`, `number`). The relation name is `@Relation(collectionRelation)` → the evo-inflector plural → `<type>List`. `_links` is an optional subsection, and naming strategies are not applied to HAL keys. It doesn't depend on spring-hateoas; types are recognized by class name
- Extracting kotlinx.serialization `@Serializable` classes through the serial descriptor (when kotlinx-serialization-json is on the classpath): `@SerialName` names, excluding `@Transient` properties and properties without a backing field, properties with a default value are optional (`@EncodeDefault` makes them required in responses), single-value serializers use that value's type, the sealed discriminator `type` (`@JsonClassDiscriminator`) and OpenAPI `oneOf`. Setting `extraction.kotlinxSerialization` (yml `kardx.kotlinx-serialization`, default `true`)
- The OpenAPI schema name sources (`openapi-schemas.json`) also record the body type for explicitly set names (`openApi { responseSchema }`, `openApi.errorSchema`, `openApi.schemaNameResolver`) (the name is not changed). The Gradle plugin uses that class's KDoc as the schema description
- Composing description rules with `DescriptionResolver`: the default steps `DESCRIPTION_ANNOTATION` (`@Description`), `CONFIGURED_ANNOTATIONS` (`descriptions.annotations`), `SOURCE_DOCS` (KDoc/Javadoc), `DEFAULT`, which looks at them in order, and the composition functions `DescriptionResolver.firstOf(...)` and `orElse`. Put your own rule before or after the default steps without losing `@Description`, annotations and KDoc
- Custom snippet kinds `SnippetFile.custom(fileName, title, includedByDefault)`, placing them with `document { register(kind, after = ... / before = ...) }`, and the current order `document.order`. Registered kinds go into the generated document with the given title and position; unregistered snippets are appended at the end in file name order, as before
- `@ExperimentalRestDocsApi` (`RequiresOptIn`, warning): marks APIs outside the 1.0 compatibility guarantee. It is on KDoc/Javadoc descriptions (`descriptions.sourceDocs`, `DescriptionResolver.SOURCE_DOCS`, `FromParamTag`, name-only parameter declarations), `StompTestClient`, `WebSocketHandlerTester` and `ClientDocumentation`. Enable it with `@OptIn(ExperimentalRestDocsApi::class)` or `optIn.add("io.github.kardx.ExperimentalRestDocsApi")`
- Client adapter SPI `io.github.kardx.client.ClientDocumentation` (`@RestDocsExtensionApi`, experimental): call `configuration(provider)` before the request and `document(exchange, requestConverter, responseConverter, ...)` after the response so that other HTTP test clients get the same documentation. The RestTestClient adapter is built on this alone
- `MaskTarget.location` (`MaskLocation.HEADER`, `COOKIE`, `PARAMETER`) and `cookieName`: maskers tell headers, cookies and query and form parameters apart
- `PropertyContext.declaredName` (the source name before naming strategies) and `declaringClass` (the declaring class)

#### `kardx-annotations`

- `@Description`, `@Masked`, `@EnumCode`, `@AsciiDescription`, added to production code as `compileOnly`

#### `kardx-kotest`, `kardx-kotest5`

- Kotest spec documentation extension `RestDocsExtension`: snippets per leaf test, an AsciiDoc document per spec. Naming rules `TestNames`, `EnumDocumentationExtension` for Kotest
- When the Kotest Spring extension is present, the yml and Jackson mapper of its context are used
- When registered both in a spec and in the project config, the extension registered in the spec does the documentation (so a single spec can change `sectionTitle`, `documentGenerator` and so on)
- Split into a Kotest 6.x artifact (`-kotest`) and a 5.x artifact (`-kotest5`) with the same packages and public API; they declare the same Gradle capability, so having both is reported as a conflict

#### `kardx-boot`

- Logs the documentation addresses (REST Docs, Swagger UI, OpenAPI, OpenAPI viewer, AsyncAPI) at INFO when the app starts. Change them with `kardx.startup-log.*`; the defaults are read from the actual document locations recorded by the Gradle plugin. Applies the WebFlux `base-path`
- Language of the startup log text: `kardx.language`, otherwise the value recorded by the Gradle plugin, otherwise English. An unsupported value logs a warning and falls back to English instead of stopping the app

#### `kardx-bom`

- Aligns the versions of the artifacts above (includes `kardx-tooling`; the Gradle and Maven plugins are not dependencies, so they are not included)

#### `kardx-tooling` (new artifact)

- The steps after the tests are moved into a plain Java 17 module that runs without the Gradle API: OpenAPI post-processing, baseline spec comparison, documentation coverage, the OpenAPI viewer, IntelliJ HTTP Client, Postman cleanup, the KDoc and Javadoc scanner, export and aggregation helpers. The Gradle plugin, the Maven plugin and the CLI run the same code, so the output files are the same
- Command-line tool `io.github.kardx.tooling.cli.ApiDocsCli`: commands `api-docs` (everything enabled in the configuration) `openapi` `viewer` `coverage` `http` `postman` `check` `baseline` `source-docs`, options `--config` `--base-dir` `--snippets` `--output` `--work` `--language` `--title` `--version` `--server` `--format` `--baseline` `--fail-on` `--source` `--method-docs` `--verbose`. Command-line options override the configuration file, and the exit code is 0 for success, 1 for failure, 2 for incorrect usage ([Maven and the command line](docs/maven.md))
- JSON configuration file (shared by CLI `--config` and Maven `<configFile>`): maps one to one to `openApiGlobalHeaders`, `openApiCommonResponses`, `openApiSecurity`, `apiVersioning`, `openApiInfo`, `openApiViewer`, `coverage`, `httpClient`, `postmanCollection` and `openApiCheck` in Gradle `kardx { }`. Unknown keys fail with the key path
- Executable jar `kardx-tooling-0.1-all.jar` (classifier `all`, includes the epages OpenAPI and Postman generators 0.20.2): `java -jar ... api-docs --config restdocs-tooling.json` from any build tool. It can also be called through `exec-maven-plugin` without the plugin (in that case add the epages generators as plugin dependencies)

#### Maven plugin `io.github.kardx-project:kardx-maven-plugin` (new artifact, goal prefix `kardx`)

- `api-docs` (default phase `prepare-package`): runs core's `DocumentationIndexCli` in a separate JVM with the test classpath to create `index.adoc`, collects the asciidoctor-maven-plugin input `target/kardx/asciidoc-sources`, runs `AsyncApiCli` if the tests left `*.asyncapi.json`, and then creates OpenAPI, the viewer, coverage, `.http` and Postman in `target/api-docs/`
- `check` (default phase `verify`): compares with the baseline spec `docs/openapi/openapi3.yaml`, report `target/kardx/openapi-changes.txt`. `baseline` (no phase): copies the generated spec to the baseline. `source-docs` (default phase `process-classes`, experimental): KDoc and Javadoc index
- Configuration precedence is POM parameters > `configFile` > defaults, and the OpenAPI title and version default to `${project.name}` and `${project.version}`. Skip with `-Drestdocs.skip`
- Bundles the epages generators 0.20.2 regardless of the Boot version (it only reads `resource.json`). The test side stays 0.19.x for Boot 3 and 0.20.x for Boot 4
- Gradle only: the WireMock stub jar, `apiDocsExport` and `apiDocsUpload`, aggregating multiple projects, generating on run, adding dependencies automatically, putting docs into the app (do it yourself with maven-resources-plugin), the document location file for the startup log, passing the language to the test JVM, up-to-date checks and caching

#### Gradle plugin `io.github.kardx-project`

- Sets up the documentation pipeline: passing test paths, `apiDocsIndex`, Asciidoctor inputs and extensions, epages `openapi3` defaults and post-processing, `apiDocsAsyncApi`, the `apiDocs` aggregate and location output, including docs in app resources and the executable jar
- Adds the library and test client dependencies automatically (detecting MVC, WebFlux and Kotest), excludes the servlet starter in WebFlux apps
- Generates docs automatically before IntelliJ runs and `bootRun` (`generateOnRun`), warns when documents without masked sensitive headers would be exposed in production
- Optional features (off by default): OpenAPI change comparison `apiDocsCheck` and `apiDocsBaseline`, Redoc and Scalar static viewers `apiDocsViewer`, Postman Collection, multi-project aggregation `apiDocsAggregate`, WireMock stub jar `apiDocsStubsJar`, common error responses `openApiCommonResponses` (OpenAPI and an index table)
- The actual work of the steps after the tests lives in `kardx-tooling`, and the plugin calls it. Nothing changes in behavior or output files for plugin users. The internal packages `io.github.kardx.gradle.internal.*` (openapi, spec, check, aggregate, coverage, httpclient, sourcedocs, export, i18n, io, pages) moved to `io.github.kardx.tooling.internal.*` (internal, so not public API)
- Spec export (run directly): copying to a directory `apiDocsExport`, HTTP upload `apiDocsUpload`, attaching specs to the Maven publication (`export.publishSpecs`)
- `apiDocsCheck` fails when there is no baseline file (except with `failOn = "none"`; turn off with `openApiCheck.requireBaseline = false`)
- All task names start with `apiDocs`, and intermediate tasks don't appear in `./gradlew tasks`
- Fails immediately on apply with Gradle below 7.6, and is compatible with the configuration cache
- `kardx { language = "ko" }`: passes the same language to the test JVM, `apiDocsIndex`, `apiDocsAsyncApi` and the app startup log, and also uses it for the plugin's text (the common error response table, the constraint and cookie text in OpenAPI descriptions, the OpenAPI viewer's offline notice, the aggregate page)
- Adds the responses declared with `errorResponse(...)` to the OpenAPI operation. Precedence: status actually produced by the test > declaration > `openApiCommonResponses`. Declared fields become a schema in `components.schemas` (an existing identical schema is reused; otherwise it is named after the status, like `ConflictResponse`), and declaring the same status with different descriptions produces a warning
- `asyncApi { bearerAuth(); basicAuth(); apiKeyHeader("X-API-KEY") }` and the `securitySchemes` map (query and cookie API keys are written directly, like `"apiKey:query:<name>"`): passed to `apiDocsAsyncApi` as `--security`
- `kardx.asciidoctorJvmArgs` (default `["-XX:TieredStopAtLevel=1"]`, an empty list turns it off): JVM arguments for the asciidoctor work. In the Boot 4 sample, asciidoctor went from about 7.4 to 6.9 seconds
- Documentation coverage `apiDocsCoverage` (when the epages plugin is present, part of `apiDocs`): compares the endpoint inventory left by the tests with the documented operations, writes `coverage.json` and `coverage.md` to `build/kardx/coverage/` (`apiDocs` copies them to `build/api-docs/coverage/`), and prints a one-line summary when the task actually runs. Path variable names are ignored, and operations left out with `openApi { excluded = true }` also count as documented by their actual request path. `/error`, `/actuator/**`, springdoc paths (`/v3/api-docs/**`, `/v3/api-docs.yaml`, `/swagger-ui/**`, `/swagger-ui.html`) and the documentation paths are always excluded, and `exclude(...)` adds to them. `coverage { enabled; failOnUndocumented; exclude(...) }`. Without an inventory it passes with `no endpoint inventory`
- Sealed and polymorphic types as OpenAPI `oneOf`: creates a component schema per subtype (`<base type><subtype>` when names collide) and adds `discriminator` and `mapping`. Nested paths, such as inside the common response wrapper (`data.method`, `data.items[].method`, `data`), are also replaced by following `$ref` and arrays, and the flat schema the replaced spot pointed to is removed if nothing else uses it
- Puts `openApi { summary / description }` into the operation merged from the fragments. When tests of the same operation set different values, the first value in snippet directory name order is used and an `OpenAPI summary/description:` warning is logged. Per-version specs get the same values, and the `###` titles of the HTTP Client export (`api.http`) also follow the summary of the post-processed spec
- `openApiSecurity { oauth2 { clientCredentials(tokenUrl); authorizationCode(authorizationUrl, tokenUrl); password(tokenUrl); implicit(authorizationUrl); scope(name, description) }; openIdConnect(url) }` (positional arguments): security schemes `oauth2` and `openIdConnect`. Scopes that an operation uses but that are not declared with `scope()` are added with an empty description. Operations that use a scheme that is not configured produce a warning and lose that security requirement
- Fills `default` of request body schema properties from Kotlin default values
- `apiVersioning { header(...) | queryParameter(...) | mediaTypeParameter(...) | pathSegment(); splitSpecs }`: the operation's `x-api-version` and the version header or query parameter. `mediaTypeParameter` and `pathSegment` only add `x-api-version`. Versions are compared numerically segment by segment. For several versions of the same path and method, `splitSpecs = true` creates additional `openapi3-v<version>.yaml` files next to `openapi3.yaml`; otherwise the highest version is kept and a warning is logged. epages merges the same path and method, so the body schema is the union of all versions
- IntelliJ HTTP Client export `apiDocsHttpClient`, `httpClient { enabled; fileName }` (off by default, requires the epages plugin): writes `api.http`, which holds one representative request per operation (successful-response tests first) as `### <title>`, `# @name <operationId>`, `{{baseUrl}}`, headers (except `Host` and `Content-Length`) and body, and `http-client.env.json` to `build/kardx/http-client/`, and `apiDocs` copies them to `build/api-docs/http-client/`. A Bearer `Authorization` header becomes `{{bearerToken}}`, and an empty `bearerToken` is added to the environment file
- `apiDocsCheck` detects more: tightened request constraints (`minLength`, `minimum`, `minItems` increased; `maxLength`, `maximum`, `maxItems` decreased; `pattern` added or changed), added operation security requirements and scheme changes, removed response headers and removed `oneOf` variants (request and response) are breaking changes, and added response `oneOf` variants and `default` changes are warnings. `anyOf`, `allOf`, `additionalProperties` and scope changes are not evaluated
- `apiDocsSourceDocs` (before `test`, cacheable): reads KDoc and Javadoc (property KDoc > `@property` > `@param`; Java field > getter > record `@param`; enum constants; controller methods) from the Kotlin and Java `main` sources of the project and the projects it depends on through `project(...)`, without the compiler, writes them to `build/kardx/source-docs/source-docs.json` and passes the path to the test JVM. Links, `{@code}` and HTML are cleaned up into one line, and declarations it can't read are just skipped without failing the build. When sources change, everything is read again (about 0.2 seconds for the Boot 4 sample). Overloads are `#name` when the comments are the same, and `#name/<arity>` by the source argument count when they differ. Top-level declarations, local and anonymous classes, Java constructor `@param` and `///` comments are not collected, and project dependencies are found through the java plugin's main source variant, so Android, KMP and included builds are left out. `sourceDocs { enabled; includeProjectDependencies }` (both `true` by default; with `enabled = false` the task stays but `test` doesn't depend on it). The OpenAPI operation summary (first sentence, without the trailing period) and description (the whole text) follow `openApi { }` values > method comments > the previous defaults
- `sourceDocs { methodDocs = true }` (default `false`): using controller method KDoc and Javadoc as the OpenAPI operation summary and description only works when turned on. This keeps implementation notes on handler methods from leaking into the summary of public documentation. When on, the whole method comment also becomes the `api-summary` (HTML document) description for tests whose block has no `description`. When off, `apiDocsSourceDocs` writes only the `@param` text (`params`) into the method entries of `source-docs.json`; the value is a task input, so changing it reruns the task. Field, type and enum constant comments are used regardless of this value
- `openApiInfo { }`: top-level spec metadata. `description` and `descriptionFile` (Markdown), `termsOfService`, `contact`, `license`, document `externalDocs`, tag order and descriptions with `tag("Orders") { description; externalDocs { } }` (takes precedence over the test class `@AsciiDescription`), `x-tagGroups` with `tagGroup(...)` (tags without a group go into a final `Other`, `기타` in Korean). It goes into per-version specs too, but not into the `apiDocsAggregate` merged spec. Setting both `description` and `descriptionFile`, or a missing file, fails; a missing `license.name` or `externalDocs.url`, or an unused tag, produces a warning and is left out
- `html { enabled }` (default is the Gradle property `kardx.html`, otherwise `true`): when off, `apiDocs` doesn't run `asciidoctor` and `apiDocsIndex` and deletes the previous HTML. OpenAPI, the viewer, Postman, `.http`, coverage and copying into app resources are unchanged, and the location output shows `REST Docs HTML    skipped`. To turn it off only locally, use `kardx.html=false` in `~/.gradle/gradle.properties` or `-Pkardx.html=false`; to turn it on only in CI, use `enabled = providers.environmentVariable("CI").isPresent`
- Class KDoc and Javadoc go into the `description` of OpenAPI component schemas (`components.schemas`). It is the description of the class that produced the schema name (the outer type, the type argument when the name has the wrapper removed, nested classes included), and explicitly set names get it too when the body type is known. Names that already have a description or that are shared by several classes are skipped. Turned off by `sourceDocs { enabled = false }`

#### Documentation and samples

- User guide (`docs/`), architecture document (`docs/architecture.md`), a guide to migrating from plain Spring REST Docs, a guide to migrating from Spring Auto REST Docs (ScaCap)
- Guide to `@WebMvcTest` slice tests (`docs/getting-started.md`) and the English/Korean document pair check `scripts/check-docs-pairs.py`. The root task `checkDocsPairs` (verification group, wired into `check`) runs this script, so `./gradlew build` also fails when a pair is out of step
- The package cycle check `scripts/check-package-cycles.py` is run by the root task `checkPackageCycles` (verification group, wired into `check`) on the core, kotest and kotest5 (each including kotest-common) and Gradle plugin sources, so `./gradlew build` also fails when a cycle appears. It doesn't rerun when the sources are unchanged. The Python used by the two root check tasks can be changed, like `-Prestdocs.python=py` (Windows)
- The Gradle compatibility tests also run spec export and upload. Checked on 7.6.4, 8.14.6, 9.0.0 and 9.8.2
- CI guide `docs/ci.md`: GitHub Actions PR checks (`apiDocsCheck`, `coverage { failOnUndocumented }`), report PR comments, the baseline update flow, deploying with GitHub Pages and `apiDocsUpload`, multiple projects, a GitLab CI example
- Three sample apps (Boot 3, Boot 4, Boot 4 WebFlux, package `com.example.restdocs`) and output verification scripts (`scripts/`)
- Maven sample `samples/maven-boot-4` (Boot 4.1, Java app + Kotlin tests, Maven Wrapper): with the Maven plugin, asciidoctor-maven-plugin 3.2.0 and maven-resources-plugin, `./mvnw verify` produces the tests, index, OpenAPI, viewer, coverage, HTML, the baseline spec check and the docs inside the jar. Run `./gradlew publishToMavenLocal` in the repository first
- Maven and command-line guide `docs/maven.md` (with a Korean pair): feature comparison, configuration, goals and parameters, JSON configuration, the spec check, KDoc descriptions, `exec-maven-plugin` and `java -jar`, what only works in Gradle, troubleshooting. The package cycle check and tooling's javap-based `apiDump`/`apiCheck` also cover tooling and maven-plugin
- The document pair check also covers `CONTRIBUTING.md` and `CONTRIBUTING.ko.md` (18 pairs now, also added to the `checkDocsPairs` inputs)
- Reproducible snippets guide (`docs/build.md`): the `Date` header, Spring Data `PageImpl` property order. The Boot 3 sample pins Jackson 2's property order to the Jackson 3 (Boot 4) defaults in `src/main/resources/config/application.yml` (`sort-properties-alphabetically`, `sort-creator-properties-by-declaration-order`). `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` was not used because it changes the JSON shape and field paths (`pageable.pageNumber` → `page.number`). In all three samples, running `test --rerun` twice produces the same snippets, and the second `asciidoctor` is UP-TO-DATE
- The output verification scripts check query credential masking (`access_token=****`, WireMock `matches ".*"`). `update-docs-toc.py` doesn't put headings inside code blocks into the table of contents, and `verify-live-app.py` retries 60 times at 1-second intervals until the app is up
- An error code table in the troubleshooting document (`docs/troubleshooting.md`, with a Korean pair) (an `<a id="kdx-xxx"></a>` anchor, problem and fix per code). `scripts/update-error-codes.py` builds the table from `RestDocsErrorCode` (the fix column is written and kept by hand), and the root task `checkErrorCodes` (wired into `check`) fails when the table is out of step or a fix cell is empty
- The getting started guide starts with test method parameter injection instead of a base class (the base class is optional). Guides for the Spring HATEOAS and kotlinx.serialization extraction rules, `openApiInfo`, `html { enabled }`, and class KDoc → schema descriptions

### Changed

Behavior that differs from the development versions.

- Docs: the long `docs/build.md` was split into four (both English and Korean). `build.md` (commands, one table of Gradle plugin options and tasks, dependencies, generating on run, the startup log, production exposure, output locations), `openapi.md` (Swagger UI, `openApiInfo`, OAuth2 and OpenID Connect schemes, API versions), `checks.md` (`apiDocsCheck`, `apiDocsCoverage`), `outputs.md` (viewers, Postman, `.http`, WireMock, export and upload, multi-project aggregation). The overlapping "what each command does" list and the app address table were merged into the task table and the "View in the browser" part of the document list. Old `build.md#…` links were updated to the new pages
- KDoc/Javadoc → descriptions is experimental and off by default, and it has per-item switches (previously, with the Gradle plugin, type, property and enum constant comments and `@param` were on by default). The scanner is not a compiler, so it misses some declarations. Turn it on with `kardx { sourceDocs { enabled = true } }`; once on, `fieldDocs` (property, field, getter and record comments → descriptions of fields and of `parametersOf<T>()` and `queryParameters<T>()` parameters), `enumDocs` (enum constants), `schemaDescriptions` (class comments → OpenAPI component schema descriptions) and `paramDocs` (handler `@param` → items declared by name only or with `FromParamTag`) default to `true`, `methodDocs` (handler method summary and description) defaults to `false`, and `includeProjectDependencies` defaults to `true` (Kotlin and Groovy DSL). Items that are off are left out of `source-docs.json`, and every switch is a task input, so changing one reruns the scan and the tests. When it is off, `test` doesn't depend on `apiDocsSourceDocs` and doesn't receive the system property, running `apiDocsSourceDocs` directly skips it (`SKIPPED`), and no class descriptions go into OpenAPI schemas. Core's yml `kardx.source-docs.enabled` (`RestDocsSettings.sourceDocsEnabled`, default `true`) remains as a test-side kill switch (`false` ignores the index passed in). The error for a name-only item whose description is not found (`KDX-101`) tells you to add `@param <name>` and turn on the Gradle plugin's experimental source docs (`kardx { sourceDocs { enabled = true } }`). The Boot 3 and 4 samples use `sourceDocs { enabled = true; methodDocs = true }`, and the WebFlux sample uses `sourceDocs { enabled = true }`
- `relaxed = true` relaxes every section (including path, query and form parameters, parts, cookies and links). Previously it applied only to fields, parts and events. To relax only the bodies, use `relaxed(RelaxedSection.REQUEST_FIELDS, RelaxedSection.RESPONSE_FIELDS)`
- DSL modifiers (`means`, `isOptional`, `optional()`, `constraint`, `example`, `withDefaultValue`, `enumOf`, `ofType`, `deprecated`, `masked` and others) return a copy instead of changing the original item. Items shared as `val` constants no longer leak into other tests. A modifier whose result is not used (`AUTH isOptional true` on a line of its own) has no effect. Changing `descriptor` directly still changes the shared object
- `securitySchemes` (default an empty list) was added at the end of the `AsyncApiInfo` constructor. Calls with named arguments still compile, and code compiled against earlier development builds must be recompiled. Two authentication schemes with the same name throw `IllegalArgumentException`
- Generated OpenAPI and AsyncAPI viewer pages no longer contain source comments
- `StompTestClient.sendAndReceive` now resends at an interval that starts at 200ms and doubles up to 1 second until a frame arrives (previously every 100ms). Spring's simple broker sends no RECEIPT for SUBSCRIBE, so the subscription can't be confirmed and resending stays; the KDoc and docs now say that the payload may be sent more than once. To send only once, use `subscribe` → `send` → `await`. STOMP sessions established after the constructor's connection timeout are closed
- When the endpoint inventory (`.kardx/endpoints/`) can't be written, the documentation test doesn't fail; a warning is logged and the next documented request tries again
- `apiDocsCheck`: the report (`openapi-changes.txt`) writes the header `The OpenAPI spec matches the baseline (baseline <baseline> ↔ generated <generated>)` and `Breaking changes: 0`, `All structural differences: 0` even when there are no differences (previously it started with `differs` even with no differences). An `enum` appearing on a request parameter or field that had none is a breaking change (`Request enum added: <location> [values…]`), and a path where only path variable names changed (`/a/{id}` → `/a/{userId}`) is reported as a warning (`Path variables renamed: …`) instead of an endpoint removal, and the operation is still compared
- In the `apiDocs` location output, the OpenAPI line shown without the epages plugin tells you how to turn it on: `OpenAPI (Swagger) off (apply id("com.epages.restdocs-api-spec") version "0.20.2" to enable OpenAPI, coverage and the viewer)` (`0.19.4` for Boot 3)
- When `project.group` is not set, `apiDocsStubsJar` fails with `apiDocsStubsJar: project.group is not set, …` instead of building a jar that Stub Runner can't find (`META-INF//<name>/…`). `group` and `version` are read when the task runs, so they can be set later in the build script
- When two projects go to the same directory (the root project and `:root`), `apiDocsAggregate` fails with `apiDocsAggregate: projects : and :root both go to the directory root/ …` instead of overwriting one project's docs
- The plugin's tasks are checked with `validatePlugins { enableStricterValidation = true }`, and tasks that had no caching annotation (`apiDocsAggregate`, `apiDocsCheck`) got `@DisableCachingByDefault`
- Relative output path settings reject Windows drive paths (`C:/x`, `C:\x`) and paths starting with `\\`
- The `Date` header is removed from documented responses by default. Snippets and WireMock mappings are the same on every run, and `asciidoctor` doesn't rerun when nothing changed. Headers documented with `responseHeaders(...)` stay. To keep it, use `removedResponseHeaders = emptySet()` or `kardx.removed-response-headers: none`
- `maskSensitiveHeaders()` and `mask-sensitive-headers: true` mask not only headers but also the query parameters in `SENSITIVE_QUERY_PARAMETERS`. `?access_token=demo-token` in the three samples becomes `?access_token=****` in the snippets
- Performance: the cost of `andDocument` per request dropped by about 40% (with OpenAPI on, about 3.0 → 1.8ms). The epages JSON mapper is shared (not shared on JDK 26 and later), the snippet templates the library creates are cached, and the per-operation side files (`openapi-extras.json`, schemas, WireMock mappings) are written directly instead of atomically. Files shared by several tests are still written atomically
- The tasks that run in a second `apiDocs` with no changes went from 4 to 2: `postman` (inputs and outputs declared) and `apiDocsViewer` are UP-TO-DATE, and `apiDocsAppDocs` still runs (it refills deleted docs) but reads only one spec file.
- Because Gradle can't serialize epages' `servers` input, `openapi3` doesn't track state (`doNotTrackState`, always runs) and declares the spec file as an output. On Gradle 8 and later, user tasks that read `build/api-spec/openapi3.*` without `dependsOn("openapi3")` hit the implicit dependency check
- `kardx.bootJar.path` must be a subdirectory inside the app resources (`static/docs`, `public/api-docs` and so on). An empty value, a value that leaves the resources, or a resource root (`static`, `public`, `resources`, `META-INF/resources`) fails at configuration time
- The directories the plugin clears (`snippetsDirectory`, `asciidocDirectory`, `outputDirectory`, `aggregate.outputDirectory`) are checked first. The project, root and build directories and their parents, and paths inside or above `src/` and the asciidoc sources, are rejected; outside `build/`, a directory is written only when it is empty or has the marker file `.kardx-output` (the marker is left after writing). The default locations inside `build/` are unchanged. `apiDocsAggregate` collecting into `docs/` for GitHub Pages also needs an empty directory or the marker file the first time
- The missing-name error of extraction list editing (`except`, `optionalAt`, `optionalUnder`) gets the code `KDX-108`, and the empty extraction result warning (`found nothing to document`) gets `KDX-107`. Other messages also start with `kardx[KDX-xxx]: …` instead of `kardx: …`
- `PagedModel<T>` in the Spring Data page table is `org.springframework.data.web.PagedModel`. Spring HATEOAS `PagedModel` is extracted separately in HAL shape

### Fixed

- Table cells in Markdown-format message (`message`) and SSE (`sse-events`) snippets were empty (they were using the AsciiDoc-only `tableCellContent`)
- `apiDocsUpload`: secrets in the upload address (`user:password@` user info, query parameter values) appeared as is in logs, retry warnings and error messages. They are now masked as `****`
- On Gradle 9 and later, the plugin called a deprecated API (`Configuration.setVisible`) and caused a warning
- OpenAPI 3.0 schemas built from declared fields lacked `nullable`, so they didn't match existing component schemas of the same shape. `nullable` is now included, and existing schemas (`ErrorResponse` and so on) are reused
- `StompTestClient` didn't work on Spring Framework 7 (Boot 4)
- Declaring a non-JSON `contentType` (`text/plain`, `text/html` and so on) without fields in `errorResponse(...)` attached the common JSON error schema in OpenAPI. It now uses a `type: string` schema and a string example
- AsyncAPI viewer Try it out: the `message` header of a STOMP ERROR frame that rejected CONNECT appeared escaped in the log (`Authorization\c Bearer ...`). This is because stompjs decodes headers only after it receives CONNECTED with STOMP 1.2. The viewer now decodes headers received before version negotiation or on a 1.1 connection with the STOMP 1.2 rules (`\c`, `\n`, `\r`, `\\`) for display
- Java bean fields: getters inherited from a generic superclass that uses the same type parameter name for a different type were not resolved with the superclass's bindings (`data` of `PageResp<T> : Base<List<T>>` is now `data[]`)
- Getter properties inherited by a Kotlin class extending a Java bean were not documented
- Element fields of a class that extends a collection without type arguments (`class UserList : ArrayList<User>()`) were not documented
- `CharArray` and `char[]` were not documented as `String`
- Request field default values (Kotlin defaults) could not be read when a required constructor parameter was a `java.time` type, `UUID`, `Date`, an array or a concrete JDK collection
- (Kotest 6) The project config's extension documented before a spec that registered `RestDocsExtension` through `override val extensions`. The spec now wins (`@ApplyExtension` is not covered)
- (Kotest) The app's ObjectMapper leaked into a non-Spring spec that ran next on the same thread, and errors during cleanup hid the test's own failure
- (JUnit) A `@RepeatedTest` repetition that failed in `@BeforeEach` was not marked as failed and stayed in the document
- (boot) The static docs address and the Swagger UI address in the startup log lacked `spring.mvc.servlet.path` (as of springdoc 2.8.17 and 3.2.0)
- `apiDocsAggregate`: when a project used `apiVersioning { splitSpecs = true }`, the per-version spec (`openapi3-v1.yaml`) contained the same operations as the main spec and failed with a `GET /x: order, order` conflict. Now only the main spec of each project is merged, and per-version specs are linked from the list page
- OpenAPI post-processing turned string values like `10:30` (examples, enums, default values) into sexagesimal numbers (`630`) under YAML 1.1 rules. They are now read with YAML 1.2 number rules
- After switching `openapi3 { format }` between yaml and json, the spec of the previous format left in `build/api-spec` was used first (`apiDocsCheck`, the viewer, export, aggregation). The file of the previous format is now deleted
- Changing `layout.buildDirectory` in the build script body made epages read the copy of the snippets in the default build directory
- `coverage { exclude(...) }` patterns with path variables (`/internal/users/{id}`, `{id:\d+}`) didn't match. They now match regardless of names and regular expressions
- `apiDocsSourceDocs` silently skipped whole source files that are not UTF-8. It now reads invalid bytes as U+FFFD and logs a `Source docs: N source files are not UTF-8 (first: …)` warning once
- With `asyncApi { enabled = false }`, `apiDocsExport` and `apiDocsUpload` exported an AsyncAPI spec left from an earlier build. They now fail with `No AsyncAPI spec: kardx.asyncApi.enabled is false. Enable it, or set <option> = false to leave it out.`
- Some settings read by `openapi3` post-processing (`openApiGlobalHeaders`, `openApiCommonResponses`, `openApiCommonResponseSchema` and others) were not declared as task inputs
- The document pair check `check-docs-pairs.py` closed a code block early on a ```` ``` ```` line inside a ```` ```` ```` fence. Now only a fence of the same character that is the same length or longer closes it
- `openapi3` declared no output, so when only the spec changed, `apiDocs` and the viewer were UP-TO-DATE and kept the previous spec
- A `java.nio.file.Path` field (an Iterable whose elements are itself) caused a `StackOverflowError` during field extraction. `Path`, `File`, `URL`, `InetAddress`, `ByteBuffer`, `Class` and their subtypes (`Date` and `Calendar` subclasses such as `Timestamp` and `GregorianCalendar`) are documented as strings, as Jackson does. Endlessly nested arrays stop at `maxExtractionDepth`
- Kotlin constructor default values that change on every evaluation (random IDs, the current time) and default values of `@Masked` fields appeared in the docs. Default values are now created twice and used only when they are equal. The second time puts different placeholder values into required parameters, so default values computed from other parameters (`pageSize: Int = limit` that appeared as `default: 0`) are also dropped. Objects are created twice per class, so side effects in `init` also run twice. Default values read from environment variables or configuration are the same every time, so they can't be told apart and still appear
- `StompTestClient` waited until the timeout even on a server STOMP ERROR frame, a disconnect or a rejected CONNECT. It now fails immediately with `STOMP ERROR from server: <message>: <body>`. `sendAndReceive` exceeding the timeout or resending just before the deadline is fixed too
- (JUnit) Tests that failed only in `@AfterEach` stayed in the document
- Even with `maskSensitiveHeaders()` on, credentials in form bodies (`username=…&password=…&client_secret=…`) appeared as is in snippets, OpenAPI examples and `.http`
- `http-response` and WireMock mappings changed on every run because of the `Expires` of `ResponseCookie.maxAge(...)` cookies
- Response headers documented with REST Docs snippets, as in `snippets(HeaderDocumentation.responseHeaders(headerWithName("Date")))`, were removed first by `removedResponseHeaders` and failed with `not found in the response`
- Failures to write the endpoint inventory logged a warning and a stack trace on every request. It now retries only once and logs the stack trace only on the first failure
- `apiDocsAppDocs` synced (Sync) the `bootJar.path` directory and deleted the app's own resources. It now copies the docs and deletes only the files the plugin copied last time (`build/kardx/app-docs-files.txt`). When the path changes, the copies in the old path are deleted, and with `bootJar.enabled = false` all copies are deleted on the next `apiDocs`
- Cleanup of previous specs in `openapi3` could delete other files in the same directory. It now deletes only `<prefix>.<ext>`, `-v` version specs that start with a digit, and the files listed in `build/kardx/openapi-version-specs.txt`
- `apiDocsHttpClient` wrote values masked for documentation (`****`) as is, so `.http` requests failed authentication. Masked values of query parameters, form fields and cookies are now replaced with variables of that name (`{{access_token}}`, `{{SESSION}}`), and headers whose whole value is masked and `Basic ****` with variables named after the lowercase header name (`{{x_api_key}}`), and they are declared with empty values in `http-client.env.json` and `http-client.private.env.json`. Actual values are not written. Only the default marker `****` is recognized
- When epages `openapi3 { outputDirectory }` pointed at `build/resources/main/...`, `apiDocs` and `bootJar` failed Gradle 8's implicit dependency check (`':apiDocsIndex' uses this output of task ':openapi3'`). In that case the spec is no longer declared as an output, and when it points inside the app resources, a single warning tells you to remove `outputDirectory` (the plugin already copies the spec to `bootJar.path`)
- When a directory setting that the plugin clears pointed at an existing file, it was treated as an empty directory and the file was deleted (`aggregate { outputDirectory = file("docs/index.html") }` and so on). It now fails with `<setting> points to <path>, which is a file; it must be a directory`
- When `postmanCollection { enabled }` was set with a provider (Gradle property, environment variable) instead of a fixed value, `apiDocs` didn't run `postman`. The value is now read when the task graph is built

[Unreleased]: https://github.com/kardx-project/kardx/compare/v0.1...HEAD
[0.1]: https://github.com/kardx-project/kardx/releases/tag/v0.1
