package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.tooling.internal.io.OutputPaths;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/** {@code apiDocsAsyncApi}: 테스트가 남긴 {@code *.asyncapi.json} 으로 AsyncAPI 문서와 뷰어를 만든다. */
public final class AsyncApiTasks {
    /** CLI 가 읽는 스니펫 (core 의 AsyncApiMetadata.FILE_SUFFIX). */
    private static final String METADATA_PATTERN = "**/*.asyncapi.json";

    private AsyncApiTasks() {
    }

    /**
     * @param cliClasspath CLI 실행 클래스패스. 앱·테스트 클래스 출력은 넣지 않는다: CLI 는 스니펫 JSON 만 읽으므로
     *     코드만 바꿨을 때 이 태스크가 다시 돌지 않게 한다
     */
    public static TaskProvider<JavaExec> register(
        Project project, KardxExtension extension, TaskProvider<Test> test, FileCollection cliClasspath,
        Provider<Directory> outputDir, PluginServices services) {
        KardxExtension.AsyncApi spec = extension.getAsyncApi();
        ConfigurableFileTree metadata = project.fileTree(extension.getSnippetsDirectory(),
            tree -> tree.include(METADATA_PATTERN));

        return project.getTasks().register(PluginConstants.ASYNC_API_TASK, JavaExec.class, task -> {
            task.setGroup(PluginConstants.GROUP);
            task.setDescription("Generates AsyncAPI 3.0 for WebSocket, STOMP and SSE messages documented by tests.");
            task.dependsOn(test);
            task.setClasspath(cliClasspath);
            // core 의 asyncapi/AsyncApiCli.kt (@file:JvmName). 옵션 이름도 그 파일의 AsyncApiCliOptions 와 맞춘다
            task.getMainClass().set("io.github.kardx.asyncapi.AsyncApiCli");
            DocsLanguageSetup.passTo(task, extension);
            task.onlyIf("asyncApi.enabled", t -> spec.getEnabled().get());
            task.getInputs()
                .files(metadata)
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .withPropertyName("snippets")
                .ignoreEmptyDirectories();
            task.getInputs().property("title", spec.getTitle());
            task.getInputs().property("version", spec.getVersion());
            task.getInputs()
                .property("description", spec.getDescription())
                .optional(true);
            task.getInputs().property("servers", spec.getServers());
            task.getInputs().property("securitySchemes", spec.getSecuritySchemes());
            task.getInputs().property("fileName", spec.getFileName());
            task.getOutputs()
                .dir(outputDir)
                .withPropertyName("output");

            FileSystemOperations fs = services.getFs();

            // Gradle 은 Java 람다로 넘긴 태스크 액션의 구현을 추적하지 못한다(빌드 캐시·up-to-date). 그래서 익명 클래스로 쓴다
            task.doFirst(new Action<>() {
                @Override
                public void execute(@Nonnull Task t) {
                    fs.delete(d -> d.delete(outputDir));
                }
            });

            task.getArgumentProviders().add(() -> {
                File output = outputDir.get().getAsFile();
                String fileName = fileName(spec);
                List<String> args = new ArrayList<>(List.of(
                    "--snippets", extension.getSnippetsDirectory().get().getAsFile().getAbsolutePath(),
                    "--output", new File(output, fileName + ".yaml").getAbsolutePath(),
                    "--viewer", new File(output, fileName + ".html").getAbsolutePath(),
                    "--title", spec.getTitle().get(),
                    "--version", spec.getVersion().get()));

                if (spec.getDescription().isPresent()) {
                    args.addAll(List.of("--description", spec.getDescription().get()));
                }

                spec.getServers().get().forEach((name, url) -> args.addAll(List.of("--server", name + "=" + url)));
                spec.getSecuritySchemes().get()
                    .forEach((name, scheme) -> args.addAll(List.of("--security", name + "=" + scheme)));

                return args;
            });
        });
    }

    /**
     * AsyncAPI 결과 파일 경로 (확장자 제외, 문서 디렉터리 기준). {@code async/index} 처럼 하위 디렉터리를 쓸 수 있다.
     * 문서 디렉터리 밖을 가리키는 값({@code /x}, {@code ../x})은 거부한다.
     */
    static String fileName(KardxExtension.AsyncApi spec) {
        String original = spec.getFileName().get();

        return OutputPaths.relativePath(original.replaceAll("\\.(html|ya?ml)$", ""), original,
            "kardx.asyncApi.fileName", "'asyncapi' or 'async/index'");
    }
}
