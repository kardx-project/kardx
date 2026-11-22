package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 플러그인이 라이브러리와 테스트 클라이언트 의존성을 넣는지 ({@code kardx.addDependencies}). 모듈은 테스트가 만든 로컬
 * Maven 저장소의 POM 뿐이다 (의존 그래프만 해석한다). REST Docs 클라이언트의 버전은 로컬 BOM({@code test:bom})이 정한다.
 */
class LibraryDependenciesFunctionalTest {
    private static final String VERSION = SpringBootIntegration.pluginVersion();
    private static final String BUILD = """
        plugins {
            id 'java'
            id 'io.github.kardx-project'
        }
        repositories { maven { url = uri('repo') } }
        def app = providers.gradleProperty('app')
            .getOrElse('org.springframework.boot:spring-boot-starter-web:1.0')
        def extra = providers.gradleProperty('extra')
        dependencies {
            if (!providers.gradleProperty('noBom').isPresent()) {
                implementation platform('test:bom:1.0')
            }
            implementation app
            if (extra.isPresent()) {
                testImplementation extra.get()
            }
        }
        kardx {
            addDependencies = !providers.gradleProperty('off').isPresent()
        }
        tasks.register('modules') {
            doLast {
                ['compileClasspath', 'testCompileClasspath', 'testRuntimeClasspath'].each { name ->
                    def modules = configurations.getByName(name).incoming.resolutionResult.allComponents
                        .findAll { it.id instanceof org.gradle.api.artifacts.component.ModuleComponentIdentifier }
                        .collect { "${it.moduleVersion.group}:${it.moduleVersion.name}:${it.moduleVersion.version}" }
                        .sort()
                    println "$name=$modules"
                }
            }
        }
        """;

    @TempDir
    File root;

    @BeforeEach
    void writeProject() throws IOException {
        write("settings.gradle", "rootProject.name = 'app'\n");
        write("build.gradle", BUILD);
        pom("test", "bom", "1.0", """
            <packaging>pom</packaging>
            <dependencyManagement>
              <dependencies>
                <dependency>
                  <groupId>org.springframework.restdocs</groupId>
                  <artifactId>spring-restdocs-mockmvc</artifactId>
                  <version>9.9</version>
                </dependency>
                <dependency>
                  <groupId>org.springframework.restdocs</groupId>
                  <artifactId>spring-restdocs-webtestclient</artifactId>
                  <version>9.9</version>
                </dependency>
              </dependencies>
            </dependencyManagement>
            """);

        for (String starter : List.of("spring-boot-starter-web", "spring-boot-starter-webflux",
            "spring-boot-starter-websocket")) {
            pom("org.springframework.boot", starter, "1.0", "");
        }

        for (String client : List.of("spring-restdocs-mockmvc", "spring-restdocs-webtestclient")) {
            pom("org.springframework.restdocs", client, "9.9", "");
        }

        for (String module : List.of("", "-annotations", "-kotest", "-kotest5")) {
            pom("io.github.kardx-project", "kardx" + module, VERSION, "");
        }

        pom("io.github.kardx-project", "kardx", "0.0.1", "");
        pom("io.kotest", "kotest-runner-junit5", "6.0.0", "");
        pom("io.kotest", "kotest-runner-junit5", "5.9.1", "");
    }

    @Test
    void addsAnnotationsCoreAndMockMvcToMvcApp() {
        String output = modules();

        assertEquals(List.of("io.github.kardx-project:kardx-annotations:" + VERSION,
            "org.springframework.boot:spring-boot-starter-web:1.0"), libraries(output, "compileClasspath"));

        for (String classpath : List.of("testCompileClasspath", "testRuntimeClasspath")) {
            List<String> modules = libraries(output, classpath);

            assertTrue(modules.contains("io.github.kardx-project:kardx:" + VERSION), output);
            assertTrue(modules.contains("org.springframework.restdocs:spring-restdocs-mockmvc:9.9"), output);
            assertFalse(modules.contains("org.springframework.restdocs:spring-restdocs-webtestclient:9.9"), output);
            assertFalse(modules.contains("io.github.kardx-project:kardx-annotations:" + VERSION),
                "annotations 는 compileOnly 와 같다: " + output);
        }

    }

