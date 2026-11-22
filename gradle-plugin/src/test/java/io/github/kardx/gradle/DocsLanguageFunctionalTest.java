package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code kardx.language}: 테스트 JVM·core CLI 의 시스템 속성, 앱의 문서 위치 파일, 플러그인이 만드는 뷰어 페이지까지
 * 닿는지, 지원하지 않는 값이면 실패하는지. epages·Spring Boot 는 테스트 클래스패스의 가짜 플러그인이다.
 */
class DocsLanguageFunctionalTest {
    private static final String PROPERTY = "-Dkardx.language=";
    private static final String TITLES = "titles: shop, shop, shop";
    private static final String LOCATIONS = "build/resources/main/META-INF/kardx/docs-locations.properties";
    private static final String BUILD = """
        plugins {
            id 'java'
            id 'org.springframework.boot'
            id 'com.epages.restdocs-api-spec'
            id 'io.github.kardx-project'
        }
        kardx {
            addDependencies = false
            openApiViewer { enabled = true }
            %s
        }
        // core CLI 는 core 가 없으면 돌 수 없다. 시스템 속성은 태스크 설정으로 확인한다
        tasks.named('apiDocsIndex') { enabled = false }
        tasks.named('apiDocsAsyncApi') { enabled = false }
        tasks.matching { it.name == 'openapi3' }.configureEach { t ->
            def directory = layout.buildDirectory.dir('api-spec').get().asFile
            t.doLast {
                directory.mkdirs()
                new File(directory, 'openapi3.yaml').text = '{"openapi": "3.0.1", "info": {"title": "shop", ' +
                    '"version": "1"}, "paths": {}}'
            }
        }
        tasks.register('printJvmArgs') {
            doLast {
                ['test', 'apiDocsIndex', 'apiDocsAsyncApi'].each { name ->
                    def language = tasks.getByName(name).allJvmArgs.findAll { it.contains('kardx.language') }
                    println "$name: $language"
                }
                // 제목 기본값은 언어 문구가 아니라 프로젝트 이름이다
                println "titles: ${kardx.index.title.get()}, ${kardx.asyncApi.title.get()}, " +
                    "${kardx.aggregate.title.get()}"
            }
        }
        """;

    @TempDir
    File root;

    @BeforeEach
    void writeSettings() throws IOException {
        write("settings.gradle", "rootProject.name = 'shop'\n");
    }

    @Test
    void configuredLanguageReachesTestJvmCliTasksAppAndPages() throws IOException {
        write("build.gradle", BUILD.formatted("language = 'KO'"));

        BuildResult result = runner("apiDocs", "printJvmArgs").build();
        String viewer = Files.readString(new File(root, "build/api-docs/openapi.html").toPath());

        for (String task : List.of("test", "apiDocsIndex", "apiDocsAsyncApi")) {
            assertTrue(result.getOutput().contains(task + ": [" + PROPERTY + "ko]"), "소문자로 넘긴다\n"
                + result.getOutput());
        }

        assertTrue(Files.readString(new File(root, LOCATIONS).toPath()).contains("language=ko\n"));
        assertTrue(result.getOutput().contains(TITLES), "제목 기본값은 언어와 상관없이 프로젝트 이름\n" + result.getOutput());
        assertTrue(viewer.contains("<html lang=\"ko\">") && viewer.contains("스크립트(unpkg.com)를 불러오지 못했습니다"),
            viewer);
    }

    @Test
    void unsetLanguagePassesNothingAndWritesEnglishPages() throws IOException {
        write("build.gradle", BUILD.formatted(""));

        BuildResult result = runner("apiDocs", "printJvmArgs").build();
        String viewer = Files.readString(new File(root, "build/api-docs/openapi.html").toPath());

        assertFalse(result.getOutput().contains(PROPERTY), "앱 설정(yml)이 정하게 둔다\n" + result.getOutput());
        assertFalse(Files.readString(new File(root, LOCATIONS).toPath()).contains("language="));
        assertTrue(result.getOutput().contains(TITLES), result.getOutput());
        assertTrue(viewer.contains("<html lang=\"en\">") && viewer.contains("Could not load the Redoc script"), viewer);
        assertFalse(Pattern.compile("\\p{IsHangul}").matcher(viewer).find(), "템플릿의 한국어 주석은 남지 않는다\n" + viewer);
    }

    @Test
    void unsupportedLanguageFailsWithSupportedValues() throws IOException {
        write("build.gradle", BUILD.formatted("language = 'fr'"));

        BuildResult result = runner("apiDocs").buildAndFail();

        assertTrue(result.getOutput().contains("kardx.language must be en or ko, got 'fr'"),
            result.getOutput());
    }

    private GradleRunner runner(String... arguments) {
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
