package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.GradleToolLog;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.pages.OpenApiViewerPage;
import io.github.kardx.tooling.internal.steps.OpenApiViewerStep;
import java.io.File;
import java.util.List;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

/**
 * {@code apiDocsViewer}: {@code openapi3} 결과로 정적 뷰어 페이지({@link OpenApiViewerPage})를 만들어
 * {@code apiDocs} 에 넣는다. {@code openApiViewer.enabled = true} 일 때만 실행된다.
 */
public final class OpenApiViewerTasks {
    private OpenApiViewerTasks() {
    }

    static void register(Project project, KardxExtension extension, Provider<List<File>> specCandidates,
        Object openApiTask, TaskProvider<Sync> apiDocs) {
        KardxExtension.OpenApiViewer spec = extension.getOpenApiViewer();
        Provider<Directory> output = project.getLayout().getBuildDirectory().dir(PluginConstants.OPEN_API_VIEWER_DIR);
        Provider<Directory> none = project.getLayout().getBuildDirectory().dir(PluginConstants.NONE_DIR);
        FileCollection specs = project.files(specCandidates);
        Provider<String> language = DocsLanguageSetup.effective(extension);

        TaskProvider<Task> viewer = project.getTasks().register(PluginConstants.OPEN_API_VIEWER_TASK, task -> {
            task.setGroup(PluginConstants.GROUP);
            task.setDescription("Generates a static Redoc or Scalar page for the OpenAPI spec.");
            task.dependsOn(openApiTask);
            task.onlyIf("openApiViewer.enabled", t -> spec.getEnabled().get());
            task.getInputs()
                .files(specCandidates)
                .withPathSensitivity(PathSensitivity.NONE)
                .withPropertyName("spec");
            task.getInputs().property("type", spec.getType());
            task.getInputs().property("fileName", spec.getFileName());
            task.getInputs().property(DocsLanguageSetup.INPUT, language);
            task.getInputs()
                .property("title", spec.getTitle())
                .optional(true);
            task.getOutputs()
                .dir(output)
                .withPropertyName("output");

            // Gradle 은 Java 람다로 넘긴 태스크 액션의 구현을 추적하지 못한다(빌드 캐시·up-to-date). 그래서 익명 클래스로 쓴다
            task.doLast(new Action<>() {
                @Override
                public void execute(@Nonnull Task t) {
                    OpenApiViewerStep.run(specs.getFiles(), output.get().getAsFile(),
                        new OpenApiViewerStep.Settings(spec.getType().get(), spec.getTitle().getOrNull(),
                            fileName(spec), DocsLanguage.of(language.get())),
                        GradleToolLog.of(t.getLogger()));
                }
            });
        });

        apiDocs.configure(task -> {
            // 꺼져 있으면 태스크 그래프에도 넣지 않는다 (기존 출력과 같게)
            task.dependsOn(spec.getEnabled().map(enabled -> enabled ? List.of(viewer) : List.of()));
            task.from(spec.getEnabled().flatMap(enabled -> enabled ? output : none));
        });
    }

    /** 뷰어 파일 경로 (문서 디렉터리 기준). {@code .html} 이 없으면 붙이고, 문서 디렉터리 밖을 가리키면 거부한다. */
    public static String fileName(KardxExtension.OpenApiViewer spec) {
        return OpenApiViewerStep.fileName(spec.getFileName().get(), "kardx.openApiViewer.fileName");
    }
}
