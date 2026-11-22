package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;

/**
 * WebFlux 앱의 테스트 런타임 클래스패스에서 {@code spring-boot-starter-web} 을 뺀다
 * ({@link KardxExtension#getExcludeServletStarterForWebFlux()}).
 *
 * <p>epages 가 runtime 으로 끌고 오는 {@code spring-boot-starter-web} 이 있으면 {@code @SpringBootTest} 가 WebFlux 앱을
 * 서블릿 앱으로 띄운다. {@code testRuntimeClasspath} 가 해석되기 직전({@code withDependencies})에 그 configuration 이
 * 상속한 선언 의존성만 보고 정하므로, 다른 configuration 을 미리 해석하지 않고 설정 단계에도 일을 하지 않는다.
 */
public final class WebFluxTestClasspath {
    private static final String BOOT_GROUP = WebStack.BOOT_GROUP;
    private static final String WEB_STARTER = WebStack.WEB_STARTER;

    private WebFluxTestClasspath() {
    }

    public static void configure(Project project, KardxExtension extension) {
        Provider<Boolean> enabled = extension.getExcludeServletStarterForWebFlux();
        Logger logger = project.getLogger();

        project.getConfigurations()
            .named(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            .configure(classpath -> classpath.withDependencies(own -> {
                if (enabled.get() && isWebFluxWithoutWebStarter(classpath.getAllDependencies())) {
                    excludeWebStarter(classpath);
                    logger.info("kardx: excluded {}:{} from {} (WebFlux app). "
                            + "Set kardx.excludeServletStarterForWebFlux = false to keep it.",
                        BOOT_GROUP, WEB_STARTER, classpath.getName());
                }
            }));
    }

    /** WebFlux 앱이고({@link WebStack#WEBFLUX}) 서블릿 쪽({@code spring-boot-starter-web} 등)을 직접 선언하지 않았는가. */
    static boolean isWebFluxWithoutWebStarter(Set<? extends Dependency> declared) {
        return WebStack.of(declared) == WebStack.WEBFLUX;
    }

    private static void excludeWebStarter(Configuration classpath) {
        classpath.exclude(Map.of("group", BOOT_GROUP, "module", WEB_STARTER));
    }
}
