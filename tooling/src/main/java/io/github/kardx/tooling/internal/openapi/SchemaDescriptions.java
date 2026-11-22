package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 컴포넌트 스키마({@code components.schemas})의 {@code description} 을 클래스 KDoc·Javadoc 으로 채운다.
 *
 * <p>스키마 이름과 타입은 라이브러리가 조각마다 남긴 {@value OpenApiSchemaNames#METADATA_FILE} 의 {@code type}
 * ({@code com.shop.ApiResponse<com.shop.order.Item>})과 {@code resource.json} 의 최종 스키마 이름(이름 충돌 정리 뒤)으로 잇는다.
 * 설명은 설명 색인({@code source-docs.json} 의 {@code types.<이진 이름>.description})에서 읽는다.
 * {@code sourceDocs.schemaDescriptions = false} 면 플러그인이 색인에 클래스 설명을 적지 않으므로 여기서 넣을 것이 없다.
 * <ul>
 *   <li>설명할 클래스는 스키마 이름을 만든 클래스다: 바깥 타입의 단순 이름이 스키마 이름에 있으면 바깥 타입
 *       ({@code ApiResponseItem} → {@code ApiResponse}), 없으면(래퍼를 뗀 이름) 이름에 있는 타입 인자
 *       ({@code Item} → {@code Item})</li>
 *   <li>한 스키마 이름이 서로 다른 클래스에서 나왔으면(직접 정한 이름을 여러 타입이 같이 씀) 설명을 넣지 않는다</li>
 *   <li>직접 정한 이름({@code openApi { responseSchema }}, {@code openApiErrorSchema}, 이름 규칙)도 본문 타입을 알면
 *       라이브러리가 {@code type} 을 남기므로 같은 규칙으로 설명한다. 타입을 모르거나(필드를 직접 쓴 본문) 이름에 어느 클래스
 *       이름도 없으면 건너뛴다</li>
 *   <li>스키마에 이미 설명이 있으면 두고, 컬렉션처럼 KDoc 이 없는 클래스면 넣지 않는다</li>
 * </ul>
 */
final class SchemaDescriptions {
    private static final List<String> SIDES = List.of(SchemaUsage.REQUEST, SchemaUsage.RESPONSE);
    /** 타입 문자열 안의 정규화된 클래스 이름 (점이 하나 이상). */
    private static final Pattern QUALIFIED = Pattern.compile("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+");

    private SchemaDescriptions() {
    }

    /**
     * 스키마 이름 → 설명. 색인 파일이 없으면 빈 맵.
     *
     * @param sourceDocs 설명 색인 ({@code source-docs.json}). 기능을 껐으면(기본) null
     */
    static Map<String, String> read(File snippetsDirectory, File sourceDocs) {
        Map<String, String> descriptions = new TreeMap<>();

        if (sourceDocs == null || !sourceDocs.isFile()) {
            return descriptions;
        }

        Map<String, Object> types = SpecMaps.mapOrEmpty(SpecMaps.mapOrEmpty(JsonFiles.read(sourceDocs)).get("types"));

        classesByName(snippetsDirectory).forEach((name, classes) -> {
            String description = classes.size() == 1 ? description(types, classes.iterator().next()) : null;

            if (description != null) {
                descriptions.put(name, description);
            }
        });

        return descriptions;
    }

    /** 설명이 없는 컴포넌트 스키마에 설명을 넣는다. @return 넣은 스키마 수 */
    static int transform(Map<String, Object> document, Map<String, String> descriptions) {
        Map<String, Object> schemas = SpecMaps.componentSchemas(document);
        int changed = 0;

        if (schemas == null) {
            return 0;
        }

        for (Map.Entry<String, String> entry : descriptions.entrySet()) {
            Map<String, Object> schema = map(schemas, entry.getKey());

            if (schema != null && schema.get(DESCRIPTION) == null) {
                schema.put(DESCRIPTION, entry.getValue());
                changed++;
            }
        }

        return changed;
    }

    /** 스키마 이름 → 그 이름을 만든 클래스들. 타입을 남기지 않은 조각은 건너뛴다. */
    private static Map<String, Set<String>> classesByName(File snippetsDirectory) {
        Map<String, Set<String>> classes = new TreeMap<>();

        for (Path file : OpenApiResources.files(snippetsDirectory)) {
            Path metadataFile = file.resolveSibling(OpenApiSchemaNames.METADATA_FILE);

            if (!Files.isRegularFile(metadataFile)) {
                continue;
            }

            Map<String, Object> resource = SpecMaps.mapOrEmpty(JsonFiles.read(file.toFile()));
            Map<String, Object> metadata = SpecMaps.mapOrEmpty(JsonFiles.read(metadataFile.toFile()));

            for (String side : SIDES) {
                Object name = SpecMaps.mapOrEmpty(SpecMaps.mapOrEmpty(resource.get(side)).get("schema")).get("name");
                Object type = SpecMaps.mapOrEmpty(metadata.get(side)).get("type");

                if (name != null && type != null) {
                    String described = describedClass(String.valueOf(name), String.valueOf(type));

                    if (described != null) {
                        classes.computeIfAbsent(String.valueOf(name), n -> new TreeSet<>()).add(described);
                    }
                }
            }
        }

        return classes;
    }

    /**
     * 스키마 이름을 만든 클래스: 이름에 바깥 타입의 단순 이름이 있으면 바깥 타입, 없으면 이름에 있는 타입 인자.
     * 어느 클래스 이름도 없으면(직접 정한 {@code ProductDetailResponse} 의 본문이 {@code ApiResponse<ProductResponse>}) 어느 클래스의
     * 설명인지 모르므로 null 이다.
     */
    static String describedClass(String schemaName, String type) {
        List<String> classes = new ArrayList<>();
        Matcher matcher = QUALIFIED.matcher(type);

        while (matcher.find()) {
            classes.add(matcher.group());
        }

        return classes.stream()
            .filter(name -> schemaName.contains(simpleName(name)))
            .findFirst()
            .orElse(null);
    }

    /**
     * 색인의 클래스 설명. 색인 키는 이진 이름({@code Outer$Inner})이고 타입 문자열은 Kotlin 이름({@code Outer.Inner})이라 뒤의
     * 점부터 {@code $} 로 바꿔 가며 찾는다.
     */
    private static String description(Map<String, Object> types, String className) {
        String candidate = className;

        while (true) {
            Object description = SpecMaps.mapOrEmpty(types.get(candidate)).get(DESCRIPTION);

            if (description instanceof String text && !text.isBlank()) {
                return text;
            }

            int dot = candidate.lastIndexOf('.');

            if (dot < 0) {
                return null;
            }

            candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1);
        }
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
