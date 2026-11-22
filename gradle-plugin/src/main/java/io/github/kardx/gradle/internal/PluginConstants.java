package io.github.kardx.gradle.internal;

import java.util.List;

/** 연결하는 다른 플러그인 id, 태스크 이름, 빌드 디렉터리 안의 경로. 여러 곳에서 같은 문자열을 쓰므로 한곳에 둔다. */
public final class PluginConstants {

    // ---- 연결하는 플러그인 id ----
    /** 확장 이름 ({@code kardx { }}). */
    public static final String EXTENSION_NAME = "kardx";
    /** 플러그인이 등록하는 태스크의 그룹. */
    public static final String GROUP = "documentation";

    public static final String SPRING_BOOT_PLUGIN = "org.springframework.boot";
    public static final String ASCIIDOCTOR_PLUGIN = "org.asciidoctor.jvm.convert";
    public static final String EPAGES_PLUGIN = "com.epages.restdocs-api-spec";
    public static final String MAVEN_PUBLISH_PLUGIN = "maven-publish";

    // ---- 이 플러그인이 만드는 태스크 ----
    public static final String INDEX_TASK = "apiDocsIndex";
    public static final String ASCIIDOC_SOURCES_TASK = "apiDocsAsciidocSources";
    public static final String ASYNC_API_TASK = "apiDocsAsyncApi";
    public static final String API_DOCS_TASK = "apiDocs";
    public static final String APP_DOCS_TASK = "apiDocsAppDocs";
    public static final String OPEN_API_SNIPPETS_TASK = "apiDocsOpenApiSnippets";
    public static final String OPEN_API_CHECK_TASK = "apiDocsCheck";
    public static final String OPEN_API_BASELINE_TASK = "apiDocsBaseline";
    public static final String OPEN_API_VIEWER_TASK = "apiDocsViewer";
    public static final String AGGREGATE_TASK = "apiDocsAggregate";
    public static final String STUBS_JAR_TASK = "apiDocsStubsJar";
    public static final String EXPORT_TASK = "apiDocsExport";
    public static final String UPLOAD_TASK = "apiDocsUpload";
    public static final String COVERAGE_TASK = "apiDocsCoverage";
    public static final String HTTP_CLIENT_TASK = "apiDocsHttpClient";
    /** main 소스의 KDoc·Javadoc 을 설명 색인으로 모으는 태스크. */
    public static final String SOURCE_DOCS_TASK = "apiDocsSourceDocs";
    /** Maven 배포에 붙일 스펙을 고정 이름으로 모으는 중간 태스크. */
    public static final String PUBLICATION_SPECS_TASK = "apiDocsPublicationSpecs";

    // ---- 다른 플러그인의 태스크 ----
    public static final String TEST_TASK = "test";
    public static final String CHECK_TASK = "check";
    public static final String CLASSES_TASK = "classes";
    public static final String ASCIIDOCTOR_TASK = "asciidoctor";
    public static final String BOOT_JAR_TASK = "bootJar";
    public static final String BOOT_RUN_TASK = "bootRun";
    public static final String OPENAPI3_TASK = "openapi3";
    /** epages 가 등록하는 태스크 (같은 이름의 확장도 있다). */
    public static final String POSTMAN_TASK = "postman";
    public static final List<String> EPAGES_TASKS = List.of(OPENAPI3_TASK, "openapi", POSTMAN_TASK);

    // ---- configuration ----
    public static final String ASCIIDOCTOR_CONFIGURATION = "restdocsAsciidoctor";
    /** 모을 프로젝트를 선언하는 configuration ({@code restdocsAggregation(project(":order"))}). */
    public static final String AGGREGATION_CONFIGURATION = "restdocsAggregation";
    /** 위 선언을 풀어 문서 디렉터리를 받는 configuration. */
    public static final String AGGREGATION_DOCS_CONFIGURATION = "restdocsAggregationDocs";
    /** 각 프로젝트가 apiDocs 결과를 내보내는 configuration. */
    public static final String API_DOCS_ELEMENTS_CONFIGURATION = "restdocsApiDocsElements";
    /** 위 두 configuration 을 짝짓는 문서 종류 ({@code org.gradle.docstype}). */
    public static final String API_DOCS_TYPE = "restdocs-api-docs";

