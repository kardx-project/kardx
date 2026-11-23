[English](../getting-started.md) | **한국어**

# 시작하기

[문서 목록](README.md)

<!-- toc -->
**목차**

- [설치](#설치)
  - [지원하는 빌드 구성](#지원하는-빌드-구성)
  - [플러그인 없이 쓸 때의 의존성](#플러그인-없이-쓸-때의-의존성)
- [프로젝트 만들기](#프로젝트-만들기)
  - [1. 디렉터리 구조](#1-디렉터리-구조)
  - [2. settings.gradle.kts](#2-settingsgradlekts)
  - [3. build.gradle.kts](#3-buildgradlekts)
  - [4. application.yml (운영)](#4-applicationyml-운영)
  - [5. application-docs.yml (테스트)](#5-application-docsyml-테스트)
  - [6. 직접 쓰는 문서](#6-직접-쓰는-문서)
  - [7. DTO 와 컨트롤러](#7-dto-와-컨트롤러)
  - [8. 문서화 테스트](#8-문서화-테스트)
  - [9. 준비 코드 나누기 (선택)](#9-준비-코드-나누기-선택)
  - [10. 실행](#10-실행)
  - [Boot 3 에서 다른 점](#boot-3-에서-다른-점)
- [@WebMvcTest 슬라이스 테스트](#webmvctest-슬라이스-테스트)
- [다음에 볼 것](#다음에-볼-것)
<!-- /toc -->

라이브러리를 설치하고, 새 Spring Boot 프로젝트에서 첫 API 문서를 만드는 데까지 안내합니다.

문서화 테스트는 Kotlin 으로 씁니다. DSL 이 중위 함수, reified 타입 인자, 수신 객체 지정 람다로 되어 있어 Java 에서는 쓸 만하게 부를 수 없습니다. 앱 코드와 DTO 는 Java 여도 됩니다: Java record·bean 과 Java 어노테이션도 읽습니다([추출 규칙](http-api.md#추출-규칙)).

## 설치

Gradle 플러그인 `io.github.kardx-project` 을 쓰면 라이브러리 의존성은 적지 않아도 됩니다. 플러그인이 프로젝트에 맞는 것을 넣습니다 ([자동으로 넣는 의존성](build.md#자동으로-넣는-의존성)).

```kotlin
plugins {
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"   // REST Docs 버전을 Spring Boot BOM 이 정한다
    id("org.asciidoctor.jvm.convert") version "4.0.5"       // REST Docs HTML
    id("com.epages.restdocs-api-spec") version "0.20.2"     // OpenAPI (선택. Boot 3: 0.19.4)
    id("io.github.kardx-project") version "0.1"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")      // 또는 -webflux
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Kotest 로 문서화하면 io.kotest 의존성만 적는다. 버전(6.x / 5.x)에 맞는 확장을 플러그인이 넣는다
}
```

| 플러그인이 넣는 것 | 언제 |
|---|---|
| `io.github.kardx-project:kardx-annotations` (`compileOnly`) | 항상. DTO·Enum 에 붙이는 `@Description`, `@Masked`, `@EnumCode`, `@AsciiDescription`. 운영 jar 에 들어가지 않습니다 |
| `io.github.kardx-project:kardx` (`testImplementation`) | 항상 (Kotest 확장을 넣으면 그것이 함께 가져옴) |
| `io.github.kardx-project:kardx-kotest` / `-kotest5` | 테스트 의존성에 `io.kotest` 6.x / 5.x 버전을 적었을 때 (버전을 적지 않았으면 넣지 않으므로 직접 선언). 이 문서의 8·9 절(JUnit) 대신 [Kotest](kotest.md) 를 봅니다 |
| `spring-restdocs-mockmvc` / `spring-restdocs-webtestclient` | MVC 앱 / WebFlux 앱. 버전은 Spring Boot BOM |
| `com.epages:restdocs-api-spec-mockmvc` / `-webtestclient` | epages 플러그인이 있을 때. 버전은 epages 플러그인과 같음 |
| `io.github.kardx-project:kardx-boot` (`developmentOnly`) | Spring Boot 앱. 앱이 뜰 때(IDE 실행·`bootRun`) 문서 주소를 로그로 남깁니다 |

직접 선언한 모듈은 건드리지 않으므로 버전을 바꾸려면 그 모듈만 적습니다. 자동 추가를 끄려면 `kardx { addDependencies = false }`, 시작 로그 모듈만 빼려면 `startupLog = false` 입니다.

Maven Central 에 `io.github.kardx-project` 로 배포되어 있습니다. Gradle 플러그인도 Maven Central 에 있으므로 `pluginManagement` 저장소에도 `mavenCentral()` 을 넣습니다.

### 지원하는 빌드 구성

Gradle 플러그인은 Gradle 전용입니다. 테스트가 만드는 것은 빌드 도구와 상관없이 됩니다. 테스트 다음 단계(index, OpenAPI 후처리, 뷰어, 보고서)는 Gradle 플러그인에, Maven 에서는 Maven 플러그인 `io.github.kardx-project:kardx-maven-plugin` 에 있습니다. 같은 단계를 명령줄 도구(`kardx-tooling`)로도 돌릴 수 있어서 어떤 빌드에서든 부를 수 있습니다. Maven 설정, 목표, CLI 는 [Maven 과 명령줄](maven.md)에 있습니다.

| 기능 | Gradle + 플러그인 (Kotlin·Groovy DSL) | 플러그인 없는 Gradle | Maven |
|---|---|---|---|
| 문서화 테스트: 스니펫, 테스트 클래스마다 AsciiDoc 문서 하나, 필드 추출과 검증 | 됨 | 됨 | 됨 (`build/` 대신 `target/`) |
| index 문서 | 됨 | `DocumentationIndexCli` ([index 문서](asciidoc.md#index-문서)) | 됨. `api-docs` 목표 |
| REST Docs HTML | 됨 (`org.asciidoctor.jvm.convert` 가 있을 때) | 직접 꾸린 Asciidoctor 설정 | `api-docs` 목표가 모은 소스로 `asciidoctor-maven-plugin` |
| AsyncAPI 스펙과 뷰어 | 됨 | `AsyncApiCli` ([SSE · WebSocket · AsyncAPI](messaging.md)) | 됨. `api-docs` 목표 |
| OpenAPI 스펙 | 됨. 다듬어짐: 타입, 제약, 기본값, `oneOf`, multipart, 쿠키, 보안, operationId | tooling CLI 로 같은 다듬어진 스펙. 아니면 epages `openapi3` 태스크가 만드는 것만 | 됨. 다듬어짐. `api-docs` 목표 |
| KDoc/Javadoc 설명, 이름만 적은 파라미터 (실험 기능, 기본 꺼짐) | 됨. `sourceDocs { enabled = true }` 일 때 | tooling CLI `source-docs` | `source-docs` 목표 |
| OpenAPI 뷰어, IntelliJ HTTP Client, Postman, 문서화 커버리지, 기준 스펙과 비교 검사 | 됨 | tooling CLI | 됨. `api-docs`·`check` 목표 |
| 내보내기·업로드, 여러 프로젝트 모으기 | 됨 | 안 됨 | 안 됨 |
| WireMock 매핑 | 됨. stub jar 도 | yml `kardx.wire-mock-stubs: true` (매핑만) | 플러그인 없는 Gradle 과 같음 |
| 앱의 `/docs/` 에서 문서 열기, 앱 시작 로그 | 됨 | 결과를 `static/docs` 에 직접 복사하고 `boot` 모듈을 직접 넣음. 시작 로그는 기본 파일 이름을 씀 | 플러그인 없는 Gradle 과 같음 (`maven-resources-plugin` 으로 복사) |

**Gradle 버전과 Kotlin DSL.** 플러그인은 Gradle 7.6 이상이 필요하고, Gradle 은 JDK 17 이상에서 돌아야 합니다. 플러그인을 Java 17 용으로 컴파일했으므로 더 낮은 JDK 의 Gradle 은 플러그인을 읽지 못합니다 (`UnsupportedClassVersionError ... class file version 61.0`). IntelliJ 는 Settings → Build Tools → Gradle → Gradle JVM 을, 명령줄은 `JAVA_HOME` 이나 `org.gradle.java.home` 을 확인합니다. 이 문서의 Kotlin DSL 예시는 플러그인 설정을 `=` 로 넣는데(`language = "ko"`, `enabled = true`), Kotlin DSL 이 이런 지연 속성에 `=` 를 받는 것은 Gradle 8.2 부터입니다. Gradle 7.6~8.1 에서는 `.set(...)` 을 부르거나, 지원하는 모든 버전에서 `=` 가 되는 Groovy DSL 을 씁니다. 쓰는 Spring Boot 버전이 더 새 Gradle 을 요구할 수도 있습니다 (Spring Boot 4 는 Gradle 8.14 이상).

```kotlin
// Gradle 7.6~8.1, build.gradle.kts
kardx {
    language.set("ko")
    openApiViewer { enabled.set(true) }
}
```

**Groovy DSL.** 이 문서의 `kardx { }` 블록은 `build.gradle` 에서도 똑같이 씁니다. 플러그인 선언과 문자열 문법만 다릅니다.

```groovy
// settings.gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```groovy
// build.gradle
plugins {
    id 'org.jetbrains.kotlin.jvm' version '2.4.21'
    id 'org.springframework.boot' version '4.1.2'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'org.asciidoctor.jvm.convert' version '4.0.5'
    id 'com.epages.restdocs-api-spec' version '0.20.2'
    id 'io.github.kardx-project' version '0.1'
}

kardx {
    language = 'ko'
    openApiViewer { enabled = true }
    openApiCommonResponses.put(401, '토큰이 없거나 만료됨')
}

openapi3 {
    title = 'Demo API'
}
```

### 플러그인 없이 쓸 때의 의존성

Maven 이나 플러그인 없는 Gradle, 또는 `addDependencies = false` 면 직접 선언합니다. `addDependencies = false` 는 의존성만 넣지 않고 문서 파이프라인은 그대로 구성합니다. Gradle 플러그인 없이 쓰면 문서 파이프라인도 직접 구성합니다. Maven 은 Maven 플러그인([Maven 과 명령줄](maven.md)), Gradle 은 [자동으로 넣는 의존성](build.md#자동으로-넣는-의존성) 아래의 CLI 와 tooling CLI 를 씁니다.

| 아티팩트 | 넣는 곳 | 내용 |
|---|---|---|
| `io.github.kardx-project:kardx-annotations` | 운영 코드 `compileOnly` (Maven `provided`) | `@Description` 등. 다른 어노테이션(Swagger `@Schema` 등)으로 설명을 쓰면 필요 없습니다 ([다른 어노테이션에서 설명 가져오기](http-api.md#다른-어노테이션에서-설명-가져오기)) |
| `io.github.kardx-project:kardx` | `testImplementation` | 라이브러리 본체. Spring REST Docs core·spring-test 와 annotations 를 가져옵니다. MockMvc·WebTestClient 모듈은 가져오지 않습니다 |
| `org.springframework.restdocs:spring-restdocs-mockmvc` | `testImplementation` | MockMvc 로 문서화할 때 (버전은 Spring Boot BOM) |
| `org.springframework.restdocs:spring-restdocs-webtestclient` | `testImplementation` | WebTestClient 로 문서화할 때 (WebFlux 앱) |
| `io.github.kardx-project:kardx-kotest` | `testImplementation` | Kotest 6.x 스펙으로 문서화할 때. 본체를 함께 가져옵니다 |
| `io.github.kardx-project:kardx-kotest5` | `testImplementation` | Kotest 5.x 면 위 대신 이것 (같은 패키지·API). 둘을 함께 넣지 않습니다 |
| `com.epages:restdocs-api-spec-mockmvc` / `-webtestclient` | `testImplementation` | OpenAPI 리소스 (Boot 3: 0.19.x, Boot 4: 0.20.x) |
| `io.github.kardx-project:kardx-boot` | `developmentOnly` | 앱 시작 시 문서 주소 로그 |
| `io.github.kardx-project:kardx-bom` | `platform(...)` | 위 `io.github.kardx-project` 아티팩트들의 버전을 맞춥니다 |

```kotlin
dependencies {
    val kardx = platform("io.github.kardx-project:kardx-bom:0.1")

    compileOnly(kardx)
    compileOnly("io.github.kardx-project:kardx-annotations")

    testImplementation(kardx)
    testImplementation("io.github.kardx-project:kardx")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

```xml
<dependency>
    <groupId>io.github.kardx-project</groupId>
    <artifactId>kardx-annotations</artifactId>
    <version>0.1</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>io.github.kardx-project</groupId>
    <artifactId>kardx</artifactId>
    <version>0.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.restdocs</groupId>
    <artifactId>spring-restdocs-mockmvc</artifactId>
    <scope>test</scope>
</dependency>
```

그 밖의 선택 의존성:

| 쓰려는 기능 | 추가할 의존성 |
|---|---|
| 제약조건 문서화 | `spring-boot-starter-validation` (운영 코드) |
| Boot 4 에서 Kotlin data class 직렬화 | `tools.jackson.module:jackson-module-kotlin` (Jackson 3 용) |

## 프로젝트 만들기

새 Spring Boot 4 프로젝트에 처음부터 넣는 순서입니다. 아래 파일을 그대로 만들면 테스트, REST Docs HTML, OpenAPI(Swagger UI) 가 나옵니다. WebSocket·SSE 메시지 문서(AsyncAPI)는 [메시지를 문서화](messaging.md)했을 때만 생깁니다. Boot 3 는 [Boot 3 에서 다른 점](#boot-3-에서-다른-점)을 참고하세요.

### 1. 디렉터리 구조

```
demo/
├── settings.gradle.kts
├── build.gradle.kts                                       Gradle 플러그인 io.github.kardx-project
└── src/
    ├── main/
    │   ├── kotlin/com/example/demo/DemoApplication.kt     DTO 에 @Description
    │   └── resources/application.yml                      Swagger UI 가 문서 스펙을 보게 설정
    ├── docs/asciidoc/                                     직접 쓰는 문서 (선택)
    │   ├── index-template.adoc                            index 머리말, API 목록 자리 표시 (없으면 내장 템플릿)
    │   └── _landing.adoc                                  모든 문서 머리에 들어갈 안내 (선택, _ 로 시작하면 단독 HTML 이 되지 않는다)
    └── test/
        ├── kotlin/com/example/demo/
        │   ├── ApiDocsTest.kt                             문서화 테스트 기본 클래스
        │   └── UserApiDocsTest.kt                         문서화 테스트
        └── resources/application-docs.yml                 라이브러리 설정 (kardx.*)
```

### 2. settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "demo"
```

### 3. build.gradle.kts

<!-- docs-pair: ignore-next-block -->
```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.5"          // REST Docs HTML
    id("com.epages.restdocs-api-spec") version "0.20.2"        // OpenAPI (선택)
    id("io.github.kardx-project") version "0.1"         // 문서 파이프라인 + 라이브러리 의존성
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }

kardx {
    language = "ko" // 라이브러리가 넣는 문구를 한국어로 (기본값 en)
}
```

이것으로 끝입니다. 플러그인이 넣는 것과 기본값은 다음과 같습니다.

- 의존성: annotations(`compileOnly`), 본체와 `spring-restdocs-mockmvc`·`restdocs-api-spec-mockmvc`(`testImplementation`), 시작 로그 모듈(`developmentOnly`). [자동으로 넣는 의존성](build.md#자동으로-넣는-의존성)
- index: 문서를 만든 모든 테스트 클래스를 넣습니다. `kardx { index { packageName; baseClass } }` 는 적지 않아도 됩니다. 6 절의 템플릿이 없으면 프로젝트 이름을 제목으로 하는 내장 템플릿을 씁니다(제목은 `kardx { index { title = "..." } }`). [index 문서](asciidoc.md#index-문서)
- 언어: 라이브러리가 문서에 넣는 문구(index 목록 제목, `메인으로 돌아가기` 링크, `제약:` 등)는 기본이 영어라, 이 가이드는 `language = "ko"` 로 한국어를 씁니다 ([문서 언어](configuration.md#문서-언어)).
- OpenAPI: `openapi3 { }` 를 적지 않으면 `openapi3.yaml`, 제목은 프로젝트 이름, 버전은 프로젝트 버전, 서버는 `/` 입니다. 바꿀 값만 적습니다.

```kotlin
openapi3 {
    title = "Demo API"      // 기본값: 프로젝트 이름
    version = "0.0.1"       // 기본값: 프로젝트 버전
}
```

플러그인이 해 주는 일과 바꿀 수 있는 값은 [Gradle 플러그인](build.md#gradle-플러그인)에 있습니다.

### 4. application.yml (운영)

Swagger UI 가 런타임 스캔 결과 대신 테스트로 검증한 스펙(`/docs/openapi3.yaml`)을 보여 주게 합니다. Swagger UI 를 쓰지 않으면 필요 없습니다.

```yaml
springdoc:
  swagger-ui:
    url: /docs/openapi3.yaml # 런타임 스캔 대신 테스트로 검증한 스펙을 보여 준다

server:
  mime-mappings:
    yaml: application/yaml
```

### 5. application-docs.yml (테스트)

라이브러리 설정입니다. 모든 속성은 [yml 설정](configuration.md#yml-설정)에 있습니다.

```yaml
kardx:
  common-include-files: _landing.adoc    # 모든 문서 머리에 넣을 파일
  mask-sensitive-headers: true           # Authorization, Cookie 등 값을 가린다 (기본은 가리지 않음)
```

### 6. 직접 쓰는 문서

둘 다 선택입니다. `src/docs/asciidoc/index-template.adoc` 이 있으면 `// restdocs:api-index` 줄 자리에 테스트 클래스별 문서 링크가 들어갑니다. 없으면 프로젝트 이름을 제목으로 하는 내장 템플릿에 목록만 들어갑니다.

```asciidoc
= Demo API
:doctype: book
:icons: font
:toc: left
:sectlinks:

* link:/swagger-ui/index.html[Swagger UI] · link:./openapi3.yaml[openapi3.yaml]
// WebSocket·STOMP·SSE 메시지를 문서화했다면: * link:./asyncapi.html[WebSocket·SSE 메시지]

== API 목록

// restdocs:api-index
```

`src/docs/asciidoc/_landing.adoc`. 모든 API 문서 머리에 들어갑니다.

```asciidoc
NOTE: 이 문서는 통과한 테스트의 실제 요청·응답으로 만들어졌습니다.
```

### 7. DTO 와 컨트롤러

```kotlin
package com.example.demo

import io.github.kardx.annotation.Description
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

data class CreateUserRequest(
    @field:NotBlank @Description("닉네임") val nickname: String,
    @Description("소개") val bio: String? = null,
)

data class UserResponse(
    @Description("사용자 ID") val id: Long,
    @Description("닉네임") val nickname: String,
)

@RestController
class UserController {
    @GetMapping("/api/users/{id}")
    fun get(@PathVariable id: Long) = UserResponse(id, "홍길동")

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateUserRequest) = UserResponse(1, request.nickname)
}
```

### 8. 문서화 테스트

아래는 JUnit 입니다. Kotest 스펙으로 쓰려면 [Kotest](kotest.md) 를 봅니다(스펙마다 확장을 등록). `@WebMvcTest` 슬라이스 테스트는 [@WebMvcTest 슬라이스 테스트](#webmvctest-슬라이스-테스트)를 봅니다.

```kotlin
package com.example.demo

import io.github.kardx.dsl.pathMeans
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.junit.AutoRestDocumentationExtension
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs") // src/test/resources/application-docs.yml
@DisplayName("사용자 API")
class UserApiDocsTest {

    @Test
    @DisplayName("사용자 조회")
    fun getUser(mockMvc: MockMvc) {
        mockMvc.perform(get("/api/users/{id}", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument {
                description = "ID로 사용자를 조회한다."
                pathParameters("id" pathMeans "사용자 ID")
                responseFields<UserResponse>()
            }
    }

    @Test
    @DisplayName("사용자 생성")
    fun createUser(mockMvc: MockMvc) {
        mockMvc.perform(post("/api/users").jsonContent(CreateUserRequest("홍길동")))
            .andStatus(HttpStatus.CREATED)
            .andDocument {
                description = "사용자를 만든다."
                requestFields<CreateUserRequest>()
                responseFields<UserResponse>()
            }
    }
}
```

`AutoRestDocumentationExtension` 은 `MockMvc` 파라미터를 받는 테스트 메서드(와 `@BeforeEach` 메서드)에 REST Docs 용 `MockMvc` 를 넣습니다. Spring 테스트 컨텍스트로 만든 `restDocsMockMvc(context, provider)` 와 같은 클라이언트입니다. WebFlux 앱은 같은 방식으로 `WebTestClient` 파라미터를 받습니다 ([WebTestClient](webtestclient.md)). `@Autowired`·`@Qualifier`·`@Value` 가 붙은 파라미터와 생성자 파라미터는 Spring 에 맡깁니다. 클래스에 Spring 테스트 컨텍스트(`@SpringBootTest`, `@WebMvcTest`, `@WebFluxTest`)가 있어야 하고, 없으면 [KDX-014](troubleshooting.md#kdx-014) 로 실패합니다.

문서를 만든 테스트 클래스는 모두 index 에 들어가므로 기본 클래스가 필요 없습니다.

MockMvc Kotlin DSL 을 쓴다면 같은 블록을 `mockMvc.get("/api/users/{id}", 1L).andExpect { status { isOk() } }.andDocument { ... }` 처럼 이어 씁니다. `MockMvcTester` 결과(`exchange()`)에도 `andDocument { }` 가 있습니다 ([andDocument](http-api.md#anddocument)). 필드가 응답과 맞지 않으면 테스트가 실패하면서 빠진 필드와 붙여 넣을 DSL 줄을 알려 줍니다 ([검증 실패 메시지](http-api.md#검증-실패-메시지)).

Spring Boot 가 넣는 `MockMvc`(`@AutoConfigureMockMvc`·`@WebMvcTest` 와 `@Autowired`, `@AutoConfigureRestDocs`)로 문서화하지 않습니다. 이 라이브러리의 REST Docs 설정이 없어 [KDX-012](troubleshooting.md#kdx-012) 로 실패하고, `@AutoConfigureRestDocs` 면 스니펫이 다른 디렉터리에 생겨 섹션이 조용히 빠지므로 [KDX-011](troubleshooting.md#kdx-011) 로 실패합니다(디렉터리가 우연히 같으면 한 번 경고만 합니다).

### 9. 준비 코드 나누기 (선택)

여러 테스트 클래스가 어노테이션이나 도우미를 같이 쓰면 기본 클래스로 묶습니다. `@BeforeEach` 를 쓰는 예전 방식도 그대로 됩니다.

```kotlin
package com.example.demo

import io.github.kardx.junit.AutoRestDocumentationExtension
import io.github.kardx.mockmvc.restDocsMockMvc
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.context.WebApplicationContext

/** 문서화 테스트가 같이 쓰는 준비 코드. */
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
abstract class ApiDocsTest {
    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpRestDocs(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        mockMvc = restDocsMockMvc(context, provider)
    }
}

class UserApiDocsTest : ApiDocsTest() {
    @Test
    fun getUser() {
        mockMvc.perform(get("/api/users/{id}", 1L)) // ...
    }
}
```

클라이언트를 필드에 두거나 Kotest 스펙 본문에서 만들려면 provider 없는 `restDocsMockMvc(context)`(`restDocsWebTestClient(context)`)를 씁니다. 요청마다 그때 실행 중인 테스트로 문서화합니다. 문서화 중인 테스트 밖에서 요청하면 [KDX-013](troubleshooting.md#kdx-013) 으로 실패합니다.

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
class UserApiDocsTest(@Autowired context: WebApplicationContext) {
    private val mockMvc = restDocsMockMvc(context) // 그때 실행 중인 테스트로 문서화한다
}
```

### 10. 실행

```bash
./gradlew test        # 스니펫과 AsciiDoc 만 만든다
./gradlew apiDocs     # + index, HTML, OpenAPI (메시지가 있으면 AsyncAPI). 끝나면 생성 위치를 출력한다
./gradlew bootRun     # 앱 실행. 그 전에 apiDocs 를 자동으로 돌린다 (IntelliJ 에서 main 실행도 같다)
./gradlew bootJar     # + 위 결과를 실행 jar 의 /docs 에 넣는다
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

IDE 나 `bootRun` 으로 앱이 뜨면 문서 주소가 로그에 찍힙니다 ([앱 시작 로그](build.md#앱-시작-로그)). 실행 jar(`java -jar`)는 `kardx { startupLogScope = "runtimeOnly" }` 일 때만 찍습니다. 줄 머리와 이름은 `language = "ko"` 를 따르고, 아래 `apiDocs` 의 생성 위치 출력은 언제나 영어입니다.

```
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API 문서 (REST Docs   ) http://localhost:8080/docs/index.html
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API 문서 (Swagger UI  ) http://localhost:8080/swagger-ui/index.html
INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API 문서 (OpenAPI 스펙) http://localhost:8080/docs/openapi3.yaml
```

`apiDocs` 가 끝나면 다음처럼 생성 위치를 보여 줍니다. `file:///` 주소는 터미널이나 IDE 에서 바로 열립니다.

```
Generated API docs
  REST Docs HTML    file:///.../demo/build/api-docs/index.html
  In the app        http://localhost:8080/docs/index.html (IDE run, bootRun, bootJar)
  OpenAPI (Swagger) /.../demo/build/api-docs/openapi3.yaml
  Coverage          file:///.../demo/build/api-docs/coverage/coverage.md
  AsciiDoc          /.../demo/build/generated-asciidoc
  Snippets          /.../demo/build/generated-snippets
```

`Coverage` 줄은 epages 플러그인이 있어서 나옵니다 ([문서화 커버리지](checks.md#문서화-커버리지)). epages 플러그인이 없으면 `Coverage` 줄이 없고, OpenAPI 줄이 켜는 방법을 알려 줍니다: `OpenAPI (Swagger) off (apply id("com.epages.restdocs-api-spec") version "0.20.2" to enable OpenAPI, coverage and the viewer)` (Boot 3 은 `0.19.4`). WebSocket·SSE 메시지를 문서화했다면 `WebSocket·SSE     file:///.../asyncapi.html` 줄이 더 나오고, 켠 산출물(OpenAPI 뷰어, Postman, HTTP Client)도 한 줄씩 더 나옵니다.

상황별로 어떤 명령을 실행하는지(CI, 기준 비교, 스펙 넘기기, `clean`)와 태스크가 도는 순서는 [상황별 명령과 순서](build.md#상황별-명령과-순서)에 있습니다.

### Boot 3 에서 다른 점

| 항목 | Boot 4 | Boot 3 |
|---|---|---|
| Spring Boot 플러그인 | `4.1.2` | `3.5.16` |
| epages 플러그인 (`restdocs-api-spec-mockmvc` 는 플러그인이 같은 버전으로 넣는다) | `0.20.2` | `0.19.4` |
| Jackson Kotlin 모듈 | `tools.jackson.module:jackson-module-kotlin` | `com.fasterxml.jackson.module:jackson-module-kotlin` |
| springdoc | `springdoc-openapi-starter-webmvc-ui:3.2.0` | `springdoc-openapi-starter-webmvc-ui:2.8.17` |

Asciidoctor 확장(`spring-restdocs-asciidoctor`)과 AsciidoctorJ 버전은 플러그인이 Boot 버전에 맞춰 고릅니다.

## @WebMvcTest 슬라이스 테스트

웹 계층만 띄우는 `@WebMvcTest` 슬라이스 테스트에서도 문서를 만들 수 있습니다. 테스트할 컨트롤러와, 그 컨트롤러가 쓰는 것의 목만 올라옵니다. MockMvc 는 위 문서화 테스트와 똑같이 테스트 메서드 파라미터로 받습니다.

```kotlin
@ExtendWith(AutoRestDocumentationExtension::class)
@WebMvcTest(UserController::class)
@ActiveProfiles("docs")
@DisplayName("사용자 API")
class UserApiSliceDocsTest {

    @Test
    @DisplayName("사용자 조회")
    fun getUser(mockMvc: MockMvc) {
        mockMvc.perform(get("/api/users/{id}", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument {
                pathParameters("id" pathMeans "사용자 ID")
                responseFields<UserResponse>()
            }
    }
}
```

- `@WebMvcTest` 의 위치는 Boot 버전마다 다릅니다. Boot 4 는 `spring-boot-webmvc-test` 모듈의 `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` 이므로 `testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")` 를 더합니다. Boot 3 는 `spring-boot-starter-test` 에 이미 있는 `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` 입니다. Gradle 플러그인은 어느 쪽도 넣지 않습니다.
- 슬라이스 컨텍스트도 `WebApplicationContext` 이므로 `MockMvc` 파라미터가 `@SpringBootTest` 와 똑같이 동작합니다. 컨텍스트의 Jackson 매퍼를 쓰고, `AutoRestDocumentationExtension` 이 테스트 설정의 `kardx.*` 를 읽습니다(`@ActiveProfiles("docs")` 면 `application-docs.yml`). 핸들러를 알 수 있으므로 operationId·파라미터 타입도 평소처럼 컨트롤러에서 정해집니다.
- `@WebMvcTest` 가 만드는 `MockMvc` 는 `@AutoConfigureRestDocs` 를 붙이든 아니든 `@Autowired` 로 받지 않습니다. 이 라이브러리의 REST Docs 설정이 없고([KDX-012](troubleshooting.md#kdx-012)), `@AutoConfigureRestDocs` 면 Spring Boot 가 스니펫 디렉터리를 다르게 정해 섹션이 빠집니다 ([KDX-011](troubleshooting.md#kdx-011), [기존 Spring REST Docs 에서 옮기기](migration.md#2-테스트-기본-클래스)).
- 컨트롤러가 쓰는 빈은 `@MockitoBean`(Spring Framework 6.2 이상)이나 springmockk 의 `@MockkBean` 으로 목을 만듭니다.
- 클래스패스에 Spring Security 가 있으면 슬라이스에 포함되어 `springSecurityFilterChain` 빈이 생깁니다. `restDocsMockMvc` 는 `kardx.apply-spring-security: true` 가 아니면 걸지 않고, 값을 어느 쪽으로든 정할 때까지 한 번 경고합니다 ([Spring Security](http-api.md#spring-security)).
- WebSocket·STOMP 문서화는 서버가 떠 있어야 하므로 그 테스트는 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 로 둡니다.

이 저장소의 샘플은 `@SpringBootTest` 를 쓰고 슬라이스 테스트는 없습니다. 프로젝트에서 다르게 동작하면(예: 컨트롤러가 기대는 필터나 `WebMvcConfigurer` 가 슬라이스에 없을 때) `@Import` 로 더하거나 `@SpringBootTest` 로 돌아갑니다.

## 다음에 볼 것

- 문서화 문법과 자동 추출: [HTTP API 문서화](http-api.md)
- Kotest 스펙으로 문서화: [Kotest](kotest.md)
- WebFlux 앱(WebTestClient)과 RestTestClient: [WebTestClient · RestTestClient](webtestclient.md)
- yml 설정과 마스킹: [설정](configuration.md)
- 결과물 위치와 플러그인 옵션: [빌드와 산출물](build.md)
- OpenAPI·Swagger UI 와 스펙 검사: [OpenAPI 와 Swagger UI](openapi.md), [스펙 검사와 커버리지](checks.md)
- 표의 열·소제목·문서 틀 바꾸기: [스니펫과 문서 모양 바꾸기](customizing-snippets.md)

선택 산출물과 기능(OpenAPI 뷰어, Postman, IntelliJ HTTP Client, WireMock stub, 문서화 커버리지, 변경 비교, 여러 프로젝트 모으기, Enum 코드 문서 등)과 각각을 켜는 법은 README 에 모아 두었습니다: [만들어지는 결과물](../../README.ko.md#만들어지는-결과물), [선택 기능](../../README.ko.md#선택-기능).
