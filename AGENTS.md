# AGENTS.md

Guide for AI coding agents (Claude Code, Codex, Copilot, Cursor, …) working **on this repository**.
To *use* kardx in another project, see [`skills/kardx/SKILL.md`](skills/kardx/SKILL.md) and
[`llms.txt`](https://kardx-project.github.io/kardx/llms.txt) instead.

kardx is a Kotlin DSL on top of Spring REST Docs: documentation tests produce snippets, AsciiDoc/HTML,
OpenAPI (via epages), AsyncAPI and more. It ships a library, a Gradle plugin, a Maven plugin and a CLI.

## Layout

| Path | What | Language |
|---|---|---|
| `annotations/` | `kardx-annotations` (`@Description`, `@EnumCode`, …), no dependencies | Kotlin |
| `core/` | `kardx` — DSL, extraction, snippets, JUnit extension. Compiled against REST Docs 3.0, binary compatible with 4.0 | Kotlin |
| `kotest/`, `kotest5/`, `kotest-common/` | Kotest 6.x / 5.x extensions; `kotest-common` is shared source compiled into both (not a Gradle project) | Kotlin |
| `boot/` | `kardx-boot` — logs doc URLs at app startup | Kotlin |
| `gradle-plugin/` | `io.github.kardx-project` plugin (included build). Wiring only; logic lives in `tooling` | Java |
| `tooling/` | `kardx-tooling` — post-test steps + CLI, no Gradle API (included build) | Java |
| `maven-plugin/` | Maven goals over `tooling` | Java |
| `samples/` | `spring-boot-3`, `spring-boot-4`, `spring-boot-4-webflux` (Gradle, composite build), `maven-boot-4` (Maven, uses published artifacts) | Kotlin |
| `build-logic/` | `restdocs.*` convention plugins (names kept from before the rename — don't rename) | Kotlin DSL |
| `docs/`, `docs/ko/` | User docs, English and Korean pairs | Markdown |
| `site/`, `scripts/build-site.py` | MkDocs site built from `docs/` (GitHub Pages) | |

Architecture, package layering and contracts: [`docs/architecture.md`](docs/architecture.md).
Full verification order: [`docs/development.md`](docs/development.md).

## Commands

```bash
./gradlew build                         # everything: tests, ktlint, detekt, apiCheck, checkSampleSync,
                                        # checkDocsPairs, checkErrorCodes, checkPackageCycles, sample apiDocs
./gradlew :core:test                    # one module (also :kotest:test :kotest5:test :boot:test)
./gradlew -p gradle-plugin test         # Gradle plugin (included build)
./gradlew -p tooling build              # tooling + its javap apiCheck
./gradlew :core:apiDump                 # after a public API change (tooling: ./gradlew -p tooling apiDump)

# generated-output checks (exit code 1 on any FAIL)
./gradlew :samples:spring-boot-4:apiDocs && python3 scripts/verify-docs-artifacts.py samples/spring-boot-4
./gradlew :samples:spring-boot-3:apiDocs && python3 scripts/verify-docs-artifacts.py samples/spring-boot-3
./gradlew :samples:spring-boot-4-webflux:apiDocs && python3 scripts/verify-docs-artifacts.py samples/spring-boot-4-webflux

python3 scripts/check-docs-pairs.py     # en/ko docs in step
python3 scripts/update-docs-toc.py      # after changing headings in docs/
python3 scripts/update-error-codes.py   # after changing RestDocsErrorCode (troubleshooting table)
```

`./gradlew build` must pass before you call a change done. Don't start sample app servers unless asked;
prefer the scripts above, which work on build outputs.

## Rules that the build enforces

- **Public API is pinned.** `explicitApi()` everywhere; signatures live in `<module>/api/*.api`.
  Change API → run `apiDump` and commit the diff. Existing `<init>` / method lines may only be added to, never removed.
- **No `data class` in public API** (`copy`/`componentN` become ABI). Use a regular class with hand-written
  `equals`/`hashCode`/`toString` and a contract test in `PublicValueClassesTest`. To add a property: append it with a
  default to the primary constructor and keep a secondary constructor with the old signature.
- **Docs come in pairs.** Every change to `README.md` / `docs/*.md` needs the same change in `README.ko.md` /
  `docs/ko/*.md` (same headings, code blocks, tables, link targets). `checkDocsPairs` fails the build otherwise.
  Korean headings get Korean anchors (GitHub rules).
- **Samples stay in sync.** Common sources of `samples/spring-boot-3` and `samples/spring-boot-4` must be identical
  (`checkSampleSync`); intentional differences go in the allowlist in `build-logic`.
- **No package cycles**, and error codes in `RestDocsErrorCode` must match `docs/troubleshooting.md`.
- ktlint + detekt (`config/detekt/`) must pass.

## Conventions

- Kotlin: 4-space indent, 120 columns, no `!!`, cognitive complexity ≤ 15, no TODOs or dead code,
  a comment on every swallowed exception. Literals repeated 3+ times become constants.
- One responsibility per file. Extension functions live in a file named after the receiver
  (`ResultActionsExtensions.kt`). No `Util`/`Support` grab-bag files.
- Things users don't import go in `io.github.kardx.internal.*` (`…gradle.internal.*`, `…tooling.internal.*`).
  Experimental API: `@ExperimentalRestDocsApi`; extension SPI: `@RestDocsExtensionApi`.
- Multi-line text: raw strings or `joinToString`, not `"…\n" + …`.
- Error and warning messages are English. User-facing errors carry a `KDX-NNN` code with a docs link.
- Tests follow the module's existing style (Kotest `BehaviorSpec` + MockK is the default). In core tests,
  `RestDocsConfiguration` is reset after each test; use `withSettings` for per-test settings.
- Performance matters: a feature must not noticeably slow down tests or doc generation (lazy work, Gradle caching).
  `asciidoctor` must be `UP-TO-DATE` on a second unchanged run.
- User-visible changes go in `CHANGELOG.md` and `CHANGELOG.ko.md` (same entries, English and Korean).
- Commits: `<type>: <description>` with type `feat|fix|refactor|docs|test|chore|perf|ci`.

## Two supported lines

Spring Boot 3.5 (REST Docs 3, Spring 6.2, epages 0.19, springdoc 2.8, Jackson 2) and Spring Boot 4.x
(REST Docs 4, Spring 7, epages 0.20, springdoc 3.x, Jackson 3) are both supported, often through the
**same artifact names at different versions** in `gradle/libs.versions.toml`. Minimum versions are pinned on purpose
(jakarta.validation 3.0, Servlet 6.0, Maven API 3.9, Kotlin metadata `kotlin-baseline`, Kotest 5.x for `kotest5`).
Don't "upgrade" those; Dependabot is configured to leave them alone (`.github/dependabot.yml`).

## Releasing

Tag `v<version>` matching `version` in `gradle.properties` → `.github/workflows/release.yml` signs, uploads to Maven
Central (then press **Publish** in the Central Portal) and publishes the Gradle plugin. Details:
[`docs/development.md#releasing`](docs/development.md#releasing).
