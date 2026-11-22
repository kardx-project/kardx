package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * {@code apiDocsIndex}: index 템플릿에 테스트 클래스별 링크를 넣는다. 설정이 없으면 테스트가 문서와 함께 남긴 항목으로
 * 문서를 만든 모든 테스트 클래스를 넣고, 템플릿 파일이 없으면 내장 템플릿(제목 {@code index.title})을 쓴다.
 * {@code apiDocsAsciidocSources}: 직접 쓴 문서, 생성된 문서, index 를 Asciidoctor 입력으로 모은다.
 *
 * <p>index 는 생성 문서 디렉터리({@code build/generated-asciidoc}) 밖({@code build/kardx/index})에 만든다.
 * test 태스크가 실행 전에 생성 문서 디렉터리를 비우므로, 그 안에 두면 테스트를 다시 돌릴 때마다 출력이 지워져
 * 문서 내용이 같아도 index 가 다시 실행된다. index 안의 링크({@code ./<문서>.html})는 {@code apiDocsAsciidocSources}
 * 가 index 와 생성 문서를 같은 디렉터리로 모으므로 그대로 맞는다.
 */
public final class IndexTasks {
    /** 테스트가 클래스별 문서와 함께 기록을 남기는 디렉터리 (생성 문서 디렉터리 기준). core {@code DocumentRecord.DIRECTORY} 와 같다. */
    static final String RECORDS_DIR = ".restdocs-documents";

    private IndexTasks() {
    }

    public static TaskProvider<JavaExec> registerIndex(
        Project project, KardxExtension extension, TaskProvider<Test> test, FileCollection testClasspath) {
        KardxExtension.Index spec = extension.getIndex();
        Provider<RegularFile> output = indexFile(project);
        // CLI 는 테스트 문서 중 맨 위 디렉터리의 <문서>.adoc 과, 테스트가 함께 남긴 문서 기록만 읽는다
        ConfigurableFileTree documents = project.fileTree(extension.getAsciidocDirectory(),
            tree -> tree.include("*.adoc", RECORDS_DIR + "/*.properties"));

        return project.getTasks().register(PluginConstants.INDEX_TASK, JavaExec.class, task -> {
            task.setGroup(PluginConstants.GROUP);
            task.setDescription("Writes index.adoc that links every documented test class.");
            task.dependsOn(test);
            // core 의 CLI 가 테스트 런타임 클래스패스에 있다. packageName 을 주면 테스트 클래스를 훑는다
            task.setClasspath(testClasspath);
            // core 의 asciidoc/DocumentationIndexCli.kt (@file:JvmName). 옵션 이름도 그 파일의 OPTIONS 와 맞춘다
            task.getMainClass().set("io.github.kardx.asciidoc.DocumentationIndexCli");
            DocsLanguageSetup.passTo(task, extension);
            DirectoryProperty asciidoc = extension.getAsciidocDirectory();

            task.onlyIf("index.packageName and index.baseClass are both set or both unset", t -> {
                boolean consistent = isConsistent(spec);

                if (!consistent) {
                    t.getLogger().warn("kardx: skipped {} — set both kardx.index.packageName and "
                        + "index.baseClass, or neither (the index then lists every documented test class).",
                        PluginConstants.INDEX_TASK);
                }

                return consistent;
            });
            task.onlyIf("a test class wrote a document (when index.packageName is not set)", t -> {
                boolean found = isScan(spec) || hasRecords(asciidoc.get().getAsFile());

                if (!found) {
                    t.getLogger().lifecycle("kardx: skipped {} — no test class wrote a document to {} yet, "
                        + "so there is nothing to list.", PluginConstants.INDEX_TASK, asciidoc.get().getAsFile());
                }

                return found;
            });
            task.getInputs()
                .files(documents)
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .withPropertyName("documents")
                .ignoreEmptyDirectories();
            // 템플릿 파일은 없어도 된다(내장 템플릿). file(...).optional() 은 값이 없을 때만 봐주고 파일이 없으면 검증에서
            // 실패하므로 files(...) 로 넣는다
            task.getInputs()
                .files(spec.getTemplate())
                .withPathSensitivity(PathSensitivity.NONE)
                .withPropertyName("template");
            task.getInputs()
                .property("title", spec.getTitle());
            task.getInputs()
                .property("packageName", spec.getPackageName())
                .optional(true);
            task.getInputs()
                .property("baseClass", spec.getBaseClass())
                .optional(true);
            task.getOutputs()
                .file(output)
                .withPropertyName("index");
            task.getArgumentProviders().add(() -> arguments(extension, output.get().getAsFile()));
        });
    }

