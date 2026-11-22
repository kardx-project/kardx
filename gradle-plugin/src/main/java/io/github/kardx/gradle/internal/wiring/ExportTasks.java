package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.ApiSpecExportTask;
import io.github.kardx.gradle.tasks.ApiSpecUploadTask;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

/**
 * 빌드 뒤 스펙을 밖으로 내보내는 태스크. 어느 것도 {@code build}·{@code check}·{@code apiDocs}·{@code bootRun} 에 붙지 않고
 * 직접 실행할 때만 돈다.
 * <ul>
 *   <li>{@code apiDocsExport}: 정한 디렉터리로 복사 ({@link ApiSpecExportTask})</li>
 *   <li>{@code apiDocsUpload}: HTTP 로 보낸다 ({@link ApiSpecUploadTask})</li>
 *   <li>Maven 배포물에 붙이기는 {@link MavenSpecPublication}</li>
 * </ul>
 * 각 태스크는 고른 결과를 만드는 태스크에만 의존한다: OpenAPI → {@code openapi3}, AsyncAPI → {@code apiDocsAsyncApi},
 * 문서 디렉터리 → {@code apiDocs}.
 */
public final class ExportTasks {
    private ExportTasks() {
    }

    public static void register(
        Project project, KardxExtension extension, Provider<Directory> asyncApiOutput) {
        KardxExtension.Export export = extension.getExport();
        KardxExtension.Upload upload = extension.getUpload();

        project.getTasks().register(PluginConstants.EXPORT_TASK, ApiSpecExportTask.class, task -> {
            task.setGroup(PluginConstants.GROUP);
            task.setDescription("Copies the selected API specs (and optionally the HTML docs) to export.directory.");
            sources(project, extension, task, asyncApiOutput);
            task.dependsOn(producedWhen(export.getOpenApi(), openApiTask(project)));
            task.dependsOn(producedWhen(export.getAsyncApi(), PluginConstants.ASYNC_API_TASK));
            task.dependsOn(producedWhen(export.getHtml(), PluginConstants.API_DOCS_TASK));
            task.getIncludeOpenApi().set(export.getOpenApi());
            task.getIncludeAsyncApi().set(export.getAsyncApi());
            task.getIncludeHtml().set(export.getHtml());
            task.getOpenApiFileName().set(export.getOpenApiFileName());
            task.getAsyncApiFileName().set(export.getAsyncApiFileName());
            task.getHtmlDirectoryName().set(export.getHtmlDirectoryName());
            task.getDirectory().set(export.getDirectory());
        });

        project.getTasks().register(PluginConstants.UPLOAD_TASK, ApiSpecUploadTask.class, task -> {
            task.setGroup(PluginConstants.GROUP);
            task.setDescription("Uploads the selected API specs over HTTP to upload.url.");
            task.getOpenApiSpec().from(selected(project, task.getIncludeOpenApi(), openApiCandidates(project)));
            task.getAsyncApiSpec()
                .from(selected(project, task.getIncludeAsyncApi(), asyncApiFile(project, extension, asyncApiOutput)));
            task.dependsOn(producedWhen(upload.getOpenApi(), openApiTask(project)));
            task.dependsOn(producedWhen(upload.getAsyncApi(), PluginConstants.ASYNC_API_TASK));
            task.getIncludeOpenApi().set(upload.getOpenApi());
            task.getIncludeAsyncApi().set(upload.getAsyncApi());
            task.getUrl().set(upload.getUrl());
            task.getAsyncApiUrl().set(upload.getAsyncApiUrl());
            task.getMethod().set(upload.getMethod());
            task.getHeaders().set(upload.getHeaders());
            task.getRequestTimeout().set(upload.getTimeout());
            task.getRetries().set(upload.getRetries());
            placeholders(project, task.getProjectName(), task.getProjectVersion());
        });

        MavenSpecPublication.configure(project, extension, asyncApiOutput);
    }

