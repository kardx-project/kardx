package io.github.kardx.tooling.internal.check;

import static io.github.kardx.tooling.internal.spec.SpecMaps.REF;
import static io.github.kardx.tooling.internal.spec.SpecMaps.mapOrEmpty;

import java.util.Map;
import java.util.Objects;

/** 비교에 쓰는 스펙 노드 읽기: 같은 문서 안의 {@code $ref} 따라가기, 타입·형식. */
final class SpecNodes {
    /** 순환 스키마를 끊는 깊이. */
    static final int MAX_DEPTH = 32;

    private SpecNodes() {
    }

    static String reference(Object node) {
        return node instanceof Map<?, ?> map && map.get(REF) instanceof String ref ? ref : "";
    }

    /** {@code type} 과 {@code format} ({@code integer(int64)}). 둘 다 없으면 null. */
    static String type(Map<?, ?> schema) {
        Object type = schema.get("type");
        Object format = schema.get("format");

        if (type == null && format == null) {
            return null;
        }

        return format == null ? String.valueOf(type) : type + "(" + format + ")";
    }

    /** 같은 문서 안의 {@code $ref} ({@code #/components/...}) 를 따라간다. 따라갈 수 없으면 그대로. */
    static Object resolve(Map<?, ?> document, Object node) {
        Object current = node;

        for (int hops = 0; hops < MAX_DEPTH && current instanceof Map<?, ?> map && map.get(REF) instanceof String ref
            && ref.startsWith("#/"); hops++) {
            Object target = document;

            for (String segment : ref.substring(2).split("/")) {
                target = mapOrEmpty(target).get(segment.replace("~1", "/").replace("~0", "~"));
            }

            if (target == null) {
                return current;
            }

            current = target;
        }

        return current;
    }

    static boolean isTrue(Object value) {
        return Objects.equals(value, Boolean.TRUE);
    }
}
