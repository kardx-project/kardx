package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.gradle.KardxPlugin;
import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import java.io.File;
import java.util.List;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringBootIntegrationTest {
    private static final String DEVELOPMENT_ONLY = "developmentOnly";

    @Test
    void pluginVersionComesFromProcessedResource() {
        String version = SpringBootIntegration.pluginVersion();

        assertFalse(version.isBlank());
        assertFalse(version.contains("${"), version);
    }

    @Test
    void majorVersionReadsBomCoordinates() {
        assertEquals(4, SpringBootIntegration.majorVersion("org.springframework.boot:spring-boot-dependencies:4.1.2"));
        assertEquals(3, SpringBootIntegration.majorVersion("org.springframework.boot:spring-boot-dependencies:3.5.16"));
        assertEquals(0, SpringBootIntegration.majorVersion((String) null));
        assertEquals(0, SpringBootIntegration.majorVersion("org.springframework.boot:spring-boot-dependencies:x"));
    }

    @Test
    void startupLogModuleIsAlsoCompileOnlyWithoutTransitivesForIdeMetadata(@TempDir File dir) {
        Project project = bootProject(dir, DEVELOPMENT_ONLY);

        assertEquals(1, startupLogModules(project, DEVELOPMENT_ONLY).size());

        List<Dependency> compileOnly = startupLogModules(project, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);

        assertEquals(1, compileOnly.size());
        assertFalse(((ModuleDependency) compileOnly.get(0)).isTransitive(), "전이 의존성 없이 넣는다");

        // 실행 jar(Boot 의 productionRuntimeClasspath 는 runtimeClasspath 에서 developmentOnly 를 뺀 것)와 테스트에는 없다
        for (String classpath : List.of(
            JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME,
            JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME,
            JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)) {
            assertTrue(startupLogModules(project, classpath).isEmpty(), classpath);
        }
    }

    @Test
    void runtimeOnlyScopeAlsoAddsCompileOnly(@TempDir File dir) {
        Project project = bootProject(dir, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME);

        assertEquals(1, startupLogModules(project, JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME).size());
        assertEquals(1, startupLogModules(project, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).size());
    }

    @Test
    void scopesAlreadyOnCompileClasspathAreNotDuplicated(@TempDir File dir) {
        for (String scope : List.of(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
            JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)) {
            Project project = bootProject(new File(dir, scope), scope);

            assertEquals(1, startupLogModules(project, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).size(), scope);
        }
    }

    @Test
    void declaredModuleIsLeftAlone(@TempDir File dir) {
        Project project = bootProject(dir, DEVELOPMENT_ONLY, p ->
            p.getDependencies().add(DEVELOPMENT_ONLY, "io.github.kardx-project:" + SpringBootIntegration.BOOT_MODULE + ":0.0.1"));

        assertEquals(1, startupLogModules(project, DEVELOPMENT_ONLY).size());
        assertTrue(startupLogModules(project, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).isEmpty());
    }

    private static Project bootProject(File dir, String scope) {
        return bootProject(dir, scope, p -> { });
    }

    private static Project bootProject(File dir, String scope, Action<Project> beforeEvaluate) {
        Project project = ProjectBuilder.builder()
            .withProjectDir(dir)
            .build();
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(PluginConstants.SPRING_BOOT_PLUGIN);
        project.getPluginManager().apply(KardxPlugin.class);
        project.getExtensions()
            .getByType(KardxExtension.class)
            .getStartupLogScope()
            .set(scope);
        beforeEvaluate.execute(project);
        ((ProjectInternal) project).evaluate();

        return project;
    }

    /** configuration 과 그 상위 configuration 에 선언된 시작 로그 모듈. */
    private static List<Dependency> startupLogModules(Project project, String configuration) {
        return project.getConfigurations()
            .getByName(configuration)
            .getAllDependencies()
            .stream()
            .filter(d -> SpringBootIntegration.BOOT_MODULE.equals(d.getName()))
            .toList();
    }

    /** {@code developmentOnly} 와 {@code bootJar} 만 만드는 가짜 Spring Boot 플러그인 (테스트 리소스로 같은 id 를 준다). */
    @SuppressWarnings("unused") // 테스트 리소스의 플러그인 id 로 Gradle 이 만든다
    public static class FakeSpringBootPlugin implements Plugin<Project> {
        @Override
        public void apply(@Nonnull Project project) {
            project.getConfigurations().create(DEVELOPMENT_ONLY);
            project.getTasks().register(PluginConstants.BOOT_JAR_TASK);
        }
    }
}
