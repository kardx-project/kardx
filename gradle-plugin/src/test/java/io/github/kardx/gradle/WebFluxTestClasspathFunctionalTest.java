package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * WebFlux 앱이면 epages 가 끌고 오는 {@code spring-boot-starter-web} 을 테스트 런타임 클래스패스에서 빼는지.
 * 모듈은 테스트가 만든 로컬 Maven 저장소의 POM 뿐이다 (의존 그래프만 해석한다).
 */
class WebFluxTestClasspathFunctionalTest {
    private static final String WEB = "spring-boot-starter-web";
    private static final String BUILD = """
        plugins {
            id 'java'
            id 'io.github.kardx-project'
        }
        repositories { maven { url = uri('repo') } }
        def app = providers.gradleProperty('app')
            .getOrElse('org.springframework.boot:spring-boot-starter-webflux:1.0')
        dependencies {
            implementation app
            testImplementation 'com.epages:restdocs-api-spec:1.0'
        }
        kardx {
            excludeServletStarterForWebFlux = !providers.gradleProperty('keep').isPresent()
            // 라이브러리 자동 추가는 LibraryDependenciesFunctionalTest 가 본다
            addDependencies = false
        }
        tasks.register('modules') {
            doLast {
                ['testRuntimeClasspath', 'testCompileClasspath'].each { name ->
                    def modules = configurations.getByName(name).incoming.resolutionResult.allComponents
                        .collect { it.moduleVersion.name }
                    println "$name=$modules"
                }
            }
        }
        """;

    @TempDir
    File root;

    @BeforeEach
    void writeProject() throws IOException {
        write("settings.gradle", "rootProject.name = 'flux'\n");
        write("build.gradle", BUILD);
        pom("org.springframework.boot", "spring-boot-starter-webflux", "");
        pom("org.springframework.boot", WEB, "");
        // epages 처럼 starter-web 을 runtime 으로 끌고 온다
        pom("com.epages", "restdocs-api-spec", """
            <dependencies>
              <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-web</artifactId>
                <version>1.0</version>
                <scope>runtime</scope>
              </dependency>
            </dependencies>
            """);
    }

    @Test
    void excludesServletStarterFromTestRuntimeClasspathOfWebFluxApp() {
        String output = modules();

        assertFalse(runtimeModules(output).contains(WEB), output);
        assertTrue(runtimeModules(output).contains("restdocs-api-spec"), output);
    }

    @Test
    void keepsServletStarterWhenDisabled() {
        String output = modules("-Pkeep");

        assertTrue(runtimeModules(output).contains(WEB), output);
    }

    @Test
    void keepsServletStarterDeclaredDirectly() {
        String output = modules("-Papp=org.springframework.boot:spring-boot-starter-web:1.0");

        assertTrue(runtimeModules(output).contains(WEB), output);
    }

    @Test
    void doesNothingForServletApp() {
        String output = modules("-Papp=com.epages:restdocs-api-spec:1.0");

        assertTrue(runtimeModules(output).contains(WEB), output);
    }

    private String modules(String... arguments) {
        List<String> args = new ArrayList<>(List.of("modules", "-q", "--warning-mode=fail"));

        args.addAll(List.of(arguments));

        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(args)
            .build()
            .getOutput();
    }

    /** {@code testRuntimeClasspath=[a, b]} 줄의 모듈 이름. */
    private static List<String> runtimeModules(String output) {
        String prefix = "testRuntimeClasspath=";
        String line = output.lines()
            .filter(it -> it.startsWith(prefix))
            .findFirst()
            .orElseThrow();

        return List.of(line.substring(prefix.length() + 1, line.length() - 1)
            .split(", "));
    }

    private void pom(String group, String artifact, String dependencies) throws IOException {
        write("repo/" + group.replace('.', '/') + "/" + artifact + "/1.0/" + artifact + "-1.0.pom", """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>%s</groupId>
              <artifactId>%s</artifactId>
              <version>1.0</version>
              <packaging>pom</packaging>
              %s
            </project>
            """.formatted(group, artifact, dependencies));
    }

    private void write(String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
