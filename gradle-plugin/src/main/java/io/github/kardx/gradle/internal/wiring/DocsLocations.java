package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.Html;
import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.ApiDocsCoverageTask;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Task;

/** 문서 생성 위치를 출력한다 ({@code apiDocsAppDocs} 의 태스크 액션에서 부른다. {@code Project} 를 쓰지 않는다). */
final class DocsLocations {
    /** 앱을 기본 포트로 띄웠을 때의 주소. 안내 문구에만 쓴다. */
    private static final String LOCAL_APP_URL = "http://localhost:8080/";
    /** Spring Boot 가 웹 루트에서 서빙하는 classpath 정적 위치. boot 모듈 {@code ApiDocsLocations.STATIC_LOCATIONS} 와 같다. */
    private static final List<String> STATIC_LOCATIONS =
        List.of("static/", "public/", "resources/", "META-INF/resources/");
    private static final String OPEN_API_LABEL = "  OpenAPI (Swagger) ";

    private DocsLocations() {
    }

    /**
     * 문서 위치를 lifecycle 로그로 출력한다.
     *
     * @param postman 켜져 있으면 Postman Collection 파일, 아니면 null. OpenAPI 스펙 목록에서 뺀다
     * @param bootMajor Spring Boot 주 버전 (모르면 0). epages 가 없을 때 권할 버전을 고른다
     */
    static void print(Task task, KardxExtension extension, boolean openApi, File postman, int bootMajor) {
        File docs = extension.getOutputDirectory().get().getAsFile();
        List<String> lines = new ArrayList<>(List.of("Generated API docs"));

        lines.addAll(restDocsLines(extension, docs));

        String asyncApi = AsyncApiTasks.fileName(extension.getAsyncApi());

        if (openApi) {
            openApiSpecs(docs, asyncApi, postman).forEach(spec -> lines.add(OPEN_API_LABEL + spec));
        } else {
            lines.add(OPEN_API_LABEL + EpagesVersion.offHint(bootMajor));
        }

        if (extension.getOpenApiViewer().getEnabled().get()) {
            String viewer = OpenApiViewerTasks.fileName(extension.getOpenApiViewer());

            addIfPresent(lines, "  OpenAPI viewer    ", new File(docs, viewer));
        }

        if (postman != null) {
            addIfPresent(lines, "  Postman           ", new File(docs, postman.getName()));
        }

        if (openApi && extension.getHttpClient().getEnabled().get()) {
            addIfPresent(lines, "  HTTP Client       ", new File(docs, PluginConstants.HTTP_CLIENT_DOCS_DIR + "/"
                + HttpClientTasks.fileName(extension.getHttpClient().getFileName().get())));
        }

        if (openApi && extension.getCoverage().getEnabled().get()) {
            addIfPresent(lines, "  Coverage          ", new File(docs, PluginConstants.COVERAGE_DOCS_DIR + "/"
                + ApiDocsCoverageTask.MARKDOWN_FILE));
        }

        addIfPresent(lines, "  WebSocket·SSE     ", new File(docs, asyncApi + ".html"));
        lines.add("  AsciiDoc          " + extension.getAsciidocDirectory().get().getAsFile());
        lines.add("  Snippets          " + extension.getSnippetsDirectory().get().getAsFile());
        task.getLogger().lifecycle(String.join("\n", lines));
    }

    /** REST Docs HTML 위치. 변환을 껐으면({@code html.enabled = false}) 끈 방법을 한 줄로 알린다. */
    static List<String> restDocsLines(KardxExtension extension, File docs) {
        String index = PluginConstants.INDEX_HTML;

        if (!extension.getHtml().getEnabled().get()) {
            return List.of("  REST Docs HTML    skipped (kardx.html.enabled = false or -P" + Html.PROPERTY
                + "=false)");
        }

        return List.of(
            "  REST Docs HTML    " + new File(docs, index).toPath().toUri(),
            "  In the app        " + LOCAL_APP_URL + webPath(extension) + "/" + index + " (IDE run, bootRun, bootJar)");
    }

    /**
     * 실제로 생긴 OpenAPI 스펙 파일. epages 의 파일 이름(outputFileNamePrefix, format)을 따르므로 디렉터리에서 찾는다.
     * 같은 디렉터리의 AsyncAPI 문서와 Postman Collection 은 뺀다.
     */
    static List<File> openApiSpecs(File docs, String asyncApi, File postman) {
        String postmanName = postman == null ? null : postman.getName();

        return OpenApiSpecFiles.list(docs)
            .stream()
            .filter(spec -> !spec.getName().startsWith(asyncApi + ".") && !spec.getName().equals(postmanName))
            .toList();
    }

    private static void addIfPresent(List<String> lines, String label, File file) {
        if (file.isFile()) {
            lines.add(label + file.toPath().toUri());
        }
    }

    /** 실행 jar 안 문서 경로를 앱 URL 경로로. Spring Boot 는 정적 위치 아래를 루트에서 서빙한다. */
    static String webPath(KardxExtension extension) {
        return webPath(extension.getBootJar().getPath().get());
    }

    /**
     * {@code static/docs}·{@code public/docs}·{@code resources/docs}·{@code META-INF/resources/docs} → {@code docs}.
     * 정적 위치가 아니면 그대로 둔다.
     */
    static String webPath(String path) {
        return STATIC_LOCATIONS.stream()
            .filter(path::startsWith)
            .findFirst()
            .map(location -> path.substring(location.length()))
            .orElse(path);
    }
}
