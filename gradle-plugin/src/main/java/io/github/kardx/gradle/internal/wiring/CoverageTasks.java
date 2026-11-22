package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.ApiDocsCoverageTask;
import io.github.kardx.tooling.internal.coverage.CoverageInputs;
import io.github.kardx.tooling.internal.steps.CoverageReportStep;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * {@code apiDocsCoverage}({@link ApiDocsCoverageTask})를 등록하고 {@code apiDocs} 에 넣는다 ({@code coverage.enabled} 일 때).
 * 엔드포인트 목록 디렉터리({@value CoverageInputs#ENDPOINTS_DIR})는 문서 조각이 아니므로 operation 입력에서 뺀다.
 */
final class CoverageTasks {

    private CoverageTasks() {
    }

    static void register(
        Project project, KardxExtension extension, TaskProvider<Test> test, TaskProvider<Sync> apiDocs) {
        KardxExtension.Coverage coverage = extension.getCoverage();
        Provider<Directory> output = project.getLayout().getBuildDirectory().dir(PluginConstants.COVERAGE_DIR);
        Provider<Directory> none = project.getLayout().getBuildDirectory().dir(PluginConstants.NONE_DIR);
        File projectDir = project.getProjectDir();

        TaskProvider<ApiDocsCoverageTask> task = project.getTasks()
            .register(PluginConstants.COVERAGE_TASK, ApiDocsCoverageTask.class, t -> {
                t.setGroup(PluginConstants.GROUP);
                t.setDescription("Reports which application endpoints are documented by the documentation tests.");
                t.dependsOn(test);
                t.getEndpointFiles().from(project.fileTree(extension.getSnippetsDirectory(),
                    tree -> tree.include(CoverageReportStep.ENDPOINT_PATTERNS)));
                t.getOperationFiles().from(project.fileTree(extension.getSnippetsDirectory(), tree -> tree
                    .include(CoverageReportStep.OPERATION_PATTERNS)
                    .exclude(CoverageReportStep.OPERATION_EXCLUDES)));
                t.getExcludes().set(excludes(extension));
                t.getFailOnUndocumented().set(coverage.getFailOnUndocumented());
                t.getOutputDirectory().set(output);
                t.getReportLocation().set(extension.getOutputDirectory().map(docs -> relative(projectDir,
                    new File(docs.getAsFile(), PluginConstants.COVERAGE_DOCS_DIR + "/"
                        + ApiDocsCoverageTask.MARKDOWN_FILE))));
            });

        apiDocs.configure(sync -> {
            sync.dependsOn(coverage.getEnabled().map(enabled -> enabled ? List.of(task) : List.of()));
            sync.from(coverage.getEnabled().flatMap(enabled -> enabled ? output : none),
                copy -> copy.into(PluginConstants.COVERAGE_DOCS_DIR));
        });
    }

    /** 기본 제외 경로, 문서 경로({@code /docs/**}), 사용자 제외 경로. */
    private static Provider<List<String>> excludes(KardxExtension extension) {
        return extension.getCoverage().getExcludes().zip(extension.getBootJar().getPath(), (configured, path) -> {
            // 언제나 빼는 경로({@link CoverageReportStep#DEFAULT_EXCLUDES})에 문서 경로를 더한다.
            // configuration cache 가 값을 저장하므로 JDK 불변 목록 대신 ArrayList 로 준다
            List<String> excludes = new ArrayList<>(CoverageReportStep.DEFAULT_EXCLUDES);

            excludes.add("/" + DocsLocations.webPath(path) + "/**");
            excludes.addAll(configured);

            return excludes;
        });
    }

    private static String relative(File projectDir, File file) {
        String path = file.toPath().startsWith(projectDir.toPath())
            ? projectDir.toPath().relativize(file.toPath()).toString()
            : file.getPath();

        return path.replace('\\', '/');
    }
}
