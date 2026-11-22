package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;

/**
 * 라이브러리와 테스트 클라이언트 의존성을 넣는다 ({@link KardxExtension#getAddDependencies()}, 기본값 true).
 * <ul>
 *   <li>{@code compileOnly}: {@value #ANNOTATIONS} (플러그인과 같은 버전)</li>
 *   <li>{@code testImplementation}: {@value #CORE} (플러그인 버전). Kotest 판을 선언에서 알 수 있으면 그 판의 확장
 *       ({@value #KOTEST}·{@value #KOTEST5}, core 를 함께 가져온다)</li>
 *   <li>{@code testImplementation}: MVC 앱({@link WebStack#MVC})이면 {@code spring-restdocs-mockmvc}, WebFlux 앱이면
 *       {@code spring-restdocs-webtestclient}. 버전은 적지 않고 프로젝트의 Spring Boot BOM 에 맡긴다</li>
 *   <li>epages 플러그인이 있으면 {@code com.epages:restdocs-api-spec-mockmvc}·{@code -webtestclient} 를 epages 플러그인과 같은
 *       버전으로</li>
 * </ul>
 * 사용자가 같은 모듈을 직접 선언했으면(다른 버전, 같은 빌드의 프로젝트 포함) 넣지 않는다. 선언된 의존성만 보고 정하며, 그
 * configuration 을 상속한 classpath 가 해석되기 직전({@code withDependencies}, Kotlin 플러그인이 stdlib 를 넣는 방식과 같다)에
 * 더하므로 빌드 스크립트의 선언이 모두 끝난 뒤에 정하고 다른 configuration 을 미리 해석하지 않는다.
 */
public final class LibraryDependencies {
    static final String GROUP = "io.github.kardx-project";
    static final String CORE = "kardx";
    static final String ANNOTATIONS = CORE + "-annotations";
    static final String KOTEST = CORE + "-kotest";
    static final String KOTEST5 = CORE + "-kotest5";

    /** 같은 빌드에서 프로젝트로 넣을 때의 이름(이 저장소의 샘플)도 함께 본다. */
    private static final Set<String> ANNOTATIONS_NAMES = Set.of(ANNOTATIONS, "annotations");
    private static final Set<String> KOTEST_NAMES = Set.of(KOTEST, "kotest", KOTEST5, "kotest5");
    /** 이 중 하나가 있으면 core 가 따라온다. */
    private static final Set<String> LIBRARY_NAMES = Set.of(CORE, "core", KOTEST, "kotest", KOTEST5, "kotest5");

    private static final String RESTDOCS_GROUP = "org.springframework.restdocs";
    private static final String MOCK_MVC = "spring-restdocs-mockmvc";
    private static final String WEB_TEST_CLIENT = "spring-restdocs-webtestclient";
    private static final String EPAGES_GROUP = "com.epages";
    private static final String EPAGES_MOCK_MVC = "restdocs-api-spec-mockmvc";
    private static final String EPAGES_WEB_TEST_CLIENT = "restdocs-api-spec-webtestclient";

    private LibraryDependencies() {
    }

    public static void configure(Project project, KardxExtension extension) {
        Provider<Boolean> enabled = extension.getAddDependencies();
        DependencyHandler handler = project.getDependencies();
        Logger logger = project.getLogger();
        EpagesPlugin epages = new EpagesPlugin();

        project.getPluginManager().withPlugin(PluginConstants.EPAGES_PLUGIN, applied ->
            epages.applied(EpagesVersion.of(project.getPlugins().getPlugin(PluginConstants.EPAGES_PLUGIN))));
        project.afterEvaluate(p -> warnIfEpagesMismatched(p, epages));

        Configuration compileClasspath =
            project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);

        project.getConfigurations()
            .named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
            .configure(compileOnly -> compileOnly.withDependencies(own -> {
                if (enabled.get()
                    && !DependencyMatch.declares(compileClasspath.getAllDependencies(), GROUP, ANNOTATIONS_NAMES)) {
                    add(own, handler, logger, compileOnly, GROUP + ":" + ANNOTATIONS + ":" + pluginVersion());
                }
            }));
        project.getConfigurations()
            .named(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME)
            .configure(testImplementation -> testImplementation.withDependencies(own -> {
                if (enabled.get()) {
                    testLibraries(testImplementation.getAllDependencies(), epages, logger)
                        .forEach(notation -> add(own, handler, logger, testImplementation, notation));
                }
            }));

        AtomicBoolean hinted = new AtomicBoolean();

