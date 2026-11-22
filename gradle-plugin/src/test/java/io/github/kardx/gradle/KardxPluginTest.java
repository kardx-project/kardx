package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.internal.wiring.OpenApiIntegration;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 플러그인을 실제 프로젝트에 적용해 태스크 연결을 확인한다. */
class KardxPluginTest {

    @Test
    void registersDocumentationTasksWithJavaPlugin(@TempDir File dir) {
        Project project = javaProject(dir);
        evaluate(project);

        for (String name : List.of(PluginConstants.INDEX_TASK, PluginConstants.ASYNC_API_TASK,
            PluginConstants.API_DOCS_TASK)) {
            Task task = project.getTasks().findByName(name);
            assertNotNull(task, name);
            assertEquals(PluginConstants.GROUP, task.getGroup(), name);
            assertNotNull(task.getDescription(), name);
        }

        // 사용자가 직접 부르지 않는 중간 태스크는 그룹이 없어 ./gradlew tasks 에 나오지 않는다
        for (String name : List.of(PluginConstants.ASCIIDOC_SOURCES_TASK, PluginConstants.APP_DOCS_TASK)) {
            Task task = project.getTasks().findByName(name);
            assertNotNull(task, name);
            assertNull(task.getGroup(), name);
        }

        assertNotNull(project.getExtensions().findByName(PluginConstants.EXTENSION_NAME));
    }

    @Test
    void asyncApiClasspathLeavesOutCompiledAppAndTestClasses(@TempDir File dir) {
        Project project = javaProject(dir);
        evaluate(project);

        JavaExec asyncApi = (JavaExec) project.getTasks().getByName(PluginConstants.ASYNC_API_TASK);
        Set<File> classpath = asyncApi.getClasspath().getFiles();
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        for (String sourceSet : List.of("main", "test")) {
            for (File output : sourceSets.getByName(sourceSet).getOutput()) {
                assertFalse(classpath.contains(output), sourceSet + " output on the classpath: " + output);
            }
        }

        JavaExec index = (JavaExec) project.getTasks().getByName(PluginConstants.INDEX_TASK);
        assertTrue(index.getClasspath().getFiles().containsAll(sourceSets.getByName("test").getOutput().getFiles()),
            "index 는 테스트 클래스를 훑으므로 테스트 출력이 필요하다");
    }

    @Test
    void indexIsWrittenOutsideTheDirectoryTestClears(@TempDir File dir) {
        Project project = javaProject(dir);
        KardxExtension extension = project.getExtensions().getByType(KardxExtension.class);
        extension.getIndex().getPackageName().set("com.example");
        extension.getIndex().getBaseClass().set("com.example.DocsTest");
        evaluate(project);

        JavaExec index = (JavaExec) project.getTasks().getByName(PluginConstants.INDEX_TASK);
        File build = project.getLayout().getBuildDirectory().get().getAsFile();
        File output = new File(build, "kardx/index/index.adoc");
        assertEquals(Set.of(output), index.getOutputs().getFiles().getFiles());
        File asciidoc = extension.getAsciidocDirectory().get().getAsFile();
        assertFalse(output.toPath().startsWith(asciidoc.toPath()), "test 가 비우는 디렉터리 밖이어야 한다");

        List<String> args = index.getArgumentProviders()
            .stream()
            .flatMap(p -> StreamSupport.stream(p.asArguments().spliterator(), false))
            .toList();
        assertEquals(output.getAbsolutePath(), args.get(args.indexOf("--output") + 1));
        assertEquals(asciidoc.getAbsolutePath(), args.get(args.indexOf("--documents") + 1));

        Task sources = project.getTasks().getByName(PluginConstants.ASCIIDOC_SOURCES_TASK);
        assertTrue(sources.getTaskDependencies().getDependencies(sources).contains(index));
    }

    @Test
    void asciidocSourcesCollectsIndexNextToGeneratedDocuments(@TempDir File dir) throws Exception {
        Project project = javaProject(dir);
        KardxExtension extension = project.getExtensions().getByType(KardxExtension.class);
        extension.getIndex().getPackageName().set("com.example");
        extension.getIndex().getBaseClass().set("com.example.DocsTest");
        evaluate(project);

        File build = project.getLayout().getBuildDirectory().get().getAsFile();
        File asciidoc = extension.getAsciidocDirectory().get().getAsFile();
        Files.createDirectories(asciidoc.toPath());
        Files.writeString(new File(asciidoc, "member-docs.adoc").toPath(), "= 회원");
        Files.writeString(new File(asciidoc, "index.adoc").toPath(), "예전 위치의 index");
        File index = new File(build, "kardx/index/index.adoc");
        Files.createDirectories(index.toPath().getParent());
        Files.writeString(index.toPath(), "새 index");

        Sync sources = (Sync) project.getTasks().getByName(PluginConstants.ASCIIDOC_SOURCES_TASK);
        Set<String> collected = sources.getSource().getFiles()
            .stream()
            .map(f -> build.toPath().relativize(f.toPath()).toString())
            .collect(Collectors.toSet());

        assertEquals(Set.of("generated-asciidoc/member-docs.adoc", "kardx/index/index.adoc"), collected);
    }

