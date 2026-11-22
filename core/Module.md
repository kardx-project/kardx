# Module kardx

Spring REST Docs 를 Kotlin 으로 쓰기 위한 라이브러리. 공개 패키지는 다음과 같다. 테스트에서는 보통 `dsl.*`,
`extractor.*`, `mockmvc.*`(또는 `webtestclient.*`) 세 패키지면 된다.

- `io.github.kardx.dsl`: 항목 문법(필드·파라미터·헤더·쿠키·파트·링크·SSE 이벤트), 필드 타입(`DocsFieldType`),
  `andDocument { }` 블록(`DocumentationBuilder`, `OpenApiBuilder`, 오류 응답 `ErrorResponseBuilder`, 느슨한 검증
  `RelaxedSection`, 파라미터 설명 출처 `FromParamTag`)과 메시지 블록(`MessageDocumentationBuilder`, `MessageDirection`,
  `MessageProtocol`), 검증 실패 예외 `DocumentationMismatchException`
- `io.github.kardx.extractor`: 타입에서 필드·파라미터 추출 (`responseFieldsOf`, `requestFieldsOf`, `parametersOf`,
  `wrappedResponseFieldsOf`), 파라미터 타입 `ofType`, 목록 미리보기 `describe`·`printDescription`
- `io.github.kardx.mockmvc`: MockMvc 진입점 (`ResultActions`·`ResultActionsDsl`·`MvcTestResult` 의 `andDocument`,
  `restDocsMockMvc`, `performAsync`, 요청·결과 보조 함수)
- `io.github.kardx.sse`: 끝나지 않는 SSE 스트림 문서화 (`MockMvc.performStream`). 이벤트 항목 문법(`events`)은 `dsl`
- `io.github.kardx.webtestclient`: WebTestClient 진입점(`ResponseSpec`·`BodyContentSpec.andDocument`)과 설정 헬퍼
- `io.github.kardx.resttestclient`: RestTestClient 진입점 (Spring Framework 7 / Boot 4 전용). `RestTestClient.Builder.restDocs`,
  `ResponseSpec`·`BodyContentSpec`·`BodySpec.andDocument`
- `io.github.kardx.websocket`: WebSocket·STOMP 메시지 문서화(`MessageDocumentation`), 테스트 도우미 `WebSocketHandlerTester`,
  STOMP 테스트 클라이언트(`StompTestClient`, `StompFrame`, `StompSubscription`)
- `io.github.kardx.config`: 전역 설정 `RestDocsConfiguration`·`RestDocsSettings`, 응답 래퍼, 설명 어노테이션,
  헤더 마스킹, 문서 언어(`DocsLanguage`), 생성 문서의 스니펫 종류(`SnippetFile`)와 섹션 순서(`DocumentSectionOrder`)
- `io.github.kardx.junit`: AsciiDoc 문서 자동 생성(`AutoRestDocumentationExtension`), Enum 코드 문서(`EnumDocumentationExtension`)
- `io.github.kardx.lifecycle`: 테스트 프레임워크와 무관한 문서화 수명주기(`DocumentationLifecycle`, `DocumentationRun`)와
  문서 구조 모델(`DocumentOutline`, `DocumentSection`). JUnit·Kotest 확장이 함께 쓴다. 테스트 프레임워크 확장을 만들 때만 쓰는
  opt-in API(`@RestDocsExtensionApi`)라 일반 테스트 코드에서는 쓰지 않는다. Kotest 확장은 별도 아티팩트
  `kardx-kotest` 에 있다
- `io.github.kardx.asciidoc`: AsciiDoc 문서 모양(`DocumentGenerator`), Enum 코드 문서 형식, 문서 index (`DocumentationIndex`)
- `io.github.kardx.openapi`: OpenAPI(epages) 스키마 이름(`SchemaNames`)과 operationId 규칙(`OperationIdRule`,
  `OperationIdInput`)
- `io.github.kardx.asyncapi`: AsyncAPI 3.0 문서·뷰어 생성 (`AsyncApiGenerator`, `AsyncApiInfo`, `AsyncApiServer`),
  인증 방식(`AsyncApiSecurityScheme`, `AsyncApiSecurityType`, `AsyncApiApiKeyLocation`)

`io.github.kardx.internal` 아래 패키지는 구현이다. 대부분 Kotlin `internal` 이라 사용자 코드에서 보이지 않고,
public 으로 보이는 것은 공개 inline 함수가 부르는 `@PublishedApi` 선언뿐이다. 이 패키지들은 호환을 보장하지 않으므로
직접 쓰지 않는다.
