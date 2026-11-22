package io.github.kardx.gradle.dsl;

import java.time.Duration;
import java.util.regex.Pattern;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Nested;

/**
 * {@code kardx { }} 설정. 경로의 기본값은 대부분 Gradle 빌드 디렉터리 아래다. 직접 쓰는 파일인
 * {@link #getAsciidocSourceDirectory()}({@code src/docs/asciidoc}), {@code index.template}
 * ({@code src/docs/asciidoc/index-template.adoc}), {@code openApiCheck.baseline}({@code docs/openapi/openapi3.yaml})은
 * 프로젝트 디렉터리 기준이다.
 *
 * <pre>{@code
 * kardx {
 *     outputDirectory = layout.buildDirectory.dir("api-docs")
 *     index {
 *         title = "주문 API"
 *     }
 *
 *     asyncApi {
 *         title = "Chat API"
 *         server("stomp", "stomp://localhost:8080/ws")
 *         bearerAuth()
 *     }
 * }
 * }</pre>
 */
public abstract class KardxExtension {

    /**
     * REST Docs 스니펫 디렉터리. 테스트와 epages, Asciidoctor 에 같은 값을 넘긴다. 기본값 {@code build/generated-snippets}.
     * {@link #getCleanBeforeTest()} 가 통째로 지우므로 {@code src/} 안, 프로젝트 디렉터리, 다른 파일이 있는 디렉터리는 거부한다
     * (빌드 디렉터리 밖이면 플러그인이 쓴 디렉터리라는 표시 파일 {@code .kardx-output} 을 남긴다).
     */
    public abstract DirectoryProperty getSnippetsDirectory();

    /**
     * 테스트가 만든 AsciiDoc 문서 디렉터리. 기본값 {@code build/generated-asciidoc}. 직접 쓰는 문서의 위치는
     * {@link #getAsciidocSourceDirectory()} 다. 지우기 규칙은 {@link #getSnippetsDirectory()} 와 같다.
     */
    public abstract DirectoryProperty getAsciidocDirectory();

    /** 직접 쓴 AsciiDoc 문서 디렉터리 (index 템플릿, include 파일). 기본값 {@code src/docs/asciidoc} */
    public abstract DirectoryProperty getAsciidocSourceDirectory();

    /**
     * REST Docs HTML, OpenAPI, AsyncAPI 를 모으는 디렉터리. 기본값 {@code build/api-docs}. {@code apiDocs} 가 동기화하며
     * 다른 파일을 지우므로 지우기 규칙은 {@link #getSnippetsDirectory()} 와 같다.
     */
    public abstract DirectoryProperty getOutputDirectory();

    /** 테스트 전에 스니펫·AsciiDoc 디렉터리를 비울지 여부. 지운 테스트의 문서가 남지 않게 한다. 기본값 true */
    public abstract Property<Boolean> getCleanBeforeTest();

    /**
     * Asciidoctor 용 {@code spring-restdocs-asciidoctor} 버전.
     * Spring Boot 플러그인이 있으면 Boot BOM 버전을 쓰므로 정하지 않아도 된다. Asciidoctor 플러그인이 있고 Spring Boot
     * 플러그인이 없으면 꼭 정해야 한다 (없으면 의존성을 풀 때 빌드가 실패한다).
     */
    public abstract Property<String> getRestDocsVersion();

    /**
     * 앱을 실행할 때 문서를 자동으로 만들지 여부. 기본값 true.
     * IntelliJ 에서 main 을 실행할 때(Gradle 로 classes 만 빌드)와, 요청한 태스크가 {@code bootRun} 뿐일 때 그 앞에
     * apiDocs 를 실행한다.
     * 이때 문서 테스트가 실패해도 앱 실행은 막지 않는다.
     */
    public abstract Property<Boolean> getGenerateOnRun();

    /**
     * 앱이 뜰 때 API 문서 주소(REST Docs, Swagger UI, OpenAPI, AsyncAPI)를 INFO 로그로 남길지 여부. 기본값 true.
     * Spring Boot 앱이면 {@code kardx-boot} 를 {@link #getStartupLogScope()} 에 넣는다.
     * 앱 설정 {@code kardx.startup-log.enabled=false} 로도 끌 수 있다.
     */
    public abstract Property<Boolean> getStartupLog();

    /**
     * 시작 로그 모듈을 넣을 configuration. 기본값 {@code developmentOnly}: IDE 실행과 {@code bootRun} 에서만 로그가 나오고
     * 실행 jar 에는 들어가지 않는다. 실행 jar({@code java -jar})에서도 로그를 남기려면 {@code runtimeOnly}.
     * 프로젝트에 없는 configuration 이름이면 빌드가 실패한다.
     */
    public abstract Property<String> getStartupLogScope();

    /**
     * 생성 문서의 언어: {@code en}(기본값) 또는 {@code ko}. 대소문자는 가리지 않고, 다른 값이면 빌드가 실패한다.
     * 플러그인이 쓰는 문구(index 의 공통 오류 응답 절, OpenAPI 설명의 제약·쿠키 문구, 뷰어·집계 페이지)를 바꾸고,
     * 테스트 JVM 과 core CLI({@code apiDocsIndex}, {@code apiDocsAsyncApi})에 시스템 속성 {@code kardx.language}
     * 로, 앱 리소스 {@code docs-locations.properties} 에 {@code language} 로 넘긴다. Gradle 로그와 {@code apiDocsCheck}
     * 보고서는 언제나 영어다.
     *
     * <p>기본값을 convention 으로 두지 않는다: 정하지 않으면 아무것도 넘기지 않아 테스트 설정(application.yml)의
     * {@code kardx.language} 가 정하고, 플러그인 문구는 {@code en} 이다.
     *
     * <p>제목 기본값({@code index.title}, {@code asyncApi.title}, {@code aggregate.title})은 언어 문구가 아니라 프로젝트
     * 이름이라 언어를 바꿔도 그대로다.
     *
     * <pre>{@code
     * kardx {
     *     language = "ko"
     * }
     * }</pre>
     */
    public abstract Property<String> getLanguage();

    /**
     * Swagger UI 의 Authorize 창에서 한 번만 입력해 모든 요청에 붙일 헤더 이름. 기본값 없음.
     * OpenAPI 에서 헤더 파라미터 대신 {@code apiKey}(in: header) 보안 스키마가 된다.
     * 테스트 설정 {@code kardx.open-api.global-headers} 와 합쳐 쓴다.
     */
    public abstract SetProperty<String> getOpenApiGlobalHeaders();

