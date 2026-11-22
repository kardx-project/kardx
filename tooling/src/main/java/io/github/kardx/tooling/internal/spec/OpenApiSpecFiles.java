package io.github.kardx.tooling.internal.spec;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.io.TextFiles;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/** epages 가 만든 스펙 파일(YAML 또는 JSON, 확장자로 구분)을 찾고, 읽고, 같은 형식으로 다시 쓴다. */
public final class OpenApiSpecFiles {
    /** OpenAPI path item 에서 operation 을 담는 키. */
    public static final List<String> OPERATION_METHODS =
        List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private OpenApiSpecFiles() {
    }

    /** 디렉터리 안의 스펙 파일 ({@code .yaml}, {@code .yml}, {@code .json}). 디렉터리가 없으면 빈 목록. */
    public static List<File> list(File directory) {
        File[] specs = directory.listFiles((dir, name) ->
            name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json"));

        return specs == null ? List.of() : List.of(specs);
    }

    /** 스펙 파일을 확장자에 맞게 읽는다 ({@code .json} 은 JSON, 나머지는 YAML). */
    public static Object read(File spec) {
        return isJson(spec) ? JsonFiles.read(spec) : SwaggerYaml.load(TextFiles.read(spec));
    }

    /** [document] 를 [spec] 의 확장자에 맞는 형식으로 쓴다. */
    public static void write(File spec, Object document) {
        TextFiles.write(spec, isJson(spec) ? JsonFiles.toPrettyJson(document) : SwaggerYaml.dump(document));
    }

    /** OpenAPI 3 스펙이면 문서 트리, 아니면 null. 같은 디렉터리의 Swagger 2·postman 결과를 걸러 낸다. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readOpenApi3(File spec) {
        Object parsed = read(spec);

        if (parsed instanceof Map<?, ?> map && isOpenApi3(map)) {
            return (Map<String, Object>) map;
        }

        return null;
    }

    /** 최상위 {@code openapi} 가 3 으로 시작하는지. */
    public static boolean isOpenApi3(Map<?, ?> document) {
        return String.valueOf(document.get("openapi")).startsWith("3");
    }

    /**
     * 디렉터리 안의 OpenAPI 3 스펙마다 {@code transform} 을 부르고, 하나라도 바꿨으면(결과가 0 보다 크면) 다시 쓴다.
     *
     * @return {@code transform} 결과의 합 (바꾼 항목 수)
     */
    public static int rewriteOpenApi3(File directory, ToIntFunction<Map<String, Object>> transform) {
        int changed = 0;

        for (File spec : list(directory)) {
            Map<String, Object> document = readOpenApi3(spec);
            int count = document == null ? 0 : transform.applyAsInt(document);

            if (count > 0) {
                write(spec, document);
                changed += count;
            }
        }

        return changed;
    }

    private static boolean isJson(File spec) {
        return spec.getName().endsWith(".json");
    }

    /** 후보 중 실제로 있는 첫 파일. 없으면 openapi3 가 결과를 만들지 않은 것이다. */
    public static File existing(Set<File> candidates) {
        return candidates
            .stream()
            .filter(File::isFile)
            .findFirst()
            .orElseThrow(() -> new ToolingException("No generated OpenAPI spec among " + candidates
                + ". Run the openapi3 task (com.epages.restdocs-api-spec) first."));
    }
}
