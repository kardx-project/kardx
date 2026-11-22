[English](../http-api.md) | **한국어**

# HTTP API 문서화

[문서 목록](README.md)

<!-- toc -->
**목차**

- [브라우저에서 보기](#브라우저에서-보기)
- [문서화 DSL](#문서화-dsl)
  - [andDocument](#anddocument)
  - [항목 문법](#항목-문법)
  - [파라미터 타입은 핸들러에서 정해진다](#파라미터-타입은-핸들러에서-정해진다)
  - [deprecated API](#deprecated-api)
  - [느슨한 검증](#느슨한-검증)
- [필드 자동 추출](#필드-자동-추출)
  - [추출 규칙](#추출-규칙)
  - [빈 추출 결과](#빈-추출-결과)
  - [Spring Data 페이지](#spring-data-페이지)
  - [Spring HATEOAS](#spring-hateoas)
  - [kotlinx.serialization](#kotlinxserialization)
  - [지원하지 않는 모양](#지원하지-않는-모양)
  - [다른 어노테이션에서 설명 가져오기](#다른-어노테이션에서-설명-가져오기)
  - [KDoc/Javadoc 에서 설명 가져오기](#kdocjavadoc-에서-설명-가져오기)
  - [설명 출처 바꾸기](#설명-출처-바꾸기)
  - [추출 결과와 직접 기술 섞기](#추출-결과와-직접-기술-섞기)
  - [추출 결과 미리보기](#추출-결과-미리보기)
  - [더 이상 쓰지 않는 필드](#더-이상-쓰지-않는-필드)
  - [마스킹되는 필드 표시](#마스킹되는-필드-표시)
- [검증 실패 메시지](#검증-실패-메시지)
- [헤더와 인증](#헤더와-인증)
  - [Spring Security](#spring-security)
- [공통 응답 래퍼](#공통-응답-래퍼)
- [여러 상태 코드 문서화](#여러-상태-코드-문서화)
  - [오류 응답은 한 클래스로](#오류-응답은-한-클래스로)
  - [선언한 오류 응답](#선언한-오류-응답)
  - [공통 오류 응답](#공통-오류-응답)
- [OpenAPI 이름과 메타데이터](#openapi-이름과-메타데이터)
  - [스키마 이름](#스키마-이름)
  - [operationId](#operationid)
  - [OpenAPI 태그와 요약](#openapi-태그와-요약)
  - [Enum 은 드롭다운으로](#enum-은-드롭다운으로)
  - [정규식 경로 변수](#정규식-경로-변수)
  - [값 하나인 JSON 본문](#값-하나인-json-본문)
- [파일 다운로드](#파일-다운로드)
  - [스니펫에 나가는 것](#스니펫에-나가는-것)
  - [요청 본문 파일 업로드](#요청-본문-파일-업로드)
  - [OpenAPI 결과](#openapi-결과)
- [OpenAPI 스펙에 나가는 정보](#openapi-스펙에-나가는-정보)
  - [타입과 형식](#타입과-형식)
  - [제약조건](#제약조건)
  - [sealed 타입과 oneOf](#sealed-타입과-oneof)
  - [기본값·예시·제약 설명](#기본값예시제약-설명)
  - [multipart 와 쿠키](#multipart-와-쿠키)
  - [그 밖에 다듬는 것](#그-밖에-다듬는-것)
  - [Gradle 플러그인 없이 쓸 때](#gradle-플러그인-없이-쓸-때)
- [MockMvc 보조 함수](#mockmvc-보조-함수)
<!-- /toc -->

MockMvc 테스트에서 요청·응답을 문서화하는 방법입니다. WebTestClient(WebFlux 앱)도 같은 블록을 씁니다 ([WebTestClient](webtestclient.md)). 필드는 직접 쓰거나 타입에서 추출합니다. 성공·오류처럼 상태 코드가 여러 개인 API 는 [여러 상태 코드 문서화](#여러-상태-코드-문서화)를 보세요.

## 브라우저에서 보기

앱을 띄우면(`bootRun`, IntelliJ 실행, `java -jar`) 주소로, 띄우지 않았다면 파일로 엽니다.

| 보고 싶은 것 | 앱 주소 | 파일 |
|---|---|---|
| 전체 API 목록 (index) | `http://localhost:8080/docs/index.html` | `build/api-docs/index.html` |
| 테스트 클래스 하나의 문서 | `http://localhost:8080/docs/product-api-docs-test.html` | `build/api-docs/product-api-docs-test.html` |
| API 하나 (테스트 메서드 하나) | `http://localhost:8080/docs/product-api-docs-test.html#get-product-not-found` | 같은 파일 + `#get-product-not-found` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` | 없음 (앱이 필요합니다) |
| OpenAPI 스펙 | `http://localhost:8080/docs/openapi3.yaml` | `build/api-docs/openapi3.yaml` |
| OpenAPI 정적 뷰어 (Redoc·Scalar, 켰을 때) | `http://localhost:8080/docs/openapi.html` | `build/api-docs/openapi.html` (앱 없이 열림) |

주소의 `/docs` 는 Gradle 플러그인의 `bootJar.path`(기본 `static/docs`)에서, 포트는 앱 설정에서 옵니다. 파일 경로는 `./gradlew apiDocs` 기준이고 플러그인의 `outputDirectory` 로 바꿀 수 있습니다. 문서 이름은 테스트 클래스 이름, `#` 뒤는 테스트 메서드 이름을 kebab-case 로 바꾼 값입니다 (`ProductApiDocsTest.getProductNotFound` → `product-api-docs-test.html#get-product-not-found`).

- Swagger UI 는 springdoc 의존성과 `springdoc.swagger-ui.url: /docs/openapi3.yaml` 설정이 있어야 이 스펙을 보여 줍니다 ([시작하기](getting-started.md#4-applicationyml-운영)). 앱 없이 보려면 `openapi3.yaml` 을 [Swagger Editor](https://editor.swagger.io) 에 붙여 넣습니다.
- OpenAPI 정적 뷰어는 기본으로 꺼져 있고 Gradle 플러그인의 `kardx { openApiViewer { enabled = true } }` 로 켭니다 ([OpenAPI 뷰어](outputs.md#openapi-뷰어-redoc--scalar)). 스펙을 페이지 안에 넣으므로 springdoc 이 없어도 보입니다.
- Swagger UI 의 Try it out 은 문서를 연 앱으로 요청을 보냅니다 (`openapi3 { setServer("/") }`).
- 상태 코드가 여러 개인 API 는 Swagger UI 에서 operation 하나를 펼치면 응답 목록에 함께 보입니다.

## 문서화 DSL

### andDocument

`ResultActions.andDocument { }` 는 블록에서 지정한 항목마다 REST Docs 스니펫을 만듭니다. 같은 함수를 여러 번 부르면 항목이 이어 붙습니다. 항목 목록을 받는 함수(`pathParameters`, `requestFields` …)는 가변 인자와 `List` 를 모두 받습니다.

`mockMvc.perform(...)` 대신 MockMvc Kotlin DSL 이나 `MockMvcTester` 로 요청해도 같은 블록을 씁니다. 세 진입점이 만드는 스니펫·OpenAPI 조각은 같습니다.

```kotlin
// MockMvc Kotlin DSL: ResultActionsDsl.andDocument
mockMvc.get("/api/users/{id}", 1L) {
    accept = MediaType.APPLICATION_JSON
}.andExpect {
    status { isOk() }
}.andDocument {
    pathParameters("id" pathMeans "사용자 ID")
    responseFields<UserResponse>()
}

// MockMvcTester (Spring Framework 6.2+, Boot 3.4+): MvcTestResult.andDocument. AssertJ 검증을 이어 쓴다
val tester = MockMvcTester.create(mockMvc)       // mockMvc 는 restDocsMockMvc(context, provider)
val result = tester.get().uri("/api/users/{id}", 1L).exchange()
    .andDocument { pathParameters("id" pathMeans "사용자 ID") }

assertThat(result).hasStatusOk()
```

세 함수 모두 `import io.github.kardx.mockmvc.andDocument` 하나로 씁니다. `MockMvcTester` 가 없는 Spring 6.1 이하에서도 나머지 두 진입점은 그대로 동작합니다.

WebTestClient 는 `exchange()` 뒤에 같은 블록을 붙입니다(`import io.github.kardx.webtestclient.andDocument`). RestTestClient(Boot 4)도 같습니다(`import io.github.kardx.resttestclient.andDocument`). 설정 헬퍼와 지원 범위는 [WebTestClient · RestTestClient](webtestclient.md) 에 있습니다.

```kotlin
webTestClient.get()
    .uri("/api/users/{id}", 1L)
    .exchange()
    .expectStatus().isOk
    .andDocument { pathParameters("id" pathMeans "사용자 ID") }
```

필드·파라미터가 실제 요청·응답과 맞지 않으면 원인을 알려 주는 메시지로 실패합니다 ([검증 실패 메시지](#검증-실패-메시지)).

| 블록 멤버 | 만드는 스니펫 |
|---|---|
| `description = "..."` | `api-summary` (메서드, URL 템플릿, 응답 상태, 설명). 항상 만듭니다. 템플릿에 쿼리(`?q={q}`)가 있으면 떼고 보여 줍니다 |
| `deprecated = true`, `deprecated("이유")` | `api-summary` 에 `*Deprecated*` 줄, OpenAPI `deprecated: true` ([deprecated API](#deprecated-api)) |
| `pathParameters(...)` | `path-parameters` |
| `pathParameters("id")`, `queryParameters("page", "size")`, 그리고 이름만 받는 `formParameters`·`requestHeaders`·`requestParts` | 같은 스니펫. 설명은 핸들러 메서드의 `@param` 태그에서 가져옵니다. Gradle 플러그인과 알려진 핸들러(MockMvc, 핸들러를 기록하는 WebTestClient)가 필요합니다 ([이름만 적은 파라미터 → 메서드 `@param`](#이름만-적은-파라미터--메서드-param)) |
| `queryParameters(...)` | `query-parameters` |
| `formParameters(...)` | `form-parameters` |
| `requestHeaders(...)`, `responseHeaders(...)` | `request-headers`, `response-headers` |
| `requestCookies(...)`, `responseCookies(...)` | `request-cookies`, `response-cookies` |
| `requestParts(...)` | `request-parts` |
| `requestPartFields("meta", ...)` | `request-part-meta-fields` |
| `requestFields(...)`, `responseFields(...)` | `request-fields`, `response-fields` |
| `requestFile("...")`, `responseFile("...")` | `request-file`, `response-file`: 파일 본문이 무엇인지(설명, 미디어 타입, 파일 이름, 크기)와 OpenAPI 본문 설명 ([파일 다운로드](#파일-다운로드)) |
| `links(...)` | `links` |
| `events(...)` | `sse-events`, `response-fields-event-<이름>` ([SSE](messaging.md#server-sent-events)) |
| `relaxed = true`, `relaxed(RelaxedSection...)` | 모든 항목, 또는 고른 항목만 relaxed 모드로 검증합니다 (문서화하지 않은 항목 허용) ([느슨한 검증](#느슨한-검증)) |
| `errorResponse(status, "...")` | `error-responses`, 그리고 테스트가 만들지 않는 오류의 OpenAPI 응답 ([선언한 오류 응답](#선언한-오류-응답)) |
| `snippets(...)` | 위로 표현할 수 없는 REST Docs 스니펫·직접 만든 스니펫을 그대로 더합니다 ([직접 만든 스니펫 더하기](customizing-snippets.md#직접-만든-스니펫-더하기)) |
| `openApi { ... }` | [OpenAPI 설정](openapi.md#openapi--swagger-ui) |

### 항목 문법

```kotlin
"id" pathMeans "사용자 ID" ofType Long::class
"page" queryMeans "페이지 번호" ofType Int::class isOptional true withDefaultValue "0" example "2"
"name" formMeans "이름" constraint "2~10자"
"Authorization" headerMeans "Bearer 토큰"
"SESSION" cookieMeans "세션 ID"
"file" partMeans "업로드 파일"
"self" linkMeans "이 리소스"
"nickname" type STRING means "닉네임" isOptional true example "홍길동"
"metadata" subsection OBJECT means "자유 형식 메타데이터"    // 하위 필드를 검증하지 않는다
"internal" type STRING means "내부용" isIgnored true         // 표에서 빼고 검증도 하지 않는다
```

| 연산자 | 대상 | 의미 |
|---|---|---|
| `means` | 필드 | 설명 |
| `pathMeans FromParamTag` (`queryMeans`, `formMeans`, `headerMeans`, `partMeans` 도) | 파라미터, 헤더, 파트 | 수식어는 붙이면서 설명은 핸들러의 `@param` 태그에서 가져옵니다: `"page" queryMeans FromParamTag isOptional true` ([이름만 적은 파라미터 → 메서드 `@param`](#이름만-적은-파라미터--메서드-param)) |
| `isOptional true`, `optional()` | 모두 (필드, 파라미터, 헤더, 쿠키, 링크, 파트, SSE 이벤트) | 없어도 되는 항목 |
| `constraint` | 필드, 파라미터, 헤더 | Constraints 열. OpenAPI·AsyncAPI 설명 뒤 `제약: ...` (`language = "ko"`. 기본 `en` 은 `Constraints: ...`) |
| `example`, `withDefaultValue` | 필드, 파라미터, 헤더 | 설명 칸에 예시·기본값 표시. OpenAPI `example`·`default` (타입에 맞춰 숫자·불리언으로) |
| `enumOf TodoStatus::class` | 필드, 파라미터 | 가능한 값. OpenAPI `enum` (Swagger UI 드롭다운) |
| `ofType Long::class`, `ofType typeOf<List<Long>>()` | 파라미터 | 값 타입. OpenAPI 파라미터 `type`·`format` (없으면 컨트롤러 인자 타입, 그것도 모르면 `string`. [파라미터 타입은 핸들러에서 정해진다](#파라미터-타입은-핸들러에서-정해진다)). 타입 분석을 쓰므로 `parametersOf` 와 같은 `io.github.kardx.extractor` 에 있다 |
| `isIgnored true`, `ignored()` | 필드 | 문서와 검증에서 제외 |
| `deprecated()`, `deprecated "이유"` | 필드, 파라미터 | 설명 칸 앞에 `Deprecated: 이유`. OpenAPI·AsyncAPI `deprecated: true` ([더 이상 쓰지 않는 필드](#더-이상-쓰지-않는-필드)) |
| `masked()`, `masked "규칙"` | 필드 | 값이 마스킹되어 나간다는 표시. 설명 칸 앞에 `Masked: 규칙`. OpenAPI·AsyncAPI `x-masked: true` ([마스킹되는 필드 표시](#마스킹되는-필드-표시)) |
| `withFields Event::class`, `withFields 필드 목록` | SSE 이벤트 | 이벤트 `data` 의 필드. 클래스를 주면 응답 규칙으로 추출 ([SSE](messaging.md#server-sent-events)) |

`isOptional false`, `isIgnored false` 는 아무것도 바꾸지 않습니다. 이미 optional·ignored 인 항목을 되돌리지 않으므로, 조건에 따라 켤 때 `isOptional (page == null)` 처럼 그대로 넘겨도 됩니다.

수식어(`means`, `isOptional`, `optional()`, `constraint`, `example`, `withDefaultValue`, `enumOf`, `ofType`, `deprecated`, `masked` …)는 원래 항목을 바꾸지 않고 **복사본을 돌려줍니다**. 그래서 같이 쓰는 항목을 `val` 상수로 빼 두어도 됩니다. 한 테스트에서 붙인 수식어는 그 테스트에만 적용됩니다.

```kotlin
val AUTH = "Authorization" headerMeans "Bearer 액세스 토큰"

requestHeaders(AUTH)                       // 필수
requestHeaders(AUTH isOptional true)       // 이 테스트에서만 optional. AUTH 자체는 그대로 필수
```

원래 항목이 바뀌지 않으므로, 결과를 쓰지 않은 수식어는 아무 효과가 없습니다. 위처럼 결과를 넘겨야 합니다.

```kotlin
val nickname = "nickname" type STRING means "닉네임"
nickname isOptional true                   // 효과 없음: 복사본을 버린다
responseFields(nickname isOptional true)   // 결과를 쓴다
```

Spring REST Docs 디스크립터를 직접 바꾸면(`AUTH.descriptor.optional()`) 공유한 객체도 바뀌므로 수식어를 씁니다. 추출 목록(`responseFieldsOf<T>()`)은 부를 때마다 새로 만듭니다. 목록의 일부만 optional 로 바꿀 때는 [`optionalAt`](#추출-결과와-직접-기술-섞기)을 씁니다.

필드 타입은 `STRING`, `NUMBER`, `BOOLEAN`, `OBJECT`, `ARRAY`, `NULL`, `VARIES` 와 의미를 드러내는 `ENUM`, `DATE`, `DATETIME`(JSON 에서는 문자열) 입니다. `DATE`·`DATETIME` 은 OpenAPI·AsyncAPI 에 `format: date`·`format: date-time` 이 붙습니다. 직접 쓴 파라미터의 OpenAPI 타입은 `ofType` 이 없으면 컨트롤러 인자에서 정해집니다 (아래 절).

### 파라미터 타입은 핸들러에서 정해진다

직접 쓴 경로·쿼리·폼 파라미터(`pathMeans`, `queryMeans`, `formMeans`)에 타입을 적지 않으면, 요청을 처리한 컨트롤러 메서드의 같은 이름 인자에서 OpenAPI 타입을 정합니다. 대상 인자는 `@PathVariable`, `@RequestParam`, 그리고 어노테이션 없는 단순 타입 인자(Spring 이 요청 파라미터로 바인딩하는 것)입니다.

```kotlin
@GetMapping("/api/v1/products/{id}")
fun get(@PathVariable id: Long, @RequestParam(required = false) status: ProductStatus?) = ...

// 테스트: ofType 없이
pathParameters("id" pathMeans "상품 ID")                                   // integer, int64
queryParameters("status" queryMeans "상태" isOptional true)                // string + enum [SALE, SOLD_OUT, ...]
```

| 컨트롤러 인자 | OpenAPI |
|---|---|
| `Long` | `integer`(`int64`) |
| `LocalDate` | `string`(`date`) |
| Enum | `string` + 값 목록(`enum`) |
| 쿼리·폼의 `List<Long>` | `array` (`items: {type: integer, format: int64}`) |

- 규칙은 [타입과 형식](#타입과-형식) 표와 같습니다. 파라미터 하나로 나타낼 수 없는 타입(객체)은 그대로 `string` 입니다. 경로 변수는 배열을 쓰지 않습니다.
- `ofType`·`enumOf` 를 적으면 그것이 우선합니다. 핸들러와 다른 타입으로 문서화할 때나 핸들러를 모를 때 씁니다.
- `parametersOf<T>()` 로 추출한 파라미터는 추출한 타입을 그대로 씁니다.
- 핸들러는 MockMvc 와, 핸들러를 기록하는 WebTestClient(`restDocsWebTestClient`, `recordHandlers()`)에서만 알 수 있습니다. 함수형 라우터(`RouterFunction`), `bindToServer`, RestTestClient 는 핸들러를 모르므로 `string` 입니다.
- AsciiDoc 표는 바뀌지 않습니다. OpenAPI 타입만 정합니다.
- Java 컨트롤러는 `-parameters` 로 컴파일해야 이름을 적지 않은 `@RequestParam`·`@PathVariable` 인자의 이름을 찾습니다 (Spring Boot Gradle 플러그인은 기본으로 켭니다). 이름을 찾지 못한 인자는 건너뜁니다.

URL 템플릿의 경로 변수를 블록에서 문서화하지 않으면, OpenAPI 를 만들 때 테스트마다 한 번 경고가 나옵니다. 문서화하지 않은 경로 변수는 OpenAPI 에 설명 없이 나가므로, 경고에 나온 줄을 블록에 붙여 넣습니다.

```
kardx[KDX-204]: GET /api/v1/products/{id} in ProductApiDocsTest.getProduct does not document path variable(s) id, so OpenAPI shows them without description. Add to the andDocument block: pathParameters("id" pathMeans "...")
```

### deprecated API

더 이상 쓰지 않을 API 는 블록에서 표시합니다. `api-summary` 스니펫에 `*Deprecated*: <이유>` 줄이 생기고(설명보다 앞), OpenAPI operation 에 `deprecated: true` 가 붙습니다.

```kotlin
.andDocument {
    deprecated("GET /api/v2/users 를 쓰세요")   // 이유와 함께
    // deprecated = true                        // 이유 없이
}
```

- `openApi { deprecated = true }` 와 같은 값입니다. 어느 쪽에서 바꿔도 같습니다.
- 정하지 않으면 컨트롤러 메서드나 컨트롤러 클래스의 `@Deprecated`(Kotlin·Java)를 따릅니다. Kotlin `@Deprecated` 의 `message` 가 이유가 되고, Java `@Deprecated` 는 이유 없이 표시합니다. 핸들러를 모르는 요청은 따르지 않습니다(위 절과 같은 조건).
- 핸들러가 `@Deprecated` 여도 문서에 표시하지 않으려면 `deprecated = false` 로 끕니다.
- `api-summary` 템플릿을 고쳐 쓰면, 모델 키 `deprecated`(true)·`deprecationNote` 는 deprecated 일 때만 들어갑니다 ([라이브러리가 제공하는 템플릿](customizing-snippets.md#라이브러리가-제공하는-템플릿)).
- 필드·파라미터 하나만 표시할 때는 [더 이상 쓰지 않는 필드](#더-이상-쓰지-않는-필드)를 봅니다.

```kotlin
@Deprecated("다음 버전의 숨김 처리 API 를 쓰세요")
@DeleteMapping("/{id}")
fun delete(@PathVariable id: Long): ResponseEntity<Unit> = ...   // 블록에 쓰지 않아도 deprecated 로 나간다
```

### 느슨한 검증

문서화한 항목은 기본으로 엄격하게 검증합니다. 실제 요청·응답에 문서화하지 않은 것이 있으면 테스트가 실패합니다. 일부 항목에서만 문서화하지 않은 것을 허용하려면 그 항목을 `relaxed(...)` 에 적습니다 (`import io.github.kardx.dsl.RelaxedSection`).

```kotlin
.andDocument {
    queryParameters<ProductSearchCondition>()
    responseFields<ProductResponse>()
    relaxed(RelaxedSection.RESPONSE_FIELDS)   // 응답 필드만 느슨하게. 쿼리 파라미터는 그대로 엄격하게
}
```

| `RelaxedSection` | 항목 |
|---|---|
| `PATH_PARAMETERS`, `QUERY_PARAMETERS`, `FORM_PARAMETERS` | `path-parameters`, `query-parameters`, `form-parameters` |
| `REQUEST_FIELDS`, `RESPONSE_FIELDS` | `request-fields`, `response-fields` |
| `REQUEST_PARTS`, `REQUEST_PART_FIELDS` | `request-parts`, `request-part-<파트>-fields` (모든 파트) |
| `REQUEST_COOKIES`, `RESPONSE_COOKIES` | `request-cookies`, `response-cookies` |
| `LINKS` | `links` |
| `EVENTS` | `sse-events` 와 이벤트 `data` 필드: 문서화하지 않은 이벤트 이름과 필드를 허용 |

- 헤더는 항목이 없습니다. 헤더 검증은 원래 문서화하지 않은 헤더를 허용하기 때문입니다 (문서화한 헤더가 있는지만 봅니다).
- relaxed 인 항목도 문서화한 필수 항목이 없으면 실패합니다.
- `relaxed(...)` 를 다시 부르면 항목이 더해집니다. `relaxed = true` 는 모든 항목을 느슨하게, `relaxed = false` 는 모든 항목을 다시 엄격하게 합니다 (`relaxed(...)` 로 더한 것도).
- `REQUEST_FIELDS`·`RESPONSE_FIELDS` 를 relaxed 로 하면 그 본문의 OpenAPI 스키마에 필드 설명이 들어가지 않습니다. epages 에 relaxed 검증이 없기 때문입니다.

`relaxed = true` 는 경로·쿼리·폼 파라미터, 파트, 쿠키, 링크까지 **모든** 항목을 느슨하게 합니다. 본문만 느슨하게 하고 파라미터는 계속 검증하려면 `relaxed(RelaxedSection.REQUEST_FIELDS, RelaxedSection.RESPONSE_FIELDS)` 로 바꿉니다.

## 필드 자동 추출

`andDocument { }` 블록에서는 타입 인자를 준 단축형으로 바로 등록합니다.

| 블록 안 단축형 | 같은 뜻 | 용도 |
|---|---|---|
| `responseFields<T>(path = "")` | `responseFields(responseFieldsOf<T>(path))` | 응답 본문 필드 |
| `requestFields<T>(path = "")` | `requestFields(requestFieldsOf<T>(path))` | 요청 본문 필드. 기본값이 있는 생성자 파라미터도 optional |
| `wrappedResponseFields<T>(dataPath, dataOptional)` | `responseFields(wrappedResponseFieldsOf<T>(...))` | 공통 응답 래퍼 + 데이터 필드 ([공통 응답 래퍼](#공통-응답-래퍼)) |
| `pathParameters<T>(path = "")` | `pathParameters(parametersOf<T>(path))` | 경로 파라미터. 프로퍼티 이름이 URI 템플릿 변수(`{id}`)와 같아야 한다 |
| `queryParameters<T>(path = "")` · `formParameters<T>(path = "")` | `queryParameters(parametersOf<T>(path))` · `formParameters(parametersOf<T>(path))` | 쿼리·폼 파라미터. 중첩 객체는 `a.b`, 배열은 이름 하나 |
| `requestPartFields<T>(part, path = "")` | `requestPartFields(part, requestFieldsOf<T>(path))` | 멀티파트 파트의 본문 필드 |

```kotlin
responseFields<UserResponse>()            // id, nickname, status
responseFields<UserResponse>("data")      // data.id, data.nickname, ...
responseFields<List<UserResponse>>()      // [].id, [].nickname, ...
responseFields<Page<UserResponse>>()      // Spring Data Page: content[], totalElements, ... (아래 절)
queryParameters<UserSearchCondition>()
```

단축형은 `io.github.kardx.extractor` 패키지의 확장 함수입니다 (`import io.github.kardx.extractor.responseFields`). 타입 인자 없이 부르면(`responseFields("id" type NUMBER means "ID")`, `queryParameters()`) 보통의 직접 쓴 목록·가변 인자 버전입니다.

목록을 고치거나 합칠 때는 목록을 돌려주는 `…Of` 함수를 씁니다. `responseFieldsOf<T>(path)`, `requestFieldsOf<T>(path)`, `parametersOf<T>(path)`, `wrappedResponseFieldsOf<T>(...)` 는 `KClass`·`KType` 을 받는 형태도 있습니다 (`responseFieldsOf(UserResponse::class)`).

```kotlin
responseFields(responseFieldsOf<UserResponse>().except("internalNote"))            // 그 필드와 하위 필드를 뺀다
responseFields(responseFieldsOf<UserResponse>().optionalAt("nickname", "profile.bio")) // 그 필드만 optional
responseFields(responseFieldsOf<OrderResponse>().optionalUnder("items[]"))         // 그 경로와 하위 필드 모두 optional
queryParameters(parametersOf<UserSearchCondition>().except("debug"))               // 그 이름의 파라미터를 뺀다
queryParameters(parametersOf<UserSearchCondition>().optionalAt("keyword"))         // 그 파라미터만 optional
queryParameters(parametersOf<UserSearchCondition>() + ("debug" queryMeans "디버그 출력").optional())
```

| 함수 | 받는 목록 | 하는 일 |
|---|---|---|
| `except(vararg paths)` | `List<RestDocsField>` | 그 경로의 필드와 하위 필드(`path.x`, `path[]`, `path[].x`)를 뺀다 |
| `optionalAt(vararg paths)` | `List<RestDocsField>` | 그 경로의 필드만 optional 로 바꾼다. 하위 필드는 그대로 |
| `optionalUnder(vararg paths)` | `List<RestDocsField>` | 그 경로의 필드와 하위 필드를 모두 optional 로 바꾼다. `"items[]"` 는 배열 `items` 는 필수로 두고 원소 필드만 optional 로 한다 (테스트 데이터의 배열이 비었을 때) |
| `except(vararg names)` | `List<RestDocsParam>` | 그 이름의 파라미터를 뺀다 |
| `optionalAt(vararg names)` | `List<RestDocsParam>` | 그 이름의 파라미터를 optional 로 바꾼다 (표의 Required `false`, OpenAPI `required: false`) |

모두 `io.github.kardx.extractor` 패키지에 있고, 원래 목록을 바꾸지 않고 새 목록을 돌려주며, 목록에 없는 경로·이름을 주면 실패합니다(오타로 필드가 남지 않게). 파라미터는 `name` 으로도 고를 수 있습니다 (`parametersOf<T>().filter { it.name != "debug" }`). 추출 결과의 OpenAPI 스키마 이름 기본값(`UserResponse`)은 이렇게 고쳐도, 이어서 `+` 로 직접 쓴 필드를 더하거나 `filterNot` 으로 걸러도 유지됩니다. 다른 타입이나 다른 경로(`responseFieldsOf<X>("data")`)에서 추출한 목록을 섞으면 본문이 한 타입이 아니므로 자동 이름을 붙이지 않습니다. 그때는 `openApi { responseSchema<T>() }` 로 정합니다.

### 추출 규칙

- **프로퍼티**: Jackson 이 직렬화하는 public 프로퍼티를 주 생성자 순서로 뽑습니다 (생성자 밖 프로퍼티는 선언 순서). Java record 는 component, Java bean 은 getter 를 씁니다 (Jackson 2 면 `getURL` → `url`, Jackson 3 면 `URL`).
- **요청과 응답**: 요청(`requestFieldsOf`, `parametersOf`)에서는 받을 수 없는 프로퍼티 — 생성자 밖의 읽기 전용 `val`, `@JsonProperty(access = READ_ONLY)` — 를 빼고, 생성자 밖 `var` 는 optional 입니다. 응답에서는 `access = WRITE_ONLY`(비밀번호 등)를 뺍니다.
- **이름**: Jackson 과 같은 순서로 정합니다. `@JsonProperty` 가 먼저이고, 다음은 클래스(또는 상위 클래스)의 `@JsonNaming`, 마지막은 전역 규칙입니다. 전역 규칙은 `extraction.propertyNameStrategy` 설정이 있으면 그것을, 없으면 매퍼의 `PropertyNamingStrategy`(예: `spring.jackson.property-naming-strategy=SNAKE_CASE`)를 씁니다. `@JsonIgnore` 와 클래스의 `@JsonIgnoreProperties` 는 뺍니다. `parametersOf<T>()` 는 Spring 의 파라미터 바인딩(`@ModelAttribute`)이 Jackson 을 거치지 않으므로 이름 규칙을 적용하지 않고 프로퍼티 이름 그대로 씁니다.
- **Jackson 구조 어노테이션**: `@JsonUnwrapped` 는 하위 필드를 부모 수준에 펼치고(`prefix`/`suffix` 반영), 클래스의 `@JsonValue` 는 그 값의 타입으로 바꿉니다. sealed 타입의 `@JsonTypeInfo` 는 `include = PROPERTY` 면 판별자 필드(`type`, 값 목록은 `@JsonSubTypes` 이름)를 더하고, `EXISTING_PROPERTY` 면 그 프로퍼티에 값 목록을 붙이고, `WRAPPER_OBJECT` 면 하위 타입 이름이 키인 객체(`vehicle.car.doors`, 모두 optional)로 만듭니다. 프로퍼티의 `@JsonFormat(shape = NUMBER / STRING / BOOLEAN)` 은 필드 타입을 바꾸고(`Instant` → Number, `BigDecimal` → String), `@JsonAnyGetter` 맵은 필드가 아니라 부모 객체에 펼쳐지는 항목으로 봅니다(아래 표).
- **Java 의 null**: Java record·bean 은 타입에 null 여부가 없으므로 어노테이션으로 정합니다. 원시 타입은 필수, `@Nullable`·`@CheckForNull` 은 optional, `@NonNull`·`@Nonnull`·`@NotNull` 은 필수, 클래스·바깥 클래스·패키지에 `@NullMarked`(jspecify)나 `@NonNullApi`(Spring)가 있으면 어노테이션 없는 타입은 필수입니다. 그 밖의 참조 타입은 optional 입니다 (springdoc 처럼). 어노테이션은 패키지와 상관없이 이름으로 봅니다.
- **설명**: 기본은 `@Description` 값이고, 없으면 `No description` 입니다. `@Description` 은 아래 어느 위치에 붙여도 읽습니다. Swagger `@Schema` 같은 다른 어노테이션도 읽으려면 [다른 어노테이션에서 설명 가져오기](#다른-어노테이션에서-설명-가져오기)를 보세요. Gradle 플러그인의 실험 기능인 source docs 를 켜면 어노테이션이 설명하지 않은 곳은 KDoc/Javadoc 이 채웁니다 ([KDoc/Javadoc 에서 설명 가져오기](#kdocjavadoc-에서-설명-가져오기)).
- **optional**: nullable 과 `Optional<T>` 는 optional 이고, 그 하위 필드도 모두 optional 입니다.
- **제약조건**: 프로퍼티·필드(`@field:`)·getter·생성자 파라미터 어디에 붙어도 찾습니다. `message` 가 있으면 `{min}` 같은 자리를 채워 쓰고, 없으면 REST Docs 기본 문구를 씁니다. 원소 제약(`List<@NotBlank String>`)은 `원소: NotBlank - ...`(`language = "ko"`. 기본 `en` 은 `Items:`) 로 붙고, 검증 그룹이 있으면 이름 뒤에 그룹을 적습니다 (`NotNull [OnCreate] - ...`). nullable 이거나 기본값이 있는 프로퍼티에 `@NotNull`·`@NotBlank`·`@NotEmpty`(기본 그룹)가 있으면 Required 열은 `true` 지만, REST Docs 검증은 optional 그대로라 값을 빼거나 `null` 을 보내는 검증 실패 테스트도 통과합니다. OpenAPI 에는 키워드로 나갑니다 ([제약조건](#제약조건)).
- **Enum**: 설명 뒤에 값 목록(`ACTIVE : 활성`)을 붙이고 OpenAPI `enum` 으로 내보냅니다. 값은 JSON 에 실제로 나가는 값입니다 (`@JsonValue` 메서드, 상수의 `@JsonProperty`, 없으면 상수 이름). 상수 설명은 `description` 필드에서 읽습니다.

| 타입 | 결과 |
|---|---|
| 객체 `address: Address` | `address` (Object) + `address.street` … |
| 배열 `boxes: List<Box>` | `boxes` (Array) + `boxes[].width` …. 원소가 nullable(`List<Box?>`)이면 하위 필드는 optional 이고, 실제 `null` 원소도 통과한다 (표에 나오지 않는 ignored 줄 `boxes[]`) |
| Enum 배열 `levels: List<Level>` | `levels` (Array) 한 줄. OpenAPI·AsyncAPI 는 `type: array`, `items.enum` |
| 중첩 배열 `List<List<Box>>` | `cells` + `cells[][].width` … |
| 값 배열 `List<String>` | `tags` (Array) 한 줄 |
| `Map`, 자기 참조, 최대 깊이 초과 | subsection 한 줄 (하위 필드는 검증하지 않음) |
| `JsonNode` 등 Jackson 트리 타입, `Any`, 풀리지 않은 타입 인자 | 구조를 알 수 없으므로 VARIES subsection 한 줄. 배열 원소면 배열이 subsection |
| 인터페이스·추상 클래스 `pet: Pet`, `@JsonAnyGetter` 가 있는 클래스 | `pet` (Object **subsection**) + 선언 타입의 프로퍼티 `pet.name`. 구현 클래스에만 있는 필드(`pet.age`)·펼쳐진 키가 있어도 통과한다. 배열이면 배열이 subsection |
| Kotlin `object` | 프로퍼티가 있으면 일반 객체처럼 펼친다. 없으면 빈 객체 한 줄 |
| Spring Data `Page`·`Slice`·`Sort`, `ProblemDetail` | Jackson 이 실제로 내보내는 모양 ([Spring Data 페이지](#spring-data-페이지), [지원하지 않는 모양](#지원하지-않는-모양)) |
| `Pair`, `Triple` | `first`, `second`(, `third`) 객체 |
| Spring HATEOAS `EntityModel`·`CollectionModel`·`PagedModel`, `RepresentationModel` 하위 클래스 | HAL 모양: `_links`, `_embedded.<관계>[]`, `page.*` ([Spring HATEOAS](#spring-hateoas)) |
| kotlinx `@Serializable` 클래스 | serial descriptor 기준: `@SerialName` 이름, 기본값은 optional ([kotlinx.serialization](#kotlinxserialization)) |
| sealed 타입 | 하위 타입 필드의 합집합. 일부 하위 타입에만 있으면 optional. OpenAPI 에서는 하위 타입마다 `oneOf` 의 한 변형이 된다 ([sealed 타입과 oneOf](#sealed-타입과-oneof)) |
| value class, `Optional<T>` | 안쪽 값 한 줄 |
| 최상위 `List<User>` | `[].id` …. 경로 `"data"` 를 주면 `data` (Array) + `data[].id` |
| 최상위 `String`, `Map`, `List<String>` | 경로가 있을 때만 한 줄. 없으면 빈 목록이고 경고 로그를 남긴다 (`found nothing to document`) |

| Kotlin·JDK 타입 | 문서 타입 |
|---|---|
| `String`, `Char`, `ByteArray`, `UUID`, `URI`, `URL`, `Path`, `File`, `InetAddress`, `ByteBuffer`, `Class`, `Locale`, `Date`·`Calendar`(`Timestamp` 같은 하위 타입 포함), `java.time.*` | String |
| `Int`, `Long`, `BigDecimal` 등 `Number` | Number |
| `Boolean` | Boolean |
| Enum | String (상수 목록 포함) |
| 배열, `Collection` | Array |
| `Map`, data class, 일반 클래스 | Object |
| `Any`, 타입 인자 없는 제네릭, 그 외 JDK 클래스 | Varies (`Any`·타입 인자는 subsection) |

| `@Description` 위치 | 예 |
|---|---|
| Kotlin 생성자 프로퍼티 (위치 생략) | `@Description("ID") val id: Long` |
| 프로퍼티, 백킹 필드 | `@property:Description(...)`, `@field:Description(...)` |
| getter, setter | `@get:Description(...)`, `@set:Description(...)` (`var`) |
| 클래스 본문 프로퍼티 | `@Description("...") val total: Int = 0` |
| Java 필드, getter, setter | `@Description("ID") private Long id;` |
| Java record component | `record User(@Description("ID") Long id)` |

생성자 파라미터 전용 위치(`@param:`)는 지원하지 않습니다. 지원하면 Kotlin 2.2 이상에서 위치를 생략한 모든 프로퍼티에 컴파일 경고가 납니다. 위치를 생략하면 프로퍼티에 붙으므로 기능 차이는 없습니다.

### 빈 추출 결과

추출한 필드·파라미터가 없으면(값 하나인 `String`·`Long` 본문, 경로 없는 `Map`, 프로퍼티 없는 클래스) `kardx[KDX-107]: responseFieldsOf<...>() found nothing to document` 경고 로그를 남깁니다. 빈 필드 목록은 표를 만들지 않으므로 본문은 `http-response` 예시에만 나옵니다. 값 하나인 본문에 필드 표가 필요하면 직접 기술하세요.

`pathParameters`·`queryParameters`·`formParameters` 는 빈 목록을 넘겨도 표를 만듭니다. 그래서 `parametersOf<T>()` 가 비어 있는데 요청에 파라미터가 있으면 REST Docs 가 `Query parameters with the following names were not documented: [sort]` 로 실패해 빠진 것을 알려 줍니다.

### Spring Data 페이지

Spring Data 의 `Page`·`Slice`·`Sort` 는 `Iterable` 이지만 Jackson 은 객체로 내보냅니다. 추출기는 Jackson 이 실제로 쓰는 모양으로 추출합니다 (spring-data 에 의존하지 않고 클래스 이름으로 판별). Boot 3(Spring Data 3.5)과 Boot 4(Spring Data 4.1)의 모양은 같습니다 (키 순서만 다름).

| 타입 | 추출 결과 |
|---|---|
| `Page<T>`, `PageImpl<T>` | `content[]`, `pageable`(`pageNumber`, `pageSize`, `sort.*`, `offset`, `paged`, `unpaged`), `totalElements`, `totalPages`, `last`, `first`, `size`, `number`, `sort`(`empty`, `sorted`, `unsorted`), `numberOfElements`, `empty` |
| `Page<T>` + `VIA_DTO` 모드 | `content[]`, `page.size`, `page.number`, `page.totalElements`, `page.totalPages` (Spring Data `PagedModel` 과 같은 모양) |
| `Slice<T>` | `Page` 에서 `totalElements`, `totalPages` 를 뺀 모양 |
| `PagedModel<T>` (`org.springframework.data.web`) | `content[]`, `page.*`. Spring HATEOAS `PagedModel` 은 HAL 모양으로 추출합니다 ([Spring HATEOAS](#spring-hateoas)) |
| `parametersOf<Pageable>()` | 쿼리 파라미터 `page`, `size`, `sort` (모두 optional) |
| `parametersOf<Sort>()` | `sort` |

```kotlin
mockMvc.perform(
    get("/api/articles").param("page", "0")
        .param("sort", "id,desc"),
).andDocument {
    queryParameters<Pageable>()
    responseFields<Page<ArticleResponse>>()
}
```

- `VIA_DTO` 는 `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` 나 `spring.data.web.pageable.serialization-mode=via-dto` 일 때 Spring Data 가 쓰는 모양입니다. 매퍼에 `PageImpl` 용 mixin 이 등록됐는지로 판별하므로 `restDocsMockMvc`(앱 매퍼 사용)로 문서화하면 자동으로 맞습니다.
- 파라미터 이름은 Spring Data 기본값입니다. `spring.data.web.pageable.page-parameter`, `one-indexed-parameters`, `@Qualifier` 접두사로 바꿨다면 직접 기술하세요.
- 페이지 없이 만든 `PageImpl(list)` 는 Jackson 이 직렬화하지 못합니다 (`Unpaged` 의 `getOffset` 이 예외). 실제 앱에서도 실패하므로 `PageRequest` 를 넘기세요.
- 공개 API 응답에는 Spring Data 도 권장하는 Spring Data `PagedModel` 이나 직접 만든 페이지 DTO 가 더 안정적입니다. 자체 `PageResponse<T>` 는 일반 클래스처럼 추출합니다.

### Spring HATEOAS

Spring HATEOAS 모델은 HAL(`application/hal+json`, Spring HATEOAS 기본값) 모양으로 추출합니다. 클래스 이름으로 판별하므로 라이브러리가 spring-hateoas 에 의존하지 않습니다.

| 타입 | 추출 결과 |
|---|---|
| `EntityModel<T>` | `T` 의 필드가 같은 수준에 + `_links` |
| `RepresentationModel` 을 상속한 클래스 | 자기 필드 + `_links` (`links` 프로퍼티 대신) |
| `CollectionModel<T>` | `_embedded.<관계>[]`(원소) + `_links` |
| `PagedModel<T>` (`org.springframework.hateoas`) | `_embedded.<관계>[]` + `_links` + `page.size`, `page.totalElements`, `page.totalPages`, `page.number` |

```kotlin
@Relation(collectionRelation = "books")
class BookResponse(val id: Long, val title: String)

mockMvc.perform(get("/api/books").param("page", "0"))
    .andDocument {
        responseFields<PagedModel<EntityModel<BookResponse>>>()
        // _embedded, _embedded.books[], _embedded.books[].id, _embedded.books[].title, _embedded.books[]._links,
        // _links, page, page.size, page.totalElements, page.totalPages, page.number
    }
```

- `_links` 는 관계 이름이 키인 subsection 입니다. 어떤 링크가 오는지는 타입으로 알 수 없어 그 아래는 검증하지 않습니다. `_links`·`_embedded` 는 비면 Spring HATEOAS 가 빼므로 optional 입니다.
- 컬렉션의 관계 이름(`<관계>`)은 Spring HATEOAS 와 같은 순서로 정합니다: 원소 클래스(`EntityModel<T>` 면 `T`)의 `@Relation(collectionRelation)`, 없으면 evo-inflector 가 클래스패스에 있을 때 복수형(`books`), 그것도 없으면 `<타입 이름>List`(`bookResponseList`).
- HAL 키(`_links`, `_embedded`, `page.*`)에는 이름 규칙(`@JsonNaming`, `extraction.propertyNameStrategy`)을 적용하지 않습니다. 직접 만든 클래스의 필드에는 적용합니다.
- 한계: HAL 만 지원합니다(HAL-FORMS, Collection+JSON, UBER 아님). 원소 타입이 섞인 컬렉션은 지원하지 않고, `CollectionModel<*>`(원소 타입을 모름)는 `_embedded` 가 subsection 입니다.
- Spring Data 의 `org.springframework.data.web.PagedModel` 은 모양이 다른(`content[]`, `page.*`) 별개 클래스입니다: [Spring Data 페이지](#spring-data-페이지).

### kotlinx.serialization

kotlinx-serialization-json 이 테스트 클래스패스에 있으면 Spring MVC·WebFlux 는 `@Serializable` 클래스를 Jackson 대신 kotlinx 로 직렬화합니다. 추출기도 똑같이 그런 클래스를 serial descriptor 로 읽습니다.

```kotlin
@Serializable
class UserResponse(
    @SerialName("user_id") val id: Long,
    val nickname: String? = null,    // 기본값이 있으므로 optional
    @Transient val cache: String = "", // 빠진다
)
```

- 이름은 `@SerialName` 을 따르고, `@Transient` 프로퍼티와 백킹 필드 없는 프로퍼티는 뺍니다. 이런 클래스에는 Jackson 어노테이션과 이름 규칙을 쓰지 않습니다.
- 기본값이 있는 프로퍼티는 요청과 응답 모두 optional 입니다(`Json` 기본값 `encodeDefaults = false` 면 응답에 쓰지 않음). `@EncodeDefault` 를 붙이면 응답에서 필수입니다.
- 값 하나를 쓰는 직렬화기(primitive descriptor 를 쓰는 `@Serializable(with = ...)`)는 그 값의 타입(String, Number, Boolean)으로 문서화합니다.
- sealed 타입에는 판별자 `type`(또는 `@JsonClassDiscriminator` 로 정한 이름)이 붙고 하위 타입의 serial 이름이 값이 됩니다. OpenAPI `oneOf` 는 Jackson 과 같습니다 ([sealed 타입과 oneOf](#sealed-타입과-oneof)).
- 기본으로 켜져 있습니다. 앱이 `@Serializable` 클래스도 Jackson 으로 직렬화하면 `extraction.kotlinxSerialization = false`(yml `kardx.kotlinx-serialization: false`, [설정](configuration.md#yml-설정))로 끕니다.
- 한계: 앱의 `Json { }` 인스턴스 설정(`namingStrategy`, `classDiscriminator`, `encodeDefaults`)은 추출기가 알 수 없습니다. 바꿨다면 목록을 고치거나(`except`, `optionalAt`) 필드를 직접 씁니다.

### 지원하지 않는 모양

추출기는 타입만 보므로 직렬화를 바꾸는 아래 기능은 따라가지 못합니다. REST Docs 가 실제 응답으로 검증하므로 문서가 틀리게 나가지는 않고 테스트가 실패합니다. 우회법을 쓰세요.

| 기능 | 증상 | 우회 |
|---|---|---|
| `@JsonView` | 뷰에서 빠진 필드가 `not found in the payload` | `responseFieldsOf<T>().except("secret")` |
| `@JsonFormat(shape = OBJECT / ARRAY)` (Enum 을 객체로 등) | 하위 키가 미문서화 | `"tier" subsection OBJECT means "..."` 로 바꾸거나 직접 기술 |
| `@JsonTypeInfo(include = WRAPPER_ARRAY)` | `["car", {...}]` 를 경로로 표현할 수 없다 | `relaxed = true` 와 직접 기술 |
| `@JsonSerialize(using = ...)`, 커스텀 Serializer | 모양이 다르다 | 직접 기술 |
| `@JsonInclude(NON_EMPTY)` 의 빈 컬렉션 | `not found in the payload` | `responseFieldsOf<T>().optionalAt("tags")` |
| `@JsonAlias` | 별칭은 역직렬화(요청)에만 쓰이므로 문서에는 정식 이름이 나온다. 테스트가 별칭으로 보내면 실패한다 | 테스트 요청은 정식 이름으로 보내거나 별칭 필드를 직접 기술 |
| 최상위 인터페이스·`@JsonAnyGetter` 타입 (`responseFieldsOf<Pet>()`), 최상위 `ProblemDetail` 의 확장 항목 | 구현체 필드·확장 키가 미문서화 (최상위는 subsection 으로 둘 수 없다) | 구현 클래스로 추출하거나 `relaxed = true` |

`ProblemDetail` 은 RFC 9457 필드 `type`, `title`, `status`, `detail`, `instance` 를 모두 optional 로 추출합니다. Spring 의 Jackson mixin 이 빈 값을 빼고(Spring 7 은 기본 `type` 인 `about:blank` 도 뺀다) `properties` 맵을 부모 객체에 펼치기 때문입니다. 다른 객체 안의 `ProblemDetail` 은 subsection 이라 확장 항목도 통과합니다.

### 다른 어노테이션에서 설명 가져오기

DTO 에 이미 Swagger `@Schema` 나 Jackson `@JsonPropertyDescription` 으로 설명을 달아 두었다면 `@Description` 을 다시 붙이지 않아도 됩니다. 그 어노테이션을 읽도록 설정하면 문서에 같은 설명이 나옵니다. 직접 만든 어노테이션도 읽을 수 있습니다.

가장 짧은 시작은 테스트 설정 yml 한 줄입니다.

```yaml
kardx:
  description-annotations: swagger
```

이제 `@Schema(description = "...")` 값이 필드 설명이 됩니다. `@Description` 이 붙은 필드는 계속 `@Description` 을 씁니다.

#### 설정 전과 후

`com.example` 패키지의 DTO 입니다. 필드마다 다른 어노테이션으로 설명을 달았습니다.

```kotlin
package com.example

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import io.github.kardx.annotation.Description
import io.swagger.v3.oas.annotations.media.Schema

annotation class ApiField(val text: String)            // 직접 만든 어노테이션

data class ProductResponse(
    @Description("상품 ID") val id: Long,
    @Schema(description = "상품 이름") val name: String,
    @JsonPropertyDescription("판매 가격(원)") val price: Long,
    @ApiField(text = "재고 수량") val stock: Int,
    val memo: String?,
)
```

테스트에서는 `responseFields<ProductResponse>()` 로 문서화합니다. 아래 표는 만들어진 `response-fields` 표의 Path, Type, Description 열입니다.

**설정 전** (`description-annotations` 없음): `@Description` 만 읽습니다.

| Path | Type | Description |
|---|---|---|
| `id` | `Number` | 상품 ID |
| `name` | `String` | No description |
| `price` | `Number` | No description |
| `stock` | `Number` | No description |
| `memo` | `String` | No description |

**설정 후**:

```yaml
kardx:
  description-annotations: swagger, jackson, com.example.ApiField#text
```

| Path | Type | Description | 설명을 읽은 곳 |
|---|---|---|---|
| `id` | `Number` | 상품 ID | `@Description` |
| `name` | `String` | 상품 이름 | `swagger` → `@Schema` 의 `description` |
| `price` | `Number` | 판매 가격(원) | `jackson` → `@JsonPropertyDescription` 의 `value` |
| `stock` | `Number` | 재고 수량 | `com.example.ApiField#text` |
| `memo` | `String` | No description | 어디에도 없음 |

마지막 열은 설명을 위해 덧붙인 것으로, 실제 문서에는 없습니다.

#### 항목 쓰는 법

목록의 항목 하나가 "어떤 어노테이션의 어떤 속성을 읽을지" 입니다. 다섯 가지로 씁니다.

| 항목 | 읽는 값 | 예 |
|---|---|---|
| `swagger` | `io.swagger.v3.oas.annotations.media.Schema` 의 `description` | `@Schema(description = "상품 이름")` |
| `jackson` | `com.fasterxml.jackson.annotation.JsonPropertyDescription` 의 `value` | `@JsonPropertyDescription("판매 가격(원)")` |
| `별칭#attribute` | 별칭이 가리키는 어노테이션의 `attribute` 속성 | `swagger#title` → `@Schema(title = "상품 이름")` |
| `FQCN` | 그 어노테이션의 `value` 속성. 없으면 `description` 속성 | `com.example.Doc` → `@Doc("상품 이름")` |
| `FQCN#attribute` | 그 어노테이션의 `attribute` 속성 | `com.example.ApiField#text` → `@ApiField(text = "재고 수량")` |

- 별칭(`swagger`, `jackson`)은 대소문자를 가리지 않습니다. 모든 항목의 앞뒤 공백과 `#` 앞뒤 공백은 무시합니다 (`swagger # title` 은 `swagger#title`).
- 별칭 뒤에도 `#속성` 을 붙일 수 있습니다. `swagger#title` 은 `io.swagger.v3.oas.annotations.media.Schema#title` 과 같습니다.
- 읽는 속성은 어노테이션이 선언한 `String` 속성이어야 합니다. 숫자나 배열 속성, `toString` 같은 상속 메서드는 오류입니다.
- 중첩 클래스로 선언한 어노테이션은 `$` 로 잇습니다 (`com.example.Docs$ApiField`).

목록은 쉼표로 이어 쓰거나 YAML 목록으로 씁니다. 둘은 같습니다.

```yaml
kardx:
  description-annotations: swagger, jackson, com.example.ApiField#text
```

```yaml
kardx:
  description-annotations:
    - swagger
    - jackson
    - com.example.ApiField#text
```

설정은 다른 `kardx.*` 설정과 같은 곳에 둡니다. 보통 테스트 설정 `src/test/resources/application-docs.yml` 입니다 ([시작하기](getting-started.md#5-application-docsyml-테스트)). Spring 테스트가 아니면 시스템 속성 `-Dkardx.description-annotations=swagger,jackson` 이나 환경 변수 `KARDX_DESCRIPTION_ANNOTATIONS=swagger,jackson` 으로 줍니다 ([yml 설정](configuration.md#yml-설정)).

#### 찾는 순서

필드마다 다음 순서로 찾고, 처음 찾은 값을 씁니다.

1. `@Description`
2. 목록의 첫 항목, 둘째 항목 … 적은 순서대로
3. KDoc/Javadoc, Gradle 플러그인에서 `sourceDocs { enabled = true }` 로 켰을 때 (실험 기능, 기본 꺼짐. [KDoc/Javadoc 에서 설명 가져오기](#kdocjavadoc-에서-설명-가져오기))
4. 어디에도 없으면 `No description`

빈 문자열이나 공백뿐인 값은 없는 것으로 보고 다음으로 넘어갑니다. `@Schema` 의 `description` 기본값이 `""` 이므로, `@Schema(title = "...")` 처럼 `description` 을 비운 필드는 다음 항목을 봅니다.

한 필드에 어노테이션이 여러 개 붙어 있으면 결과는 이렇습니다.

| 필드에 붙은 어노테이션 | 목록 `swagger, jackson` | 목록 `jackson, swagger` |
|---|---|---|
| `@Description("A") @Schema(description = "B")` | A | A |
| `@Description("  ") @Schema(description = "B")` | B | B |
| `@Schema(description = "B") @JsonPropertyDescription("C")` | B | C |
| `@Schema @JsonPropertyDescription("C")` | C | C |
| `@Schema(title = "T")` | No description | No description |

#### 상황별 예시

**Swagger 만 쓰는 프로젝트**. springdoc 을 쓰고 DTO 에 `@Schema` 를 달아 두었다면 별칭 하나면 됩니다.

```yaml
kardx:
  description-annotations: swagger
```

`description` 을 비우고 `title` 만 쓴 필드도 있다면 `title` 을 뒤에 더합니다. `description` 이 있으면 그것을, 비어 있으면 `title` 을 씁니다.

```yaml
kardx:
  description-annotations: swagger, swagger#title
```

**Jackson**. JSON Schema 생성 등을 위해 `@JsonPropertyDescription` 을 달아 두었다면 `jackson` 을 씁니다. Jackson 2 와 3 모두 이 어노테이션은 `com.fasterxml.jackson.annotation` 패키지에 있어서 설정이 같습니다.

```yaml
kardx:
  description-annotations: jackson
```

**직접 만든 어노테이션**. 설명을 담는 속성 이름이 `value` 나 `description` 이면 클래스 이름만 적습니다.

```kotlin
package com.example

annotation class Doc(val value: String)
```

```yaml
kardx:
  description-annotations: com.example.Doc
```

속성 이름이 다르면 `#` 뒤에 적습니다.

```kotlin
package com.example

annotation class ApiField(val text: String, val order: Int = 0)
```

```yaml
kardx:
  description-annotations: com.example.ApiField#text
```

Java 로 만든 어노테이션은 `@Retention(RetentionPolicy.RUNTIME)` 이 있어야 합니다. Java 의 기본값(`CLASS`)이면 실행 중에 읽을 수 없어 `No description` 이 됩니다. Kotlin 어노테이션은 기본이 `RUNTIME` 입니다.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface ApiField {
    String text();
}
```

**여러 개 섞기**. 모듈마다 쓰는 어노테이션이 다르면 모두 적습니다. 한 필드에 둘 이상 붙어 있을 때는 앞에 적은 것이 이깁니다.

```yaml
kardx:
  description-annotations:
    - swagger                       # 새 API
    - jackson                       # JSON Schema 를 만들던 옛 DTO
    - com.example.ApiField#text     # 사내 공통 어노테이션
```

**Java record·bean**. Java DTO 도 같은 설정으로 읽습니다. record 는 component 에, bean 은 필드나 getter 에 붙입니다.

```java
public record CodeResponse(@Schema(description = "코드") String code) {
}

public class LegacyResponse {
    @Schema(description = "필드에 붙인 설명")
    private String onField;

    private String onGetter;

    public String getOnField() {
        return onField;
    }

    @Schema(description = "getter 에 붙인 설명")
    public String getOnGetter() {
        return onGetter;
    }
}
```

**어노테이션 위치**. Kotlin 에서는 `@Description` 과 같은 규칙으로 찾습니다. 위치(use-site target)를 생략하든 지정하든 모두 읽습니다.

```kotlin
class ProductRequest(
    @Schema(description = "생성자 파라미터") val plain: String,     // Java 어노테이션은 생성자 파라미터에 붙는다
    @field:Schema(description = "백킹 필드") val onField: String,
    @get:Schema(description = "getter") val onGetter: String,
)
```

생성자 파라미터에만 붙을 수 있는 어노테이션(`@Target(AnnotationTarget.VALUE_PARAMETER)`)도 읽습니다.

**public 이 아닌 어노테이션**. Kotlin `private annotation class` 나 Java 의 package-private `@interface` 도 읽습니다.

**반복 가능한 어노테이션**. `@Repeatable` 어노테이션을 한 곳에 두 번 이상 붙이면 컨테이너 어노테이션으로 묶이는데, 이때도 안을 펼쳐 붙인 순서대로 보고 처음으로 비어 있지 않은 값을 씁니다.

```kotlin
@Repeatable
annotation class Note(val value: String)

class ProductResponse(
    @Note("") @Note("상품 이름") val name: String,   // "상품 이름"
)
```

#### 코드로 설정하기

yml 대신 `RestDocsConfiguration.configure { }` 나 `withSettings` 에서도 정할 수 있습니다.

| 코드 | 언제 쓰나 |
|---|---|
| `descriptions.annotation("swagger")` | yml 과 같은 문자열(별칭, `별칭#attribute`, `FQCN`, `FQCN#attribute`)을 그대로 쓸 때 |
| `descriptions.annotation(ApiField::class, "text")` | 클래스를 직접 가리켜 이름 오타를 컴파일 때 잡을 때. 속성을 생략하면 `value`, 없으면 `description` |
| `descriptions.from<ApiField> { it.text }` | 속성까지 코드로 읽을 때. 여러 속성을 조합할 수도 있습니다. 함수가 `null`·빈 값·공백뿐인 값을 돌려주면 다음 항목으로 넘어갑니다 |
| `descriptions.from(ApiField::class) { it.text }` | 위와 같고, 클래스를 값으로 넘길 때 (Java 나 제네릭 코드에서) |
| `descriptions.annotations = listOf(...)` | 목록을 통째로 바꿀 때. yml 목록도 무시합니다 |

앞의 세 함수는 목록 **끝에 더하고**, 대입은 목록을 **통째로 바꿉니다**.

```kotlin
RestDocsConfiguration.configure {
    descriptions {
        annotation("swagger")
        annotation(ApiField::class, "text")
        from<Schema> { it.description.ifBlank { it.title } }
    }
}
```

```kotlin
import io.github.kardx.config.DescriptionAnnotation

RestDocsConfiguration.configure {
    descriptions.annotations = listOf(
        DescriptionAnnotation.parse("jackson"),
        DescriptionAnnotation.of(ApiField::class, "text"),
        DescriptionAnnotation.from<Schema> { it.description },
    )
}
```

대입한 목록은 복사해 둡니다. 넘긴 `MutableList` 를 나중에 바꿔도 설정에는 반영되지 않습니다 (`masking.headers`, `document.commonIncludeFiles` 같은 다른 목록·집합 설정도 같습니다).

목록을 비우려면 `descriptions.annotations = emptyList()` 를 대입합니다. 그러면 `@Description` 만 읽습니다.

yml 과 코드를 함께 쓰면 이 순서로 목록이 만들어집니다.

1. yml 의 `description-annotations`
2. `configure { }` 에서 더한 항목
3. `withSettings` 에서 더한 항목 (그 블록 안에서만)

예를 들어 yml 이 `swagger`, `configure` 가 `descriptions.annotation("jackson")`, `withSettings` 가 `descriptions.annotation(ApiField::class, "text")` 이면 블록 안의 목록은 `swagger, jackson, ApiField#text` 이고, 블록 밖은 `swagger, jackson` 입니다. `reset()` 은 `configure` 로 더한 항목만 지우고 yml 목록은 남깁니다.

테스트 하나에서만 다른 어노테이션을 읽으려면 `withSettings` 로 감쌉니다.

```kotlin
@Test
fun getLegacyProduct() {
    RestDocsConfiguration.withSettings({ descriptions.annotation("jackson") }) {
        mockMvc
            .perform(get("/api/legacy/products/{id}", 1L))
            .andDocument { responseFields<LegacyProductResponse>() }
    }
}
```

#### 적용 범위

설정한 목록은 설명을 자동으로 만드는 곳 모두에 똑같이 적용됩니다.

- 요청·응답 본문 필드: `requestFieldsOf`, `responseFieldsOf`, `wrappedResponseFieldsOf` (래퍼의 `data` 줄 포함)
- 쿼리·경로·폼 파라미터: `parametersOf`
- WebSocket·STOMP·SSE 메시지 페이로드 ([메시지 문서화](messaging.md))
- 이 설명을 그대로 쓰는 OpenAPI·AsyncAPI 스키마

Enum 필드는 읽은 설명 뒤에 상수 목록이 붙는 것도 같습니다. 직접 기술한 항목(`"id" type NUMBER means "ID"`)에는 영향이 없습니다.

#### 오류와 해결

목록이 잘못되면 yml 을 읽는 시점(문서화 테스트가 시작될 때)에 속성 이름을 담은 `IllegalArgumentException` 으로 실패합니다. 코드(`descriptions.annotation(...)`)로 줬다면 그 줄에서 앞의 `kardx.description-annotations: ` 없이 같은 메시지로 실패합니다.

| 오류 메시지 | 원인 | 해결 |
|---|---|---|
| `kardx.description-annotations: class 'io.swagger.v3.oas.annotations.media.Schema' (alias 'swagger') not found` | 별칭이 가리키는 어노테이션이 클래스패스에 없습니다 | 아래 의존성을 추가합니다 |
| `kardx.description-annotations: class 'com.example.ApiField' not found` | 클래스 이름 오타, 클래스패스에 없음, 중첩 클래스를 `.` 로 이음 | 이름을 고치고, 중첩 클래스는 `com.example.Docs$ApiField` 로 씁니다 |
| `kardx.description-annotations: class 'com.example.ProductResponse' is not an annotation` | 어노테이션이 아닌 클래스를 적었습니다 | 어노테이션 클래스 이름을 적습니다 |
| `kardx.description-annotations: annotation 'com.example.ApiField' has no String attribute 'value' or 'description'` | 속성을 생략했는데 `value`·`description` 이라는 String 속성이 없습니다 | `com.example.ApiField#text` 처럼 속성을 적습니다 |
| `kardx.description-annotations: annotation 'com.example.ApiField' has no String attribute 'order'` | 그런 속성이 없거나 `String` 이 아닙니다 | String 속성 이름을 적습니다 |
| `kardx.description-annotations: 'com.example.ApiField#' has no attribute name after '#'` | `#` 뒤가 비었습니다 | 속성 이름을 적거나 `#` 을 지웁니다 |
| `kardx.description-annotations: '#text' has no annotation name` | `#` 앞(어노테이션 이름)이 비었습니다 | 별칭이나 FQCN 을 적습니다 |

`swagger`, `jackson` 별칭은 그 어노테이션이 테스트 클래스패스에 있어야 합니다. DTO 에 이미 붙여 쓰고 있다면 대개 들어 있습니다.

| 별칭 | 필요한 의존성 | 보통 들어오는 경로 |
|---|---|---|
| `swagger` | `io.swagger.core.v3:swagger-annotations-jakarta` (또는 `swagger-annotations`) | springdoc-openapi |
| `jackson` | `com.fasterxml.jackson.core:jackson-annotations` | Spring Boot 웹 스타터의 Jackson |

```kotlin
dependencies {
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.47")
}
```

오류는 없는데 설명이 `No description` 이면 다음을 확인합니다.

- 값이 비어 있지 않은지 (`@Schema` 는 `description` 을 적어야 합니다. `title` 은 읽지 않습니다)
- Java 어노테이션이 `@Retention(RetentionPolicy.RUNTIME)` 인지
- 직접 정한 `descriptions.resolver` 에 `DescriptionResolver.DEFAULT` 가 들어 있는지. 예: `myRule orElse DescriptionResolver.DEFAULT` ([설명 규칙 조합하기](#설명-규칙-조합하기))

#### 주의

- **공백뿐인 `@Description` 은 건너뜁니다.** `@Description("  ")` 은 설명이 없는 것으로 보고 목록의 다음 어노테이션을 봅니다. 다른 설명이 없으면 `No description` 입니다.
- **IDE 자동 완성**: IntelliJ 에서 `description-annotations` 값을 입력하면 `swagger`, `jackson` 을 제안합니다 (라이브러리 jar 의 Spring 설정 메타데이터).

### KDoc/Javadoc 에서 설명 가져오기

> **실험 기능이며 기본으로 꺼져 있습니다.** 주석은 Kotlin·Java 컴파일러가 아니라 가벼운 스캐너가 읽으므로 놓치는 선언이 있습니다 ([알려진 한계](#알려진-한계)). 설정과 결과는 아직 바뀔 수 있습니다.

DTO 와 컨트롤러에 KDoc·Javadoc 을 이미 달아 두었다면 그 주석도 설명으로 쓸 수 있습니다. Gradle 플러그인에서 `kardx { sourceDocs { enabled = true } }` 로 켭니다 ([켜기](#켜기)). 그러면 `apiDocsSourceDocs` 태스크가 `test` 전에 소스에서 주석을 읽고([Gradle 플러그인](build.md#gradle-플러그인)), 테스트는 어노테이션으로 설명을 찾지 못한 곳에 그 주석을 씁니다. 켜면 타입·프로퍼티·enum 상수 주석과 핸들러 메서드의 `@param` 태그를 씁니다. `@param` 은 테스트가 `pathParameters("id")` 처럼 이름만 적은 파라미터의 설명이 됩니다 ([이름만 적은 파라미터 → 메서드 `@param`](#이름만-적은-파라미터--메서드-param)). 컨트롤러 메서드 주석을 OpenAPI summary·description 으로 쓰려면 `methodDocs = true` 도 필요합니다 ([컨트롤러 메서드 → OpenAPI summary·description](#컨트롤러-메서드--openapi-summarydescription)).

```kotlin
/** 사용자. */
class UserResponse(
    /** 사용자 ID */
    val id: Long,
    val nickname: String,
    val status: UserStatus,
)

enum class UserStatus {
    /** 활성 사용자 */
    ACTIVE,

    /** 탈퇴한 사용자 */
    WITHDRAWN,
}

@RestController
class UserController(private val userService: UserService) {
    /**
     * 사용자를 조회한다. 탈퇴한 사용자는 404 를 돌려준다.
     *
     * @param id 조회할 [UserResponse.id]
     */
    @GetMapping("/api/users/{id}")
    fun getUser(@PathVariable id: Long): UserResponse = userService.getUser(id)
}
```

그러면 `responseFieldsOf<UserResponse>()` 는 `id` 를 `사용자 ID` 로 문서화하고, `status` 의 값 목록은 `ACTIVE : 활성 사용자`, `WITHDRAWN : 탈퇴한 사용자` 가 됩니다. `nickname` 은 주석이 없으니 그대로 `No description` 입니다. `methodDocs = true` 면 OpenAPI operation 의 summary 가 `사용자를 조회한다`(첫 문장에서 끝 마침표를 뺀 것), description 이 전체 문장이고, 전체 문장은 HTML 문서의 설명도 됩니다. 테스트가 `pathParameters("id")` 로 적으면 `id` 의 설명은 `@param id` 의 `조회할 UserResponse.id` 입니다.

샘플(`sourceDocs { enabled = true; methodDocs = true }` 로 기능을 켬)의 메모 API 는 설명 어노테이션도, 테스트에 적은 설명도 없이 KDoc 만으로 문서화합니다 (경로 변수는 `pathParameters("id")` 로 이름만 적고, 선택 쿼리 파라미터는 `"includeReminder" queryMeans FromParamTag isOptional true` 로 적습니다): [MemoController](../../samples/spring-boot-4/src/main/kotlin/com/example/restdocs/memo/MemoController.kt), [MemoApiDocsTest](../../samples/spring-boot-4/src/test/kotlin/com/example/restdocs/memo/MemoApiDocsTest.kt).

#### 켜기

이 기능은 Gradle 플러그인에서 설정합니다. `enabled` 가 기능을 켜고, 나머지 스위치는 켰을 때 무엇을 읽을지 고릅니다. Groovy DSL 도 이름이 같습니다.

```kotlin
kardx {
    sourceDocs {
        enabled = true                    // 기본값 false (실험 기능)
        fieldDocs = true                  // 기본값 true
        enumDocs = true                   // 기본값 true
        schemaDescriptions = true         // 기본값 true
        paramDocs = true                  // 기본값 true
        methodDocs = false                // 기본값 false
        includeProjectDependencies = true // 기본값 true
    }
}
```

| 스위치 | 기본값 | 쓰이는 곳 |
|---|---|---|
| `enabled` | `false` | 기능 전체. `false` 면 `test` 가 `apiDocsSourceDocs` 에 의존하지 않고 시스템 속성도 받지 않으며, `apiDocsSourceDocs` 를 직접 실행해도 건너뛰고(`SKIPPED`), OpenAPI 스키마에도 클래스 설명이 들어가지 않습니다 |
| `fieldDocs` | `true` | 프로퍼티·필드·getter·record 구성 요소 주석 → 필드 설명, `parametersOf<T>()`·`queryParameters<T>()` 의 파라미터 설명 |
| `enumDocs` | `true` | enum 상수 주석 → 값 목록과 `@EnumCode` 표 |
| `schemaDescriptions` | `true` | 클래스 주석 → OpenAPI 컴포넌트 스키마의 `description` |
| `paramDocs` | `true` | 핸들러 메서드 `@param` → 이름만 적거나 `FromParamTag` 로 적은 경로·쿼리·폼 파라미터, 요청 헤더, 파트 |
| `methodDocs` | `false` | 핸들러 메서드 주석 → OpenAPI summary·description 과 `api-summary`(HTML 문서)의 설명 |
| `includeProjectDependencies` | `true` | `project(...)` 의존성의 `main` 소스도 읽습니다 |

- 끈 항목은 `source-docs.json` 에서 빠지므로 테스트도 OpenAPI 단계도 그 항목을 보지 않습니다. 모든 스위치가 태스크 입력이라 하나를 바꾸면 스캔과 테스트가 다시 돕니다.
- `paramDocs = false` 면 이름만 적은 항목은 설명을 가져올 곳이 없어 테스트가 실패합니다 ([KDX-101](troubleshooting.md#kdx-101)).
- 테스트 쪽 차단 스위치는 테스트 yml 의 `kardx.source-docs.enabled: false`(`descriptions.sourceDocs`, 기본값 `true`)입니다. 이 값이 `false` 면 Gradle 이 파일을 넘겨도 테스트가 무시합니다. 이 값으로 기능을 켤 수는 없습니다. `sourceDocs { enabled = true }` 가 없으면 파일 자체가 없기 때문입니다. OpenAPI 스키마의 클래스 설명은 Gradle 플러그인이 넣으므로 이 값을 따르지 않습니다. 그쪽은 `schemaDescriptions = false` 로 끕니다 ([yml 설정](configuration.md#yml-설정)).

#### 우선순위

KDoc/Javadoc 은 마지막 출처입니다. 필드와 파라미터마다:

1. `@Description`
2. `description-annotations` 목록 ([다른 어노테이션에서 설명 가져오기](#다른-어노테이션에서-설명-가져오기))
3. KDoc/Javadoc
4. `No description`

그래서 필드에 `@Description` 이나 `@Schema` 를 붙이면 주석보다 그것이 먼저입니다. `descriptions.resolver` 에 규칙 하나만 대입하면 KDoc/Javadoc 까지 이 단계 전체가 바뀝니다. 유지하려면 `DescriptionResolver.DEFAULT`(또는 `SOURCE_DOCS`)와 조합합니다 ([설명 규칙 조합하기](#설명-규칙-조합하기)). enum 상수 주석과 컨트롤러 메서드 주석은 `descriptions.resolver` 를 거치지 않으므로 그대로 쓰입니다.

#### 읽는 것

| 주석 위치 | 쓰이는 곳 |
|---|---|
| Kotlin 프로퍼티 KDoc (생성자 프로퍼티나 클래스 본문 프로퍼티 위의 `/** ... */`) | 그 프로퍼티 |
| 클래스 KDoc 의 `@property 이름` | 프로퍼티에 자기 KDoc 이 없을 때 그 프로퍼티 |
| 클래스·생성자 KDoc 의 `@param 이름` | 위 둘이 모두 없을 때 그 프로퍼티 |
| Java 필드 Javadoc | 그 프로퍼티 |
| Java getter Javadoc (`getName`, `isActive`) | 필드에 Javadoc 이 없을 때 그 프로퍼티 |
| Java record Javadoc 의 `@param 이름` | 그 record component |
| enum 상수 KDoc·Javadoc | 값 목록과 `@EnumCode` 표의 그 상수. 다른 설명이 없을 때만 씁니다: `extraction.enumDescriptionProperty` 값(`@EnumCode` 는 `description` 도)이 없거나 `null` 일 때. `@EnumCode(descriptionProperty = ...)` 를 직접 정하면 쓰지 않습니다 |
| 클래스 KDoc·Javadoc | 그 클래스로 만든 OpenAPI 컴포넌트 스키마의 `description` (아래) |
| 컨트롤러 메서드 KDoc·Javadoc | operation 의 OpenAPI summary·description 과 `api-summary`(HTML 문서)의 설명. `methodDocs = true` 일 때만 (아래) |
| 컨트롤러 메서드 KDoc·Javadoc 의 `@param 이름` | 이름만 적은(`pathParameters("id")`) 경로·쿼리·폼 파라미터, 요청 헤더, 파트. `paramDocs`(기본값 `true`)를 따르고 `methodDocs` 와는 상관없음 (아래) |

- 주석은 소스의 프로퍼티 이름으로 맞추므로, Jackson 이름 규칙(`@JsonProperty`, `@JsonNaming`, 전역 규칙)과 kotlinx `@SerialName` 은 어노테이션과 똑같이 적용됩니다.
- 주석이 쓰이는 범위는 목록과 같습니다 ([적용 범위](#적용-범위)): 요청·응답 필드, `parametersOf`, 메시지 payload.
- 중첩 클래스(`Outer.Inner`)도 맞춥니다.
- 클래스 주석은 OpenAPI 컴포넌트 스키마(`components.schemas`)의 `description` 이 됩니다. 어느 클래스인지는 스키마 이름을 만든 클래스로 정합니다: 바깥 타입(`ApiResponseItem` → `ApiResponse`), 래퍼를 뗀 이름이면 타입 인자(`Item`). 중첩 클래스도 맞춥니다. 직접 정한 이름(`openApi { responseSchema }`, `openApi.errorSchema`, `openApi.schemaNameResolver`)도 본문 타입을 알면(필드를 타입에서 추출했으면) 넣습니다. 이미 설명이 있는 스키마는 그대로 두고, 서로 다른 클래스가 같이 쓰는 이름에는 넣지 않습니다. `schemaDescriptions = false` 면 꺼집니다.

#### 컨트롤러 메서드 → OpenAPI summary·description

이 부분은 `enabled = true` 여도 꺼져 있습니다. 핸들러 메서드의 주석은 코드를 고치는 사람을 위한 구현 메모("캐시를 먼저 본다", "데모 토큰이 없으면 401")인 경우가 많은데, 켜 두면 그대로 API 의 공개 summary 가 되기 때문입니다. 컨트롤러 주석을 API 사용자에게 보여 줄 글로 쓰고 있다면 Gradle 에서 켭니다.

```kotlin
kardx {
    sourceDocs {
        enabled = true
        methodDocs = true // 기본값 false. 필드·타입·enum 상수 주석과 @param 은 이 값과 상관없이 쓴다
    }
}
```

`methodDocs = false` 면 `apiDocsSourceDocs` 가 `source-docs.json` 에 메서드 summary·description 을 쓰지 않으므로(`@param` 글은 `paramDocs` 를 따름) operation 은 기본 summary·description 을 그대로 씁니다. 이 설정은 태스크 입력이라 값을 바꾸면 스캔과 테스트가 다시 돕니다.

`methodDocs = true` 면 요청을 처리한 핸들러 메서드의 KDoc·Javadoc 이 OpenAPI operation 에 들어갑니다. 첫 문장이 `summary`, 전체가 `description` 입니다. 첫 문장은 처음 나오는 `. `, 끝의 `.`, 또는 첫 문단 구분까지입니다 (`다.` 로 끝나는 한국어 문장도 같습니다). summary 에서는 그 끝 마침표를 뺍니다. 이름이 같은 오버로드 메서드는 주석이 같으면 하나로 묶고, 다르면 소스에 선언한 파라미터 수로 구분합니다 (`suspend` 함수의 `Continuation` 과 확장 수신 객체는 세지 않습니다).

| | summary | description |
|---|---|---|
| 1. `openApi { summary }`, `openApi { description }` | 우선 | 우선 |
| 2. 메서드 KDoc·Javadoc | 첫 문장 | 전체 |
| 3. 기본값 ([OpenAPI 태그와 요약](#openapi-태그와-요약)) | 테스트 이름 | 블록 `description`, 없으면 summary |

- `openApi { }` 에 적은 값이 항상 이기고, 주석과 달라도 경고하지 않습니다.
- HTTP Client 내보내기(`api.http`)의 `###` 제목은 최종 summary 를 따릅니다.
- OpenAPI operation 의 description 에서도 메서드 주석이 블록 `description` 보다 먼저입니다.
- REST Docs 산출물에서는 블록에 `description` 이 없을 때 전체 문장이 `api-summary` 스니펫의 설명(따라서 HTML 문서의 설명)이 됩니다. 여기서는 블록의 `description = "..."` 이 항상 이깁니다. 섹션 제목은 테스트 이름 그대로입니다.
- 핸들러는 MockMvc 와, 핸들러를 기록하는 WebTestClient(`restDocsWebTestClient`, `recordHandlers()`)에서 알 수 있습니다 ([파라미터 타입은 핸들러에서 정해진다](#파라미터-타입은-핸들러에서-정해진다)와 같음). RestTestClient 와 함수형 라우트는 핸들러 메서드를 알려 주지 않으므로 그 operation 에는 메서드 주석이 들어가지 않습니다 (필드 주석은 그대로 씁니다. [알려진 한계](#알려진-한계)).

#### 이름만 적은 파라미터 → 메서드 `@param`

경로·쿼리·폼 파라미터, 요청 헤더, 요청 파트는 이름만 적을 수 있습니다. 그러면 설명은 요청을 처리한 핸들러 메서드의 `@param` 태그에서 옵니다. `sourceDocs { enabled = true }` 가 필요하고, `methodDocs` 가 아니라 `paramDocs`(기본값 `true`)를 따릅니다. `@param` 글은 파라미터 하나를 설명하는 글이라 구현 메모가 summary 로 샐 걱정이 없기 때문입니다.

```kotlin
/**
 * 상품을 검색한다.
 *
 * @param categoryId 검색할 카테고리
 * @param keyword 상품 이름에 든 단어
 */
@GetMapping("/api/categories/{categoryId}/products")
fun search(@PathVariable categoryId: Long, @RequestParam("q") keyword: String): List<ProductResponse> = ...

// 테스트에서
.andDocument {
    pathParameters("categoryId")
    queryParameters("q") // @RequestParam("q") → 소스 인자 keyword → @param keyword
}
```

| 선언 | 맞추는 핸들러 인자 |
|---|---|
| `pathParameters("id")` | `@PathVariable` |
| `queryParameters("page")`, `formParameters("name")` | `@RequestParam`, 또는 Spring 어노테이션이 없는 단순 타입 인자 |
| `requestHeaders("X-Tenant")` | `@RequestHeader` (헤더 이름은 대소문자를 가리지 않음) |
| `requestParts("file")` | `@RequestPart`, 또는 `@RequestParam` |

- 요청 이름은 어노테이션의 `name`/`value`, 어노테이션에 이름이 없으면 인자 이름과 맞춥니다. 그다음 그 인자의 소스 이름으로 `@param` 글을 찾습니다.
- 테스트에 적은 설명이 항상 이깁니다. 이름만 적은 항목과 설명을 적은 항목을 섞어 쓸 수 있습니다. `pathParameters(...)` 를 다시 부르면 이어 붙습니다.
- 이름만 적었는데 설명을 찾지 못하면 테스트가 항목마다 이름을 적은 `IllegalArgumentException` 으로 실패합니다 (`path parameter 'id' declared by name only (or with FromParamTag), but no description was found. …`). `@param` 태그를 달고 기능을 켜거나(`sourceDocs { enabled = true }`) 테스트에 설명을 적습니다 ([KDX-101](troubleshooting.md#kdx-101)).
- 설명은 적지 않고 수식어(`isOptional`, `example`, `withDefaultValue` …)만 붙이려면 설명 자리에 `FromParamTag` 를 둡니다. 이름만 적은 항목처럼 `@param` 에서 설명을 가져오고, 설명을 적은 형태의 수식어를 모두 쓸 수 있습니다:

  ```kotlin
  queryParameters(
      "q" queryMeans FromParamTag,
      "page" queryMeans FromParamTag isOptional true withDefaultValue "0",
      "sort" queryMeans "정렬 순서" isOptional true, // 설명을 적은 항목과 한 호출에 섞어 쓸 수 있다
  )
  requestHeaders("Accept-Language" headerMeans FromParamTag isOptional true example "ko")
  ```

  `pathMeans`, `queryMeans`, `formMeans`, `headerMeans`, `partMeans` 에 쓸 수 있습니다. `@param` 은 요청 항목만 설명하므로 응답 헤더와 메시지 헤더에서는 실패합니다.
- `@ModelAttribute` 객체의 필드는 핸들러 인자가 아닙니다. `queryParameters<T>()` 나 `parametersOf<T>()` 로 문서화하면 타입의 KDoc·Javadoc 에서 설명을 가져옵니다.
- 핸들러를 알아야 합니다: MockMvc, 또는 핸들러를 기록하는 WebTestClient. RestTestClient 와 함수형 라우트(`RouterFunction`)에는 핸들러 메서드가 없으므로 이름만 적은 항목은 실패합니다 ([알려진 한계](#알려진-한계)).
- `@param` 글은 소스의 인자 이름으로 찾으므로 Java 핸들러는 클래스 파일에 인자 이름이 있어야 합니다 (`-parameters`, Spring Boot Gradle 플러그인이 켭니다). 없으면 어노테이션에 이름도 없는 인자는 아예 맞추지 못하고, `@PathVariable("id")` 처럼 이름이 있어도 `@param` 글을 찾지 못합니다. 어느 쪽이든 항목이 실패합니다. Kotlin 핸들러는 따로 할 일이 없습니다.

#### 문장 정리

설명은 표의 칸에 들어가므로 주석 하나가 한 줄이 됩니다.

| 주석에서 | 설명에서 |
|---|---|
| 줄 앞의 `*`, 주석 기호 | 뺍니다 |
| 블록 태그 (`@param`, `@return`, `@throws`, `@see`, `@since`, `@property`, `@constructor`, `@sample` …) | 본문에서 뺍니다 |
| `[UserResponse]`, `[사용자][UserResponse]` | `UserResponse`, `사용자` |
| `{@link UserResponse}`, `{@link UserResponse 사용자}` | `UserResponse`, `사용자` |
| `{@code id}` | `` `id` `` |
| HTML 태그 (`<p>`, `<b>` …) | 뺍니다. `<p>` 는 문단 구분 |
| 여러 문단 | 공백 하나로 잇습니다 |

#### 알려진 한계

- **컴파일러가 아닙니다.** 스캐너는 Kotlin·Java 컴파일러가 아니라 가벼운 읽기 도구입니다. 알아보지 못하는 문법의 선언은 건너뛰고 나머지를 계속 읽으며, 빌드를 실패시키지 않습니다. 건너뛴 선언은 설명이 없을 뿐입니다.
- **빌드가 볼 수 있는 소스만.** 태스크는 그 프로젝트와, `includeProjectDependencies` 면 `project(...)` 로 의존하는 프로젝트의 `main` 소스 세트를 읽습니다. jar 로 들어온 DTO(다른 저장소, 배포된 모듈, 라이브러리)는 여기에 소스가 없으니 주석도 없습니다. 이런 DTO 에는 `@Description` 이나 `description-annotations` 를 씁니다. 테스트 소스와 test fixtures 는 읽지 않고, 생성된 소스(kapt, KSP, 어노테이션 프로세서)는 그 디렉터리가 `main` 소스 세트에 들어 있을 때만 읽습니다.
- **프로젝트 의존성은 Java 플러그인의 main 소스 변형이 있어야 합니다.** `project(...)` 의존성은 `java` 플러그인이 main 소스용으로 내보내는 변형으로 찾습니다. Android·Kotlin Multiplatform 모듈과 included build(`includeBuild`)에는 그 변형이 없으므로 소스를 읽지 않습니다.
- **`/** ... */` 주석만.** 줄 주석(`//`), 일반 블록 주석(`/* ... */`), Markdown 문서 주석(`///`, JDK 23)은 읽지 않습니다.
- **모으지 않는 것**: 최상위 함수·프로퍼티(최상위에 선언한 함수형 라우트의 핸들러 함수 포함), 확장 프로퍼티, 함수 본문 안에 선언한 클래스·object(지역 클래스, 익명 object·클래스), Java 생성자의 `@param`(record 의 `@param` 만 읽음. Kotlin 주 생성자·보조 생성자의 `@param` 은 읽음). sealed·`oneOf` 변형 타입의 설명도 클래스 주석에서 가져오지 않습니다.
- **핸들러 주석은 컨트롤러에서 찾습니다.** 메서드 주석과 `@param` 태그는 컨트롤러 클래스와 핸들러 메서드를 선언한 클래스(컨트롤러가 메서드를 다시 정의하지 않았을 때의 추상 기반 컨트롤러)에서 찾습니다. 컨트롤러가 구현한 인터페이스 메서드(OpenAPI 파일로 생성한 API 인터페이스 등)의 주석은 쓰지 않으며, `{@inheritDoc}` 은 빈 글이 됩니다. 반면 프로퍼티 주석은 상위 클래스와 인터페이스에서도 찾습니다.
- **파라미터 수가 같은 오버로드.** 이름이 같은 핸들러 메서드의 주석이 다르면 파라미터 수로만 구분하므로, 파라미터 수가 같고 주석이 다른 오버로드에는 메서드 주석이 들어가지 않습니다.
- **핸들러 메서드가 없으면 메서드 주석도 없습니다.** RestTestClient 와 함수형 엔드포인트(`router { }`, `coRouter { }`, `RouterFunction`)는 핸들러 메서드를 알려 주지 않고, WebTestClient 는 핸들러를 기록할 때만 알려 줍니다. 이때 operation 에는 메서드 주석이 들어가지 않고 이름만 적은 항목은 실패합니다. 필드·enum·스키마 주석은 그대로 씁니다.
- **Java bean 이름은 이름만 보고 정합니다.** getter 이름은 이름만으로 프로퍼티 이름이 됩니다. `getURL` 은 `java.beans.Introspector` 처럼 `URL` 이 되지만 Jackson 은 `url` 을 쓰고, `isX` 는 반환 타입과 상관없이 getter 로 보지만 Jackson 은 `boolean` 일 때만 그렇게 봅니다. 이런 getter 의 주석은 맞춰지지 않거나 엉뚱한 프로퍼티에 붙을 수 있습니다. 이럴 때는 필드에 주석을 답니다.
- **스키마 설명은 스키마 이름에 클래스 이름이 있어야 합니다.** 요청·응답 본문 스키마만, 스키마 이름에 단순 이름이 들어 있는 클래스로 설명합니다. 클래스 이름이 들어 있지 않은 직접 정한 이름(본문이 `ApiResponse<ProductResponse>` 인데 `openApi { responseSchema = "ProductDetailResponse" }`, 또는 `openApi.schemaNameResolver` 가 정한 이름), 필드를 직접 적은 본문(타입 없음), 서로 다른 클래스가 같이 쓰는 이름에는 설명이 들어가지 않습니다.
- **마크업은 대부분 그대로 남습니다.** KDoc 링크, Javadoc 인라인 태그, HTML 은 바꿉니다 ([문장 정리](#문장-정리)). 그 밖의 Markdown(`**굵게**`, 목록, 코드 블록)은 적은 그대로 남고, 주석 하나가 한 줄이 되며, summary 는 첫 문장뿐입니다.
- **소스는 UTF-8 로 읽습니다.** 다른 인코딩(`options.encoding = "MS949"`)의 파일도 읽지만 UTF-8 이 아닌 글자는 설명에서 `U+FFFD` 가 되고, 태스크가 그런 파일 수와 첫 파일을 적은 경고를 한 번 남깁니다. 소스를 UTF-8 로 저장합니다.
- **Gradle 플러그인에서만.** 플러그인 없이(Maven, 라이브러리만) 쓰면 주석 파일이 없으므로 설명은 어노테이션에서만 옵니다.
- **실험 API.** 이 기능의 테스트 쪽 API 에는 `@ExperimentalRestDocsApi` 가 붙어 있습니다: `descriptions.sourceDocs`, `DescriptionResolver.SOURCE_DOCS`, `FromParamTag` 와 `pathMeans`/`queryMeans`/`formMeans`/`headerMeans`/`partMeans`, 이름만 적는 파라미터 선언(`pathParameters("id")` …). `@OptIn(ExperimentalRestDocsApi::class)`(또는 모듈 전체 opt-in) 없이 쓰면 컴파일 경고가 납니다. 다음 릴리스에서 바뀔 수 있고 1.0 의 호환 보장 범위에 들지 않습니다 ([실험 API](configuration.md#실험-api)).

주석이 반영되지 않으면 [문제 해결](troubleshooting.md)을 보세요.

### 설명 출처 바꾸기

`descriptions.resolver` 는 필드 설명을 정하는 규칙 `DescriptionResolver`(`io.github.kardx.config`)입니다. 기본값 `DescriptionResolver.DEFAULT` 는 `@Description`, `descriptions.annotations` 목록, KDoc/Javadoc 을 차례로 봅니다. 규칙 하나만 대입하면 그 규칙이 설명을 모두 정하고 이 단계들은 쓰지 않습니다. 유지하려면 기본 단계와 조합합니다 ([설명 규칙 조합하기](#설명-규칙-조합하기)).

어노테이션에서 읽는다면 목록으로 충분합니다. resolver 는 어노테이션이 아닌 규칙이 필요할 때 씁니다.

| | yml 목록 | 코드 목록 | 직접 정한 `descriptions.resolver` |
|---|---|---|---|
| 코드 | 필요 없음 | `configure`, `withSettings` | `configure`, `withSettings` |
| `@Description` 을 먼저 보나 | 예 | 예 | `DescriptionResolver.DEFAULT`(또는 `DESCRIPTION_ANNOTATION`)와 조합하면 |
| 할 수 있는 것 | 어노테이션의 String 속성 읽기 | yml 과 같음 + 함수로 읽기(`descriptions.from`) | 무엇이든. 필드 이름, 여러 어노테이션 조합, 외부 사전 |
| 테스트 하나에서만 | 안 됨 | `withSettings` | `withSettings` |

```kotlin
import io.github.kardx.config.DescriptionResolver
import io.github.kardx.config.orElse

RestDocsConfiguration.configure {
    descriptions {
        // Swagger @Schema 를 먼저, 없으면 기본 단계(@Description → 목록 → KDoc/Javadoc)
        resolver = DescriptionResolver { it.findAnnotation<Schema>()?.description } orElse DescriptionResolver.DEFAULT
    }
}
```

`DescriptionResolver` 는 fun interface 라 프로퍼티에 람다를 대입할 때는 SAM 생성자(`DescriptionResolver { ... }`)를 씁니다. 규칙이 받는 `PropertyContext` 에는 JSON 필드 이름(`name`), 이름 규칙을 적용하기 전 소스의 이름(`declaredName`), 선언한 클래스(`declaringClass`, 모르면 `null`), 붙은 어노테이션 전체(`annotations`), Kotlin 프로퍼티(`property`, Java record·bean 이면 `null`)가 있습니다. `findAnnotation` 은 어노테이션 위치(use-site target)와 상관없이 찾습니다. `null` 을 돌려주면 "여기엔 설명 없음"이라 다음 단계를 보고, 남은 단계가 없으면 `No description` 이 됩니다.

#### 설명 규칙 조합하기

기본 단계가 공개되어 있으므로 체인을 다시 만들지 않고 내 규칙을 그 앞이나 뒤에 끼웁니다.

| 단계 | 읽는 것 |
|---|---|
| `DescriptionResolver.DESCRIPTION_ANNOTATION` | `@Description` |
| `DescriptionResolver.CONFIGURED_ANNOTATIONS` | 지금 적용 중인 `descriptions.annotations` 목록 (yml 과 코드) |
| `DescriptionResolver.SOURCE_DOCS` | KDoc/Javadoc. 실험 기능 (`@ExperimentalRestDocsApi`) |
| `DescriptionResolver.DEFAULT` | `firstOf(DESCRIPTION_ANNOTATION, CONFIGURED_ANNOTATIONS, SOURCE_DOCS)` |

`DescriptionResolver.firstOf(a, b, ...)` 는 처음 찾은 설명을 돌려주고, `a orElse b`(import `io.github.kardx.config.orElse`)는 규칙 둘로 같은 일을 합니다. `null`·빈 값·공백뿐인 값은 못 찾은 것으로 봅니다.

**이름 기반 사전**. `createdAt` 처럼 여러 DTO 에 반복되는 필드를 기본 단계 다음에 이름으로 설명합니다.

```kotlin
val glossary = mapOf(
    "createdAt" to "생성 시각",
    "updatedAt" to "수정 시각",
)
val byGlossary = DescriptionResolver { glossary[it.name] }

RestDocsConfiguration.configure {
    descriptions.resolver = DescriptionResolver.DEFAULT orElse byGlossary
}
```

단계를 고르려면 나열합니다. 다음은 어노테이션과 사전만 읽고 KDoc/Javadoc 은 읽지 않습니다.

```kotlin
RestDocsConfiguration.configure {
    descriptions.resolver = DescriptionResolver.firstOf(
        DescriptionResolver.DESCRIPTION_ANNOTATION,
        DescriptionResolver.CONFIGURED_ANNOTATIONS,
        byGlossary,
    )
}
```

이미 정해진 규칙(예: 기본 클래스가 정한 것) 위에 더하려면 지금 값을 씁니다: `descriptions.resolver = descriptions.resolver orElse byGlossary`. 0.1 의 `val previous = descriptionResolver; descriptionResolver = { previous(it) ?: ... }` 패턴을 대신합니다.

### 추출 결과와 직접 기술 섞기

추출 결과는 `List<RestDocsField>` 이므로 걸러 내고 더할 수 있습니다. 뺄 때는 `except`, optional 로 바꿀 때는 `optionalAt` 을 씁니다. 직접 쓴 필드를 `+` 로 더해도 스키마 이름(`ProductResource`)은 유지됩니다.

```kotlin
responseFields(
    responseFieldsOf<ProductResource>().except("_links") +
        ("_links" subsection OBJECT means "HAL 링크"),
)
```

### 추출 결과 미리보기

`describe()` 는 필드·파라미터 목록이 문서 표에 무엇으로 나갈지 글자 표로 돌려줍니다. 테스트를 돌려 HTML 을 열어 보지 않고 추출 결과를 확인할 때 씁니다. 추출 함수 결과(`responseFieldsOf`, `requestFieldsOf`, `wrappedResponseFieldsOf`, `parametersOf`)와 직접 쓴 목록 모두에 쓸 수 있습니다.

```kotlin
println(requestFieldsOf<ProductRequest>().describe())
println(parametersOf<ProductSearchCondition>().describe())
```

<!-- docs-pair: ignore-next-block -->
```
| Path  | Type   | Required | Description | Constraints |
|-------|--------|----------|-------------|-------------|
| name  | String | true     | 상품명      |             |
| price | Number | true     | 가격        |             |
```

| 함수 | 결과 |
|---|---|
| `List<RestDocsField>.describe()` | Path·Type·Required·Description·Constraints 표 (`response-fields` 표와 같은 열). ignored 필드는 빠지고, subsection 필드는 Type 에 `(subsection)` |
| `List<RestDocsParam>.describe()` | Parameter·Required·Description·Constraints 표 |
| `printDescription()` | 표를 표준 출력에 찍고 목록을 그대로 돌려준다. `responseFields(responseFieldsOf<T>().printDescription())` |

설명 칸에는 `[Deprecated: ...]`, `[Masked: ...]`, `(Default: ...)`, `(Example: ...)` 이 한 줄로 붙습니다. 한글은 두 칸으로 세어 터미널에서 열이 맞습니다. 함수는 `io.github.kardx.extractor` 패키지에 있습니다 (`import io.github.kardx.extractor.describe`).

### 더 이상 쓰지 않는 필드

Kotlin `@Deprecated` 나 Java `@Deprecated` 가 붙은 프로퍼티는 추출할 때 자동으로 표시됩니다.

```kotlin
data class ProductSearchCondition(
    @Description("최소 가격") val minPrice: Long? = null,
    @Deprecated("minPrice 를 쓴다")
    @Description("최소 가격 (예전 이름)")
    val priceFrom: Long? = null,
)
```

| 출력 | 결과 |
|---|---|
| AsciiDoc 표 | Description 칸 맨 위에 `*Deprecated*: minPrice 를 쓴다` 줄 (Java `@Deprecated` 는 `*Deprecated*` 만) |
| OpenAPI (Gradle 플러그인) | 파라미터와 본문 필드 스키마에 `deprecated: true` |
| AsyncAPI | 페이로드 스키마 속성에 `deprecated: true` |

직접 쓸 때는 `"nick" type STRING means "닉네임" deprecated "nickname 을 쓴다"` 나 `deprecated()` 를 붙입니다. 표의 문구를 바꾸려면 [스니펫 템플릿](customizing-snippets.md#스니펫-템플릿-바꾸기)의 `{{#deprecated}}` 부분을 고칩니다. 검증에는 영향이 없습니다. operation 전체를 deprecated 로 하려면 블록에서 `deprecated("이유")` 를 씁니다 ([deprecated API](#deprecated-api)).

OpenAPI 의 응답 스키마는 `$ref` 로 여러 operation 이 함께 씁니다. 한 테스트에서만 `deprecated()` 로 표시한 필드가 같은 스키마를 쓰는 다른 operation 에 번지지 않도록, 같은 스키마 속성을 문서화한 모든 테스트가 표시했을 때만 `deprecated: true` 를 넣습니다. 어노테이션에서 온 표시는 그 타입을 추출한 모든 테스트에 있으므로 그대로 남습니다. [마스킹 표시](#마스킹되는-필드-표시)도 같습니다.

### 마스킹되는 필드 표시

이름 `홍*동`, 전화번호 `010-****-1234` 처럼 앱이 값을 가려서 내보내는 필드가 있다면, 그 사실을 문서에 적어 둘 수 있습니다. 문서를 보는 쪽(프런트엔드, 연동사)은 원래 값이 오지 않는다는 것을 알고 화면이나 검증을 맞춥니다.

**값을 실제로 마스킹하는 기능이 아닙니다.** 마스킹은 앱(직렬화기 등)이 하고, 여기서는 문서에 표시만 합니다.

#### `@Masked`

DTO 프로퍼티에 `@Masked` 를 붙입니다. 값은 마스킹 규칙 설명이고, 비우면 규칙 없이 `Masked` 만 나옵니다. annotations 모듈(`io.github.kardx.annotation.Masked`)에 있고, 붙이는 위치는 `@Description` 과 같습니다 (생성자 프로퍼티, `@property:`, `@field:`, `@get:`, Java 필드·getter·record component).

```kotlin
data class UserResponse(
    @Description("사용자 이름")
    @Masked("가운데 글자를 *")
    val name: String,

    @Description("휴대폰 번호")
    @Masked("가운데 4자리를 *")
    val phone: String,
)
```

`responseFieldsOf<UserResponse>()`·`requestFieldsOf<T>()`·메시지와 SSE 페이로드처럼 **본문 필드**를 추출할 때 붙습니다. 값이 문서로 나가지 않는 파라미터(`parametersOf<T>()`)에는 붙이지 않습니다.

#### 수식어

직접 쓰는 필드에는 `masked()` 나 `masked "규칙"` 을 붙입니다.

```kotlin
responseFields(
    "name" type STRING means "사용자 이름" masked "가운데 글자를 *",
    ("phone" type STRING means "휴대폰 번호").masked(),     // 규칙 없이
)
```

#### 회사 마스킹 어노테이션 읽기

Jackson serializer 를 거는 커스텀 어노테이션처럼 이미 쓰고 있는 마스킹 어노테이션이 있다면 `@Masked` 를 따로 붙이지 않고 그 어노테이션을 읽게 합니다. 문법은 [`description-annotations`](#항목-쓰는-법) 와 같습니다.

```yaml
kardx:
  masked-annotations: com.example.MaskName, com.example.Masking#rule
```

| 항목 | 결과 | 예 |
|---|---|---|
| `FQCN` | 붙어 있으면 마스킹됨 (규칙 설명 없음) | `com.example.MaskName` → `@MaskName` 은 `Masked` |
| `FQCN#attribute` | 그 속성 값이 규칙 설명. String 이면 값, enum 이면 상수 이름 | `com.example.Masking#rule` → `@Masking(rule = "뒤 4자리")` 은 `Masked: 뒤 4자리`, `@Masking(type = MaskType.PHONE)` 에 `#type` 이면 `Masked: PHONE` |

- `@Masked` 는 설정과 상관없이 항상 먼저 읽습니다. 그다음 목록 순서대로 보고, 처음 찾은 어노테이션을 씁니다.
- 코드로는 `masking.annotation("com.example.MaskName")`, `masking.annotation(Masking::class, "rule")`, 목록을 통째로 바꾸는 `masking.annotations = listOf(...)` 입니다 ([설정 목록](configuration.md#설정-목록)).
- 잘못된 값은 `description-annotations` 와 같은 형식의 오류로 실패합니다: `kardx.masked-annotations: class 'com.example.MaskName' not found`, `... is not an annotation`, `... annotation 'com.example.Masking' has no String or enum attribute 'count'`, `... 'com.example.Masking#' has no attribute name after '#'`.

#### 문서와 Swagger 에 보이는 모습

위 `UserResponse` 의 `phone` 은 이렇게 나옵니다.

| 출력 | 결과 |
|---|---|
| AsciiDoc 필드 표 | Description 칸 맨 위에 `*Masked*: 가운데 4자리를 *` 줄 (`@Deprecated` 가 함께 있으면 그 아래). 규칙이 없으면 `*Masked*` |
| OpenAPI (Gradle 플러그인) | 스키마 속성 `description` 끝에 `Masked: 가운데 4자리를 *`, 확장 속성 `x-masked: true`, `x-masked-rule: 가운데 4자리를 *` |
| AsyncAPI | 페이로드 스키마 속성에 OpenAPI 와 같은 설명·확장 속성 |
| `describe()` | 설명 칸 앞에 `[Masked: 가운데 4자리를 *]` |

```
|`+phone+`
|`+String+`
|true
|*Masked*: 가운데 4자리를 * +
휴대폰 번호
```

```yaml
phone:
  type: string
  description: |-
    휴대폰 번호

    Masked: 가운데 4자리를 *
  x-masked: true
  x-masked-rule: 가운데 4자리를 *
```

Swagger UI 는 설명 끝의 `Masked: ...` 를 그대로 보여 줍니다. `x-masked` 는 화면에는 보이지 않지만 코드 생성기나 스펙 검사 도구가 읽을 수 있습니다. 표의 문구를 바꾸려면 [스니펫 템플릿](customizing-snippets.md#스니펫-템플릿-바꾸기)의 `{{#masked}}` 부분을 고칩니다. 검증에는 영향이 없습니다.

#### 헤더 마스킹과의 차이

| | 마스킹되는 필드 표시 (`@Masked`) | 헤더 마스킹 (`masking.headers`) |
|---|---|---|
| 하는 일 | 앱이 값을 가려서 내보낸다는 **사실**을 문서에 적는다 | 문서에 찍히는 헤더 **값**을 `****` 로 가린다 |
| 대상 | 본문 필드 | 요청·응답 헤더, 쿠키 |
| 값이 바뀌나 | 아니요 | 문서 안의 값만 바뀐다 (앱 응답은 그대로) |
| 설정 | `@Masked`, `masked()`, `masked-annotations` | `mask-sensitive-headers`, `masked-headers` ([민감 정보 마스킹](configuration.md#민감-정보-마스킹)) |

## 검증 실패 메시지

Spring REST Docs 는 문서화한 필드·파라미터가 실제와 다르면 `SnippetException` 으로, 필드 타입이 다르면 `FieldTypesDoNotMatchException`(타입 없이 문서화한 필드가 없으면 `FieldTypeRequiredException`)으로 실패합니다. `andDocument { }` (MockMvc, WebTestClient, RestTestClient), SSE `events(...)`, `MessageDocumentation` 은 이것을 `DocumentationMismatchException`(`SnippetException` 의 하위 클래스, `io.github.kardx.dsl`)으로 감싸 다음을 알려 줍니다. 원래 예외는 `cause` 에 남습니다. 필드 타입 예외는 원래 `SnippetException` 이 아니었으므로, 그 예외를 직접 잡던 코드는 `cause` 를 봅니다.

- 문서화하지 않은 항목(실제에 있음)과 문서화했지만 실제에 없는 항목을 경로별로. 필드는 실제 JSON 타입이 붙습니다
- 타입이 맞지 않는 필드의 문서화한 타입과 실제 타입
- 바로 붙여 넣을 수 있는 DSL 줄 (`"data.memo" type STRING means "..."`, `"page" queryMeans "..."`, `"tick" eventMeans "..."`)
- 실제 본문으로 확인되는 흔한 원인 (확인되지 않으면 힌트를 붙이지 않습니다)

```
io.github.kardx.dsl.DocumentationMismatchException: Documented response fields do not match the actual response.

Not documented (present in the response):
  - data.memo (String)

Documented but not in the response:
  - data.nickname
  Remove them, or mark them `isOptional true` if they can be absent.

Add to the documentation (replace "..." with a description):
  "data.memo" type STRING means "..."

Hints:
  - Absent although the parent object is present: `data.nickname`. If the value was null and the mapper omits nulls (@JsonInclude(NON_NULL), spring.jackson.default-property-inclusion: non_null), mark it `isOptional true` or make the property nullable; otherwise fill it in the test data.

The original Spring REST Docs message is the cause of this exception.
```

| 힌트 | 판별 조건 | 해결 |
|---|---|---|
| 빈 배열 | 없는 경로가 `items[].x` 이고 실제 `items` 가 `[]` | 테스트 데이터에 원소를 넣거나 `responseFieldsOf<T>().optionalUnder("items[]")` (모든 배열은 `extraction.arrayElementFieldsOptional`) |
| 이름 전략 | 대소문자·`_`·`-` 만 다른 이름이 양쪽에 있음 (`userName` ↔ `user_name`) | 앱 매퍼와 같은 매퍼로 추출 (`restDocsMockMvc` 또는 `objectMapper` 설정) |
| 래퍼 없이 문서화 | 문서의 `id` 가 실제로는 `data.id` | `wrappedResponseFields<T>()` + `response-wrapper`, 또는 `responseFields<T>("data")` |
| 래퍼와 함께 문서화 | 문서의 `data.id` 가 실제로는 최상위 `id` | 이 응답은 `responseFields<T>()` |
| 부모는 있는데 빠진 필드 | 부모 객체는 있고 키만 없음 | null 이 직렬화에서 빠졌다면 `isOptional true` 나 nullable 프로퍼티 |

필드 타입이 다르면 목록 대신 타입을 보여 줍니다.

```
io.github.kardx.dsl.DocumentationMismatchException: Documented response fields do not match the actual response.

Type mismatch:
  - data.price: documented Number, actual String

Change the documentation (replace "..." with a description):
  "data.price" type STRING means "..."

Hints:
  - `data.price` is documented as a number but the payload has a string. Common causes: @JsonFormat(shape = JsonFormat.Shape.STRING) or @JsonSerialize(using = ToStringSerializer::class) on the property of a hand-documented field, or a mapper that writes numbers as strings (JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS). If the string is intended, document it as STRING.

The original Spring REST Docs message is the cause of this exception.
```

| 실제 타입 | 붙여 넣을 DSL 줄 | 힌트 |
|---|---|---|
| `Null` | `"x" type <문서화한 타입> means "..." isOptional true` (optional 필드는 null 을 허용) | nullable 프로퍼티, `isOptional true`, 추출 목록은 `optionalAt("x")` |
| `Varies` (배열 원소마다 다름) | `"items[].x" type VARIES means "..."` | 의도가 아니면 앱에서 값의 타입을 맞춘다 |
| 숫자로 문서화했는데 `String` | `"x" type STRING means "..."` | `@JsonFormat(shape = STRING)`, `ToStringSerializer`, `WRITE_NUMBERS_AS_STRINGS` |
| 문자열로 문서화했는데 `Number`·`Array` | `"x" type NUMBER means "..."` 등 | 날짜를 타임스탬프로 쓰는 `WRITE_DATES_AS_TIMESTAMPS`, 숫자 `@JsonValue` Enum |
| 그 밖 | 실제 타입으로 바꾼 줄 | (없음) |

배열 원소 일부에서 null·빠진 값은 타입 불일치가 아니라 "Documented but not in the response" 로 나옵니다.

## 헤더와 인증

요청·응답 헤더는 `requestHeaders`, `responseHeaders` 로 문서화합니다. 인증 헤더와 커스텀 헤더 모두 같습니다.

```kotlin
mockMvc
    .perform(
        get("/api/todos/{id}", 1L)
            .bearerToken("access-token")                   // Authorization: Bearer access-token
            .header("X-Request-Id", "req-123"),            // 커스텀 헤더
    )
    .andDocument {
        requestHeaders(
            "Authorization" headerMeans "Bearer 액세스 토큰",
            "X-Request-Id" headerMeans "요청 추적 ID. 보내면 응답에 그대로 돌려준다" isOptional true,
        )
        responseHeaders("X-Request-Id" headerMeans "요청의 추적 ID" isOptional true)
    }
```

| 결과물 | 헤더가 나오는 곳 |
|---|---|
| REST Docs | 요청·응답 헤더 표, `http-request`·`http-response` 예시 |
| OpenAPI (Swagger) | 헤더 파라미터, 응답 `headers` |
| OpenAPI 보안 | `Authorization: Bearer ...` → `bearerAuthJWT`, `Basic ...` → `basic` 보안 스키마. Swagger UI 에 Authorize 버튼이 생긴다 |
| OpenAPI 전역 헤더 | `open-api.global-headers` 에 적은 헤더 → `apiKey` 보안 스키마. Authorize 창에서 한 번 입력하면 모든 요청에 붙는다 |

- **수식어**: `isOptional`, `constraint "UUID"`, `example "req-123"`, `withDefaultValue "ko"`. `example`·`withDefaultValue` 는 표의 설명 아래 `Example:`·`Default:` 줄과 OpenAPI 헤더의 `example`·`default` 가 됩니다 ([기본값·예시·제약 설명](#기본값예시제약-설명)).
- **Authorize 버튼**: 보안 스키마는 요청의 `Authorization` 값으로 만듭니다. 헤더 없이 인증한 테스트(`with(jwt())`)는 `openApi { bearerAuth() }` 로 선언합니다 ([헤더 없이 인증한 테스트의 보안 스키마](#헤더-없이-인증한-테스트의-보안-스키마)). 토큰이 JWT 가 아니거나 마스킹했어도, OpenAPI 조각에만 형식을 맞춘 가짜 JWT 를 넣어 만듭니다. 실제 토큰은 스펙에 들어가지 않습니다. `open-api.security-schemes: false` 로 끄면 OpenAPI 조각에서 `Authorization` 을 빼서 보안 스키마가 생기지 않습니다 (REST Docs 문서에는 그대로 남습니다).
- **민감 헤더의 예시**: OpenAPI 의 `Authorization`·`Proxy-Authorization`·`Cookie` 헤더 파라미터에는 요청에 보낸 값(보안 스키마용 가짜 JWT, 마스킹 값, 테스트 토큰)을 예시로 넣지 않습니다. `example`·`withDefaultValue` 로 직접 지정한 값은 개발용 Swagger 에서 바로 쓰도록 그대로 나갑니다 (`"Authorization" headerMeans "인증 토큰" example "Bearer dev-token"`). Postman Collection 도 같아서, 라이브러리가 넣은 가짜 토큰만 `{{bearerToken}}` 으로 바꿉니다.
- **토큰 없는 테스트**: 401·404 확인처럼 토큰 없이 보낸 테스트가 같은 API 에 섞여도, Gradle 플러그인이 같은 메서드·경로의 조각에 보안 요구사항을 맞춰 Authorize 가 유지됩니다. 플러그인 없이 쓰면 같은 API 의 모든 테스트에 토큰을 보내세요.
- **항상 보내는 헤더**: `X-Tenant-Id`, `X-Client-Version` 처럼 모든 요청에 붙는 헤더는 기본으로 operation 마다 입력하는 헤더 파라미터로 나옵니다. `open-api.global-headers` 에 적으면 `Authorization` 처럼 Authorize 창에서 한 번만 입력합니다 (OpenAPI 의 `apiKey`, `in: header` 보안 스키마). 테스트의 `requestHeaders(...)` 로 문서화한 헤더에만 적용되고, REST Docs 문서는 그대로입니다. Gradle 플러그인의 `openapi3` 뒤처리로 적용되며, 플러그인 확장 `kardx { openApiGlobalHeaders = setOf("X-Tenant-Id") }` 로도 정할 수 있습니다.

  ```yaml
  kardx:
    open-api:
      global-headers: X-Tenant-Id, X-Client-Version   # 코드: openApi.globalHeaders = setOf(...)
  ```
- **마스킹**: [마스킹](configuration.md#민감-정보-마스킹)을 켜면 인증 헤더는 방식은 남기고 값만 가립니다 (`Authorization: Bearer ****`).

### Spring Security

`restDocsMockMvc` 는 기본으로 Spring Security 필터 체인을 걸지 않습니다. 인터셉터·필터 등 Spring Security 가 아닌 방식으로 인증하는 프로젝트가 있기 때문입니다. Spring Security 로 보호하는 API 를 실제와 같게 문서화하려면 켭니다. 켜면 컨텍스트의 필터 체인(`springSecurityFilterChain` 빈)을 `@AutoConfigureMockMvc` 처럼 MockMvc 에 걸어, 인증이 필요한 API 를 인증 없이 부르면 실제 앱처럼 401·403 이 됩니다.

```yaml
kardx:
  apply-spring-security: true   # 코드: http.applySpringSecurity = true
```

```kotlin
testImplementation("org.springframework.security:spring-security-test")
```

```kotlin
mockMvc.perform(get("/api/me").with(jwt()))                       // 인증한 요청
mockMvc.perform(post("/api/orders").with(user("hong")).with(csrf()))
mockMvc.perform(get("/api/me")).andStatus(HttpStatus.UNAUTHORIZED)  // 401 응답
```

- spring-security-test 가 있으면 `springSecurity()` 로 걸어 `with(user(...))`·`with(jwt())`·`with(csrf())` 를 쓸 수 있습니다. 없으면 필터 체인만 걸리므로 실제 토큰·세션을 보내야 합니다.
- 켜지 않으면 필터 체인이 없으므로 인증 없이 부른 요청도 200 으로 문서화됩니다. Spring Security 로 보호하는 API 인데 401 응답을 문서화하고 싶거나, 인증 없이 200 이 나오는 것이 이상하다면 켜세요. 테스트 클래스 하나만 켜려면 `RestDocsConfiguration.withSettings({ http.applySpringSecurity = true }) { ... }` 로 MockMvc 를 만듭니다.
- `Authorization` 헤더를 보내면 OpenAPI 보안 스키마가 생깁니다(위 표). `with(jwt())` 처럼 헤더 없이 인증한 요청은 헤더가 없으므로 그대로는 보안 스키마가 생기지 않습니다. 아래처럼 선언합니다.
- `restDocsMockMvc` 는 이 설정을 MockMvc 를 만들 때 한 번 읽습니다. 컨텍스트에 `springSecurityFilterChain` 빈이 있는데 설정을 정하지 않았으면 JVM 마다 한 번 경고를 남깁니다. 필터 체인 없이 문서화하는 것이 의도라면 `apply-spring-security: false` 로 명시해 끕니다.

#### 헤더 없이 인증한 테스트의 보안 스키마

`openApi { bearerAuth() }`·`basicAuth()` 는 요청에 `Authorization` 헤더가 없어도 그 operation 을 OpenAPI 에서 인증이 필요한 API 로 표시합니다. 헤더를 보냈을 때와 같은 보안 스키마와 operation 의 `security` 가 생겨 Swagger UI 에 Authorize 버튼이 나옵니다.

```kotlin
mockMvc.perform(get("/api/me").with(jwt()))
    .andStatus(HttpStatus.OK)
    .andDocument {
        responseFields<MeResponse>()
        openApi { bearerAuth() }
    }
```

| 선언 | OpenAPI 보안 스키마 | 헤더를 보냈을 때와 같은 경우 |
|---|---|---|
| `bearerAuth()` | `bearerAuthJWT` (`type: http`, `scheme: bearer`, `bearerFormat: JWT`) | `Authorization: Bearer ...` |
| `basicAuth()` | `basic` (`type: http`, `scheme: basic`) | `Authorization: Basic ...` |

- OpenAPI 조각(`resource.json`)에만 형식을 맞춘 가짜 값을 넣습니다. REST Docs 스니펫(`http-request`, `curl-request` 등)과 요청 헤더 표는 바뀌지 않습니다.
- 요청에 다른 방식의 `Authorization` 이 있어도 OpenAPI 에서는 선언을 따릅니다. `open-api.security-schemes: false` 여도 선언한 operation 에는 붙습니다.
- MockMvc 와 WebTestClient(`mutateWith(mockJwt())` 등) 모두 같습니다.
- `Authorization` 을 요청 헤더 파라미터로 보이고 싶으면 따로 `requestHeaders("Authorization" headerMeans "...")` 를 문서화합니다. 이때는 실제 요청에 헤더가 있어야 합니다.

#### OAuth2 와 OpenID Connect

API 가 OAuth2 인가 서버의 토큰을 받는다면 operation 에 필요한 scope 를 선언합니다. 그러면 Swagger UI 의 Authorize 에 토큰 입력 칸 대신 OAuth2 로그인이 나옵니다.

```kotlin
mockMvc.perform(get("/api/users/{id}", 1L).with(jwt()))
    .andStatus(HttpStatus.OK)
    .andDocument {
        responseFields<UserResponse>()
        openApi { oauth2("users:read") }       // 또는 openIdConnect("openid", "profile")
    }
```

| 선언 | operation `security` | 보안 스키마 |
|---|---|---|
| `oauth2("users:read", ...)` | `- oauth2: [users:read]` | 빌드에 적은 flow 를 가진 `oauth2` (`type: oauth2`) |
| `openIdConnect("openid", ...)` | `- openIdConnect: [openid]` | 빌드에 적은 discovery URL 을 가진 `openIdConnect` (`type: openIdConnect`) |

scope 는 생략할 수 있습니다(`oauth2()` 는 특정 scope 없이 토큰만 요구). 스키마 이름은 `oauth2`, `openIdConnect` 로 고정입니다.

테스트는 토큰·인가 URL 을 모르므로, 이것은 프로젝트마다 한 번 Gradle 플러그인에 적습니다.

```kotlin
kardx {
    openApiSecurity {
        oauth2 {
            clientCredentials("https://auth.example.com/oauth/token")
            authorizationCode(
                "https://auth.example.com/oauth/authorize",
                "https://auth.example.com/oauth/token",
            )
            scope("users:read", "사용자 조회")
        }
        openIdConnect("https://auth.example.com/.well-known/openid-configuration")
    }
}
```

- `scope(이름, 설명)` 은 모든 flow 의 `scopes` 맵을 채웁니다. operation 이 쓰는데 `scope()` 로 선언하지 않은 scope 도 설명 없이(빈 문자열) 들어가므로, 설명을 붙이려면 선언합니다.
- 메서드는 위치 인자를 받습니다: `clientCredentials(tokenUrl)`, `authorizationCode(authorizationUrl, tokenUrl)`, `password(tokenUrl)`, `implicit(authorizationUrl)`. Java 메서드라서 Kotlin 이름 있는 인자(`tokenUrl = ...`)는 컴파일되지 않습니다.
- Groovy 빌드 스크립트에서도 같은 확장을 씁니다.
- operation 이 쓰는 스키마를 `openApiSecurity` 에 설정하지 않았으면 빌드가 그 operation 과 추가할 설정을 경고로 알립니다. OpenAPI 는 `oauth2` 에 flow 를, `openIdConnect` 에 URL 을 요구하므로, 올바른 스키마를 쓸 수 없으면 그 operation 의 보안 요구사항은 뺍니다.
- `bearerAuth()` 와 마찬가지로 REST Docs 스니펫은 바뀌지 않습니다.

## 공통 응답 래퍼

모든 API 가 `{ "code": 200, "message": "OK", "data": { ... } }` 처럼 응답을 감싼다면 래퍼를 yml 이나 코드에서 한 번 등록합니다. `data-path`(`dataPath`)의 기본값은 `data` 입니다.

```yaml
kardx:
  response-wrapper:
    class: com.example.common.ApiResponse
    data-path: data
```

```kotlin
RestDocsConfiguration.configure {
    extraction.responseWrapper<ApiResponse<*>>(dataPath = "data")
    // 또는: extraction.responseWrapper = ResponseWrapper(ApiResponse::class, dataPath = "data")
}
```

| 함수 | 결과 |
|---|---|
| `wrappedResponseFieldsOf<UserResponse>()` | `code`, `message`, `data` (Object), `data.id` … |
| `wrappedResponseFieldsOf<List<UserResponse>>()` | `data` (Array), `data[].id` … |
| `wrappedResponseFieldsOf<PageResponse<UserResponse>>()` (`total`·`content` 를 가진 직접 만든 페이지 클래스) | `data.total`, `data.content[].id` … |
| `wrappedResponseFieldsOf<Page<UserResponse>>()` (Spring Data `Page`) | `data.content[].id`, `data.totalElements`, `data.totalPages` … ([Spring Data 페이지](#spring-data-페이지)) |
| `wrappedResponseFieldsOf<UserResponse>(dataOptional = true)` | 데이터가 없을 수 있는 응답. 데이터 필드가 모두 optional |
| `wrappedResponseFieldsOf(PageResponse::class, UserResponse::class, "data.content[]")` | 제네릭이 아닌 중간 래퍼 |
| `wrappedResponseFieldsOf("data.id" type NUMBER means "ID")` | 래퍼 필드 + 직접 기술한 데이터 필드 |

위 함수는 목록을 돌려줍니다. `andDocument { }` 블록에서 그대로 등록할 때는 같은 인자의 단축형 `wrappedResponseFields<UserResponse>()`, `wrappedResponseFields<UserResponse>(dataOptional = true)`, `wrappedResponseFields("data.id" type NUMBER means "ID")` 를 씁니다 (`responseFields(wrappedResponseFieldsOf<T>())` 와 같음). 제네릭이 아닌 중간 래퍼(`KClass` 판)는 단축형이 없으니 `responseFields(wrappedResponseFieldsOf(PageResponse::class, UserResponse::class, "data.content[]"))` 로 씁니다.

```kotlin
.andDocument {
    wrappedResponseFields<UserResponse>()
}
```

`data` 줄의 설명은 래퍼 클래스 `data` 프로퍼티의 설명(`@Description` 이나 [`description-annotations`](#다른-어노테이션에서-설명-가져오기))을 씁니다. 설명이 없으면 `No description` 대신 데이터 타입 이름(`wrappedResponseFieldsOf<UserResponse>()` 이면 `UserResponse`)을 씁니다. 중간 래퍼의 데이터 프로퍼티(`data.content`)도 같습니다. 래퍼를 등록하지 않았으면 데이터 필드만 루트에서 추출합니다. 함수와 단축형은 `responseFieldsOf` 와 같은 `io.github.kardx.extractor` 패키지에 있습니다 (`import io.github.kardx.extractor.wrappedResponseFieldsOf`, `...extractor.wrappedResponseFields`).

## 여러 상태 코드 문서화

한 API 의 성공·오류 응답은 상태 코드마다 테스트를 하나씩 써서 문서화합니다. 테스트를 따로 쓸 만큼은 아닌 오류는 있는 테스트에서 `errorResponse(...)` 로 선언합니다 ([선언한 오류 응답](#선언한-오류-응답)).

- **OpenAPI(Swagger)**: 같은 경로·메서드의 테스트가 operation 하나로 합쳐지고, 상태 코드마다 응답이 생깁니다. 예를 들어 `GET /products/{id}` 아래에 `200` 과 `404` 가 함께 들어갑니다.
- **REST Docs 문서**: 테스트마다 섹션이 생기고, 섹션 맨 위 요약에 응답 상태가 붙습니다 (`GET /products/{id}` → `404 Not Found`).

```kotlin
@Test
@DisplayName("상품 조회")
fun getProduct() {
    mockMvc
        .perform(get("/api/products/{id}", 1L))
        .andStatus(HttpStatus.OK)
        .andDocument {
            pathParameters("id" pathMeans "상품 ID")
            wrappedResponseFields<ProductResponse>()
        }
}

@Test
@DisplayName("상품 조회 - 없는 상품 (404)")
fun getProductNotFound() {
    mockMvc
        .perform(get("/api/products/{id}", 999L))
        .andStatus(HttpStatus.NOT_FOUND)
        .andDocument {
            description = "상품이 없으면 404 와 오류 메시지를 돌려준다."
            pathParameters("id" pathMeans "상품 ID")
            responseFields<ErrorResponse>()        // 오류 응답 공통 클래스
        }
}

@Test
@DisplayName("상품 등록 - 잘못된 입력 (400)")
fun createProductInvalid() {
    mockMvc
        .perform(
            post("/api/products")
                .jsonContent(CreateProductRequest(name = "", price = BigDecimal("-1"))),
        )
        .andStatus(HttpStatus.BAD_REQUEST)
        .andDocument {
            requestFields<CreateProductRequest>()
            responseFields<ErrorResponse>()        // errors 에 필드 오류 목록
        }
}
```

오류 응답의 스키마 이름(`ErrorResponse`)은 테스트마다 정하지 않고 설정에서 한 번 지정합니다 ([오류 응답은 한 클래스로](#오류-응답은-한-클래스로)).

| 오류 응답 모양 | 블록에서 쓰는 응답 필드 |
|---|---|
| 오류 전용 클래스 (권장) | `responseFields<ErrorResponse>()` |
| 공통 래퍼, `data` 가 null | `wrappedResponseFields<Nothing?>()` |
| 공통 래퍼, `data` 가 오류 상세 | `wrappedResponseFields<List<FieldErrorDetail>>()` |

- operation 의 요약·설명은 성공 응답 테스트(200, 201, 202, 204, 그 밖의 2xx 순)의 값입니다(Gradle 플러그인). 오류 테스트의 설명은 REST Docs 문서의 그 섹션에서 보입니다.
- 응답 설명(`description`)은 상태의 reason phrase 입니다 (`OK`, `Not Found`). 그 응답을 만든 테스트의 요약이 operation 요약과 다르면 덧붙입니다: `Not Found: 상품 조회 - 없는 상품 (404)` (Gradle 플러그인. epages 는 `"404"` 로 둔다).
- 요청·응답 예시는 테스트마다 하나씩 들어가고 이름은 스니펫 디렉터리, 요약은 테스트 `@DisplayName` 입니다. Swagger UI 의 예시 선택 목록에 요약이 보이고, 요청 예시는 성공 응답 테스트의 것이 먼저입니다.
- 문서에 넣지 않을 오류 검증 테스트는 `andDocument` 를 부르지 않으면 됩니다. 문서와 스펙 어디에도 나오지 않습니다. (실제로 **실패한** 테스트는 [자동으로 빠집니다](asciidoc.md#문서-자동-생성).)
- 오류 테스트에서도 문서화한 요청 필드는 실제 요청과 맞아야 합니다. 검증 실패를 일으키는 값이라도 필드 자체는 보내야 합니다.

### 오류 응답은 한 클래스로

오류 응답(4xx·5xx)은 보통 모든 API 가 같은 클래스를 씁니다. 그렇다면 스키마 이름을 설정에서 한 번만 정합니다. 테스트에서 `responseSchema` 를 정하지 않은 오류 응답에 자동으로 붙습니다.

```kotlin
data class ErrorResponse(
    @Description("오류 코드") val code: Int,
    @Description("오류 메시지") val message: String,
    @Description("잘못된 입력 필드. 검증 오류일 때만") val errors: List<FieldErrorDetail>? = null,
)
```

지정은 **사용하는 프로젝트**에서 합니다. 라이브러리는 기본값이 없습니다(지정하지 않으면 추출한 타입 이름을 씁니다). 둘 중 하나를 고르세요.

**코드로** — 테스트 기본 클래스에서 MockMvc 를 만들기 전에 지정합니다. MVC 샘플(Boot 3·4)이 이 방식이고, 설정을 `DocsConfig` 객체에 모아 둡니다 (`samples/*/src/test/.../support/DocsConfig.kt`).

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
abstract class RestDocsTestSupport {
    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpRestDocs(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        RestDocsConfiguration.configure { openApi.errorSchema<ErrorResponse>() }
        // openApi.errorSchema(ErrorResponse::class), openApi.errorSchema = "ErrorResponse" 도 같다
        mockMvc = restDocsMockMvc(context, provider)
    }
}
```

**yml 로** — `src/test/resources/application-docs.yml` 같은 테스트 설정에 씁니다. WebFlux 샘플(`samples/spring-boot-4-webflux/src/test/resources/application-docs.yml`)이 이 형식을 씁니다.

```yaml
kardx:
  open-api:
    error-schema: com.example.common.ErrorResponse   # 클래스 이름(FQCN) 또는 스키마 이름
```

그러면 오류 테스트에서는 필드만 적습니다. 404 와 400 모두 `#/components/schemas/ErrorResponse` 를 가리킵니다.

```kotlin
mockMvc
    .perform(get("/api/products/{id}", 999L))
    .andStatus(HttpStatus.NOT_FOUND)
    .andDocument { responseFields<ErrorResponse>() }
```

**같은 이름의 구조가 다르면 합쳐집니다.** 예를 들어 404 는 `errors` 없이, 400 은 `errors` 와 함께 문서화해도 Gradle 플러그인이 두 모양을 합친 `ErrorResponse` 하나(`errors` 는 선택)로 맞춥니다 ([스키마 이름](#스키마-이름)). 같은 경로의 타입이 서로 다르면(`data` 가 한쪽은 객체, 한쪽은 배열) 합칠 수 없어 경고하므로 테스트마다 다른 이름을 주세요. 위처럼 오류 전용 클래스 하나에 선택 필드(`errors`)를 두면 처음부터 한 스키마로 정확하게 표현됩니다.

### 선언한 오류 응답

오류(400, 401, 403, 409 …)마다 테스트를 쓰려면 잘못된 입력, 없는 토큰, 겹치는 데이터를 일부러 만들어야 해서 오류 하나에 비해 품이 많이 듭니다. 이런 오류는 이미 있는 테스트의 블록에서 선언합니다. 그 테스트의 문서와 OpenAPI operation 에 들어갑니다.

```kotlin
@Test
@DisplayName("상품 등록")
fun createProduct() {
    mockMvc
        .perform(
            post("/api/v1/products")
                .jsonContent(CreateProductRequest(name = "키보드", price = BigDecimal("59000"))),
        )
        .andStatus(HttpStatus.CREATED)
        .andDocument {
            requestFields<CreateProductRequest>()
            wrappedResponseFields<ProductResponse>()

            errorResponse(HttpStatus.UNAUTHORIZED, "토큰이 없거나 만료됨")
            errorResponse(HttpStatus.BAD_REQUEST, "입력값 오류") {
                fields<ErrorResponse>()
                example(ErrorResponse(400, "입력값이 올바르지 않습니다", listOf(FieldErrorDetail("name", "비어 있을 수 없습니다"))))
            }
            errorResponse(HttpStatus.CONFLICT, "이미 등록된 상품명", ErrorResponse(409, "이미 등록된 상품명입니다"))
        }
}
```

| 형태 | 문서에 나가는 것 |
|---|---|
| `errorResponse(409, "...")` | 상태와 설명만 |
| `errorResponse(409, "...") { fields(...) }`, `{ fields<ErrorResponse>() }` | 본문 필드: 필드 표와 OpenAPI 응답 스키마. `fields<T>()` 는 응답 규칙으로 추출합니다 (`import io.github.kardx.extractor.fields`) |
| `{ example("""{"code":409}""") }` | 본문 예시 (JSON 글) |
| `{ example(ErrorResponse(...)) }` | 본문 예시. 응답 본문과 같은 Jackson 매퍼로 직렬화합니다 (`@JsonProperty`, 이름 규칙, null 처리가 그대로 반영됨). `String` 은 JSON 글로 보고 그대로 씁니다 |
| `{ contentType = "application/problem+json" }` | 미디어 타입. 기본값 `application/json`, `+json` 으로 끝나면 JSON 으로 봅니다 |
| `{ contentType = "text/plain"; example("Too many requests") }` | JSON 이 아닌 본문(`text/plain`, `text/html` …). 예시는 글자 그대로 씁니다 |
| `errorResponse(409, "...", ErrorResponse(...))` | 한 줄 형태: 객체의 런타임 클래스에서 필드를 추출하고, 객체를 예시로 씁니다 |

상태는 `Int` 또는 `HttpStatusCode`(`HttpStatus.CONFLICT`)로 줍니다.

**나가는 것**

- REST Docs: `error-responses` 스니펫이 그 테스트 섹션의 `Error Responses` 소제목 아래 들어갑니다. 상태·설명 표(`409 Conflict`)가 먼저 오고, 필드나 예시가 있는 선언마다 `409 Conflict 응답 필드` 표와 `409 Conflict 응답 예시` 블록이 이어집니다. 표 머리글과 제목은 [문서 언어](configuration.md#문서-언어)를 따릅니다.
- OpenAPI(Gradle 플러그인): 상태마다 설명·예시·본문 스키마가 있는 응답이 생깁니다. 선언한 필드는 `components.schemas` 의 스키마가 됩니다. 똑같은 스키마가 이미 있으면(다른 테스트의 `ErrorResponse` 등) 그것을 쓰고, 없으면 상태 이름으로 짓습니다(`ConflictResponse`, 그 이름을 이미 쓰면 operationId 를 앞에 붙임). 필드가 없으면 JSON 본문은 [공통 오류 응답](#공통-오류-응답)의 공통 오류 스키마를 가리킵니다 (있을 때). JSON 이 아니고 필드도 없는 본문은 `type: string` 이 되고 예시도 문자열로 들어가며, 공통 JSON 오류 스키마는 붙지 않습니다.
- 그래서 본문 없는 선언은 두 출력이 다릅니다. `errorResponse(400, "Invalid input")` 은 HTML 문서에 상태와 설명만 보이지만, OpenAPI 응답에는 공통 오류 본문이 붙습니다 (`open-api.error-schema` 로 정한 스키마나 오류 응답이 가장 많이 가리키는 스키마. 미디어 타입은 그 스키마를 쓰는 오류 응답이 달리 정하지 않았으면 `application/json`). HTML 에도 본문을 보이려면 필드나 예시를 줍니다.
- 스키마는 모양만 비교하고 `contentType` 은 보지 않습니다. 선언한 본문이 이미 있는 스키마와 모양이 같으면 `application/problem+json` 으로 선언했고 기존 스키마가 `application/json` 응답에서 왔더라도 그 스키마 이름(`ErrorResponse` 등)을 그대로 씁니다.
- 같은 operation 의 여러 테스트가 같은 상태를 선언하면 합칩니다. 설명이 다르면 스니펫 경로 순으로 앞선 것을 쓰고 빌드 로그에 `OpenAPI declared responses: ... is declared with different descriptions ...` 경고가 나옵니다.

operation 한 상태에서 **어느 응답이 이기는지**:

1. 테스트가 실제로 받은 응답
2. 선언한 응답
3. `openApiCommonResponses` 의 공통 응답

테스트가 실제로 받은 상태와 같은 선언은 실제 응답이 이미 문서화하므로 경고 없이 스니펫과 OpenAPI 에서 모두 빠집니다. 선언한 401·403 은 공통 응답과 달리 operation 에 보안 요구사항이 없어도 더합니다.

**제한**

- 400~599 만 됩니다. 범위 밖 상태, 빈 설명, 빈 `contentType`·예시, 한 블록에서 같은 상태를 두 번 선언하면 `IllegalArgumentException` 입니다.
- **선언일 뿐 검증하지 않습니다.** API 가 정말 이 상태·본문을 돌려주는지 아무것도 확인하지 않습니다. 확인하는 것은 하나입니다: 필드와 JSON 예시를 함께 주면 예시가 필드와 맞아야 하고(문서화하지 않은 필드나 빠진 필수 필드는 `DocumentationMismatchException`), JSON 미디어 타입의 예시는 올바른 JSON 이어야 합니다.
- 한 줄 형태는 컬렉션·배열·`Map`, 제네릭 클래스(`RestResponse<ErrorBody>`), 문자열 같은 값에서 필드를 추출하지 못해 필드 없이 예시만 남습니다. 블록 형태로 타입을 적습니다: `{ fields<RestResponse<ErrorBody>>(); example(...) }`.
- 선언은 [공통 응답 래퍼](#공통-응답-래퍼)로 감싸지 않습니다. 오류 본문도 래퍼로 감싼다면 래퍼 객체를 예시로 넘기고 필드도 래퍼 타입에서 추출합니다.

### 공통 오류 응답

401(인증 실패)이나 500(서버 오류)처럼 거의 모든 API 가 돌려줄 수 있지만 API 마다 테스트하지 않는 응답은 Gradle 플러그인 설정에서 한 번 선언합니다.

```kotlin
kardx {
    openApiCommonResponses.put(401, "토큰이 없거나 만료됨")
    openApiCommonResponses.put(500, "서버 오류")       // 빈 설명("")이면 표준 문구(Internal Server Error)
    // openApiCommonResponseSchema = "ErrorResponse"   // 기본: 오류 응답이 가장 많이 가리키는 스키마
}
```

- 각 operation 에서 테스트가 문서화하거나 [선언](#선언한-오류-응답)하지 않은 상태 코드에만 더합니다. 테스트로 문서화하거나 선언한 응답은 그대로입니다.
- 401·403 은 보안 요구사항(security)이 있는 operation 에만 더합니다.
- 응답 본문은 공통 오류 스키마를 가리킵니다: `openApiCommonResponseSchema` 로 정한 이름, 정하지 않으면 스펙에서 오류 응답(4xx·5xx)이 가장 많이 가리키는 스키마(보통 위의 `open-api.error-schema`)입니다. 그런 스키마가 없거나 정한 이름이 스펙에 없으면(경고) 설명만 넣습니다.
- REST Docs HTML 에는 index 문서 끝에 "공통 오류 응답"(기본 언어 `en` 이면 `Common error responses`) 표가 한 번 들어갑니다 (앵커 `common-error-responses`). 테스트 클래스별 문서에는 넣지 않습니다.
- Postman Collection 은 바꾸지 않습니다.

이 설정과 [`errorResponse(...)`](#선언한-오류-응답)는 **테스트로 확인하지 않은 내용이 문서에 들어가는 두 곳**입니다. 여기 적은 응답은 실제로 그 모양으로 나가는지 아무도 검증하지 않습니다. 그래서 기본으로 꺼져 있습니다. 중요한 오류 응답은 테스트로 문서화하세요.

## OpenAPI 이름과 메타데이터

OpenAPI 스펙에서 operation 과 스키마의 이름, 묶음을 정하는 설정입니다. 여러 상태 코드를 가진 API 만이 아니라 문서화한 모든 요청에 적용됩니다.

### 스키마 이름

스키마 이름은 OpenAPI 의 `components.schemas` 에 본문 구조를 등록하는 이름입니다. 요청·응답은 `$ref: "#/components/schemas/TodoResponse"` 로 그것을 가리킵니다. REST Docs 문서와 필드 검증에는 영향이 없습니다.

대부분은 `openApi { requestSchema / responseSchema }` 를 쓸 필요가 없습니다. 타입에서 추출한 본문은 이름이 자동으로 붙기 때문입니다. 직접 지정하는 경우는 둘뿐입니다. 자동 이름을 다른 이름으로 바꾸고 싶을 때(예: `ApiResponseTodoResponse` 대신 `TodoResponse`), 그리고 필드를 모두 직접 썼거나 서로 다른 타입·경로에서 추출한 목록을 섞어 자동 이름이 없을 때입니다. 추출한 목록을 고쳐도(`filterNot`, `except`, 직접 쓴 필드를 `+`) 자동 이름은 남습니다.

**기본으로 자동으로 붙습니다.** `responseFieldsOf<T>()`, `requestFieldsOf<T>()`, `wrappedResponseFieldsOf<T>()` 로 추출한 본문은 그 타입의 이름을 씁니다. 블록 단축형(`responseFields<T>()`, `requestFields<T>()`, `wrappedResponseFields<T>()`)도 이 함수를 부르므로 같습니다.

| 추출 | 스키마 이름 |
|---|---|
| `requestFieldsOf<CreateTodoRequest>()` | `CreateTodoRequest` |
| `responseFieldsOf<ErrorResponse>()` | `ErrorResponse` |
| `wrappedResponseFieldsOf<TodoResponse>()` (래퍼 `ApiResponse`) | `ApiResponseTodoResponse` |
| `wrappedResponseFieldsOf<PageResponse<TodoResponse>>()` | `ApiResponsePageResponseTodoResponse` |
| `responseFieldsOf<TodoResponse>("[]")` | `ListTodoResponse` |
| 다른 경로로 추출(`responseFieldsOf<T>("data")`), 직접 쓴 필드만, 서로 다른 타입·경로에서 추출한 목록을 섞은 것 | 없음 (epages 가 `api-todos-1290543316` 같은 이름을 만든다) |
| 필드가 없는 본문(값 하나인 `String`·`Long`, 파일), `relaxed = true` | 없음 (빈 객체 스키마에 타입 이름을 붙이지 않는다) |

이름은 다음 순서로 먼저 정해진 것을 씁니다.

1. 테스트의 `openApi { responseSchema ... }`
2. 오류 응답(4xx·5xx)이면 `openApi.errorSchema` ([오류 응답은 한 클래스로](#오류-응답은-한-클래스로))
3. 추출한 타입의 이름

| 설정 | yml | 설명 |
|---|---|---|
| `openApi.schemaNames` | `open-api.schema-names` | 자동 이름을 켜고 끈다. 기본값 true |
| `openApi.schemaNamesWithoutWrapper` | `open-api.schema-names-without-wrapper` | 공통 응답 래퍼 이름을 뗀다 (`ApiResponseTodoResponse` → `TodoResponse`). 기본값 false. 아래 설명 |
| `openApi.errorSchema` | `open-api.error-schema` | 오류 응답 공통 이름 (이름 또는 클래스 이름) |
| `openApi.schemaNameResolver` | 없음 (코드 전용) | 타입 → 이름 규칙. 예: `SchemaNameResolver { "Dto" + SchemaNames.of(it.type) }` (`io.github.kardx.openapi.SchemaNames`) |

**래퍼 이름을 떼려면** yml 한 줄을 켭니다. `wrappedResponseFieldsOf<T>()` 로 추출한 응답의 자동 이름만 바뀌고, 스키마 내용(래퍼 필드 `code`·`message`·`data`)은 그대로입니다.

```yaml
kardx:
  open-api:
    schema-names-without-wrapper: true
```

| 추출 (래퍼 `ApiResponse`) | 기본 | 켰을 때 |
|---|---|---|
| `wrappedResponseFieldsOf<TodoResponse>()` | `ApiResponseTodoResponse` | `TodoResponse` |
| `wrappedResponseFieldsOf<List<TodoResponse>>()` | `ApiResponseListTodoResponse` | `ListTodoResponse` |
| `wrappedResponseFieldsOf(PageResponse::class, TodoResponse::class, "data.content[]")` | `ApiResponsePageResponseTodoResponse` | `PageResponseTodoResponse` |
| `wrappedResponseFieldsOf<Unit>()` | `ApiResponseUnit` | `ApiResponseUnit` (데이터가 `Unit`·`Any` 면 그대로) |

뗀 이름이 모양이 다른 다른 스키마와 겹치면(같은 `TodoResponse` 를 요청 본문으로도 쓰거나, 래퍼 없이 돌려주는 다른 API 가 있을 때) 아래 규칙대로 플러그인이 나눕니다. 이때 래퍼 응답의 다음 후보는 래퍼를 붙인 이름(`ApiResponseTodoResponse`)이고, 바뀐 내용은 빌드 로그에 남습니다.

**이름이 겹치면 Gradle 플러그인이 정리합니다.** 이름은 패키지를 넣지 않으므로 겹칠 수 있고, epages 는 같은 이름의 스키마 중 하나만 남깁니다. 플러그인은 OpenAPI 를 만들기 전(`apiDocsOpenApiSnippets`)에 같은 이름인데 구조(필드 경로·타입·필수 여부)가 다른 스키마를 찾아 다음 규칙으로 정리하고, 바꾼 내용을 빌드 로그에 남깁니다 (`OpenAPI schema names: Item → OrderItem (...)`). 결과는 테스트 실행 순서와 상관없이 같습니다.

| 상황 | 결과 |
|---|---|
| 자동 이름이 서로 다른 타입끼리 겹침 (`order.Item`, `cart.Item`) | 모든 타입의 이름이 달라지는 가장 짧은 단계로 한정한다: 바깥 클래스 이름(`OuterItem`), 그다음 패키지 이름을 뒤에서부터(`OrderItem`, `CartItem`, `ShopOrderItem` …). 타입 인자에도 같은 단계를 적용한다 |
| 같은 타입의 요청과 응답 모양이 다름 (`@JsonProperty(access = WRITE_ONLY/READ_ONLY)`, 생성자 밖 `val`) | 응답은 `Account`, 요청은 `AccountRequest` |
| 직접 정한 이름(`responseSchema`, `openApi.errorSchema`)과 자동 이름이 겹침 | 직접 정한 쪽이 이름을 갖고, 자동 이름이 위 규칙으로 바뀐다 |
| 직접 정한 이름끼리, 또는 같은 타입·같은 방향끼리 모양이 다름 | 필드를 합친 스키마 하나로 맞춘다. 모든 곳에 있고 필수인 필드만 필수로 남는다 |
| 위에서 같은 경로의 타입이 서로 다름 (`value` 가 String 과 Number) | 합칠 수 없으므로 경고(`warn`)하고 `VARIES` 로 둔다. 테스트마다 다른 이름을 주세요 |

직접 정한 이름은 바꾸지 않고 합치는 이유는, 그 이름을 다른 도구(클라이언트 생성기 등)가 쓰고 있을 수 있어서입니다. 합친 스키마도 모든 응답을 정확히 표현합니다(일부에만 있는 필드는 선택). 플러그인 없이 쓰면 이 정리가 없으므로 이름이 겹치지 않게 `responseSchema` 를 주세요.

테스트에서 직접 정할 때는 이렇게 씁니다.

```kotlin
openApi {
    requestSchema<CreateTodoRequest>()                 // CreateTodoRequest
    responseSchema<ApiResponse<TodoResponse>>()        // ApiResponseTodoResponse (제네릭은 이어 붙인다)
    // responseSchema(ErrorResponse::class), responseSchema = "ErrorResponse" 도 같다
}
```

### operationId

OpenAPI 의 `operationId` 는 Swagger UI 의 딥 링크(`#/<태그>/<operationId>`)와 클라이언트 코드 생성기(OpenAPI Generator 등)의 메서드 이름이 됩니다. 기본값은 **요청을 처리한 컨트롤러(핸들러) 메서드 이름**입니다.

```kotlin
@RestController
class UserController {
    @GetMapping("/api/v1/users/search")
    fun searchUsers(...) = ...      // operationId: searchUsers
}
```

- MockMvc 는 `MvcResult.handler`, WebTestClient 는 `restDocsWebTestClient` 나 `recordHandlers()` 를 건 클라이언트가 기록한 핸들러([WebTestClient](webtestclient.md#operationid))에서 이름을 읽습니다. Kotlin `suspend` 함수, `internal` 함수(JVM 이름 `name$module`), CGLIB 프록시 컨트롤러도 소스의 이름이 나옵니다.
- 같은 operation(HTTP 메서드 + 경로)을 여러 테스트가 문서화해도 id 는 하나입니다.
- 서로 다른 operation 에 같은 id 가 생기면(여러 컨트롤러의 `get`, `create`, 오버로드) **겹친 것만** 컨트롤러 이름을 앞에 붙입니다. 컨트롤러 이름에서 `RestController`·`Controller`·`Api` 접미사는 뗍니다 (`ProductController.get` → `productGet`, `CouponController.get` → `couponGet`). 그래도 겹치면 경로 순으로 뒤에 숫자를 붙입니다(`userGet2`). 경로·HTTP 메서드 순으로 처리하므로 테스트 실행 순서와 상관없이 같은 결과입니다.
- 핸들러를 알 수 없는 요청은 테스트 이름으로 만듭니다(아래 `test-name`): 함수형 라우터(`RouterFunction`), 정적 리소스, 핸들러를 기록하지 않은 WebTestClient(`bindToServer`, `recordHandlers()` 없는 `bindToController`·`bindToRouterFunction`), RestTestClient.

**바꾸는 방법은 세 가지이고, 앞의 것이 이깁니다.**

1. 테스트에서 직접: `openApi { operationId = "searchUsersByNickname" }`. Gradle 플러그인이 글자를 바꾸거나 번호를 붙이지 않고 그대로 씁니다. 다른 operation 과 겹치면 빌드 로그에 `OpenAPI operationId: ...` 경고가 나옵니다. 빈 문자열이나 공백이 있으면 `IllegalArgumentException` 입니다.
2. 직접 만든 규칙: `openApi.operationIdResolver = OperationIdResolver { input -> ... }`. `OperationIdInput`(HTTP 메서드, 경로 템플릿, 컨트롤러 단순 이름, 핸들러 메서드 이름, 테스트 기반 id)을 받아 id 를 돌려줍니다. `null` 이면 3 으로 만듭니다. 돌려준 id 는 1 처럼 그대로 쓰입니다(겹치면 경고).
3. 미리 정한 규칙 고르기: `openApi.operationIdRule`(yml `kardx.open-api.operation-id-rule`).

| 규칙 (yml 값) | `UserController.searchUsers` 의 `GET /api/v1/users/search` | 핸들러를 모를 때 |
|---|---|---|
| `HANDLER_METHOD` (`handler-method`, 기본) | `searchUsers`. 겹친 것만 `userSearchUsers` | 테스트 이름 |
| `CONTROLLER_AND_METHOD` (`controller-and-method`) | `userSearchUsers` | 테스트 이름 |
| `PATH` (`path`) | `getApiV1UsersSearch`. 경로 변수는 `By` + 이름(`/users/{id}` → `getUsersById`), 영문·숫자 밖의 글자는 낱말 경계 | 같은 규칙 (핸들러가 필요 없다) |
| `TEST_NAME` (`test-name`) | `user-api-docs-test.search-users` (스니펫 디렉터리 `class-name/method-name` 의 `/` 를 `.` 으로) | — |

```yaml
kardx:
  open-api:
    operation-id-rule: path
```

```kotlin
RestDocsConfiguration.configure {
    openApi.operationIdResolver = OperationIdResolver { input ->
        input.handlerMethodName?.let { "${input.controllerName?.removeSuffix("Controller")?.lowercase()}_$it" }
    }
}
```

`TEST_NAME` 규칙이나 테스트 이름 대체에서는 한글 테스트 이름(Kotest, JUnit 백틱 메서드)이 id 에 그대로 남습니다. OpenAPI 로는 유효하지만 코드 생성기가 메서드 이름을 만들지 못할 수 있으므로 그 테스트에 1 을 쓰거나 `PATH` 규칙을 고릅니다([Kotest](kotest.md#index-와-openapi)).

operationId 는 OpenAPI 스펙에만 쓰입니다. 스니펫 디렉터리, AsciiDoc 섹션 앵커, WireMock 매핑 파일 이름, 스키마 이름은 그대로 테스트 이름에서 나옵니다.

### OpenAPI 태그와 요약

모든 operation 에 태그와 요약(`summary`)이 들어갑니다. Swagger UI 는 태그로 API 를 묶고, 요약을 목록의 한 줄 설명으로 보여 줍니다.

| 항목 | 값 |
|---|---|
| 태그 | 문서 클래스의 `@DisplayName`, 없으면 클래스 단순 이름. AsciiDoc 문서 제목과 같습니다. 문서 클래스는 JUnit `@Nested` 면 가장 바깥 클래스, Kotest 는 스펙입니다 |
| 요약 (JUnit) | 테스트 메서드의 `@DisplayName`, 없으면 메서드 이름 (`` fun `사용자 조회`() `` → `사용자 조회`, `fun getUser()` → `getUser`) |
| 요약 (Kotest) | 섹션 제목 ([Kotest](kotest.md#섹션-제목)) |

- `@ParameterizedTest` 의 실행들은 메서드의 요약을 함께 씁니다 (실행마다의 표시 이름 `[1] 1` 이 아니라).
- 테스트가 만든 다른 스레드에서 문서화해도 같은 값입니다.
- `openApi { tags("마이페이지") }` 는 태그를 더합니다. 기본 태그는 그대로 남습니다.
- AsyncAPI 메시지의 태그·요약도 같은 규칙입니다 ([메시지 문서화](messaging.md)).

**요약·설명 직접 정하기.** 여러 테스트가 같은 operation 을 문서화하면(하위 타입마다, 또는 성공·실패 예시) 합친 operation 은 대표 테스트(성공 응답 테스트 먼저, 그다음 스니펫 디렉터리 이름순)의 요약을 받아 API 가 아니라 예시 하나처럼 읽힐 수 있습니다(`계좌이체 결제`). operation 자체의 이름은 `openApi { summary }` 로, OpenAPI 에만 넣을 설명은 `openApi { description }` 으로 정합니다.

```kotlin
.andDocument {
    description = "계좌이체로 결제한다."
    openApi {
        summary = "결제 요청"            // 테스트 이름 대신 operation 의 summary
        description = "주문을 결제한다. 결제 수단은 method.type 으로 고른다."  // 블록 description 대신
    }
}
```

- Gradle 플러그인이 epages 가 조각을 합친 뒤 operation 에 넣고, 버전별 스펙(`splitSpecs`)에도 같은 값이 들어갑니다. HTTP Client 내보내기(`api.http`)의 `###` 제목도 스펙의 summary 를 따릅니다.
- `summary` 만 정했고 operation 에 따로 설명이 없었으면(요약을 설명으로 쓰고 있었으면) 설명도 새 summary 를 따릅니다.
- 같은 operation 의 테스트들이 서로 다른 값을 정하면 스니펫 디렉터리 이름순으로 처음 값을 쓰고 빌드 로그에 `OpenAPI summary/description: POST /api/v1/payments: ...` 경고가 남습니다. 테스트마다 같은 값을 적는 것은 괜찮습니다.
- REST Docs 산출물은 바뀌지 않습니다: `api-summary`, 섹션 제목, OpenAPI 예시 이름(예시마다의 `summary`)은 테스트 이름 그대로입니다. `Created` 같은 응답 설명도 테스트 이름 기준으로 정합니다.
- Gradle 플러그인 없이 쓰면 값은 `openapi-extras.json` 에만 남습니다.
- `openApi { summary }` 가 없으면 `sourceDocs { enabled = true; methodDocs = true }` 일 때 컨트롤러 메서드의 KDoc·Javadoc 이 테스트 이름보다 먼저입니다 ([컨트롤러 메서드 → OpenAPI summary·description](#컨트롤러-메서드--openapi-summarydescription)).

### Enum 은 드롭다운으로

Enum 타입 필드와 파라미터는 상수 목록이 OpenAPI 의 `enum` 으로 나갑니다. Swagger UI 의 Try it out 에서 쿼리·경로 파라미터는 드롭다운으로 고르고, 스키마에는 가능한 값이 표시됩니다.

```yaml
- name: status
  in: query
  schema:
    type: string
    enum: [TODO, DOING, DONE]
```

- `parametersOf<T>()`, `responseFieldsOf<T>()` 같은 추출은 자동으로 넣습니다.
- 직접 쓸 때는 `enumOf` 를 붙입니다: `"status" queryMeans "상태" enumOf TodoStatus::class`, `"status" type ENUM means "상태" enumOf TodoStatus::class`. 파라미터는 `ofType TodoStatus::class` 도 같습니다.
- REST Docs 표의 타입 칸은 JSON 타입(`String`)이고, REST Docs 가 실제 값이 문자열인지 검증합니다. `@JsonValue` 가 숫자를 돌려주는 Enum 은 `Number` 로 검증합니다. 값은 JSON 에 실제로 나가는 값입니다 (`@JsonValue`, 상수의 `@JsonProperty`, 없으면 상수 이름).
- Enum 목록(`List<Level>`)은 타입 칸이 `Array` 이고, OpenAPI 에는 `type: array`, `items: {type: string, enum: [...]}` 로 나갑니다.
- AsyncAPI 스키마에도 같은 `enum` 이 들어갑니다.

### 정규식 경로 변수

Spring 의 정규식 경로 변수(`@GetMapping("/files/{name:.+}")`)는 OpenAPI 경로에서 정규식을 뺍니다. OpenAPI 경로는 `/files/{name}`, 파라미터 이름은 `name` 이라 `pathParameters("name" pathMeans "...")` 의 설명이 그대로 이어집니다. 정규식 안의 중괄호(`{code:[A-Z]{2}}`)도 처리합니다. REST Docs 문서(`api-summary`)의 URL 은 테스트에 쓴 템플릿 그대로입니다.

```kotlin
mockMvc.perform(get("/files/{name:.+}", "report.final.pdf"))
    .andDocument { pathParameters("name" pathMeans "파일 이름") }
```

### 값 하나인 JSON 본문

파일 본문(`application/pdf`, `application/octet-stream` …)과 `text/plain`·`text/csv` 본문은 [파일 다운로드](#파일-다운로드)를 봅니다.

값 하나인 JSON 본문(`Long`, `List<String>`)은 `responseFields<Long>()` 처럼 타입에서 추출했으면 그 타입의 스키마(`type: integer`, `format: int64`)가 됩니다. 필드 표는 없습니다 ([빈 추출 결과](#빈-추출-결과)).

## 파일 다운로드

파일을 내려주는 API 는 경로·쿼리 파라미터와 응답 헤더(`Content-Type`, `Content-Disposition`)를 문서화하고, 무슨 파일인지 `responseFile("...")` 로 적습니다. 본문에는 필드가 없으므로 `responseFields` 는 `responseFile` 과 함께 쓸 수 없습니다 (`responseFile cannot be used together with responseFields` 로 실패합니다). 받은 바이트는 `andReturnEntity<ByteArray>()` 로 확인합니다 (예제: [ProductApiDocsTest](../../samples/spring-boot-4/src/test/kotlin/com/example/restdocs/product/ProductApiDocsTest.kt) 의 `downloadManual`).

```kotlin
@Test
@DisplayName("상품 설명서 다운로드 (PDF)")
fun downloadManual() {
    val response = mockMvc.perform(get("/api/v1/products/{id}/manual", 1L).param("lang", "ko"))
        .andStatus(HttpStatus.OK)
        .andDocument {
            description = "상품 설명서 PDF 를 첨부 파일로 내려받는다. 파일 이름은 `Content-Disposition` 에 있다."
            pathParameters("id" pathMeans "상품 ID" ofType Long::class)
            queryParameters("lang" queryMeans "설명서 언어 (ko, en)" isOptional true withDefaultValue "ko")
            responseHeaders(
                "Content-Type" headerMeans "파일 형식 (`application/pdf`)",
                "Content-Disposition" headerMeans "첨부 파일과 파일 이름 (`attachment; filename=...`)",
            )
            responseFile("상품 설명서 PDF")
        }
        .andReturnEntity<ByteArray>()

    assertThat(response.headers.contentDisposition.filename).isEqualTo("product-1-manual-ko.pdf")
    assertThat(response.body).startsWith(*"%PDF-".toByteArray())
}
```

### 스니펫에 나가는 것

글자가 아닌 본문은 스니펫에 바이트 대신 크기만 나갑니다. 바이트를 그대로 두면 `http-response.adoc` 에 깨진 글자가 찍히고 큰 파일은 문서를 채우기 때문입니다. 헤더(`Content-Length` 포함)는 실제 값 그대로입니다.

```http
HTTP/1.1 200 OK
Content-Type: application/pdf;charset=UTF-8
Content-Disposition: attachment; filename="product-1-manual-ko.pdf"
Content-Length: 45

<binary: 45 bytes>
```

바이트를 보여 주지 않으므로 `response-file` 스니펫이 본문이 무엇인지 알려 줍니다. `responseFile` 의 설명, `charset` 을 뺀 미디어 타입, 실제 `Content-Disposition` 의 파일 이름, 테스트가 받은 본문의 크기입니다. 바이너리 본문이면 `responseFile` 이 없어도 만들고, 그때는 설명 행이 없습니다. 머리글은 [문서 언어](configuration.md#문서-언어)를 따릅니다 (아래는 `language = "ko"`).

```asciidoc
[cols="1h,3"]
|===
|설명
|상품 설명서 PDF

|미디어 타입
|`+application/pdf+`

|파일 이름 (예시)
|`+product-1-manual-ko.pdf+`

|크기 (예시)
|45바이트

|===
```

생성 문서에는 응답 헤더 다음에 `Response File` 소제목으로 들어갑니다. [`document.snippets`](customizing-snippets.md#문서에-넣을-스니펫-고르기) 에 `response-body` 를 넣었다면 이 operation 에서는 표가 그 자리를 대신합니다. 글자 본문(JSON, CSV …)은 `responseFile` 을 불렀을 때만(CSV 내보내기 등) 표가 생기고, 본문 내용도 스니펫에 그대로 남습니다.

| 결과물 | 바이너리 본문 |
|---|---|
| `http-response`, `response-body` | `<binary: N bytes>`. `response-body` 는 언어 없는 `[source]` 블록이다 (REST Docs 는 하위 타입을 써서 `[source,pdf]` 로 만든다) |
| `response-file`, `request-file` | 본문의 설명·미디어 타입·파일 이름·크기 |
| `http-request`, `request-body`, `curl-request`, `httpie-request` | 파일 업로드도 같다. `application/octet-stream` 요청 본문과 multipart 의 파일 파트가 `<binary: N bytes>` 가 된다 |
| WireMock 매핑 | 원래 바이트를 `base64Body` 로 쓴다 (stub 은 실제 파일을 돌려준다) |
| Postman Collection 의 예시 응답 | `<binary: N bytes>` |
| 사용자 전처리기 (`http.requestPreprocessors`·`http.responsePreprocessors`) | 크기 표시로 바뀐 본문을 받는다 |

글자로 보고 그대로 두는 Content-Type 은 `text/*`(`text/csv`, `text/plain` …), 하위 타입에 `json`·`xml` 이 들어간 것(`application/json`, `application/hal+json`, `application/problem+json`, `application/x-ndjson`, `image/svg+xml` …), `application/x-www-form-urlencoded`, YAML·JavaScript·GraphQL·SQL, `multipart/*`(파트는 파트의 Content-Type 으로 따로 본다)입니다. Content-Type 이 없는 본문도 그대로 둡니다. 그래서 CSV 내보내기는 문서에 내용이 그대로 나갑니다.

`restDocsMockMvc` 는 UTF-8 인코딩 필터를 응답에도 강제로 걸어서 `Content-Type` 에 `;charset=UTF-8` 이 붙습니다(위 예). 실제 앱의 응답 헤더와 다를 수 있습니다.

### 요청 본문 파일 업로드

본문 전체가 파일인 요청(multipart 가 아닌 `application/octet-stream`, `image/png` …)은 `requestFile("...")` 로 설명합니다. 같은 행의 `request-file` 표(`Request File` 소제목)와 OpenAPI 요청 본문 설명이 됩니다. multipart 업로드는 파일 파트를 `requestParts` 로 적으므로([multipart 와 쿠키](#multipart-와-쿠키)) `requestFile` 은 `requestParts`·`requestFields` 와 함께 쓸 수 없습니다.

```kotlin
mockMvc.perform(put("/api/v1/products/{id}/image", 1L).contentType(MediaType.IMAGE_PNG).content(png))
    .andStatus(HttpStatus.NO_CONTENT)
    .andDocument {
        pathParameters("id" pathMeans "상품 ID")
        requestFile("업로드할 이미지 원본")
    }
```

### OpenAPI 결과

본문 필드가 없으므로 epages 는 빈 객체 스키마를 만듭니다. Gradle 플러그인이 `openapi3` 뒤에 JSON 이 아닌 본문의 빈 스키마를 `type: string` 으로 바꿉니다. `text/*` 가 아니면 `format: binary` 를 붙이고, 예시(`examples`)를 지우고, 미디어 타입의 `charset` 파라미터를 뺍니다 (`application/pdf;charset=UTF-8` → `application/pdf`. 바이너리 본문에는 문자 집합이 없습니다). `text/plain`·`text/csv` 는 `type: string` 이고 예시와 미디어 타입이 그대로 남습니다. `responseFile`·`requestFile` 의 설명은 본문 스키마의 `description` 이 되고, 파일 응답에 문서화한 `Content-Disposition` 헤더에는 실제 값이 `example` 로 들어갑니다. 문서화한 응답 헤더는 응답 `headers` 가 됩니다.

```yaml
/api/v1/products/{id}/manual:
  get:
    operationId: manual
    parameters:
    - name: id
      in: path
      description: 상품 ID
      required: true
      schema:
        type: integer
        format: int64
    - name: lang
      in: query
      description: 설명서 언어 (ko, en)
      required: false
      schema:
        type: string
        default: ko
    responses:
      "200":
        description: OK
        headers:
          Content-Type:
            description: 파일 형식 (`application/pdf`)
            schema:
              type: string
          Content-Disposition:
            description: 첨부 파일과 파일 이름 (`attachment; filename=...`)
            schema:
              type: string
            example: attachment; filename="product-1-manual-ko.pdf"
        content:
          application/pdf:
            schema:
              type: string
              format: binary
              description: 상품 설명서 PDF
```

Gradle 플러그인 없이 epages 플러그인만 쓰면 빈 객체 스키마, 미디어 타입의 `charset`, 예시 `<binary: N bytes>` 가 그대로 남고 파일 설명은 들어가지 않습니다.

## OpenAPI 스펙에 나가는 정보

추출기와 수식어는 REST Docs 표보다 자세한 정보(정수인지, 날짜 형식인지, 길이 제한 등)를 디스크립터 속성으로 남깁니다. epages 는 이 속성을 읽지 않으므로 Gradle 플러그인(`io.github.kardx-project`)이 `openapi3` 뒤에 생성된 스펙에 합칩니다. 결과는 테스트 실행 순서와 상관없이 같고, Boot 3(epages 0.19)·Boot 4(0.20) 모두 같습니다. 플러그인 없이 epages 만 쓰면 [아래](#gradle-플러그인-없이-쓸-때)처럼 일부만 나갑니다.

### 타입과 형식

`responseFieldsOf`·`requestFieldsOf`·`parametersOf` 는 프로퍼티 타입에서, 직접 쓴 파라미터는 `ofType`, 없으면 컨트롤러 인자 타입에서 정합니다 ([파라미터 타입은 핸들러에서 정해진다](#파라미터-타입은-핸들러에서-정해진다)). REST Docs 표의 Type 열은 JSON 타입(`Number`, `String`) 그대로입니다.

| Kotlin·JDK 타입 | OpenAPI |
|---|---|
| `Int`, `Short`, `Byte` / `Long` / `BigInteger` | `integer` + `int32` / `int64` / 없음 |
| `Float` / `Double` / `BigDecimal` | `number` + `float` / `double` / 없음 |
| `Boolean` | `boolean` |
| `Instant`, `OffsetDateTime`, `ZonedDateTime`, `LocalDateTime`, `Date` | `string` + `date-time` |
| `LocalDate` / `LocalTime` | `string` + `date` / `time` |
| `Duration`, `Period` | `string` + `duration` (Boot 기본 직렬화 `PT1H30M`) |
| `UUID` / `URI`·`URL` / `ByteArray` | `string` + `uuid` / `uri` / `byte` (base64) |
| Enum | `string` + `enum` (숫자 `@JsonValue` 면 `integer`) |
| 값 배열 `List<String>`, `Set<Long>`, `IntArray` | `array` + 원소 `items` (`items: {type: integer, format: int64}`) |
| Enum 배열 `List<Level>` | `array` + `items: {type: string, enum: [...]}` |
| 값 맵 `Map<String, Int>` | `object` + `additionalProperties` |
| `@JsonFormat(shape = NUMBER / STRING)` | 바뀐 JSON 타입 (`Instant` → `number`, `BigDecimal` → `string`) |
| `@JsonFormat(pattern = ...)` | `format` 없이 `string` |

```kotlin
pathParameters("id" pathMeans "상품 ID" ofType Long::class)                 // integer, int64
queryParameters("ids" queryMeans "ID 목록" ofType typeOf<List<Long>>())    // array, items integer
```

### 제약조건

Bean Validation 어노테이션(jakarta·javax·Hibernate Validator)은 표의 Constraints 열에 더해 스키마 키워드가 됩니다. 프로퍼티 타입에 맞는 키워드를 씁니다. 아래 예의 설명 접두사(`제약:`, `쿠키:`)는 `language = "ko"` 일 때의 출력이고, 기본 언어 `en` 에서는 `Constraints:`, `Cookies:` 입니다 ([문서 언어](configuration.md#문서-언어)).

| 제약 | 문자열 | 숫자 | 배열·컬렉션 |
|---|---|---|---|
| `@Size(min, max)` | `minLength`·`maxLength` | | `minItems`·`maxItems` |
| `@Length` (Hibernate) | `minLength`·`maxLength` | | |
| `@NotBlank` | `minLength: 1` + required | | |
| `@NotEmpty` | `minLength: 1` + required | | `minItems: 1` + required |
| `@NotNull` | required | required | required |
| `@Min`, `@Max`, `@Range` | | `minimum`·`maximum` | |
| `@DecimalMin`, `@DecimalMax` | | `minimum`·`maximum` (`inclusive = false` 면 `exclusiveMinimum: true`) | |
| `@Positive`, `@PositiveOrZero`, `@Negative`, `@NegativeOrZero` | | 0 기준 `minimum`·`maximum` | |
| `@Pattern` | `pattern` | | |
| `@Email`, Hibernate `@URL` | `format: email`, `format: uri` | | |

- 원소 제약 `List<@NotBlank @Size(max = 5) String>` 은 `items` 의 키워드가 됩니다 (Kotlin 타입 인자, Java 타입 어노테이션).
- nullable 이거나 기본값이 있는 프로퍼티에 `@NotNull`·`@NotBlank`·`@NotEmpty` 가 있으면 `required` 에 넣고 `nullable` 을 뺍니다.
- optional 부모(nullable 객체, 기본값이 있는 목록)의 하위 필드는 REST Docs 표에서는 optional 이지만, 스키마에서는 부모 객체 안의 `required` 에 넣고 `nullable` 을 뺍니다 (`options: [{name}]` 의 `name` 이 non-null 이거나 `@NotBlank` 일 때). 하위 필드 자신이 nullable 이면 그대로 optional 입니다. sealed 하위 타입에만 있는 프로퍼티와 `extraction.arrayElementFieldsOptional = true` 의 원소 필드는 부모 안에서도 optional 입니다.
- `nullable` 은 Kotlin·Java 타입의 nullability 를 따릅니다. epages 는 optional 값 필드에 모두 `nullable: true` 를 붙이고 하위 필드가 있는 객체·배열에는 붙이지 않는데, 플러그인이 이를 맞춥니다: 기본값이 있는 non-null 요청 프로퍼티(`tags: List<String> = emptyList()`)는 `required` 에 넣지 않고 `nullable` 만 빼고, nullable 객체·배열(`errors: List<FieldError>?`)과 nullable 원소(`List<Box?>` 의 `items`)에는 `nullable: true` 를 붙입니다 (응답 예시의 `errors: null`, `[{...}, null]` 이 스키마에 맞습니다). 직접 기술한 필드는 epages 결과 그대로입니다.
- 키워드가 없는 제약(`@Past`, `@Future`, `@Digits`, `@AssertTrue`, 사용자 정의 제약)은 설명 뒤에 `제약: Past - Must be a date in the past` 로 붙습니다.
- **검증 그룹**: `groups` 가 지정된 제약은 어느 요청에서 검증되는지 문서만으로 알 수 없으므로 키워드·required 에 넣지 않고 설명(`제약: NotNull [OnCreate] - ...`)으로만 남깁니다. 그룹이 없거나 `Default` 인 제약만 키워드가 됩니다.

### sealed 타입과 oneOf

Kotlin sealed 클래스·인터페이스나 Jackson `@JsonTypeInfo`·`@JsonSubTypes` 가 붙은 타입은 여러 모양 중 하나입니다. REST Docs 필드 표는 하위 타입 필드의 합집합을 보여 주고(일부 하위 타입에만 있는 필드는 optional), 검증도 그 합집합으로 합니다. OpenAPI 에서는 Gradle 플러그인이 그 프로퍼티를 하위 타입마다 스키마 하나씩인 `oneOf` 로 바꿉니다.

```kotlin
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(CardPayment::class, name = "CARD"),
    JsonSubTypes.Type(BankTransfer::class, name = "BANK"),
)
sealed interface Payment

data class CardPayment(val cardNumber: String) : Payment

data class BankTransfer(val bankCode: String, val accountNumber: String) : Payment

data class CreateOrderRequest(val productId: Long, val payment: Payment)
```

```yaml
CreateOrderRequest:
  type: object
  properties:
    payment:
      oneOf:
        - $ref: '#/components/schemas/CardPayment'
        - $ref: '#/components/schemas/BankTransfer'
      discriminator:
        propertyName: type
        mapping:
          CARD: '#/components/schemas/CardPayment'
          BANK: '#/components/schemas/BankTransfer'
```

- 타입에서 추출한 요청·응답 본문(`requestFields<T>()`, `responseFields<T>()` 와 `Of` 함수들)에 적용되고, 깊이는 상관없습니다: 프로퍼티, 본문 자체, 배열 원소(`payments[]`).
- 하위 타입마다 그 이름의 컴포넌트 스키마가 생깁니다. 그 이름을 다른 스키마가 이미 쓰고 있으면 `<기반 타입><하위 타입>` (`PaymentCardPayment`) 이 됩니다.
- `discriminator` 는 `@JsonTypeInfo(use = NAME | SIMPLE_NAME | CLASS)` 이면서 `include = PROPERTY`(기본값)·`EXISTING_PROPERTY` 일 때만 붙고, `mapping` 은 `@JsonSubTypes` 이름(`use = CLASS` 면 클래스 이름)에서 만듭니다. 다른 `use`(`MINIMAL_CLASS`, `DEDUCTION`)와 `@JsonTypeInfo` 가 없는 sealed 타입은 discriminator 없이 `oneOf` 만 받습니다.
- `include = WRAPPER_OBJECT`·`WRAPPER_ARRAY` 는 하위 타입 스키마 중 하나로 나타낼 수 없어 `oneOf` 를 만들지 않고, 펼친 스키마를 그대로 둡니다.
- 접두 경로 아래와 공통 응답 래퍼 안에서도 됩니다. 추출한 목록마다 타입과 추출한 경로를 기억하므로 플러그인이 전체 경로를 받습니다: `requestFieldsOf<T>("data")` → `data.payment`, `wrappedResponseFields<T>()` → `data.payment` (래퍼의 데이터 자체가 sealed 타입이면 `data`, 그 목록이면 `data[]`), 배열 원소 안의 프로퍼티 → `data.items[].payment`. 여러 목록을 합친 본문(`requestFieldsOf<A>("a") + requestFieldsOf<B>("b")`)은 목록마다 따로 찾습니다. 플러그인은 `$ref`·배열을 따라 그 프로퍼티까지 내려가므로, 공유 컴포넌트(래퍼의 데이터 스키마 등) 안이면 그 스키마를 쓰는 모든 곳이 함께 바뀝니다.

```kotlin
.andDocument {
    requestFields<CreateOrderRequest>()
    wrappedResponseFields<OrderResponse>()   // { "code": 200, "data": { "payment": { "type": "CARD", ... } } }
}
```

```yaml
OrderResponse:
  type: object
  properties:
    code: {type: integer}
    data:
      type: object
      properties:
        payment:
          oneOf:
            - $ref: '#/components/schemas/CardPayment'
            - $ref: '#/components/schemas/BankTransfer'
          discriminator:
            propertyName: type
```

- 여전히 `oneOf` 가 되지 않는 경우: 직접 쓴 필드(`"payment" type OBJECT`, 쓸 수 있는 하위 타입 정보가 없음)와 목록에서 뺀 프로퍼티(`except("payment")`). 제네릭 래퍼 타입에서만 추출하고 데이터 필드는 직접 쓴 목록(`responseFieldsOf<ApiResponse<*>>()` + 직접 쓴 데이터 필드)도 찾을 하위 타입이 없습니다.

### 기본값·예시·제약 설명

| 수식어 | OpenAPI |
|---|---|
| `withDefaultValue "20"` | 스키마 `default`. 타입이 `integer`·`number`·`boolean` 이면 그 값으로 바꾼다 (`default: 20`), 배열이면 쉼표로 나눈다 |
| `example "10"` | 필드는 스키마 `example`, 파라미터·헤더는 파라미터·헤더 `example` (Swagger UI 입력 칸의 기본값) |
| `constraint "2~10자"` | 설명 뒤 `제약: 2~10자`. 추출한 필드에 쓰면 표 문구만 바뀌고 어노테이션에서 만든 키워드는 남는다 |

**Kotlin 기본값.** 기본값이 있는 생성자 파라미터(`val size: Int = 20`)는 `withDefaultValue` 없이도 기본값이 됩니다.

```kotlin
data class ProductSearchRequest(
    val keyword: String? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sort: SortOrder = SortOrder.LATEST,
)

queryParameters(parametersOf<ProductSearchRequest>())   // page 기본값 0, size 기본값 20, sort 기본값 LATEST
```

- `parametersOf<T>()` 로 추출한 파라미터(경로·쿼리·폼)는 `withDefaultValue` 를 적은 것과 같아서 표의 설명 칸과 OpenAPI `default` 에 나옵니다. `queryParameters<T>()` 같은 단축 함수도 같습니다. 직접 적은 `withDefaultValue` 가 이깁니다.
- `requestFields<T>()` 로 추출한 필드는 필드 표의 설명 칸(`(Default: 20)`)과 OpenAPI 스키마 프로퍼티의 `default` 로 나갑니다(Gradle 플러그인). 기본값은 요청 추출에서만 읽습니다. 응답에는 값이 늘 들어 있으므로 `responseFields<T>()` 는 쓰지 않습니다.
- 문자열·숫자·불리언·Enum 값만 씁니다. `null`, 빈 문자열, 컬렉션, 맵, 객체는 건너뜁니다.
- 가리는 필드(`@Masked` 나 `masking.annotations` 의 어노테이션)의 기본값은 내지 않습니다.
- 라이브러리는 기본값으로 인스턴스를 두 번 만들어 값을 읽고(클래스마다 한 번), 두 인스턴스의 값이 같을 때만 냅니다. 그래서 계산할 때마다 바뀌는 기본값(`UUID.randomUUID().toString()`, 현재 시각)은 실행마다 스니펫을 바꾸는 대신 빠집니다. 환경 변수·설정에서 읽는 기본값(`System.getenv("CLIENT_ID") ?: "local"`)은 두 번 다 같은 값이라 상수와 구별할 수 없어 테스트를 돌릴 때의 값이 **그대로 나갑니다**. 요청 클래스의 생성자 기본값으로 비밀 값을 읽지 말거나 그 필드에 `@Masked` 를 붙이세요.
- 자리 값과 실패: 기본값이 없는 파라미터에는 자리 값을 넣습니다: `null`, `""`, `0`, `false`, Enum 첫 상수, 빈 컬렉션, Kotlin 클래스면 같은 방법으로 만든 자리 객체(sealed 타입은 만들 수 있는 첫 하위 타입)입니다. 만들 수 없으면(인터페이스·Java 클래스 파라미터, 예외를 던지는 `init` 블록) 그 클래스의 기본값은 오류 없이 건너뛰고 다른 것은 달라지지 않습니다. Java 클래스에는 읽을 기본값이 없습니다.

타입을 모르는 파라미터(`ofType` 없이 직접 썼고 핸들러에서도 타입을 알 수 없는 것)는 `string` 이라 기본값도 문자열(`default: "20"`)입니다. 요청·응답 헤더(`headerMeans`)도 `example`·`withDefaultValue`·`constraint` 를 받습니다. `example` 이 없으면 요청 헤더의 OpenAPI 예시는 실제 요청 헤더 값입니다. 단 `Authorization`·`Proxy-Authorization`·`Cookie`·`Set-Cookie` 는 예시를 넣지 않습니다(마스킹 값이나 보안 스키마용 가짜 토큰이 보이지 않게).

### multipart 와 쿠키

multipart 로 파일을 올리는 요청은 이 절, 본문 전체가 파일인 요청은 [요청 본문 파일 업로드](#요청-본문-파일-업로드), 파일을 내려주는 응답은 [파일 다운로드](#파일-다운로드)를 봅니다. 파일 파트의 바이트는 스니펫에 `<binary: N bytes>` 로만 나갑니다.

epages 는 multipart 파트와 쿠키를 스펙에 넣지 않습니다. 라이브러리가 OpenAPI 조각 옆에 `openapi-extras.json` 으로 남기고 Gradle 플러그인이 넣습니다.

| 문서화 | OpenAPI |
|---|---|
| `requestParts("file" partMeans "...")` (실제 요청에 파일 이름이 있는 파트) | `multipart/form-data` 요청 본문의 `file: {type: string, format: binary}` |
| `requestPartFields<ImageMeta>("meta")` | `meta` 는 필드로 만든 객체 스키마, `encoding.meta.contentType` 은 실제 파트의 Content-Type |
| 그 밖의 파트 | `type: string` |
| `requestCookies("SESSION" cookieMeans "...")` | `in: cookie` 파라미터 (`isOptional` 이면 `required: false`) |
| `responseCookies("SESSION" cookieMeans "...")` | 응답 `Set-Cookie` 헤더 설명에 `` 쿠키: `SESSION`: ... `` 목록 (OpenAPI 3.0 에는 응답 쿠키 자리가 없다) |

### 그 밖에 다듬는 것

| 항목 | 결과 |
|---|---|
| operationId | 라이브러리가 남긴 후보로 정한다 ([operationId](#operationid)): 직접 정한 id 는 그대로, 규칙 id 는 겹친 것만 컨트롤러 이름·숫자를 붙인다. 테스트 이름 id(`test-name` 규칙, 핸들러를 모를 때)는 `/` 를 `.` 으로, 글자(한글 등 포함)·숫자·`.`·`_`·`-` 밖의 문자는 `-` 로 (`product-api-docs-test.get-product`, Kotest `user-spec.사용자-조회`), 겹치면 `-2`, `-3`. 여러 테스트가 합쳐진 operation 은 성공 응답 테스트의 후보를 쓰고, 테스트 이름 id 는 테스트 이름의 `-` 경계까지 공통인 부분(`get-product` + `get-product-not-found` → `get-product`), 없으면 성공 응답 테스트의 id (epages 0.19 처럼 id 를 이어 붙이지 않는다) |
| 최상위 `tags` | operation 의 태그를 순서대로 모은다. 테스트 클래스에 `@AsciiDescription` 이 있으면 그 태그의 `description` |
| JSON 예시 | 문자열이 아니라 JSON 값. 스키마 검증 도구가 예시를 검사할 수 있다 |
| 보안 요구사항 | 보안 스키마가 있는 문서에서 인증 헤더 없이 문서화한 operation 은 `security: []` (인증 없음). 보안 스키마가 없는 문서는 최상위에 `security: []` 한 번 (Redocly `security-defined`) |
| API 버전 (Spring Framework 7) | `x-api-version`, 그리고 방식에 따라 버전 헤더나 쿼리 파라미터 ([API 버전](openapi.md#api-버전)) |

### Gradle 플러그인 없이 쓸 때

epages 플러그인만 쓰면 위 보강이 없습니다. epages 가 직접 읽는 것만 나갑니다: 파라미터의 `integer`·`number`·`boolean` 타입과 기본값(타입을 알 때), Enum `enum`, 값 배열의 원소 타입(`string`·`number`·`boolean`). 생성된 스펙은 `node scripts/validate-openapi.mjs` 로 검사할 수 있습니다 ([개발](development.md)).

## MockMvc 보조 함수

| 함수 | 설명 |
|---|---|
| `restDocsMockMvc(context, provider)` | REST Docs 설정, UTF-8 필터를 적용한 MockMvc. 앱의 Jackson 매퍼 빈을 현재 스레드의 기본 매퍼로 씁니다. 요청·응답 본문 pretty print 와 사용자 전처리기([요청·응답 가공](customizing-snippets.md#요청응답-가공))는 `andDocument` 가 하므로 MockMvc 기본 전처리기로 등록하지 않습니다. 이 MockMvc 로 REST Docs 의 `document()` 를 직접 쓰면 본문이 pretty print 되지 않으니 `preprocessRequest(prettyPrint())`·`preprocessResponse(prettyPrint())` 를 함께 넘기세요 |
| `jsonContent(body)` | 객체를 JSON 으로 직렬화하고 `Content-Type: application/json` 을 설정합니다. `String`·`CharSequence`(`StringBuilder` 등)는 이미 JSON 인 본문으로 보고 그대로(`jsonContent("""{"name":"홍길동"}""")`), `ByteArray` 는 UTF-8 문자열로 보냅니다 (WebSocket 메시지 payload 와 같은 규칙). `post(...)`, `multipart(...)` 빌더 모두에 쓸 수 있습니다 |
| `ResultActions.andPrint()` | 요청·응답을 콘솔에 출력합니다. `andDo(MockMvcResultHandlers.print())` 와 같습니다. 모든 요청을 출력하려면 `print-requests: true` 를 씁니다 |
| `ResultActions.andLog()` | 요청·응답을 로그(DEBUG)로 남깁니다. `andDo(MockMvcResultHandlers.log())` 와 같습니다 |
| `ResultActions.andStatus(HttpStatus.NOT_FOUND)`, `andStatus(404)` | 응답 상태 코드 검증. `andExpect(status().isNotFound)` 와 같고 `MockMvcResultMatchers.status` import 가 필요 없습니다. `HttpStatusCode`(`HttpStatus` 상수 포함)나 숫자를 받고, 다르면 Spring `status()` 와 같은 메시지(`Response status expected:<404> but was:<200>`)로 실패합니다. 같은 `ResultActions` 를 돌려주므로 `andDocument { }` 를 이어 씁니다. WebTestClient 는 `expectStatus()` 를 그대로 씁니다 |
| `ResultActions.andExpectJsonPath("$.code", 404)` | 응답 JSON 값 검증. `andExpect(jsonPath("$.code").value(404))` 와 같습니다 |
| `ResultActions.andReturnContent()` | 응답 본문을 UTF-8 문자열로 돌려줍니다. `contentAsString` 은 charset 이 없으면 한글이 깨집니다 |
| `bearerToken(token)` | `Authorization: Bearer <token>` 헤더. 마스킹을 켜면 문서에서 가려집니다 |
| `MockMvc.performStream(request, eventCount = 2) { ... }` | 끝나지 않는 스트림(SSE)에서 처음 N 개 이벤트까지 받아 고정한다. 패키지는 `io.github.kardx.sse` ([끝나지 않는 스트림](messaging.md#끝나지-않는-스트림)) |
| `MockMvc.performAsync(request)` | SSE, `DeferredResult` 같은 비동기 응답을 끝까지 받습니다 |
| `ResultActions.andReturnAsObject<T>()` | 응답 본문을 제네릭 타입 그대로 역직렬화합니다 (`andReturnContent` 와 같은 UTF-8, 앱 매퍼). `T` 가 `String` 이면 원문, `ByteArray` 면 응답 바이트 그대로입니다. 본문이 비었거나 공백뿐이면 `IllegalStateException` 이므로 그런 응답은 `andReturnEntity` 를 씁니다 |
| `ResultActions.andReturnEntity<T>()` | 상태 코드·응답 헤더 전부·본문을 Spring `ResponseEntity<T>` 로 돌려줍니다. 본문 처리는 `andReturnAsObject` 와 같고, 본문이 비었거나 공백뿐이면(204 등) `body` 가 null, `T` 가 `String` 이면 원문 그대로, `ByteArray` 면 응답 바이트 그대로입니다. `response.statusCode`, `response.headers.location`, `response.body` |
| `ResultActionsDsl.andDocument { }`, `MvcTestResult.andDocument { }` | MockMvc Kotlin DSL·`MockMvcTester` 결과를 문서화합니다 ([andDocument](#anddocument)) |

Jackson 2 와 3 을 모두 지원하고, Spring 6 과 7 의 바이너리 차이는 라이브러리가 처리합니다.
