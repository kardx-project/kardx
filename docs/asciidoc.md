**English** | [한국어](ko/asciidoc.md)

# AsciiDoc documents

[Document list](README.md)

<!-- toc -->
**Contents**

- [Viewing in a browser](#viewing-in-a-browser)
- [Automatic document generation](#automatic-document-generation)
  - [Section order](#section-order)
  - [Snippets and sections by test shape](#snippets-and-sections-by-test-shape)
  - [Changing how documents look](#changing-how-documents-look)
- [Index document](#index-document)
- [Enum code document](#enum-code-document)
<!-- /toc -->

When the tests finish, the snippets are collected into AsciiDoc documents. You can also build an index that ties several documents together and Enum code tables.

## Viewing in a browser

You view AsciiDoc (`.adoc`) after the `asciidoctor` task converts it to HTML. `.adoc` files include snippets through the `{snippets}` path, so snippets may appear empty in an IDE preview.

| What you want to see | App address | File |
|---|---|---|
| Index (list of all documents and section links) | `http://localhost:8080/docs/index.html` | `build/api-docs/index.html` |
| The document of one test class | `http://localhost:8080/docs/user-api-docs-test.html` | `build/api-docs/user-api-docs-test.html` |
| A section in a document | `.../user-api-docs-test.html#get-user` | Same file + `#get-user` |
| Enum code document | `http://localhost:8080/docs/code/enum-code.html` | `build/api-docs/code/enum-code.html` |
| A single Enum table | `.../code/enum-code.html#User_status` | Same file + `#User_status` |
| AsciiDoc before conversion | None | `build/generated-asciidoc/*.adoc` |

The `/docs` in the address comes from the Gradle plugin's `bootJar.path` (default `static/docs`), and the port from the app settings. File paths assume `./gradlew apiDocs` and can be changed with the plugin's `outputDirectory`. The document name is the test class name, and the part after `#` is the test method name converted to kebab-case (`ProductApiDocsTest.getProductNotFound` → `product-api-docs-test.html#get-product-not-found`).

- Clicking an entry in the left table of contents (`:toc: left`) moves to that section. The section links in the index use the same anchors.
- An Enum table anchor is the `@EnumCode` title with spaces replaced by `_`.

## Automatic document generation

`AutoRestDocumentationExtension` replaces REST Docs' `RestDocumentationExtension`. Kotest specs use `RestDocsExtension`, which behaves the same way ([Kotest](kotest.md)). The behavior and document layout below are the same for both; only how sections are chosen (leaf tests that ran instead of test methods) and the naming rules differ. Like the original, it injects `RestDocumentationContextProvider` into test methods, and it also does the following.

1. Before a test method runs, it deletes that method's previous snippets. If you remove a parameter and rerun just one test, no stale snippets are left behind.
2. When a test class finishes, it collects the snippets and writes `<class-name>.adoc`. The class `@DisplayName` is the title, and each method `@DisplayName` is a section title. Tests declared in superclasses, `@ParameterizedTest` and `@RepeatedTest` are included too. Snippet directories and sections per test shape are described in [Snippets and sections by test shape](#snippets-and-sections-by-test-shape).
3. Tests that produce no snippets (such as tests that verify failure cases) get no section. A **failed test** has its snippets deleted and is left out of the document, so a failing API doesn't remain in the documents or OpenAPI.
   - The section anchor is the method name converted to kebab-case. A backtick method name (`` fun `get user succeeds`() ``) has its spaces replaced by `-`, giving `#get-user-succeeds` (the snippet directory follows the REST Docs rules as is).
   - Test classes with the same name in different packages use the same document and snippet directory, so a warning is printed. Give them different names.
4. It adds a link back to the index (`← Back to index`; `← 메인으로 돌아가기` with `language = "ko"`) at the start and end of the document, and in the title slot of the left table of contents (above `Table of Contents`), which stays visible while scrolling. Change the target and text with the `index-link` and `index-link-text` settings; with `index-link: none`, none of the three is added.
   - The table of contents link is a single `:toc-title: pass:[...]` line in the document header. It is not added when the table of contents is disabled (`Config(toc = "")`).
   - To drop only the table of contents link, override `DocumentGenerator.tocTitleAttribute()` to return an empty string ([Changing the document layout](customizing-snippets.md#changing-the-document-layout)).
   - The Enum code document (`code/enum-code.html`) gets only the table of contents link, with the target adjusted one level up (`../index.html`). The index document itself gets none.

Documents are generated with the settings in effect while the test runs (after `@BeforeEach`). So even after the test ends and the `@BeforeEach` settings are [reverted](configuration.md#how-to-change-settings), document settings such as `document.commonIncludeFiles` are kept.

```kotlin
@AsciiDescription("Member management")                                        // "(Member management)" next to the title
@DisplayName("User API")
class UserApiDocsTest : ApiDocsTest() {
    @Test
    @AsciiDescription("Admin only", bracket = AsciiDescription.Bracket.SQUARE)   // "[Admin only]"
    @DisplayName("Delete user")
    fun deleteUser() { ... }
}
```

### Section order

By default, sections are ordered by method name, and `@Nested` classes by class name. If JUnit ordering annotations are present, they are followed.

| On the test class | Section order |
|---|---|
| `@TestMethodOrder(MethodOrderer.OrderAnnotation::class)` + method `@Order(n)` | By `@Order` value. Methods without `@Order` come last |
| `@TestMethodOrder(MethodOrderer.DisplayName::class)` | By `@DisplayName` |
| `@TestMethodOrder(MethodOrderer.MethodName::class)` | By method name |
| `@TestClassOrder(ClassOrderer.OrderAnnotation::class)` + `@Nested` class `@Order(n)` | `@Nested` classes by `@Order` (`ClassName` and `DisplayName` work the same way) |
| None, or `Random` or a custom orderer | The `document-section-order` setting below |

```kotlin
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DisplayName("Product API")
class ProductApiDocsTest : ApiDocsTest() {
    @Test @Order(1) @DisplayName("Get product") fun getProduct() { ... }
    @Test @Order(2) @DisplayName("Search products") fun searchProducts() { ... }
    @Test @Order(3) @DisplayName("Create product") fun createProduct() { ... }
}
```

The default when there is no ordering annotation is chosen with a setting. `@TestMethodOrder` on an outer class also applies to its `@Nested` classes.

| Setting | yml | Values |
|---|---|---|
| `document.sectionOrder` | `document-section-order` | `METHOD_NAME` (`method-name`, default), `DISPLAY_NAME` (`display-name`: by `@DisplayName`) |

You can't choose source order (declaration order). JVM reflection doesn't guarantee that methods are returned in declaration order, so the result could differ between environments (JUnit's default order is "deterministic but intentionally nonobvious" for the same reason). To keep declaration order, add `@Order`.

### Snippets and sections by test shape

The snippet directory is `<document class>/<method slot>` (REST Docs' `{class-name}/{method-name}`, kebab-case). Each test shape is mapped as follows so that they don't overwrite each other.

| Test | Snippet directory | Document section |
|---|---|---|
| `UserApiTest.getUser()` | `user-api-test/get-user` | `#get-user` in `user-api-test.adoc` |
| `getUser()` in `@Nested inner class Success` inside `UserApiTest` | `user-api-test/success-get-user` | `#success-get-user` in the outer class document `user-api-test.adoc`. The title is prefixed with the `@Nested` class display name, as in `Success - Get user`. `@Nested` classes don't get their own documents |
| The 2nd invocation of `@ParameterizedTest getUser(id)` | `user-api-test/get-user/2` | A section per invocation, `#get-user-2`. The invocation display name (`[2] id=2`, `@ParameterizedTest(name = ...)`) is appended to the title |
| `@RepeatedTest` | `user-api-test/get-user` (same place for every repetition) | One section. The same request is repeated, so the last run remains |
| Overloads with the same name, `create()` and `create(info: TestInfo)` | `create`, `create-test-info` (the overload with parameters gets the parameter type names) | Two sections |

- In OpenAPI, invocations with the same path and method are merged into one operation (responses per status code).
- A failed invocation of a parameterized test has only its own directory deleted and is left out of the document.
- `@DisplayName` goes into the AsciiDoc title as is, so it is interpreted as inline formatting (`*emphasis*`, `` `code` ``). To show the characters literally, escape them with a backslash, as in `\*`, or wrap them as `+*star*+`. In OpenAPI summaries and tags, they appear as is.

### Changing how documents look

Which snippet types to include (`document-snippets`), subheadings, section titles and the table of contents position (overriding `DocumentGenerator`), snippet table columns (overriding templates) and custom snippets are covered in [Customizing snippets and documents](customizing-snippets.md).

## Index document

The documents of several test classes are tied together into one index (`/docs/index.html`). The Gradle plugin's `apiDocsIndex` task builds it, and every setting is optional.

- **List**: by default, every test class (JUnit and Kotest) that produced a document. When a test writes its class document, it also records the document name, title and order in `build/generated-asciidoc/.restdocs-documents/<document>.properties`, and the index reads those, so it doesn't scan the classpath. Each class has its own file, so parallel forks don't mix them up, and records of classes that didn't run again (incremental runs) are kept.
- **Template**: if `src/docs/asciidoc/index-template.adoc` exists, the entries and section links for each test class are inserted at its marker line (`// restdocs:api-index`). Otherwise, a built-in template that uses the same header attributes as the generated documents (`= <index.title>` + `== API list`, `== API 목록` with `language = "ko"`) is used. The title defaults to the project name.

```asciidoc
= Our Service API
:toc: left

== API list

// restdocs:api-index
```

```kotlin
kardx {
    index {
        title = "Our Service API"                       // title of the built-in template. Defaults to the project name
        // Set the two below together. When set, only tests in that package that extend the base class (and Kotest specs in the same package) are included
        packageName = "com.myapp"                       // subpackages are searched too
        baseClass = "com.myapp.support.ApiDocsTest"     // concrete classes that extend this class
    }
}
```

If only one of `packageName` and `baseClass` is set, or neither is set and no test has written a document, `apiDocsIndex` logs a one-line reason and skips. When `baseClass` is set, Kotest specs in the same package are searched in addition to its subclasses (only specs that produced documents become entries, [Kotest](kotest.md#index-and-openapi)).

To use it without the plugin, run `DocumentationIndexCli` with the `--output` argument. `--documents <directory>` is the directory containing the generated test documents (`*.adoc`) and their records (defaults to the directory of the `--output` file), `--template` is the template (the built-in template if omitted, titled with `--title`), and `--package` and `--base-class` are passed together when scanning a package (the plugin builds the index in `build/kardx/index/` and passes `asciidocDirectory` as `--documents`). In code, call `DocumentationIndex.write(template, output, testClasses)`, or `DocumentationIndex.write(template, output, testClasses, documents)` if the documents directory differs. Links are `./<document>.html`, so put the index and the test documents in the same directory when rendering. Entries are ordered by `@DisplayName` (the class name if absent) and are appended at the end if there is no marker. To set the order, put JUnit `@Order(n)` on test classes. Classes with `@Order` come first in value order, and the rest follow in `@DisplayName` order.

```kotlin
@Order(1)
@DisplayName("Auth API")
class AuthApiDocsTest : ApiDocsTest()
```

## Enum code document

Enums annotated with `@EnumCode` are scanned to build a code table document. Each enum gets a section titled with its `value` and a `Code` / `Description` table, with one row per constant.

```kotlin
@EnumCode("User status")
enum class UserStatus(@get:JsonValue val code: String, val description: String) {
    ACTIVE("A", "Active"),
    INACTIVE("I", "Inactive"),

    @Deprecated("Use INACTIVE")
    DORMANT("D", "Dormant"),
}

class MyEnumDocs : EnumDocumentationExtension(packageName = "com.myapp", title = "Code definitions")

@ExtendWith(MyEnumDocs::class, AutoRestDocumentationExtension::class)
abstract class ApiDocsTest
```

For Kotest, register `io.github.kardx.kotest.EnumDocumentationExtension(packageName = "com.myapp", title = "Code definitions")` in a spec or in the project config ([Kotest](kotest.md#enum-code-document)).

Result (`build/generated-asciidoc/code/enum-code.adoc`):

```asciidoc
[[User_status]]
== User status
enum : UserStatus

[cols="3,7"]
|===
| Code | Description

| A
| Active

| I
| Inactive

|===
```

| Attribute | Table column | When left empty (default) |
|---|---|---|
| `value` | Section title and anchor | (required) |
| `codeProperty` | `Code` | The value actually written to JSON: `@JsonValue` member → the constant's `@JsonProperty` → the constant name. The same value as the value list after field descriptions and `enumOf` |
| `descriptionProperty` | `Description` | The property named by the `extraction.enumDescriptionProperty` setting (yml `kardx.enum-description-property`, default `description`) → the `description` property → the constant name |

- To set them explicitly: `@EnumCode("Payment status", codeProperty = "code", descriptionProperty = "label")`. To use the constant name as the code, use `codeProperty = "name"`.
- Properties are looked up in this order: a field declared by the enum → a getter (`getCode()`) → a no-argument method with the same name (`name()`). Constants whose value is `null` use the constant name.
- If an explicitly given property doesn't exist on the enum, it is treated as a typo and document generation fails: `@EnumCode(descriptionProperty = "descripton") on com.myapp.UserStatus: the enum has no property 'descripton'. ...`. Only when looking up defaults does it silently move on to the next candidate.
- The output is `build/generated-asciidoc/code/enum-code.adoc`. You can change it with `fileName` and `outputDirectory`.
- Subpackages and multiple jars are scanned. `@Deprecated` constants are left out.
- Even with several test classes, it is built only once per run.
- From other documents, link to it with `link:./code/enum-code.html#User_status[User status]`. The anchor is the title with spaces replaced by `_`.
- To change the header or column widths, extend `AsciiDocEnumDocumentFormat` ([Enum code document format](customizing-snippets.md#enum-code-document-format)).
