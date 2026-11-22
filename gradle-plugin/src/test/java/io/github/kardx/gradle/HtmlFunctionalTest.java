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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gradle TestKit: {@code html { enabled }} 과 Gradle 속성 {@code kardx.html} 로 HTML 변환을 건너뛴다. Asciidoctor
 * 플러그인은 가짜다 ({@code index.html} 하나를 쓴다).
 */
class HtmlFunctionalTest {
    private static final String BUILD = """
        plugins {
            id 'java'
            id 'org.asciidoctor.jvm.convert'
            id 'io.github.kardx-project'
        }
        kardx {
            asyncApi { enabled = false }
            addDependencies = false
            %s
        }
        // 가짜 asciidoctor: 결과 위치에 index.html 을 쓴다
        tasks.named('asciidoctor') { t ->
            def target = layout.buildDirectory.file('docs/asciidoc/index.html').get().asFile
            t.doLast { target.parentFile.mkdirs(); target.text = '<html/>' }
        }
        // 테스트 없이 OpenAPI 스펙 하나를 결과에 더한다
        tasks.named('apiDocs') { from('spec') }
        """;

    @Test
    void propertySkipsTheConversionAndRemovesEarlierHtml(@TempDir File root) throws IOException {
        setUp(root, "");

        BuildResult withHtml = gradle(root, "apiDocs", "--configuration-cache").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(withHtml, ":asciidoctor"), withHtml.getOutput());
        assertTrue(new File(root, "build/api-docs/index.html").isFile());

        BuildResult without = gradle(root, "apiDocs", "-Pkardx.html=false", "--configuration-cache").build();

        assertNull(outcome(without, ":asciidoctor"), "asciidoctor 를 실행하지 않는다\n" + without.getOutput());
        assertNull(outcome(without, ":apiDocsIndex"), "HTML 입력도 만들지 않는다");
        assertFalse(new File(root, "build/api-docs/index.html").exists(), "지난 실행의 HTML 은 지운다");
        assertTrue(new File(root, "build/api-docs/openapi3.yaml").isFile(), "다른 문서는 그대로");
        assertTrue(without.getOutput().contains("REST Docs HTML    skipped"), without.getOutput());
    }

    @Test
    void buildScriptValueWinsOverTheProperty(@TempDir File root) throws IOException {
        setUp(root, "html { enabled = false }");

        BuildResult result = gradle(root, "apiDocs", "-Pkardx.html=true").build();

        assertNull(outcome(result, ":asciidoctor"), result.getOutput());
        assertTrue(new File(root, "build/api-docs/openapi3.yaml").isFile());
    }

    @Test
    void asciidoctorStillRunsWhenAskedForDirectly(@TempDir File root) throws IOException {
        setUp(root, "html { enabled = false }");

        BuildResult result = gradle(root, "asciidoctor").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":asciidoctor"), result.getOutput());
    }

    @Test
    void rejectsAnInvalidPropertyValue(@TempDir File root) throws IOException {
        setUp(root, "");

        BuildResult result = gradle(root, "apiDocs", "-Pkardx.html=no").buildAndFail();

        assertTrue(result.getOutput().contains("kardx.html must be true or false, got 'no'"),
            result.getOutput());
    }

    private static void setUp(File root, String settings) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", BUILD.formatted(settings));
        write(root, "spec/openapi3.yaml", "openapi: 3.0.1\n");
    }

    private static GradleRunner gradle(File root, String... arguments) {
        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(List.of(arguments));
    }

    private static TaskOutcome outcome(BuildResult result, String path) {
        return result.task(path) == null ? null : result.task(path).getOutcome();
    }

    private static void write(File root, String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
