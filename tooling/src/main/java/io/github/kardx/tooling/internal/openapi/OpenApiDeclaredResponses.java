package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.CONTENT;
import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;
import static io.github.kardx.tooling.internal.spec.SpecMaps.EXAMPLE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.RESPONSES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.openapi.OpenApiCommonResponses.ErrorBody;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import groovy.json.JsonException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 테스트가 실제로 만들지 않고 선언만 한 응답({@code errorResponse(409, "…") { … }})을 operation 에 더한다. 라이브러리가
 * {@value OpenApiResources#EXTRAS_FILE} 에 남긴다 ({@link Resource#declaredResponses()}).
 * <ul>
 *   <li>테스트가 만든 상태 코드(이미 스펙에 있는 응답)는 건드리지 않는다</li>
 *   <li>공통 응답({@link OpenApiCommonResponses})보다 먼저 더하므로 선언이 공통 응답을 이긴다. {@code 401}·{@code 403} 도
 *       보안 요구사항과 상관없이 더한다 (직접 선언했으므로)</li>
 *   <li>같은 operation 을 여러 테스트가 문서화했으면 선언을 합친다. 같은 상태 코드를 다른 설명으로 선언했으면 조각 경로
 *       이름순으로 앞선 것을 쓰고 경고한다</li>
 *   <li>본문 스키마를 선언했으면 {@link DeclaredResponseSchemas} 가 {@code components.schemas} 에 넣은 이름을 가리키고,
 *       없으면 공통 응답과 같은 본문({@link OpenApiCommonResponses#errorBody})을 쓴다. 단 JSON 이 아닌 미디어 타입
 *       ({@code text/plain} …)이면 공통 JSON 오류 스키마 대신 {@code type: string} 이다</li>
 *   <li>예시는 미디어 타입이 JSON 이고 JSON 으로 읽히면 값으로, 아니면 글 그대로 {@code example} 에 넣는다</li>
 *   <li>더한 operation 의 응답은 공통 응답과 같은 상태 코드 순서로 다시 놓는다</li>
 * </ul>
 */
final class OpenApiDeclaredResponses {
    private static final String STATUS = "status";
    private static final String CONTENT_TYPE = "contentType";

    private OpenApiDeclaredResponses() {
    }

    /**
     * 한 operation 의 선언. 상태 코드마다 조각 경로 이름순으로 처음 본 선언을 둔다.
     *
     * @param key 경고에 쓰는 이름 ({@code POST /orders})
     */
    private record Declarations(String key, Map<String, Object> operation, Map<Integer, Map<String, Object>> byStatus) {
    }

    /**
     * 문서 트리를 고친다.
     *
     * @param fallback 본문 스키마를 선언하지 않은 응답의 본문
     * @param warnings 같은 상태 코드를 다른 설명으로 선언한 operation 을 여기에 더한다 (같은 내용은 한 번)
     * @return 더한 응답 수 (operation × 상태 코드)
     */
    static int transform(
        Map<String, Object> document, List<Resource> resources, ErrorBody fallback, List<String> warnings) {
        int added = 0;

        for (Declarations declarations : collect(document, resources, warnings)) {
            added += addResponses(document, declarations, fallback);
        }

        return added;
    }

    private static Collection<Declarations> collect(
        Map<String, Object> document, List<Resource> resources, List<String> warnings) {
        Map<String, Declarations> byOperation = new LinkedHashMap<>();

        for (Resource resource : resources) {
            Map<String, Object> operation = resource.operation(document);
            List<Map<String, Object>> declared = resource.declaredResponses();

            if (operation == null || declared.isEmpty()) {
                continue;
            }

            String key = resource.method().toUpperCase(Locale.ROOT) + " " + resource.path();
            Declarations declarations = byOperation.computeIfAbsent(key,
                k -> new Declarations(k, operation, new TreeMap<>()));

            declared.forEach(response -> merge(declarations, response, warnings));
        }

        return byOperation.values();
    }

    private static void merge(Declarations declarations, Map<String, Object> declared, List<String> warnings) {
        if (!(declared.get(STATUS) instanceof Number status)) {
            return;
        }

        Map<String, Object> first = declarations.byStatus().putIfAbsent(status.intValue(), declared);

        if (first == null || Objects.equals(first.get(DESCRIPTION), declared.get(DESCRIPTION))) {
            return;
        }

        String warning = declarations.key() + ": response " + status.intValue()
            + " is declared with different descriptions by several tests, using '" + first.get(DESCRIPTION) + "'";

        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    private static int addResponses(Map<String, Object> document, Declarations declarations, ErrorBody fallback) {
        Map<String, Object> operation = declarations.operation();
        Map<String, Object> responses = SpecMaps.child(operation, RESPONSES);
        int added = 0;

        for (Map.Entry<Integer, Map<String, Object>> entry : declarations.byStatus().entrySet()) {
            String status = String.valueOf(entry.getKey());

            if (!responses.containsKey(status)) {
                responses.put(status, response(document, operation, entry.getKey(), entry.getValue(), fallback));
                added++;
            }
        }

        if (added > 0) {
            operation.put(RESPONSES, OpenApiCommonResponses.sorted(responses));
        }

        return added;
    }

    private static Map<String, Object> response(
        Map<String, Object> document, Map<String, Object> operation, int status, Map<String, Object> declared,
        ErrorBody fallback) {
        Map<String, Object> schema = SpecMaps.map(declared.get(SCHEMA));
        String contentType = SpecMaps.string(declared, CONTENT_TYPE);
        String description = SpecMaps.string(declared, DESCRIPTION);
        String text = description == null || description.isBlank()
            ? OpenApiCommonResponses.phrase(status)
            : description;
        ErrorBody body;

        if (schema == null && contentType != null && !isJson(contentType)) {
            return textResponse(text, contentType, declared.get(EXAMPLE));
        }

        if (schema == null) {
            body = new ErrorBody(fallback.schema(), contentType == null ? fallback.mediaType() : contentType);
        } else {
            String name = DeclaredResponseSchemas.register(document, schema, status,
                SpecMaps.string(operation, "operationId"));

            body = new ErrorBody(name, contentType == null ? OpenApiCommonResponses.DEFAULT_MEDIA_TYPE : contentType);
        }

        Map<String, Object> response = OpenApiCommonResponses.response(text, body);
        Object example = declared.get(EXAMPLE);

        if (example != null && body.mediaType() != null) {
            SpecMaps.child(SpecMaps.child(response, CONTENT), body.mediaType())
                .put(EXAMPLE, exampleValue(example, body.mediaType()));
        }

        return response;
    }

    /**
     * 본문 필드 없이 JSON 이 아닌 미디어 타입({@code text/plain}, {@code text/html} …)으로 선언한 응답. 공통 JSON 오류
     * 스키마를 붙이지 않고 {@code type: string} 과 글 그대로의 예시를 쓴다.
     */
    private static Map<String, Object> textResponse(String description, String contentType, Object example) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> media = new LinkedHashMap<>();
        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put(SpecMaps.TYPE, "string");
        media.put(SCHEMA, schema);

        if (example != null) {
            media.put(EXAMPLE, String.valueOf(example));
        }

        response.put(DESCRIPTION, description);
        response.put(CONTENT, new LinkedHashMap<>(Map.of(contentType, media)));

        return response;
    }

    private static boolean isJson(String mediaType) {
        return mediaType.toLowerCase(Locale.ROOT).contains("json");
    }

    /** JSON 미디어 타입이면 예시 글을 JSON 값으로 읽는다. 읽히지 않거나 JSON 이 아니면 그대로. */
    private static Object exampleValue(Object example, String mediaType) {
        if (!(example instanceof String text) || !isJson(mediaType)) {
            return example;
        }

        try {
            return SpecMaps.deepCopy(JsonFiles.parse(text));
        } catch (JsonException | IllegalArgumentException e) {
            return text;
        }
    }
}
