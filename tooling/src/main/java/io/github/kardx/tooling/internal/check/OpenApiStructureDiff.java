package io.github.kardx.tooling.internal.check;

import io.github.kardx.tooling.internal.io.JsonFiles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 두 문서(YAML·JSON 을 읽은 Map·List·값)의 구조 차이를 위치별로 나열한다. 의미는 보지 않는다 ({@link OpenApiBreakingChanges}).
 * 이름({@code name}, 있으면 {@code in} 과 함께)이 있는 원소의 목록(파라미터·태그 등)은 순서가 아니라 이름으로 맞춘다.
 *
 * <pre>
 * added  paths &gt; /api/v1/orders &gt; post
 * removed  components &gt; schemas &gt; OrderResponse &gt; properties &gt; memo
 * changed  info &gt; version: 1.0.0 → 1.1.0
 * </pre>
 */
public final class OpenApiStructureDiff {
    static final String ADDED = "added";
    static final String REMOVED = "removed";
    static final String CHANGED = "changed";

    private static final String SEPARATOR = " > ";
    private static final String NAME = "name";
    private static final int MAX_VALUE_LENGTH = 60;

    private OpenApiStructureDiff() {
    }

    /** 차이 목록 ({@code "추가  paths > /x"}). 같으면 빈 목록. */
    public static List<String> compare(Object before, Object after) {
        List<String> out = new ArrayList<>();

        walk("", before, after, out);

        return out;
    }

    private static void walk(String path, Object before, Object after, List<String> out) {
        if (Objects.equals(before, after)) {
            return;
        }

        if (before instanceof Map<?, ?> b && after instanceof Map<?, ?> a) {
            walkMaps(path, b, a, out);
        } else if (before instanceof List<?> b && after instanceof List<?> a) {
            walkLists(path, b, a, out);
        } else {
            out.add(CHANGED + "  " + label(path) + ": " + brief(before) + " → " + brief(after));
        }
    }

    private static void walkMaps(String path, Map<?, ?> before, Map<?, ?> after, List<String> out) {
        Set<Object> keys = new LinkedHashSet<>(before.keySet());

        keys.addAll(after.keySet());

        for (Object key : keys) {
            String child = child(path, String.valueOf(key));

            if (!after.containsKey(key)) {
                out.add(REMOVED + "  " + child);
            } else if (!before.containsKey(key)) {
                out.add(ADDED + "  " + child);
            } else {
                walk(child, before.get(key), after.get(key), out);
            }
        }
    }

    private static void walkLists(String path, List<?> before, List<?> after, List<String> out) {
        Map<String, Object> b = keyed(before);
        Map<String, Object> a = keyed(after);

        if (b == null || a == null) {
            Set<Object> removed = new LinkedHashSet<>(before);
            Set<Object> added = new LinkedHashSet<>(after);

            removed.removeAll(after);
            added.removeAll(before);
            removed.forEach(value -> out.add(REMOVED + "  " + child(path, brief(value))));
            added.forEach(value -> out.add(ADDED + "  " + child(path, brief(value))));

            if (removed.isEmpty() && added.isEmpty()) {
                out.add(CHANGED + "  " + label(path) + ": order");
            }

            return;
        }

        walkMaps(path, b, a, out);
    }

    /** 원소가 모두 {@code name} 이 있는 Map 이면 이름({@code in} 이 있으면 {@code name (in)}) → 원소. 아니면 null. */
    private static Map<String, Object> keyed(List<?> list) {
        Map<String, Object> keyed = new LinkedHashMap<>();

        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map) || map.get(NAME) == null) {
                return null;
            }

            Object in = map.get("in");

            keyed.put(in == null ? String.valueOf(map.get(NAME)) : map.get(NAME) + " (" + in + ")", element);
        }

        return keyed.size() == list.size() ? keyed : null;
    }

    private static String child(String path, String key) {
        return path.isEmpty() ? key : path + SEPARATOR + key;
    }

    private static String label(String path) {
        return path.isEmpty() ? "(document)" : path;
    }

    /** 값 요약. 긴 값·구조는 줄인다. */
    private static String brief(Object value) {
        String text;

        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            text = JsonFiles.toCompactJson(value);
        } else {
            text = String.valueOf(value);
        }

        text = text.replace('\n', ' ');

        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + "…" : text;
    }
}
