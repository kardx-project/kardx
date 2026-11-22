**English** | [한국어](ko/openapi.md)

# OpenAPI and Swagger UI

[Document list](README.md)

<!-- toc -->
**Contents**

- [OpenAPI / Swagger UI](#openapi--swagger-ui)
  - [OpenAPI info and tags](#openapi-info-and-tags)
  - [OAuth2 and OpenID Connect schemes](#oauth2-and-openid-connect-schemes)
- [API versioning](#api-versioning)
<!-- /toc -->

What the Gradle plugin does with the OpenAPI spec: Swagger UI, info and tags, OAuth2 and OpenID Connect schemes, and API versions. How tests describe operations is in [What goes into the OpenAPI spec](http-api.md#what-goes-into-the-openapi-spec) and [Schema names](http-api.md#schema-names); the plugin settings and tasks are in [Build and outputs](build.md).

## OpenAPI / Swagger UI

If [epages restdocs-api-spec](https://github.com/ePages-de/restdocs-api-spec) is on the test classpath, `andDocument` also produces an OpenAPI resource (`resource.json`). The test class `@DisplayName` becomes the tag, and the method `@DisplayName` becomes the summary.

```kotlin
.andDocument {
    ...
    openApi {
        requestSchema = "CreateUserRequest"
        responseSchema = "UserResponse"
        deprecated = true
        tags("My page")
        operationId = "updateMyProfile"     // sets the operationId directly (default: the controller method name)
        summary = "Update my profile"       // operation summary (default: the test display name)
        description = "..."                 // operation description (default: the block description)
        excluded = true                     // leaves this API out of OpenAPI (it stays in the REST Docs documents)
    }
}
```

- By default, the operationId is the name of the controller method that handled the request. You change the rule with the setting `openApi.operationIdRule` (yml `kardx.open-api.operation-id-rule`), and the plugin resolves collisions (adding the controller name only to the colliding ones) when it builds the spec. The detailed rules are in [operationId](http-api.md#operationid). There is no plugin-side option (the rule is set in one place, the test settings).

- When you document success and error responses of the same API in separate tests, they are merged into one operation with a response per status code. See [Documenting multiple status codes](http-api.md#documenting-multiple-status-codes).
- Documents with relaxed request or response fields (`relaxed = true`, `relaxed(RelaxedSection.RESPONSE_FIELDS)`) go into OpenAPI without those field descriptions, because epages doesn't support relaxed validation.
- Errors a test doesn't produce can be declared in its block with `errorResponse(...)`; the plugin adds them to the operation before the common responses ([Declared error responses](http-api.md#declared-error-responses)).
- Integer and date formats, Bean Validation constraints, `withDefaultValue` and `example`, and multipart and cookies are merged into the spec by the Gradle plugin after `openapi3`. What goes out and how is described in [What goes into the OpenAPI spec](http-api.md#what-goes-into-the-openapi-spec).
- Exclude tests that document the same path and method differently (for examples or validation) with `excluded = true` or `openApi.enabled = false`. epages merges them into one.
- Swagger UI is served by pointing springdoc's `springdoc.swagger-ui.url` at the generated `openapi3.yaml`. See the [samples](../README.md#samples) settings.

### OpenAPI info and tags

epages sets only the title, version and servers of the spec (`openapi3 { }`). `kardx { openApiInfo { } }` fills in the rest of the top level after `openapi3`: `info.description`, `termsOfService`, `contact`, `license`, the document `externalDocs`, the order and descriptions of `tags`, and `x-tagGroups`. Items you don't set stay as epages wrote them.

```kotlin
kardx {
    openApiInfo {
        descriptionFile = file("src/docs/openapi-intro.md")   // or description = "..." (Markdown)
        termsOfService = "https://example.com/terms"
        contact {
            name = "API team"
            email = "api@example.com"
        }
        license {
            name = "Apache 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0"
        }
        externalDocs {
            url = "https://wiki.example.com/api"
            description = "Integration guide"
        }
        tag("Users") {
            description = "Sign-up and profile"
            externalDocs { url = "https://wiki.example.com/api/users" }
        }
        tag("Orders")                         // order only
        tagGroup("Accounts", "Users")         // x-tagGroups (Redoc, Scalar)
        tagGroup("Shopping", "Orders", "Cart")
    }
}
```

- `description` (Markdown) or `descriptionFile` (a UTF-8 Markdown file; the spec is rebuilt when it changes). Setting both, or a file that doesn't exist, fails the build.
- `license` needs a `name` and `externalDocs` needs a `url` (OpenAPI requires them); without one the block is left out with a build warning.
- `tag(...)` puts tags first, in the order you call it; the other tags follow in epages' order. A description set here wins over the test class `@AsciiDescription`. A tag no documented operation uses is left out with a warning (tags come from the test class `@DisplayName` or `openApi { tag(...) }`).
- `tagGroup(...)` writes `x-tagGroups`, which Redoc and Scalar show as groups in the side menu. Redoc hides tags that are in no group, so the plugin collects those into a last group `Other` (`기타` with `language = "ko"`).
- The values also go into the per-version specs (`apiVersioning { splitSpecs = true }`). The merged spec of `apiDocsAggregate` doesn't get them; it has its own `aggregate { title; version; description }`.

### OAuth2 and OpenID Connect schemes

`openApiSecurity { }` holds what a test can't know about OAuth2 and OpenID Connect: the flows, their URLs and the scope descriptions. Tests only say which scheme and scopes an operation needs (`openApi { oauth2("users:read") }`). How the two fit together is in [OAuth2 and OpenID Connect](http-api.md#oauth2-and-openid-connect).

## API versioning

Spring Framework 7 (Spring Boot 4) can map one path to several handlers by API version (`@GetMapping("/api/users/{id}", version = "1.1")`). The library reads the version of the handler that served each documented request, so the test needs nothing extra. On Spring Framework 6 (Boot 3) there is no version attribute, and nothing changes.

How a client sends the version is configured in the app (`ApiVersionConfigurer`, `spring.mvc.apiversion.*`), which the build can't read. Tell the plugin the same style:

```kotlin
kardx {
    apiVersioning {
        header("X-API-Version")   // or queryParameter("version"), mediaTypeParameter("v"), pathSegment()
        splitSpecs = false        // default
    }
}
```

| Style | What a versioned operation gets in OpenAPI |
|---|---|
| `header("X-API-Version")` | `x-api-version: "1.1"`, and a required header parameter `X-API-Version` whose `enum` and example are the version |
| `queryParameter("version")` | `x-api-version`, and a required query parameter `version`, as above |
| `mediaTypeParameter("v")` | `x-api-version` |
| `pathSegment()` | `x-api-version`. The version is already part of the documented path |

When the same path and method are documented in several versions, one spec can hold only one of them:

- `splitSpecs = false` (default): one spec. For each path and method the highest version wins, and the build warns with the versions it left out.
- `splitSpecs = true`: one more spec per version, `openapi3-v<version>.yaml` (`openapi3-v1.0.yaml`, `openapi3-v1.1.yaml`), next to `openapi3.yaml` in the epages output directory (`build/api-spec/`); `apiDocs` copies them to `build/api-docs/` as well. Each contains that version's operations and every operation without a version. `openapi3.yaml` is still written as with `false` (highest version per operation), without the warning.

- A baseline version (`version = "1.2+"`) is written as `1.2`. Versions are compared part by part, numerically where both parts are numbers (`1.9` < `1.10`).
- epages merges the fragments of the same path and method into one operation, so the request and response body schemas of a versioned operation are the union of all its versions, in every spec. Document versions with different bodies on different paths, or keep in mind that a field may belong to only some versions.
- RestTestClient tests and functional routes (`RouterFunction`) carry no handler version, so their operations get no `x-api-version`.
