package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.internal.PluginConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.Task;

/** epages 태스크({@code openapi3}, {@code postman})가 실제로 쓰는 출력 위치. 태스크 설정을 리플렉션으로 읽는다. */
final class EpagesOutputs {
    /** epages postman 의 기본 파일 이름. */
    private static final String POSTMAN_PREFIX = "postman-collection";

    private EpagesOutputs() {
    }

    /** openapi3 결과 후보 ({@code <outputFileNamePrefix>.yaml|yml|json}). 있는 첫 파일이 스펙이다. */
    static List<File> openApiSpecCandidates(Project project) {
        File directory = epagesOutputDirectory(project, PluginConstants.OPENAPI3_TASK);

        List<File> candidates = new ArrayList<>();

        // configuration cache 가 값을 저장하므로 JDK 불변 목록(Stream.toList) 대신 ArrayList 로 준다
        openApiFilePatterns(project).forEach(name -> candidates.add(new File(directory, name)));

        return candidates;
    }

    /** postman 결과 파일 ({@code <outputFileNamePrefix>.json}, 기본 {@code postman-collection.json}). */
    static File postmanFile(Project project) {
        return new File(epagesOutputDirectory(project, PluginConstants.POSTMAN_TASK),
            epagesFilePrefix(project, PluginConstants.POSTMAN_TASK, POSTMAN_PREFIX) + ".json");
    }

    /** openapi3 태스크의 출력 파일 이름 앞부분 ({@code outputFileNamePrefix}, 기본 openapi3). */
    static String openApiFilePrefix(Project project) {
        return epagesFilePrefix(project, PluginConstants.OPENAPI3_TASK, PluginConstants.OPENAPI3_TASK);
    }

    /** openapi3 태스크의 출력 파일 이름 패턴 ({@code outputFileNamePrefix}, 기본 openapi3). */
    static List<String> openApiFilePatterns(Project project) {
        String prefix = openApiFilePrefix(project);

        return List.of(prefix + ".yaml", prefix + ".yml", prefix + ".json");
    }

    /**
     * apiDocs 에 넣을 openapi3 결과의 이름 패턴: 스펙과 {@code apiVersioning.splitSpecs} 의 버전별 스펙
     * ({@code <outputFileNamePrefix>-v<버전>.<확장자>}).
     */
    static List<String> openApiOutputPatterns(Project project) {
        List<String> patterns = new ArrayList<>(openApiFilePatterns(project));

        openApiFilePatterns(project).forEach(name -> {
            int dot = name.lastIndexOf('.');

            patterns.add(name.substring(0, dot) + "-v*" + name.substring(dot));
        });

        return patterns;
    }

    /** openapi3 결과의 확장자 (epages {@code openapi3 { format }}). {@code json} 이 아니면 {@code yaml}. */
    static String openApiExtension(Project project) {
        Object spec = project.getExtensions().findByName(PluginConstants.OPENAPI3_TASK);
        Object format = spec == null ? null : Reflection.invokeIfPresent(spec, "getFormat");

        return "json".equals(String.valueOf(format)) ? "json" : OpenApiDefaults.FORMAT;
    }

    /** epages 태스크의 {@code outputFileNamePrefix}. 알 수 없으면 [fallback]. */
    private static String epagesFilePrefix(Project project, String taskName, String fallback) {
        Task task = project.getTasks().findByName(taskName);
        Object value = task == null ? null : Reflection.invokeIfPresent(task, "getOutputFileNamePrefix");

        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    /** epages 태스크가 실제로 쓰는 출력 디렉터리. 알 수 없으면 epages 기본값 build/api-spec. */
    static File epagesOutputDirectory(Project project, String taskName) {
        Task task = project.getTasks().findByName(taskName);

        if (task != null) {
            Object directory = Reflection.invokeIfPresent(task, "getOutputDirectory");

            if (directory != null) {
                return project.file(directory);
            }
        }

        return project.getLayout().getBuildDirectory().dir(PluginConstants.EPAGES_OUTPUT_DIR).get().getAsFile();
    }
}
