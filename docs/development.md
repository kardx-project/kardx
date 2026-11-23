**English** | [한국어](ko/development.md)

# Development

[Document list](README.md)

<!-- toc -->
**Contents**

- [Releasing](#releasing)
<!-- /toc -->

How to change or verify this repository. Library users don't need this. Issues, commit messages and pull requests are covered in [CONTRIBUTING](../CONTRIBUTING.md) ([한국어](../CONTRIBUTING.ko.md)).

```
kardx/
├── annotations/      kardx-annotations (no dependencies)
├── core/             kardx (compiled against the Spring REST Docs 3.0 API, binary compatible with 4.0)
├── boot/             kardx-boot (logs document addresses at app startup. Compiled against Boot 3, binary compatible with 4)
├── kotest/           kardx-kotest (extension for documenting Kotest 6.x specs. Kotest is compileOnly)
├── kotest5/          kardx-kotest5 (Kotest 5.x edition of the same extension. Same package and public API)
├── kotest-common/    shared sources and tests compiled by both kotest and kotest5 (not a Gradle project, not published)
├── bom/              kardx-bom
├── gradle-plugin/    io.github.kardx-project Gradle plugin (composite build, Java)
├── tooling/          kardx-tooling (steps after the tests and the CLI, no Gradle API. Included build, Java)
├── maven-plugin/     kardx-maven-plugin (Maven goals over tooling, Java)
├── samples/
│   ├── spring-boot-3/    Boot 3.5 + epages 0.19 + springdoc 2.8 (JUnit tests + one Kotest spec)
│   ├── spring-boot-4/    Boot 4.1 + epages 0.20 + springdoc 3.2 (JUnit tests + one Kotest spec)
│   ├── spring-boot-4-webflux/  Boot 4.1 WebFlux + WebTestClient (no servlet stack. JUnit tests + one Kotest spec)
│   └── maven-boot-4/     Boot 4.1 Maven project with the Maven plugin (not part of the Gradle build)
├── build-logic/      shared build settings for the modules (restdocs.* convention plugins)
├── config/detekt/    detekt rules
├── gradle/           version catalog (libs.versions.toml) and wrapper
├── docs/             user documentation
└── scripts/          scripts that verify document outputs and the running app, and update document tables of contents
```

The responsibilities and visibility of each core package, the layering, resources and contract tests are described in [core in the architecture document](architecture.md#core). The documentation lifecycle shared by the JUnit extension and the Kotest extension lives in core's `io.github.kardx.lifecycle` (opt-in `@RestDocsExtensionApi`), and the logic is not duplicated in the two extensions ([kotest in the architecture document](architecture.md#kotest)).

Implementation that users don't import goes in `io.github.kardx.internal.*` (`io.github.kardx.gradle.internal.*` for the Gradle plugin, `io.github.kardx.tooling.internal.*` for tooling). The Gradle plugin keeps only the wiring and calls the steps in `tooling` (`internal.steps`); the Maven plugin (`io.github.kardx.maven`) and the CLI call the same code through `internal.pipeline` ([tooling in the architecture document](architecture.md#tooling)).

After changing library or plugin code, verify in the order below. Each step is slower than the one before and catches what it can't. The verification scripts print `PASS`/`FAIL` for each item and exit with code 1 if anything fails (CI also judges by the exit code).

```bash
# 1. Tests of the module you changed
./gradlew :core:test                # or :kotest:test :kotest5:test; the Gradle plugin: ./gradlew -p gradle-plugin test
./gradlew -p tooling build          # tooling: tests and apiCheck (javap). The Maven plugin: ./gradlew :maven-plugin:test

# 2. Full build: build, tests, ktlint, detekt, apiCheck, checkSampleSync, checkDocsPairs, checkPackageCycles, checkErrorCodes (including the Gradle plugin and tooling builds; check also covers them)
#    The samples are subprojects, so their bootJar, and with it apiDocs, is built too
./gradlew build

# 3. Verify document outputs (snippets, AsciiDoc, HTML, OpenAPI, AsyncAPI, viewer, Postman, WireMock. The number of items differs per sample and is printed at the end)
./gradlew :samples:spring-boot-4:apiDocs && python3 scripts/verify-docs-artifacts.py samples/spring-boot-4
./gradlew :samples:spring-boot-3:apiDocs && python3 scripts/verify-docs-artifacts.py samples/spring-boot-3
# The WebFlux sample checks only the WebTestClient outputs
./gradlew :samples:spring-boot-4-webflux:apiDocs && python3 scripts/verify-docs-artifacts.py samples/spring-boot-4-webflux

# 4. Verify the running app (17 items: HTTP, SSE, WebSocket, STOMP). Rebuild the jar and restart: an app started earlier still runs the old code and docs
./gradlew :samples:spring-boot-4:bootJar
java -jar samples/spring-boot-4/build/libs/spring-boot-4-0.1.jar --server.port=18084 &
python3 scripts/verify-live-app.py 18084
# The WebFlux sample is not covered by verify-live-app.py. Start it and check the addresses only (Swagger UI is at /webjars/swagger-ui/index.html)
./gradlew :samples:spring-boot-4-webflux:bootJar
java -jar samples/spring-boot-4-webflux/build/libs/spring-boot-4-webflux-0.1.jar --server.port=18085 &

# 5. As a consumer: install into the local Maven repository (including the Gradle plugin), then build a separate project that uses mavenLocal()
./gradlew publishToMavenLocal

# 6. The Maven sample: uses the artifacts installed in step 5 (tests, index, OpenAPI, viewer, coverage, HTML, baseline check, docs in the jar)
cd samples/maven-boot-4 && ./mvnw verify && cd ../..

./gradlew clean                     # also cleans the Gradle plugin module (only when clean is the only task)
```

The samples use the in-repository modules and the plugin directly through a composite build, so steps 1 to 4 don't need `publishToMavenLocal`. Steps 5 and 6 check the published artifacts (the Maven sample can only use those). For step 5, in a separate project outside the repository, add `mavenLocal()` to `repositories` and to `pluginManagement`, apply `id("io.github.kardx-project")`, and run `./gradlew apiDocs` there. Sample jars are named `samples/<sample>/build/libs/<sample>-<version>.jar` (`-plain.jar` is the non-executable one).

Additional checks that need external tools or are run on demand:

```bash
# Validate with the official AsyncAPI parser (pass the directory where @asyncapi/parser is installed. Several files allowed)
# For the Boot 3 sample: samples/spring-boot-3/build/api-docs/asyncapi.yaml
node scripts/validate-asyncapi.mjs <parser-dir> samples/spring-boot-4/build/api-docs/async/index.yaml

# Validate OpenAPI (Redocly recommended rules. Pass the directory where @redocly/openapi-core 1.x is installed. Exit code 1 on errors)
# npm install --prefix /tmp/redocly @redocly/openapi-core@1.34.5
node scripts/validate-openapi.mjs /tmp/redocly samples/spring-boot-4/build/api-docs/openapi3.yaml samples/spring-boot-3/build/api-docs/openapi3.yaml

# Check package cycles (core, kotest, kotest5 (each including kotest-common), Gradle plugin, tooling, Maven plugin. Exit code 1 on a cycle; --graph prints dependencies per layer).
# The root checkPackageCycles task (part of check) runs the same check, so ./gradlew build fails on a cycle
python3 scripts/check-package-cycles.py
```

The Gradle plugin's compatibility test against other Gradle versions (`GradleCompatibilityFunctionalTest`) needs distributions, so it doesn't run normally. Pass versions (downloaded) or unpacked installation directories, separated by commas: `./gradlew -p gradle-plugin test --tests '*GradleCompatibility*' -PgradleVersions=7.6.4,9.0.0`. It runs the core scenarios (applying the plugin, adding and resolving dependencies, epages defaults, the document locations file, configuration cache reuse) and spec export and upload (`apiDocsExport`, `apiDocsUpload` against a local HTTP server, also failing if the output mentions `deprecated`). Verified on 7.6.4, 8.14.6 (wrapper), 9.0.0 and 9.8.2.

The Kotest extension tests (`./gradlew :kotest:test :kotest5:test`) run fixture specs with the Kotest engine (`TestEngineLauncher`) and check the documents and snippets. The fixture specs are `@EnabledIf(LaunchedOnly::class)`, so Gradle doesn't run them directly (they show up as disabled specs in the test results). The test classpaths of both modules don't include the Kotest Spring extension, so the path that runs without Spring is verified as well.

**Shared sources for Kotest 5.x and 6.x.** `kotest` (6.x) and `kotest5` (5.x) compile the same package and public API against different Kotest versions. Code that doesn't depend on the version (naming rules `TestNames`, spec document records `SpecDocument`, run state `ActiveRun`, documentation behavior `LeafTestDocumentation`, `EnumDocumentationExtension`, Spring context lookup `SpringContexts`) and most tests and fixture specs exist only once in `kotest-common/src/{main,test}/kotlin`, and the convention plugin `restdocs.kotest-module` adds them to both modules' source sets, detekt inputs and Dokka source links (they are unpublished shared sources, not a Gradle project). Each module keeps only the parts that touch the Kotest API.

| Module | main | test |
|---|---|---|
| `kotest-common` | The shared code above | `RestDocsExtensionTest`, `TestNamesTest`, fixture specs, `FixtureRun` (result shape) |
| `kotest` (6.x) | `RestDocsExtension` (listener implementation), `KotestTestCases` (name `name.name`, parent, `TestType.Test`) | `runFixtures` (6.x launcher), `LaunchedOnly` (6.x `Condition`), `InstancePerRoot` fixture spec, `KotestSixOnlyTest` |
| `kotest5` (5.x) | `RestDocsExtension`, `KotestTestCases` (`name.testName`, `TestType.Dynamic` is also a leaf) | `runFixtures` (5.x launcher), `LaunchedOnly` (5.x `EnabledCondition`), `withData` and `extensions()` fixture specs, `KotestFiveOnlyTest` |

IntelliJ attaches a directory to only one module, so files in `kotest-common` are analyzed against one module (usually `kotest`). In the other module's files, `internal` declarations of the shared code may show up red, but the Gradle build works for both modules. When you change shared code, check both versions with `./gradlew :kotest:check :kotest5:check`.

The public API is managed with Kotlin `explicitApi()` and pinned in `<module>/api/*.api`. When you change the API, update it with `./gradlew apiDump` and include the result (`check` verifies it with `apiCheck`). `tooling` is Java, so it has its own javap-based `apiDump`/`apiCheck` (`./gradlew -p tooling apiDump`, file `tooling/api/kardx-tooling.api`; only the `io.github.kardx.tooling` package and `cli.ApiDocsCli` are public). The Maven plugin's contract is its goals and parameters, which [Maven and the command line](maven.md) documents. The change history is in [CHANGELOG.md](../CHANGELOG.md).

The public API has no data classes, because `copy` and `componentN` become part of the ABI, so adding even one property breaks binary compatibility. Value objects (`AsyncApiInfo`, `ResponseWrapper`, `DocumentGenerator.Config`, and so on) are regular classes that implement `equals`/`hashCode`/`toString` by hand (add a contract test to `PublicValueClassesTest`) and have a public constructor meant to be called with named arguments.

When you add a property to such a class, append the new property with a default value at the end of the primary constructor, and keep a secondary constructor that takes the previous constructor signature and delegates to the new one (a constructor with default values also changes the signature of its synthetic `$default` method, so this keeps it findable by existing binaries). Check in the `apiDump` output that the existing `<init>` lines were not removed, only added to.

Shared build settings for the modules are in the `restdocs.*` convention plugins in `build-logic/`. For library modules (annotations, core, boot), `restdocs.kotlin-library` fills the javadoc jar with Dokka and emits interface default implementations with `jvmDefault = NO_COMPATIBILITY` so no `DefaultImpls` are generated. Module build files contain only module-specific values such as the module name and description. The lowest supported Kotlin (`kotlin-baseline`, the metadata version and kotlin-stdlib in the POM) and the Kotlin used by detekt (`detekt-kotlin`) are changed in `gradle/libs.versions.toml`.

Like the minimal setup in the documentation, the samples don't declare library dependencies and leave them to the Gradle plugin. `restdocs.kotlin-sample` replaces the published coordinates the plugin adds (`io.github.kardx-project:...`) with projects in the same build, so the samples are always built from the current sources.

The common sources of the two samples (spring-boot-3 and 4) must be identical, and `checkSampleSync` checks this (the WebFlux sample is a different app and is not covered). Add intentional differences to the allowlist in the root `build.gradle.kts` (the list and the reasons are in the [architecture document](architecture.md#samples). The specs that port JUnit documentation tests to Kotest exist only in the Boot 4 sample).

When you change sections of the documents (`docs/*.md`), regenerate the tables of contents.

```bash
python3 scripts/update-docs-toc.py
```

The English docs (`README.md`, `docs/*.md`) and the Korean docs (`README.ko.md`, `docs/ko/*.md`) must stay in step. After editing either side, run the pair check until it prints `in step`. The root `checkDocsPairs` task (verification group) runs the same script and is attached to `check`, so `./gradlew build` fails when the pairs drift. Both root check tasks run `python3`; where it's called something else (Windows: `py`), pass `-Prestdocs.python=py`.

```bash
python3 scripts/check-docs-pairs.py
```

For each pair it compares the file sets, the heading structure (count and levels, not titles), fenced code blocks (count, language and code; comments are dropped and Korean text on the Korean side matches anything), table shapes (count, rows, columns), and the set of relative link targets. It prints `path:line: message` per finding and exits with code 1. For an intended difference, put a marker directly above the code block or table in either doc (only blank lines may come between):

```markdown
<!-- docs-pair: ignore-next-block -->
<!-- docs-pair: ignore-next-table -->
```

Errors and warnings carry a code from `RestDocsErrorCode` (`kardx[KDX-011]: ...`) and link to its row in `docs/troubleshooting.md`. When you add or change a code, regenerate the code tables of both troubleshooting docs and write the fix column for new rows. The root `checkErrorCodes` task (attached to `check`) fails when a table is out of date or a fix is missing.

```bash
python3 scripts/update-error-codes.py
```

## Releasing

`.github/workflows/release.yml` publishes the libraries, the Maven plugin and `kardx-tooling` (including the `-all` jar) to Maven Central (Sonatype Central Portal), and the Gradle plugin to the Gradle Plugin Portal. It needs these repository secrets:

| Secret | Use |
|---|---|
| `SIGNING_KEY` | ASCII-armored PGP private key (passed as `ORG_GRADLE_PROJECT_signingInMemoryKey`) |
| `SIGNING_PASSWORD` | Passphrase of that key |
| `CENTRAL_USERNAME` | Central Portal user token name |
| `CENTRAL_PASSWORD` | Central Portal user token password |
| `GRADLE_PUBLISH_KEY` | Gradle Plugin Portal API key |
| `GRADLE_PUBLISH_SECRET` | Gradle Plugin Portal API secret |

1. Set `version` in `gradle.properties` (for example `0.1`) and merge it to `main`.
2. Tag that commit `v<version>` and push the tag. The workflow fails if the tag and the version differ.
3. The workflow runs `build`, `publishToStaging` (signed, into `build/staging-deploy`), `scripts/central-upload.sh`, `gradle-plugin` `publishPlugins`, and creates a GitHub release with generated notes.
4. The Central deployment is `USER_MANAGED`: once it is `VALIDATED`, press Publish in the Central Portal (Deployments). Until then the Gradle plugin on the Plugin Portal can't resolve `kardx-tooling`.

```bash
git tag v0.1
git push origin v0.1
```

Without `signingInMemoryKey`, `build` and `publishToMavenLocal` don't sign. To check the bundle locally, pass a key and run `./gradlew publishToStaging` then `scripts/central-upload.sh --dry-run` (it checks that every `.jar`/`.pom`/`.module` has `.asc`, `.md5` and `.sha1`, and uploads nothing). Delete `build/staging-deploy` first when files from an earlier run are there. The Gradle plugin and its marker are also put on Central, so `pluginManagement { repositories { mavenCentral() } }` works too.
