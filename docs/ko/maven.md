[English](../maven.md) | **한국어**

# Maven 과 명령줄

[문서 목록](README.md)

<!-- toc -->
**목차**

- [얻는 것](#얻는-것)
- [설정](#설정)
  - [의존성](#의존성)
  - [빌드 플러그인](#빌드-플러그인)
- [목표와 파라미터](#목표와-파라미터)
  - [api-docs](#api-docs)
  - [check](#check)
  - [baseline](#baseline)
  - [source-docs](#source-docs)
- [JSON 설정 파일](#json-설정-파일)
- [스펙 검사](#스펙-검사)
- [KDoc 설명](#kdoc-설명)
- [Maven 플러그인 없이](#maven-플러그인-없이)
  - [exec-maven-plugin](#exec-maven-plugin)
  - [java -jar](#java--jar)
  - [명령과 옵션](#명령과-옵션)
- [Gradle 에서만 되는 것](#gradle-에서만-되는-것)
- [트러블슈팅](#트러블슈팅)
  - [The OpenAPI generator is not on the classpath](#the-openapi-generator-is-not-on-the-classpath)
  - [No snippets directory](#no-snippets-directory)
  - [HTML 에 index 가 없거나 스니펫이 빠짐](#html-에-index-가-없거나-스니펫이-빠짐)
<!-- /toc -->

문서화 테스트는 빌드 도구와 상관없이 됩니다. 테스트 다음 단계(index, OpenAPI 다듬기, 뷰어, 커버리지, `.http`, Postman, 스펙 검사)는 Gradle 에서는 Gradle 플러그인이, Maven 에서는 Maven 플러그인 `io.github.kardx-project:kardx-maven-plugin` 이, 그 밖의 빌드에서는 명령줄 도구가 맡습니다. 셋 다 같은 코드(`io.github.kardx-project:kardx-tooling` 아티팩트)를 돌리므로 만드는 파일도 같습니다.

라이브러리는 아직 Maven Central 에 없습니다. 저장소를 받아 먼저 `./gradlew publishToMavenLocal` 을 실행합니다. Maven 은 따로 설정하지 않아도 `~/.m2/repository` 를 읽습니다. 동작하는 프로젝트는 [samples/maven-boot-4](../../samples/maven-boot-4) 에 있습니다(`./mvnw verify`).

## 얻는 것

| 기능 | Gradle 플러그인 | Maven 플러그인 | CLI (`java -jar`, `exec-maven-plugin`) |
|---|---|---|---|
| index 문서와 Asciidoctor 용 AsciiDoc 소스 | 됨 | `api-docs` 목표 | 안 됨 (core `DocumentationIndexCli`, [index 문서](asciidoc.md#index-문서)) |
| REST Docs HTML | `org.asciidoctor.jvm.convert` 가 있을 때 | `asciidoctor-maven-plugin` (직접 설정, 아래) | 직접 꾸린 Asciidoctor 설정 |
| AsyncAPI 스펙과 뷰어 | 됨 | `api-docs` 목표. 테스트가 `*.asyncapi.json` 을 남겼을 때 | 안 됨 (core `AsyncApiCli`, [SSE · WebSocket · AsyncAPI](messaging.md)) |
| OpenAPI 스펙, 다듬어짐 (타입, 제약, 기본값, `oneOf`, multipart, 쿠키, 보안, operationId) | 됨 | `api-docs` 목표 | `openapi` |
| OpenAPI 뷰어, IntelliJ HTTP Client, Postman | 됨 | `api-docs` 목표. 켰을 때 | `viewer`, `http`, `postman` |
| 문서화 커버리지 | 됨 | `api-docs` 목표 | `coverage` |
| 기준 스펙과 비교 검사 | `apiDocsCheck`, `apiDocsBaseline` | `check`, `baseline` 목표 | `check`, `baseline` |
| KDoc/Javadoc 설명 (실험 기능) | `sourceDocs { enabled = true }` | `source-docs` 목표 | `source-docs` |
| 앱의 `/docs/` 에서 문서 열기 | 됨 | `maven-resources-plugin` (아래) | 직접 구성 |
| WireMock stub jar, 스펙 내보내기·업로드, 모으기, 실행할 때 자동 생성 | 됨 | 안 됨 ([Gradle 에서만 되는 것](#gradle-에서만-되는-것)) | 안 됨 |

결과 디렉터리 구성은 Gradle 의 `build/api-docs/` 와 같고 위치만 `target/api-docs/` 입니다: `openapi3.yaml`(API 버전을 쓰면 버전별 스펙도), `openapi.html`, `coverage/coverage.md`·`coverage.json`, 환경 파일과 함께 `http-client/api.http`, `postman-collection.json`, `asyncapi.yaml`·`asyncapi.html`, 그리고 asciidoctor-maven-plugin 이 여기에 쓰는 HTML.

## 설정

### 의존성

Maven 에는 자동으로 넣는 의존성이 없으므로 라이브러리, REST Docs 모듈, epages 모듈을 직접 적습니다. Kotest·시작 로그 모듈까지 담은 전체 표는 [플러그인 없이 쓸 때의 의존성](getting-started.md#플러그인-없이-쓸-때의-의존성)에 있습니다. 문서화 테스트는 Kotlin 이므로 앱이 Java 여도 테스트용 `kotlin-stdlib`·`kotlin-reflect` 와 kotlin-maven-plugin(아래)이 필요합니다.

```xml
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
<dependency>
    <groupId>com.epages</groupId>
    <artifactId>restdocs-api-spec-mockmvc</artifactId>
    <version>0.20.2</version>
    <scope>test</scope>
</dependency>
```

테스트 쪽 epages `restdocs-api-spec-mockmvc`(또는 `-webtestclient`)는 Boot 3 이면 0.19.x, Boot 4 면 0.20.x 를 씁니다. Maven 플러그인은 테스트가 쓴 `resource.json` 만 읽으므로 Boot 버전과 상관없이 epages 생성기 0.20.2 를 담고 있습니다.

### 빌드 플러그인

샘플의 `<build><plugins>` 부분입니다. 이 라이브러리의 플러그인을 asciidoctor-maven-plugin **위에** 적습니다. 둘 다 `prepare-package` 에서 돌고, Maven 은 같은 단계의 플러그인을 적은 순서대로 돌립니다.

```xml
<build>
    <plugins>
        <!-- Kotlin 테스트: src/test/kotlin 을 컴파일한다 (앱은 Java 로 둬도 된다) -->
        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <version>${kotlin.version}</version>
            <executions>
                <execution>
                    <id>test-compile</id>
                    <phase>test-compile</phase>
                    <goals>
                        <goal>test-compile</goal>
                    </goals>
                    <configuration>
                        <sourceDirs>
                            <sourceDir>${project.basedir}/src/test/kotlin</sourceDir>
                        </sourceDirs>
                    </configuration>
                </execution>
            </executions>
            <configuration>
                <jvmTarget>${java.version}</jvmTarget>
            </configuration>
        </plugin>

        <!-- 테스트 뒤: index, OpenAPI, 뷰어, 커버리지 (prepare-package), 기준 스펙 검사 (verify) -->
        <plugin>
            <groupId>io.github.kardx-project</groupId>
            <artifactId>kardx-maven-plugin</artifactId>
            <version>0.1</version>
            <executions>
                <execution>
                    <goals>
                        <goal>api-docs</goal>
                        <goal>check</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <title>Books API</title>
                <openApiViewer>true</openApiViewer>
                <httpClient>true</httpClient>
                <!-- POM 파라미터가 없는 설정(OpenAPI 정보, 보안, API 버전)은 JSON 파일로 -->
                <configFile>${project.basedir}/restdocs-tooling.json</configFile>
            </configuration>
        </plugin>

        <!-- REST Docs HTML: api-docs 가 모은 AsciiDoc 소스를 변환한다 -->
        <plugin>
            <groupId>org.asciidoctor</groupId>
            <artifactId>asciidoctor-maven-plugin</artifactId>
            <version>3.2.0</version>
            <executions>
                <execution>
                    <id>generate-docs</id>
                    <phase>prepare-package</phase>
                    <goals>
                        <goal>process-asciidoc</goal>
                    </goals>
                    <configuration>
                        <sourceDirectory>${project.build.directory}/kardx/asciidoc-sources</sourceDirectory>
                        <outputDirectory>${project.build.directory}/api-docs</outputDirectory>
                        <preserveDirectories>true</preserveDirectories>
                        <backend>html5</backend>
                        <attributes>
                            <snippets>${project.build.directory}/generated-snippets</snippets>
                        </attributes>
                    </configuration>
                </execution>
            </executions>
            <dependencies>
                <dependency>
                    <groupId>org.springframework.restdocs</groupId>
                    <artifactId>spring-restdocs-asciidoctor</artifactId>
                    <version>${spring-restdocs.version}</version>
                </dependency>
            </dependencies>
        </plugin>

        <!-- 앱이 /docs/ 에서 문서를 내놓도록 jar 에 넣는다 -->
        <plugin>
            <artifactId>maven-resources-plugin</artifactId>
            <executions>
                <execution>
                    <id>copy-api-docs</id>
                    <phase>prepare-package</phase>
                    <goals>
                        <goal>copy-resources</goal>
                    </goals>
                    <configuration>
                        <outputDirectory>${project.build.outputDirectory}/static/docs</outputDirectory>
                        <resources>
                            <resource>
                                <directory>${project.build.directory}/api-docs</directory>
                            </resource>
                        </resources>
                    </configuration>
                </execution>
            </executions>
        </plugin>

        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

- `api-docs` 는 손으로 쓴 문서(`src/docs/asciidoc`, `index-template.adoc` 제외), 생성 문서(`target/generated-asciidoc`), 새 `index.adoc` 을 `target/kardx/asciidoc-sources` 에 모읍니다. 위처럼 asciidoctor-maven-plugin 의 `sourceDirectory` 를 여기로 둡니다.
- `snippets` 속성과 `spring-restdocs-asciidoctor` 의존성(`${spring-restdocs.version}` 은 Spring Boot parent 가 정함)은 Spring REST Docs 의 `operation::`·`include::{snippets}` 에 필요합니다.
- maven-resources-plugin 이 앞의 둘 다음에 `target/api-docs` 를 `static/docs` 로 복사하므로, Gradle 플러그인처럼 `java -jar` 로 띄운 앱이 `/docs/index.html`, `/docs/openapi3.yaml` 등을 내놓습니다.

```bash
./mvnw verify
# target/api-docs/index.html, openapi3.yaml, openapi.html, coverage/coverage.md, http-client/api.http
```

## 목표와 파라미터

목표 접두사는 `kardx` 입니다. 우선순위는 POM 파라미터 > JSON 파일(`configFile`) > 기본값입니다. OpenAPI 제목·버전은 둘 다 정하지 않으면 `${project.name}`·`${project.version}` 입니다. `-Drestdocs.skip=true` 면 모든 목표를 건너뜁니다.

### api-docs

기본 단계 `prepare-package`(테스트 클래스패스 필요). 순서대로: core 의 `DocumentationIndexCli` 를 테스트 클래스패스로 돌려 `index.adoc` 을 만들고, Asciidoctor 입력을 모으고, 테스트가 `*.asyncapi.json` 을 남겼으면 core 의 `AsyncApiCli` 를 돌린 뒤, 설정에서 켠 대로 OpenAPI 스펙, 뷰어, 커버리지, `.http`, Postman 을 만듭니다.

| 파라미터 | 기본값 | 정하는 것 |
|---|---|---|
| `snippetsDirectory` | `${project.build.directory}/generated-snippets` | 테스트가 쓴 스니펫 |
| `outputDirectory` | `${project.build.directory}/api-docs` | 문서를 둘 곳 |
| `workDirectory` | `${project.build.directory}/kardx` | 중간 파일 |
| `configFile` (속성 `restdocs.configFile`) | 없음 | [JSON 설정 파일](#json-설정-파일) |
| `language` (속성 `restdocs.language`) | `en` | 생성 문구의 언어 (`en`, `ko`) |
| `format` | `yaml` | OpenAPI 파일 형식 (`yaml`, `json`) |
| `title`, `version`, `description` | `${project.name}`, `${project.version}`, 없음 | OpenAPI info |
| `servers` (목록) | `/` | OpenAPI servers |
| `globalHeaders` (목록) | 없음 | `apiKey` 보안 스킴으로 바꿀 헤더. Gradle 의 `openApiGlobalHeaders` 와 같음 |
| `commonResponses` (맵, 상태 → 문구) | 없음 | [공통 오류 응답](http-api.md#공통-오류-응답) |
| `commonResponseSchema` | 없음 | 공통 응답의 스키마 이름 |
| `openApi` | `true` | OpenAPI 스펙 만들기 (뷰어, `.http`, Postman 이 이것을 씀) |
| `openApiViewer`, `openApiViewerType` | `false`, `redoc` | [OpenAPI 뷰어](outputs.md#openapi-뷰어-redoc--scalar) (`redoc`, `scalar`) |
| `coverage`, `coverageExcludes` (목록), `failOnUndocumented` | `true`, 없음, `false` | [문서화 커버리지](checks.md#문서화-커버리지) |
| `httpClient` | `false` | [IntelliJ HTTP Client](outputs.md#intellij-http-client) 파일 |
| `postman` | `false` | [Postman Collection](outputs.md#postman-collection) |
| `asciidocDirectory` | `${project.build.directory}/generated-asciidoc` | 테스트가 만든 문서 |
| `asciidocSourceDirectory` | `${project.basedir}/src/docs/asciidoc` | 손으로 쓴 문서 |
| `asciidocSourcesDirectory` | `${project.build.directory}/kardx/asciidoc-sources` | 모은 Asciidoctor 입력 |
| `index`, `indexTemplate`, `indexTitle` | `true`, `${project.basedir}/src/docs/asciidoc/index-template.adoc`, `${project.name}` | index 문서. 템플릿 파일이 없으면 내장 템플릿을 씀 |
| `asyncApi`, `asyncApiFileName` | `true`, `asyncapi` | AsyncAPI 스펙과 뷰어 (`asyncapi.yaml`, `asyncapi.html`) |

### check

기본 단계 `verify`. 새 스펙을 커밋한 기준 스펙과 비교하고 보고서를 `target/kardx/openapi-changes.txt` 에 씁니다([스펙 검사](#스펙-검사)).

| 파라미터 | 기본값 | 정하는 것 |
|---|---|---|
| `baseline` (속성 `restdocs.baseline`) | `${project.basedir}/docs/openapi/openapi3.yaml` | 기준 스펙 |
| `failOn` (속성 `restdocs.failOn`) | `any` | `any`(모든 변경), `breaking`(하위 호환을 깨는 변경만), `none`(보고만) |
| `requireBaseline` | `true` | 기준 파일이 없으면 실패 |

### baseline

단계에 묶이지 않고 직접 부릅니다. 만든 스펙을 `baseline` 으로 복사합니다(파라미터와 속성은 `check` 와 같음).

### source-docs

기본 단계 `process-classes`, 실험 기능. [KDoc 설명](#kdoc-설명)을 봅니다.

## JSON 설정 파일

POM 파라미터가 없는 설정(OpenAPI info·태그, OAuth2, API 버전 등)은 JSON 파일에 적습니다. Maven 플러그인(`<configFile>`)과 CLI(`--config`)가 같은 모양을 읽습니다. 모든 키는 생략할 수 있고, 아래 값은 기본값이거나 예시입니다. 모르는 키는 그 경로(예: `openApi.info.contct`)와 함께 실패하므로 오타가 조용히 넘어가지 않습니다. 상대 경로는 프로젝트 디렉터리(Maven)나 `--base-dir`(CLI) 기준입니다.

```json
{
  "snippets": "target/generated-snippets", "output": "target/api-docs", "work": "target/kardx",
  "language": "en",
  "openApi": {
    "enabled": true, "title": "API documentation", "version": "1.0.0", "description": null,
    "servers": ["/"], "format": "yaml", "fileNamePrefix": "openapi3",
    "globalHeaders": [], "commonResponses": { "401": "Token missing or expired" }, "commonResponseSchema": null,
    "security": { "oauth2": { "flows": { "clientCredentials": { "tokenUrl": "https://auth.example.com/oauth/token" } },
                              "scopes": { "payments:write": "Approve payments" } },
                  "openIdConnectUrl": null },
    "apiVersioning": { "style": "header", "name": "X-API-Version", "splitSpecs": false },
    "info": { "description": null, "descriptionFile": null, "termsOfService": null,
              "contact": { "name": "", "url": "", "email": "" }, "license": { "name": "", "url": "" },
              "externalDocs": { "url": "", "description": "" }, "tagOrder": [],
              "tags": { "Orders": { "description": "", "externalDocs": { "url": "", "description": "" } } },
              "tagGroups": { "Shop": ["Orders"] } },
    "sourceDocs": "target/kardx/source-docs/source-docs.json"
  },
  "viewer": { "enabled": false, "type": "redoc", "title": null, "fileName": "openapi.html" },
  "coverage": { "enabled": true, "excludes": [], "failOnUndocumented": false, "docsPath": "docs" },
  "httpClient": { "enabled": false, "fileName": "api.http" },
  "postman": { "enabled": false, "title": null, "baseUrl": "http://localhost" },
  "check": { "baseline": "docs/openapi/openapi3.yaml", "failOn": "any", "requireBaseline": true, "report": "target/kardx/openapi-changes.txt" }
}
```

키는 Gradle `kardx { }` 설정과 하나씩 대응하므로, 각 키가 하는 일은 Gradle 문서에 있습니다.

| JSON 키 | Gradle 설정 | 자세히 |
|---|---|---|
| `openApi.title`, `version`, `description`, `servers`, `format` | epages `openapi3 { }` | [OpenAPI / Swagger UI](openapi.md#openapi--swagger-ui) |
| `openApi.globalHeaders` | `openApiGlobalHeaders` | [Gradle 플러그인](build.md#gradle-플러그인) |
| `openApi.commonResponses`, `commonResponseSchema` | `openApiCommonResponses`, `openApiCommonResponseSchema` | [공통 오류 응답](http-api.md#공통-오류-응답) |
| `openApi.security` | `openApiSecurity { }` (`oauth2 { }` 포함) | [OAuth2 와 OpenID Connect](http-api.md#oauth2-와-openid-connect) |
| `openApi.apiVersioning` | `apiVersioning { }` | [API 버전](openapi.md#api-버전) |
| `openApi.info` | `openApiInfo { }` | [OpenAPI 정보와 태그](openapi.md#openapi-정보와-태그) |
| `viewer` | `openApiViewer { }` | [OpenAPI 뷰어](outputs.md#openapi-뷰어-redoc--scalar) |
| `coverage` | `coverage { }` | [문서화 커버리지](checks.md#문서화-커버리지) |
| `httpClient` | `httpClient { }` | [IntelliJ HTTP Client](outputs.md#intellij-http-client) |
| `postman` | `postmanCollection { }` | [Postman Collection](outputs.md#postman-collection) |
| `check` | `openApiCheck { }` | [OpenAPI 변경 비교](checks.md#openapi-변경-비교) |

- `info.tags` 의 순서가 태그 순서가 됩니다. `tagOrder` 에 적은 이름이 먼저 옵니다.
- `postman.title` 의 기본값은 OpenAPI 제목입니다.
- CLI 의 OpenAPI 제목·버전 기본값은 `API documentation`·`1.0.0`(epages 기본값)이고, Maven 플러그인은 대신 프로젝트 이름·버전을 씁니다.
- Maven 에서는 `snippets`, `output`, `work` 를 항상 POM 파라미터(기본값이 있음)에서 가져오므로 이 셋은 POM 에서 정합니다.

샘플의 `restdocs-tooling.json` 은 POM 으로 정할 수 없는 것만 적습니다:

```json
{
  "openApi": {
    "description": "HTTP API spec extracted from the passing documentation tests. Full docs: [REST Docs](/docs/index.html).",
    "info": {
      "contact": { "name": "Books team", "email": "books@example.com" },
      "license": { "name": "Apache 2.0", "url": "https://www.apache.org/licenses/LICENSE-2.0" },
      "tags": { "Books": { "description": "Look up and add books" } }
    }
  }
}
```

## 스펙 검사

`check` 목표는 `target/api-docs/openapi3.yaml` 을 커밋한 기준 스펙(`docs/openapi/openapi3.yaml`)과 비교해 변경을 출력하고 `failOn` 에 따라 실패합니다. 규칙은 Gradle 의 `apiDocsCheck` 와 같습니다([OpenAPI 변경 비교](checks.md#openapi-변경-비교)). 기준 스펙이 없거나 오래됐으면 실패 메시지가 `baseline` 목표를 실행하라고 알려 줍니다:

```bash
./mvnw package io.github.kardx-project:kardx-maven-plugin:baseline
git add docs/openapi/openapi3.yaml
```

로컬 빌드에서 보고만 하려면 `-Drestdocs.failOn=none`, 기준 스펙 없이 시작하려면 `<requireBaseline>false</requireBaseline>` 를 씁니다.

## KDoc 설명

`source-docs` 목표(Gradle `sourceDocs { }` 처럼 실험 기능)는 main 소스의 KDoc·Javadoc 을 색인으로 모으고, 테스트가 이것으로 필드·파라미터 설명을 채웁니다([KDoc/Javadoc 에서 설명 가져오기](http-api.md#kdocjavadoc-에서-설명-가져오기)). 목표를 추가하고 surefire 로 파일 경로를 테스트에 넘깁니다. 이 시스템 속성이 없으면 테스트는 색인을 읽지 않습니다.

```xml
<plugin>
    <groupId>io.github.kardx-project</groupId>
    <artifactId>kardx-maven-plugin</artifactId>
    <version>0.1</version>
    <executions>
        <execution>
            <goals>
                <goal>source-docs</goal>
                <goal>api-docs</goal>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <kardx.source-docs>${project.build.directory}/kardx/source-docs/source-docs.json</kardx.source-docs>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

| 파라미터 | 기본값 | 정하는 것 |
|---|---|---|
| `sourceDirectories` (목록) | 프로젝트의 compile source root 와 `src/main/kotlin` | 읽을 디렉터리 (`*.kt`, `*.java`) |
| `sourceDocsFile` | `${project.build.directory}/kardx/source-docs/source-docs.json` | 색인 파일 |
| `methodDocs` | `false` | 컨트롤러 메서드 주석을 operation 요약·설명으로 씀 |

## Maven 플러그인 없이

tooling 아티팩트에는 명령줄 도구 `io.github.kardx.tooling.cli.ApiDocsCli` 가 있습니다. `api-docs`·`check`·`baseline`·`source-docs` 목표가 하는 일을 하지만 index, AsciiDoc 소스, AsyncAPI 는 만들지 않습니다. 이것들은 core 의 `DocumentationIndexCli`·`AsyncApiCli` 를 직접 돌리거나([index 문서](asciidoc.md#index-문서), [SSE · WebSocket · AsyncAPI](messaging.md)) Maven 플러그인을 씁니다.

### exec-maven-plugin

테스트 뒤에 Maven JVM 안에서 CLI 를 돌립니다. epages 생성기를 플러그인 의존성으로 넣어야 합니다(Postman 을 쓰면 `restdocs-api-spec-postman-generator` 도).

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.6.2</version>
    <executions>
        <execution>
            <id>api-docs</id>
            <phase>prepare-package</phase>
            <goals>
                <goal>java</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <mainClass>io.github.kardx.tooling.cli.ApiDocsCli</mainClass>
        <includeProjectDependencies>false</includeProjectDependencies>
        <includePluginDependencies>true</includePluginDependencies>
        <blockSystemExit>true</blockSystemExit>
        <arguments>
            <argument>api-docs</argument>
            <argument>--base-dir</argument>
            <argument>${project.basedir}</argument>
            <argument>--config</argument>
            <argument>restdocs-tooling.json</argument>
        </arguments>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>io.github.kardx-project</groupId>
            <artifactId>kardx-tooling</artifactId>
            <version>0.1</version>
        </dependency>
        <dependency>
            <groupId>com.epages</groupId>
            <artifactId>restdocs-api-spec-openapi3-generator</artifactId>
            <version>0.20.2</version>
        </dependency>
    </dependencies>
</plugin>
```

실행하면 해가 없는 `SLF4J: No SLF4J providers` 안내가 나옵니다. 숨기려면 `org.slf4j:slf4j-nop` 을 플러그인 의존성에 넣습니다.

### java -jar

tooling 아티팩트는 epages OpenAPI·Postman 생성기 0.20.2 를 담은 실행용 jar(분류자 `all`)도 함께 배포합니다. 테스트가 스니펫을 쓴 뒤라면 어떤 빌드 도구에서든 됩니다. `./gradlew publishToMavenLocal` 뒤에는 `~/.m2/repository/io/github/kardx-project/kardx-tooling/0.1/` 에 있습니다.

```bash
java -jar kardx-tooling-0.1-all.jar api-docs --config restdocs-tooling.json
java -jar kardx-tooling-0.1-all.jar check --fail-on breaking
java -jar kardx-tooling-0.1-all.jar --help
```

### 명령과 옵션

| 명령 | 하는 일 |
|---|---|
| `api-docs` | 설정에서 켠 것 전부: OpenAPI 스펙, 뷰어, 커버리지, HTTP Client, Postman |
| `openapi` | 스니펫으로 만든 OpenAPI 3 스펙 (epages 생성기 + 이 라이브러리의 다듬기) |
| `viewer` | 스펙을 담은 정적 Redoc·Scalar 페이지 (`openapi` 먼저) |
| `coverage` | 문서화 커버리지 보고서 (`coverage/coverage.md`, `coverage.json`) |
| `http` | IntelliJ HTTP Client 파일 (`http-client/api.http`. `openapi` 먼저) |
| `postman` | Postman collection (`postman-collection.json`) |
| `check` | 스펙을 커밋한 기준 스펙과 비교하고 `check.failOn` 에 따라 실패 |
| `baseline` | 스펙을 기준 스펙으로 저장 |
| `source-docs` | 테스트가 읽는 KDoc/Javadoc 설명 색인 (실험 기능. `--output <파일>` 과 `--source <디렉터리>` 하나 이상 필요) |

| 옵션 | 기본값 | 정하는 것 |
|---|---|---|
| `--config <file>` | 없음 | JSON 설정 파일 |
| `--base-dir <dir>` | 현재 디렉터리 | 상대 경로의 기준 디렉터리 |
| `--snippets <dir>` | `target/generated-snippets` | 테스트가 쓴 스니펫 |
| `--output <dir>` | `target/api-docs` | 문서 디렉터리. `source-docs` 는 색인 파일 |
| `--work <dir>` | 스니펫 디렉터리 옆의 `kardx` | 중간 파일 |
| `--language en\|ko` | `en` | 생성 문구의 언어 |
| `--title`, `--version` | `API documentation`, `1.0.0` | OpenAPI info |
| `--server <url>` (여러 번) | `/` | OpenAPI servers |
| `--format yaml\|json` | `yaml` | OpenAPI 파일 형식 |
| `--baseline <file>`, `--fail-on any\|breaking\|none` | `docs/openapi/openapi3.yaml`, `any` | `check`·`baseline` 용 |
| `--source <dir>` (여러 번), `--method-docs` | 없음, 꺼짐 | `source-docs` 용 |
| `--verbose` | 꺼짐 | 출력을 더 자세히 |
| `--help` | | 사용법 |

명령줄 옵션이 설정 파일 값을 덮습니다. 종료 코드는 `0` 성공, `1` 실패(메시지에 이유), `2` 잘못된 사용입니다.

## Gradle 에서만 되는 것

- WireMock stub jar. 매핑 자체는 플러그인 없이도 yml `kardx.wire-mock-stubs: true` 로 만듭니다([WireMock stub](outputs.md#wiremock-stub)).
- `apiDocsExport`·`apiDocsUpload`, 여러 프로젝트 모으기(`apiDocsAggregate`).
- 앱을 실행할 때(`bootRun`, IDE 실행) 문서 자동 생성.
- 라이브러리 의존성 자동 추가. 직접 적습니다([플러그인 없이 쓸 때의 의존성](getting-started.md#플러그인-없이-쓸-때의-의존성)).
- 앱에 문서 넣기. [빌드 플러그인](#빌드-플러그인)처럼 maven-resources-plugin 을 씁니다.
- 시작 로그용 문서 위치 파일. 그래서 `boot` 모듈은 기본 파일 이름을 씁니다.
- 테스트 JVM 에 `language` 넘기기. `<language>ko</language>` 를 쓰면 생성 AsciiDoc 도 같은 언어가 되도록 surefire 의 `systemPropertyVariables` 에 `kardx.language` 를 `ko` 로 함께 넣습니다.
- 최신 여부 확인과 캐시. 목표는 빌드마다 돕니다.

HTML 변환은 직접 설정한 asciidoctor-maven-plugin 이 합니다.

## 트러블슈팅

### The OpenAPI generator is not on the classpath

`The OpenAPI generator is not on the classpath. Add com.epages:restdocs-api-spec-openapi3-generator ...`: tooling 아티팩트는 epages 생성기를 가져오지 않습니다. Maven 플러그인과 `-all` jar 에는 들어 있고, exec-maven-plugin 이나 직접 꾸린 클래스패스라면 [exec-maven-plugin](#exec-maven-plugin) 처럼 `com.epages:restdocs-api-spec-openapi3-generator`(Postman 은 `restdocs-api-spec-postman-generator` 도)를 넣습니다.

### No snippets directory

`No snippets directory: ... Run the documentation tests first`: 테스트가 스니펫을 쓰기 전에 목표나 명령이 돌았거나, 다른 곳을 보고 있습니다. Maven 에서는 `test` 를 포함하는 단계를 실행하고(`./mvnw kardx:api-docs` 만 따로가 아니라 `./mvnw verify`), 테스트를 건너뛰지 않습니다. CLI 는 `--snippets` 와 `--base-dir` 을 확인합니다.

### HTML 에 index 가 없거나 스니펫이 빠짐

asciidoctor-maven-plugin 이 `api-docs` 보다 먼저 돌았습니다. 둘 다 `prepare-package` 에 있고 Maven 은 적은 순서대로 돌리므로 kardx-maven-plugin 을 asciidoctor-maven-plugin 위에 적습니다. asciidoctor-maven-plugin 의 `sourceDirectory` 가 `target/kardx/asciidoc-sources` 인지, `snippets` 속성을 정했는지도 확인합니다.