    /**
     * 모든 OpenAPI operation 이 돌려줄 수 있는 공통 응답. 상태 코드 → 설명. 기본값 없음 (끔).
     * 빈 설명이면 표준 reason phrase({@code Unauthorized}) 를 쓴다.
     *
     * <ul>
     *   <li>{@code openapi3} 뒤에 각 operation 에 더한다. 테스트가 문서화한 상태 코드는 건드리지 않는다 (테스트 쪽이 이긴다)</li>
     *   <li>{@code 401}·{@code 403} 은 보안 요구사항(security)이 있는 operation 에만 더한다</li>
     *   <li>응답 본문은 {@link #getOpenApiCommonResponseSchema()} 스키마를 가리킨다. 정하지 않으면 스펙에서 오류 응답(4xx·5xx)이
     *       가장 많이 가리키는 스키마(보통 테스트 설정 {@code open-api.error-schema})를 쓰고, 그런 스키마가 없으면 설명만 넣는다</li>
     *   <li>REST Docs index 문서 끝에 "공통 오류 응답" 표를 한 번 넣는다</li>
     *   <li>Postman Collection 은 바꾸지 않는다</li>
     * </ul>
     *
     * <pre>{@code
     * kardx {
     *     openApiCommonResponses.put(401, "인증 토큰이 없거나 만료됨")
     *     openApiCommonResponses.put(500, "")   // Internal Server Error
     * }
     * }</pre>
     */
    public abstract MapProperty<Integer, String> getOpenApiCommonResponses();

    /**
     * {@link #getOpenApiCommonResponses()} 의 응답 본문 스키마 이름 ({@code components.schemas} 의 키). 기본값 없음:
     * 스펙에서 오류 응답이 가장 많이 가리키는 스키마를 쓴다. 정한 이름이 스펙에 없으면 경고하고 설명만 넣는다.
     */
    public abstract Property<String> getOpenApiCommonResponseSchema();

    /**
     * WebFlux 앱의 테스트 런타임 클래스패스에서 {@code org.springframework.boot:spring-boot-starter-web} 을 뺄지 여부.
     * 기본값 true.
     *
     * <p>epages({@code restdocs-api-spec-webtestclient})는 {@code spring-boot-starter-web} 을 runtime 으로 끌고 오고,
     * 테스트 클래스패스에 Spring MVC 가 있으면 {@code @SpringBootTest} 가 WebFlux 앱을 서블릿 앱으로 띄워
     * {@code No bean named 'webHandler'} 로 실패한다. 프로젝트가 WebFlux 앱이면({@code spring-boot-starter-webflux} 를
     * 선언했고 {@code spring-boot-starter-web}·{@code -websocket}, {@code spring-webmvc} 는 선언하지 않았으면)
     * {@code testRuntimeClasspath} 에서 뺀다. 선언된 의존성만 보고 판단하며 다른 configuration 을 미리 해석하지 않는다.
     * 라이브러리 본체는 {@code spring-restdocs-mockmvc} 를 끌고 오지 않으므로 그 밖의 exclude 는 필요 없다.
     */
    public abstract Property<Boolean> getExcludeServletStarterForWebFlux();

    /**
     * 라이브러리와 테스트 클라이언트 의존성을 알아서 넣을지 여부. 기본값 true. 이미 선언한 모듈은 건드리지 않는다.
     * <ul>
     *   <li>{@code compileOnly}: {@code io.github.kardx-project:kardx-annotations} (플러그인과 같은 버전)</li>
     *   <li>{@code testImplementation}: {@code io.github.kardx-project:kardx} (플러그인 버전). 테스트 의존성에 적힌
     *       {@code io.kotest} 버전으로 Kotest 판을 알 수 있으면 그 판의 확장({@code -kotest}: 6.x, {@code -kotest5}: 5.x)</li>
     *   <li>{@code testImplementation}: MVC 앱({@code spring-boot-starter-web}·{@code -websocket},
     *       {@code spring-webmvc})이면 {@code spring-restdocs-mockmvc}, WebFlux 앱({@code spring-boot-starter-webflux}, {@code spring-webflux})이면
     *       {@code spring-restdocs-webtestclient}. 둘 다 선언하면 MVC. 버전은 Spring Boot BOM 이 정한다</li>
     *   <li>epages 플러그인이 있으면 {@code com.epages:restdocs-api-spec-mockmvc}·{@code -webtestclient} (epages 플러그인과 같은
     *       버전)</li>
     * </ul>
     * 실제로는 {@code compileClasspath}·{@code testCompileClasspath}·{@code testRuntimeClasspath} 가 해석될 때 그 classpath 에
     * 더한다. 끄면 위 의존성을 직접 선언한다.
     *
     * <pre>{@code
     * kardx {
     *     addDependencies = false
     * }
     * }</pre>
     */
    public abstract Property<Boolean> getAddDependencies();

    /**
     * {@code asciidoctor} 가 HTML 변환에 띄우는 JVM 의 인자. 기본값 {@code ["-XX:TieredStopAtLevel=1"]}: 변환 JVM 은 몇 초만
     * 살므로 C2 컴파일을 하지 않는 편이 빠르다 (Boot 4 샘플 7.2 초 → 6.8 초). 문서가 매우 많아 변환이 오래 걸리면 빈 목록으로
     * 끈다. {@code asciidoctor} 를 다른 실행 방식({@code IN_PROCESS})으로 돌리면 쓰이지 않는다. 다른 JVM 인자는
     * {@code tasks.asciidoctor { jvm { } }} 에 그대로 더하면 된다.
     *
     * <pre>{@code
     * kardx {
     *     asciidoctorJvmArgs = []                    // 끈다 (JVM 기본값)
     *     asciidoctorJvmArgs.add("-Xmx1g")           // 기본값에 더한다
     * }
     * }</pre>
     */
    public abstract ListProperty<String> getAsciidoctorJvmArgs();

