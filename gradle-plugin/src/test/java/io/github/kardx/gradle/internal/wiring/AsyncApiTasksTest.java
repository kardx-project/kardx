package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.kardx.gradle.KardxPlugin;
import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.tooling.ToolingException;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.JavaExec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsyncApiTasksTest {

    @Test
    void argumentsStayTheSameWithoutSecuritySchemes(@TempDir File dir) {
        Project project = javaProject(dir);
        JavaExec task = asyncApiTask(project);
        File snippets = extension(project).getSnippetsDirectory().get().getAsFile();
        File output = new File(project.getLayout().getBuildDirectory().get().getAsFile(),
            PluginConstants.ASYNC_API_OUTPUT_DIR);

        assertEquals(List.of(
            "--snippets", snippets.getAbsolutePath(),
            "--output", new File(output, "asyncapi.yaml").getAbsolutePath(),
            "--viewer", new File(output, "asyncapi.html").getAbsolutePath(),
            "--title", project.getName(),
            "--version", "unspecified"), arguments(task));
        assertEquals(Map.of(), task.getInputs().getProperties().get("securitySchemes"));
    }

    @Test
    void securitySchemesBecomeRepeatedArgumentsAfterServers(@TempDir File dir) {
        Project project = javaProject(dir);
        KardxExtension.AsyncApi spec = extension(project).getAsyncApi();
        spec.server("stomp", "stomp://localhost:8080/ws");
        spec.bearerAuth();
        spec.basicAuth();
        spec.apiKeyHeader("X-API-KEY");
        JavaExec task = asyncApiTask(project);
        List<String> args = arguments(task);

        assertEquals(List.of(
            "--server", "stomp=stomp://localhost:8080/ws",
            "--security", "bearerAuth=bearer",
            "--security", "basicAuth=basic",
            "--security", "apiKeyAuth=apiKey:header:X-API-KEY"), args.subList(args.indexOf("--server"), args.size()));
    }

    @Test
    void securitySchemesAreTaskInputs(@TempDir File dir) {
        Project project = javaProject(dir);
        KardxExtension.AsyncApi spec = extension(project).getAsyncApi();
        JavaExec task = asyncApiTask(project);
        spec.bearerAuth();

        assertEquals(Map.of("bearerAuth", "bearer"), task.getInputs().getProperties().get("securitySchemes"));

        // 같은 이름은 하나뿐: 헤더를 바꾸면 입력 값이 바뀌어 다시 돈다
        spec.apiKeyHeader("X-API-KEY");
        spec.apiKeyHeader("X-Client-Key");

        assertEquals(Map.of("bearerAuth", "bearer", "apiKeyAuth", "apiKey:header:X-Client-Key"),
            task.getInputs().getProperties().get("securitySchemes"));
    }

    @Test
    void apiKeyHeaderRejectsInvalidHeaderNames(@TempDir File dir) {
        KardxExtension.AsyncApi spec = spec(dir);

        for (String invalid : new String[] {null, "", " ", "X API KEY", "X-Key:1"}) {
            assertThrows(GradleException.class, () -> spec.apiKeyHeader(invalid), invalid);
        }

        assertEquals(Map.of(), spec.getSecuritySchemes().get());
    }

    @Test
    void fileNameDropsExtensionAndAllowsSubdirectories(@TempDir File dir) {
        KardxExtension.AsyncApi spec = spec(dir);

        assertEquals("asyncapi", fileName(spec, "asyncapi"));
        assertEquals("asyncapi", fileName(spec, "asyncapi.yaml"));
        assertEquals("async/index", fileName(spec, "async/index.html"));
        assertEquals("async/index", fileName(spec, "async\\index"));
    }

    @Test
    void fileNameRejectsPathsOutsideTheDocsDirectory(@TempDir File dir) {
        KardxExtension.AsyncApi spec = spec(dir);

        for (String invalid : new String[] {"", "/abs", "../up", "a/../../b", "dir/"}) {
            assertThrows(ToolingException.class, () -> fileName(spec, invalid), invalid);
        }
    }

    private static String fileName(KardxExtension.AsyncApi spec, String value) {
        spec.getFileName().set(value);

        return AsyncApiTasks.fileName(spec);
    }

    private static KardxExtension.AsyncApi spec(File dir) {
        Project project = ProjectBuilder.builder()
            .withProjectDir(dir)
            .build();

        return project.getObjects().newInstance(KardxExtension.class).getAsyncApi();
    }

    private static Project javaProject(File dir) {
        Project project = ProjectBuilder.builder()
            .withProjectDir(dir)
            .build();
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(KardxPlugin.class);
        extension(project).getAddDependencies().set(false);

        return project;
    }

    private static KardxExtension extension(Project project) {
        return project.getExtensions().getByType(KardxExtension.class);
    }

    private static JavaExec asyncApiTask(Project project) {
        ((ProjectInternal) project).evaluate();

        return (JavaExec) project.getTasks().getByName(PluginConstants.ASYNC_API_TASK);
    }

    private static List<String> arguments(JavaExec task) {
        return task.getArgumentProviders()
            .stream()
            .flatMap(p -> StreamSupport.stream(p.asArguments().spliterator(), false))
            .toList();
    }
}
