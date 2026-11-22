package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.CONTENT;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUEST_BODY;
import static io.github.kardx.tooling.internal.spec.SpecMaps.RESPONSES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import groovy.json.JsonException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 요청·응답 본문 예시를 다듬는다. JSON 본문 예시는 문자열이 아니라 JSON 값으로, 예시마다 테스트 summary 를 붙이고,
 * 성공 응답 테스트의 예시를 앞에 둔다.
 */
final class OpenApiExamples {
    private static final String SUMMARY = "summary";
    private static final String VALUE = "value";

    private OpenApiExamples() {
    }

    static void examples(Map<String, Object> operation, Map<String, Resource> byId) {
        List<Map<String, Object>> contents = new ArrayList<>();

        contents.add(map(map(operation, REQUEST_BODY), CONTENT));

        Map<String, Object> responses = map(operation, RESPONSES);

        if (responses != null) {
            responses.values()
                .forEach(response -> contents.add(map(map(response), CONTENT)));
        }

        contents.stream()
            .filter(Objects::nonNull)
            .forEach(content -> content.forEach((type, media) -> {
                Map<String, Object> mediaMap = map(media);

                if (mediaMap != null) {
                    rewriteExamples(type, mediaMap, byId);
                }
            }));
    }

    /** 예시를 성공 응답 테스트 먼저, 그다음 이름순으로 두고 JSON 문자열은 JSON 값으로, summary 를 붙인다. */
    private static void rewriteExamples(String mediaType, Map<String, Object> media, Map<String, Resource> byId) {
        Map<String, Object> examples = map(media, "examples");

        if (examples == null) {
            return;
        }

        boolean json = mediaType.toLowerCase(Locale.ROOT).contains("json");
        List<String> keys = new ArrayList<>(examples.keySet());
        Map<String, Object> rewritten = new LinkedHashMap<>();

        keys.sort(Comparator.comparing((String key) -> PrimaryResources.isSuccess(byId.get(key)) ? 0 : 1)
            .thenComparing(Comparator.naturalOrder()));

        for (String key : keys) {
            Map<String, Object> example = map(examples.get(key));

            if (example == null) {
                rewritten.put(key, examples.get(key));
                continue;
            }

            Map<String, Object> copy = new LinkedHashMap<>();
            Resource resource = byId.get(key);

            if (resource != null && resource.summary() != null && !example.containsKey(SUMMARY)) {
                copy.put(SUMMARY, resource.summary());
            }

            copy.putAll(example);

            if (json && copy.get(VALUE) instanceof String text) {
                copy.put(VALUE, parseJson(text));
            }

            rewritten.put(key, copy);
        }

        media.put("examples", rewritten);
    }

    private static Object parseJson(String text) {
        try {
            // 파서의 지연 맵을 키 순서를 지킨 일반 맵·리스트로 바꾼다
            return SpecMaps.deepCopy(JsonFiles.parse(text));
        } catch (JsonException | IllegalArgumentException e) {
            // JSON 미디어 타입이어도 본문이 JSON 이 아니면(빈 문자열 등) 문자열 예시 그대로 둔다
            return text;
        }
    }
}