    /** index 문서 설정 ({@code apiDocsIndex} 태스크). {@link #index(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract Index getIndex();

    /** AsyncAPI 문서 설정 ({@code apiDocsAsyncApi} 태스크). {@link #asyncApi(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract AsyncApi getAsyncApi();

    /** 앱 정적 리소스에 문서를 넣는 설정. {@link #bootJar(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract BootJar getBootJar();

    /** 기준 스펙과 비교하는 설정 ({@code apiDocsCheck}, {@code apiDocsBaseline}). {@link #openApiCheck(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract OpenApiCheck getOpenApiCheck();

    /** 정적 OpenAPI 뷰어 페이지 설정. {@link #openApiViewer(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract OpenApiViewer getOpenApiViewer();

    /** Postman Collection 출력 설정. {@link #postmanCollection(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract PostmanCollection getPostmanCollection();

    /** 여러 프로젝트의 문서를 모으는 설정 ({@code apiDocsAggregate}). {@link #aggregate(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract Aggregate getAggregate();

    /** WireMock stub 생성 설정. {@link #wireMock(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract WireMock getWireMock();

    /** 스펙을 디렉터리로 내보내고 Maven 배포에 붙이는 설정 ({@code apiDocsExport}). {@link #export(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract Export getExport();

    /** 스펙을 HTTP 로 올리는 설정 ({@code apiDocsUpload}). {@link #upload(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract Upload getUpload();

    /** 문서화 범위 보고서 설정 ({@code apiDocsCoverage}). {@link #coverage(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract Coverage getCoverage();

    /** OAuth2·OpenID Connect 보안 스키마 설정. {@link #openApiSecurity(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract OpenApiSecurity getOpenApiSecurity();

    /** IntelliJ HTTP Client 파일 출력 설정 ({@code apiDocsHttpClient}). {@link #httpClient(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract HttpClient getHttpClient();

    /** Spring Framework 7 API 버전 설정. {@link #apiVersioning(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract ApiVersioning getApiVersioning();

    /**
     * KDoc·Javadoc 을 설명으로 쓰는 설정 ({@code apiDocsSourceDocs}, 실험 기능, 기본 꺼짐). {@link #sourceDocs(Action)} 블록으로
     * 바꾼다.
     */
    @Nested
    public abstract SourceDocs getSourceDocs();

    /**
     * OpenAPI 최상위 메타데이터 ({@code info.description}·contact·license·termsOfService, {@code externalDocs}, 태그 설명·순서,
     * {@code x-tagGroups}). {@link #openApiInfo(Action)} 블록으로 바꾼다.
     */
    @Nested
    public abstract OpenApiInfo getOpenApiInfo();

    /** REST Docs HTML 변환 설정. {@link #html(Action)} 블록으로 바꾼다. */
    @Nested
    public abstract Html getHtml();

    /** OpenAPI 최상위 메타데이터 설정. */
    public void openApiInfo(Action<? super OpenApiInfo> action) {
        action.execute(getOpenApiInfo());
    }

    /** REST Docs HTML 변환 설정. */
    public void html(Action<? super Html> action) {
        action.execute(getHtml());
    }

    /** KDoc·Javadoc 설명 설정 (실험 기능, 기본 꺼짐). */
    public void sourceDocs(Action<? super SourceDocs> action) {
        action.execute(getSourceDocs());
    }

    /** index 문서 설정. */
    public void index(Action<? super Index> action) {
        action.execute(getIndex());
    }

    /** AsyncAPI 설정. */
    public void asyncApi(Action<? super AsyncApi> action) {
        action.execute(getAsyncApi());
    }

    /** 실행 jar 에 문서를 넣는 설정. */
    public void bootJar(Action<? super BootJar> action) {
        action.execute(getBootJar());
    }

    /** 기준 스펙과 비교하는 설정. */
    public void openApiCheck(Action<? super OpenApiCheck> action) {
        action.execute(getOpenApiCheck());
    }

    /** 정적 OpenAPI 뷰어 설정. */
    public void openApiViewer(Action<? super OpenApiViewer> action) {
        action.execute(getOpenApiViewer());
    }

    /** Postman Collection 설정. */
    public void postmanCollection(Action<? super PostmanCollection> action) {
        action.execute(getPostmanCollection());
    }

    /** 여러 프로젝트의 문서를 모으는 설정. */
    public void aggregate(Action<? super Aggregate> action) {
        action.execute(getAggregate());
    }

    /** WireMock stub 설정. */
    public void wireMock(Action<? super WireMock> action) {
        action.execute(getWireMock());
    }

    /** 스펙 내보내기·Maven 배포 설정. */
    public void export(Action<? super Export> action) {
        action.execute(getExport());
    }

    /** 스펙 업로드 설정. */
    public void upload(Action<? super Upload> action) {
        action.execute(getUpload());
    }

    /** 문서화 범위 보고서 설정. */
    public void coverage(Action<? super Coverage> action) {
        action.execute(getCoverage());
    }

    /** OAuth2·OpenID Connect 보안 스키마 설정. */
    public void openApiSecurity(Action<? super OpenApiSecurity> action) {
        action.execute(getOpenApiSecurity());
    }

    /** IntelliJ HTTP Client 파일 설정. */
    public void httpClient(Action<? super HttpClient> action) {
        action.execute(getHttpClient());
    }

    /** API 버전 설정. */
    public void apiVersioning(Action<? super ApiVersioning> action) {
        action.execute(getApiVersioning());
    }

    /**
     * 테스트 클래스별 문서를 index 하나로 묶는다 ({@code apiDocsIndex}, 결과는 {@code /docs/index.html}). 설정하지 않아도 된다.
     * 기본은 문서를 만든 모든 테스트 클래스를 {@code @Order}·{@code @DisplayName} 순서로 넣는다 (테스트가 문서와 함께 남긴 목록을
     * 읽으므로 클래스패스를 훑지 않는다). 템플릿 파일이 없으면 {@link #getTitle()} 을 제목으로 하는 내장 템플릿을 쓴다.
     *
     * <pre>{@code
     * index {
     *     title = "주문 API"                                  // 내장 템플릿의 제목. 기본값 프로젝트 이름
     *     // 아래 둘은 함께 정한다 (예전 방식): 그 패키지에서 기본 클래스를 상속한 테스트와 Kotest 스펙만 넣는다
     *     packageName = "com.example"
     *     baseClass = "com.example.support.ApiDocsTest"
     * }
     * }</pre>
     */
    public abstract static class Index {
        /**
         * {@code // restdocs:api-index} 줄이 있는 템플릿. 기본값 {@code src/docs/asciidoc/index-template.adoc}.
         * 그 줄 자리에 테스트 클래스별 링크가 들어가고, 줄이 없으면 템플릿 끝에 붙는다. 파일이 없으면 내장 템플릿을 쓴다.
         */
        public abstract RegularFileProperty getTemplate();

