package io.github.kardx.gradle.internal.wiring;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * epages 태스크의 출력 선언. epages 는 스니펫 디렉터리를 문자열 입력으로만 두고 출력을 선언하지 않는다. 그러면
 * <ul>
 *   <li>아무것도 바뀌지 않은 {@code apiDocs} 에서도 태스크가 매번 돈다</li>
 *   <li>Gradle 이 태스크가 쓴 파일을 모른다. 같은 빌드에서 그 파일을 읽는 태스크({@code apiDocs}, {@code apiDocsViewer},
 *       {@code apiDocsHttpClient}, {@code apiDocsBaseline} …)가 Gradle 이 지난 빌드에 기억한 내용으로 UP-TO-DATE 를 판단해,
 *       스펙이 바뀌었는데 지난 결과를 남길 수 있다 (파일 시스템 감시 이벤트가 늦게 오면)</li>
 * </ul>
 */
final class EpagesTaskOutputs {
    private EpagesTaskOutputs() {
    }

    /**
     * {@code openapi3}: 스펙 후보({@code <prefix>.yaml|yml|json})를 출력으로 알린다. Gradle 이 실행 전에 그 위치의 기억을
     * 지우므로 뒤 태스크가 새 내용을 본다. 같은 디렉터리의 버전 스펙({@code <prefix>-v<버전>.*})은 이름을 미리 알 수 없어 선언하지
     * 않는다: 디렉터리를 읽는 태스크({@code apiDocs})는 기억이 지워진 디렉터리를 디스크에서 다시 읽는다. 목록을 설정 단계에 읽으면
     * configuration cache 가 그 디렉터리를 입력으로 삼아 매번 버려진다.
     *
     * <p>epages {@code outputDirectory} 가 앱 리소스 안이면(이관 전 흔한 설정) 한 번 경고한다. 그중 빌드 결과
     * ({@code build/resources/main} 등) 안이면 출력을 알리지 않는다: 클래스패스를 읽는 태스크({@code jar}, {@code bootJar},
     * {@code apiDocsIndex})가 openapi3 에 의존하지 않아 Gradle 의 암묵적 의존 검사에 걸려 빌드가 실패한다.
     *
     * <p>상태는 추적하지 않는다(늘 돈다): 추적하면 Gradle 이 입력 값을 저장하는데 epages 의 {@code @Input servers}
     * (swagger {@code Server} 목록, 기본값도 있다)는 직렬화할 수 없어 빌드가 실패한다.
     */
    static void openApi3(Project project, Task task, File specDir, List<String> specNames) {
        task.doNotTrackState("epages openapi3 has an input (servers) that Gradle cannot serialize");

        Optional<File> buildOutput = containing(specDir, sourceSetOutputs(project));
        Optional<File> resources = buildOutput.or(() -> containing(specDir, sourceSetResources(project)));

        if (resources.isPresent()) {
            String undeclared = buildOutput.isPresent()
                ? " Until then the spec is not declared as an output of openapi3, because tasks that read the "
                + "classpath would fail Gradle's dependency validation."
                : "";

            project.getLogger().warn("kardx: epages openapi3.outputDirectory points into the app's "
                + "resources (" + resources.get() + "). The plugin already copies the spec into the app "
                + "(kardx.bootJar.path), so remove outputDirectory from openapi3 { }." + undeclared);
        }

        if (buildOutput.isPresent()) {
            return;
        }

        List<File> specs = new ArrayList<>();

        specNames.forEach(name -> specs.add(new File(specDir, name)));
        task.getOutputs()
            .files(specs)
            .withPropertyName("restdocsOpenApiSpecs");
    }

    /**
     * {@code postman}: 조각 복사본(그 내용)을 입력으로, Collection 파일을 출력으로 선언해 바뀐 것이 없으면 UP-TO-DATE 가 되게
     * 한다. 설정 값({@code title}, {@code apiVersion}, {@code baseUrl})은 epages 의 {@code @Input} 이 덮는다. 같은 디렉터리를
     * epages 의 다른 태스크도 쓰므로 디렉터리가 아니라 파일을 선언한다.
     */
    static void postman(Task task, Provider<Directory> snippets, File collection) {
        task.getInputs()
            .files(snippets)
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .withPropertyName("restdocsOpenApiSnippets")
            .ignoreEmptyDirectories();
        task.getOutputs()
            .file(collection)
            .withPropertyName("restdocsPostmanCollection");
    }

    /** [directory] 가 [candidates] 중 하나와 같거나 그 안이면 그 디렉터리. */
    private static Optional<File> containing(File directory, List<File> candidates) {
        Path path = normalized(directory);

        return candidates.stream()
            .filter(candidate -> path.startsWith(normalized(candidate)))
            .findFirst();
    }

    /** 모든 소스 세트의 빌드 결과 디렉터리 (클래스, 리소스). */
    private static List<File> sourceSetOutputs(Project project) {
        List<File> directories = new ArrayList<>();

        sourceSets(project).forEach(sourceSet -> {
            directories.addAll(sourceSet.getOutput().getClassesDirs().getFiles());
            Optional.ofNullable(sourceSet.getOutput().getResourcesDir()).ifPresent(directories::add);
        });

        return directories;
    }

    /** 모든 소스 세트의 리소스 원본 디렉터리 ({@code src/main/resources} 등). */
    private static List<File> sourceSetResources(Project project) {
        List<File> directories = new ArrayList<>();

        sourceSets(project).forEach(sourceSet -> directories.addAll(sourceSet.getResources().getSrcDirs()));

        return directories;
    }

    private static List<SourceSet> sourceSets(Project project) {
        SourceSetContainer sourceSets = project.getExtensions().findByType(SourceSetContainer.class);

        return sourceSets == null ? List.of() : new ArrayList<>(sourceSets);
    }

    private static Path normalized(File file) {
        return file.toPath()
            .toAbsolutePath()
            .normalize();
    }
}
