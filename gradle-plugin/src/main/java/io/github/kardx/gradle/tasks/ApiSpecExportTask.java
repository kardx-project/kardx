package io.github.kardx.gradle.tasks;

import io.github.kardx.tooling.internal.export.GeneratedSpecs;
import io.github.kardx.tooling.internal.export.NameTemplate;
import io.github.kardx.tooling.internal.export.SpecKind;
import io.github.kardx.tooling.internal.io.OutputDirectoryGuard;
import io.github.kardx.tooling.internal.io.TextFiles;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * {@code apiDocsExport}: 고른 결과(OpenAPI, AsyncAPI, 문서 디렉터리 전체)를 정한 디렉터리에 정한 이름으로 복사한다.
 * Maven 배포에 붙일 스펙을 고정 이름으로 모으는 {@code apiDocsPublicationSpecs} 도 이 타입이다.
 *
 * <p>사용자가 고른 디렉터리에는 다른 파일이 있을 수 있어 출력을 추적하지 않고, 복사는 빠르므로 실행할 때마다 한다.
 */
@UntrackedTask(because = "Copies into a user-chosen directory that may hold other files; copying is cheap")
public abstract class ApiSpecExportTask extends DefaultTask {
    private static final String OPTION = "kardx.export.";

    /** OpenAPI 스펙 후보 ({@code openapi3.yaml|yml|json}). 있는 첫 파일을 쓴다. epages 가 없으면 비어 있다. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getOpenApiSpec();

    /** AsyncAPI 스펙 파일. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getAsyncApiSpec();

    /** {@code apiDocs} 결과 디렉터리. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getHtmlDirectory();

    /** OpenAPI 스펙을 내보낼지 여부 ({@code export.openApi}). */
    @Input
    public abstract Property<Boolean> getIncludeOpenApi();

    /** AsyncAPI 스펙을 내보낼지 여부 ({@code export.asyncApi}). */
    @Input
    public abstract Property<Boolean> getIncludeAsyncApi();

    /** 문서 디렉터리(HTML)를 내보낼지 여부 ({@code export.html}). */
    @Input
    public abstract Property<Boolean> getIncludeHtml();

    /** OpenAPI 파일 이름 템플릿 ({@link NameTemplate}). */
    @Input
    public abstract Property<String> getOpenApiFileName();

    /** AsyncAPI 파일 이름 템플릿. */
    @Input
    public abstract Property<String> getAsyncApiFileName();

    /** 문서 디렉터리를 복사할 하위 디렉터리 이름 템플릿. {@link #getIncludeHtml()} 일 때만 읽는다. */
    @Input
    @Optional
    public abstract Property<String> getHtmlDirectoryName();

    /** {@code {name}} 값. */
    @Input
    public abstract Property<String> getProjectName();

    /** {@code {version}} 값. */
    @Input
    public abstract Property<String> getProjectVersion();

    /** 내보낼 디렉터리. 정하지 않으면 실행할 때 알려 주며 실패한다. */
    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getDirectory();

    /** 디렉터리 복사. */
    @Inject
    protected abstract FileSystemOperations getFs();

    /** 고른 결과를 복사한다. */
    @TaskAction
    public void export() {
        File directory = directory();
        List<String> exported = new ArrayList<>();

        if (getIncludeOpenApi().get()) {
            File spec = GeneratedSpecs.openApi(getOpenApiSpec().getFiles(), OPTION + "openApi");

            exported.add(copy(spec, SpecKind.OPENAPI, getOpenApiFileName(), "openApiFileName", directory));
        }

        if (getIncludeAsyncApi().get()) {
            File spec = GeneratedSpecs.asyncApi(getAsyncApiSpec().getFiles(), OPTION + "asyncApi");

            exported.add(copy(spec, SpecKind.ASYNCAPI, getAsyncApiFileName(), "asyncApiFileName", directory));
        }

        if (getIncludeHtml().get()) {
            exported.add(copyHtml(directory));
        }

        if (exported.isEmpty()) {
            throw new GradleException("Nothing to export: " + OPTION + "openApi, asyncApi and html are all false.");
        }

        getLogger().lifecycle("Exported API docs to {}: {}", directory, String.join(", ", exported));
    }

    private File directory() {
        if (!getDirectory().isPresent()) {
            throw new GradleException(OPTION + "directory is not set. Set it before running " + getName()
                + ", e.g. kardx { export { directory = layout.buildDirectory.dir(\"exported-specs\") } }");
        }

        return getDirectory().get().getAsFile();
    }

    private String copy(File spec, SpecKind kind, Property<String> template, String property, File directory) {
        String name = NameTemplate.fileName(template.get(), values(kind, GeneratedSpecs.extension(spec)),
            OPTION + property);

        TextFiles.copy(spec, new File(directory, name));

        return name;
    }

    private String copyHtml(File directory) {
        File source = GeneratedSpecs.htmlDirectory(getHtmlDirectory().getFiles(), OPTION + "html");
        // {type}·{ext} 는 디렉터리 이름에 뜻이 없지만 템플릿 규칙을 하나로 두려고 값만 채운다
        String name = NameTemplate.fileName(getHtmlDirectoryName().get(), values(SpecKind.OPENAPI, ""),
            OPTION + "htmlDirectoryName");

        getFs().sync(spec -> spec.from(source)
            .exclude(OutputDirectoryGuard.MARKER)
            .into(new File(directory, name)));

        return name + "/";
    }

    private Map<String, String> values(SpecKind kind, String extension) {
        return NameTemplate.values(getProjectName().get(), getProjectVersion().get(), kind, extension);
    }
}