        /** 템플릿 파일이 없을 때 쓰는 내장 템플릿의 제목 ({@code = 제목}). 기본값 프로젝트 이름 */
        public abstract Property<String> getTitle();

        /**
         * 문서화 테스트를 찾을 패키지. 하위 패키지도 찾는다. {@link #getBaseClass()} 와 함께 정한다. 기본값 없음
         * (문서를 만든 모든 테스트 클래스).
         */
        public abstract Property<String> getPackageName();

        /** 문서화 테스트의 기본 클래스 이름. 이 클래스를 상속한 구체 클래스를 링크한다. {@link #getPackageName()} 과 함께 정한다. */
        public abstract Property<String> getBaseClass();
    }

    /** WebSocket·STOMP·SSE 메시지로 AsyncAPI 문서를 만든다. 문서화한 메시지가 없으면 파일을 만들지 않는다. */
    public abstract static class AsyncApi {
        private static final String BEARER_AUTH = "bearerAuth";
        private static final String BASIC_AUTH = "basicAuth";
        private static final String API_KEY_AUTH = "apiKeyAuth";
        /** HTTP 헤더 이름 (RFC 9110 token). */
        private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

        /** AsyncAPI 문서를 만들지 여부. false 이면 {@code apiDocsAsyncApi} 를 건너뛰고 {@code apiDocs} 결과에서도 빠진다. 기본값 true */
        public abstract Property<Boolean> getEnabled();

        /** AsyncAPI {@code info.title} 과 뷰어 HTML 제목. 기본값 프로젝트 이름 */
        public abstract Property<String> getTitle();

        /** AsyncAPI {@code info.version}. 기본값 프로젝트 버전 */
        public abstract Property<String> getVersion();

        /** AsyncAPI {@code info.description}. 기본값 없음 (넣지 않는다) */
        public abstract Property<String> getDescription();

        /**
         * AsyncAPI {@code servers}. 서버 이름 → {@code protocol://host[/path]}.
         * 뷰어의 Try it out 이 연결할 주소다. {@link #server(String, String)} 로 하나씩 더한다.
         */
        public abstract MapProperty<String, String> getServers();

        /**
         * 결과 파일 경로 (확장자 제외, 문서 디렉터리 기준). 기본값 {@code asyncapi} → {@code asyncapi.yaml}, {@code asyncapi.html}.
         * 하위 디렉터리도 된다: {@code async/index} → {@code async/index.yaml}, {@code async/index.html}
         * (앱에서 {@code /docs/async/index.html}). 앱 시작 로그는 플러그인이 남긴 이 이름을 읽으므로 앱 설정을 맞추지 않아도 된다.
         */
        public abstract Property<String> getFileName();

        /**
         * AsyncAPI {@code components.securitySchemes}. 스키마 이름 → core CLI 의 {@code --security} 값
         * ({@code bearer}, {@code basic}, {@code apiKey:header:<헤더 이름>}). 기본값 없음 (넣지 않는다).
         * {@link #bearerAuth()}, {@link #basicAuth()}, {@link #apiKeyHeader(String)} 로 더한다.
         */
        public abstract MapProperty<String, String> getSecuritySchemes();

        /** 서버를 더한다. {@code server("stomp", "stomp://localhost:8080/ws")} */
        public void server(String name, String url) {
            getServers().put(name, url);
        }

        /** {@code Authorization: Bearer <토큰>} 인증 스키마({@code bearerAuth})를 더한다. */
        public void bearerAuth() {
            getSecuritySchemes().put(BEARER_AUTH, "bearer");
        }

        /** HTTP Basic 인증 스키마({@code basicAuth})를 더한다. */
        public void basicAuth() {
            getSecuritySchemes().put(BASIC_AUTH, "basic");
        }

        /**
         * {@code headerName} 헤더로 키를 보내는 인증 스키마({@code apiKeyAuth})를 더한다. {@code apiKeyHeader("X-API-KEY")}.
         * 스키마 이름이 하나라 여러 번 부르면 마지막 헤더를 쓴다.
         */
        public void apiKeyHeader(String headerName) {
            if (headerName == null || !HEADER_NAME.matcher(headerName).matches()) {
                throw new GradleException("kardx.asyncApi.apiKeyHeader needs an HTTP header name such as "
                    + "'X-API-KEY', got '" + headerName + "'");
            }

            getSecuritySchemes().put(API_KEY_AUTH, "apiKey:header:" + headerName);
        }
    }

    /**
     * 문서를 앱의 정적 리소스({@code build/resources/main/<path>})로 넣는다.
     * 실행 jar, {@code bootRun}, IDE 실행 모두에서 {@code /docs/index.html} 로 열린다. 운영 앱에서도 열리므로, 민감 헤더를
     * 가리지 않고 만든 문서가 들어가면 빌드가 경고한다 (문서 {@code docs/build.md} 의 "운영 노출").
     */
    public abstract static class BootJar {
        /**
         * 문서를 {@code build/resources/main/<path>} 로 복사할지 여부. 기본값 true. 이 값은 {@code bootJar} 가
         * {@code apiDocs} 에 의존할지와, 앱 시작 로그가 읽는 {@code docs-locations.properties} 를 쓸지(false 면 지운다)도 정한다.
         * false 로 바꾸면 다음 {@code apiDocs} 가 지난 실행이 복사한 문서를 지운다.
         */
        public abstract Property<Boolean> getEnabled();

        /**
         * 리소스 안의 경로. 기본값 {@code static/docs} (앱에서 {@code /docs/index.html}). 앱 시작 로그는 플러그인이 남긴 이 경로를
         * 읽으므로 앱 설정 {@code kardx.docs-path} 를 맞추지 않아도 된다. 비었거나 리소스 밖({@code ../x}, 절대 경로)이거나
         * 정적 위치 자체({@code static}, {@code public}, {@code resources}, {@code META-INF/resources})면 빌드가 실패한다.
         * 플러그인은 이 경로에 자기가 복사한 파일만 바꾸거나 지우고, 앱이 같은 디렉터리에 둔 파일은 그대로 둔다.
         * 경로를 바꾸면 이전 경로에 복사한 문서를 지운다.
         */
        public abstract Property<String> getPath();
    }

