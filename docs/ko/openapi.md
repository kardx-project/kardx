[English](../openapi.md) | **한국어**

# OpenAPI 와 Swagger UI

[문서 목록](README.md)

<!-- toc -->
**목차**

- [OpenAPI / Swagger UI](#openapi--swagger-ui)
  - [OpenAPI 정보와 태그](#openapi-정보와-태그)
  - [OAuth2·OpenID Connect 스키마](#oauth2openid-connect-스키마)
- [API 버전](#api-버전)
<!-- /toc -->

Gradle 플러그인이 OpenAPI 스펙에 하는 일을 설명합니다: Swagger UI, info 와 태그, OAuth2·OpenID Connect 스키마, API 버전. 테스트에서 operation 을 설명하는 방법은 [OpenAPI 스펙에 나가는 정보](http-api.md#openapi-스펙에-나가는-정보)와 [스키마 이름](http-api.md#스키마-이름)에, 플러그인 설정과 태스크는 [빌드와 산출물](build.md)에 있습니다.

## OpenAPI / Swagger UI

[epages restdocs-api-spec](https://github.com/ePages-de/restdocs-api-spec) 이 테스트 클래스패스에 있으면 `andDocument` 가 OpenAPI 리소스(`resource.json`)도 만듭니다. 테스트 클래스 `@DisplayName` 은 tag, 메서드 `@DisplayName` 은 summary 가 됩니다.

```kotlin
.andDocument {
    ...
    openApi {
        requestSchema = "CreateUserRequest"
        responseSchema = "UserResponse"
        deprecated = true
        tags("마이페이지")
        operationId = "updateMyProfile"     // operationId 를 직접 정한다 (기본은 컨트롤러 메서드 이름)
        summary = "내 프로필 수정"            // operation summary (기본은 테스트 표시 이름)
        description = "..."                 // operation description (기본은 블록 description)
        excluded = true                     // 이 API 를 OpenAPI 에서 뺀다 (REST Docs 문서에는 남는다)
    }
}
```

- operationId 는 기본으로 요청을 처리한 컨트롤러 메서드 이름입니다. 규칙은 설정 `openApi.operationIdRule`(yml `kardx.open-api.operation-id-rule`)로 바꾸고, 겹침 처리(겹친 것만 컨트롤러 이름을 붙임)는 플러그인이 스펙을 만들 때 합니다. 자세한 규칙은 [operationId](http-api.md#operationid) 에 있습니다. 플러그인 쪽 옵션은 없습니다(규칙은 테스트 설정 하나로 정합니다).

- 같은 API 의 성공·오류 응답은 테스트를 나눠 문서화하면 operation 하나에 상태 코드별 응답으로 합쳐집니다. [여러 상태 코드 문서화](http-api.md#여러-상태-코드-문서화)를 보세요.
- 요청·응답 필드를 relaxed 로 한 문서(`relaxed = true`, `relaxed(RelaxedSection.RESPONSE_FIELDS)`)는 그 필드 설명 없이 OpenAPI 에 들어갑니다. epages 가 relaxed 검증을 지원하지 않기 때문입니다.
- 테스트가 만들지 않는 오류는 블록에서 `errorResponse(...)` 로 선언할 수 있습니다. 플러그인이 공통 응답보다 먼저 operation 에 더합니다 ([선언한 오류 응답](http-api.md#선언한-오류-응답)).
- 정수·날짜 형식, Bean Validation 제약, `withDefaultValue`·`example`, multipart·쿠키는 Gradle 플러그인이 `openapi3` 뒤에 스펙에 합칩니다. 무엇이 어떻게 나가는지는 [OpenAPI 스펙에 나가는 정보](http-api.md#openapi-스펙에-나가는-정보)에 있습니다.
- 같은 경로·메서드를 다르게 문서화한 테스트(예시·검증용)는 `excluded = true` 나 `openApi.enabled = false` 로 빼세요. epages 가 하나로 합칩니다.
- Swagger UI 는 springdoc 의 `springdoc.swagger-ui.url` 을 생성된 `openapi3.yaml` 로 지정해 띄웁니다. [샘플](../../README.ko.md#샘플) 설정을 참고하세요.

### OpenAPI 정보와 태그

epages 는 스펙의 제목·버전·서버만 정합니다(`openapi3 { }`). `kardx { openApiInfo { } }` 는 `openapi3` 뒤에 나머지 최상위 값을 채웁니다: `info.description`, `termsOfService`, `contact`, `license`, 문서 `externalDocs`, `tags` 의 순서와 설명, `x-tagGroups`. 정하지 않은 항목은 epages 결과 그대로입니다.

```kotlin
kardx {
    openApiInfo {
        descriptionFile = file("src/docs/openapi-intro.md")   // 또는 description = "..." (Markdown)
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
            description = "연동 안내"
        }
        tag("Users") {
            description = "가입과 프로필"
            externalDocs { url = "https://wiki.example.com/api/users" }
        }
        tag("Orders")                         // 순서만 정한다
        tagGroup("Accounts", "Users")         // x-tagGroups (Redoc, Scalar)
        tagGroup("Shopping", "Orders", "Cart")
    }
}
```

- `description`(Markdown) 또는 `descriptionFile`(UTF-8 Markdown 파일. 바뀌면 스펙을 다시 만듭니다). 둘 다 정하거나 파일이 없으면 빌드가 실패합니다.
- `license` 는 `name`, `externalDocs` 는 `url` 이 있어야 합니다(OpenAPI 필수 값). 없으면 빌드 경고와 함께 그 블록을 넣지 않습니다.
- `tag(...)` 는 부른 순서대로 태그를 앞에 둡니다. 나머지 태그는 epages 순서대로 뒤에 옵니다. 여기서 정한 설명이 테스트 클래스 `@AsciiDescription` 보다 앞섭니다. 문서화한 operation 이 쓰지 않는 태그는 경고와 함께 뺍니다(태그는 테스트 클래스 `@DisplayName` 이나 `openApi { tag(...) }` 에서 옵니다).
- `tagGroup(...)` 은 `x-tagGroups` 를 씁니다. Redoc 과 Scalar 가 옆 메뉴에 그룹으로 보여 줍니다. Redoc 은 어느 그룹에도 없는 태그를 숨기므로 그런 태그는 마지막 그룹 `Other`(`language = "ko"` 면 `기타`)에 모읍니다.
- 버전별 스펙(`apiVersioning { splitSpecs = true }`)에도 들어갑니다. `apiDocsAggregate` 의 병합 스펙에는 넣지 않습니다. 병합 스펙은 `aggregate { title; version; description }` 를 씁니다.

### OAuth2·OpenID Connect 스키마

`openApiSecurity { }` 에는 테스트가 알 수 없는 OAuth2·OpenID Connect 정보, 곧 flow 와 그 URL, scope 설명을 적습니다. 테스트는 operation 에 필요한 스키마와 scope 만 밝힙니다(`openApi { oauth2("users:read") }`). 둘이 어떻게 맞물리는지는 [OAuth2 와 OpenID Connect](http-api.md#oauth2-와-openid-connect)에 있습니다.

## API 버전

Spring Framework 7 (Spring Boot 4) 에서는 같은 경로를 API 버전에 따라 다른 핸들러로 보낼 수 있습니다(`@GetMapping("/api/users/{id}", version = "1.1")`). 라이브러리가 문서화한 요청을 처리한 핸들러의 버전을 읽으므로 테스트에 따로 적을 것은 없습니다. Spring Framework 6 (Boot 3) 에는 버전 속성이 없어 달라지는 것이 없습니다.

클라이언트가 버전을 보내는 방식은 앱에서 정하고(`ApiVersionConfigurer`, `spring.mvc.apiversion.*`), 빌드는 이를 읽을 수 없습니다. 플러그인에 같은 방식을 알려 줍니다.

```kotlin
kardx {
    apiVersioning {
        header("X-API-Version")   // 또는 queryParameter("version"), mediaTypeParameter("v"), pathSegment()
        splitSpecs = false        // 기본값
    }
}
```

| 방식 | 버전이 있는 operation 이 OpenAPI 에서 받는 것 |
|---|---|
| `header("X-API-Version")` | `x-api-version: "1.1"`, 그리고 `enum`·예시가 그 버전인 필수 헤더 파라미터 `X-API-Version` |
| `queryParameter("version")` | `x-api-version`, 그리고 위와 같은 필수 쿼리 파라미터 `version` |
| `mediaTypeParameter("v")` | `x-api-version` |
| `pathSegment()` | `x-api-version`. 버전은 이미 문서화한 경로에 들어 있습니다 |

같은 경로·메서드를 여러 버전으로 문서화하면 스펙 하나에는 그중 하나만 담을 수 있습니다.

- `splitSpecs = false` (기본값): 스펙 하나. 경로·메서드마다 가장 높은 버전이 남고, 빠진 버전을 빌드 경고로 알립니다.
- `splitSpecs = true`: 버전마다 스펙을 하나 더 만듭니다. `openapi3-v<version>.yaml` (`openapi3-v1.0.yaml`, `openapi3-v1.1.yaml`)이 epages 출력 디렉터리(`build/api-spec/`)의 `openapi3.yaml` 옆에 생기고, `apiDocs` 가 `build/api-docs/` 에도 복사합니다. 각 스펙에는 그 버전의 operation 과 버전이 없는 operation 이 모두 들어갑니다. `openapi3.yaml` 은 `false` 일 때처럼(operation 마다 가장 높은 버전) 그대로 만들고, 경고만 하지 않습니다.

- 기준 버전 표기(`version = "1.2+"`)는 `1.2` 로 적습니다. 버전은 마디별로 비교하고, 두 마디가 모두 숫자면 숫자로 비교합니다(`1.9` < `1.10`).
- epages 가 같은 경로·메서드의 조각을 operation 하나로 합치므로, 버전이 있는 operation 의 요청·응답 본문 스키마는 모든 스펙에서 그 버전들의 합입니다. 본문이 다른 버전은 다른 경로로 문서화하거나, 어떤 필드는 일부 버전에만 있을 수 있다는 점을 염두에 두세요.
- RestTestClient 테스트와 함수형 라우트(`RouterFunction`)는 핸들러 버전을 알 수 없어 `x-api-version` 이 붙지 않습니다.
