**English** | [한국어](ko/ci.md)

# CI recipes

[Document list](README.md)

<!-- toc -->
**Contents**

- [What to run](#what-to-run)
- [Pull request check](#pull-request-check)
  - [Build settings](#build-settings)
  - [Workflow](#workflow)
- [Pull request comment](#pull-request-comment)
- [Intentional API changes](#intentional-api-changes)
- [Publishing the docs from main](#publishing-the-docs-from-main)
  - [GitHub Pages](#github-pages)
  - [Sending specs to a registry](#sending-specs-to-a-registry)
- [Speed](#speed)
- [Multiple projects](#multiple-projects)
- [GitLab CI](#gitlab-ci)
<!-- /toc -->

Copy-paste setups for running the documentation pipeline in CI: fail a pull request on breaking API changes and undocumented endpoints, post the reports as a comment, and publish the docs from `main`. GitHub Actions comes first; a short GitLab CI version is at the end. The tasks and settings used here are explained in [Build and outputs](build.md).

`apiDocsCheck`, `apiDocsBaseline` and `apiDocsCoverage` exist only when the epages plugin (`com.epages.restdocs-api-spec`) is applied, because they work on the OpenAPI spec.

## What to run

| Goal | Task | Setting | Report |
|---|---|---|---|
| Failing tests fail the build | `build` (`check` → `test`) | None | Gradle test report |
| Breaking API changes fail the build | `apiDocsCheck` | `openApiCheck { failOn }` | `build/reports/kardx/openapi-changes.txt` |
| Undocumented endpoints fail the build | `apiDocs` (runs `apiDocsCoverage`) | `coverage { failOnUndocumented = true }` | `build/api-docs/coverage/coverage.md`, `coverage.json` |
| A docs site | `apiDocs` | None | `build/api-docs/` |
| Specs sent to a registry | `apiDocsUpload` | `upload { url }` | None |

`build` pulls in `apiDocs` only for a Spring Boot app with `bootJar.enabled` (the default), and `apiDocsCheck` only with `openApiCheck { runOnCheck = true }` ([Which command when](build.md#which-command-when)). Naming both tasks on the command line works in every case, so the recipes below do that.

## Pull request check

### Build settings

```kotlin
// build.gradle.kts
kardx {
    openApiCheck {
        failOn = "breaking"         // fail only on backward-incompatible changes (default "any": any difference)
    }
    coverage {
        failOnUndocumented = true   // fail when an endpoint has no documentation test
    }
}
```

- `failOn = "any"` (the default) also fails on added endpoints and changed descriptions, so every API change needs a new baseline. `"breaking"` fails only on the changes listed in [Comparing OpenAPI changes](checks.md#comparing-openapi-changes) and prints the rest as warnings.
- `apiDocsCheck` needs a committed baseline (`docs/openapi/openapi3.yaml`). Without one it fails with `No OpenAPI baseline`; create it as in [Intentional API changes](#intentional-api-changes) before turning on the check.
- To fail on undocumented endpoints only in CI and not in local builds, read the flag from a Gradle property as shown under [Documentation coverage](checks.md#documentation-coverage).

### Workflow

```yaml
# .github/workflows/api-docs.yml
name: API docs

on:
  pull_request:

permissions:
  contents: read

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v6
      # --continue: apiDocsCheck still runs when the coverage check fails, so both reports exist
      - name: Build, compare with the baseline, check coverage
        run: ./gradlew build apiDocs apiDocsCheck --continue
      - name: Upload API docs
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: api-docs
          path: |
            build/api-docs/
            build/reports/kardx/
          if-no-files-found: ignore
```

- Use the JDK your project builds with in `java-version`.
- One Gradle invocation runs the tests once. Splitting `build`, `apiDocs` and `apiDocsCheck` into separate jobs or invocations runs the tests again in each.
- The job fails when a test fails, when `apiDocsCheck` fails, or when `apiDocsCoverage` finds an undocumented endpoint with `failOnUndocumented = true`. The artifact holds the generated docs (including `coverage/`) and the change report.

## Pull request comment

Post the change report and the coverage report on the pull request, updating one comment instead of adding a new one on every push. Add these steps to the end of the `check` job above and give the job write access to pull requests:

```yaml
jobs:
  check:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      # ... the steps of the check job above ...
      - name: Write the comment
        if: always() && github.event_name == 'pull_request'
        run: |
          {
            echo '## API docs'
            echo
            echo '### OpenAPI changes'
            echo
            echo '~~~text'
            head -n 200 build/reports/kardx/openapi-changes.txt 2>/dev/null || echo 'apiDocsCheck did not run'
            echo '~~~'
            echo
            cat build/api-docs/coverage/coverage.md 2>/dev/null || echo 'apiDocsCoverage did not run'
          } > api-docs-comment.md
      - name: Post the comment
        if: always() && github.event_name == 'pull_request'
        uses: marocchino/sticky-pull-request-comment@v2
        with:
          header: api-docs
          path: api-docs-comment.md
```

- `apiDocsCheck` writes `openapi-changes.txt` every time it runs. With a baseline it starts with `The OpenAPI spec matches the baseline (…)` or `The OpenAPI spec differs from the baseline (…)` followed by `Breaking changes: N` and `All structural differences: N` (both `0` when nothing changed) and the full lists; without one it holds the missing-baseline message. The comment shows the first 200 lines; the full report is in the artifact.
- `apiDocsCoverage` writes `coverage.md` every time it runs, with the undocumented endpoints and their handlers. `apiDocs` copies it to `build/api-docs/coverage/`.
- `if: always()` posts the comment when the build fails too, which is when it's useful. When a test fails, neither task runs and the comment says so.
- The `header` value keeps one comment per pull request; the next run replaces it.
- In pull requests from forks, the `GITHUB_TOKEN` is read-only, so the comment step fails there. The artifact is still uploaded.
- Without a third-party action, `gh pr comment ${{ github.event.pull_request.number }} --body-file api-docs-comment.md` (with `GH_TOKEN: ${{ github.token }}` in `env`) posts a new comment on every run instead of updating one.

## Intentional API changes

When a pull request changes the API on purpose, `apiDocsCheck` fails until the baseline is updated. Update it locally and commit it with the change:

```bash
./gradlew apiDocsBaseline   # saves the current spec to docs/openapi/openapi3.yaml
git add docs/openapi/openapi3.yaml
git commit -m "Update the OpenAPI baseline"
```

- Reviewers see the API change as a diff of `docs/openapi/openapi3.yaml` in the pull request. To require a specific reviewer for API changes, list the file in `CODEOWNERS`.
- `./gradlew apiDocsBaseline apiDocsCheck` updates the baseline and then checks against it in one run.
- The baseline location is `openApiCheck { baseline }`. With `openapi3 { format = "json" }`, keep the baseline as `.json`.
- If the spec contains values that change on every run (timestamps, random IDs in examples), the check fails every time. Use fixed example values.

## Publishing the docs from main

### GitHub Pages

`build/api-docs/` is a static site: `index.html` (REST Docs, only with the Asciidoctor plugin; without it, enable the viewer and link `openapi.html`), `openapi3.yaml`, `asyncapi.html` when you documented messages, and `openapi.html` when the [OpenAPI viewer](outputs.md#openapi-viewer-redoc--scalar) is enabled. Swagger UI is not in it, because springdoc serves it from the app; turn on the viewer for a spec page that works on static hosting.

```yaml
# .github/workflows/api-docs-pages.yml
name: Publish API docs

on:
  push:
    branches: [main]

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew apiDocs
      - uses: actions/upload-pages-artifact@v3
        with:
          path: build/api-docs
  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

- Set **Settings → Pages → Source** to **GitHub Actions** once.
- The documents contain the requests and responses the tests actually sent. Unless [sensitive header masking](configuration.md#masking-sensitive-data) is on, the tokens and cookies used in tests are published too. Check this before publishing, especially on a public site ([Production exposure](build.md#production-exposure)).
- The Redoc and Scalar viewers load their script from the unpkg CDN when the page opens.
- To publish from a branch folder instead (**Deploy from a branch** with `/docs`), you can point `outputDirectory` or `aggregate.outputDirectory` at the repository's `docs/`. The plugin empties that directory before writing, so outside `build/` it only accepts an empty directory or one with the marker file `.kardx-output` ([Directories the plugin empties](build.md#directories-the-plugin-empties)). Don't point it at a `docs/` that holds hand-written files.

### Sending specs to a registry

The plugin can upload the specs over HTTP itself ([apiDocsUpload](outputs.md#apidocsupload)). Pass the token through an environment variable so it never appears in the build script:

```kotlin
// build.gradle.kts
kardx {
    upload {
        url = "https://registry.example.com/apis/{name}/{version}/{type}"
        headers.put("Authorization", providers.environmentVariable("API_REGISTRY_TOKEN").map { "Bearer $it" })
    }
}
```

```yaml
      # a step in a job that runs on main
      - name: Upload specs
        env:
          API_REGISTRY_TOKEN: ${{ secrets.API_REGISTRY_TOKEN }}
        run: ./gradlew apiDocsUpload
```

- `apiDocsUpload` sends on every run (it is never UP-TO-DATE) and logs header names, not values.
- To keep the spec files as build output instead, use `./gradlew apiDocsExport` with `export { directory }` and upload that directory as an artifact. To attach them to a Maven publication, set `export { publishSpecs = true }` and run `./gradlew publish` ([Exporting specs](outputs.md#exporting-specs)).
- None of these run as part of `build`; call them in the job that should send the specs.

## Speed

- `gradle/actions/setup-gradle` caches the Gradle dependencies and wrapper between runs.
- Run every task in one Gradle invocation, as above. Each task runs the tasks it depends on first, so naming `test` or `openapi3` separately isn't needed.
- `apiDocsCheck` never reports UP-TO-DATE; it compares on every run. The comparison doesn't run the tests again in the same invocation.
- This plugin's tasks are compatible with the configuration cache, but `org.asciidoctor.jvm.convert` 4.0.x and the epages plugin are not yet, so enabling `--configuration-cache` for the full pipeline reports problems from those plugins ([Gradle plugin](build.md#gradle-plugin)). Leave it off for these jobs, or use `--configuration-cache-problems=warn`.
- With the Gradle build cache on, `apiDocsCoverage` can be restored from the cache. It then prints no console summary, but `coverage.md` is still in `build/api-docs/coverage/`.

## Multiple projects

- `./gradlew build apiDocs apiDocsCheck` run from the root runs each task in every project that has it. Each project compares against its own baseline (`<project>/docs/openapi/openapi3.yaml`) and writes its reports under its own `build/` directory, so collect them with patterns such as `**/build/api-docs/coverage/coverage.md` in the artifact and comment steps.
- To publish one site, declare the projects with `restdocsAggregation(...)` in the aggregating project, run `./gradlew apiDocsAggregate`, and upload `build/api-docs-aggregate/` instead of `build/api-docs` ([Aggregating multiple projects](outputs.md#aggregating-multiple-projects)). It contains a list page, the merged `openapi3.yaml` and a copy of each project's docs.

## GitLab CI

The same pipeline in `.gitlab-ci.yml`: the check runs on merge requests and on the default branch, and `pages` publishes `build/api-docs` from the default branch.

```yaml
# .gitlab-ci.yml
variables:
  GRADLE_USER_HOME: "$CI_PROJECT_DIR/.gradle"

default:
  image: eclipse-temurin:17-jdk
  cache:
    key: gradle
    paths:
      - .gradle/caches
      - .gradle/wrapper

api-docs:
  script:
    - ./gradlew build apiDocs apiDocsCheck --continue
  artifacts:
    when: always
    paths:
      - build/api-docs/
      - build/reports/kardx/
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH

pages:
  script:
    - ./gradlew apiDocs
    - cp -r build/api-docs public
  artifacts:
    paths:
      - public
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
```

- The reports are in the job artifacts. To post them on the merge request, send `api-docs-comment.md` built as in [Pull request comment](#pull-request-comment) through the GitLab notes API with a project access token.
- For `apiDocsUpload`, define `API_REGISTRY_TOKEN` as a masked CI/CD variable; GitLab passes it to the job as an environment variable.