    /**
     * 저장소에 커밋해 둔 기준 OpenAPI 스펙과 새로 만든 스펙을 비교한다 (epages 플러그인이 있을 때).
     * {@code ./gradlew apiDocsCheck} 가 비교하고, {@code ./gradlew apiDocsBaseline} 이 기준을 새 스펙으로 바꾼다.
     * 변경 요약은 콘솔과 {@code build/reports/kardx/openapi-changes.txt} 에 나온다.
     *
     * <pre>{@code
     * openApiCheck {
     *     baseline = layout.projectDirectory.file("docs/openapi/openapi3.yaml") // 기본값
     *     failOn = "breaking"   // 하위 호환을 깨는 변경만 실패, 나머지는 경고
     *     runOnCheck = true     // ./gradlew check 에도 넣는다
     *     requireBaseline = true // 기준 파일이 없으면 실패 (기본값)
     * }
     * }</pre>
     */
    public abstract static class OpenApiCheck {
        /** 기준 스펙 파일 (YAML 또는 JSON). 기본값 {@code docs/openapi/openapi3.yaml} (프로젝트 디렉터리 기준) */
        public abstract RegularFileProperty getBaseline();

        /**
         * 실패 조건. {@code any}(기본값): 어떤 변경이든 실패, {@code breaking}: 하위 호환을 깨는 변경만 실패하고 나머지는
         * 경고, {@code none}: 실패하지 않고 요약만 출력한다.
         */
        public abstract Property<String> getFailOn();

        /** {@code check} 태스크가 {@code apiDocsCheck} 도 실행할지 여부. 기본값 false */
        public abstract Property<Boolean> getRunOnCheck();

        /**
         * 기준 스펙 파일이 없을 때 실패할지 여부. 기본값 true: {@code failOn} 이 {@code none} 이 아니면 기준이 없을 때 실패하고
         * {@code apiDocsBaseline} 을 실행하라고 알려 준다. false 면 예전처럼 {@code failOn = "any"} 일 때만 실패하고
         * {@code breaking} 은 경고만 한다.
         */
        public abstract Property<Boolean> getRequireBaseline();
    }

    /**
     * 생성한 OpenAPI 스펙을 보여 주는 정적 HTML 페이지를 만든다. 스펙을 페이지에 넣으므로 파일로 열어도 보이고,
     * 뷰어 스크립트는 CDN(unpkg, 버전 고정)에서 받는다. 결과는 {@code build/api-docs/<fileName>} 과 앱의
     * {@code /docs/<fileName>} 이다.
     *
     * <pre>{@code
     * openApiViewer {
     *     enabled = true
     *     type = "scalar"            // 기본값 redoc
     *     fileName = "reference.html" // 기본값 openapi.html
     *     title = "주문 API"          // 기본값: 스펙의 info.title
     * }
     * }</pre>
     */
    public abstract static class OpenApiViewer {
        /** 뷰어 페이지를 만들지 여부. 기본값 false */
        public abstract Property<Boolean> getEnabled();

        /** 뷰어 종류: {@code redoc}(기본값, 읽기 전용 3단 레이아웃) 또는 {@code scalar}(요청 보내기 포함) */
        public abstract Property<String> getType();

        /**
         * 결과 파일 경로 (문서 디렉터리 기준, {@code .html}). 기본값 {@code openapi.html}. 앱 시작 로그는 플러그인이 남긴 이
         * 이름을 읽는다.
         */
        public abstract Property<String> getFileName();

        /** 페이지 제목. 기본값: 스펙의 {@code info.title} */
        public abstract Property<String> getTitle();
    }

    /**
     * epages 의 {@code postman} 태스크 결과(Postman Collection v2.1 JSON)를 {@code apiDocs} 에 넣는다.
     * 파일 이름·제목·baseUrl 은 epages 의 {@code postman { }} 설정을 따른다 (기본 {@code postman-collection.json}).
     */
    public abstract static class PostmanCollection {
        /** {@code apiDocs} 가 {@code postman} 을 실행하고 결과를 모을지 여부. 기본값 false */
        public abstract Property<Boolean> getEnabled();
    }

    /**
     * 여러 서브프로젝트의 문서를 이 프로젝트에 모은다 ({@code ./gradlew apiDocsAggregate}).
     * 모을 프로젝트는 {@code dependencies { restdocsAggregation(project(":order")) }} 로 정한다.
     * 결과는 {@link #getOutputDirectory()} 에 프로젝트별 디렉터리, 병합 스펙 {@code openapi3.yaml}, 목록 {@code index.html}.
     */
    public abstract static class Aggregate {
        /** 병합 스펙의 {@code info.title} 과 목록 페이지 제목. 기본값 프로젝트 이름 */
        public abstract Property<String> getTitle();

        /** 병합 스펙의 {@code info.version}. 기본값 프로젝트 버전 */
        public abstract Property<String> getVersion();

        /** 병합 스펙의 {@code info.description}. 기본값 없음 */
        public abstract Property<String> getDescription();

        /**
         * 결과 디렉터리. 기본값 {@code build/api-docs-aggregate}. 실행할 때마다 비우므로 지우기 규칙은
         * {@link KardxExtension#getSnippetsDirectory()} 와 같다 (빌드 디렉터리 밖의 {@code docs/} 는 비어 있거나
         * 표시 파일 {@code .kardx-output} 이 있어야 한다).
         */
        public abstract DirectoryProperty getOutputDirectory();
    }

    /**
     * 문서화 테스트가 요청·응답마다 WireMock 매핑 JSON 을 만들게 한다 (라이브러리가 직접 쓴다, WireMock 의존성 없음).
     * 결과는 {@code build/generated-snippets/wiremock/mappings/<class-name>/<method-name>.json} 이고,
     * {@code ./gradlew apiDocsStubsJar} 가 {@code build/libs/<name>-<version>-stubs.jar} 로 묶는다.
     */
    public abstract static class WireMock {
        /** 매핑을 만들지 여부. 테스트에 {@code kardx.wire-mock-stubs=true} 를 넘긴다. 기본값 false */
        public abstract Property<Boolean> getEnabled();
    }

