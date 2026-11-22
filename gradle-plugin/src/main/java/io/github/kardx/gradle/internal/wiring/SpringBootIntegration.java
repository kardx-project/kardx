package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.normalization.RuntimeClasspathNormalization;

/**
 * Spring Boot ({@value PluginConstants#SPRING_BOOT_PLUGIN}) 연동.
 * <ul>
 *   <li>{@code bootJar}: 문서를 실행 jar 에 넣는다</li>
 *   <li>{@code developmentOnly}: 앱 시작 시 문서 주소를 로그로 남기는 모듈을 넣는다 (IDE 용으로 {@code compileOnly} 에도)</li>
 *   <li>Boot 플러그인에서 BOM 좌표와 버전을 읽는다</li>
 * </ul>
 */
public final class SpringBootIntegration {
    static final String BOOT_MODULE = "kardx-boot";

    /** 사용자가 시작 로그 모듈을 직접 선언했는지 볼 configuration. */
    private static final List<String> DECLARABLE =
        List.of("implementation", "runtimeOnly", "developmentOnly", "api", "compileOnly");

    private SpringBootIntegration() {
    }

    /** 실행 jar 는 {@code build/resources/main} 을 그대로 담으므로 문서 동기화({@code apiDocsAppDocs}) 뒤에 만든다. */
    public static void configureBootJar(
        Project project, KardxExtension extension, TaskProvider<Sync> apiDocs) {
        project.getPluginManager().withPlugin(PluginConstants.SPRING_BOOT_PLUGIN, applied -> {
            KardxExtension.BootJar spec = extension.getBootJar();

            // 복사한 문서와 문서 위치 파일은 테스트 클래스패스(build/resources/main)에도 보인다. 문서가 바뀌었다고 테스트가 다시
            // 돌지 않게 클래스패스 비교에서 뺀다. 문서를 넣을 경로가 잘못됐으면 빌드를 시작하기 전에 알린다
            project.afterEvaluate(p -> {
                RuntimeClasspathNormalization classpath = project.getNormalization().getRuntimeClasspath();
                String path = spec.getEnabled().get()
                    ? BootJarPath.of(spec.getPath().get())
                    : spec.getPath().get();

                classpath.ignore(path + "/**");
                classpath.ignore(AppDocsProperties.RESOURCE);
            });

            project.getTasks()
                .named(PluginConstants.BOOT_JAR_TASK)
                .configure(jar -> jar.dependsOn(project.provider(() ->
                    spec.getEnabled().get()
                        ? List.of(apiDocs, project.getTasks().named(PluginConstants.APP_DOCS_TASK))
                        : List.of())));
        });
    }

    /**
     * Spring Boot 앱에 {@value #BOOT_MODULE} 을 넣는다. 기본은 Boot 의 {@code developmentOnly} 라서 IDE 실행과
     * {@code bootRun} 에서만 동작하고 실행 jar·테스트 클래스패스에는 들어가지 않는다 ({@code startupLogScope} 로 바꾼다).
     * IDE 가 설정 메타데이터를 읽도록 {@code compileOnly} 에도 넣는다 (IntelliJ 가 RUNTIME 범위 의존성의 메타데이터를 읽지 않기 때문).
     * 사용자가 이미 넣었으면(버전을 직접 정했거나 같은 빌드의 프로젝트로 넣었으면) 건드리지 않는다.
     */
    public static void configureStartupLog(Project project, KardxExtension extension) {
        project.getPluginManager().withPlugin(PluginConstants.SPRING_BOOT_PLUGIN, applied ->
            project.afterEvaluate(p -> addStartupLogModule(p, extension)));
    }

