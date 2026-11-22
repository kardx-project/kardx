package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.openapi.DescriptorHints.DEPRECATED;
import static io.github.kardx.tooling.internal.openapi.DescriptorHints.MASKED;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code deprecated}·마스킹 표시를 넣어도 되는 스키마인지 정한다. 응답 스키마는 {@code $ref} 로 여러 operation 이 함께 쓰므로,
 * 한 테스트에서만 {@code masked()}·{@code deprecated()} 로 표시한 필드가 같은 스키마를 쓰는 다른 operation 에 번질 수 있다.
 * 그래서 같은 스키마 속성을 가리키는 모든 필드 디스크립터가 표시했을 때만 표시한다. 어노테이션({@code @Masked},
 * {@code @Deprecated})에서 온 표시는 그 타입을 추출한 모든 테스트에 있으므로 그대로 남는다.
 */
final class SharedSchemaMarks {
    private static final List<String> MARKS = List.of(DEPRECATED, MASKED);

    /** 스키마 노드(동일성) → 표시하지 않은 디스크립터가 가리킨 표시 키. */
    private final Map<Map<String, Object>, Set<String>> unmarked = new IdentityHashMap<>();

    private SharedSchemaMarks() {
    }

    static SharedSchemaMarks collect(Map<String, Object> document, List<Resource> resources) {
        SharedSchemaMarks marks = new SharedSchemaMarks();

        for (Resource resource : resources) {
            Map<String, Object> operation = resource.operation(document);

            if (operation != null) {
                BodyFieldHints.targets(document, operation, resource)
                    .forEach(field -> marks.record(field.node(), field.attributes()));
            }
        }

        return marks;
    }

    private void record(Map<String, Object> node, Map<String, Object> attributes) {
        for (String mark : MARKS) {
            if (!Boolean.TRUE.equals(attributes.get(mark))) {
                unmarked.computeIfAbsent(node, k -> new HashSet<>())
                    .add(mark);
            }
        }
    }

    /** [attributes] 가 [mark] 를 표시했고, [node] 를 가리키는 다른 디스크립터도 모두 표시했는지. */
    boolean agreed(Map<String, Object> node, Map<String, Object> attributes, String mark) {
        return Boolean.TRUE.equals(attributes.get(mark)) && !unmarked.getOrDefault(node, Set.of()).contains(mark);
    }
}
