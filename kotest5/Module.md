# Module kardx-kotest5

An extension that creates Spring REST Docs snippets and a per-spec AsciiDoc document from Kotest specs. It is for Kotest 5.x
(for Kotest 6.x, use `kardx-kotest`). The two artifacts have the same packages and the same public API, so add only one of them.

- `io.github.kardx.kotest.RestDocsExtension`: register it in a spec (`extension(RestDocsExtension())` or `override fun extensions()`) or
  in the project config (`override fun extensions()`). It creates snippets for each leaf test (`then`, `it`, `test`, a row of a data test),
  and writes `<spec-name>.adoc` when the spec finishes. Pass its `provider` to `restDocsMockMvc`, `restDocsWebTestClient` or `MessageDocumentation`
- `io.github.kardx.kotest.TestNames`: the default rules that build section titles and snippet names from test names
- `io.github.kardx.kotest.EnumDocumentationExtension`: code reference documents for `@EnumCode` enums

The DSL (`andDocument { }`) and the extraction functions are the ones from core (`kardx`).
