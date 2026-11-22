package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.tooling.internal.io.OutputDirectoryGuard;
import java.io.File;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

/**
 * {@code apiDocs}: HTML, OpenAPI, AsyncAPI 를 한 디렉터리로 모은다. 각 결과는 해당 연동이 {@code from} 을 더한다.
 * {@code apiDocsAppDocs}: apiDocs 뒤에 항상 실행되어 앱 리소스로 복사하고 위치를 출력한다.
 */
public final class ApiDocsTasks {
    private static final String OUTPUT_PROPERTY = "kardx.outputDirectory";

    private ApiDocsTasks() {
    }

    public static TaskProvider<Sync> registerApiDocs(
        Project project, KardxExtension extension, TaskProvider<JavaExec> asyncApi,
        Provider<Directory> asyncApiOutput) {
        return project.getTasks().register(PluginConstants.API_DOCS_TASK, Sync.class, task -> {
            task.setGroup(PluginConstants.GROUP);
            task.setDescription("Collects REST Docs HTML, OpenAPI and AsyncAPI into one directory.");
            task.dependsOn(asyncApi);

            // asyncApi.enabled = false 면 지난 결과가 남아 있어도 넣지 않는다
            Provider<Directory> none = project.getLayout().getBuildDirectory().dir(PluginConstants.NONE_DIR);

            task.from(extension.getAsyncApi().getEnabled().flatMap(enabled -> enabled ? asyncApiOutput : none),
                copy -> copy.include("**/*.yaml", "**/*.html"));
            task.into(extension.getOutputDirectory());
            guardOutput(task, extension.getOutputDirectory(), OutputGuards.of(project, extension));
        });
    }

    /**
     * Sync 는 결과 디렉터리에서 문서가 아닌 파일을 지운다. 그래서 지워도 되는 위치인지 먼저 확인하고
     * ({@link OutputDirectoryGuard}), 빌드 디렉터리 밖이면 표시 파일을 남기고 다음 동기화에서도 지키게 한다.
     */
    private static void guardOutput(Sync task, DirectoryProperty output, Provider<OutputDirectoryGuard> guard) {
        task.preserve(keep -> keep.include(OutputDirectoryGuard.MARKER));
        // Gradle 은 Java 람다로 넘긴 태스크 액션의 구현을 추적하지 못한다(빌드 캐시·up-to-date). 그래서 익명 클래스로 쓴다
        task.doFirst(new Action<>() {
            @Override
            public void execute(@Nonnull Task t) {
                guard.get().check(output.get().getAsFile(), OUTPUT_PROPERTY);
            }
        });
        task.doLast(new Action<>() {
            @Override
            public void execute(@Nonnull Task t) {
                guard.get().mark(output.get().getAsFile());
            }
        });
    }

    /**
     * apiDocs 뒤에 항상 실행되는 마무리 태스크 {@code apiDocsAppDocs}. apiDocs 가 UP-TO-DATE 여도 실행되므로
     * 앱 리소스의 문서가 지워졌거나 설정이 바뀐 경우에도 다시 맞춘다.
     * <ul>
     *   <li>Spring Boot 앱이면 문서를 {@code build/resources/main/<bootJar.path>} 로 복사한다. 그래서 실행 jar 뿐 아니라
     *       IDE 실행·bootRun 도 같은 위치에서 문서를 서빙한다. 앱이 같은 디렉터리에 둔 파일은 지우지 않는다
     *       ({@link AppDocsCopy})</li>
     *   <li>생성 위치를 출력한다</li>
     * </ul>
     */
    public static void registerAppDocs(
        Project project, KardxExtension extension, TaskProvider<Sync> apiDocs) {
        KardxExtension.BootJar spec = extension.getBootJar();
        DirectoryProperty output = extension.getOutputDirectory();
        Provider<RegularFile> manifest = project.getLayout()
            .getBuildDirectory()
            .file(PluginConstants.APP_DOCS_MANIFEST);

        TaskProvider<Task> appDocs = project.getTasks().register(PluginConstants.APP_DOCS_TASK, task -> {
            task.setDescription("Copies API docs into the app's static resources and prints where they are.");
            task.dependsOn(apiDocs);
        });

        apiDocs.configure(task -> task.finalizedBy(appDocs));
        project.afterEvaluate(p -> {
            boolean boot = p.getPluginManager().hasPlugin(PluginConstants.SPRING_BOOT_PLUGIN);
            boolean openApi = p.getPluginManager().hasPlugin(PluginConstants.EPAGES_PLUGIN);
            int bootMajor = boot ? SpringBootIntegration.majorVersion(p) : 0;
            // postman 태스크는 epages 가 afterEvaluate 에서 만들므로 파일 이름은 실행 그래프를 만들 때 정한다
            Provider<File> postman = p.provider(() -> openApi && extension.getPostmanCollection().getEnabled().get()
                ? EpagesOutputs.postmanFile(p)
                : null);
            File resources = boot
                ? p.getExtensions()
                    .getByType(SourceSetContainer.class)
                    .getByName("main").getOutput().getResourcesDir()
                : null;

            // Gradle 은 Java 람다로 넘긴 태스크 액션의 구현을 추적하지 못한다(빌드 캐시·up-to-date). 그래서 익명 클래스로 쓴다
            appDocs.configure(task -> task.doLast(new Action<>() {
                @Override
                public void execute(@Nonnull Task t) {
                    copyToResources(output, resources, spec, manifest.get().getAsFile());
                    writeLocations(t.getLogger(), resources, extension, openApi, postman.getOrNull());
                    DocsLocations.print(t, extension, openApi, postman.getOrNull(), bootMajor);
                }
            }));
        });
    }

    /**
     * Spring Boot 앱이면 앱 리소스에 넣은 문서의 경로·파일 이름을 boot 모듈이 읽을 파일로 남기고({@link AppDocsProperties}),
     * 민감 헤더를 가리지 않은 문서가 앱에 들어가면 경고한다({@link ProductionExposure}). 문서를 넣지 않으면
     * ({@code bootJar.enabled = false}) 지난 파일을 지운다.
     */
    private static void writeLocations(
        Logger logger, File resources, KardxExtension extension, boolean openApi, File postman) {
        if (resources == null) {
            return;
        }

        if (extension.getBootJar().getEnabled().get()) {
            AppDocsProperties.write(resources, extension.getOutputDirectory().get().getAsFile(), extension, openApi,
                postman);
            ProductionExposure.warnIfUnmasked(logger, extension.getAsciidocDirectory().get().getAsFile());
        } else {
            AppDocsProperties.delete(resources);
        }
    }

    /**
     * Spring Boot 앱이고 {@code bootJar.enabled} 이면 문서를 {@code build/resources/main/<path>} 로 복사하고, 지난 실행에 복사했지만
     * 이제 없는 문서를 지운다. 꺼져 있으면 지난 실행이 복사한 문서를 지운다.
     */
    private static void copyToResources(
        DirectoryProperty output, File resources, KardxExtension.BootJar spec, File manifest) {
        if (resources == null) {
            return;
        }

        if (spec.getEnabled().get()) {
            AppDocsCopy.copy(output.get().getAsFile(), resources, BootJarPath.of(spec.getPath().get()), manifest);
        } else {
            AppDocsCopy.removePrevious(resources, manifest);
        }
    }
}
