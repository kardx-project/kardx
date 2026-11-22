package io.github.kardx.tooling.internal.check;

import static io.github.kardx.tooling.internal.check.SpecNodes.MAX_DEPTH;
import static io.github.kardx.tooling.internal.check.SpecNodes.reference;
import static io.github.kardx.tooling.internal.check.SpecNodes.resolve;
import static io.github.kardx.tooling.internal.check.SpecNodes.type;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUIRED;
import static io.github.kardx.tooling.internal.spec.SpecMaps.listOrEmpty;
import static io.github.kardx.tooling.internal.spec.SpecMaps.mapOrEmpty;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 두 스키마 사이의 깨는 변경 ({@link OpenApiBreakingChanges}): 타입·형식 변경, 요청의 필수 필드 추가와 enum 값 삭제·새 enum,
 * 요청 제약이 좁아짐({@link RequestConstraints}), 응답의 필드 삭제·선택으로 바뀜과 enum 값 추가, {@code oneOf} 하위 타입 삭제.
 * 경고: 응답 {@code oneOf} 하위 타입 추가, {@code default} 변경. {@code $ref} 를 따라가며, 순환 스키마는 같은 참조 쌍에 다시
 * 들어가지 않는다.
 */
final class SchemaChanges {
    private static final String PROPERTIES = "properties";
    private static final String ENUM = "enum";
    private static final String ITEMS = "items";
    private static final String DEFAULT = "default";

    private final Map<?, ?> beforeDocument;
    private final Map<?, ?> afterDocument;
    private final List<String> found;
    private final List<String> warnings;
    /** 지금 비교 중인 {@code $ref} 쌍. */
    private final Set<String> visiting = new HashSet<>();

    SchemaChanges(Map<?, ?> beforeDocument, Map<?, ?> afterDocument, List<String> found, List<String> warnings) {
        this.beforeDocument = beforeDocument;
        this.afterDocument = afterDocument;
        this.found = found;
        this.warnings = warnings;
    }

    void compareSchema(Direction direction, Location location, Object beforeNode, Object afterNode,
        int depth) {
        // 같은 참조 쌍을 비교하는 중이면(순환 스키마: Category.parent → Category) 다시 들어가지 않는다
        // 참조가 없는 인라인 스키마는 순환할 수 없으므로 표시하지 않는다
        String references = reference(beforeNode) + "|" + reference(afterNode);
        boolean inline = "|".equals(references);
        Map<?, ?> before = mapOrEmpty(resolve(beforeDocument, beforeNode));
        Map<?, ?> after = mapOrEmpty(resolve(afterDocument, afterNode));

        if (depth > MAX_DEPTH || before.isEmpty() || after.isEmpty() || !inline && !visiting.add(references)) {
            return;
        }

        try {
            compareResolvedSchema(direction, location, before, after, depth);
        } finally {
            visiting.remove(references);
        }
    }

    private void compareResolvedSchema(Direction direction, Location location, Map<?, ?> before, Map<?, ?> after,
        int depth) {
        String beforeType = type(before);
        String afterType = type(after);

        if (beforeType != null && afterType != null && !beforeType.equals(afterType)) {
            found.add("Type changed: " + location + " (" + beforeType + " → " + afterType + ")");

            return;
        }

        compareEnum(direction, location, before, after);
        compareConstraints(direction, location, before, after);
        compareOneOf(direction, location, before, after);
        compareProperties(direction, location, before, after, depth);

        if (before.containsKey(ITEMS)) {
            compareSchema(direction, location.child("[]"), before.get(ITEMS), after.get(ITEMS), depth + 1);
        }
    }

    /**
     * 요청은 보낼 수 있던 값이 빠지면(제한 없던 값에 enum 이 생기는 것도), 응답은 클라이언트가 모르는 값이 생기면 깨진다.
     */
    private void compareEnum(Direction direction, Location location, Map<?, ?> before, Map<?, ?> after) {
        boolean request = direction == Direction.REQUEST;

        if (request && !before.containsKey(ENUM) && after.containsKey(ENUM)) {
            found.add("Request enum added: " + location + " " + listOrEmpty(after.get(ENUM)));

            return;
        }

        if (!before.containsKey(ENUM) || !after.containsKey(ENUM)) {
            return;
        }

        Set<Object> changed = new LinkedHashSet<>(listOrEmpty((request ? before : after).get(ENUM)));

        changed.removeAll(listOrEmpty((request ? after : before).get(ENUM)));

        if (!changed.isEmpty()) {
            found.add((request ? "Enum value removed: " : "Response enum value added: ") + location + " " + changed);
        }
    }

