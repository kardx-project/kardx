package io.github.kardx.maven;

import io.github.kardx.tooling.internal.io.FileTrees;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.pipeline.ApiDocsConfig;
import io.github.kardx.tooling.internal.pipeline.ApiDocsPipeline;
import io.github.kardx.tooling.internal.pipeline.ConfigMaps;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * 문서화 테스트 뒤의 일을 한다 (Gradle {@code apiDocs} 의 Asciidoctor 앞부분): 문서 목차(index.adoc)와 Asciidoctor 입력 디렉터리,
 * AsyncAPI, OpenAPI 3 스펙(epages 생성기 + 이 라이브러리의 보강), 뷰어, 문서화 범위 보고서, IntelliJ HTTP Client 파일,
 * Postman Collection. HTML 은 그 뒤에 asciidoctor-maven-plugin 이 {@code asciidocSourcesDirectory} 를 변환해 만든다.
 */
@Mojo(name = "api-docs", defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
    requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class ApiDocsMojo extends AbstractApiDocsMojo {
    private static final String INDEX_FILE = "index.adoc";
    private static final String RECORDS_DIR = ".restdocs-documents";

    /** 테스트가 쓴 클래스별 AsciiDoc 문서. */
    @Parameter(defaultValue = "${project.build.directory}/generated-asciidoc")
    File asciidocDirectory;

    /** 손으로 쓴 AsciiDoc ({@code _landing.adoc} 등). */
    @Parameter(defaultValue = "${project.basedir}/src/docs/asciidoc")
    File asciidocSourceDirectory;

    /** 손으로 쓴 문서 + 생성 문서 + index.adoc 을 모은 Asciidoctor 입력. asciidoctor-maven-plugin 의 sourceDirectory 로 쓴다. */
    @Parameter(defaultValue = "${project.build.directory}/kardx/asciidoc-sources")
    File asciidocSourcesDirectory;

    /** 문서 목차(index.adoc)를 만든다. */
    @Parameter(defaultValue = "true")
    boolean index;

    /** 목차 템플릿. 없으면 내장 템플릿. */
    @Parameter(defaultValue = "${project.basedir}/src/docs/asciidoc/index-template.adoc")
    File indexTemplate;

    /** 내장 목차 템플릿의 제목. */
    @Parameter(defaultValue = "${project.name}")
    String indexTitle;

    /** 테스트가 남긴 메시지({@code *.asyncapi.json})가 있으면 AsyncAPI 문서와 뷰어를 만든다. */
    @Parameter(defaultValue = "true")
    boolean asyncApi;

    /** AsyncAPI 파일 이름 (확장자 없이, 결과 디렉터리 기준). */
    @Parameter(defaultValue = "asyncapi")
    String asyncApiFileName;

    /** OpenAPI 스펙을 만든다. 끄면 뷰어·HTTP Client·Postman 도 만들지 않는다. */
    @Parameter
    Boolean openApi;

    @Parameter
    String title;

    @Parameter
    String version;

    @Parameter
    String description;

    /** OpenAPI {@code servers}. 기본 {@code /}. */
    @Parameter
    List<String> servers;

    /** apiKey 보안 스키마로 바꿀 전역 헤더. */
    @Parameter
    List<String> globalHeaders;

    /** 모든 operation 에 더할 응답 (상태 코드 → 설명). */
    @Parameter
    Map<String, String> commonResponses;

    /** 공통 응답 본문 스키마 이름. */
    @Parameter
    String commonResponseSchema;

    /** Redoc·Scalar 뷰어 ({@code openapi.html}). */
    @Parameter
    Boolean openApiViewer;

    /** {@code redoc} 또는 {@code scalar}. */
    @Parameter
    String openApiViewerType;

    /** 문서화 범위 보고서 ({@code coverage/}). */
    @Parameter
    Boolean coverage;

    /** 범위 보고서에서 뺄 경로 패턴 ({@code /internal/**}). */
    @Parameter
    List<String> coverageExcludes;

    /** 문서화하지 않은 엔드포인트가 있으면 빌드를 실패시킨다. */
    @Parameter
    Boolean failOnUndocumented;

    /** IntelliJ HTTP Client 파일 ({@code http-client/api.http}). */
    @Parameter
    Boolean httpClient;

    /** Postman Collection ({@code postman-collection.json}). */
    @Parameter
    Boolean postman;

    @Override
    void run(ToolLog log) throws MojoExecutionException {
        ApiDocsConfig config = config();

        if (index && asciidocDirectory.isDirectory()) {
            index(testClasspath(), config, log);
        }

        asciidocSources();

        if (asyncApi && !FileTrees.find(snippetsDirectory, List.of("**/*.asyncapi.json")).isEmpty()) {
            asyncApi(testClasspath(), config, log);
        }

        ApiDocsPipeline.run(config, HINTS, log);
    }

    @Override
    void customize(Map<String, Object> settings) {
        put(settings, OPEN_API, "enabled", openApi);
        put(settings, OPEN_API, "title", title);
        put(settings, OPEN_API, "version", version);
        put(settings, OPEN_API, "description", description);
        put(settings, OPEN_API, "servers", servers);
        put(settings, OPEN_API, "globalHeaders", globalHeaders);
        put(settings, OPEN_API, "commonResponses", commonResponses);
        put(settings, OPEN_API, "commonResponseSchema", commonResponseSchema);
        put(settings, "viewer", "enabled", openApiViewer);
        put(settings, "viewer", "type", openApiViewerType);
        put(settings, "coverage", "enabled", coverage);
        put(settings, "coverage", "excludes", coverageExcludes);
        put(settings, "coverage", "failOnUndocumented", failOnUndocumented);
        put(settings, "httpClient", "enabled", httpClient);
        put(settings, "postman", "enabled", postman);
    }

    /** 목차: core 의 DocumentationIndexCli 를 테스트 클래스패스로 띄운다 (Gradle {@code apiDocsIndex}). */
    private void index(List<String> classpath, ApiDocsConfig config, ToolLog log) {
        List<String> args = new ArrayList<>(List.of(
            "--output", indexFile().getAbsolutePath(),
            "--documents", asciidocDirectory.getAbsolutePath(),
            "--title", indexTitle));

        if (indexTemplate != null && indexTemplate.isFile()) {
            args.addAll(List.of("--template", indexTemplate.getAbsolutePath()));
        }

        ForkedCli.run(classpath, "io.github.kardx.asciidoc.DocumentationIndexCli", config.language().code(),
            args, log);
    }

    /** 손으로 쓴 문서(템플릿 제외) + 생성 문서(옛 index·문서 기록 제외) + 새 index (Gradle {@code apiDocsAsciidocSources}). */
    private void asciidocSources() {
        FileTrees.delete(asciidocSourcesDirectory);
        copy(asciidocSourceDirectory, asciidocSourcesDirectory, List.of(relative(asciidocSourceDirectory,
            indexTemplate)));
        copy(asciidocDirectory, asciidocSourcesDirectory, List.of(INDEX_FILE, RECORDS_DIR + "/**"));

        if (index && indexFile().isFile()) {
            TextFiles.copy(indexFile(), new File(asciidocSourcesDirectory, INDEX_FILE));
        }
    }

    /** AsyncAPI 문서와 뷰어: core 의 AsyncApiCli (Gradle {@code apiDocsAsyncApi}). */
    private void asyncApi(List<String> classpath, ApiDocsConfig config, ToolLog log) {
        List<String> args = new ArrayList<>(List.of(
            "--snippets", snippetsDirectory.getAbsolutePath(),
            "--output", new File(outputDirectory, asyncApiFileName + ".yaml").getAbsolutePath(),
            "--viewer", new File(outputDirectory, asyncApiFileName + ".html").getAbsolutePath(),
            "--title", config.openApi().generator().title(),
            "--version", config.openApi().generator().version()));

        ForkedCli.run(classpath, "io.github.kardx.asyncapi.AsyncApiCli", config.language().code(), args, log);
    }

    private File indexFile() {
        return new File(workDirectory, "index/" + INDEX_FILE);
    }

    private List<String> testClasspath() throws MojoExecutionException {
        try {
            return project.getTestClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Could not resolve the test classpath", e);
        }
    }

    private static void copy(File source, File target, List<String> excludes) {
        Path base = source.toPath();

        for (File file : FileTrees.find(source, List.of("**"), excludes)) {
            TextFiles.copy(file, target.toPath().resolve(base.relativize(file.toPath())).toFile());
        }
    }

    /** {@code file} 의 {@code directory} 기준 경로. 그 밖이면 맞을 리 없는 패턴. */
    private static String relative(File directory, File file) {
        if (file == null || !file.toPath().startsWith(directory.toPath())) {
            return "";
        }

        return directory.toPath()
            .relativize(file.toPath())
            .toString()
            .replace(File.separatorChar, '/');
    }

    private static void put(Map<String, Object> settings, String section, String key, Object value) {
        ConfigMaps.put(settings, List.of(section, key), value);
    }
}
