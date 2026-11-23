[English](../migration.md) | **한국어**

# 기존 Spring REST Docs 에서 옮기기

[문서 목록](README.md)

<!-- toc -->
**목차**

- [1. 의존성과 플러그인](#1-의존성과-플러그인)
- [2. 테스트 기본 클래스](#2-테스트-기본-클래스)
- [3. document() 를 한 줄씩 바꾸기](#3-document-를-한-줄씩-바꾸기)
  - [대응표](#대응표)
  - [필드를 타입에서 추출하기](#필드를-타입에서-추출하기)
- [4. 기존 스니펫·index.adoc 과 섞어 쓰기](#4-기존-스니펫indexadoc-과-섞어-쓰기)
- [5. epages resource() 를 쓰던 경우](#5-epages-resource-를-쓰던-경우)
- [6. Swagger 어노테이션 설명 재사용](#6-swagger-어노테이션-설명-재사용)
- [7. 옮긴 뒤 확인](#7-옮긴-뒤-확인)
- [Spring Auto REST Docs 에서 옮기기](#spring-auto-rest-docs-에서-옮기기)
<!-- /toc -->

`document("id", requestFields(fieldWithPath(...)), ...)` 로 쓴 테스트가 있는 프로젝트에 이 라이브러리를 들이는 방법입니다. 한 번에 바꿀 필요는 없습니다. 기존 테스트는 그대로 돌고, 새로 쓰거나 고치는 테스트부터 `andDocument { }` 로 옮기면 됩니다.

아래 예시는 Boot 4 기준이며, 스크래치 프로젝트에서 그대로 컴파일하고 실행해 확인했습니다.

## 1. 의존성과 플러그인

기존 `spring-restdocs-mockmvc`, `asciidoctor` 설정은 지우지 않아도 됩니다. Gradle 플러그인을 더하면 라이브러리 의존성(annotations, 본체, epages 플러그인이 있으면 epages 의 MockMvc 모듈, 앱 시작 로그 모듈)도 플러그인이 넣습니다. 이미 선언한 `spring-restdocs-mockmvc`·`restdocs-api-spec-mockmvc` 는 그대로 두고 건드리지 않습니다. 플러그인이 Asciidoctor 입력·스니펫 경로·`spring-restdocs-asciidoctor` 확장을 설정하므로, 직접 쓴 `asciidoctor { }`·`asciidoctorExt` 설정은 옮긴 뒤 지웁니다. 앱 리소스 안(`build/resources/main/static/docs`, `src/main/resources/static/...`)을 가리키는 epages `openapi3 { outputDirectory = ... }` 와 스펙을 `static/` 으로 복사하던 태스크(`copyOasToSwagger` 등)도 지웁니다. 플러그인이 문서와 스펙을 `kardx.bootJar.path`(기본 `static/docs`)로 앱에 복사합니다. 그런 `outputDirectory` 가 남아 있으면 플러그인이 경고합니다.

```kotlin
plugins {
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("com.epages.restdocs-api-spec") version "0.20.2"        // OpenAPI 를 만들 때만. Boot 3: 0.19.4
    id("io.github.kardx-project") version "0.1"         // 라이브러리 의존성도 넣는다
}
```

index 는 설정 없이 문서를 만든 모든 테스트 클래스를 넣습니다. 기존 `index.adoc` 을 템플릿(`src/docs/asciidoc/index-template.adoc`)으로 옮기면 그 마커 줄 자리에 들어갑니다(4절). 옛 문서와 섞어 쓰는 동안 새 문서만 묶고 싶으면 `kardx { index { packageName = "com.example.demo"; baseClass = "com.example.demo.ApiDocsTest" } }` 로 범위를 좁힙니다.

라이브러리와 Gradle 플러그인이 Maven Central 에 있으므로 `pluginManagement` 에도 `mavenCentral()` 을 넣습니다. 전체 설정과 Boot 3 차이는 [시작하기](getting-started.md#설치)에 있습니다. 문서는 `./gradlew apiDocs` 로 만듭니다(`asciidoctor` 태스크를 직접 부르던 스크립트는 바꿉니다).

## 2. 테스트 기본 클래스

| 기존 | 바꾼 뒤 |
|---|---|
| `@ExtendWith(RestDocumentationExtension::class)` | `@ExtendWith(AutoRestDocumentationExtension::class)` (`io.github.kardx.junit`). 테스트 클래스마다 AsciiDoc 문서를 만든다 |
| `MockMvcBuilders.webAppContextSetup(context).apply(documentationConfiguration(provider)).build()` | `restDocsMockMvc(context, provider)` (`io.github.kardx.mockmvc`). UTF-8, pretty print. Spring Security 필터 체인은 `apply-spring-security: true` 일 때만 (`@AutoConfigureMockMvc` 와 달리 기본은 걸지 않음) |
| `@AutoConfigureRestDocs` + 주입한 `MockMvc` | `MockMvc` 를 테스트 메서드 파라미터로 받거나(`fun getUser(mockMvc: MockMvc)`, `AutoRestDocumentationExtension` 이 넣는다) 위처럼 `restDocsMockMvc(context, provider)` 로 바꾸고 `@AutoConfigureRestDocs` 를 지운다. 주입한 `MockMvc` 는 Spring Boot 가 스니펫 디렉터리를 테스트 클래스·메서드 이름으로만 정해(`ManualRestDocumentation`) 이 라이브러리의 디렉터리 규칙과 다르다. 일반 테스트 메서드는 같지만 `@Nested` 클래스(`success/get-user` ↔ `user-api-test/success-get-user`), `@ParameterizedTest`(실행마다 같은 디렉터리 ↔ `get-user/1`, `get-user/2`), 인자가 다른 같은 이름의 테스트 메서드는 스니펫이 다른 곳에 생겨 생성 문서에서 그 섹션이 빠진다. 이제 이런 클라이언트로 문서화하면 디렉터리가 다를 때 [KDX-011](troubleshooting.md#kdx-011) 로 실패한다(같으면 한 번 경고). REST Docs 설정이 없는 주입 `MockMvc` 는 [KDX-012](troubleshooting.md#kdx-012) 로 실패한다 |
| `WebTestClient.bindToApplicationContext(context).configureClient().filter(documentationConfiguration(provider)).build()` | `restDocsWebTestClient(context, provider)` (`io.github.kardx.webtestclient`). 다른 바인딩은 빌더에 `.restDocs(provider)` ([WebTestClient](webtestclient.md)) |

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs") // src/test/resources/application-docs.yml
abstract class ApiDocsTest {
    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpRestDocs(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        mockMvc = restDocsMockMvc(context, provider)
    }
}
```

`AutoRestDocumentationExtension` 은 `RestDocumentationExtension` 을 대신하므로 둘을 같이 걸지 않습니다. 기존 `document(...)` 테스트도 이 기본 클래스에서 그대로 돕니다. 새 테스트에는 기본 클래스가 필요 없습니다. 테스트 클래스에 어노테이션을 붙이고 `MockMvc` 를 파라미터로 받습니다 ([시작하기](getting-started.md#8-문서화-테스트)).

## 3. document() 를 한 줄씩 바꾸기

기존 테스트입니다.

```kotlin
@Test
fun before() {
    mockMvc.perform(get("/api/users/{id}", 1L))
        .andExpect(status().isOk)
        .andDo(
            document(
                "users/get",
                preprocessResponse(prettyPrint()),
                pathParameters(parameterWithName("id").description("사용자 ID")),
                responseFields(
                    fieldWithPath("id").type(JsonFieldType.NUMBER).description("사용자 ID"),
                    fieldWithPath("nickname").type(JsonFieldType.STRING).description("닉네임").optional(),
                ),
            ),
        )
}
```

바꾼 테스트입니다. 스니펫 이름(`"users/get"`)은 쓰지 않습니다. `<테스트 클래스>/<메서드>` (`migration-docs-test/get-user`)로 정해지고, 메서드의 `@DisplayName` 이 문서 섹션 제목이 됩니다. pretty print 는 기본입니다.

```kotlin
@Test
@DisplayName("사용자 조회")
fun getUser() {
    mockMvc.perform(get("/api/users/{id}", 1L))
        .andExpect(status().isOk)
        .andDocument {
            pathParameters("id" pathMeans "사용자 ID")
            responseFields(
                "id" type NUMBER means "사용자 ID",
                "nickname" type STRING means "닉네임" isOptional true,
            )
        }
}
```

import 는 `io.github.kardx.dsl.*`(`pathMeans`, `type`, `NUMBER` …)와 `io.github.kardx.mockmvc.andDocument` 입니다.

### 대응표

| Spring REST Docs | 이 라이브러리 (`andDocument { }` 안) |
|---|---|
| `document("id", ...)` | `.andDocument { ... }` (식별자는 자동) |
| `preprocessRequest(prettyPrint())`, `preprocessResponse(prettyPrint())` | 기본으로 적용 |
| `pathParameters(parameterWithName("id").description("ID"))` | `pathParameters("id" pathMeans "ID")` |
| `queryParameters(parameterWithName("page").description("페이지").optional())` | `queryParameters("page" queryMeans "페이지" isOptional true)` |
| `formParameters(parameterWithName("name").description("이름"))` | `formParameters("name" formMeans "이름")` |
| `requestHeaders(headerWithName("Authorization").description("토큰"))` | `requestHeaders("Authorization" headerMeans "토큰")` |
| `responseHeaders(headerWithName("ETag").description("버전"))` | `responseHeaders("ETag" headerMeans "버전")` |
| `requestCookies(cookieWithName("SESSION").description("세션"))` | `requestCookies("SESSION" cookieMeans "세션")` |
| `requestParts(partWithName("file").description("파일"))` | `requestParts("file" partMeans "파일")` |
| `requestPartFields("meta", fieldWithPath("name")...)` | `requestPartFields("meta", "name" type STRING means "이름")` |
| `links(linkWithRel("self").description("이 리소스"))` | `links("self" linkMeans "이 리소스")` |
| `fieldWithPath("id").type(JsonFieldType.NUMBER).description("ID")` | `"id" type NUMBER means "ID"` |
| `.optional()` | `isOptional true` 또는 `optional()` |
| `.ignored()` | `isIgnored true` 또는 `ignored()` |
| `subsectionWithPath("meta").type(JsonFieldType.OBJECT).description("...")` | `"meta" subsection OBJECT means "..."` |
| `.attributes(key("constraints").value("2~10자"))` | `constraint "2~10자"` |
| `relaxedResponseFields(...)`, `relaxedRequestFields(...)` (그 밖의 `relaxed*` 도) | 블록에 `relaxed(RelaxedSection.RESPONSE_FIELDS)`, `relaxed(RelaxedSection.REQUEST_FIELDS)`. `relaxed = true` 는 모든 항목을 느슨하게 합니다 ([느슨한 검증](http-api.md#느슨한-검증)) |
| 위로 표현할 수 없는 스니펫 (`requestBody()`, 직접 만든 `Snippet`) | `snippets(requestBody())` |
| `requestFields(fieldWithPath(...)...)` 를 DTO 마다 반복 | `requestFields<CreateUserRequest>()` (아래 절) |

DSL 의 다른 수식어(`example`, `withDefaultValue`, `enumOf`, `ofType`, `deprecated`)는 [항목 문법](http-api.md#항목-문법)에 있습니다.

Spring REST Docs 와 다른 점이 하나 있습니다. `fieldWithPath("x").optional()` 은 디스크립터를 제자리에서 바꾸지만, DSL 수식어(`isOptional true`, `optional()`, `means` …)는 복사본을 돌려줍니다. 공유하는 `val` 상수의 항목은 바뀌지 않고, 결과를 넘기지 않은 수식어는 효과가 없습니다. `NICKNAME isOptional true` 를 따로 한 줄 쓰지 말고 `requestFields(NICKNAME isOptional true)` 로 씁니다.

### 필드를 타입에서 추출하기

필드 목록을 손으로 쓰던 곳은 DTO 에서 추출할 수 있습니다. nullable 프로퍼티는 optional, Bean Validation 제약은 Constraints 열, `@Description` 은 설명이 됩니다 ([필드 자동 추출](http-api.md#필드-자동-추출)).

```kotlin
.andDocument {
    pathParameters("id" pathMeans "사용자 ID")
    responseFields<UserResponse>()
}
```

`responseFields<T>()`·`requestFields<T>()` 같은 단축형과 `…Of` 함수, `describe()` 는 `io.github.kardx.extractor` 의 확장 함수라 따로 import 합니다(`import io.github.kardx.extractor.responseFields`). `…Of` 함수는 목록을 돌려주고, 단축형은 블록에 바로 등록합니다.

추출 결과가 기존 표와 같은지는 테스트를 돌리기 전에 `describe()` 로 확인합니다 ([추출 결과 미리보기](http-api.md#추출-결과-미리보기)).

```kotlin
println(requestFieldsOf<ProductRequest>().describe())
// | Path  | Type   | Required | Description | Constraints |
// |-------|--------|----------|-------------|-------------|
// | name  | String | true     | 상품명      |             |
// | price | Number | true     | 가격        |             |
```

추출 결과와 실제 응답이 다르면 테스트가 실패하면서 빠진 필드, 붙여 넣을 DSL 줄, 원인 힌트를 알려 줍니다 ([검증 실패 메시지](http-api.md#검증-실패-메시지)). 공통 응답 래퍼(`{ code, message, data }`)를 쓴다면 [공통 응답 래퍼](http-api.md#공통-응답-래퍼)를 먼저 등록합니다.

## 4. 기존 스니펫·index.adoc 과 섞어 쓰기

옮기는 동안 두 방식이 한 프로젝트에 있어도 됩니다.

| 기존 것 | 그대로 쓰는 법 |
|---|---|
| `document("users/get")` 테스트 | 같은 기본 클래스에서 그대로 돈다. 스니펫은 지금처럼 `build/generated-snippets/users/get/` 에 생긴다. 이 테스트는 자동 생성 문서에 섹션이 생기지 않는다(스니펫 디렉터리가 `<클래스>/<메서드>` 가 아니므로) |
| 직접 쓴 `src/docs/asciidoc/*.adoc` (예: `users.adoc` 의 `include::{snippets}/users/get/http-request.adoc[]`) | 그대로 HTML 이 된다. 플러그인이 `{snippets}` 속성을 설정한다. 옮긴 테스트의 스니펫을 include 하려면 경로를 `{snippets}/<test-class>/<method>/` 로 바꾸거나, 자동 생성 문서(`<test-class>.adoc`)로 링크한다 |
| 직접 쓴 `src/docs/asciidoc/index.adoc` | 플러그인은 `index-template.adoc` 으로 `index.html` 을 만들므로 이름이 겹친다. 파일 이름을 `index-template.adoc` 으로 바꾸고, 테스트 클래스 목록이 들어갈 자리에 `// restdocs:api-index` 줄을 넣는다. 기존 내용과 include 는 그대로 남는다 |
| 문서마다 넣던 머리말 (`include::_header.adoc[]`) | `kardx.common-include-files: _header.adoc` 이 모든 자동 생성 문서에 넣는다 |

```asciidoc
= 우리 서비스 API
:toc: left

== 옛 문서

include::users.adoc[]

== API 목록

// restdocs:api-index
```

자동 생성 문서의 섹션 순서는 메서드 이름순입니다. 기존 문서처럼 정한 순서로 두려면 `@TestMethodOrder(MethodOrderer.OrderAnnotation::class)` 와 `@Order` 를 씁니다 ([섹션 순서](asciidoc.md#섹션-순서)). 다 옮긴 뒤에는 직접 쓴 문서와 include 를 지우고 index 템플릿만 남기면 됩니다.

## 5. epages resource() 를 쓰던 경우

```kotlin
@Test
fun epagesBefore() {
    mockMvc.perform(post("/api/products").jsonContent(ProductRequest("키보드", 10000)))
        .andExpect(status().isOk)
        .andDo(
            document(
                "products/create",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("상품")
                        .summary("상품 등록")
                        .description("상품을 등록한다.")
                        .requestSchema(Schema.schema("ProductRequest"))
                        .deprecated(true)
                        .build(),
                ),
            ),
        )
}
```

`andDocument { }` 는 epages 가 클래스패스에 있으면 같은 OpenAPI 조각(`resource.json`)을 함께 만듭니다. `resource()` 를 따로 넘기지 않습니다.

```kotlin
@Test
@DisplayName("상품 등록")                      // summary (클래스 @DisplayName 은 tag)
fun createProduct() {
    mockMvc.perform(post("/api/products").jsonContent(ProductRequest("키보드", 10000)))
        .andExpect(status().isOk)
        .andDocument {
            description = "상품을 등록한다."
            requestFields<ProductRequest>()   // 설명은 @Schema(description) 에서
            responseFields<ProductRequest>()
            openApi {
                deprecated = true
                tags("상품")
            }
        }
}
```

| `ResourceSnippetParameters` | 바꾼 뒤 |
|---|---|
| `.tag("상품")` | 테스트 클래스 `@DisplayName`. 더 붙이려면 `openApi { tags("상품") }` |
| `.summary("상품 등록")` | 테스트 메서드 `@DisplayName` |
| `.description("...")` | `description = "..."` |
| `.requestSchema(Schema.schema("X"))`, `.responseSchema(...)` | 대개 필요 없다. 추출한 타입 이름이 자동으로 붙는다. 정하려면 `openApi { requestSchema = "X" }`, `responseSchema<ApiResponse<X>>()` ([스키마 이름](http-api.md#스키마-이름)) |
| `.deprecated(true)` | `openApi { deprecated = true }` |
| `.pathParameters(...)`, `.requestFields(...)`, `.responseFields(...)` | 블록의 같은 이름 함수. REST Docs 표와 OpenAPI 에 한 번에 들어간다 |
| `MockMvcRestDocumentationWrapper.document(...)`, `WebTestClientRestDocumentationWrapper.document(...)` | `.andDocument { }` |
| OpenAPI 에서만 빼기 | `openApi { excluded = true }` |
| `document("products/create", ...)` 의 식별자로 정해지던 operationId (`products.create`) | 기본은 컨트롤러 메서드 이름(`create`). 기존 id 를 지키려면 `openApi { operationId = "products.create" }`, 또는 규칙 `open-api.operation-id-rule: test-name`(테스트 클래스·메서드 이름) ([operationId](http-api.md#operationid)) |

옮기는 동안 같은 경로·메서드를 기존 테스트와 새 테스트가 함께 문서화하면 epages 가 operation 하나로 합칩니다. operationId 는 `andDocument` 로 옮긴 테스트의 것(컨트롤러 메서드 이름)이 쓰이고, 아직 `document()` 만 쓰는 operation 은 예전처럼 식별자에서 나옵니다. 스키마 이름이 같고 구조가 다르면 Gradle 플러그인이 이름을 나누고 빌드 로그에 남깁니다(`OpenAPI schema names: ProductRequest → DemoProductRequest`). 옮긴 뒤 기존 테스트를 지우면 정리됩니다.

## 6. Swagger 어노테이션 설명 재사용

DTO 에 springdoc 용 `@Schema(description = "...")` 가 이미 있다면 설명을 다시 쓰지 않습니다. 테스트 yml 에 한 줄을 넣으면 `@Description` 이 없는 프로퍼티의 설명을 `@Schema` 에서 읽습니다.

```yaml
# src/test/resources/application-docs.yml
kardx:
  description-annotations: swagger       # Swagger @Schema(description) 를 설명으로 쓴다
```

```kotlin
data class ProductRequest(
    @field:Schema(description = "상품명") val name: String,
    @field:Schema(description = "가격") val price: Long,
)
```

`jackson`(`@JsonPropertyDescription`), 직접 만든 어노테이션(`com.example.ApiField#text`)도 됩니다 ([다른 어노테이션에서 설명 가져오기](http-api.md#다른-어노테이션에서-설명-가져오기)).

## 7. 옮긴 뒤 확인

1. `./gradlew apiDocs` 를 실행하고 `build/api-docs/index.html` 에서 옮긴 테스트의 섹션을 엽니다.
2. 표가 예전과 다르면 `println(responseFieldsOf<T>().describe())` 로 추출 결과를 보고, 필요한 줄만 직접 기술로 바꾸거나 더합니다 ([추출 결과와 직접 기술 섞기](http-api.md#추출-결과와-직접-기술-섞기)).
3. 옮긴 테스트의 `document("...")` 스니펫 디렉터리를 include 하던 직접 쓴 문서를 정리합니다.

## Spring Auto REST Docs 에서 옮기기

[Spring Auto REST Docs](https://github.com/ScaCap/spring-auto-restdocs)(ScaCap)는 Javadoc 과 타입을 읽어 REST Docs 스니펫을 채웁니다. 그 개념 대부분은 여기에도 대응하는 것이 있어서, 위 절들처럼 테스트 단위로 옮길 수 있습니다. 가장 큰 차이: Auto REST Docs 는 핸들러의 파라미터·반환 타입을 살피지만, 이 라이브러리는 적어 준 타입(`responseFields<UserResponse>()`)을 문서화하고 REST Docs 가 실제 응답과 맞는지 검증합니다.

| Spring Auto REST Docs | 이 라이브러리 |
|---|---|
| JSON doclet 이나 Dokka JSON 출력 (doclet 을 건 `javadoc` 태스크, `org.springframework.restdocs.javadocJsonDir`) | Gradle 플러그인의 `apiDocsSourceDocs`. `kardx { sourceDocs { enabled = true } }` 로 켭니다(실험 기능, 기본 꺼짐). doclet·Dokka·시스템 속성 설정이 필요 없습니다 ([KDoc/Javadoc 에서 설명 가져오기](http-api.md#kdocjavadoc-에서-설명-가져오기)) |
| 필드·getter 의 Javadoc → 필드 설명 | 프로퍼티·필드·getter·record `@param` 의 KDoc·Javadoc. `@Description`, `description-annotations` 다음 순서 |
| 컨트롤러 메서드 Javadoc → `description` 스니펫 | `sourceDocs { enabled = true; methodDocs = true }` 일 때 OpenAPI summary(첫 문장)와 description, 그리고 블록에 `description = "..."` 이 없을 때 `api-summary` 의 설명 |
| `@title` Javadoc 태그 → 섹션 제목 | 테스트 메서드의 `@DisplayName` |
| Bean Validation 제약 → Optional 열과 제약 | 같습니다. Required 열, Constraints 열, OpenAPI 키워드 ([추출 규칙](http-api.md#추출-규칙)) |
| `@Deprecated` / Javadoc `@deprecated` | 프로퍼티·핸들러의 Kotlin·Java `@Deprecated` ([더 이상 쓰지 않는 필드](http-api.md#더-이상-쓰지-않는-필드), [deprecated API](http-api.md#deprecated-api)). Javadoc 태그는 읽지 않습니다 |
| `AutoDocumentation.requestFields()`, `responseFields()` | 블록의 `requestFields<T>()`, `responseFields<T>()` |
| `AutoDocumentation.pathParameters()` | `pathParameters("id")` 로 적으면 메서드의 `@param id` 가 설명이 됩니다 ([이름만 적은 파라미터](http-api.md#이름만-적은-파라미터--메서드-param)). 또는 `pathParameters("id" pathMeans "...")`. OpenAPI 타입은 핸들러에서 가져옵니다 ([파라미터 타입은 핸들러에서](http-api.md#파라미터-타입은-핸들러에서-정해진다)) |
| `AutoDocumentation.requestParameters()`, `modelAttribute(...)` | 파라미터 객체면 `queryParameters<T>()` 나 `parametersOf<T>()`, 직접 쓰면 `queryParameters("page" queryMeans "...")` |
| `AutoDocumentation.requestHeaders()`, `authorization(...)` | `requestHeaders("Authorization" headerMeans "...")`. OpenAPI 보안 스키마는 요청에서 만듭니다 ([헤더와 인증](http-api.md#헤더와-인증)) |
| `AutoDocumentation.links()` | `links("self" linkMeans "...")` |
| `AutoDocumentation.methodAndPath()`, `description()` | `api-summary` 스니펫 (메서드, 경로, 설명) |
| `AutoDocumentation.sectionBuilder()`, `auto-section.adoc` | `AutoRestDocumentationExtension`: 테스트 클래스마다 문서 하나, 테스트마다 섹션 하나, index 로 묶음. 스니펫은 `document-snippets` 로 고릅니다 ([문서에 넣을 스니펫 고르기](customizing-snippets.md#문서에-넣을-스니펫-고르기)) |
| `JacksonResultHandlers.prepareJackson(objectMapper)` | 필요 없습니다. `restDocsMockMvc` 가 앱의 매퍼를 씁니다 |
| `alwaysDo(document("{class-name}/{method-name}", ...))` | 필요 없습니다. 식별자는 자동입니다 |

순서:

1. Gradle 플러그인을 적용하고(1절) doclet·Dokka 설정, `test` 의 `javadoc` 의존, `javadocJsonDir` 시스템 속성을 지웁니다. `kardx { sourceDocs { enabled = true } }` 로 켜면 주석은 `apiDocsSourceDocs` 가 읽습니다. 실험 기능이고 스캐너가 놓치는 선언이 있으므로 결과를 [알려진 한계](http-api.md#알려진-한계)와 함께 확인합니다.
2. 테스트 기본 클래스를 바꿉니다(2절). `prepareJackson`, `alwaysDo(document(...))`, `withDefaults(AutoDocumentation...)` 설정을 빼고 `restDocsMockMvc(context, provider)` 를 씁니다.
3. 테스트마다 타입을 적은 `andDocument { }` 블록을 씁니다: `requestFields<CreateUserRequest>()`, `responseFields<UserResponse>()`, 그리고 경로 파라미터와 헤더. 실행 전에 `describe()` 로 표를 확인합니다 ([추출 결과 미리보기](http-api.md#추출-결과-미리보기)).
4. 섹션 제목은 `@title` 에서 `@DisplayName` 으로 옮깁니다. 메서드 Javadoc 을 OpenAPI summary·description 과 AsciiDoc 문서의 설명으로 쓰려면 `kardx { sourceDocs { enabled = true; methodDocs = true } }` 로 두고, 아니면 `description = "..."` 을 적습니다.
5. `auto-section.adoc` 을 include 하던 직접 쓴 문서는 생성된 클래스 문서와 index 로 바꿉니다(4절).
6. `./gradlew apiDocs` 를 실행해 비교합니다. 설명이 빠졌으면 [문제 해결](troubleshooting.md)을 봅니다 (jar 로 들어온 DTO 는 읽을 소스가 없습니다).
