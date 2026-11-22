package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * 앱을 실행하려고 빌드할 때 문서를 자동으로 만든다.
 * <ul>
 *   <li>IntelliJ: main 실행 전에 Gradle 로 {@code :classes} 를 부른다 ({@code -Didea.active=true}, 동기화는 제외).
 *       classes 는 test 의 입력이라 apiDocs 에 의존시킬 수 없으므로(순환) 빌드 끝에 apiDocs 를 이어서 실행한다</li>
 *   <li>{@code bootRun}: 시작 전에 apiDocs 를 실행한다</li>
 * </ul>
 * 이렇게 실행할 때는 문서 테스트가 실패해도 앱 실행을 막지 않는다. 바뀐 것이 없으면 테스트는 up-to-date 다.
 */
public final class GenerateOnRun {
    /**
     * IntelliJ 가 앱(main) 실행 전에 부르는 빌드에 붙는 태스크 (이름만 본다). {@code testClasses} 는 넣지 않는다: IntelliJ 가
     * 테스트 하나를 자기 러너로 실행할 때도 {@code :classes :testClasses} 를 부르므로, 넣으면 테스트 하나를 돌려도 전체 문서 테스트와
     * apiDocs 가 먼저 돈다. 그래서 테스트 소스의 main(예: Testcontainers 용 {@code TestApplication}) 실행에서는 문서를 만들지 않는다.
     */
    private static final Set<String> IDE_RUN_TASKS = Set.of("classes", "processResources");

    /** 다음 토큰을 값으로 갖는 태스크 옵션 ({@code bootRun --args foo}, {@code test --tests Foo}). */
    private static final Set<String> OPTIONS_WITH_VALUE = Set.of("--args", "--tests");

    private GenerateOnRun() {
    }

    public static void configure(
        Project project, KardxExtension extension, TaskProvider<Test> test, TaskProvider<Sync> apiDocs) {
        List<String> requested = requestedTaskNames(project.getGradle().getStartParameter().getTaskNames());
        boolean ide = "true".equals(System.getProperty("idea.active"))
            && !"true".equals(System.getProperty("idea.sync.active"));
        boolean ideRun = ide && isIdeAppRun(requested);
        boolean bootRun = !requested.isEmpty()
            && requested.stream().allMatch(PluginConstants.BOOT_RUN_TASK::equals);

        if (!ideRun && !bootRun) {
            return;
        }

        project.afterEvaluate(p -> {
            if (!extension.getGenerateOnRun().get()) {
                return;
            }

            test.configure(task -> task.setIgnoreFailures(true));

            if (ideRun) {
                project.getTasks()
                    .named(PluginConstants.CLASSES_TASK)
                    .configure(task -> task.finalizedBy(apiDocs));
            }

            if (bootRun) {
                project.getPluginManager().withPlugin(PluginConstants.SPRING_BOOT_PLUGIN, applied ->
                    project.getTasks()
                        .named(PluginConstants.BOOT_RUN_TASK)
                        .configure(task ->
                            task.dependsOn(apiDocs, project.getTasks().named(PluginConstants.APP_DOCS_TASK))));
            }

            project.getLogger().lifecycle(
                "kardx: generating API docs before the app starts (turn off with "
                    + "kardx { generateOnRun = false })");
        });
    }

    /** IntelliJ 가 앱 실행 전에 부르는 빌드인가 (main 출력만 만든다). */
    static boolean isIdeAppRun(List<String> requested) {
        return !requested.isEmpty() && IDE_RUN_TASKS.containsAll(requested);
    }

    /** 태스크 옵션({@code --args=...}, {@code --args foo}, {@code --tests Foo})을 빼고 태스크 이름만 남긴다. */
    static List<String> requestedTaskNames(List<String> arguments) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = arguments.iterator();

        while (iterator.hasNext()) {
            String argument = iterator.next();

            if (argument.startsWith("-")) {
                if (OPTIONS_WITH_VALUE.contains(argument) && iterator.hasNext()) {
                    iterator.next();
                }

                continue;
            }

            names.add(argument.substring(argument.lastIndexOf(':') + 1));
        }

        return names;
    }
}
