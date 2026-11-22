[English](../checks.md) | **한국어**

# 스펙 검사와 커버리지

[문서 목록](README.md)

<!-- toc -->
**목차**

- [OpenAPI 변경 비교](#openapi-변경-비교)
- [문서화 커버리지](#문서화-커버리지)
  - [목록에 잡히지 않는 엔드포인트](#목록에-잡히지-않는-엔드포인트)
<!-- /toc -->

풀 리퀘스트와 CI 에서 쓰는 두 검사입니다: 생성한 OpenAPI 스펙을 커밋한 기준 스펙과 비교하기, 문서화 테스트가 없는 엔드포인트 찾기. 워크플로 파일은 [CI 예제](ci.md)에 있습니다.

## OpenAPI 변경 비교

epages 플러그인이 있으면 생성한 OpenAPI 스펙을 저장소에 커밋해 둔 기준 스펙과 비교할 수 있습니다. API 를 의도치 않게 바꿨는지 PR 에서 알아차리려는 것입니다. 두 태스크 모두 `openapi3` 뒤에 실행됩니다(테스트 포함).

```bash
./gradlew apiDocsBaseline   # 지금 스펙을 docs/openapi/openapi3.yaml 로 저장한다. 이 파일을 커밋한다
./gradlew apiDocsCheck      # 새 스펙과 기준을 비교한다
```

```kotlin
kardx {
    openApiCheck {
        baseline = layout.projectDirectory.file("docs/openapi/openapi3.yaml") // 기본값
        failOn = "breaking"   // any(기본값) | breaking | none
        runOnCheck = true     // ./gradlew check (CI) 에도 넣는다
    }
}
```

| `failOn` | 동작 |
|---|---|
| `any` (기본값) | 다른 곳이 하나라도 있으면 실패합니다. 의도한 변경이면 `apiDocsBaseline` 으로 기준을 갱신해 함께 커밋합니다 |
| `breaking` | 하위 호환을 깨는 변경이 있을 때만 실패하고, 나머지 차이는 경고로 출력합니다 |
| `none` | 실패하지 않고 요약만 출력합니다 |

달라졌으면 요약을 출력하고, 전체 목록은 `build/reports/kardx/openapi-changes.txt` 에 남깁니다. 보고서는 언제나 같은 머리로 시작합니다: `The OpenAPI spec matches the baseline (baseline … ↔ generated …)` 또는 `The OpenAPI spec differs from the baseline (…)`, 이어서 `Breaking changes: N`, `Warnings: N`(경고가 있을 때만), `All structural differences: N`. 그래서 스크립트가 어느 경우든 개수를 읽을 수 있습니다.

```
The OpenAPI spec differs from the baseline (baseline …/docs/openapi/openapi3.yaml ↔ generated …/build/api-spec/openapi3.yaml)
Breaking changes: 3
  - Type changed: POST /api/v1/products 201 application/json data.price (integer → number)
  - Response field removed: POST /api/v1/products 201 application/json data.discountRate
  - Response enum value added: POST /api/v1/products 201 application/json data.status [HIDDEN]
All structural differences: 3
  - changed  components > schemas > ProductResponse > properties > data > properties > price > type: integer → number
  - removed  components > schemas > ProductResponse > properties > data > properties > discountRate
  - added  components > schemas > ProductResponse > properties > data > properties > status > enum > HIDDEN
Report: file:///…/build/reports/kardx/openapi-changes.txt
If the changes are intended, run ./gradlew apiDocsBaseline to update the baseline and commit it.
```

보고서는 언어 설정과 상관없이 영어입니다. "전체 구조 차이"(`All structural differences`) 는 두 문서를 위치별로 비교한 결과입니다(설명·예시 변경 포함). 파라미터·태그처럼 이름이 있는 목록은 순서가 아니라 이름으로 맞추므로 순서만 바뀐 것은 차이가 아닙니다. "하위 호환을 깨는 변경"(`Breaking changes`) 은 `$ref` 를 따라가 operation 별로 판정하며, 판정하는 규칙은 다음뿐입니다.

- 엔드포인트(경로·메서드) 삭제, 2xx 응답 삭제, 요청·응답 미디어 타입 삭제
- 필수 파라미터 추가, 파라미터가 필수로 바뀜, 필수 요청 본문 추가, 요청 본문이 필수로 바뀜
- 요청 본문: 필수 필드 추가, 필드가 필수로 바뀜
- 파라미터·요청 본문 필드의 제약 강화: `minLength`·`minimum`·`minItems` 증가·추가, `maxLength`·`maximum`·`maxItems` 감소·추가, `pattern` 추가·변경(보내던 값이 거부될 수 있다)
- 응답 본문: 필드 삭제, 필수 필드가 선택으로 바뀜
- 응답 헤더: 헤더 삭제
- enum: 요청에서 값 삭제(보내던 값이 거부된다), enum 이 없던 요청 파라미터·필드에 `enum` 추가(`Request enum added: …`, 목록 밖의 값이 거부된다), 응답에서 값 추가(모르는 값을 받은 클라이언트가 깨질 수 있다)
- `oneOf`: 변형(variant) 삭제. 요청·응답 모두
- 보안: operation 에 보안 요구사항이 생김(공개 API 가 인증을 요구하게 됨), 또는 스키마가 바뀜(`bearerAuthJWT` → `oauth2`)
- 어디서든: 타입·형식 변경(`type`, `format`)

다음 세 가지는 깨는 변경이 아니라 경고로 알립니다: 응답 `oneOf` 에 변형 추가(클라이언트가 모르는 모양을 받을 수 있다), `default` 값 변경, 경로 변수 이름 변경(`Path variables renamed: /users/{id} → /users/{userId}`. 클라이언트가 부르는 URL 은 같으므로 엔드포인트 삭제·추가로 보지 않고 operation 을 그대로 비교한다). 요청 `oneOf` 에 변형 추가는 둘 다 아닙니다. `failOn = "breaking"` 이면 출력만 하고 실패하지 않으며, `any` 면 어차피 모든 차이가 실패입니다.

스키마 이름만 바뀐 것, 필드·엔드포인트 추가, 응답 enum 값 삭제, 제약 완화, 설명 변경은 깨는 변경이 아닙니다. 자기 자신을 참조하는 스키마(`Category.parent`)는 한 번만 따라갑니다. `anyOf`·`allOf`·`additionalProperties`, 보안 요구사항의 scope 변경, 응답 쪽 제약, 응답 헤더 추가, `components.securitySchemes` 의 내용(flow URL, scope 목록)은 판정하지 않습니다(구조 차이에는 나옵니다). 더 넓은 규칙이 필요하면 [oasdiff](https://github.com/oasdiff/oasdiff) 같은 전용 도구를 CI 에서 함께 돌리세요(`oasdiff breaking docs/openapi/openapi3.yaml build/api-docs/openapi3.yaml`). 이 플러그인의 비교는 외부 실행 파일 없이 동작하는 것을 우선했습니다.

- 기준 파일 형식(YAML·JSON)은 확장자로 읽습니다. `openapi3 { format = "json" }` 이면 기준 파일도 `.json` 으로 둡니다.
- 기준 파일이 없으면 `failOn = "none"` 이 아닌 한 실패하고, `./gradlew apiDocsBaseline` 으로 기준을 만들라고 알려 줍니다. 기준이 없으면 어떤 변경도 판정할 수 없어, 그대로 두면 `breaking` 검사가 기준을 만들 때까지 계속 통과하기 때문입니다. 예전처럼 `failOn = "any"` 일 때만 실패하고 `breaking` 은 경고만 하려면 `openApiCheck { requireBaseline = false }` 로 둡니다.
- 스펙이 실행마다 달라지면(시각·무작위 값 예시) 비교가 매번 실패합니다. 고정 예시값을 쓰세요.

## 문서화 커버리지

`apiDocsCoverage` 는 앱이 실제로 매핑한 엔드포인트와 테스트가 문서화한 operation 을 비교해, 아무도 문서화 테스트를 쓰지 않은 API 를 찾아 줍니다. 기본으로 켜져 있고 `apiDocs` 의 일부로 테스트 뒤에 돕니다. 이 태스크는 epages 플러그인(`com.epages.restdocs-api-spec`)을 적용했을 때만 있습니다. 결과를 한 줄로 출력합니다(태스크가 UP-TO-DATE 이거나 빌드 캐시에서 왔으면 출력하지 않습니다).

```
API docs coverage: 23/25 endpoints documented (2 undocumented, see build/api-docs/coverage/coverage.md)
```

양쪽을 모으는 방법은 다음과 같습니다.

- **앱이 매핑한 엔드포인트.** 테스트 중 Spring 애플리케이션 컨텍스트마다 처음 문서화하는 요청에서, 라이브러리가 그 컨텍스트의 `RequestMappingHandlerMapping` 빈(서블릿·리액티브)을 읽어 스니펫 디렉터리의 `.kardx/endpoints/` 에 목록을 씁니다. HTTP 메서드와 경로 패턴마다 한 항목이고 핸들러 메서드가 함께 적힙니다. 메서드가 없는 매핑은 모든 메서드로 칩니다. 컨텍스트마다 한 번만 하고 매핑이 같은 컨텍스트는 같은 파일을 한 번만 쓰므로 테스트가 눈에 띄게 느려지지 않습니다. 모든 컨텍스트의 목록을 합쳐서 봅니다.
- **문서화한 operation.** 모든 OpenAPI 조각(`resource.json`)의 메서드와 경로 템플릿입니다. 그래서 epages 플러그인이 필요하고, 없으면 `apiDocsCoverage` 가 아예 등록되지 않습니다. OpenAPI 에서 뺀 테스트(`openApi { excluded = true }`, `openApi.enabled = false`)도 문서화한 것으로 셉니다. 기록된 요청(`http-request.adoc`)을 엔드포인트 패턴과 맞춰 봅니다. 양쪽 모두 API 버전이 있으면 버전도 같아야 합니다.

경로 변수 이름은 달라도 됩니다. `/users/{id}` 와 `/users/{userId}` 는 같은 엔드포인트입니다.

```kotlin
kardx {
    coverage {
        enabled = true                 // 기본값
        failOnUndocumented = false     // 기본값. true 면 문서화하지 않은 엔드포인트가 있을 때 태스크 실패
        exclude("/internal/**", "/health")
    }
}
```

`/error`, `/actuator/**`, springdoc 의 `/v3/api-docs/**`·`/v3/api-docs.yaml`·`/swagger-ui/**`·`/swagger-ui.html`, 문서 경로 자체(기본 `bootJar.path` 면 `/docs/**`)는 언제나 뺍니다. `exclude(...)` 는 여기에 패턴을 더할 뿐 뺄 수는 없습니다. 패턴은 Ant 형식입니다: `*` 는 경로 한 칸, `**` 는 여러 칸. 패턴에 Spring 이 보여 주는 모양 그대로 경로 변수를 써도 됩니다. 이름과 정규식은 상관없습니다 (`exclude("/internal/users/{id}")` 는 `/internal/users/{userId}`, `/internal/users/{id:\d+}` 도 뺍니다).

| 결과 | 내용 |
|---|---|
| 콘솔 | 위의 한 줄 요약 (태스크가 실제로 돌 때만) |
| `build/api-docs/coverage/coverage.md` | 문서화하지 않은 엔드포인트와 그 핸들러. PR 이나 CI 로그에서 읽는 용도 |
| `build/api-docs/coverage/coverage.json` | 같은 내용을 도구용으로 |

**CI 에서.** 아직 테스트를 쓰는 중에는 `failOnUndocumented = false` 로 두고 요약만 봅니다([CI 적용](ci.md)은 `coverage.md` 를 PR 댓글로 남깁니다). 모두 문서화했다면, 문서화 테스트 없이 새로 생긴 엔드포인트가 빌드를 실패시키게 합니다. CI 에서만 실패시키려면 다음처럼 합니다.

```kotlin
kardx {
    coverage {
        failOnUndocumented = providers.gradleProperty("strictDocs").map(String::toBoolean).orElse(false)
    }
}
```

```bash
./gradlew build apiDocs -PstrictDocs=true
```

테스트가 엔드포인트 목록을 하나도 쓰지 않았으면(예: 모든 테스트가 standalone 설정) 태스크는 `no endpoint inventory` 를 출력하고 통과합니다. `failOnUndocumented = true` 여도 마찬가지입니다.

### 목록에 잡히지 않는 엔드포인트

목록에는 테스트 컨텍스트에 들어 있는 것만 잡히므로, 목록에서 빠진 엔드포인트는 문서화하지 않았다고도 나오지 않습니다.

| 경우 | 이유 |
|---|---|
| standalone 설정: `MockMvcBuilders.standaloneSetup(...)`, `WebTestClient.bindToController(...)`, `bindToRouterFunction(...)`, `bindToServer()` | 읽을 애플리케이션 컨텍스트가 없습니다. 이런 테스트는 조용히 건너뛰고, 문서화한 operation 은 그대로 셉니다 |
| RestTestClient 테스트 | 라이브러리가 RestTestClient 에서 애플리케이션 컨텍스트를 얻지 못하므로 목록에 아무것도 더하지 않습니다(문서화한 operation 은 그대로 셉니다). 같은 컨텍스트의 MockMvc·WebTestClient 문서화 테스트가 있으면 엔드포인트가 목록에 들어갑니다 |
| 함수형 라우트(`RouterFunction`, `router { }`, `coRouter { }`) | 읽지 않습니다. 어노테이션 컨트롤러만 목록에 들어갑니다 |
| 슬라이스 테스트: `@WebMvcTest(UserController::class)`, `@WebFluxTest(...)` | 컨텍스트에 고른 컨트롤러만 있습니다. 어떤 테스트 컨텍스트도 올리지 않는 컨트롤러는 목록에 없습니다. `@SpringBootTest` 문서화 테스트가 하나라도 있으면 앱의 모든 컨트롤러가 목록에 들어갑니다 |
