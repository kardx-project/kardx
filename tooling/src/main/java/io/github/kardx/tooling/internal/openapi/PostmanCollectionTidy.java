package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * epages {@code postman} 결과를 다듬는다. 라이브러리는 epages 가 Bearer 보안 스키마를 알아보도록 마스킹된 토큰을
 * 가짜 JWT({@code alg: none})로 바꿔 넘기는데, 그 값이 Collection 의 {@code Authorization} 헤더 예시로 들어간다.
 * 그 값을 Postman 변수 {@code {{bearerToken}}} 으로 바꾸고 Collection 변수로 선언해, 토큰을 한 번만 넣으면 되게 한다.
 * 또 epages 는 요청에 없던 선택 헤더를 {@code value} 없이 쓰는데, Collection v2.1 스키마는 헤더에 {@code value} 를
 * 요구하므로 빈 값과 {@code disabled: true} 를 넣는다.
 */
public final class PostmanCollectionTidy {
    public static final String TOKEN_VARIABLE = "bearerToken";
    private static final String KEY = "key";
    private static final String VALUE = "value";
    /** 가짜 JWT 의 헤더 조각 ({@code {"alg":"none","typ":"JWT"}} 의 Base64URL). */
    private static final String PLACEHOLDER_PREFIX = "Bearer eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.";

    private PostmanCollectionTidy() {
    }

    /** 고친 헤더 수. 파일이 없거나 고칠 것이 없으면 0 이고 파일을 건드리지 않는다. */
    public static int apply(File collection) {
        if (!collection.isFile() || !(JsonFiles.read(collection) instanceof Map<?, ?> raw)) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) raw;
        int replaced = replaceTokens(document);
        int filled = fillHeaderValues(document);

        if (replaced > 0) {
            declareVariable(document);
        }

        if (replaced + filled > 0) {
            JsonFiles.writeReadable(collection, document);
        }

        return replaced + filled;
    }

    /** {@code header: [{key: Authorization, value: "Bearer eyJ…"}]} 를 찾아 바꾼다 (요청·응답 예시 모두). */
    private static int replaceTokens(Object node) {
        return SpecMaps.sumOverMaps(node, map -> {
            if ("Authorization".equalsIgnoreCase(String.valueOf(map.get(KEY))) && map.get(VALUE) instanceof String text
                && text.startsWith(PLACEHOLDER_PREFIX)) {
                map.put(VALUE, "Bearer {{" + TOKEN_VARIABLE + "}}");

                return 1;
            }

            return 0;
        });
    }

    /** {@code header: [{key: X}]} 처럼 값이 없는 헤더에 {@code value: ""}, {@code disabled: true} 를 넣는다. */
    private static int fillHeaderValues(Object node) {
        return SpecMaps.sumOverMaps(node, map -> map.get("header") instanceof List<?> headers ? fillMissing(headers) : 0);
    }

    @SuppressWarnings("unchecked")
    private static int fillMissing(List<?> headers) {
        int filled = 0;

        for (Object header : headers) {
            if (header instanceof Map<?, ?> map && map.containsKey(KEY) && !map.containsKey(VALUE)) {
                ((Map<String, Object>) map).put(VALUE, "");
                ((Map<String, Object>) map).put("disabled", true);
                filled++;
            }
        }

        return filled;
    }

    @SuppressWarnings("unchecked")
    private static void declareVariable(Map<String, Object> document) {
        List<Object> variables = document.get("variable") instanceof List<?> list
            ? new ArrayList<>((List<Object>) list)
            : new ArrayList<>();
        boolean declared = variables.stream()
            .anyMatch(v -> v instanceof Map<?, ?> m && TOKEN_VARIABLE.equals(m.get(KEY)));

        if (!declared) {
            Map<String, Object> variable = new LinkedHashMap<>();

            variable.put(KEY, TOKEN_VARIABLE);
            variable.put(VALUE, "");
            variable.put("type", "string");
            variables.add(variable);
        }

        document.put("variable", variables);
    }
}