    /** 내보내기 태스크의 원본 위치와 자리표시자 값. 고른 결과만 입력이 된다 ({@link #selected}). */
    static void sources(
        Project project, KardxExtension extension, ApiSpecExportTask task,
        Provider<Directory> asyncApiOutput) {
        task.getOpenApiSpec().from(selected(project, task.getIncludeOpenApi(), openApiCandidates(project)));
        task.getAsyncApiSpec()
            .from(selected(project, task.getIncludeAsyncApi(), asyncApiFile(project, extension, asyncApiOutput)));
        task.getHtmlDirectory()
            .from(selected(project, task.getIncludeHtml(), extension.getOutputDirectory().map(Directory::getAsFile)));
        placeholders(project, task.getProjectName(), task.getProjectVersion());
    }

    /**
     * [include] 이면 [files], 아니면 빈 목록. 고르지 않은 결과는 입력에서도 뺀다: 그 결과를 만드는 태스크(예: {@code apiDocs})가
     * 같은 실행에 있으면 Gradle 이 의존성을 선언하지 않은 입력으로 보고 태스크를 실패시킨다.
     */
    static Provider<Object> selected(Project project, Provider<Boolean> include, Provider<?> files) {
        Provider<Object> none = project.provider(ArrayList::new);

        return include.flatMap(on -> on ? files.map(Object.class::cast) : none);
    }

    /** {@code {name}}·{@code {version}} 값. 버전은 빌드 스크립트가 나중에 정하는 경우가 많아 늦게 읽는다. */
    private static void placeholders(Project project, Property<String> name, Property<String> version) {
        name.set(project.getName());
        version.set(project.provider(() -> String.valueOf(project.getVersion())));
    }

    /** [selected] 이면 [producer] 태스크에 의존한다. 끈 결과의 태스크는 실행 그래프에 오르지 않는다. */
    static Provider<List<Object>> producedWhen(Provider<Boolean> selected, Object producer) {
        return selected.map(on -> {
            // configuration cache 가 값을 저장하므로 JDK 불변 목록(List.of) 대신 ArrayList 로 준다
            List<Object> tasks = new ArrayList<>();

            if (on) {
                tasks.add(producer);
            }

            return tasks;
        });
    }

    /** epages 의 {@code openapi3}. epages 가 afterEvaluate 에서 만들므로 이름으로 찾는다. */
    static Object openApiTask(Project project) {
        return project.getTasks().matching(t -> t.getName().equals(PluginConstants.OPENAPI3_TASK));
    }

    /** epages 가 있으면 openapi3 결과 후보, 없으면 빈 목록. */
    private static Provider<List<File>> openApiCandidates(Project project) {
        return project.provider(() -> project.getPluginManager().hasPlugin(PluginConstants.EPAGES_PLUGIN)
            ? EpagesOutputs.openApiSpecCandidates(project)
            : new ArrayList<>());
    }

    /**
     * AsyncAPI 결과 파일. {@code asyncApi.enabled} 가 false 면 빈 목록이다: 그때 {@code apiDocsAsyncApi} 는 건너뛰므로
     * 결과 디렉터리에는 끄기 전 실행의 스펙이 남아 있을 수 있다.
     */
    private static Provider<List<File>> asyncApiFile(
        Project project, KardxExtension extension, Provider<Directory> asyncApiOutput) {
        Provider<List<File>> none = project.provider(ArrayList::new);

        return extension.getAsyncApi()
            .getEnabled()
            .flatMap(enabled -> enabled ? asyncApiOutput.map(dir -> {
                // configuration cache 가 값을 저장하므로 JDK 불변 목록(List.of) 대신 ArrayList 로 준다
                List<File> files = new ArrayList<>();

                files.add(dir.file(AsyncApiTasks.fileName(extension.getAsyncApi()) + ".yaml").getAsFile());

                return files;
            }) : none);
    }
}
