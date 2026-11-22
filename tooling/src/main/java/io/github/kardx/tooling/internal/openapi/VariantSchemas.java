package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.COMPONENTS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMAS;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 다형 본문({@link OpenApiPolymorphism})의 하위 타입 스키마를 {@code components.schemas} 에 넣고 가리킬 이름을 정한다.
 * <ul>
 *   <li>{@code title} 을 빼고 같은 이름에 같은 스키마가 있으면 그 이름을 그대로 쓴다 (같은 하위 타입을 여러 operation 이 쓸 때)</li>
 *   <li>같은 이름을 다른 스키마가 쓰면 상위 타입 이름을 앞에 붙이고({@code PaymentCardPayment}), 그래도 겹치면 번호를 붙인다</li>
 * </ul>
 * 넣는 스키마는 epages 처럼 {@code title} 에 이름을 적는다.
 */
final class VariantSchemas {
    private static final String TITLE = "title";

    private VariantSchemas() {
    }

    /** @return 하위 타입을 가리킬 {@code components.schemas} 이름 */
    static String register(Map<String, Object> document, Map<String, Object> schema, String name, String baseName) {
        Map<String, Object> schemas = SpecMaps.child(SpecMaps.child(document, COMPONENTS), SCHEMAS);
        Map<String, Object> wanted = withoutTitle(schema);
        String qualified = (baseName == null ? "" : baseName) + name;
        String chosen = usable(schemas, name, wanted) ? name : qualified;

        for (int n = 2; !usable(schemas, chosen, wanted); n++) {
            chosen = qualified + n;
        }

        if (!schemas.containsKey(chosen)) {
            Map<String, Object> component = new LinkedHashMap<>();

            component.put(TITLE, chosen);
            component.putAll(SpecMaps.mapOrEmpty(SpecMaps.deepCopy(wanted)));
            schemas.put(chosen, component);
        }

        return chosen;
    }

    /** [name] 이 비었거나 [wanted] 와 같은 스키마인지. */
    private static boolean usable(Map<String, Object> schemas, String name, Map<String, Object> wanted) {
        Map<String, Object> existing = SpecMaps.map(schemas.get(name));

        if (existing == null) {
            return !schemas.containsKey(name);
        }

        return withoutTitle(existing).equals(wanted);
    }

    private static Map<String, Object> withoutTitle(Map<String, Object> schema) {
        Map<String, Object> copy = new LinkedHashMap<>(schema);

        copy.remove(TITLE);

        return copy;
    }
}
