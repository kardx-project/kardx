package io.github.kardx.gradle.tasks;

import io.github.kardx.gradle.internal.GradleToolLog;
import io.github.kardx.tooling.internal.steps.CoverageReportStep;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * {@code apiDocsCoverage}: 앱의 엔드포인트 중 문서화한 것의 비율을 보고한다. 문서화 테스트가 남긴 엔드포인트 목록과
 * 문서화한 operation({@code resource.json})만 읽으므로 빠르다. 결과는 {@value #JSON_FILE}·{@value #MARKDOWN_FILE} 이고
 * {@code apiDocs} 가 {@code coverage/} 아래에 넣는다. 콘솔에는 한 줄 요약을 남긴다.
 */
@CacheableTask
public abstract class ApiDocsCoverageTask extends DefaultTask {
    /** 기계가 읽는 보고서 파일 이름. */
    public static final String JSON_FILE = CoverageReportStep.JSON_FILE;
    /** 사람이 읽는 보고서 파일 이름. */
    public static final String MARKDOWN_FILE = CoverageReportStep.MARKDOWN_FILE;

    /** 엔드포인트 목록 파일 ({@code <snippets>/.kardx/endpoints/*.json}). */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getEndpointFiles();

    /**
     * 문서화한 operation 의 {@code resource.json}, 옆의 {@code openapi-extras.json}, OpenAPI 에서 뺀 operation 을 알아보는
     * {@code http-request.adoc}.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getOperationFiles();

    /** 보고에서 뺄 경로 패턴 (기본 제외 경로 포함). */
    @Input
    public abstract ListProperty<String> getExcludes();

    /** 문서화하지 않은 엔드포인트가 있으면 실패할지 여부. */
    @Input
    public abstract Property<Boolean> getFailOnUndocumented();

    /** 보고서 디렉터리. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** 콘솔 요약에 보여 줄 보고서 위치 ({@code build/api-docs/coverage/coverage.md}). 결과에 영향이 없다. */
    @Internal
    public abstract Property<String> getReportLocation();

    /** 보고서를 쓰고 요약을 남긴다. {@code failOnUndocumented} 이고 빠진 엔드포인트가 있으면 실패한다. */
    @TaskAction
    public void report() {
        CoverageReportStep.run(getEndpointFiles(), getOperationFiles(), getOutputDirectory().get().getAsFile(),
            new CoverageReportStep.Settings(getExcludes().get(), getFailOnUndocumented().get(),
                getReportLocation().get(), "kardx { coverage { exclude(\"/path/**\") } }"),
            GradleToolLog.of(getLogger()));
    }
}
