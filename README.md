**English** | [한국어](README.ko.md)

# kardx

[![CI](https://github.com/kardx-project/kardx/actions/workflows/ci.yml/badge.svg)](https://github.com/kardx-project/kardx/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kardx-project/kardx?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.kardx-project/kardx)
[![Docs](https://img.shields.io/badge/docs-kardx--project.github.io-7c4dff)](https://kardx-project.github.io/kardx/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![JDK](https://img.shields.io/badge/JDK-17%2B-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2%2B-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5%20%7C%204.x-6DB33F?logo=springboot&logoColor=white)

*Kotlin Auto Rest Docs eXtension* — a library that makes [Spring REST Docs](https://docs.spring.io/spring-restdocs/reference/) easy to use from Kotlin.

**Documentation website: [kardx-project.github.io/kardx](https://kardx-project.github.io/kardx/)** ([한국어](https://kardx-project.github.io/kardx/ko/))

Put `@Description` on your DTOs and the field documentation comes from the types; when the tests finish, the AsciiDoc documents are assembled for you. The same tests also produce OpenAPI (Swagger UI) and AsyncAPI (WebSocket, STOMP, SSE) specs.

```kotlin
mockMvc
    .perform(get("/api/users/{id}", 1L))
    .andStatus(HttpStatus.OK)
    .andDocument {
        description = "Gets a user by ID."
        pathParameters("id" pathMeans "User ID")
        responseFields<UserResponse>()
    }
```

## What you get

`./gradlew apiDocs` collects everything in `build/api-docs/`, and when you run the app (executable jar, `bootRun`, IntelliJ run) you browse it under `/docs/`. IDE runs and `bootRun` also log the addresses of the documents that exist when the app starts (the executable jar only with `startupLogScope = "runtimeOnly"`).

| Output | What it is | Default | How to turn it on | App address | Details |
|---|---|---|---|---|---|
| REST Docs HTML | One document per test class (request and response examples, field tables) plus an index listing them all | On | `org.asciidoctor.jvm.convert` plugin | `/docs/index.html` | [AsciiDoc documents](docs/asciidoc.md) |
| OpenAPI spec | An `openapi3.yaml` verified by the tests | Off | `com.epages.restdocs-api-spec` plugin (this library's Gradle plugin adds the test dependency) | `/docs/openapi3.yaml` | [OpenAPI](docs/openapi.md#openapi--swagger-ui) |
| Swagger UI | A page that shows the spec above and lets you call the API | Off | springdoc dependency in the app and `springdoc.swagger-ui.url: /docs/openapi3.yaml` | `/swagger-ui/index.html` | [Getting started](docs/getting-started.md#4-applicationyml-production) |
| OpenAPI static viewer | A single HTML file built with Redoc or Scalar. Opens without a server and can be handed over as a file | Off | `kardx { openApiViewer { enabled = true } }` | `/docs/openapi.html` | [OpenAPI viewer](docs/outputs.md#openapi-viewer-redoc--scalar) |
| AsyncAPI document and viewer | WebSocket, STOMP and SSE message spec with a Try it out viewer | Generated when you document messages | `MessageDocumentation`, `events(...)` | `/docs/asyncapi.html` | [SSE · WebSocket · AsyncAPI](docs/messaging.md) |
| Enum code document | Table of codes and descriptions for `@EnumCode` enums | Off | Register `EnumDocumentationExtension` | `/docs/code/enum-code.html` | [Enum code documents](docs/asciidoc.md#enum-code-document) |
| Postman Collection | A collection that sends the documented requests straight from Postman | Off | `postmanCollection { enabled = true }` | `/docs/postman-collection.json` | [Postman](docs/outputs.md#postman-collection) |
| IntelliJ HTTP Client | An `.http` file with the documented requests, sent from the IntelliJ editor | Off (needs OpenAPI) | `httpClient { enabled = true }` | `/docs/http-client/api.http` | [IntelliJ HTTP Client](docs/outputs.md#intellij-http-client) |
| Documentation coverage | The endpoints the app maps that no test documents | On (needs OpenAPI) | `coverage { }` | None (`build/api-docs/coverage/`) | [Documentation coverage](docs/checks.md#documentation-coverage) |
| WireMock stub | WireMock mappings and a stub jar that imitate the documented responses | Off | `wireMock { enabled = true }` | None (`build/generated-snippets/wiremock/`) | [WireMock stub](docs/outputs.md#wiremock-stub) |

`openApiViewer`, `postmanCollection`, `httpClient`, `coverage` and `wireMock` go inside the Gradle plugin setting `kardx { }`. All file locations are listed in [Output locations](docs/build.md#output-locations).

## Optional features

Features that are off by default or that you use only when needed.

- **OpenAPI change comparison** (`./gradlew apiDocsCheck`): compares a committed baseline spec with the new spec and reports backward-incompatible changes. You can add it to `check` in CI ([Comparing OpenAPI changes](docs/checks.md#comparing-openapi-changes)).
- **Failing on undocumented endpoints** (`coverage { failOnUndocumented = true }`): `apiDocsCoverage` always reports endpoints without a documentation test; with this setting it fails the build, for example only in CI ([Documentation coverage](docs/checks.md#documentation-coverage)).
- **OAuth2 and OpenID Connect** (`openApi { oauth2("users:read") }` with `openApiSecurity { }` in the build): operations require scopes, and Swagger UI shows the OAuth2 login ([OAuth2 and OpenID Connect](docs/http-api.md#oauth2-and-openid-connect)).
- **API versioning** (Spring Framework 7, `apiVersioning { }`): handler versions become `x-api-version` and version parameters, in one spec or one spec per version ([API versioning](docs/openapi.md#api-versioning)).
- **Exporting and uploading specs** (`./gradlew apiDocsExport`, `apiDocsUpload`): copy the generated specs to a directory, upload them over HTTP to an API registry, or, with `export { publishSpecs = true }`, attach them to a `maven-publish` publication as `<name>-<version>-openapi.yaml` ([Exporting specs](docs/outputs.md#exporting-specs)).
- **Declared error responses** (`errorResponse(409, "...")` in the block): document 400, 401, 403, 409 and other errors without writing a test for each. They go into that test's document (an `Error Responses` table with fields and example) and its OpenAPI operation. Declared, not verified ([Declared error responses](docs/http-api.md#declared-error-responses)).
- **Common error responses** (`openApiCommonResponses`): declare responses you don't test, such as 401 and 500, once, and they are added to each OpenAPI operation and to a table in the index. It's content not verified by a test, so it's off by default ([Common error responses](docs/http-api.md#common-error-responses)).
- **Aggregating multiple projects** (`./gradlew apiDocsAggregate`): collects the documents of subprojects into one listing page and a merged OpenAPI ([Aggregating multiple projects](docs/outputs.md#aggregating-multiple-projects)).
- **OpenAPI info and tags** (`openApiInfo { }`): description (or a Markdown file), contact, license, terms of service, external docs, tag order and descriptions, and `x-tagGroups` for Redoc and Scalar ([OpenAPI info and tags](docs/openapi.md#openapi-info-and-tags)).
- **Skipping the HTML conversion locally** (`html { enabled = false }` or `-Pkardx.html=false`): `asciidoctor` takes most of the doc build time; OpenAPI and the other outputs are still built ([Build and outputs](docs/build.md#output-locations)).
- **OpenAPI static viewer**, **Postman Collection**, **IntelliJ HTTP Client**, **WireMock stub**: see the table above.
- **WebFlux and WebTestClient, RestTestClient**: attach the same `andDocument { }` to WebTestClient, or to RestTestClient on Boot 4 ([WebTestClient · RestTestClient](docs/webtestclient.md)).
- **AsyncAPI authentication** (`asyncApi { bearerAuth() }`, `basicAuth()`, `apiKeyHeader(...)`): the spec declares how the server authenticates, and the viewer's Try it out sends the credential ([Authentication in AsyncAPI](docs/messaging.md#authentication-in-asyncapi)).
- **Kotest 6 and 5**: document with `given`/`when`/`then` specs. Kotest 5.x uses the `-kotest5` artifact ([Kotest](docs/kotest.md)).
- **Customizing snippets and document layout**: table columns, subheadings, table of contents position, custom snippets ([Customizing snippets and document layout](docs/customizing-snippets.md)).

## Features

- **Kotlin DSL**: you write an `andDocument { }` block with infix functions such as `"id" pathMeans "User ID"`. The same block works with `mockMvc.perform`, the MockMvc Kotlin DSL (`mockMvc.get { }`), `MockMvcTester`, WebTestClient (WebFlux apps) and RestTestClient (Boot 4) ([WebTestClient · RestTestClient](docs/webtestclient.md)). Modifiers return copies, so shared entries in `val` constants are safe.
- **Error codes**: library errors and warnings start with a stable code such as `kardx[KDX-011]` and link to its fix in [Troubleshooting](docs/troubleshooting.md#error-codes).
- **Failures that explain the cause**: when the documentation and the actual response differ, the message shows the missing fields, DSL lines you can paste, and causes such as empty arrays, naming strategies or wrappers. `describe()` previews the extraction result without running a test.
- **Field extraction from types**: `responseFields<Page<User>>()` (Spring Data `Page`, Spring HATEOAS HAL models and kotlinx `@Serializable` classes included; use `responseFieldsOf<T>()` when you need a list) produces paths, types, required flags, descriptions and constraints. It follows Jackson naming rules, and REST Docs verifies the result against the actual response. Besides `@Description`, descriptions are also read from existing annotations such as Swagger `@Schema` (yml `description-annotations`) and, with the Gradle plugin's `sourceDocs { enabled = true }` (experimental, opt-in), from KDoc and Javadoc. Controller method comments can also give operations their OpenAPI summary and their description (OpenAPI and HTML) with `methodDocs = true`, and a method's `@param` tags describe parameters the test declares by name only, such as `pathParameters("id")` ([Descriptions from KDoc/Javadoc](docs/http-api.md#descriptions-from-kdocjavadoc)).
- **Marking masked fields**: fields annotated with `@Masked("middle 4 digits as *")` or your company's masking annotation (yml `masked-annotations`) are marked `Masked` in the documentation tables and `x-masked` in OpenAPI and AsyncAPI. This is a documentation marker; values are not changed ([Marking masked fields](docs/http-api.md#marking-masked-fields)).
- **JUnit and Kotest**: JUnit 5 and 6 test classes are documented with `AutoRestDocumentationExtension`, Kotest specs (`given`/`when`/`then`, `describe`/`it`, and so on) with `RestDocsExtension` (`kardx-kotest` for Kotest 6.x, `kardx-kotest5` for 5.x). You can mix both in one project, and their documents go into the same index and OpenAPI ([Kotest](docs/kotest.md)).
- **Automatic document assembly**: each test class becomes one document, and the documents are tied together by an index. Enums annotated with `@EnumCode("Product status")` become a code / description table document.
- **SSE · WebSocket · STOMP**: documents and an AsyncAPI spec are built from the messages actually exchanged. `StompTestClient` connects, subscribes, sends and waits for the frame in the test ([StompTestClient](docs/messaging.md#sending-and-receiving-with-stomptestclient)).
- **Readable operationId**: the OpenAPI operationId defaults to the controller method name (`getUser`), so code generators can use it even when test names are in Korean. Change it with `openApi { operationId = "..." }` or a rule setting (`open-api.operation-id-rule`) ([operationId](docs/http-api.md#operationid)).
- **Accurate OpenAPI**: extracted types and Bean Validation constraints become schema keywords such as `integer`/`int64`, `date-time`, `minLength` and `maximum`; `withDefaultValue`, Kotlin default values and `example` become `default` and `example`; sealed and `@JsonSubTypes` types become `oneOf` (with a discriminator when `@JsonTypeInfo` names the subtypes); multipart parts and cookies become the request body and `in: cookie` ([What goes into the OpenAPI spec](docs/http-api.md#what-goes-into-the-openapi-spec)).
- **Gradle plugin and yml settings**: a single plugin adds the library dependencies, generates HTML, OpenAPI and AsyncAPI, and packages them into the executable jar. You change settings in `application.yml`.
- **Same request path as the real app**: `restDocsMockMvc` uses the app's Jackson mapper as is. The Spring Security filter chain is applied when you turn on `apply-spring-security: true` (it is not applied by default, because some projects authenticate in other ways, such as interceptors).
- **Optional masking**: if you want, header values such as tokens and cookies are masked. Nothing is masked by default.
- **English output by default, Korean with one setting**: the texts the library puts into the documents (list headings, the index link, the constraints prefix, the viewer UI and so on) are English; `kardx { language = "ko" }` switches them to Korean ([Documentation language](docs/configuration.md#documentation-language)).

## Compatibility

| Spring Boot | Spring REST Docs | Jackson | epages (OpenAPI, optional) | Test clients |
|---|---|---|---|---|
| 3.5.x | 3.0.x | 2.x | 0.19.x | MockMvc, WebTestClient |
| 4.0.x, 4.1.x | 4.0.x | 3.x | 0.20.x | MockMvc, WebTestClient, RestTestClient |

| Tool | Supported | Version this repository builds and verifies with |
|---|---|---|
| JDK | 17 or later | 17 (library and plugin bytecode 17) |
| Kotlin | 2.2 or later | Compiled with 2.4.21; metadata and stdlib 2.2.21 |
| Gradle | 7.6 or later (the plugin fails right away below that). The Kotlin DSL examples use `=` on plugin settings, which needs Gradle 8.2; on 7.6 to 8.1 use `.set(...)` or the Groovy DSL. Your Spring Boot version may need more (Spring Boot 4: 8.14 or later) | Wrapper 8.14.8. The plugin's core scenarios and spec export and upload (`apiDocsExport`, `apiDocsUpload`) also run on 7.6.4, 9.0.0 and 9.8.2 |
| Maven | With the Maven plugin `io.github.kardx-project:kardx-maven-plugin` ([Maven and the command line](docs/maven.md)) | 3.9.11 (Maven Wrapper of `samples/maven-boot-4`) |
| Asciidoctor Gradle plugin (`org.asciidoctor.jvm.convert`) | 4.0.x | 4.0.5 |
| epages (`com.epages.restdocs-api-spec`) | Boot 3 → 0.19.x, Boot 4 → 0.20.x (the plugin warns on a mismatch) | 0.19.4, 0.20.2 |
| Kotest | 6.x → `kardx-kotest`, 5.x → `-kotest5` (the plugin picks one based on the declared Kotest version) | 6.2.7, 5.9.1 |
| JUnit | 5 and 6 | 5.14.4 |

You write documentation tests in Kotlin. The DSL is built from infix functions, reified type arguments and lambdas with receivers, so it cannot be called usefully from Java. Your app code and DTOs can be Java: Java record and bean properties and Java annotations (`@Description`, Bean Validation, nullability, and so on) are read ([Extraction rules](docs/http-api.md#extraction-rules)).

You need MockMvc, WebTestClient or (Boot 4 only) RestTestClient tests ([RestTestClient](docs/webtestclient.md#resttestclient)). There is one artifact regardless of the Boot version, and the REST Docs version is decided by your project's Spring Boot BOM.

The Gradle plugin works with the Kotlin and Groovy DSL. Maven has its own plugin (index, refined OpenAPI, viewer, coverage, `.http`, Postman, spec check), and the same steps run from a command line tool for any other build ([Maven and the command line](docs/maven.md)). A few features stay Gradle only (WireMock stub jar, spec export and upload, aggregation); what each setup gets is in [Supported build setups](docs/getting-started.md#supported-build-setups).

## Quick start

Artifacts are published to Maven Central under `io.github.kardx-project`. The Gradle plugin is published there too, so add `mavenCentral()` to the `pluginManagement` repositories as well.

`settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

`build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("com.epages.restdocs-api-spec") version "0.20.2"      // only when you generate OpenAPI
    id("io.github.kardx-project") version "0.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }
```

The plugin adds what your project needs: the library (annotations and core, plus the matching extension if you use Kotest), the REST Docs MockMvc or WebTestClient module, the epages module (when `com.epages.restdocs-api-spec` is applied), and the startup log module. The index and OpenAPI settings also work with their defaults ([Dependencies added automatically](docs/build.md#dependencies-added-automatically); for use without the plugin, see [Getting started](docs/getting-started.md#dependencies-without-the-plugin)).

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@DisplayName("User API")
class UserApiDocsTest {

    @Test
    @DisplayName("Get user")
    fun getUser(mockMvc: MockMvc) { // the extension passes a REST Docs MockMvc
        mockMvc
            .perform(get("/api/users/{id}", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument { responseFields<UserResponse>() }
    }
}
```

Run it:

```bash
./gradlew apiDocs     # runs the tests and builds build/api-docs/ (prints file:/// links to open)
./gradlew bootRun     # starts the app after generating the docs: http://localhost:8080/docs/index.html
./gradlew bootJar     # executable jar with the docs inside, served at /docs/
```

Which command to use for CI, the baseline check, exporting specs or `clean` is in [Which command when](docs/build.md#which-command-when); ready-made GitHub Actions and GitLab CI setups are in [CI recipes](docs/ci.md).

Running `./gradlew apiDocs` creates the HTML (`index.html` links every test class that produced a document) and `openapi3.yaml` (plus `asyncapi.html` if you documented messages) in `build/api-docs/` and prints their locations. `./gradlew bootRun` and IntelliJ runs generate the documents automatically first, and when the app starts they log the document addresses. The full walkthrough, including the directory layout, yml and index template, is in [Getting started](docs/getting-started.md). Documents packaged into the executable jar are also served by the production app, so check [Production exposure](docs/build.md#production-exposure).

## Documentation

The guides below are also published as a website with search and an English/Korean switcher: **[kardx-project.github.io/kardx](https://kardx-project.github.io/kardx/)**.

| What you want to do | Where to look |
|---|---|
| Install and create your first document | [Getting started](docs/getting-started.md) |
| Document requests and responses, automatic field extraction, response wrappers, error responses | [Documenting HTTP APIs](docs/http-api.md) |
| Document a WebFlux app (WebTestClient) or use RestTestClient | [WebTestClient · RestTestClient](docs/webtestclient.md) |
| Document with Kotest specs | [Kotest](docs/kotest.md) |
| SSE, WebSocket, STOMP and AsyncAPI | [SSE · WebSocket · AsyncAPI](docs/messaging.md) |
| yml and code settings, header masking | [Configuration](docs/configuration.md) |
| Gradle plugin, tasks, output locations, startup log, production exposure | [Build and outputs](docs/build.md) |
| OpenAPI and Swagger UI, info and tags, API versions | [OpenAPI and Swagger UI](docs/openapi.md) |
| Spec change check (`apiDocsCheck`) and documentation coverage | [Spec check and coverage](docs/checks.md) |
| Static viewer, Postman, IntelliJ HTTP Client, WireMock, export and upload, aggregation | [Viewer, clients, stubs and sharing](docs/outputs.md) |
| Maven plugin and the command line tool | [Maven and the command line](docs/maven.md) |
| Run the checks in CI, comment on pull requests, publish the docs | [CI recipes](docs/ci.md) |
| Generated documents, index, Enum code documents | [AsciiDoc documents](docs/asciidoc.md) |
| Change table columns, subheadings and the document layout; custom snippets | [Customizing snippets and document layout](docs/customizing-snippets.md) |
| Migrate existing `document(...)` tests | [Migrating from Spring REST Docs](docs/migration.md) |
| Fix errors | [Troubleshooting](docs/troubleshooting.md) |
| Let an AI assistant (Claude Code, Copilot, Cursor) write the tests: `llms.txt` and a ready-made skill | [AI assistants](docs/ai.md) |
| Module layout and which artifacts to add | [Modules](docs/architecture.md) |

The full list is in [docs/README.md](docs/README.md).

## Samples

[samples/spring-boot-3](samples/spring-boot-3) and [samples/spring-boot-4](samples/spring-boot-4) contain the same sample app. They use HTTP, cookies, multipart, HAL, SSE, STOMP, WebSocket handlers, response wrappers, yml settings and the Gradle plugin, and one Kotest spec (`UserSearchDocsSpec`) produces documents alongside the JUnit tests. The memo API (`memo` package, `MemoApiDocsTest`) takes every description from KDoc only. In the Boot 4 sample, `src/test/.../kotest/` also contains specs that port the JUnit documentation tests to Kotest `FunSpec` one to one. So that the same API does not appear twice, these specs write their documents only to `build/generated-asciidoc-kotest`, and they are not included in the app's documents, OpenAPI or AsyncAPI.

```bash
./gradlew :samples:spring-boot-4:bootJar
java -jar samples/spring-boot-4/build/libs/spring-boot-4-0.1.jar
# http://localhost:8080/docs/index.html, /swagger-ui/index.html, /docs/async/index.html (AsyncAPI; the sample sets fileName = "async/index")
```

The message APIs of the samples accept one demo token, `demo-token`. For STOMP, send `Authorization: Bearer demo-token` in the CONNECT headers. `/ws/echo` and the SSE endpoints (`/api/v1/notifications/stream`, and `/api/v1/product-events` in the WebFlux sample) take `?access_token=demo-token` or the same `Authorization` header. In the AsyncAPI viewer's Try it out, enter the token in the Authentication box and turn on the "Send as a query parameter for WebSocket and SSE" option, because a browser can't add headers to a WebSocket handshake or an `EventSource`.

A WebFlux app (WebTestClient) is in [samples/spring-boot-4-webflux](samples/spring-boot-4-webflux). It documents `suspend`, `Flow` and `Mono` handlers and SSE streams that end.

```bash
./gradlew :samples:spring-boot-4-webflux:bootJar
java -jar samples/spring-boot-4-webflux/build/libs/spring-boot-4-webflux-0.1.jar
# http://localhost:8080/docs/index.html, /webjars/swagger-ui/index.html, /docs/asyncapi.html
```

A Maven project (Java app, Kotlin tests, the Maven plugin) is in [samples/maven-boot-4](samples/maven-boot-4). Install the library into your local Maven repository first.

```bash
./gradlew publishToMavenLocal
cd samples/maven-boot-4 && ./mvnw verify
# target/api-docs/ (index.html, openapi3.yaml, openapi.html, coverage/, http-client/)
```

## Development and license

The repository layout and how to verify changes are in [Development](docs/development.md), and the change history is in [CHANGELOG](CHANGELOG.md). How to report issues and contribute is in [CONTRIBUTING](CONTRIBUTING.md) ([한국어](CONTRIBUTING.ko.md)). The license is Apache License 2.0.
