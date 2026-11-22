package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 다른 Gradle 버전에서 핵심 시나리오(플러그인 적용, 라이브러리 의존성 추가와 해석, epages 기본값, 앱 문서 위치 파일, configuration
 * cache 재사용)와 스펙 내보내기·업로드({@code apiDocsExport}, {@code apiDocsUpload})를 돌린다. 버전마다 배포판이 필요하므로
 * 평소에는 돌지 않는다. 버전(내려받는다) 또는 풀어 둔 설치 디렉터리를 쉼표로 준다: {@code ./gradlew test --tests '*GradleCompatibility*' -PgradleVersions=7.6.4,/path/to/gradle-9.0.0}.
 */
class GradleCompatibilityFunctionalTest {
    /**
     * 가짜 epages 의 openapi3 결과. 가짜 epages 는 afterEvaluate 에서 태스크를 만든다. 그때의 확장 값(format)으로 결과
     * 파일 이름을 정한다.
     */
    private static final String FAKE_SPEC = """
        def epages = extensions.getByName('openapi3')
        tasks.matching { it.name == 'openapi3' }.configureEach { t ->
            def file = layout.buildDirectory.file("api-spec/openapi3.${epages.format}").get().asFile
            t.doLast {
                file.parentFile.mkdirs()
                file.text = '{"openapi": "3.0.1", "info": {"title": "x", "version": "1"}, "paths": {}}'
            }
        }
        """;
    private static final String BUILD = """
        plugins {
            id 'java'
            id 'org.springframework.boot'
            id 'com.epages.restdocs-api-spec'
            id 'io.github.kardx-project'
        }
        repositories { maven { url = uri('repo') } }
        dependencies {
            implementation platform('test:bom:1.0')
            implementation 'org.springframework.boot:spring-boot-starter-web:1.0'
        }
        kardx {
            asciidocDirectory = layout.projectDirectory.dir('asciidoc')
            asyncApi { enabled = false }
        }
        tasks.named('apiDocsIndex') { enabled = false }
        """ + FAKE_SPEC;
    private static final String EXPORT_BUILD = """
        plugins {
            id 'java'
            id 'com.epages.restdocs-api-spec'
            id 'io.github.kardx-project'
        }
        version = '2.0'
        kardx {
            addDependencies = false
            asyncApi { enabled = false }
            export { directory = layout.buildDirectory.dir('exported') }
            upload {
                url = providers.gradleProperty('url')
                headers.put('Authorization', providers.gradleProperty('token').map { 'Bearer ' + it })
            }
        }
        tasks.named('apiDocsIndex') { enabled = false }
        """ + FAKE_SPEC;
    private static final String TOKEN = "compat-s3cret-token";

    @TempDir
    File root;

    static Stream<String> gradleVersions() {
        return Arrays.stream(System.getProperty("restdocs.gradleVersions", "").split(","))
            .map(String::trim)
            .filter(version -> !version.isEmpty());
    }

    @ParameterizedTest(name = "Gradle {0}", allowZeroInvocations = true)
    @MethodSource("gradleVersions")
    void coreScenarioRuns(String gradleVersion) throws IOException {
        write("settings.gradle", "rootProject.name = 'app'\n");
        write("build.gradle", BUILD);
        pom("test", "bom", "1.0", """
            <packaging>pom</packaging>
            <dependencyManagement><dependencies><dependency>
              <groupId>org.springframework.restdocs</groupId>
              <artifactId>spring-restdocs-mockmvc</artifactId>
              <version>9.9</version>
            </dependency></dependencies></dependencyManagement>
            """);
        pom("org.springframework.boot", "spring-boot-starter-web", "1.0", "");
        pom("org.springframework.restdocs", "spring-restdocs-mockmvc", "9.9", "");
        pom("io.github.kardx-project", "kardx", pluginVersion(), "");
        pom("io.github.kardx-project", "kardx-annotations", pluginVersion(), "");
        pom("io.github.kardx-project", "kardx-boot", pluginVersion(), "");

        List<String> tasks = List.of("apiDocs", "compileTestJava", "--configuration-cache");
        BuildResult first = runner(gradleVersion, tasks).build();
        BuildResult second = runner(gradleVersion, tasks).build();
        String locations = Files.readString(
            new File(root, "build/resources/main/META-INF/kardx/docs-locations.properties").toPath());

        assertTrue(new File(root, "build/api-docs/openapi3.yaml").isFile(), first.getOutput());
        assertTrue(locations.contains("startup-log.open-api.file=openapi3.yaml"), locations);
        assertTrue(second.getOutput().contains("Reusing configuration cache"), second.getOutput());
    }

    @ParameterizedTest(name = "Gradle {0}", allowZeroInvocations = true)
    @MethodSource("gradleVersions")
    void exportAndUploadRun(String gradleVersion) throws IOException {
        List<String> received = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        server.createContext("/apis", exchange -> {
            try {
                received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.sendResponseHeaders(204, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            String url = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort()
                + "/apis/{name}/{version}.{ext}";

            write("settings.gradle", "rootProject.name = 'shop'\n");
            write("build.gradle", EXPORT_BUILD);

            List<String> tasks = List.of("apiDocsExport", "apiDocsUpload", "-Purl=" + url, "-Ptoken=" + TOKEN,
                "--configuration-cache", "--warning-mode=all");
            BuildResult first = runner(gradleVersion, tasks).build();
            BuildResult second = runner(gradleVersion, tasks).build();

            assertTrue(new File(root, "build/exported/shop-openapi3.yaml").isFile(), first.getOutput());
            assertEquals(List.of("PUT /apis/shop/2.0.yaml Bearer " + TOKEN, "PUT /apis/shop/2.0.yaml Bearer " + TOKEN),
                received);
            assertTrue(second.getOutput().contains("Reusing configuration cache"), second.getOutput());

            for (BuildResult result : List.of(first, second)) {
                assertTrue(result.getOutput().contains("Uploaded openapi3.yaml (HTTP 204)"), result.getOutput());
                assertFalse(result.getOutput().contains(TOKEN), result.getOutput());
                assertFalse(result.getOutput().contains("deprecated"), "플러그인이 지원 중단 API 를 쓰지 않는다\n"
                    + result.getOutput());
            }
        } finally {
            server.stop(0);
        }
    }

    private GradleRunner runner(String gradleVersion, List<String> arguments) {
        GradleRunner runner = GradleRunner.create();
        File installation = new File(gradleVersion);

        if (installation.isDirectory()) {
            runner.withGradleInstallation(installation);
        } else {
            runner.withGradleVersion(gradleVersion);
        }

        return runner
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(arguments);
    }

    private static String pluginVersion() {
        try (InputStream in = GradleCompatibilityFunctionalTest.class.getResourceAsStream(
            "/io/github/kardx/gradle/internal/wiring/kardx-plugin.properties")) {
            Properties properties = new Properties();

            properties.load(in);

            return properties.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void pom(String group, String artifact, String version, String body) throws IOException {
        write("repo/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version
            + ".pom", """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>%s</groupId>
              <artifactId>%s</artifactId>
              <version>%s</version>
              %s
            </project>
            """.formatted(group, artifact, version, body.isEmpty() ? "<packaging>pom</packaging>" : body));
    }

    private void write(String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
