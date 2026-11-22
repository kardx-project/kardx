package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.COMPONENTS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SECURITY_SCHEMES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 문서 최상위 항목을 채운다. {@code tags} 에 operation 의 태그를 모으고 테스트 클래스 {@code @AsciiDescription} 을 설명으로
 * 넣는다. 보안 요구사항이 없는 operation 을 {@code security: []} (인증 없음)로 적고, 보안 스키마가 없는 문서는 최상위에 한 번 적는다.
 */
final class OpenApiDocumentTidy {
    private static final String SECURITY = "security";
    private static final String TAGS = "tags";
    private static final String NAME = "name";

    private OpenApiDocumentTidy() {
    }

    /**
     * 보안 요구사항이 없는 operation 을 인증 없음으로 적는다 (Redocly {@code security-defined}). 보안 스키마가 있으면 그런
     * operation 마다 {@code security: []}, 없으면 인증 없이 문서화한 문서이므로 최상위에 한 번 적는다.
     */
    static void explicitSecurity(Map<String, Object> document) {
        Map<String, Object> schemes = map(map(document, COMPONENTS), SECURITY_SCHEMES);

        if (document.containsKey(SECURITY)) {
            return;
        }

        if (schemes == null || schemes.isEmpty()) {
            document.put(SECURITY, new ArrayList<>());

            return;
        }

        SpecMaps.operations(document)
            .forEach(operation -> operation.putIfAbsent(SECURITY, new ArrayList<>()));
    }

    static void tags(Map<String, Object> document, List<Resource> resources) {
        Map<String, String> descriptions = new LinkedHashMap<>();

        resources.forEach(resource -> {
            Map<String, Object> tag = map(resource.extras().get("tag"));

            if (tag != null && tag.get(NAME) != null && tag.get(DESCRIPTION) != null) {
                descriptions.putIfAbsent(String.valueOf(tag.get(NAME)), String.valueOf(tag.get(DESCRIPTION)));
            }
        });

        List<Object> tags = SpecMaps.childList(document, TAGS);
        Set<String> names = new TreeSet<>();

        SpecMaps.maps(tags).forEach(tag -> names.add(String.valueOf(tag.get(NAME))));

        for (Map<String, Object> operation : SpecMaps.operations(document)) {
            for (Object name : SpecMaps.listOrEmpty(operation.get(TAGS))) {
                if (names.add(String.valueOf(name))) {
                    Map<String, Object> tag = new LinkedHashMap<>();
                    tag.put(NAME, name);
                    tags.add(tag);
                }
            }
        }

        SpecMaps.maps(tags).forEach(tag -> {
            String description = descriptions.get(String.valueOf(tag.get(NAME)));

            if (description != null && tag.get(DESCRIPTION) == null) {
                tag.put(DESCRIPTION, description);
            }
        });
    }
}