        for (String name : List.of(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME,
            JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)) {
            project.getConfigurations()
                .named(name)
                .configure(classpath ->
                    classpath.getIncoming().afterResolve(resolved -> hintIfUnmanaged(resolved, logger, hinted)));
        }
    }

    /**
     * {@code testImplementation} 에 넣을 모듈 좌표. {@code declared} 는 그 configuration 과 상위({@code implementation} 등)의
     * 선언이다.
     */
    static List<String> testLibraries(Set<? extends Dependency> declared, EpagesPlugin epages, Logger logger) {
        List<String> notations = new ArrayList<>();
        String kotest = DependencyMatch.declares(declared, GROUP, KOTEST_NAMES)
            ? null
            : KotestVersionLine.artifact(declared);

        if (kotest != null) {
            notations.add(GROUP + ":" + kotest + ":" + pluginVersion());
        } else if (!DependencyMatch.declares(declared, GROUP, LIBRARY_NAMES)) {
            notations.add(GROUP + ":" + CORE + ":" + pluginVersion());
        }

        WebStack stack = WebStack.of(declared);

        if (stack == WebStack.NONE) {
            return notations;
        }

        String client = stack == WebStack.MVC ? MOCK_MVC : WEB_TEST_CLIENT;

        if (!DependencyMatch.declares(declared, RESTDOCS_GROUP, client)) {
            notations.add(RESTDOCS_GROUP + ":" + client);
        }

        if (epages.isApplied()
            && !DependencyMatch.declares(declared, EPAGES_GROUP, Set.of(EPAGES_MOCK_MVC, EPAGES_WEB_TEST_CLIENT))) {
            String epagesClient = stack == WebStack.MVC ? EPAGES_MOCK_MVC : EPAGES_WEB_TEST_CLIENT;

            if (epages.version == null) {
                epages.logUnknownVersionOnce(logger, epagesClient);
            } else {
                notations.add(EPAGES_GROUP + ":" + epagesClient + ":" + epages.version);
            }
        }

        return notations;
    }

    private static void add(
        DependencySet own, DependencyHandler handler, Logger logger, Configuration configuration, String notation) {
        own.add(handler.create(notation));
        logger.info("kardx: added {} to {} (kardx.addDependencies)", notation,
            configuration.getName());
    }

    /**
     * 버전 없이 넣은 REST Docs 클라이언트를 아무것도 관리하지 않아 해석하지 못했으면 이유와 고치는 방법을 한 번 알린다.
     * 해석 실패 자체는 Gradle 이 알린다.
     */
    private static void hintIfUnmanaged(ResolvableDependencies resolved, Logger logger, AtomicBoolean hinted) {
        resolved.getResolutionResult()
            .getAllDependencies()
            .stream()
            .filter(UnresolvedDependencyResult.class::isInstance)
            .map(DependencyResult::getRequested)
            .filter(ModuleComponentSelector.class::isInstance)
            .map(ModuleComponentSelector.class::cast)
            .filter(selector -> RESTDOCS_GROUP.equals(selector.getGroup())
                && Set.of(MOCK_MVC, WEB_TEST_CLIENT).contains(selector.getModule())
                && selector.getVersion().isEmpty())
            .findFirst()
            .filter(selector -> hinted.compareAndSet(false, true))
            .ifPresent(selector -> logger.error("kardx: {}:{} was added without a version, but nothing "
                + "manages its version. Apply the Spring Boot BOM (plugin io.spring.dependency-management, or "
                + "implementation(platform(\"org.springframework.boot:spring-boot-dependencies:<version>\"))), declare "
                + "it with a version yourself, or set kardx { addDependencies = false }.",
                selector.getGroup(), selector.getModule()));
    }

    private static void warnIfEpagesMismatched(Project project, EpagesPlugin epages) {
        String warning = EpagesVersion.mismatch(SpringBootIntegration.majorVersion(project), epages.version);

        if (warning != null) {
            project.getLogger().warn(warning);
        }
    }

    private static String pluginVersion() {
        return SpringBootIntegration.pluginVersion();
    }

    /** epages 플러그인이 적용됐는지와 그 버전 (모르면 null). */
    static final class EpagesPlugin {
        private volatile boolean applied;
        private volatile String version;
        private final AtomicBoolean logged = new AtomicBoolean();

        boolean isApplied() {
            return applied;
        }

        void applied(String version) {
            this.applied = true;
            this.version = version;
        }

        void logUnknownVersionOnce(Logger logger, String module) {
            if (logged.compareAndSet(false, true)) {
                logger.lifecycle("kardx: could not read the com.epages.restdocs-api-spec plugin version, so "
                    + "{}:{} was not added. Declare it with the same version as the plugin.", EPAGES_GROUP, module);
            }
        }
    }
}
