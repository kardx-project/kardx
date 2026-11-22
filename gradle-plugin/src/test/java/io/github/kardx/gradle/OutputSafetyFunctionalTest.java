package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 플러그인이 지우거나 동기화하는 디렉터리를 사용자가 바꿔도 앱·사용자 파일을 지우지 않는다: {@code bootJar.path},
 * {@code asciidocDirectory}(테스트 전 비우기), {@code outputDirectory}({@code apiDocs}), {@code aggregate.outputDirectory}.
 */
class OutputSafetyFunctionalTest {
    /** Spring Boot 는 테스트 클래스패스의 가짜 플러그인이다. index CLI·AsyncAPI 는 core 가 없어 끈다. */
    private static final String BOOT_APP = """
        plugins {
            id 'java'
            id 'org.springframework.boot'
            id 'io.github.kardx-project'
        }
        kardx {
            addDependencies = false
            asyncApi { enabled = false }
            %s
        }
        tasks.named('apiDocsIndex') { enabled = false }
        tasks.named('apiDocs') { from('docs-source') }
        """;

    @TempDir
    File root;

    @BeforeEach
    void writeProject() throws IOException {
        write("settings.gradle", "rootProject.name = 'shop'\n");
        write("src/main/resources/application.yml", "server.port: 8080\n");
        write("src/main/resources/static/app.css", "body {}\n");
        write("src/main/resources/static/docs/custom.css", "h1 {}\n");
        write("docs-source/index.html", "<html>docs</html>");
    }

    @Test
    void bootJarPathAtAStaticRootFailsBeforeTouchingTheApp() throws IOException {
        write("build.gradle", BOOT_APP.formatted("bootJar { path = 'static' }"));

        BuildResult result = gradle("processResources", "apiDocs").buildAndFail();

        assertTrue(result.getOutput().contains("kardx.bootJar.path 'static' is an application resource root "
            + "(static, public, resources, META-INF/resources). The docs would mix with the app's own files there; "
            + "use a sub directory such as 'static/docs'."), result.getOutput());
    }

    @Test
    void bootJarPathOutsideTheResourcesFails() throws IOException {
        write("build.gradle", BOOT_APP.formatted("bootJar { path = '../../src/main/resources/static' }"));

        BuildResult result = gradle("apiDocs").buildAndFail();

        assertTrue(result.getOutput().contains("kardx.bootJar.path must be a directory inside the app's "
            + "resources such as 'static/docs' or 'public/api-docs', got '../../src/main/resources/static'"),
            result.getOutput());
        assertTrue(new File(root, "src/main/resources/static/app.css").isFile());
    }

    @Test
    void appDocsKeepsTheAppsResourcesAndCleansUpAMovedPath() throws IOException {
        write("build.gradle", BOOT_APP.formatted("bootJar { path = providers.gradleProperty('docsPath').get() }"));

        gradle("processResources", "apiDocs", "-PdocsPath=static/docs").build();

        File resources = new File(root, "build/resources/main");

        assertTrue(new File(resources, "static/docs/index.html").isFile());
        assertTrue(new File(resources, "static/docs/custom.css").isFile(), "앱이 문서 디렉터리에 둔 파일");
        assertTrue(new File(resources, "static/app.css").isFile());
        assertTrue(new File(resources, "application.yml").isFile());

        gradle("processResources", "apiDocs", "-PdocsPath=static/manual").build();

        assertTrue(new File(resources, "static/manual/index.html").isFile());
        assertFalse(new File(resources, "static/docs/index.html").exists(), "이전 경로에 복사한 문서는 지운다");
        assertTrue(new File(resources, "static/docs/custom.css").isFile());
    }

    @Test
    void cleanBeforeTestRefusesTheHandWrittenAsciidocDirectory() throws IOException {
        write("build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
            }
            kardx {
                addDependencies = false
                asciidocDirectory = file('src/docs/asciidoc')
            }
            """);
        write("src/docs/asciidoc/index.adoc", "= Hand written\n");
        // 테스트 태스크가 NO-SOURCE 로 건너뛰지 않게 테스트 클래스 하나를 둔다 (프레임워크 없이 컴파일된다)
        write("src/test/java/shop/PlainTest.java", "package shop;\n\npublic class PlainTest {\n}\n");

        BuildResult result = gradle("test").buildAndFail();

        assertTrue(result.getOutput().contains("kardx.asciidocDirectory must not be inside or contain the "
            + "source directory "), result.getOutput());
        assertTrue(result.getOutput().contains("because the plugin deletes it before writing"), result.getOutput());
        assertEquals("= Hand written\n", Files.readString(new File(root, "src/docs/asciidoc/index.adoc").toPath()));
    }

    @Test
    void apiDocsOutsideTheBuildDirectoryNeedsAnEmptyOrMarkedDirectory() throws IOException {
        write("build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
            }
            kardx {
                addDependencies = false
                asyncApi { enabled = false }
                outputDirectory = file('site')
            }
            tasks.named('apiDocsIndex') { enabled = false }
            tasks.named('apiDocs') { from('docs-source') }
            """);
        write("site/CNAME", "docs.example.com\n");

        BuildResult refused = gradle("apiDocs").buildAndFail();

        assertTrue(refused.getOutput().contains("kardx.outputDirectory points to "), refused.getOutput());
        assertTrue(refused.getOutput().contains("which is outside the build directory and holds files the plugin "
            + "did not write. The plugin deletes this directory before writing to it."), refused.getOutput());
        assertTrue(new File(root, "site/CNAME").isFile());

        Files.delete(new File(root, "site/CNAME").toPath());
        gradle("apiDocs").build();
        gradle("apiDocs").build();

        assertTrue(new File(root, "site/index.html").isFile());
        assertTrue(new File(root, "site/.kardx-output").isFile(), "다음 실행이 알아보는 표시 파일");
    }

    @Test
    void aggregateRefusesARepositoryDocsDirectoryWithOtherFiles() throws IOException {
        write("settings.gradle", "rootProject.name = 'shop'\ninclude 'order'\n");
        write("build.gradle", """
            plugins { id 'io.github.kardx-project' }
            dependencies { restdocsAggregation project(':order') }
            kardx { aggregate { outputDirectory = file('docs') } }
            """);
        write("order/build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
            }
            kardx { asyncApi { enabled = false }; addDependencies = false }
            tasks.named('apiDocs') { from('spec') }
            """);
        write("order/spec/openapi3.yaml", "openapi: 3.0.1\ninfo: {title: order, version: 1}\npaths: {}\n");
        write("docs/guide.md", "# Guide\n");

        BuildResult refused = gradle("apiDocsAggregate").buildAndFail();

        assertTrue(refused.getOutput().contains("kardx.aggregate.outputDirectory points to "),
            refused.getOutput());
        assertTrue(new File(root, "docs/guide.md").isFile());

        write("docs/.kardx-output", "");
        gradle("apiDocsAggregate").build();

        assertTrue(new File(root, "docs/order/openapi3.yaml").isFile(), "표시 파일이 있으면 플러그인이 내용을 바꾼다");
        assertTrue(new File(root, "docs/.kardx-output").isFile());
    }

    private GradleRunner gradle(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(List.of(arguments));
    }

    private void write(String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
