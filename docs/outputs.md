**English** | [한국어](ko/outputs.md)

# Viewer, clients, stubs and sharing

[Document list](README.md)

<!-- toc -->
**Contents**

- [OpenAPI viewer (Redoc · Scalar)](#openapi-viewer-redoc--scalar)
  - [Differences from Swagger UI](#differences-from-swagger-ui)
  - [Choosing between redoc and scalar](#choosing-between-redoc-and-scalar)
  - [Scripts are loaded from a CDN](#scripts-are-loaded-from-a-cdn)
  - [File name and startup log](#file-name-and-startup-log)
- [Postman Collection](#postman-collection)
- [IntelliJ HTTP Client](#intellij-http-client)
- [WireMock stub](#wiremock-stub)
- [Exporting specs](#exporting-specs)
  - [apiDocsExport](#apidocsexport)
  - [apiDocsUpload](#apidocsupload)
  - [Maven publication](#maven-publication)
- [Aggregating multiple projects](#aggregating-multiple-projects)
<!-- /toc -->

Optional outputs built from the same documentation tests: a static OpenAPI viewer, a Postman Collection, an IntelliJ HTTP Client file, WireMock stubs, exported or uploaded specs, and one page for several projects. They are off by default or run only when you run them. The plugin settings and tasks are in [Build and outputs](build.md).

## OpenAPI viewer (Redoc · Scalar)

Builds a single HTML file (`openapi.html`) that shows the generated OpenAPI spec. The spec is embedded in the page, so you can open the file by double-clicking it without the app, and you can share just that one file by email, messenger, or wiki attachment. Off by default.

```kotlin
kardx {
    openApiViewer {
        enabled = true              // default false
        type = "redoc"              // default. "scalar"
        fileName = "openapi.html"   // default. Subpaths work too: "api/reference.html"
        title = "Order API"         // browser tab title. Default: the spec's info.title
    }
}
```

When you run `./gradlew apiDocs`, the `apiDocsViewer` task builds the page from the `openapi3` result and gathers it with the other documents. The `com.epages.restdocs-api-spec` plugin is required (it's what builds the spec).

| Result | Location |
|---|---|
| Build | `build/api-docs/openapi.html` |
| App (IDE run, `bootRun`, executable jar) | `http://localhost:8080/docs/openapi.html` |
| Startup log | `API docs (OpenAPI viewer) http://localhost:8080/docs/openapi.html` (with `language = "ko"`: `API 문서 (OpenAPI 뷰어)`) |

### Differences from Swagger UI

| | Swagger UI | Static viewer (Redoc · Scalar) |
|---|---|---|
| Purpose | Calling the API directly during development (Try it out) | A readable reference document. Scalar can send requests too |
| What you need | The app dependency `springdoc-openapi-starter-webmvc-ui` (`-webflux-ui` for WebFlux) and the `springdoc.swagger-ui.url` setting | One line of Gradle plugin configuration. No app dependency |
| Opening without a server | Not possible (the app must be running) | Possible (the spec is inside the file) |
| Distribution | With the app (`/swagger-ui/index.html`) | The app's `/docs/openapi.html`, or a single file via static hosting or attachment |
| Scripts | Bundled in the springdoc jar | Loaded by the browser from the unpkg CDN (below) |

Both show the same `openapi3.yaml`, so you can keep both enabled.

### Choosing between redoc and scalar

- **`redoc`** (default): a three-pane layout with the menu on the left, descriptions in the middle, and request and response examples on the right. It can't send requests, so it suits read-only documents handed to external partners or planners.
- **`scalar`**: the same reference layout plus request sending (a test client). A page opened from the app sends requests to the spec's `servers` (for example, `openapi3 { setServer("/") }`), so it can replace Swagger UI's Try it out. When opened as a file, you need to change the server URL to the actual app URL, and the browser's CORS restrictions apply. It starts with the developer toolbar and the AI question feature (which sends the spec to an external service) turned off.

### Scripts are loaded from a CDN

The page contains only the spec; the browser loads the viewer script from the unpkg CDN when the page is opened. The version and integrity hash (SRI) are pinned so the file can't be swapped out.

| type | Script (MIT) |
|---|---|
| `redoc` | `https://unpkg.com/redoc@2.5.4/bundles/redoc.standalone.js` |
| `scalar` | `https://unpkg.com/@scalar/api-reference@1.76.4/dist/browser/standalone.js` |

- In environments without internet access (air-gapped networks, blocked CDNs), you see only the notice "Could not load the Redoc script (unpkg.com)." (with `language = "ko"`: "Redoc 스크립트(unpkg.com)를 불러오지 못했습니다") In that case, view the `openapi3.yaml` in the same directory with an internal tool, or use Swagger UI (its scripts are bundled in the jar).
- The spec is embedded as a script variable in the page; only the script file is requested from the CDN.

### File name and startup log

- If you change `fileName`, you don't need to change app settings: `apiDocsAppDocs` records the file name for the startup log ([Startup log](build.md#startup-log)). Set `kardx.startup-log.open-api-viewer.file` only to override it. The label in the log line is `startup-log.open-api-viewer.label`; to turn it off, use `startup-log.open-api-viewer.enabled: false` ([Startup log](build.md#startup-log)).
- The startup log writes this line only when the file exists, so it doesn't appear in projects that haven't enabled the viewer.
- If you enable it in the project that aggregates multiple projects, a viewer for the merged spec is built too ([Aggregating multiple projects](#aggregating-multiple-projects)).

## Postman Collection

Adds the result of the epages `postman` task (Postman Collection v2.1) to `apiDocs`.

```kotlin
kardx {
    postmanCollection { enabled = true }   // build/api-docs/postman-collection.json, /docs/postman-collection.json in the app
}

postman {                                    // epages settings (optional)
    title = "Order API"
    baseUrl = "http://localhost:8080"        // default http://localhost
}
```

- `postman` reads the same fragment copy as `openapi3` (`apiDocsOpenApiSnippets`: security requirements and schema names aligned). OpenAPI post-processing such as global header → `apiKey` conversion and type and constraint enrichment doesn't apply to a Collection, so it isn't done (headers go into the request examples as is).
- The fake token that the library inserts so epages can detect Bearer auth is replaced with `Bearer {{bearerToken}}`, and a Collection variable `bearerToken` is declared. Set a token in this variable once in Postman, and every request uses it.
- Optional headers that weren't in the request are added with an empty value and `disabled: true`, as the v2.1 schema expects.
- The file name follows epages `postman { outputFileNamePrefix }`.

## IntelliJ HTTP Client

Writes the documented requests as an [HTTP Client](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) file (`.http`). Open it in IntelliJ IDEA and send each request from the gutter, without importing anything. Off by default. Like the other OpenAPI features it needs the epages plugin (the task `apiDocsHttpClient` exists only then), but unlike the Postman Collection it doesn't need the epages `postman` task.

```kotlin
kardx {
    httpClient {
        enabled = true          // default false
        fileName = "api.http"   // default
    }
}
```

| Result | Location |
|---|---|
| Requests | `build/api-docs/http-client/api.http` |
| Environment | `build/api-docs/http-client/http-client.env.json` |
| App (IDE run, `bootRun`, executable jar) | `http://localhost:8080/docs/http-client/api.http` |

There is one request per documented operation, made from the request a test actually sent (`http-request.adoc`): the method, the URL with `{{baseUrl}}` in place of the host, the headers and the body. When several tests document the same operation, the one with a success response is used first, as for OpenAPI. Each request starts with `### <title>` (the operation summary, or the operationId without one), then a `# @name <operationId>` line, so you can refer to its response from a later request. Requests are in path and method order.

```http
### Create product
# @name createProduct
POST {{baseUrl}}/api/v1/products
Content-Type: application/json

{
  "name": "Keyboard",
  "price": 59000
}
```

`http-client.env.json` defines one environment, `local`, with `baseUrl` set to `http://localhost:8080`. Pick it in the environment selector of the editor. For other servers, add environments to this file in your project or to your own copy; keep tokens in `http-client.private.env.json`, which IntelliJ reads alongside it and which you don't commit.

- Every header the test sent is kept except `Host` and `Content-Length`, with the values the test sent.
- An `Authorization: Bearer …` header becomes `Authorization: Bearer {{bearerToken}}`, and the environment file then has `"bearerToken": ""`; put a token there (or in the private file). Other tokens and cookies used in tests are written as they are unless [sensitive header masking](configuration.md#masking-sensitive-data) is on.
- Values masked for the docs (`****`) become variables, so a request doesn't send `****` and fail authentication: a query parameter, a form field (`application/x-www-form-urlencoded`) or a cookie becomes a variable with its own name (`access_token={{access_token}}`, `SESSION={{SESSION}}`), and a fully masked header or an authorization header that keeps only its scheme (`Basic ****`) becomes the lower-case header name (`X-Api-Key: {{x_api_key}}`, `Authorization: Basic {{authorization}}`). Names keep only letters, digits and `_`. Each variable is declared empty in `http-client.env.json` and in `http-client.private.env.json`, which is written whenever there is a secret variable (`bearerToken` included); fill in the real values in the private file. No real value is ever written.
- Only the default mask `****` is recognized. If you changed the mask with `masking.replacement` or a `ValueMasker`, those values stay as they are in the file.
- The file is gathered by `apiDocs` like the Postman Collection, so it is also in the app resources, the executable jar, and an HTML export (`export { html = true }`).

## WireMock stub

Makes documentation tests also produce a [WireMock](https://wiremock.org/) mapping (stub) JSON for each request and response. Other services or frontends can then mimic this API with responses verified by tests.

```kotlin
kardx {
    wireMock { enabled = true }   // passes kardx.wire-mock-stubs=true to tests
}
```

| Result | Location |
|---|---|
| Mappings | `build/generated-snippets/wiremock/mappings/<class-name>/<method-name>.json` (one per `andDocument`) |
| stub jar | `./gradlew apiDocsStubsJar` → `build/libs/<name>-<version>-stubs.jar`, containing `META-INF/<group>/<name>/<version>/mappings/` |

```bash
# Start WireMock standalone directly (use the parent of the mappings directory as root-dir)
java -jar wiremock-standalone-3.13.2.jar --port 9999 --root-dir build/generated-snippets/wiremock
curl http://localhost:9999/api/v1/products/1
```

- Requests are matched by method, path (the actual value the test sent, `urlPath`), query parameters (`equalTo`), and JSON body (`equalToJson`, ignoring array order and extra fields). Headers are not matched (masked authentication headers can't match the real values). So when tests document the same path and body with only different statuses (for example, 200 and 401 with and without authentication), either one responds.
- Responses contain the status, headers (except `Content-Length` and `Transfer-Encoding`), and body. Non-text bodies (images and so on) are stored as `base64Body`. Masked header values (`Set-Cookie: SESSION=****`) are stored masked.
- The mapping `id` is derived from the document identifier, so it stays the same when regenerated.
- The stub jar follows the layout of Spring Cloud Contract Stub Runner, so the project needs a `group` (`group = "com.example"`). Without one, `apiDocsStubsJar` fails with `apiDocsStubsJar: project.group is not set, …`. `group` and `version` are read when the task runs, so setting them later in the build script works. To publish it, add it as an artifact, as in `publishing { publications { create<MavenPublication>("stubs") { artifact(tasks.named("apiDocsStubsJar")) } } }`.
- The library writes the mapping format itself, so no WireMock or Spring Cloud Contract dependency is needed, and Boot 3 and 4 behave the same. You can also turn it on without the plugin with yml `kardx.wire-mock-stubs: true`.
- `MessageDocumentation` (WebSocket, STOMP) documents don't produce mappings.

## Exporting specs

There are three ways to send the generated specs out of the build. None of them is attached to `build`, `check`, `apiDocs`, or `bootRun`; they run only when you run them directly, and they first run only the tasks that produce the selected outputs (OpenAPI → `openapi3`, AsyncAPI → `apiDocsAsyncApi`, HTML → `apiDocs`).

| Way | Run | Result |
|---|---|---|
| Copy to a directory | `./gradlew apiDocsExport` | Copies to `export.directory` under the names you choose |
| Upload over HTTP | `./gradlew apiDocsUpload` | `PUT`s (or `POST`s) to `upload.url` |
| Attach to the Maven publication | `./gradlew publish` (`maven-publish`) | With `export.publishSpecs = true`, uploads `<name>-<version>-openapi.yaml` and so on next to the jar |

```kotlin
import java.time.Duration

kardx {
    export {
        directory = layout.buildDirectory.dir("exported-specs")   // required
        asyncApi = true
        // publishSpecs = true    // with maven-publish, publish also uploads <name>-<version>-openapi.yaml
    }
    upload {
        url = "https://registry.example.com/apis/{name}/{version}/{type}"
        headers.put("Authorization", providers.environmentVariable("API_REGISTRY_TOKEN").map { "Bearer $it" })
        timeout = Duration.ofSeconds(30)
        retries = 2
    }
}
```

Inside the `kardx { }` block, a fully qualified name such as `java.time.Duration` doesn't compile, because `java` resolves to the Java plugin extension. `import` it at the top of the build script as above.

File names and URLs take placeholders.

| Placeholder | Value |
|---|---|
| `{name}` | Project name |
| `{version}` | Project version |
| `{type}` | `openapi` or `asyncapi` |
| `{ext}` | Spec file extension (`yaml`, `json`) |

### apiDocsExport

| `export { }` setting | Default | Description |
|---|---|---|
| `directory` | None (required) | The directory to export to. Running without it fails. Use a directory that doesn't hold other files |
| `openApi` | `true` | The OpenAPI spec (the `openapi3` output, after post-processing) |
| `asyncApi` | `false` | The AsyncAPI spec (the `apiDocsAsyncApi` output). If selected and there are no messages, it fails and explains why. With `asyncApi { enabled = false }` it fails with `No AsyncAPI spec: kardx.asyncApi.enabled is false. …` instead of exporting a spec left over from an earlier build |
| `html` | `false` | The whole `apiDocs` directory (including HTML) |
| `openApiFileName` | `{name}-openapi3.{ext}` | Relative to the directory |
| `asyncApiFileName` | `{name}-asyncapi.{ext}` | Relative to the directory |
| `htmlDirectoryName` | `{name}-api-docs` | The subdirectory the docs directory is copied to when `html` is on |
| `publishSpecs` | `false` | The Maven publication below |
| `publicationName` | None | The publication to attach the specs to. If absent, every `MavenPublication` (except Gradle plugin marker publications) |

The task doesn't track its outputs, so it copies every time it runs.

### apiDocsUpload

| `upload { }` setting | Default | Description |
|---|---|---|
| `url` | None (required) | Where to upload the OpenAPI spec (http or https). Also the default of `asyncApiUrl` |
| `asyncApiUrl` | `url` | Where to upload the AsyncAPI spec |
| `method` | `PUT` | `PUT` or `POST` |
| `headers` | None | Request headers. Pass the values through `providers.environmentVariable(...)` or `providers.gradleProperty(...)` so that no secret is written in the build script. A `Content-Type` here replaces the one chosen from the extension (`application/yaml`, `application/json`) |
| `openApi` | `true` | Whether to upload the OpenAPI spec |
| `asyncApi` | `false` | Whether to upload the AsyncAPI spec. Fails like `apiDocsExport` when `asyncApi { enabled = false }` |
| `timeout` | 30 seconds | Connect and response timeout |
| `retries` | `0` | Number of retries |

- Only the JDK HTTP client is used (no extra dependency).
- A non-2xx response fails the task with the status code and the first 2000 characters of the response body.
- Header values never appear in logs or error messages; only header names do (`headers: [Authorization, Content-Type]`). Header values echoed in the response body are masked too.
- Secrets inside the URL are masked the same way: the user info (`https://user:password@host/...`) and every query parameter value (`?token=...`) appear as `****` in logs, retry warnings and error messages, both as written and URL-decoded. Prefer a header for credentials anyway; a URL can end up in proxy or server access logs. Header values are read at execution time, so with the configuration cache a changed token is still sent.
- Redirects are not followed (so headers don't go to another server).
- Retries happen only on connection failures, 429, and 5xx, waiting 1, 2, 4… seconds (at most 30).
- It's a side-effect task without outputs, so it's never UP-TO-DATE and sends on every run.
- Uploading both specs to the same URL is an error. Put `{type}` in `url` or set `asyncApiUrl` separately.

### Maven publication

With the `maven-publish` plugin and `export.publishSpecs = true`, the selected specs (`export.openApi`, `export.asyncApi`) are attached to Maven publications with the classifiers `openapi` and `asyncapi`. `publish` and `publishToMavenLocal` upload `<name>-<version>-openapi.yaml` and `<name>-<version>-asyncapi.yaml` next to the jar. HTML is not attached.

```kotlin
plugins {
    `maven-publish`
}

publishing {
    publications { create<MavenPublication>("maven") { from(components["java"]) } }
}

kardx {
    export { publishSpecs = true }
}
```

The intermediate task `apiDocsPublicationSpecs` collects the specs under fixed names in `build/kardx/publication`, and the publishing tasks depend on it. Without `maven-publish`, nothing happens.

## Aggregating multiple projects

When several subprojects each produce documents with this plugin, you can gather them in one project (usually the root) into a single list page and a merged OpenAPI spec. The aggregating project doesn't need the Java plugin.

```kotlin
// root build.gradle.kts
plugins {
    id("io.github.kardx-project")
}

dependencies {
    restdocsAggregation(project(":order"))
    restdocsAggregation(project(":member"))
}

kardx {
    aggregate {
        title = "Shopping API"  // default: project name
        version = "1.0.0"     // default: project version
    }
    openApiViewer { enabled = true }   // also builds a viewer for the merged spec (optional)
}
```

```bash
./gradlew apiDocsAggregate   # runs apiDocs in each project and gathers them in build/api-docs-aggregate
```

| Result (`build/api-docs-aggregate/`) | Contents |
|---|---|
| `index.html` | REST Docs, OpenAPI, AsyncAPI, and Postman links per project |
| `openapi3.yaml` | Merged spec (when some project produced OpenAPI) |
| `openapi.html` | Merged spec viewer (when `openApiViewer` is enabled in the aggregating project) |
| `order/`, `services/member/` | Copy of each project's `build/api-docs` (project path `:services:member` → `services/member`, the root project → `root`). If two projects map to the same directory (the root project and a project named `:root`), the task fails (`apiDocsAggregate: projects : and :root both go to the directory root/ …`) |

Projects are connected only in the way Gradle recommends (variants). Projects that produce documents publish the `apiDocs` result as the configuration `restdocsApiDocsElements` (attributes `Category=documentation`, `DocsType=restdocs-api-docs`), and the aggregating project resolves its `restdocsAggregation` declarations to receive them. It never references other projects' tasks or `build` directories directly, so it works with the configuration cache and parallel builds.

Merge rules (projects are processed in project path order):

- **Paths**: merged. If the same path and method exist in two projects, it fails, because there's no way to know which one is right. Exclude one side with `openApi { excluded = true }` or split the paths. Only each project's main spec (`openapi3.yaml`) is merged: the per-version specs of `apiVersioning { splitSpecs = true }` (`openapi3-v1.yaml`) repeat its operations, so they are only linked on the list page.
- **Schemas and other components**: if the name and content are the same, one copy is kept. If the name is the same but the content differs, the later project's one is renamed to `<project name in PascalCase><name>` (with 2, 3, … appended if that name is also taken) (`Item` in `:member` → `MemberItem`, `bearerAuth` → `MemberBearerAuth`), and that project's `$ref`s and security requirements are renamed too. Schemas whose content changed because they reference a renamed schema are renamed as well. The renamed names appear in the build log.
- **operationId**: on collision, the later project's one is renamed to `<project name>-<operationId>`.
- **Top-level security**: not placed at the top level of the merged document; instead it's copied down to that project's operations that have no `security`. This keeps one project's authentication requirement from spreading to another project's public APIs.
- **tags and servers**: merged with duplicates removed by name or URL. The first description wins. If projects have different server URLs, they all go into the top-level `servers`, so align `openapi3 { setServer(...) }` if needed.
- **info**: `aggregate { title, version, description }`.
- AsyncAPI is not merged; the list page links to each project's viewer (or the yaml if there's no viewer).
