package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.tooling.internal.io.OutputDirectoryGuard;
import java.util.List;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * test 태스크: 스니펫·AsciiDoc 경로와 정한 문서 언어를 시스템 속성으로 넘기고, 실행 전에 지난 결과를 지운다.
 * 지우기 전에 두 디렉터리가 지워도 되는 위치인지 확인한다 ({@link OutputDirectoryGuard}).
 */
public final class TestTaskSetup {
    private static final String SNIPPETS_PROPERTY = "kardx.snippetsDirectory";
    private static final String ASCIIDOC_PROPERTY = "kardx.asciidocDirectory";

    private TestTaskSetup() {
    }

    public static void configure(
        Project project, TaskProvider<Test> test, KardxExtension extension, PluginServices services) {
        DirectoryProperty snippets = extension.getSnippetsDirectory();
        DirectoryProperty asciidoc = extension.getAsciidocDirectory();
        Provider<OutputDirectoryGuard> guard = OutputGuards.of(project, extension);

        test.configure(task -> {
            // 경로는 이 플러그인이 정한다. 시스템 속성은 application.yml 보다 우선한다.
            task.getJvmArgumentProviders().add(() -> List.of(
                "-D" + PluginConstants.PROPERTY_PREFIX + "snippets.directory="
                    + snippets.get().getAsFile().getAbsolutePath(),
                "-D" + PluginConstants.PROPERTY_PREFIX + "asciidoc-directory="
                    + asciidoc.get().getAsFile().getAbsolutePath()));
            DocsLanguageSetup.passTo(task, extension);
            DocsLanguageSetup.announceTo(task, extension);
            task.getOutputs()
                .dir(snippets)
                .withPropertyName("snippetsDirectory");
            task.getOutputs()
                .dir(asciidoc)
                .withPropertyName("asciidocDirectory");
            task.getInputs().property("cleanBeforeTest", extension.getCleanBeforeTest());

            Provider<Boolean> clean = extension.getCleanBeforeTest();
            FileSystemOperations fs = services.getFs();

            // Gradle 은 Java 람다로 넘긴 태스크 액션의 구현을 추적하지 못한다(빌드 캐시·up-to-date). 그래서 익명 클래스로 쓴다
            task.doFirst(new Action<>() {
                @Override
                public void execute(@Nonnull Task t) {
                    if (clean.get()) {
                        OutputDirectoryGuard checks = guard.get();

                        checks.check(snippets.get().getAsFile(), SNIPPETS_PROPERTY);
                        checks.check(asciidoc.get().getAsFile(), ASCIIDOC_PROPERTY);
                        fs.delete(spec -> spec.delete(snippets, asciidoc));
                        checks.mark(snippets.get().getAsFile());
                        checks.mark(asciidoc.get().getAsFile());
                    }
                }
            });
        });
    }
}
