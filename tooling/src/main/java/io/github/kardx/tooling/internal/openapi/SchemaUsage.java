package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 스키마 하나를 쓰는 조각의 요청 또는 응답. {@code resource.json} 과 라이브러리가 옆에 남긴
 * {@value OpenApiSchemaNames#METADATA_FILE}(이름의 출처)에서 읽는다.
 */
record SchemaUsage(
    Path file,
    Map<String, Object> resource,
    String side,
    boolean automatic,
    String type,
    List<String> candidates
) {
    static final String REQUEST = "request";
    static final String RESPONSE = "response";
    static final String PATH = "path";
    static final String TYPE = "type";
    static final String OPTIONAL = "optional";
    private static final String SCHEMA = "schema";
    private static final String NAME = "name";

    Map<String, Object> part() {
        return map(resource.get(side));
    }

    String name() {
        return String.valueOf(map(part().get(SCHEMA)).get(NAME));
    }

    void rename(String name) {
        Map<String, Object> schema = new LinkedHashMap<>(map(part().get(SCHEMA)));
        schema.put(NAME, name);
        part().put(SCHEMA, schema);
    }

    List<Map<String, Object>> fields() {
        return SpecMaps.maps(part().get(fieldsKey()));
    }

    void replaceFields(List<Map<String, Object>> fields) {
        part().put(fieldsKey(), fields);
    }

    private String fieldsKey() {
        return side.equals(REQUEST) ? "requestFields" : "responseFields";
    }

    /** 구조: 무시하지 않은 필드의 경로·타입·optional. 설명은 보지 않는다. */
    Set<String> structure() {
        return fields().stream()
            .filter(field -> !Boolean.TRUE.equals(field.get("ignored")))
            .map(field -> field.get(PATH) + "|" + field.get(TYPE) + "|" + field.get(OPTIONAL))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 같은 이름을 같은 모양으로 써야 하는 묶음. 자동 이름은 타입과 방향, 직접 정한 이름은 이름이다. */
    String identity() {
        return automatic ? type + " " + side : "name " + name();
    }

    @SuppressWarnings("unchecked")
    static List<SchemaUsage> read(File snippetsDirectory) {
        List<SchemaUsage> usages = new ArrayList<>();

        for (Path file : OpenApiResources.files(snippetsDirectory)) {
            Map<String, Object> resource = (Map<String, Object>) JsonFiles.read(file.toFile());
            Path metadataFile = file.resolveSibling(OpenApiSchemaNames.METADATA_FILE);
            Map<String, Object> metadata = Files.isRegularFile(metadataFile)
                ? map(JsonFiles.read(metadataFile.toFile()))
                : Map.of();

            for (String side : List.of(RESPONSE, REQUEST)) {
                usage(file, resource, side, metadata == null ? null : map(metadata.get(side)))
                    .ifPresent(usages::add);
            }
        }

        return usages;
    }

    private static Optional<SchemaUsage> usage(
        Path file, Map<String, Object> resource, String side, Map<String, Object> origin) {
        Map<String, Object> part = map(resource.get(side));
        Map<String, Object> schema = part == null ? null : map(part.get(SCHEMA));

        if (schema == null || schema.get(NAME) == null) {
            return Optional.empty();
        }

        String name = String.valueOf(schema.get(NAME));
        boolean automatic = origin != null
            && Boolean.TRUE.equals(origin.get("automatic"))
            && name.equals(origin.get(NAME))
            && origin.get(TYPE) != null;
        List<String> candidates = new ArrayList<>();

        if (automatic && origin.get("candidates") instanceof List<?> list) {
            list.forEach(candidate -> candidates.add(String.valueOf(candidate)));
        }

        if (candidates.isEmpty()) {
            candidates.add(name);
        }

        String type = automatic ? String.valueOf(origin.get(TYPE)) : null;

        return Optional.of(new SchemaUsage(file, resource, side, automatic, type, candidates));
    }
}
