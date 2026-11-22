package io.github.kardx.tooling.internal.aggregate;

import static io.github.kardx.tooling.internal.spec.SpecMaps.COMPONENTS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.PATHS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SECURITY_SCHEMES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.child;
import static io.github.kardx.tooling.internal.spec.SpecMaps.childList;
import static io.github.kardx.tooling.internal.spec.SpecMaps.listOrEmpty;
import static io.github.kardx.tooling.internal.spec.SpecMaps.mapOrEmpty;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 여러 프로젝트의 OpenAPI 3 스펙을 하나로 합친다 ({@code apiDocsAggregate}). 규칙:
 * <ul>
 *   <li>paths: 합친다. 같은 경로·메서드가 두 프로젝트에 있으면 실패한다 (어느 쪽이 맞는지 알 수 없다)</li>
 *   <li>components (schemas, securitySchemes 등): 이름과 내용이 같으면 하나로 둔다. 이름이 같고 내용이 다르면 나중
 *       프로젝트의 것을 {@code <프로젝트 이름 PascalCase><이름 첫 글자 대문자>} 로 바꾸고 그 프로젝트의 {@code $ref}·보안 요구사항도
 *       바꾼다 ({@code :member} 의 {@code Item} → {@code MemberItem})</li>
 *   <li>operationId: 겹치면 나중 프로젝트의 것을 {@code <프로젝트 이름>-<operationId>} 로 바꾼다</li>
 *   <li>최상위 security: 합친 문서의 최상위에 두지 않고, 그 프로젝트의 operation 중 security 가 없는 것에 내려 쓴다</li>
 *   <li>tags·servers: 이름·URL 로 중복을 빼고 합친다. 먼저 나온 설명을 쓴다</li>
 *   <li>info: 집계 설정의 title·version·description</li>
 * </ul>
 * 프로젝트는 넘긴 순서(프로젝트 경로순)대로 처리한다.
 */
public final class OpenApiMerger {

    private final Map<String, Object> merged = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();
    /** 경로·메서드 → 정의한 프로젝트. */
    private final Map<String, String> owners = new HashMap<>();
    private final Set<Object> operationIds = new LinkedHashSet<>();

    private OpenApiMerger() {
    }

    /**
     * 프로젝트 하나의 스펙.
     *
     * @param project 프로젝트 이름 ({@code order}, {@code services/member})
     * @param document 읽은 스펙. 합치는 동안 고친다
     */
    public record Source(String project, Map<String, Object> document) {
    }

    /**
     * 합친 결과.
     *
     * @param document 합친 스펙
     * @param notes 바꾼 이름 등 알릴 내용
     */
    public record Result(Map<String, Object> document, List<String> notes) {
    }

    /**
     * [sources] 를 차례로 합친다. {@code openapi} 버전은 첫 스펙의 것을 쓴다.
     *
     * @param sources 합칠 스펙. 비어 있으면 안 된다 ({@code apiDocsAggregate} 는 OpenAPI 스펙이 있을 때만 부른다)
     */
    public static Result merge(List<Source> sources, String title, String version, String description) {
        OpenApiMerger merger = new OpenApiMerger();
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("title", title);
        info.put("version", version);

        if (description != null) {
            info.put("description", description);
        }

        merger.merged.put("openapi", sources.get(0).document().get("openapi"));
        merger.merged.put("info", info);
        sources.forEach(merger::add);

        if (!merger.conflicts.isEmpty()) {
            throw new ToolingException("apiDocsAggregate: the same operation is documented by more than one project.\n"
                + String.join("\n", merger.conflicts)
                + "\nExclude one of them (openApi { excluded = true }) or use different paths.");
        }

        merger.merged.entrySet().removeIf(entry -> entry.getValue() instanceof List<?> list && list.isEmpty());

        return new Result(merger.merged, merger.notes);
    }

    private void add(Source source) {
        Map<String, Object> document = source.document();
        Map<String, Object> components = mapOrEmpty(document.get(COMPONENTS));
        Map<String, Map<String, String>> renames = componentRenames(source.project(), components);

        SpecRewrites.rewriteReferences(document, renames);
        SpecRewrites.renameSecurityRequirements(document, renames.getOrDefault(SECURITY_SCHEMES, Map.of()));
        SpecRewrites.pushDownSecurity(document);
        addComponents(mapOrEmpty(document.get(COMPONENTS)), renames);
        addPaths(source.project(), mapOrEmpty(document.get(PATHS)));
        union("servers", listOrEmpty(document.get("servers")), server -> mapOrEmpty(server).get("url"));
        union("tags", listOrEmpty(document.get("tags")), tag -> mapOrEmpty(tag).get("name"));
    }