    /**
     * CLI 인자. 템플릿 파일이 없으면 내장 템플릿({@code --title} 이 제목)을, 패키지·기본 클래스가 없으면 테스트가 남긴 문서 기록을
     * 쓴다.
     */
    private static List<String> arguments(KardxExtension extension, File output) {
        KardxExtension.Index spec = extension.getIndex();
        File template = spec.getTemplate().get().getAsFile();
        List<String> arguments = new ArrayList<>(List.of(
            "--output", output.getAbsolutePath(),
            "--documents", extension.getAsciidocDirectory().get().getAsFile().getAbsolutePath(),
            "--title", spec.getTitle().get()));

        if (template.isFile()) {
            arguments.addAll(List.of("--template", template.getAbsolutePath()));
        }

        if (spec.getPackageName().isPresent() && spec.getBaseClass().isPresent()) {
            arguments.addAll(List.of(
                "--package", spec.getPackageName().get(),
                "--base-class", spec.getBaseClass().get()));
        }

        return arguments;
    }

    /** 패키지와 기본 클래스를 함께 정했거나 둘 다 정하지 않았는가. 하나만 정하면 index 를 건너뛴다. */
    static boolean isConsistent(KardxExtension.Index spec) {
        return spec.getPackageName().isPresent() == spec.getBaseClass().isPresent();
    }

    /** 패키지를 훑는(예전) 방식인가. 이때는 문서가 없어도 템플릿만으로 index 를 만든다. */
    private static boolean isScan(KardxExtension.Index spec) {
        return spec.getPackageName().isPresent() && spec.getBaseClass().isPresent();
    }

    /** 테스트가 문서와 함께 남긴 기록이 있는가. */
    private static boolean hasRecords(File asciidocDirectory) {
        File[] records = new File(asciidocDirectory, RECORDS_DIR).listFiles(f -> f.getName().endsWith(".properties"));

        return records != null && records.length > 0;
    }

    /** {@code apiDocsIndex} 가 실행되는가 (onlyIf 와 같은 조건). 아니면 지난 실행이 남긴 index 를 쓰지 않는다. */
    private static boolean runs(KardxExtension.Index spec, File asciidocDirectory) {
        return isConsistent(spec) && (isScan(spec) || hasRecords(asciidocDirectory));
    }

    public static TaskProvider<Sync> registerAsciidocSources(
        Project project, KardxExtension extension, TaskProvider<Test> test, TaskProvider<JavaExec> index) {
        return project.getTasks().register(PluginConstants.ASCIIDOC_SOURCES_TASK, Sync.class, task -> {
            task.setDescription("Collects hand-written and generated AsciiDoc into one Asciidoctor input directory.");
            task.dependsOn(test, index);
            task.from(extension.getAsciidocSourceDirectory(), copy -> copy.exclude(t ->
                t.getFile().equals(extension.getIndex().getTemplate().get().getAsFile())));
            // 예전 버전이 생성 문서 디렉터리에 남긴 index.adoc 이 새 index 와 겹치지 않게 뺀다. 문서 기록은 문서가 아니다
            task.from(extension.getAsciidocDirectory(), copy -> copy.exclude(PluginConstants.INDEX_FILE,
                RECORDS_DIR + "/**"));

            // index 가 건너뛰어(onlyIf) 실행되지 않으면 예전 실행이 남긴 파일을 넣지 않는다
            KardxExtension.Index spec = extension.getIndex();
            task.from(indexFile(project), copy -> copy.include(t ->
                runs(spec, extension.getAsciidocDirectory().get().getAsFile())));
            task.into(project.getLayout().getBuildDirectory().dir(PluginConstants.ASCIIDOC_SOURCES_DIR));
        });
    }

    /** {@code apiDocsIndex} 가 만드는 index 파일. 생성 문서 디렉터리 밖에 둔다. */
    static Provider<RegularFile> indexFile(Project project) {
        return project.getLayout()
            .getBuildDirectory()
            .file(PluginConstants.INDEX_DIR + "/" + PluginConstants.INDEX_FILE);
    }
}
