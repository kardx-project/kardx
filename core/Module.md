# Module kardx

A library for writing Spring REST Docs in Kotlin. The public packages are listed below. Tests usually need only three packages:
`dsl.*`, `extractor.*` and `mockmvc.*` (or `webtestclient.*`).

- `io.github.kardx.dsl`: item syntax (fields, parameters, headers, cookies, parts, links, SSE events), field types (`DocsFieldType`),
  the `andDocument { }` block (`DocumentationBuilder`, `OpenApiBuilder`, error responses `ErrorResponseBuilder`, relaxed verification
  `RelaxedSection`, parameter description source `FromParamTag`) and the message block (`MessageDocumentationBuilder`, `MessageDirection`,
  `MessageProtocol`), the verification failure exception `DocumentationMismatchException`
- `io.github.kardx.extractor`: extracting fields and parameters from types (`responseFieldsOf`, `requestFieldsOf`, `parametersOf`,
  `wrappedResponseFieldsOf`), parameter type `ofType`, list preview `describe` and `printDescription`
- `io.github.kardx.mockmvc`: MockMvc entry points (`andDocument` on `ResultActions`, `ResultActionsDsl` and `MvcTestResult`,
  `restDocsMockMvc`, `performAsync`, request and result helpers)
- `io.github.kardx.sse`: documenting SSE streams that never end (`MockMvc.performStream`). The event item syntax (`events`) is in `dsl`
- `io.github.kardx.webtestclient`: WebTestClient entry points (`ResponseSpec` and `BodyContentSpec.andDocument`) and setup helpers
- `io.github.kardx.resttestclient`: RestTestClient entry points (Spring Framework 7 / Boot 4 only). `RestTestClient.Builder.restDocs`,
  `ResponseSpec`, `BodyContentSpec` and `BodySpec.andDocument`
- `io.github.kardx.websocket`: WebSocket and STOMP message documentation (`MessageDocumentation`), the test helper `WebSocketHandlerTester`,
  the STOMP test client (`StompTestClient`, `StompFrame`, `StompSubscription`)
- `io.github.kardx.config`: global settings `RestDocsConfiguration` and `RestDocsSettings`, response wrappers, description annotations,
  header masking, document language (`DocsLanguage`), snippet kinds in generated documents (`SnippetFile`) and section order (`DocumentSectionOrder`)
- `io.github.kardx.junit`: automatic AsciiDoc document generation (`AutoRestDocumentationExtension`), enum code documents (`EnumDocumentationExtension`)
- `io.github.kardx.lifecycle`: the documentation lifecycle independent of the test framework (`DocumentationLifecycle`, `DocumentationRun`) and
  the document structure model (`DocumentOutline`, `DocumentSection`). The JUnit and Kotest extensions share it. It is an opt-in API
  (`@RestDocsExtensionApi`) used only when building test framework extensions, so regular test code doesn't use it. The Kotest extension is in
  the separate artifact `kardx-kotest`
- `io.github.kardx.asciidoc`: AsciiDoc document layout (`DocumentGenerator`), enum code document format, the documentation index (`DocumentationIndex`)
- `io.github.kardx.openapi`: OpenAPI (epages) schema names (`SchemaNames`) and operationId rules (`OperationIdRule`,
  `OperationIdInput`)
- `io.github.kardx.asyncapi`: AsyncAPI 3.0 document and viewer generation (`AsyncApiGenerator`, `AsyncApiInfo`, `AsyncApiServer`),
  authentication schemes (`AsyncApiSecurityScheme`, `AsyncApiSecurityType`, `AsyncApiApiKeyLocation`)

The packages under `io.github.kardx.internal` are implementation. Most of it is Kotlin `internal` and not visible from user code;
the only declarations that appear public are the `@PublishedApi` ones called by public inline functions. These packages have no
compatibility guarantee, so don't use them directly.
