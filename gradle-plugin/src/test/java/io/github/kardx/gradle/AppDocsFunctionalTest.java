package io.github.kardx.gradle;

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
 * 앱에 들어가는 문서: epages {@code openapi3 { }} 기본값(yaml, 프로젝트 이름·버전, server "/"), boot 모듈이 읽는 문서 위치 파일,
 * 민감 헤더를 가리지 않은 문서의 운영 노출 경고. epages·Spring Boot 는 테스트 클래스패스의 가짜 플러그인이다 (가짜 openapi3 는
 * 확장의 format·title·version 으로 결과 파일을 쓴다).
 */
class AppDocsFunctionalTest {
    private static final String BUILD = """
        plugins {
            id 'java'
            id 'org.springframework.boot'
            id 'com.epages.restdocs-api-spec'
            id 'io.github.kardx-project'
        }
        version = '2.0.0'
        kardx {
            addDependencies = false
            // 테스트 없이 기록만 둔다. build/ 아래면 Gradle 이 처음 빌드에서 모르는 출력으로 보고 지운다
            asciidocDirectory = layout.projectDirectory.dir('asciidoc')
            bootJar { path = 'static/manual' }
            // AsyncAPI CLI 는 core 가 없으면 돌 수 없다. 파일 이름은 문서 위치 파일에만 쓰인다
            asyncApi { enabled = false }
        }
        // 클래스패스를 훑지 않고 문서 기록만 쓰는 index CLI 는 core 가 없으면 돌 수 없다
        tasks.named('apiDocsIndex') { enabled = false }
        tasks.matching { it.name == 'openapi3' }.configureEach { t ->
            def spec = project.extensions.getByName('openapi3')
            def defaultDirectory = layout.buildDirectory.dir('api-spec').get().asFile
            t.doLast {
                def directory = t.outputDirectory ? file(t.outputDirectory) : defaultDirectory
                directory.mkdirs()
                // YAML 은 JSON 을 그대로 읽으므로 두 형식 모두 JSON 글자로 쓴다
                new File(directory, "openapi3.${spec.format}").text = groovy.json.JsonOutput.toJson([
                    openapi: '3.0.1', info: [title: spec.title, version: spec.version],
                    servers: [[url: spec.server]], paths: [:]])
            }
        }
        %s
        """;

    @TempDir
    File root;

    @BeforeEach
    void writeProject() throws IOException {
        write("settings.gradle", "rootProject.name = 'shop'\n");
        // 테스트가 문서와 함께 남기는 기록. 이 문서는 민감 헤더를 가리지 않고 만들었다
        write("asciidoc/.restdocs-documents/order-docs.properties",
            "document=order-docs\nheaders-masked=false\n");
    }

    @Test
    void openApi3DefaultsMatchTheAppAndTheAppLearnsDocLocations() throws IOException {
        write("build.gradle", BUILD.formatted(""));

        BuildResult result = gradle("apiDocs");
        File docs = new File(root, "build/api-docs");
        String spec = Files.readString(new File(docs, "openapi3.yaml").toPath());
        String locations = Files.readString(
            new File(root, "build/resources/main/META-INF/kardx/docs-locations.properties").toPath());

        assertFalse(new File(docs, "openapi3.json").exists(), "epages 기본값(json)이면 시작 로그·Swagger UI 가 어긋난다");
        assertTrue(spec.contains("shop") && spec.contains("2.0.0") && spec.contains("url: /"), spec);
        assertTrue(locations.contains("docs-path=manual\n"), locations);
        assertTrue(locations.contains("startup-log.open-api.file=openapi3.yaml\n"), locations);
        assertFalse(locations.contains("async-api"), "꺼진 문서는 적지 않는다: " + locations);
        assertTrue(locations.contains("startup-log.rest-docs.file=index.html\n"), locations);
        assertTrue(result.getOutput().contains("1 documented test class(es) ran without sensitive header masking "
            + "(e.g. order-docs)"), result.getOutput());
    }

    @Test
    void valuesWrittenInTheBuildScriptWin() throws IOException {
        write("build.gradle", BUILD.formatted("""
            openapi3 {
                format = 'json'
                title = 'Shop API'
                version = '1.0.0' // epages 기본값과 같은 글자라도 사용자가 쓴 값이다
            }
            """));

        gradle("apiDocs");

        String spec = Files.readString(new File(root, "build/api-docs/openapi3.json").toPath());

        assertTrue(spec.contains("Shop API") && spec.contains("1.0.0") && !spec.contains("2.0.0"), spec);
        assertTrue(Files.readString(
            new File(root, "build/resources/main/META-INF/kardx/docs-locations.properties").toPath())
            .contains("startup-log.open-api.file=openapi3.json\n"));
    }

    @Test
    void noExposureWarningWhenDocsStayOutOfTheApp() throws IOException {
        write("build.gradle", BUILD.formatted("kardx { bootJar { enabled = false } }"));

        BuildResult result = gradle("apiDocs");

        assertFalse(result.getOutput().contains("sensitive header masking"), result.getOutput());
        assertFalse(new File(root, "build/resources/main/META-INF/kardx/docs-locations.properties")
            .exists());
    }

    @Test
    void epagesOutputInsideTheAppResourcesDoesNotBreakTheBuild() throws IOException {
        // 이관 전 흔한 설정: epages 가 스펙을 jar 리소스에 바로 쓴다. 스펙 파일을 openapi3 출력으로 알리면 클래스패스를
        // 읽는 태스크(jar, bootJar, apiDocsIndex)가 Gradle 8 의 암묵적 의존 검사에 걸려 빌드가 실패한다
        write("build.gradle", BUILD.formatted("openapi3 { outputDirectory = 'build/resources/main/static/docs' }"));

        BuildResult result = gradle("apiDocs", "jar");

        assertTrue(new File(root, "build/api-docs/openapi3.yaml").isFile(), "스펙은 그대로 문서에 들어간다");
        assertTrue(result.getOutput().contains("epages openapi3.outputDirectory points into the app's resources"),
            result.getOutput());
        assertTrue(result.getOutput().contains("remove outputDirectory from openapi3 { }"), result.getOutput());
    }

    private BuildResult gradle(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(List.of(arguments))
            .build();
    }

    private void write(String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
