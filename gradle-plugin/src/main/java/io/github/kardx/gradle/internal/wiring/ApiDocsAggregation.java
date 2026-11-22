package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.ApiDocsAggregateTask;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.GradleVersion;

/**
 * 여러 프로젝트의 문서를 모으는 연결. 프로젝트끼리는 Gradle 의 variant 로만 이어진다 (다른 프로젝트의 태스크·디렉터리를
 * 직접 참조하지 않는다).
 * <ul>
 *   <li>문서를 만드는 프로젝트(Java 플러그인이 있는 곳): {@code restdocsApiDocsElements} 로 apiDocs 결과 디렉터리를 내보낸다.
 *       속성은 {@code Category=documentation}, {@code DocsType=restdocs-api-docs}</li>
 *   <li>모으는 프로젝트(어디든): {@code dependencies { restdocsAggregation(project(":order")) }} 로 선언하면
 *       {@code apiDocsAggregate} 가 그 디렉터리를 받아 모은다 ({@link ApiDocsAggregateTask})</li>
 * </ul>
 */
public final class ApiDocsAggregation {
    private static final GradleVersion GRADLE_9 = GradleVersion.version("9.0");

    private ApiDocsAggregation() {
    }

    /** 모으는 쪽. 선언용·해석용 configuration 과 {@code apiDocsAggregate} 를 만든다. 선언이 없으면 아무 일도 하지 않는다. */
    public static void configureAggregator(Project project, KardxExtension extension) {
        Configuration declared = project.getConfigurations().create(PluginConstants.AGGREGATION_CONFIGURATION, c -> {
            c.setDescription("Projects whose API docs apiDocsAggregate collects.");
            c.setCanBeConsumed(false);
            c.setCanBeResolved(false);
        });

        Configuration docs = project.getConfigurations().create(PluginConstants.AGGREGATION_DOCS_CONFIGURATION, c -> {
            c.setDescription("API docs directories of the projects declared in restdocsAggregation.");
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            hideFromAssemble(c);
            c.extendsFrom(declared);
            attributes(project, c.getAttributes());
        });

        Provider<List<ResolvedArtifactResult>> artifacts = docs.getIncoming()
            .getArtifacts()
            .getResolvedArtifacts()
            .map(ApiDocsAggregation::byProjectPath);
        KardxExtension.Aggregate spec = extension.getAggregate();
        KardxExtension.OpenApiViewer viewer = extension.getOpenApiViewer();

        project.getTasks().register(PluginConstants.AGGREGATE_TASK, ApiDocsAggregateTask.class, task -> {
            task.setGroup(PluginConstants.GROUP);
            task.setDescription("Collects API docs of the projects declared in restdocsAggregation into one index.");
            task.getDocs().from(docs.getIncoming().getArtifacts().getArtifactFiles());
            task.getProjects().set(artifacts.map(list -> list.stream()
                .map(artifact -> projectPath(artifact.getId().getComponentIdentifier()))
                .toList()));
            task.getDirectories().set(artifacts.map(list -> list.stream()
                .map(ResolvedArtifactResult::getFile)
                .toList()));
            task.getTitle().set(spec.getTitle());
            task.getVersion().set(spec.getVersion());
            task.getInfoDescription().set(spec.getDescription());
            task.getViewerType().set(viewer.getEnabled().map(on -> on ? viewer.getType().get() : null));
            task.getViewerFileName().set(viewer.getEnabled().map(on -> on ? OpenApiViewerTasks.fileName(viewer) : null));
            task.getLanguage().set(DocsLanguageSetup.effective(extension));
            task.getOutputDirectory().set(spec.getOutputDirectory());
            task.getOutputGuard().set(OutputGuards.of(project, extension));
            // ./gradlew apiDocsAggregate 는 모든 프로젝트의 같은 이름 태스크를 고르므로, 선언이 없는 프로젝트에서는 건너뛴다
            task.onlyIf("restdocsAggregation has projects",
                t -> !((ApiDocsAggregateTask) t).getProjects().get().isEmpty());
        });
    }

    /** 문서를 만드는 쪽. apiDocs 결과 디렉터리를 {@code restdocsApiDocsElements} 로 내보낸다 (해석될 때만 apiDocs 가 돈다). */
    public static void configureProducer(
        Project project, KardxExtension extension, TaskProvider<Sync> apiDocs) {
        project.getConfigurations().create(PluginConstants.API_DOCS_ELEMENTS_CONFIGURATION, c -> {
            c.setDescription("API docs (apiDocs output) for apiDocsAggregate in another project.");
            c.setCanBeConsumed(true);
            c.setCanBeResolved(false);
            hideFromAssemble(c);
            attributes(project, c.getAttributes());
            c.getOutgoing().artifact(extension.getOutputDirectory(), artifact -> {
                artifact.setType("directory");
                artifact.builtBy(apiDocs);
            });
        });
    }

    /**
     * Gradle 9 보다 앞 버전에서 [configuration] 의 산출물을 {@code assemble} 이 만들지 않게 한다. Gradle 9 부터는
     * {@code visible} 이 아무 일도 하지 않고 지원 중단(deprecated) 경고만 내므로 부르지 않는다.
     */
    private static void hideFromAssemble(Configuration configuration) {
        if (GradleVersion.current().getBaseVersion().compareTo(GRADLE_9) < 0) {
            configuration.setVisible(false);
        }
    }

    private static void attributes(Project project, AttributeContainer attributes) {
        attributes.attribute(Category.CATEGORY_ATTRIBUTE,
            project.getObjects().named(Category.class, Category.DOCUMENTATION));
        attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
            project.getObjects().named(DocsType.class, PluginConstants.API_DOCS_TYPE));
    }

    private static List<ResolvedArtifactResult> byProjectPath(Set<ResolvedArtifactResult> artifacts) {
        return artifacts.stream()
            .sorted(Comparator.comparing(artifact -> projectPath(artifact.getId().getComponentIdentifier())))
            .toList();
    }

    private static String projectPath(ComponentIdentifier id) {
        return id instanceof ProjectComponentIdentifier project ? project.getProjectPath() : id.getDisplayName();
    }
}
