package io.github.kardx.tooling.internal.pipeline;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.epages.EpagesGenerators;
import io.github.kardx.tooling.internal.io.FileTrees;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.openapi.PostmanCollectionTidy;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.PreviousSpecFiles;
import io.github.kardx.tooling.internal.steps.CoverageReportStep;
import io.github.kardx.tooling.internal.steps.HttpClientStep;
import io.github.kardx.tooling.internal.steps.OpenApiCheckStep;
import io.github.kardx.tooling.internal.steps.OpenApiPostProcessing;
import io.github.kardx.tooling.internal.steps.OpenApiSnippets;
import io.github.kardx.tooling.internal.steps.OpenApiViewerStep;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gradle 의 {@code apiDocs} 가 테스트 뒤에 하는 일을 빌드 도구 없이 한다: OpenAPI 스펙(epages 생성기 + 후처리), 뷰어, 문서화 범위
 * 보고서, IntelliJ HTTP Client 파일, Postman Collection. 결과 디렉터리 구성은 Gradle 의 {@code build/api-docs} 와 같다
 * ({@code openapi3.yaml}, {@code openapi.html}, {@code coverage/}, {@code http-client/}, {@code postman-collection.json}).
 * 명령줄({@code ApiDocsCli})과 Maven 플러그인이 쓴다.
 */
public final class ApiDocsPipeline {
    /** 결과 디렉터리 안의 범위 보고서 위치. */
    public static final String COVERAGE_DIR = "coverage";
    /** 결과 디렉터리 안의 HTTP Client 파일 위치. */
    public static final String HTTP_CLIENT_DIR = "http-client";
    private static final String SNIPPETS_COPY_DIR = "api-spec-snippets";
    private static final String VIEWER_DIR = "openapi-viewer";
    private static final String VERSION_SPECS_MANIFEST = "openapi-version-specs.txt";

    private ApiDocsPipeline() {
    }

    /**
     * 실패·경고 메시지에 보일, 쓰는 쪽(명령줄, Maven) 말투의 설정 이름과 명령.
     *
     * @param baselineCommand 기준 스펙을 갱신하는 명령
     * @param failOnSetting {@code failOn} 설정
     * @param requireBaselineSetting 기준 없이 통과시키는 설정
     * @param coverageExcludeSetting 범위 보고서에서 경로를 빼는 설정
     */
    public record Hints(String baselineCommand, String failOnSetting, String requireBaselineSetting,
        String coverageExcludeSetting) {
        /** 명령줄({@code ApiDocsCli})과 그 설정 파일의 이름. */
        public static final Hints CLI = new Hints("the 'baseline' command", "check.failOn",
            "check.requireBaseline = false", "coverage.excludes");
    }

    /** 설정에서 켠 것을 모두 만든다. OpenAPI 를 끄면 스펙이 필요한 뷰어·HTTP Client·Postman 도 건너뛴다. */
    public static void run(ApiDocsConfig config, Hints hints, ToolLog log) {
        if (!config.snippets().isDirectory()) {
            throw new ToolingException("No snippets directory: " + config.snippets() + ". Run the documentation "
                + "tests first (they write the snippets), or point 'snippets' at the right directory.");
        }

        if (config.openApi().enabled()) {
            openApi(config, log);

            if (config.viewer().enabled()) {
                viewer(config, log);
            }

            if (config.httpClient().enabled()) {
                httpClient(config, log);
            }

            if (config.postman().enabled()) {
                postman(config, log);
            }
        }

        if (config.coverage().enabled()) {
            coverage(config, hints, log);
        }
    }

    /**
     * OpenAPI 조각을 복사·정리하고, epages 생성기로 기본 스펙을 만든 뒤 후처리한다.
     *
     * @return 만든 스펙 파일
     */
    public static File openApi(ApiDocsConfig config, ToolLog log) {
        requireGenerator();

        File copy = new File(config.work(), SNIPPETS_COPY_DIR);
        File manifest = new File(config.work(), VERSION_SPECS_MANIFEST);
        EpagesGenerators.OpenApiSettings generator = config.openApi().generator();

        OpenApiSnippets.copy(config.snippets(), copy, log);
        PreviousSpecFiles.delete(config.output(), generator.fileNamePrefix(), manifest);

        File spec = EpagesGenerators.openApi3(copy, config.output(), generator);
        List<File> written = OpenApiPostProcessing.apply(copy, config.output(), config.openApi().postProcessing(),
            log);

        PreviousSpecFiles.record(manifest, written);
        log.lifecycle("OpenAPI: {}", spec);

        return spec;
    }

