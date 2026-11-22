package io.github.kardx.gradle.internal.wiring;

import java.util.Set;
import org.gradle.api.artifacts.Dependency;

/**
 * 프로젝트가 선언한 웹 스택. 선언된 의존성만 보고 정한다 (해석하지 않는다).
 * <ul>
 *   <li>{@link #MVC}: {@code spring-boot-starter-web}·{@code spring-boot-starter-websocket}(starter-web 을 포함한다) 또는
 *       {@code spring-webmvc}. WebFlux 를 함께 선언해도 Spring Boot 는 서블릿 앱으로 띄우므로 MVC 다</li>
 *   <li>{@link #WEBFLUX}: 위가 없고 {@code spring-boot-starter-webflux} 또는 {@code spring-webflux}</li>
 *   <li>{@link #NONE}: 둘 다 없음 (예: {@code spring-websocket} 만 쓰는 메시지 문서화)</li>
 * </ul>
 */
enum WebStack {
    MVC,
    WEBFLUX,
    NONE;

    static final String BOOT_GROUP = "org.springframework.boot";
    static final String WEB_STARTER = "spring-boot-starter-web";
    private static final String SPRING_GROUP = "org.springframework";
    private static final Set<String> MVC_STARTERS = Set.of(WEB_STARTER, "spring-boot-starter-websocket");

    static WebStack of(Iterable<? extends Dependency> declared) {
        if (DependencyMatch.declares(declared, BOOT_GROUP, MVC_STARTERS)
            || DependencyMatch.declares(declared, SPRING_GROUP, "spring-webmvc")) {
            return MVC;
        }

        if (DependencyMatch.declares(declared, BOOT_GROUP, "spring-boot-starter-webflux")
            || DependencyMatch.declares(declared, SPRING_GROUP, "spring-webflux")) {
            return WEBFLUX;
        }

        return NONE;
    }
}
