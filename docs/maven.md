**English** | [한국어](ko/maven.md)

# Maven and the command line

[Document list](README.md)

<!-- toc -->
**Contents**

- [What you get](#what-you-get)
- [Setup](#setup)
  - [Dependencies](#dependencies)
  - [Build plugins](#build-plugins)
- [Goals and parameters](#goals-and-parameters)
  - [api-docs](#api-docs)
  - [check](#check)
  - [baseline](#baseline)
  - [source-docs](#source-docs)
- [The JSON configuration file](#the-json-configuration-file)
- [Checking the spec](#checking-the-spec)
- [KDoc descriptions](#kdoc-descriptions)
- [Without the Maven plugin](#without-the-maven-plugin)
  - [exec-maven-plugin](#exec-maven-plugin)
  - [java -jar](#java--jar)
  - [Commands and options](#commands-and-options)
- [Still Gradle only](#still-gradle-only)
- [Troubleshooting](#troubleshooting)
  - [The OpenAPI generator is not on the classpath](#the-openapi-generator-is-not-on-the-classpath)
  - [No snippets directory](#no-snippets-directory)
  - [HTML has no index or the snippets are missing](#html-has-no-index-or-the-snippets-are-missing)
<!-- /toc -->

The documentation tests work with any build tool. What happens after the tests (index, OpenAPI refinement, viewer, coverage, `.http`, Postman, spec check) is in the Gradle plugin for Gradle, in the Maven plugin `io.github.kardx-project:kardx-maven-plugin` for Maven, and in a command line tool for anything else. All three run the same code (the `io.github.kardx-project:kardx-tooling` artifact), so the files they write are the same.

The library is not on Maven Central yet. Clone the repository and run `./gradlew publishToMavenLocal` first; Maven reads `~/.m2/repository` without further setup. A working project is [samples/maven-boot-4](../samples/maven-boot-4) (`./mvnw verify`).

## What you get

| Feature | Gradle plugin | Maven plugin | CLI (`java -jar`, `exec-maven-plugin`) |
|---|---|---|---|
| Index document and AsciiDoc sources for Asciidoctor | Yes | `api-docs` goal | No (core `DocumentationIndexCli`, [Index document](asciidoc.md#index-document)) |
| REST Docs HTML | With `org.asciidoctor.jvm.convert` | `asciidoctor-maven-plugin` (your configuration, below) | Your own Asciidoctor setup |
| AsyncAPI spec and viewer | Yes | `api-docs` goal, when tests left `*.asyncapi.json` | No (core `AsyncApiCli`, [SSE · WebSocket · AsyncAPI](messaging.md)) |
| OpenAPI spec, refined (types, constraints, defaults, `oneOf`, multipart, cookies, security, operationIds) | Yes | `api-docs` goal | `openapi` |
| OpenAPI viewer, IntelliJ HTTP Client, Postman | Yes | `api-docs` goal, when turned on | `viewer`, `http`, `postman` |
| Documentation coverage | Yes | `api-docs` goal | `coverage` |
| Spec check against a baseline | `apiDocsCheck`, `apiDocsBaseline` | `check`, `baseline` goals | `check`, `baseline` |
| Descriptions from KDoc/Javadoc (experimental) | `sourceDocs { enabled = true }` | `source-docs` goal | `source-docs` |
| Docs served by the app at `/docs/` | Yes | `maven-resources-plugin` (below) | Your build |
| WireMock stub jar, spec export and upload, aggregation, generate on run | Yes | No ([Still Gradle only](#still-gradle-only)) | No |

The output layout is the same as Gradle's `build/api-docs/`, under `target/api-docs/`: `openapi3.yaml` (plus one spec per version with API versioning), `openapi.html`, `coverage/coverage.md` and `coverage.json`, `http-client/api.http` with its environment files, `postman-collection.json`, `asyncapi.yaml` and `asyncapi.html`, and the HTML that asciidoctor-maven-plugin writes there.

## Setup

### Dependencies

Maven has no automatic dependencies, so declare the library, the REST Docs module and the epages module yourself. The full table, including Kotest and the startup log module, is in [Dependencies without the plugin](getting-started.md#dependencies-without-the-plugin). The documentation tests are Kotlin, so a Java app also needs `kotlin-stdlib` and `kotlin-reflect` for tests and kotlin-maven-plugin (below).

```xml
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
<dependency>
    <groupId>com.epages</groupId>
    <artifactId>restdocs-api-spec-mockmvc</artifactId>
    <version>0.20.2</version>
    <scope>test</scope>
</dependency>
```

On the test side, use epages `restdocs-api-spec-mockmvc` (or `-webtestclient`) 0.19.x with Boot 3 and 0.20.x with Boot 4. The Maven plugin bundles the epages generator 0.20.2 whatever your Boot version, because it only reads the `resource.json` files the tests write.

### Build plugins

This is the `<build><plugins>` section of the sample. Declare our plugin **above** asciidoctor-maven-plugin: both run in `prepare-package`, and Maven runs plugins of the same phase in the order they are declared.

```xml
<build>
    <plugins>
        <!-- Kotlin tests: compile src/test/kotlin (the app itself can stay Java) -->
        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <version>${kotlin.version}</version>
            <executions>
                <execution>
                    <id>test-compile</id>
                    <phase>test-compile</phase>
                    <goals>
                        <goal>test-compile</goal>
                    </goals>
                    <configuration>
                        <sourceDirs>
                            <sourceDir>${project.basedir}/src/test/kotlin</sourceDir>
                        </sourceDirs>
                    </configuration>
                </execution>
            </executions>
            <configuration>
                <jvmTarget>${java.version}</jvmTarget>
            </configuration>
        </plugin>

        <!-- After the tests: index, OpenAPI, viewer, coverage (prepare-package) and the baseline check (verify) -->
        <plugin>
            <groupId>io.github.kardx-project</groupId>
            <artifactId>kardx-maven-plugin</artifactId>
            <version>0.1</version>
            <executions>
                <execution>
                    <goals>
                        <goal>api-docs</goal>
                        <goal>check</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <title>Books API</title>
                <openApiViewer>true</openApiViewer>
                <httpClient>true</httpClient>
                <!-- Settings without a POM parameter (OpenAPI info, security, API versioning) go in the JSON file -->
                <configFile>${project.basedir}/restdocs-tooling.json</configFile>
            </configuration>
        </plugin>

        <!-- REST Docs HTML from the AsciiDoc sources that api-docs assembled -->
        <plugin>
            <groupId>org.asciidoctor</groupId>
            <artifactId>asciidoctor-maven-plugin</artifactId>
            <version>3.2.0</version>
            <executions>
                <execution>
                    <id>generate-docs</id>
                    <phase>prepare-package</phase>
                    <goals>
                        <goal>process-asciidoc</goal>
                    </goals>
                    <configuration>
                        <sourceDirectory>${project.build.directory}/kardx/asciidoc-sources</sourceDirectory>
                        <outputDirectory>${project.build.directory}/api-docs</outputDirectory>
                        <preserveDirectories>true</preserveDirectories>
                        <backend>html5</backend>
                        <attributes>
                            <snippets>${project.build.directory}/generated-snippets</snippets>
                        </attributes>
                    </configuration>
                </execution>
            </executions>
            <dependencies>
                <dependency>
                    <groupId>org.springframework.restdocs</groupId>
                    <artifactId>spring-restdocs-asciidoctor</artifactId>
                    <version>${spring-restdocs.version}</version>
                </dependency>
            </dependencies>
        </plugin>

        <!-- Put the docs into the jar so the app serves them at /docs/ -->
        <plugin>
            <artifactId>maven-resources-plugin</artifactId>
            <executions>
                <execution>
                    <id>copy-api-docs</id>
                    <phase>prepare-package</phase>
                    <goals>
                        <goal>copy-resources</goal>
                    </goals>
                    <configuration>
                        <outputDirectory>${project.build.outputDirectory}/static/docs</outputDirectory>
                        <resources>
                            <resource>
                                <directory>${project.build.directory}/api-docs</directory>
                            </resource>
                        </resources>
                    </configuration>
                </execution>
            </executions>
        </plugin>

        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

- `api-docs` copies your hand-written documents (`src/docs/asciidoc`, without `index-template.adoc`), the generated documents (`target/generated-asciidoc`) and the new `index.adoc` into `target/kardx/asciidoc-sources`. Point asciidoctor-maven-plugin's `sourceDirectory` there, as above.
- The `snippets` attribute and the `spring-restdocs-asciidoctor` dependency (`${spring-restdocs.version}` comes from the Spring Boot parent) are what Spring REST Docs needs for `operation::` and `include::{snippets}`.
- maven-resources-plugin copies `target/api-docs` into `static/docs` after the other two, so `java -jar` serves `/docs/index.html`, `/docs/openapi3.yaml` and so on, as with the Gradle plugin.

```bash
./mvnw verify
# target/api-docs/index.html, openapi3.yaml, openapi.html, coverage/coverage.md, http-client/api.http
```

## Goals and parameters

The goal prefix is `kardx`. Precedence: a POM parameter wins over the JSON file (`configFile`), which wins over the defaults. The OpenAPI title and version default to `${project.name}` and `${project.version}` when neither sets them. `-Drestdocs.skip=true` skips every goal.

### api-docs

Default phase `prepare-package` (needs the test classpath). In order: builds `index.adoc` by running core's `DocumentationIndexCli` on the test classpath, assembles the Asciidoctor input, runs core's `AsyncApiCli` when tests left `*.asyncapi.json`, then the OpenAPI spec, viewer, coverage, `.http` and Postman, as the configuration turns them on.

| Parameter | Default | What it sets |
|---|---|---|
| `snippetsDirectory` | `${project.build.directory}/generated-snippets` | Snippets the tests wrote |
| `outputDirectory` | `${project.build.directory}/api-docs` | Where the docs go |
| `workDirectory` | `${project.build.directory}/kardx` | Intermediate files |
| `configFile` (property `restdocs.configFile`) | None | The [JSON configuration file](#the-json-configuration-file) |
| `language` (property `restdocs.language`) | `en` | Language of the generated texts (`en`, `ko`) |
| `format` | `yaml` | OpenAPI file format (`yaml`, `json`) |
| `title`, `version`, `description` | `${project.name}`, `${project.version}`, none | OpenAPI info |
| `servers` (list) | `/` | OpenAPI servers |
| `globalHeaders` (list) | None | Headers turned into an `apiKey` security scheme, as `openApiGlobalHeaders` in Gradle |
| `commonResponses` (map, status → text) | None | [Common error responses](http-api.md#common-error-responses) |
| `commonResponseSchema` | None | Schema name of the common responses |
| `openApi` | `true` | Build the OpenAPI spec (the viewer, `.http` and Postman need it) |
| `openApiViewer`, `openApiViewerType` | `false`, `redoc` | [OpenAPI viewer](outputs.md#openapi-viewer-redoc--scalar) (`redoc`, `scalar`) |
| `coverage`, `coverageExcludes` (list), `failOnUndocumented` | `true`, none, `false` | [Documentation coverage](checks.md#documentation-coverage) |
| `httpClient` | `false` | [IntelliJ HTTP Client](outputs.md#intellij-http-client) file |
| `postman` | `false` | [Postman Collection](outputs.md#postman-collection) |
| `asciidocDirectory` | `${project.build.directory}/generated-asciidoc` | Documents the tests generated |
| `asciidocSourceDirectory` | `${project.basedir}/src/docs/asciidoc` | Hand-written documents |
| `asciidocSourcesDirectory` | `${project.build.directory}/kardx/asciidoc-sources` | The assembled Asciidoctor input |
| `index`, `indexTemplate`, `indexTitle` | `true`, `${project.basedir}/src/docs/asciidoc/index-template.adoc`, `${project.name}` | The index document; the built-in template is used when the file doesn't exist |
| `asyncApi`, `asyncApiFileName` | `true`, `asyncapi` | AsyncAPI spec and viewer (`asyncapi.yaml`, `asyncapi.html`) |

### check

Default phase `verify`. Compares the new spec with the committed baseline and writes the report to `target/kardx/openapi-changes.txt` ([Checking the spec](#checking-the-spec)).

| Parameter | Default | What it sets |
|---|---|---|
| `baseline` (property `restdocs.baseline`) | `${project.basedir}/docs/openapi/openapi3.yaml` | The baseline spec |
| `failOn` (property `restdocs.failOn`) | `any` | `any` (any change), `breaking` (backward-incompatible changes only), `none` (report only) |
| `requireBaseline` | `true` | Fail when the baseline file doesn't exist |

### baseline

No phase; you call it. Copies the generated spec to `baseline` (same parameter and property as `check`).

### source-docs

Default phase `process-classes`, experimental. See [KDoc descriptions](#kdoc-descriptions).

## The JSON configuration file

Settings that have no POM parameter (OpenAPI info and tags, OAuth2, API versioning and so on) go in a JSON file. The Maven plugin (`<configFile>`) and the CLI (`--config`) read the same shape. Every key is optional; the values below are the defaults or examples. An unknown key fails with its path (for example `openApi.info.contct`), so typos don't pass silently. Relative paths are resolved against the project directory (Maven) or `--base-dir` (CLI).

```json
{
  "snippets": "target/generated-snippets", "output": "target/api-docs", "work": "target/kardx",
  "language": "en",
  "openApi": {
    "enabled": true, "title": "API documentation", "version": "1.0.0", "description": null,
    "servers": ["/"], "format": "yaml", "fileNamePrefix": "openapi3",
    "globalHeaders": [], "commonResponses": { "401": "Token missing or expired" }, "commonResponseSchema": null,
    "security": { "oauth2": { "flows": { "clientCredentials": { "tokenUrl": "https://auth.example.com/oauth/token" } },
                              "scopes": { "payments:write": "Approve payments" } },
                  "openIdConnectUrl": null },
    "apiVersioning": { "style": "header", "name": "X-API-Version", "splitSpecs": false },
    "info": { "description": null, "descriptionFile": null, "termsOfService": null,
              "contact": { "name": "", "url": "", "email": "" }, "license": { "name": "", "url": "" },
              "externalDocs": { "url": "", "description": "" }, "tagOrder": [],
              "tags": { "Orders": { "description": "", "externalDocs": { "url": "", "description": "" } } },
              "tagGroups": { "Shop": ["Orders"] } },
    "sourceDocs": "target/kardx/source-docs/source-docs.json"
  },
  "viewer": { "enabled": false, "type": "redoc", "title": null, "fileName": "openapi.html" },
  "coverage": { "enabled": true, "excludes": [], "failOnUndocumented": false, "docsPath": "docs" },
  "httpClient": { "enabled": false, "fileName": "api.http" },
  "postman": { "enabled": false, "title": null, "baseUrl": "http://localhost" },
  "check": { "baseline": "docs/openapi/openapi3.yaml", "failOn": "any", "requireBaseline": true, "report": "target/kardx/openapi-changes.txt" }
}
```

The keys map one to one to the Gradle `kardx { }` settings, so the Gradle docs describe what each one does.

| JSON key | Gradle setting | Details |
|---|---|---|
| `openApi.title`, `version`, `description`, `servers`, `format` | epages `openapi3 { }` | [OpenAPI / Swagger UI](openapi.md#openapi--swagger-ui) |
| `openApi.globalHeaders` | `openApiGlobalHeaders` | [Gradle plugin](build.md#gradle-plugin) |
| `openApi.commonResponses`, `commonResponseSchema` | `openApiCommonResponses`, `openApiCommonResponseSchema` | [Common error responses](http-api.md#common-error-responses) |
| `openApi.security` | `openApiSecurity { }` (with `oauth2 { }`) | [OAuth2 and OpenID Connect](http-api.md#oauth2-and-openid-connect) |
| `openApi.apiVersioning` | `apiVersioning { }` | [API versioning](openapi.md#api-versioning) |
| `openApi.info` | `openApiInfo { }` | [OpenAPI info and tags](openapi.md#openapi-info-and-tags) |
| `viewer` | `openApiViewer { }` | [OpenAPI viewer](outputs.md#openapi-viewer-redoc--scalar) |
| `coverage` | `coverage { }` | [Documentation coverage](checks.md#documentation-coverage) |
| `httpClient` | `httpClient { }` | [IntelliJ HTTP Client](outputs.md#intellij-http-client) |
| `postman` | `postmanCollection { }` | [Postman Collection](outputs.md#postman-collection) |
| `check` | `openApiCheck { }` | [Comparing OpenAPI changes](checks.md#comparing-openapi-changes) |

- The order of `info.tags` becomes the tag order; names in `tagOrder` come first.
- `postman.title` defaults to the OpenAPI title.
- The OpenAPI title and version default to `API documentation` and `1.0.0` for the CLI (epages defaults); the Maven plugin uses the project name and version instead.
- With Maven, `snippets`, `output` and `work` always come from the POM parameters (which have defaults), so set those in the POM.

The sample's `restdocs-tooling.json` sets only what the POM can't:

```json
{
  "openApi": {
    "description": "HTTP API spec extracted from the passing documentation tests. Full docs: [REST Docs](/docs/index.html).",
    "info": {
      "contact": { "name": "Books team", "email": "books@example.com" },
      "license": { "name": "Apache 2.0", "url": "https://www.apache.org/licenses/LICENSE-2.0" },
      "tags": { "Books": { "description": "Look up and add books" } }
    }
  }
}
```

## Checking the spec

The `check` goal compares `target/api-docs/openapi3.yaml` with the baseline you commit (`docs/openapi/openapi3.yaml`), prints the changes and fails according to `failOn`. The rules are the same as Gradle's `apiDocsCheck` ([Comparing OpenAPI changes](checks.md#comparing-openapi-changes)). When the baseline is missing or out of date, the failure message tells you to run the `baseline` goal:

```bash
./mvnw package io.github.kardx-project:kardx-maven-plugin:baseline
git add docs/openapi/openapi3.yaml
```

To only report in a local build, use `-Drestdocs.failOn=none`; to start without a baseline, set `<requireBaseline>false</requireBaseline>`.

## KDoc descriptions

The `source-docs` goal (experimental, like the Gradle `sourceDocs { }`) reads the KDoc and Javadoc of your main sources into an index that the tests use for field and parameter descriptions ([Descriptions from KDoc/Javadoc](http-api.md#descriptions-from-kdocjavadoc)). Add the goal and pass the file to the tests through surefire; without that system property the tests don't read it.

```xml
<plugin>
    <groupId>io.github.kardx-project</groupId>
    <artifactId>kardx-maven-plugin</artifactId>
    <version>0.1</version>
    <executions>
        <execution>
            <goals>
                <goal>source-docs</goal>
                <goal>api-docs</goal>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <kardx.source-docs>${project.build.directory}/kardx/source-docs/source-docs.json</kardx.source-docs>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

| Parameter | Default | What it sets |
|---|---|---|
| `sourceDirectories` (list) | The project's compile source roots plus `src/main/kotlin` | Directories to read (`*.kt`, `*.java`) |
| `sourceDocsFile` | `${project.build.directory}/kardx/source-docs/source-docs.json` | The index file |
| `methodDocs` | `false` | Use controller method comments as operation summaries and descriptions |

## Without the Maven plugin

The tooling artifact has a command line tool, `io.github.kardx.tooling.cli.ApiDocsCli`. It does what the `api-docs`, `check`, `baseline` and `source-docs` goals do, but not the index, the AsciiDoc sources or AsyncAPI: for those, run core's `DocumentationIndexCli` and `AsyncApiCli` yourself ([Index document](asciidoc.md#index-document), [SSE · WebSocket · AsyncAPI](messaging.md)), or use the Maven plugin.

### exec-maven-plugin

This runs the CLI in the Maven JVM after the tests. The epages generator must be a plugin dependency (add `restdocs-api-spec-postman-generator` too for Postman).

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.6.2</version>
    <executions>
        <execution>
            <id>api-docs</id>
            <phase>prepare-package</phase>
            <goals>
                <goal>java</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <mainClass>io.github.kardx.tooling.cli.ApiDocsCli</mainClass>
        <includeProjectDependencies>false</includeProjectDependencies>
        <includePluginDependencies>true</includePluginDependencies>
        <blockSystemExit>true</blockSystemExit>
        <arguments>
            <argument>api-docs</argument>
            <argument>--base-dir</argument>
            <argument>${project.basedir}</argument>
            <argument>--config</argument>
            <argument>restdocs-tooling.json</argument>
        </arguments>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>io.github.kardx-project</groupId>
            <artifactId>kardx-tooling</artifactId>
            <version>0.1</version>
        </dependency>
        <dependency>
            <groupId>com.epages</groupId>
            <artifactId>restdocs-api-spec-openapi3-generator</artifactId>
            <version>0.20.2</version>
        </dependency>
    </dependencies>
</plugin>
```

It prints a harmless `SLF4J: No SLF4J providers` note; add `org.slf4j:slf4j-nop` as a plugin dependency to hide it.

### java -jar

The tooling artifact also ships a runnable jar with the classifier `all` that includes the epages OpenAPI and Postman generators 0.20.2. It works with any build tool, after the tests have written the snippets. After `./gradlew publishToMavenLocal` it is in `~/.m2/repository/io/github/kardx-project/kardx-tooling/0.1/`.

```bash
java -jar kardx-tooling-0.1-all.jar api-docs --config restdocs-tooling.json
java -jar kardx-tooling-0.1-all.jar check --fail-on breaking
java -jar kardx-tooling-0.1-all.jar --help
```

### Commands and options

| Command | What it does |
|---|---|
| `api-docs` | Everything the configuration turns on: OpenAPI spec, viewer, coverage, HTTP Client, Postman |
| `openapi` | OpenAPI 3 spec from the snippets (epages generator plus this library's refinements) |
| `viewer` | Static Redoc or Scalar page for the spec (run `openapi` first) |
| `coverage` | Documentation coverage report (`coverage/coverage.md`, `coverage.json`) |
| `http` | IntelliJ HTTP Client file (`http-client/api.http`; run `openapi` first) |
| `postman` | Postman collection (`postman-collection.json`) |
| `check` | Compares the spec with the committed baseline and fails according to `check.failOn` |
| `baseline` | Saves the spec as the baseline |
| `source-docs` | KDoc/Javadoc description index for the tests (experimental; needs `--output <file>` and at least one `--source <dir>`) |

| Option | Default | What it sets |
|---|---|---|
| `--config <file>` | None | The JSON configuration file |
| `--base-dir <dir>` | Current directory | Directory relative paths are resolved against |
| `--snippets <dir>` | `target/generated-snippets` | Snippets the tests wrote |
| `--output <dir>` | `target/api-docs` | Docs directory; for `source-docs`, the index file |
| `--work <dir>` | `kardx` next to the snippets directory | Intermediate files |
| `--language en\|ko` | `en` | Language of generated texts |
| `--title`, `--version` | `API documentation`, `1.0.0` | OpenAPI info |
| `--server <url>` (repeatable) | `/` | OpenAPI servers |
| `--format yaml\|json` | `yaml` | OpenAPI file format |
| `--baseline <file>`, `--fail-on any\|breaking\|none` | `docs/openapi/openapi3.yaml`, `any` | For `check` and `baseline` |
| `--source <dir>` (repeatable), `--method-docs` | None, off | For `source-docs` |
| `--verbose` | Off | More output |
| `--help` | | Usage |

Command line options override the configuration file. Exit codes: `0` success, `1` failure (the message says why), `2` wrong usage.

## Still Gradle only

- The WireMock stub jar. The mappings themselves work without the plugin with yml `kardx.wire-mock-stubs: true` ([WireMock stub](outputs.md#wiremock-stub)).
- `apiDocsExport` and `apiDocsUpload`, and aggregating several projects (`apiDocsAggregate`).
- Generating the docs when the app runs (`bootRun`, IDE runs).
- Adding the library dependencies automatically: declare them yourself ([Dependencies without the plugin](getting-started.md#dependencies-without-the-plugin)).
- Packaging the docs into the app: use maven-resources-plugin as in [Build plugins](#build-plugins).
- The document locations file for the startup log. The `boot` module then uses the default file names.
- Passing `language` to the test JVM. With `<language>ko</language>`, also set `kardx.language` to `ko` in surefire's `systemPropertyVariables`, so the generated AsciiDoc uses the same language.
- Up-to-date checks and caching: the goals run on every build.

HTML conversion is done by asciidoctor-maven-plugin with your configuration.

## Troubleshooting

### The OpenAPI generator is not on the classpath

`The OpenAPI generator is not on the classpath. Add com.epages:restdocs-api-spec-openapi3-generator ...`: the tooling artifact doesn't bring the epages generators. The Maven plugin and the `-all` jar include them; with exec-maven-plugin or your own classpath, add `com.epages:restdocs-api-spec-openapi3-generator` (and `restdocs-api-spec-postman-generator` for Postman), as in [exec-maven-plugin](#exec-maven-plugin).

### No snippets directory

`No snippets directory: ... Run the documentation tests first`: the goal or command ran before any test wrote snippets, or it looks in the wrong place. With Maven, run a phase that includes `test` (`./mvnw verify`, not `./mvnw kardx:api-docs` alone), and don't skip tests. With the CLI, check `--snippets` and `--base-dir`.

### HTML has no index or the snippets are missing

asciidoctor-maven-plugin ran before `api-docs`. Both are in `prepare-package`, and Maven runs them in the order they are declared, so declare kardx-maven-plugin above asciidoctor-maven-plugin. Also check that asciidoctor-maven-plugin's `sourceDirectory` is `target/kardx/asciidoc-sources` and that the `snippets` attribute is set.
