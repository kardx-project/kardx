package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.OpenApiCheckTask;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.io.File;
import java.util.List;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

/** {@code apiDocsCheck}({@link OpenApiCheckTask})·{@code apiDocsBaseline} 을 등록하고 {@code check} 에 연결한다. */
final class OpenApiCheckTasks {
    private OpenApiCheckTasks() {
    }

    static void register(Project project, KardxExtension extension, Provider<List<File>> specCandidates,
        Object openApiTask) {
        KardxExtension.OpenApiCheck spec = extension.getOpenApiCheck();
        TaskProvider<OpenApiCheckTask> check = project.getTasks()
            .register(PluginConstants.OPEN_API_CHECK_TASK, OpenApiCheckTask.class, task -> {
                task.setGroup(PluginConstants.GROUP);
                task.setDescription("Compares the generated OpenAPI spec with the committed baseline.");
                task.dependsOn(openApiTask);
                // 비교는 빠르고, 깨지지 않는 변경의 경고를 check 마다 보여야 하므로 매번 한다 (UP-TO-DATE 로 건너뛰지 않는다)
                task.getOutputs().upToDateWhen(t -> false);
                task.getSpec().from(specCandidates);
                // 기준 파일은 apiDocsBaseline 의 출력이기도 하다. 비교가 갱신을 끌고 오지 않게 위치만 쓴다
                task.getBaseline().from(spec.getBaseline().getLocationOnly());
                task.mustRunAfter(PluginConstants.OPEN_API_BASELINE_TASK);
                task.getFailOn().set(spec.getFailOn());
                task.getRequireBaseline().set(spec.getRequireBaseline());
                task.getReport().set(project.getLayout().getBuildDirectory()
                    .file(PluginConstants.OPEN_API_CHANGES_REPORT));
            });

        project.getTasks().register(PluginConstants.OPEN_API_BASELINE_TASK, task -> {
            task.setGroup(PluginConstants.GROUP);
            task.setDescription("Replaces the committed OpenAPI baseline with the generated spec.");
            task.dependsOn(openApiTask);
            task.getInputs()
                .files(specCandidates)
                .withPathSensitivity(PathSensitivity.NONE)
                .withPropertyName("spec");
            task.getOutputs()
                .file(spec.getBaseline())
                .withPropertyName("baseline");

            FileCollection generated = project.files(specCandidates);
            RegularFileProperty baseline = spec.getBaseline();

            // Gradle 은 Java 람다로 넘긴 태스크 액션의 구현을 추적하지 못한다(빌드 캐시·up-to-date). 그래서 익명 클래스로 쓴다
            task.doLast(new Action<>() {
                @Override
                public void execute(@Nonnull Task t) {
                    File target = baseline.get().getAsFile();

                    TextFiles.copy(OpenApiSpecFiles.existing(generated.getFiles()), target);
                    t.getLogger().lifecycle("Updated the OpenAPI baseline: {}", target);
                }
            });
        });

        project.getTasks()
            .named(PluginConstants.CHECK_TASK)
            .configure(task -> task.dependsOn(spec.getRunOnCheck().map(on -> on ? List.of(check) : List.of())));
    }
}
