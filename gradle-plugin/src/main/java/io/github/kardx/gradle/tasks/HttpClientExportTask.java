package io.github.kardx.gradle.tasks;

import io.github.kardx.gradle.internal.GradleToolLog;
import io.github.kardx.tooling.internal.steps.HttpClientStep;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * {@code apiDocsHttpClient}: 문서화한 요청을 IntelliJ HTTP Client 파일과 환경 파일
 * ({@value io.github.kardx.tooling.internal.httpclient.HttpClientFile#ENV_FILE})로 만든다. 가린 값을 바꾼 변수가
 * 있으면 비밀 환경 파일({@value io.github.kardx.tooling.internal.httpclient.HttpClientFile#PRIVATE_ENV_FILE})에도
 * 빈 값으로 선언한다. operation 마다 대표 테스트의 요청 하나를 쓴다
 * ({@link io.github.kardx.tooling.internal.httpclient.HttpClientRequests}). {@code apiDocs} 가 {@code http-client/}
 * 아래에 넣는다.
 */
@CacheableTask
public abstract class HttpClientExportTask extends DefaultTask {

    /** 조각 파일: {@code resource.json}, {@code openapi-extras.json}, {@code http-request.adoc}. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getSnippetFiles();

    /** 후처리한 OpenAPI 스펙 후보. operationId 를 읽는다. 없어도 된다. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getSpec();

    /** 요청 파일 이름 ({@code api.http}). */
    @Input
    public abstract Property<String> getFileName();

    /** 결과 디렉터리. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** 지난 결과를 지우고 요청 파일과 환경 파일을 쓴다. */
    @TaskAction
    public void export() {
        HttpClientStep.run(getSnippetFiles(), getSpec(), getOutputDirectory().get().getAsFile(), getFileName().get(),
            GradleToolLog.of(getLogger()));
    }
}
