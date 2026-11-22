package io.github.kardx.tooling.internal.pipeline;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.epages.EpagesGenerators;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.openapi.ApiVersioningSettings;
import io.github.kardx.tooling.internal.openapi.OpenApiInfoSettings;
import io.github.kardx.tooling.internal.openapi.OpenApiSecuritySettings;
import io.github.kardx.tooling.internal.pages.OpenApiViewerPage;
import io.github.kardx.tooling.internal.steps.OpenApiCheckStep;
import io.github.kardx.tooling.internal.steps.OpenApiPostProcessing;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 명령줄과 Maven 플러그인이 쓰는 설정. JSON 파일(또는 같은 모양의 맵)에서 읽고, 값이 없으면 Gradle 플러그인의 기본값을 쓴다.
 * 경로는 기준 디렉터리(명령줄의 {@code --base-dir}, Maven 의 프로젝트 디렉터리)에 대한 상대 경로로 쓸 수 있다.
 *
 * <pre>{@code
 * {
 *   "snippets": "target/generated-snippets", "output": "target/api-docs", "work": "target/kardx",
 *   "language": "en",
 *   "openApi": { "title": "Orders API", "version": "1.0", "servers": ["/"], "format": "yaml",
 *                "globalHeaders": ["X-API-KEY"], "commonResponses": { "401": "Token missing" },
 *                "security": { "oauth2": { "flows": { "clientCredentials": { "tokenUrl": "…" } } } },
 *                "apiVersioning": { "style": "header" },
 *                "info": { "contact": { "name": "API team" }, "tags": { "Orders": { "description": "..." } } } },
 *   "viewer": { "enabled": true, "type": "redoc" }, "coverage": { "excludes": ["/internal/**"] },
 *   "httpClient": { "enabled": true }, "postman": { "enabled": false },
 *   "check": { "baseline": "docs/openapi/openapi3.yaml", "failOn": "any" }
 * }
 * }</pre>
 *
 * @param snippets 문서화 테스트가 쓴 스니펫 디렉터리
 * @param output 결과 문서 디렉터리 (스펙, 뷰어, 보고서)
 * @param work 중간 파일 디렉터리 (스니펫 복사본 등)
 */
