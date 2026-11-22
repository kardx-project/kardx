package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.SourceDocsTask;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * {@code apiDocsSourceDocs}({@link SourceDocsTask})를 등록하고 {@code sourceDocs.enabled} 일 때만 test 에 연결한다 (실험 기능,
 * 기본 꺼짐). 꺼져 있으면 test 는 태스크에 의존하지 않고 시스템 속성도 받지 않으며, 태스크를 직접 불러도 건너뛴다.
 * <ul>
 *   <li>읽는 소스: 이 프로젝트의 main 소스 세트(Kotlin·Java)와, {@code includeProjectDependencies} 이면 테스트 런타임
 *       클래스패스의 {@code project(…)} 의존성이 내보내는 main 소스 디렉터리. 후자는 java 플러그인이 만드는
 *       {@code mainSourceElements} 변형(verification / main-sources)을 artifact view 로 다시 골라 받는다 (jacoco 보고서 모으기와
 *       같은 방식). 해석은 실행할 때 하므로 configuration cache 와 맞고, 그 변형이 없는 프로젝트는 조용히 빠진다</li>
 *   <li>test 는 이 태스크에 의존하고 색인 경로를 시스템 속성으로 받는다 ({@link SourceDocsArgument})</li>
 * </ul>
 */
public final class SourceDocsTasks {
    private static final List<String> SOURCE_PATTERNS = List.of("**/*.kt", "**/*.java");

    private SourceDocsTasks() {
    }

    public static void configure(Project project, KardxExtension extension, TaskProvider<Test> test) {
        KardxExtension.SourceDocs settings = extension.getSourceDocs();
        Provider<Boolean> enabled = settings.getEnabled();
        FileCollection dependencySources = dependencySources(project);
        FileCollection none = project.files();

        TaskProvider<SourceDocsTask> task = project.getTasks()
            .register(PluginConstants.SOURCE_DOCS_TASK, SourceDocsTask.class, t -> {
                t.setGroup(PluginConstants.GROUP);
                t.setDescription("Collects KDoc and Javadoc of the main sources into the description index used by "
                    + "the documentation tests.");
                t.getSources().from(mainSources(project));
                t.getSources().from(settings.getIncludeProjectDependencies()
                    .map(include -> include ? dependencySources : none));
                t.getFieldDocs().set(settings.getFieldDocs());
                t.getEnumDocs().set(settings.getEnumDocs());
                t.getSchemaDescriptions().set(settings.getSchemaDescriptions());
                t.getParamDocs().set(settings.getParamDocs());
                t.getMethodDocs().set(settings.getMethodDocs());
                t.onlyIf("sourceDocs.enabled is true", spec -> enabled.get());
                t.getOutputFile().set(project.getLayout().getBuildDirectory()
                    .file(PluginConstants.SOURCE_DOCS_DIR + "/" + PluginConstants.SOURCE_DOCS_FILE));
            });
        Provider<RegularFile> index = enabled
            .flatMap(on -> on
                ? task.flatMap(SourceDocsTask::getOutputFile)
                : project.getObjects().fileProperty());
        SourceDocsArgument argument = project.getObjects().newInstance(SourceDocsArgument.class);

        argument.getFile().set(index);
        test.configure(t -> {
            t.dependsOn(enabled.map(on -> on ? List.of(task) : List.of()));
            t.getJvmArgumentProviders().add(argument);
        });
    }

    /** 이 프로젝트의 main 소스. Kotlin 플러그인은 kotlin 디렉터리를 {@code allSource} 에 더한다. */
    private static FileCollection mainSources(Project project) {
        SourceSet main = project.getExtensions()
            .getByType(SourceSetContainer.class)
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        return main.getAllSource()
            .matching(pattern -> pattern.include(SOURCE_PATTERNS));
    }

    /** 테스트 런타임 클래스패스의 프로젝트 의존성이 내보내는 main 소스 디렉터리의 Kotlin·Java 파일. */
    private static FileCollection dependencySources(Project project) {
        SourceSet test = project.getExtensions()
            .getByType(SourceSetContainer.class)
            .getByName(SourceSet.TEST_SOURCE_SET_NAME);
        ObjectFactory objects = project.getObjects();
        FileCollection directories = project.getConfigurations()
            .getByName(test.getRuntimeClasspathConfigurationName())
            .getIncoming()
            .artifactView(view -> {
                view.withVariantReselection();
                view.setLenient(true);
                view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
                view.attributes(attributes -> {
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE,
                        objects.named(Category.class, Category.VERIFICATION));
                    attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE,
                        objects.named(VerificationType.class, VerificationType.MAIN_SOURCES));
                });
            })
            .getFiles();

        return directories.getAsFileTree()
            .matching(pattern -> pattern.include(SOURCE_PATTERNS));
    }
}
