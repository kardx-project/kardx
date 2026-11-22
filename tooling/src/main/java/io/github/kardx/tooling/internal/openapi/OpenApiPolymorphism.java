package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kotlin sealed 타입·Jackson {@code @JsonSubTypes} 본문을 {@code oneOf} 로 바꾼다. 라이브러리가
 * {@value OpenApiResources#EXTRAS_FILE} 의 {@value #REQUEST_KEY}·{@value #RESPONSE_KEY} 에 남긴다
 * ({@code path}, {@code baseName}, {@code discriminator}, {@code variants}).
 * <ul>
 *   <li>하위 타입마다 {@code components.schemas} 를 만들고({@link VariantSchemas}) {@code path} 의 스키마를
 *       {@code oneOf: [$ref …]} 로 바꾼다. 식별자가 있으면 {@code discriminator} 의 {@code mapping} 도 그 이름을 가리킨다</li>
 *   <li>{@code path} 는 REST Docs 필드 경로({@code ""} 는 본문 루트, {@code payments[]} 는 배열 원소, 공통 응답 래퍼 안이면
 *       {@code data.method}·{@code data.items[].method}, 데이터 자체면 {@code data})다. 중간의 {@code $ref}·배열을 따라
 *       내려간다 ({@link SchemaSlot})</li>
 *   <li>바꾼 자리의 설명({@code description})·{@code nullable} 은 남긴다. 바꾼 자리가 {@code $ref} 로 가리키던 스키마는 더 쓰는
 *       곳이 없으면 뺀다</li>
 * </ul>
 * 필드 경로로 찾는 다른 보강(스키마 정보, 기본값)이 하위 필드를 찾을 수 있도록 그 뒤에 실행한다. 필드 표·스니펫은 바꾸지 않는다.
 */
final class OpenApiPolymorphism {
    static final String REQUEST_KEY = "requestBodyPolymorphism";
    static final String RESPONSE_KEY = "responseBodyPolymorphism";
    private static final String CONTENT_TYPE = "contentType";
    private static final String NAME = "name";
    private static final String DISCRIMINATOR = "discriminator";
    private static final List<String> KEPT = List.of(DESCRIPTION, DescriptorHints.NULLABLE);

    private OpenApiPolymorphism() {
    }

    /** @return {@code oneOf} 로 바꾼 자리 수 */
    static int transform(Map<String, Object> document, List<Resource> resources) {
        Set<String> replaced = new LinkedHashSet<>();
        int changed = 0;

        for (Resource resource : resources) {
            Map<String, Object> operation = resource.operation(document);

            if (operation == null) {
                continue;
            }

            Map<String, Object> request = SpecMaps.requestMedia(
                operation, SpecMaps.string(resource.request(), CONTENT_TYPE));
            Map<String, Object> response = SpecMaps.responseMedia(
                operation, resource.status(), SpecMaps.string(resource.response(), CONTENT_TYPE));

            changed += apply(document, request, resource.polymorphism(REQUEST_KEY), replaced);
            changed += apply(document, response, resource.polymorphism(RESPONSE_KEY), replaced);
        }

        OpenApiBodySchemas.removeUnreferenced(document, SpecMaps.componentSchemas(document), replaced);

        return changed;
    }

    private static int apply(Map<String, Object> document, Map<String, Object> media,
        List<Map<String, Object>> polymorphism, Set<String> replaced) {
        int changed = 0;

        for (Map<String, Object> declared : polymorphism) {
            SchemaSlot slot = SchemaSlot.locate(document, media, SpecMaps.string(declared, "path"));
            List<Map<String, Object>> variants = SpecMaps.maps(declared.get("variants"));

            if (slot == null || variants.isEmpty() || isOneOf(slot.schema())) {
                continue;
            }

            String previous = SpecMaps.referencedName(slot.schema());

            if (previous != null) {
                replaced.add(previous);
            }

            slot.replace(oneOf(document, declared, variants, SpecMaps.map(slot.schema())));
            changed++;
        }

        return changed;
    }

    private static Map<String, Object> oneOf(Map<String, Object> document, Map<String, Object> declared,
        List<Map<String, Object>> variants, Map<String, Object> previous) {
        String baseName = SpecMaps.string(declared, "baseName");
        Map<String, String> names = new LinkedHashMap<>();
        List<Object> references = new ArrayList<>();

        for (Map<String, Object> variant : variants) {
            String name = SpecMaps.string(variant, NAME);
            Map<String, Object> schema = SpecMaps.map(variant.get(SCHEMA));

            if (name != null && schema != null) {
                String registered = VariantSchemas.register(document, schema, name, baseName);

                names.put(name, registered);
                references.add(reference(registered));
            }
        }

        Map<String, Object> oneOf = new LinkedHashMap<>();

        oneOf.put("oneOf", references);
        discriminator(SpecMaps.map(declared.get(DISCRIMINATOR)), names, oneOf);

        if (previous != null && previous.get(SpecMaps.REF) == null) {
            KEPT.stream()
                .filter(previous::containsKey)
                .forEach(key -> oneOf.put(key, previous.get(key)));
        }

        return oneOf;
    }

    /** 식별자 속성과 값 → 하위 타입 스키마. 식별자가 없으면 넣지 않는다. */
    private static void discriminator(Map<String, Object> declared, Map<String, String> names,
        Map<String, Object> oneOf) {
        String property = SpecMaps.string(declared, "property");

        if (property == null) {
            return;
        }

        Map<String, Object> discriminator = new LinkedHashMap<>();
        Map<String, Object> mapping = new LinkedHashMap<>();

        discriminator.put("propertyName", property);
        SpecMaps.mapOrEmpty(declared.get("mapping")).forEach((value, variant) -> {
            String registered = names.get(String.valueOf(variant));

            if (registered != null) {
                mapping.put(value, SpecMaps.COMPONENT_PREFIX + registered);
            }
        });

        if (!mapping.isEmpty()) {
            discriminator.put("mapping", mapping);
        }

        oneOf.put(DISCRIMINATOR, discriminator);
    }

    private static Map<String, Object> reference(String name) {
        Map<String, Object> reference = new LinkedHashMap<>();

        reference.put(SpecMaps.REF, SpecMaps.COMPONENT_PREFIX + name);

        return reference;
    }

    private static boolean isOneOf(Object schema) {
        Map<String, Object> map = SpecMaps.map(schema);

        return map != null && map.containsKey("oneOf");
    }
}
