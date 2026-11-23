[English](../configuration.md) | **한국어**

# 설정

[문서 목록](README.md)

<!-- toc -->
**목차**

- [바꾸는 방법](#바꾸는-방법)
  - [병렬 실행](#병렬-실행)
- [설정 목록](#설정-목록)
- [yml 설정](#yml-설정)
- [문서 언어](#문서-언어)
- [민감 정보 마스킹](#민감-정보-마스킹)
- [실험 API](#실험-api)
<!-- /toc -->

프로젝트 공통 값은 `application.yml` 로, 테스트마다 다른 값은 코드로 정하는 것을 권합니다. 경로는 [Gradle 플러그인](build.md#gradle-플러그인)에서 정합니다.

## 바꾸는 방법

코드에서는 `RestDocsConfiguration` 을 씁니다. yml 은 [yml 설정](#yml-설정)을 참고하세요.

| 함수 | 적용 범위 |
|---|---|
| `configure { ... }` | 기본값을 바꿉니다. 테스트 기본 클래스의 `@BeforeEach` 에서 부릅니다. 어디서 불렀는지에 따라 유지 범위가 다릅니다 (아래 표) |
| `withSettings({ ... }) { ... }` | 두 번째 블록 안에서만, 현재 스레드에서만. 병렬 테스트에서도 안전합니다 |
| `current` | 지금 적용 중인 값 |
| `reset()` | `configure` 로 더한 값을 누가 언제 더했든 모두 지웁니다(`@BeforeAll`·다른 테스트의 것 포함). 지운 값은 테스트가 끝나도 되살아나지 않습니다. yml(속성)에서 읽은 값은 남습니다. `AutoRestDocumentationExtension` 을 쓰면 보통 부를 일이 없습니다 |

`AutoRestDocumentationExtension` 은 테스트가 실행되는 동안(`@BeforeEach` → 테스트 메서드 → `@AfterEach`) 테스트 스레드에서 한 `configure` 를 그 테스트가 더한 블록으로 기억했다가, 테스트가 끝나면 **그 블록만** 뺍니다. 그래서 `@AfterEach fun reset() = RestDocsConfiguration.reset()` 을 쓰지 않아도 설정이 테스트마다 쌓이지 않고, 다른 테스트(병렬로 도는 테스트 포함)의 설정을 지우거나 되살리지 않습니다. Kotest 의 `RestDocsExtension` 도 같습니다. 리프 테스트와 그 `beforeEach`·`afterEach` 에서 한 설정은 테스트가 끝나면 빼고, 스펙 본문·`beforeSpec` 의 설정은 남깁니다([Kotest](kotest.md#junit-확장과-같은-동작)).

| `configure` 를 부른 곳 | 유지 범위 |
|---|---|
| 테스트 메서드, `@BeforeEach`, `@AfterEach` | 그 테스트에만. 테스트가 끝나면(실패해도) 사라집니다 |
| 테스트가 직접 만든 스레드·스레드 풀, 서버·비동기 스레드 | 어느 테스트의 것도 아니므로 계속 유지됩니다 (테스트 스레드에서 부르세요) |
| `@BeforeAll`, `companion object`, 테스트 클래스 밖 | 계속 유지됩니다. 다음 테스트 클래스에도 적용되므로, 지우려면 `@AfterAll` 에서 `reset()` 합니다 |
| 테스트 클래스의 생성자·`init` 블록·프로퍼티 초기화 | 쓰지 마세요. JUnit 은 테스트마다 인스턴스를 새로 만들고 이것은 기억하는 시점보다 먼저 실행되므로 되돌려지지 않고 테스트마다 쌓입니다. `@BeforeEach` 로 옮깁니다 |

클래스 문서(`<class-name>.adoc`)는 마지막 테스트가 실행될 때(`@BeforeEach` 이후, 테스트 본문 전)의 설정으로 만들므로, `@BeforeEach` 에서 정한 `document.commonIncludeFiles` 같은 문서 설정은 테스트가 끝나 되돌아가도 문서에 쓰입니다. REST Docs 의 `RestDocumentationExtension` 을 그대로 쓰는 테스트는 되돌리지 않으므로 `@AfterEach` 에서 `reset()` 합니다.

### 병렬 실행

테스트를 병렬로 돌려도(JUnit `@Execution(CONCURRENT)`·클래스 병렬, Kotest 스펙 병렬) 각 테스트는 자기가 더한 `configure` 블록만 빼므로 서로의 설정을 지우거나 되살리지 않습니다. 보장하지 못하는 것은 다음입니다.

- `configure` 로 만든 값은 JVM 전역 하나입니다. 동시에 도는 동안에는 두 테스트가 더한 블록이 함께 적용된 값을 보므로, 서로 다른 `configure` 값으로 **동시에 문서화**하면 스니펫·OpenAPI·문서에 상대 테스트의 설정이 섞일 수 있습니다.
- 클래스 문서는 그 클래스에서 마지막으로 실행한 테스트가 본 설정으로 만듭니다. 그때 다른 테스트의 블록이 있었으면 그 값도 들어가고, 같은 클래스의 메서드를 병렬로 돌리면 어느 메서드가 마지막인지 정해지지 않습니다.
- yml(`kardx.*`) 값도 JVM 전역 하나라, 값이 다른 컨텍스트의 클래스를 병렬로 돌리면 나중에 읽은 컨텍스트의 값이 쓰입니다.

테스트마다 다른 설정은 `withSettings` 로 주거나(현재 스레드에만 적용), 그런 테스트를 `@Execution(SAME_THREAD)`·`@ResourceLock` 으로 차례로 돌립니다.

```kotlin
// 빈 배열 응답을 문서화하는 테스트 하나에서만
RestDocsConfiguration.withSettings({ extraction.arrayElementFieldsOptional = true }) {
    mockMvc
        .perform(get("/api/products?keyword=none"))
        .andDocument { ... }
}
```

## 설정 목록

설정은 대부분 쓰는 때(문서화하는 `andDocument`, 추출하는 `responseFieldsOf` 등)에 읽으므로 클라이언트를 만든 뒤에 바꿔도 적용됩니다. **(생성 시)** 표시가 있는 설정만 `restDocsMockMvc(...)` 를 부를 때 한 번 읽으므로, 그 전에 정해야 그 MockMvc 에 적용됩니다. yml 의 `kardx.*` 은 `restDocsMockMvc`·`restDocsWebTestClient` 가 컨텍스트에서 읽어 넣습니다.

설정은 영향을 주는 곳별로 묶여 있습니다. `configure { }` 안에서 묶음을 블록으로 열거나 경로로 적습니다. `language` 만 맨 위에 있습니다.

```kotlin
RestDocsConfiguration.configure {
    language = DocsLanguage.KO
    extraction { maxDepth = 5 }
    masking { maskSensitive() }
    openApi.enabled = false
    document { commonIncludeFiles = listOf("_landing.adoc") }
}
```

| 묶음 | 다루는 것 |
|---|---|
| `extraction` | 필드 자동 추출: 응답 래퍼, 매퍼, 이름, 깊이, kotlinx.serialization |
| `descriptions` | 필드 설명을 어디서 가져올지: 설명 규칙, 어노테이션, KDoc/Javadoc |
| `masking` | 헤더·쿠키·파라미터 값 가리기, 마스킹되는 필드 표시 |
| `http` | 요청·응답 가공, 콘솔 출력, MockMvc 생성 |
| `openApi` | OpenAPI 리소스: 보안 스키마, 스키마 이름, operationId |
| `asyncApi` | AsyncAPI 메타데이터 |
| `output` | 스니펫·문서 디렉터리, WireMock stub |
| `document` | 생성 AsciiDoc 문서: include, index 링크, 넣을 스니펫과 순서 |

콜백으로 주는 설정은 fun interface 입니다(`DescriptionResolver`, `ValueMasker`, `ObjectMapperProvider`, `PropertyNameStrategy`, `SchemaNameResolver`, `OperationIdResolver`). 이런 프로퍼티에 람다를 대입할 때는 SAM 생성자를 씁니다: `descriptions.resolver = DescriptionResolver { ... }`, `extraction.objectMapper = ObjectMapperProvider { mapper }`.

| 설정 | 기본값 | 설명 |
|---|---|---|
| `extraction.responseWrapper` | `null` | [공통 응답 래퍼](http-api.md#공통-응답-래퍼) |
| `extraction.objectMapper` | 아래 순서 | 직렬화에 쓸 Jackson 매퍼 (`ObjectMapperProvider { mapper }`). `restDocsMockMvc`·`restDocsWebTestClient` 가 찾은 앱 매퍼 빈, 클래스패스의 Jackson 3, Jackson 2 순서로 고릅니다 |
| `descriptions.resolver` | `DescriptionResolver.DEFAULT`: `@Description`, 그다음 `descriptions.annotations`, 그다음 KDoc/Javadoc | 필드 설명을 정하는 규칙. 내 규칙을 기본 단계와 조합하면 기본 단계도 계속 쓰입니다 ([설명 출처 바꾸기](http-api.md#설명-출처-바꾸기)) |
| `descriptions.annotations` | 없음 | `@Description` 다음에 설명을 읽을 어노테이션 ([다른 어노테이션에서 설명 가져오기](http-api.md#다른-어노테이션에서-설명-가져오기)) |
| `masking.annotations` | 없음 | `@Masked` 다음에 "값이 마스킹되어 나감" 표시로 읽을 어노테이션. 문서 표시만 하고 값은 바꾸지 않는다 ([마스킹되는 필드 표시](http-api.md#마스킹되는-필드-표시)). 헤더 값을 가리는 `masking.headers` 와는 다르다 |
| `extraction.propertyNameStrategy` | `null` (매퍼 설정을 따름) | 필드 이름 변환. 예: `PropertyNameStrategy { PropertyNamingStrategies.SNAKE_CASE.nameForField(null, null, it.name) }` (Jackson 2·3 공통. Jackson 3 의 `translate` 는 protected 라 `::translate` 는 컴파일되지 않습니다) |
| `extraction.enumDescriptionProperty` | `description` | Enum 상수 설명을 읽을 프로퍼티(필드 → getter → 같은 이름의 메서드). 필드 설명 뒤 값 목록과 `@EnumCode` 표의 `Description` 기본값에 쓰인다 |
| `descriptions.sourceDocs` | `true` | 실험 기능(`@ExperimentalRestDocsApi`). KDoc/Javadoc 설명의 테스트 쪽 차단 스위치. `false` 면 Gradle 플러그인이 넘긴 파일을 무시한다. 이 값으로 기능을 켤 수는 없다. 기능은 Gradle 플러그인의 `kardx { sourceDocs { enabled = true } }` 로 켜며(실험 기능, 기본 꺼짐), 켜지 않으면 파일이 없으므로 이 값은 아무 일도 하지 않는다 ([KDoc/Javadoc 에서 설명 가져오기](http-api.md#kdocjavadoc-에서-설명-가져오기)) |
| `extraction.maxDepth` | `10` | 자동 추출 최대 깊이. 넘으면 subsection |
| `extraction.arrayElementFieldsOptional` | `false` | 배열 원소의 하위 필드를 optional 로 표시. 빈 배열을 문서화할 때 켭니다. 배열 하나만이면 `responseFieldsOf<T>().optionalUnder("items[]")` |
| `extraction.kotlinxSerialization` | `true` | kotlinx.serialization `@Serializable` 클래스를 serial descriptor 로 추출(`@SerialName`, 기본값은 optional). kotlinx-serialization-json 이 클래스패스에 있을 때 Spring 이 직렬화하는 모양입니다. 앱이 Jackson 으로 직렬화하면 `false` ([kotlinx.serialization](http-api.md#kotlinxserialization)) |
| `masking.headers` | 없음 (가리지 않음) | 값을 가릴 헤더. [민감 정보 마스킹](#민감-정보-마스킹) |
| `masking.replacement` | `****` | 가린 값 대신 쓸 문자열 |
| `masking.masker` | `null` | 가리는 방식을 바꾸는 `ValueMasker`. `MaskTarget` 을 받는다: `location`(`HEADER`, `COOKIE`, `PARAMETER`), `name`, `value`, 쿠키면 `cookieName` |
| `masking.queryParameters` | 없음 (가리지 않음) | 값을 가릴 쿼리·폼 파라미터 (`?access_token=****`, `application/x-www-form-urlencoded` 본문의 `password=****`). `masking.maskSensitive()` 가 `MaskingSettings.SENSITIVE_QUERY_PARAMETERS` 를 더한다. [민감 정보 마스킹](#민감-정보-마스킹) |
| `http.requestPreprocessors` | 없음 | 요청에 더 적용할 Spring REST Docs `OperationPreprocessor`. 라이브러리 가공(URI·마스킹·pretty print) 뒤에 적용한다. 코드 전용 ([요청·응답 가공](customizing-snippets.md#요청응답-가공)) |
| `http.responsePreprocessors` | 없음 | 응답에 더 적용할 전처리기. 라이브러리 가공 뒤에 적용한다. 코드 전용 |
| `http.removedResponseHeaders` | `HttpSettings.VOLATILE_RESPONSE_HEADERS` (`Date`) | 값이 실행마다 바뀌어 문서에서 빼는 응답 헤더. 스니펫·WireMock 매핑이 실행마다 같게 유지된다. 자세한 내용은 표 아래 |
| `http.printRequests` | `false` | 요청·응답을 콘솔에 출력. 요청마다 읽으므로 클라이언트를 만든 뒤 `configure { http.printRequests = true }`·`withSettings` 로 켜도 된다 |
| `http.applySpringSecurity` | `false` | **(생성 시)** 켜면 `restDocsMockMvc` 가 컨텍스트의 Spring Security 필터 체인을 건다 ([Spring Security](http-api.md#spring-security)). 기본은 꺼져 있다. 자세한 내용은 표 아래 |
| `openApi.enabled` | `true` | epages 가 있을 때 OpenAPI 리소스를 만들지 여부 |
| `openApi.securitySchemes` | `true` | 요청의 `Authorization` 으로 OpenAPI 보안 스키마(Swagger Authorize)를 만든다. 실제 토큰은 스펙에 넣지 않는다 |
| `openApi.globalHeaders` | 없음 | Swagger Authorize 에서 한 번만 입력해 모든 요청에 붙일 헤더. 헤더 파라미터 대신 `apiKey` 보안 스키마가 된다 ([헤더와 인증](http-api.md#헤더와-인증)). Gradle 플러그인이 적용한다 |
| `openApi.includeStreams` | `false` | 끝나지 않는 스트림(`performStream`)도 OpenAPI 에 넣을지 여부. Swagger UI 는 이런 응답에서 계속 로딩만 돕니다 |
| `openApi.schemaNames` | `true` | 추출한 타입으로 OpenAPI 스키마 이름을 자동으로 붙인다 ([스키마 이름](http-api.md#스키마-이름)) |
| `openApi.schemaNamesWithoutWrapper` | `false` | 공통 응답 래퍼로 감싼 응답의 자동 이름에서 래퍼 이름을 뺀다 (`ApiResponseTodoResponse` → `TodoResponse`) |
| `openApi.schemaNameResolver` | `null` | 타입 → 스키마 이름 규칙 `SchemaNameResolver` (코드 전용) |
| `openApi.operationIdRule` | `HANDLER_METHOD` | OpenAPI operationId 규칙: `HANDLER_METHOD`(컨트롤러 메서드 이름 `searchUsers`), `CONTROLLER_AND_METHOD`(`userSearchUsers`), `PATH`(`getApiV1UsersById`), `TEST_NAME`(테스트 클래스·메서드 이름, 이전 동작) ([operationId](http-api.md#operationid)) |
| `openApi.operationIdResolver` | `null` | `OperationIdInput` 으로 operationId 를 직접 만드는 `OperationIdResolver`. `null` 을 돌려주면 `openApi.operationIdRule` 로 만든다 (코드 전용) |
| `openApi.errorSchema` | `null` | 오류 응답(4xx·5xx)의 공통 OpenAPI 스키마 이름. `openApi.errorSchema(ErrorResponse::class)` 로도 정합니다 ([오류 응답은 한 클래스로](http-api.md#오류-응답은-한-클래스로)) |
| `asyncApi.enabled` | `true` | 메시지·SSE 문서화 때 AsyncAPI 메타데이터를 남길지 여부 |
| `output.wireMockStubs` | `false` | `andDocument` 마다 WireMock 매핑 JSON 을 `<스니펫 디렉터리>/wiremock/mappings` 에 남긴다. Gradle 플러그인 `wireMock { enabled = true }` 가 켠다 ([WireMock stub](outputs.md#wiremock-stub)) |
| `output.snippetsDirectory` | `build/generated-snippets` (Maven 은 `target/...`) | 스니펫 디렉터리. Gradle 플러그인을 쓰면 플러그인 값이 우선하고, 플러그인이 테스트 전에 이 디렉터리를 비우므로 프로젝트·소스 디렉터리와, `build/` 밖이면서 플러그인이 쓰지 않은 파일이 있는 디렉터리는 거부합니다 ([플러그인이 비우는 디렉터리](build.md#플러그인이-비우는-디렉터리)) |
| `output.asciidocDirectory` | `build/generated-asciidoc` (Maven 은 `target/...`) | 생성 문서 디렉터리. Gradle 플러그인을 쓰면 플러그인 값이 우선하고, `output.snippetsDirectory` 와 같은 삭제 규칙이 적용됩니다 |
| `document.includePrefix` | `{snippets}` | 문서 안의 스니펫 include 경로 접두사 |
| `document.commonIncludeFiles` | 없음 | 모든 문서 머리에 include 할 파일 (문서 디렉터리 기준) |
| `document.indexLink` | `index.html` | 생성 문서의 처음과 끝, 왼쪽 목차 제목 자리에 넣는 "메인으로 돌아가기" 링크 대상. `null` 이면 넣지 않는다 ([AsciiDoc](asciidoc.md)) |
| `document.indexLinkText` | `null` (언어 기본값: `Back to index`, `ko` 면 `메인으로 돌아가기`) | 그 링크의 글자 |
| `language` | `DocsLanguage.EN` | 라이브러리가 생성 문서에 넣는 문구의 언어 (`EN`, `KO`). [문서 언어](#문서-언어) |
| `document.snippets` | `SnippetFile.defaults()` | 생성 문서에 넣을 스니펫 종류 ([문서에 넣을 스니펫 고르기](customizing-snippets.md#문서에-넣을-스니펫-고르기)) |
| `document.sectionOrder` | `METHOD_NAME` | JUnit `@TestMethodOrder` 가 없을 때 생성 문서의 섹션 순서. `DISPLAY_NAME` 이면 `@DisplayName` 순 ([섹션 순서](asciidoc.md#섹션-순서)) |

위 표에서 두 행의 자세한 내용입니다.

- **`http.removedResponseHeaders`** ([요청·응답 가공](customizing-snippets.md#요청응답-가공))
  - 대소문자를 구분하지 않습니다. `responseHeaders(...)` 로 문서화한 헤더는 늘 남습니다.
  - 비어 있지 않으면 `Max-Age` 가 있는 `Set-Cookie` 의 `Expires` 도 뺍니다 (지금 시각에서 계산되고, 어차피 `Max-Age` 가 우선합니다).
  - `emptySet()` 이면 받은 그대로 모두 남기고, `http.removedResponseHeaders += "X-Request-Id"` 로 더 뺍니다.
  - `http.responsePreprocessors` 앞에 적용합니다.
- **`http.applySpringSecurity`**
  - 인터셉터 등 다른 방식으로 인증하는 프로젝트가 있어 기본은 걸지 않습니다.
  - 필터 체인 빈이 있는데 정하지 않았으면 JVM 마다 한 번 경고하고, `false` 로 명시하면 경고하지 않습니다.
  - WebTestClient 에는 쓰지 않습니다 (WebFlux 가 `WebFilter` 빈을 겁니다).

Maven 판정은 Spring REST Docs 와 같습니다. 작업 디렉터리에 `pom.xml` 이 있으면 `target` 을 씁니다.

## yml 설정

`application.yml`(또는 프로필 yml, 시스템 속성 `-D`, 환경 변수)의 `kardx.*` 로 기본값을 정할 수 있습니다. `@SpringBootTest` 같은 Spring 테스트면 라이브러리가 테스트 컨텍스트의 설정을 읽습니다.

라이브러리 jar 에 Spring 설정 메타데이터(`META-INF/spring-configuration-metadata.json`)가 들어 있어 IntelliJ 가 키를 자동 완성하고 설명을 보여 줍니다. 저장소 소스에서는 `additional-spring-configuration-metadata.json` 이 원본이고, 빌드가 표준 이름으로 바꿔 jar 에 넣습니다. "Cannot resolve configuration property" 가 보이면 Gradle 을 다시 동기화(새 버전을 받도록)하세요.

```yaml
kardx:
  common-include-files: _landing.adoc
  mask-sensitive-headers: true
  response-wrapper:
    class: com.example.ApiResponse
    data-path: data
```

키는 기능별로 묶입니다. OpenAPI 설정은 `open-api.*`, AsyncAPI 는 `async-api.*`, 스니펫 위치는 `snippets.*`, 응답 래퍼는 `response-wrapper.*` 아래에 있고(boot 모듈의 `startup-log.*` 와 같은 모양), 나머지는 `kardx.` 바로 아래의 키입니다. yml 에서는 중첩해서 쓰거나(`open-api:` 아래 `enabled: false`) 점으로 이어 써도(`open-api.enabled: false`) 같습니다. 코드 설정을 묶을 때 yml 키는 바뀌지 않았고, 둘째 열이 코드 경로입니다.

| 속성 | 같은 설정 |
|---|---|
| `snippets.directory`, `asciidoc-directory` | `output.snippetsDirectory`, `output.asciidocDirectory` |
| `snippets.include-prefix` | `document.includePrefix` |
| `common-include-files` | `document.commonIncludeFiles` (쉼표 목록 또는 YAML 목록) |
| `index-link`, `index-link-text` | `document.indexLink`, `document.indexLinkText`. `index-link: none` 이면 링크를 넣지 않습니다 |
| `document-snippets` | `document.snippets`. 내장 스니펫의 파일 이름(`api-summary, http-request, response-fields`)이나 상수 이름(`request-info`) |
| `document-section-order` | `document.sectionOrder` (`method-name`, `display-name`) |
| `language` | `language` (`en`, `ko`, 대소문자 무시) |
| `masked-headers`, `masked-header-value` | `masking.headers`, `masking.replacement` |
| `masked-query-parameters` | `masking.queryParameters` (쉼표 목록 또는 YAML 목록) |
| `mask-sensitive-headers` | `masking.maskSensitive()` (민감 헤더와 `SENSITIVE_QUERY_PARAMETERS`) |
| `removed-response-headers` | `http.removedResponseHeaders` (쉼표 목록 또는 YAML 목록). 목록은 기본값에 더하지 않고 전체를 정하므로(코드의 `=` 와 같음) `Date` 도 계속 빼려면 넣는다: `Date, X-Request-Id`. `none` 이면 모두 남김 |
| `print-requests` | `http.printRequests` |
| `apply-spring-security` | `http.applySpringSecurity` |
| `wire-mock-stubs` | `output.wireMockStubs` |
| `array-element-fields-optional`, `max-extraction-depth`, `enum-description-property` | `extraction.arrayElementFieldsOptional`, `extraction.maxDepth`, `extraction.enumDescriptionProperty` |
| `kotlinx-serialization` | `extraction.kotlinxSerialization` (기본값 `true`) |
| `description-annotations` | `descriptions.annotations` (쉼표 목록 또는 YAML 목록). 별칭(`swagger`, `jackson`), `별칭#attribute`(`swagger#title`), `FQCN`, `FQCN#attribute`. 자세한 사용법은 [다른 어노테이션에서 설명 가져오기](http-api.md#다른-어노테이션에서-설명-가져오기) |
| `source-docs.enabled` | `descriptions.sourceDocs`. KDoc/Javadoc 설명의 테스트 쪽 차단 스위치 (기본값 `true`). `false` 면 Gradle 플러그인이 파일을 넘겨도 테스트가 무시한다. 기능 자체는 Gradle 플러그인에서 `kardx { sourceDocs { enabled = true } }` 로 켠다 (실험 기능, 기본 꺼짐) ([KDoc/Javadoc 에서 설명 가져오기](http-api.md#kdocjavadoc-에서-설명-가져오기)) |
| `masked-annotations` | `masking.annotations` (쉼표 목록 또는 YAML 목록). `FQCN`(규칙 설명 없음), `FQCN#attribute`(String 값이나 enum 이름을 규칙 설명으로). [마스킹되는 필드 표시](http-api.md#마스킹되는-필드-표시) |
| `response-wrapper.class`, `response-wrapper.data-path` | `extraction.responseWrapper` |
| `open-api.enabled` | `openApi.enabled` |
| `open-api.schema-names` | `openApi.schemaNames` |
| `open-api.schema-names-without-wrapper` | `openApi.schemaNamesWithoutWrapper` |
| `open-api.operation-id-rule` | `openApi.operationIdRule` (`handler-method`, `controller-and-method`, `path`, `test-name`) |
| `open-api.include-streams` | `openApi.includeStreams` |
| `open-api.security-schemes` | `openApi.securitySchemes` |
| `open-api.global-headers` | `openApi.globalHeaders` (쉼표 목록 또는 YAML 목록) |
| `open-api.error-schema` | `openApi.errorSchema`. 클래스 이름(FQCN)을 주면 클래스 이름이 스키마 이름이 됩니다 |
| `async-api.enabled` | `asyncApi.enabled` |

환경 변수 이름은 점과 `-` 를 모두 `_` 로 바꾼 것입니다(`KARDX_OPEN_API_ENABLED`, `KARDX_SNIPPETS_DIRECTORY`).

- 우선순위는 라이브러리 기본값, yml, `configure { }`, `withSettings` 순서입니다. 뒤가 이깁니다.
- yml 로 정하는 값은 모두 코드로도 정할 수 있습니다. 예를 들어 아래 둘은 같습니다.

```yaml
kardx:
  response-wrapper:
    class: com.example.ApiResponse
    data-path: data
  open-api:
    error-schema: com.example.ErrorResponse
  description-annotations: swagger, com.example.ApiField#text
```

```kotlin
RestDocsConfiguration.configure {
    extraction.responseWrapper<ApiResponse<*>>(dataPath = "data")   // 또는 extraction.responseWrapper(ApiResponse::class, "data")
    openApi.errorSchema<ErrorResponse>()                             // 또는 openApi.errorSchema(ErrorResponse::class)
    descriptions {
        annotation("swagger")                                        // 또는 from<Schema> { it.description }
        annotation(ApiField::class, "text")                          // 또는 annotation("com.example.ApiField#text")
    }
}
```
- `reset()` 은 `configure` 로 바꾼 값만 지우고 yml 값은 남깁니다. `AutoRestDocumentationExtension` 의 자동 되돌리기도 yml 값은 건드리지 않습니다.
- yml 값은 테스트의 Spring 컨텍스트에서 읽어 **JVM 전역 기본값 하나**에 반영합니다. `kardx.*` 값이 서로 다른 컨텍스트(프로파일, `@TestPropertySource`, `properties = [...]`)의 테스트 클래스를 **병렬로** 돌리면, 나중에 읽은 컨텍스트의 값으로 다른 클래스의 문서가 만들어질 수 있습니다. 그런 클래스는 차례로 실행하거나(`@Execution(ExecutionMode.SAME_THREAD)`, `@ResourceLock`), 클래스마다 다른 값을 yml 대신 `withSettings`·`configure` 로 줍니다. 기본 JUnit 실행(병렬 없음)은 클래스마다 자기 컨텍스트를 다시 읽으므로 문제없습니다.
- `AutoRestDocumentationExtension` 은 클래스 문서를 만들 때 마지막으로 실행한 메서드의 설정을 씁니다. 같은 클래스의 메서드를 병렬로 돌리면(`@Execution(CONCURRENT)`) 메서드마다 다른 문서 설정(`document.commonIncludeFiles` 등)은 보장되지 않고, 테스트가 끝날 때의 설정 되돌리기도 서로 겹칩니다. 메서드마다 다른 값은 `withSettings` 로 줍니다. Kotest 스펙 안의 테스트 병렬 실행도 같은 이유로 지원하지 않습니다.
- `configure { }` 블록은 기억해 두었다가 yml 을 다시 읽거나 블록을 더할 때마다 다시 적용합니다. 블록 안에서는 설정 값만 바꾸고, 카운터 증가·파일 쓰기 같은 부수효과는 두지 마세요.
- 목록·집합 설정(`descriptions.annotations`, `masking.headers`, `document.commonIncludeFiles` 등)에 대입한 값은 복사해 둡니다. 넘긴 `MutableList` 를 나중에 바꿔도 반영되지 않습니다.
- fun interface 로 주는 설정(`descriptions.resolver`, `extraction.objectMapper`, `extraction.propertyNameStrategy`, `masking.masker`, `openApi.schemaNameResolver`, `openApi.operationIdResolver`)은 코드에서만 바꿀 수 있습니다.
- 앱 실행 설정(시작 로그의 `docs-path`, `startup-log.*`)은 운영 `application.yml` 에 씁니다: [앱 시작 로그](build.md#앱-시작-로그). 문서 경로·파일 이름은 Gradle 플러그인이 남긴 실제 값을 기본으로 읽으므로 보통 적지 않습니다.
- **모르는 키는 경고합니다.** Spring 테스트가 컨텍스트 설정을 읽을 때 `kardx.` 로 시작하는데 위 표(와 시작 로그의 `docs-path`·`startup-log.*`)에 없는 키를 찾으면, 가장 가까운 키를 붙여 경고 로그를 한 번 남깁니다. 오타 난 키는 아무 효과 없이 무시되기 때문입니다.
  - relaxed binding 형태(`maskSensitiveHeaders`, `mask_sensitive_headers`, 환경 변수 `KARDX_MASK_SENSITIVE_HEADERS`, 목록 색인 `common-include-files[0]`)는 같은 키로 봅니다.
  - 이름을 모두 알 수 있는 속성 소스(yml·properties·시스템 속성·환경 변수)만 봅니다.
  - 시작 로그 키(`startup-log.*`)는 boot 모듈이 읽으므로 하위 키의 오타는 검사하지 않습니다.
  - 키가 기능별로 묶이기 전의 평평한 이름(`open-api-enabled`, `open-api-schema-names`, `async-api-enabled`, `snippets-directory`, `snippets-include-prefix` 등)은 읽지 않고, 경고가 가장 가까운 키 대신 바뀐 키를 정확히 알려 줍니다(`... is ignored. It was renamed to 'kardx.open-api.enabled'.`). 환경 변수 이름은 바뀌지 않았습니다.

```
WARN ... io.github.kardx.config : kardx[KDX-001]: unknown property 'kardx.mask-sensitive-header' in [Config resource 'class path resource [application-docs.yml]' via location 'optional:classpath:/'] is ignored. Did you mean 'kardx.mask-sensitive-headers'? Supported properties are listed in docs/configuration.md.
```
- 목록에 더하는 함수(`descriptions.annotation`, `descriptions.from`)는 yml 목록 뒤에 더하고, `withSettings` 에서 부르면 그 뒤에 더합니다. 통째로 바꾸려면 `descriptions.annotations = listOf(...)` 처럼 대입합니다 ([코드로 설정하기](http-api.md#코드로-설정하기)).
- 값이 잘못되면 속성 이름을 담은 오류로 실패합니다 (`kardx.print-requests: expected true or false`, `kardx.description-annotations: class 'com.example.ApiField' not found`).
- Spring 테스트가 아니면 시스템 속성(`-Dkardx.masked-headers=Authorization`)과 환경 변수(`KARDX_MASKED_HEADERS`)를 읽습니다.

## 문서 언어

라이브러리가 생성 문서에 넣는 문구(목록 제목, 링크 글자, 설명 접두사 등)는 기본이 영어이고, 한 설정으로 한국어로 바꿉니다. 직접 쓴 설명·`@DisplayName`·템플릿은 바뀌지 않습니다.

**Gradle 플러그인을 쓰면 플러그인에서 정합니다.**

```kotlin
// build.gradle.kts
kardx {
    language = "ko"
}
```

플러그인은 이 값을 테스트 JVM 과 문서 생성 태스크(`apiDocsIndex`, `apiDocsAsyncApi`)에 시스템 속성 `kardx.language` 로 넘기고, 앱 리소스(`docs-locations.properties`)에 남겨 [앱 시작 로그](build.md#앱-시작-로그)도 같은 언어를 쓰게 하며, 플러그인이 직접 만드는 문구에도 씁니다. yml 에만 적으면 테스트가 만드는 문구는 바뀌지만 플러그인 문구(OpenAPI 설명의 제약·쿠키 문구, 공통 오류 응답 표, OpenAPI 뷰어의 오프라인 안내, 집계 페이지)는 영어로 남아 섞입니다. 지원하지 않는 값이면 빌드가 실패합니다 (`kardx.language must be en or ko, got 'fr'`).

플러그인으로 테스트를 돌리는데 문서 언어(`kardx.language` 나 `RestDocsConfiguration.configure` 로 정한 값)가 플러그인 언어와 다르면, 라이브러리가 `io.github.kardx.config` 로거에 JVM 마다 한 번 WARN 을 남깁니다: `kardx[KDX-005]: the documentation language is 'ko' (kardx.language or RestDocsConfiguration.configure) but the Gradle plugin uses 'en', so plugin-made pages and test-made snippets will mix languages. Set kardx { language = "ko" } in the build file.` 메시지대로 `kardx { }` 에서 언어를 정하면 됩니다. 플러그인 없이 돌면 비교할 값이 없으므로 경고하지 않습니다.

플러그인 없이 쓰면 다음으로 정합니다. 우선순위는 다른 설정과 같습니다: 시스템 속성·환경 변수·yml < `configure { }` < `withSettings`.

```yaml
kardx:
  language: ko            # en(기본값), ko. 대소문자 무시. 시스템 속성 -Dkardx.language, 환경 변수 KARDX_LANGUAGE
```

```kotlin
RestDocsConfiguration.configure { language = DocsLanguage.KO }   // io.github.kardx.config.DocsLanguage
```

| 언어를 따르는 것 | `en` (기본값) | `ko` |
|---|---|---|
| 문서 목록(index) 내장 템플릿의 목록 제목·문서 링크 | `API list`, `Open document` | `API 목록`, `문서 열기` |
| 생성 문서의 index 링크 ([AsciiDoc](asciidoc.md)) | `← Back to index` | `← 메인으로 돌아가기` |
| OpenAPI·AsyncAPI 스키마 설명의 접두사 | `Constraints: `, `Items: ` | `제약: `, `원소: ` |
| 자동으로 붙는 필드 설명: Spring Data `Page`·`Slice`·`Sort`·`Pageable`, `ProblemDetail` | `Items on this page`, `HTTP status code` … | `이 페이지의 항목`, `HTTP 상태 코드` … |
| sealed 타입 구분자 설명 | `Type discriminator`, `Value when the type is …` | `타입 구분자`, `… 타입일 때의 값` |
| AsyncAPI 채널 설명 (연결·구독 헤더, 선택 이벤트) | `Headers sent when connecting (…)` … | `연결할 때 헤더를 보낸다 (…)` … |
| AsyncAPI 뷰어 화면 (`<html lang>` 포함) | `Connection`, `Not connected` … | `연결 설정`, `연결 안 됨` … |
| 공통 오류 응답 표 (플러그인 `openApiCommonResponses`) | `Common error responses`, `Status code` | `공통 오류 응답`, `상태 코드` |
| OpenAPI 설명의 쿠키 목록·선택 표시 (플러그인) | `Cookies:`, `(optional)` | `쿠키:`, `(선택)` |
| OpenAPI 뷰어 오프라인 안내·`<html lang>` (플러그인) | `Could not load the Redoc script (unpkg.com). …` | `Redoc 스크립트(unpkg.com)를 불러오지 못했습니다. …` |
| 집계 페이지 (플러그인 `apiDocsAggregate`) | `Merged OpenAPI: `, `Project` | `전체 OpenAPI: `, `프로젝트` |
| 앱 시작 로그의 머리·항목 이름 ([앱 시작 로그](build.md#앱-시작-로그)) | `API docs (OpenAPI spec)` … | `API 문서 (OpenAPI 스펙)` … |

언어와 상관없이 **언제나 영어**인 것: 로그·경고·예외 메시지(단, 위의 앱 시작 로그는 줄과 문서 없음 경고가 언어를 따름), Gradle 태스크의 콘솔 출력(`Generated API docs` 블록 등), `apiDocsCheck` 보고서, IDE 설정 메타데이터의 설명. 표 머리글(`Path`, `Type`, `Description` 등)은 스니펫 템플릿이 정하므로 언어 설정과 상관없습니다 ([스니펫 템플릿 바꾸기](customizing-snippets.md#스니펫-템플릿-바꾸기)).

개별 설정이 언어 기본값보다 먼저입니다. `document.indexLinkText`, 시작 로그의 `startup-log.title`·`*.label`, 플러그인의 `index.title`(기본값 프로젝트 이름) 을 정하면 그 글자를 그대로 씁니다.

## 민감 정보 마스킹

기본으로는 아무것도 가리지 않습니다. 요청·응답·메시지에 실제로 있는 값이 그대로 문서에 남습니다. 토큰이나 세션 값이 남으면 안 되는 헤더를 골라 켭니다.

```kotlin
RestDocsConfiguration.configure {
    masking {
        maskSensitive()                              // Authorization, Proxy-Authorization, Cookie, Set-Cookie 와 자격증명 쿼리·폼 파라미터
        headers = headers + "X-Api-Key"              // 직접 더하기
        queryParameters = queryParameters + "ticket" // 쿼리·폼 파라미터도
        masker = ValueMasker { target ->             // 가리는 방식 바꾸기
            val auth = target.location == MaskLocation.HEADER && target.name.equals("Authorization", ignoreCase = true)
            if (auth) target.value.substringBefore(' ') + " ****" else "****"
        }
    }
}
```

yml 로는 `mask-sensitive-headers: true`, `masked-headers: X-Api-Key`, `masked-query-parameters: ticket`, `masked-header-value: "[hidden]"` 입니다.

- 헤더 이름은 그대로 남으므로 `requestHeaders` 로 문서화할 수 있습니다. 대소문자는 구분하지 않습니다.
- 인증 헤더(`Authorization`, `Proxy-Authorization`)는 방식은 남기고 값만 가립니다: `Bearer ****`. `masking.masker` 를 정하면 값 전체가 든 `MaskTarget` 을 받습니다. 가리는 쿠키마다(`location = COOKIE`, `cookieName` 있음), 가리는 쿼리·폼 파라미터에도(`location = PARAMETER`, `name` 이 파라미터 이름) 불리고, 파라미터 값에는 방식 앞부분이 없으므로 값의 앞 몇 글자를 남기는 마스커는 파라미터 토큰의 일부를 드러냅니다. 위 예시처럼 `location` 과 `name` 으로 가르세요.
- `Cookie`, `Set-Cookie` 는 쿠키마다 값만 가리고 이름과 속성은 남깁니다 (`SESSION=****; Path=/; HttpOnly`). curl 스니펫의 `--cookie` 도 같이 가립니다.
- HTTP 스니펫, WebSocket·STOMP 메시지 스니펫에 같은 규칙이 적용됩니다.
- **쿼리·폼 파라미터.** 브라우저 `EventSource`·WebSocket 처럼 헤더를 보낼 수 없는 클라이언트는 토큰을 쿼리(`?access_token=...`)로 보내고, OAuth 토큰 요청·로그인 폼은 `password`·`client_secret` 을 `application/x-www-form-urlencoded` 본문으로 보냅니다(MockMvc POST 의 `.param(...)` 은 이 본문이 됩니다).
  - `masking.queryParameters` 의 파라미터 값은 HTTP 문서화(`andDocument`)의 요청 URI, 폼 본문, multipart 의 파일이 아닌 파트에서 가려지므로 curl·httpie·http-request·request-body 스니펫과 OpenAPI 예시에 `access_token=****`, `password=****` 로 나옵니다.
  - 파라미터 이름·순서와 다른 파라미터는 그대로이고 `Content-Length` 는 가린 본문에 맞춥니다.
  - JSON 본문의 필드는 `@Masked` 로 가립니다.
  - WireMock 매핑에서는 가린 파라미터가 아무 값이나 맞추므로(`"matches": ".*"`) 스텁을 그대로 쓸 수 있습니다.
  - 이름은 대소문자를 구분하지 않습니다. `masking.masker` 를 정하면 `location = PARAMETER` 와 파라미터 이름·값으로 부릅니다.
- `masking.maskSensitive()`(yml `mask-sensitive-headers: true`)는 `MaskingSettings.SENSITIVE_QUERY_PARAMETERS` 도 더합니다: `access_token`, `id_token`, `refresh_token`, `token`, `api_key`, `apikey`, `client_secret`, `client_assertion`, `password`, `passwd`, `current_password`, `new_password`, `signature`, `sig`, `X-Amz-Signature`, `X-Amz-Security-Token`, `X-Goog-Signature`. 그래서 이 설정을 켜면 `?access_token=demo-token` 이나 `password=...` 로그인 폼을 보내는 테스트의 문서는 `access_token=****`, `password=****` 가 됩니다. yml 키 `mask-sensitive-headers` 도 이름과 달리 이 파라미터들을 가립니다.
- AsyncAPI 문서와 뷰어는 배포되는 산출물이라, 이 설정과 상관없이 `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie` 값을 항상 가립니다.
- 이 기능은 문서에 찍히는 헤더 값을 가립니다. 앱이 응답 본문의 값을 가려서 내보낸다는 사실(이름 `홍*동` 등)을 문서에 적으려면 [마스킹되는 필드 표시](http-api.md#마스킹되는-필드-표시)(`@Masked`)를 씁니다.
- **Kotlin 기본값.** Kotlin 클래스에서 추출한 요청 필드·파라미터는 생성자 기본값을 문서에 냅니다(`val size: Int = 20` → `Default: 20`, OpenAPI `default`).
  - `@Masked` 필드(와 `masking.annotations` 가 붙은 필드)의 기본값은 내지 않습니다.
  - 두 번 계산해 값이 다른 기본값은 뺍니다: 랜덤 ID·현재 시각, 그리고 다른 생성자 파라미터에서 계산한 기본값(`val pageSize: Int = limit`)입니다. 두 번째 계산은 필수 파라미터에 다른 자리 값을 넣기 때문입니다. 이를 위해 클래스마다 객체를 두 번 만들므로 `init` 블록의 부수 효과도 두 번 실행됩니다.
  - 컬렉션 파라미터에서 계산한 기본값(`val count: Int = items.size`)은 두 번 다 빈 컬렉션이라 구별하지 못합니다.
  - 환경 변수·설정에서 읽는 기본값(`val clientId: String = System.getenv("CLIENT_ID") ?: "local"`)은 상수와 구별할 수 없어 테스트를 돌릴 때의 값이 **그대로 나갑니다**. 요청 클래스의 생성자 기본값에 비밀 값을 두지 말거나 그 필드에 `@Masked` 를 붙이세요 ([Kotlin 기본값](http-api.md#기본값예시제약-설명)).

## 실험 API

아직 다듬는 중인 API 에는 `@ExperimentalRestDocsApi`(`io.github.kardx`)가 붙어 있습니다. 다음 릴리스에서 바뀌거나 빠질 수 있고, 1.0 의 호환 보장 범위에 들지 않습니다. opt-in 없이 쓰면 컴파일 오류가 아니라 경고가 납니다.

지금 붙은 곳:

- KDoc/Javadoc 설명: `descriptions.sourceDocs`, `DescriptionResolver.SOURCE_DOCS`, `FromParamTag` 와 `pathMeans`/`queryMeans`/`formMeans`/`headerMeans`/`partMeans(FromParamTag)`, 이름만 적는 파라미터 선언(`pathParameters("id")`, `queryParameters(...)`, `formParameters(...)`, `requestHeaders(...)`, `requestParts(...)`) ([KDoc/Javadoc 에서 설명 가져오기](http-api.md#kdocjavadoc-에서-설명-가져오기))
- 메시징 테스트 도구: `StompTestClient`, `WebSocketHandlerTester` ([메시징](messaging.md))
- 클라이언트 어댑터 SPI `io.github.kardx.client.ClientDocumentation` ([클라이언트 어댑터 SPI](architecture.md#클라이언트-어댑터-spi))

쓰는 곳에 붙이거나 모듈 전체에 켭니다.

```kotlin
@OptIn(ExperimentalRestDocsApi::class)
class OrderApiDocsTest { ... }
```

```kotlin
// build.gradle.kts
kotlin {
    compilerOptions {
        optIn.add("io.github.kardx.ExperimentalRestDocsApi")
    }
}
```
