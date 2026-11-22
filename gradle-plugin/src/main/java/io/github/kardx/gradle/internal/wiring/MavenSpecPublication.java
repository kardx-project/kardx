package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.ApiSpecExportTask;
import io.github.kardx.tooling.internal.export.SpecKind;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;

/**
 * {@code export.publishSpecs = true} 이고 {@code maven-publish} 가 있으면 고른 스펙을 Maven 배포물에 붙인다.
 * classifier 는 {@code openapi}·{@code asyncapi} 라서 {@code publish} 가 {@code <name>-<version>-openapi.yaml} 을 jar 옆에
 * 올린다. 스펙은 {@code apiDocsPublicationSpecs} 가 고정 이름으로 모으고(없으면 이유를 알려 주며 실패), 배포 태스크는 그 태스크에
 * 의존한다. {@code maven-publish} 가 없으면 아무것도 하지 않는다.
 *
 * <p>배포물을 정하지 않으면({@code publicationName}) 모든 {@link MavenPublication} 에 붙이되, Gradle 플러그인 마커
 * ({@code *PluginMarkerMaven}, POM 만 있는 배포물)는 뺀다.
 */
final class MavenSpecPublication {
    private static final String PLUGIN_MARKER_SUFFIX = "PluginMarkerMaven";
    private static final String ASYNC_API_EXTENSION = "yaml";

    private MavenSpecPublication() {
    }

    static void configure(Project project, KardxExtension extension, Provider<Directory> asyncApiOutput) {
        // publishSpecs 와 epages 의 format 은 빌드 스크립트가 정하므로 그 뒤에 읽는다
        project.afterEvaluate(p -> {
            KardxExtension.Export export = extension.getExport();
            boolean publishing = p.getPluginManager().hasPlugin(PluginConstants.MAVEN_PUBLISH_PLUGIN);

            if (!publishing || !export.getPublishSpecs().get()) {
                return;
            }

            if (!export.getOpenApi().get() && !export.getAsyncApi().get()) {
                p.getLogger().warn("kardx: export.publishSpecs is true but export.openApi and "
                    + "export.asyncApi are both false, so no spec is attached to the Maven publication.");

                return;
            }

            attach(p, extension, asyncApiOutput);
        });
    }

    private static void attach(Project project, KardxExtension extension, Provider<Directory> asyncApiOutput) {
        KardxExtension.Export export = extension.getExport();
        boolean openApi = export.getOpenApi().get();
        boolean asyncApi = export.getAsyncApi().get();
        String openApiExtension = EpagesOutputs.openApiExtension(project);
        Provider<Directory> staging = project.getLayout()
            .getBuildDirectory()
            .dir(PluginConstants.PUBLICATION_SPECS_DIR);
        TaskProvider<ApiSpecExportTask> specs = registerStaging(project, extension, asyncApiOutput, staging,
            openApiExtension);
        String only = export.getPublicationName().getOrNull();

        project.getExtensions()
            .getByType(PublishingExtension.class)
            .getPublications()
            .withType(MavenPublication.class)
            .matching(publication -> only == null
                ? !publication.getName().endsWith(PLUGIN_MARKER_SUFFIX)
                : publication.getName().equals(only))
            .configureEach(publication -> {
                if (openApi) {
                    addArtifact(publication, staging, SpecKind.OPENAPI, openApiExtension, specs);
                }

                if (asyncApi) {
                    addArtifact(publication, staging, SpecKind.ASYNCAPI, ASYNC_API_EXTENSION, specs);
                }
            });
    }

    /** 배포할 스펙을 {@code openapi.<ext>}·{@code asyncapi.yaml} 로 모으는 중간 태스크. */
    private static TaskProvider<ApiSpecExportTask> registerStaging(
        Project project, KardxExtension extension, Provider<Directory> asyncApiOutput,
        Provider<Directory> staging, String openApiExtension) {
        KardxExtension.Export export = extension.getExport();

        return project.getTasks()
            .register(PluginConstants.PUBLICATION_SPECS_TASK, ApiSpecExportTask.class, task -> {
                task.setDescription("Collects the API specs that export.publishSpecs attaches to Maven publications.");
                ExportTasks.sources(project, extension, task, asyncApiOutput);
                task.dependsOn(ExportTasks.producedWhen(export.getOpenApi(), ExportTasks.openApiTask(project)));
                task.dependsOn(ExportTasks.producedWhen(export.getAsyncApi(), PluginConstants.ASYNC_API_TASK));
                task.getIncludeOpenApi().set(export.getOpenApi());
                task.getIncludeAsyncApi().set(export.getAsyncApi());
                task.getIncludeHtml().set(false);
                task.getOpenApiFileName().set(fileName(SpecKind.OPENAPI, openApiExtension));
                task.getAsyncApiFileName().set(fileName(SpecKind.ASYNCAPI, ASYNC_API_EXTENSION));
                task.getDirectory().set(staging);
            });
    }

    private static void addArtifact(
        MavenPublication publication, Provider<Directory> staging, SpecKind kind, String extension,
        TaskProvider<ApiSpecExportTask> specs) {
        Provider<RegularFile> file = staging.map(dir -> dir.file(fileName(kind, extension)));

        publication.artifact(file, artifact -> {
            artifact.setClassifier(kind.type());
            artifact.setExtension(extension);
            artifact.builtBy(specs);
        });
    }

    private static String fileName(SpecKind kind, String extension) {
        return kind.type() + "." + extension;
    }
}