    @Test
    void indexNeedsNoSettings(@TempDir File dir) throws Exception {
        Project project = javaProject(dir);
        KardxExtension extension = project.getExtensions().getByType(KardxExtension.class);
        evaluate(project);

        JavaExec index = (JavaExec) project.getTasks().getByName(PluginConstants.INDEX_TASK);
        List<String> args = arguments(index);
        assertFalse(args.contains("--package"), "패키지를 정하지 않으면 훑지 않는다: " + args);
        assertFalse(args.contains("--template"), "템플릿 파일이 없으면 내장 템플릿: " + args);
        assertEquals(project.getName(), args.get(args.indexOf("--title") + 1));

        File template = extension.getIndex().getTemplate().get().getAsFile();
        Files.createDirectories(template.toPath().getParent());
        Files.writeString(template.toPath(), "= API\n");
        assertEquals(template.getAbsolutePath(), arguments(index).get(arguments(index).indexOf("--template") + 1));

        // 테스트가 남긴 index 항목은 Asciidoctor 입력에 넣지 않고, index 는 설정 없이도 넣는다
        File build = project.getLayout().getBuildDirectory().get().getAsFile();
        File asciidoc = extension.getAsciidocDirectory().get().getAsFile();
        Files.createDirectories(new File(asciidoc, ".restdocs-documents").toPath());
        Files.writeString(new File(asciidoc, "member-docs.adoc").toPath(), "= 회원");
        Files.writeString(new File(asciidoc, ".restdocs-documents/member-docs.properties").toPath(), "document=member-docs");
        File output = new File(build, "kardx/index/index.adoc");
        Files.createDirectories(output.toPath().getParent());
        Files.writeString(output.toPath(), "index");

        Sync sources = (Sync) project.getTasks().getByName(PluginConstants.ASCIIDOC_SOURCES_TASK);
        Set<String> collected = sources.getSource().getFiles()
            .stream()
            .map(f -> build.toPath().relativize(f.toPath()).toString())
            .collect(Collectors.toSet());

        assertEquals(Set.of("generated-asciidoc/member-docs.adoc", "kardx/index/index.adoc"), collected);
    }

    @Test
    void indexIsSkippedWhenOnlyPackageOrBaseClassIsSet(@TempDir File dir) {
        Project project = javaProject(dir);
        KardxExtension extension = project.getExtensions().getByType(KardxExtension.class);
        extension.getIndex().getPackageName().set("com.example");
        evaluate(project);

        JavaExec index = (JavaExec) project.getTasks().getByName(PluginConstants.INDEX_TASK);
        assertFalse(index.getOnlyIf().isSatisfiedBy(index));

        extension.getIndex().getBaseClass().set("com.example.DocsTest");
        assertTrue(index.getOnlyIf().isSatisfiedBy(index));
        assertTrue(arguments(index).contains("--package"));
    }

    private static List<String> arguments(JavaExec task) {
        return task.getArgumentProviders()
            .stream()
            .flatMap(p -> StreamSupport.stream(p.asArguments().spliterator(), false))
            .toList();
    }

    @Test
    void openApiTasksDoNotPullInCheck(@TempDir File dir) {
        Project project = javaProject(dir);
        project.getPluginManager().apply(PluginConstants.EPAGES_PLUGIN);
        evaluate(project);

        for (String name : PluginConstants.EPAGES_TASKS) {
            Task task = project.getTasks().getByName(name);
            Set<String> dependencies = task.getTaskDependencies().getDependencies(task)
                .stream()
                .map(Task::getName)
                .collect(Collectors.toSet());

            assertFalse(dependencies.contains(PluginConstants.CHECK_TASK), name + " -> " + dependencies);
            assertTrue(dependencies.contains(PluginConstants.OPEN_API_SNIPPETS_TASK), name + " -> " + dependencies);
        }

        Task snippets = project.getTasks().getByName(PluginConstants.OPEN_API_SNIPPETS_TASK);
        assertTrue(
            snippets.getTaskDependencies().getDependencies(snippets)
                .stream()
                .anyMatch(t -> t.getName().equals(PluginConstants.TEST_TASK)),
            "test 는 조각 복사를 통해 계속 먼저 돈다");
        assertNull(snippets.getGroup(), "중간 태스크라 ./gradlew tasks 에 나오지 않는다");
    }

