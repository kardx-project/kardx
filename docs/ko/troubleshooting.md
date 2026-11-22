[English](../troubleshooting.md) | **한국어**

# 트러블슈팅

[문서 목록](README.md)

<!-- toc -->
**목차**

- [오류 코드](#오류-코드)
<!-- /toc -->

| 증상 | 원인과 해결 |
|---|---|
| Swagger UI 에서 `Failed to load API definition` / `404 /docs/openapi3.yaml` | 문서가 아직 없습니다. IntelliJ 실행과 `bootRun` 은 실행 전에 문서를 자동으로 만듭니다(`generateOnRun`). 그 밖의 방법(IntelliJ 자체 빌드, 다른 IDE)으로 띄웠다면 `./gradlew apiDocs` 를 실행하세요. 떠 있는 앱은 다시 시작하지 않아도 됩니다 |
| Swagger UI Try it out 이 계속 로딩만 돈다 (SSE) | 끝나지 않는 스트림은 Swagger UI 가 표시하지 못합니다. `performStream` 으로 문서화하면 OpenAPI 에서 자동으로 빠집니다. AsyncAPI Try it out(`/docs/asyncapi.html#try-it-out`)에서 구독하세요 |
| `/docs/asyncapi.html` 이 404 | 문서화한 WebSocket·STOMP·SSE 메시지가 없으면 AsyncAPI 파일을 만들지 않습니다. 메시지를 문서화하면 생깁니다. 파일 이름을 바꿨다면(`asyncApi { fileName }`) 그 경로로 엽니다 |
| 시작 로그에 `API docs not found: no documents in classpath:static/docs ...` (`language = "ko"` 면 `API 문서 없음: ...`) | 앱 리소스에 문서가 아직 없습니다. `./gradlew apiDocs` 를 실행하거나 앱을 Gradle(`bootRun`, IntelliJ 기본 설정)로 다시 실행하세요 |
| 문서의 라이브러리 문구가 영어다 (`Back to index`, `API list`, `Constraints:`). 한국어로 쓰고 싶다 | 라이브러리가 넣는 문구의 기본 언어가 영어입니다. 한국어로 쓰려면 `kardx { language = "ko" }` 를 정합니다 ([문서 언어](configuration.md#문서-언어)) |
| 문서 문구가 영어·한국어로 섞인다 (`메인으로 돌아가기` 인데 `Common error responses`, `Constraints:` 등) | 언어를 yml(`kardx.language`)에만 정해서, 테스트가 쓰는 문구는 그 언어를 따르고 Gradle 플러그인이 쓰는 문구는 영어로 남았습니다. 테스트 로그에 `kardx[KDX-005]: the documentation language is 'ko' … but the Gradle plugin uses 'en'` 으로 시작하는 WARN 이 남습니다. 플러그인에서 정합니다: `kardx { language = "ko" }` ([문서 언어](configuration.md#문서-언어)) |
| 시작 로그에서 어떤 문서 줄이 빠진다 | 파일이 없거나 이름이 다릅니다. Gradle 플러그인이 실제 경로·파일 이름을 앱 리소스(`META-INF/kardx/docs-locations.properties`)에 남기므로 보통은 맞춥니다. 앱 설정에 `kardx.docs-path`·`startup-log.<문서>.file` 을 적었다면 그 값이 이기니 지우거나 고치세요 ([앱 시작 로그](build.md#앱-시작-로그)) |
| 경고 `kardx[KDX-001]: unknown property 'kardx....'` | `kardx.` 로 시작하는 키에 오타가 있어 무시됐습니다. 메시지의 `Did you mean` 키로 고치세요. 예전의 평평한 키(`open-api-enabled` 등)는 `It was renamed to` 뒤의 키로 바꿉니다 ([yml 설정](configuration.md#yml-설정)) |
| 빌드 경고 `API docs are packaged into the application ... without sensitive header masking` | 문서가 실행 jar 에 들어가는데 가리기를 켜지 않은 문서가 있습니다. 테스트 설정에 `mask-sensitive-headers: true` 를 켜거나 운영 빌드에서 `bootJar.enabled` 를 끄세요 ([운영 노출](build.md#운영-노출)) |
| 빌드 로그 `skipped apiDocsIndex — ...` | index.packageName·baseClass 중 하나만 정했거나, 아직 문서를 쓴 테스트가 없습니다. 둘 다 정하거나 둘 다 지우세요 ([index 문서](asciidoc.md#index-문서)) |
| 빌드 오류 `... spring-restdocs-mockmvc was added without a version, but nothing manages its version` | 플러그인이 버전 없이 넣은 REST Docs 모듈을 관리하는 BOM 이 없습니다. `io.spring.dependency-management` 플러그인이나 `implementation(platform("org.springframework.boot:spring-boot-dependencies:…"))` 를 넣거나, 그 모듈을 버전과 함께 직접 선언하세요 ([자동으로 넣는 의존성](build.md#자동으로-넣는-의존성)) |
| 빌드 경고 `com.epages.restdocs-api-spec 0.19.4 does not match Spring Boot 4` | epages 버전 계열이 Boot 와 맞지 않습니다. Boot 3 은 0.19.x, Boot 4 는 0.20.x 를 씁니다 |
| `The io.github.kardx-project plugin requires Gradle 7.6 or newer` | Gradle 래퍼를 올리세요: `./gradlew wrapper --gradle-version <버전>` |
| Gradle 이 플러그인을 읽을 때 `UnsupportedClassVersionError` (`class file version 61.0`) | Gradle 이 JDK 17 보다 낮은 JDK 에서 돕니다. Gradle 을 JDK 17 이상에서 돌리세요: IntelliJ Settings → Build Tools → Gradle → Gradle JVM, 또는 `JAVA_HOME`·`org.gradle.java.home` ([지원하는 빌드 구성](getting-started.md#지원하는-빌드-구성)) |
| `apiDocs` 출력에 `OpenAPI (Swagger) off (apply id("com.epages.restdocs-api-spec") ...)` | epages 플러그인이 없어 OpenAPI 스펙·커버리지·뷰어가 없습니다. 메시지의 버전으로 적용하세요 (Boot 4 는 0.20.x, Boot 3 은 0.19.x) |
| `apiDocsExport`·`apiDocsUpload` 가 `No AsyncAPI spec: kardx.asyncApi.enabled is false. ...` | AsyncAPI 생성을 껐는데 내보내기·업로드가 AsyncAPI 를 고릅니다. `asyncApi { enabled }` 를 다시 켜거나 `export { asyncApi = false }`·`upload { asyncApi = false }` 로 둡니다 ([스펙 내보내기](outputs.md#스펙-내보내기)) |
| `apiDocsStubsJar: project.group is not set, ...` | Stub Runner 는 `META-INF/<group>/<name>/<version>` 으로 stub 을 찾습니다. 빌드 스크립트에 `group = "com.example"` 을 정하세요 ([WireMock stub](outputs.md#wiremock-stub)) |
| `apiDocsAggregate: projects : and :root both go to the directory root/ ...` | 모으는 두 프로젝트가 같은 디렉터리로 갑니다. 하나만 모으세요 ([여러 프로젝트 모으기](outputs.md#여러-프로젝트-모으기)) |
| 경고 `Source docs: N source files are not UTF-8 (first: ...)` | 일부 소스가 UTF-8 이 아니어서(`MS949` 등) 주석의 글자가 U+FFFD 로 바뀌었습니다. 소스를 UTF-8 로 저장하세요 |
| `apiDocsCheck` 경고 `Path variables renamed: /a/{id} → /a/{userId}` | 변수 이름만 바뀌었습니다. 클라이언트가 부르는 URL 은 같으므로 엔드포인트 삭제가 아니라 경고입니다. 의도한 변경이면 `apiDocsBaseline` 으로 기준을 갱신합니다 |
| `apiDocsCheck` 의 깨는 변경 `Request enum added: ...` | 아무 값이나 받던 요청 파라미터·필드에 `enum` 이 생겨 클라이언트가 보내던 값이 거부될 수 있습니다. 의도한 변경이면 기준을 갱신합니다 ([OpenAPI 변경 비교](checks.md#openapi-변경-비교)) |
| `kardx.startupLogScope: configuration '...' not found. Use developmentOnly or runtimeOnly, or set startupLog = false.` | `startupLogScope` 가 가리키는 configuration 이 없습니다. 오타이거나, Spring Boot 플러그인이 없어 `developmentOnly` 가 만들어지지 않은 빌드입니다. `runtimeOnly` 나 `startupLog = false` 를 씁니다 ([앱 시작 로그](build.md#앱-시작-로그)) |
| `Set kardx.restDocsVersion (no Spring Boot plugin to take it from).` | Spring Boot 플러그인 없이 Asciidoctor 플러그인을 적용해(라이브러리 모듈, 문서 전용 프로젝트) `spring-restdocs-asciidoctor` 버전을 정할 수 없습니다. `kardx { restDocsVersion = "..." }` 에 쓰는 Spring REST Docs 버전을 적습니다 ([Gradle 플러그인](build.md#gradle-플러그인)) |
| `kardx.response-wrapper.data-path needs response-wrapper.class` | yml 에 래퍼 클래스 없이 데이터 경로만 적었습니다. `kardx.response-wrapper.class` 를 더합니다 ([공통 응답 래퍼](http-api.md#공통-응답-래퍼)) |
| `A header in kardx.upload.headers has no value, for example an environment variable or Gradle property it reads is not set.` | 보통 CI 시크릿이 작업에 전달되지 않은 경우입니다. 헤더가 읽는 환경 변수나 Gradle 속성을 설정합니다 ([스펙 내보내기](outputs.md#스펙-내보내기)) |
| `kardx.bootJar.path must be a directory inside the app's resources such as 'static/docs' or 'public/api-docs', got '...'` | `bootJar.path` 가 비었거나, 절대 경로·드라이브 문자이거나 `..` 를 씁니다. `static/docs` 처럼 앱 리소스의 상대 하위 디렉터리를 쓰세요 ([앱 리소스에 넣는 문서](build.md#앱-리소스에-넣는-문서)) |
| `kardx.bootJar.path '...' is an application resource root (static, public, resources, META-INF/resources). ...` | 그 루트에 넣으면 문서가 앱 파일과 섞입니다. `static/docs` 같은 하위 디렉터리를 쓰세요 ([앱 리소스에 넣는 문서](build.md#앱-리소스에-넣는-문서)) |
| `kardx.<directory> must not be the project directory, the build directory or one of their parents, because the plugin deletes it before writing: ...` | `snippetsDirectory`, `asciidocDirectory`, `outputDirectory`, `aggregate.outputDirectory` 가 플러그인이 비우면 안 되는 디렉터리를 가리킵니다. 하위 디렉터리, 되도록 `build/` 아래를 쓰세요 ([플러그인이 비우는 디렉터리](build.md#플러그인이-비우는-디렉터리)) |
| `kardx.<directory> must not be inside or contain the source directory ..., because the plugin deletes it before writing: ...` | 디렉터리가 `src/` 나 AsciiDoc 원본 디렉터리 안에 있거나 그것을 품고 있습니다. `build/` 아래 디렉터리를 쓰세요 ([플러그인이 비우는 디렉터리](build.md#플러그인이-비우는-디렉터리)) |
| `kardx.<directory> points to ..., which is outside the build directory and holds files the plugin did not write. ...` | `build/` 밖에서는 빈 디렉터리나 표시 파일 `.kardx-output` 이 있는 디렉터리만 바꿉니다. `build/` 아래를 쓰거나, 비우거나, 내용을 지워도 된다면 빈 `.kardx-output` 을 만드세요 ([플러그인이 비우는 디렉터리](build.md#플러그인이-비우는-디렉터리)) |
| 태스크가 의존성 선언 없이 `build/api-spec/openapi3.yaml`(`openapi3` 의 출력)을 쓴다고 Gradle 이 알림 | 플러그인이 스펙 파일을 `openapi3` 의 출력으로 선언하므로 Gradle 8 이상이 읽는 쪽을 검사합니다. 그 태스크에 `dependsOn("openapi3")` 를 더하세요 ([Gradle 플러그인](build.md#gradle-플러그인)) |
| yml 에 `Cannot resolve configuration property 'kardx...'` | 라이브러리 jar 의 설정 메타데이터를 IDE 가 아직 못 읽었습니다. Gradle 을 다시 동기화(Reload All Gradle Projects)하면 사라집니다. IntelliJ 는 RUNTIME 범위 의존성(`developmentOnly`)의 메타데이터를 읽지 않으므로 플러그인이 시작 로그 모듈을 `compileOnly` 에도 넣습니다. 모듈을 직접 선언했다면 `compileOnly(...) { isTransitive = false }` 도 넣으세요 ([앱 시작 로그](build.md#앱-시작-로그)) |
| 운영 코드에서 `Unresolved reference 'Description'` | Gradle 플러그인이 annotations 를 `compileOnly` 에 넣습니다. `addDependencies = false` 거나 플러그인 없이 쓰면 직접 `compileOnly` 로 추가하세요 |
| `DocumentationMismatchException: Documented response fields do not match ...` | 문서화한 필드·파라미터가 실제와 다릅니다. 메시지의 목록·붙여 넣을 DSL 줄·힌트를 보세요. 원래 REST Docs 메시지는 `Caused by` 에 있습니다 ([검증 실패 메시지](http-api.md#검증-실패-메시지)) |
| `DocumentationMismatchException: ... Type mismatch: - price: documented Number, actual String` | 문서화한 필드 타입과 실제 JSON 타입이 다릅니다 (REST Docs 의 `FieldTypesDoNotMatchException` 은 `Caused by` 에 있습니다). 메시지의 DSL 줄로 타입을 고치거나 원인을 없앱니다. 숫자가 문자열이면 `@JsonFormat(shape = JsonFormat.Shape.STRING)`·`ToStringSerializer`·`JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS`, 문자열로 문서화한 날짜가 숫자·배열이면 `WRITE_DATES_AS_TIMESTAMPS` 를 끕니다. 실제 값이 `Null` 이면 프로퍼티를 nullable 로 하거나 `isOptional true`(추출 목록은 `optionalAt("...")`), 배열 원소마다 타입이 다르면(`Varies`) `VARIES` 로 문서화합니다 ([검증 실패 메시지](http-api.md#검증-실패-메시지)) |
| 추출 결과가 어떤 표가 될지 테스트 없이 보고 싶다 | `println(responseFieldsOf<T>().describe())` ([추출 결과 미리보기](http-api.md#추출-결과-미리보기)) |
| KDoc·Javadoc 주석이 설명으로 나오지 않음 | 다음 순서로 확인합니다. (1) 기능이 켜져 있는지: 실험 기능이라 기본으로 꺼져 있으므로 Gradle 플러그인에서 `kardx { sourceDocs { enabled = true } }` 로 켭니다. 그 주석 종류의 항목별 스위치(`fieldDocs`, `enumDocs`, `schemaDescriptions`, `paramDocs` 는 기본값 `true`, `methodDocs` 는 기본값 `false`)가 켜져 있는지, yml `kardx.source-docs.enabled`(기본값 `true`)가 `false` 가 아닌지도 봅니다. (2) 클래스가 테스트 소스가 아니라 이 프로젝트나 `project(...)` 의존 프로젝트(`sourceDocs { includeProjectDependencies = true }`, 기본값)의 `main` 소스셋에 있는지. (3) DTO 가 jar 로 들어오지 않았는지. jar 에는 읽을 소스가 없습니다 (`@Description` 이나 `description-annotations` 를 씁니다). (4) 필드에 `@Description` 이나 목록의 어노테이션이 없는지. 있으면 그것이 주석보다 먼저입니다. (5) 스캐너가 모으지 않는 선언이거나 가벼운 스캐너가 건너뛰는 문법을 쓰지 않았는지 ([알려진 한계](http-api.md#알려진-한계)). 건너뛴 선언은 하나씩 로그에 남지 않으므로(`--debug` 는 읽다가 오류로 멈춘 파일만 보여 줍니다) `build/kardx/source-docs/source-docs.json` 을 열어 클래스(JVM 바이너리 이름, `Outer$Inner`)와 프로퍼티를 찾아 봅니다. 컨트롤러 주석은 `methodDocs = true` 일 때만 쓰고 핸들러를 알아야 하므로 RestTestClient 와 함수형 라우트에는 들어가지 않습니다 ([KDoc/Javadoc 에서 설명 가져오기](http-api.md#kdocjavadoc-에서-설명-가져오기)) |
| `kardx[KDX-101]: path parameter 'id' declared by name only (or with FromParamTag), but no description was found.` | 테스트가 `pathParameters("id")`(또는 `FromParamTag`)로 적었는데 `@param id` 를 찾지 못했습니다. 핸들러 메서드의 KDoc/Javadoc 에 `@param id` 를 더하고 실험 기능인 source docs 를 켜거나(`kardx { sourceDocs { enabled = true } }`, `paramDocs` 는 `true` 그대로) 테스트에서 설명합니다(`"id" pathMeans "..."`). `@param` 을 찾으려면 Gradle 플러그인(`apiDocsSourceDocs`)과 알려진 핸들러가 필요합니다. RestTestClient, 함수형 라우트, `bindToServer` 는 핸들러를 모릅니다 ([이름만 적은 파라미터 → 메서드 `@param`](http-api.md#이름만-적은-파라미터--메서드-param)) |
| 경고 `kardx[KDX-501]: A and B both write X.adoc and share snippet directory 'x'. Rename one of them.` | 다른 패키지의 두 테스트 클래스가 단순 이름이 같아 문서가 서로 덮어씁니다. 한쪽 클래스 이름을 바꿉니다 ([문서 자동 생성](asciidoc.md#문서-자동-생성)) |
| 병렬 테스트에서 한 클래스의 문서가 다른 클래스의 설정(yml 이나 `configure` 의 래퍼, 마스킹, `document.commonIncludeFiles` 등)으로 만들어짐 | 설정 값은 JVM 전역 하나입니다. 각 테스트는 자기가 더한 `configure` 만 빼므로 서로 지우지는 않지만, 동시에 도는 동안에는 다른 테스트가 더한 블록과 다른 컨텍스트(프로파일, `@TestPropertySource`)의 yml 값이 함께 적용됩니다. 테스트마다 다른 값은 `withSettings` 로 주거나, 그 클래스들을 차례로 실행하세요(`@Execution(ExecutionMode.SAME_THREAD)`, `@ResourceLock`). 같은 클래스의 메서드를 병렬로 돌리면 문서에 쓰는 마지막 테스트의 설정도 정해지지 않습니다 ([병렬 실행](configuration.md#병렬-실행)) |
| `configure` 한 값이 테스트가 끝나도 남는다 | 테스트 스레드가 아닌 곳(테스트가 만든 스레드·스레드 풀, `@BeforeAll`, 격리 모드 Kotest 스펙 본문·`beforeSpec`)에서 불렀습니다. 테스트 스레드(`@BeforeEach`·본문, Kotest `beforeEach`)에서 부르거나 `@AfterAll` 에서 `reset()` 하세요 ([설정](configuration.md#바꾸는-방법), [Kotest](kotest.md#junit-확장과-같은-동작)) |
| 생성 문서의 섹션 순서가 원하는 것과 다름 | 기본은 메서드 이름순입니다. `@TestMethodOrder(MethodOrderer.OrderAnnotation::class)` + `@Order` 를 쓰세요 ([섹션 순서](asciidoc.md#섹션-순서)) |
| `mockMvc.get("/x") { }` 나 `MockMvcTester` 결과에 `andDocument` 가 없다 | `io.github.kardx.mockmvc.andDocument` 를 import 합니다. 이 import 하나가 `ResultActions`·`ResultActionsDsl`·`MvcTestResult` 를 모두 덮습니다. IDE 가 다른 `andDocument`(예: WebTestClient 용)를 가져왔다면 그 import 를 바꿉니다 |
| `Fields with the following paths were not found in the payload: [items[].x]` | 테스트 응답의 배열이 비어 있습니다. 데이터를 넣거나 원소 필드를 optional 로 둡니다: `responseFields(responseFieldsOf<T>().optionalUnder("items[]"))`. 모든 배열에 적용하려면 `array-element-fields-optional: true` (한 테스트만 `withSettings({ extraction.arrayElementFieldsOptional = true })`) |
| `The following parts of the payload were not documented` | 추출한 타입과 실제 응답이 다릅니다. `@JsonInclude`, 커스텀 Serializer 처럼 모양을 바꾸는 설정은 추출기가 알 수 없으니 직접 기술하거나 그 항목만 느슨하게 하세요: `relaxed(RelaxedSection.RESPONSE_FIELDS)` ([느슨한 검증](http-api.md#느슨한-검증)) |
| 필드 이름이 응답과 다름 (`userName` vs `user_name`) | `restDocsMockMvc` 를 쓰면 앱 매퍼의 이름 전략을 따릅니다. 매퍼를 직접 만들었다면 `objectMapper` 설정을 앱과 맞추세요 |
| WebFlux 앱 테스트가 `No bean named 'webHandler' available` 로 실패 | 테스트 클래스패스에 Spring MVC 가 있어 서블릿 앱으로 떴습니다. epages 의 `spring-boot-starter-web`(Boot 3·4)은 Gradle 플러그인이 자동으로 뺍니다(`spring-boot-starter-webflux` 를 선언한 프로젝트, `excludeServletStarterForWebFlux`). 플러그인 없이 쓰면 직접 `exclude` 하세요. 본체는 MockMvc 를 끌고 오지 않습니다 ([WebTestClient 의존성](webtestclient.md#의존성)) |
| WebFlux 앱에서 `/docs/openapi3.yaml` 이 내려받기로 열린다 (`application/octet-stream`) | WebFlux(Netty)는 `.yaml` 미디어 타입을 모르고, `server.mime-mappings` 는 서블릿 전용입니다. `WebFluxConfigurer` 로 `/docs/**` 리소스 핸들러에 `setMediaTypes(mapOf("yaml" to MediaType("text", "yaml", Charsets.UTF_8)))` 를 주세요 ([문서 YAML 이 내려받기로 열릴 때](webtestclient.md#문서-yaml-이-내려받기로-열릴-때-webflux)) |
| WebTestClient `andDocument` 가 `REST Docs configuration not found` 로 실패 | 클라이언트에 REST Docs 필터가 없습니다. `restDocsWebTestClient(context, provider)` 로 만들거나 빌더에 `.restDocs(provider)` 를 붙이세요 |
| WebTestClient 문서의 `api-summary` 가 실제 경로(`/api/users/1`)이고 epages 가 `MissingUrlTemplateException` | `uri { ... }` 빌더로 만든 요청에는 URL 템플릿이 없습니다. `uri("/api/users/{id}", 1)` 처럼 템플릿으로 쓰세요 |
| Boot 4 에서 value class 필드가 `id-QXt-Mfc` 로 직렬화됨 | Jackson 3 용 `tools.jackson.module:jackson-module-kotlin` 이 필요합니다 |
| `include::_landing.adoc[]` 오류 | `document.commonIncludeFiles` 의 파일이 `src/docs/asciidoc` 에 없습니다 |
| 문서에 토큰·세션 값이 그대로 나옴 | 마스킹은 기본으로 꺼져 있습니다. `mask-sensitive-headers: true` 나 `masking.maskSensitive()` 로 켜세요. 흔한 자격증명 쿼리·폼 파라미터(URI 쿼리나 `application/x-www-form-urlencoded` 본문의 `access_token`, `password`, `client_secret` …)도 가리고, 다른 것은 `masked-query-parameters` 로 더합니다 ([민감 정보 마스킹](configuration.md#민감-정보-마스킹)) |
| `StompTestClient` 가 `STOMP ERROR from server: <message>: <본문>` 으로 실패 | 서버가 STOMP `ERROR` 프레임으로 답했습니다(채널 인터셉터가 CONNECT·SEND·SUBSCRIBE 를 거절, 없는 목적지 …). 접두사 뒤는 서버의 `message` 헤더와 본문입니다. 서버나 테스트에서 원인(토큰 없음, 틀린 목적지)을 고치세요. `STOMP connection failed: ...` 는 연결이 끊겼다는 뜻입니다 ([StompTestClient](messaging.md#stomptestclient-로-주고받기)) |
| 문서에 응답 `Date` 헤더가 없음 | 스니펫이 실행마다 같도록 기본으로 뺍니다. `http.removedResponseHeaders = emptySet()`(`removed-response-headers: none`)으로 남기거나 `responseHeaders(...)`(DSL, 또는 `snippets(...)` 로 넘긴 REST Docs `responseHeaders`)로 문서화하세요. yml `removed-response-headers` 목록은 기본값을 대신하므로 `Date` 도 목록에 넣지 않으면 다시 나옵니다 ([재현 가능한 스니펫](build.md#재현-가능한-스니펫)) |
| 문서의 `Set-Cookie` 에 `Expires` 속성이 없음 | 쿠키에 `Max-Age` 도 있으면 `Expires` 를 뺍니다. 지금 시각에서 계산되어 실행마다 스니펫을 바꾸고, 뜻은 `Max-Age` 와 같기 때문입니다. 받은 그대로 두려면 `http.removedResponseHeaders = emptySet()`(`removed-response-headers: none`)으로 두세요 |
| 설명이 전부 `No description` | `@Description` 이 없거나, 다른 어노테이션(`@Schema` 등)을 `description-annotations` 에 적지 않았거나, 직접 정한 `descriptions.resolver` 가 다른 어노테이션을 보면서 기본 단계로 넘기지 않습니다(`orElse DescriptionResolver.DEFAULT` 로 잇습니다). Java 로 만든 어노테이션은 `@Retention(RUNTIME)` 이어야 합니다 ([다른 어노테이션에서 설명 가져오기](http-api.md#다른-어노테이션에서-설명-가져오기)) |
| `kardx.description-annotations: class '...' not found` | 클래스 이름 오타이거나 그 어노테이션 의존성이 없습니다. 별칭(`swagger`, `jackson`)도 의존성이 필요합니다 ([오류와 해결](http-api.md#오류와-해결)) |
| 제약조건이 문서에 없음 | `jakarta.validation-api` 가 클래스패스에 있는지, 직접 만든 템플릿이 Constraints 열을 지우지 않았는지 확인하세요 |
| Asciidoctor 에서 `AbstractMethodError` (Boot 4) | AsciidoctorJ 3 이 필요합니다. Gradle 플러그인은 자동으로 맞추고, 직접 구성했다면 `asciidoctorj { setVersion("3.0.1") }` 을 넣으세요 |
| 인증이 필요한 API 가 인증 없이 200 으로 문서화됨, 또는 경고 `kardx[KDX-006]: the application context has a springSecurityFilterChain bean, but restDocsMockMvc does not apply it` | `restDocsMockMvc` 는 기본으로 Spring Security 필터 체인을 걸지 않습니다. Spring Security 로 보호하는 API 라면 `kardx.apply-spring-security: true` 로 켜고, `spring-security-test` 를 넣어 `with(jwt())`·`with(user(...))` 로 인증합니다. 필터 체인 없이 문서화하는 것이 의도라면 `apply-spring-security: false` 로 명시하면 경고가 사라집니다. 이 설정은 MockMvc 를 만들 때 읽으므로 `restDocsMockMvc` 를 부르기 전에 정합니다 ([Spring Security](http-api.md#spring-security)) |
| `with(jwt())`·`@WithMockUser` 로 인증한 API 에 Swagger Authorize 가 없다 | 요청에 `Authorization` 헤더가 없어 보안 스키마가 생기지 않았습니다. `openApi { bearerAuth() }`(Basic 은 `basicAuth()`)로 선언하세요 ([헤더 없이 인증한 테스트의 보안 스키마](http-api.md#헤더-없이-인증한-테스트의-보안-스키마)) |
| 문서의 응답 본문이 `<binary: 1234 bytes>` | 글자가 아닌 본문(PDF, 이미지, `application/octet-stream` …)은 크기만 남깁니다. 의도한 동작이고, 옆의 `Response File` 표가 미디어 타입·파일 이름·크기를 보여 주며 `responseFile("...")` 로 설명을 더합니다. 글자 본문인데 이렇게 나오면 응답 `Content-Type` 을 확인하세요 ([파일 다운로드](http-api.md#파일-다운로드)) |
| 빌드 로그 `OpenAPI schema names: Item → OrderItem (...)` | 이름이 같은데 구조가 다른 스키마를 Gradle 플러그인이 나눈 것입니다. 원하는 이름이 있으면 `openApi { responseSchema = "..." }` ([스키마 이름](http-api.md#스키마-이름)) |
| 빌드 경고 `OpenAPI schema names: ...: set VARIES where the same path has different types ...` | 직접 정한 같은 스키마 이름을 구조가 다른 응답에 썼습니다. 테스트마다 다른 이름을 주세요 |
| 로그 `kardx[KDX-107]: responseFieldsOf<...>() found nothing to document` | 그 타입에 문서화할 고정 필드가 없습니다 (값 하나인 본문, 경로 없는 `Map`). 경로를 주거나 그 항목을 빼세요 ([빈 추출 결과](http-api.md#빈-추출-결과)) |
| `Query parameters with the following names were not documented: [...]` 인데 `parametersOf<T>()` 를 넘겼다 | 추출 결과가 비었거나 이름이 다릅니다. `Pageable`·`Sort` 는 `page`·`size`·`sort` 로 추출되니 이름을 바꿨다면 직접 기술하세요 ([Spring Data 페이지](http-api.md#spring-data-페이지)) |
| `@JsonView`·`@JsonFormat(shape = OBJECT)`·`@JsonAlias` 등으로 필드 검증이 실패 | 추출기가 따라가지 못하는 직렬화 기능입니다 ([지원하지 않는 모양](http-api.md#지원하지-않는-모양)) |
| Java record 필드가 모두 Required `false` | 어노테이션 없는 Java 참조 타입은 optional 로 봅니다. 필수면 `@NonNull`·`@NotNull` 을 붙이거나 패키지에 `@NullMarked` 를 둡니다 ([추출 규칙](http-api.md#추출-규칙)) |
| Kotest: `IllegalStateException: kardx[KDX-401]: no Kotest leaf test is running` | 리프 테스트(`then`·`it`·`test`) 밖(스펙 본문, `given`·`describe` 블록)에서 요청을 문서화했거나 `RestDocsExtension` 을 등록하지 않았습니다. 요청과 `andDocument` 를 리프 테스트 안으로 옮기고 스펙(`extension(RestDocsExtension())`)이나 프로젝트 설정에 등록하세요 ([Kotest](kotest.md#등록)) |
| Kotest: `kardx-kotest is built for Kotest 6.x, but Kotest 5.x is on the test classpath` (또는 반대) | Kotest 버전과 아티팩트가 맞지 않습니다. Kotest 5.x 는 `kardx-kotest5`, 6.x 는 `kardx-kotest` 를 넣습니다. Gradle 플러그인은 선언한 Kotest 버전으로 맞는 것을 고릅니다 ([Kotest 버전별 의존성](kotest.md#kotest-버전별-의존성)) |
| Gradle: `Cannot select module with conflict on capability 'io.github.kardx-project:kardx-kotest-extension'` | `-kotest` 와 `-kotest5` 가 함께 들어왔습니다. 하나만 남기세요(다른 의존성이 끌고 오면 `exclude`) |
| Kotest 5.x: 스니펫은 생기는데 스펙 문서(`.adoc`)가 없다 | 스펙의 `override fun extensions() = listOf(RestDocsExtension())` 가 부를 때마다 새 인스턴스를 만듭니다. 인스턴스를 프로퍼티에 두고 돌려주세요 ([Kotest 5.x](kotest.md#kotest-5x)) |
| Kotest: 경고 `tests '...' and '...' in ... map to the same snippet directory` | 같은 스펙의 두 테스트 이름이 다듬으면 같아집니다(`조회 (a)`·`조회 [a]`). 뒤의 것에 `-2` 를 붙였지만 순서에 따라 바뀔 수 있으니 이름을 바꾸세요 ([스니펫 디렉터리 이름](kotest.md#스니펫-디렉터리-이름)) |
| OpenAPI Generator 등으로 만든 클라이언트의 메서드 이름이 비거나 겹친다 (한글 테스트 이름) | `test-name` 규칙을 골랐거나 핸들러를 알 수 없는 요청(함수형 라우터, 핸들러를 기록하지 않은 WebTestClient)이라 operationId 가 테스트 이름(`<spec>.<스니펫 디렉터리 이름>`)이고 한글이 들어갔습니다. 코드 생성기는 ASCII 가 아닌 글자를 지웁니다. 해결: (a) 그 테스트에 `openApi { operationId = "..." }`, 또는 (b) 규칙을 `handler-method`(기본)·`path` 로 ([operationId](http-api.md#operationid)) |
| 빌드 로그에 `OpenAPI operationId: '...' is set on several operations` | `openApi { operationId }` 나 `openApi.operationIdResolver` 로 정한 같은 id 가 여러 operation 에 있습니다. 직접 정한 id 는 바꾸지 않으므로 테스트마다 다른 id 를 주세요 |
| Kotest 테스트 하나만 돌렸더니 스니펫 디렉터리 이름의 `-2` 가 없어졌다 | 같은 이름이 되는 테스트에 붙는 번호는 실행 순서를 따릅니다. 이름을 바꿔 겹치지 않게 하세요 ([스니펫 디렉터리 이름](kotest.md#스니펫-디렉터리-이름)) |
| Kotest: 새 스레드에서 `currentTestProvider` 가 `no Kotest leaf test is running` | 코루틴 밖 스레드에서는 지금 테스트를 찾지 못합니다. 코루틴(`withContext(Dispatchers.IO)`)으로 옮기거나 스펙에 등록한 확장의 `provider` 를 쓰세요(마지막으로 시작한 테스트로 대신 찾음) |
| Kotest 격리 모드에서 `include::...[]` 나 설정이 여러 번 적용된다 | 격리 모드는 인스턴스마다 스펙 본문·`beforeSpec` 을 다시 실행해 `configure` 가 쌓입니다. `beforeEach` 에서 설정하세요 ([Kotest](kotest.md#junit-확장과-같은-동작)) |
| Kotest 스펙의 문서가 index 에 없다 | 문서화한 테스트가 없거나 모두 실패했습니다. `index.packageName` 을 정했다면 스펙이 그 패키지(하위 포함) 밖에 있을 수도 있습니다. 설정을 지우면 문서를 만든 모든 스펙이 들어갑니다 ([index 와 OpenAPI](kotest.md#index-와-openapi)) |
| Kotest 스펙에서 yml 의 `kardx.*` 이 적용되지 않는다 | Kotest Spring 확장(6.x `io.kotest:kotest-extensions-spring`, 5.x `io.kotest.extensions:kotest-extensions-spring`)이 스펙에 적용되지 않았습니다. 6.x 는 `@ApplyExtension(SpringExtension::class)` 나 프로젝트 설정, 5.x 는 프로젝트 설정·스펙의 `override fun extensions()` 에 등록하세요 ([Spring 과 함께 쓰기](kotest.md#spring-과-함께-쓰기)) |
| `@DisplayName` 의 `*`·백틱이 굵게·코드로 보인다 | AsciiDoc 서식으로 해석됩니다. `\*` 로 막거나 `+...+` 로 감싸세요 ([테스트 모양별 스니펫·섹션](asciidoc.md#테스트-모양별-스니펫섹션)) |
| 경로 변수·파라미터가 OpenAPI 에 `type: string` 으로 나온다, 숫자 기본값이 `"20"` | 직접 쓴 파라미터의 타입은 컨트롤러 인자에서 정하는데, 핸들러를 알 수 없는 요청입니다: `recordHandlers()` 없는 WebTestClient(`bindToController`·`bindToRouterFunction`), 함수형 라우터, `bindToServer`. 핸들러를 기록하는 클라이언트(`restDocsWebTestClient`, `recordHandlers()`)를 쓰거나 `ofType Long::class` 를 붙이세요. 컨트롤러 인자가 `String` 이거나 이름이 다르면(Java 는 `-parameters` 없이 컴파일) 역시 `string` 입니다 ([파라미터 타입은 핸들러에서 정해진다](http-api.md#파라미터-타입은-핸들러에서-정해진다)) |
| 경고 `kardx[KDX-204]: GET /... in ... does not document path variable(s) id` | URL 템플릿의 경로 변수를 블록에서 문서화하지 않아 OpenAPI 에 설명 없이 나갑니다. 메시지 끝의 `pathParameters(...)` 줄을 `andDocument` 블록에 붙여 넣으세요. 테스트마다 한 번만 경고합니다 ([파라미터 타입은 핸들러에서 정해진다](http-api.md#파라미터-타입은-핸들러에서-정해진다)) |
| Swagger UI 에서 API 가 `default` 그룹에 몰린다 / `summary` 가 비어 있다 | 이 라이브러리의 `andDocument` 로 문서화한 operation 은 태그(문서 클래스 제목)와 요약(테스트 표시 이름)이 항상 들어갑니다. 비어 있다면 그 operation 은 이 라이브러리 밖(epages `MockMvcRestDocumentationWrapper.document`, `resource(...)` 를 직접 부른 테스트)에서 문서화한 것입니다. `andDocument` 로 옮기세요 ([OpenAPI 태그와 요약](http-api.md#openapi-태그와-요약)) |
| 여러 테스트로 문서화한 operation 의 summary 가 테스트 하나의 이름이다(`계좌이체 결제`), 또는 빌드가 `OpenAPI summary/description: ... tests declare different summary values` 를 경고한다 | 합친 operation 은 대표 테스트의 이름을 받습니다. `openApi { summary = "..." }` 로 operation 이름을 정하고, 그 operation 의 테스트마다 같은 값을 적으세요. 값이 다르면 스니펫 디렉터리 이름순으로 처음 값을 씁니다 ([OpenAPI 태그와 요약](http-api.md#openapi-태그와-요약)) |
| `apiDocsCheck` 가 `No OpenAPI baseline: ...` 로 실패 | 기준 파일이 없으면 `failOn = "none"` 이 아닌 한 실패합니다. `./gradlew apiDocsBaseline` 으로 만들어 커밋하거나, 예전처럼 통과시키려면 `openApiCheck { requireBaseline = false }` ([OpenAPI 변경 비교](checks.md#openapi-변경-비교)) |
| `kardx { upload { timeout = java.time.Duration.ofSeconds(30) } }` 이 `Unresolved reference: time` | 블록 안의 `java` 는 Java 플러그인 확장으로 해석됩니다. 빌드 스크립트 맨 위에 `import java.time.Duration` 을 두고 `Duration.ofSeconds(30)` 으로 쓰세요 ([스펙 내보내기](outputs.md#스펙-내보내기)) |
| OpenAPI 에 `minLength`·`format`·`multipart/form-data`·`in: cookie` 가 없다 | Gradle 플러그인(`io.github.kardx-project`)이 `openapi3` 뒤에 넣습니다. epages 플러그인만 쓰면 일부만 나갑니다 ([Gradle 플러그인 없이 쓸 때](http-api.md#gradle-플러그인-없이-쓸-때)) |
| 직접 쓴 `type ENUM ... enumOf X::class` 가 `Type mismatch: - status: documented String, actual Number` 로 실패 | `@JsonValue` 가 숫자인 Enum 입니다. Enum 필드 타입은 실제 JSON 타입으로 검증합니다. `type NUMBER` 로 쓰세요 (추출한 필드는 자동으로 맞춥니다) |
| Redocly `no-invalid-media-type-examples` 경고 | 예시가 스키마를 어깁니다. 검증 실패를 문서화한 테스트(빈 이름, 음수 가격)의 요청 예시면 의도한 것입니다. 성공 테스트라면 제약이나 예시 데이터를 확인하세요 |
| `errorResponse status must be between 400 and 599, but was 302` | `errorResponse(...)` 는 오류 응답만 선언합니다. 리다이렉트 등 다른 상태는 테스트로 문서화하세요 ([선언한 오류 응답](http-api.md#선언한-오류-응답)) |
| `errorResponse(409) is declared more than once in this andDocument block` (또는 `needs a non-blank description`, `has a blank contentType`, `has a blank example`) | 한 블록에서 상태마다 선언은 하나, 설명은 꼭 적습니다. 예시를 빼려면 빈 값을 넘기지 말고 `example(...)` 을 지우세요 |
| `responseFile cannot be used together with responseFields` (또는 `requestFile cannot be used together with requestFields`, `... with requestParts`) | 파일 본문에는 필드가 없습니다. 다운로드 테스트에서 `responseFields` 를 지우고, multipart 의 파일 파트는 `requestFile` 대신 `requestParts` 로 적으세요 ([파일 다운로드](http-api.md#파일-다운로드)) |
| `DocumentationMismatchException: Documented errorResponse(409) fields do not match the actual example.` | 선언한 예시에 `fields(...)` 에 없는 필드가 있거나 필수 필드가 빠졌습니다. 예시나 필드를 고치세요. 선언한 응답에서 확인하는 것은 이것뿐입니다 ([선언한 오류 응답](http-api.md#선언한-오류-응답)) |
| `the example of errorResponse(409) is not valid JSON for content type application/json` | 예시 글이 JSON 이 아닙니다. 고치거나, `example(...)` 에 객체를 넘기거나, JSON 이 아닌 `contentType` 을 정하세요 |
| 선언한 오류 응답이 문서와 OpenAPI 에 없다 | 그 테스트가 그 상태를 실제로 받았습니다. 실제 응답이 문서화하므로 선언은 경고 없이 빠집니다 |
| `errorResponse(409, "...", example)` 가 예시만 만들고 필드 표가 없다 | 예시가 컬렉션·배열·`Map`·제네릭 클래스·단순 값이라 런타임 클래스에서 필드를 추출하지 못합니다. 블록 형태로 씁니다: `errorResponse(409, "...") { fields<RestResponse<ErrorBody>>(); example(example) }` |
| 수식어가 효과가 없다: `AUTH isOptional true` 를 따로 한 줄 쓴 뒤 `requestHeaders(AUTH)` 가 여전히 필수 | 수식어는 복사본을 돌려주고 원래 항목은 그대로 둡니다. 결과를 씁니다: `requestHeaders(AUTH isOptional true)` ([항목 문법](http-api.md#항목-문법)) |
| `relaxed = true` 인 테스트가 문서화하지 않은 쿼리 파라미터·쿠키·링크로 실패하지 않는다 | `relaxed = true` 는 모든 항목을 느슨하게 합니다. 본문만 느슨하게 하려면 `relaxed(RelaxedSection.REQUEST_FIELDS, RelaxedSection.RESPONSE_FIELDS)` 를 쓰세요 ([느슨한 검증](http-api.md#느슨한-검증)) |
| RestTestClient `andDocument` 가 `this RestTestClient has no REST Docs configuration. Build it with restDocs(provider).` 로 실패 | 빌더의 `build()` 앞에 `.restDocs(provider)` 를 붙이세요 ([RestTestClient](webtestclient.md#resttestclient)) |
| `Could not connect to STOMP endpoint ws://localhost:.../ws` | 그 주소에서 앱이 떠 있지 않거나(`@SpringBootTest(webEnvironment = RANDOM_PORT)` 와 `@LocalServerPort` 를 쓰세요), 경로가 틀렸거나, 서버가 CONNECT 프레임을 거절했습니다(`connectHeaders` 가 없거나 틀림) ([StompTestClient](messaging.md#stomptestclient-로-주고받기)) |
| `No STOMP frame arrived on /topic/... within PT5S after sending to /app/...` (`sendAndReceive`), 또는 `No STOMP frame arrived on /topic/... within PT5S.` (`StompSubscription.await`) | 구독한 목적지로 아무것도 오지 않았습니다. `subscribeTo` 가 서버가 보내는 곳과 같은지(템플릿 `/topic/rooms.{roomId}` 가 아니라 `/topic/rooms.1`) 확인하거나 `timeout` 을 늘리세요 |
| `No RestDocumentationContext. Call MessageDocumentation inside a test method run by AutoRestDocumentationExtension (or RestDocumentationExtension).` | `MessageDocumentation` 을 문서화 테스트 밖(`@BeforeAll`, 다른 스레드, 확장이 없는 클래스)에서 썼습니다. Kotest 에서는 `restDocs.provider` 나 `RestDocsExtension.currentTestProvider` 를 넘깁니다 ([WebSocket / STOMP](messaging.md#websocket--stomp)) |
| ``Expected 3 stream events within PT5S but received 1. Increase `timeout`, or trigger the events in `whileStreaming`.`` | `performStream` 이 `eventCount` 개를 기다렸는데 덜 왔습니다. `whileStreaming` 안에서 이벤트를 일으키거나, `eventCount` 를 줄이거나, `timeout` 을 늘립니다 ([끝나지 않는 스트림](messaging.md#끝나지-않는-스트림)) |
| `--security expects name=bearer\|basic\|apiKey:<header\|query\|cookie>:<name>, got ...` 또는 `kardx.asyncApi.apiKeyHeader needs an HTTP header name` | 인증 방식 값의 형식이 틀렸습니다. `bearer`, `basic`, `apiKey:<header\|query\|cookie>:<이름>` 으로 씁니다 ([AsyncAPI 인증](messaging.md#asyncapi-인증)) |
| AsyncAPI Try it out: 토큰을 넣었는데 순수 WebSocket·SSE 연결이 거절된다 | 브라우저는 WebSocket 핸드셰이크와 `EventSource` 에 헤더를 붙일 수 없어, 헤더 자격 증명은 STOMP 로만 보냅니다. "WebSocket·SSE 는 쿼리 파라미터로 보내기"를 켜고, 서버가 그 파라미터의 토큰을 받게 하세요 ([AsyncAPI 인증](messaging.md#asyncapi-인증)) |
| 테스트가 문서화한 엔드포인트를 `apiDocsCoverage` 가 문서화하지 않았다고 한다 | 커버리지는 메서드·경로 템플릿이 같은 OpenAPI 조각(`resource.json`)이 있으면 문서화한 것으로 봅니다. OpenAPI 에서 뺀 테스트(`openApi { excluded = true }`, `openApi.enabled = false`)도 실제 요청 경로로 셉니다. 요청을 템플릿으로 만들었는지(`get("/api/users/{id}", 1)`. WebTestClient 의 `uri { }` 빌더는 실제 경로를 남깁니다), 메서드가 같은지, API 버전을 쓰면 문서화한 버전이 엔드포인트의 버전과 같은지 확인하세요 ([문서화 커버리지](checks.md#문서화-커버리지)) |
| 문서화하지 않은 게 분명한 엔드포인트가 커버리지 보고서에 없거나, `apiDocsCoverage` 가 `no endpoint inventory` 를 출력한다 | 목록에는 테스트 애플리케이션 컨텍스트에 들어 있는 것만 잡힙니다. standalone 설정(`standaloneSetup`, `bindToController`, `bindToRouterFunction`)은 읽을 컨텍스트가 없고, 함수형 라우트(`RouterFunction`, `router { }`)는 읽지 않으며, 슬라이스 테스트(`@WebMvcTest(UserController::class)`)는 고른 컨트롤러만 잡습니다. `@SpringBootTest` 문서화 테스트를 하나 두면 모든 컨트롤러가 목록에 들어갑니다 ([목록에 잡히지 않는 엔드포인트](checks.md#목록에-잡히지-않는-엔드포인트)) |
| operation 이 `oauth2`·`openIdConnect` 를 쓰는데 `openApiSecurity` 에 설정이 없다는 빌드 경고, Swagger UI 에 OAuth2 로그인이 없다 | 테스트는 `openApi { oauth2(...) }`·`openIdConnect(...)` 를 선언했지만 빌드에 flow 나 discovery URL 이 없어 보안 요구사항을 쓸 수 없습니다. `kardx { openApiSecurity { oauth2 { ... } } }` 를 더하세요 ([OAuth2 와 OpenID Connect](http-api.md#oauth2-와-openid-connect)) |
| 스펙에서 빠진 API 버전에 대한 빌드 경고, Swagger UI 에 엔드포인트의 최신 버전만 보인다 | 같은 경로·메서드를 여러 버전으로 문서화했고, 스펙 하나에는 가장 높은 버전만 남습니다. 버전마다 스펙을 나누려면 `apiVersioning { splitSpecs = true }` ([API 버전](openapi.md#api-버전)) |
| Kotlin 기본값(`val size: Int = 20`)이 `default` 로 나오지 않는다 | 기본값은 요청 추출(`parametersOf<T>()`, `requestFields<T>()`)에서만 읽고 `responseFields<T>()` 에서는 읽지 않습니다. 문자열·숫자·불리언·Enum 만, 그리고 라이브러리가 기본값으로 인스턴스를 만들 수 있을 때만 읽습니다. 아니면 그 항목에 `withDefaultValue "20"` 을 붙이세요 ([기본값·예시·제약 설명](http-api.md#기본값예시제약-설명)) |
| `MockMvc`·`WebTestClient` 테스트 메서드 파라미터에서 `ParameterResolutionException: Discovered multiple competing ParameterResolvers` | 직접 만든 JUnit 확장도 그 타입을 넣고, 이제 `AutoRestDocumentationExtension` 도 이 파라미터를 채웁니다. 문서화 테스트에서는 그 resolver 를 빼거나, 클라이언트를 파라미터로 받지 말고 `restDocsMockMvc(context)` 로 만든 필드에 둡니다 ([시작하기](getting-started.md#9-준비-코드-나누기-선택)) |

## 오류 코드

라이브러리의 오류·경고는 `kardx[KDX-xxx]` 로 시작하고 아래 표의 그 줄 주소로 끝난다. 문구가 바뀌어도 코드는
그대로이므로 코드로 찾는다. 백의 자리가 단계다: `0xx` 테스트 준비·설정, `1xx` 문서화, `2xx` OpenAPI, `3xx` 메시지
(WebSocket, STOMP, SSE), `4xx` Kotest, `5xx` 파일, `6xx` 시작 로그. (경고) 표시는 테스트를 실패시키지 않는다.

<!-- error-codes -->
| 코드 | 문제 | 해결 |
|---|---|---|
| <a id="kdx-001"></a>`KDX-001` | 알 수 없는 `kardx.*` 속성. 오타로 보고 무시한다 (경고). | [설정](configuration.md)에 있는 키로 고친다. 비슷한 키가 있으면 메시지가 알려 준다 |
| <a id="kdx-002"></a>`KDX-002` | `kardx.*` 속성 값이 잘못됐다 (모르는 값, 숫자·true/false 가 아님, 클래스 없음). | [설정](configuration.md)에 적힌 값 중 하나를 쓴다 (클래스 이름은 전체 이름이고 테스트 클래스패스에 있어야 한다) |
| <a id="kdx-003"></a>`KDX-003` | `response-wrapper.data-path` 만 있고 `response-wrapper.class` 가 없다. | `kardx.response-wrapper.class` 를 더하거나 `data-path` 를 지운다 |
| <a id="kdx-004"></a>`KDX-004` | `description-annotations`·`masked-annotations` 항목이 어노테이션이 아니거나 그런 속성이 없다. | 어노테이션 클래스만 적는다. 별칭(`swagger`, `jakarta`) 또는 `패키지.Annotation#속성` |
| <a id="kdx-005"></a>`KDX-005` | 테스트(yml)의 문서 언어와 Gradle 플러그인의 언어가 다르다 (경고). | yml(`kardx.language`)과 Gradle 플러그인(`kardx { language }`)의 언어를 같게 한다. 플러그인에만 정해도 된다 |
| <a id="kdx-006"></a>`KDX-006` | `springSecurityFilterChain` 빈이 있는데 `apply-spring-security` 를 정하지 않아 걸지 않았다 (경고). | 보안이 걸린 API 로 문서화하려면 `kardx.apply-spring-security: true`, 경고만 끄려면 `false` |
| <a id="kdx-007"></a>`KDX-007` | spring-security-test 가 없어 필터 체인을 일반 필터로 걸었다 (정보). | `with(jwt())`, `@WithMockUser` 등을 쓰려면 `testImplementation("org.springframework.security:spring-security-test")` 를 더한다 |
| <a id="kdx-008"></a>`KDX-008` | 클래스패스에 Jackson 매퍼가 없다. | Jackson(`spring-boot-starter-json` 또는 `jackson-databind`)을 더하거나 `RestDocsConfiguration.configure { extraction { objectMapper = ObjectMapperProvider { mapper } } }` 로 정한다 |
| <a id="kdx-009"></a>`KDX-009` | `objectMapper` 설정이 Jackson 매퍼가 아닌 것을 돌려줬다. | `objectMapper` 가 `ObjectMapper`(Jackson 2)나 `JsonMapper`(Jackson 3)를 돌려주게 한다 |
| <a id="kdx-010"></a>`KDX-010` | KDoc/Javadoc 설명 색인(`source-docs.json`)을 읽지 못했다 (경고). | Gradle 로 테스트를 돌려 `apiDocsSourceDocs` 가 색인을 다시 쓰게 한다(`./gradlew test`). IDE 에서는 테스트 실행을 Gradle 에 맡긴다 |
| <a id="kdx-011"></a>`KDX-011` | 테스트 확장이 만들지 않은 REST Docs 설정(Spring Boot `@AutoConfigureRestDocs` 로 주입받은 `MockMvc` 등)으로 문서화했다. 스니펫이 문서가 읽는 곳과 다른 곳에 생기면 실패, 같은 곳이면 경고한다. | `@AutoConfigureRestDocs` 를 지우고 라이브러리가 클라이언트를 만들게 한다: 테스트 메서드 `MockMvc`/`WebTestClient` 파라미터, 또는 `restDocsMockMvc(context)` ([시작하기](getting-started.md)) |
| <a id="kdx-012"></a>`KDX-012` | REST Docs 설정이 없는 클라이언트(주입받은 그대로의 `MockMvc`, `restDocs(provider)` 없는 클라이언트)로 문서화했다. | `restDocsMockMvc(context)`, `restDocsWebTestClient(context)` 로 만들거나 테스트 메서드 파라미터로 받는다. `RestTestClient` 는 `.restDocs(provider)` 를 더한다 |
| <a id="kdx-013"></a>`KDX-013` | 문서화 중인 테스트가 없는데 지금 테스트의 문맥을 찾았다 (확장 미등록, 테스트 밖에서 요청). | `@ExtendWith(AutoRestDocumentationExtension::class)`(Kotest: `RestDocsExtension`)를 등록하고 문서화 요청은 테스트 메서드 안에서 보낸다 |
| <a id="kdx-014"></a>`KDX-014` | `MockMvc`·`WebTestClient` 테스트 파라미터를 만들 Spring 테스트 컨텍스트가 없거나 종류가 맞지 않는다. | 클래스에 `@SpringBootTest`(`@WebMvcTest`, `@WebFluxTest`)를 붙인다. 서블릿 앱은 `MockMvc`, WebFlux 는 `WebTestClient` |
| <a id="kdx-101"></a>`KDX-101` | 이름만(또는 `FromParamTag` 로) 선언한 항목의 설명을 KDoc/Javadoc 에서 찾지 못했다. | 핸들러 메서드 KDoc/Javadoc 에 `@param <이름>` 을 더하고 실험 기능인 source docs 를 켜거나(`kardx { sourceDocs { enabled = true } }`, 기본 꺼짐) 테스트에서 설명한다 |
| <a id="kdx-102"></a>`KDX-102` | 응답 항목에 `FromParamTag` 를 썼다. `@param` 은 요청 항목만 설명한다. | 응답 항목은 테스트(`"id" means "..."`)나 필드 KDoc 으로 설명한다. `@param` 은 요청 항목만 설명한다 |
| <a id="kdx-103"></a>`KDX-103` | 파일 본문(`requestFile`·`responseFile`)과 필드·파트를 함께 선언했다. | 파일 본문과 필드 중 하나만 둔다. 파일 본문에는 필드가 없다. 멀티파트 파일은 `requestParts` 로만 설명한다 |
| <a id="kdx-104"></a>`KDX-104` | `requestFile`·`responseFile` 설명이 비었거나 두 번 선언했다. | `requestFile`·`responseFile` 에 비지 않은 설명을 주고 `andDocument` 블록마다 한 번만 선언한다 |
| <a id="kdx-105"></a>`KDX-105` | `errorResponse` 선언이 잘못됐다 (상태 범위, 빈 설명·contentType·예시, 중복). | 상태는 400~599, 설명·contentType·예시는 비지 않게, 상태마다 `errorResponse` 하나. 다른 상태는 그 상태를 받는 테스트로 문서화한다 |
| <a id="kdx-106"></a>`KDX-106` | `errorResponse` 예시가 JSON content type 인데 올바른 JSON 이 아니다. | 예시 글을 고치거나 `example(...)` 에 객체를 넘기거나 JSON 이 아닌 `contentType` 을 정한다 |
| <a id="kdx-107"></a>`KDX-107` | 타입에서 추출할 필드가 없다 (단일 값, Map). 경로를 주거나 그 섹션을 빼야 한다 (경고). | 경로를 주거나(`responseFieldsOf<T>("data")`) 본문을 직접 기술하거나 그 섹션을 뺀다 (경고) |
| <a id="kdx-108"></a>`KDX-108` | 필드·파라미터 목록에서 고른 이름이 그 타입에 없다. | `except`·`optionalAt`·`optionalUnder` 에 준 이름을 고친다. 메시지에 타입이 가진 이름 목록이 있다 |
| <a id="kdx-109"></a>`KDX-109` | 문서화한 내용이 실제 요청·응답·메시지와 맞지 않는다 (`DocumentationMismatchException`). | 메시지의 문서화한 목록과 실제 목록을 비교해 빠진 항목은 더하고 없는 항목은 지우거나 optional 로 표시한다 |
| <a id="kdx-110"></a>`KDX-110` | 응답 본문을 객체로 읽으려는데 본문이 비었다. | 본문이 없을 수 있으면 `andReturnEntity<T>()` 를 쓰고, 아니면 요청의 상태 코드를 확인한다 |
| <a id="kdx-201"></a>`KDX-201` | `andDocument { openApi { operationId } }` 값이 비었거나 공백을 담았다. | 공백 없는 `operationId` 를 쓴다 (`order-list`, `getOrder`) |
| <a id="kdx-202"></a>`KDX-202` | `openApi.operationIdResolver` 가 비었거나 공백을 담은 operationId 를 돌려줬다. | `openApi.operationIdResolver` 가 비지 않고 공백 없는 id 를 돌려주게 한다 |
| <a id="kdx-203"></a>`KDX-203` | 보안 scope 가 비었거나 공백을 담았다. | 빈 scope 를 지우고 scope 를 하나씩 넘긴다 (`scopes("read", "write")`) |
| <a id="kdx-204"></a>`KDX-204` | 경로 변수를 문서화하지 않아 OpenAPI 에 설명 없이 들어간다 (경고). | `andDocument` 블록에 `pathParameters("id")`(또는 설명과 함께)를 더한다 (경고) |
| <a id="kdx-301"></a>`KDX-301` | 테스트 밖에서 메시지를 문서화했다 (REST Docs 문맥 없음). | `AutoRestDocumentationExtension`(Kotest: `RestDocsExtension`)이 돌리는 테스트 메서드 안에서 `MessageDocumentation` 을 부른다 |
| <a id="kdx-302"></a>`KDX-302` | 정한 시간 안에 SSE 이벤트가 다 오지 않았다. | `timeout` 을 늘리거나 `eventCount` 를 줄이거나 `whileStreaming` 에서 이벤트를 일으킨다 |
| <a id="kdx-303"></a>`KDX-303` | STOMP 엔드포인트에 연결하지 못했다. | `@SpringBootTest(webEnvironment = RANDOM_PORT)` 와 `@LocalServerPort` 를 쓰고 엔드포인트 경로와 handshake 인터셉터를 확인한다 |
| <a id="kdx-304"></a>`KDX-304` | 정한 시간 안에 WebSocket·STOMP 메시지가 오지 않았다. | destination 과 핸들러가 메시지를 보내는지 확인한다. 핸들러가 느리면 `timeout` 을 늘린다 |
| <a id="kdx-401"></a>`KDX-401` | Kotest 리프 테스트 밖에서 문서화했다. | 리프 테스트(`then`, `it`, `test`) 안에서 문서화하고 스펙이나 프로젝트 설정에 `RestDocsExtension` 을 등록한다 |
| <a id="kdx-402"></a>`KDX-402` | Kotest 판과 확장 아티팩트(`-kotest`·`-kotest5`)가 맞지 않는다. | Kotest 6.x 는 `-kotest`, 5.x 는 `-kotest5` 를 쓰고 둘 중 하나만 둔다 |
| <a id="kdx-403"></a>`KDX-403` | 한 스펙의 두 테스트가 같은 스니펫 디렉터리 이름이 된다 (경고). | 정규화한 이름이 달라지도록 테스트 하나의 이름을 바꾼다 (경고) |
| <a id="kdx-404"></a>`KDX-404` | 실패한 Kotest 테스트의 스니펫을 정리하다 실패했다 (경고). | 스니펫 디렉터리 권한을 확인한다. 테스트 결과에는 영향이 없다 (경고) |
| <a id="kdx-501"></a>`KDX-501` | 다른 패키지의 같은 이름 테스트 클래스가 같은 문서·스니펫 디렉터리를 쓴다 (경고). | 테스트 클래스 하나의 이름을 바꾸거나 `index.packageName` 으로 다른 패키지를 문서화한다 (경고) |
| <a id="kdx-502"></a>`KDX-502` | 지난 스니펫을 지우지 못했다 (경고). | 스니펫 디렉터리의 파일을 잡고 있는 프로그램을 닫고 권한을 확인한다 (경고) |
| <a id="kdx-503"></a>`KDX-503` | 문서화 결과 파일을 쓰지 못했다. | 출력 디렉터리에 쓸 수 있는지, 디스크가 가득 차지 않았는지 확인한다 |
| <a id="kdx-504"></a>`KDX-504` | 문서화 커버리지용 엔드포인트 목록을 쓰지 못했다 (경고). | `build/kardx` 에 쓸 수 있는지 확인한다. 못 쓰면 커버리지가 `no endpoint inventory` 를 낸다 (경고) |
| <a id="kdx-505"></a>`KDX-505` | `@EnumCode` 에 적은 속성이 Enum 에 없다. | `@EnumCode` 의 속성 이름을 고치거나 비워 기본값을 쓴다 |
| <a id="kdx-506"></a>`KDX-506` | `@EnumCode` Enum 클래스를 읽지 못했다. | Enum 이 테스트 클래스패스에 있고 정적 초기화가 실패하지 않는지 확인한다 |
| <a id="kdx-507"></a>`KDX-507` | index 템플릿 파일이 없다. | 템플릿 파일을 만들거나 `kardx { index { template } }` 의 경로를 고친다 (기본 `src/docs/asciidoc/index-template.adoc`) |
| <a id="kdx-601"></a>`KDX-601` | 시작 로그의 언어 설정이 지원하지 않는 값이라 영어로 쓴다 (경고). | 앱의 `kardx.language` 에 지원하는 언어(`en`, `ko`)를 쓴다. 그동안은 영어로 쓴다 (경고) |
| <a id="kdx-602"></a>`KDX-602` | 앱 리소스에 문서가 없어 시작 로그에 주소 대신 안내를 쓴다. | `./gradlew apiDocs` 를 실행하거나 Gradle 로 앱을 띄운다 (`bootRun`, IntelliJ 기본 설정) |
<!-- /error-codes -->
