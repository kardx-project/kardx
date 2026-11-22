# Changelog

이 프로젝트의 주요 변경을 기록합니다. 형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 를 따르고, 버전은 [Semantic Versioning](https://semver.org/lang/ko/) 을 따릅니다.

## [Unreleased]

첫 공개 버전(0.1)입니다. Spring Boot 3.5 (REST Docs 3.0) 와 4.0·4.1 (REST Docs 4.0) 을 같은 아티팩트로 지원합니다.

라이브러리가 생성 문서에 넣는 문구의 기본 언어는 영어입니다. 개발 중 버전처럼 한국어 문구(`메인으로 돌아가기`, `API 목록`, `제약:` 등)를 쓰려면 `kardx { language = "ko" }` 를 정합니다. 이에 따라 `document.indexLinkText` 는 `String?` 이고 `null`(기본값)이면 언어의 기본 문구를 씁니다. 로그·경고·예외 메시지, Gradle 콘솔 출력, `apiDocsCheck` 보고서는 언어와 상관없이 영어입니다.

### Breaking (0.1 → next)

1.0 전에 공개 API 를 정리했습니다. yml 키(`kardx.*`)와 Gradle 플러그인 DSL(`kardx { }`)은 바뀌지 않았습니다.

- **설정을 묶음으로 나눴다.** `RestDocsSettings` 의 평평한 프로퍼티가 `extraction`, `descriptions`, `masking`, `http`, `openApi`, `asyncApi`, `output`, `document` 로 옮겨 갔다(`language` 만 그대로). 블록으로 열거나 경로로 쓴다 ([설정 목록](docs/ko/configuration.md#설정-목록))

  ```kotlin
  // 0.1
  configure { openApiEnabled = false; maskSensitiveHeaders(); maxExtractionDepth = 5 }
  // next
  configure {
      openApi.enabled = false
      masking { maskSensitive() }
      extraction.maxDepth = 5
  }
  ```

  | 0.1 | next |
  |---|---|
  | `responseWrapper`, `responseWrapper(...)`, `responseWrapper<T>()` | `extraction.responseWrapper` (같은 함수들) |
  | `objectMapper`, `propertyNamingStrategy` | `extraction.objectMapper`, `extraction.propertyNameStrategy` |
  | `enumDescriptionProperty`, `maxExtractionDepth`, `arrayElementFieldsOptional`, `kotlinxSerialization` | `extraction.enumDescriptionProperty`, `extraction.maxDepth`, `extraction.arrayElementFieldsOptional`, `extraction.kotlinxSerialization` |
  | `descriptionResolver`, `sourceDocsEnabled` | `descriptions.resolver`, `descriptions.sourceDocs` |
  | `descriptionAnnotations`, `descriptionAnnotation(...)`, `descriptionFrom<A> { }` | `descriptions.annotations`, `descriptions.annotation(...)`, `descriptions.from<A> { }` |
  | `maskedAnnotations`, `maskedAnnotation(...)` | `masking.annotations`, `masking.annotation(...)` |
  | `maskedHeaders`, `maskedQueryParameters`, `maskedHeaderValue` | `masking.headers`, `masking.queryParameters`, `masking.replacement` |
  | `headerMasker`, `maskSensitiveHeaders()` | `masking.masker`, `masking.maskSensitive()` |
  | `RestDocsSettings.SENSITIVE_HEADERS`, `RestDocsSettings.SENSITIVE_QUERY_PARAMETERS` | `MaskingSettings.SENSITIVE_HEADERS`, `MaskingSettings.SENSITIVE_QUERY_PARAMETERS` |
  | `requestPreprocessors`, `responsePreprocessors`, `removedResponseHeaders`, `printRequests`, `applySpringSecurity` | `http.` + 같은 이름 |
  | `RestDocsSettings.VOLATILE_RESPONSE_HEADERS` | `HttpSettings.VOLATILE_RESPONSE_HEADERS` |
  | `openApiEnabled`, `openApiIncludeStreams`, `openApiSecuritySchemes`, `openApiGlobalHeaders` | `openApi.enabled`, `openApi.includeStreams`, `openApi.securitySchemes`, `openApi.globalHeaders` |
  | `openApiErrorSchema`, `openApiErrorSchema(...)`, `openApiErrorSchema<T>()` | `openApi.errorSchema` (같은 함수들) |
  | `openApiSchemaNames`, `openApiSchemaNamesWithoutWrapper`, `openApiSchemaNameResolver` | `openApi.schemaNames`, `openApi.schemaNamesWithoutWrapper`, `openApi.schemaNameResolver` |
  | `openApiOperationIdRule`, `openApiOperationIdResolver` | `openApi.operationIdRule`, `openApi.operationIdResolver` |
  | `asyncApiEnabled` | `asyncApi.enabled` |
  | `snippetsDirectory`, `asciidocDirectory`, `wireMockStubs` | `output.` + 같은 이름 |
  | `snippetsIncludePrefix`, `commonIncludeFiles`, `indexLink`, `indexLinkText` | `document.includePrefix`, `document.commonIncludeFiles`, `document.indexLink`, `document.indexLinkText` |
  | `documentSnippets`, `documentSectionOrder` | `document.snippets`, `document.sectionOrder` |

- **콜백 설정이 fun interface 가 됐다.** `descriptions.resolver`(`DescriptionResolver`), `extraction.objectMapper`(`ObjectMapperProvider`), `extraction.propertyNameStrategy`(`PropertyNameStrategy`, 입력 `PropertyNameInput.name`), `openApi.schemaNameResolver`(`SchemaNameResolver`, 입력 `SchemaNameInput.type`), `openApi.operationIdResolver`(`OperationIdResolver`). 프로퍼티에 람다를 대입하려면 SAM 생성자를 쓴다. 생성자 인자에는 그냥 람다를 넘겨도 된다

  ```kotlin
  // 0.1
  propertyNamingStrategy = { PropertyNamingStrategies.SNAKE_CASE.nameForField(null, null, it) }
  openApiSchemaNameResolver = { "Dto" + SchemaNames.of(it) }
  // next
  extraction.propertyNameStrategy = PropertyNameStrategy { PropertyNamingStrategies.SNAKE_CASE.nameForField(null, null, it.name) }
  openApi.schemaNameResolver = SchemaNameResolver { "Dto" + SchemaNames.of(it.type) }
  ```

- **`HeaderMasker` 를 없앴다.** `masking.masker` 는 `ValueMasker` 이고 `MaskTarget`(`location`, `name`, `value`, `cookieName`)을 받는다. 헤더·쿠키·파라미터를 `location` 으로 가른다

  ```kotlin
  // 0.1
  headerMasker = HeaderMasker { name, value -> if (name == "Authorization") value.substringBefore(' ') + " ****" else "****" }
  // next
  masking.masker = ValueMasker { target ->
      if (target.location == MaskLocation.HEADER && target.name == "Authorization") target.value.substringBefore(' ') + " ****" else "****"
  }
  ```

- **기본 설명 규칙을 이어 쓰는 방식이 바뀌었다.** 기본 단계가 `DescriptionResolver.DEFAULT` 로 공개되어, 직접 만든 규칙이 `@Description`·어노테이션 목록·KDoc 을 잃지 않게 조합한다 ([설명 규칙 조합하기](docs/ko/http-api.md#설명-규칙-조합하기))

  ```kotlin
  // 0.1
  val previous = descriptionResolver
  descriptionResolver = { it.findAnnotation<Schema>()?.description ?: previous(it) }
  // next
  descriptions.resolver = DescriptionResolver { it.findAnnotation<Schema>()?.description } orElse descriptions.resolver
  ```

- **입력 클래스의 생성자가 internal 이 됐다.** `PropertyContext`, `OperationIdInput` 은 라이브러리만 만든다. 앞으로 프로퍼티가 더해질 수 있다
- **JUnit `AutoRestDocumentationExtension` 이 final 이 됐다.** 상속해 `documentGenerator()` 를 재정의하는 대신 생성자 인자 `documentGenerator`(`DocumentGeneratorFactory`)를 넘겨 `@RegisterExtension` 으로 등록한다. 추상 기본 클래스의 static `@RegisterExtension` 은 하위 클래스에도 적용된다. `@ExtendWith(AutoRestDocumentationExtension::class)` 는 그대로 쓴다

  ```kotlin
  // 0.1
  class MyDocsExtension : AutoRestDocumentationExtension() {
      override fun documentGenerator(): DocumentGenerator = MyGenerator()
  }
  // next
  companion object {
      @JvmField
      @RegisterExtension
      val restDocs = AutoRestDocumentationExtension(documentGenerator = DocumentGeneratorFactory { MyGenerator() })
  }
  ```

- **`EnumDocumentationExtension` 의 형식은 생성자 인자 `format` 으로 준다.** JUnit 은 `protected open fun format()` 재정의를 없앴고(상속은 그대로 된다), Kotest 는 `() -> EnumDocumentFormat` 대신 인스턴스를 받는다

  ```kotlin
  // 0.1
  class MyEnumDocs : EnumDocumentationExtension(packageName = "com.example") {
      override fun format(): EnumDocumentFormat = KoreanEnumFormat()
  }
  // next
  class MyEnumDocs : EnumDocumentationExtension(packageName = "com.example", format = KoreanEnumFormat())
  ```

- **Kotest `RestDocsExtension` 의 규칙 인자 타입.** `sectionTitle`(`SectionTitleProvider`)·`snippetName`(`SnippetNameProvider`)은 `TestPath`(`names`, `specClass`)를 받고, `documentGenerator`(`DocumentGeneratorFactory`)는 `DocumentGeneratorContext` 를 받는다. `{ MyGenerator() }` 는 그대로 컴파일된다

  ```kotlin
  // 0.1
  RestDocsExtension(sectionTitle = { names -> names.last() })
  // next
  RestDocsExtension(sectionTitle = { test -> test.names.last() })
  ```

- **`SnippetFile` 이 enum 에서 클래스가 됐다.** 상수 이름(`SnippetFile.RESPONSE_BODY` …)은 그대로이고, `SnippetFile.entries` 는 `SnippetFile.BUILT_IN` 으로 바뀌었다. `SnippetFile` 에 대한 `when` 에는 `else` 가 필요하다
- **확장 제작 API**: `DocumentationLifecycle.writeDocument` 의 생성기 인자가 `DocumentGeneratorFactory` 가 됐다 (`writeDocument(documentClass, generator, outline)`)

### Added

#### `kardx` (core)

- 문서화 DSL `andDocument { }`: 경로·쿼리·폼 파라미터, 요청·응답 헤더, 쿠키, 멀티파트 파트, 링크, 본문 필드, 임의 스니펫, OpenAPI 설정. MockMvc(`ResultActions`, Kotlin DSL `ResultActionsDsl`, `MockMvcTester`)와 WebTestClient(`ResponseSpec`, `BodyContentSpec`, `BodySpec`)에서 같은 블록을 쓴다
- 설정 헬퍼 `restDocsMockMvc`·`restDocsWebTestClient`(앱 Jackson 매퍼와 `kardx.*` 속성 반영)와 MockMvc 보조 함수(`andStatus`, `andReturnEntity<T>()`, `andReturnAsObject<T>()`, `andPrint()` 등)
- 타입에서 필드·파라미터 추출 `responseFieldsOf<T>()`, `requestFieldsOf<T>()`, `parametersOf<T>()`: 제네릭, 컬렉션, sealed, value class, Map, 순환 참조, Java record·bean, Jackson 이름 규칙, Bean Validation 제약, Enum 상수 설명, Spring Data `Page`·`Slice`, `ProblemDetail`
- 공통 응답 래퍼(`ResponseWrapper`, `wrappedResponseFieldsOf<T>()`), 목록 편집(필드 `except`·`optionalAt`·`optionalUnder`, 파라미터 `except`·`optionalAt`, `RestDocsParam.name`), 추출 결과 미리보기(`describe()`, `printDescription()`)
- 문서 표시: `@Description`, `@Masked`, `@Deprecated`, 다른 어노테이션에서 설명·마스킹 규칙 읽기(`description-annotations`, `masked-annotations`)
- 검증 실패 진단 `DocumentationMismatchException`: 문서화하지 않은 항목과 없는 항목, 필드 타입 불일치(`FieldTypesDoNotMatchException`·`FieldTypeRequiredException`), 붙여 넣을 DSL 줄, 원인 힌트
- 글자가 아닌 요청·응답 본문과 multipart 파일 파트는 스니펫에 `<binary: N bytes>` 로만 남긴다 (WireMock 매핑은 원래 바이트)
- 파일 본문 설명 `responseFile("...")`·`requestFile("...")`: 바이너리 본문마다 `response-file`·`request-file` 표(설명, charset 을 뺀 미디어 타입, `Content-Disposition` 의 파일 이름, 크기)를 만들어 생성 문서의 본문 원문 자리에 넣고, `response-body`·`request-body` 의 바이너리 본문은 `[source,pdf]` 대신 언어 없는 `[source]` 블록으로 쓴다. Gradle 플러그인은 설명을 OpenAPI 본문 스키마의 `description` 으로 넣고, 바이너리 미디어 타입에서 `charset` 을 빼고(`application/pdf`), 파일 응답의 `Content-Disposition` 헤더에 실제 값을 `example` 로 넣는다
- 선택적 헤더 마스킹(`masking.maskSensitive()`, `masking.headers`, `ValueMasker`)과 Spring REST Docs 전처리기 추가(`http.requestPreprocessors`, `http.responsePreprocessors`)
- 쿼리·폼 파라미터 마스킹 `masking.queryParameters`(yml `kardx.masked-query-parameters`, 기본값 빈 목록): 이름이 맞는 파라미터 값을 요청 URI 의 쿼리, `application/x-www-form-urlencoded` 본문, multipart 의 파일이 아닌 파트에서 가려 curl·httpie·http-request·request-body 스니펫, OpenAPI, WireMock 매핑에 `****` 로 남긴다 (본문을 가리면 `Content-Length` 도 맞춘다). WireMock 은 가린 파라미터를 `matches ".*"` 로 받고, `masking.masker` 를 정했으면 그것을 부른다. 잘 알려진 자격 증명 이름은 `MaskingSettings.SENSITIVE_QUERY_PARAMETERS`(`access_token`, `id_token`, `refresh_token`, `token`, `api_key`, `apikey`, `client_secret`, `client_assertion`, `password`, `passwd`, `current_password`, `new_password`, `signature`, `sig`, `X-Amz-Signature`, `X-Amz-Security-Token`, `X-Goog-Signature`). `masking.masker` 는 파라미터에도 `MaskLocation.PARAMETER` 와 파라미터 이름으로 불린다
- 문서화한 응답에서 뺄 헤더 `http.removedResponseHeaders`(yml `kardx.removed-response-headers`, 기본값 `HttpSettings.VOLATILE_RESPONSE_HEADERS` = `Date`, `none` 이면 빼지 않음). yml 목록은 기본값에 더하지 않고 전체를 정한다. `responseHeaders(...)` 로 문서화한 헤더(DSL, `snippets(...)` 로 넘긴 REST Docs `responseHeaders`)는 빼지 않는다. 비어 있지 않으면 `Max-Age` 가 있는 `Set-Cookie` 의 `Expires` 속성(지금 시각에서 계산되어 실행마다 바뀜)도 뺀다
- SSE 문서화(`performAsync` + `events(...)`, 끝나지 않는 스트림의 `performStream`), WebSocket·STOMP 메시지 문서화(`MessageDocumentation`, `WebSocketHandlerTester`)
- AsyncAPI 3.0 생성(`AsyncApiGenerator`, `AsyncApiCli`)과 Try it out 뷰어
- epages restdocs-api-spec 연동 OpenAPI 조각: 타입·형식·제약·예시·기본값, Enum, multipart·쿠키, 자동 스키마 이름, 공통 오류 스키마, 보안 스키마(헤더 없이 인증한 테스트는 `openApi { bearerAuth() }`·`basicAuth()`)와 전역 헤더, 응답 상태별 operation, operationId 규칙(`handler-method` 기본)
- OpenAPI 를 핸들러에서 채우기: 타입을 적지 않은 경로·쿼리·폼 파라미터의 타입(컨트롤러 인자), deprecated(`@Deprecated`), 태그(문서 클래스 제목)·요약(테스트 표시 이름). 블록의 `deprecated = true`·`deprecated("이유")` 는 `api-summary` 에 `*Deprecated*` 줄로도 나간다. 문서화하지 않은 경로 변수는 경고
- AsciiDoc 문서 자동 생성(`AutoRestDocumentationExtension`), 문서 index(`DocumentationIndex`, `DocumentationIndexCli`), Enum 코드 문서(`EnumDocumentationExtension`), Required·Constraints 열이 있는 스니펫 템플릿, 생성 문서의 index 링크와 섹션 순서 설정
- 설정 `RestDocsConfiguration.configure { }`·`withSettings({ }) { }`·`current`, `application.yml` 의 `kardx.*` 키(기능별 묶음 `open-api.*`, `async-api.*`, `snippets.*`, `response-wrapper.*`), IDE 자동 완성용 설정 메타데이터, 모르는 키 경고
- WireMock 매핑 출력(`wire-mock-stubs`)
- 테스트 프레임워크 확장 제작용 opt-in API `io.github.kardx.lifecycle` (`@RestDocsExtensionApi`)
- 문서 언어 `RestDocsSettings.language`(`DocsLanguage.EN` 기본, `KO`)와 yml·시스템 속성 `kardx.language`: index 문구, index 링크, 스키마 설명 접두사, Spring Data·`ProblemDetail`·sealed 자동 설명, AsyncAPI 채널 설명과 뷰어 화면
- 선언한 오류 응답 `errorResponse(status, "설명")`: 테스트가 만들지 않는 오류(400~599)를 블록에서 선언한다. 블록 형태로 본문 필드(`fields(...)`, `fields<T>()`)·예시(`example(json)`, `example(객체)`)·`contentType` 을, 한 줄 형태 `errorResponse(409, "...", 예시 객체)` 로 필드 추출과 예시를 한 번에 준다. `error-responses` 스니펫(상태·설명 표, 응답별 필드 표·예시)이 생기고 OpenAPI 응답과 스키마가 된다. 선언일 뿐 검증하지 않으며 예시가 선언한 필드와 맞는지만 확인한다. 테스트가 실제로 받은 상태와 같은 선언은 뺀다
- 항목별 느슨한 검증 `relaxed(RelaxedSection...)`: 경로·쿼리·폼 파라미터, 요청·응답 필드, 파트·파트 필드, 요청·응답 쿠키, 링크, SSE 이벤트 중 고른 것만 문서화하지 않은 항목을 허용한다
- 문서 언어 불일치 경고: Gradle 플러그인으로 테스트를 돌릴 때 `kardx.language`·`RestDocsConfiguration.configure` 로 정한 문서 언어가 플러그인 언어와 다르면 `io.github.kardx.config` 로거에 JVM 마다 한 번 WARN(`kardx: the documentation language is '<core>' ... but the Gradle plugin uses '<plugin>' ...`)을 남기고 `kardx { language = "<core>" }` 를 안내한다. 플러그인 없이는 경고하지 않는다
- `StompTestClient`(`StompSubscription`, `StompFrame`): 테스트에서 실행 중인 앱의 STOMP 엔드포인트에 CONNECT 헤더로 연결하고, 구독·전송·프레임 대기(`sendAndReceive` 는 구독이 등록될 때까지 다시 보냄)를 맡는다. spring-messaging·spring-websocket 이 테스트 클래스패스에 있어야 한다
- RestTestClient 진입점(Spring Boot 4 / Spring Framework 7 전용, 패키지 `io.github.kardx.resttestclient`): `RestTestClient.Builder.restDocs(provider)` 와 `ResponseSpec`·`BodyContentSpec`·`BodySpec.andDocument { }`. REST Docs 4.0 에서 동작한다 (`spring-restdocs-resttestclient` 불필요). multipart 파트는 나누지 않고, 핸들러 기반 기본값(operationId, 파라미터 타입, `@Deprecated`)은 쓰지 않으며, 상대 URI 는 `http://localhost` 기준으로 푼다
- AsyncAPI 인증 방식 `AsyncApiInfo.securitySchemes`(`AsyncApiSecurityScheme.bearer`·`basic`·`apiKey`, `AsyncApiApiKeyLocation`)와 CLI `--security name=bearer|basic|apiKey:<header|query|cookie>:<name>`: `components.securitySchemes` 와 서버 `security` 가 된다. 뷰어 Try it out 에 인증 상자가 생겨 STOMP 는 CONNECT 헤더로, WebSocket·SSE 는 고르면 쿼리 파라미터로 자격 증명을 보낸다 (브라우저가 헤더를 붙일 수 없음. 쿠키는 페이지가 넣지 못함)
- 엔드포인트 목록: 애플리케이션 컨텍스트마다 처음 문서화하는 요청에서 `RequestMappingHandlerMapping`(서블릿·리액티브)을 한 번 읽어 스니펫 디렉터리의 `.kardx/endpoints/<id>.json` 에 메서드·경로 패턴·핸들러·deprecated·API 버전을 쓴다. 매핑이 같은 컨텍스트는 같은 파일을 한 번만 쓰고, standalone 설정·RestTestClient(컨텍스트를 알 수 없음)와 함수형 라우트는 건너뛴다
- sealed·`@JsonTypeInfo`/`@JsonSubTypes` 타입의 하위 타입 스키마를 `openapi-extras.json` 에 남긴다(`requestBodyPolymorphism`·`responseBodyPolymorphism`). 판별자는 `use = NAME | SIMPLE_NAME | CLASS` 이면서 `include = PROPERTY | EXISTING_PROPERTY` 일 때만 남기고, `WRAPPER_OBJECT`·`WRAPPER_ARRAY` 는 남기지 않는다. 필드 목록마다 추출한 타입과 경로를 기억하므로 경로를 붙인 추출(`requestFieldsOf<T>("data")`), 공통 응답 래퍼(`wrappedResponseFields<T>()` 의 `data.method`, 데이터 자체가 다형이면 `data`), 여러 목록을 합친 본문도 경로 앞부분을 붙여 남긴다. 직접 쓴 필드만 있는 본문은 남기지 않는다. 필드 표·스니펫·검증은 그대로다
- `openApi { oauth2(vararg scopes) }`·`openIdConnect(vararg scopes)`: 헤더 없이 인증한 테스트의 OAuth2·OpenID Connect 보안 요구사항과 scope
- `openApi { summary = "..."; description = "..." }`: OpenAPI operation 의 summary·description 을 테스트 이름·블록 설명 대신 정한다. `openapi-extras.json` 의 `summary`·`description` 으로 남기고, REST Docs 스니펫(`api-summary`)과 예시 이름은 그대로다
- Kotlin 생성자 기본값 읽기(요청 추출에서만): `parametersOf<T>()` 로 추출한 파라미터는 `withDefaultValue` 처럼(직접 적은 값이 이김), `requestFields<T>()` 로 추출한 본문 필드는 필드 표의 `(Default: 20)` 과 OpenAPI `default` 용(`fieldDefaults`)으로 남긴다. 문자열·숫자·불리언·Enum 만, 클래스마다 한 번 읽고 실패하면 건너뛴다
- Spring Framework 7 API 버전(`@GetMapping(version = "1.1")` 등)을 핸들러에서 읽어 OpenAPI 조각과 엔드포인트 목록에 남긴다. 기준 버전 표기 `"1.2+"` 는 `"1.2"` 로 적는다. RestTestClient 와 함수형 라우트는 버전을 남기지 않고, Spring Framework 6 에서는 아무것도 하지 않는다
- KDoc/Javadoc 을 설명으로: 기본 설명 출처가 `@Description` → `description-annotations` 다음에 Gradle 플러그인이 남긴 소스 주석(`-Dkardx.source-docs`)을 본다. 필드·`parametersOf` 파라미터·enum 상수 설명에 쓰고, 핸들러 메서드 주석은 `openapi-extras.json` 의 `sourceSummary`·`sourceDescription` 으로 남긴다(MockMvc·WebTestClient, RestTestClient 는 핸들러를 몰라 없음). enum 상수 주석은 다른 설명(`extraction.enumDescriptionProperty`)이 없을 때만 쓰고 `@EnumCode(descriptionProperty = ...)` 를 직접 정하면 쓰지 않는다. `descriptions.resolver` 를 기본 단계 없이 바꾸면 필드·파라미터 주석도 함께 빠진다. 파일은 JVM 마다 한 번 읽고, 읽을 수 없으면 경고 한 번 후 끈다. yml `kardx.source-docs.enabled: false`(`descriptions.sourceDocs`) 로 끈다
- 이름만 적는 파라미터 선언 `pathParameters("id")`, `queryParameters("keyword", "page")`, `formParameters(...)`, `requestHeaders(...)`, `requestParts(...)`: 설명은 요청을 처리한 핸들러 메서드 KDoc·Javadoc 의 `@param` 에서 가져온다(`@PathVariable`·`@RequestParam`·`@RequestHeader`·`@RequestPart` 의 이름, 없으면 인자 이름으로 맞춤, `methodDocs` 와 상관없음). 테스트에 적은 설명이 이기고, 설명을 찾지 못하면 항목 이름을 적은 `IllegalArgumentException` 으로 실패한다. 수식어가 필요하면 설명 자리에 `FromParamTag` 를 둔다(`"page" queryMeans FromParamTag isOptional true example "0"`, `pathMeans`·`queryMeans`·`formMeans`·`headerMeans`·`partMeans`, 응답·메시지 헤더에는 쓸 수 없음). RestTestClient·함수형 라우트와 `-parameters` 없이 컴파일한 Java 핸들러에서는 찾지 못한다
- JUnit 테스트 메서드·`@BeforeEach` 파라미터 주입: `AutoRestDocumentationExtension` 이 `MockMvc`·`WebTestClient` 파라미터에 REST Docs 용 클라이언트(`restDocsMockMvc(context, provider)` 와 같은 것)를 넣는다. 기본 클래스와 `@BeforeEach` 가 필요 없다. `@Autowired`·`@Qualifier`·`@Value` 파라미터와 생성자 파라미터는 Spring 에 맡기고, Spring 테스트가 아니면 `KDX-014` 로 실패한다
- provider 없는 `restDocsMockMvc(context)`·`restDocsWebTestClient(context)`: 요청마다 그때 실행 중인 테스트(JUnit 테스트 메서드, Kotest 리프 테스트)로 문서화하므로 필드·생성자·Kotest 스펙 본문에서 한 번 만들어 둘 수 있다. 실행 중인 테스트가 없으면 `KDX-013`
- 오류 코드 `RestDocsErrorCode`(`io.github.kardx.diagnostics`, 45개): 라이브러리의 오류·경고 메시지가 `kardx[KDX-011]: …` 로 시작하고 문제 해결 문서의 그 코드 앵커(`troubleshooting.md#kdx-011`) 주소로 끝난다. 백의 자리가 단계(0xx 준비·설정, 1xx 문서화, 2xx OpenAPI, 3xx 메시지, 4xx Kotest, 5xx 파일, 6xx 시작 로그)
- 다른 REST Docs 설정으로 문서화하면 알린다: Spring Boot `@AutoConfigureRestDocs` 로 받은 클라이언트처럼 이 라이브러리가 만들지 않은 설정이면 스니펫 위치가 다를 때 `KDX-011` 로 실패하고(섹션이 문서에서 조용히 빠지던 문제), 같을 때는 JVM 마다 한 번 경고한다. REST Docs 설정이 없는 클라이언트(주입받은 그대로의 `MockMvc`)는 `KDX-012`
- Spring HATEOAS 모델을 HAL 모양으로 추출: `EntityModel<T>`(`T` 필드 + `_links`), `RepresentationModel` 하위 클래스(자기 필드 + `_links`), `CollectionModel<T>`(`_embedded.<관계>[]` + `_links`), `PagedModel<T>`(+ `page.size`·`totalElements`·`totalPages`·`number`). 관계 이름은 `@Relation(collectionRelation)` → evo-inflector 복수형 → `<타입>List`. `_links` 는 optional subsection, HAL 키에는 이름 규칙을 적용하지 않는다. spring-hateoas 에 의존하지 않고 클래스 이름으로 판별한다
- kotlinx.serialization `@Serializable` 클래스를 serial descriptor 로 추출(kotlinx-serialization-json 이 클래스패스에 있을 때): `@SerialName` 이름, `@Transient`·백킹 필드 없는 프로퍼티 제외, 기본값이 있는 프로퍼티는 optional(`@EncodeDefault` 는 응답에서 필수), 값 하나인 직렬화기는 그 값 타입, sealed 판별자 `type`(`@JsonClassDiscriminator`)과 OpenAPI `oneOf`. 설정 `extraction.kotlinxSerialization`(yml `kardx.kotlinx-serialization`, 기본값 `true`)
- OpenAPI 스키마 이름 출처(`openapi-schemas.json`)에 직접 정한 이름(`openApi { responseSchema }`, `openApi.errorSchema`, `openApi.schemaNameResolver`)도 본문 타입을 남긴다 (이름은 바꾸지 않음). Gradle 플러그인이 그 클래스 KDoc 을 스키마 설명으로 쓴다
- 설명 규칙 조합 `DescriptionResolver`: 기본 단계 `DESCRIPTION_ANNOTATION`(`@Description`), `CONFIGURED_ANNOTATIONS`(`descriptions.annotations`), `SOURCE_DOCS`(KDoc/Javadoc)와 이를 차례로 보는 `DEFAULT`, 조합 함수 `DescriptionResolver.firstOf(...)` 와 `orElse`. 직접 만든 규칙을 기본 단계 앞이나 뒤에 끼워 `@Description`·어노테이션·KDoc 을 잃지 않는다
- 사용자 스니펫 종류 `SnippetFile.custom(fileName, title, includedByDefault)` 와 위치 정하기 `document { register(kind, after = ... / before = ...) }`, 지금 순서 `document.order`. 등록한 종류는 정한 제목과 자리로 생성 문서에 들어가고, 등록하지 않은 스니펫은 전처럼 파일 이름 순으로 맨 뒤에 붙는다
- `@ExperimentalRestDocsApi`(`RequiresOptIn`, 경고): 1.0 호환 보장 밖의 API 표시. KDoc/Javadoc 설명(`descriptions.sourceDocs`, `DescriptionResolver.SOURCE_DOCS`, `FromParamTag`, 이름만 적는 파라미터 선언), `StompTestClient`, `WebSocketHandlerTester`, `ClientDocumentation` 에 붙는다. `@OptIn(ExperimentalRestDocsApi::class)` 나 `optIn.add("io.github.kardx.ExperimentalRestDocsApi")` 로 켠다
- 클라이언트 어댑터 SPI `io.github.kardx.client.ClientDocumentation`(`@RestDocsExtensionApi`, 실험): 요청 전에 `configuration(provider)`, 응답 뒤에 `document(exchange, requestConverter, responseConverter, ...)` 를 불러 다른 HTTP 테스트 클라이언트도 같은 문서화를 하게 한다. RestTestClient 어댑터가 이것만으로 만들어져 있다
- `MaskTarget.location`(`MaskLocation.HEADER`·`COOKIE`·`PARAMETER`)과 `cookieName`: 마스커가 헤더·쿠키·쿼리·폼 파라미터를 가른다
- `PropertyContext.declaredName`(이름 규칙 적용 전 소스 이름)과 `declaringClass`(선언한 클래스)

#### `kardx-annotations`

- 운영 코드에 `compileOnly` 로 넣는 `@Description`, `@Masked`, `@EnumCode`, `@AsciiDescription`

#### `kardx-kotest`, `kardx-kotest5`

- Kotest 스펙 문서화 확장 `RestDocsExtension`: 리프 테스트마다 스니펫, 스펙마다 AsciiDoc 문서. 이름 규칙 `TestNames`, Kotest 용 `EnumDocumentationExtension`
- Kotest Spring 확장이 있으면 그 컨텍스트의 yml 과 Jackson 매퍼를 읽는다
- 스펙과 프로젝트 설정 양쪽에 등록하면 스펙에 등록한 확장이 문서화한다 (스펙 하나만 `sectionTitle`·`documentGenerator` 등을 바꿀 수 있다)
- Kotest 6.x 용(`-kotest`)과 5.x 용(`-kotest5`)이 같은 패키지·공개 API 로 나뉘고, 같은 Gradle capability 를 내보내 함께 들어오면 충돌로 알린다

#### `kardx-boot`

- 앱이 뜰 때 문서 주소(REST Docs, Swagger UI, OpenAPI, OpenAPI 뷰어, AsyncAPI)를 INFO 로그로 남긴다. `kardx.startup-log.*` 로 바꾸고, Gradle 플러그인이 남긴 실제 문서 위치를 기본값으로 읽는다. WebFlux `base-path` 반영
- 시작 로그 문구의 언어: `kardx.language`, 없으면 Gradle 플러그인이 남긴 값, 그것도 없으면 영어. 지원하지 않는 값이면 앱을 멈추지 않고 경고 후 영어

#### `kardx-bom`

- 위 아티팩트의 버전 맞춤 (`kardx-tooling` 포함. Gradle·Maven 플러그인은 의존성이 아니라 넣지 않는다)

#### `kardx-tooling` (새 아티팩트)

- 테스트 다음 단계를 Gradle API 없이 도는 순수 Java 17 모듈로 뺐다: OpenAPI 후처리, 기준 스펙 비교, 문서화 커버리지, OpenAPI 뷰어, IntelliJ HTTP Client, Postman 다듬기, KDoc·Javadoc 스캐너, 내보내기·모으기 도우미. Gradle 플러그인, Maven 플러그인, CLI 가 같은 코드를 돌리므로 결과 파일이 같다
- 명령줄 도구 `io.github.kardx.tooling.cli.ApiDocsCli`: 명령 `api-docs`(설정에서 켠 것 전부) `openapi` `viewer` `coverage` `http` `postman` `check` `baseline` `source-docs`, 옵션 `--config` `--base-dir` `--snippets` `--output` `--work` `--language` `--title` `--version` `--server` `--format` `--baseline` `--fail-on` `--source` `--method-docs` `--verbose`. 명령줄 옵션이 설정 파일을 덮고, 종료 코드는 0 성공·1 실패·2 잘못된 사용 ([Maven 과 명령줄](docs/ko/maven.md))
- JSON 설정 파일(CLI `--config`, Maven `<configFile>` 공용): Gradle `kardx { }` 의 `openApiGlobalHeaders`·`openApiCommonResponses`·`openApiSecurity`·`apiVersioning`·`openApiInfo`·`openApiViewer`·`coverage`·`httpClient`·`postmanCollection`·`openApiCheck` 와 하나씩 대응한다. 모르는 키는 키 경로와 함께 실패한다
- 실행용 jar `kardx-tooling-0.1-all.jar`(분류자 `all`, epages OpenAPI·Postman 생성기 0.20.2 포함): 어떤 빌드 도구에서든 `java -jar ... api-docs --config restdocs-tooling.json`. 플러그인 없이 `exec-maven-plugin` 으로도 부를 수 있다(이때는 epages 생성기를 플러그인 의존성으로 넣는다)

#### Maven 플러그인 `io.github.kardx-project:kardx-maven-plugin` (새 아티팩트, 목표 접두사 `kardx`)

- `api-docs`(기본 단계 `prepare-package`): 테스트 클래스패스로 core 의 `DocumentationIndexCli` 를 따로 띄운 JVM 에서 돌려 `index.adoc` 을 만들고, asciidoctor-maven-plugin 입력 `target/kardx/asciidoc-sources` 를 모으고, 테스트가 `*.asyncapi.json` 을 남겼으면 `AsyncApiCli` 를 돌린 뒤 OpenAPI·뷰어·커버리지·`.http`·Postman 을 `target/api-docs/` 에 만든다
- `check`(기본 단계 `verify`): 기준 스펙 `docs/openapi/openapi3.yaml` 과 비교, 보고서 `target/kardx/openapi-changes.txt`. `baseline`(단계 없음): 만든 스펙을 기준으로 복사. `source-docs`(기본 단계 `process-classes`, 실험 기능): KDoc·Javadoc 색인
- 설정 우선순위는 POM 파라미터 > `configFile` > 기본값이고, OpenAPI 제목·버전 기본값은 `${project.name}`·`${project.version}`. `-Drestdocs.skip` 으로 건너뛴다
- Boot 버전과 상관없이 epages 생성기 0.20.2 를 담는다(`resource.json` 만 읽음). 테스트 쪽은 그대로 Boot 3 이면 0.19.x, Boot 4 면 0.20.x
- Gradle 에서만 되는 것: WireMock stub jar, `apiDocsExport`·`apiDocsUpload`, 여러 프로젝트 모으기, 실행할 때 자동 생성, 의존성 자동 추가, 앱에 문서 넣기(maven-resources-plugin 으로 직접), 시작 로그용 문서 위치 파일, 테스트 JVM 에 언어 넘기기, 최신 여부 확인·캐시

#### Gradle 플러그인 `io.github.kardx-project`

- 문서 파이프라인 구성: 테스트 경로 전달, `apiDocsIndex`, Asciidoctor 입력·확장, epages `openapi3` 기본값과 후처리, `apiDocsAsyncApi`, `apiDocs` 모음과 위치 출력, 앱 리소스·실행 jar 포함
- 라이브러리·테스트 클라이언트 의존성 자동 추가(MVC·WebFlux·Kotest 판정), WebFlux 앱에서 서블릿 스타터 제외
- IntelliJ 실행·`bootRun` 전 문서 자동 생성(`generateOnRun`), 민감 헤더를 가리지 않은 문서의 운영 노출 경고
- 선택 기능(기본 꺼짐): OpenAPI 변경 비교 `apiDocsCheck`·`apiDocsBaseline`, Redoc·Scalar 정적 뷰어 `apiDocsViewer`, Postman Collection, 여러 프로젝트 모으기 `apiDocsAggregate`, WireMock stub jar `apiDocsStubsJar`, 공통 오류 응답 `openApiCommonResponses`(OpenAPI 와 index 표)
- 테스트 다음 단계의 실제 일은 `kardx-tooling` 에 있고 플러그인은 이것을 부른다. 플러그인 사용자에게는 동작·결과 파일 변화가 없다. 내부 패키지 `io.github.kardx.gradle.internal.*`(openapi, spec, check, aggregate, coverage, httpclient, sourcedocs, export, i18n, io, pages)가 `io.github.kardx.tooling.internal.*` 로 옮겨 갔다(내부용이라 공개 API 가 아님)
- 스펙 내보내기(직접 실행): 디렉터리로 복사 `apiDocsExport`, HTTP 업로드 `apiDocsUpload`, Maven 배포물에 스펙 붙이기(`export.publishSpecs`)
- `apiDocsCheck` 는 기준 파일이 없으면 실패한다(`failOn = "none"` 제외, `openApiCheck.requireBaseline = false` 로 끔)
- 태스크 이름은 모두 `apiDocs` 로 시작하고, 중간 단계 태스크는 `./gradlew tasks` 에 나오지 않는다
- Gradle 7.6 미만이면 적용할 때 바로 실패하고, configuration cache 와 호환된다
- `kardx { language = "ko" }`: 테스트 JVM·`apiDocsIndex`·`apiDocsAsyncApi` 와 앱 시작 로그에 같은 언어를 넘기고, 플러그인 문구(공통 오류 응답 표, OpenAPI 설명의 제약·쿠키 문구, OpenAPI 뷰어 오프라인 안내, 집계 페이지)에도 쓴다
- `errorResponse(...)` 로 선언한 응답을 OpenAPI operation 에 더한다. 테스트가 실제로 만든 상태 > 선언 > `openApiCommonResponses` 순으로 이긴다. 선언한 필드는 `components.schemas` 의 스키마가 되고(같은 스키마가 있으면 재사용, 없으면 `ConflictResponse` 처럼 상태 이름), 같은 상태를 다른 설명으로 선언하면 경고한다
- `asyncApi { bearerAuth(); basicAuth(); apiKeyHeader("X-API-KEY") }` 와 `securitySchemes` 맵(쿼리·쿠키 API 키는 `"apiKey:query:<name>"` 처럼 직접): `apiDocsAsyncApi` 에 `--security` 로 넘긴다
- `kardx.asciidoctorJvmArgs`(기본값 `["-XX:TieredStopAtLevel=1"]`, 빈 목록이면 끔): asciidoctor 작업 JVM 인자. Boot 4 샘플에서 asciidoctor 약 7.4 → 6.9초
- 문서화 커버리지 `apiDocsCoverage`(epages 플러그인이 있을 때, `apiDocs` 의 일부): 테스트가 남긴 엔드포인트 목록과 문서화한 operation 을 비교해 `build/kardx/coverage/` 에 `coverage.json`·`coverage.md` 를 쓰고(`apiDocs` 가 `build/api-docs/coverage/` 로 복사) 태스크가 실제로 돌 때 한 줄 요약을 남긴다. 경로 변수 이름은 무시하고, `openApi { excluded = true }` 로 뺀 operation 도 실제 요청 경로로 문서화한 것으로 센다. `/error`·`/actuator/**`·springdoc 경로(`/v3/api-docs/**`, `/v3/api-docs.yaml`, `/swagger-ui/**`, `/swagger-ui.html`)·문서 경로는 언제나 빼고 `exclude(...)` 는 여기에 더한다. `coverage { enabled; failOnUndocumented; exclude(...) }`. 목록이 없으면 `no endpoint inventory` 로 통과
- sealed·다형 타입을 OpenAPI `oneOf` 로: 하위 타입마다 컴포넌트 스키마(이름이 겹치면 `<기반 타입><하위 타입>`)를 만들고 `discriminator`·`mapping` 을 붙인다. 공통 응답 래퍼 안(`data.method`, `data.items[].method`, `data`)처럼 중첩된 경로도 `$ref`·배열을 따라 내려가 바꾸고, 바꾼 자리가 가리키던 평탄한 스키마는 더 쓰는 곳이 없으면 뺀다
- `openApi { summary / description }` 을 조각을 합친 operation 에 넣는다. 같은 operation 의 테스트가 서로 다른 값을 정하면 스니펫 디렉터리 이름순으로 처음 값을 쓰고 `OpenAPI summary/description:` 경고를 남긴다. 버전별 스펙에도 같은 값이 들어가고, HTTP Client 내보내기(`api.http`)의 `###` 제목도 후처리한 스펙의 summary 를 따른다
- `openApiSecurity { oauth2 { clientCredentials(tokenUrl); authorizationCode(authorizationUrl, tokenUrl); password(tokenUrl); implicit(authorizationUrl); scope(name, description) }; openIdConnect(url) }`(위치 인자): 보안 스키마 `oauth2`·`openIdConnect`. operation 이 쓰는데 `scope()` 로 선언하지 않은 scope 는 빈 설명으로 넣는다. 설정하지 않은 스키마를 쓰는 operation 은 경고하고 그 보안 요구사항을 뺀다
- Kotlin 기본값으로 요청 본문 스키마 프로퍼티의 `default` 를 채운다
- `apiVersioning { header(...) | queryParameter(...) | mediaTypeParameter(...) | pathSegment(); splitSpecs }`: operation 의 `x-api-version` 과 버전 헤더·쿼리 파라미터. `mediaTypeParameter`·`pathSegment` 는 `x-api-version` 만 붙인다. 버전은 마디별 숫자로 비교한다. 같은 경로·메서드의 여러 버전은 `splitSpecs = true` 면 `openapi3.yaml` 옆에 `openapi3-v<version>.yaml` 을 더 만들고, 아니면 가장 높은 버전을 남기고 경고한다. epages 가 같은 경로·메서드를 합치므로 본문 스키마는 모든 버전의 합이다
- IntelliJ HTTP Client 내보내기 `apiDocsHttpClient`, `httpClient { enabled; fileName }`(기본 꺼짐, epages 플러그인 필요): operation 마다 대표 요청 하나(성공 응답 테스트 먼저)를 `### <제목>`, `# @name <operationId>`, `{{baseUrl}}`·헤더(`Host`·`Content-Length` 제외)·본문으로 담은 `api.http` 와 `http-client.env.json` 을 `build/kardx/http-client/` 에 쓰고 `apiDocs` 가 `build/api-docs/http-client/` 로 복사한다. Bearer `Authorization` 헤더는 `{{bearerToken}}` 이 되고 환경 파일에 빈 `bearerToken` 이 생긴다
- `apiDocsCheck` 가 더 판정한다: 요청 제약 강화(`minLength`·`minimum`·`minItems` 증가, `maxLength`·`maximum`·`maxItems` 감소, `pattern` 추가·변경), operation 보안 요구사항 추가·스키마 변경, 응답 헤더 삭제, `oneOf` 변형 삭제(요청·응답)는 깨는 변경으로, 응답 `oneOf` 변형 추가와 `default` 변경은 경고로. `anyOf`·`allOf`·`additionalProperties`·scope 변경은 판정하지 않는다
- `apiDocsSourceDocs`(`test` 전, 캐시 가능): 프로젝트와 `project(...)` 의존 프로젝트의 Kotlin·Java `main` 소스에서 KDoc·Javadoc(프로퍼티 KDoc > `@property` > `@param`, Java 필드 > getter > record `@param`, enum 상수, 컨트롤러 메서드)을 컴파일러 없이 읽어 `build/kardx/source-docs/source-docs.json` 에 쓰고 테스트 JVM 에 경로를 넘긴다. 링크·`{@code}`·HTML 을 정리해 한 줄로 만들고, 읽지 못하는 선언은 건너뛸 뿐 빌드를 실패시키지 않는다. 소스가 바뀌면 전체를 다시 읽는다(Boot 4 샘플 약 0.2초). 오버로드는 주석이 같으면 `#name`, 다르면 소스 인자 수로 `#name/<arity>`. 최상위 선언·지역/익명 클래스·Java 생성자 `@param`·`///` 주석은 모으지 않고, 프로젝트 의존성은 java 플러그인의 main 소스 변형으로 찾으므로 Android·KMP·included build 는 빠진다. `sourceDocs { enabled; includeProjectDependencies }`(모두 기본 `true`, `enabled = false` 면 태스크는 남고 `test` 만 의존하지 않는다). OpenAPI operation 의 summary(첫 문장, 끝 마침표 제외)·description(전체)은 `openApi { }` 값 > 메서드 주석 > 기존 기본값 순
- `sourceDocs { methodDocs = true }`(기본값 `false`): 컨트롤러 메서드 KDoc·Javadoc 을 OpenAPI operation 의 summary·description 으로 쓰는 것은 켜야 동작한다. 핸들러 메서드의 구현 메모가 공개 문서의 summary 로 새지 않게 하려는 것이다. 켜면 메서드 주석 전체가 블록에 `description` 이 없는 테스트의 `api-summary`(HTML 문서) 설명도 된다. 끄면 `apiDocsSourceDocs` 가 `source-docs.json` 의 메서드 항목에 `@param` 글(`params`)만 쓰고, 값은 태스크 입력이라 바꾸면 다시 돈다. 필드·타입·enum 상수 주석은 이 값과 상관없이 쓴다
- `openApiInfo { }`: 스펙 최상위 메타데이터. `description`·`descriptionFile`(Markdown), `termsOfService`, `contact`, `license`, 문서 `externalDocs`, `tag("Orders") { description; externalDocs { } }` 로 태그 순서·설명(테스트 클래스 `@AsciiDescription` 보다 앞섬), `tagGroup(...)` 로 `x-tagGroups`(그룹 없는 태그는 마지막 `Other`, 한국어 `기타`). 버전별 스펙에도 들어가고 `apiDocsAggregate` 병합 스펙에는 들어가지 않는다. `description` 과 `descriptionFile` 을 함께 정하거나 파일이 없으면 실패, `license.name`·`externalDocs.url` 이 없거나 쓰지 않는 태그면 경고 후 생략
- `html { enabled }`(기본값은 Gradle 속성 `kardx.html`, 없으면 `true`): 끄면 `apiDocs` 가 `asciidoctor`·`apiDocsIndex` 를 돌리지 않고 지난 HTML 을 지운다. OpenAPI·뷰어·Postman·`.http`·커버리지·앱 리소스 복사는 그대로이고 위치 출력에 `REST Docs HTML    skipped` 가 나온다. 로컬에서만 끄려면 `~/.gradle/gradle.properties` 에 `kardx.html=false` 또는 `-Pkardx.html=false`, CI 에서만 켜려면 `enabled = providers.environmentVariable("CI").isPresent`
- 클래스 KDoc·Javadoc 을 OpenAPI 컴포넌트 스키마(`components.schemas`)의 `description` 으로 넣는다. 스키마 이름을 만든 클래스(바깥 타입, 래퍼를 뗀 이름이면 타입 인자, 중첩 클래스 포함)의 설명이고, 직접 정한 이름도 본문 타입을 알면 넣는다. 이미 설명이 있거나 여러 클래스가 같이 쓰는 이름은 건너뛴다. `sourceDocs { enabled = false }` 면 꺼진다

#### 문서와 샘플

- 사용 설명서(`docs/`), 아키텍처 문서(`docs/architecture.md`), 기존 Spring REST Docs 에서 옮기기 안내, Spring Auto REST Docs(ScaCap)에서 옮기기 안내
- `@WebMvcTest` 슬라이스 테스트 안내(`docs/getting-started.md`)와 영문·한국어 문서 짝 검사 `scripts/check-docs-pairs.py`. 루트 태스크 `checkDocsPairs`(verification 그룹, `check` 에 연결)가 이 스크립트를 실행하므로 `./gradlew build` 도 짝이 어긋나면 실패한다
- 패키지 순환 검사 `scripts/check-package-cycles.py` 를 루트 태스크 `checkPackageCycles`(verification 그룹, `check` 에 연결)가 core·kotest·kotest5(각각 kotest-common 포함)·Gradle 플러그인 소스에 돌리므로 `./gradlew build` 도 순환이 생기면 실패한다. 소스가 그대로면 다시 돌지 않는다. 두 루트 검사 태스크가 부르는 파이썬은 `-Prestdocs.python=py` 처럼 바꿀 수 있다 (Windows)
- Gradle 호환 테스트가 스펙 내보내기·업로드까지 돌린다. 7.6.4·8.14.6·9.0.0·9.8.2 에서 확인
- CI 적용 안내 `docs/ci.md`: GitHub Actions PR 검사(`apiDocsCheck`, `coverage { failOnUndocumented }`), 보고서 PR 댓글, 기준 갱신 흐름, GitHub Pages·`apiDocsUpload` 배포, 여러 프로젝트, GitLab CI 예
- 샘플 앱 3개(Boot 3, Boot 4, Boot 4 WebFlux, 패키지 `com.example.restdocs`)와 산출물 검증 스크립트(`scripts/`)
- Maven 샘플 `samples/maven-boot-4`(Boot 4.1, Java 앱 + Kotlin 테스트, Maven Wrapper): Maven 플러그인, asciidoctor-maven-plugin 3.2.0, maven-resources-plugin 으로 `./mvnw verify` 가 테스트·index·OpenAPI·뷰어·커버리지·HTML·기준 스펙 검사·jar 안의 문서까지 만든다. 저장소에서 `./gradlew publishToMavenLocal` 을 먼저 실행한다
- Maven·명령줄 안내 `docs/maven.md`(한국어 짝): 기능 비교, 설정, 목표·파라미터, JSON 설정, 스펙 검사, KDoc 설명, `exec-maven-plugin`·`java -jar`, Gradle 에서만 되는 것, 트러블슈팅. 패키지 순환 검사와 tooling 의 javap 기반 `apiDump`/`apiCheck` 가 tooling·maven-plugin 도 본다
- 문서 짝 검사가 `CONTRIBUTING.md`·`CONTRIBUTING.ko.md` 도 본다(지금 18쌍, `checkDocsPairs` 입력에도 추가)
- 재현 가능한 스니펫 안내(`docs/build.md`): `Date` 헤더, Spring Data `PageImpl` 속성 순서. Boot 3 샘플은 `src/main/resources/config/application.yml` 에서 Jackson 2 의 속성 순서를 Jackson 3(Boot 4) 기본값과 같게 고정한다(`sort-properties-alphabetically`, `sort-creator-properties-by-declaration-order`). `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` 는 JSON 모양과 필드 경로(`pageable.pageNumber` → `page.number`)가 바뀌어 쓰지 않았다. 세 샘플 모두 `test --rerun` 을 두 번 돌려도 스니펫이 같고 두 번째 `asciidoctor` 는 UP-TO-DATE
- 산출물 검증 스크립트가 쿼리 자격 증명 마스킹(`access_token=****`, WireMock `matches ".*"`)을 확인한다. `update-docs-toc.py` 는 코드 블록 안의 제목을 목차에 넣지 않고, `verify-live-app.py` 는 앱이 뜰 때까지 1초 간격으로 60번 다시 시도한다
- 문제 해결 문서(`docs/troubleshooting.md`, 한국어 짝)에 오류 코드 표(코드마다 `<a id="kdx-xxx"></a>` 앵커, 문제, 해결). `scripts/update-error-codes.py` 가 `RestDocsErrorCode` 에서 표를 만들고(해결 칸은 손으로 쓰고 유지), 루트 태스크 `checkErrorCodes`(`check` 에 연결)가 표가 어긋나거나 해결 칸이 비면 실패한다
- 시작하기 안내가 기본 클래스 대신 테스트 메서드 파라미터 주입으로 시작한다(기본 클래스는 선택). Spring HATEOAS·kotlinx.serialization 추출 규칙, `openApiInfo`, `html { enabled }`, 클래스 KDoc → 스키마 설명 안내

### Changed

개발 중 버전과 달라진 동작입니다.

- 문서: 길던 `docs/build.md` 를 넷으로 나눴다 (영문·한국어 모두). `build.md`(명령, Gradle 플러그인 옵션과 태스크 표 하나, 의존성, 실행 시 생성, 시작 로그, 운영 노출, 생성 위치), `openapi.md`(Swagger UI, `openApiInfo`, OAuth2·OpenID Connect 스키마, API 버전), `checks.md`(`apiDocsCheck`, `apiDocsCoverage`), `outputs.md`(뷰어, Postman, `.http`, WireMock, 내보내기·업로드, 여러 프로젝트 모으기). 겹치던 "명령별 역할" 목록과 앱 주소 표는 태스크 표와 문서 목록의 "브라우저에서 보기"로 합쳤다. 옛 `build.md#…` 링크는 새 페이지로 고쳤다
- KDoc/Javadoc → 설명은 실험 기능이고 기본으로 꺼져 있으며, 항목별 스위치가 생겼다 (예전에는 Gradle 플러그인을 쓰면 타입·프로퍼티·enum 상수 주석과 `@param` 이 기본으로 켜져 있었다). 스캐너가 컴파일러가 아니라 놓치는 선언이 있기 때문이다. `kardx { sourceDocs { enabled = true } }` 로 켜고, 켠 뒤에는 `fieldDocs`(프로퍼티·필드·getter·record 주석 → 필드와 `parametersOf<T>()`·`queryParameters<T>()` 파라미터 설명), `enumDocs`(enum 상수), `schemaDescriptions`(클래스 주석 → OpenAPI 컴포넌트 스키마 설명), `paramDocs`(핸들러 `@param` → 이름만 적거나 `FromParamTag` 로 적은 항목)가 기본값 `true`, `methodDocs`(핸들러 메서드 summary·description)가 기본값 `false`, `includeProjectDependencies` 가 기본값 `true` 다 (Kotlin·Groovy DSL). 끈 항목은 `source-docs.json` 에서 빠지고, 모든 스위치가 태스크 입력이라 바꾸면 스캔과 테스트가 다시 돈다. 꺼져 있으면 `test` 가 `apiDocsSourceDocs` 에 의존하지 않고 시스템 속성도 받지 않으며, `apiDocsSourceDocs` 를 직접 실행해도 건너뛰고(`SKIPPED`), OpenAPI 스키마에 클래스 설명도 넣지 않는다. core 의 yml `kardx.source-docs.enabled`(`RestDocsSettings.sourceDocsEnabled`, 기본값 `true`)는 테스트 쪽 차단 스위치로 남는다(`false` 면 넘겨받은 색인을 무시). 이름만 적은 항목의 설명을 찾지 못한 오류(`KDX-101`)는 `@param <이름>` 을 달고 Gradle 플러그인의 실험 기능 source docs(`kardx { sourceDocs { enabled = true } }`)를 켜라고 안내한다. 샘플은 Boot 3·4 가 `sourceDocs { enabled = true; methodDocs = true }`, WebFlux 가 `sourceDocs { enabled = true }` 를 쓴다
- `relaxed = true` 가 모든 항목(경로·쿼리·폼 파라미터, 파트, 쿠키, 링크 포함)을 느슨하게 한다. 예전에는 필드·파트·이벤트에만 적용됐다. 본문만 느슨하게 하려면 `relaxed(RelaxedSection.REQUEST_FIELDS, RelaxedSection.RESPONSE_FIELDS)`
- DSL 수식어(`means`, `isOptional`, `optional()`, `constraint`, `example`, `withDefaultValue`, `enumOf`, `ofType`, `deprecated`, `masked` 등)가 원래 항목을 바꾸지 않고 복사본을 돌려준다. `val` 상수로 공유한 항목이 다른 테스트로 번지지 않는다. 결과를 쓰지 않은 수식어(`AUTH isOptional true` 를 따로 한 줄)는 아무 효과가 없다. `descriptor` 를 직접 바꾸면 여전히 공유 객체가 바뀐다
- `AsyncApiInfo` 생성자 끝에 `securitySchemes`(기본값 빈 목록)가 붙었다. 이름 있는 인자 호출은 그대로 컴파일되고, 이전 개발 빌드로 컴파일한 코드는 다시 컴파일해야 한다. 같은 이름의 인증 방식이 두 번 있으면 `IllegalArgumentException`
- 생성한 OpenAPI·AsyncAPI 뷰어 페이지에 소스 주석이 들어가지 않는다
- `StompTestClient.sendAndReceive` 가 프레임이 올 때까지 다시 보내는 간격을 200ms 에서 두 배씩 1초까지 늘린다 (예전에는 100ms 마다). Spring 의 simple broker 는 SUBSCRIBE 에 RECEIPT 를 보내지 않아 구독 등록을 확인할 수 없으므로 다시 보내기는 그대로이고, 페이로드가 두 번 이상 갈 수 있다는 점을 KDoc 과 문서에 적었다. 한 번만 보내려면 `subscribe` → `send` → `await` 를 쓴다. 생성자의 연결 대기 시간이 지난 뒤에 맺어진 STOMP 세션은 끊는다
- 엔드포인트 목록(`.kardx/endpoints/`)을 쓰지 못하면 문서화 테스트를 실패시키지 않고 경고를 남기며, 다음 문서화 요청에서 다시 시도한다
- `apiDocsCheck`: 보고서(`openapi-changes.txt`)가 차이가 없어도 `The OpenAPI spec matches the baseline (baseline <기준> ↔ generated <생성>)` 머리와 `Breaking changes: 0`, `All structural differences: 0` 을 쓴다 (예전에는 차이가 없어도 `differs` 로 시작했다). enum 이 없던 요청 파라미터·필드에 `enum` 이 생기면 깨는 변경(`Request enum added: <위치> [값…]`)이고, 경로 변수 이름만 바뀐 경로(`/a/{id}` → `/a/{userId}`)는 엔드포인트 삭제가 아니라 경고(`Path variables renamed: …`)로 알리고 operation 을 계속 비교한다
- `apiDocs` 위치 출력에서 epages 플러그인이 없을 때의 OpenAPI 줄이 켜는 방법을 알려 준다: `OpenAPI (Swagger) off (apply id("com.epages.restdocs-api-spec") version "0.20.2" to enable OpenAPI, coverage and the viewer)` (Boot 3 은 `0.19.4`)
- `apiDocsStubsJar` 는 `project.group` 이 없으면 Stub Runner 가 찾을 수 없는 jar(`META-INF//<name>/…`)를 만드는 대신 `apiDocsStubsJar: project.group is not set, …` 으로 실패한다. `group`·`version` 은 태스크가 돌 때 읽으므로 빌드 스크립트 뒤쪽에서 정해도 된다
- `apiDocsAggregate` 는 두 프로젝트가 같은 디렉터리로 가면(루트 프로젝트와 `:root`) 한쪽 문서를 덮어쓰는 대신 `apiDocsAggregate: projects : and :root both go to the directory root/ …` 로 실패한다
- 플러그인 태스크를 `validatePlugins { enableStricterValidation = true }` 로 검사하고, 캐시 표시가 없던 태스크(`apiDocsAggregate`, `apiDocsCheck`)에 `@DisableCachingByDefault` 를 붙였다
- 출력 상대 경로 설정에 Windows 드라이브 경로(`C:/x`, `C:\x`)와 `\\` 로 시작하는 경로를 거부한다
- 문서화한 응답에서 `Date` 헤더를 기본으로 뺀다. 스니펫과 WireMock 매핑이 실행마다 같아지고, 바뀐 것이 없으면 `asciidoctor` 가 다시 돌지 않는다. `responseHeaders(...)` 로 문서화한 헤더는 남는다. 그대로 두려면 `removedResponseHeaders = emptySet()` 또는 `kardx.removed-response-headers: none`
- `maskSensitiveHeaders()`·`mask-sensitive-headers: true` 가 헤더뿐 아니라 `SENSITIVE_QUERY_PARAMETERS` 의 쿼리 파라미터도 가린다. 세 샘플의 `?access_token=demo-token` 이 스니펫에서 `?access_token=****` 가 된다
- 성능: 요청당 `andDocument` 비용이 약 40% 줄었다(OpenAPI 켬, 약 3.0 → 1.8ms). epages JSON 매퍼를 공유하고(JDK 26 이상에서는 공유하지 않음), 라이브러리가 만드는 스니펫 템플릿을 캐시하고, 연산마다 따로 쓰는 부속 파일(`openapi-extras.json`, 스키마, WireMock 매핑)은 원자적 쓰기 대신 바로 쓴다. 여러 테스트가 함께 쓰는 파일은 원자적 쓰기 그대로다
- 바뀐 것이 없는 두 번째 `apiDocs` 에서 도는 태스크가 4개에서 2개로 줄었다: `postman`(입출력 선언)과 `apiDocsViewer` 가 UP-TO-DATE 이고, `apiDocsAppDocs` 는 계속 돌되(지운 문서를 다시 채움) 스펙 파일 하나만 읽는다.
- `openapi3` 는 epages 의 `servers` 입력을 Gradle 이 직렬화하지 못해 상태를 추적하지 않고(`doNotTrackState`, 항상 실행) 스펙 파일을 출력으로 선언한다. Gradle 8 이상에서 `build/api-spec/openapi3.*` 를 `dependsOn("openapi3")` 없이 읽는 사용자 태스크는 암묵적 의존성 검사에 걸린다
- `kardx.bootJar.path` 는 앱 리소스 안의 하위 디렉터리(`static/docs`, `public/api-docs` 등)여야 한다. 비었거나 리소스 밖으로 나가는 값, 리소스 루트(`static`, `public`, `resources`, `META-INF/resources`)는 설정 단계에서 실패한다
- 플러그인이 비우는 디렉터리(`snippetsDirectory`, `asciidocDirectory`, `outputDirectory`, `aggregate.outputDirectory`)를 먼저 검사한다. 프로젝트·루트·빌드 디렉터리와 그 상위, `src/`·asciidoc 소스 안이나 상위는 거부하고, `build/` 밖은 비었거나 표시 파일 `.kardx-output` 이 있을 때만 쓴다(쓴 뒤 표시 파일을 남김). `build/` 안의 기본 위치는 그대로다. GitHub Pages 용으로 `docs/` 에 모으는 `apiDocsAggregate` 도 처음에 빈 디렉터리이거나 표시 파일이 있어야 한다
- 추출 목록 편집(`except`, `optionalAt`, `optionalUnder`)의 없는 이름 오류에 `KDX-108`, 추출 결과가 빈 경고(`found nothing to document`)에 `KDX-107` 코드가 붙는다. 다른 메시지도 `kardx: …` 대신 `kardx[KDX-xxx]: …` 로 시작한다
- Spring Data 페이지 표의 `PagedModel<T>` 는 `org.springframework.data.web.PagedModel` 이다. Spring HATEOAS `PagedModel` 은 HAL 모양으로 따로 추출한다

### Fixed

- Markdown 형식의 메시지(`message`)·SSE(`sse-events`) 스니펫 표 칸이 비던 문제 (AsciiDoc 전용 `tableCellContent` 를 쓰고 있었다)
- `apiDocsUpload`: 업로드 주소에 든 비밀값(`user:password@` 사용자 정보, 쿼리 파라미터 값)이 로그·재시도 경고·오류 메시지에 그대로 나오던 문제. 이제 `****` 로 가린다
- Gradle 9 이상에서 플러그인이 지원 중단 API(`Configuration.setVisible`)를 불러 경고가 나던 문제
- 선언한 필드로 만든 OpenAPI 3.0 스키마에 `nullable` 이 빠져 같은 모양의 기존 컴포넌트 스키마와 맞지 않던 문제. 이제 `nullable` 을 넣어 기존 스키마(`ErrorResponse` 등)를 재사용한다
- `StompTestClient` 가 Spring Framework 7(Boot 4)에서 동작하지 않던 문제
- `errorResponse(...)` 로 JSON 이 아닌 `contentType`(`text/plain`, `text/html` 등)을 필드 없이 선언하면 OpenAPI 에 공통 JSON 오류 스키마가 붙던 문제. 이제 `type: string` 스키마와 문자열 예시를 쓴다
- AsyncAPI 뷰어 Try it out: CONNECT 를 거절한 STOMP ERROR 프레임의 `message` 헤더가 이스케이프된 채(`Authorization\c Bearer ...`) 로그에 나오던 문제. stompjs 는 STOMP 1.2 로 CONNECTED 를 받은 뒤에만 헤더를 풀기 때문이다. 뷰어가 버전 협상 전이나 1.1 연결의 헤더를 STOMP 1.2 규칙(`\c`·`\n`·`\r`·`\\`)으로 풀어 보여 준다
- Java bean 필드: 같은 타입 파라미터 이름을 다른 타입에 쓰는 제네릭 상위 클래스에서 물려받은 getter 가 상위 클래스의 바인딩으로 풀리지 않던 문제 (`PageResp<T> : Base<List<T>>` 의 `data` 가 이제 `data[]`)
- Java bean 을 상속한 Kotlin 클래스에서 물려받은 getter 프로퍼티가 문서화되지 않던 문제
- 타입 인자 없이 컬렉션을 상속한 클래스(`class UserList : ArrayList<User>()`)의 요소 필드가 문서화되지 않던 문제
- `CharArray`·`char[]` 를 `String` 으로 문서화하지 않던 문제
- 필수 생성자 파라미터가 `java.time` 타입, `UUID`, `Date`, 배열, 구체 JDK 컬렉션이면 요청 필드 기본값(Kotlin 기본값)을 읽지 못하던 문제
- (Kotest 6) `override val extensions` 로 `RestDocsExtension` 을 등록한 스펙보다 프로젝트 설정의 확장이 먼저 문서화하던 문제. 이제 스펙 쪽이 이긴다 (`@ApplyExtension` 은 해당하지 않음)
- (Kotest) 앱의 ObjectMapper 가 같은 스레드에서 이어 도는 Spring 이 아닌 스펙으로 새던 문제, 정리 중 오류가 테스트 자신의 실패를 가리던 문제
- (JUnit) `@BeforeEach` 에서 실패한 `@RepeatedTest` 회차가 실패로 표시되지 않고 문서에 남던 문제
- (boot) 시작 로그의 정적 문서 주소와 Swagger UI 주소에 `spring.mvc.servlet.path` 가 빠지던 문제 (springdoc 2.8.17·3.2.0 기준)
- `apiDocsAggregate`: 프로젝트가 `apiVersioning { splitSpecs = true }` 를 쓰면 버전별 스펙(`openapi3-v1.yaml`)이 원래 스펙과 같은 operation 을 담아 `GET /x: order, order` 충돌로 실패하던 문제. 이제 프로젝트마다 기본 스펙만 합치고 버전별 스펙은 목록 페이지에 링크로 넣는다
- OpenAPI 후처리가 YAML 1.1 규칙으로 `10:30` 같은 문자열 값(예시·enum·기본값)을 60진수 숫자(`630`)로 바꿔 쓰던 문제. 이제 YAML 1.2 숫자 규칙으로 읽는다
- `openapi3 { format }` 을 yaml ↔ json 으로 바꾸면 `build/api-spec` 에 남은 지난 형식 스펙이 먼저 쓰이던 문제(`apiDocsCheck`, 뷰어, 내보내기, 집계). 이제 지난 형식 파일을 지운다
- 빌드 스크립트 본문에서 `layout.buildDirectory` 를 바꾸면 epages 가 기본 빌드 디렉터리의 스니펫 복사본을 읽던 문제
- `coverage { exclude(...) }` 패턴에 경로 변수(`/internal/users/{id}`, `{id:\d+}`)를 쓰면 맞지 않던 문제. 이제 이름·정규식과 상관없이 맞춘다
- `apiDocsSourceDocs` 가 UTF-8 이 아닌 소스 파일을 아무 안내 없이 통째로 건너뛰던 문제. 이제 잘못된 바이트를 U+FFFD 로 바꿔 읽고 `Source docs: N source files are not UTF-8 (first: …)` 경고를 한 번 남긴다
- `asyncApi { enabled = false }` 인데 `apiDocsExport`·`apiDocsUpload` 가 예전 빌드에 남은 AsyncAPI 스펙을 내보내던 문제. 이제 `No AsyncAPI spec: kardx.asyncApi.enabled is false. Enable it, or set <option> = false to leave it out.` 으로 실패한다
- `openapi3` 후처리가 읽는 설정(`openApiGlobalHeaders`, `openApiCommonResponses`, `openApiCommonResponseSchema` 등) 일부가 태스크 입력으로 선언되지 않던 문제
- 문서 짝 검사 `check-docs-pairs.py` 가 ```` ```` ```` 펜스 안의 ```` ``` ```` 줄에서 코드 블록을 일찍 닫던 문제. 이제 같은 문자로 같거나 더 긴 펜스만 닫는다
- `openapi3` 가 출력을 선언하지 않아, 스펙만 바뀌었을 때 `apiDocs`·뷰어가 UP-TO-DATE 로 지난 스펙을 남기던 문제
- `java.nio.file.Path` 필드(자기 자신을 원소로 갖는 Iterable)가 필드 추출에서 `StackOverflowError` 를 내던 문제. `Path`, `File`, `URL`, `InetAddress`, `ByteBuffer`, `Class` 와 그 하위 타입(`Timestamp`, `GregorianCalendar` 등의 `Date`·`Calendar` 하위 클래스)은 Jackson 처럼 문자열로 문서화한다. 끝없이 중첩되는 배열은 `maxExtractionDepth` 에서 멈춘다
- 평가할 때마다 바뀌는 Kotlin 생성자 기본값(랜덤 ID, 현재 시각)과 `@Masked` 필드의 기본값이 문서에 나가던 문제. 이제 기본값을 두 번 만들어 같을 때만 쓴다. 두 번째는 필수 파라미터에 다른 자리 값을 넣으므로, 다른 파라미터에서 계산한 기본값(`pageSize: Int = limit` 이 `default: 0` 으로 나가던 것)도 뺀다. 객체를 클래스마다 두 번 만들므로 `init` 의 부수 효과도 두 번 실행된다. 환경 변수·설정에서 읽은 기본값은 매번 같으므로 구별하지 못하고 그대로 나간다
- `StompTestClient` 가 서버의 STOMP ERROR 프레임·연결 끊김·CONNECT 거절에도 제한 시간까지 기다리던 문제. 이제 바로 `STOMP ERROR from server: <message>: <본문>` 으로 실패한다. `sendAndReceive` 가 제한 시간을 넘기거나 마감 직전에 다시 보내던 문제도 고쳤다
- (JUnit) `@AfterEach` 에서만 실패한 테스트가 문서에 남던 문제
- `maskSensitiveHeaders()` 를 켜도 폼 본문(`username=…&password=…&client_secret=…`)의 자격증명이 스니펫·OpenAPI 예시·`.http` 에 그대로 나가던 문제
- `ResponseCookie.maxAge(...)` 쿠키의 `Expires` 때문에 `http-response`·WireMock 매핑이 실행마다 바뀌던 문제
- `snippets(HeaderDocumentation.responseHeaders(headerWithName("Date")))` 처럼 REST Docs 스니펫으로 문서화한 응답 헤더를 `removedResponseHeaders` 가 먼저 빼 `not found in the response` 로 실패하던 문제
- 엔드포인트 목록 쓰기 실패가 요청마다 경고·스택 트레이스를 남기던 문제. 이제 한 번만 다시 시도하고 스택 트레이스는 첫 실패에만 남긴다
- `apiDocsAppDocs` 가 `bootJar.path` 디렉터리를 동기화(Sync)하며 앱 자체 리소스를 지우던 문제. 이제 문서를 복사하고, 지난번에 플러그인이 복사한 파일(`build/kardx/app-docs-files.txt`)만 지운다. 경로를 바꾸면 예전 경로의 복사본을, `bootJar.enabled = false` 면 다음 `apiDocs` 에서 복사본 전부를 지운다
- `openapi3` 의 지난 스펙 정리가 같은 디렉터리의 다른 파일까지 지울 수 있던 문제. 이제 `<prefix>.<ext>`, 숫자로 시작하는 `-v` 버전 스펙, 목록 `build/kardx/openapi-version-specs.txt` 에 적힌 파일만 지운다
- `apiDocsHttpClient` 가 문서용으로 가린 값(`****`)을 그대로 적어 `.http` 요청이 인증에 실패하던 문제. 이제 쿼리 파라미터·폼 필드·쿠키의 가린 값은 그 이름의 변수(`{{access_token}}`, `{{SESSION}}`)로, 값 전체를 가린 헤더와 `Basic ****` 는 소문자 헤더 이름 변수(`{{x_api_key}}`)로 바꾸고, `http-client.env.json`·`http-client.private.env.json` 에 빈 값으로 선언한다. 실제 값은 쓰지 않는다. 기본 표시 `****` 만 알아본다
- epages `openapi3 { outputDirectory }` 가 `build/resources/main/...` 을 가리키면 `apiDocs`·`bootJar` 가 Gradle 8 의 암묵적 의존성 검사(`':apiDocsIndex' uses this output of task ':openapi3'`)로 실패하던 문제. 이제 그 경우 스펙을 출력으로 선언하지 않고, 앱 리소스 안을 가리키면 `outputDirectory` 를 지우라고 한 번 경고한다 (플러그인이 이미 스펙을 `bootJar.path` 로 복사한다)
- 플러그인이 비우는 디렉터리 설정이 이미 있는 파일을 가리키면 빈 디렉터리로 보고 그 파일을 지우던 문제(`aggregate { outputDirectory = file("docs/index.html") }` 등). 이제 `<설정> points to <경로>, which is a file; it must be a directory` 로 실패한다
- `postmanCollection { enabled }` 를 고정값이 아니라 provider(Gradle 속성, 환경 변수)로 정하면 `apiDocs` 가 `postman` 을 실행하지 않던 문제. 이제 실행 그래프를 만들 때 값을 읽는다
