package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;
import static io.github.kardx.tooling.internal.spec.SpecMaps.EXAMPLE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.TYPE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.i18n.DocsText;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 디스크립터 속성({@link OpenApiSchemaHints} 의 목록)을 스키마·파라미터에 합치는 공통 규칙: 스키마 조각과 기본값 합치기,
 * 제약 설명 붙이기, 문자열 값을 스키마 타입의 값으로 바꾸기.
 */
final class DescriptorHints {
    static final String ATTRIBUTES = "attributes";
    static final String ITEMS = "items";
    // 아래 속성 키는 core 의 dsl/DescriptorAttributes.kt (*_ATTRIBUTE) 와 같다
    static final String REQUIRED_BY_CONSTRAINT = "requiredByConstraint";
    static final String REQUIRED_IN_PARENT = "requiredInParent";
    static final String NULLABLE = "nullable";
    static final String NULLABLE_ITEMS = "nullableItems";
    static final String DEPRECATED = "deprecated";
    static final String MASKED = "masked";
    private static final String MASKING_RULE = "maskingRule";
    // 아래 문구·키는 core 의 internal/schema/SchemaMarks.kt 와 같다
    private static final String MASKED_LABEL = "Masked";
    private static final String MASKED_EXTENSION = "x-masked";
    private static final String MASKED_RULE_EXTENSION = "x-masked-rule";
    private static final String PARAGRAPH = "\n\n";
    private static final String HINT = "schema";
    private static final String DEFAULT = "default";
    private static final String NOTE = "constraintNote";
    private static final String NUMBER = "number";

    private DescriptorHints() {
    }

    /** 조각과 기본값을 스키마에 합친다. [anyType] 이 아니면 타입이 맞지 않는 조각은 버린다. */
    static void merge(Map<String, Object> schema, Map<String, Object> attributes, boolean anyType) {
        Map<String, Object> hint = map(attributes.get(HINT));

        if (hint != null && (anyType || compatible(schema.get(TYPE), hint.get(TYPE)))) {
            hint.forEach((key, value) -> {
                if (ITEMS.equals(key)) {
                    mergeItems(schema, value);
                } else {
                    schema.put(key, value);
                }
            });
        }

        if (attributes.get(DEFAULT) != null) {
            schema.put(DEFAULT, coerce(String.valueOf(attributes.get(DEFAULT)), schema));
        }
    }

    /** 원소가 객체(하위 필드가 만든 스키마)가 아니면 원소 조각으로 바꾼다. epages 의 {@code oneOf} 원소도 바뀐다. */
    private static void mergeItems(Map<String, Object> schema, Object items) {
        Map<String, Object> existing = map(schema.get(ITEMS));

        if (existing != null && (existing.containsKey("properties") || SpecMaps.referencedName(existing) != null)) {
            return;
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> hinted = map(items);

        if (existing != null && existing.containsKey("enum")) {
            merged.putAll(existing);
        }

        if (hinted != null) {
            merged.putAll(hinted);
        }

        schema.put(ITEMS, merged);
    }

    private static boolean compatible(Object current, Object hinted) {
        return current == null || hinted == null || current.equals(hinted)
            || (NUMBER.equals(current) && "integer".equals(hinted));
    }

    /**
     * 설명 끝에 제약 문단({@link DocsText#CONSTRAINTS} + 제약 설명)을 붙인다. 어느 언어로든 같은 제약 문단이 이미 있으면
     * 그대로 둔다.
     */
    static void appendNote(Map<String, Object> target, Map<String, Object> attributes, DocsLanguage language) {
        Object note = attributes.get(NOTE);

        if (note == null || String.valueOf(note).isBlank()) {
            return;
        }

        String constraint = String.valueOf(note).replace("\n\n", ", ");
        Object description = target.get(DESCRIPTION);

        if (description == null || String.valueOf(description).isBlank()) {
            target.put(DESCRIPTION, DocsText.CONSTRAINTS.in(language) + constraint);
        } else if (!hasNote(String.valueOf(description), constraint)) {
            target.put(DESCRIPTION, description + PARAGRAPH + DocsText.CONSTRAINTS.in(language) + constraint);
        }
    }

    private static boolean hasNote(String description, String constraint) {
        return DocsText.CONSTRAINTS.variants()
            .stream()
            .anyMatch(prefix -> description.contains(prefix + constraint));
    }

    /**
     * 값이 마스킹되어 나간다는 표시: 설명 끝에 {@code Masked} 또는 {@code Masked: 규칙}, {@code x-masked: true},
     * 규칙이 있으면 {@code x-masked-rule}. 이미 있던 {@code Masked} 문단은 바꾼다.
     */
    static void markMasked(Map<String, Object> node, Map<String, Object> attributes) {
        Object rule = attributes.get(MASKING_RULE);
        boolean hasRule = rule != null && !String.valueOf(rule).isBlank();
        String note = hasRule ? MASKED_LABEL + ": " + rule : MASKED_LABEL;
        Object description = node.get(DESCRIPTION);
        List<String> paragraphs = new ArrayList<>();

        if (description != null && !String.valueOf(description).isBlank()) {
            Arrays.stream(String.valueOf(description).split(PARAGRAPH))
                .filter(p -> !p.equals(MASKED_LABEL) && !p.startsWith(MASKED_LABEL + ": "))
                .forEach(paragraphs::add);
        }

        paragraphs.add(note);
        node.put(DESCRIPTION, String.join(PARAGRAPH, paragraphs));
        node.put(MASKED_EXTENSION, true);

        if (hasRule) {
            node.put(MASKED_RULE_EXTENSION, String.valueOf(rule));
        } else {
            node.remove(MASKED_RULE_EXTENSION);
        }
    }

    /** {@code withDefaultValue}·{@code example} 문자열을 스키마 타입의 값으로. 바꿀 수 없으면 문자열 그대로. */
    static Object coerce(String value, Map<String, Object> schema) {
        String type = schema == null ? null : String.valueOf(schema.get(TYPE));
        String trimmed = value.trim();

        try {
            return switch (type == null ? "" : type) {
                case "integer" -> Long.parseLong(trimmed);
                case NUMBER -> new BigDecimal(trimmed);
                case "boolean" -> booleanOrText(trimmed, value);
                case "array" -> elements(value, map(schema.get(ITEMS)));
                default -> value;
            };
        } catch (NumberFormatException e) {
            // 숫자가 아닌 값을 적었으면 사용자가 적은 문자열 그대로 둔다
            return value;
        }
    }

    private static Object booleanOrText(String trimmed, String value) {
        return "true".equals(trimmed) || "false".equals(trimmed) ? Boolean.valueOf(trimmed) : value;
    }

    private static List<Object> elements(String value, Map<String, Object> items) {
        List<Object> elements = new ArrayList<>();

        for (String element : value.split(",")) {
            elements.add(coerce(element.trim(), items));
        }

        return elements;
    }

    static boolean hasHints(Map<String, Object> attributes) {
        return attributes != null && (attributes.get(HINT) != null || attributes.get(DEFAULT) != null
            || attributes.get(EXAMPLE) != null || attributes.get(NOTE) != null
            || attributes.get(REQUIRED_BY_CONSTRAINT) != null || attributes.get(REQUIRED_IN_PARENT) != null
            || attributes.get(NULLABLE) != null || attributes.get(NULLABLE_ITEMS) != null
            || attributes.get(DEPRECATED) != null || attributes.get(MASKED) != null);
    }
}
