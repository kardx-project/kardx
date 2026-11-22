package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.HttpClientExportTask;
import io.github.kardx.tooling.internal.io.OutputPaths;
import io.github.kardx.tooling.internal.steps.HttpClientStep;
import java.io.File;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

/**
 * {@code apiDocsHttpClient}({@link HttpClientExportTask})를 등록하고 {@code httpClient.enabled} 이면 {@code apiDocs} 의
 * {@code http-client/} 에 넣는다. Postman Collection 처럼 문서 디렉터리에 들어가므로 앱·내보내기({@code export.html})에도 따라간다.
 */
final class HttpClientTasks {

    private HttpClientTasks() {
    }

    static void register(Project project, KardxExtension extension, Provider<List<File>> specCandidates,
        Object openApiTask, TaskProvider<Sync> apiDocs) {
        KardxExtension.HttpClient spec = extension.getHttpClient();
        Provider<Directory> output = project.getLayout().getBuildDirectory().dir(PluginConstants.HTTP_CLIENT_DIR);
        Provider<Directory> none = project.getLayout().getBuildDirectory().dir(PluginConstants.NONE_DIR);

        TaskProvider<HttpClientExportTask> task = project.getTasks()
            .register(PluginConstants.HTTP_CLIENT_TASK, HttpClientExportTask.class, t -> {
                t.setGroup(PluginConstants.GROUP);
                t.setDescription("Writes the documented requests as an IntelliJ HTTP Client file.");
                // operationId 를 후처리한 스펙에서 읽으므로 openapi3 뒤에 돈다 (openapi3 는 테스트 뒤에 돈다)
                t.dependsOn(openApiTask);
                t.getSnippetFiles().from(project.fileTree(extension.getSnippetsDirectory(), tree -> tree
                    .include(HttpClientStep.SNIPPET_PATTERNS)
                    .exclude(HttpClientStep.SNIPPET_EXCLUDES)));
                t.getSpec().from(specCandidates);
                t.getFileName().set(spec.getFileName().map(HttpClientTasks::fileName));
                t.getOutputDirectory().set(output);
            });

        apiDocs.configure(sync -> {
            sync.dependsOn(spec.getEnabled().map(enabled -> enabled ? List.of(task) : List.of()));
            sync.from(spec.getEnabled().flatMap(enabled -> enabled ? output : none),
                copy -> copy.into(PluginConstants.HTTP_CLIENT_DOCS_DIR));
        });
    }

    /** 요청 파일 경로 (결과 디렉터리 기준). {@code .http} 가 없으면 붙이고, 디렉터리 밖을 가리키면 거부한다. */
    static String fileName(String original) {
        String name = OutputPaths.relativePath(original, original, "kardx.httpClient.fileName", "'api.http'");

        return name.endsWith(".http") ? name : name + ".http";
    }
}