    /**
     * {@code apiDocs} 결과 중 고른 것을 정한 디렉터리로 복사한다 ({@code ./gradlew apiDocsExport}). 빌드에 자동으로 붙지 않는다.
     * 파일 이름에는 {@code {name}}(프로젝트 이름), {@code {version}}(프로젝트 버전), {@code {ext}}(스펙 형식의 확장자),
     * {@code {type}}({@code openapi}·{@code asyncapi})을 쓸 수 있다.
     *
     * <p>{@link #getPublishSpecs()} 를 켜고 {@code maven-publish} 플러그인이 있으면 고른 스펙을 Maven 배포물에 붙인다
     * (classifier {@code openapi}·{@code asyncapi}, {@code publish} 가 {@code <name>-<version>-openapi.yaml} 을 올린다).
     *
     * <pre>{@code
     * export {
     *     directory = layout.buildDirectory.dir("exported-specs")
     *     openApiFileName = "{name}-{version}.{ext}"
     *     asyncApi = true
     *     publishSpecs = true
     * }
     * }</pre>
     */
    public abstract static class Export {
        /** 내보낼 디렉터리. 기본값 없음: 정하지 않고 {@code apiDocsExport} 를 실행하면 실패한다. 다른 파일과 섞이지 않는 디렉터리를 쓴다 */
        public abstract DirectoryProperty getDirectory();

        /** OpenAPI 스펙(epages {@code openapi3} 결과, 후처리 뒤)을 넣을지 여부. 기본값 true */
        public abstract Property<Boolean> getOpenApi();

        /** AsyncAPI 스펙({@code apiDocsAsyncApi} 결과)을 넣을지 여부. 기본값 false */
        public abstract Property<Boolean> getAsyncApi();

        /** {@code apiDocs} 디렉터리 전체(HTML 포함)를 넣을지 여부. 기본값 false */
        public abstract Property<Boolean> getHtml();

        /** OpenAPI 파일 이름 (디렉터리 기준 상대 경로). 기본값 {@code {name}-openapi3.{ext}} */
        public abstract Property<String> getOpenApiFileName();

        /** AsyncAPI 파일 이름 (디렉터리 기준 상대 경로). 기본값 {@code {name}-asyncapi.{ext}} */
        public abstract Property<String> getAsyncApiFileName();

        /** {@link #getHtml()} 을 켰을 때 문서 디렉터리를 복사할 하위 디렉터리 이름. 기본값 {@code {name}-api-docs} */
        public abstract Property<String> getHtmlDirectoryName();

        /**
         * {@code maven-publish} 가 있으면 고른 스펙({@link #getOpenApi()}, {@link #getAsyncApi()})을 Maven 배포물에 붙일지 여부.
         * 기본값 false. HTML 은 붙이지 않는다.
         */
        public abstract Property<Boolean> getPublishSpecs();

        /**
         * 스펙을 붙일 Maven 배포물 이름 ({@code publishing.publications} 의 이름). 기본값 없음: 모든 {@code MavenPublication}
         * (Gradle 플러그인 마커 배포물은 뺀다).
         */
        public abstract Property<String> getPublicationName();
    }

    /**
     * 고른 스펙을 HTTP 로 올린다 ({@code ./gradlew apiDocsUpload}). 빌드에 자동으로 붙지 않고, 실행할 때마다 보낸다.
     * JDK HTTP 클라이언트만 쓴다. 2xx 가 아니면 상태 코드와 응답 본문 앞부분으로 실패한다. 헤더 값은 로그·오류 메시지에 남기지
     * 않는다 (이름만 남긴다). 리다이렉트는 따라가지 않는다 (헤더가 다른 서버로 가지 않게).
     *
     * <p>URL 에는 {@code {name}}, {@code {version}}, {@code {type}}({@code openapi}·{@code asyncapi}),
     * {@code {ext}} 를 쓸 수 있다. 두 스펙을 함께 올리면 {@code {type}} 을 쓰거나 {@link #getAsyncApiUrl()} 을 정해 주소를
     * 나눈다.
     *
     * <pre>{@code
     * upload {
     *     url = "https://registry.example.com/apis/{name}/{version}/{type}"
     *     headers.put("Authorization", providers.environmentVariable("API_REGISTRY_TOKEN").map { "Bearer $it" })
     * }
     * }</pre>
     */
    public abstract static class Upload {
        /** OpenAPI 스펙을 올릴 주소 (http·https). 기본값 없음: 정하지 않고 실행하면 실패한다. {@link #getAsyncApiUrl()} 의 기본값이기도 하다 */
        public abstract Property<String> getUrl();

        /** AsyncAPI 스펙을 올릴 주소. 기본값 {@link #getUrl()} */
        public abstract Property<String> getAsyncApiUrl();

        /** HTTP 메서드: {@code PUT}(기본값) 또는 {@code POST} */
        public abstract Property<String> getMethod();

        /**
         * 요청 헤더. 값은 보통 {@code providers.environmentVariable(...)}·{@code providers.gradleProperty(...)} 로 넣어 빌드
         * 스크립트에 비밀값을 적지 않는다. 실행할 때 읽는다. {@code Content-Type} 을 넣으면 파일 확장자로 정한 값
         * ({@code application/yaml}, {@code application/json}) 대신 쓴다.
         */
        public abstract MapProperty<String, String> getHeaders();

        /** OpenAPI 스펙을 올릴지 여부. 기본값 true */
        public abstract Property<Boolean> getOpenApi();

        /** AsyncAPI 스펙을 올릴지 여부. 기본값 false */
        public abstract Property<Boolean> getAsyncApi();

        /** 연결·응답 대기 시간. 기본값 30초 */
        public abstract Property<Duration> getTimeout();

        /**
         * 연결 실패, 429, 5xx 일 때 다시 보낼 횟수. 기본값 0. 1초, 2초, 4초… 기다린 뒤 다시 보낸다 (한 번에 30초까지)
         */
        public abstract Property<Integer> getRetries();
    }

