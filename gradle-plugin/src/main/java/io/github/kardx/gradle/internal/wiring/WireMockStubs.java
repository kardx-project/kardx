package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import java.util.List;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;

/**
 * WireMock stub ({@code wireMock.enabled = true}).
 * <ul>
 *   <li>test: {@code kardx.wire-mock-stubs=true} 를 넘긴다. 라이브러리가 문서화한 요청·응답마다
 *       {@code <스니펫 디렉터리>/wiremock/mappings/<class-name>/<method-name>.json} 을 쓴다</li>
 *   <li>{@code apiDocsStubsJar}: 그 매핑을 {@code META-INF/<group>/<name>/<version>/mappings/} 에 담은
 *       {@code <name>-<version>-stubs.jar} (Spring Cloud Contract Stub Runner 와 같은 배치). group 과 version 은 실행할 때
 *       읽고, group 이 없으면 Stub Runner 가 찾을 수 없는 jar 를 만드는 대신 실패한다</li>
 * </ul>
 */
public final class WireMockStubs {
    /** core 의 설정 키 ({@code RestDocsSettings.wireMockStubs}). */
    private static final String PROPERTY = PluginConstants.PROPERTY_PREFIX + "wire-mock-stubs";

    private WireMockStubs() {
    }

    public static void configure(Project project, KardxExtension extension, TaskProvider<Test> test) {
        Provider<Boolean> enabled = extension.getWireMock().getEnabled();

        test.configure(task -> {
            task.getInputs().property("wireMockStubs", enabled);
            task.getJvmArgumentProviders()
                .add(() -> enabled.get() ? List.of("-D" + PROPERTY + "=true") : List.of());
        });

        // 빌드 스크립트가 plugins { } 뒤에 정하는 값이므로 늦게 읽는다
        Provider<String> group = project.provider(() -> String.valueOf(project.getGroup()));
        Provider<String> version = project.provider(() -> String.valueOf(project.getVersion()));
        String name = project.getName();

        project.getTasks().register(PluginConstants.STUBS_JAR_TASK, Jar.class, jar -> {
            jar.setGroup(PluginConstants.GROUP);
            jar.setDescription("Packages the WireMock mappings written by documentation tests into a stubs jar.");
            jar.dependsOn(test);
            jar.onlyIf("wireMock.enabled", t -> enabled.get());
            jar.getArchiveClassifier().set("stubs");
            jar.from(extension.getSnippetsDirectory().dir(PluginConstants.WIRE_MOCK_DIR + "/mappings"), copy ->
                copy.into(group.map(g -> "META-INF/" + g + "/" + name + "/" + version.get() + "/mappings")));
            // Gradle 은 Java 람다로 넘긴 태스크 액션의 구현을 추적하지 못한다(빌드 캐시·up-to-date). 그래서 익명 클래스로 쓴다
            jar.doFirst(new Action<>() {
                @Override
                public void execute(@Nonnull Task t) {
                    if (group.get().isBlank()) {
                        throw new GradleException("apiDocsStubsJar: project.group is not set, so Stub Runner cannot "
                            + "find the stubs (META-INF/<group>/<name>/<version>/mappings). Set group in the build "
                            + "script, for example group = \"com.example\".");
                    }
                }
            });
        });
    }
}
