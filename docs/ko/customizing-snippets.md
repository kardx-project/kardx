[English](../customizing-snippets.md) | **한국어**

# 스니펫과 문서 모양 바꾸기

[문서 목록](README.md)

<!-- toc -->
**목차**

- [무엇을 어디서 바꾸나](#무엇을-어디서-바꾸나)
- [스니펫 이름과 경로](#스니펫-이름과-경로)
- [문서에 넣을 스니펫 고르기](#문서에-넣을-스니펫-고르기)
  - [사용자 스니펫 종류](#사용자-스니펫-종류)
- [스니펫 템플릿 바꾸기](#스니펫-템플릿-바꾸기)
  - [라이브러리가 제공하는 템플릿](#라이브러리가-제공하는-템플릿)
  - [예: 요청 필드 표에 Default·Example 열 두기](#예-요청-필드-표에-defaultexample-열-두기)
- [직접 만든 스니펫 더하기](#직접-만든-스니펫-더하기)
- [문서 틀 바꾸기](#문서-틀-바꾸기)
  - [예: 목차를 오른쪽에, 소제목을 한국어로](#예-목차를-오른쪽에-소제목을-한국어로)
  - [JUnit 에 연결하기](#junit-에-연결하기)
  - [Kotest 에 연결하기](#kotest-에-연결하기)
  - [index 문서](#index-문서)
- [직접 쓴 AsciiDoc 에서 쓰기](#직접-쓴-asciidoc-에서-쓰기)
- [요청·응답 가공](#요청응답-가공)
- [Enum 코드 문서 형식](#enum-코드-문서-형식)
<!-- /toc -->

생성되는 표의 열, 문서에 들어가는 스니펫, 섹션 제목 형식처럼 결과물의 모양을 바꾸는 방법을 한곳에 모았습니다. 이 문서의 예시는 저장소 밖의 별도 프로젝트(mavenLocal 의 0.1, Boot 4.1)에서 실행해 결과를 확인한 코드입니다.

용어 두 가지를 먼저 정리합니다.

- **스니펫(snippet)**: 테스트 하나가 만드는 AsciiDoc 조각 파일입니다 (`http-request.adoc`, `response-fields.adoc` …). Spring REST Docs 가 [Mustache](https://mustache.github.io/) 템플릿으로 만듭니다.
- **생성 문서**: 테스트 클래스(Kotest 는 스펙) 하나마다 이 라이브러리가 만드는 `<class-name>.adoc` 입니다. 스니펫을 `include::` 로 모은 것이고, `asciidoctor` 태스크가 HTML 로 바꿉니다.

## 무엇을 어디서 바꾸나

| 하고 싶은 것 | 방법 | 절 |
|---|---|---|
| 필드 표에 열을 더하거나 순서를 바꾸기 | 테스트 리소스에 같은 이름의 `.snippet` 템플릿을 둔다 | [스니펫 템플릿 바꾸기](#스니펫-템플릿-바꾸기) |
| 이 라이브러리에 없는 표를 문서에 더하기 | `TemplatedSnippet` 을 상속하고 `andDocument { snippets(...) }` 로 넘긴다 | [직접 만든 스니펫 더하기](#직접-만든-스니펫-더하기) |
| 생성 문서에서 curl 을 빼고 HTTPie 를 넣기 | yml `document-snippets` | [문서에 넣을 스니펫 고르기](#문서에-넣을-스니펫-고르기) |
| 소제목(`.CURL`, `.Request Fields`)·섹션 제목·목차 위치 바꾸기 | `DocumentGenerator` 재정의 | [문서 틀 바꾸기](#문서-틀-바꾸기) |
| 모든 문서 머리에 안내문 넣기 | yml `common-include-files` | [문서 틀 바꾸기](#문서-틀-바꾸기) |
| 직접 쓴 문서에 스니펫 몇 개만 가져오기 | `include::{snippets}/...[]`, `operation::` 매크로 | [직접 쓴 AsciiDoc 에서 쓰기](#직접-쓴-asciidoc-에서-쓰기) |
| 요청·응답 예시에서 헤더를 빼거나 호스트를 바꾸기 | 헤더 마스킹 설정, `http.requestPreprocessors`·`http.responsePreprocessors` | [요청·응답 가공](#요청응답-가공) |
| Enum 코드 표의 머리글·열 비율 바꾸기 | `AsciiDocEnumDocumentFormat` 상속 | [Enum 코드 문서 형식](#enum-코드-문서-형식) |

## 스니펫 이름과 경로

스니펫은 `<스니펫 디렉터리>/<문서 이름>/<테스트 자리>/<스니펫 이름>.adoc` 에 생깁니다. 스니펫 디렉터리의 기본값은 `build/generated-snippets` 이고 Gradle 플러그인의 `snippetsDirectory` 로 바꿉니다 ([생성 위치](build.md#생성-위치)).

| 테스트 | 스니펫 디렉터리 |
|---|---|
| JUnit `UserApiDocsTest.getUser()` | `user-api-docs-test/get-user/` |
| Kotest `UserApiDocsSpec` 의 `test("사용자 조회")` | `user-api-docs-spec/사용자-조회/` |

`@Nested`·파라미터 테스트의 규칙은 [테스트 모양별 스니펫·섹션](asciidoc.md#테스트-모양별-스니펫섹션)에, Kotest 이름 규칙은 [스니펫 디렉터리 이름](kotest.md#스니펫-디렉터리-이름)에 있습니다. 생성 문서는 이 경로를 `include::{snippets}/user-api-docs-test/get-user/http-request.adoc[]` 처럼 씁니다. `{snippets}` 는 Gradle 플러그인이 Asciidoctor 에 넘기는 속성이고, 접두사 자체는 `snippets.include-prefix` 로 바꿉니다 ([yml 설정](configuration.md#yml-설정)).

## 문서에 넣을 스니펫 고르기

테스트 디렉터리에 생긴 스니펫 중 생성 문서에 `include::` 할 것은 설정 `document.snippets`(yml `document-snippets`)로 고릅니다. 라이브러리 스니펫은 아래 표 순서를 따르고, [직접 정한 종류](#사용자-스니펫-종류)를 그 사이에 끼울 수 있습니다. 소제목은 각 include 위의 `.제목` 줄입니다.

| 상수 (`SnippetFile`) | 스니펫 파일 | 소제목 | 기본 포함 |
|---|---|---|---|
| `API_SUMMARY` | `api-summary` | 없음 | 예 |
| `MESSAGE` | `message-NN` | 없음 | 예 |
| `MESSAGE_FIELDS` | `request-fields-message-NN` | `Payload Fields` | 예 |
| `CURL_INFO` | `curl-request` | `CURL` | 예 |
| `HTTPIE_INFO` | `httpie-request` | `HTTPie` | 아니요 |
| `REQUEST_INFO` | `http-request` | `Request Info` | 예 |
| `REQUEST_PATH_PARAMETERS` | `path-parameters` | `Path Parameters` | 예 |
| `QUERY_PARAMETERS` | `query-parameters` | `Query Parameters` | 예 |
| `FORM_PARAMETERS` | `form-parameters` | `Form Parameters` | 예 |
| `REQUEST_HEADERS` | `request-headers` | `Request Headers` | 예 |
| `REQUEST_COOKIES` | `request-cookies` | `Request Cookies` | 예 |
| `REQUEST_PARTS` | `request-parts` | `Request Parts` | 예 |
| `REQUEST_PART_FIELDS` | `request-part-<파트>-fields` | `Request Part Fields (<파트>)` | 예 |
| `REQUEST_PART_BODY` | `request-part-<파트>-body` | `Request Part Body` | 아니요 |
| `REQUEST_FIELDS` | `request-fields` | `Request Fields` | 예 |
| `REQUEST_SUBSECTION_FIELDS` | `request-fields-<이름>` | `Request Fields (<이름>)` | 예 |
| `REQUEST_FILE` | `request-file` | `Request File` | 예 (바이너리 요청 본문이나 `requestFile(...)` 일 때만) |
| `REQUEST_BODY` | `request-body` | `Request Body` | 아니요 |
| `RESPONSE_INFO` | `http-response` | `Response Info` | 예 |
| `RESPONSE_HEADERS` | `response-headers` | `Response Headers` | 예 |
| `RESPONSE_COOKIES` | `response-cookies` | `Response Cookies` | 예 |
| `RESPONSE_FILE` | `response-file` | `Response File` | 예 (바이너리 응답 본문이나 `responseFile(...)` 일 때만, [파일 다운로드](http-api.md#파일-다운로드)) |
| `RESPONSE_FIELDS` | `response-fields` | `Response Fields` | 예 |
| `SSE_EVENTS` | `sse-events` | `Events` | 예 |
| `EVENT_FIELDS` | `response-fields-event-<이벤트>` | `Event Data (<이벤트>)` | 예 |
| `RESPONSE_SUBSECTION_FIELDS` | `response-fields-<이름>` | `Response Fields (<이름>)` | 예 |
| `LINKS` | `links` | `Links` | 예 |
| `ERROR_RESPONSES` | `error-responses` | `Error Responses` | 예 ([선언한 오류 응답](http-api.md#선언한-오류-응답)이 있는 테스트에만 생김) |
| `RESPONSE_BODY` | `response-body` | `Response Body` | 아니요 |

- 기본에서 빠진 넷은 다른 스니펫과 내용이 겹치는 것입니다(`request-body` 는 `http-request` 의 본문과 같습니다).
- `request-file`·`response-file` 이 들어가면 같은 테스트의 `request-body`·`response-body` 는 빠지고 표가 그 자리를 대신합니다 (그 본문은 `<binary: N bytes>` 뿐입니다).
- WebSocket·STOMP 메시지 스니펫은 메시지 순번별로 묶여 들어갑니다 (`message-01`, 그 필드, `message-02` …).
- [종류로 등록](#사용자-스니펫-종류)하지 않은 직접 만든 스니펫은 이 설정과 상관없이 항상 들어가고, 순서가 정해진 스니펫 뒤에 파일 이름순으로 옵니다. 소제목은 파일 이름(`required-roles`)입니다.

yml 에는 스니펫 파일 이름이나 상수 이름(`request-info`, `REQUEST_INFO` 모두 됨)을 적습니다. 적은 것만 들어가므로 기본값에 더하려면 기본 목록도 함께 적습니다. 모르는 이름이면 `kardx.document-snippets: unknown snippet '...'` 로 실패합니다.

```yaml
# src/test/resources/application-docs.yml
kardx:
  document-snippets: api-summary, http-request, httpie-request, path-parameters, request-fields, http-response, response-headers, response-fields
```

코드로는 `RestDocsConfiguration.configure { document { snippets = SnippetFile.defaults() + SnippetFile.HTTPIE_INFO } }` 입니다 (`io.github.kardx.config.SnippetFile`).

이 설정은 생성 문서에 무엇을 include 할지만 정합니다. 스니펫 파일은 그대로 만들어지므로 직접 쓴 문서에서는 빠진 스니펫도 가져다 쓸 수 있습니다.

### 사용자 스니펫 종류

[직접 만든 스니펫](#직접-만든-스니펫-더하기)에 소제목을 붙이고 라이브러리 스니펫 사이 자리를 주려면 `SnippetFile.custom` 으로 종류를 만들고 `document { register(...) }` 로 등록합니다.

```kotlin
import io.github.kardx.config.SnippetFile

val REQUIRED_ROLES = SnippetFile.custom("required-roles", title = "필요 권한")

RestDocsConfiguration.configure {
    document { register(REQUIRED_ROLES, after = SnippetFile.RESPONSE_FIELDS) }
}
```

- `fileName` 은 `.adoc` 을 뺀 스니펫 파일 이름(직접 만든 `Snippet` 이 쓰는 이름)입니다. 라이브러리 스니펫과 이름이 같으면 실패합니다. `title` 은 소제목이고, 빈 문자열이면 소제목 없이 include 합니다.
- `after = ...` 면 그 종류 뒤, `before = ...` 면 그 앞, 둘 다 없으면 맨 뒤에 둡니다. 같은 종류를 다시 등록하면 자리를 옮깁니다. 그 결과 순서는 `document.order`(읽기 전용)입니다.
- `includedByDefault = true`(기본값)면 등록할 때 `document.snippets` 에도 더합니다. `false` 면 직접 더합니다(`snippets += REQUIRED_ROLES`). 등록한 뒤 `document.snippets` 를 통째로 바꾸면 그 종류가 다시 빠지므로, 먼저 바꾸거나 `+=` 로 더합니다.
- yml `document-snippets` 에는 라이브러리 스니펫 이름만 적을 수 있습니다. 사용자 종류는 코드로 정합니다.
- `SnippetFile` 은 enum 이 아니라 클래스입니다. 라이브러리 종류 목록은 `SnippetFile.BUILT_IN`(예전 `SnippetFile.entries`)이고, `when (kind)` 에는 `else` 가 필요합니다.

## 스니펫 템플릿 바꾸기

스니펫은 클래스패스의 `org/springframework/restdocs/templates/asciidoctor/<스니펫 이름>.snippet` 템플릿으로 만듭니다. 테스트 리소스의 같은 경로에 파일을 두면 라이브러리 jar 의 템플릿보다 먼저 읽힙니다.

```
src/test/resources/org/springframework/restdocs/templates/asciidoctor/request-fields.snippet
```

템플릿 문법은 Mustache 입니다. `{{name}}` 은 값, `{{#name}}...{{/name}}` 은 값이 있거나 참일 때(목록이면 원소마다), `{{^name}}...{{/name}}` 은 값이 없거나 거짓일 때입니다. `{{#tableCellContent}}...{{/tableCellContent}}` 는 REST Docs 가 주는 함수로, 표 칸 안의 `|` 를 이스케이프합니다.

### 라이브러리가 제공하는 템플릿

라이브러리 jar 에 아래 템플릿이 들어 있습니다. 여기 없는 스니펫(`http-request`, `curl-request`, `request-body` …)은 Spring REST Docs 기본 템플릿을 씁니다. 바꾸려면 [Spring REST Docs 기본 템플릿](https://github.com/spring-projects/spring-restdocs/tree/main/spring-restdocs-core/src/main/resources/org/springframework/restdocs/templates/asciidoctor)(`default-<이름>.snippet`)을 복사해 `<이름>.snippet` 으로 둡니다.

| 템플릿 | 열 | 항목 목록 |
|---|---|---|
| `request-fields`, `response-fields`, `request-part-fields` | Path, Type, Required, Description, Constraints | `fields` |
| `path-parameters`, `query-parameters`, `form-parameters` | Parameter, Required, Description, Constraints | `parameters` (`path-parameters` 는 표 제목 `{{path}}`) |
| `request-headers`, `response-headers` | Name, Required, Description, Constraints | `headers` |
| `request-cookies`, `response-cookies` | Name, Required, Description | `cookies` |
| `request-parts` | Part, Required, Description | `requestParts` |
| `links` | Relation, Required, Description | `links` |
| `api-summary` (이 라이브러리) | 표 없음 | `method`, `path`(URL 템플릿), `status`, `description`. deprecated API 일 때만 `deprecated`(true)·`deprecationNote`(이유) ([deprecated API](http-api.md#deprecated-api)) |
| `sse-events` (이 라이브러리) | Event, Required, Description | `events` (`name`, `optional`, `description`) |
| `error-responses` (이 라이브러리) | 상태·설명 표, 이어서 응답마다 필드 표(경로, 타입, 설명)와 예시 블록 | `responses`(`status`, `description`), `details`(`hasFields`, `fields`(`path`, `type`, `description`), `fieldsTitle`, `example`, `exampleTitle`, `language`), 머리글 문구 `statusHeader`, `descriptionHeader`, `pathHeader`, `typeHeader` ([선언한 오류 응답](http-api.md#선언한-오류-응답)) |
| `file-body` (이 라이브러리, `request-file`·`response-file` 이 씀) | 이름·값 두 열, 항목마다 한 행 | `rows`(`label`, `value`, `code`(미디어 타입·파일 이름이면 `true`)) ([파일 다운로드](http-api.md#파일-다운로드)) |
| `message` (이 라이브러리) | Headers 표와 Payload | `direction`, `destination`, `protocol`, `description`, `hasHeaders`, `headers`(`name`, `optional`, `description`, `example`), `payload` |

필드·파라미터·헤더 항목 하나에서 쓸 수 있는 값입니다. 앞의 넷은 Spring REST Docs 가, 나머지는 이 라이브러리가 디스크립터 속성으로 넣습니다. 값이 없는 항목에는 키가 없으므로 `{{#키}}` 로 감싸 씁니다.

| 값 | 내용 | 있는 곳 |
|---|---|---|
| `path` / `name` / `rel` | 필드 경로 / 파라미터·헤더·쿠키·파트 이름 / 링크 관계 | 모두 |
| `type` | JSON 타입 (`String`, `Number` …) | 필드 |
| `description` | 설명 | 모두 |
| `optional` | optional 이면 `true` | 모두 |
| `constraints` | 제약 설명 한 줄 (`NotBlank - ...`, `constraint "..."`) | 필드, 파라미터, 헤더 |
| `default` | `withDefaultValue` 값 | 필드, 파라미터, 헤더 |
| `example` | `example` 값 | 필드, 파라미터, 헤더 |
| `requiredByConstraint` | optional 이지만 `@NotNull`·`@NotBlank`·`@NotEmpty` 로 값이 꼭 있어야 하면 `true` | 필드, 파라미터 |
| `deprecated`, `deprecationNote` | `@Deprecated`·`deprecated()` 표시와 이유 | 필드, 파라미터 |
| `masked`, `maskingRule` | `@Masked`·`masked()` 표시와 규칙 ([마스킹되는 필드 표시](http-api.md#마스킹되는-필드-표시)) | 본문 필드 |

기본 템플릿은 `default`·`example` 을 설명 칸 아래 `Default:`·`Example:` 줄로, `deprecated`·`masked` 를 설명 칸 맨 위 `*Deprecated*: 이유`, `*Masked*: 규칙` 줄로 보여 줍니다. Required 열은 `optional` 이 아니거나 `requiredByConstraint` 이면 `true` 입니다.

### 예: 요청 필드 표에 Default·Example 열 두기

설명 칸 아래에 붙던 기본값과 예시를 따로 열로 뺍니다.

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
        "nickname" type STRING means "닉네임" example "홍길동" constraint "1~20자",
        "bio" type STRING means "소개" isOptional true withDefaultValue "(없음)",
    )
}
```

결과 `request-fields.adoc`:

```
|===
|Path|Type|Required|Description|Default|Example|Constraints

|`+nickname+`
|`+String+`
|true
|닉네임
|
|`+홍길동+`
|1~20자

|`+bio+`
|`+String+`
|false
|소개
|`+(없음)+`
|
|

|===
```

- 열을 지우면 그 정보가 표에서 사라질 뿐 검증과 OpenAPI 에는 영향이 없습니다. Constraints 열을 지우면 제약이 표에 나오지 않습니다.
- `response-fields` 처럼 여러 템플릿이 같은 모양이면 바꾸려는 것마다 파일을 둡니다.
- 템플릿을 바꾼 뒤에는 테스트를 다시 돌려야 스니펫이 새로 만들어집니다 (`./gradlew apiDocs` 는 테스트 입력이 바뀌었으므로 다시 실행합니다).

Markdown 형식(`withTemplateFormat(TemplateFormats.markdown())`)을 쓰면 템플릿 경로가 `templates/markdown/` 입니다. 라이브러리는 Markdown 용으로 `api-summary`, `error-responses`, `file-body`, `message`, `sse-events` 만 제공하고, 생성 문서는 AsciiDoc 전용입니다.

## 직접 만든 스니펫 더하기

블록 함수로 표현할 수 없는 정보(권한, 요금, 호출 제한 …)는 Spring REST Docs 의 `TemplatedSnippet` 을 상속해 스니펫으로 만들고 `andDocument { snippets(...) }` 로 넘깁니다.

```kotlin
package com.example.demo.support

import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.TemplatedSnippet

/** 이 API 를 부를 수 있는 권한 표. 파일 이름 required-roles.adoc, 템플릿 required-roles.snippet. */
class RequiredRolesSnippet(private val roles: Map<String, String>) : TemplatedSnippet("required-roles", null) {
    override fun createModel(operation: Operation): Map<String, Any> = mapOf(
        "roles" to roles.map { (name, description) -> mapOf("name" to name, "description" to description) },
    )
}

fun requiredRoles(vararg roles: Pair<String, String>): RequiredRolesSnippet = RequiredRolesSnippet(roles.toMap())
```

`createModel` 이 돌려준 맵이 템플릿 값이 됩니다. `operation` 에서 실제 요청·응답(`operation.request.headers`, `operation.response.status` …)도 읽을 수 있습니다. 템플릿은 `src/test/resources/org/springframework/restdocs/templates/asciidoctor/required-roles.snippet` 에 둡니다. 템플릿이 없으면 테스트가 템플릿을 찾지 못했다는 오류로 실패합니다.

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
    description = "ID로 사용자를 조회한다."
    pathParameters("id" pathMeans "사용자 ID")
    responseFields<UserResponse>()
    snippets(requiredRoles("USER" to "로그인한 사용자", "ADMIN" to "관리자"))
}
```

- 스니펫은 테스트 디렉터리에 `required-roles.adoc` 으로 생기고, 생성 문서의 섹션 끝(알려진 스니펫 뒤)에 소제목 `.required-roles` 로 들어갑니다. 소제목과 자리를 정하려면 [사용자 스니펫 종류](#사용자-스니펫-종류)로 등록하거나 [문서 틀 바꾸기](#문서-틀-바꾸기)에서 바꿉니다.
- Spring REST Docs 의 다른 스니펫(`requestBody()`, `responseBody(beneathPath("data"))` 등)도 같은 방법으로 넘깁니다.
- `snippets(...)` 로 넘긴 스니펫은 OpenAPI 조각에 들어가지 않습니다.

## 문서 틀 바꾸기

생성 문서의 각 부분은 `io.github.kardx.asciidoc.DocumentGenerator` 의 함수가 만듭니다. 상속해서 필요한 함수만 재정의합니다. 각 함수는 끝 개행 없는 블록 하나를 돌려주고, 블록 사이는 빈 줄 하나로 이어집니다. 빈 문자열을 돌려주면 그 블록은 빠집니다.

| 함수 | 만드는 부분 | 기본값 |
|---|---|---|
| `startPart()` | 문서 맨 앞(제목보다 먼저) | 없음 |
| `titlePart(title, description)` | `= 제목`, 헤더 속성, index 링크, 공통 include | 아래 함수들을 조합 |
| `config()` | 헤더 속성 `Config(doctype, icons, sourceHighlighter, toc, tocLevels, isSectLinks)` | `book`, `font`, `highlightjs`, `left`, `3`, `true` |
| `tocTitleAttribute()` | 왼쪽 목차 제목 자리의 index 링크 (`:toc-title:` 한 줄) | `index-link` 설정을 따른다 |
| `navigationPart()` | 제목 아래와 문서 끝의 `← 메인으로 돌아가기` 링크 (`language = "ko"`. 기본 `en` 은 `← Back to index`) | `index-link`, `index-link-text` 설정 |
| `commonIncludeFiles()` | 제목 아래에 include 할 파일 | `common-include-files` 설정 |
| `sectionTitlePart(methodId, title, description)` | 테스트 하나의 섹션 제목과 앵커 | `[[앵커]]` + `== icon:link[] 제목` |
| `sectionRowPart(snippet, snippetDirectory)` | 스니펫 하나의 `.소제목` + `include::` | 위 [표](#문서에-넣을-스니펫-고르기)의 소제목 |
| `endPart()` | 문서 맨 끝 | 없음 |

`sectionRowPart` 를 재정의해 include 줄을 직접 만들 때는 상속한 `snippetsIncludePrefix`(protected, 기본값은 설정 `snippets.include-prefix` 의 값 `{snippets}`)를 접두사로 씁니다. 생성자 인자(`DocumentGenerator("{snippets}")`)로 정할 수도 있습니다.

```kotlin
override fun sectionRowPart(snippet: SnippetInclude, snippetDirectory: String): String =
    "include::$snippetsIncludePrefix/$snippetDirectory/${snippet.fileName}[leveloffset=+1]"
```

설정만으로 되는 것은 설정을 씁니다. 모든 문서 머리에 파일을 넣는 것은 `common-include-files`, index 링크의 대상과 글자는 `index-link`·`index-link-text`(`none` 이면 링크를 모두 뺌)입니다 ([yml 설정](configuration.md#yml-설정)).

### 예: 목차를 오른쪽에, 소제목을 한국어로

```kotlin
package com.example.demo.support

import io.github.kardx.asciidoc.DocumentGenerator
import io.github.kardx.config.SnippetFile
import io.github.kardx.config.SnippetInclude

/** 목차를 오른쪽에, 섹션 제목에서 링크 아이콘을 빼고, 소제목을 한국어로 바꾼다. */
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

    override fun endPart(): String = "'''\n문의: api@example.com"

    private companion object {
        val TITLES = mapOf(
            SnippetFile.CURL_INFO to "curl 예시",
            SnippetFile.REQUEST_INFO to "요청",
            SnippetFile.RESPONSE_INFO to "응답",
        )
        val CUSTOM_TITLES = mapOf("required-roles.adoc" to "필요한 권한")
    }
}
```

- `SnippetInclude` 는 스니펫 하나입니다. `kind` 는 위 표의 `SnippetFile` 이나 등록한 사용자 종류(그 밖의 직접 만든 스니펫이면 `null`), `fileName` 은 `request-fields.adoc` 같은 파일 이름, `title` 은 기본 소제목, `messageGroup` 은 테스트 안의 메시지 순번입니다(`request-fields-message-02` → 2, 메시지가 아니면 0. 메시지 문서에서 `sectionRowPart` 를 바꿀 때 씁니다).
- `asciidocAnchor` 는 이름을 AsciiDoc 앵커로 다듬는 함수(`DocumentGenerator.asciidocAnchor`)입니다. 섹션 앵커를 바꾸면 index 의 섹션 링크와 어긋나므로 앵커 줄은 그대로 둡니다.
- `tocTitleAttribute()` 를 빈 문자열로 두면 목차 제목 자리의 index 링크만 빠지고, 제목 아래·문서 끝의 링크(`navigationPart`)는 남습니다.

결과 `build/generated-asciidoc/user-api-docs-test.adoc` 의 일부:

```asciidoc
= 사용자 API
:doctype: book
:icons: font
:source-highlighter: highlightjs
:toc: right
:toclevels: 2
:sectlinks:

[.restdocs-nav]
link:index.html[&#8592; 메인으로 돌아가기]

include::_landing.adoc[]

[[get-user]]
== 사용자 조회

include::{snippets}/user-api-docs-test/get-user/api-summary.adoc[]

.curl 예시
include::{snippets}/user-api-docs-test/get-user/curl-request.adoc[]

.요청
include::{snippets}/user-api-docs-test/get-user/http-request.adoc[]

...

.필요한 권한
include::{snippets}/user-api-docs-test/get-user/required-roles.adoc[]

[.restdocs-nav]
link:index.html[&#8592; 메인으로 돌아가기]

'''
문의: api@example.com
```

### JUnit 에 연결하기

`AutoRestDocumentationExtension` 을 `@ExtendWith` 대신 `@RegisterExtension` 으로 등록하고, 생성자 인자 `documentGenerator` 에 `DocumentGeneratorFactory` 를 넘깁니다. 확장은 final 이라 상속해 `documentGenerator()` 를 재정의하는 방법은 더 이상 없습니다.

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

- 추상 기본 클래스의 static `@RegisterExtension` 필드는 기본 클래스의 `@ExtendWith` 처럼 모든 하위 클래스에 적용됩니다.
- 팩터리는 `DocumentGeneratorContext`(`documentClass`: 가장 바깥 테스트 클래스)를 받으므로 문서마다 생성기를 고를 수 있습니다.
- `@ExtendWith(AutoRestDocumentationExtension::class)` 는 그대로 쓸 수 있고 기본 생성기를 씁니다.

### Kotest 에 연결하기

`RestDocsExtension` 의 생성자 인자 `documentGenerator` 에 같은 `DocumentGeneratorFactory` 를 넘깁니다 ([Kotest](kotest.md#규칙-바꾸기)). 생성자 인자라 그냥 람다를 넘겨도 됩니다.

```kotlin
val restDocs = extension(RestDocsExtension(documentGenerator = { MyDocumentGenerator() }))
```

프로젝트 설정(`AbstractProjectConfig.extensions`, Kotest 5.x 는 `override fun extensions()`)에 등록할 때도 같은 인자를 줍니다.

### index 문서

index 는 생성 문서가 아니라 직접 쓰는 템플릿(`src/docs/asciidoc/index-template.adoc`)에서 만듭니다. 머리말·속성·링크는 템플릿에 그대로 쓰고, `// restdocs:api-index` 줄 자리에 테스트 클래스별 링크가 들어갑니다 ([index 문서](asciidoc.md#index-문서)).

## 직접 쓴 AsciiDoc 에서 쓰기

`src/docs/asciidoc/` 에 둔 `.adoc` 은 생성 문서와 함께 HTML 이 됩니다(`_` 로 시작하는 파일은 단독 HTML 이 되지 않음). 여기서 스니펫을 가져오는 방법은 둘입니다.

```asciidoc
= 사용 안내
:toc: left

== 사용자 조회 예시 (include)

include::{snippets}/user-api-docs-test/get-user/http-request.adoc[]

include::{snippets}/user-api-docs-test/get-user/response-fields.adoc[]

== 사용자 조회 예시 (operation 매크로)

operation::user-api-docs-test/get-user[snippets='curl-request,http-response']
```

- `include::` 는 파일 하나를 그대로 넣습니다. 소제목은 직접 씁니다.
- `operation::<스니펫 디렉터리>[snippets='...']` 는 Spring REST Docs Asciidoctor 확장의 매크로로, 고른 스니펫마다 제목(`Curl request`, `HTTP response`)을 붙여 넣습니다. `snippets` 를 빼면 그 디렉터리의 스니펫을 모두 넣습니다. Gradle 플러그인이 이 확장을 Asciidoctor 에 연결합니다.
- 위 파일은 `build/api-docs/guide.html` 이 되므로 index 템플릿에 `link:./guide.html[사용 안내]` 로 링크합니다.
- `.adoc` 이 아닌 파일을 include 하면 그 파일만 바뀌었을 때 Asciidoctor 가 다시 실행되지 않습니다 ([Gradle 플러그인](build.md#gradle-플러그인)).

## 요청·응답 가공

`andDocument`(MockMvc·WebTestClient·RestTestClient, SSE `performStream` 포함)는 요청·응답 예시(`http-request`, `http-response`, `curl-request` …)와 OpenAPI 예시, WireMock 매핑을 만들기 전에 다음을 항상 적용합니다. MockMvc·WebTestClient 를 `restDocsMockMvc`·`restDocsWebTestClient` 로 만들었든 직접 만들었든 같습니다.

| 순서 | 가공 | 내용 | 바꾸는 법 |
|---|---|---|---|
| 1 | URI | `http://localhost` 로, 포트는 뺌 | 뒤의 `http.requestPreprocessors` 로 다시 바꿈 |
| 2 | 헤더·쿼리·폼 파라미터 마스킹 | 고른 헤더와 쿼리·폼 파라미터의 값을 가림 (URI 쿼리, 폼 본문, multipart 의 파일이 아닌 파트. 기본은 가리지 않음) | `mask-sensitive-headers`, `masked-headers`, `masked-query-parameters`, `masked-header-value`, `masking.masker` ([민감 정보 마스킹](configuration.md#민감-정보-마스킹)) |
| 3 | pretty print | JSON·XML 본문을 들여쓰기 | 끌 수 없음 |
| 4 | 실행마다 바뀌는 응답 헤더 | 값이 실행마다 바뀌는 응답 헤더(기본 `Date`)와 `Max-Age` 가 있는 `Set-Cookie` 의 `Expires` 속성을 뺌. `responseHeaders(...)`(DSL 이나 `snippets(...)` 로 넘긴 REST Docs 스니펫)로 문서화한 헤더는 남김 | `http.removedResponseHeaders`, `removed-response-headers` ([설정 목록](configuration.md#설정-목록)) |
| 5 | 사용자 전처리기 | `http.requestPreprocessors` / `http.responsePreprocessors` 의 Spring REST Docs `OperationPreprocessor` 를 순서대로 | 아래 |

헤더를 예시에서 아예 빼거나 호스트를 실제 주소로 보이게 하려면 `http.requestPreprocessors`·`http.responsePreprocessors` 에 Spring REST Docs 전처리기를 넣습니다. 위 1~4 **뒤에** 적용되므로 라이브러리가 바꾼 URI 를 다시 바꿀 수 있고, 가린 헤더 값과 pretty print 된 본문을 받습니다. 코드로만 정하고(yml 키 없음) 기본값은 빈 목록입니다. 문서화할 때 읽으므로 `restDocsMockMvc` 의 Spring Security 적용·`http.printRequests` 를 그대로 쓰면서 더할 수 있습니다.

```kotlin
import org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders
import org.springframework.restdocs.operation.preprocess.Preprocessors.modifyUris

// 모든 테스트: 기본 클래스나 @BeforeAll 에서
RestDocsConfiguration.configure {
    http {
        requestPreprocessors = listOf(
            modifyHeaders().remove("X-Debug"),
            modifyUris().scheme("https").host("api.example.com").removePort(),
        )
        responsePreprocessors = listOf(modifyHeaders().remove("X-Trace-Id"))
    }
}

// 테스트 하나에서만
RestDocsConfiguration.withSettings({ http.responsePreprocessors = listOf(modifyHeaders().remove("X-Request-Id")) }) {
    mockMvc.perform(get("/api/users/{id}", 1))
        .andDocument { ... }
}
```

결과 `http-request.adoc` 에는 `X-Debug` 헤더가 없고 `Host: api.example.com` 이 들어가며, `curl-request.adoc` 은 `curl 'https://api.example.com/api/users/1'` 입니다. 응답 예시에서는 `X-Trace-Id` 가 빠집니다(그 테스트 하나에서는 `X-Request-Id` 도).

- `Date` 헤더는 기본으로 이미 빠집니다(4번). `bindToServer` 로 띄운 서버는 실행마다 다른 값을 보내 스니펫·WireMock 매핑이 매번 바뀌기 때문입니다. 남기려면 `http.removedResponseHeaders = emptySet()`(yml `removed-response-headers: none`)으로 두거나 `responseHeaders(...)` 로 문서화합니다(DSL, 또는 `snippets(...)` 로 넘긴 REST Docs `HeaderDocumentation.responseHeaders`). 같은 방식으로 더 빼려면 `http.removedResponseHeaders += "X-Request-Id"` 처럼 더합니다. yml 목록은 기본값에 더하지 않고 전체를 정하므로 `removed-response-headers: Date, X-Request-Id` 처럼 씁니다.
- 같은 이유로 `Max-Age` 가 있는 `Set-Cookie`(Spring `ResponseCookie.maxAge(...)`)는 지금 시각에서 계산된 `Expires` 속성을 뺍니다. 둘 다 있으면 브라우저는 `Max-Age` 를 따릅니다. `Max-Age` 가 없는 쿠키의 `Expires` 는 남습니다. `http.removedResponseHeaders` 가 비어 있으면 응답을 받은 그대로 둡니다.
- 예시에서 뺀 헤더도 `requestHeaders`·`responseHeaders` 로 문서화하면 실제 요청·응답으로 검증합니다.
- 바꾼 URI 는 예시(스니펫, OpenAPI 예시)에만 쓰입니다. OpenAPI 의 서버 주소는 `openapi3 { setServer(...) }` 로 정합니다.
- 메시지 문서화(`MessageDocumentation`)는 HTTP 요청·응답이 아니므로 이 설정을 쓰지 않습니다.
- Spring REST Docs 의 기본 전처리기(`documentationConfiguration(provider).operationPreprocessors().withRequestDefaults(...)`)를 건 MockMvc 를 직접 만들어도 동작합니다. REST Docs 는 기본 전처리기를 문서화 호출의 전처리기(위 1~5) 뒤에 적용하므로 결과는 같지만, `restDocsMockMvc` 가 하는 일(Spring Security 필터 체인, `print-requests`, 앱 Jackson 매퍼)을 직접 더해야 합니다.

## Enum 코드 문서 형식

[Enum 코드 문서](asciidoc.md#enum-코드-문서)의 모양은 `EnumDocumentFormat` 이 정합니다. 기본 구현 `AsciiDocEnumDocumentFormat` 을 상속해 필요한 함수만 바꿉니다.

| 함수 | 만드는 부분 |
|---|---|
| `header(title, packageName, indexLink)` | 문서 제목과 헤더 속성 |
| `tableHeader(title, className)` | Enum 하나의 앵커·절 제목·표 머리 |
| `tableRow(code, description)` | 상수 한 줄 |
| `tableFooter()`, `footer()` | 표 끝, 문서 끝 |
| `anchorOf(title)` (protected) | 표 앵커. 기본은 제목의 공백을 `_` 로 |

```kotlin
import io.github.kardx.asciidoc.AsciiDocEnumDocumentFormat

class KoreanEnumFormat : AsciiDocEnumDocumentFormat() {
    override fun tableHeader(title: String, className: String): String = """
        [[${anchorOf(title)}]]
        == $title (`$className`)

        [cols="2,8"]
        |===
        | 코드 | 설명
    """.trimIndent()
}
```

`EnumDocumentationExtension` 의 생성자 인자 `format` 에 넘깁니다 (JUnit·Kotest 같은 이름).

```kotlin
// JUnit
class MyEnumDocs : EnumDocumentationExtension(packageName = "com.example.demo", title = "코드 정의", format = KoreanEnumFormat())

// Kotest (io.github.kardx.kotest.EnumDocumentationExtension)
EnumDocumentationExtension(packageName = "com.example.demo", title = "코드 정의", format = KoreanEnumFormat())
```

결과:

```asciidoc
[[사용자_상태]]
== 사용자 상태 (`UserStatus`)

[cols="2,8"]
|===
| 코드 | 설명

| ACTIVE
| 활성
...
```

AsciiDoc 이 아닌 형식이 필요하면 `EnumDocumentFormat` 을 직접 구현합니다(`fileExtension`, `header`, `tableHeader`, `tableRow`, `tableFooter`). 이때 결과 파일은 Asciidoctor 가 HTML 로 바꾸지 않습니다.
