package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.openapi.DescriptorHints.ATTRIBUTES;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.DEPRECATED;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.REQUIRED_BY_CONSTRAINT;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.appendNote;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.coerce;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.hasHints;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.merge;
import static io.github.kardx.tooling.internal.spec.SpecMaps.EXAMPLE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUIRED;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 디스크립터 속성을 path·query·header 파라미터와 응답 헤더에 합친다 ({@link OpenApiSchemaHints}). */
final class ParameterHints {
    private static final String PATH = "path";
    private static final String NAME = "name";
    private static final String HEADERS = "headers";

    private ParameterHints() {
    }

    static int apply(Map<String, Object> operation, Resource resource, DocsLanguage language) {
        return parameters(operation, PATH, resource.requestDescriptors("pathParameters"), language)
            + parameters(operation, "query", resource.requestDescriptors("queryParameters"), language)
            + parameters(operation, "header", resource.requestDescriptors(HEADERS), language)
            + responseHeaders(operation, resource, language);
    }

    /** 응답 헤더({@code responses.<status>.headers}). 이름은 대소문자를 가리지 않는다. */
    private static int responseHeaders(Map<String, Object> operation, Resource resource, DocsLanguage language) {
        Map<String, Object> headers = map(SpecMaps.response(operation, resource.status()), HEADERS);
        int changed = 0;

        if (headers == null) {
            return 0;
        }

        for (Map<String, Object> descriptor : resource.responseDescriptors(HEADERS)) {
            Map<String, Object> attributes = map(descriptor.get(ATTRIBUTES));
            String name = String.valueOf(descriptor.get(NAME));
            Map<String, Object> header = headers.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(entry -> map(entry.getValue()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

            if (header != null && hasHints(attributes)) {
                applyToParameter(header, attributes, language);
                changed++;
            }
        }

        return changed;
    }

    private static int parameters(
        Map<String, Object> operation, String in, List<Map<String, Object>> descriptors, DocsLanguage language) {
        List<Object> parameters = SpecMaps.list(operation.get(SpecMaps.PARAMETERS));
        int changed = 0;

        if (parameters == null) {
            return 0;
        }

        for (Map<String, Object> descriptor : descriptors) {
            Map<String, Object> attributes = map(descriptor.get(ATTRIBUTES));
            Map<String, Object> parameter = parameters.stream()
                .map(SpecMaps::map)
                .filter(p -> p != null && in.equals(p.get("in")))
                .filter(p -> Objects.equals(p.get(NAME), descriptor.get(NAME)))
                .findFirst()
                .orElse(null);

            if (parameter != null && hasHints(attributes)) {
                applyToParameter(parameter, attributes, language);
                changed++;
            }
        }

        return changed;
    }

    private static void applyToParameter(
        Map<String, Object> parameter, Map<String, Object> attributes, DocsLanguage language) {
        Map<String, Object> schema = SpecMaps.child(parameter, SCHEMA);

        // epages 는 파라미터를 모두 string 으로 만들므로(Enum 값이 있는 파라미터는 라이브러리가 일부러 string 으로 넘긴다)
        // 파라미터의 타입은 조각을 따른다
        merge(schema, attributes, true);
        appendNote(parameter, attributes, language);

        if (attributes.get(EXAMPLE) != null) {
            parameter.put(EXAMPLE, coerce(String.valueOf(attributes.get(EXAMPLE)), schema));
        }

        if (Boolean.TRUE.equals(attributes.get(REQUIRED_BY_CONSTRAINT))) {
            parameter.put(REQUIRED, true);
        }

        if (Boolean.TRUE.equals(attributes.get(DEPRECATED))) {
            parameter.put(DEPRECATED, true);
        }
    }
}
