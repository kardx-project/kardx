package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.Html;
import io.github.kardx.gradle.dsl.OpenApiInfo;
import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.OpenApiCheckTask;
import io.github.kardx.tooling.internal.pages.OpenApiViewerPage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;

/** {@code kardx { }} 확장의 기본값. 경로는 모두 빌드 디렉터리 또는 {@code src/docs/asciidoc} 아래다. */
public final class ExtensionDefaults {
    private ExtensionDefaults() {
    }

    public static void apply(Project project, KardxExtension extension) {
        DirectoryProperty build = project.getLayout().getBuildDirectory();
        Directory projectDir = project.getLayout().getProjectDirectory();

        extension.getSnippetsDirectory().convention(build.dir(PluginConstants.SNIPPETS_DIR));
        extension.getAsciidocDirectory().convention(build.dir(PluginConstants.ASCIIDOC_DIR));
        extension.getAsciidocSourceDirectory().convention(projectDir.dir(PluginConstants.ASCIIDOC_SOURCE_DIR));
        extension.getOutputDirectory().convention(build.dir(PluginConstants.API_DOCS_DIR));
        extension.getCleanBeforeTest().convention(true);
        extension.getGenerateOnRun().convention(true);
        extension.getStartupLog().convention(true);
        extension.getStartupLogScope().convention("developmentOnly");
        extension.getExcludeServletStarterForWebFlux().convention(true);
        extension.getAddDependencies().convention(true);
        extension.getAsciidoctorJvmArgs().convention(AsciidoctorIntegration.DEFAULT_JVM_ARGS);
        extension.getOpenApiGlobalHeaders().convention(List.of());
        extension.getOpenApiCommonResponses().convention(Map.of());
        extension.getIndex().getTemplate().convention(projectDir.file(PluginConstants.INDEX_TEMPLATE));
        extension.getIndex().getTitle().convention(project.provider(project::getName));
        extension.getAsyncApi().getEnabled().convention(true);
        extension.getAsyncApi().getFileName().convention("asyncapi");
        extension.getAsyncApi().getTitle().convention(project.provider(project::getName));
        extension.getAsyncApi().getVersion().convention(project.provider(() -> String.valueOf(project.getVersion())));
        extension.getBootJar().getEnabled().convention(true);
        extension.getBootJar().getPath().convention("static/docs");
        extension.getOpenApiCheck().getBaseline().convention(projectDir.file(PluginConstants.OPEN_API_BASELINE));
        extension.getOpenApiCheck().getFailOn().convention(OpenApiCheckTask.FAIL_ON_ANY);
        extension.getOpenApiCheck().getRunOnCheck().convention(false);
        extension.getOpenApiCheck().getRequireBaseline().convention(true);
        extension.getOpenApiViewer().getEnabled().convention(false);
        extension.getOpenApiViewer().getType().convention(OpenApiViewerPage.REDOC);
        extension.getOpenApiViewer().getFileName().convention("openapi.html");
        extension.getPostmanCollection().getEnabled().convention(false);
        extension.getAggregate().getTitle().convention(project.provider(project::getName));
        extension.getAggregate().getVersion().convention(project.provider(() -> String.valueOf(project.getVersion())));
        extension.getAggregate().getOutputDirectory().convention(build.dir(PluginConstants.AGGREGATE_DIR));
        extension.getWireMock().getEnabled().convention(false);
        extension.getCoverage().getEnabled().convention(true);
        extension.getCoverage().getFailOnUndocumented().convention(false);
        extension.getCoverage().getExcludes().convention(List.of());
        extension.getOpenApiSecurity().getOauth2().getFlows().convention(Map.of());
        extension.getOpenApiSecurity().getOauth2().getScopes().convention(Map.of());
        extension.getHttpClient().getEnabled().convention(false);
        extension.getHttpClient().getFileName().convention("api.http");
        extension.getApiVersioning().getSplitSpecs().convention(false);
        extension.getSourceDocs().getEnabled().convention(false);
        extension.getSourceDocs().getFieldDocs().convention(true);
        extension.getSourceDocs().getEnumDocs().convention(true);
        extension.getSourceDocs().getSchemaDescriptions().convention(true);
        extension.getSourceDocs().getParamDocs().convention(true);
        extension.getSourceDocs().getMethodDocs().convention(false);
        extension.getSourceDocs().getIncludeProjectDependencies().convention(true);
        extension.getHtml().getEnabled().convention(htmlDefault(project));
        applyOpenApiInfo(extension.getOpenApiInfo());
        applyExport(extension.getExport());
        applyUpload(extension.getUpload());
    }

    /** Gradle 속성 {@value Html#PROPERTY} ({@code true}/{@code false}), 없으면 true. 다른 값이면 읽을 때 실패한다. */
    private static Provider<Boolean> htmlDefault(Project project) {
        return project.getProviders()
            .gradleProperty(Html.PROPERTY)
            .map(ExtensionDefaults::parseHtml)
            .orElse(true);
    }

    static boolean parseHtml(String value) {
        String trimmed = value.trim();

        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(trimmed);
        }

        throw new GradleException("Gradle property " + Html.PROPERTY + " must be true or false, got '" + value + "'");
    }

    private static void applyOpenApiInfo(OpenApiInfo info) {
        info.getTagOrder().convention(List.of());
        info.getTagDescriptions().convention(Map.of());
        info.getTagExternalDocs().convention(Map.of());
        info.getTagGroups().convention(Map.of());
    }

    private static void applyExport(KardxExtension.Export export) {
        export.getOpenApi().convention(true);
        export.getAsyncApi().convention(false);
        export.getHtml().convention(false);
        export.getOpenApiFileName().convention("{name}-openapi3.{ext}");
        export.getAsyncApiFileName().convention("{name}-asyncapi.{ext}");
        export.getHtmlDirectoryName().convention("{name}-api-docs");
        export.getPublishSpecs().convention(false);
    }

    private static void applyUpload(KardxExtension.Upload upload) {
        upload.getAsyncApiUrl().convention(upload.getUrl());
        upload.getMethod().convention("PUT");
        upload.getHeaders().convention(Map.of());
        upload.getOpenApi().convention(true);
        upload.getAsyncApi().convention(false);
        upload.getTimeout().convention(Duration.ofSeconds(30));
        upload.getRetries().convention(0);
    }
}
