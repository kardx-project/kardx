**English** | [한국어](ko/README.md)

# Documentation

The user guide for kardx. If you are new, start with [Getting started](getting-started.md).

## Viewing in a browser

Start the app (executable jar, `bootRun` and IDE runs all work the same) and open the addresses below. Without the app, open the files in `build/api-docs/`.

| Document | Address |
|---|---|
| Full API list (REST Docs) | `http://localhost:8080/docs/index.html` |
| A single API | `http://localhost:8080/docs/<test-class>.html#<test-method>` (Kotest: `<spec>.html#<test-name>`) |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI spec | `http://localhost:8080/docs/openapi3.yaml` |
| AsyncAPI (WebSocket, STOMP, SSE) | `http://localhost:8080/docs/asyncapi.html` |
| AsyncAPI Try it out (subscribing to SSE streams that never end, and so on) | `http://localhost:8080/docs/asyncapi.html#try-it-out` |
| Enum codes | `http://localhost:8080/docs/code/enum-code.html` |
| OpenAPI static viewer (when enabled) | `http://localhost:8080/docs/openapi.html` |
| Postman Collection (when enabled) | `http://localhost:8080/docs/postman-collection.json` |
| IntelliJ HTTP Client (when enabled) | `http://localhost:8080/docs/http-client/api.http` |

You can change `/docs` and the file locations with the [Gradle plugin](build.md#gradle-plugin) settings. Detailed addresses for each document are at the start of that document.

## Document list

| What you want to do | Document | Contents |
|---|---|---|
| Install and create your first document | [Getting started](getting-started.md) | Installation, supported build setups (Kotlin and Groovy DSL, Gradle without the plugin, Maven), setting up a new project end to end (Gradle, yml, tests, running), Boot 3 differences, slice tests (`@WebMvcTest`) |
| Document requests and responses | [Documenting HTTP APIs](http-api.md) | The `andDocument { }` DSL (including the MockMvc Kotlin DSL and `MockMvcTester`), automatic field extraction and preview, relaxed verification, verification failure messages, common response wrapper, multiple status codes, declared error responses, what goes into OpenAPI, MockMvc helper functions |
| Document a WebFlux app or use RestTestClient | [WebTestClient · RestTestClient](webtestclient.md) | The same `andDocument { }` with WebTestClient and RestTestClient (Boot 4), supported features per client, dependencies for WebFlux apps |
| Document with Kotest specs | [Kotest](kotest.md) | Kotest 6.x and 5.x artifacts and registration, examples per spec style, rules for titles and snippet names, mixing with JUnit, limitations |
| Document SSE, WebSocket and STOMP | [SSE · WebSocket · AsyncAPI](messaging.md) | Server-Sent Events, streams that never end, WebSocket and STOMP messages, `StompTestClient`, handler tests, AsyncAPI, authentication and Try it out |
| Change settings | [Configuration](configuration.md) | `configure` / `withSettings`, settings list, `application.yml` keys, header masking, documentation language |
| Run the build and find the outputs | [Build and outputs](build.md) | Which command to run when, Gradle plugin options and tasks (one table), dependencies added automatically, automatic generation on run, startup log, production exposure, output locations, directories the plugin empties, reproducible snippets |
| Shape the OpenAPI spec | [OpenAPI and Swagger UI](openapi.md) | Swagger UI, `openApi { }` per test, `openApiInfo` (info, contact, license, tags, `x-tagGroups`), OAuth2 and OpenID Connect schemes, API versioning |
| Catch API changes and undocumented endpoints | [Spec check and coverage](checks.md) | `apiDocsCheck` and `apiDocsBaseline`, what counts as a breaking change, `apiDocsCoverage`, endpoints the inventory doesn't see |
| Hand the API to others | [Viewer, clients, stubs and sharing](outputs.md) | Redoc and Scalar viewer, Postman Collection, IntelliJ HTTP Client, WireMock stubs, `apiDocsExport`, `apiDocsUpload`, Maven publication, aggregating multiple projects |
| Use Maven or the command line | [Maven and the command line](maven.md) | The Maven plugin (goals, parameters, the recipe with asciidoctor-maven-plugin), the JSON configuration file, spec check, KDoc descriptions, `exec-maven-plugin` and `java -jar` without the plugin, CLI commands and options, what stays Gradle only, troubleshooting |
| Run the pipeline in CI | [CI recipes](ci.md) | GitHub Actions pull request check (breaking changes, undocumented endpoints), pull request comment with the reports, updating the baseline, publishing to GitHub Pages or a registry, speed, multiple projects, GitLab CI |
| Understand generated documents and the index | [AsciiDoc documents](asciidoc.md) | Automatic document generation, section order, snippets per test shape, index, Enum code documents |
| Change how documents look | [Customizing snippets and document layout](customizing-snippets.md) | Choosing which snippets to include, templates and variables, custom snippets, `DocumentGenerator`, use in hand-written AsciiDoc, request and response preprocessing, Enum table format |
| Migrate existing REST Docs tests | [Migrating from Spring REST Docs](migration.md) | Dependencies, test base class, `document()` mapping table, mixing with existing snippets and index, epages `resource()`, reusing Swagger descriptions |
| Fix errors | [Troubleshooting](troubleshooting.md) | Common errors and fixes |
| Let an AI assistant write the tests | [AI assistants](ai.md) | `llms.txt`, `llms-full.txt`, the Claude Code skill, rules files for Copilot, Cursor and Codex |
| Work on this repository | [Development](development.md) | Repository layout, the order of checks after a change, build, verification scripts |
| See the module layout | [Modules](architecture.md) | Role and contents of each module, which artifacts to add, how documents are produced |

The change history is in [CHANGELOG](../CHANGELOG.md).
