**English** | [한국어](ko/customizing-snippets.md)

# Customizing snippets and document layout

[Document list](README.md)

<!-- toc -->
**Contents**

- [What to change where](#what-to-change-where)
- [Snippet names and paths](#snippet-names-and-paths)
- [Choosing snippets for the document](#choosing-snippets-for-the-document)
  - [Custom snippet kinds](#custom-snippet-kinds)
- [Changing snippet templates](#changing-snippet-templates)
  - [Templates provided by the library](#templates-provided-by-the-library)
  - [Example: Default and Example columns in the request fields table](#example-default-and-example-columns-in-the-request-fields-table)
- [Adding your own snippets](#adding-your-own-snippets)
- [Changing the document layout](#changing-the-document-layout)
  - [Example: table of contents on the right, custom subtitles](#example-table-of-contents-on-the-right-custom-subtitles)
  - [Wiring into JUnit](#wiring-into-junit)
  - [Wiring into Kotest](#wiring-into-kotest)
  - [index document](#index-document)
- [Using snippets in hand-written AsciiDoc](#using-snippets-in-hand-written-asciidoc)
- [Request and response preprocessing](#request-and-response-preprocessing)
- [Enum code document format](#enum-code-document-format)
<!-- /toc -->

This page gathers the ways to change the shape of the output, such as the columns of generated tables, the snippets included in documents, and the format of section titles. The examples here were run in a separate project outside the repository (0.1 from mavenLocal, Boot 4.1) and their results verified.

First, two terms.

- **Snippet**: an AsciiDoc fragment file produced by a single test (`http-request.adoc`, `response-fields.adoc` …). Spring REST Docs produces it from a [Mustache](https://mustache.github.io/) template.
- **Generated document**: the `<class-name>.adoc` that this library produces for each test class (each spec in Kotest). It gathers snippets with `include::`, and the `asciidoctor` task turns it into HTML.

## What to change where

| What you want | How | Section |
|---|---|---|
| Add columns to the field table or reorder them | Put a `.snippet` template with the same name in test resources | [Changing snippet templates](#changing-snippet-templates) |
| Add a table this library doesn't have to the document | Extend `TemplatedSnippet` and pass it with `andDocument { snippets(...) }` | [Adding your own snippets](#adding-your-own-snippets) |
| Remove curl and add HTTPie in generated documents | yml `document-snippets` | [Choosing snippets for the document](#choosing-snippets-for-the-document) |
| Change subtitles (`.CURL`, `.Request Fields`), section titles, or the table of contents position | Override `DocumentGenerator` | [Changing the document layout](#changing-the-document-layout) |
| Put a notice at the top of every document | yml `common-include-files` | [Changing the document layout](#changing-the-document-layout) |
| Pull just a few snippets into a hand-written document | `include::{snippets}/...[]`, the `operation::` macro | [Using snippets in hand-written AsciiDoc](#using-snippets-in-hand-written-asciidoc) |
| Remove headers from request and response examples or change the host | Header masking settings, `http.requestPreprocessors` and `http.responsePreprocessors` | [Request and response preprocessing](#request-and-response-preprocessing) |
| Change the headings and column widths of the Enum code table | Extend `AsciiDocEnumDocumentFormat` | [Enum code document format](#enum-code-document-format) |

## Snippet names and paths

Snippets are created at `<snippet directory>/<document name>/<test slot>/<snippet name>.adoc`. The snippet directory defaults to `build/generated-snippets` and is changed with the Gradle plugin's `snippetsDirectory` ([Output locations](build.md#output-locations)).

| Test | Snippet directory |
|---|---|
| JUnit `UserApiDocsTest.getUser()` | `user-api-docs-test/get-user/` |
| `test("Get user")` in Kotest `UserApiDocsSpec` | `user-api-docs-spec/get-user/` |

The rules for `@Nested` and parameterized tests are in [Snippets and sections by test shape](asciidoc.md#snippets-and-sections-by-test-shape), and the Kotest naming rules are in [Snippet directory name](kotest.md#snippet-directory-name). Generated documents use this path as `include::{snippets}/user-api-docs-test/get-user/http-request.adoc[]`. `{snippets}` is an attribute the Gradle plugin passes to Asciidoctor, and the prefix itself is changed with `snippets.include-prefix` ([yml settings](configuration.md#yml-settings)).

## Choosing snippets for the document

Which of the snippets created in a test directory are `include::`d in the generated document is chosen with the setting `document.snippets` (yml `document-snippets`). Built-in snippets keep the order of the table below; you can place [your own kinds](#custom-snippet-kinds) between them. The subtitle is the `.Title` line above each include.

| Constant (`SnippetFile`) | Snippet file | Subtitle | Included by default |
|---|---|---|---|
| `API_SUMMARY` | `api-summary` | None | Yes |
| `MESSAGE` | `message-NN` | None | Yes |
| `MESSAGE_FIELDS` | `request-fields-message-NN` | `Payload Fields` | Yes |
| `CURL_INFO` | `curl-request` | `CURL` | Yes |
| `HTTPIE_INFO` | `httpie-request` | `HTTPie` | No |
| `REQUEST_INFO` | `http-request` | `Request Info` | Yes |
| `REQUEST_PATH_PARAMETERS` | `path-parameters` | `Path Parameters` | Yes |
| `QUERY_PARAMETERS` | `query-parameters` | `Query Parameters` | Yes |
| `FORM_PARAMETERS` | `form-parameters` | `Form Parameters` | Yes |
| `REQUEST_HEADERS` | `request-headers` | `Request Headers` | Yes |
| `REQUEST_COOKIES` | `request-cookies` | `Request Cookies` | Yes |
| `REQUEST_PARTS` | `request-parts` | `Request Parts` | Yes |
| `REQUEST_PART_FIELDS` | `request-part-<part>-fields` | `Request Part Fields (<part>)` | Yes |
| `REQUEST_PART_BODY` | `request-part-<part>-body` | `Request Part Body` | No |
| `REQUEST_FIELDS` | `request-fields` | `Request Fields` | Yes |
| `REQUEST_SUBSECTION_FIELDS` | `request-fields-<name>` | `Request Fields (<name>)` | Yes |
| `REQUEST_FILE` | `request-file` | `Request File` | Yes (only for a binary request body or `requestFile(...)`) |
| `REQUEST_BODY` | `request-body` | `Request Body` | No |
| `RESPONSE_INFO` | `http-response` | `Response Info` | Yes |
| `RESPONSE_HEADERS` | `response-headers` | `Response Headers` | Yes |
| `RESPONSE_COOKIES` | `response-cookies` | `Response Cookies` | Yes |
| `RESPONSE_FILE` | `response-file` | `Response File` | Yes (only for a binary response body or `responseFile(...)`, [File downloads](http-api.md#file-downloads)) |
| `RESPONSE_FIELDS` | `response-fields` | `Response Fields` | Yes |
| `SSE_EVENTS` | `sse-events` | `Events` | Yes |
| `EVENT_FIELDS` | `response-fields-event-<event>` | `Event Data (<event>)` | Yes |
| `RESPONSE_SUBSECTION_FIELDS` | `response-fields-<name>` | `Response Fields (<name>)` | Yes |
| `LINKS` | `links` | `Links` | Yes |
| `ERROR_RESPONSES` | `error-responses` | `Error Responses` | Yes (only tests with [declared error responses](http-api.md#declared-error-responses) have it) |
| `RESPONSE_BODY` | `response-body` | `Response Body` | No |

- The four left out by default overlap with other snippets (`request-body` is the same as the body in `http-request`).
- When `request-file` or `response-file` is included, it replaces `request-body` or `response-body` of the same test (the body there is only `<binary: N bytes>`).
- WebSocket and STOMP message snippets are grouped by message number (`message-01`, its fields, `message-02` …).
- Your own snippets that you haven't [registered as a kind](#custom-snippet-kinds) are always included regardless of this setting, after the ordered snippets in file name order. Their subtitle is the file name (`required-roles`).

In yml, list snippet file names or constant names (both `request-info` and `REQUEST_INFO` work). Only what you list is included, so to add to the defaults, list the default entries too. An unknown name fails with `kardx.document-snippets: unknown snippet '...'`.

```yaml
# src/test/resources/application-docs.yml
kardx:
  document-snippets: api-summary, http-request, httpie-request, path-parameters, request-fields, http-response, response-headers, response-fields
```

In code, it's `RestDocsConfiguration.configure { document { snippets = SnippetFile.defaults() + SnippetFile.HTTPIE_INFO } }` (`io.github.kardx.config.SnippetFile`).

This setting only decides what the generated document includes. The snippet files are still produced, so hand-written documents can use snippets that were left out.

### Custom snippet kinds

To give [your own snippet](#adding-your-own-snippets) a subtitle and a place among the built-in snippets, declare it with `SnippetFile.custom` and register it in `document { register(...) }`.

```kotlin
import io.github.kardx.config.SnippetFile

val REQUIRED_ROLES = SnippetFile.custom("required-roles", title = "Required Roles")

RestDocsConfiguration.configure {
    document { register(REQUIRED_ROLES, after = SnippetFile.RESPONSE_FIELDS) }
}
```

- `fileName` is the snippet file name without `.adoc` (the name your `Snippet` writes). A built-in name fails. `title` is the subtitle; an empty string includes the snippet without one.
- `after = ...` places it after that kind, `before = ...` before it, and neither at the end. Registering the same kind again moves it. `document.order` is the resulting order (read-only).
- With `includedByDefault = true` (the default), registering also adds the kind to `document.snippets`. With `false`, add it yourself (`snippets += REQUIRED_ROLES`). Assigning `document.snippets` after registering drops the kind again, so assign first or use `+=`.
- yml `document-snippets` accepts built-in names only. Custom kinds are set in code.
- `SnippetFile` is a class, not an enum: the built-in list is `SnippetFile.BUILT_IN` (was `SnippetFile.entries`), and a `when (kind)` over it needs an `else` branch.

## Changing snippet templates

Snippets are produced from the `org/springframework/restdocs/templates/asciidoctor/<snippet name>.snippet` templates on the classpath. If you put a file at the same path in test resources, it's read before the template in the library jar.

```
src/test/resources/org/springframework/restdocs/templates/asciidoctor/request-fields.snippet
```

The template syntax is Mustache. `{{name}}` is a value, `{{#name}}...{{/name}}` renders when the value exists or is true (once per element for a list), and `{{^name}}...{{/name}}` renders when the value is absent or false. `{{#tableCellContent}}...{{/tableCellContent}}` is a function provided by REST Docs that escapes `|` inside table cells.

### Templates provided by the library

The library jar contains the templates below. Snippets not listed here (`http-request`, `curl-request`, `request-body` …) use the Spring REST Docs default templates. To change those, copy a [Spring REST Docs default template](https://github.com/spring-projects/spring-restdocs/tree/main/spring-restdocs-core/src/main/resources/org/springframework/restdocs/templates/asciidoctor) (`default-<name>.snippet`) and save it as `<name>.snippet`.

| Template | Columns | Item list |
|---|---|---|
| `request-fields`, `response-fields`, `request-part-fields` | Path, Type, Required, Description, Constraints | `fields` |
| `path-parameters`, `query-parameters`, `form-parameters` | Parameter, Required, Description, Constraints | `parameters` (`path-parameters` has the table title `{{path}}`) |
| `request-headers`, `response-headers` | Name, Required, Description, Constraints | `headers` |
| `request-cookies`, `response-cookies` | Name, Required, Description | `cookies` |
| `request-parts` | Part, Required, Description | `requestParts` |
| `links` | Relation, Required, Description | `links` |
| `api-summary` (this library) | No table | `method`, `path` (URL template), `status`, `description`. Only for a deprecated API: `deprecated` (true) and `deprecationNote` (the reason) ([Deprecated APIs](http-api.md#deprecated-apis)) |
| `sse-events` (this library) | Event, Required, Description | `events` (`name`, `optional`, `description`) |
| `error-responses` (this library) | Status and description table, then per response a field table (Path, Type, Description) and an example block | `responses` (`status`, `description`), `details` (`hasFields`, `fields` (`path`, `type`, `description`), `fieldsTitle`, `example`, `exampleTitle`, `language`), header texts `statusHeader`, `descriptionHeader`, `pathHeader`, `typeHeader` ([Declared error responses](http-api.md#declared-error-responses)) |
| `file-body` (this library, used by `request-file` and `response-file`) | Two columns, label and value, one row per item | `rows` (`label`, `value`, `code` (`true` for the media type and file name)) ([File downloads](http-api.md#file-downloads)) |
| `message` (this library) | Headers table and Payload | `direction`, `destination`, `protocol`, `description`, `hasHeaders`, `headers` (`name`, `optional`, `description`, `example`), `payload` |

These are the values available for a single field, parameter, or header item. The first four come from Spring REST Docs; the rest are added by this library as descriptor attributes. Items without a value don't have the key, so wrap them in `{{#key}}`.

| Value | Contents | Where |
|---|---|---|
| `path` / `name` / `rel` | Field path / parameter, header, cookie, or part name / link relation | All |
| `type` | JSON type (`String`, `Number` …) | Fields |
| `description` | Description | All |
| `optional` | `true` if optional | All |
| `constraints` | One-line constraint description (`NotBlank - ...`, `constraint "..."`) | Fields, parameters, headers |
| `default` | The `withDefaultValue` value | Fields, parameters, headers |
| `example` | The `example` value | Fields, parameters, headers |
| `requiredByConstraint` | `true` if optional but `@NotNull`, `@NotBlank`, or `@NotEmpty` requires a value | Fields, parameters |
| `deprecated`, `deprecationNote` | `@Deprecated` / `deprecated()` mark and reason | Fields, parameters |
| `masked`, `maskingRule` | `@Masked` / `masked()` mark and rule ([Marking masked fields](http-api.md#marking-masked-fields)) | Body fields |

The default templates show `default` and `example` as `Default:` and `Example:` lines below the description, and `deprecated` and `masked` as `*Deprecated*: reason` and `*Masked*: rule` lines at the top of the description cell. The Required column is `true` when the item is not `optional` or is `requiredByConstraint`.

### Example: Default and Example columns in the request fields table

Moves the default value and example, which are normally appended below the description, into their own columns.

```
|===
|Path|Type|Required|Description|Default|Example|Constraints

{{#fields}}
|{{#tableCellContent}}`+{{path}}+`{{/tableCellContent}}
|{{#tableCellContent}}`+{{type}}+`{{/tableCellContent}}
|{{#tableCellContent}}{{#optional}}{{#requiredByConstraint}}true{{/requiredByConstraint}}{{^requiredByConstraint}}false{{/requiredByConstraint}}{{/optional}}{{^optional}}true{{/optional}}{{/tableCellContent}}
|{{#tableCellContent}}{{#deprecated}}*Deprecated*{{#deprecationNote}}: {{.}}{{/deprecationNote}} +
{{/deprecated}}{{#masked}}*Masked*{{#maskingRule}}: {{.}}{{/maskingRule}} +
{{/masked}}{{description}}{{/tableCellContent}}
|{{#tableCellContent}}{{#default}}`+{{.}}+`{{/default}}{{/tableCellContent}}
|{{#tableCellContent}}{{#example}}`+{{.}}+`{{/example}}{{/tableCellContent}}
|{{#tableCellContent}}{{#constraints}}{{.}}{{/constraints}}{{/tableCellContent}}

{{/fields}}
|===
```

```kotlin
.andDocument {
    requestFields(
        "nickname" type STRING means "Nickname" example "Hong Gildong" constraint "1-20 characters",
        "bio" type STRING means "Bio" isOptional true withDefaultValue "(none)",
    )
}
```

Resulting `request-fields.adoc`:

```
|===
|Path|Type|Required|Description|Default|Example|Constraints

|`+nickname+`
|`+String+`
|true
|Nickname
|
|`+Hong Gildong+`
|1-20 characters

|`+bio+`
|`+String+`
|false
|Bio
|`+(none)+`
|
|

|===
```

- Removing a column only removes that information from the table; validation and OpenAPI are unaffected. If you remove the Constraints column, constraints don't appear in the table.
- When several templates have the same shape, such as `response-fields`, put a file for each one you want to change.
- After changing a template, rerun the tests to regenerate the snippets (`./gradlew apiDocs` reruns them because the test inputs changed).

With the Markdown format (`withTemplateFormat(TemplateFormats.markdown())`), the template path is `templates/markdown/`. The library provides only `api-summary`, `error-responses`, `file-body`, `message`, and `sse-events` for Markdown, and generated documents are AsciiDoc only.

## Adding your own snippets

Information that the block functions can't express (permissions, pricing, rate limits …) can be turned into a snippet by extending Spring REST Docs' `TemplatedSnippet` and passing it with `andDocument { snippets(...) }`.

```kotlin
package com.example.demo.support

import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.TemplatedSnippet

/** Table of roles that can call this API. File name required-roles.adoc, template required-roles.snippet. */
class RequiredRolesSnippet(private val roles: Map<String, String>) : TemplatedSnippet("required-roles", null) {
    override fun createModel(operation: Operation): Map<String, Any> = mapOf(
        "roles" to roles.map { (name, description) -> mapOf("name" to name, "description" to description) },
    )
}

fun requiredRoles(vararg roles: Pair<String, String>): RequiredRolesSnippet = RequiredRolesSnippet(roles.toMap())
```

The map returned by `createModel` becomes the template values. You can also read the actual request and response from `operation` (`operation.request.headers`, `operation.response.status` …). Put the template at `src/test/resources/org/springframework/restdocs/templates/asciidoctor/required-roles.snippet`. Without the template, the test fails with an error saying the template couldn't be found.

```
|===
|Role|Description

{{#roles}}
|`+{{name}}+`
|{{description}}

{{/roles}}
|===
```

```kotlin
.andDocument {
    description = "Gets a user by ID."
    pathParameters("id" pathMeans "User ID")
    responseFields<UserResponse>()
    snippets(requiredRoles("USER" to "Signed-in user", "ADMIN" to "Administrator"))
}
```

- The snippet is created as `required-roles.adoc` in the test directory and goes at the end of the section in the generated document (after the known snippets) with the subtitle `.required-roles`. To give it a subtitle and a position, register it as a [custom snippet kind](#custom-snippet-kinds), or change it in [Changing the document layout](#changing-the-document-layout).
- Other Spring REST Docs snippets (`requestBody()`, `responseBody(beneathPath("data"))`, and so on) are passed the same way.
- Snippets passed with `snippets(...)` don't go into the OpenAPI fragments.

## Changing the document layout

Each part of a generated document is produced by a function of `io.github.kardx.asciidoc.DocumentGenerator`. Extend it and override only the functions you need. Each function returns one block without a trailing newline, and blocks are joined with one blank line. Returning an empty string leaves that block out.

| Function | Part it produces | Default |
|---|---|---|
| `startPart()` | Very beginning of the document (before the title) | None |
| `titlePart(title, description)` | `= Title`, header attributes, index link, common includes | Combines the functions below |
| `config()` | Header attributes `Config(doctype, icons, sourceHighlighter, toc, tocLevels, isSectLinks)` | `book`, `font`, `highlightjs`, `left`, `3`, `true` |
| `tocTitleAttribute()` | The index link in the left table of contents title slot (one `:toc-title:` line) | Follows the `index-link` setting |
| `navigationPart()` | The back-to-index link (`← Back to index`) below the title and at the end of the document | `index-link`, `index-link-text` settings |
| `commonIncludeFiles()` | Files to include below the title | `common-include-files` setting |
| `sectionTitlePart(methodId, title, description)` | Section title and anchor for one test | `[[anchor]]` + `== icon:link[] Title` |
| `sectionRowPart(snippet, snippetDirectory)` | `.Subtitle` + `include::` for one snippet | Subtitles from the [table](#choosing-snippets-for-the-document) above |
| `endPart()` | Very end of the document | None |

When you override `sectionRowPart` to build the include line yourself, use the inherited `snippetsIncludePrefix` (protected; defaults to the value of the setting `snippets.include-prefix`, `{snippets}`) as the prefix. You can also set it with the constructor argument (`DocumentGenerator("{snippets}")`).

```kotlin
override fun sectionRowPart(snippet: SnippetInclude, snippetDirectory: String): String =
    "include::$snippetsIncludePrefix/$snippetDirectory/${snippet.fileName}[leveloffset=+1]"
```

Use settings for what settings can do. Putting files at the top of every document is `common-include-files`; the target and text of the index link are `index-link` and `index-link-text` (`none` removes all links) ([yml settings](configuration.md#yml-settings)).

### Example: table of contents on the right, custom subtitles

```kotlin
package com.example.demo.support

import io.github.kardx.asciidoc.DocumentGenerator
import io.github.kardx.config.SnippetFile
import io.github.kardx.config.SnippetInclude

/** Puts the table of contents on the right, removes the link icon from section titles, and replaces the subtitles. */
class MyDocumentGenerator : DocumentGenerator() {
    override fun config(): Config = Config(toc = "right", tocLevels = 2)

    override fun sectionTitlePart(methodId: String, title: String, description: String?): String = """
        [[${asciidocAnchor(methodId)}]]
        == $title
    """.trimIndent()

    override fun sectionRowPart(snippet: SnippetInclude, snippetDirectory: String): String {
        val block = super.sectionRowPart(snippet, snippetDirectory)
        val title = snippet.kind?.let(TITLES::get) ?: CUSTOM_TITLES[snippet.fileName] ?: return block

        return block.replaceFirst(".${snippet.title}", ".$title")
    }

    override fun tocTitleAttribute(): String = ""

    override fun endPart(): String = "'''\nContact: api@example.com"

    private companion object {
        val TITLES = mapOf(
            SnippetFile.CURL_INFO to "curl example",
            SnippetFile.REQUEST_INFO to "Request",
            SnippetFile.RESPONSE_INFO to "Response",
        )
        val CUSTOM_TITLES = mapOf("required-roles.adoc" to "Required roles")
    }
}
```

- `SnippetInclude` is one snippet. `kind` is the `SnippetFile` from the table above or a registered custom kind (`null` for other snippets of your own), `fileName` is a file name such as `request-fields.adoc`, `title` is the default subtitle, and `messageGroup` is the message number within the test (`request-fields-message-02` → 2, 0 for non-message snippets; useful when overriding `sectionRowPart` for message documents).
- `asciidocAnchor` is the function that turns a name into an AsciiDoc anchor (`DocumentGenerator.asciidocAnchor`). Changing the section anchor breaks the section links in the index, so leave the anchor line as is.
- If `tocTitleAttribute()` returns an empty string, only the index link in the table of contents title slot is removed; the links below the title and at the end of the document (`navigationPart`) remain.

Part of the resulting `build/generated-asciidoc/user-api-docs-test.adoc`:

```asciidoc
= User API
:doctype: book
:icons: font
:source-highlighter: highlightjs
:toc: right
:toclevels: 2
:sectlinks:

[.restdocs-nav]
link:index.html[&#8592; Back to index]

include::_landing.adoc[]

[[get-user]]
== Get user

include::{snippets}/user-api-docs-test/get-user/api-summary.adoc[]

.curl example
include::{snippets}/user-api-docs-test/get-user/curl-request.adoc[]

.Request
include::{snippets}/user-api-docs-test/get-user/http-request.adoc[]

...

.Required roles
include::{snippets}/user-api-docs-test/get-user/required-roles.adoc[]

[.restdocs-nav]
link:index.html[&#8592; Back to index]

'''
Contact: api@example.com
```

### Wiring into JUnit

Register `AutoRestDocumentationExtension` with `@RegisterExtension` instead of `@ExtendWith`, and pass a `DocumentGeneratorFactory` to its `documentGenerator` constructor argument. The extension is final; subclassing it and overriding `documentGenerator()` is no longer possible.

```kotlin
import io.github.kardx.asciidoc.DocumentGeneratorFactory
import io.github.kardx.junit.AutoRestDocumentationExtension
import org.junit.jupiter.api.extension.RegisterExtension

@SpringBootTest
@ActiveProfiles("docs")
abstract class ApiDocsTest {
    companion object {
        @JvmField
        @RegisterExtension
        val restDocs = AutoRestDocumentationExtension(documentGenerator = DocumentGeneratorFactory { MyDocumentGenerator() })
    }
}
```

- A static `@RegisterExtension` field in an abstract base class applies to every subclass, like `@ExtendWith` on the base class.
- The factory receives a `DocumentGeneratorContext` (`documentClass`: the outermost test class), so it can pick a generator per document.
- `@ExtendWith(AutoRestDocumentationExtension::class)` still works and uses the default generator.

### Wiring into Kotest

Pass the same `DocumentGeneratorFactory` to the `documentGenerator` constructor argument of `RestDocsExtension` ([Kotest](kotest.md#changing-the-rules)). As a constructor argument, a plain lambda works too.

```kotlin
val restDocs = extension(RestDocsExtension(documentGenerator = { MyDocumentGenerator() }))
```

Pass the same argument when registering it in the project config (`AbstractProjectConfig.extensions`; on Kotest 5.x, `override fun extensions()`).

### index document

The index is built not from a generated document but from a hand-written template (`src/docs/asciidoc/index-template.adoc`). Write the preamble, attributes, and links in the template as you like; links for each test class are inserted at the `// restdocs:api-index` line ([index document](asciidoc.md#index-document)).

## Using snippets in hand-written AsciiDoc

`.adoc` files in `src/docs/asciidoc/` become HTML along with the generated documents (files starting with `_` don't become standalone HTML). There are two ways to pull snippets in from here.

```asciidoc
= Usage guide
:toc: left

== Get user example (include)

include::{snippets}/user-api-docs-test/get-user/http-request.adoc[]

include::{snippets}/user-api-docs-test/get-user/response-fields.adoc[]

== Get user example (operation macro)

operation::user-api-docs-test/get-user[snippets='curl-request,http-response']
```

- `include::` inserts a single file as is. Write the subtitle yourself.
- `operation::<snippet directory>[snippets='...']` is a macro of the Spring REST Docs Asciidoctor extension that inserts each chosen snippet with a title (`Curl request`, `HTTP response`). If you omit `snippets`, all snippets in that directory are inserted. The Gradle plugin wires this extension into Asciidoctor.
- The file above becomes `build/api-docs/guide.html`, so link to it from the index template with `link:./guide.html[Usage guide]`.
- If you include a file that isn't `.adoc`, Asciidoctor doesn't rerun when only that file changes ([Gradle plugin](build.md#gradle-plugin)).

## Request and response preprocessing

`andDocument` (MockMvc, WebTestClient and RestTestClient, including SSE `performStream`) always applies the following before producing request and response examples (`http-request`, `http-response`, `curl-request` …), OpenAPI examples, and WireMock mappings. It's the same whether you built MockMvc or WebTestClient with `restDocsMockMvc` / `restDocsWebTestClient` or yourself.

| Order | Processing | What it does | How to change it |
|---|---|---|---|
| 1 | URI | Changed to `http://localhost`, port removed | Change it again with `http.requestPreprocessors` (later step) |
| 2 | Header, query and form parameter masking | Masks the values of selected headers and of query and form parameters (URI query, form body, non-file multipart parts; not masked by default) | `mask-sensitive-headers`, `masked-headers`, `masked-query-parameters`, `masked-header-value`, `masking.masker` ([Masking sensitive data](configuration.md#masking-sensitive-data)) |
| 3 | pretty print | Indents JSON and XML bodies | Can't be turned off |
| 4 | Volatile response headers | Removes response headers whose values change on every run (`Date` by default) and the `Expires` attribute of `Set-Cookie` values that have `Max-Age`. Headers documented with `responseHeaders(...)` (DSL or REST Docs snippets passed to `snippets(...)`) are kept | `http.removedResponseHeaders`, `removed-response-headers` ([List of settings](configuration.md#list-of-settings)) |
| 5 | Your preprocessors | The Spring REST Docs `OperationPreprocessor`s in `http.requestPreprocessors` / `http.responsePreprocessors`, in order | Below |

To remove headers from the examples entirely or show the real host, add Spring REST Docs preprocessors to `http.requestPreprocessors` and `http.responsePreprocessors`. They're applied **after** steps 1 to 4 above, so they can change the URI the library set, and they receive masked header values and pretty-printed bodies. They're set in code only (no yml key), and the default is an empty list. They're read at documentation time, so you can add them while keeping `restDocsMockMvc`'s Spring Security setup and `http.printRequests`.

```kotlin
import org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders
import org.springframework.restdocs.operation.preprocess.Preprocessors.modifyUris

// All tests: in the base class or @BeforeAll
RestDocsConfiguration.configure {
    http {
        requestPreprocessors = listOf(
            modifyHeaders().remove("X-Debug"),
            modifyUris().scheme("https").host("api.example.com").removePort(),
        )
        responsePreprocessors = listOf(modifyHeaders().remove("X-Trace-Id"))
    }
}

// Only in one test
RestDocsConfiguration.withSettings({ http.responsePreprocessors = listOf(modifyHeaders().remove("X-Request-Id")) }) {
    mockMvc.perform(get("/api/users/{id}", 1))
        .andDocument { ... }
}
```

The resulting `http-request.adoc` has no `X-Debug` header and contains `Host: api.example.com`, and `curl-request.adoc` is `curl 'https://api.example.com/api/users/1'`. `X-Trace-Id` is removed from the response example (and `X-Request-Id` too, in that one test).

- The `Date` header is already removed by default (step 4), because a server started with `bindToServer` sends a different value on every run and the snippets and WireMock mappings would change each time. To keep it, set `http.removedResponseHeaders = emptySet()` (yml `removed-response-headers: none`) or document it with `responseHeaders(...)`, either the DSL or REST Docs' `HeaderDocumentation.responseHeaders` passed to `snippets(...)`. To remove more headers the same way, add them: `http.removedResponseHeaders += "X-Request-Id"`. The yml list replaces the default instead of adding to it, so write `removed-response-headers: Date, X-Request-Id`.
- For the same reason, a `Set-Cookie` with `Max-Age` (Spring `ResponseCookie.maxAge(...)`) loses its `Expires` attribute, which is computed from the current time; browsers use `Max-Age` when both are present. `Expires` on a cookie without `Max-Age` stays. With `http.removedResponseHeaders` empty the response is kept as received.
- Headers removed from the examples are still validated against the actual request and response if you document them with `requestHeaders` / `responseHeaders`.
- The changed URI is used only in examples (snippets, OpenAPI examples). The OpenAPI server URL is set with `openapi3 { setServer(...) }`.
- Message documentation (`MessageDocumentation`) doesn't use these settings, since messages are not HTTP requests and responses.
- It also works if you build MockMvc yourself with Spring REST Docs default preprocessors (`documentationConfiguration(provider).operationPreprocessors().withRequestDefaults(...)`). REST Docs applies the default preprocessors after the preprocessors of the documentation call (steps 1 to 5 above), so the result is the same, but you have to add what `restDocsMockMvc` does (Spring Security filter chain, `print-requests`, the app's Jackson mapper) yourself.

## Enum code document format

The shape of the [Enum code document](asciidoc.md#enum-code-document) is decided by `EnumDocumentFormat`. Extend the default implementation `AsciiDocEnumDocumentFormat` and change only the functions you need.

| Function | Part it produces |
|---|---|
| `header(title, packageName, indexLink)` | Document title and header attributes |
| `tableHeader(title, className)` | Anchor, section title, and table header for one enum |
| `tableRow(code, description)` | One line per constant |
| `tableFooter()`, `footer()` | End of a table, end of the document |
| `anchorOf(title)` (protected) | Table anchor. By default, spaces in the title become `_` |

```kotlin
import io.github.kardx.asciidoc.AsciiDocEnumDocumentFormat

class KoreanEnumFormat : AsciiDocEnumDocumentFormat() {
    override fun tableHeader(title: String, className: String): String = """
        [[${anchorOf(title)}]]
        == $title (`$className`)

        [cols="2,8"]
        |===
        | Code | Description
    """.trimIndent()
}
```

Pass it to the constructor argument `format` of `EnumDocumentationExtension` (the same name for JUnit and Kotest).

```kotlin
// JUnit
class MyEnumDocs : EnumDocumentationExtension(packageName = "com.example.demo", title = "Code definitions", format = KoreanEnumFormat())

// Kotest (io.github.kardx.kotest.EnumDocumentationExtension)
EnumDocumentationExtension(packageName = "com.example.demo", title = "Code definitions", format = KoreanEnumFormat())
```

Result:

```asciidoc
[[User_status]]
== User status (`UserStatus`)

[cols="2,8"]
|===
| Code | Description

| ACTIVE
| Active
...
```

If you need a format other than AsciiDoc, implement `EnumDocumentFormat` directly (`fileExtension`, `header`, `tableHeader`, `tableRow`, `tableFooter`). In that case, Asciidoctor doesn't turn the result file into HTML.
