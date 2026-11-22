package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.openapi.SchemaUsage.OPTIONAL;
import static io.github.kardx.tooling.internal.openapi.SchemaUsage.PATH;
import static io.github.kardx.tooling.internal.openapi.SchemaUsage.TYPE;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 같은 이름에 모양이 여럿인 스키마를 필드를 합친 하나로 맞춘다. 모든 조각에 있고 필수인 필드만 필수로 남기고,
 * 같은 경로의 타입이 다르면 합칠 수 없으므로 {@code VARIES} 로 두고 경고한다.
 */
final class SchemaMerging {
    private static final String VARIES = "VARIES";

    private SchemaMerging() {
    }

    /** 같은 이름의 모양이 여럿이면 필드를 합친 하나로 맞춘다. */
    static void merge(
        String name, List<SchemaUsage> group, List<String> changes, List<String> warnings, Set<Path> touched) {
        if (group.stream().map(SchemaUsage::structure).distinct().count() <= 1) {
            return;
        }

        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        Map<String, Integer> presence = new LinkedHashMap<>();
        Set<String> conflicts = new TreeSet<>();

        for (SchemaUsage usage : group) {
            for (Map<String, Object> field : usage.fields()) {
                String path = String.valueOf(field.get(PATH));

                presence.merge(path, 1, Integer::sum);
                mergeField(merged, path, field, conflicts);
            }
        }

        // 일부 조각에만 있는 필드는 optional 이다 (같은 조각 안의 중복은 없다고 본다)
        presence.forEach((path, count) -> {
            if (count < group.size()) {
                merged.get(path).put(OPTIONAL, true);
            }
        });

        List<Map<String, Object>> fields = new ArrayList<>(merged.values());

        for (SchemaUsage usage : group) {
            usage.replaceFields(fields);
            touched.add(usage.file());
        }

        changes.add(name + ": merged " + group.size() + " differently shaped places into one schema with all fields");

        if (!conflicts.isEmpty()) {
            warnings.add(name + ": set VARIES where the same path has different types " + conflicts + " — "
                + group.stream().map(u -> u.file().getParent().getFileName().toString()).distinct().sorted().toList()
                + ". Give the schemas their own names (openApi { responseSchema = \"...\" }).");
        }
    }

    /** [field] 를 [merged] 의 같은 경로 필드에 합친다. 타입이 다르면 {@code VARIES} 로 두고 [conflicts] 에 남긴다. */
    private static void mergeField(
        Map<String, Map<String, Object>> merged, String path, Map<String, Object> field, Set<String> conflicts) {
        Map<String, Object> existing = merged.get(path);

        if (existing == null) {
            merged.put(path, new LinkedHashMap<>(field));

            return;
        }

        if (!Objects.equals(existing.get(TYPE), field.get(TYPE))) {
            conflicts.add(path);
            existing.put(TYPE, VARIES);
        }

        if (Boolean.TRUE.equals(field.get(OPTIONAL))) {
            existing.put(OPTIONAL, true);
        }
    }
}