    private static void addStartupLogModule(Project project, KardxExtension extension) {
        if (!extension.getStartupLog().get()) {
            return;
        }

        String scope = extension.getStartupLogScope().get();
        Configuration target = project.getConfigurations().findByName(scope);

        if (target == null) {
            throw new GradleException("kardx.startupLogScope: configuration '" + scope + "' not found. "
                + "Use developmentOnly or runtimeOnly, or set startupLog = false.");
        }

        boolean declared = DECLARABLE.stream()
            .map(project.getConfigurations()::findByName)
            .filter(Objects::nonNull)
            .flatMap(c -> c.getDependencies().stream())
            .anyMatch(d -> "io.github.kardx-project".equals(d.getGroup())
                && (BOOT_MODULE.equals(d.getName()) || "boot".equals(d.getName())));

        if (declared) {
            return;
        }

        String notation = "io.github.kardx-project:" + BOOT_MODULE + ":" + pluginVersion();
        target.getDependencies().add(project.getDependencies().create(notation));
        addForIdeMetadata(project, target, notation);
    }

    /**
     * IntelliJ 는 RUNTIME 범위 의존성({@code developmentOnly}·{@code runtimeOnly})의
     * {@code META-INF/spring-configuration-metadata.json} 을 읽지 않아 {@code application.yml} 의
     * {@code kardx.*} 키에 "Cannot resolve configuration property" 경고를 낸다. 그래서 모듈을
     * {@code compileOnly} 에도 전이 의존성 없이 넣는다. {@code compileOnly} 는 런타임 클래스패스·테스트 클래스패스·실행 jar 에
     * 들어가지 않는다. {@code startupLogScope} 가 이미 컴파일 클래스패스에 보이면({@code implementation}·{@code api}·
     * {@code compileOnly}) 넣지 않는다.
     */
    private static void addForIdeMetadata(Project project, Configuration target, String notation) {
        Configuration compileClasspath =
            project.getConfigurations().findByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
        Configuration compileOnly = project.getConfigurations().findByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);

        if (compileClasspath == null || compileOnly == null || compileClasspath.getHierarchy().contains(target)) {
            return;
        }

        ModuleDependency metadata = (ModuleDependency) project.getDependencies().create(notation);
        metadata.setTransitive(false);
        compileOnly.getDependencies().add(metadata);
    }

    /** 이 플러그인의 버전. 런타임 모듈도 같은 버전으로 배포한다. */
    static String pluginVersion() {
        try (InputStream in = SpringBootIntegration.class.getResourceAsStream("kardx-plugin.properties")) {
            Properties properties = new Properties();

            if (in != null) {
                properties.load(in);
            }

            String version = properties.getProperty("version");

            if (version == null || version.isBlank() || version.contains("${")) {
                throw new GradleException("kardx Gradle plugin was built without its version "
                    + "(kardx-plugin.properties). Set kardx { startupLog = false } to skip.");
            }

            return version;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Spring Boot BOM 좌표 ({@code group:artifact:version}). Boot 플러그인이 없으면 null. */
    static String bomCoordinates(Project project) {
        Plugin<?> boot = project.getPlugins().findPlugin(PluginConstants.SPRING_BOOT_PLUGIN);

        if (boot == null) {
            return null;
        }

        try {
            return (String) boot.getClass()
                .getField("BOM_COORDINATES")
                .get(null);
        } catch (ReflectiveOperationException e) {
            // Boot 플러그인 버전에 따라 상수가 없을 수 있다. 없으면 BOM 을 모르는 것으로 본다
            return null;
        }
    }

    /** Spring Boot 주 버전. Boot 플러그인이 없거나 알 수 없으면 0. */
    static int majorVersion(Project project) {
        return majorVersion(bomCoordinates(project));
    }

    static int majorVersion(String bomCoordinates) {
        if (bomCoordinates == null) {
            return 0;
        }

        String version = bomCoordinates.substring(bomCoordinates.lastIndexOf(':') + 1);

        try {
            return Integer.parseInt(version.substring(0, version.indexOf('.')));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            // 버전 형식을 알 수 없으면(스냅숏 이름 등) 주 버전을 모르는 것으로 본다
            return 0;
        }
    }
}
