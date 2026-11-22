package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.OpenApiInfo;
import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.GradleToolLog;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.tasks.OpenApiCheckTask;
import io.github.kardx.gradle.tasks.SourceDocsTask;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.openapi.OpenApiBodySchemas;
import io.github.kardx.tooling.internal.openapi.OpenApiErrorResponses;
import io.github.kardx.tooling.internal.openapi.OpenApiMetadata;
import io.github.kardx.tooling.internal.openapi.OpenApiRefinements;
import io.github.kardx.tooling.internal.openapi.OpenApiSchemaNames;
import io.github.kardx.tooling.internal.openapi.PostmanCollectionTidy;
import io.github.kardx.tooling.internal.spec.PreviousSpecFiles;
import io.github.kardx.tooling.internal.steps.OpenApiPostProcessing;
import io.github.kardx.tooling.internal.steps.OpenApiSnippets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * epages ({@value PluginConstants#EPAGES_PLUGIN}) 연동.
 * <ul>
 *   <li>{@code openapi3 { }} 의 기본값을 맞춘다: yaml, 프로젝트 이름·버전, server {@code "/"} ({@link OpenApiDefaults})</li>
 *   <li>{@code apiDocsOpenApiSnippets}: OpenAPI 조각(resource.json)을 복사하고 같은 API 테스트 사이 보안 요구사항을 맞춘다.
 *       이름이 같고 구조가 다른 스키마도 여기서 정리한다({@link OpenApiSchemaNames}).
 *       test 의 출력을 고치지 않으므로 다음 빌드에서 테스트가 다시 돌지 않는다</li>
 *   <li>{@code openapi3}·{@code openapi}·{@code postman}: 그 복사본을 읽게 하고, epages 가 거는 {@code check} 의존을 뺀다</li>
 *   <li>{@code openapi3} 뒤에 전역 헤더를 apiKey 보안 스키마로 바꾸고, JSON 이 아닌 본문 스키마를 문자열로 바꾸고
 *       ({@link OpenApiBodySchemas}), 라이브러리가 남긴 타입·제약·multipart·쿠키 정보로 보강하고({@link OpenApiRefinements}),
 *       테스트가 선언한 응답과 공통 응답을 더하고({@link OpenApiErrorResponses}), 문서 메타데이터와 클래스 KDoc 스키마 설명을
 *       넣은 뒤({@link OpenApiMetadata}) 결과를 apiDocs 에 넣는다</li>
 *   <li>{@code apiDocsCheck}·{@code apiDocsBaseline}: 기준 스펙과 비교·갱신 ({@link OpenApiCheckTask})</li>
 *   <li>{@code apiDocsViewer}: 정적 뷰어 페이지 ({@link OpenApiViewerTasks})</li>
 *   <li>{@code apiDocsCoverage}: 문서화 범위 보고서 ({@link CoverageTasks}), {@code apiDocsHttpClient}: IntelliJ HTTP
 *       Client 파일 ({@link HttpClientTasks})</li>
 *   <li>{@code postman}: 가짜 JWT 토큰 예시를 Postman 변수로 바꾸고({@link PostmanCollectionTidy}), 켜면 결과를 apiDocs 에
 *       넣는다</li>
 * </ul>
 */
public final class OpenApiIntegration {
    /** epages 태스크·확장이 스니펫 디렉터리를 받는 setter. */
    private static final String SET_SNIPPETS_DIRECTORY = "setSnippetsDirectory";
    private static final Class<?>[] STRING_PARAMETER = {String.class};

    private OpenApiIntegration() {
    }

    public static void configure(
        Project project, KardxExtension extension, TaskProvider<Test> test, TaskProvider<Sync> apiDocs) {
        project.getPluginManager().withPlugin(PluginConstants.EPAGES_PLUGIN, applied -> {
            OpenApiDefaults.apply(project);

            Provider<Directory> copyDir = project.getLayout().getBuildDirectory()
                .dir(PluginConstants.OPEN_API_SNIPPETS_DIR);
            TaskProvider<Sync> openApiSnippets = registerSnippetsCopy(project, extension, test, copyDir);

            pointEpagesAtCopy(project, extension, openApiSnippets, copyDir);
            addOutputs(project, extension, apiDocs);
            // 문서화한 operation 을 resource.json 에서 읽으므로 epages 가 있을 때만 등록한다
            CoverageTasks.register(project, extension, test, apiDocs);
        });
    }

    /**
     * epages 태스크({@code openapi3}·{@code openapi}·{@code postman})가 스니펫 대신 복사본을 읽게 하고, epages 가 거는
     * {@code check} 의존을 뺀다. afterEvaluate 에서 스펙 후처리와 Postman 다듬기도 건다.
     */
    private static void pointEpagesAtCopy(
        Project project, KardxExtension extension, TaskProvider<Sync> openApiSnippets,
        Provider<Directory> copyDir) {
        List<String> names = PluginConstants.EPAGES_TASKS;

        // 0.20 은 태스크 값을, 0.19 는 확장 값을 읽으므로 둘 다 복사본 경로로 둔다. 둘 다 설정 시점에 정한다.
        // 경로는 쓸 때마다 다시 읽는다: 이 코드는 plugins { } 안에서 도는데, 빌드 스크립트 본문이 그 뒤에
        // layout.buildDirectory 를 바꿀 수 있다
        project.getTasks()
            .matching(t -> names.contains(t.getName()))
            .configureEach(task -> {
                task.dependsOn(openApiSnippets);
                Reflection.invoke(task, SET_SNIPPETS_DIRECTORY, STRING_PARAMETER, absolutePath(copyDir));
            });

        project.afterEvaluate(p -> {
            String copyPath = absolutePath(copyDir);

            for (String name : names) {
                Object spec = p.getExtensions().findByName(name);

                if (spec != null) {
                    Reflection.invoke(spec, SET_SNIPPETS_DIRECTORY, STRING_PARAMETER, copyPath);
                }
            }

            // 0.19 는 자기 afterEvaluate 에서 확장 값(기본 build/generated-snippets)을 태스크에 다시 넣는다.
            // 이 afterEvaluate 는 그 뒤에 돌므로 태스크 값을 한 번 더 복사본 경로로 둔다.
            p.getTasks()
                .matching(t -> names.contains(t.getName()))
                .configureEach(task ->
                    Reflection.invoke(task, SET_SNIPPETS_DIRECTORY, STRING_PARAMETER, copyPath));

            // epages 는 afterEvaluate 에서 태스크를 등록하며 dependsOn("check") 를 건다. 그래서 apiDocs 가 ktlint·detekt
            // 같은 사용자 check 전체를 끌고 온다. 이 afterEvaluate 는 epages 의 것보다 늦게 등록되므로 여기서 건 설정은
            // epages 의 설정 뒤에 실행된다. 테스트는 apiDocsOpenApiSnippets 를 통해 계속 먼저 돈다.
            p.getTasks()
                .matching(t -> names.contains(t.getName()))
                .configureEach(task -> task.setDependsOn(withoutCheck(task.getDependsOn())));

            addSpecPostProcessing(p, extension, copyDir);
            addPostmanTidy(p, copyDir);
        });
    }

    /** {@code openapi3} 결과를 apiDocs 에 넣고, 기준 비교·뷰어·Postman 태스크를 연결한다. */
    private static void addOutputs(
        Project project, KardxExtension extension, TaskProvider<Sync> apiDocs) {
        Object openapi3 = project.getTasks().matching(t -> t.getName().equals(PluginConstants.OPENAPI3_TASK));
        Provider<List<File>> specCandidates = project.provider(() -> EpagesOutputs.openApiSpecCandidates(project));

        apiDocs.configure(task -> {
            task.dependsOn(openapi3);
            // 파일 이름은 epages openapi3 { outputFileNamePrefix, format } 를 따른다. 같은 디렉터리의 다른 결과
            // (openapi v2, postman)는 넣지 않는다
            Provider<File> specDirectory = project.provider(
                () -> EpagesOutputs.epagesOutputDirectory(project, PluginConstants.OPENAPI3_TASK));

            task.from(specDirectory, copy -> copy.include(EpagesOutputs.openApiOutputPatterns(project)));
        });

        OpenApiCheckTasks.register(project, extension, specCandidates, openapi3);
        OpenApiViewerTasks.register(project, extension, specCandidates, openapi3, apiDocs);
        HttpClientTasks.register(project, extension, specCandidates, openapi3, apiDocs);
        configurePostman(project, extension, apiDocs);
    }

    /**
     * openapi3 앞에 지난 실행의 스펙(다른 형식, 버전 스펙)을 지우고, 뒤에 스펙을 고친다: 전역 헤더 → apiKey, JSON 아닌 본문,
     * 라이브러리가 남긴 정보로 보강, 선언·공통 응답, 문서 메타데이터·스키마 설명, 버전별 스펙.
     */
    private static void addSpecPostProcessing(
        Project project, KardxExtension extension, Provider<Directory> copyDir) {
        File specDir = EpagesOutputs.epagesOutputDirectory(project, PluginConstants.OPENAPI3_TASK);
        String specPrefix = EpagesOutputs.openApiFilePrefix(project);
        Provider<Set<String>> headers = extension.getOpenApiGlobalHeaders();
        Provider<Map<Integer, String>> commonResponses = extension.getOpenApiCommonResponses();
        Provider<String> commonSchema = extension.getOpenApiCommonResponseSchema();
        Provider<String> language = DocsLanguageSetup.effective(extension);
        KardxExtension.OpenApiSecurity security = extension.getOpenApiSecurity();
        KardxExtension.ApiVersioning versioning = extension.getApiVersioning();
        List<String> specNames = EpagesOutputs.openApiFilePatterns(project);
        Provider<RegularFile> versionSpecs = project.getLayout()
            .getBuildDirectory()
            .file(PluginConstants.VERSION_SPECS_MANIFEST);
        OpenApiInfo info = extension.getOpenApiInfo();
        Provider<RegularFile> sourceDocs = sourceDocsIndex(project, extension);

        project.getTasks()
            .matching(t -> t.getName().equals(PluginConstants.OPENAPI3_TASK))
            .configureEach(task -> {
                // 쓰는 스펙 파일을 Gradle 에 알린다. 뒤 태스크가 같은 빌드에서 바뀐 스펙을 본다 (늘 돈다)
                EpagesTaskOutputs.openApi3(project, task, specDir, specNames);
                // 후처리가 읽는 설정은 모두 입력으로 선언한다. 지금은 openapi3 가 상태를 추적하지 않아 늘 돌지만,
                // 추적할 수 있게 되면 설정이 바뀔 때 다시 돌게 한다. 언어는 설명에 붙이는 문구(제약, 쿠키 목록)에 쓴다
                task.getInputs().property(DocsLanguageSetup.INPUT, language);
                task.getInputs().property("restdocsGlobalHeaders", headers);
                task.getInputs().property("restdocsCommonResponses", commonResponses);
                task.getInputs()
                    .property("restdocsCommonResponseSchema", commonSchema)
                    .optional(true);
                SpecSettingsInputs.declare(task, security, versioning);
                OpenApiInfoInputs.declare(task, info);
                // 클래스 KDoc → 스키마 설명. test 가 이미 이 태스크에 의존하므로 실행 순서는 그대로다
                task.getInputs()
                    .files(sourceDocs)
                    .withPropertyName("restdocsSourceDocs")
                    .withPathSensitivity(PathSensitivity.NONE)
                    .optional();
                // Gradle 은 Java 람다로 넘긴 태스크 액션의 구현을 추적하지 못한다(빌드 캐시·up-to-date). 그래서 익명 클래스로 쓴다
                task.doFirst(new Action<>() {
                    @Override
                    public void execute(@Nonnull Task t) {
                        // epages 는 결과 디렉터리를 비우지 않는다. 형식을 바꾸면 지난 형식의 스펙이 남아 먼저 읽힌다
                        PreviousSpecFiles.delete(specDir, specPrefix, versionSpecs.get().getAsFile());
                    }
                });
                task.doLast(new Action<>() {
                    @Override
                    public void execute(@Nonnull Task t) {
                        DocsLanguage docsLanguage = DocsLanguage.of(language.get());
                        OpenApiPostProcessing.Settings settings = new OpenApiPostProcessing.Settings(headers.get(),
                            commonResponses.get(), commonSchema.getOrNull(), docsLanguage,
                            SpecSettingsInputs.security(security), SpecSettingsInputs.versioning(versioning),
                            OpenApiInfoInputs.settings(info),
                            sourceDocs.isPresent() ? sourceDocs.get().getAsFile() : null);
                        List<File> written = OpenApiPostProcessing.apply(copyDir.get().getAsFile(), specDir, settings,
                            GradleToolLog.of(t.getLogger()));

                        PreviousSpecFiles.record(versionSpecs.get().getAsFile(), written);
                    }
                });
            });
    }

    /** {@code sourceDocs.enabled} 이면 {@code apiDocsSourceDocs} 가 만든 설명 색인, 아니면 값 없음. */
    private static Provider<RegularFile> sourceDocsIndex(Project project, KardxExtension extension) {
        TaskProvider<SourceDocsTask> task = project.getTasks()
            .named(PluginConstants.SOURCE_DOCS_TASK, SourceDocsTask.class);

        return extension.getSourceDocs()
            .getEnabled()
            .flatMap(enabled -> enabled
                ? task.flatMap(SourceDocsTask::getOutputFile)
                : project.getObjects().fileProperty());
    }

    /**
     * postman 의 입력(조각 복사본)·출력(Collection)을 선언하고, 뒤에 Collection 을 다듬는다
     * ({@link PostmanCollectionTidy}).
     */
    private static void addPostmanTidy(Project project, Provider<Directory> copyDir) {
        File postman = EpagesOutputs.postmanFile(project);

        project.getTasks()
            .matching(t -> t.getName().equals(PluginConstants.POSTMAN_TASK))
            .configureEach(task -> EpagesTaskOutputs.postman(task, copyDir, postman));
        project.getTasks()
            .matching(t -> t.getName().equals(PluginConstants.POSTMAN_TASK))
            .configureEach(task -> task.doLast(new Action<>() {
                @Override
                public void execute(@Nonnull Task t) {
                    int tidied = PostmanCollectionTidy.apply(postman);

                    t.getLogger().info("Postman: tidied {} headers (fake token → {{{}}}, empty value)", tidied,
                        PostmanCollectionTidy.TOKEN_VARIABLE);
                }
            }));
    }

    /**
     * epages 에는 test 의 출력(스니펫) 대신 복사본을 준다. 보안 요구사항을 맞추느라 resource.json 을 고쳐야 하는데,
     * test 의 출력 파일을 고치면 Gradle 이 다음 빌드에서 test 를 다시 돌린다.
     */
    private static TaskProvider<Sync> registerSnippetsCopy(
        Project project, KardxExtension extension, TaskProvider<Test> test, Provider<Directory> copyDir) {
        return project.getTasks().register(PluginConstants.OPEN_API_SNIPPETS_TASK, Sync.class, task -> {
            task.setDescription("Copies OpenAPI resource snippets and aligns security requirements per operation.");
            task.dependsOn(test);
            task.from(extension.getSnippetsDirectory(), copy -> copy.include(OpenApiSnippets.PATTERNS));
            task.into(copyDir);
            task.doLast(new Action<>() {
                @Override
                public void execute(@Nonnull Task t) {
                    OpenApiSnippets.prepare(((Sync) t).getDestinationDir(), GradleToolLog.of(t.getLogger()));
                }
            });
        });
    }

    /**
     * {@code postmanCollection.enabled} 이면 apiDocs 가 epages {@code postman} 을 실행하고 결과 JSON 을 넣는다.
     * postman 도 openapi3 와 같은 조각 복사본(보안 요구사항·스키마 이름을 맞춘 것)을 읽는다. 스펙 후처리
     * (전역 헤더 → apiKey, 스키마 보강)는 OpenAPI 전용이라 하지 않는다: Collection 은 요청 예시의 헤더를 그대로 담는다.
     * 가짜 JWT 토큰 예시만 {@link PostmanCollectionTidy} 가 {@code {{bearerToken}}} 변수로 바꾼다.
     */
    private static void configurePostman(
        Project project, KardxExtension extension, TaskProvider<Sync> apiDocs) {
        Provider<Boolean> enabled = extension.getPostmanCollection().getEnabled();

        apiDocs.configure(task -> {
            // Callable 은 실행 그래프를 만들 때 부른다. provider 를 map 한 값을 넘기면 enabled 가 Gradle 속성·환경 변수
            // provider 일 때 dependsOn 이 값을 읽지 않아 postman 이 돌지 않는다
            task.dependsOn((Callable<Object>) () -> enabled.get()
                ? project.getTasks().matching(t -> t.getName().equals(PluginConstants.POSTMAN_TASK))
                : List.of());
            // configuration cache 가 값을 저장하므로 JDK 불변 목록(List.of) 대신 ArrayList 로 준다
            task.from(project.provider(() -> {
                List<File> files = new ArrayList<>();

                if (enabled.get()) {
                    files.add(EpagesOutputs.postmanFile(project));
                }

                return files;
            }));
        });
    }

    private static String absolutePath(Provider<Directory> directory) {
        return directory.get()
            .getAsFile()
            .getAbsolutePath();
    }

    /** 태스크 의존 목록에서 같은 프로젝트의 {@code check} (이름, 태스크, 태스크 provider)를 뺀다. */
    public static List<Object> withoutCheck(Iterable<Object> dependencies) {
        List<Object> kept = new ArrayList<>();

        for (Object dependency : dependencies) {
            if (!isCheck(dependency)) {
                kept.add(dependency);
            }
        }

        return kept;
    }

    private static boolean isCheck(Object dependency) {
        // Task 와 TaskProvider 는 Named 다
        if (dependency instanceof CharSequence name) {
            return PluginConstants.CHECK_TASK.contentEquals(name);
        }

        return dependency instanceof Named named && PluginConstants.CHECK_TASK.equals(named.getName());
    }
}
