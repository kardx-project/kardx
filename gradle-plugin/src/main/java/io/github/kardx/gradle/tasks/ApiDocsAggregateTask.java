package io.github.kardx.gradle.tasks;

import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.tooling.internal.aggregate.OpenApiMerger;
import io.github.kardx.tooling.internal.aggregate.ProjectDirectories;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.io.OutputDirectoryGuard;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.openapi.OpenApiVersionSplit;
import io.github.kardx.tooling.internal.pages.AggregateIndexPage;
import io.github.kardx.tooling.internal.pages.OpenApiViewerPage;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * {@code apiDocsAggregate}: {@code restdocsAggregation} 으로 선언한 프로젝트들의 {@code apiDocs} 결과를 모은다.
 * 결과 디렉터리에는 프로젝트별 디렉터리({@code order/}, {@code services/member/}), 병합 스펙 {@code openapi3.yaml}
 * ({@link OpenApiMerger}), 목록 {@code index.html}({@link AggregateIndexPage}), 켜져 있으면 병합 스펙 뷰어가 생긴다.
 */
@DisableCachingByDefault(because = "Copies and merges files; caching would not be faster than running it")
public abstract class ApiDocsAggregateTask extends DefaultTask {
    private static final String MERGED_SPEC = "openapi3.yaml";
    private static final String OUTPUT_PROPERTY = "kardx.aggregate.outputDirectory";

    /** 모든 프로젝트의 문서 디렉터리 (입력 추적과 태스크 의존용). */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDocs();

    /** 프로젝트 경로 ({@code :order}). {@link #getDirectories()} 와 순서가 같다. */
    @Input
    public abstract ListProperty<String> getProjects();

    /** 프로젝트별 문서 디렉터리. {@link #getProjects()} 와 순서가 같다. 내용은 {@link #getDocs()} 로 추적한다. */
    @Internal
    public abstract ListProperty<File> getDirectories();

    /** 병합 스펙의 {@code info.title} 과 목록 페이지 제목. */
    @Input
    public abstract Property<String> getTitle();

    /** 병합 스펙의 {@code info.version}. */
    @Input
    public abstract Property<String> getVersion();

    /** 병합 스펙의 {@code info.description}. */
    @Input
    @Optional
    public abstract Property<String> getInfoDescription();

    /** 병합 스펙 뷰어 종류 ({@code redoc}, {@code scalar}). 없으면 뷰어를 만들지 않는다. */
    @Input
    @Optional
    public abstract Property<String> getViewerType();

    /** 병합 스펙 뷰어 파일 이름. */
    @Input
    @Optional
    public abstract Property<String> getViewerFileName();

    /** 목록 페이지·뷰어 문구의 언어 ({@code en}, {@code ko}). */
    @Input
    public abstract Property<String> getLanguage();

    /** 결과 디렉터리. 실행할 때마다 비우고 다시 채운다. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** 결과 디렉터리를 비워도 되는지 확인한다 ({@code src/}·프로젝트 디렉터리·사용자 파일이 있는 디렉터리는 거부). */
    @Internal
    public abstract Property<OutputDirectoryGuard> getOutputGuard();

    /** 파일 복사·삭제. */
    @Inject
    protected abstract FileSystemOperations getFs();

