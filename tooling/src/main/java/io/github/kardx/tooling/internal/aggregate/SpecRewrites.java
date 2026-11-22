package io.github.kardx.tooling.internal.aggregate;

import static io.github.kardx.tooling.internal.spec.SpecMaps.PATHS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REF;
import static io.github.kardx.tooling.internal.spec.SpecMaps.listOrEmpty;
import static io.github.kardx.tooling.internal.spec.SpecMaps.mapOrEmpty;

import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 합치기 전에 프로젝트 스펙을 고친다: 바뀐 컴포넌트 이름으로 {@code $ref}·보안 요구사항을 바꾸고, 최상위 보안을 operation 에 내려 쓴다. */
final class SpecRewrites {
    private static final String SECURITY = "security";
    private static final String REF_PREFIX = "#/components/";

    private SpecRewrites() {
    }

    /**
     * 프로젝트의 최상위 {@code security} 를 자기 operation 중 {@code security} 가 없는 것에 내려 쓰고 최상위에서 뺀다.
     * 합친 문서의 최상위에 두면 다른 프로젝트의 공개 operation 까지 인증이 필요해지기 때문이다.
     */
    static void pushDownSecurity(Map<String, Object> document) {
        Object security = document.remove(SECURITY);

        if (!(security instanceof List<?> requirements)) {
            return;
        }

        mapOrEmpty(document.get(PATHS)).values()
            .forEach(item -> mapOrEmpty(item).forEach((method, operation) -> {
                if (OpenApiSpecFiles.OPERATION_METHODS.contains(method)) {
                    mapOrEmpty(operation).putIfAbsent(SECURITY, SpecMaps.deepCopy(requirements));
                }
            }));
    }

    /** 보안 요구사항({@code security: [{name: []}]})의 스키마 이름을 바꾼다. 문서 최상위와 모든 operation. */
    static void renameSecurityRequirements(Map<String, Object> document, Map<String, String> renames) {
        if (renames.isEmpty()) {
            return;
        }

        List<Object> holders = new ArrayList<>(List.of(document));

        mapOrEmpty(document.get(PATHS)).values()
            .forEach(item -> holders.addAll(mapOrEmpty(item).values()));

        for (Object holder : holders) {
            for (Object requirement : listOrEmpty(mapOrEmpty(holder).get(SECURITY))) {
                Map<String, Object> names = mapOrEmpty(requirement);
                Map<String, Object> copy = new LinkedHashMap<>(names);

                names.clear();
                copy.forEach((name, scopes) -> names.put(renames.getOrDefault(name, name), scopes));
            }
        }
    }

    /** {@code $ref: "#/components/<type>/<name>"} 를 바꾼다 (제자리). */
    static void rewriteReferences(Object node, Map<String, Map<String, String>> renames) {
        SpecMaps.forEachMap(node, map -> {
            if (map.get(REF) instanceof String ref && ref.startsWith(REF_PREFIX)) {
                map.put(REF, renamedReference(ref, renames));
            }
        });
    }

    private static String renamedReference(String ref, Map<String, Map<String, String>> renames) {
        String[] parts = ref.substring(REF_PREFIX.length()).split("/", 2);

        if (parts.length < 2) {
            return ref;
        }

        String name = renames.getOrDefault(parts[0], Map.of()).get(parts[1]);

        return name == null ? ref : REF_PREFIX + parts[0] + "/" + name;
    }

    /** 참조를 바꾼 깊은 복사본. 비교용이라 원본은 건드리지 않는다. */
    static Object rewritten(Object node, Map<String, Map<String, String>> renames) {
        Object copy = SpecMaps.deepCopy(node);

        rewriteReferences(copy, renames);

        return copy;
    }
}