    @Test
    void epagesReadsTheSnippetCopyFromTheBuildDirectorySetAfterThePluginsBlock(@TempDir File dir) {
        Project project = javaProject(dir);
        project.getPluginManager().apply(PluginConstants.EPAGES_PLUGIN);
        // 빌드 스크립트 본문에서 plugins { } 뒤에 바꾼 것처럼
        project.getLayout().getBuildDirectory().set(new File(dir, "out"));
        evaluate(project);

        String expected = new File(dir, "out/" + PluginConstants.OPEN_API_SNIPPETS_DIR).getAbsolutePath();
        Sync copy = (Sync) project.getTasks().getByName(PluginConstants.OPEN_API_SNIPPETS_TASK);

        assertEquals(expected, copy.getDestinationDir().getAbsolutePath());

        for (String name : PluginConstants.EPAGES_TASKS) {
            FakeApiSpecTask task = (FakeApiSpecTask) project.getTasks().getByName(name);
            FakeExtension extension = (FakeExtension) project.getExtensions().getByName(name);

            assertEquals(expected, task.getSnippetsDirectory(), name);
            assertEquals(expected, extension.getSnippetsDirectory(), name);
        }
    }

    /** 후처리가 읽는 설정은 모두 openapi3 의 입력이다 (출력을 선언하는 epages 에서도 설정이 바뀌면 다시 돈다). */
    @Test
    void openapi3DeclaresEveryPostProcessingSetting(@TempDir File dir) {
        Project project = javaProject(dir);
        project.getPluginManager().apply(PluginConstants.EPAGES_PLUGIN);
        evaluate(project);

        Set<String> inputs = project.getTasks()
            .getByName(PluginConstants.OPENAPI3_TASK)
            .getInputs()
            .getProperties()
            .keySet();

        assertTrue(inputs.containsAll(List.of("restdocsGlobalHeaders", "restdocsCommonResponses",
            "restdocsCommonResponseSchema", "restdocsOAuth2Flows", "restdocsApiVersionSplit")), inputs.toString());
    }

    @Test
    void withoutCheckKeepsOtherDependencies(@TempDir File dir) {
        Project project = javaProject(dir);
        Task other = project.getTasks().getByName(PluginConstants.CLASSES_TASK);
        List<Object> kept = OpenApiIntegration.withoutCheck(List.of(
            PluginConstants.CHECK_TASK,
            PluginConstants.TEST_TASK,
            project.getTasks().getByName(PluginConstants.CHECK_TASK),
            project.getTasks().named(PluginConstants.CHECK_TASK),
            other));

        assertEquals(List.of(PluginConstants.TEST_TASK, other), kept);
    }

    @Test
    void newOptionsAreOffByDefault(@TempDir File dir) {
        Project project = javaProject(dir);
        project.getPluginManager().apply(PluginConstants.EPAGES_PLUGIN);
        evaluate(project);

        for (String name : List.of(PluginConstants.OPEN_API_CHECK_TASK, PluginConstants.OPEN_API_BASELINE_TASK,
            PluginConstants.OPEN_API_VIEWER_TASK, PluginConstants.AGGREGATE_TASK, PluginConstants.STUBS_JAR_TASK)) {
            Task task = project.getTasks().findByName(name);
            assertNotNull(task, name);
            assertEquals(PluginConstants.GROUP, task.getGroup(), name);
        }

        Set<String> apiDocs = dependencyNames(project, PluginConstants.API_DOCS_TASK);

        assertFalse(apiDocs.contains(PluginConstants.OPEN_API_VIEWER_TASK), apiDocs.toString());
        assertFalse(apiDocs.contains(PluginConstants.POSTMAN_TASK), apiDocs.toString());
        assertFalse(dependencyNames(project, PluginConstants.CHECK_TASK).contains(PluginConstants.OPEN_API_CHECK_TASK));
        assertFalse(testJvmArguments(project).stream().anyMatch(arg -> arg.contains("wire-mock")));
    }

