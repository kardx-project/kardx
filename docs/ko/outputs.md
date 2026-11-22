[English](../outputs.md) | **한국어**

# 뷰어·클라이언트·stub·공유

[문서 목록](README.md)

<!-- toc -->
**목차**

- [OpenAPI 뷰어 (Redoc · Scalar)](#openapi-뷰어-redoc--scalar)
  - [Swagger UI 와의 차이](#swagger-ui-와의-차이)
  - [redoc 과 scalar 고르기](#redoc-과-scalar-고르기)
  - [CDN 에서 스크립트를 받습니다](#cdn-에서-스크립트를-받습니다)
  - [이름과 시작 로그](#이름과-시작-로그)
- [Postman Collection](#postman-collection)
- [IntelliJ HTTP Client](#intellij-http-client)
- [WireMock stub](#wiremock-stub)
- [스펙 내보내기](#스펙-내보내기)
  - [apiDocsExport](#apidocsexport)
  - [apiDocsUpload](#apidocsupload)
  - [Maven 배포물](#maven-배포물)
- [여러 프로젝트 모으기](#여러-프로젝트-모으기)
<!-- /toc -->

같은 문서화 테스트로 만드는 선택 산출물입니다: 정적 OpenAPI 뷰어, Postman Collection, IntelliJ HTTP Client 파일, WireMock stub, 스펙 내보내기·업로드, 여러 프로젝트를 모은 페이지. 기본으로 꺼져 있거나 직접 실행할 때만 돕니다. 플러그인 설정과 태스크는 [빌드와 산출물](build.md)에 있습니다.

## OpenAPI 뷰어 (Redoc · Scalar)

생성한 OpenAPI 스펙을 보여 주는 HTML 파일 하나(`openapi.html`)를 만듭니다. 스펙을 페이지 안에 넣으므로 앱 없이 파일을 더블클릭해 열어도 보이고, 그 파일 하나만 메일·메신저·위키 첨부로 전달해도 됩니다. 기본으로 꺼져 있습니다.

```kotlin
kardx {
    openApiViewer {
        enabled = true              // 기본값 false
        type = "redoc"              // 기본값. "scalar"
        fileName = "openapi.html"   // 기본값. 하위 경로도 됨: "api/reference.html"
        title = "주문 API"          // 브라우저 탭 제목. 기본값: 스펙의 info.title
    }
}
```

`./gradlew apiDocs` 를 실행하면 `apiDocsViewer` 태스크가 `openapi3` 결과로 페이지를 만들어 다른 문서와 함께 모읍니다. `com.epages.restdocs-api-spec` 플러그인이 있어야 합니다(스펙을 만드는 쪽이므로).

| 결과 | 위치 |
|---|---|
| 빌드 | `build/api-docs/openapi.html` |
| 앱 (IDE 실행·`bootRun`·실행 jar) | `http://localhost:8080/docs/openapi.html` |
| 앱 시작 로그 | `API docs (OpenAPI viewer) http://localhost:8080/docs/openapi.html` (`language = "ko"` 면 `API 문서 (OpenAPI 뷰어)`) |

### Swagger UI 와의 차이

| | Swagger UI | 정적 뷰어 (Redoc · Scalar) |
|---|---|---|
| 용도 | 개발 중 API 를 직접 호출해 보기 (Try it out) | 읽기 좋은 참조 문서. Scalar 는 요청 보내기도 된다 |
| 필요한 것 | 앱 의존성 `springdoc-openapi-starter-webmvc-ui`(WebFlux 는 `-webflux-ui`)와 `springdoc.swagger-ui.url` 설정 | Gradle 플러그인 설정 한 줄. 앱 의존성 없음 |
| 서버 없이 열기 | 안 됨 (앱이 떠 있어야 한다) | 됨 (스펙이 파일 안에 있다) |
| 배포 | 앱과 함께 (`/swagger-ui/index.html`) | 앱의 `/docs/openapi.html`, 또는 파일 하나를 정적 호스팅·첨부로 |
| 스크립트 | springdoc jar 안에 들어 있다 | 브라우저가 unpkg CDN 에서 받는다 (아래) |

둘은 같은 `openapi3.yaml` 을 보여 주므로 함께 켜 둬도 됩니다.

### redoc 과 scalar 고르기

- **`redoc`**(기본): 왼쪽 메뉴, 가운데 설명, 오른쪽 요청·응답 예시의 3단 화면입니다. 요청을 보내는 기능이 없어 외부 연동사·기획자에게 넘기는 읽기 전용 문서에 맞습니다.
- **`scalar`**: 같은 참조 화면에 요청 보내기(테스트 클라이언트)가 있습니다. 앱에서 연 페이지는 스펙의 `servers`(예: `openapi3 { setServer("/") }`)로 요청을 보내므로 Swagger UI 의 Try it out 대신 쓸 수 있습니다. 파일로 열었을 때는 서버 주소를 실제 앱 주소로 바꿔야 하고, 브라우저의 CORS 제한을 받습니다. 개발자 도구 막대와 AI 질문 기능(스펙을 외부 서비스로 보냄)은 끈 상태로 띄웁니다.

### CDN 에서 스크립트를 받습니다

페이지에는 스펙만 들어 있고 뷰어 스크립트는 열 때 브라우저가 unpkg CDN 에서 받습니다. 버전과 무결성 해시(SRI)를 고정해 다른 파일로 바뀌지 않게 합니다.

| type | 스크립트 (MIT) |
|---|---|
| `redoc` | `https://unpkg.com/redoc@2.5.4/bundles/redoc.standalone.js` |
| `scalar` | `https://unpkg.com/@scalar/api-reference@1.76.4/dist/browser/standalone.js` |

- 인터넷에 접근할 수 없는 환경(폐쇄망, CDN 차단)에서는 "Could not load the Redoc script (unpkg.com)" 안내만 보입니다(`language = "ko"` 면 "Redoc 스크립트(unpkg.com)를 불러오지 못했습니다"). 이때는 같은 디렉터리의 `openapi3.yaml` 을 사내 도구로 보거나 Swagger UI(스크립트가 jar 에 포함)를 씁니다.
- 스펙은 페이지 안의 스크립트 변수로 들어 있고, CDN 에는 스크립트 파일만 요청합니다.

### 이름과 시작 로그

- `fileName` 을 바꿔도 앱 설정은 고치지 않아도 됩니다. `apiDocsAppDocs` 가 시작 로그용으로 파일 이름을 기록합니다 ([앱 시작 로그](build.md#앱-시작-로그)). `kardx.startup-log.open-api-viewer.file` 은 그 값을 덮어쓸 때만 정합니다. 로그 줄의 이름은 `startup-log.open-api-viewer.label`, 끄려면 `startup-log.open-api-viewer.enabled: false` 입니다 ([앱 시작 로그](build.md#앱-시작-로그)).
- 시작 로그는 파일이 있을 때만 이 줄을 남기므로 뷰어를 켜지 않은 프로젝트에는 나오지 않습니다.
- 여러 프로젝트를 모으는 프로젝트에서 켜면 병합 스펙의 뷰어도 만듭니다 ([여러 프로젝트 모으기](#여러-프로젝트-모으기)).

## Postman Collection

epages 의 `postman` 태스크 결과(Postman Collection v2.1)를 `apiDocs` 에 넣습니다.

```kotlin
kardx {
    postmanCollection { enabled = true }   // build/api-docs/postman-collection.json, 앱에서 /docs/postman-collection.json
}

postman {                                    // epages 설정 (선택)
    title = "주문 API"
    baseUrl = "http://localhost:8080"        // 기본값 http://localhost
}
```

- `postman` 도 `openapi3` 와 같은 조각 복사본(`apiDocsOpenApiSnippets`: 보안 요구사항·스키마 이름을 맞춘 것)을 읽습니다. 전역 헤더 → `apiKey` 변환, 타입·제약 보강 같은 OpenAPI 후처리는 Collection 에 해당하지 않아 하지 않습니다(헤더는 요청 예시에 그대로 들어갑니다).
- 라이브러리가 epages 의 Bearer 감지를 위해 넣는 가짜 토큰은 `Bearer {{bearerToken}}` 으로 바꾸고 Collection 변수 `bearerToken` 을 선언합니다. Postman 에서 이 변수에 토큰을 한 번 넣으면 모든 요청에 쓰입니다.
- 요청에 없던 선택 헤더는 v2.1 스키마에 맞게 빈 값·`disabled: true` 로 넣습니다.
- 파일 이름은 epages `postman { outputFileNamePrefix }` 를 따릅니다.

## IntelliJ HTTP Client

문서화한 요청을 [HTTP Client](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) 파일(`.http`)로 씁니다. IntelliJ IDEA 에서 열면 가져오기 없이 줄 옆 실행 버튼으로 요청을 보냅니다. 기본으로 꺼져 있습니다. 다른 OpenAPI 기능처럼 epages 플러그인이 필요하지만(그때만 `apiDocsHttpClient` 태스크가 있습니다), Postman Collection 과 달리 epages `postman` 태스크는 필요 없습니다.

```kotlin
kardx {
    httpClient {
        enabled = true          // 기본값 false
        fileName = "api.http"   // 기본값
    }
}
```

| 결과 | 위치 |
|---|---|
| 요청 | `build/api-docs/http-client/api.http` |
| 환경 | `build/api-docs/http-client/http-client.env.json` |
| 앱 (IDE 실행, `bootRun`, 실행 jar) | `http://localhost:8080/docs/http-client/api.http` |

문서화한 operation 마다 요청이 하나이고, 테스트가 실제로 보낸 요청(`http-request.adoc`)으로 만듭니다: 메서드, 호스트 자리에 `{{baseUrl}}` 을 넣은 URL, 헤더, 본문. 같은 operation 을 여러 테스트가 문서화했으면 OpenAPI 처럼 성공 응답 테스트를 먼저 씁니다. 요청은 `### <제목>`(operation summary, 없으면 operationId)으로 시작하고 다음 줄에 `# @name <operationId>` 가 있어, 뒤 요청에서 그 응답을 참조할 수 있습니다. 요청은 경로·메서드 순입니다.

```http
### Create product
# @name createProduct
POST {{baseUrl}}/api/v1/products
Content-Type: application/json

{
  "name": "Keyboard",
  "price": 59000
}
```

`http-client.env.json` 에는 `baseUrl` 이 `http://localhost:8080` 인 환경 `local` 하나가 있습니다. 편집기의 환경 선택에서 고릅니다. 다른 서버는 프로젝트에 둔 이 파일이나 복사본에 환경을 더합니다. 토큰은 IntelliJ 가 함께 읽는 `http-client.private.env.json` 에 두고 커밋하지 않습니다.

- `Host`·`Content-Length` 를 뺀 모든 헤더를 테스트가 보낸 값 그대로 적습니다.
- `Authorization: Bearer …` 헤더는 `Authorization: Bearer {{bearerToken}}` 이 되고, 환경 파일에 `"bearerToken": ""` 가 생깁니다. 여기(또는 private 파일)에 토큰을 넣습니다. 그 밖에 테스트에서 쓴 토큰·쿠키는 [민감 헤더 마스킹](configuration.md#민감-정보-마스킹)을 켜지 않았으면 그대로 들어갑니다.
- 문서용으로 가린 값(`****`)은 변수로 바뀝니다. 그대로 두면 요청이 `****` 를 보내 인증에 실패하기 때문입니다. 쿼리 파라미터, 폼 필드(`application/x-www-form-urlencoded`), 쿠키는 자기 이름의 변수(`access_token={{access_token}}`, `SESSION={{SESSION}}`)가 되고, 값 전체를 가린 헤더와 방식만 남긴 인증 헤더(`Basic ****`)는 소문자 헤더 이름의 변수(`X-Api-Key: {{x_api_key}}`, `Authorization: Basic {{authorization}}`)가 됩니다. 이름에는 글자·숫자·`_` 만 남깁니다. 변수는 `http-client.env.json` 과, 비밀 변수가 있으면(`bearerToken` 포함) 함께 생기는 `http-client.private.env.json` 에 빈 값으로 선언됩니다. 실제 값은 private 파일에 채웁니다. 실제 값을 파일에 쓰는 일은 없습니다.
- 기본 표시 `****` 만 알아봅니다. `masking.replacement`·`ValueMasker` 로 표시를 바꿨다면 그 값은 파일에 그대로 남습니다.
- Postman Collection 처럼 `apiDocs` 가 모으므로 앱 리소스, 실행 jar, HTML 내보내기(`export { html = true }`)에도 들어갑니다.

## WireMock stub

문서화 테스트가 요청·응답마다 [WireMock](https://wiremock.org/) 매핑(stub) JSON 도 만들게 합니다. 다른 서비스나 프런트엔드가 테스트로 검증된 응답으로 이 API 를 흉내 낼 수 있습니다.

```kotlin
kardx {
    wireMock { enabled = true }   // 테스트에 kardx.wire-mock-stubs=true 를 넘긴다
}
```

| 결과 | 위치 |
|---|---|
| 매핑 | `build/generated-snippets/wiremock/mappings/<class-name>/<method-name>.json` (`andDocument` 하나에 하나) |
| stub jar | `./gradlew apiDocsStubsJar` → `build/libs/<name>-<version>-stubs.jar`, 안의 `META-INF/<group>/<name>/<version>/mappings/` |

```bash
# WireMock standalone 으로 바로 띄우기 (mappings 디렉터리의 부모를 root-dir 로)
java -jar wiremock-standalone-3.13.2.jar --port 9999 --root-dir build/generated-snippets/wiremock
curl http://localhost:9999/api/v1/products/1
```

- 요청은 메서드, 경로(테스트가 보낸 실제 값, `urlPath`), 쿼리 파라미터(`equalTo`), JSON 본문(`equalToJson`, 배열 순서·추가 필드 무시)으로 맞춥니다. 헤더는 맞추지 않습니다(마스킹한 인증 헤더는 실제 값과 맞을 수 없습니다). 그래서 같은 경로·본문으로 상태만 다르게 문서화한 테스트(예: 인증 유무로 200·401)는 어느 하나가 응답합니다.
- 응답은 상태, 헤더(`Content-Length`·`Transfer-Encoding` 제외), 본문입니다. 글자 형식이 아닌 본문(이미지 등)은 `base64Body` 로 넣습니다. 마스킹한 헤더 값(`Set-Cookie: SESSION=****`)은 가린 그대로 들어갑니다.
- 매핑 `id` 는 문서 식별자로 정해 다시 만들어도 같습니다.
- stub jar 는 Spring Cloud Contract Stub Runner 의 배치를 따르므로 프로젝트에 `group` 이 있어야 합니다 (`group = "com.example"`). 없으면 `apiDocsStubsJar` 가 `apiDocsStubsJar: project.group is not set, …` 으로 실패합니다. `group`·`version` 은 태스크가 돌 때 읽으므로 빌드 스크립트 뒤쪽에서 정해도 됩니다. 배포하려면 `publishing { publications { create<MavenPublication>("stubs") { artifact(tasks.named("apiDocsStubsJar")) } } }` 처럼 산출물로 넣습니다.
- 라이브러리가 매핑 형식을 직접 쓰므로 WireMock·Spring Cloud Contract 의존성이 필요 없고, Boot 3·4 모두 같습니다. yml `kardx.wire-mock-stubs: true` 로 플러그인 없이 켤 수도 있습니다.
- `MessageDocumentation`(WebSocket·STOMP) 문서는 매핑을 만들지 않습니다.

## 스펙 내보내기

만든 스펙을 빌드 밖으로 보내는 방법은 세 가지입니다. 어느 것도 `build`·`check`·`apiDocs`·`bootRun` 에 붙지 않고 직접 실행할 때만 돌며, 고른 결과를 만드는 태스크(OpenAPI → `openapi3`, AsyncAPI → `apiDocsAsyncApi`, HTML → `apiDocs`)만 먼저 실행합니다.

| 방법 | 실행 | 결과 |
|---|---|---|
| 디렉터리로 복사 | `./gradlew apiDocsExport` | `export.directory` 에 정한 이름으로 복사합니다 |
| HTTP 로 올리기 | `./gradlew apiDocsUpload` | `upload.url` 로 `PUT`(또는 `POST`) 합니다 |
| Maven 배포물에 붙이기 | `./gradlew publish` (`maven-publish`) | `export.publishSpecs = true` 면 `<name>-<version>-openapi.yaml` 등을 jar 옆에 올립니다 |

```kotlin
import java.time.Duration

kardx {
    export {
        directory = layout.buildDirectory.dir("exported-specs")   // 필수
        asyncApi = true
        // publishSpecs = true    // maven-publish 가 있으면 publish 가 <name>-<version>-openapi.yaml 도 올린다
    }
    upload {
        url = "https://registry.example.com/apis/{name}/{version}/{type}"
        headers.put("Authorization", providers.environmentVariable("API_REGISTRY_TOKEN").map { "Bearer $it" })
        timeout = Duration.ofSeconds(30)
        retries = 2
    }
}
```

`kardx { }` 블록 안에서 `java.time.Duration` 처럼 패키지 이름으로 쓰면 `java` 가 Java 플러그인 확장으로 해석되어 컴파일되지 않습니다. 위처럼 빌드 스크립트 맨 위에서 `import` 합니다.

파일 이름과 URL 에는 자리표시자를 씁니다.

| 자리표시자 | 값 |
|---|---|
| `{name}` | 프로젝트 이름 |
| `{version}` | 프로젝트 버전 |
| `{type}` | `openapi` 또는 `asyncapi` |
| `{ext}` | 스펙 파일 확장자 (`yaml`, `json`) |

### apiDocsExport

| `export { }` 설정 | 기본값 | 설명 |
|---|---|---|
| `directory` | 없음 (필수) | 내보낼 디렉터리. 정하지 않고 실행하면 실패합니다. 다른 파일과 섞이지 않는 디렉터리를 씁니다 |
| `openApi` | `true` | OpenAPI 스펙(`openapi3` 결과, 후처리 뒤) |
| `asyncApi` | `false` | AsyncAPI 스펙(`apiDocsAsyncApi` 결과). 고르고 메시지가 없으면 이유를 알려 주며 실패합니다. `asyncApi { enabled = false }` 면 예전 빌드에 남은 스펙을 내보내지 않고 `No AsyncAPI spec: kardx.asyncApi.enabled is false. …` 로 실패합니다 |
| `html` | `false` | `apiDocs` 디렉터리 전체(HTML 포함) |
| `openApiFileName` | `{name}-openapi3.{ext}` | 디렉터리 기준 상대 경로 |
| `asyncApiFileName` | `{name}-asyncapi.{ext}` | 디렉터리 기준 상대 경로 |
| `htmlDirectoryName` | `{name}-api-docs` | `html` 을 켰을 때 문서 디렉터리를 복사할 하위 디렉터리 |
| `publishSpecs` | `false` | 아래 Maven 배포물 |
| `publicationName` | 없음 | 스펙을 붙일 배포물 이름. 없으면 모든 `MavenPublication`(Gradle 플러그인 마커 배포물은 뺀다) |

출력을 추적하지 않는 태스크라 실행할 때마다 복사합니다.

### apiDocsUpload

| `upload { }` 설정 | 기본값 | 설명 |
|---|---|---|
| `url` | 없음 (필수) | OpenAPI 스펙을 올릴 주소 (http·https). `asyncApiUrl` 의 기본값이기도 합니다 |
| `asyncApiUrl` | `url` | AsyncAPI 스펙을 올릴 주소 |
| `method` | `PUT` | `PUT` 또는 `POST` |
| `headers` | 없음 | 요청 헤더. 값은 `providers.environmentVariable(...)`·`providers.gradleProperty(...)` 로 넣어 빌드 스크립트에 비밀값을 적지 않습니다. `Content-Type` 을 넣으면 확장자로 정한 값(`application/yaml`, `application/json`) 대신 씁니다 |
| `openApi` | `true` | OpenAPI 스펙을 올릴지 |
| `asyncApi` | `false` | AsyncAPI 스펙을 올릴지. `asyncApi { enabled = false }` 면 `apiDocsExport` 처럼 실패합니다 |
| `timeout` | 30초 | 연결·응답 대기 시간 |
| `retries` | `0` | 다시 보낼 횟수 |

- JDK HTTP 클라이언트만 씁니다(추가 의존성 없음).
- 2xx 가 아니면 상태 코드와 응답 본문 앞 2000자로 실패합니다.
- 헤더 값은 로그와 오류 메시지에 남기지 않습니다. 헤더 이름만 남깁니다 (`headers: [Authorization, Content-Type]`). 응답 본문에 헤더 값이 그대로 들어 있어도 가립니다.
- 주소 안의 비밀값도 같은 방식으로 가립니다: 사용자 정보(`https://user:password@host/...`)와 모든 쿼리 파라미터 값(`?token=...`)이 로그·재시도 경고·오류 메시지에 `****` 로 나옵니다 (쓰인 형태와 URL 디코딩한 형태 모두). 그래도 자격 증명은 헤더로 보내는 편이 낫습니다. 주소는 프록시나 서버 접근 로그에 남을 수 있습니다. 헤더 값은 실행할 때 읽으므로 configuration cache 를 써도 토큰을 바꾸면 새 값이 갑니다.
- 리다이렉트는 따라가지 않습니다(헤더가 다른 서버로 가지 않게).
- 다시 보내기는 연결 실패, 429, 5xx 일 때만 합니다. 1초, 2초, 4초…(최대 30초) 기다립니다.
- 출력이 없는 부수 효과 태스크라 UP-TO-DATE 가 없고 실행할 때마다 보냅니다.
- 두 스펙을 같은 주소로 올리게 되면 오류입니다. `url` 에 `{type}` 을 넣거나 `asyncApiUrl` 을 따로 정합니다.

### Maven 배포물

`maven-publish` 플러그인이 있고 `export.publishSpecs = true` 면 고른 스펙(`export.openApi`, `export.asyncApi`)을 Maven 배포물에 classifier `openapi`·`asyncapi` 로 붙입니다. `publish`·`publishToMavenLocal` 이 jar 옆에 `<name>-<version>-openapi.yaml`, `<name>-<version>-asyncapi.yaml` 을 올립니다. HTML 은 붙이지 않습니다.

```kotlin
plugins {
    `maven-publish`
}

publishing {
    publications { create<MavenPublication>("maven") { from(components["java"]) } }
}

kardx {
    export { publishSpecs = true }
}
```

스펙은 중간 태스크 `apiDocsPublicationSpecs` 가 `build/kardx/publication` 에 고정 이름으로 모으고, 배포 태스크가 그 태스크에 의존합니다. `maven-publish` 가 없으면 아무것도 하지 않습니다.

## 여러 프로젝트 모으기

여러 서브프로젝트가 각자 이 플러그인으로 문서를 만들 때, 한 프로젝트(보통 루트)에서 목록 페이지 하나와 병합 OpenAPI 스펙으로 모읍니다. 모으는 프로젝트에는 Java 플러그인이 없어도 됩니다.

```kotlin
// 루트 build.gradle.kts
plugins {
    id("io.github.kardx-project")
}

dependencies {
    restdocsAggregation(project(":order"))
    restdocsAggregation(project(":member"))
}

kardx {
    aggregate {
        title = "쇼핑 API"     // 기본값: 프로젝트 이름
        version = "1.0.0"     // 기본값: 프로젝트 버전
    }
    openApiViewer { enabled = true }   // 병합 스펙의 뷰어도 만든다 (선택)
}
```

```bash
./gradlew apiDocsAggregate   # 각 프로젝트의 apiDocs 를 실행하고 build/api-docs-aggregate 에 모은다
```

| 결과 (`build/api-docs-aggregate/`) | 내용 |
|---|---|
| `index.html` | 프로젝트별 REST Docs·OpenAPI·AsyncAPI·Postman 링크 |
| `openapi3.yaml` | 병합 스펙 (OpenAPI 를 만든 프로젝트가 있을 때) |
| `openapi.html` | 병합 스펙 뷰어 (모으는 프로젝트에서 `openApiViewer` 를 켰을 때) |
| `order/`, `services/member/` | 각 프로젝트의 `build/api-docs` 복사본 (프로젝트 경로 `:services:member` → `services/member`, 루트 프로젝트 → `root`). 두 프로젝트가 같은 디렉터리로 가면(루트 프로젝트와 `:root` 라는 프로젝트) 실패합니다 (`apiDocsAggregate: projects : and :root both go to the directory root/ …`) |

프로젝트끼리는 Gradle 이 권장하는 방식(variant)으로만 이어집니다. 문서를 만드는 프로젝트는 `apiDocs` 결과를 configuration `restdocsApiDocsElements`(속성 `Category=documentation`, `DocsType=restdocs-api-docs`)로 내보내고, 모으는 프로젝트는 `restdocsAggregation` 선언을 풀어 받습니다. 다른 프로젝트의 태스크나 `build` 디렉터리를 직접 참조하지 않으므로 configuration cache·병렬 빌드와 함께 쓸 수 있습니다.

병합 규칙 (프로젝트 경로순으로 처리합니다):

- **경로**: 합칩니다. 같은 경로·메서드가 두 프로젝트에 있으면 어느 쪽이 맞는지 알 수 없으므로 실패합니다. 한쪽을 `openApi { excluded = true }` 로 빼거나 경로를 나눕니다. 프로젝트마다 기본 스펙(`openapi3.yaml`)만 합칩니다. `apiVersioning { splitSpecs = true }` 의 버전별 스펙(`openapi3-v1.yaml`)은 같은 operation 을 다시 담으므로 목록 페이지에 링크만 넣습니다.
- **스키마 등 components**: 이름과 내용이 같으면 하나로 둡니다. 이름이 같고 내용이 다르면 나중 프로젝트의 것을 `<프로젝트 이름 PascalCase><이름>` 으로 바꾸고(그 이름도 있으면 뒤에 2, 3…)(`:member` 의 `Item` → `MemberItem`, `bearerAuth` → `MemberBearerAuth`), 그 프로젝트의 `$ref` 와 보안 요구사항도 함께 바꿉니다. 바뀐 스키마를 참조해 내용이 달라진 스키마도 같이 바뀝니다. 바꾼 이름은 빌드 로그에 나옵니다.
- **operationId**: 겹치면 나중 프로젝트의 것을 `<프로젝트 이름>-<operationId>` 로 바꿉니다.
- **최상위 security**: 합친 문서의 최상위에 두지 않고, 그 프로젝트의 operation 중 `security` 가 없는 것에 내려 씁니다. 한 프로젝트의 인증 요구가 다른 프로젝트의 공개 API 로 번지지 않게 하려는 것입니다.
- **tags·servers**: 이름·URL 로 중복을 빼고 합칩니다. 설명은 먼저 나온 것을 씁니다. 프로젝트마다 서버 주소가 다르면 모두 최상위 `servers` 에 들어가므로 필요하면 `openapi3 { setServer(...) }` 를 맞춥니다.
- **info**: `aggregate { title, version, description }`.
- AsyncAPI 는 합치지 않고 목록 페이지에 프로젝트별 뷰어(없으면 yaml) 링크로 넣습니다.
