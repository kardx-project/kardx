[English](README.md) | **한국어**

# kardx

[![CI](https://github.com/kardx-project/kardx/actions/workflows/ci.yml/badge.svg)](https://github.com/kardx-project/kardx/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kardx-project/kardx?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.kardx-project/kardx)
[![Docs](https://img.shields.io/badge/%EB%AC%B8%EC%84%9C-kardx--project.github.io-7c4dff)](https://kardx-project.github.io/kardx/ko/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![JDK](https://img.shields.io/badge/JDK-17%2B-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2%2B-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5%20%7C%204.x-6DB33F?logo=springboot&logoColor=white)

*Kotlin Auto Rest Docs eXtension* — [Spring REST Docs](https://docs.spring.io/spring-restdocs/reference/) 를 Kotlin 으로 쓰기 쉽게 만든 라이브러리입니다.

**문서 사이트: [kardx-project.github.io/kardx/ko](https://kardx-project.github.io/kardx/ko/)** ([English](https://kardx-project.github.io/kardx/))

DTO 에 `@Description` 을 붙이면 필드 문서를 타입에서 만들고, 테스트가 끝나면 AsciiDoc 문서를 조립합니다. 같은 테스트로 OpenAPI(Swagger UI)와 AsyncAPI(WebSocket·STOMP·SSE) 스펙도 만듭니다.

```kotlin
mockMvc
    .perform(get("/api/users/{id}", 1L))
    .andStatus(HttpStatus.OK)
    .andDocument {
        description = "ID로 사용자를 조회한다."
        pathParameters("id" pathMeans "사용자 ID")
        responseFields<UserResponse>()
    }
```

## 만들어지는 결과물

`./gradlew apiDocs` 가 `build/api-docs/` 에 모으고, 앱을 띄우면(실행 jar, `bootRun`, IntelliJ 실행) `/docs/` 아래에서 봅니다. IDE 실행과 `bootRun` 은 앱이 뜰 때 있는 문서의 주소도 로그로 보여 줍니다 (실행 jar 는 `startupLogScope = "runtimeOnly"` 일 때만).

| 결과물 | 무엇인가 | 기본 | 켜는 설정 | 앱 주소 | 자세히 |
|---|---|---|---|---|---|
| REST Docs HTML | 테스트 클래스마다 한 문서(요청·응답 예시, 필드 표)와 전체 목록 index | 켜짐 | `org.asciidoctor.jvm.convert` 플러그인 | `/docs/index.html` | [AsciiDoc 문서](docs/ko/asciidoc.md) |
| OpenAPI 스펙 | 테스트로 검증한 `openapi3.yaml` | 꺼짐 | `com.epages.restdocs-api-spec` 플러그인 (테스트 의존성은 이 라이브러리의 Gradle 플러그인이 넣는다) | `/docs/openapi3.yaml` | [OpenAPI](docs/ko/openapi.md#openapi--swagger-ui) |
| Swagger UI | 위 스펙을 보고 직접 호출해 보는 화면 | 꺼짐 | 앱에 springdoc 의존성과 `springdoc.swagger-ui.url: /docs/openapi3.yaml` | `/swagger-ui/index.html` | [시작하기](docs/ko/getting-started.md#4-applicationyml-운영) |
| OpenAPI 정적 뷰어 | Redoc·Scalar 로 만든 HTML 파일 하나. 서버 없이 열리고 파일째 전달할 수 있다 | 꺼짐 | `kardx { openApiViewer { enabled = true } }` | `/docs/openapi.html` | [OpenAPI 뷰어](docs/ko/outputs.md#openapi-뷰어-redoc--scalar) |
| AsyncAPI 문서·뷰어 | WebSocket·STOMP·SSE 메시지 스펙과 Try it out 뷰어 | 메시지를 문서화하면 만든다 | `MessageDocumentation`, `events(...)` | `/docs/asyncapi.html` | [SSE · WebSocket · AsyncAPI](docs/ko/messaging.md) |
| Enum 코드 문서 | `@EnumCode` Enum 의 코드·설명 표 | 꺼짐 | `EnumDocumentationExtension` 등록 | `/docs/code/enum-code.html` | [Enum 코드 문서](docs/ko/asciidoc.md#enum-코드-문서) |
| Postman Collection | 문서화한 요청을 Postman 에서 바로 보내는 컬렉션 | 꺼짐 | `postmanCollection { enabled = true }` | `/docs/postman-collection.json` | [Postman](docs/ko/outputs.md#postman-collection) |
| IntelliJ HTTP Client | 문서화한 요청을 담아 IntelliJ 편집기에서 보내는 `.http` 파일 | 꺼짐 (OpenAPI 필요) | `httpClient { enabled = true }` | `/docs/http-client/api.http` | [IntelliJ HTTP Client](docs/ko/outputs.md#intellij-http-client) |
| 문서화 커버리지 | 앱이 매핑했지만 어떤 테스트도 문서화하지 않은 엔드포인트 | 켜짐 (OpenAPI 필요) | `coverage { }` | 없음 (`build/api-docs/coverage/`) | [문서화 커버리지](docs/ko/checks.md#문서화-커버리지) |
| WireMock stub | 문서화한 응답을 흉내 내는 WireMock 매핑과 stub jar | 꺼짐 | `wireMock { enabled = true }` | 없음 (`build/generated-snippets/wiremock/`) | [WireMock stub](docs/ko/outputs.md#wiremock-stub) |

`openApiViewer`·`postmanCollection`·`httpClient`·`coverage`·`wireMock` 은 Gradle 플러그인 설정 `kardx { }` 안에 씁니다. 파일 위치 전체는 [생성 위치](docs/ko/build.md#생성-위치)에 있습니다.

## 선택 기능

기본으로 꺼져 있거나 필요할 때만 쓰는 기능입니다.

- **OpenAPI 변경 비교** (`./gradlew apiDocsCheck`): 커밋해 둔 기준 스펙과 새 스펙을 비교해 하위 호환을 깨는 변경을 알립니다. CI 의 `check` 에 넣을 수 있습니다 ([OpenAPI 변경 비교](docs/ko/checks.md#openapi-변경-비교)).
- **문서화하지 않은 엔드포인트로 실패** (`coverage { failOnUndocumented = true }`): `apiDocsCoverage` 는 문서화 테스트가 없는 엔드포인트를 늘 알려 주고, 이 설정을 켜면 빌드를 실패시킵니다. CI 에서만 켤 수도 있습니다 ([문서화 커버리지](docs/ko/checks.md#문서화-커버리지)).
- **OAuth2·OpenID Connect** (`openApi { oauth2("users:read") }` 와 빌드의 `openApiSecurity { }`): operation 이 scope 를 요구하고, Swagger UI 에 OAuth2 로그인이 나옵니다 ([OAuth2 와 OpenID Connect](docs/ko/http-api.md#oauth2-와-openid-connect)).
- **API 버전** (Spring Framework 7, `apiVersioning { }`): 핸들러 버전이 `x-api-version` 과 버전 파라미터가 되고, 스펙 하나 또는 버전마다 스펙 하나로 나갑니다 ([API 버전](docs/ko/openapi.md#api-버전)).
- **스펙 내보내기·업로드** (`./gradlew apiDocsExport`, `apiDocsUpload`): 만든 스펙을 디렉터리로 복사하거나 API 레지스트리에 HTTP 로 올리고, `export { publishSpecs = true }` 이면 `maven-publish` 배포물에 `<name>-<version>-openapi.yaml` 로 붙입니다 ([스펙 내보내기](docs/ko/outputs.md#스펙-내보내기)).
- **선언한 오류 응답** (블록의 `errorResponse(409, "...")`): 400·401·403·409 같은 오류를 오류마다 테스트를 쓰지 않고 문서화합니다. 그 테스트의 문서(`Error Responses` 표, 필드·예시)와 OpenAPI operation 에 들어갑니다. 선언일 뿐 검증하지 않습니다 ([선언한 오류 응답](docs/ko/http-api.md#선언한-오류-응답)).
- **공통 오류 응답** (`openApiCommonResponses`): 401·500 처럼 테스트하지 않는 공통 응답을 한 번 선언하면 OpenAPI 의 각 operation 과 index 표에 들어갑니다. 테스트로 검증하지 않은 내용이라 기본으로 꺼져 있습니다 ([공통 오류 응답](docs/ko/http-api.md#공통-오류-응답)).
- **여러 프로젝트 모으기** (`./gradlew apiDocsAggregate`): 서브프로젝트들의 문서를 목록 페이지 하나와 병합 OpenAPI 로 모읍니다 ([여러 프로젝트 모으기](docs/ko/outputs.md#여러-프로젝트-모으기)).
- **OpenAPI 정보와 태그** (`openApiInfo { }`): 설명(또는 Markdown 파일), 연락처, 라이선스, 이용 약관, 외부 문서, 태그 순서와 설명, Redoc·Scalar 용 `x-tagGroups` ([OpenAPI 정보와 태그](docs/ko/openapi.md#openapi-정보와-태그)).
- **로컬에서 HTML 변환 건너뛰기** (`html { enabled = false }` 또는 `-Pkardx.html=false`): 문서 빌드 시간의 대부분인 `asciidoctor` 를 건너뜁니다. OpenAPI 와 다른 결과는 그대로 만듭니다 ([빌드와 결과물](docs/ko/build.md#생성-위치)).
- **OpenAPI 정적 뷰어**, **Postman Collection**, **IntelliJ HTTP Client**, **WireMock stub**: 위 표.
- **WebFlux·WebTestClient, RestTestClient**: 같은 `andDocument { }` 를 WebTestClient 에, Boot 4 에서는 RestTestClient 에도 붙입니다 ([WebTestClient · RestTestClient](docs/ko/webtestclient.md)).
- **AsyncAPI 인증** (`asyncApi { bearerAuth() }`, `basicAuth()`, `apiKeyHeader(...)`): 스펙에 서버의 인증 방식이 들어가고, 뷰어의 Try it out 이 자격 증명을 보냅니다 ([AsyncAPI 인증](docs/ko/messaging.md#asyncapi-인증)).
- **Kotest 6·5**: `given`/`when`/`then` 스펙으로 문서화합니다. Kotest 5.x 는 `-kotest5` 아티팩트 ([Kotest](docs/ko/kotest.md)).
- **스니펫·문서 모양 바꾸기**: 표의 열, 소제목, 목차 위치, 직접 만든 스니펫 ([스니펫과 문서 모양 바꾸기](docs/ko/customizing-snippets.md)).

## 특징

- **Kotlin DSL**: `andDocument { }` 블록과 `"id" pathMeans "사용자 ID"` 같은 중위 함수로 씁니다. `mockMvc.perform`, MockMvc Kotlin DSL(`mockMvc.get { }`), `MockMvcTester`, WebTestClient(WebFlux 앱), RestTestClient(Boot 4) 모두에서 같은 블록을 씁니다 ([WebTestClient · RestTestClient](docs/ko/webtestclient.md)). 수식어는 복사본을 돌려주므로 같이 쓰는 항목을 `val` 상수로 두어도 됩니다.
- **오류 코드**: 라이브러리의 오류·경고는 `kardx[KDX-011]` 같은 고정 코드로 시작하고 [트러블슈팅](docs/ko/troubleshooting.md#오류-코드)의 해결 방법으로 이어집니다.
- **원인을 알려 주는 실패**: 문서와 실제 응답이 다르면 빠진 필드, 바로 붙여 넣을 DSL 줄, 빈 배열·이름 전략·래퍼 같은 원인을 메시지로 보여 줍니다. `describe()` 로 추출 결과를 테스트 없이 미리 봅니다.
- **타입에서 필드 추출**: `responseFields<Page<User>>()`(Spring Data `Page`, Spring HATEOAS HAL 모델, kotlinx `@Serializable` 클래스 포함, 목록이 필요하면 `responseFieldsOf<T>()`)가 경로, 타입, 필수 여부, 설명, 제약조건을 만듭니다. Jackson 이름 규칙을 따르고, REST Docs 가 실제 응답으로 다시 검증합니다. 설명은 `@Description` 외에 Swagger `@Schema` 같은 기존 어노테이션(yml `description-annotations`)과, Gradle 플러그인의 `sourceDocs { enabled = true }`(실험 기능, 직접 켬)로 KDoc·Javadoc 에서도 읽습니다. `methodDocs = true` 를 더하면 컨트롤러 메서드 주석도 operation 의 OpenAPI summary 와 설명(OpenAPI·HTML)이 되고, 메서드의 `@param` 태그는 테스트가 `pathParameters("id")` 처럼 이름만 적은 파라미터의 설명이 됩니다 ([KDoc/Javadoc 에서 설명 가져오기](docs/ko/http-api.md#kdocjavadoc-에서-설명-가져오기)).
- **마스킹되는 필드 표시**: `@Masked("가운데 4자리를 *")` 나 회사의 마스킹 어노테이션(yml `masked-annotations`)이 붙은 필드는 문서 표에 `Masked`, OpenAPI·AsyncAPI 에 `x-masked` 로 표시됩니다. 값을 바꾸지 않는 문서 표시입니다 ([마스킹되는 필드 표시](docs/ko/http-api.md#마스킹되는-필드-표시)).
- **JUnit 과 Kotest**: JUnit 5·6 테스트 클래스는 `AutoRestDocumentationExtension`, Kotest 스펙(`given`/`when`/`then`, `describe`/`it` 등)은 `RestDocsExtension` 으로 문서화합니다(Kotest 6.x 는 `kardx-kotest`, 5.x 는 `kardx-kotest5`). 둘을 한 프로젝트에 섞어 써도 문서가 index·OpenAPI 에 함께 들어갑니다 ([Kotest](docs/ko/kotest.md)).
- **문서 자동 조립**: 테스트 클래스 하나가 문서 하나가 되고, 여러 문서를 index 로 묶습니다. `@EnumCode("상품 상태")` 를 붙인 Enum 은 코드 / 설명 표 문서로 만듭니다.
- **SSE · WebSocket · STOMP**: 실제로 주고받은 메시지로 문서와 AsyncAPI 스펙을 만듭니다. `StompTestClient` 가 테스트에서 연결·구독·전송·프레임 대기를 맡습니다 ([StompTestClient](docs/ko/messaging.md#stomptestclient-로-주고받기)).
- **읽기 좋은 operationId**: OpenAPI operationId 는 기본으로 컨트롤러 메서드 이름(`getUser`)이라 테스트 이름이 한글이어도 코드 생성기가 그대로 씁니다. `openApi { operationId = "..." }` 나 규칙 설정(`open-api.operation-id-rule`)으로 바꿉니다 ([operationId](docs/ko/http-api.md#operationid)).
- **정확한 OpenAPI**: 추출한 타입과 Bean Validation 제약이 `integer`/`int64`, `date-time`, `minLength`, `maximum` 같은 스키마 키워드로, `withDefaultValue`·Kotlin 기본값·`example` 이 `default`·`example` 로, sealed·`@JsonSubTypes` 타입이 `oneOf`(`@JsonTypeInfo` 가 하위 타입 이름을 정하면 discriminator 포함)로, multipart 파트와 쿠키가 요청 본문·`in: cookie` 로 나갑니다 ([OpenAPI 스펙에 나가는 정보](docs/ko/http-api.md#openapi-스펙에-나가는-정보)).
- **Gradle 플러그인과 yml 설정**: 플러그인 하나가 라이브러리 의존성 추가, HTML·OpenAPI·AsyncAPI 생성, 실행 jar 포함까지 구성합니다. 설정은 `application.yml` 에서 바꿉니다.
- **실제 앱과 같은 요청 경로**: `restDocsMockMvc` 는 앱의 Jackson 매퍼를 그대로 씁니다. Spring Security 필터 체인은 `apply-spring-security: true` 로 켜면 겁니다 (인터셉터 등 다른 방식으로 인증하는 프로젝트가 있어 기본은 걸지 않음).
- **선택적 마스킹**: 원하면 토큰·쿠키 같은 헤더 값을 가립니다. 기본은 가리지 않습니다.
- **영어·한국어 문서 문구**: 라이브러리가 문서에 넣는 문구(목록 제목, index 링크, 제약 접두사, 뷰어 화면 등)는 기본이 영어이고, `kardx { language = "ko" }` 한 줄로 한국어가 됩니다 ([문서 언어](docs/ko/configuration.md#문서-언어)).

## 호환성

| Spring Boot | Spring REST Docs | Jackson | epages (OpenAPI, 선택) | 테스트 클라이언트 |
|---|---|---|---|---|
| 3.5.x | 3.0.x | 2.x | 0.19.x | MockMvc, WebTestClient |
| 4.0.x, 4.1.x | 4.0.x | 3.x | 0.20.x | MockMvc, WebTestClient, RestTestClient |

| 도구 | 지원 | 이 저장소가 빌드·검증하는 버전 |
|---|---|---|
| JDK | 17 이상 | 17 (라이브러리·플러그인 바이트코드 17) |
| Kotlin | 2.2 이상 | 2.4.21 로 컴파일, 메타데이터·stdlib 는 2.2.21 |
| Gradle | 7.6 이상 (미만이면 플러그인이 바로 실패). Kotlin DSL 예시는 플러그인 설정에 `=` 를 쓰는데 이는 Gradle 8.2 부터 됨. 7.6~8.1 에서는 `.set(...)` 이나 Groovy DSL. 쓰는 Spring Boot 버전이 더 요구할 수 있음 (Spring Boot 4: 8.14 이상) | 래퍼 8.14.8. 플러그인 핵심 시나리오와 스펙 내보내기·업로드(`apiDocsExport`, `apiDocsUpload`)를 7.6.4·9.0.0·9.8.2 에서도 돌림 |
| Maven | Maven 플러그인 `io.github.kardx-project:kardx-maven-plugin` 으로 ([Maven 과 명령줄](docs/ko/maven.md)) | 3.9.11 (`samples/maven-boot-4` 의 Maven Wrapper) |
| Asciidoctor Gradle 플러그인 (`org.asciidoctor.jvm.convert`) | 4.0.x | 4.0.5 |
| epages (`com.epages.restdocs-api-spec`) | Boot 3 → 0.19.x, Boot 4 → 0.20.x (어긋나면 플러그인이 경고) | 0.19.4, 0.20.2 |
| Kotest | 6.x → `kardx-kotest`, 5.x → `-kotest5` (플러그인이 선언한 Kotest 버전으로 고른다) | 6.2.7, 5.9.1 |
| JUnit | 5·6 | 5.14.4 |

문서화 테스트는 Kotlin 으로 씁니다. DSL 이 중위 함수, reified 타입 인자, 수신 객체 지정 람다로 되어 있어 Java 에서는 쓸 만하게 부를 수 없습니다. 앱 코드와 DTO 는 Java 여도 됩니다: Java record·bean 의 프로퍼티와 Java 어노테이션(`@Description`, Bean Validation, nullability 등)을 읽습니다([추출 규칙](docs/ko/http-api.md#추출-규칙)).

MockMvc, WebTestClient, 또는 (Boot 4 전용) RestTestClient 테스트가 필요합니다 ([RestTestClient](docs/ko/webtestclient.md#resttestclient)). 아티팩트는 Boot 버전과 상관없이 하나이고, REST Docs 버전은 프로젝트의 Spring Boot BOM 이 정합니다.

Gradle 플러그인은 Kotlin DSL 과 Groovy DSL 모두에서 씁니다. Maven 은 따로 Maven 플러그인이 있고(index, 다듬어진 OpenAPI, 뷰어, 커버리지, `.http`, Postman, 스펙 검사), 다른 빌드에서는 같은 단계를 명령줄 도구로 돌립니다([Maven 과 명령줄](docs/ko/maven.md)). 몇 가지(WireMock stub jar, 스펙 내보내기·업로드, 모으기)는 Gradle 에서만 됩니다. 구성마다 되는 것은 [지원하는 빌드 구성](docs/ko/getting-started.md#지원하는-빌드-구성)에 있습니다.

## 빠른 시작

Maven Central 에 `io.github.kardx-project` 로 배포되어 있습니다. Gradle 플러그인도 Maven Central 에 있으므로 `pluginManagement` 저장소에도 `mavenCentral()` 을 넣습니다.

`settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

`build.gradle.kts`

<!-- docs-pair: ignore-next-block -->
```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("com.epages.restdocs-api-spec") version "0.20.2"      // OpenAPI 를 만들 때만
    id("io.github.kardx-project") version "0.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }

kardx {
    language = "ko" // 라이브러리가 넣는 문구를 한국어로 (기본값 en)
}
```

라이브러리(annotations·본체, Kotest 를 쓰면 그 판의 확장), REST Docs MockMvc·WebTestClient 모듈, epages 모듈(`com.epages.restdocs-api-spec` 이 있을 때), 앱 시작 로그 모듈은 플러그인이 프로젝트에 맞춰 넣습니다. index·OpenAPI 설정도 기본값으로 동작합니다 ([자동으로 넣는 의존성](docs/ko/build.md#자동으로-넣는-의존성), 플러그인 없이 쓸 때는 [시작하기](docs/ko/getting-started.md#플러그인-없이-쓸-때의-의존성)).

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@DisplayName("사용자 API")
class UserApiDocsTest {

    @Test
    @DisplayName("사용자 조회")
    fun getUser(mockMvc: MockMvc) { // 확장이 REST Docs 용 MockMvc 를 넣는다
        mockMvc
            .perform(get("/api/users/{id}", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument { responseFields<UserResponse>() }
    }
}
```

실행:

```bash
./gradlew apiDocs     # 테스트를 돌리고 build/api-docs/ 를 만든다 (열어 볼 file:/// 링크를 출력)
./gradlew bootRun     # 문서를 만든 뒤 앱을 띄운다: http://localhost:8080/docs/index.html
./gradlew bootJar     # 문서를 담은 실행 jar. /docs/ 에서 열린다
```

CI, 기준 비교, 스펙 내보내기, `clean` 에 어떤 명령을 쓰는지는 [상황별 명령과 순서](docs/ko/build.md#상황별-명령과-순서)에, 바로 쓸 수 있는 GitHub Actions·GitLab CI 설정은 [CI 적용](docs/ko/ci.md)에 있습니다.

`./gradlew apiDocs` 를 실행하면 `build/api-docs/` 에 HTML(`index.html` 이 문서를 만든 모든 테스트 클래스를 잇는다), `openapi3.yaml` (메시지를 문서화했다면 `asyncapi.html` 도) 이 생기고 위치가 출력됩니다. `./gradlew bootRun` 이나 IntelliJ 실행은 그 전에 문서를 자동으로 만들고, 앱이 뜨면 문서 주소를 로그로 보여 줍니다. 디렉터리 구조, yml, index 템플릿까지 포함한 전체 과정은 [시작하기](docs/ko/getting-started.md)에 있습니다. 실행 jar 에 들어간 문서는 운영 앱에서도 열리므로 [운영 노출](docs/ko/build.md#운영-노출)을 확인하세요.

## 문서

아래 문서는 검색과 영어·한국어 전환이 되는 웹사이트로도 볼 수 있습니다: **[kardx-project.github.io/kardx/ko](https://kardx-project.github.io/kardx/ko/)**

| 하려는 것 | 볼 문서 |
|---|---|
| 처음 설치하고 첫 문서 만들기 | [시작하기](docs/ko/getting-started.md) |
| 요청·응답 문서화, 필드 자동 추출, 응답 래퍼, 오류 응답 | [HTTP API 문서화](docs/ko/http-api.md) |
| WebFlux 앱(WebTestClient)·RestTestClient 로 문서화 | [WebTestClient · RestTestClient](docs/ko/webtestclient.md) |
| Kotest 스펙으로 문서화 | [Kotest](docs/ko/kotest.md) |
| SSE·WebSocket·STOMP 와 AsyncAPI | [SSE · WebSocket · AsyncAPI](docs/ko/messaging.md) |
| yml·코드 설정, 헤더 마스킹 | [설정](docs/ko/configuration.md) |
| Gradle 플러그인, 태스크, 결과물 위치, 앱 시작 로그, 운영 노출 | [빌드와 산출물](docs/ko/build.md) |
| OpenAPI·Swagger UI, info 와 태그, API 버전 | [OpenAPI 와 Swagger UI](docs/ko/openapi.md) |
| 스펙 변경 검사(`apiDocsCheck`)와 문서화 커버리지 | [스펙 검사와 커버리지](docs/ko/checks.md) |
| 정적 뷰어, Postman, IntelliJ HTTP Client, WireMock, 내보내기·업로드, 여러 프로젝트 모으기 | [뷰어·클라이언트·stub·공유](docs/ko/outputs.md) |
| Maven 플러그인과 명령줄 도구 | [Maven 과 명령줄](docs/ko/maven.md) |
| CI 에서 검사 돌리기, PR 댓글, 문서 배포 | [CI 적용](docs/ko/ci.md) |
| 생성 문서·index·Enum 코드 문서 | [AsciiDoc 문서](docs/ko/asciidoc.md) |
| 표의 열·소제목·문서 틀 바꾸기, 직접 만든 스니펫 | [스니펫과 문서 모양 바꾸기](docs/ko/customizing-snippets.md) |
| 기존 `document(...)` 테스트에서 옮기기 | [기존 Spring REST Docs 에서 옮기기](docs/ko/migration.md) |
| 오류 해결 | [트러블슈팅](docs/ko/troubleshooting.md) |
| AI 도우미(Claude Code·Copilot·Cursor)에게 테스트 맡기기: `llms.txt` 와 바로 쓰는 스킬 | [AI 도우미](docs/ko/ai.md) |
| 모듈 구성과 넣을 아티팩트 | [모듈 구성](docs/ko/architecture.md) |

전체 목록은 [docs/ko/README.md](docs/ko/README.md) 에 있습니다.

## 샘플

[samples/spring-boot-3](samples/spring-boot-3) 과 [samples/spring-boot-4](samples/spring-boot-4) 에 같은 샘플 앱이 있습니다. HTTP, 쿠키, multipart, HAL, SSE, STOMP, WebSocket 핸들러, 응답 래퍼, yml 설정, Gradle 플러그인을 모두 쓰고, JUnit 테스트 사이에 Kotest 스펙 하나(`UserSearchDocsSpec`)가 함께 문서를 만듭니다. 메모 API(`memo` 패키지, `MemoApiDocsTest`)는 모든 설명을 KDoc 에서만 가져옵니다. Boot 4 샘플의 `src/test/.../kotest/` 에는 JUnit 문서화 테스트를 Kotest `FunSpec` 으로 그대로 옮긴 스펙도 있습니다. 같은 API 를 두 번 싣지 않도록 이 스펙들의 문서는 `build/generated-asciidoc-kotest` 에만 쓰고 앱 문서·OpenAPI·AsyncAPI 에는 넣지 않습니다.

```bash
./gradlew :samples:spring-boot-4:bootJar
java -jar samples/spring-boot-4/build/libs/spring-boot-4-0.1.jar
# http://localhost:8080/docs/index.html, /swagger-ui/index.html, /docs/async/index.html (AsyncAPI. 샘플이 fileName = "async/index")
```

샘플의 메시지 API 는 데모 토큰 `demo-token` 하나만 받습니다. STOMP 는 CONNECT 헤더에 `Authorization: Bearer demo-token` 을 보냅니다. `/ws/echo` 와 SSE 엔드포인트(`/api/v1/notifications/stream`, WebFlux 샘플의 `/api/v1/product-events`)는 `?access_token=demo-token` 이나 같은 `Authorization` 헤더를 받습니다. AsyncAPI 뷰어의 Try it out 에서는 인증 상자에 토큰을 넣고 "WebSocket·SSE 는 쿼리 파라미터로 보내기" 옵션을 켭니다. 브라우저는 WebSocket 핸드셰이크와 `EventSource` 에 헤더를 붙일 수 없기 때문입니다.

WebFlux 앱(WebTestClient)은 [samples/spring-boot-4-webflux](samples/spring-boot-4-webflux) 에 따로 있습니다. `suspend`·`Flow`·`Mono` 핸들러와 끝나는 SSE 를 문서화합니다.

```bash
./gradlew :samples:spring-boot-4-webflux:bootJar
java -jar samples/spring-boot-4-webflux/build/libs/spring-boot-4-webflux-0.1.jar
# http://localhost:8080/docs/index.html, /webjars/swagger-ui/index.html, /docs/asyncapi.html
```

Maven 프로젝트(Java 앱, Kotlin 테스트, Maven 플러그인)는 [samples/maven-boot-4](samples/maven-boot-4) 에 있습니다. 먼저 라이브러리를 로컬 Maven 저장소에 설치합니다.

```bash
./gradlew publishToMavenLocal
cd samples/maven-boot-4 && ./mvnw verify
# target/api-docs/ (index.html, openapi3.yaml, openapi.html, coverage/, http-client/)
```

## 개발과 라이선스

저장소 구조와 검증 방법은 [개발](docs/ko/development.md), 변경 내역은 [CHANGELOG](CHANGELOG.md) 에 있습니다. 이슈 제보와 기여 방법은 [기여 안내](CONTRIBUTING.ko.md)([English](CONTRIBUTING.md))에 있습니다. 라이선스는 Apache License 2.0 입니다.
