**English** | [한국어](ko/getting-started.md)

# Getting started

[Document list](README.md)

<!-- toc -->
**Contents**

- [Installation](#installation)
  - [Supported build setups](#supported-build-setups)
  - [Dependencies without the plugin](#dependencies-without-the-plugin)
- [Creating a project](#creating-a-project)
  - [1. Directory structure](#1-directory-structure)
  - [2. settings.gradle.kts](#2-settingsgradlekts)
  - [3. build.gradle.kts](#3-buildgradlekts)
  - [4. application.yml (production)](#4-applicationyml-production)
  - [5. application-docs.yml (test)](#5-application-docsyml-test)
  - [6. Hand-written documents](#6-hand-written-documents)
  - [7. DTOs and controller](#7-dtos-and-controller)
  - [8. Documentation test](#8-documentation-test)
  - [9. Sharing setup (optional)](#9-sharing-setup-optional)
  - [10. Running](#10-running)
  - [Differences in Boot 3](#differences-in-boot-3)
- [Slice tests with @WebMvcTest](#slice-tests-with-webmvctest)
- [What to read next](#what-to-read-next)
<!-- /toc -->

This guide takes you from installing the library to producing your first API document in a new Spring Boot project.

You write documentation tests in Kotlin. The DSL is built from infix functions, reified type arguments, and lambdas with receivers, so it cannot be called usefully from Java. Your application code and DTOs can be Java: Java records, beans, and Java annotations are read as well ([Extraction rules](http-api.md#extraction-rules)).

## Installation

With the Gradle plugin `io.github.kardx-project`, you don't need to declare the library dependencies. The plugin adds the ones that fit your project ([Dependencies added automatically](build.md#dependencies-added-automatically)).

```kotlin
plugins {
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"   // the Spring Boot BOM decides the REST Docs version
    id("org.asciidoctor.jvm.convert") version "4.0.5"       // REST Docs HTML
    id("com.epages.restdocs-api-spec") version "0.20.2"     // OpenAPI (optional. Boot 3: 0.19.4)
    id("io.github.kardx-project") version "0.1"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")      // or -webflux
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // When documenting with Kotest, declare only the io.kotest dependencies. The plugin adds the extension matching the version (6.x / 5.x)
}
```

| What the plugin adds | When |
|---|---|
| `io.github.kardx-project:kardx-annotations` (`compileOnly`) | Always. `@Description`, `@Masked`, `@EnumCode`, `@AsciiDescription` for DTOs and enums. Not included in the production jar |
| `io.github.kardx-project:kardx` (`testImplementation`) | Always (when a Kotest extension is added, it brings this in) |
| `io.github.kardx-project:kardx-kotest` / `-kotest5` | When your test dependencies declare an `io.kotest` 6.x / 5.x version (not added if no version is declared, so declare it yourself). See [Kotest](kotest.md) instead of sections 8 and 9 (JUnit) of this guide |
| `spring-restdocs-mockmvc` / `spring-restdocs-webtestclient` | MVC app / WebFlux app. Version from the Spring Boot BOM |
| `com.epages:restdocs-api-spec-mockmvc` / `-webtestclient` | When the epages plugin is applied. Same version as the epages plugin |
| `io.github.kardx-project:kardx-boot` (`developmentOnly`) | Spring Boot apps. Logs the documentation URLs when the app starts (IDE run, `bootRun`) |

Modules you declare yourself are left alone, so to change a version, declare just that module. To turn off automatic dependencies, use `kardx { addDependencies = false }`; to leave out only the startup log module, use `startupLog = false`.

Artifacts are published to Maven Central under `io.github.kardx-project`. The Gradle plugin is published there too, so add `mavenCentral()` to the `pluginManagement` repositories as well.

### Supported build setups

The Gradle plugin is Gradle only. What the tests produce works with any build tool. The steps after the tests (index, OpenAPI post-processing, viewers, reports) live in the Gradle plugin and, for Maven, in the Maven plugin `io.github.kardx-project:kardx-maven-plugin`. The same steps also run from a command line tool (`kardx-tooling`), which any build can call. Maven setup, goals and the CLI are in [Maven and the command line](maven.md).

| Feature | Gradle with the plugin (Kotlin or Groovy DSL) | Gradle without the plugin | Maven |
|---|---|---|---|
| Documentation tests: snippets, one AsciiDoc document per test class, field extraction and verification | Yes | Yes | Yes (`target/` instead of `build/`) |
| Index document | Yes | `DocumentationIndexCli` ([index document](asciidoc.md#index-document)) | Yes, `api-docs` goal |
| REST Docs HTML | Yes (with `org.asciidoctor.jvm.convert`) | Your own Asciidoctor setup | `asciidoctor-maven-plugin` on the sources the `api-docs` goal assembles |
| AsyncAPI spec and viewer | Yes | `AsyncApiCli` ([SSE · WebSocket · AsyncAPI](messaging.md)) | Yes, `api-docs` goal |
| OpenAPI spec | Yes, refined: types, constraints, defaults, `oneOf`, multipart, cookies, security, operationIds | With the tooling CLI, the same refined spec; otherwise only what the epages `openapi3` task builds | Yes, refined, `api-docs` goal |
| Descriptions from KDoc/Javadoc, parameters declared by name only (experimental, off by default) | Yes, with `sourceDocs { enabled = true }` | Tooling CLI `source-docs` | `source-docs` goal |
| OpenAPI viewer, IntelliJ HTTP Client, Postman, documentation coverage, spec check against a baseline | Yes | Tooling CLI | Yes, `api-docs` and `check` goals |
| Export and upload, aggregation of several projects | Yes | No | No |
| WireMock mappings | Yes, plus the stub jar | yml `kardx.wire-mock-stubs: true` (mappings only) | Same as without the plugin |
| Docs served by the app at `/docs/`, startup log | Yes | Copy the output into `static/docs` and add the `boot` module yourself; the startup log then uses the default file names | Same as without the plugin (copy with `maven-resources-plugin`) |

**Gradle version and the Kotlin DSL.** The plugin needs Gradle 7.6 or later, running on JDK 17 or later: the plugin is compiled for Java 17, so Gradle on an older JDK can't load it (`UnsupportedClassVersionError ... class file version 61.0`). In IntelliJ, check Settings → Build Tools → Gradle → Gradle JVM; on the command line, `JAVA_HOME` or `org.gradle.java.home`.

The Kotlin DSL examples in these docs assign plugin settings with `=` (`language = "ko"`, `enabled = true`); the Kotlin DSL accepts `=` on these lazy properties from Gradle 8.2. On Gradle 7.6 to 8.1, call `.set(...)` instead, or use the Groovy DSL, where `=` works on every supported version. Your Spring Boot version may need a newer Gradle anyway (Spring Boot 4 needs Gradle 8.14 or later).

```kotlin
// Gradle 7.6 to 8.1, build.gradle.kts
kardx {
    language.set("ko")
    openApiViewer { enabled.set(true) }
}
```

**Groovy DSL.** Every `kardx { }` block in these docs works the same in `build.gradle`; only the plugin and string syntax differ.

```groovy
// settings.gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```groovy
// build.gradle
plugins {
    id 'org.jetbrains.kotlin.jvm' version '2.4.21'
    id 'org.springframework.boot' version '4.1.2'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'org.asciidoctor.jvm.convert' version '4.0.5'
    id 'com.epages.restdocs-api-spec' version '0.20.2'
    id 'io.github.kardx-project' version '0.1'
}

kardx {
    language = 'ko'
    openApiViewer { enabled = true }
    openApiCommonResponses.put(401, 'Token missing or expired')
}

openapi3 {
    title = 'Demo API'
}
```

### Dependencies without the plugin

With Maven, Gradle without the plugin, or `addDependencies = false`, declare the dependencies yourself. `addDependencies = false` only skips the dependencies; the documentation pipeline is still set up. Without the Gradle plugin you also set up the documentation pipeline yourself: with Maven, the Maven plugin ([Maven and the command line](maven.md)); with Gradle, the CLIs below [Dependencies added automatically](build.md#dependencies-added-automatically) and the tooling CLI.

| Artifact | Where | What |
|---|---|---|
| `io.github.kardx-project:kardx-annotations` | Production code `compileOnly` (Maven `provided`) | `@Description` and others. Not needed if you write descriptions with other annotations (Swagger `@Schema` and so on) ([Reading descriptions from other annotations](http-api.md#reading-descriptions-from-other-annotations)) |
| `io.github.kardx-project:kardx` | `testImplementation` | The library itself. Brings in Spring REST Docs core, spring-test, and the annotations. Does not bring in the MockMvc or WebTestClient modules |
| `org.springframework.restdocs:spring-restdocs-mockmvc` | `testImplementation` | When documenting with MockMvc (version from the Spring Boot BOM) |
| `org.springframework.restdocs:spring-restdocs-webtestclient` | `testImplementation` | When documenting with WebTestClient (WebFlux apps) |
| `io.github.kardx-project:kardx-kotest` | `testImplementation` | When documenting with Kotest 6.x specs. Brings in the library itself |
| `io.github.kardx-project:kardx-kotest5` | `testImplementation` | For Kotest 5.x, use this instead of the above (same package and API). Never add both |
| `com.epages:restdocs-api-spec-mockmvc` / `-webtestclient` | `testImplementation` | OpenAPI resources (Boot 3: 0.19.x, Boot 4: 0.20.x) |
| `io.github.kardx-project:kardx-boot` | `developmentOnly` | Logs the documentation URLs at app startup |
| `io.github.kardx-project:kardx-bom` | `platform(...)` | Aligns the versions of the `io.github.kardx-project` artifacts above |

```kotlin
dependencies {
    val kardx = platform("io.github.kardx-project:kardx-bom:0.1")

    compileOnly(kardx)
    compileOnly("io.github.kardx-project:kardx-annotations")

    testImplementation(kardx)
    testImplementation("io.github.kardx-project:kardx")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

```xml
<dependency>
    <groupId>io.github.kardx-project</groupId>
    <artifactId>kardx-annotations</artifactId>
    <version>0.1</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>io.github.kardx-project</groupId>
    <artifactId>kardx</artifactId>
    <version>0.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.restdocs</groupId>
    <artifactId>spring-restdocs-mockmvc</artifactId>
    <scope>test</scope>
</dependency>
```

Other optional dependencies:

| Feature | Dependency to add |
|---|---|
| Documenting constraints | `spring-boot-starter-validation` (production code) |
| Kotlin data class serialization on Boot 4 | `tools.jackson.module:jackson-module-kotlin` (for Jackson 3) |

## Creating a project

These are the steps for adding the library to a new Spring Boot 4 project from scratch. If you create the files below as they are, you get tests, REST Docs HTML, and OpenAPI (Swagger UI). WebSocket and SSE message documents (AsyncAPI) appear only when you [document messages](messaging.md). For Boot 3, see [Differences in Boot 3](#differences-in-boot-3).

### 1. Directory structure

```
demo/
├── settings.gradle.kts
├── build.gradle.kts                                       Gradle plugin io.github.kardx-project
└── src/
    ├── main/
    │   ├── kotlin/com/example/demo/DemoApplication.kt     @Description on DTOs
    │   └── resources/application.yml                      makes Swagger UI show the documented spec
    ├── docs/asciidoc/                                     hand-written documents (optional)
    │   ├── index-template.adoc                            index preamble, API list placeholder (built-in template if absent)
    │   └── _landing.adoc                                  notice placed at the top of every document (optional; files starting with _ do not become standalone HTML)
    └── test/
        ├── kotlin/com/example/demo/
        │   ├── ApiDocsTest.kt                             documentation test base class
        │   └── UserApiDocsTest.kt                         documentation test
        └── resources/application-docs.yml                 library settings (kardx.*)
```

### 2. settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "demo"
```

### 3. build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.5"          // REST Docs HTML
    id("com.epages.restdocs-api-spec") version "0.20.2"        // OpenAPI (optional)
    id("io.github.kardx-project") version "0.1"         // documentation pipeline + library dependencies
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }
```

That's all. Here is what the plugin adds and its defaults.

- Dependencies: the annotations (`compileOnly`), the library itself plus `spring-restdocs-mockmvc` and `restdocs-api-spec-mockmvc` (`testImplementation`), and the startup log module (`developmentOnly`). See [Dependencies added automatically](build.md#dependencies-added-automatically)
- index: includes every test class that produced a document. You don't need `kardx { index { packageName; baseClass } }`. Without the template from section 6, a built-in template titled with the project name is used (set the title with `kardx { index { title = "..." } }`). See [index document](asciidoc.md#index-document)
- Language: the texts the library puts into the documents (the index list heading, the `Back to index` link, `Constraints:` and so on) are English. For Korean, add `kardx { language = "ko" }` ([Documentation language](configuration.md#documentation-language)).
- OpenAPI: without `openapi3 { }`, the output is `openapi3.yaml`, the title is the project name, the version is the project version, and the server is `/`. Set only the values you want to change.

```kotlin
openapi3 {
    title = "Demo API"      // default: project name
    version = "0.0.1"       // default: project version
}
```

What the plugin does and what you can change is described in [Gradle plugin](build.md#gradle-plugin).

### 4. application.yml (production)

This makes Swagger UI show the spec verified by tests (`/docs/openapi3.yaml`) instead of the runtime scan result. You don't need it if you don't use Swagger UI.

```yaml
springdoc:
  swagger-ui:
    url: /docs/openapi3.yaml # show the test-verified spec instead of the runtime scan

server:
  mime-mappings:
    yaml: application/yaml
```

### 5. application-docs.yml (test)

These are the library settings. All properties are listed in [yml settings](configuration.md#yml-settings).

```yaml
kardx:
  common-include-files: _landing.adoc    # file placed at the top of every document
  mask-sensitive-headers: true           # masks values of Authorization, Cookie, and so on (not masked by default)
```

### 6. Hand-written documents

Both are optional. If `src/docs/asciidoc/index-template.adoc` exists, links to each test class's document are inserted at the `// restdocs:api-index` line. Otherwise, only the list goes into a built-in template titled with the project name.

```asciidoc
= Demo API
:doctype: book
:icons: font
:toc: left
:sectlinks:

* link:/swagger-ui/index.html[Swagger UI] · link:./openapi3.yaml[openapi3.yaml]
// If you documented WebSocket·STOMP·SSE messages: * link:./asyncapi.html[WebSocket·SSE messages]

== API list

// restdocs:api-index
```

`src/docs/asciidoc/_landing.adoc` goes at the top of every API document.

```asciidoc
NOTE: This document was generated from the actual requests and responses of passing tests.
```

### 7. DTOs and controller

```kotlin
package com.example.demo

import io.github.kardx.annotation.Description
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

data class CreateUserRequest(
    @field:NotBlank @Description("Nickname") val nickname: String,
    @Description("Bio") val bio: String? = null,
)

data class UserResponse(
    @Description("User ID") val id: Long,
    @Description("Nickname") val nickname: String,
)

@RestController
class UserController {
    @GetMapping("/api/users/{id}")
    fun get(@PathVariable id: Long) = UserResponse(id, "Hong Gildong")

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateUserRequest) = UserResponse(1, request.nickname)
}
```

### 8. Documentation test

The following uses JUnit. To write Kotest specs, see [Kotest](kotest.md) (you register the extension in each spec). For `@WebMvcTest` slice tests, see [Slice tests with @WebMvcTest](#slice-tests-with-webmvctest).

```kotlin
package com.example.demo

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
@ActiveProfiles("docs") // src/test/resources/application-docs.yml
@DisplayName("User API")
class UserApiDocsTest {

    @Test
    @DisplayName("Get user")
    fun getUser(mockMvc: MockMvc) {
        mockMvc.perform(get("/api/users/{id}", 1L))
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
        mockMvc.perform(post("/api/users").jsonContent(CreateUserRequest("Hong Gildong")))
            .andStatus(HttpStatus.CREATED)
            .andDocument {
                description = "Creates a user."
                requestFields<CreateUserRequest>()
                responseFields<UserResponse>()
            }
    }
}
```

`AutoRestDocumentationExtension` passes a REST Docs `MockMvc` to every test method (and `@BeforeEach` method) that declares a `MockMvc` parameter: the same client as `restDocsMockMvc(context, provider)`, built from the Spring test context. WebFlux apps take a `WebTestClient` parameter the same way ([WebTestClient](webtestclient.md)). Parameters marked `@Autowired`, `@Qualifier` or `@Value` and constructor parameters are left to Spring. The class needs a Spring test context (`@SpringBootTest`, `@WebMvcTest`, `@WebFluxTest`); otherwise the test fails with [KDX-014](troubleshooting.md#kdx-014).

Every test class that produces a document goes into the index, so no base class is needed.

If you use the MockMvc Kotlin DSL, chain the same block like `mockMvc.get("/api/users/{id}", 1L).andExpect { status { isOk() } }.andDocument { ... }`. `MockMvcTester` results (`exchange()`) also have `andDocument { }` ([andDocument](http-api.md#anddocument)). If the fields don't match the response, the test fails and tells you the missing fields and the DSL lines to paste in ([Verification failure messages](http-api.md#verification-failure-messages)).

Don't document with a `MockMvc` that Spring Boot injects (`@Autowired` with `@AutoConfigureMockMvc`, `@WebMvcTest`, or `@AutoConfigureRestDocs`). It has no REST Docs setup from this library, so the test fails with [KDX-012](troubleshooting.md#kdx-012), or, with `@AutoConfigureRestDocs`, snippets go to a different directory and sections would silently go missing: that fails with [KDX-011](troubleshooting.md#kdx-011) (when the directories happen to match, it only warns once).

### 9. Sharing setup (optional)

When many test classes share annotations or helpers, put them in a base class. The older form with a `@BeforeEach` still works:

```kotlin
package com.example.demo

import io.github.kardx.junit.AutoRestDocumentationExtension
import io.github.kardx.mockmvc.restDocsMockMvc
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.context.WebApplicationContext

/** Shared setup for documentation tests. */
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
abstract class ApiDocsTest {
    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpRestDocs(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        mockMvc = restDocsMockMvc(context, provider)
    }
}

class UserApiDocsTest : ApiDocsTest() {
    @Test
    fun getUser() {
        mockMvc.perform(get("/api/users/{id}", 1L)) // ...
    }
}
```

To keep the client in a field or build it in a Kotest spec body, `restDocsMockMvc(context)` (and `restDocsWebTestClient(context)`) needs no provider: each request documents into the test that is running at that moment. Outside a documented test it fails with [KDX-013](troubleshooting.md#kdx-013).

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
class UserApiDocsTest(@Autowired context: WebApplicationContext) {
    private val mockMvc = restDocsMockMvc(context) // documents into whichever test is running
}
```

### 10. Running

```bash
./gradlew test        # produces only snippets and AsciiDoc
./gradlew apiDocs     # + index, HTML, OpenAPI (AsyncAPI if there are messages). Prints the output locations when done
./gradlew bootRun     # runs the app. Runs apiDocs automatically first (same when running main in IntelliJ)
./gradlew bootJar     # + puts the results above into /docs of the executable jar
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

When the app starts from the IDE or `bootRun`, the documentation URLs are logged ([Startup log](build.md#startup-log)). The executable jar (`java -jar`) logs them only with `kardx { startupLogScope = "runtimeOnly" }`.

```
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (REST Docs   ) http://localhost:8080/docs/index.html
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (Swagger UI  ) http://localhost:8080/swagger-ui/index.html
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (OpenAPI spec) http://localhost:8080/docs/openapi3.yaml
```

When `apiDocs` finishes, it shows the output locations like this. The `file:///` URLs open directly from a terminal or IDE.

```
Generated API docs
  REST Docs HTML    file:///.../demo/build/api-docs/index.html
  In the app        http://localhost:8080/docs/index.html (IDE run, bootRun, bootJar)
  OpenAPI (Swagger) /.../demo/build/api-docs/openapi3.yaml
  Coverage          file:///.../demo/build/api-docs/coverage/coverage.md
  AsciiDoc          /.../demo/build/generated-asciidoc
  Snippets          /.../demo/build/generated-snippets
```

The `Coverage` line appears because the epages plugin is applied ([Documentation coverage](checks.md#documentation-coverage)). Without it there is no `Coverage` line, and the OpenAPI line says how to turn it on: `OpenAPI (Swagger) off (apply id("com.epages.restdocs-api-spec") version "0.20.2" to enable OpenAPI, coverage and the viewer)` (`0.19.4` with Boot 3). If you documented WebSocket or SSE messages, an extra `WebSocket·SSE     file:///.../asyncapi.html` line appears, and enabled outputs (OpenAPI viewer, Postman, HTTP Client) add a line each.

Which command to run in which situation (CI, the baseline check, handing the spec over, `clean`) and the order the tasks run in are in [Which command when](build.md#which-command-when).

### Differences in Boot 3

| Item | Boot 4 | Boot 3 |
|---|---|---|
| Spring Boot plugin | `4.1.2` | `3.5.16` |
| epages plugin (the plugin adds `restdocs-api-spec-mockmvc` with the same version) | `0.20.2` | `0.19.4` |
| Jackson Kotlin module | `tools.jackson.module:jackson-module-kotlin` | `com.fasterxml.jackson.module:jackson-module-kotlin` |
| springdoc | `springdoc-openapi-starter-webmvc-ui:3.2.0` | `springdoc-openapi-starter-webmvc-ui:2.8.17` |

The plugin picks the Asciidoctor extension (`spring-restdocs-asciidoctor`) and the AsciidoctorJ version to match the Boot version.

## Slice tests with @WebMvcTest

You can document from `@WebMvcTest` slice tests, which start only the web layer: the controller under test plus mocks for what it depends on. Take `MockMvc` as a test method parameter, exactly as in the documentation test above.

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@WebMvcTest(UserController::class)
@ActiveProfiles("docs")
@DisplayName("User API")
class UserApiSliceDocsTest {

    @Test
    @DisplayName("Get user")
    fun getUser(mockMvc: MockMvc) {
        mockMvc.perform(get("/api/users/{id}", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument {
                pathParameters("id" pathMeans "User ID")
                responseFields<UserResponse>()
            }
    }
}
```

- `@WebMvcTest` lives in a different place per Boot version. Boot 4: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, in the `spring-boot-webmvc-test` module, so add `testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")`. Boot 3: `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`, already in `spring-boot-starter-test`. The Gradle plugin doesn't add either.
- The slice context is a `WebApplicationContext`, so the `MockMvc` parameter works as with `@SpringBootTest`: it uses the context's Jackson mapper, and `AutoRestDocumentationExtension` reads `kardx.*` from the test's settings (`@ActiveProfiles("docs")` picks up `application-docs.yml`). The handler is known, so operationId and parameter types come from the controller as usual.
- Don't `@Autowired` the `MockMvc` that `@WebMvcTest` provides, with or without `@AutoConfigureRestDocs`: it has no REST Docs setup from this library ([KDX-012](troubleshooting.md#kdx-012)), and with `@AutoConfigureRestDocs` Spring Boot names snippet directories differently, so sections would go missing ([KDX-011](troubleshooting.md#kdx-011), [Migrating from Spring REST Docs](migration.md#2-test-base-class)).
- Mock the controller's dependencies with `@MockitoBean` (Spring Framework 6.2 and later) or springmockk's `@MockkBean`.
- If Spring Security is on the classpath, the slice includes it, so there is a `springSecurityFilterChain` bean. `restDocsMockMvc` doesn't apply it unless `kardx.apply-spring-security: true`, and logs a warning once until you set the value either way ([Spring Security](http-api.md#spring-security)).
- WebSocket and STOMP documentation needs a running server, so keep those tests on `@SpringBootTest(webEnvironment = RANDOM_PORT)`.

The samples in this repository use `@SpringBootTest` and have no slice test. If something behaves differently in your project (for example, a filter or `WebMvcConfigurer` your controller relies on is not in the slice), add it with `@Import` or fall back to `@SpringBootTest`.

## What to read next

- Documentation syntax and automatic extraction: [Documenting HTTP APIs](http-api.md)
- Documenting with Kotest specs: [Kotest](kotest.md)
- WebFlux apps (WebTestClient) and RestTestClient: [WebTestClient · RestTestClient](webtestclient.md)
- yml settings and masking: [Configuration](configuration.md)
- Output locations and plugin options: [Build and outputs](build.md)
- OpenAPI, Swagger UI and the spec check: [OpenAPI and Swagger UI](openapi.md), [Spec check and coverage](checks.md)
- Changing table columns, subheadings, and document layout: [Customizing snippets and document layout](customizing-snippets.md)

Optional outputs and features (OpenAPI viewer, Postman, IntelliJ HTTP Client, WireMock stub, documentation coverage, change comparison, aggregation, Enum code documents and more) are listed with how to turn each one on in the README: [What you get](../README.md#what-you-get) and [Optional features](../README.md#optional-features).