    /**
     * 종류별로 바꿀 이름 (종류 → 원래 이름 → 새 이름). 이미 합친 것과 이름은 같고 내용이 다르면 바꾼다. 바꾼 이름을 참조하는
     * 컴포넌트는 내용이 달라지므로, 더 바꿀 것이 없을 때까지 다시 본다.
     */
    private Map<String, Map<String, String>> componentRenames(String project, Map<String, Object> components) {
        Map<String, Map<String, String>> renames = new LinkedHashMap<>();
        Map<String, Object> existing = mapOrEmpty(merged.get(COMPONENTS));
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Map.Entry<String, Object> type : mapOrEmpty(SpecRewrites.rewritten(components, renames)).entrySet()) {
                Map<String, String> renamed = renames.computeIfAbsent(type.getKey(), k -> new LinkedHashMap<>());

                changed |= renameConflicts(project, type.getKey(), mapOrEmpty(type.getValue()),
                    mapOrEmpty(existing.get(type.getKey())), renamed);
            }
        }

        return renames;
    }

    /** 한 종류의 컴포넌트 중 이미 합친 것과 이름만 같은 것을 [renamed] 에 더한다. 더했으면 true. */
    private boolean renameConflicts(String project, String type, Map<String, Object> components,
        Map<String, Object> mergedOfType, Map<String, String> renamed) {
        boolean changed = false;

        for (Map.Entry<String, Object> component : components.entrySet()) {
            Object current = mergedOfType.get(component.getKey());

            if (current == null || renamed.containsKey(component.getKey())
                || Objects.equals(current, component.getValue())) {
                continue;
            }

            String base = ComponentNames.pascal(project) + ComponentNames.capitalize(component.getKey());
            String name = ComponentNames.uniqueName(base, mergedOfType, components, renamed);

            renamed.put(component.getKey(), name);
            notes.add(project + ": components." + type + "." + component.getKey() + " → " + name
                + " (same name, different content)");
            changed = true;
        }

        return changed;
    }

    private void addComponents(Map<String, Object> components, Map<String, Map<String, String>> renames) {
        Map<String, Object> target = child(merged, COMPONENTS);

        for (Map.Entry<String, Object> type : components.entrySet()) {
            Map<String, Object> mergedOfType = child(target, type.getKey());
            Map<String, String> renamed = renames.getOrDefault(type.getKey(), Map.of());

            mapOrEmpty(type.getValue()).forEach((name, value) ->
                mergedOfType.putIfAbsent(renamed.getOrDefault(name, name), value));
        }
    }

    private void addPaths(String project, Map<String, Object> paths) {
        Map<String, Object> target = child(merged, PATHS);

        for (Map.Entry<String, Object> path : paths.entrySet()) {
            Map<String, Object> item = child(target, path.getKey());

            for (Map.Entry<String, Object> entry : mapOrEmpty(path.getValue()).entrySet()) {
                if (!OpenApiSpecFiles.OPERATION_METHODS.contains(entry.getKey())) {
                    item.putIfAbsent(entry.getKey(), entry.getValue());
                    continue;
                }

                String operation = entry.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey();
                String owner = owners.putIfAbsent(operation, project);

                if (owner != null) {
                    conflicts.add("  " + operation + ": " + owner + ", " + project);
                    continue;
                }

                renameOperationId(project, operation, mapOrEmpty(entry.getValue()));
                item.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private void renameOperationId(String project, String operation, Map<String, Object> body) {
        Object id = body.get("operationId");

        if (id == null || operationIds.add(id)) {
            return;
        }

        String renamed = project.replace('/', '-') + "-" + id;

        body.put("operationId", renamed);
        operationIds.add(renamed);
        notes.add(project + ": " + operation + " operationId " + id + " → " + renamed + " (duplicate)");
    }

    private void union(String key, List<Object> values, Function<Object, Object> identity) {
        List<Object> target = childList(merged, key);
        Set<Object> seen = new LinkedHashSet<>();

        target.forEach(value -> seen.add(identity.apply(value)));

        for (Object value : values) {
            if (seen.add(identity.apply(value))) {
                target.add(value);
            }
        }
    }
}