    /**
     * 앱의 엔드포인트 중 문서화한 것의 비율을 보고한다 ({@code apiDocsCoverage}, {@code apiDocs} 에 포함).
     * 문서화 테스트가 Spring 컨텍스트마다 남긴 엔드포인트 목록({@code <snippets>/.kardx/endpoints/*.json})과
     * 문서화한 operation({@code resource.json}, epages 플러그인 필요)을 비교한다. 경로 변수 이름은 보지 않는다
     * ({@code {id}} == {@code {userId}}). 결과는 {@code build/api-docs/coverage/coverage.json}·{@code coverage.md}.
     *
     * <p>{@code /error}, {@code /actuator/**}, springdoc({@code /v3/api-docs/**}, {@code /v3/api-docs.yaml},
     * {@code /swagger-ui/**}, {@code /swagger-ui.html})과 문서 경로({@code bootJar.path}, 기본 {@code /docs/**})는
     * 언제나 뺀다. {@link #exclude(String...)} 는 여기에 더한다.
     *
     * <pre>{@code
     * coverage {
     *     failOnUndocumented = true
     *     exclude("/internal/**", "/health")
     * }
     * }</pre>
     */
    public abstract static class Coverage {
        /** 보고서를 만들지 여부. 기본값 true */
        public abstract Property<Boolean> getEnabled();

        /** 문서화하지 않은 엔드포인트가 있으면 빌드를 실패시킬지 여부. 기본값 false (보고만 한다) */
        public abstract Property<Boolean> getFailOnUndocumented();

        /** 보고에서 뺄 경로 패턴 (Ant 형식: {@code *} 는 경로 한 칸, {@code **} 는 여러 칸). 기본 제외 경로에 더한다 */
        public abstract ListProperty<String> getExcludes();

        /** 보고에서 뺄 경로 패턴을 더한다. {@code exclude("/internal/**")} */
        public void exclude(String... patterns) {
            getExcludes().addAll(patterns);
        }
    }

    /**
     * 테스트의 {@code openApi { oauth2("users:read") }}·{@code openIdConnect(...)} 가 가리킬 OpenAPI 보안 스키마
     * ({@code components.securitySchemes} 의 {@code oauth2}, {@code openIdConnect})를 정한다. 테스트가 쓰는데 정하지 않은
     * 스키마는 유효한 OpenAPI 를 만들 수 없으므로 경고하고 그 operation 의 보안 요구사항을 뺀다.
     *
     * <pre>{@code
     * openApiSecurity {
     *     oauth2 {
     *         clientCredentials("https://auth.example.com/oauth/token")
     *         authorizationCode("https://auth.example.com/authorize", "https://auth.example.com/oauth/token")
     *         scope("users:read", "Read users")
     *     }
     *     openIdConnect("https://auth.example.com/.well-known/openid-configuration")
     * }
     * }</pre>
     */
    public abstract static class OpenApiSecurity {
        /** {@code oauth2} 보안 스키마. {@link #oauth2(Action)} 블록으로 바꾼다. */
        @Nested
        public abstract OAuth2 getOauth2();

        /** {@code openIdConnect} 보안 스키마의 {@code openIdConnectUrl}. 기본값 없음 (스키마를 넣지 않는다) */
        public abstract Property<String> getOpenIdConnectUrl();

        /** {@code oauth2} 보안 스키마 설정. */
        public void oauth2(Action<? super OAuth2> action) {
            action.execute(getOauth2());
        }

        /** {@code openIdConnect} 보안 스키마를 더한다. {@code url} 은 OpenID Provider 설정 문서 주소다. */
        public void openIdConnect(String url) {
            getOpenIdConnectUrl().set(url);
        }
    }

    /**
     * OAuth2 보안 스키마의 flow 와 scope. flow 를 하나도 정하지 않으면 스키마를 넣지 않는다.
     * flow 는 {@link #getFlows()} 에 {@code <flow>.<주소 종류>} → 주소로 담긴다 ({@code clientCredentials.tokenUrl}).
     */
    public abstract static class OAuth2 {
        private static final String TOKEN_URL = ".tokenUrl";
        private static final String AUTHORIZATION_URL = ".authorizationUrl";

        /** {@code <flow>.<tokenUrl|authorizationUrl|refreshUrl>} → 주소. 아래 메서드로 더한다 */
        public abstract MapProperty<String, String> getFlows();

        /** scope 이름 → 설명. 테스트가 쓴 scope 중 여기에 없는 것은 빈 설명으로 더한다 */
        public abstract MapProperty<String, String> getScopes();

        /** client credentials flow 를 더한다. */
        public void clientCredentials(String tokenUrl) {
            getFlows().put("clientCredentials" + TOKEN_URL, tokenUrl);
        }

        /** authorization code flow 를 더한다. */
        public void authorizationCode(String authorizationUrl, String tokenUrl) {
            getFlows().put("authorizationCode" + AUTHORIZATION_URL, authorizationUrl);
            getFlows().put("authorizationCode" + TOKEN_URL, tokenUrl);
        }

        /** resource owner password flow 를 더한다. */
        public void password(String tokenUrl) {
            getFlows().put("password" + TOKEN_URL, tokenUrl);
        }

        /** implicit flow 를 더한다. */
        public void implicit(String authorizationUrl) {
            getFlows().put("implicit" + AUTHORIZATION_URL, authorizationUrl);
        }

        /** 모든 flow 에 넣을 scope 와 설명을 더한다. */
        public void scope(String name, String description) {
            getScopes().put(name, description);
        }
    }

    /**
     * 문서화한 요청을 IntelliJ HTTP Client 파일로 만든다 ({@code apiDocsHttpClient}, epages 플러그인 필요). operation 마다
     * 대표 테스트의 요청 하나를 {@code ###} 로 나누어 쓴다. 결과는 {@code build/api-docs/http-client/<fileName>} 과
     * {@code http-client.env.json} ({@code local} 환경의 {@code baseUrl}). 문서용으로 가린 값({@code ****})은
     * {@code {{access_token}}} 같은 변수로 바뀌고, 그 변수는 환경 파일과 {@code http-client.private.env.json} 에 빈 값으로 선언된다.
     *
     * <pre>{@code
     * httpClient {
     *     enabled = true
     *     fileName = "orders.http" // 기본값 api.http
     * }
     * }</pre>
     */
    public abstract static class HttpClient {
        /** 파일을 만들어 {@code apiDocs} 에 넣을지 여부. 기본값 false */
        public abstract Property<Boolean> getEnabled();

        /** 요청 파일 이름 ({@code .http}). 기본값 {@code api.http} */
        public abstract Property<String> getFileName();
    }

