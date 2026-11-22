package io.github.kardx.gradle.tasks;

import io.github.kardx.gradle.internal.GradleToolLog;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.tooling.internal.steps.OpenApiCheckStep;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * {@code apiDocsCheck}: 커밋해 둔 기준 OpenAPI 스펙과 새로 만든 스펙을 비교한다. 하위 호환을 깨는 변경과 경고
 * ({@link io.github.kardx.tooling.internal.check.OpenApiBreakingChanges})와 전체 구조 차이
 * ({@link io.github.kardx.tooling.internal.check.OpenApiStructureDiff})를 콘솔과
 * {@code build/reports/kardx/openapi-changes.txt} 에 남기고, {@code failOn} 에 따라 실패한다.
 * {@code apiDocsBaseline} 은 새 스펙을 기준 파일로 복사한다. 둘 다 {@code openapi3} 뒤에 실행된다.
 */
@DisableCachingByDefault(because = "Compares two small specs; caching would not be faster than running it")
public abstract class OpenApiCheckTask extends DefaultTask {
    /** 어떤 변경이든 실패 (기본값). */
    public static final String FAIL_ON_ANY = OpenApiCheckStep.FAIL_ON_ANY;
    /** 하위 호환을 깨는 변경만 실패. */
    public static final String FAIL_ON_BREAKING = OpenApiCheckStep.FAIL_ON_BREAKING;
    /** 실패하지 않는다. */
    public static final String FAIL_ON_NONE = OpenApiCheckStep.FAIL_ON_NONE;

    /** 새로 만든 스펙 후보 ({@code openapi3.yaml|yml|json}). 있는 첫 파일을 쓴다. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getSpec();

    /** 기준 스펙 파일 하나. 없어도 된다 (그때는 만드는 방법을 알려 준다). */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getBaseline();

    /** {@value #FAIL_ON_ANY}, {@value #FAIL_ON_BREAKING}, {@value #FAIL_ON_NONE}. */
    @Input
    public abstract Property<String> getFailOn();

    /** 기준 파일이 없을 때 {@code failOn} 이 {@value #FAIL_ON_NONE} 이 아니면 실패할지 여부. false 면 {@value #FAIL_ON_ANY} 일 때만 실패한다. */
    @Input
    public abstract Property<Boolean> getRequireBaseline();

    /** 변경 보고서. */
    @OutputFile
    public abstract RegularFileProperty getReport();

    /** 비교하고 보고서를 쓴 뒤, {@code failOn} 에 따라 실패하거나 경고한다. */
    @TaskAction
    public void check() {
        OpenApiCheckStep.run(getSpec().getFiles(), getBaseline().getSingleFile(),
            getReport().get().getAsFile(),
            new OpenApiCheckStep.Settings(getFailOn().get(), getRequireBaseline().get(),
                "kardx.openApiCheck.failOn", "./gradlew " + PluginConstants.OPEN_API_BASELINE_TASK,
                "kardx.openApiCheck.requireBaseline = false"),
            GradleToolLog.of(getLogger()));
    }
}
