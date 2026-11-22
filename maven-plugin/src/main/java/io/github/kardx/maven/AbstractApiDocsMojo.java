package io.github.kardx.maven;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.pipeline.ApiDocsConfig;
import io.github.kardx.tooling.internal.pipeline.ApiDocsPipeline;
import io.github.kardx.tooling.internal.pipeline.ConfigMaps;
import java.io.File;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * 목표들이 같이 쓰는 파라미터와 설정 만들기. 설정은 명령줄({@code ApiDocsCli})과 같은 JSON 모양이다: {@code configFile} 을 먼저 읽고,
 * POM 에 적은 파라미터가 그 값을 덮는다. 둘 다 없으면 OpenAPI 제목·버전은 프로젝트 이름·버전이다.
 */
abstract class AbstractApiDocsMojo extends AbstractMojo {
    static final String OPEN_API = "openApi";
    /** 실패·경고 메시지에 보일 Maven 말투의 설정 이름. */
    static final ApiDocsPipeline.Hints HINTS = new ApiDocsPipeline.Hints(
        "mvn io.github.kardx-project:kardx-maven-plugin:baseline", "<failOn>",
        "<requireBaseline>false</requireBaseline>", "<coverageExcludes>");

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    /** 건너뛴다 ({@code -Drestdocs.skip}). */
    @Parameter(property = "restdocs.skip", defaultValue = "false")
    boolean skip;

    /** 명령줄과 같은 모양의 JSON 설정 파일. POM 파라미터로 줄 수 없는 설정(보안, API 버전, openApiInfo 등)을 여기 적는다. */
    @Parameter(property = "restdocs.configFile")
    File configFile;

    /** 문서화 테스트가 쓴 스니펫 디렉터리. */
    @Parameter(defaultValue = "${project.build.directory}/generated-snippets")
    File snippetsDirectory;

    /** 결과 문서 디렉터리 (스펙, 뷰어, 보고서). */
    @Parameter(defaultValue = "${project.build.directory}/api-docs")
    File outputDirectory;

    /** 중간 파일 디렉터리. */
    @Parameter(defaultValue = "${project.build.directory}/kardx")
    File workDirectory;

    /** 생성 문구의 언어 ({@code en}, {@code ko}). 정하지 않으면 설정 파일 값, 그것도 없으면 {@code en}. */
    @Parameter(property = "restdocs.language")
    String language;

    /** OpenAPI 파일 형식 ({@code yaml}, {@code json}). */
    @Parameter
    String format;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping (restdocs.skip)");

            return;
        }

        try {
            run(new MavenToolLog(getLog()));
        } catch (ToolingException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (UncheckedIOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    abstract void run(ToolLog log) throws MojoExecutionException, MojoFailureException;

    /** 목표별 파라미터를 설정 맵에 더한다. */
    void customize(Map<String, Object> settings) {
        // 기본: 더하지 않는다
    }

    ApiDocsConfig config() {
        File baseDir = project.getBasedir();
        Map<String, Object> settings = configFile == null ? new LinkedHashMap<>() : ConfigMaps.read(configFile);

        ConfigMaps.put(settings, List.of("snippets"), path(snippetsDirectory));
        ConfigMaps.put(settings, List.of("output"), path(outputDirectory));
        ConfigMaps.put(settings, List.of("work"), path(workDirectory));
        ConfigMaps.put(settings, List.of("language"), language);
        ConfigMaps.put(settings, List.of(OPEN_API, "format"), format);
        customize(settings);
        defaultValue(settings, List.of(OPEN_API, "title"), project.getName());
        defaultValue(settings, List.of(OPEN_API, "version"), project.getVersion());

        return ApiDocsConfig.of(settings, baseDir);
    }

    /** 설정 파일에도 POM 에도 없으면 {@code value} 를 쓴다. */
    private static void defaultValue(Map<String, Object> settings, List<String> path, Object value) {
        Object parent = settings.get(path.get(0));

        if (!(parent instanceof Map<?, ?> map) || !map.containsKey(path.get(1))) {
            ConfigMaps.put(settings, path, value);
        }
    }

    static String path(File file) {
        return file == null ? null : file.getAbsolutePath();
    }
}