    /**
     * <b>실험 기능, 기본 꺼짐.</b> main 소스의 KDoc·Javadoc 을 문서 설명으로 쓴다. {@code apiDocsSourceDocs} 가 Kotlin·Java
     * 소스를 컴파일 없이 가벼운 스캐너로 읽어 {@code build/kardx/source-docs/source-docs.json} 을 만들고, test 가 그
     * 파일을 시스템 속성 {@code kardx.source-docs} 로 받는다. {@code @Description} 이나 설정한 설명 어노테이션이
     * 없는 필드·파라미터에만 쓰인다 (설명 순서의 맨 끝). 스캐너는 완전하지 않다 — 최상위 함수, 지역 클래스, {@code ///} 주석
     * 등은 읽지 못한다 (docs/http-api.md "알려진 한계"). {@link SourceDocs#getEnabled() enabled} 를 켜면 아래 항목별 스위치가
     * 정하는 것만 색인에 적는다. 끈 항목은 색인에서 빠지므로 테스트도 OpenAPI 후처리도 쓰지 않는다.
     *
     * <pre>{@code
     * sourceDocs {
     *     enabled = true              // 기본값 false (실험 기능)
     *     fieldDocs = true            // 속성·record·getter 문서 → 필드·DTO 파라미터 설명
     *     enumDocs = true             // enum 상수 문서
     *     schemaDescriptions = true   // 클래스 문서 → OpenAPI 컴포넌트 스키마 설명
     *     paramDocs = true            // 핸들러 메서드 @param → path·query·header·form·part 설명
     *     methodDocs = false          // 핸들러 메서드 요약·설명 (OpenAPI·HTML)
     *     includeProjectDependencies = true
     * }
     * }</pre>
     *
     * 테스트 쪽에서는 {@code kardx.source-docs.enabled=false} 로 색인을 받아도 쓰지 않게 끌 수 있다.
     */
    public abstract static class SourceDocs {
        /**
         * 색인을 만들어 테스트와 OpenAPI 후처리에 넘길지 여부 (실험 기능). false 면 태스크를 test 에 연결하지 않고 시스템
         * 속성도 넘기지 않는다. 기본값 false
         */
        public abstract Property<Boolean> getEnabled();

        /** 속성·record 구성 요소·getter 의 문서를 필드와 DTO 파라미터(폼·쿼리 객체) 설명으로 쓸지 여부. 기본값 true */
        public abstract Property<Boolean> getFieldDocs();

        /** enum 상수의 문서를 상수 설명으로 쓸지 여부. 기본값 true */
        public abstract Property<Boolean> getEnumDocs();

        /** 클래스 문서 본문을 OpenAPI 컴포넌트 스키마({@code components.schemas})의 description 으로 쓸지 여부. 기본값 true */
        public abstract Property<Boolean> getSchemaDescriptions();

        /**
         * 핸들러 메서드 KDoc·Javadoc 의 {@code @param} 을 path·query·header·form·part 파라미터 설명으로 쓸지 여부.
         * {@link #getMethodDocs() methodDocs} 와 따로 동작한다. 기본값 true
         */
        public abstract Property<Boolean> getParamDocs();

        /**
         * 컨트롤러 메서드의 KDoc·Javadoc 본문을 OpenAPI operation 과 HTML 의 요약·설명으로 쓸지 여부. 핸들러 메서드에는
         * 구현 메모가 적혀 있는 일이 많아 켜기 전까지는 공개 문서로 새지 않게 모으지 않는다. 기본값 false
         */
        public abstract Property<Boolean> getMethodDocs();

        /**
         * 테스트 런타임 클래스패스의 {@code project(…)} 의존성(java 플러그인을 쓴 프로젝트)의 main 소스도 읽을지 여부.
         * 기본값 true
         */
        public abstract Property<Boolean> getIncludeProjectDependencies();
    }

    /**
     * Spring Framework 7 API 버전({@code @GetMapping(version = "1.1")})을 OpenAPI 에 적는 방식. 버전을 가진 operation 에는
     * 언제나 {@code x-api-version} 을 넣고, 헤더·쿼리 방식이면 그 버전만 받는 필수 파라미터를 더한다.
     * 같은 경로·메서드를 여러 버전으로 문서화했으면 {@link #getSplitSpecs()} 가 정한다.
     *
     * <pre>{@code
     * apiVersioning {
     *     header("X-API-Version")   // 또는 queryParameter("version"), mediaTypeParameter("v"), pathSegment()
     *     splitSpecs = true
     * }
     * }</pre>
     */
    public abstract static class ApiVersioning {
        /** 헤더로 버전을 보낸다. */
        public static final String HEADER = "header";
        /** 쿼리 파라미터로 버전을 보낸다. */
        public static final String QUERY = "query";
        /** 미디어 타입 파라미터로 버전을 보낸다. */
        public static final String MEDIA_TYPE = "mediaType";
        /** 경로 한 칸으로 버전을 보낸다. */
        public static final String PATH = "path";

        /** {@value #HEADER}, {@value #QUERY}, {@value #MEDIA_TYPE}, {@value #PATH}. 기본값 없음 ({@code x-api-version} 만) */
        public abstract Property<String> getStyle();

        /** 헤더·쿼리·미디어 타입 파라미터 이름. 경로 방식이면 쓰지 않는다 */
        public abstract Property<String> getName();

        /**
         * 같은 경로·메서드를 여러 버전으로 문서화했을 때 버전마다 스펙({@code openapi3-v<버전>.yaml}, 버전 없는 operation 포함)을
         * 따로 만들지 여부. 기본값 false: 스펙 하나에 가장 높은 버전을 남기고 가려진 버전을 경고한다
         */
        public abstract Property<Boolean> getSplitSpecs();

        /** {@code name} 헤더로 버전을 보낸다. {@code header("X-API-Version")} */
        public void header(String name) {
            use(HEADER, name, "header");
        }

        /** {@code name} 쿼리 파라미터로 버전을 보낸다. {@code queryParameter("version")} */
        public void queryParameter(String name) {
            use(QUERY, name, "queryParameter");
        }

        /** 미디어 타입 파라미터 {@code name} 으로 버전을 보낸다 ({@code application/json;v=1.1}). */
        public void mediaTypeParameter(String name) {
            use(MEDIA_TYPE, name, "mediaTypeParameter");
        }

        /** 경로 한 칸으로 버전을 보낸다 ({@code /v1.1/users}). */
        public void pathSegment() {
            getStyle().set(PATH);
            getName().unset();
        }

        private void use(String style, String name, String method) {
            if (name == null || name.isBlank()) {
                throw new GradleException("kardx.apiVersioning." + method + "(name) needs a parameter name");
            }

            getStyle().set(style);
            getName().set(name);
        }
    }
}