    @Test
    void testJvmAlwaysLearnsThePluginLanguageButOnlyGetsTheLanguageWhenSet(@TempDir File dir) {
        Project unset = javaProject(new File(dir, "unset"));
        evaluate(unset);

        assertTrue(testJvmArguments(unset).contains("-Dkardx.plugin-language=en"), "기본값을 알린다");
        assertFalse(testJvmArguments(unset).stream().anyMatch(arg -> arg.startsWith("-Dkardx.language=")),
            "정하지 않았으면 앱 설정(yml)이 정하게 둔다");

        Project korean = javaProject(new File(dir, "korean"));
        korean.getExtensions()
            .getByType(KardxExtension.class)
            .getLanguage()
            .set("KO");
        evaluate(korean);

        assertTrue(testJvmArguments(korean).containsAll(
            List.of("-Dkardx.language=ko", "-Dkardx.plugin-language=ko")));
    }

    @Test
    void enabledOptionsJoinTheirTasks(@TempDir File dir) {
        Project project = javaProject(dir);
        project.getPluginManager().apply(PluginConstants.EPAGES_PLUGIN);
        KardxExtension extension = project.getExtensions().getByType(KardxExtension.class);
        extension.getOpenApiViewer().getEnabled().set(true);
        extension.getPostmanCollection().getEnabled().set(true);
        extension.getOpenApiCheck().getRunOnCheck().set(true);
        extension.getWireMock().getEnabled().set(true);
        evaluate(project);

        Set<String> apiDocs = dependencyNames(project, PluginConstants.API_DOCS_TASK);

        assertTrue(apiDocs.contains(PluginConstants.OPEN_API_VIEWER_TASK), apiDocs.toString());
        assertTrue(apiDocs.contains(PluginConstants.POSTMAN_TASK), apiDocs.toString());
        assertTrue(dependencyNames(project, PluginConstants.CHECK_TASK).contains(PluginConstants.OPEN_API_CHECK_TASK));
        assertTrue(dependencyNames(project, PluginConstants.OPEN_API_CHECK_TASK)
            .contains(PluginConstants.OPENAPI3_TASK));
        assertTrue(testJvmArguments(project).contains("-Dkardx.wire-mock-stubs=true"));
        assertTrue(extension.getOpenApiCheck().getBaseline().get().getAsFile().toPath()
            .endsWith("docs/openapi/openapi3.yaml"), "기준 스펙 기본 위치");
    }

    @Test
    void exportTasksRunOnlyWhenAskedAndDependOnSelectedOutputs(@TempDir File dir) {
        Project project = javaProject(dir);
        project.getPluginManager().apply(PluginConstants.EPAGES_PLUGIN);
        KardxExtension extension = project.getExtensions().getByType(KardxExtension.class);
        extension.getExport().getHtml().set(true);
        evaluate(project);

        for (String name : List.of(PluginConstants.EXPORT_TASK, PluginConstants.UPLOAD_TASK)) {
            Task task = project.getTasks().getByName(name);

            assertEquals(PluginConstants.GROUP, task.getGroup(), name);
            assertNotNull(task.getDescription(), name);

            for (String trigger : List.of("build", PluginConstants.CHECK_TASK, PluginConstants.API_DOCS_TASK)) {
                assertFalse(dependencyNames(project, trigger).contains(name), trigger + " -> " + name);
            }
        }

        assertEquals(Set.of(PluginConstants.OPENAPI3_TASK, PluginConstants.API_DOCS_TASK),
            dependencyNames(project, PluginConstants.EXPORT_TASK), "고른 결과(OpenAPI, HTML)를 만드는 태스크에만 의존한다");
        assertEquals(Set.of(PluginConstants.OPENAPI3_TASK), dependencyNames(project, PluginConstants.UPLOAD_TASK));
        assertNull(project.getTasks().findByName(PluginConstants.PUBLICATION_SPECS_TASK), "maven-publish 가 없으면 없다");
    }

    @Test
    void aggregationNeedsNoJavaPluginAndProducersShareDocsAsVariant(@TempDir File dir) {
        Project root = ProjectBuilder.builder()
            .withProjectDir(dir)
            .build();
        root.getPluginManager().apply(KardxPlugin.class);
        evaluate(root);

        assertNotNull(root.getTasks().findByName(PluginConstants.AGGREGATE_TASK));
        assertNotNull(root.getConfigurations().findByName(PluginConstants.AGGREGATION_CONFIGURATION));
        assertNull(root.getConfigurations().findByName(PluginConstants.API_DOCS_ELEMENTS_CONFIGURATION),
            "Java 플러그인이 없으면 내보낼 문서가 없다");

        Project producer = javaProject(new File(dir, "producer"));
        Configuration elements = producer.getConfigurations()
            .getByName(PluginConstants.API_DOCS_ELEMENTS_CONFIGURATION);

        assertTrue(elements.isCanBeConsumed());
        assertFalse(elements.isCanBeResolved());
        assertEquals(PluginConstants.API_DOCS_TYPE,
            elements.getAttributes().getAttribute(DocsType.DOCS_TYPE_ATTRIBUTE).getName());
    }

