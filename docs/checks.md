**English** | [한국어](ko/checks.md)

# Spec check and coverage

[Document list](README.md)

<!-- toc -->
**Contents**

- [Comparing OpenAPI changes](#comparing-openapi-changes)
- [Documentation coverage](#documentation-coverage)
  - [Endpoints that aren't in the inventory](#endpoints-that-arent-in-the-inventory)
<!-- /toc -->

Two checks for pull requests and CI: comparing the generated OpenAPI spec with a committed baseline, and finding endpoints that no documentation test covers. Workflow files are in [CI recipes](ci.md).

## Comparing OpenAPI changes

With the epages plugin, you can compare the generated OpenAPI spec with a baseline spec committed to the repository. The goal is to notice unintended API changes in pull requests. Both tasks run after `openapi3` (tests included).

```bash
./gradlew apiDocsBaseline   # saves the current spec to docs/openapi/openapi3.yaml. Commit this file
./gradlew apiDocsCheck      # compares the new spec with the baseline
```

```kotlin
kardx {
    openApiCheck {
        baseline = layout.projectDirectory.file("docs/openapi/openapi3.yaml") // default
        failOn = "breaking"   // any (default) | breaking | none
        runOnCheck = true     // also part of ./gradlew check (CI)
    }
}
```

| `failOn` | Behavior |
|---|---|
| `any` (default) | Fails on any difference. If the change is intended, update the baseline with `apiDocsBaseline` and commit it together |
| `breaking` | Fails only on backward-incompatible changes and prints the other differences as warnings |
| `none` | Never fails; only prints a summary |

If there are differences, a summary is printed and the full list is written to `build/reports/kardx/openapi-changes.txt`. The report always starts with the same header: `The OpenAPI spec matches the baseline (baseline … ↔ generated …)` or `The OpenAPI spec differs from the baseline (…)`, then `Breaking changes: N`, `Warnings: N` (only when there are warnings) and `All structural differences: N`, so a script can read the counts either way.

```
The OpenAPI spec differs from the baseline (baseline …/docs/openapi/openapi3.yaml ↔ generated …/build/api-spec/openapi3.yaml)
Breaking changes: 3
  - Type changed: POST /api/v1/products 201 application/json data.price (integer → number)
  - Response field removed: POST /api/v1/products 201 application/json data.discountRate
  - Response enum value added: POST /api/v1/products 201 application/json data.status [HIDDEN]
All structural differences: 3
  - changed  components > schemas > ProductResponse > properties > data > properties > price > type: integer → number
  - removed  components > schemas > ProductResponse > properties > data > properties > discountRate
  - added  components > schemas > ProductResponse > properties > data > properties > status > enum > HIDDEN
Report: file:///…/build/reports/kardx/openapi-changes.txt
If the changes are intended, run ./gradlew apiDocsBaseline to update the baseline and commit it.
```

The report is English whatever the documentation language. `All structural differences` compares the two documents position by position (including description and example changes). Named lists such as parameters and tags are matched by name, not by order, so a change in order alone is not a difference. `Breaking changes` are decided per operation by following `$ref`, and only the following rules apply.

- Removing an endpoint (path and method), removing a 2xx response, removing a request or response media type
- Adding a required parameter, a parameter becoming required, adding a required request body, the request body becoming required
- Request body: adding a required field, a field becoming required
- Request constraints tightened, on parameters and request body fields: `minLength`, `minimum`, `minItems` raised or added, `maxLength`, `maximum`, `maxItems` lowered or added, `pattern` added or changed (values clients used to send may be rejected)
- Response body: removing a field, a required field becoming optional
- Response headers: removing a header
- enum: removing a value from a request (values clients used to send are rejected), adding an `enum` to a request parameter or field that had none (`Request enum added: …`; values outside the list are now rejected), adding a value to a response (clients receiving an unknown value may break)
- `oneOf`: removing a variant, in requests and responses alike
- Security: an operation gaining a security requirement (a public API now needs authentication), or its scheme changing (`bearerAuthJWT` → `oauth2`)
- Anywhere: type or format changes (`type`, `format`)

Three changes are reported as warnings rather than breaking changes: adding a variant to a response `oneOf` (clients may receive a shape they don't know), a changed `default`, and renamed path variables (`Path variables renamed: /users/{id} → /users/{userId}`; the URL clients call is the same, so the operations are still compared, not reported as a removed and an added endpoint). Adding a variant to a request `oneOf` is neither. With `failOn = "breaking"` they are printed without failing; with `any` every difference fails anyway.

Renaming only a schema, adding fields or endpoints, removing response enum values, loosening a constraint, and changing descriptions are not breaking changes. Self-referencing schemas (`Category.parent`) are followed only once. `anyOf`, `allOf`, `additionalProperties`, scope changes in a security requirement, constraints in responses, added response headers, and the contents of `components.securitySchemes` (flow URLs, scope lists) are not judged (they do appear in the structural differences).

If you need broader rules, run a dedicated tool such as [oasdiff](https://github.com/oasdiff/oasdiff) in CI as well (`oasdiff breaking docs/openapi/openapi3.yaml build/api-docs/openapi3.yaml`). This plugin's comparison prioritizes working without external executables.

- The baseline file format (YAML or JSON) is read from the extension. With `openapi3 { format = "json" }`, keep the baseline file as `.json` too.
- If there's no baseline file, it fails unless `failOn = "none"`, and tells you to create one with `./gradlew apiDocsBaseline`. Without a baseline no change can be judged, so otherwise a `breaking` check would keep passing until someone creates the baseline. To get the old lenient behavior (fail only with `failOn = "any"`, only warn with `breaking`), set `openApiCheck { requireBaseline = false }`.
- If the spec changes on every run (timestamp or random value examples), the comparison fails every time. Use fixed example values.

## Documentation coverage

`apiDocsCoverage` compares the endpoints the app actually maps with the operations the tests documented, so you find APIs that nobody wrote a documentation test for. It is on by default and runs as part of `apiDocs`, after the tests. The task exists only when the epages plugin (`com.epages.restdocs-api-spec`) is applied. It prints one line (not when the task is UP-TO-DATE or comes from the build cache):

```
API docs coverage: 23/25 endpoints documented (2 undocumented, see build/api-docs/coverage/coverage.md)
```

How the two sides are collected:

- **Endpoints the app maps.** During the tests, on the first documented request in each Spring application context, the library reads the context's `RequestMappingHandlerMapping` beans (servlet and reactive) and writes the list to `.kardx/endpoints/` in the snippet directory. There is one entry per HTTP method and path pattern, with the handler method; a mapping without a method counts as any method. This happens once per context, and contexts with the same mappings write the same file once, so the tests don't get noticeably slower. The lists of all contexts are combined.
- **Documented operations.** The method and path template of every OpenAPI fragment (`resource.json`), so the epages plugin is needed; without it, `apiDocsCoverage` isn't registered at all. Tests left out of OpenAPI (`openApi { excluded = true }`, `openApi.enabled = false`) still count as documented: their recorded request (`http-request.adoc`) is matched against the endpoint patterns. When both sides have an API version, the versions must match too.

Path variable names don't have to match: `/users/{id}` and `/users/{userId}` are the same endpoint.

```kotlin
kardx {
    coverage {
        enabled = true                 // default
        failOnUndocumented = false     // default. true fails the task when an endpoint is undocumented
        exclude("/internal/**", "/health")
    }
}
```

`/error`, `/actuator/**`, springdoc's `/v3/api-docs/**`, `/v3/api-docs.yaml`, `/swagger-ui/**` and `/swagger-ui.html`, and the docs path itself (`/docs/**` with the default `bootJar.path`) are always excluded; `exclude(...)` adds patterns to these and can't remove them. Patterns are Ant style: `*` matches one path segment, `**` any number. A pattern may contain path variables as Spring shows them, with any name and with or without a regex (`exclude("/internal/users/{id}")` also excludes `/internal/users/{userId}` and `/internal/users/{id:\d+}`).

| Result | Contents |
|---|---|
| Console | The one-line summary above (only when the task actually runs) |
| `build/api-docs/coverage/coverage.md` | The undocumented endpoints with their handlers, for reading in a pull request or CI log |
| `build/api-docs/coverage/coverage.json` | The same data for tools |

**In CI.** Keep `failOnUndocumented = false` while you are still writing tests and read the summary ([CI recipes](ci.md) posts `coverage.md` on the pull request). Once everything is documented, make a new endpoint without a documentation test fail the build. To fail only in CI:

```kotlin
kardx {
    coverage {
        failOnUndocumented = providers.gradleProperty("strictDocs").map(String::toBoolean).orElse(false)
    }
}
```

```bash
./gradlew build apiDocs -PstrictDocs=true
```

When the tests wrote no endpoint list at all (for example, every test uses a standalone setup), the task prints `no endpoint inventory` and passes, even with `failOnUndocumented = true`.

### Endpoints that aren't in the inventory

The inventory holds only what the test contexts contain, so an endpoint can be missing from it and then never shows up as undocumented.

| Case | Why |
|---|---|
| Standalone setups: `MockMvcBuilders.standaloneSetup(...)`, `WebTestClient.bindToController(...)`, `bindToRouterFunction(...)`, `bindToServer()` | There is no application context to read. These tests are skipped silently; their documented operations still count |
| RestTestClient tests | The library doesn't get the application context from RestTestClient, so these tests add nothing to the inventory (their documented operations still count). A MockMvc or WebTestClient documentation test on the same context lists the endpoints |
| Functional routes (`RouterFunction`, `router { }`, `coRouter { }`) | Not read. Only annotated controllers are listed |
| Slice tests: `@WebMvcTest(UserController::class)`, `@WebFluxTest(...)` | The context contains only the selected controllers. A controller that no test context loads isn't listed. One `@SpringBootTest` documentation test lists every controller of the app |