    private void compareConstraints(Direction direction, Location location, Map<?, ?> before, Map<?, ?> after) {
        if (direction == Direction.REQUEST) {
            RequestConstraints.tightened(before, after)
                .forEach(change -> found.add("Request constraint tightened: " + location + " (" + change + ")"));
        }

        Object previous = before.get(DEFAULT);
        Object next = after.get(DEFAULT);

        if (!Objects.equals(previous, next)) {
            warnings.add("Default changed: " + location + " (" + (previous == null ? "(none)" : previous) + " → "
                + (next == null ? "(none)" : next) + ")");
        }
    }

    /** 하위 타입이 빠지면 깨진다. 응답에 하위 타입이 생기면 모르는 타입을 받으므로 경고한다. */
    private void compareOneOf(Direction direction, Location location, Map<?, ?> before, Map<?, ?> after) {
        Set<String> previous = OneOfVariants.of(before);
        Set<String> next = OneOfVariants.of(after);

        if (previous == null || next == null) {
            return;
        }

        Set<String> removed = new LinkedHashSet<>(previous);
        Set<String> added = new LinkedHashSet<>(next);

        removed.removeAll(next);
        added.removeAll(previous);

        if (!removed.isEmpty()) {
            found.add("oneOf variant removed: " + location + " " + removed);
        }

        if (!added.isEmpty() && direction == Direction.RESPONSE) {
            warnings.add("Response oneOf variant added: " + location + " " + added);
        }
    }

    private void compareProperties(Direction direction, Location location, Map<?, ?> before, Map<?, ?> after,
        int depth) {
        Map<?, ?> beforeProperties = mapOrEmpty(before.get(PROPERTIES));
        Map<?, ?> afterProperties = mapOrEmpty(after.get(PROPERTIES));
        Set<Object> beforeRequired = new LinkedHashSet<>(listOrEmpty(before.get(REQUIRED)));
        Set<Object> afterRequired = new LinkedHashSet<>(listOrEmpty(after.get(REQUIRED)));
        Set<Object> names = new LinkedHashSet<>(beforeProperties.keySet());

        names.addAll(afterProperties.keySet());

        for (Object name : names) {
            boolean existed = beforeProperties.containsKey(name);
            boolean exists = afterProperties.containsKey(name);
            String field = location.child(String.valueOf(name)).toString();

            if (direction == Direction.REQUEST) {
                compareRequestField(field, existed, beforeRequired.contains(name), afterRequired.contains(name));
            } else {
                compareResponseField(field, existed && !exists, beforeRequired.contains(name) && exists
                    && !afterRequired.contains(name));
            }

            if (existed && exists) {
                compareSchema(direction, location.child(String.valueOf(name)), beforeProperties.get(name),
                    afterProperties.get(name), depth + 1);
            }
        }
    }

    private void compareRequestField(String field, boolean existed, boolean wasRequired, boolean isRequired) {
        if (!isRequired || wasRequired) {
            return;
        }

        found.add((existed ? "Field became required: " : "Required field added: ") + field);
    }

    private void compareResponseField(String field, boolean removed, boolean becameOptional) {
        if (removed) {
            found.add("Response field removed: " + field);
        } else if (becameOptional) {
            found.add("Response field became optional: " + field);
        }
    }

    enum Direction { REQUEST, RESPONSE }

    /**
     * 메시지에 쓰는 위치: 어디의 본문인지({@code GET /orders/{id} 200 application/json})와 본문 안 필드 경로
     * ({@code lines[].sku}).
     */
    record Location(String context, String path) {
        Location child(String name) {
            if (name.equals("[]")) {
                return new Location(context, path + name);
            }

            return new Location(context, path.isEmpty() ? name : path + "." + name);
        }

        @Override
        public String toString() {
            return path.isEmpty() ? context : context + " " + path;
        }
    }
}
