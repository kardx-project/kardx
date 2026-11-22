**English** | [한국어](ko/kotest.md)

# Documenting with Kotest

[Document list](README.md)

<!-- toc -->
**Contents**

- [When to use](#when-to-use)
- [Dependencies](#dependencies)
  - [Dependencies by Kotest version](#dependencies-by-kotest-version)
- [Registration](#registration)
  - [Registering per spec](#registering-per-spec)
  - [Registering for the whole project](#registering-for-the-whole-project)
- [Examples by spec style](#examples-by-spec-style)
- [Naming rules](#naming-rules)
  - [Document title](#document-title)
  - [Section title](#section-title)
  - [Snippet directory name](#snippet-directory-name)
  - [Changing the rules](#changing-the-rules)
- [Same behavior as the JUnit extension](#same-behavior-as-the-junit-extension)
- [Using with Spring](#using-with-spring)
  - [MockMvc](#mockmvc)
  - [WebTestClient](#webtestclient)
  - [WebSocket · STOMP messages](#websocket--stomp-messages)
- [Enum code document](#enum-code-document)
- [index and OpenAPI](#index-and-openapi)
- [Kotest 5.x](#kotest-5x)
- [Mixing with JUnit tests](#mixing-with-junit-tests)
- [Limitations](#limitations)
<!-- /toc -->

## When to use

The JUnit extension (`AutoRestDocumentationExtension`) relies on test methods and parameter injection, so it can't be attached to Kotest specs. To document tests written with Kotest's `given`/`when`/`then`, `describe`/`it`, or `context`/`test`, use `RestDocsExtension` from `kardx-kotest`.

The DSL (`andDocument { }`), field extraction (`responseFields<T>()`), settings (`RestDocsConfiguration`, yml), and the Gradle plugin are exactly the same as with JUnit. Only the extension differs; snippet cleanup, failure handling, settings rollback, and document writing use the same code as the JUnit extension.

> **Kotest 6.x and 5.x are supported.** Each version has its own artifact (6.x `-kotest`, 5.x `-kotest5`) with the same package and public API. The repository verifies against 6.2.6 and 5.9.1 ([Dependencies by Kotest version](#dependencies-by-kotest-version), [Kotest 5.x](#kotest-5x)).

## Dependencies

### Dependencies by Kotest version

The Kotest extension has a different artifact for each Kotest version. Both artifacts have the same package (`io.github.kardx.kotest`) and the same public API (`RestDocsExtension`, `SectionTitleProvider`, `SnippetNameProvider`, `TestPath`, `TestNames`, `EnumDocumentationExtension`), so you keep your code and only choose the dependency coordinates.

**With the Gradle plugin, you don't need to choose.** If the `io.kotest` versions declared in your test dependencies (BOMs included) are all 6.x, the plugin adds `-kotest`; if all 5.x, `-kotest5`, with the plugin's version. If no version is declared (managed by another BOM) or 5 and 6 are mixed, it doesn't decide and adds nothing, so declare it yourself in that case. To turn off all automatic dependencies, use `kardx { addDependencies = false }` ([Dependencies added automatically](build.md#dependencies-added-automatically)).

| Your project's Kotest | Artifact to add | Kotest Spring extension (for Spring tests) | Verified versions |
|---|---|---|---|
| 6.x | `io.github.kardx-project:kardx-kotest` | `io.kotest:kotest-extensions-spring` (same version as Kotest) | 6.2.6 |
| 5.x | `io.github.kardx-project:kardx-kotest5` | `io.kotest.extensions:kotest-extensions-spring:1.x` (different coordinates) | 5.9.1, Spring extension 1.3.0 |

**Never put both artifacts on one classpath.** Classes with the same name would exist twice, and which one is used would be undefined. Both modules publish the same Gradle capability (`io.github.kardx-project:kardx-kotest-extension`), so if both come in through Gradle (including when another dependency pulls one in), dependency resolution fails with a capability conflict and asks you to pick one. The capability is carried in Gradle module metadata, so it has no effect in tools that read only the POM, such as Maven. If you add the artifact for the wrong line (`-kotest5` on Kotest 6.x, or the reverse), creating `RestDocsExtension` fails immediately:

```
java.lang.IllegalStateException: kardx-kotest is built for Kotest 6.x, but Kotest 5.x is on the test classpath. Use kardx-kotest5 instead (same package and API), and keep only one of the two.
```

Kotest 6.x (with the Gradle plugin):

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:6.2.6")   // the plugin adds kardx-kotest
    // For Spring tests (@SpringBootTest). Constructor injection and reading kardx.* from yml
    testImplementation("io.kotest:kotest-extensions-spring:6.2.6")
}

tasks.test { useJUnitPlatform() }
```

Kotest runs on the JUnit Platform, so `useJUnitPlatform()` is required; the Kotest Gradle plugin is not needed. The pipeline (index, HTML, OpenAPI) uses the same Gradle plugin settings as JUnit, and the index includes every spec that produced a document without any extra setup ([index and OpenAPI](#index-and-openapi)).

Kotest 5.x (with the Gradle plugin):

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")   // the plugin adds kardx-kotest5
    // For Spring tests. The 5.x Spring extension is published separately under the io.kotest.extensions group
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
}
```

Registration differs slightly from 6.x ([Kotest 5.x](#kotest-5x)).

Projects without the plugin, or that don't declare a Kotest version, add the extension themselves: `testImplementation("io.github.kardx-project:kardx-kotest:0.1")` (`-kotest5` for 5.x; it brings in core). With the BOM, omit the version. Your project decides the versions of Kotest and the Kotest Spring extension (this module has both as `compileOnly`). Verified on both Spring Boot 3.5 and 4.1.

## Registration

### Registering per spec

Register it in the spec body with `extension(RestDocsExtension())`, and pass the returned extension's `provider` to MockMvc. Create MockMvc once in the spec body; each documentation call uses the snippet slot of the test running at that moment.

Type-argument shortcuts such as `responseFields<UserResponse>()` are not members of the `andDocument` block type but extension functions in the `io.github.kardx.extractor` package. Without the import, `responseFields<T>()` doesn't compile (the IDE only finds the member `responseFields(...)`, which takes no type argument), so add the imports below. Item syntax such as `pathMeans` and `type` is in `io.github.kardx.dsl`, and `andDocument` and `restDocsMockMvc` are in `io.github.kardx.mockmvc`.

```kotlin
import io.github.kardx.dsl.pathMeans
import io.github.kardx.extractor.requestFields     // shortcut requestFields<T>()
import io.github.kardx.extractor.responseFields    // shortcut responseFields<T>()
import io.github.kardx.kotest.RestDocsExtension
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.context.WebApplicationContext

@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("User API")                    // io.kotest.core.annotation.DisplayName (the JUnit one works too)
class UserApiDocsSpec(context: WebApplicationContext) : BehaviorSpec({
    val restDocs = extension(RestDocsExtension())
    val mockMvc = restDocsMockMvc(context, restDocs.provider)

    given("a user exists") {
        `when`("fetching one") {
            then("returns the user") {
                mockMvc.perform(get("/api/v1/users/{id}", 1))
                    .andStatus(HttpStatus.OK)
                    .andDocument {
                        pathParameters("id" pathMeans "User ID")
                        responseFields<UserResponse>()
                    }
            }
        }
    }
})
```

This produces `build/generated-asciidoc/user-api-docs-spec.adoc`, with one section titled `a user exists - fetching one - returns the user`.

### Registering for the whole project

To register it for all specs, add it to the project config's `extensions` (for 5.x, `override fun extensions()`; see [Kotest 5.x](#kotest-5x)). Specs don't have the extension instance, so pass `RestDocsExtension.currentTestProvider`. This provider finds the leaf test currently running from the coroutine context. Put the project config class at `io.kotest.provided.ProjectConfig` (`io/kotest/provided/ProjectConfig.kt` in test sources), which Kotest 6 looks for by default, or, if it has another name, point to it with `kotest.framework.config.fqn=<class name>` in `src/test/resources/kotest.properties`.

```kotlin
import io.github.kardx.kotest.RestDocsExtension
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension

class ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(SpringExtension(), RestDocsExtension())
}

@SpringBootTest
class UserApiDocsSpec(context: WebApplicationContext) : FunSpec({
    val mockMvc = restDocsMockMvc(context, RestDocsExtension.currentTestProvider)

    test("Get user") { ... }
})
```

Specs without documentation calls produce neither snippets nor documents, so it's fine to register it alongside regular unit tests. Even if you register it both in a spec and in the project, each test is documented only once. In that case **the extension registered in the spec** documents that spec, and it writes the spec document (`.adoc`) exactly once. The extension in the project config skips that spec. So you can register the extension project-wide and still change `sectionTitle`, `snippetName`, or `documentGenerator` for a single spec (for example, a different `:toclevels:` for just that spec's document).

```kotlin
// The project config has RestDocsExtension(). Only this spec's document gets a two-level TOC
class OrderApiDocsSpec(context: WebApplicationContext) : FunSpec({
    val restDocs = extension(
        RestDocsExtension(
            documentGenerator = {
                object : DocumentGenerator() {
                    override fun config(): Config = Config(tocLevels = 2)
                }
            },
        ),
    )
    val mockMvc = restDocsMockMvc(context, restDocs.provider)
    // ...
})
```

An extension counts as registered in the spec when it is registered on the spec instance, as with `extension(...)` in the spec body. Extensions attached with `@ApplyExtension(RestDocsExtension::class)` or with `override val extensions` (deprecated in Kotest 6) are not recognized as registered in the spec.

`currentTestProvider` finds the current test only in the test coroutine (and its child coroutines). The `provider` of an extension registered in a spec falls back to the leaf test that extension most recently started when called from a thread outside coroutines (a `Thread` or `ExecutorService` the test created itself), but `currentTestProvider` has no such fallback and throws `IllegalStateException`. If you need to document from a new thread, use the `provider` of the extension registered in the spec, or move the work into a coroutine (`withContext(Dispatchers.IO)`).

## Examples by spec style

Each leaf test (`then`, `it`, `test`, `should`, and so on) becomes one section. Containers (`given`, `when`, `describe`, `context`) don't become sections; only their names are prepended. Prefixes added by spec styles, such as `Given: `, `When: `, `Then: `, `Describe: `, and `It: `, and the focus and disabled markers (`f:`, `!`) are not included in names.

| Style | Test | Section title | Snippet directory (under `<spec>/`) |
|---|---|---|---|
| `BehaviorSpec` | `given("a user exists") { `when`("fetched") { then("200") } }` | `a user exists - fetched - 200` | `a-user-exists-fetched-200` |
| `FunSpec` | `test("Get user")` | `Get user` | `get-user` |
| `FunSpec` | `context("List") { test("First page") }` | `List - First page` | `list-first-page` |
| `DescribeSpec` | `describe("GET /users/{id}") { it("returns user") }` | `GET /users/{id} - returns user` | `get-users-id-returns-user` |

Sections are ordered by when the tests first ran (usually declaration order).

## Naming rules

### Document title

Each spec class is one document. The title follows the same rules as a JUnit test class.

- The spec class's `@DisplayName` (Kotest `io.kotest.core.annotation.DisplayName` or JUnit `org.junit.jupiter.api.DisplayName`), or the simple class name if absent
- `@AsciiDescription`, if present, is appended after the title and becomes the description of the OpenAPI top-level `tags`
- The document file name and the first level of the snippet directory are the kebab-case simple class name (`UserApiDocsSpec` → `user-api-docs-spec.adoc`)

### Section title

The test names from the outermost container to the leaf are joined with ` - ` (`TestNames.sectionTitle`). This matches the section titles of JUnit `@Nested` tests (`Success - Get`). This value becomes the OpenAPI `summary`, the AsyncAPI message summary, and the index link text.

### Snippet directory name

The snippet directory name is the document identifier. The section anchor (`user-api-docs-spec.html#<name>`) and the WireMock mapping file name come from it. The OpenAPI `operationId` is the controller method name by default, and comes from this name (`user-api-docs-spec.<name>`) only when you chose the `test-name` rule or the handler is unknown ([index and OpenAPI](#index-and-openapi)). That's why it's built from the test name rather than a sequence number: adding or reordering tests doesn't change other tests' names.

The default rule (`TestNames.snippetName`) applies the following to each name and joins them with `-`.

1. Normalizes to Unicode NFC (composes decomposed Hangul, as produced on macOS)
2. Treats an uppercase letter following a lowercase letter or digit as a word boundary (`getUser` → `get-user`, the same result as REST Docs' kebab-case)
3. Converts everything to lowercase
4. Replaces each run of characters that are not letters (including Hangul), digits, or `_` (spaces, `/`, `.`, `:`, parentheses …) with a single `-`, and trims leading and trailing `-`

| Test name | Directory |
|---|---|
| `Get user (success)` | `get-user-success` |
| `GET /api/v1.2/users` | `get-api-v1-2-users` |
| `calling the getUser API` | `calling-the-get-user-api` |

If it exceeds 150 bytes in UTF-8, it's truncated and an 8-character hash of the full name is appended (`…-3f2a9c1b`). The hash is derived from the name, so it's the same on every run.

JUnit uses backtick method names (`` fun `get user`() ``) as directories exactly as REST Docs does (spaces kept) and only tidies the anchor, because the JVM forbids characters such as `/`, `.`, and `:` in method names. Kotest names have no such restriction, so the anchor rule (`DocumentGenerator.asciidocAnchor`) is applied to directories too, and `.` and `:`, which cause problems in paths, are replaced as well.

When two tests in the same spec end up with the same name, the later one gets `-2`, `-3`, and a warning is logged. The number follows the order in which the tests first started in that run. So if you run only the later test in the IDE, it gets the unnumbered name (`…-a`), and its snippet directory and anchor (and operationId with the `test-name` rule) differ from the full run (`…-a-2`). Rename the tests so they don't collide.

```text
WARNING: kardx[KDX-403]: tests 'List / same name (a)' and 'List / same name [a]' in com.example.UserApiDocsSpec
map to the same snippet directory 'list-same-name-a'. Using 'list-same-name-a-2' for the second one; ...
```

### Changing the rules

Change them with constructor arguments: `sectionTitle` (`SectionTitleProvider`), `snippetName` (`SnippetNameProvider`) and `documentGenerator` (`DocumentGeneratorFactory`). The rules receive a `TestPath`: `names` is the list of test names (outermost container → leaf, without style prefixes and focus or disabled markers) and `specClass` is the spec class. The defaults are `SectionTitleProvider.DEFAULT` and `SnippetNameProvider.DEFAULT`.

```kotlin
val restDocs = extension(
    RestDocsExtension(
        sectionTitle = { test -> test.names.last() },                         // leaf name only
        snippetName = { test -> test.names.drop(1).joinToString("-") { TestNames.slug(it) } },
        documentGenerator = { object : DocumentGenerator() { override fun config() = Config(tocLevels = 2) } },
    ),
)
```

The value returned by `snippetName` is also tidied with the four steps above between each `-` so it can be used in a path (`test` if empty). `documentGenerator` is the same as the `documentGenerator` argument of the JUnit extension ([AsciiDoc documents](asciidoc.md)).

Constructor arguments accept plain lambdas as above. To keep a rule in a variable or property, use the SAM constructor, as in `val leafOnly = SectionTitleProvider { test -> test.names.last() }`.

## Same behavior as the JUnit extension

| Behavior | Kotest |
|---|---|
| Deleting previous snippets | When a leaf test first writes to its directory in the current run |
| Failed tests | Snippets are deleted and the test is left out of the document and OpenAPI (even if it failed after documenting inside the test) |
| Tests without snippets | Not included in the document (validation tests that don't document, skipped tests) |
| Rolling back `configure` | `RestDocsConfiguration.configure` calls made inside a leaf test and its `beforeEach`, `beforeTest`, `afterEach`, `afterTest` (in the test coroutine and its child coroutines) have only their block removed when the test ends. Settings made in the spec body and `beforeSpec` are kept (same as JUnit `@BeforeAll`). For isolation modes, see the note below |
| Document settings | The document is written with the settings in effect right before the test body starts, after all user `beforeEach` and `beforeTest` have finished (Kotest `BeforeInvocationListener`; the same point as after JUnit's `@BeforeEach`). `configure` calls in the test body don't go into the document, and those of the last leaf test run are used |
| Writing the document | When the spec finishes. If isolation mode creates several spec instances, the document is rewritten with the tests so far each time an instance finishes, so the final document contains them all |
| Document name collisions | Warns when specs or test classes with the same name in different packages write the same document |

Verified that document sections and snippet directory names come out the same in `IsolationMode.SingleInstance` (default), `InstancePerRoot`, `InstancePerLeaf`, and `InstancePerTest`.

**In isolation modes, configure in `beforeEach` instead of the spec body or `beforeSpec`.** Isolation modes (`InstancePerRoot`, `InstancePerLeaf`, `InstancePerTest`) create several spec instances and rerun the spec body and `beforeSpec` for each instance. A `configure` there doesn't belong to a test and isn't rolled back, so it piles up once per instance (for example, with `document.commonIncludeFiles += "x.adoc"`, the document gets `include::x.adoc[]` several times). Settings in `beforeEach` are added per test and removed when it ends, so they don't pile up. If the value is the same for all specs, set it once in the project config (`beforeProject`) or yml.

```kotlin
class UserApiDocsSpec : FunSpec({
    isolationMode = IsolationMode.InstancePerRoot

    beforeEach { RestDocsConfiguration.configure { document.commonIncludeFiles = listOf("user.adoc") } }
    ...
})
```

## Using with Spring

When you apply the Kotest Spring extension (6.x `io.kotest:kotest-extensions-spring`, 5.x `io.kotest.extensions:kotest-extensions-spring`) to a spec (6.x `@ApplyExtension(SpringExtension::class)` or the project config; for 5.x see [Kotest 5.x](#kotest-5x)), `RestDocsExtension`, for each leaf test, uses that context to

- read the `kardx.*` settings from yml and `@TestPropertySource`
- use the app's Jackson mapper bean to serialize document examples

If the Spring extension isn't on the classpath or isn't applied to the spec, only code settings (`RestDocsConfiguration.configure`) are used. A missing class doesn't cause an error.

Code settings made in `beforeEach`, like JUnit's `@BeforeEach`, apply to each test and are rolled back when the test ends. The sample [UserSearchDocsSpec](../samples/spring-boot-4/src/test/kotlin/com/example/restdocs/kotest/UserSearchDocsSpec.kt) does this.

```kotlin
beforeEach { DocsConfig.apply() }   // RestDocsConfiguration.configure { ... }
```

Examples of moving JUnit documentation tests to Kotest are in the Boot 4 sample's [kotest package](../samples/spring-boot-4/src/test/kotlin/com/example/restdocs/kotest). There is one `FunSpec` per JUnit class (`@Nested` → `context`, `@ParameterizedTest` → `withTests`, including STOMP, WebSocket, and SSE), and the test names match the JUnit `@DisplayName`s, so the section titles are the same. The JUnit versions already document the same APIs, so these specs call `MirrorDocsConfig.apply()` in `beforeEach` to write their documents to a different directory (`output.asciidocDirectory`) and not produce OpenAPI, AsyncAPI, or WireMock fragments. They register the extension in the spec with the same document generator as the JUnit versions (two-level TOC), so their document headers match the JUnit documents even though the project config also registers an extension.

```kotlin
val restDocs = extension(MirrorDocsConfig.restDocsExtension())   // RestDocsExtension(documentGenerator = { SampleDocumentGenerator() })
val mockMvc = restDocsMockMvc(context, restDocs.provider)

beforeEach { MirrorDocsConfig.apply() }   // DocsConfig.apply(), then another output.asciidocDirectory and OpenAPI, AsyncAPI, WireMock off
```

### MockMvc

```kotlin
val mockMvc = restDocsMockMvc(context, restDocs.provider)
```

The `MockMvcTester` and MockMvc Kotlin DSL (`mockMvc.get { }`) entry points are the same as with JUnit ([Documenting HTTP APIs](http-api.md)).

### WebTestClient

```kotlin
class ProductApiDocsSpec(context: ApplicationContext) : FunSpec({
    val restDocs = extension(RestDocsExtension())
    val client = restDocsWebTestClient(context, restDocs.provider)

    test("Get product") {
        client.get().uri("/api/v1/products/{id}", 1)
            .exchange()
            .expectStatus().isOk
            .andDocument { responseFields<ProductResponse>() }
    }
})
```

### WebSocket · STOMP messages

```kotlin
test("Send a chat message") {
    MessageDocumentation(restDocs.provider).send("/app/chat.send", ChatSendRequest(roomId = 1, content = "Hello")) {
        description = "Sends a message to the chat room."
        fields<ChatSendRequest>()
    }
}
```

The AsyncAPI message summary is the section title, and the tag is the document title ([SSE · WebSocket · AsyncAPI](messaging.md)).

## Enum code document

Register `EnumDocumentationExtension` in a spec or the project config. It produces the same document with the same code as the JUnit extension of the same name, and the same package and output file are written only once per test run. To change the format, pass an `EnumDocumentFormat` instance to `format` ([Enum code document format](customizing-snippets.md#enum-code-document-format)).

```kotlin
class ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(
        SpringExtension(),
        RestDocsExtension(),
        EnumDocumentationExtension(packageName = "com.myapp", title = "Code definitions"),
    )
}
```

## index and OpenAPI

The Gradle plugin's `apiDocsIndex` includes, without any setup, every spec and JUnit test class that produced a document. It reads the records left when spec documents are written, so it doesn't scan the classpath, and you no longer need `baseClass = "io.kotest.core.spec.Spec"`. Entries are ordered by `@DisplayName`.

If you set both `index.packageName` and `index.baseClass`, only test classes in that package that extend `baseClass` and **Kotest specs in the same package** are included (Kotest specs can't extend a JUnit base class). Even then, only specs that produced a document become entries.

The OpenAPI tag is the spec's `@DisplayName`, or the simple class name if absent (the same as the document title and the same rule as JUnit), and `summary` is the section title ([OpenAPI tags and summaries](http-api.md#openapi-tags-and-summaries)). `operationId` is decided by the same rules as JUnit ([operationId](http-api.md#operationid)): by default it's the name of the controller method that handled the request (`getUser`), so the id is ASCII even when test names are in Korean. Set the id of a single test directly with `openApi { operationId = "..." }`.

```kotlin
then("returns the matching users") {
    mockMvc.perform(get("/api/v1/users/search").param("nickname", "Hong Gildong"))
        .andDocument {
            responseFields<List<UserResponse>>()
            openApi { operationId = "searchUsersByNickname" }
        }
}
```

If you chose the `test-name` rule (`kardx.open-api.operation-id-rule: test-name`), or for requests whose handler is unknown (functional routers, WebTestClient bindings where the server side isn't visible), the id becomes `<spec>.<snippet directory name>`, and Korean characters stay as they are. A Korean operationId is valid in an OpenAPI spec and Swagger UI shows it fine, but client code generators (OpenAPI Generator and others) often strip non-ASCII characters when building method names. The test-name part then disappears (`user-api-docs-spec.사용자-조회` → `userApiDocsSpec`) or several operations end up with the same name, so generation fails or numbers are appended. Fixes: (a) use `openApi { operationId = "..." }` on that test, or (b) switch the rule to `handler-method` (default) or `path` (`GET /users/{id}` → `getUsersById`, ASCII even without the handler).

## Kotest 5.x

`kardx-kotest5` is the same extension compiled against the Kotest 5.x API. Naming rules, exclusion of failed tests, settings rollback and the capture point (after `beforeEach`, before the body), and document and OpenAPI results are the same as 6.x. The same tests as for the 6.x version (BehaviorSpec, FunSpec, DescribeSpec, Korean names, isolation modes, project registration, working without the Spring extension, and so on) are run on the Kotest 5.9.1 engine to verify this.

**Registration.** `extension(RestDocsExtension())` in the spec body is the same as 6.x. Other registrations use the 5.x API.

```kotlin
// Project config: in 5.x, extensions is a function (in 6.x, val extensions)
class ProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension, RestDocsExtension())
}

// When registering with the function in a spec class, keep the instance in a property and return the same one
@SpringBootTest
class UserApiDocsSpec(context: WebApplicationContext) : FunSpec({
    val mockMvc = restDocsMockMvc(context, RestDocsExtension.currentTestProvider)

    test("Get user") { ... }
}) {
    private val restDocs = RestDocsExtension()

    override fun extensions(): List<Extension> = listOf(restDocs)
}
```

5.x fetches the list again every time it calls `extensions()`. If you create a new one each time, as in `override fun extensions() = listOf(RestDocsExtension())`, the instance that documented the tests differs from the one called when the spec finishes, so snippets are produced but the spec document (`.adoc`) isn't written.

**Spring extension.** In the 5.x Spring extension (`io.kotest.extensions:kotest-extensions-spring:1.3.0`), `SpringExtension` is a value, not a class, so instead of `@ApplyExtension(SpringExtension::class)`, add it to the project config or the spec's `extensions()`. For constructor injection (`class Spec(context: WebApplicationContext)`), add `SpringAutowireConstructorExtension` too. Applied this way, yml `kardx.*` and the app's Jackson mapper are read exactly as in 6.x. The project config class is found at `io.kotest.provided.ProjectConfig`, as in 6.x.

```kotlin
class ProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension, SpringAutowireConstructorExtension)
}
```

**Differences from 6.x.**

| Item | 6.x | 5.x |
|---|---|---|
| Isolation modes | `InstancePerRoot`, `InstancePerLeaf`, `InstancePerTest` | `InstancePerLeaf`, `InstancePerTest` (5.x has no `InstancePerRoot`) |
| Data tests (`withData`) | Rows are split into leaves (`TestType.Test`) and containers | Rows are `TestType.Dynamic`, so whether they're leaves isn't known in advance, and they're treated as leaves (same as the 5.x Spring extension). With nested `withData`, inner rows go into one section of the outer row |
| Project config registration | `override val extensions` | `override fun extensions()` |
| Spec class registration | `@ApplyExtension`, `override val extensions` | `override fun extensions()` (instance in a property) |
| Spring extension | `io.kotest:kotest-extensions-spring:6.x`, `@ApplyExtension(SpringExtension::class)` | `io.kotest.extensions:kotest-extensions-spring:1.x`, the `SpringExtension` value in `extensions()` |
| Public API | — | The same. The 5.x listener interfaces have a `name` property, so the binary API dump shows an extra `getName()` |

## Mixing with JUnit tests

You can have JUnit tests and Kotest specs in one project. Both run on the JUnit Platform, so a single `test` task runs them, and their documents are created side by side in the same `build/generated-asciidoc`. They also go into the index, HTML, OpenAPI, and AsyncAPI together. The samples (`samples/spring-boot-3`, `samples/spring-boot-4`) use several JUnit tests alongside MockMvc Kotest specs (including project-wide registration), and `samples/spring-boot-4-webflux` uses a WebTestClient Kotest spec (`ProductSummaryDocsSpec`).

- If you add `kotest-runner-junit5` with a version to `testImplementation`, the Gradle plugin adds core and the Kotest module for that line (`-kotest` or `-kotest5`) (without the plugin or without a declared version, add them yourself)
- Settings are shared within one JVM. On either side, `configure` calls made inside a test are rolled back when the test ends
- JUnit classes and Kotest specs with the same simple name produce colliding document names, so a warning is logged
- If you document the same API in both a JUnit test and a Kotest spec, OpenAPI merges them into one operation with the same path and method (operationId unchanged) and just adds another response example. AsyncAPI adds an example to the same message (one if the content is the same), and the index gets two documents. To publish only one side, in that side's `beforeEach` set `output.asciidocDirectory` to a different directory and `openApi.enabled`, `asyncApi.enabled`, and `output.wireMockStubs` to `false` (`MirrorDocsConfig` in the Boot 4 sample). Tests that change server state (sequence numbers and so on) share the same Spring context, so the example values of whichever side runs later may differ (`@DirtiesContext`)

## Limitations

- **Add only the one artifact that matches your Kotest version.** 5.x and 6.x differ in the test name property (5.x `testName`, 6.x `name`) and the package of the test result class (5.x `io.kotest.core.test.TestResult`, 6.x `io.kotest.engine.test.TestResult`), so one binary can't support both. A mismatched artifact fails immediately with `IllegalStateException` when `RestDocsExtension` is created ([Dependencies by Kotest version](#dependencies-by-kotest-version)). Kotest 4.x and earlier are not supported
- **Running tests within one spec in parallel is not recommended** (`concurrency`, `testExecutionMode`). Each test removes only the `configure` it added, so tests don't erase each other's settings, but the settings are a single JVM-wide value, so concurrently running tests' settings get mixed, and the "last test" used for the document and the section order change from run to run. Providers are distinguished per test by the coroutine context
- The limitation when running specs in parallel (documenting concurrently with different `configure` or yml values gets mixed) is the same as JUnit ([Parallel execution](configuration.md#parallel-execution))
- Documenting outside a leaf test (spec body, container blocks) throws `IllegalStateException: kardx[KDX-401]: no Kotest leaf test is running`. Make requests and call `andDocument` inside `then`, `it`, or `test`
- If you create a new thread yourself in a test (`Thread`, `ExecutorService`) and document from it, `currentTestProvider` can't find the current test, and the spec's `provider` falls back to the most recently started test ([Registering for the whole project](#registering-for-the-whole-project)). `configure` calls made on such a thread don't belong to a test either and aren't rolled back. Coroutines (`withContext(Dispatchers.IO)`, `launch`) inherit the context, so they work
