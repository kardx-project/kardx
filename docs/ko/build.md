[English](../build.md) | **한국어**

# 빌드와 산출물

[문서 목록](README.md)

<!-- toc -->
**목차**

- [상황별 명령과 순서](#상황별-명령과-순서)
  - [자주 하는 실수](#자주-하는-실수)
- [Gradle 플러그인](#gradle-플러그인)
  - [자동으로 넣는 의존성](#자동으로-넣는-의존성)
- [실행할 때 자동 생성](#실행할-때-자동-생성)
  - [앱 시작 로그](#앱-시작-로그)
- [운영 노출](#운영-노출)
- [생성 위치](#생성-위치)
  - [플러그인이 비우는 디렉터리](#플러그인이-비우는-디렉터리)
  - [앱 리소스에 넣는 문서](#앱-리소스에-넣는-문서)
  - [재현 가능한 스니펫](#재현-가능한-스니펫)
<!-- /toc -->

Gradle 플러그인이 문서 파이프라인을 어떻게 구성하는지, 결과물이 어디에 생기는지 설명합니다. 그 위에 얹은 기능은 따로 있습니다: [OpenAPI 와 Swagger UI](openapi.md), [스펙 검사와 커버리지](checks.md), [뷰어·클라이언트·stub·공유](outputs.md).

## 상황별 명령과 순서

명령은 대개 하나면 됩니다. 각 태스크가 자기가 의존하는 태스크(테스트 포함)를 먼저 실행하므로, `apiDocs` 전에 `test` 를, `bootJar` 전에 `apiDocs` 를 따로 돌리지 않습니다. 입력이 바뀌지 않은 태스크는 UP-TO-DATE 로 건너뜁니다.

| 상황 | 실행 순서 | 무엇이 일어나고 결과는 어디에 |
|---|---|---|
| 처음 | 1. 문서화 테스트를 씁니다 ([시작하기](getting-started.md#8-문서화-테스트))<br>2. `./gradlew apiDocs`<br>3. 출력된 `file:///…/build/api-docs/index.html` 링크를 엽니다 | 테스트 → 스니펫 → HTML·OpenAPI·AsyncAPI → `build/api-docs/` |
| 개발 중 평소 | IntelliJ 에서 `main` 실행, 또는 `./gradlew bootRun` | 앱이 뜨기 전에 `apiDocs` 가 돕니다. IntelliJ 는 자기가 부르는 `:classes` 빌드 바로 뒤에, `bootRun` 은 앱 태스크 앞에서 돕니다. 그다음 `http://localhost:8080/docs/index.html` 을 엽니다 ([실행할 때 자동 생성](#실행할-때-자동-생성)) |
| 문서만 다시 만들기 | `./gradlew apiDocs` | `build/api-docs/` 와 앱 리소스(`build/resources/main/static/docs`)를 갱신합니다. IntelliJ·`bootRun` 으로 이미 띄운 앱은 그 위치에서 서빙하므로 다시 시작하지 않아도 됩니다. jar 로 띄운 앱은 jar 에 담긴 문서를 그대로 보여 줍니다 |
| 문서를 담은 실행 jar | `./gradlew bootJar` | 먼저 `apiDocs` 와 `apiDocsAppDocs` 를 돌리고, 문서를 `build/libs/<name>-<version>.jar` 의 `BOOT-INF/classes/static/docs/` 에 넣습니다 (`bootJar.enabled` 가 기본값 true 일 때만. [운영 노출](#운영-노출)) |
| CI | `./gradlew build` | 문서화 테스트가 실패하면 빌드가 실패하고, Spring Boot 앱이면 `bootJar` 가 `apiDocs` 까지 끌고 옵니다. 내보내기·업로드는 하지 않습니다. 무엇이 도는지는 표 아래에, 워크플로 파일은 [CI 적용](ci.md)에 있습니다 |
| API 변경이 의도한 것일 때 | 1. `./gradlew apiDocsBaseline`<br>2. `docs/openapi/openapi3.yaml` 을 변경과 함께 커밋 | 이후 `apiDocsCheck` 는 새 기준과 비교합니다. 한 번에 실행해도(`./gradlew apiDocsBaseline apiDocsCheck`) 비교는 항상 기준 갱신 뒤에 돕니다 ([OpenAPI 변경 비교](checks.md#openapi-변경-비교)) |
| 스펙을 다른 곳에 넘기기 | `./gradlew apiDocsExport`, `./gradlew apiDocsUpload`, 또는 `export.publishSpecs = true` 로 `./gradlew publish` | 직접 실행할 때만 돌고, 필요한 결과를 만드는 태스크(`openapi3`, `apiDocsAsyncApi`, `apiDocs`)만 먼저 돌립니다 ([스펙 내보내기](outputs.md#스펙-내보내기)) |
| 여러 프로젝트를 한곳에 | 모으는 프로젝트에서 `./gradlew apiDocsAggregate` | `restdocsAggregation(...)` 으로 선언한 프로젝트마다 `apiDocs` 를 돌린 뒤 `build/api-docs-aggregate/` 를 만듭니다 ([여러 프로젝트 모으기](outputs.md#여러-프로젝트-모으기)) |
| 처음부터 다시 | `./gradlew clean` 후 위 명령 중 하나 | 생성된 문서 전부와 앱 리소스의 복사본을 포함해 `build/` 를 지웁니다. 테스트를 지우거나 이름을 바꿨을 때는 필요 없습니다 (아래) |

**CI 에서 `./gradlew build` 가 돌리는 것.**

- `check` 가 `test` 를 돌리므로 문서화 테스트가 실패하면 빌드가 실패합니다.
- Spring Boot 앱이면 `assemble` 이 `bootJar` 를 만들면서 `apiDocs` 까지 끌고 옵니다(`bootJar.enabled` 일 때, 기본값).
- `apiDocsCheck` 는 `openApiCheck { runOnCheck = true }` 일 때만 들어가고, 아니면 `./gradlew build apiDocsCheck` 로 돌립니다.
- `apiDocsCoverage` 는 `apiDocs` 와 함께 돌고, `coverage { failOnUndocumented = true }` 일 때만 빌드를 실패시킵니다 ([문서화 커버리지](checks.md#문서화-커버리지)).
- 내보내기·업로드는 하지 않습니다.

**테스트를 지우거나 이름을 바꿨을 때.** `cleanBeforeTest = true`(기본값)이면 `test` 가 실행 전에 스니펫·AsciiDoc 디렉터리를 비우고, 뒤 단계(`apiDocsAsciidocSources`, `apiDocsOpenApiSnippets`, `apiDocs`)는 복사가 아니라 동기화하고 `apiDocsAppDocs` 는 지난번에 복사했지만 이제 없는 파일을 지우므로, 없어진 테스트의 문서는 다음 실행에서 사라집니다. 테스트를 지우거나 이름을 바꾸면 테스트 클래스가 바뀌므로 `test` 는 알아서 다시 돕니다. `clean` 이 필요한 것은 `cleanBeforeTest = false` 로 둔 경우뿐입니다.

중간 태스크까지 모든 태스크는 [Gradle 플러그인](#gradle-플러그인) 표에, 결과 경로는 [생성 위치](#생성-위치)에 있습니다.

### 자주 하는 실수

- **문서가 바뀌지 않았습니다.**
  - jar 로 띄운 앱은 빌드할 때 담긴 문서를 그대로 씁니다. `bootJar` 로 다시 만들고 다시 시작합니다.
  - 자동 생성은 `bootRun` 하나만 요청했을 때만 돌므로 `./gradlew clean bootRun` 은 문서를 만들지 않고 앱을 띄웁니다. 이때는 `./gradlew apiDocs` 를 실행합니다(다시 시작할 필요 없음).
  - IntelliJ 에서는 빌드 설정이 Gradle 이어야 자동 생성됩니다.
  - IDE 실행이나 `bootRun` 중에 실패한 테스트는 앱 실행을 막지 않지만 그 문서는 빠지고, 실패는 빌드 로그에 남습니다.
  - `test` 가 UP-TO-DATE 라면 테스트·앱 코드·리소스가 바뀌지 않았으니 문서도 달라질 것이 없습니다. 예외는 직접 쓴 문서가 include 하는 `.adoc` 이 아닌 파일입니다 (`./gradlew asciidoctor --rerun`, [Gradle 플러그인](#gradle-플러그인) 설명 참고).
- **jar 에 문서가 없습니다.** `bootJar { enabled }` 를 확인하고(예: [운영 노출](#운영-노출)의 `-PwithDocs=false` 같은 속성이 끕니다), `jar tf build/libs/<name>-<version>.jar | grep static/docs` 로 안을 봅니다. 문서화 테스트가 실패하면 `bootJar` 가 실패하며, 문서가 일부만 든 jar 를 만들지는 않습니다.
- **`apiDocsCheck` 가 "No OpenAPI baseline" 으로 실패합니다.** 아직 기준 파일이 없습니다. `./gradlew apiDocsBaseline` 을 한 번 실행해 파일을 커밋하거나, 기준을 만들 때까지 `openApiCheck { requireBaseline = false }` 로 둡니다.

## Gradle 플러그인

`id("io.github.kardx-project")` 는 문서 파이프라인을 구성하고 라이브러리 의존성을 넣습니다. 다른 플러그인은 적용하지 않고, 적용돼 있을 때만 연결합니다. 플러그인 순서는 상관없습니다. Gradle 7.6 이상이 필요하고, 그보다 낮으면 적용할 때 필요한 버전을 알리고 실패합니다(실제로 확인한 버전은 7.6.4 를 포함해 [호환성](../../README.ko.md#호환성) 표에 있습니다).

| 연결하는 것 | 하는 일 |
|---|---|
| 의존성 (`compileOnly`, `testImplementation`) | 라이브러리와 테스트 클라이언트를 넣습니다. 직접 선언한 모듈은 건드리지 않습니다 ([자동으로 넣는 의존성](#자동으로-넣는-의존성)) |
| `test` | 스니펫·AsciiDoc 경로를 시스템 속성으로 넘기고, 실행 전에 지난 결과를 지웁니다 |
| `apiDocsSourceDocs` | 실험 기능이며 기본으로 꺼져 있습니다. `sourceDocs { enabled = true }` 면 `test` 전에 돌아 `main` 소스의 KDoc·Javadoc 을 `build/kardx/source-docs/source-docs.json` 에 쓰고, 테스트가 이를 설명으로 씁니다 (자세한 내용은 표 아래) |
| `apiDocsIndex` | 문서를 만든 테스트 클래스마다 링크를 넣어 index 를 만듭니다. 설정은 선택입니다 (자세한 내용은 표 아래) |
| `apiDocsAsciidocSources` | 직접 쓴 문서, 생성된 문서, index 를 Asciidoctor 입력 디렉터리 하나로 모읍니다 |
| `asciidoctor` (`org.asciidoctor.jvm.convert`) | 입력 디렉터리, 스니펫 경로, `spring-restdocs-asciidoctor` 확장, Boot 4 용 AsciidoctorJ 3 을 설정합니다. `_` 로 시작하는 파일은 단독 HTML 로 만들지 않습니다. 스니펫 중 `*.adoc` 만 입력으로 보므로, 스니펫이 같으면 테스트를 다시 돌려도 건너뜁니다. 변환 JVM 에 `asciidoctorJvmArgs`(기본 `-XX:TieredStopAtLevel=1`)를 넘깁니다 |
| `apiDocsOpenApiSnippets` (`com.epages.restdocs-api-spec`) | OpenAPI 조각(`resource.json`)을 `build/kardx/api-spec-snippets` 로 복사하고, 같은 API 의 테스트 사이 보안 요구사항을 맞춥니다 (토큰 없는 테스트가 섞여도 Authorize 유지). 이름이 같고 구조가 다른 스키마도 여기서 합치거나 이름을 바꾸고 `OpenAPI schema names: …` 로 로그에 남깁니다 ([스키마 이름](http-api.md#스키마-이름)). 테스트 출력을 고치지 않으므로 다음 빌드에서 테스트가 다시 돌지 않습니다 |
| `openapi3` (`com.epages.restdocs-api-spec`) | 그 복사본으로 스펙을 만들고 라이브러리가 남긴 정보를 합칩니다 ([OpenAPI 스펙에 나가는 정보](http-api.md#openapi-스펙에-나가는-정보)). 이 라이브러리에 맞는 기본값을 두고, epages 가 거는 `check` 의존은 뺍니다 (자세한 내용은 표 아래) |
| `apiDocsAsyncApi` | WebSocket·STOMP·SSE 메시지로 AsyncAPI 문서와 Try it out 뷰어를 만듭니다. 스니펫 중 `*.asyncapi.json` 과 `asyncApi { }` 설정이 입력이라 바뀐 것이 없으면 건너뜁니다. 앱 코드만 바뀌면 다시 실행하지 않습니다 |
| `apiDocs` | HTML, OpenAPI, AsyncAPI 를 한 디렉터리에 모읍니다. 켜 두었으면 Postman Collection 과 IntelliJ HTTP Client 파일도 함께 모읍니다 |
| `apiDocsCoverage` (`com.epages.restdocs-api-spec`) | `apiDocs` 의 일부로 돕니다. 앱이 매핑한 엔드포인트와 문서화한 operation 을 비교해 보고서를 씁니다 ([문서화 커버리지](checks.md#문서화-커버리지)) |
| `apiDocsHttpClient` (`com.epages.restdocs-api-spec`) | 켜면 `apiDocs` 가 IntelliJ HTTP Client 파일도 모읍니다 ([IntelliJ HTTP Client](outputs.md#intellij-http-client)) |
| `apiDocsAppDocs` | `apiDocs` 뒤에 항상 실행됩니다. Spring Boot 앱이면 문서를 앱 리소스로 복사해 앱이 `/docs/index.html` 로 서빙하게 합니다 (자세한 내용은 표 아래) |
| IntelliJ 실행·`bootRun` | 앱을 띄우기 전에 `apiDocs` 를 실행합니다 ([실행할 때 자동 생성](#실행할-때-자동-생성)) |
| `apiDocsCheck`, `apiDocsBaseline` (`com.epages.restdocs-api-spec`) | 커밋해 둔 기준 스펙과 비교하거나 기준을 갱신합니다 ([OpenAPI 변경 비교](checks.md#openapi-변경-비교)) |
| `apiDocsViewer` (`com.epages.restdocs-api-spec`) | 켜면 Redoc·Scalar 정적 뷰어 페이지를 만듭니다 ([OpenAPI 뷰어](outputs.md#openapi-뷰어-redoc--scalar)) |
| `postman` (`com.epages.restdocs-api-spec`) | 켜면 `apiDocs` 가 Postman Collection 도 모읍니다 ([Postman Collection](outputs.md#postman-collection)) |
| `apiDocsAggregate` | 여러 프로젝트의 문서를 하나의 목록과 병합 스펙으로 모읍니다 ([여러 프로젝트 모으기](outputs.md#여러-프로젝트-모으기)) |
| `apiDocsStubsJar` | 켜면 문서화 테스트가 만든 WireMock 매핑을 stub jar 로 묶습니다 ([WireMock stub](outputs.md#wiremock-stub)) |
| `apiDocsExport`, `apiDocsUpload` | 직접 실행하면 스펙을 디렉터리로 내보내거나 HTTP 로 올립니다. `maven-publish` 가 있고 `export.publishSpecs = true` 면 배포물에 스펙을 붙입니다 ([스펙 내보내기](outputs.md#스펙-내보내기)) |
| `testRuntimeClasspath` | WebFlux 앱이면 epages 가 끌고 오는 `spring-boot-starter-web` 을 빼서 서블릿 앱으로 뜨는 것을 막습니다 ([WebTestClient 의존성](webtestclient.md#의존성)). 자세한 내용은 표 아래 |

긴 행의 자세한 내용입니다.

- **`apiDocsSourceDocs`** ([KDoc/Javadoc 에서 설명 가져오기](http-api.md#kdocjavadoc-에서-설명-가져오기))
  - `sourceDocs { enabled = true }` 일 때만 `test` 전에 돕니다. 이 프로젝트와, 기본으로는 의존하는 프로젝트(`project(...)`)의 Kotlin·Java `main` 소스에서 KDoc·Javadoc 을 읽어 `build/kardx/source-docs/source-docs.json` 에 씁니다.
  - `test` 는 이 파일 경로를 시스템 속성으로 받고, 테스트는 필드·파라미터·enum 상수·operation 설명의 마지막 출처로 씁니다.
  - 파일에 무엇을 넣을지는 항목별 스위치(`fieldDocs`, `enumDocs`, `schemaDescriptions`, `paramDocs`, `methodDocs`)를 따릅니다. 메서드 주석은 `methodDocs = true` 일 때만 모으므로, 핸들러 메서드의 구현 메모가 기본으로 공개 summary 가 되지는 않습니다.
  - 입력은 소스 파일이고 출력은 캐시되므로 소스가 그대로면 건너뜁니다. 소스가 하나라도 바뀌면 전체를 다시 읽으며 보통 1초도 걸리지 않습니다 (이 라이브러리의 모듈까지 `project(...)` 의존성으로 읽는 Boot 4 샘플에서 약 0.2초).
  - 읽지 못하는 선언은 건너뛸 뿐 빌드를 실패시키지 않습니다.
  - 소스는 UTF-8 로 읽습니다. UTF-8 이 아닌 파일(`MS949` 로 컴파일하던 옛 Java 모듈 등)도 읽되 잘못된 바이트를 U+FFFD 로 바꾸고, 태스크가 `Source docs: N source files are not UTF-8 (first: …)` 경고를 한 번 남깁니다. 그 설명을 제대로 얻으려면 소스를 UTF-8 로 저장합니다.
  - `enabled = false`(기본값)면 태스크는 등록된 채로 남지만 `test` 가 의존하지 않고 시스템 속성도 받지 않으며, 직접 실행해도 건너뜁니다(`SKIPPED`).
- **`apiDocsIndex`**
  - 테스트가 클래스별 문서와 함께 남긴 기록(`build/generated-asciidoc/.restdocs-documents/`)을 읽으므로 클래스패스를 훑지 않습니다.
  - 템플릿(`src/docs/asciidoc/index-template.adoc`)이 없으면 내장 템플릿(제목 `index.title`, 기본값 프로젝트 이름)을 씁니다.
  - `index.packageName`·`index.baseClass` 를 함께 정하면 예전처럼 그 패키지에서 기본 클래스를 상속한 테스트와 Kotest 스펙만 넣습니다. 하나만 정했거나 문서를 쓴 테스트가 없으면 이유를 한 줄 남기고 건너뜁니다.
  - 결과는 test 가 비우는 `build/generated-asciidoc` 밖(`build/kardx/index/index.adoc`)에 두므로, 테스트를 다시 돌려도 생성 문서가 같으면 건너뜁니다.
- **`openapi3`**
  - 전역 헤더(`open-api.global-headers`)를 `apiKey` 보안 스키마로 바꾸고, 라이브러리가 남긴 타입·제약·기본값·예시·multipart·쿠키 정보를 합치고, 응답 설명·operationId·태그·예시를 다듬습니다.
  - `openapi3 { }` 에 적지 않은 값은 이 라이브러리에 맞춰 둡니다: `format = "yaml"`(epages 기본값은 json), `title` = 프로젝트 이름, `version` = 프로젝트 버전(`unspecified` 면 epages 기본값), server `"/"`(Swagger UI 의 Try it out 이 문서를 연 서버로 요청). `openapi3 { }` 에 적은 값은 덮어쓰지 않습니다.
  - epages 가 거는 `check` 의존은 뺍니다: `apiDocs`·`openapi3` 는 테스트만 먼저 실행하고 ktlint·detekt 같은 `check` 는 실행하지 않습니다. 정적 분석도 같이 돌리려면 `./gradlew check apiDocs` 를 씁니다.
- **`apiDocsAppDocs`**
  - Spring Boot 앱이면 문서를 `build/resources/main/<bootJar.path>`(기본 `static/docs`)로 복사해서 IDE 실행, `bootRun`, 실행 jar 모두 `/docs/index.html` 로 열리게 합니다.
  - 실제 문서 경로·파일 이름을 시작 로그가 읽을 파일(`META-INF/kardx/docs-locations.properties`)로 남기고, 생성 위치를 출력합니다.
  - 민감 헤더를 가리지 않은 문서가 실행 jar 에 들어가면 경고합니다 ([운영 노출](#운영-노출)).
  - `apiDocs` 가 UP-TO-DATE 여도 돌므로 지워진 문서를 다시 채웁니다. 앱이 같은 디렉터리에 둔 파일은 건드리지 않습니다 ([앱 리소스에 넣는 문서](#앱-리소스에-넣는-문서)).
- **`testRuntimeClasspath`**
  - 여기서 WebFlux 앱은 `spring-boot-starter-webflux` 를 선언하고 `spring-boot-starter-web`·`-websocket`, `spring-webmvc` 는 선언하지 않은 앱입니다.
  - 판정은 직접 선언한 의존성만 봅니다(전이로 들어온 스타터는 못 봄).
  - 대상은 `testRuntimeClasspath` 뿐입니다. 다른 소스셋·JVM Test Suites 는 직접 `exclude` 합니다.
  - 끄려면 `excludeServletStarterForWebFlux = false`.

이 플러그인의 태스크 이름은 모두 `apiDocs` 로 시작합니다. 직접 실행하는 태스크(`apiDocs`, `apiDocsIndex`, `apiDocsSourceDocs`, `apiDocsAsyncApi`, `apiDocsCoverage`, `apiDocsHttpClient`, `apiDocsCheck`, `apiDocsBaseline`, `apiDocsViewer`, `apiDocsAggregate`, `apiDocsStubsJar`, `apiDocsExport`, `apiDocsUpload`)는 `documentation` 그룹에 있어 `./gradlew tasks` 에 설명과 함께 나옵니다. 중간 단계 태스크(`apiDocsAsciidocSources`, `apiDocsOpenApiSnippets`, `apiDocsAppDocs`, `maven-publish` 와 `export.publishSpecs` 일 때만 있는 `apiDocsPublicationSpecs`)는 그룹이 없어 `./gradlew tasks --all` 에만 나옵니다.

문서(직접 쓴 `.adoc`)가 `.adoc` 가 아닌 스니펫 파일을 include 하면, 그 파일만 바뀌었을 때는 Asciidoctor 가 다시 실행되지 않습니다. 이럴 때는 `./gradlew asciidoctor --rerun` 으로 다시 만듭니다.

생성 문서에 라이브러리가 넣는 문구(목록 제목, index 링크, 제약 접두사, 공통 오류 응답 표 등)는 기본이 영어입니다. 한국어로 쓰려면 `kardx { language = "ko" }` 를 정합니다. 플러그인이 테스트 JVM·문서 생성 태스크·앱 시작 로그에 같은 값을 넘기므로 yml 이 아니라 여기서 정해야 문구가 섞이지 않습니다 ([문서 언어](configuration.md#문서-언어)). 태스크 콘솔 출력과 `apiDocsCheck` 보고서는 언어와 상관없이 영어입니다.

이 플러그인의 태스크는 Gradle configuration cache 와 호환됩니다 (태스크 액션이 `Project` 를 참조하지 않습니다). 다만 `org.asciidoctor.jvm.convert` 4.0.x 와 epages 플러그인은 아직 호환되지 않아, `--configuration-cache` 로 전체 파이프라인을 돌리면 그 플러그인들의 문제가 보고됩니다. 선택 기능의 태스크(`apiDocsCheck`, `apiDocsViewer`, `apiDocsAggregate`, `apiDocsStubsJar`, `apiDocsExport`, `apiDocsUpload`)도 호환됩니다. 예: `./gradlew apiDocs -x asciidoctor --configuration-cache --configuration-cache-problems=warn` 은 두 번째 실행에서 캐시를 재사용하고, 남는 경고는 epages `openapi3`·`postman` 의 것뿐입니다.

UP-TO-DATE 판단: 바뀐 것이 없으면 두 번째 `./gradlew apiDocs` 에서 `postman`·`apiDocsViewer` 는 UP-TO-DATE 로 건너뜁니다 (epages 가 선언하지 않은 입력·출력을 플러그인이 선언합니다). `apiDocsAppDocs` 는 지워진 앱 복사본을 되살리려고 계속 돕니다.

`openapi3` 은 매번 돕니다: epages 의 입력 `servers` 를 Gradle 이 직렬화할 수 없어 플러그인이 상태를 추적하지 않게(`doNotTrackState`) 하고, 스펙 파일(`build/api-spec/openapi3.yaml` 등)을 출력으로 선언합니다. 출력이 없으면 스펙만 바뀌었을 때 같은 빌드의 뒤 태스크가 옛 스펙을 UP-TO-DATE 로 쓸 수 있습니다.

이제 이 파일이 선언된 출력이므로 Gradle 8 이상은 누가 읽는지 검사합니다: `build/api-spec/openapi3.*` 을 읽는 사용자 태스크는 `dependsOn("openapi3")` 를 선언해야(또는 그 파일을 태스크에 연결된 입력으로 받아야) 하며, 아니면 Gradle 이 암묵적 의존성 문제를 알립니다.

epages `openapi3 { outputDirectory }` 가 앱 리소스(`build/resources/main/...`, `src/main/resources/...`) 안을 가리키면 플러그인이 한 번 경고하고 그 설정을 지우라고 안내합니다. 플러그인이 이미 스펙을 앱에 복사하기 때문입니다([앱 리소스에 넣는 문서](#앱-리소스에-넣는-문서)). `build/resources/main` 안이면 스펙 파일을 출력으로 선언하지 않습니다. `jar`·`bootJar`·`apiDocsIndex` 가 `openapi3` 에 의존하지 않고 그 디렉터리를 읽기 때문입니다.

```kotlin
kardx {
    snippetsDirectory = layout.buildDirectory.dir("generated-snippets")      // 기본값
    asciidocDirectory = layout.buildDirectory.dir("generated-asciidoc")      // 기본값
    asciidocSourceDirectory = layout.projectDirectory.dir("src/docs/asciidoc") // 기본값
    outputDirectory = layout.buildDirectory.dir("api-docs")                  // 기본값
    cleanBeforeTest = true                                                   // 기본값
    language = "ko"                      // 기본값 없음(en). 생성 문서에 라이브러리가 넣는 문구의 언어 (en, ko)
    generateOnRun = true                 // 기본값. IntelliJ 실행·bootRun 전에 문서 자동 생성
    openApiGlobalHeaders = setOf("X-Tenant-Id")   // Swagger Authorize 로 한 번만 입력하는 헤더 (테스트 설정과 합침)
    openApiCommonResponses.put(401, "토큰이 없거나 만료됨") // 기본값 없음. 테스트가 문서화하거나 선언하지 않은 공통 오류 응답
    openApiCommonResponseSchema = "ErrorResponse" // 기본값: 오류 응답이 가장 많이 가리키는 스키마
    startupLog = true                    // 기본값. 앱 시작 시 문서 주소 INFO 로그 (kardx-boot)
    startupLogScope = "developmentOnly"  // 기본값. IDE 실행·bootRun 에서만. 실행 jar 에서도 남기려면 "runtimeOnly"
    restDocsVersion = "4.0.2"            // Spring Boot 플러그인이 없을 때만 필요. 그때 Asciidoctor 플러그인이 있으면 반드시 필요
    excludeServletStarterForWebFlux = true // 기본값. WebFlux 앱이면 테스트 런타임에서 spring-boot-starter-web 을 뺀다
    addDependencies = true               // 기본값. 라이브러리·테스트 클라이언트 의존성을 넣는다
    asciidoctorJvmArgs = listOf("-XX:TieredStopAtLevel=1") // 기본값. asciidoctor 변환 JVM 인자. emptyList() 면 끈다

    index {                              // 모두 선택
        template = layout.projectDirectory.file("src/docs/asciidoc/index-template.adoc")   // 기본값. 없으면 내장 템플릿
        title = "주문 API"               // 기본값: 프로젝트 이름 (내장 템플릿의 제목)
        packageName = "com.example"      // 기본값 없음: 문서를 만든 모든 테스트 클래스. 정하면 baseClass 와 함께
        baseClass = "com.example.support.ApiDocsTest"
    }
    asyncApi {
        enabled = true                   // 기본값
        title = "Chat API"               // 기본값: 프로젝트 이름
        version = "1.0.0"                // 기본값: 프로젝트 버전
        description = "채팅 메시지"
        server("stomp", "stomp://localhost:8080/ws")
        server("websocket", "ws://localhost:8080")
        server("http", "http://localhost:8080")            // SSE
        fileName = "asyncapi"            // 기본값. asyncapi.yaml, asyncapi.html. 하위 경로도 됨: "async/index"
        bearerAuth()                     // 기본값 없음. basicAuth(), apiKeyHeader("X-API-KEY"), securitySchemes.put(...) 도 있다
    }
    bootJar {
        enabled = true                   // 기본값
        path = "static/docs"             // 기본값. 앱에서 /docs/index.html. 하위 디렉터리여야 한다 (앱 리소스에 넣는 문서)
    }
    coverage {                           // apiDocsCoverage (문서화 커버리지)
        enabled = true                   // 기본값
        failOnUndocumented = false       // 기본값. true 면 문서화하지 않은 엔드포인트가 있을 때 실패
        exclude("/internal/**")          // 기본 제외(/error, /actuator/** 등)에 더한다
    }

    // 아래는 기본으로 꺼져 있거나 직접 실행할 때만 동작하는 선택 기능이다
    sourceDocs {                         // apiDocsSourceDocs (KDoc/Javadoc 에서 설명 가져오기, 실험 기능)
        enabled = false                  // 기본값. true 면 test 가 먼저 스캔을 돌리고 주석을 설명으로 쓴다
        fieldDocs = true                 // 기본값. 프로퍼티·필드·getter·record 주석 → 필드 설명
        enumDocs = true                  // 기본값. enum 상수 주석 → 값 목록, @EnumCode 표
        schemaDescriptions = true        // 기본값. 클래스 주석 → OpenAPI 컴포넌트 스키마 설명
        paramDocs = true                 // 기본값. 핸들러 @param → 이름만 적은 파라미터
        methodDocs = false               // 기본값. true 면 컨트롤러 메서드 주석이 OpenAPI summary 가 된다
        includeProjectDependencies = true // 기본값. project(...) 의존성의 main 소스도 읽는다
    }
    openApiCheck {
        baseline = layout.projectDirectory.file("docs/openapi/openapi3.yaml") // 기본값
        failOn = "any"                   // 기본값. "breaking" 이면 깨는 변경만 실패, "none" 이면 경고만
        runOnCheck = false               // 기본값. true 면 ./gradlew check 에도 들어간다
        requireBaseline = true           // 기본값. 기준 파일이 없으면 실패 (failOn = "none" 이면 경고)
    }
    openApiViewer {
        enabled = false                  // 기본값
        type = "redoc"                   // 기본값. "scalar"
        fileName = "openapi.html"        // 기본값
        title = "주문 API"               // 기본값: 스펙의 info.title
    }
    html { enabled = true }               // 기본값(Gradle 속성 kardx.html). false 면 HTML 변환을 건너뛴다
    openApiInfo {                         // 스펙의 info, externalDocs, tags, x-tagGroups (OpenAPI 정보와 태그)
        descriptionFile = file("src/docs/openapi-intro.md")
        contact { email = "api@example.com" }
        tag("Users") { description = "가입과 프로필" }
    }
    postmanCollection { enabled = false } // 기본값
    httpClient {                          // IntelliJ HTTP Client 파일
        enabled = false                  // 기본값
        fileName = "api.http"            // 기본값
    }
    wireMock { enabled = false }          // 기본값
    openApiSecurity {                     // openApi { oauth2(...) } 가 쓰는 OAuth2·OpenID Connect 스키마
        oauth2 {
            clientCredentials("https://auth.example.com/oauth/token")
            scope("users:read", "사용자 조회")
        }
        openIdConnect("https://auth.example.com/.well-known/openid-configuration")
    }
    apiVersioning {                       // Spring Framework 7 API 버전 (API 버전)
        header("X-API-Version")          // 또는 queryParameter("version"), mediaTypeParameter("v"), pathSegment()
        splitSpecs = false               // 기본값
    }
    aggregate {                           // apiDocsAggregate (모으는 프로젝트에서)
        title = "전체 API"               // 기본값: 프로젝트 이름
        version = "1.0.0"                // 기본값: 프로젝트 버전
        description = "모든 서비스의 API" // 선택. 병합 스펙의 info.description
        outputDirectory = layout.buildDirectory.dir("api-docs-aggregate") // 기본값
    }
    export {                              // apiDocsExport, Maven 배포물 (스펙 내보내기)
        directory = layout.buildDirectory.dir("exported-specs") // 기본값 없음 (apiDocsExport 에 필수)
        asyncApi = true                  // 기본값 false. openApi 는 기본값 true, html 은 false
        publishSpecs = false             // 기본값
    }
    upload {                              // apiDocsUpload
        url = "https://registry.example.com/apis/{name}/{version}/{type}" // 기본값 없음 (apiDocsUpload 에 필수)
    }
}
```

`asyncApi { bearerAuth() }`, `basicAuth()`, `apiKeyHeader("X-API-KEY")` 는 WebSocket·STOMP·SSE 서버의 인증 방식을 선언합니다. AsyncAPI 스펙의 `components.securitySchemes` 와 뷰어 Try it out 의 인증 상자가 됩니다. 쿼리 파라미터나 쿠키로 받는 키는 직접 넣습니다: `securitySchemes.put("tokenQuery", "apiKey:query:access_token")`. 프로토콜마다 브라우저가 보낼 수 있는 것까지 [AsyncAPI 인증](messaging.md#asyncapi-인증)에 있습니다.

### 자동으로 넣는 의존성

플러그인은 아래 의존성을 넣습니다. 같은 모듈을 직접 선언했으면(버전이 달라도, 같은 빌드의 프로젝트여도) 넣지 않습니다. 선언된 의존성만 보고 정하고, 그 configuration 을 쓰는 classpath 가 해석될 때 더하므로(`withDependencies`) 빌드 스크립트의 선언 순서와 상관없고 configuration cache 와 함께 쓸 수 있습니다.

| 넣는 곳 | 의존성 | 언제 | 버전 |
|---|---|---|---|
| `compileOnly` | `io.github.kardx-project:kardx-annotations` | 항상 | 플러그인과 같음 |
| `testImplementation` | `io.github.kardx-project:kardx` | 아래 Kotest 확장을 넣지 않을 때 | 플러그인과 같음 |
| `testImplementation` | `io.github.kardx-project:kardx-kotest` / `-kotest5` | 테스트 의존성에 적힌 `io.kotest` 버전이 6.x / 5.x 하나일 때 (버전을 적지 않았거나 섞였으면 넣지 않음, 본체를 함께 가져온다) | 플러그인과 같음 |
| `testImplementation` | `org.springframework.restdocs:spring-restdocs-mockmvc` | MVC 앱: `spring-boot-starter-web`·`spring-boot-starter-websocket` 또는 `spring-webmvc` 선언. WebFlux 를 함께 선언해도 MVC (Spring Boot 가 서블릿 앱으로 띄운다) | 적지 않음 (Spring Boot BOM) |
| `testImplementation` | `org.springframework.restdocs:spring-restdocs-webtestclient` | WebFlux 앱: 위가 없고 `spring-boot-starter-webflux` 또는 `spring-webflux` 선언 | 적지 않음 (Spring Boot BOM) |
| `testImplementation` | `com.epages:restdocs-api-spec-mockmvc` / `-webtestclient` | epages 플러그인이 있고 위와 같은 MVC / WebFlux 판정 | epages 플러그인과 같음 |
| `developmentOnly`, `compileOnly` | `io.github.kardx-project:kardx-boot` | Spring Boot 앱 ([앱 시작 로그](#앱-시작-로그)) | 플러그인과 같음 |

- 웹 스타터가 없는 프로젝트(예: `spring-websocket` 만으로 STOMP 메시지만 문서화)는 본체만 넣습니다. 메시지 문서화(`MessageDocumentation`)는 MockMvc·WebTestClient 없이 동작합니다.
- REST Docs 클라이언트의 버전은 프로젝트의 Spring Boot BOM(`io.spring.dependency-management` 플러그인 또는 `implementation(platform("org.springframework.boot:spring-boot-dependencies:…"))`)이 정합니다. 아무것도 버전을 관리하지 않으면 해석이 실패하고, 플러그인이 이유와 고치는 방법을 한 줄 남깁니다.
- epages 플러그인 버전은 플러그인 jar 이름에서 읽습니다. 읽지 못하면(소스로 포함한 빌드 등) epages 모듈을 넣지 않고 lifecycle 로그를 한 줄 남깁니다. Spring Boot 와 epages 버전 계열이 어긋나면(Boot 4 + 0.19.x, Boot 3 + 0.20.x) 경고합니다.
- 버전을 직접 정하려면 그 모듈을 선언합니다. 전부 끄려면 `kardx { addDependencies = false }` 로 끄고 [플러그인 없이 쓸 때의 의존성](getting-started.md#플러그인-없이-쓸-때의-의존성)을 직접 선언합니다. 시작 로그 모듈은 `startupLog = false` 로 따로 끕니다.

경로는 플러그인이 테스트에 시스템 속성으로 넘기므로 yml 의 `snippets.directory` 보다 우선합니다. Maven 에서는 이 단계를 대신 돌리는 Maven 플러그인을 씁니다([Maven 과 명령줄](maven.md)). 플러그인 없는 Gradle 이면 라이브러리 CLI 를 직접 실행합니다: `io.github.kardx.asciidoc.DocumentationIndexCli`, `io.github.kardx.asyncapi.AsyncApiCli`. OpenAPI 후처리, 뷰어, 커버리지, `.http`, Postman, 스펙 검사는 tooling CLI `io.github.kardx.tooling.cli.ApiDocsCli` 로 합니다([Maven 플러그인 없이](maven.md#maven-플러그인-없이)).

## 실행할 때 자동 생성

앱을 실행하면 플러그인이 그 전에 문서를 만듭니다. 따로 `./gradlew apiDocs` 를 실행할 필요가 없습니다.

| 실행 방법 | 동작 |
|---|---|
| IntelliJ 에서 `main` 실행 (기본 설정인 Gradle 빌드) | IntelliJ 가 부르는 Gradle 빌드(`:classes`) 끝에 `apiDocs` 를 이어서 실행합니다 |
| `./gradlew bootRun` | 앱을 띄우기 전에 `apiDocs` 를 실행합니다 |
| 실행 jar (`bootJar`) | 항상 `apiDocs` 뒤에 만듭니다 |

- 이렇게 실행할 때는 문서 테스트가 실패해도 앱 실행을 막지 않습니다. 통과한 테스트의 문서만 만들고 실패는 빌드 로그에 남습니다. `./gradlew test` 나 `build` 는 평소처럼 실패합니다.
- 코드가 바뀌지 않았으면 테스트와 문서 생성은 up-to-date 로 건너뜁니다. 첫 실행이나 코드를 바꾼 뒤에만 시간이 걸립니다.
- IntelliJ 동기화나 터미널의 `./gradlew classes` 에서는 만들지 않습니다. IntelliJ 가 테스트를 자기 러너로 실행할 때(`:classes :testClasses` 빌드)도 만들지 않으므로 테스트 하나를 돌릴 때 전체 문서 테스트가 먼저 돌지 않습니다. 같은 이유로 테스트 소스의 `main`(예: Testcontainers 용 `TestApplication`)을 실행할 때는 만들지 않습니다.
- IntelliJ 설정에서 빌드를 "IntelliJ IDEA" 로 바꾸면 Gradle 을 거치지 않으므로 자동으로 만들 수 없습니다. 이때는 `./gradlew apiDocs` 를 실행합니다.
- 끄려면 `kardx { generateOnRun = false }` 를 씁니다.

### 앱 시작 로그

앱이 뜨면 문서 주소를 INFO 로그로 남깁니다.

```
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (REST Docs    ) http://localhost:8080/docs/index.html
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (Swagger UI   ) http://localhost:8080/swagger-ui/index.html
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (OpenAPI spec ) http://localhost:8080/docs/openapi3.yaml
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (WebSocket/SSE) http://localhost:8080/docs/asyncapi.html
```

줄 머리와 항목 이름은 [문서 언어](configuration.md#문서-언어)를 따릅니다. `kardx { language = "ko" }` 면 `API 문서 (OpenAPI 스펙 )` 처럼 한국어입니다. 언어는 앱 설정 `kardx.language`, 없으면 플러그인이 남긴 값, 그것도 없으면 영어입니다. 지원하지 않는 값이어도 앱은 멈추지 않고 경고 한 줄(`kardx.language: unsupported language 'fr'. Use one of en, ko; falling back to en`)을 남긴 뒤 영어를 씁니다.

문서 경로와 파일 이름은 앱 설정에 적지 않아도 됩니다. 플러그인의 `apiDocsAppDocs` 가 실제 값을 문서와 함께 `META-INF/kardx/docs-locations.properties`(앱 클래스패스, 정적 리소스 위치가 아니라 웹으로 서빙되지 않음)에 남기고 시작 로그가 그것을 기본값으로 읽습니다.

| 문서 | 빌드 설정 (바꾸면 시작 로그도 따라감) | 앱 설정으로 덮어쓰기 |
|---|---|---|
| 문서 경로 | `kardx { bootJar { path = "static/api-docs" } }` | `kardx.docs-path` |
| REST Docs | `index.html` (index 결과) | `startup-log.rest-docs.file` |
| OpenAPI | `openapi3 { outputFileNamePrefix = "api"; format = "json" }` (실제로 생긴 파일) | `startup-log.open-api.file` |
| WebSocket·SSE | `kardx { asyncApi { fileName = "async/index" } }` (하위 경로 가능) | `startup-log.async-api.file` |
| OpenAPI 뷰어 | `kardx { openApiViewer { fileName = "reference.html" } }` | `startup-log.open-api-viewer.file` |

글자·제목·켜기 여부는 앱 설정으로 바꿉니다. 항목마다 `enabled: false` 로 뺄 수 있습니다. IntelliJ 가 키를 자동 완성합니다. yml 에 경로·파일 이름을 적으면 yml 이 이깁니다(플러그인 없이 쓰거나 다른 위치에서 문서를 서빙할 때).

<!-- docs-pair: ignore-next-block -->
```yaml
kardx:
  startup-log:
    enabled: true
    title: API 문서                     # 줄 머리 (기본값은 언어를 따른다: API docs / API 문서)
    rest-docs:  { label: REST Docs }
    swagger-ui: { label: Swagger UI }   # 주소는 springdoc.swagger-ui.path 를 따른다
    open-api-viewer: { label: OpenAPI 뷰어 }   # openApiViewer 를 켰을 때
    open-api:   { label: OpenAPI 스펙 }
    async-api:  { label: WebSocket·SSE, enabled: true }
```

- Spring Boot 앱이면 플러그인이 `kardx-boot` 를 Boot 의 `developmentOnly` 에 넣습니다. 그래서 IntelliJ 실행과 `bootRun` 에서만 로그가 나오고, 실행 jar 와 테스트 클래스패스에는 들어가지 않습니다. 실행 jar(`java -jar`)에서도 남기려면 `kardx { startupLogScope = "runtimeOnly" }` 로 바꿉니다.
- 같은 모듈을 `compileOnly` 에도 전이 의존성 없이 넣습니다. IntelliJ 는 RUNTIME 범위 의존성(`developmentOnly`·`runtimeOnly`)의 설정 메타데이터를 읽지 않아 `application.yml` 의 `kardx.*` 키에 "Cannot resolve configuration property" 경고를 내기 때문입니다. `compileOnly` 는 실행 jar·런타임·테스트 클래스패스에 들어가지 않습니다. `startupLogScope` 가 이미 컴파일 클래스패스에 보이는 configuration(`implementation`·`api`·`compileOnly`)이면 따로 넣지 않습니다.
- 플러그인 없이 쓰면 직접 넣습니다: `developmentOnly("io.github.kardx-project:kardx-boot:0.1")`, IDE 자동 완성을 위해 같은 좌표를 `compileOnly(...) { isTransitive = false }` 로도. 플러그인을 쓰면서 모듈을 직접 선언했을 때도 플러그인은 건드리지 않으므로 `compileOnly` 를 직접 넣습니다.
- 실제 포트, `server.servlet.context-path`(WebFlux 앱은 `spring.webflux.base-path`), `spring.mvc.servlet.path`(서블릿 앱. 정적 문서와 springdoc Swagger UI 를 모두 `DispatcherServlet` 이 서빙하므로 두 주소에 붙습니다), `server.ssl.enabled`, `springdoc.swagger-ui.path` 를 반영하고(WebFlux 앱의 Swagger UI 기본 주소는 springdoc 의 `/webjars/swagger-ui/index.html`), 있는 문서만 보여 줍니다. 문서가 아직 없으면 만드는 방법을 한 줄 남깁니다. 웹 서버가 없는 앱·MOCK 테스트에서는 남기지 않습니다.
- 끄기: 앱 설정 `kardx.startup-log.enabled=false` (운영 프로필 등), 또는 의존성째 빼려면 `kardx { startupLog = false }`.
- 문서 경로를 바꿔도(`bootJar { path = "static/api-docs" }`) 앱 설정을 맞출 필요가 없습니다. 위 파일이 없으면(플러그인 없이 쓰거나 문서를 아직 만들지 않음) `docs-path` 기본값 `docs` 와 파일 이름 기본값을 씁니다.

## 운영 노출

`bootJar.enabled`(기본값 true)이면 문서가 앱 리소스와 실행 jar 에 들어가 운영 앱도 `/docs/**` 로 서빙합니다. 문서에는 테스트가 실제로 보낸 요청·응답이 담기므로, 민감 헤더 가리기(`kardx.mask-sensitive-headers`, 기본값 false)를 켜지 않았다면 테스트에 쓴 토큰·쿠키가 그대로 나갑니다. 테스트가 만든 기록에 가리지 않고 쓴 문서가 있으면 `apiDocsAppDocs` 가 경고를 한 줄 남깁니다.

```
kardx: API docs are packaged into the application (kardx.bootJar.enabled = true), but 3 documented test class(es) ran without sensitive header masking (e.g. user-api-docs-test). ...
```

판정은 테스트가 클래스별 문서와 함께 남기는 기록(`build/generated-asciidoc/.restdocs-documents/*.properties` 의 `headers-masked`)으로 합니다. yml 설정은 테스트 런타임에서만 알 수 있기 때문입니다. 기록은 그 문서를 쓸 때의 설정(`masking.headers` 가 비어 있지 않거나 `masking.masker` 를 정함)을 따릅니다. 기본값은 바꾸지 않았으므로, 다음 중 프로젝트에 맞는 것을 고릅니다.

- **가리기 켜기**: 테스트 설정(`src/test/resources/application-docs.yml` 등)에 `kardx.mask-sensitive-headers: true`, 또는 코드에서 `masking.maskSensitive()` ([민감 정보 마스킹](configuration.md#민감-정보-마스킹)).
- **운영 빌드에서 빼기**: 운영 jar 를 만드는 빌드에서만 `bootJar.enabled` 를 끕니다. 예: `kardx { bootJar { enabled = providers.gradleProperty("withDocs").map(String::toBoolean).orElse(true) } }` 로 두고 운영 빌드는 `./gradlew clean bootJar -PwithDocs=false`.
  - 이 빌드에서는 `clean` 을 그대로 둡니다: `bootJar.enabled = false` 면 `apiDocsAppDocs` 가 앞선 실행이 `build/resources/main/static/docs` 에 복사한 문서를 지우지만, `bootJar` 가 이 태스크를 돌리지 않으므로 빌드 디렉터리를 다시 쓰면 옛 복사본은 `apiDocs` 를 돌릴 때에야 지워집니다.
  - `bootJar.enabled = false` 면 `bootJar` 가 `apiDocs` 를 돌리지 않으므로 이 빌드에서는 `build/api-docs/` 도 만들어지지 않습니다. 필요하면 `./gradlew apiDocs` 를 따로 돌립니다.
- **경로 보호**: 문서를 운영에 두되 Spring Security 로 `/docs/**`·`/swagger-ui/**` 를 인증된 사용자에게만 열거나, 운영 프로필에서 `spring.web.resources.add-mappings` 로 막습니다. 경고는 남으므로 가리기도 함께 켜는 것을 권합니다.

## 생성 위치

`./gradlew apiDocs` 기준입니다. Maven 이면 `build` 대신 `target` 이고, Maven 플러그인의 결과 위치는 [Maven 과 명령줄](maven.md)에 있습니다.

| 산출물 | 위치 | 만드는 곳 |
|---|---|---|
| REST Docs 스니펫 | `build/generated-snippets/<class-name>/<method-name>/*.adoc` | 테스트 (`andDocument`, `MessageDocumentation`) |
| AsciiDoc 문서 | `build/generated-asciidoc/<class-name>.adoc` | 테스트 (`AutoRestDocumentationExtension`) |
| Enum 코드 문서 | `build/generated-asciidoc/code/enum-code.adoc` | 테스트 (`EnumDocumentationExtension`) |
| index | `build/kardx/index/index.adoc` | `apiDocsIndex` 태스크 (test 가 비우는 `generated-asciidoc` 밖에 둡니다) |
| KDoc/Javadoc 설명 | `build/kardx/source-docs/source-docs.json` | `apiDocsSourceDocs` 태스크, `sourceDocs { enabled = true }` 일 때만 (테스트가 `-Dkardx.source-docs` 로 읽음) |
| HTML 변환 입력 | `build/kardx/asciidoc-sources/` | `apiDocsAsciidocSources` 태스크 (직접 쓴 문서 + 생성 문서 + index 를 한 디렉터리로) |
| REST Docs HTML | `build/docs/asciidoc/*.html` | `asciidoctor` 태스크 |
| OpenAPI (Swagger 용 yaml) | `build/api-spec/openapi3.yaml` | `openapi3` 태스크 (epages) |
| OpenAPI 리소스 조각 | `build/generated-snippets/<class-name>/<method-name>/resource.json` | 테스트 (epages 가 있을 때) |
| OpenAPI 보조 정보 | 같은 디렉터리의 `openapi-schemas.json`(스키마 이름 출처), `openapi-extras.json`(multipart 파트·쿠키·값 본문 스키마·태그 설명) | 테스트. Gradle 플러그인이 읽는다 |
| AsyncAPI | `build/kardx/asyncapi/asyncapi.yaml`, `asyncapi.html` | `apiDocsAsyncApi` 태스크 |
| 위 결과 모음 | `build/api-docs/` (`index.html`, `openapi3.yaml`, `asyncapi.html` …) | `apiDocs` 태스크 |
| OpenAPI 뷰어 (켰을 때) | `build/kardx/openapi-viewer/openapi.html` → `build/api-docs/openapi.html` | `apiDocsViewer` 태스크 |
| Postman Collection (켰을 때) | `build/api-spec/postman-collection.json` → `build/api-docs/` | `postman` 태스크 (epages) |
| IntelliJ HTTP Client (켰을 때) | `build/kardx/http-client/api.http`, `http-client.env.json` → `build/api-docs/http-client/` | `apiDocsHttpClient` 태스크 |
| 엔드포인트 목록 | `build/generated-snippets/.kardx/endpoints/<id>.json` (매핑 구성이 다를 때마다 한 파일. operation 디렉터리로 보지 않는다) | 테스트 (애플리케이션 컨텍스트마다 한 번) |
| 문서화 커버리지 보고서 | `build/kardx/coverage/coverage.json`, `coverage.md` → `build/api-docs/coverage/` | `apiDocsCoverage` 태스크 |
| OpenAPI 변경 보고서 | `build/reports/kardx/openapi-changes.txt` | `apiDocsCheck` 태스크 |
| WireMock 매핑 (켰을 때) | `build/generated-snippets/wiremock/mappings/<class-name>/<method-name>.json` | 테스트 (`andDocument`) |
| WireMock stub jar (켰을 때) | `build/libs/<name>-<version>-stubs.jar` | `apiDocsStubsJar` 태스크 |
| 여러 프로젝트 모음 | `build/api-docs-aggregate/` (모으는 프로젝트) | `apiDocsAggregate` 태스크 |
| 앱 리소스 | `build/resources/main/static/docs/` | `apiDocsAppDocs` 태스크 (`apiDocs` 뒤에 실행. IDE 실행·`bootRun` 이 여기서 서빙) |
| 문서 위치 (시작 로그용) | `build/resources/main/META-INF/kardx/docs-locations.properties` | `apiDocsAppDocs` 태스크. 웹으로 서빙되지 않는 위치 |
| 문서 기록 (index·운영 노출 경고용) | `build/generated-asciidoc/.restdocs-documents/<class-name>.properties` | 테스트 (클래스 문서를 쓸 때) |
| 실행 jar 안 | `BOOT-INF/classes/static/docs/` | `bootJar` 태스크 (위 리소스를 담는다) |

앱을 띄우면 [브라우저에서 보기](README.md#브라우저에서-보기)의 주소로 봅니다. 실행 jar, `bootRun`, IntelliJ 에서 `main` 실행 모두 같습니다. `bootRun` 과 IntelliJ 실행은 앱을 띄우기 전에 문서를 자동으로 만듭니다 ([실행할 때 자동 생성](#실행할-때-자동-생성)).

생성 파일(AsciiDoc, index, AsyncAPI yaml·html, OpenAPI 전역 헤더 설정 파일)은 임시 파일에 쓴 뒤 원자적으로 바꿔치기합니다. 여러 테스트 JVM 이 같은 파일을 써도 반쯤 쓰인 파일을 읽지 않습니다. 권한은 umask 를 따르는 일반 파일과 같습니다.

증분 빌드: 테스트를 다시 돌려도 스니펫과 생성 문서 내용이 같으면 `apiDocsIndex`, `asciidoctor`, `apiDocsAsyncApi` 는 UP-TO-DATE 로 건너뜁니다. 실행마다 바뀌는 값(시각, 무작위 ID 등)을 문서화하면 매번 다시 실행되므로 고정 예시값을 쓰세요 ([메시지 헤더 예](messaging.md#websocket--stomp)).

빌드 시간: 문서 생성 시간의 대부분은 이 플러그인이 아니라 HTML 변환(`asciidoctor`)이 씁니다. Boot 4 샘플에서 테스트와 문서를 모두 다시 돌리면 약 16초가 걸립니다.

- 테스트: 약 8초 (대부분 JVM·Spring 시작)
- `asciidoctor`: 약 7초 (AsciidoctorJ 가 JRuby 위에서 돎. 짧게 사는 변환 JVM 을 C1 컴파일러까지만 쓰게 하는 기본 `asciidoctorJvmArgs` 로 약 0.5초를 줄인 값)
- 이 플러그인의 태스크: 모두 합쳐 0.3초 미만

OpenAPI, 뷰어, Postman, `.http`, 커버리지 리포트는 HTML 변환이 필요 없으므로, 로컬 개발 중에는 `html { enabled = false }` 로 끌 수 있습니다. 기본값은 Gradle 속성 `kardx.html`(`true`/`false`, 다른 값이면 빌드 실패)이고, 없으면 `true` 입니다. 빌드 스크립트에서 정한 값이 속성보다 앞섭니다.

끄면 `apiDocs` 가 `asciidoctor` 와 `apiDocsIndex` 를 돌리지 않고, 결과와 앱 리소스에서 지난 실행의 HTML 을 지우며, 위치 목록에 `REST Docs HTML    skipped` 가 나옵니다. OpenAPI, AsyncAPI, 뷰어, Postman, `.http`, 커버리지, 앱 리소스 복사는 그대로이고, `./gradlew asciidoctor` 를 직접 부르면 꺼져 있어도 변환합니다.

```kotlin
kardx {
    html {
        enabled = providers.environmentVariable("CI").isPresent // CI 에서만 변환한다
    }
}
```

```properties
# ~/.gradle/gradle.properties (내 컴퓨터에서만. 한 번만: ./gradlew apiDocs -Pkardx.html=false)
kardx.html=false
```

`kardx.asciidoctorJvmArgs`(기본값 `["-XX:TieredStopAtLevel=1"]`)는 `asciidoctor` 가 변환에 띄우는 JVM 에 넘깁니다. 빈 목록(`asciidoctorJvmArgs = emptyList()`)이면 JVM 기본값을 씁니다. 문서가 아주 많아 변환이 C2 컴파일러가 이득일 만큼 오래 걸릴 때 끄고, 더하려면 `asciidoctorJvmArgs.add("-Xmx1g")` 처럼 씁니다. `asciidoctor` 를 프로세스 안(`IN_PROCESS`)에서 돌리면 쓰이지 않습니다. 다른 JVM 설정은 `tasks.asciidoctor { jvm { } }` 에 그대로 넣어도 됩니다.

위치는 [Gradle 플러그인](#gradle-플러그인) 설정 한 곳에서 바꿉니다. 플러그인이 같은 경로를 테스트, Asciidoctor, epages, AsyncAPI 에 모두 넘기므로 서로 어긋나지 않습니다.

```kotlin
kardx {
    snippetsDirectory = layout.buildDirectory.dir("docs/snippets")
    asciidocDirectory = layout.buildDirectory.dir("docs/asciidoc")
    outputDirectory = layout.buildDirectory.dir("docs/site")
    bootJar { path = "static/api-docs" }       // 앱에서 /api-docs/index.html
}
```

### 플러그인이 비우는 디렉터리

플러그인은 일부 디렉터리를 쓰기 전에 지웁니다: `snippetsDirectory`·`asciidocDirectory` 는 `test` 전에(`cleanBeforeTest`), `outputDirectory` 는 `apiDocs` 가 동기화할 때, `aggregate.outputDirectory` 는 `apiDocsAggregate` 전에. 어디든 가리킬 수 있는 설정이므로 먼저 확인합니다.

- 항상 거부: 프로젝트 디렉터리, 루트 프로젝트 디렉터리, 빌드 디렉터리와 그 상위, 소스 디렉터리(`src/`, `asciidocSourceDirectory`) 안이거나 그것을 품은 디렉터리.
- `build/` 안: 허용. 기본 위치는 모두 여기라 달라지는 것이 없습니다.
- `build/` 밖: 디렉터리가 없거나, 비었거나, 표시 파일 `.kardx-output` 이 있을 때만 허용. 쓴 뒤 플러그인이 그 빈 표시 파일을 남겨, 다음 실행이 자기 디렉터리로 알아봅니다. 표시 파일은 앱이나 집계 결과로 복사하지 않습니다.

예를 들어 GitHub Pages(Deploy from a branch)용으로 집계 결과를 저장소의 `docs/` 폴더에 쓰려면, 처음에는 빈 `docs/` 를 쓰거나, 폴더에 지워져도 되는 파일만 있다면 빈 `docs/.kardx-output` 을 직접 만듭니다. 다른 파일이 있고 표시 파일이 없는 폴더는 지우지 않고 빌드가 실패합니다.

```
kardx.aggregate.outputDirectory points to /work/repo/docs, which is outside the build directory and holds files the plugin did not write. ...
```

`openapi3` 은 플러그인이 쓴 스펙 파일만 지웁니다: `<prefix>.yaml|yml|json`, 버전 스펙(`<prefix>-v<버전>.*`, 버전이 숫자로 시작), `build/kardx/openapi-version-specs.txt` 에 적힌 파일.

### 앱 리소스에 넣는 문서

Spring Boot 앱이면 `apiDocsAppDocs` 가 문서를 `build/resources/main/<bootJar.path>` 로 복사하고, IDE 실행·`bootRun`·실행 jar 가 거기서 문서를 서빙합니다.

- `bootJar.path` 는 `static/docs`, `public/api-docs` 처럼 앱 리소스의 하위 디렉터리여야 합니다. `\` 는 `/` 로 읽고, `.` 마디·빈 마디·끝 `/` 는 뺍니다(`./static//docs/` → `static/docs`). 빈 값, 절대 경로, 드라이브 문자, `..` 는 설정 단계에서 실패하고(`kardx.bootJar.path must be a directory inside the app's resources such as 'static/docs' or 'public/api-docs', got '...'`), Spring 리소스 루트 자체(`static`, `public`, `resources`, `META-INF/resources`, `META-INF`)도 실패합니다. 거기에 넣으면 문서가 앱의 `index.html` 등과 섞이기 때문입니다.
- 앱이 같은 디렉터리에 둔 파일(예: `src/main/resources/static/docs/` 에서 온 파일)은 지우지 않습니다. 플러그인은 복사한 파일을 `build/kardx/app-docs-files.txt` 에 적어 두고, 다음 실행에서 그중 이제 만들어지지 않는 파일만 지웁니다. 문서와 이름이 같은 파일은 덮어씁니다.
- `bootJar.path` 를 바꾸면 같은 목록으로 이전 경로의 복사본을 지웁니다. `bootJar.enabled = false` 면 `apiDocsAppDocs` 가 앞선 복사본 전부와 시작 로그의 위치 파일을 지웁니다. 이때 `bootJar` 는 `apiDocsAppDocs` 를 돌리지 않으므로 운영 빌드는 [운영 노출](#운영-노출)을 보세요.

### 재현 가능한 스니펫

스니펫, WireMock 매핑, 스펙은 테스트를 돌릴 때마다 다시 씁니다. 내용이 같으면 뒤 태스크(`asciidoctor`, `apiDocsIndex`, `apiDocsAsyncApi` …)가 UP-TO-DATE 로 남고, 실행마다 바뀌는 값이 있으면 다시 돌고 커밋한 스펙의 리뷰에도 잡음이 생깁니다.

- **`Date` 헤더.** 실제 서버(`bindToServer`)는 매번 새 값을 보내므로 문서화한 응답에서 기본으로 뺍니다(`http.removedResponseHeaders`, [설정 목록](configuration.md#설정-목록)). `responseHeaders(...)` 로 문서화한 헤더는 남깁니다. `X-Request-Id` 처럼 실행마다 바뀌는 다른 헤더는 코드에서 더합니다(`http.removedResponseHeaders += "X-Request-Id"`). yml 키 `kardx.removed-response-headers` 는 기본 목록에 더하지 않고 목록 전체를 정하므로 `Date` 도 함께 적습니다(`Date, X-Request-Id`).
- **쿠키 `Expires`.** `Max-Age` 가 있는 `Set-Cookie` 값에서는 지금 시각으로 계산되는 `Expires` 속성을 뺍니다(브라우저도 어차피 `Max-Age` 를 우선합니다). 같은 설정을 따르므로 `removed-response-headers: none`(또는 `emptySet()`)이면 쿠키를 받은 그대로 둡니다.
- **Boot 3 의 Spring Data `Page` 응답.** Jackson 2 가 `PageImpl` 을 바로 직렬화하면 프로퍼티 순서가 정해져 있지 않아 `pageable`, `sort`, `totalElements` 같은 필드 순서가 실행마다 바뀔 수 있습니다(Boot 4 의 Jackson 3 는 이미 정렬합니다). 라이브러리로는 고칠 수 없으니 앱에서 하나를 고릅니다.
  - `spring.jackson.mapper.sort-properties-alphabetically=true` 는 모든 응답의 프로퍼티를 정렬합니다. JSON 경로는 그대로지만 모든 응답의 순서가 알파벳순으로 바뀝니다.
  - `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` 는 페이지를 `PagedModel`(`content`, `page.size`, `page.number`, `page.totalElements`, `page.totalPages`)로 직렬화합니다. 순서가 안정적이고 Spring Data 가 권하는 방식이지만 JSON 모양이 바뀌므로 `pageable.pageNumber`, `sort.sorted` 같은 문서화한 경로도 바뀝니다.
- 본문의 시각·랜덤 ID: 테스트에서 고정 값을 쓰거나 고정 예시값을 씁니다 ([메시지 헤더 예](messaging.md#websocket--stomp)).
