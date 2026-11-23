[English](../README.md) | **한국어**

# 문서

kardx 사용 설명서입니다. 처음이라면 [시작하기](getting-started.md)부터 보세요.

## 브라우저에서 보기

앱을 띄운 뒤(실행 jar, `bootRun`, IDE 실행 모두 같음) 아래 주소로 엽니다. 앱 없이는 `build/api-docs/` 의 파일을 엽니다.

| 문서 | 주소 |
|---|---|
| 전체 API 목록 (REST Docs) | `http://localhost:8080/docs/index.html` |
| API 하나 | `http://localhost:8080/docs/<테스트-클래스>.html#<테스트-메서드>` (Kotest 는 `<스펙>.html#<테스트-이름>`) |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI 스펙 | `http://localhost:8080/docs/openapi3.yaml` |
| AsyncAPI (WebSocket·STOMP·SSE) | `http://localhost:8080/docs/asyncapi.html` |
| AsyncAPI Try it out (끝나지 않는 SSE 구독 등) | `http://localhost:8080/docs/asyncapi.html#try-it-out` |
| Enum 코드 | `http://localhost:8080/docs/code/enum-code.html` |
| OpenAPI 정적 뷰어 (켰을 때) | `http://localhost:8080/docs/openapi.html` |
| Postman Collection (켰을 때) | `http://localhost:8080/docs/postman-collection.json` |
| IntelliJ HTTP Client (켰을 때) | `http://localhost:8080/docs/http-client/api.http` |

`/docs` 와 파일 위치는 [Gradle 플러그인](build.md#gradle-플러그인) 설정으로 바꿀 수 있습니다. 문서별 자세한 주소는 각 문서 앞부분에 있습니다.

## 문서 목록

| 하려는 것 | 문서 | 담긴 내용 |
|---|---|---|
| 처음 설치하고 첫 문서 만들기 | [시작하기](getting-started.md) | 설치, 지원하는 빌드 구성 (Kotlin·Groovy DSL, 플러그인 없는 Gradle, Maven), 새 프로젝트 세팅 전 과정 (Gradle, yml, 테스트, 실행), Boot 3 차이, 슬라이스 테스트(`@WebMvcTest`) |
| 요청·응답을 문서화하기 | [HTTP API 문서화](http-api.md) | `andDocument { }` DSL (MockMvc Kotlin DSL·`MockMvcTester` 포함), 필드 자동 추출과 미리보기, 느슨한 검증, 검증 실패 메시지, 공통 응답 래퍼, 여러 상태 코드, 선언한 오류 응답, OpenAPI 에 나가는 정보, MockMvc 보조 함수 |
| WebFlux 앱이나 RestTestClient 로 문서화하기 | [WebTestClient · RestTestClient](webtestclient.md) | WebTestClient·RestTestClient(Boot 4)로 같은 `andDocument { }`, 클라이언트별 지원 범위, WebFlux 앱의 의존성 |
| Kotest 스펙으로 문서화하기 | [Kotest](kotest.md) | Kotest 6.x·5.x 아티팩트와 등록, 스펙 스타일별 예시, 제목·스니펫 이름 규칙, JUnit 과 섞어 쓰기, 제한 |
| SSE·WebSocket·STOMP 를 문서화하기 | [SSE · WebSocket · AsyncAPI](messaging.md) | Server-Sent Events, 끝나지 않는 스트림, WebSocket·STOMP 메시지, `StompTestClient`, 핸들러 테스트, AsyncAPI·인증·Try it out |
| 설정 바꾸기 | [설정](configuration.md) | `configure` / `withSettings`, 설정 목록, `application.yml` 키, 헤더 마스킹, 문서 언어 |
| 빌드하고 결과물 찾기 | [빌드와 산출물](build.md) | 상황별 명령, Gradle 플러그인 옵션과 태스크(표 하나), 자동으로 넣는 의존성, 실행할 때 자동 생성, 앱 시작 로그, 운영 노출, 생성 위치, 플러그인이 비우는 디렉터리, 재현 가능한 스니펫 |
| OpenAPI 스펙 다듬기 | [OpenAPI 와 Swagger UI](openapi.md) | Swagger UI, 테스트별 `openApi { }`, `openApiInfo`(info·contact·license·태그·`x-tagGroups`), OAuth2·OpenID Connect 스키마, API 버전 |
| API 변경과 문서화 누락 잡기 | [스펙 검사와 커버리지](checks.md) | `apiDocsCheck`·`apiDocsBaseline`, 깨는 변경의 기준, `apiDocsCoverage`, 목록에 잡히지 않는 엔드포인트 |
| API 를 다른 사람에게 넘기기 | [뷰어·클라이언트·stub·공유](outputs.md) | Redoc·Scalar 뷰어, Postman Collection, IntelliJ HTTP Client, WireMock stub, `apiDocsExport`, `apiDocsUpload`, Maven 배포물, 여러 프로젝트 모으기 |
| Maven 이나 명령줄로 쓰기 | [Maven 과 명령줄](maven.md) | Maven 플러그인(목표, 파라미터, asciidoctor-maven-plugin 과 함께 쓰는 설정), JSON 설정 파일, 스펙 검사, KDoc 설명, 플러그인 없이 `exec-maven-plugin`·`java -jar`, CLI 명령과 옵션, Gradle 에서만 되는 것, 트러블슈팅 |
| CI 에서 파이프라인 돌리기 | [CI 적용](ci.md) | GitHub Actions PR 검사(깨는 변경, 문서화하지 않은 엔드포인트), 보고서를 담은 PR 댓글, 기준 갱신, GitHub Pages·레지스트리 배포, 속도, 여러 프로젝트, GitLab CI |
| 생성 문서와 index 이해하기 | [AsciiDoc 문서](asciidoc.md) | 문서 자동 생성, 섹션 순서, 테스트 모양별 스니펫, index, Enum 코드 문서 |
| 문서 모양 바꾸기 | [스니펫과 문서 모양 바꾸기](customizing-snippets.md) | 넣을 스니펫 고르기, 템플릿과 변수, 직접 만든 스니펫, `DocumentGenerator`, 직접 쓴 AsciiDoc 에서 쓰기, 요청·응답 가공, Enum 표 형식 |
| 기존 REST Docs 테스트 옮기기 | [기존 Spring REST Docs 에서 옮기기](migration.md) | 의존성, 테스트 기본 클래스, `document()` 대응표, 기존 스니펫·index 와 섞어 쓰기, epages `resource()`, Swagger 설명 재사용 |
| 오류 해결 | [트러블슈팅](troubleshooting.md) | 자주 나는 오류와 해결 |
| AI 도우미에게 테스트 맡기기 | [AI 도우미](ai.md) | `llms.txt`, `llms-full.txt`, Claude Code 스킬, Copilot·Cursor·Codex 규칙 파일 |
| 이 저장소 고치기 | [개발](development.md) | 저장소 구조, 고친 뒤 확인 순서, 빌드, 검증 스크립트 |
| 모듈 구성 보기 | [모듈 구성](architecture.md) | 모듈별 역할과 내용, 넣을 아티팩트, 문서가 만들어지는 흐름 |

변경 내역은 [CHANGELOG](../../CHANGELOG.ko.md) 에 있습니다.
