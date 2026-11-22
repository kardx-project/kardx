package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.COMPONENTS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMAS;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 선언한 응답의 본문 스키마를 {@code components.schemas} 에 넣고 가리킬 이름을 정한다.
 * <ul>
 *   <li>{@code title} 을 빼고 같은 스키마가 이미 있으면(epages 가 만든 것이든 앞서 넣은 선언이든) 그 이름을 쓴다</li>
 *   <li>없으면 상태 코드의 reason phrase 로 이름을 짓는다 ({@code 409} → {@code ConflictResponse}, 모르는 코드는
 *       {@code Status499Response})</li>
 *   <li>그 이름을 다른 스키마가 쓰면 operationId 를 앞에 붙이고({@code CreateOrderConflictResponse}), 그래도 겹치면 번호를
 *       붙인다({@code CreateOrderConflictResponse2})</li>
 * </ul>
 * 넣는 스키마는 epages 처럼 {@code title} 에 이름을 적는다.
 */
final class DeclaredResponseSchemas {
    private static final String TITLE = "title";
    private static final String SUFFIX = "Response";
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    private DeclaredResponseSchemas() {
    }

    /**
     * @param operationId 이름이 겹칠 때 앞에 붙일 operation 의 id. 없으면 null
     * @return 본문이 가리킬 {@code components.schemas} 이름
     */
    static String register(Map<String, Object> document, Map<String, Object> schema, int status, String operationId) {
        Map<String, Object> schemas = SpecMaps.child(SpecMaps.child(document, COMPONENTS), SCHEMAS);
        Map<String, Object> wanted = withoutTitle(schema);
        String same = sameSchema(schemas, wanted);

        if (same != null) {
            return same;
        }

        String name = freeName(schemas, status, operationId);
        Map<String, Object> component = new LinkedHashMap<>();

        component.put(TITLE, name);
        component.putAll(SpecMaps.mapOrEmpty(SpecMaps.deepCopy(wanted)));
        schemas.put(name, component);

        return name;
    }

    /** {@code title} 을 빼고 [wanted] 와 같은 스키마의 이름 (문서 순서로 처음). 없으면 null. */
    private static String sameSchema(Map<String, Object> schemas, Map<String, Object> wanted) {
        for (Map.Entry<String, Object> entry : schemas.entrySet()) {
            Map<String, Object> existing = SpecMaps.map(entry.getValue());

            if (existing != null && withoutTitle(existing).equals(wanted)) {
                return entry.getKey();
            }
        }

        return null;
    }

    private static String freeName(Map<String, Object> schemas, int status, String operationId) {
        String phrase = HttpStatusPhrases.of(status);
        String base = pascalCase(phrase == null ? "Status " + status : phrase) + SUFFIX;

        if (!schemas.containsKey(base)) {
            return base;
        }

        String qualified = pascalCase(operationId == null ? "" : operationId) + base;
        String name = qualified;

        for (int n = 2; schemas.containsKey(name); n++) {
            name = qualified + n;
        }

        return name;
    }

    private static Map<String, Object> withoutTitle(Map<String, Object> schema) {
        Map<String, Object> copy = new LinkedHashMap<>(schema);

        copy.remove(TITLE);

        return copy;
    }

    /** 글자·숫자가 아닌 문자로 나눈 낱말의 첫 글자를 대문자로 이어 붙인다 ({@code create-order} → {@code CreateOrder}). */
    private static String pascalCase(String text) {
        return Arrays.stream(WORD_SEPARATOR.split(text))
            .filter(word -> !word.isEmpty())
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
            .collect(Collectors.joining());
    }
}
