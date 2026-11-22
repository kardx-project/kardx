package io.github.kardx.tooling.internal.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * 읽은 스펙 문서(맵·리스트 트리)를 다루는 도구와 OpenAPI 키 이름. 스펙 후처리({@code internal.openapi}), 기준 비교
 * ({@code internal.check}), 집계({@code internal.aggregate})가 함께 쓴다.
 */
public final class SpecMaps {
    public static final String PATHS = "paths";
    public static final String COMPONENTS = "components";
    public static final String SCHEMAS = "schemas";
    public static final String SECURITY_SCHEMES = "securitySchemes";
    public static final String REQUEST_BODY = "requestBody";
    public static final String RESPONSES = "responses";
    public static final String SCHEMA = "schema";
    public static final String CONTENT = "content";
    public static final String TYPE = "type";
    public static final String DESCRIPTION = "description";
    public static final String PARAMETERS = "parameters";
    public static final String REQUIRED = "required";
    public static final String EXAMPLE = "example";
    /** JSON Reference 키. */
    public static final String REF = "$ref";
    /** {@code components.schemas} 를 가리키는 {@link #REF} 값의 접두사. */
    public static final String COMPONENT_PREFIX = "#/components/schemas/";
    private static final int MAX_REF_DEPTH = 16;

    private SpecMaps() {
    }

    /** 맵이면 그 맵, 아니면 null. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    /** {@code parent[key]} 맵. 없거나 맵이 아니면 null. */
    public static Map<String, Object> map(Map<String, Object> parent, String key) {
        return parent == null ? null : map(parent.get(key));
    }

    /** 리스트면 그 리스트, 아니면 null. */
    @SuppressWarnings("unchecked")
    public static List<Object> list(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    /** 맵이 아니면 빈 맵. 고치지 않고 읽기만 하는 곳에서 null 검사를 줄인다. */
    public static Map<String, Object> mapOrEmpty(Object value) {
        Map<String, Object> map = map(value);

        return map == null ? new LinkedHashMap<>() : map;
    }

    /** 리스트가 아니면 빈 목록. */
    public static List<Object> listOrEmpty(Object value) {
        List<Object> list = list(value);

        return list == null ? List.of() : list;
    }

    /** 리스트 안의 맵들. 리스트가 아니면 빈 목록. */
    public static List<Map<String, Object>> maps(Object value) {
        List<Map<String, Object>> maps = new ArrayList<>();

        listOrEmpty(value).forEach(item -> {
            Map<String, Object> map = map(item);

            if (map != null) {
                maps.add(map);
            }
        });

        return maps;
    }

    /** {@code parent[key]} 맵. 없으면 만들어 넣는다. */
    public static Map<String, Object> child(Map<String, Object> parent, String key) {
        Map<String, Object> child = map(parent.get(key));

        if (child == null) {
            child = new LinkedHashMap<>();
            parent.put(key, child);
        }

        return child;
    }

    /** {@code parent[key]} 리스트. 없으면 만들어 넣는다. */
    public static List<Object> childList(Map<String, Object> parent, String key) {
        List<Object> child = list(parent.get(key));

        if (child == null) {
            child = new ArrayList<>();
            parent.put(key, child);
        }

        return child;
    }

    /** {@code components.schemas}. 없으면 null. */
    public static Map<String, Object> componentSchemas(Map<String, Object> document) {
        return map(map(document, COMPONENTS), SCHEMAS);
    }

    /** {@code paths[path][method]}. 없으면 null. */
    public static Map<String, Object> operation(Map<String, Object> document, String path, String method) {
        return map(map(map(document, PATHS), path), method);
    }

    /** 문서 순서대로 모든 operation. */
    public static List<Map<String, Object>> operations(Map<String, Object> document) {
        List<Map<String, Object>> operations = new ArrayList<>();
        Map<String, Object> paths = map(document, PATHS);

        if (paths == null) {
            return operations;
        }

        for (Object item : paths.values()) {
            for (String method : OpenApiSpecFiles.OPERATION_METHODS) {
                Map<String, Object> operation = map(map(item), method);

                if (operation != null) {
                    operations.add(operation);
                }
            }
        }

        return operations;
    }

    /** {@code $ref} 를 따라간 스키마. 참조가 아니면 그대로, 찾지 못하면 null. */
    public static Map<String, Object> resolve(Map<String, Object> document, Object schema) {
        Map<String, Object> current = map(schema);

        for (int depth = 0; depth < MAX_REF_DEPTH && current != null; depth++) {
            String name = referencedName(current);

            if (name == null) {
                return current;
            }

            current = map(componentSchemas(document), name);
        }

        return current;
    }

    /** {@code #/components/schemas/Name} 을 가리키면 Name, 아니면 null. */
    public static String referencedName(Object schema) {
        Object ref = schema instanceof Map<?, ?> m ? m.get(REF) : null;

        if (ref instanceof String s && s.startsWith(COMPONENT_PREFIX)) {
            return s.substring(COMPONENT_PREFIX.length());
        }

        return null;
    }

    /**
     * 트리 어디서든 {@code $ref} 로 가리키는 {@code components.schemas} 이름. 직렬화한 글에서 찾지 않고 트리를 훑으므로
     * 이름에 한글처럼 JSON 출력이 {@code \\uXXXX} 로 바꾸는 글자가 있어도 맞게 찾는다.
     */
    public static Set<String> referencedSchemaNames(Object node) {
        Set<String> names = new LinkedHashSet<>();

        collectReferences(node, names);

        return names;
    }

    private static void collectReferences(Object node, Set<String> names) {
        if (node instanceof Map<?, ?> map) {
            String name = referencedName(map);

            if (name != null) {
                names.add(name);
            }

            map.values().forEach(value -> collectReferences(value, names));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collectReferences(item, names));
        }
    }

