package io.github.kardx.tooling.internal.check;

import static io.github.kardx.tooling.internal.spec.SpecMaps.listOrEmpty;
import static io.github.kardx.tooling.internal.spec.SpecMaps.mapOrEmpty;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code oneOf} 의 하위 타입 이름 ({@link SchemaChanges}). {@code $ref} 면 가리키는 스키마 이름, 인라인이면 {@code title},
 * 그것도 없으면 스키마 JSON 이다.
 */
final class OneOfVariants {
    private static final String ONE_OF = "oneOf";

    private OneOfVariants() {
    }

    /** [schema] 의 {@code oneOf} 하위 타입 이름. {@code oneOf} 가 없으면 null. */
    static Set<String> of(Map<?, ?> schema) {
        if (!schema.containsKey(ONE_OF)) {
            return null;
        }

        Set<String> names = new LinkedHashSet<>();

        for (Object variant : listOrEmpty(schema.get(ONE_OF))) {
            String reference = SpecNodes.reference(variant);
            Object title = mapOrEmpty(variant).get("title");

            if (!reference.isEmpty()) {
                names.add(reference.substring(reference.lastIndexOf('/') + 1));
            } else if (title != null) {
                names.add(String.valueOf(title));
            } else {
                names.add(JsonFiles.toCompactJson(SpecMaps.deepCopy(variant)));
            }
        }

        return names;
    }
}