    /** 프로젝트별로 복사하고, 스펙을 합치고, 목록 페이지를 쓴다. */
    @TaskAction
    public void aggregate() {
        File output = getOutputDirectory().get().getAsFile();
        List<String> projects = getProjects().get();
        List<File> directories = getDirectories().get();
        List<AggregateIndexPage.Module> modules = new ArrayList<>();
        List<OpenApiMerger.Source> sources = new ArrayList<>();

        ProjectDirectories.checkDistinct(projects);

        if (getOutputGuard().isPresent()) {
            getOutputGuard().get().check(output, OUTPUT_PROPERTY);
        }

        getFs().delete(spec -> spec.delete(output));

        for (int i = 0; i < projects.size(); i++) {
            String name = ProjectDirectories.directoryName(projects.get(i));
            File source = directories.get(i);

            getFs().copy(spec -> spec.from(source)
                .exclude(OutputDirectoryGuard.MARKER)
                .into(new File(output, name)));
            modules.add(collect(name, source, sources));
        }

        String mergedSpec = sources.isEmpty() ? null : MERGED_SPEC;
        DocsLanguage language = DocsLanguage.of(getLanguage().get());
        String viewer = null;

        if (mergedSpec != null) {
            OpenApiMerger.Result merged = OpenApiMerger.merge(sources, getTitle().get(), getVersion().get(),
                getInfoDescription().getOrNull());

            TextFiles.write(new File(output, mergedSpec), SwaggerYaml.dump(merged.document()));
            merged.notes().forEach(note -> getLogger().lifecycle("apiDocsAggregate: {}", note));
            viewer = writeViewer(output, merged.document(), language);
        }

        TextFiles.write(new File(output, PluginConstants.INDEX_HTML),
            AggregateIndexPage.render(getTitle().get(), mergedSpec, viewer, modules, language));
        if (getOutputGuard().isPresent()) {
            getOutputGuard().get().mark(output);
        }

        getLogger().lifecycle("Aggregated API docs ({} projects): {}", modules.size(),
            new File(output, PluginConstants.INDEX_HTML).toPath().toUri());
    }

    private String writeViewer(File output, Map<String, Object> merged, DocsLanguage language) {
        if (!getViewerType().isPresent() || !getViewerFileName().isPresent()) {
            return null;
        }

        String fileName = getViewerFileName().get();

        TextFiles.write(new File(output, fileName),
            OpenApiViewerPage.render(getViewerType().get(), getTitle().get(), merged, language));

        return fileName;
    }

    /**
     * 프로젝트 문서 디렉터리에서 스펙 파일을 찾아 종류별로 나눈다. OpenAPI 3 스펙은 [sources] 에 더한다. 버전 스펙
     * ({@code apiVersioning.splitSpecs} 의 {@code openapi3-v1.yaml})은 원래 스펙과 같은 operation 을 담으므로 목록에만 둔다.
     */
    private static AggregateIndexPage.Module collect(String name, File directory, List<OpenApiMerger.Source> sources) {
        List<String> openApi = new ArrayList<>();
        List<String> asyncApi = new ArrayList<>();
        List<String> postman = new ArrayList<>();

        for (Path file : specFiles(directory.toPath())) {
            String relative = name + "/" + directory.toPath().relativize(file).toString().replace('\\', '/');
            Object parsed = OpenApiSpecFiles.read(file.toFile());

            if (!(parsed instanceof Map<?, ?> raw)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> document = (Map<String, Object>) raw;

            if (OpenApiSpecFiles.isOpenApi3(document)) {
                openApi.add(relative);
                addSource(name, file, document, sources);
            } else if (document.containsKey("asyncapi")) {
                asyncApi.add(viewerOrSpec(relative, file));
            } else if (String.valueOf(document.get("info")).contains("schema.getpostman.com")) {
                postman.add(relative);
            }
        }

        String index = PluginConstants.INDEX_HTML;
        String restDocs = new File(directory, index).isFile() ? name + "/" + index : null;

        return new AggregateIndexPage.Module(name, restDocs, openApi, asyncApi, postman);
    }

    private static void addSource(
        String name, Path file, Map<String, Object> document, List<OpenApiMerger.Source> sources) {
        if (!OpenApiVersionSplit.isSplit(file.toFile())) {
            sources.add(new OpenApiMerger.Source(name, document));
        }
    }

    /** AsyncAPI 는 같은 이름의 뷰어({@code asyncapi.html})가 있으면 그것을 링크한다. */
    private static String viewerOrSpec(String relative, Path spec) {
        String base = spec.getFileName().toString().replaceAll("\\.(ya?ml|json)$", "");
        boolean viewer = Files.isRegularFile(spec.resolveSibling(base + ".html"));

        return viewer ? relative.replaceAll("\\.(ya?ml|json)$", ".html") : relative;
    }

    /** 디렉터리 안의 YAML·JSON 파일 (경로순). */
    private static List<Path> specFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().matches(".*\\.(ya?ml|json)$"))
                .sorted(Comparator.naturalOrder())
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
