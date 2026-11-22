package io.github.kardx.gradle.tasks;

import io.github.kardx.gradle.internal.GradleToolLog;
import io.github.kardx.tooling.internal.sourcedocs.SourceDocsOptions;
import io.github.kardx.tooling.internal.steps.SourceDocsStep;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.NormalizeLineEndings;

/**
 * {@code apiDocsSourceDocs}: Kotlin·Java 소스의 KDoc·Javadoc 을 설명 색인(JSON)으로 모은다. 컴파일하지 않고 소스 글만
 * 읽으므로 빠르다 (수백 파일에 수십 ms). 소스가 없으면 빈 색인을 쓴다. 읽지 못하는 선언은 건너뛸 뿐 실패하지 않는다.
 * 실험 기능이라 {@code sourceDocs.enabled} 를 켰을 때만 test 에 연결된다. 항목별 스위치는 모두 태스크 입력이라 바꾸면 다시 돈다.
 */
@CacheableTask
public abstract class SourceDocsTask extends DefaultTask {

    /** 읽을 소스 파일. {@code .kt}·{@code .java} 가 아닌 파일은 무시한다. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    @NormalizeLineEndings
    public abstract ConfigurableFileCollection getSources();

    /** 속성·record 구성 요소·getter 문서를 모을지 여부 ({@code sourceDocs.fieldDocs}). */
    @Input
    public abstract Property<Boolean> getFieldDocs();

    /** enum 상수 문서를 모을지 여부 ({@code sourceDocs.enumDocs}). */
    @Input
    public abstract Property<Boolean> getEnumDocs();

    /** 클래스 문서 본문(OpenAPI 컴포넌트 스키마 설명)을 모을지 여부 ({@code sourceDocs.schemaDescriptions}). */
    @Input
    public abstract Property<Boolean> getSchemaDescriptions();

    /** 핸들러 메서드의 {@code @param} 을 모을지 여부 ({@code sourceDocs.paramDocs}). */
    @Input
    public abstract Property<Boolean> getParamDocs();

    /**
     * 메서드(컨트롤러 핸들러 등) 문서 주석의 본문도 모을지 여부 ({@code sourceDocs.methodDocs}). false 면 메서드 항목에
     * summary·description 이 없어 OpenAPI·HTML 의 요약·설명에 쓰이지 않는다.
     */
    @Input
    public abstract Property<Boolean> getMethodDocs();

    /** 설명 색인 파일. */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /** 소스를 읽어 색인을 쓴다. */
    @TaskAction
    public void scan() {
        SourceDocsOptions options = new SourceDocsOptions(getFieldDocs().get(), getEnumDocs().get(),
            getSchemaDescriptions().get(), getParamDocs().get(), getMethodDocs().get());

        SourceDocsStep.run(getSources().getAsFileTree().getFiles(), options, getOutputFile().get().getAsFile(),
            GradleToolLog.of(getLogger()));
    }
}