    private static Set<String> dependencyNames(Project project, String taskName) {
        Task task = project.getTasks().getByName(taskName);

        return task.getTaskDependencies().getDependencies(task)
            .stream()
            .map(Task::getName)
            .collect(Collectors.toSet());
    }

    private static List<String> testJvmArguments(Project project) {
        org.gradle.api.tasks.testing.Test test = (org.gradle.api.tasks.testing.Test) project.getTasks()
            .getByName(PluginConstants.TEST_TASK);

        return test.getJvmArgumentProviders()
            .stream()
            .flatMap(provider -> StreamSupport.stream(provider.asArguments().spliterator(), false))
            .toList();
    }

    private static Project javaProject(File dir) {
        Project project = ProjectBuilder.builder()
            .withProjectDir(dir)
            .build();
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(KardxPlugin.class);
        // 저장소가 없는 픽스처다. 라이브러리 자동 추가는 LibraryDependenciesFunctionalTest 가 본다
        project.getExtensions()
            .getByType(KardxExtension.class)
            .getAddDependencies()
            .set(false);

        return project;
    }

    private static void evaluate(Project project) {
        ((ProjectInternal) project).evaluate();
    }

    /**
     * epages(com.epages.restdocs-api-spec)처럼 동작하는 가짜 플러그인: 확장을 만들고, afterEvaluate 에서 태스크를 등록하며
     * {@code dependsOn("check")} 를 건다. 연동은 플러그인 id 로 찾으므로 테스트 리소스
     * {@code META-INF/gradle-plugins/com.epages.restdocs-api-spec.properties} 로 같은 id 를 준다.
     */
    @SuppressWarnings("unused") // 테스트 리소스의 플러그인 id 로 Gradle 이 만든다
    public static class FakeEpagesPlugin implements Plugin<Project> {
        @Override
        public void apply(@Nonnull Project project) {
            for (String name : PluginConstants.EPAGES_TASKS) {
                project.getExtensions().create(name, FakeExtension.class);
            }

            project.afterEvaluate(p -> {
                for (String name : PluginConstants.EPAGES_TASKS) {
                    FakeExtension extension = (FakeExtension) p.getExtensions().getByName(name);

                    p.getTasks()
                        .register(name, FakeApiSpecTask.class)
                        .configure(t -> {
                            t.dependsOn(PluginConstants.CHECK_TASK);
                            // epages 도 afterEvaluate 에서 확장의 outputDirectory 를 태스크로 옮긴다
                            t.setOutputDirectory(extension.getOutputDirectory());
                        });
                }
            });
        }
    }

    /** epages 확장처럼 일반 필드와 epages 의 기본값을 갖는다. */
    @SuppressWarnings("unused") // 연동이 리플렉션으로 setSnippetsDirectory·setFormat 등을 부른다
    public static class FakeExtension {
        private String snippetsDirectory;
        private String format = "json";
        private String title = "API documentation";
        private String version = "1.0.0";
        private String server = "http://localhost";
        private String outputDirectory;

        public String getOutputDirectory() {
            return outputDirectory;
        }

        public void setOutputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        public String getSnippetsDirectory() {
            return snippetsDirectory;
        }

        public void setSnippetsDirectory(String snippetsDirectory) {
            this.snippetsDirectory = snippetsDirectory;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getServer() {
            return server;
        }

        public void setServer(String server) {
            this.server = server;
        }
    }

    @SuppressWarnings("unused") // 연동이 리플렉션으로 setSnippetsDirectory 를 부른다
    public abstract static class FakeApiSpecTask extends DefaultTask {
        private String snippetsDirectory;
        private String outputDirectory;

        /** 설정하지 않으면 null: 연동은 epages 기본값 build/api-spec 을 쓴다. */
        @Internal
        public String getOutputDirectory() {
            return outputDirectory;
        }

        public void setOutputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        @Internal
        public String getSnippetsDirectory() {
            return snippetsDirectory;
        }

        public void setSnippetsDirectory(String snippetsDirectory) {
            this.snippetsDirectory = snippetsDirectory;
        }
    }
}
