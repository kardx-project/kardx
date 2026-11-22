package io.github.kardx.gradle.tasks;

import io.github.kardx.gradle.internal.GradleToolLog;
import io.github.kardx.tooling.internal.export.GeneratedSpecs;
import io.github.kardx.tooling.internal.export.NameTemplate;
import io.github.kardx.tooling.internal.export.SecretMask;
import io.github.kardx.tooling.internal.export.SpecKind;
import io.github.kardx.tooling.internal.export.SpecUploader;
import io.github.kardx.tooling.internal.export.UrlSecrets;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * {@code apiDocsUpload}: 고른 스펙을 HTTP 로 보낸다 ({@link SpecUploader}). 서버에 남는 일을 하므로 출력을 추적하지 않고
 * 실행할 때마다 보낸다.
 */
@UntrackedTask(because = "Sends the specs to a server; it has side effects and runs every time it is requested")
public abstract class ApiSpecUploadTask extends DefaultTask {
    private static final String OPTION = "kardx.upload.";
    /** 첫 재시도 전 대기 시간. 다음부터는 두 배씩 늘린다. */
    private static final Duration BACKOFF = Duration.ofSeconds(1);

    /** OpenAPI 스펙 후보. 있는 첫 파일을 쓴다. epages 가 없으면 비어 있다. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getOpenApiSpec();

    /** AsyncAPI 스펙 파일. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getAsyncApiSpec();

    /** OpenAPI 스펙을 올릴지 여부 ({@code upload.openApi}). */
    @Input
    public abstract Property<Boolean> getIncludeOpenApi();

    /** AsyncAPI 스펙을 올릴지 여부 ({@code upload.asyncApi}). */
    @Input
    public abstract Property<Boolean> getIncludeAsyncApi();

    /** OpenAPI 주소 템플릿 ({@link NameTemplate}). 없으면 실행할 때 실패한다. */
    @Input
    @Optional
    public abstract Property<String> getUrl();

    /** AsyncAPI 주소 템플릿. */
    @Input
    @Optional
    public abstract Property<String> getAsyncApiUrl();

    /** HTTP 메서드 ({@code PUT}, {@code POST}). */
    @Input
    public abstract Property<String> getMethod();

    /**
     * 요청 헤더. 값이 비밀(토큰)일 수 있어 태스크 입력으로 기록하지 않고 실행할 때 읽는다. 로그에는 이름만 남는다.
     */
    @Internal
    public abstract MapProperty<String, String> getHeaders();

    /** 연결·응답 대기 시간. {@link org.gradle.api.Task#getTimeout()}(태스크 실행 시간 제한)과 다르다. */
    @Input
    public abstract Property<Duration> getRequestTimeout();

    /** 연결 실패, 429, 5xx 일 때 다시 보낼 횟수. 1초, 2초, 4초… 기다리며, 한 번 기다리는 시간은 30초를 넘지 않는다. */
    @Input
    public abstract Property<Integer> getRetries();

    /** {@code {name}} 값. */
    @Input
    public abstract Property<String> getProjectName();

    /** {@code {version}} 값. */
    @Input
    public abstract Property<String> getProjectVersion();

    /** 고른 스펙을 차례로 보낸다. 하나라도 실패하면 태스크가 실패한다. */
    @TaskAction
    public void upload() {
        if (!getUrl().isPresent() || getUrl().get().isBlank()) {
            throw new GradleException(OPTION + "url is not set, so " + getName() + " has nowhere to send the specs. "
                + "Set it, e.g. upload { url = \"https://registry.example.com/apis/{name}/{type}\" }.");
        }

        List<Target> targets = targets();

        if (targets.isEmpty()) {
            throw new GradleException("Nothing to upload: " + OPTION + "openApi and asyncApi are both false.");
        }

        if (targets.size() > 1 && targets.get(0).uri().equals(targets.get(1).uri())) {
            URI uri = targets.get(0).uri();

            throw new GradleException("Both specs would be uploaded to "
                + new SecretMask(UrlSecrets.of(uri)).apply(uri.toString()) + ". Put {type} in "
                + OPTION + "url or set " + OPTION + "asyncApiUrl.");
        }

        SpecUploader uploader = new SpecUploader(settings(), GradleToolLog.of(getLogger()));

        targets.forEach(target -> uploader.upload(target.file(), target.uri()));
    }

    private List<Target> targets() {
        List<Target> targets = new ArrayList<>();

        if (getIncludeOpenApi().get()) {
            File spec = GeneratedSpecs.openApi(getOpenApiSpec().getFiles(), OPTION + "openApi");

            targets.add(target(spec, SpecKind.OPENAPI, getUrl().get(), "url"));
        }

        if (getIncludeAsyncApi().get()) {
            File spec = GeneratedSpecs.asyncApi(getAsyncApiSpec().getFiles(), OPTION + "asyncApi");

            targets.add(target(spec, SpecKind.ASYNCAPI, getAsyncApiUrl().getOrElse(getUrl().get()), "asyncApiUrl"));
        }

        return targets;
    }

    private Target target(File spec, SpecKind kind, String template, String property) {
        Map<String, String> values = NameTemplate.values(getProjectName().get(), getProjectVersion().get(), kind,
            GeneratedSpecs.extension(spec));

        return new Target(spec, SpecUploader.uri(NameTemplate.url(template, values, OPTION + property),
            OPTION + property));
    }

    private SpecUploader.Settings settings() {
        int retries = getRetries().get();
        Duration timeout = getRequestTimeout().get();

        if (retries < 0) {
            throw new GradleException(OPTION + "retries must be 0 or more, got " + retries);
        }

        if (timeout.isNegative() || timeout.isZero()) {
            throw new GradleException(OPTION + "timeout must be positive, got " + timeout);
        }

        if (!getHeaders().isPresent()) {
            // MapProperty 는 값이 없는 provider 를 하나라도 넣으면 맵 전체가 없어지므로 어느 헤더인지는 알 수 없다
            throw new GradleException("A header in " + OPTION + "headers has no value, for example an environment "
                + "variable or Gradle property it reads is not set.");
        }

        return new SpecUploader.Settings(SpecUploader.method(getMethod().get(), OPTION + "method"),
            getHeaders().get(), timeout, retries, BACKOFF);
    }

    /** 보낼 파일과 주소. */
    private record Target(File file, URI uri) {
    }
}
