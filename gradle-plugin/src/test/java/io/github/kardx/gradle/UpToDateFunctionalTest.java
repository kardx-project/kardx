package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 아무것도 바뀌지 않은 {@code apiDocs} 를 다시 돌리면 결과를 만드는 태스크가 UP-TO-DATE 이고, 입력이 바뀌면 다시 돈다.
 * epages 는 테스트 클래스패스의 가짜 플러그인이다: 가짜 {@code openapi3} 는 {@code spec.yaml} 을 결과 위치에 복사하고, 가짜
 * {@code postman} 은 자기가 읽는 조각 복사본의 operationId 로 Collection 을 쓴다.
 */
class UpToDateFunctionalTest {
    private static final String BUILD = """
        plugins {
            id 'java'
            id 'com.epages.restdocs-api-spec'
            id 'io.github.kardx-project'
        }
        kardx {
            addDependencies = false
            cleanBeforeTest = false
            snippetsDirectory = layout.projectDirectory.dir('snippets')
            asyncApi { enabled = false }
            openApiViewer { enabled = true }
            postmanCollection { enabled = true }
        }
        // index CLI 는 core 가 없으면 돌 수 없다
        tasks.named('apiDocsIndex') { enabled = false }
        tasks.matching { it.name == 'openapi3' }.configureEach { t ->
            def source = file('spec.yaml')
            def target = layout.buildDirectory.file('api-spec/openapi3.yaml').get().asFile
            t.doLast { target.parentFile.mkdirs(); target.text = source.text }
        }
        tasks.matching { it.name == 'postman' }.configureEach { t ->
            def target = layout.buildDirectory.file('api-spec/postman-collection.json').get().asFile
            t.doLast {
                def ids = fileTree(t.snippetsDirectory) { include '**/resource.json' }.files
                    .collect { new groovy.json.JsonSlurper().parse(it).operationId }
                    .sort()
                target.parentFile.mkdirs()
                target.text = groovy.json.JsonOutput.toJson([info: [name: 'shop'], item: ids.collect { [name: it] }])
            }
        }
        """;
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: %s, version: 1}
        paths:
          /orders: {get: {operationId: order-list, responses: {"200": {description: OK}}}}
        """;
    private static final String POSTMAN = ":postman";
    private static final String VIEWER = ":apiDocsViewer";

    @TempDir
    File root;

    @BeforeEach
    void writeProject() throws IOException {
        write("settings.gradle", "rootProject.name = 'shop'\n");
        write("build.gradle", BUILD);
        write("spec.yaml", SPEC.formatted("Shop"));
        snippet("order-list", "GET");
    }

    @Test
    void noChangeApiDocsLeavesPostmanAndViewerUpToDate() {
        BuildResult first = gradle("apiDocs");

        assertEquals(TaskOutcome.SUCCESS, outcome(first, POSTMAN));
        assertEquals(TaskOutcome.SUCCESS, outcome(first, VIEWER));

        BuildResult second = gradle("apiDocs");

        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, POSTMAN), second.getOutput());
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, VIEWER), second.getOutput());
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, ":apiDocs"), second.getOutput());
        // 앱 리소스의 문서가 지워져도 다시 채우고 위치를 출력하려고 늘 돈다
        assertEquals(TaskOutcome.SUCCESS, outcome(second, ":apiDocsAppDocs"));
        assertTrue(second.getOutput().contains("Generated API docs"), second.getOutput());
    }

    @Test
    void postmanRerunsWhenTheSnippetsOrItsCollectionChange() throws IOException {
        gradle("apiDocs");
        snippet("order-create", "POST");

        BuildResult changed = gradle("apiDocs");
        File collection = new File(root, "build/api-docs/postman-collection.json");

        assertEquals(TaskOutcome.SUCCESS, outcome(changed, POSTMAN), "새 조각은 Collection 에 들어가야 한다");
        assertTrue(Files.readString(collection.toPath()).contains("order-create"), Files.readString(collection.toPath()));
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(changed, VIEWER), "스펙은 그대로다");

        Files.delete(new File(root, "build/api-spec/postman-collection.json").toPath());

        BuildResult deleted = gradle("apiDocs");

        assertEquals(TaskOutcome.SUCCESS, outcome(deleted, POSTMAN), "지운 출력은 다시 만든다");
        assertTrue(new File(root, "build/api-spec/postman-collection.json").isFile());
    }

    /**
     * {@code enabled} 가 고정값이 아니라 provider(Gradle 속성, 환경 변수)여도 apiDocs 가 postman 을 실행하거나 건너뛴다.
     * 값을 실행 그래프를 만들 때 읽어야 한다 (provider 를 그대로 dependsOn 에 넘기면 태스크를 찾지 못할 수 있다).
     */
    @Test
    void postmanFollowsAProviderValue() throws IOException {
        write("build.gradle", BUILD.replace("postmanCollection { enabled = true }",
            "postmanCollection { enabled = providers.gradleProperty('postman').map { it.toBoolean() }.orElse(false) }"));

        BuildResult on = gradle("apiDocs", "-Ppostman=true");

        assertEquals(TaskOutcome.SUCCESS, outcome(on, POSTMAN), on.getOutput());
        assertTrue(new File(root, "build/api-docs/postman-collection.json").isFile());

        BuildResult off = gradle("apiDocs");

        assertNull(outcome(off, POSTMAN), off.getOutput());
        assertFalse(new File(root, "build/api-docs/postman-collection.json").exists(), "꺼지면 결과에서 뺀다");
    }

    /**
     * 늘 도는 {@code openapi3} 가 스펙만 바꾼 빌드. 출력을 알리지 않으면 Gradle 이 지난 빌드에 기억한 스펙으로 판단해
     * {@code apiDocs}·뷰어가 UP-TO-DATE 로 지난 결과를 남긴다 (파일 시스템 감시 이벤트가 늦으면, 자주).
     */
    @Test
    void viewerRerunsWhenTheSpecOrItsSettingsChange() throws IOException {
        gradle("apiDocs");
        write("spec.yaml", SPEC.formatted("Renamed Shop"));

        BuildResult specChanged = gradle("apiDocs");
        File page = new File(root, "build/api-docs/openapi.html");

        assertEquals(TaskOutcome.SUCCESS, outcome(specChanged, VIEWER), "openapi3 가 같은 빌드에서 바꾼 스펙을 본다");
        assertTrue(Files.readString(page.toPath()).contains("Renamed Shop"));
        assertTrue(Files.readString(new File(root, "build/api-docs/openapi3.yaml").toPath()).contains("Renamed Shop"),
            "apiDocs 도 바뀐 스펙을 넣는다 (Gradle 이 openapi3 가 쓴 파일을 안다)");
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(specChanged, POSTMAN), "조각은 그대로다");

        write("build.gradle", BUILD + "kardx { openApiViewer { type = 'scalar' } }\n");

        BuildResult settingChanged = gradle("apiDocs");

        assertEquals(TaskOutcome.SUCCESS, outcome(settingChanged, VIEWER));
        assertTrue(Files.readString(page.toPath()).toLowerCase().contains("scalar"));
    }

    private void snippet(String id, String method) throws IOException {
        write("snippets/" + id + "/resource.json", """
            {"operationId": "%s", "request": {"path": "/orders", "method": "%s"}, "response": {"status": 200}}
            """.formatted(id, method));
    }

    private BuildResult gradle(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(List.of(arguments))
            .build();
    }

    private static TaskOutcome outcome(BuildResult result, String path) {
        return result.task(path) == null ? null : result.task(path).getOutcome();
    }

    private void write(String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
