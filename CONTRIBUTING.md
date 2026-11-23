**English** | [한국어](CONTRIBUTING.ko.md)

# Contributing

Thank you for your interest in kardx. Bug reports, feature ideas, documentation fixes and code are all welcome.

## Issues

- Report bugs and suggest features with the GitHub issue forms. For a bug, include code that reproduces it and your Spring Boot, REST Docs, Kotlin, JDK and Gradle versions.
- Don't report security vulnerabilities in public issues; follow [SECURITY.md](SECURITY.md).
- For a large change, agree on the direction in an issue first. It saves time on both sides.

## Development environment

The module layout, local installation and how to use the verification scripts are in [docs/development.md](docs/development.md).

- JDK 17 or later (the gradle-plugin module uses toolchain 17, so JDK 17 must be installed)
- Use the repository's Gradle wrapper (`./gradlew`)

## Build and verification

[docs/development.md](docs/development.md) is the reference for the verification order and commands. At a minimum, check the following.

```bash
# Build, tests, ktlint, detekt, apiCheck, checkSampleSync, docs pair check checkDocsPairs (includes generating the sample docs and the Gradle plugin module)
./gradlew build

# If you changed the public API, update the api files and include them (apiCheck verifies them)
./gradlew apiDump

# Verify the generated document outputs (exit code 1 on any failure). CI checks all three samples
python3 scripts/verify-docs-artifacts.py samples/spring-boot-3
python3 scripts/verify-docs-artifacts.py samples/spring-boot-4
python3 scripts/verify-docs-artifacts.py samples/spring-boot-4-webflux

# Validate with the official AsyncAPI parser (pass the directory where @asyncapi/parser is installed)
node scripts/validate-asyncapi.mjs <parser-dir> \
  samples/spring-boot-3/build/api-docs/asyncapi.yaml \
  samples/spring-boot-4/build/api-docs/async/index.yaml
```

When you open a pull request, CI runs the same checks on JDK 17 and 21. The common sources of the Boot 3 and Boot 4 samples must be identical; `checkSampleSync` checks this.

## Code style

- Follow `.editorconfig` and the ktlint and detekt rules. If `./gradlew build` passes, you're fine.
- In core tests, the Kotest project config (`io.kotest.provided.ProjectConfig`) resets `RestDocsConfiguration` after every test. For settings used by a single test, use `withSettings`.
- The public API is managed with Kotlin `explicitApi()`. When you add or change public API, update the KDoc and the documentation (`docs/`) as well.
- Record user-visible changes in [CHANGELOG.md](CHANGELOG.md) and its Korean version `CHANGELOG.ko.md`.
- The documentation exists in English (`README.md`, `docs/`) and Korean (`README.ko.md`, `docs/ko/`). When you change one side, change the other in the same pull request.
- When you change sections of `docs/*.md` or `docs/ko/*.md`, regenerate the tables of contents of both languages with `python3 scripts/update-docs-toc.py`.
- Check the pairing of the two languages (file sets, heading structure, code blocks, tables, link targets) with `python3 scripts/check-docs-pairs.py`. The root `checkDocsPairs` task runs the same check in `check`, so `./gradlew build` also fails when the pairs drift.

## Commit messages

```
<type>: <description>

<body (optional)>
```

| type | Use |
|------|------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Structural improvement without behaviour change |
| `docs` | Documentation |
| `test` | Adding or changing tests |
| `chore` | Build settings, dependencies and other chores |
| `perf` | Performance improvement |
| `ci` | CI settings |

Example: `fix: optional detection for nullable collection fields`

## Pull requests

- Check the pull request template's checklist (`./gradlew build` passes, docs and CHANGELOG updated).
- Keep one concern per pull request.
- Contributed code is distributed under the repository's [Apache License 2.0](LICENSE).