    /** OpenAPI 뷰어 페이지. 스펙이 먼저 있어야 한다. */
    public static void viewer(ApiDocsConfig config, ToolLog log) {
        ApiDocsConfig.Viewer viewer = config.viewer();
        String fileName = OpenApiViewerStep.fileName(viewer.fileName(), "viewer.fileName");
        File directory = new File(config.work(), VIEWER_DIR);

        OpenApiViewerStep.run(specCandidates(config), directory,
            new OpenApiViewerStep.Settings(viewer.type(), viewer.title(), fileName, config.language()), log);
        TextFiles.copy(new File(directory, fileName), new File(config.output(), fileName));
        log.lifecycle("OpenAPI viewer: {}", new File(config.output(), fileName));
    }

    /** 문서화 범위 보고서 ({@value #COVERAGE_DIR}/ 아래). */
    public static void coverage(ApiDocsConfig config, Hints hints, ToolLog log) {
        ApiDocsConfig.Coverage coverage = config.coverage();
        List<String> excludes = new ArrayList<>(CoverageReportStep.DEFAULT_EXCLUDES);

        excludes.add("/" + coverage.docsPath() + "/**");
        excludes.addAll(coverage.excludes());

        File directory = new File(config.output(), COVERAGE_DIR);

        CoverageReportStep.run(
            FileTrees.find(config.snippets(), CoverageReportStep.ENDPOINT_PATTERNS),
            FileTrees.find(config.snippets(), CoverageReportStep.OPERATION_PATTERNS,
                CoverageReportStep.OPERATION_EXCLUDES),
            directory,
            new CoverageReportStep.Settings(excludes, coverage.failOnUndocumented(),
                new File(directory, CoverageReportStep.MARKDOWN_FILE).getPath(), hints.coverageExcludeSetting()),
            log);
    }

    /** IntelliJ HTTP Client 파일 ({@value #HTTP_CLIENT_DIR}/ 아래). */
    public static void httpClient(ApiDocsConfig config, ToolLog log) {
        File directory = new File(config.output(), HTTP_CLIENT_DIR);

        HttpClientStep.run(
            FileTrees.find(config.snippets(), HttpClientStep.SNIPPET_PATTERNS, HttpClientStep.SNIPPET_EXCLUDES),
            specCandidates(config), directory, config.httpClient().fileName(), log);
        log.lifecycle("HTTP Client: {}", new File(directory, config.httpClient().fileName()));
    }

    /** Postman Collection. 스펙과 같은 OpenAPI 조각 복사본을 읽고, 가짜 JWT 예시를 변수로 바꾼다. */
    public static void postman(ApiDocsConfig config, ToolLog log) {
        requireGenerator();

        File copy = new File(config.work(), SNIPPETS_COPY_DIR);

        if (!copy.isDirectory()) {
            OpenApiSnippets.copy(config.snippets(), copy, log);
        }

        EpagesGenerators.OpenApiSettings generator = config.openApi().generator();
        String title = config.postman().title() == null ? generator.title() : config.postman().title();
        File file = EpagesGenerators.postman(copy, config.output(), title, generator.version(),
            config.postman().baseUrl());
        int tidied = PostmanCollectionTidy.apply(file);

        log.info("Postman: tidied {} headers (fake token → {{{}}}, empty value)", tidied,
            PostmanCollectionTidy.TOKEN_VARIABLE);
        log.lifecycle("Postman: {}", file);
    }

    /** 만든 스펙을 기준 스펙과 비교한다 ({@link OpenApiCheckStep}). */
    public static void check(ApiDocsConfig config, Hints hints, ToolLog log) {
        ApiDocsConfig.Check check = config.check();

        OpenApiCheckStep.run(specCandidates(config), check.baseline(), check.report(),
            new OpenApiCheckStep.Settings(check.failOn(), check.requireBaseline(), hints.failOnSetting(),
                hints.baselineCommand(), hints.requireBaselineSetting()),
            log);
    }

    /** 만든 스펙을 기준 파일로 복사한다. */
    public static void baseline(ApiDocsConfig config, ToolLog log) {
        File spec = OpenApiSpecFiles.existing(specCandidates(config));

        TextFiles.copy(spec, config.check().baseline());
        log.lifecycle("OpenAPI baseline updated: {}", config.check().baseline());
    }

    /** 결과 디렉터리의 스펙 후보 ({@code <prefix>.<format>}, 다른 형식도). */
    private static Set<File> specCandidates(ApiDocsConfig config) {
        EpagesGenerators.OpenApiSettings generator = config.openApi().generator();
        Set<File> candidates = new LinkedHashSet<>();

        candidates.add(new File(config.output(), generator.fileNamePrefix() + "." + generator.format()));

        for (String extension : List.of("yaml", "yml", "json")) {
            candidates.add(new File(config.output(), generator.fileNamePrefix() + "." + extension));
        }

        return candidates;
    }

    private static void requireGenerator() {
        if (!EpagesGenerators.available()) {
            throw new ToolingException("The OpenAPI generator is not on the classpath. Add "
                + "com.epages:restdocs-api-spec-openapi3-generator (and restdocs-api-spec-postman-generator for "
                + "Postman), or use the tooling jar with the 'all' classifier.");
        }
    }
}