    /** 요청 본문의 미디어 타입 객체. 없으면 null. */
    public static Map<String, Object> requestMedia(Map<String, Object> operation, String contentType) {
        return contentType == null ? null : map(map(map(operation, REQUEST_BODY), CONTENT), contentType);
    }

    /** 응답 [status] 의 미디어 타입 객체. 없으면 null. */
    public static Map<String, Object> responseMedia(Map<String, Object> operation, int status, String contentType) {
        return contentType == null ? null : map(map(response(operation, status), CONTENT), contentType);
    }

    /** 응답 [status] 객체. 없으면 null. */
    public static Map<String, Object> response(Map<String, Object> operation, int status) {
        return map(map(operation, RESPONSES), String.valueOf(status));
    }

    /** 문자열 값. 없으면 null. */
    public static String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);

        return value == null ? null : String.valueOf(value);
    }

    /**
     * 맵·리스트를 새 {@link LinkedHashMap}·{@link ArrayList} 로 깊이 복사한다. 키 순서와 값은 그대로이고, 그 밖의 값은
     * 복사하지 않고 공유한다. 파서의 지연 맵을 일반 맵으로 바꾸거나 원본을 건드리지 않고 고칠 때 쓴다.
     */
    public static Object deepCopy(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();

            map.forEach((key, value) -> copy.put(key, deepCopy(value)));

            return copy;
        }

        if (node instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();

            list.forEach(value -> copy.add(deepCopy(value)));

            return copy;
        }

        return node;
    }

    /** [node] 아래 모든 맵을 [visitor] 에 넘긴다. 맵 자신을 먼저 넘기고 그 값들로 내려간다 (문서 순서). */
    public static void forEachMap(Object node, Consumer<Map<String, Object>> visitor) {
        if (node instanceof Map<?, ?>) {
            Map<String, Object> map = map(node);

            visitor.accept(map);
            map.values().forEach(value -> forEachMap(value, visitor));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> forEachMap(value, visitor));
        }
    }

    /** [node] 아래 모든 맵에 [counter] 를 부르고 결과를 더한다 ({@link #forEachMap} 순서). */
    public static int sumOverMaps(Object node, ToIntFunction<Map<String, Object>> counter) {
        int[] total = {0};

        forEachMap(node, map -> total[0] += counter.applyAsInt(map));

        return total[0];
    }
}
