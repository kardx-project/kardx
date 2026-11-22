package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.kardx.gradle.KardxPlugin;
import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.process.JavaForkOptions;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code asciidoctor} 변환 JVM 인자 ({@code kardx.asciidoctorJvmArgs}). Asciidoctor 플러그인은 가짜다. */
class AsciidoctorIntegrationTest {

    @Test
    void conversionJvmStopsAtC1ByDefault(@TempDir File dir) {
        assertEquals(List.of("-XX:TieredStopAtLevel=1"), jvmArgs(dir, extension -> { }));
    }

    @Test
    void anEmptyListLeavesTheJvmDefaults(@TempDir File dir) {
        assertEquals(List.of(), jvmArgs(dir, extension -> extension.getAsciidoctorJvmArgs().empty()));
    }

    @Test
    void valuesSetInTheBuildScriptReplaceTheDefault(@TempDir File dir) {
        assertEquals(List.of("-Xmx1g"),
            jvmArgs(dir, extension -> extension.getAsciidoctorJvmArgs().set(List.of("-Xmx1g"))));
    }

    /** 플러그인을 적용하고 [buildScript] 로 확장을 바꾼 뒤(빌드 스크립트 본문 자리) 평가해, asciidoctor 가 받은 JVM 인자를 준다. */
    private static List<String> jvmArgs(File dir, Consumer<KardxExtension> buildScript) {
        Project project = ProjectBuilder.builder()
            .withProjectDir(dir)
            .build();

        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(PluginConstants.ASCIIDOCTOR_PLUGIN);
        project.getPluginManager().apply(KardxPlugin.class);

        KardxExtension extension = project.getExtensions().getByType(KardxExtension.class);

        extension.getAddDependencies().set(false);
        buildScript.accept(extension);
        ((ProjectInternal) project).evaluate();

        return ((FakeAsciidoctorTask) project.getTasks().getByName(PluginConstants.ASCIIDOCTOR_TASK)).jvmArgs;
    }

    /** {@code org.asciidoctor.jvm.convert} 처럼 {@code asciidoctor} 태스크를 등록한다. */
    @SuppressWarnings("unused") // 테스트 리소스의 플러그인 id 로 Gradle 이 만든다
    public static class FakeAsciidoctorPlugin implements Plugin<Project> {
        @Override
        public void apply(@Nonnull Project project) {
            project.getTasks().register(PluginConstants.ASCIIDOCTOR_TASK, FakeAsciidoctorTask.class);
        }
    }

    /** 연동이 리플렉션으로 부르는 Asciidoctor 태스크 메서드. {@code forkOptions} 로 받은 JVM 인자만 기록한다. */
    @SuppressWarnings("unused") // 연동이 리플렉션으로 부른다
    public abstract static class FakeAsciidoctorTask extends DefaultTask {
        private final List<String> jvmArgs = new ArrayList<>();

        public void configurations(Object... names) {
            // 이 테스트는 설정 이름을 보지 않는다
        }

        public void setSourceDir(Object directory) {
            // 이 테스트는 입력 디렉터리를 보지 않는다
        }

        public void sources(Action<PatternSet> pattern) {
            // 이 테스트는 입력 패턴을 보지 않는다
        }

        public void baseDirFollowsSourceDir() {
            // 이 테스트는 base 디렉터리를 보지 않는다
        }

        public void attributes(Map<String, Object> attributes) {
            // 이 테스트는 속성을 보지 않는다
        }

        @Internal
        public File getOutputDir() {
            return new File(getProject().getLayout().getBuildDirectory().getAsFile().get(), "docs/asciidoc");
        }

        /** Asciidoctor 4 의 {@code forkOptions(Action)}. {@code jvmArgs(Iterable)} 호출만 기록하는 대역을 넘긴다. */
        public void forkOptions(Action<JavaForkOptions> action) {
            action.execute((JavaForkOptions) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {JavaForkOptions.class}, (proxy, method, args) -> {
                    if (method.getName().equals("jvmArgs") && args[0] instanceof Iterable<?> values) {
                        values.forEach(value -> jvmArgs.add(String.valueOf(value)));
                    }

                    return proxy;
                }));
        }
    }
}
