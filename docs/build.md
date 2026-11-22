**English** | [한국어](ko/build.md)

# Build and outputs

[Document list](README.md)

<!-- toc -->
**Contents**

- [Which command when](#which-command-when)
  - [Common mistakes](#common-mistakes)
- [Gradle plugin](#gradle-plugin)
  - [Dependencies added automatically](#dependencies-added-automatically)
- [Automatic generation on run](#automatic-generation-on-run)
  - [Startup log](#startup-log)
- [Production exposure](#production-exposure)
- [Output locations](#output-locations)
  - [Directories the plugin empties](#directories-the-plugin-empties)
  - [Docs in the app resources](#docs-in-the-app-resources)
  - [Reproducible snippets](#reproducible-snippets)
<!-- /toc -->

This page explains how the Gradle plugin sets up the documentation pipeline and where the outputs end up. The features built on top of it have their own pages: [OpenAPI and Swagger UI](openapi.md), [Spec check and coverage](checks.md) and [Viewer, clients, stubs and sharing](outputs.md).

## Which command when

You rarely need more than one command: each task runs the tasks it depends on first (tests included), so you don't run `test` before `apiDocs`, or `apiDocs` before `bootJar`. Tasks whose inputs haven't changed are skipped as UP-TO-DATE.

| Situation | Run, in this order | What happens, where the result is |
|---|---|---|
| First time | 1. Write a documentation test ([Getting started](getting-started.md#8-documentation-test))<br>2. `./gradlew apiDocs`<br>3. Open the `file:///…/build/api-docs/index.html` link it prints | Tests → snippets → HTML, OpenAPI, AsyncAPI → `build/api-docs/` |
| Day to day while developing | Run `main` from IntelliJ, or `./gradlew bootRun` | `apiDocs` runs before the app starts: in IntelliJ right after the `:classes` build it triggers, with `bootRun` before the app task. Then open `http://localhost:8080/docs/index.html` ([Automatic generation on run](#automatic-generation-on-run)) |
| Regenerate the docs only | `./gradlew apiDocs` | Updates `build/api-docs/` and the app resources (`build/resources/main/static/docs`). An app already started from IntelliJ or `bootRun` serves from there, so it doesn't need a restart; an app started from a jar keeps the docs packaged in it |
| Executable jar with the docs inside | `./gradlew bootJar` | Runs `apiDocs` and `apiDocsAppDocs` first, then packages the docs at `BOOT-INF/classes/static/docs/` of `build/libs/<name>-<version>.jar` (only with `bootJar.enabled`, the default; [Production exposure](#production-exposure)) |
| CI | `./gradlew build` | `check` runs `test`, which fails on a failing documentation test. For a Spring Boot app, `assemble` builds `bootJar`, which pulls in `apiDocs` (with `bootJar.enabled`, the default). `apiDocsCheck` is included only with `openApiCheck { runOnCheck = true }`; otherwise run `./gradlew build apiDocsCheck`. `apiDocsCoverage` runs with `apiDocs` and fails the build only with `coverage { failOnUndocumented = true }` ([Documentation coverage](checks.md#documentation-coverage)). Nothing is exported or uploaded. Workflow files: [CI recipes](ci.md) |
| An API change is intended | 1. `./gradlew apiDocsBaseline`<br>2. Commit `docs/openapi/openapi3.yaml` with the change | `apiDocsCheck` then compares against the new baseline. In one invocation (`./gradlew apiDocsBaseline apiDocsCheck`) the check always runs after the baseline update ([Comparing OpenAPI changes](checks.md#comparing-openapi-changes)) |
| Hand the spec to others | `./gradlew apiDocsExport`, `./gradlew apiDocsUpload`, or `./gradlew publish` with `export.publishSpecs = true` | Run only when you call them; each first runs only the producers it needs (`openapi3`, `apiDocsAsyncApi`, `apiDocs`) ([Exporting specs](outputs.md#exporting-specs)) |
| Several projects in one place | `./gradlew apiDocsAggregate` (in the aggregating project) | Runs `apiDocs` in each project declared with `restdocsAggregation(...)`, then writes `build/api-docs-aggregate/` ([Aggregating multiple projects](outputs.md#aggregating-multiple-projects)) |
| Start from nothing | `./gradlew clean`, then any of the above | Deletes `build/`, including every generated document and the copy in the app resources. Not needed for deleted or renamed tests (see below) |

**Deleted or renamed tests.** With `cleanBeforeTest = true` (the default), `test` empties the snippet and AsciiDoc directories before it runs, and the later steps (`apiDocsAsciidocSources`, `apiDocsOpenApiSnippets`, `apiDocs`) sync rather than copy, and `apiDocsAppDocs` removes the files it copied last time that are gone now, so documents of tests that no longer exist disappear on the next run. Deleting or renaming a test changes the test classes, so `test` reruns by itself. You need `clean` only if you set `cleanBeforeTest = false`.

The [Gradle plugin](#gradle-plugin) table lists every task, intermediate ones included; output paths are in [Output locations](#output-locations).

### Common mistakes

- **The docs didn't change.** An app started from a jar keeps the docs packaged at build time: rebuild with `bootJar` and restart it. Automatic generation runs only when `bootRun` is the only task you ask for, so `./gradlew clean bootRun` starts the app without generating; run `./gradlew apiDocs` (no restart needed). In IntelliJ, generation also needs the build setting left at Gradle. A test that fails during an IDE run or `bootRun` doesn't stop the app, but its document is missing; the failure is in the build log. If `test` shows UP-TO-DATE, no test, app code or resource changed, so the documents wouldn't differ; the exception is a non-`.adoc` file included from a hand-written document (`./gradlew asciidoctor --rerun`, explained under [Gradle plugin](#gradle-plugin)).
- **The docs are missing from the jar.** Check `bootJar { enabled }` (a property such as `-PwithDocs=false` from [Production exposure](#production-exposure) turns it off) and look inside with `jar tf build/libs/<name>-<version>.jar | grep static/docs`. A failing documentation test fails `bootJar`; it doesn't produce a jar with partial docs.
- **`apiDocsCheck` fails with "No OpenAPI baseline".** There's no baseline yet. Run `./gradlew apiDocsBaseline` once and commit the file, or set `openApiCheck { requireBaseline = false }` until you have one.

## Gradle plugin

`id("io.github.kardx-project")` sets up the documentation pipeline and adds the library dependencies. It doesn't apply other plugins; it only hooks into them when they are applied. Plugin order doesn't matter. It requires Gradle 7.6 or later; on an older version, it fails when applied and tells you the required version (the versions actually verified, 7.6.4 among them, are in the [Compatibility](../README.md#compatibility) table).

| What it hooks into | What it does |
|---|---|
| Dependencies (`compileOnly`, `testImplementation`) | Adds the library and the test client. Modules you declare yourself are left alone ([Dependencies added automatically](#dependencies-added-automatically)) |
| `test` | Passes the snippet and AsciiDoc paths as system properties, and deletes the previous results before running |
| `apiDocsSourceDocs` | Experimental, off by default: runs before `test` only with `sourceDocs { enabled = true }`. Reads the KDoc and Javadoc of the Kotlin and Java `main` sources of this project and, by default, of the projects it depends on (`project(...)`), and writes them to `build/kardx/source-docs/source-docs.json`. `test` gets the file's path as a system property, and the tests use it as the last source of field, parameter, enum constant and operation descriptions ([Descriptions from KDoc/Javadoc](http-api.md#descriptions-from-kdocjavadoc)). What goes into the file follows the per-item switches (`fieldDocs`, `enumDocs`, `schemaDescriptions`, `paramDocs`, `methodDocs`); method comments are collected only with `methodDocs = true`, so implementation notes on handler methods don't become public summaries by default. Its inputs are the source files and its output is cacheable, so it is skipped while the sources don't change; any change rescans all the sources, which usually takes well under a second (about 0.2 s for the Boot 4 sample, whose `project(...)` dependencies include this library's own modules). A declaration it can't read is skipped, never failing the build. Sources are read as UTF-8; a file that isn't (an older Java module compiled with `MS949`, for example) is still read, with the invalid bytes replaced by U+FFFD, and the task warns once: `Source docs: N source files are not UTF-8 (first: …)`. Save the sources as UTF-8 to get those descriptions right. With `enabled = false` (the default) the task is still registered, but `test` doesn't depend on it and gets no system property, and running it directly is skipped (`SKIPPED`) |
| `apiDocsIndex` | Builds the index with a link for each test class that produced a document. Configuration is optional: it reads the records that tests leave next to each class document (`build/generated-asciidoc/.restdocs-documents/`), so it doesn't scan the classpath, and without a template (`src/docs/asciidoc/index-template.adoc`) it uses a built-in template (title `index.title`, defaulting to the project name). If you set both `index.packageName` and `index.baseClass`, it includes only tests that extend the base class in that package and Kotest specs, as before. If only one of them is set, or no test wrote a document, it logs the reason in one line and skips. The result is placed outside `build/generated-asciidoc`, which test clears (`build/kardx/index/index.adoc`), so when you rerun tests and the generated documents are unchanged, the task is skipped |
| `apiDocsAsciidocSources` | Gathers hand-written documents, generated documents, and the index into a single Asciidoctor input directory |
| `asciidoctor` (`org.asciidoctor.jvm.convert`) | Sets the input directory, the snippet path, the `spring-restdocs-asciidoctor` extension, and AsciidoctorJ 3 for Boot 4. Files starting with `_` are not turned into standalone HTML. Only `*.adoc` snippets are treated as input, so if the snippets are unchanged, the task is skipped even when tests rerun. The conversion JVM gets `asciidoctorJvmArgs` (default `-XX:TieredStopAtLevel=1`) |
| `apiDocsOpenApiSnippets` (`com.epages.restdocs-api-spec`) | Copies the OpenAPI fragments (`resource.json`) to `build/kardx/api-spec-snippets` and aligns the security requirements across tests of the same API (Authorize is kept even when tests without a token are mixed in). It also merges or renames same-named schemas with different structures and logs them as `OpenAPI schema names: …` ([Schema names](http-api.md#schema-names)). It doesn't modify the test output, so tests don't rerun on the next build |
| `openapi3` (`com.epages.restdocs-api-spec`) | Builds the spec from that copy, turns global headers (`open-api.global-headers`) into an `apiKey` security scheme, merges the type, constraint, default, example, multipart, and cookie information left by the library, and refines response descriptions, operationIds, tags, and examples ([What goes into the OpenAPI spec](http-api.md#what-goes-into-the-openapi-spec)). Values not set in `openapi3 { }` are set to suit this library: `format = "yaml"` (the epages default is json), `title` = project name, `version` = project version (the epages default if `unspecified`), server `"/"` (Swagger UI's Try it out sends requests to the server that opened the document). Values you set in `openapi3 { }` are not overwritten. The `check` dependency that epages adds is removed: `apiDocs` and `openapi3` run only the tests first, not `check` tasks such as ktlint and detekt. To run static analysis as well, use `./gradlew check apiDocs` |
| `apiDocsAsyncApi` | Builds the AsyncAPI document and the Try it out viewer from WebSocket, STOMP, and SSE messages. Its inputs are the `*.asyncapi.json` snippets and the `asyncApi { }` settings, so it's skipped when nothing changed. It doesn't rerun when only app code changes |
| `apiDocs` | Gathers HTML, OpenAPI, and AsyncAPI into one directory, together with the Postman Collection and the IntelliJ HTTP Client file when they are enabled |
| `apiDocsCoverage` (`com.epages.restdocs-api-spec`) | Runs as part of `apiDocs`. Compares the endpoints the app maps with the documented operations and writes a report ([Documentation coverage](checks.md#documentation-coverage)) |
| `apiDocsHttpClient` (`com.epages.restdocs-api-spec`) | When enabled, `apiDocs` also gathers the IntelliJ HTTP Client file ([IntelliJ HTTP Client](outputs.md#intellij-http-client)) |
| `apiDocsAppDocs` | Always runs after `apiDocs`. For Spring Boot apps, it copies the documents to `build/resources/main/<bootJar.path>` (default `static/docs`) so that IDE runs, `bootRun`, and the executable jar all open them at `/docs/index.html`; records the actual document path and file names in a file the startup log reads (`META-INF/kardx/docs-locations.properties`); and prints the output locations. It warns when documents without sensitive header masking go into the executable jar ([Production exposure](#production-exposure)). It runs even when `apiDocs` is UP-TO-DATE, so deleted documents are restored. Files the app keeps in the same directory are left alone ([Docs in the app resources](#docs-in-the-app-resources)) |
| IntelliJ run, `bootRun` | Runs `apiDocs` before starting the app ([Automatic generation on run](#automatic-generation-on-run)) |
| `apiDocsCheck`, `apiDocsBaseline` (`com.epages.restdocs-api-spec`) | Compares against a committed baseline spec, or updates the baseline ([Comparing OpenAPI changes](checks.md#comparing-openapi-changes)) |
| `apiDocsViewer` (`com.epages.restdocs-api-spec`) | When enabled, builds a static Redoc or Scalar viewer page ([OpenAPI viewer](outputs.md#openapi-viewer-redoc--scalar)) |
| `postman` (`com.epages.restdocs-api-spec`) | When enabled, `apiDocs` also gathers the Postman Collection ([Postman Collection](outputs.md#postman-collection)) |
| `apiDocsAggregate` | Gathers the documents of multiple projects into one list and a merged spec ([Aggregating multiple projects](outputs.md#aggregating-multiple-projects)) |
| `apiDocsStubsJar` | When enabled, packages the WireMock mappings produced by documentation tests into a stub jar ([WireMock stub](outputs.md#wiremock-stub)) |
| `apiDocsExport`, `apiDocsUpload` | When run directly, export the specs to a directory or upload them over HTTP. With `maven-publish` and `export.publishSpecs = true`, the specs are attached to the publication ([Exporting specs](outputs.md#exporting-specs)) |
| `testRuntimeClasspath` | For WebFlux apps (`spring-boot-starter-webflux` declared, and none of `spring-boot-starter-web`, `-websocket`, or `spring-webmvc` declared), removes the `spring-boot-starter-web` that epages pulls in. This keeps the app from starting as a servlet app ([WebTestClient dependencies](webtestclient.md#dependencies)). The decision looks only at directly declared dependencies (starters coming in transitively are not seen), and only `testRuntimeClasspath` is affected (for other source sets and JVM Test Suites, add the `exclude` yourself). To turn it off, use `excludeServletStarterForWebFlux = false` |

All task names of this plugin start with `apiDocs`. The tasks you run directly (`apiDocs`, `apiDocsIndex`, `apiDocsSourceDocs`, `apiDocsAsyncApi`, `apiDocsCoverage`, `apiDocsHttpClient`, `apiDocsCheck`, `apiDocsBaseline`, `apiDocsViewer`, `apiDocsAggregate`, `apiDocsStubsJar`, `apiDocsExport`, `apiDocsUpload`) are in the `documentation` group, so they appear with descriptions in `./gradlew tasks`. The intermediate tasks (`apiDocsAsciidocSources`, `apiDocsOpenApiSnippets`, `apiDocsAppDocs`, and `apiDocsPublicationSpecs`, which exists only with `maven-publish` and `export.publishSpecs`) have no group and appear only in `./gradlew tasks --all`.

If a document (a hand-written `.adoc`) includes a snippet file that isn't `.adoc`, Asciidoctor doesn't rerun when only that file changes. In that case, rebuild with `./gradlew asciidoctor --rerun`.

The texts the library puts into generated documents (list headings, the index link, the constraints prefix, the common error responses table and so on) are English by default. To get Korean, set `kardx { language = "ko" }`. The plugin passes the same value to the test JVM, the document tasks and the app startup log, so set it here rather than in yml to keep the languages from mixing ([Documentation language](configuration.md#documentation-language)). Task console output and the `apiDocsCheck` report are English whatever the language.

This plugin's tasks are compatible with the Gradle configuration cache (task actions don't reference `Project`). However, `org.asciidoctor.jvm.convert` 4.0.x and the epages plugin are not yet compatible, so running the whole pipeline with `--configuration-cache` reports problems from those plugins. The optional feature tasks (`apiDocsCheck`, `apiDocsViewer`, `apiDocsAggregate`, `apiDocsStubsJar`, `apiDocsExport`, `apiDocsUpload`) are compatible too. For example, `./gradlew apiDocs -x asciidoctor --configuration-cache --configuration-cache-problems=warn` reuses the cache on the second run, and the only remaining warnings come from epages `openapi3` and `postman`.

Up-to-date checks: when nothing changed, a second `./gradlew apiDocs` skips `postman` and `apiDocsViewer` as UP-TO-DATE (the plugin declares the inputs and outputs epages doesn't). `apiDocsAppDocs` still runs, to restore deleted app copies. `openapi3` runs every time: the plugin marks it untracked (`doNotTrackState`), because its epages input `servers` can't be serialized by Gradle, and declares the spec files (`build/api-spec/openapi3.yaml` and so on) as its outputs. Without those outputs, a later task in the same build could reuse an old spec as UP-TO-DATE after only the spec had changed. Because the files are now declared outputs, Gradle 8 and later check who reads them: a task of your own that reads `build/api-spec/openapi3.*` must declare `dependsOn("openapi3")` (or take the file as an input wired to the task), or Gradle reports an implicit dependency problem. If epages `openapi3 { outputDirectory }` points into the app's resources (`build/resources/main/...` or `src/main/resources/...`), the plugin warns once and asks you to remove that setting, since it already copies the spec into the app ([Docs in the app resources](#docs-in-the-app-resources)); inside `build/resources/main` it doesn't declare the spec files as outputs, because `jar`, `bootJar` and `apiDocsIndex` read that directory without depending on `openapi3`.

```kotlin
kardx {
    snippetsDirectory = layout.buildDirectory.dir("generated-snippets")      // default
    asciidocDirectory = layout.buildDirectory.dir("generated-asciidoc")      // default
    asciidocSourceDirectory = layout.projectDirectory.dir("src/docs/asciidoc") // default
    outputDirectory = layout.buildDirectory.dir("api-docs")                  // default
    cleanBeforeTest = true                                                   // default
    language = "ko"                      // no default (en). Language of the texts the library puts into documents (en, ko)
    generateOnRun = true                 // default. Generates documents before IntelliJ runs and bootRun
    openApiGlobalHeaders = setOf("X-Tenant-Id")   // headers entered once via Swagger Authorize (merged with the test settings)
    openApiCommonResponses.put(401, "Token missing or expired") // no default. Common error responses the tests didn't document or declare
    openApiCommonResponseSchema = "ErrorResponse" // default: the schema most error responses refer to
    startupLog = true                    // default. INFO log of document URLs at app startup (kardx-boot)
    startupLogScope = "developmentOnly"  // default. Only for IDE runs and bootRun. Use "runtimeOnly" to log from the executable jar too
    restDocsVersion = "4.0.2"            // needed only without the Spring Boot plugin; required then if the Asciidoctor plugin is applied
    excludeServletStarterForWebFlux = true // default. For WebFlux apps, removes spring-boot-starter-web from the test runtime
    addDependencies = true               // default. Adds the library and test client dependencies
    asciidoctorJvmArgs = listOf("-XX:TieredStopAtLevel=1") // default. JVM args of the asciidoctor conversion; emptyList() turns it off

    index {                              // all optional
        template = layout.projectDirectory.file("src/docs/asciidoc/index-template.adoc")   // default. Built-in template if absent
        title = "Order API"              // default: project name (title of the built-in template)
        packageName = "com.example"      // no default: all test classes that produced a document. If set, set baseClass too
        baseClass = "com.example.support.ApiDocsTest"
    }
    asyncApi {
        enabled = true                   // default
        title = "Chat API"               // default: project name
        version = "1.0.0"                // default: project version
        description = "Chat messages"
        server("stomp", "stomp://localhost:8080/ws")
        server("websocket", "ws://localhost:8080")
        server("http", "http://localhost:8080")            // SSE
        fileName = "asyncapi"            // default. asyncapi.yaml, asyncapi.html. Subpaths work too: "async/index"
        bearerAuth()                     // no default. Also basicAuth(), apiKeyHeader("X-API-KEY"), securitySchemes.put(...)
    }
    bootJar {
        enabled = true                   // default
        path = "static/docs"             // default. /docs/index.html in the app. Must be a sub directory (Docs in the app resources)
    }
    coverage {                           // apiDocsCoverage (Documentation coverage)
        enabled = true                   // default
        failOnUndocumented = false       // default. true fails when an endpoint is undocumented
        exclude("/internal/**")          // added to the default excludes (/error, /actuator/**, ...)
    }

    // The following optional features are off by default or work only when run directly
    sourceDocs {                         // apiDocsSourceDocs (Descriptions from KDoc/Javadoc, experimental)
        enabled = false                  // default. true: test runs the scan first and uses the comments as descriptions
        fieldDocs = true                 // default. Property, field, getter and record comments → field descriptions
        enumDocs = true                  // default. Enum constant comments → value lists, @EnumCode tables
        schemaDescriptions = true        // default. Class comments → OpenAPI component schema descriptions
        paramDocs = true                 // default. Handler @param → parameters declared by name only
        methodDocs = false               // default. true: controller method comments become OpenAPI summaries
        includeProjectDependencies = true // default. Also reads the main sources of project(...) dependencies
    }
    openApiCheck {
        baseline = layout.projectDirectory.file("docs/openapi/openapi3.yaml") // default
        failOn = "any"                   // default. "breaking" fails only on breaking changes, "none" only warns
        runOnCheck = false               // default. If true, also part of ./gradlew check
        requireBaseline = true           // default. Fails when the baseline file is missing (warns with failOn = "none")
    }
    openApiViewer {
        enabled = false                  // default
        type = "redoc"                   // default. "scalar"
        fileName = "openapi.html"        // default
        title = "Order API"              // default: the spec's info.title
    }
    html { enabled = true }               // default (Gradle property kardx.html). false skips the HTML conversion
    openApiInfo {                         // info, externalDocs, tags, x-tagGroups of the spec (OpenAPI info and tags)
        descriptionFile = file("src/docs/openapi-intro.md")
        contact { email = "api@example.com" }
        tag("Users") { description = "Sign-up and profile" }
    }
    postmanCollection { enabled = false } // default
    httpClient {                          // IntelliJ HTTP Client file
        enabled = false                  // default
        fileName = "api.http"            // default
    }
    wireMock { enabled = false }          // default
    openApiSecurity {                     // OAuth2 and OpenID Connect schemes used by openApi { oauth2(...) }
        oauth2 {
            clientCredentials("https://auth.example.com/oauth/token")
            scope("users:read", "Read users")
        }
        openIdConnect("https://auth.example.com/.well-known/openid-configuration")
    }
    apiVersioning {                       // Spring Framework 7 API versions (API versioning)
        header("X-API-Version")          // or queryParameter("version"), mediaTypeParameter("v"), pathSegment()
        splitSpecs = false               // default
    }
    aggregate {                           // apiDocsAggregate (in the aggregating project)
        title = "All APIs"               // default: project name
        version = "1.0.0"                // default: project version
        description = "APIs of all services" // optional. info.description of the merged spec
        outputDirectory = layout.buildDirectory.dir("api-docs-aggregate") // default
    }
    export {                              // apiDocsExport, Maven publication (Exporting specs)
        directory = layout.buildDirectory.dir("exported-specs") // no default (required by apiDocsExport)
        asyncApi = true                  // default false. openApi defaults to true, html to false
        publishSpecs = false             // default
    }
    upload {                              // apiDocsUpload
        url = "https://registry.example.com/apis/{name}/{version}/{type}" // no default (required by apiDocsUpload)
    }
}
```

`asyncApi { bearerAuth() }`, `basicAuth()` and `apiKeyHeader("X-API-KEY")` declare how the WebSocket, STOMP and SSE servers authenticate. They become `components.securitySchemes` of the AsyncAPI spec and an Authentication box in the viewer's Try it out. A key in a query parameter or cookie goes in directly: `securitySchemes.put("tokenQuery", "apiKey:query:access_token")`. Details, including what the browser can send per protocol, are in [Authentication in AsyncAPI](messaging.md#authentication-in-asyncapi).

### Dependencies added automatically

The plugin adds the dependencies below. If you declared the same module yourself (even with a different version, or as a project in the same build), it isn't added. The decision looks only at declared dependencies and is made when a classpath using that configuration is resolved (`withDependencies`), so it doesn't depend on declaration order in the build script and works with the configuration cache.

| Where | Dependency | When | Version |
|---|---|---|---|
| `compileOnly` | `io.github.kardx-project:kardx-annotations` | Always | Same as the plugin |
| `testImplementation` | `io.github.kardx-project:kardx` | When the Kotest extension below is not added | Same as the plugin |
| `testImplementation` | `io.github.kardx-project:kardx-kotest` / `-kotest5` | When the `io.kotest` versions declared in test dependencies are all 6.x / all 5.x (not added if no version is declared or versions are mixed; brings in the library itself) | Same as the plugin |
| `testImplementation` | `org.springframework.restdocs:spring-restdocs-mockmvc` | MVC app: `spring-boot-starter-web`, `spring-boot-starter-websocket`, or `spring-webmvc` declared. MVC even if WebFlux is also declared (Spring Boot starts a servlet app) | Not set (Spring Boot BOM) |
| `testImplementation` | `org.springframework.restdocs:spring-restdocs-webtestclient` | WebFlux app: none of the above, and `spring-boot-starter-webflux` or `spring-webflux` declared | Not set (Spring Boot BOM) |
| `testImplementation` | `com.epages:restdocs-api-spec-mockmvc` / `-webtestclient` | The epages plugin is applied, with the same MVC / WebFlux decision as above | Same as the epages plugin |
| `developmentOnly`, `compileOnly` | `io.github.kardx-project:kardx-boot` | Spring Boot apps ([Startup log](#startup-log)) | Same as the plugin |

- Projects without a web starter (for example, documenting only STOMP messages with just `spring-websocket`) get only the library itself. Message documentation (`MessageDocumentation`) works without MockMvc or WebTestClient.
- The version of the REST Docs client is decided by the project's Spring Boot BOM (the `io.spring.dependency-management` plugin or `implementation(platform("org.springframework.boot:spring-boot-dependencies:…"))`). If nothing manages the version, resolution fails, and the plugin logs one line with the reason and how to fix it.
- The epages plugin version is read from the plugin jar name. If it can't be read (for example, a build that includes it as source), the epages modules are not added and one lifecycle log line is written. If the Spring Boot and epages version lines don't match (Boot 4 + 0.19.x, Boot 3 + 0.20.x), it warns.
- To set a version yourself, declare that module. To turn everything off, use `kardx { addDependencies = false }` and declare the [Dependencies without the plugin](getting-started.md#dependencies-without-the-plugin) yourself. The startup log module is turned off separately with `startupLog = false`.

The plugin passes paths to tests as system properties, so they take precedence over `snippets.directory` in yml. With Maven, use the Maven plugin, which runs these steps for you ([Maven and the command line](maven.md)). With Gradle without the plugin, run the library CLIs directly: `io.github.kardx.asciidoc.DocumentationIndexCli`, `io.github.kardx.asyncapi.AsyncApiCli`, and for OpenAPI post-processing, the viewer, coverage, `.http`, Postman and the spec check, the tooling CLI `io.github.kardx.tooling.cli.ApiDocsCli` ([Without the Maven plugin](maven.md#without-the-maven-plugin)).

## Automatic generation on run

When you run the app, the plugin generates the documents first. You don't need to run `./gradlew apiDocs` separately.

| How you run | Behavior |
|---|---|
| Running `main` in IntelliJ (with the default Gradle build setting) | Runs `apiDocs` right after the Gradle build IntelliJ invokes (`:classes`) |
| `./gradlew bootRun` | Runs `apiDocs` before starting the app |
| Executable jar (`bootJar`) | Always built after `apiDocs` |

- When run this way, failing documentation tests don't block the app from starting. Only documents for passing tests are generated, and failures are recorded in the build log. `./gradlew test` and `build` fail as usual.
- If code hasn't changed, tests and document generation are skipped as up-to-date. Only the first run or a run after a code change takes time.
- Nothing is generated on IntelliJ sync or `./gradlew classes` in a terminal. Nothing is generated when IntelliJ runs tests with its own runner (a `:classes :testClasses` build) either, so running a single test doesn't first run all the documentation tests. For the same reason, nothing is generated when you run a `main` in test sources (for example, a `TestApplication` for Testcontainers).
- If you switch the build setting in IntelliJ to "IntelliJ IDEA", Gradle isn't involved, so documents can't be generated automatically. In that case, run `./gradlew apiDocs`.
- To turn this off, use `kardx { generateOnRun = false }`.

### Startup log

When the app starts, the document URLs are logged at INFO.

```
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (REST Docs    ) http://localhost:8080/docs/index.html
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (Swagger UI   ) http://localhost:8080/swagger-ui/index.html
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (OpenAPI spec ) http://localhost:8080/docs/openapi3.yaml
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (WebSocket/SSE) http://localhost:8080/docs/asyncapi.html
```

The line prefix and item names follow the [documentation language](configuration.md#documentation-language): with `kardx { language = "ko" }` they are `API 문서 (OpenAPI 스펙 )` and so on. The language comes from the app setting `kardx.language`, otherwise from the value the plugin recorded, otherwise English. An unsupported value doesn't stop the app: it logs one warning (`kardx.language: unsupported language 'fr'. Use one of en, ko; falling back to en`) and uses English.

You don't need to put the document path and file names in the app settings. The plugin's `apiDocsAppDocs` records the actual values along with the documents in `META-INF/kardx/docs-locations.properties` (on the app classpath; not a static resource location, so it isn't served over the web), and the startup log reads them as defaults.

| Document | Build setting (the startup log follows when you change it) | Override in app settings |
|---|---|---|
| Document path | `kardx { bootJar { path = "static/api-docs" } }` | `kardx.docs-path` |
| REST Docs | `index.html` (index result) | `startup-log.rest-docs.file` |
| OpenAPI | `openapi3 { outputFileNamePrefix = "api"; format = "json" }` (the file actually produced) | `startup-log.open-api.file` |
| WebSocket·SSE | `kardx { asyncApi { fileName = "async/index" } }` (subpaths allowed) | `startup-log.async-api.file` |
| OpenAPI viewer | `kardx { openApiViewer { fileName = "reference.html" } }` | `startup-log.open-api-viewer.file` |

Change the text, title, and on/off state in app settings. Each entry can be removed with `enabled: false`. IntelliJ autocompletes the keys. If you set a path or file name in yml, yml wins (for use without the plugin or when serving documents from a different location).

```yaml
kardx:
  startup-log:
    enabled: true
    title: API docs                     # line prefix (default follows the language: API docs / API 문서)
    rest-docs:  { label: REST Docs }
    swagger-ui: { label: Swagger UI }   # the URL follows springdoc.swagger-ui.path
    open-api-viewer: { label: OpenAPI viewer }   # when openApiViewer is enabled
    open-api:   { label: OpenAPI spec }
    async-api:  { label: WebSocket/SSE, enabled: true }
```

- For Spring Boot apps, the plugin adds `kardx-boot` to Boot's `developmentOnly`. So the log appears only for IntelliJ runs and `bootRun`, and the module is not included in the executable jar or the test classpath. To log from the executable jar (`java -jar`) too, change it to `kardx { startupLogScope = "runtimeOnly" }`.
- The same module is also added to `compileOnly`, without transitive dependencies. IntelliJ doesn't read configuration metadata from RUNTIME-scope dependencies (`developmentOnly`, `runtimeOnly`), so it would otherwise warn "Cannot resolve configuration property" for `kardx.*` keys in `application.yml`. `compileOnly` is not included in the executable jar, runtime, or test classpath. If `startupLogScope` is already a configuration visible on the compile classpath (`implementation`, `api`, `compileOnly`), it isn't added separately.
- Without the plugin, add it yourself: `developmentOnly("io.github.kardx-project:kardx-boot:0.1")`, and for IDE autocompletion, the same coordinates as `compileOnly(...) { isTransitive = false }`. If you use the plugin but declared the module yourself, the plugin leaves it alone, so add the `compileOnly` yourself.
- It reflects the actual port, `server.servlet.context-path` (`spring.webflux.base-path` for WebFlux apps), `spring.mvc.servlet.path` (servlet apps; both the static docs and springdoc's Swagger UI are served by the `DispatcherServlet`, so their URLs include it), `server.ssl.enabled`, and `springdoc.swagger-ui.path` (the default Swagger UI URL for WebFlux apps is springdoc's `/webjars/swagger-ui/index.html`), and shows only the documents that exist. If there are no documents yet, it logs one line on how to generate them. Nothing is logged for apps without a web server or for MOCK tests.
- To turn it off: the app setting `kardx.startup-log.enabled=false` (for example, in a production profile), or to drop the dependency entirely, `kardx { startupLog = false }`.
- When you change the document path (`bootJar { path = "static/api-docs" }`), you don't need to adjust the app settings. If the file above doesn't exist (using it without the plugin, or documents not generated yet), the `docs-path` default `docs` and the default file names are used.

## Production exposure

With `bootJar.enabled` (default true), documents go into the app resources and the executable jar, so the production app also serves them at `/docs/**`. Documents contain the requests and responses the tests actually sent, so unless you turned on sensitive header masking (`kardx.mask-sensitive-headers`, default false), the tokens and cookies used in tests go out as is. If the records produced by tests include a document written without masking, `apiDocsAppDocs` logs a one-line warning.

```
kardx: API docs are packaged into the application (kardx.bootJar.enabled = true), but 3 documented test class(es) ran without sensitive header masking (e.g. user-api-docs-test). ...
```

The decision is based on the records that tests leave with each class document (`headers-masked` in `build/generated-asciidoc/.restdocs-documents/*.properties`), because the yml settings are known only at test runtime. A record follows the settings in effect when that document was written (`masking.headers` not empty, or `masking.masker` set). The defaults are unchanged, so pick whichever of the following fits your project.

- **Turn on masking**: `kardx.mask-sensitive-headers: true` in your test settings (`src/test/resources/application-docs.yml` and so on), or `masking.maskSensitive()` in code ([Masking sensitive data](configuration.md#masking-sensitive-data)).
- **Leave them out of production builds**: turn off `bootJar.enabled` only in the build that produces the production jar. For example, set `kardx { bootJar { enabled = providers.gradleProperty("withDocs").map(String::toBoolean).orElse(true) } }` and build production with `./gradlew clean bootJar -PwithDocs=false`. Keep `clean` in that build: with `bootJar.enabled = false`, `apiDocsAppDocs` removes the docs earlier runs copied into `build/resources/main/static/docs`, but `bootJar` no longer runs it, so in a reused build directory the old copies are removed only when `apiDocs` runs. With `bootJar.enabled = false`, `bootJar` no longer runs `apiDocs`, so `build/api-docs/` isn't generated for this build; run `./gradlew apiDocs` separately if you need it.
- **Protect the paths**: keep the documents in production but use Spring Security to open `/docs/**` and `/swagger-ui/**` only to authenticated users, or block them with `spring.web.resources.add-mappings` in the production profile. The warning remains, so we recommend turning on masking as well.

## Output locations

These are for `./gradlew apiDocs`. With Maven, use `target` instead of `build`; the Maven plugin's layout is in [Maven and the command line](maven.md).

| Output | Location | Produced by |
|---|---|---|
| REST Docs snippets | `build/generated-snippets/<class-name>/<method-name>/*.adoc` | Tests (`andDocument`, `MessageDocumentation`) |
| AsciiDoc documents | `build/generated-asciidoc/<class-name>.adoc` | Tests (`AutoRestDocumentationExtension`) |
| Enum code document | `build/generated-asciidoc/code/enum-code.adoc` | Tests (`EnumDocumentationExtension`) |
| index | `build/kardx/index/index.adoc` | `apiDocsIndex` task (placed outside `generated-asciidoc`, which test clears) |
| KDoc/Javadoc descriptions | `build/kardx/source-docs/source-docs.json` | `apiDocsSourceDocs` task, only with `sourceDocs { enabled = true }` (read by the tests through `-Dkardx.source-docs`) |
| HTML conversion input | `build/kardx/asciidoc-sources/` | `apiDocsAsciidocSources` task (hand-written documents + generated documents + index in one directory) |
| REST Docs HTML | `build/docs/asciidoc/*.html` | `asciidoctor` task |
| OpenAPI (yaml for Swagger) | `build/api-spec/openapi3.yaml` | `openapi3` task (epages) |
| OpenAPI resource fragments | `build/generated-snippets/<class-name>/<method-name>/resource.json` | Tests (when epages is present) |
| OpenAPI supplementary data | `openapi-schemas.json` (schema name sources) and `openapi-extras.json` (multipart parts, cookies, value body schemas, tag descriptions) in the same directory | Tests. Read by the Gradle plugin |
| AsyncAPI | `build/kardx/asyncapi/asyncapi.yaml`, `asyncapi.html` | `apiDocsAsyncApi` task |
| All of the above gathered | `build/api-docs/` (`index.html`, `openapi3.yaml`, `asyncapi.html` …) | `apiDocs` task |
| OpenAPI viewer (when enabled) | `build/kardx/openapi-viewer/openapi.html` → `build/api-docs/openapi.html` | `apiDocsViewer` task |
| Postman Collection (when enabled) | `build/api-spec/postman-collection.json` → `build/api-docs/` | `postman` task (epages) |
| IntelliJ HTTP Client (when enabled) | `build/kardx/http-client/api.http`, `http-client.env.json` → `build/api-docs/http-client/` | `apiDocsHttpClient` task |
| Endpoint inventory | `build/generated-snippets/.kardx/endpoints/<id>.json` (one file per distinct set of mappings; not an operation directory) | Tests (once per application context) |
| Documentation coverage report | `build/kardx/coverage/coverage.json`, `coverage.md` → `build/api-docs/coverage/` | `apiDocsCoverage` task |
| OpenAPI change report | `build/reports/kardx/openapi-changes.txt` | `apiDocsCheck` task |
| WireMock mappings (when enabled) | `build/generated-snippets/wiremock/mappings/<class-name>/<method-name>.json` | Tests (`andDocument`) |
| WireMock stub jar (when enabled) | `build/libs/<name>-<version>-stubs.jar` | `apiDocsStubsJar` task |
| Aggregated projects | `build/api-docs-aggregate/` (aggregating project) | `apiDocsAggregate` task |
| App resources | `build/resources/main/static/docs/` | `apiDocsAppDocs` task (runs after `apiDocs`. IDE runs and `bootRun` serve from here) |
| Document locations (for the startup log) | `build/resources/main/META-INF/kardx/docs-locations.properties` | `apiDocsAppDocs` task. A location not served over the web |
| Document records (for the index and the production exposure warning) | `build/generated-asciidoc/.restdocs-documents/<class-name>.properties` | Tests (when writing the class document) |
| Inside the executable jar | `BOOT-INF/classes/static/docs/` | `bootJar` task (contains the resources above) |

When the app is running, open the URLs listed in [Viewing in a browser](README.md#viewing-in-a-browser). They are the same for the executable jar, `bootRun`, and running `main` in IntelliJ. `bootRun` and IntelliJ runs generate the documents automatically before starting the app ([Automatic generation on run](#automatic-generation-on-run)).

Generated files (AsciiDoc, index, AsyncAPI yaml and html, the OpenAPI global header settings file) are written to a temporary file and then atomically swapped in. Even when several test JVMs write the same file, no half-written file is read. Permissions are the same as for regular files, following the umask.

Incremental builds: when you rerun tests and the snippets and generated document contents are the same, `apiDocsIndex`, `asciidoctor`, and `apiDocsAsyncApi` are skipped as UP-TO-DATE. If you document values that change on every run (timestamps, random IDs, and so on), they rerun every time, so use fixed example values ([message header example](messaging.md#websocket--stomp)).

Build time: most of the doc generation time goes to the HTML conversion (`asciidoctor`), not to this plugin. In the Boot 4 sample, a full rerun of tests and docs takes about 16 s: about 8 s for the tests (mostly JVM and Spring startup), about 7 s for `asciidoctor` (AsciidoctorJ runs on JRuby; about 0.5 s of that is saved by the default `asciidoctorJvmArgs`, which stops the short-lived conversion JVM at the C1 compiler), and under 0.3 s for all of this plugin's tasks together. OpenAPI, the viewer, Postman, `.http`, and the coverage report don't need the HTML conversion, so during local development you can turn it off with `html { enabled = false }`. The default comes from the Gradle property `kardx.html` (`true`/`false`, any other value fails the build), and `true` without it; a value set in the build script wins over the property. When it is off, `apiDocs` runs neither `asciidoctor` nor `apiDocsIndex`, removes the HTML of earlier runs from its output and the app resources, and the location list shows `REST Docs HTML    skipped`. OpenAPI, AsyncAPI, the viewer, Postman, `.http`, coverage and the copy into the app resources work as before, and running `./gradlew asciidoctor` directly still converts.

```kotlin
kardx {
    html {
        enabled = providers.environmentVariable("CI").isPresent // convert only in CI
    }
}
```

```properties
# ~/.gradle/gradle.properties (only on your machine; or once: ./gradlew apiDocs -Pkardx.html=false)
kardx.html=false
```

`kardx.asciidoctorJvmArgs` (default `["-XX:TieredStopAtLevel=1"]`) is passed to the JVM that `asciidoctor` forks for the conversion. Set it to an empty list (`asciidoctorJvmArgs = emptyList()`) to use the JVM defaults, for example when you have so many documents that the conversion runs long enough for the C2 compiler to pay off, or add to it (`asciidoctorJvmArgs.add("-Xmx1g")`). It isn't used when `asciidoctor` runs in-process (`IN_PROCESS`). Other JVM settings can still go in `tasks.asciidoctor { jvm { } }`.

Change the locations in one place, the [Gradle plugin](#gradle-plugin) settings. The plugin passes the same paths to tests, Asciidoctor, epages, and AsyncAPI, so they never drift apart.

```kotlin
kardx {
    snippetsDirectory = layout.buildDirectory.dir("docs/snippets")
    asciidocDirectory = layout.buildDirectory.dir("docs/asciidoc")
    outputDirectory = layout.buildDirectory.dir("docs/site")
    bootJar { path = "static/api-docs" }       // /api-docs/index.html in the app
}
```

### Directories the plugin empties

The plugin deletes some directories before writing to them: `snippetsDirectory` and `asciidocDirectory` before `test` (with `cleanBeforeTest`), `outputDirectory` when `apiDocs` syncs into it, and `aggregate.outputDirectory` before `apiDocsAggregate`. Because these are settings you can point anywhere, each is checked first:

- Always refused: the project directory, the root project directory, the build directory and their parents, and anything inside or containing a source directory (`src/`, `asciidocSourceDirectory`).
- Inside `build/`: allowed. All defaults are there, so nothing changes for them.
- Outside `build/`: allowed only if the directory doesn't exist, is empty, or contains the marker file `.kardx-output`. After writing, the plugin leaves that empty marker file there, so the next run recognizes the directory as its own. The marker is not copied into the app or the aggregate.

For example, to write the aggregate into the repository's `docs/` folder for GitHub Pages (Deploy from a branch), use an empty `docs/` the first time, or create an empty `docs/.kardx-output` yourself when the folder already holds files you are fine to lose. A folder with other files and no marker fails the build instead of being wiped:

```
kardx.aggregate.outputDirectory points to /work/repo/docs, which is outside the build directory and holds files the plugin did not write. ...
```

`openapi3` only deletes spec files the plugin wrote: `<prefix>.yaml|yml|json`, version specs (`<prefix>-v<version>.*`, version starting with a digit) and the ones listed in `build/kardx/openapi-version-specs.txt`.

### Docs in the app resources

For Spring Boot apps, `apiDocsAppDocs` copies the documents into `build/resources/main/<bootJar.path>`, which IDE runs, `bootRun` and the executable jar serve.

- `bootJar.path` must be a sub directory of the app resources such as `static/docs` or `public/api-docs`. `\` is read as `/`, and `.` segments, empty segments and a trailing `/` are dropped (`./static//docs/` → `static/docs`). An empty value, an absolute path, a drive letter or `..` fails at configuration time (`kardx.bootJar.path must be a directory inside the app's resources such as 'static/docs' or 'public/api-docs', got '...'`), and so does a Spring resource root itself (`static`, `public`, `resources`, `META-INF/resources`, `META-INF`), because the docs would mix with the app's own `index.html` and other files there.
- Files the app keeps in the same directory (from `src/main/resources/static/docs/`, say) are not deleted. The plugin records the files it copied in `build/kardx/app-docs-files.txt` and on the next run removes only those of them that are no longer produced. A file with the same name as a document is overwritten.
- When you change `bootJar.path`, the copies at the previous path are removed by the same list. With `bootJar.enabled = false`, `apiDocsAppDocs` removes all earlier copies and the startup log's locations file. `bootJar` doesn't run `apiDocsAppDocs` then, so see [Production exposure](#production-exposure) for production builds.

### Reproducible snippets

Snippets, WireMock mappings and the spec are written again on every test run. If their contents are the same, the later tasks (`asciidoctor`, `apiDocsIndex`, `apiDocsAsyncApi`, …) stay UP-TO-DATE; values that change from run to run make them rerun and add noise to reviews of committed specs.

- **`Date` header.** Removed from documented responses by default (`http.removedResponseHeaders`, [List of settings](configuration.md#list-of-settings)), because a real server (`bindToServer`) sends a new value every time. A header you document with `responseHeaders(...)` is kept. For other headers that change per run, such as `X-Request-Id`, add them in code (`http.removedResponseHeaders += "X-Request-Id"`); the yml key `kardx.removed-response-headers` replaces the default list instead of adding to it, so list `Date` as well (`Date, X-Request-Id`).
- **Cookie `Expires`.** A `Set-Cookie` value with `Max-Age` loses its `Expires` attribute, which is computed from the current time (`Max-Age` takes precedence in browsers anyway). This follows the same setting: with `removed-response-headers: none` (or `emptySet()`) cookies are kept as received.
- **Spring Data `Page` responses on Boot 3.** A `PageImpl` serialized directly by Jackson 2 doesn't have a fixed property order, so the order of fields like `pageable`, `sort` and `totalElements` can change between runs (Jackson 3 on Boot 4 already sorts them). The library can't fix this; choose one in the app:
  - `spring.jackson.mapper.sort-properties-alphabetically=true` sorts every response's properties. The JSON paths stay the same, but the order of all responses changes to alphabetical.
  - `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` serializes pages as `PagedModel` (`content`, `page.size`, `page.number`, `page.totalElements`, `page.totalPages`), which is stable and recommended by Spring Data. It changes the JSON shape, so documented paths such as `pageable.pageNumber` or `sort.sorted` change too.
- Timestamps and random IDs in bodies: use fixed values in tests or fixed example values ([message header example](messaging.md#websocket--stomp)).
