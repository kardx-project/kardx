package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.process.JavaForkOptions;

/**
 * {@code asciidoctor} ({@value PluginConstants#ASCIIDOCTOR_PLUGIN} 가 있으면): 입력, 스니펫 경로, REST Docs 확장을 설정하고
 * {@code html.enabled} 이면 결과를 apiDocs 에 넣는다.
 */
public final class AsciidoctorIntegration {
    /** Spring REST Docs 4 의 Asciidoctor 확장이 필요로 하는 AsciidoctorJ 버전. */
    private static final String ASCIIDOCTORJ_FOR_RESTDOCS_4 = "3.0.1";
    private static final String RESTDOCS_ASCIIDOCTOR = "org.springframework.restdocs:spring-restdocs-asciidoctor";
    /**
     * {@code kardx.asciidoctorJvmArgs} 의 기본값. 변환 JVM 은 몇 초만 살므로 C1 까지만 컴파일한다 (C2 컴파일 비용을 아낀다).
     * Boot 4 샘플 {@code asciidoctor --rerun} 3번씩: 7.12–7.22 초 → 6.71–6.82 초.
     */
    static final List<String> DEFAULT_JVM_ARGS = List.of("-XX:TieredStopAtLevel=1");

    private AsciidoctorIntegration() {
    }

    public static void configure(
        Project project, KardxExtension extension, TaskProvider<Sync> sources, TaskProvider<Sync> apiDocs) {
        project.getPluginManager().withPlugin(PluginConstants.ASCIIDOCTOR_PLUGIN, applied -> {
            createRestDocsConfiguration(project, extension);

            // Spring REST Docs 4 (Boot 4) 의 Asciidoctor 확장은 AsciidoctorJ 3 이 필요하다. Boot 플러그인이 asciidoctor 뒤에
            // 적용될 수 있으므로 Boot 플러그인이 보일 때 정한다. 사용자가 build 스크립트에서 바꾼 값은 유지한다.
            project.getPluginManager().withPlugin(PluginConstants.SPRING_BOOT_PLUGIN, boot -> {
                if (SpringBootIntegration.majorVersion(project) >= 4) {
                    Object asciidoctorj = project.getExtensions().findByName("asciidoctorj");

                    if (asciidoctorj != null) {
                        Reflection.invoke(asciidoctorj, "setVersion", new Class<?>[] {Object.class},
                            ASCIIDOCTORJ_FOR_RESTDOCS_4);
                    }
                }
            });

            // 문서는 스니펫 중 AsciiDoc 만 include 한다. 다른 스니펫(resource.json, *.asyncapi.json)은 입력에서 뺀다
            ConfigurableFileTree snippets = project.fileTree(extension.getSnippetsDirectory(),
                tree -> tree.include("**/*.adoc"));

            project.getTasks()
                .named(PluginConstants.ASCIIDOCTOR_TASK)
                .configure(task -> {
                    task.dependsOn(sources);
                    task.getInputs()
                        .files(snippets)
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                        .withPropertyName("restdocsSnippets")
                        .ignoreEmptyDirectories();
                    Reflection.invoke(task, "configurations", new Class<?>[] {Object[].class},
                        (Object) new Object[] {PluginConstants.ASCIIDOCTOR_CONFIGURATION});
                    Reflection.invoke(task, "setSourceDir", new Class<?>[] {Object.class},
                        sources.get().getDestinationDir());
                    Reflection.invoke(task, "sources", new Class<?>[] {Action.class},
                        (Action<PatternSet>) pattern -> pattern.include("**/*.adoc")
                            .exclude("**/_*"));
                    Reflection.invoke(task, "baseDirFollowsSourceDir", new Class<?>[0]);
                    Reflection.invoke(task, "attributes", new Class<?>[] {Map.class},
                        Map.of("snippets", extension.getSnippetsDirectory().get().getAsFile().getAbsolutePath()));
                });

            configureJvmArgs(project, extension);

            addHtml(project, extension, apiDocs);
        });
    }

    /**
     * {@code html.enabled} 이면 apiDocs 가 {@code asciidoctor} 를 실행하고 HTML 을 넣는다. 꺼져 있으면 둘 다 하지 않으므로
     * apiDocs(Sync)가 지난 실행의 HTML 도 지운다. 사용자가 {@code asciidoctor} 를 직접 부르면 그대로 변환한다.
     */
    private static void addHtml(Project project, KardxExtension extension, TaskProvider<Sync> apiDocs) {
        Provider<Boolean> enabled = extension.getHtml().getEnabled();
        Provider<Directory> none = project.getLayout().getBuildDirectory().dir(PluginConstants.NONE_DIR);

        apiDocs.configure(task -> {
            // Callable 은 실행 그래프를 만들 때 부른다 (값으로 만든 Provider 는 dependsOn 이 태스크를 찾지 않는다)
            task.dependsOn((Callable<Object>) () -> enabled.get()
                ? List.of(project.getTasks().named(PluginConstants.ASCIIDOCTOR_TASK))
                : List.of());
            task.from(project.provider(() -> enabled.get()
                ? Reflection.invoke(project.getTasks().getByName(PluginConstants.ASCIIDOCTOR_TASK), "getOutputDir",
                    new Class<?>[0])
                : none.get().getAsFile()));
        });
    }

    /**
     * 변환 JVM 인자({@code kardx.asciidoctorJvmArgs})를 {@code forkOptions} 로 더한다. 빌드 스크립트 본문이 값을 바꿀 수
     * 있으므로 afterEvaluate 에서 읽는다. 사용자가 {@code tasks.asciidoctor { jvm { } }} 에 더한 인자는 그대로 둔다.
     */
    private static void configureJvmArgs(Project project, KardxExtension extension) {
        project.afterEvaluate(p -> {
            List<String> jvmArgs = extension.getAsciidoctorJvmArgs().get();

            if (jvmArgs.isEmpty()) {
                return;
            }

            p.getTasks()
                .named(PluginConstants.ASCIIDOCTOR_TASK)
                .configure(task -> Reflection.invoke(task, "forkOptions", new Class<?>[] {Action.class},
                    (Action<JavaForkOptions>) options -> options.jvmArgs(jvmArgs)));
        });
    }

    /** asciidoctor 태스크가 쓸 spring-restdocs-asciidoctor. 버전은 Spring Boot BOM, 없으면 {@code restDocsVersion}. */
    private static void createRestDocsConfiguration(Project project, KardxExtension extension) {
        Configuration restdocs = project.getConfigurations().create(PluginConstants.ASCIIDOCTOR_CONFIGURATION);

        restdocs.setDescription("spring-restdocs-asciidoctor for the asciidoctor task");
        restdocs.setCanBeConsumed(false);
        restdocs.withDependencies(deps -> {
            String bom = SpringBootIntegration.bomCoordinates(project);

            if (bom != null) {
                deps.add(project.getDependencies().platform(bom));
                deps.add(project.getDependencies().create(RESTDOCS_ASCIIDOCTOR));
            } else if (extension.getRestDocsVersion().isPresent()) {
                deps.add(project.getDependencies()
                    .create(RESTDOCS_ASCIIDOCTOR + ":" + extension.getRestDocsVersion().get()));
            } else {
                throw new GradleException(
                    "Set kardx.restDocsVersion (no Spring Boot plugin to take it from).");
            }
        });
    }
}
