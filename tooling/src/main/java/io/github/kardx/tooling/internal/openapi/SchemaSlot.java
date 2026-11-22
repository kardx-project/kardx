package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스키마를 통째로 바꿀 자리: 스키마를 담은 맵과 키. 본문 루트면 미디어 타입 객체의 {@code schema}, 객체 필드면 부모 스키마
 * {@code properties} 의 필드 이름, 배열 원소면 배열 스키마의 {@code items}. {@code $ref} 를 따라 내려가므로 공유 스키마 안의
 * 자리면 그 스키마를 쓰는 모든 곳이 함께 바뀐다.
 *
 * @param container 스키마를 담은 맵
 * @param key 그 맵에서 스키마의 키
 */
record SchemaSlot(Map<String, Object> container, String key) {
    private static final String ARRAY_SEGMENT = "[]";
    /** REST Docs 필드 경로의 한 칸 ({@code a}, {@code ['a.b']}, {@code []}). {@link BodyFieldHints} 와 같다. */
    private static final Pattern SEGMENT = Pattern.compile("\\['([^']*)']|\\[]|[^.\\[]+");

    /** 지금 자리에 있는 스키마. */
    Object schema() {
        return container.get(key);
    }

    /** 자리의 스키마를 바꾼다. */
    void replace(Object schema) {
        container.put(key, schema);
    }

    /**
     * 미디어 타입 객체 [media] 의 본문에서 REST Docs 필드 경로 [path] 의 자리. 빈 경로는 본문 루트다. 찾지 못하면 null.
     */
    static SchemaSlot locate(Map<String, Object> document, Map<String, Object> media, String path) {
        if (media == null || media.get(SCHEMA) == null) {
            return null;
        }

        SchemaSlot slot = new SchemaSlot(media, SCHEMA);
        Matcher matcher = SEGMENT.matcher(path == null ? "" : path);

        while (slot != null && matcher.find()) {
            String segment = matcher.group(1) != null ? matcher.group(1) : matcher.group();
            Map<String, Object> node = SpecMaps.resolve(document, slot.schema());

            slot = node == null ? null : child(node, segment);
        }

        return slot == null || slot.schema() == null ? null : slot;
    }

    private static SchemaSlot child(Map<String, Object> node, String segment) {
        if (ARRAY_SEGMENT.equals(segment)) {
            return new SchemaSlot(node, DescriptorHints.ITEMS);
        }

        Map<String, Object> properties = SpecMaps.map(node, "properties");

        return properties == null ? null : new SchemaSlot(properties, segment);
    }
}