public record ApiDocsConfig(File snippets, File output, File work, DocsLanguage language, OpenApi openApi,
    Viewer viewer, Coverage coverage, HttpClient httpClient, Postman postman, Check check) {

    private static final String ENABLED = "enabled";
    private static final String FILE_NAME = "fileName";
    private static final String DESCRIPTION = "description";
    private static final String EXTERNAL_DOCS = "externalDocs";
    private static final String NAME = "name";
    private static final String URL = "url";
    private static final String TITLE = "title";

    /**
     * OpenAPI 스펙 생성(epages 생성기)과 후처리 설정.
     *
     * @param generator epages {@code openapi3 { }} 와 같은 기본 스펙 설정
     * @param postProcessing 이 라이브러리의 후처리 설정 (Gradle {@code kardx { }} 의 OpenAPI 항목)
     */
    public record OpenApi(boolean enabled, EpagesGenerators.OpenApiSettings generator,
        OpenApiPostProcessing.Settings postProcessing) {
    }

    /** OpenAPI 뷰어 ({@code openApiViewer { }}). */
    public record Viewer(boolean enabled, String type, String title, String fileName) {
    }

    /**
     * 문서화 범위 보고서 ({@code coverage { }}).
     *
     * @param docsPath 앱이 문서를 내놓는 경로 ({@code docs} → {@code /docs/**} 를 보고에서 뺀다)
     */
    public record Coverage(boolean enabled, List<String> excludes, boolean failOnUndocumented, String docsPath) {
    }

    /** IntelliJ HTTP Client 파일 ({@code httpClient { }}). */
    public record HttpClient(boolean enabled, String fileName) {
    }

    /**
     * Postman Collection ({@code postmanCollection { }}, epages {@code postman { title, baseUrl }}).
     *
     * @param title Collection 이름. null 이면 OpenAPI 제목
     */
    public record Postman(boolean enabled, String title, String baseUrl) {
    }

    /** 기준 스펙 비교 ({@code openApiCheck { }}). */
    public record Check(File baseline, String failOn, boolean requireBaseline, File report) {
    }

    /** 설정 JSON 과 같은 모양의 맵에서 읽는다. 빈 맵이면 모두 기본값이다. */
    public static ApiDocsConfig of(Map<?, ?> values, File baseDir) {
        ConfigValues root = ConfigValues.root(values, baseDir)
            .allow(Set.of("snippets", "output", "work", "language", "openApi", "viewer", "coverage", "httpClient",
                "postman", "check"));
        File snippets = root.file("snippets", new File(baseDir, "target/generated-snippets"));
        File output = root.file("output", new File(baseDir, "target/api-docs"));
        File work = root.file("work", new File(snippets.getAbsoluteFile().getParentFile(), "kardx"));
        DocsLanguage language = DocsLanguage.of(root.string("language", DocsLanguage.EN.code()));

        return new ApiDocsConfig(snippets, output, work, language, openApi(root.child("openApi"), language),
            viewer(root.child("viewer")), coverage(root.child("coverage")), httpClient(root.child("httpClient")),
            postman(root.child("postman")), check(root.child("check"), work));
    }

    private static OpenApi openApi(ConfigValues values, DocsLanguage language) {
        values.allow(Set.of(ENABLED, TITLE, "version", DESCRIPTION, "servers", "format", "fileNamePrefix",
            "globalHeaders", "commonResponses", "commonResponseSchema", "security", "apiVersioning", "info",
            "sourceDocs"));

        List<String> servers = values.strings("servers");
        String format = values.string("format", "yaml");

        if (!List.of("yaml", "json").contains(format)) {
            throw new ToolingException("Setting 'openApi.format' must be yaml or json, got " + format);
        }

        EpagesGenerators.OpenApiSettings generator = new EpagesGenerators.OpenApiSettings(
            values.string(TITLE, "API documentation"), values.string("version", "1.0.0"), null,
            servers.isEmpty() ? List.of("/") : servers, format,
            values.string("fileNamePrefix", EpagesGenerators.OPENAPI3_PREFIX));
        Map<Integer, String> commonResponses = new LinkedHashMap<>();

        values.stringMap("commonResponses").forEach((status, text) -> commonResponses.put(status(status), text));

        OpenApiPostProcessing.Settings postProcessing = new OpenApiPostProcessing.Settings(
            new LinkedHashSet<>(values.strings("globalHeaders")), commonResponses,
            values.string("commonResponseSchema", null), language, security(values.child("security")),
            versioning(values.child("apiVersioning")), info(values.child("info")), values.file("sourceDocs", null));
        String description = postProcessing.info().description();

        return new OpenApi(values.bool(ENABLED, true), withDescription(generator, values.string(DESCRIPTION,
            description)), postProcessing);
    }

    private static EpagesGenerators.OpenApiSettings withDescription(
        EpagesGenerators.OpenApiSettings generator, String description) {
        return new EpagesGenerators.OpenApiSettings(generator.title(), generator.version(), description,
            generator.servers(), generator.format(), generator.fileNamePrefix());
    }

    private static int status(String status) {
        try {
            return Integer.parseInt(status);
        } catch (NumberFormatException e) {
            throw new ToolingException("Setting 'openApi.commonResponses' keys must be HTTP status codes, got "
                + status, e);
        }
    }

    private static OpenApiSecuritySettings security(ConfigValues values) {
        values.allow(Set.of("oauth2", "openIdConnectUrl"));

        ConfigValues oauth2 = values.child("oauth2")
            .allow(Set.of("flows", "scopes"));
        Map<String, String> flows = new LinkedHashMap<>();

        // { "clientCredentials": { "tokenUrl": "…" } } → clientCredentials.tokenUrl (Gradle 확장의 flow 맵과 같은 키)
        oauth2.children("flows").forEach((flow, urls) -> urls.toStringMap()
            .forEach((name, url) -> flows.put(flow + "." + name, url)));

        return new OpenApiSecuritySettings(flows, oauth2.stringMap("scopes"), values.string("openIdConnectUrl", null));
    }

    private static ApiVersioningSettings versioning(ConfigValues values) {
        values.allow(Set.of("style", NAME, "splitSpecs"));

        return new ApiVersioningSettings(values.string("style", null), values.string(NAME, null),
            values.bool("splitSpecs", false));
    }

    /** {@code openApiInfo { }}. {@code tags} 의 순서가 태그 순서가 된다 ({@code tagOrder} 를 주면 그것을 먼저 둔다). */
    private static OpenApiInfoSettings info(ConfigValues values) {
        values.allow(Set.of(DESCRIPTION, "descriptionFile", "termsOfService", "contact", "license", EXTERNAL_DOCS,
            "tagOrder", "tags", "tagGroups"));

        List<String> tagOrder = new ArrayList<>(values.strings("tagOrder"));
        Map<String, String> tagDescriptions = new LinkedHashMap<>();
        Map<String, String> tagExternalDocs = new LinkedHashMap<>();

        values.children("tags").forEach((name, tag) -> {
            tag.allow(Set.of(DESCRIPTION, EXTERNAL_DOCS));

            ConfigValues docs = tag.child(EXTERNAL_DOCS)
                .allow(Set.of(URL, DESCRIPTION));

            if (!tagOrder.contains(name)) {
                tagOrder.add(name);
            }

            tagDescriptions.put(name, tag.string(DESCRIPTION, ""));
            tagExternalDocs.put(name + "." + URL, docs.string(URL, ""));
            tagExternalDocs.put(name + "." + DESCRIPTION, docs.string(DESCRIPTION, ""));
        });

        return new OpenApiInfoSettings(description(values), values.string("termsOfService", null),
            values.child("contact").allow(Set.of(NAME, URL, "email")).toStringMap(),
            values.child("license").allow(Set.of(NAME, URL)).toStringMap(),
            values.child(EXTERNAL_DOCS).allow(Set.of(URL, DESCRIPTION)).toStringMap(), tagOrder, tagDescriptions,
            tagExternalDocs, values.listMap("tagGroups"));
    }

    private static String description(ConfigValues info) {
        File file = info.file("descriptionFile", null);
        String text = info.string(DESCRIPTION, null);

        if (file == null) {
            return text;
        }

        if (text != null) {
            throw new ToolingException("Set either openApi.info.description or descriptionFile, not both "
                + "(descriptionFile = " + file + ")");
        }

        if (!file.isFile()) {
            throw new ToolingException("openApi.info.descriptionFile does not exist: " + file);
        }

        return TextFiles.read(file).stripTrailing();
    }

    private static Viewer viewer(ConfigValues values) {
        values.allow(Set.of(ENABLED, "type", TITLE, FILE_NAME));

        String type = values.string("type", OpenApiViewerPage.REDOC);

        return new Viewer(values.bool(ENABLED, false), type, values.string(TITLE, null),
            values.string(FILE_NAME, "openapi.html"));
    }

    private static Coverage coverage(ConfigValues values) {
        values.allow(Set.of(ENABLED, "excludes", "failOnUndocumented", "docsPath"));

        return new Coverage(values.bool(ENABLED, true), values.strings("excludes"),
            values.bool("failOnUndocumented", false), values.string("docsPath", "docs"));
    }

    private static HttpClient httpClient(ConfigValues values) {
        values.allow(Set.of(ENABLED, FILE_NAME));

        return new HttpClient(values.bool(ENABLED, false), values.string(FILE_NAME, "api.http"));
    }

    private static Postman postman(ConfigValues values) {
        values.allow(Set.of(ENABLED, TITLE, "baseUrl"));

        return new Postman(values.bool(ENABLED, false), values.string(TITLE, null),
            values.string("baseUrl", "http://localhost"));
    }

    private static Check check(ConfigValues values, File work) {
        values.allow(Set.of("baseline", "failOn", "requireBaseline", "report"));

        return new Check(values.file("baseline", new File(values.baseDir(), "docs/openapi/openapi3.yaml")),
            values.string("failOn", OpenApiCheckStep.FAIL_ON_ANY), values.bool("requireBaseline", true),
            values.file("report", new File(work, "openapi-changes.txt")));
    }
}