    // ---- 빌드 디렉터리 기준 경로 ----
    public static final String SNIPPETS_DIR = "generated-snippets";
    public static final String ASCIIDOC_DIR = "generated-asciidoc";
    public static final String API_DOCS_DIR = "api-docs";
    /** epages 가 출력 디렉터리를 알려 주지 않을 때의 기본값. */
    public static final String EPAGES_OUTPUT_DIR = "api-spec";
    /**
     * AsyncAPI 결과. epages 의 build/api-spec 과 겹치지 않게 따로 둔다. epages 는 출력을 선언하지 않아서, 같은 디렉터리를
     * 출력으로 선언한 태스크가 처음 실행될 때 Gradle 이 openapi3.yaml 을 오래된 파일로 보고 지운다.
     */
    public static final String ASYNC_API_OUTPUT_DIR = "kardx/asyncapi";
    public static final String ASCIIDOC_SOURCES_DIR = "kardx/asciidoc-sources";
    public static final String OPEN_API_SNIPPETS_DIR = "kardx/api-spec-snippets";
    /** apiDocsIndex 결과. test 가 실행 전에 비우는 generated-asciidoc 밖에 두어야 index 가 UP-TO-DATE 로 남는다. */
    public static final String INDEX_DIR = "kardx/index";
    /** 비어 있는 디렉터리. 꺼진 기능의 결과 대신 넣는다. */
    public static final String NONE_DIR = "kardx/none";
    public static final String OPEN_API_VIEWER_DIR = "kardx/openapi-viewer";
    /** apiDocsAppDocs 가 앱 리소스에 복사한 파일 목록. 다음 실행이 이 파일들만 지운다. */
    public static final String APP_DOCS_MANIFEST = "kardx/app-docs-files.txt";
    /** apiVersioning.splitSpecs 가 쓴 버전 스펙 이름 목록. 다음 openapi3 실행 전에 이 파일들만 지운다. */
    public static final String VERSION_SPECS_MANIFEST = "kardx/openapi-version-specs.txt";
    /** apiDocsCoverage 결과. apiDocs 가 {@link #COVERAGE_DOCS_DIR} 로 넣는다. */
    public static final String COVERAGE_DIR = "kardx/coverage";
    /** apiDocsHttpClient 결과. apiDocs 가 {@link #HTTP_CLIENT_DOCS_DIR} 로 넣는다. */
    public static final String HTTP_CLIENT_DIR = "kardx/http-client";
    /** {@link #SOURCE_DOCS_TASK} 결과 ({@link #SOURCE_DOCS_FILE}). */
    public static final String SOURCE_DOCS_DIR = "kardx/source-docs";
    public static final String SOURCE_DOCS_FILE = "source-docs.json";
    public static final String OPEN_API_CHANGES_REPORT = "reports/kardx/openapi-changes.txt";
    public static final String AGGREGATE_DIR = "api-docs-aggregate";
    /** {@link #PUBLICATION_SPECS_TASK} 결과. */
    public static final String PUBLICATION_SPECS_DIR = "kardx/publication";
    /** 문서 디렉터리 안의 범위 보고서 위치. */
    public static final String COVERAGE_DOCS_DIR = "coverage";
    /** 문서 디렉터리 안의 HTTP Client 파일 위치. */
    public static final String HTTP_CLIENT_DOCS_DIR = "http-client";
    /** 테스트가 WireMock 매핑을 쓰는 곳 (스니펫 디렉터리 기준). core 의 WireMockStubSnippet 과 같다. */
    public static final String WIRE_MOCK_DIR = "wiremock";

    // ---- 프로젝트 디렉터리 기준 경로 ----
    public static final String ASCIIDOC_SOURCE_DIR = "src/docs/asciidoc";
    public static final String OPEN_API_BASELINE = "docs/openapi/openapi3.yaml";
    public static final String INDEX_TEMPLATE = ASCIIDOC_SOURCE_DIR + "/index-template.adoc";

    /** 테스트 JVM 에 넘기는 시스템 속성 접두사. core 의 {@code ExternalSettings.PREFIX} 와 같다. */
    public static final String PROPERTY_PREFIX = "kardx.";
    /** 생성 문서 언어를 넘기는 시스템 속성. core·boot 의 {@code kardx.language} 와 같다. */
    public static final String LANGUAGE_PROPERTY = PROPERTY_PREFIX + "language";
    /**
     * 플러그인이 실제로 쓰는 문서 언어(정하지 않았으면 기본값)를 테스트 JVM 에 알리는 표시 속성. core 의
     * {@code PluginLanguageWarning.PROPERTY} 와 같다. core 는 이 값과 자기 언어가 다르면 경고만 하고 언어는 바꾸지 않는다.
     */
    public static final String PLUGIN_LANGUAGE_PROPERTY = PROPERTY_PREFIX + "plugin-language";
    /** {@link #SOURCE_DOCS_TASK} 가 만든 설명 색인의 절대 경로를 테스트 JVM 에 넘기는 시스템 속성. core 가 읽는다. */
    public static final String SOURCE_DOCS_PROPERTY = PROPERTY_PREFIX + "source-docs";
    /** apiDocsIndex 가 만드는 파일 이름 ({@link #INDEX_DIR} 안). */
    public static final String INDEX_FILE = "index.adoc";
    /** {@link #INDEX_FILE} 을 Asciidoctor 가 바꾼 문서 첫 페이지. core 의 {@code RestDocsSettings.indexLink} 기본값과 같다. */
    public static final String INDEX_HTML = "index.html";

    private PluginConstants() {
    }
}
