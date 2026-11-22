package io.github.kardx.tooling.internal.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 요청 스키마의 제약이 좁아졌는지 ({@link SchemaChanges}). 전에는 보낼 수 있던 값이 거부되므로 깨는 변경이다:
 * {@code minLength}·{@code minimum}·{@code minItems} 가 커지거나 생김, {@code maxLength}·{@code maximum}·{@code maxItems} 가
 * 작아지거나 생김, {@code pattern} 이 생기거나 바뀜.
 */
final class RequestConstraints {
    private static final List<String> LOWER_BOUNDS = List.of("minLength", "minimum", "minItems");
    private static final List<String> UPPER_BOUNDS = List.of("maxLength", "maximum", "maxItems");
    private static final String PATTERN = "pattern";

    private RequestConstraints() {
    }

    /** 좁아진 제약 ({@code "maxLength 100 → 50"}). 없으면 빈 목록. */
    static List<String> tightened(Map<?, ?> before, Map<?, ?> after) {
        List<String> tightened = new ArrayList<>();

        LOWER_BOUNDS.forEach(key -> compareBound(key, before.get(key), after.get(key), 1, tightened));
        UPPER_BOUNDS.forEach(key -> compareBound(key, before.get(key), after.get(key), -1, tightened));

        Object pattern = after.get(PATTERN);

        if (pattern != null && !Objects.equals(before.get(PATTERN), pattern)) {
            tightened.add(PATTERN + " " + describe(before.get(PATTERN)) + " → " + pattern);
        }

        return tightened;
    }

    /** [sign] 1 이면 커질 때, -1 이면 작아질 때 좁아진 것이다. 새로 생겨도 좁아진 것이다. */
    private static void compareBound(String key, Object before, Object after, int sign, List<String> tightened) {
        if (!(after instanceof Number next)) {
            return;
        }

        boolean narrower = !(before instanceof Number previous)
            || Double.compare(next.doubleValue(), previous.doubleValue()) * sign > 0;

        if (narrower) {
            tightened.add(key + " " + describe(before) + " → " + after);
        }
    }

    private static String describe(Object value) {
        return value == null ? "(none)" : String.valueOf(value);
    }
}
