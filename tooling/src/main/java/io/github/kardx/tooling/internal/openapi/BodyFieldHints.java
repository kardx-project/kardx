package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.openapi.DescriptorHints.ATTRIBUTES;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.DEPRECATED;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.ITEMS;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.MASKED;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.NULLABLE;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.NULLABLE_ITEMS;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.REQUIRED_BY_CONSTRAINT;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.REQUIRED_IN_PARENT;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.appendNote;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.coerce;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.hasHints;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.markMasked;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.merge;
import static io.github.kardx.tooling.internal.spec.SpecMaps.EXAMPLE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUIRED;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 디스크립터 속성을 요청·응답 본문 필드와 폼 파라미터의 스키마에 합친다 ({@link OpenApiSchemaHints}). REST Docs 필드 경로를
 * 따라 스키마를 찾고, 필수 여부는 부모 스키마의 {@code required} 로, null 가능 여부는 {@code nullable} 로 맞춘다.
 * {@code deprecated}·마스킹 표시는 같은 스키마를 가리키는 모든 필드가 표시했을 때만 넣는다 ({@link SharedSchemaMarks}).
 */
final class BodyFieldHints {
    private static final String PATH = "path";
    private static final String NAME = "name";
    private static final String ARRAY_SEGMENT = "[]";
    private static final Pattern SEGMENT = Pattern.compile("\\['([^']*)']|\\[]|[^.\\[]+");

    private BodyFieldHints() {
    }

    /** 경로를 따라 찾은 스키마. [parent] 는 [name] 속성을 가진 객체 스키마 (배열 원소면 null). */
    record Target(Map<String, Object> parent, String name, Map<String, Object> node) {
    }

    /** 경로로 찾은 스키마와 그 필드의 디스크립터 속성. */
    record FieldTarget(Target target, Map<String, Object> attributes) {
        Map<String, Object> node() {
            return target.node();
        }
    }

    static int apply(Map<String, Object> document, Map<String, Object> operation, Resource resource,
        SharedSchemaMarks marks, DocsLanguage language) {
        int changed = 0;

        for (FieldTarget field : targets(document, operation, resource)) {
            if (hasHints(field.attributes())) {
                applyToField(field.target(), field.attributes(), marks, language);
                changed++;
            }
        }

        return changed;
    }

    /** [resource] 가 문서화한 요청·응답 본문 필드(ignored 제외)와 폼 파라미터가 가리키는 스키마. 찾지 못한 필드는 뺀다. */
    static List<FieldTarget> targets(Map<String, Object> document, Map<String, Object> operation, Resource resource) {
        String requestType = SpecMaps.string(resource.request(), "contentType");
        String responseType = SpecMaps.string(resource.response(), "contentType");
        Map<String, Object> request = SpecMaps.requestMedia(operation, requestType);
        boolean form = requestType != null && requestType.startsWith("application/x-www-form-urlencoded");
        List<Map<String, Object>> requestFields = form
            ? formFields(resource)
            : resource.requestDescriptors("requestFields");
        List<FieldTarget> targets = new ArrayList<>();

        collect(targets, document, request, requestFields);
        collect(targets, document, SpecMaps.responseMedia(operation, resource.status(), responseType),
            resource.responseDescriptors("responseFields"));

        return targets;
    }

    /** epages 는 폼 파라미터를 본문 스키마의 속성으로 만든다. 이름을 경로로 쓴다. */
    private static List<Map<String, Object>> formFields(Resource resource) {
        List<Map<String, Object>> fields = new ArrayList<>();

        for (Map<String, Object> parameter : resource.requestDescriptors("formParameters")) {
            Map<String, Object> field = new LinkedHashMap<>(parameter);
            field.put(PATH, parameter.get(NAME));
            fields.add(field);
        }

        return fields;
    }

    private static void collect(List<FieldTarget> targets, Map<String, Object> document, Map<String, Object> media,
        List<Map<String, Object>> fields) {
        Map<String, Object> root = media == null ? null : SpecMaps.resolve(document, media.get(SCHEMA));

        if (root == null) {
            return;
        }

        for (Map<String, Object> field : fields) {
            Map<String, Object> attributes = map(field.get(ATTRIBUTES));
            Target target = Boolean.TRUE.equals(field.get("ignored"))
                ? null
                : locate(document, root, String.valueOf(field.get(PATH)));

            if (target != null) {
                targets.add(new FieldTarget(target, attributes == null ? Map.of() : attributes));
            }
        }
    }

    private static void applyToField(
        Target target, Map<String, Object> attributes, SharedSchemaMarks marks, DocsLanguage language) {
        Map<String, Object> node = target.node();

        merge(node, attributes, false);
        appendNote(node, attributes, language);

        if (marks.agreed(node, attributes, DEPRECATED)) {
            node.put(DEPRECATED, true);
        }

        if (marks.agreed(node, attributes, MASKED)) {
            markMasked(node, attributes);
        }

        if (attributes.get(EXAMPLE) != null) {
            node.put(EXAMPLE, coerce(String.valueOf(attributes.get(EXAMPLE)), node));
        }

        boolean requiredInParent = Boolean.TRUE.equals(attributes.get(REQUIRED_BY_CONSTRAINT))
            || Boolean.TRUE.equals(attributes.get(REQUIRED_IN_PARENT));

        if (requiredInParent && target.parent() != null) {
            TreeSet<String> required = new TreeSet<>();
            List<Object> existing = SpecMaps.list(target.parent().get(REQUIRED));

            if (existing != null) {
                existing.forEach(name -> required.add(String.valueOf(name)));
            }

            required.add(target.name());
            target.parent().put(REQUIRED, new ArrayList<>(required));
        }

        if (requiredInParent || Boolean.FALSE.equals(attributes.get(NULLABLE))) {
            node.remove(NULLABLE);
        } else if (Boolean.TRUE.equals(attributes.get(NULLABLE))) {
            node.put(NULLABLE, true);
        }

        Map<String, Object> items = map(node.get(ITEMS));

        if (items != null && Boolean.TRUE.equals(attributes.get(NULLABLE_ITEMS))) {
            items.put(NULLABLE, true);
        }
    }

    /** REST Docs 경로({@code a.b}, {@code a[].b}, {@code ['a.b']}, {@code [].id})를 따라 스키마를 찾는다. 없으면 null. */
    private static Target locate(Map<String, Object> document, Map<String, Object> root, String path) {
        Map<String, Object> node = root;
        Map<String, Object> parent = null;
        String name = null;
        Matcher matcher = SEGMENT.matcher(path);

        while (node != null && matcher.find()) {
            String segment = matcher.group(1) != null ? matcher.group(1) : matcher.group();

            if (ARRAY_SEGMENT.equals(segment)) {
                parent = null;
                name = null;
                node = SpecMaps.resolve(document, node.get(ITEMS));
            } else {
                parent = node;
                name = segment;
                node = SpecMaps.resolve(document, SpecMaps.map(map(node.get("properties")), segment));
            }
        }

        return node == null || node == root ? null : new Target(parent, name, node);
    }
}
