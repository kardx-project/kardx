package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 앱 리소스에 넣은 문서의 실제 경로와 파일 이름을 {@value #RESOURCE} 로 남긴다. boot 모듈({@code ApiDocsLocations})이 시작 로그의
 * 기본값으로 읽으므로 {@code bootJar.path}·{@code asyncApi.fileName}·{@code openApiViewer.fileName}·epages 의 파일 이름을
 * {@code application.yml} 에 다시 적지 않아도 된다 (yml 에 적으면 yml 이 이긴다).
 *
 * <p>키는 yml 키에서 {@code kardx.} 을 뗀 것이다. {@code language} 는 {@code kardx.language} 를 정했을 때만
 * 넣는다 (시작 로그 문구의 언어). {@code META-INF/kardx/} 은 Spring Boot 정적 리소스 위치
 * ({@code static/}, {@code public/}, {@code resources/}, {@code META-INF/resources/})가 아니므로 웹으로 서빙되지 않는다.
 */
final class AppDocsProperties {
    /** 앱 클래스패스 안의 위치. boot 모듈 {@code ApiDocsLocations.DEFAULTS_RESOURCE} 와 같다. */
    static final String RESOURCE = "META-INF/kardx/docs-locations.properties";

    private AppDocsProperties() {
    }

    /**
     * {@code resources/<RESOURCE>} 를 쓴다. {@code docs} 는 문서를 모은 디렉터리({@code apiDocs} 결과), 없는 파일의 키는 넣지 않는다.
     */
    static void write(File resources, File docs, KardxExtension extension, boolean openApi, File postman) {
        File target = new File(resources, RESOURCE);

        try {
            Files.createDirectories(target.getParentFile().toPath());
            Files.writeString(target.toPath(), text(values(docs, extension, openApi, postman)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + target, e);
        }
    }

    /** 문서를 앱에 넣지 않으면 지난 실행이 남긴 파일을 지운다. */
    static void delete(File resources) {
        try {
            Files.deleteIfExists(new File(resources, RESOURCE).toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + RESOURCE, e);
        }
    }

    static Map<String, String> values(File docs, KardxExtension extension, boolean openApi, File postman) {
        Map<String, String> values = new TreeMap<>();
        String asyncApi = AsyncApiTasks.fileName(extension.getAsyncApi());

        values.put("docs-path", DocsLocations.webPath(extension));
        values.put("startup-log.rest-docs.file", PluginConstants.INDEX_HTML);

        if (openApi) {
            // 이름 순으로 첫 OpenAPI 3 스펙. 스트림이 지연되므로 그 하나까지만 읽는다 (버전 스펙마다 YAML 을 읽지 않는다)
            DocsLocations.openApiSpecs(docs, asyncApi, postman)
                .stream()
                .sorted(Comparator.comparing(File::getName))
                .filter(spec -> OpenApiSpecFiles.readOpenApi3(spec) != null)
                .findFirst()
                .ifPresent(spec -> values.put("startup-log.open-api.file", relative(docs, spec)));
        }

        if (extension.getOpenApiViewer().getEnabled().get()) {
            values.put("startup-log.open-api-viewer.file", OpenApiViewerTasks.fileName(extension.getOpenApiViewer()));
        }

        if (extension.getAsyncApi().getEnabled().get()) {
            values.put("startup-log.async-api.file", asyncApi + ".html");
        }

        // 정한 경우에만 넣는다: 정하지 않으면 앱 설정(yml)이나 boot 모듈 기본값이 정한다
        if (extension.getLanguage().isPresent()) {
            values.put("language", DocsLanguage.of(extension.getLanguage().get()).code());
        }

        return values;
    }

    /** {@link Properties} 형식. 날짜 주석이 없고 키 순서가 고정이라 내용이 같으면 파일도 같다. */
    private static String text(Map<String, String> values) {
        Properties properties = new Properties();
        StringWriter writer = new StringWriter();

        properties.putAll(values);

        try {
            properties.store(writer, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> lines = writer.toString()
            .lines()
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .sorted()
            .toList();

        return lines.stream()
            .collect(Collectors.joining("\n", "", "\n"));
    }

    private static String relative(File docs, File file) {
        return docs.toPath()
            .relativize(file.toPath())
            .toString()
            .replace(File.separatorChar, '/');
    }
}