    @Test
    void worksWithConfigurationCache() {
        runner("compileTestJava", "--configuration-cache").build();

        assertTrue(runner("compileTestJava", "--configuration-cache").build().getOutput()
            .contains("Reusing configuration cache"));
    }

    @Test
    void addsWebTestClientToWebFluxApp() {
        List<String> modules = libraries(modules("-Papp=org.springframework.boot:spring-boot-starter-webflux:1.0"),
            "testRuntimeClasspath");

        assertTrue(modules.contains("org.springframework.restdocs:spring-restdocs-webtestclient:9.9"),
            modules::toString);
        assertFalse(modules.contains("org.springframework.restdocs:spring-restdocs-mockmvc:9.9"), modules::toString);
    }

    @Test
    void addsOnlyCoreWithoutWebStack() {
        List<String> modules = libraries(modules("-Papp=test:bom:1.0"), "testRuntimeClasspath");

        assertEquals(List.of("io.github.kardx-project:kardx:" + VERSION), modules);
    }

    @Test
    void addsKotestExtensionMatchingDeclaredKotestVersion() {
        List<String> six = libraries(modules("-Pextra=io.kotest:kotest-runner-junit5:6.0.0"), "testRuntimeClasspath");
        List<String> five = libraries(modules("-Pextra=io.kotest:kotest-runner-junit5:5.9.1"), "testRuntimeClasspath");

        assertTrue(six.contains("io.github.kardx-project:kardx-kotest:" + VERSION), six::toString);
        assertTrue(five.contains("io.github.kardx-project:kardx-kotest5:" + VERSION), five::toString);
        assertFalse(five.contains("io.github.kardx-project:kardx-kotest:" + VERSION), five::toString);
    }

    @Test
    void leavesDeclaredLibraryAlone() {
        List<String> modules = libraries(modules("-Pextra=io.github.kardx-project:kardx:0.0.1"),
            "testRuntimeClasspath");

        assertTrue(modules.contains("io.github.kardx-project:kardx:0.0.1"), modules::toString);
        assertFalse(modules.contains("io.github.kardx-project:kardx:" + VERSION), modules::toString);
    }

    @Test
    void addsNothingWhenDisabled() {
        String output = modules("-Poff");

        assertEquals(List.of("org.springframework.boot:spring-boot-starter-web:1.0"),
            libraries(output, "testRuntimeClasspath"));
    }

    @Test
    void explainsUnmanagedClientVersion() {
        BuildResult result = runner("modules", "-PnoBom").build();

        assertTrue(result.getOutput().contains("org.springframework.restdocs:spring-restdocs-mockmvc was added "
            + "without a version, but nothing manages its version"), result.getOutput());
    }

    private String modules(String... arguments) {
        List<String> args = new ArrayList<>(List.of("modules", "--warning-mode=fail"));

        args.addAll(List.of(arguments));

        return runner(args.toArray(String[]::new)).build().getOutput();
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(arguments);
    }

    /** {@code name=[a, b]} 줄의 모듈 좌표. BOM 은 뺀다. */
    private static List<String> libraries(String output, String name) {
        String prefix = name + "=";
        String line = output.lines()
            .filter(it -> it.startsWith(prefix))
            .findFirst()
            .orElseThrow(() -> new AssertionError(output));
        String list = line.substring(prefix.length() + 1, line.length() - 1);

        return list.isEmpty()
            ? List.of()
            : Stream.of(list.split(", "))
                .filter(it -> !it.startsWith("test:bom"))
                .toList();
    }

    private void pom(String group, String artifact, String version, String body) throws IOException {
        String path = "repo/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-"
            + version + ".pom";

        write(path, """
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
