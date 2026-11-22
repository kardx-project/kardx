package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * operationId 를 정한다. 라이브러리가 남긴 후보(핸들러 메서드 이름 등, {@link #operationIds})가 있으면 그것으로, 없으면
 * 테스트 이름으로 만든다. 테스트 이름 id 는 여러 테스트가 합쳐진 operation 의 id 를 epages 버전과 상관없이 같은 규칙으로 맞추고,
 * {@code /} 등 URL 에 쓰기 어려운 문자를 바꾼다 ({@code product-api-docs-test/get-product} → {@code product-api-docs-test.get-product}).
 */
final class OpenApiOperationIds {
    private static final String OPERATION_ID = "operationId";
    /** 후보의 키. 라이브러리의 OperationIdCandidates 와 같다 */
    private static final String ID = "id";
    private static final String QUALIFIED = "qualified";
    private static final String FIXED = "fixed";

    private OpenApiOperationIds() {
    }

    /**
     * 여러 테스트가 합쳐진 operation 의 operationId. epages 0.19 는 공통 접두사가 없으면 id 를 이어 붙이고
     * ({@code a/xb/y}), 0.20 은 대표 조각의 id 를 쓰며, 둘 다 단어 중간에서 자를 수 있다({@code a/root-l}).
     * 버전과 상관없이 테스트 메서드 이름의 {@code -} 경계까지의 공통 접두사({@code item-test/get-item}), 메서드 이름 부분이
     * 남지 않으면 대표 조각(성공 응답 우선, 그다음 이름순)의 id 로 맞춘다.
     */
    static void mergedOperationId(Map<String, Object> operation, List<Resource> resources) {
        List<String> ids = resources.stream()
            .map(Resource::operationId)
            .filter(Objects::nonNull)
            .sorted()
            .toList();

        if (ids.size() < 2) {
            return;
        }

        String prefix = commonPrefix(ids);
        boolean namesMethod = prefix.indexOf('/') > 0 && !prefix.endsWith("/");
        String id = namesMethod
            ? prefix
            : resources.stream()
                .min(PrimaryResources.PRIMARY_FIRST)
                .map(Resource::operationId)
                .orElse(prefix);

        operation.put(OPERATION_ID, id);
    }

    /** 모든 id 에서 뒤가 끝이거나 {@code -} 인 가장 긴 공통 접두사. 없으면 빈 문자열. */
    static String commonPrefix(List<String> ids) {
        String first = ids.get(0);
        int length = ids.stream()
            .mapToInt(id -> matchingLength(first, id))
            .min()
            .orElse(0);

        for (int end = length; end > 0; end--) {
            int cut = end;

            if (ids.stream().allMatch(id -> id.length() == cut || id.charAt(cut) == '-')) {
                return first.substring(0, end);
            }
        }

        return "";
    }

    private static int matchingLength(String a, String b) {
        int n = 0;

        while (n < a.length() && n < b.length() && a.charAt(n) == b.charAt(n)) {
            n++;
        }

        return n;
    }

    /**
     * 스펙의 모든 operation 의 최종 operationId 를 정한다. 라이브러리가 {@value OpenApiResources#EXTRAS_FILE} 에 남긴 후보
     * ({@link Resource#operationIdCandidate()})를 보고, 후보가 없으면 테스트 이름으로 만든 지금 id 를 다듬는다.
     * operationId 를 정하는 곳은 여기 하나다 (라이브러리는 후보만 만든다).
     * <ol>
     *   <li>고정 id (테스트의 {@code openApi { operationId }}, 사용자 resolver): 그대로 쓴다. 다른 operation 과 겹치면 경고</li>
     *   <li>규칙 id: 서로 다른 operation 에 같은 id 가 생기거나 고정 id 와 같으면 그 operation 들만 {@code qualified}
     *       (컨트롤러 이름을 붙인 id)로 바꾼다. 그래도 겹치면 뒤에 숫자({@code userCreate2})</li>
     *   <li>테스트 이름 id: {@link #normalize} 로 다듬고, 겹치면 {@code -2}, {@code -3} …</li>
     * </ol>
     * operation 을 경로·HTTP 메서드 순으로 처리하므로 테스트 실행 순서나 파일 순서와 상관없이 같은 결과가 나온다.
     *
     * @return 빌드 로그에 남길 경고 (고정 id 가 겹침, 한 operation 에 다른 고정 id 가 여럿)
     */
    static List<String> operationIds(Map<String, Object> document, List<Resource> resources) {
        List<String> warnings = new ArrayList<>();
        List<Slot> slots = slots(document, resources, warnings);
        Set<String> used = new HashSet<>();
        Map<String, List<String>> fixedOwners = new TreeMap<>();

        for (Slot slot : slots) {
            if (slot.kind == Kind.FIXED) {
                fixedOwners.computeIfAbsent(slot.id, k -> new ArrayList<>()).add(slot.key);
                used.add(slot.id);
                slot.operation.put(OPERATION_ID, slot.id);
            }
        }

        fixedOwners.forEach((id, owners) -> {
            if (owners.size() > 1) {
                warnings.add("'" + id + "' is set on several operations (" + String.join(", ", owners)
                    + "). Give each operation its own id; OpenAPI requires operationIds to be unique.");
            }
        });

        Set<String> collided = collidedRuleIds(slots, used);

        for (Slot slot : slots) {
            if (slot.kind == Kind.FIXED) {
                continue;
            }

            boolean qualify = slot.kind == Kind.RULE && slot.qualified != null && collided.contains(slot.id);
            String base = qualify ? slot.qualified : slot.id;
            String separator = slot.kind == Kind.RULE ? "" : "-";
            String unique = base;

            for (int n = 2; !used.add(unique); n++) {
                unique = base + separator + n;
            }

            slot.operation.put(OPERATION_ID, unique);
        }

        return warnings;
    }

    /** 규칙 id 중 다른 operation 의 규칙 id 나 고정 id 와 같은 것. */
    private static Set<String> collidedRuleIds(List<Slot> slots, Set<String> fixedIds) {
        Map<String, Integer> counts = new HashMap<>();

        slots.stream()
            .filter(slot -> slot.kind == Kind.RULE)
            .forEach(slot -> counts.merge(slot.id, 1, Integer::sum));

        Set<String> collided = new HashSet<>(fixedIds);

        counts.forEach((id, count) -> {
            if (count > 1) {
                collided.add(id);
            }
        });

        return collided;
    }

    /** id 를 정할 operation 들. 경로 순, 같은 경로는 HTTP 메서드 순이다. id 도 후보도 없는 operation 은 건드리지 않는다. */
    private static List<Slot> slots(Map<String, Object> document, List<Resource> resources, List<String> warnings) {
        Map<String, List<Resource>> byOperation = new HashMap<>();

        for (Resource resource : resources) {
            byOperation.computeIfAbsent(key(resource.method(), resource.path()), k -> new ArrayList<>())
                .add(resource);
        }

        List<Slot> slots = new ArrayList<>();
        Map<String, Object> paths = SpecMaps.map(document, SpecMaps.PATHS);

        if (paths == null) {
            return slots;
        }

        for (String path : new TreeSet<>(paths.keySet())) {
            for (String method : OpenApiSpecFiles.OPERATION_METHODS) {
                Map<String, Object> operation = SpecMaps.map(SpecMaps.map(paths.get(path)), method);

                if (operation != null) {
                    String key = key(method, path);
                    Slot slot = slot(operation, key, byOperation.getOrDefault(key, List.of()), warnings);

                    if (slot != null) {
                        slots.add(slot);
                    }
                }
            }
        }

        return slots;
    }

    /** operation 하나의 id 출처. 여러 테스트가 문서화했으면 대표 조각(성공 응답 우선)의 후보를 쓴다. */
    private static Slot slot(Map<String, Object> operation, String key, List<Resource> merged, List<String> warnings) {
        List<Map<String, Object>> candidates = merged.stream()
            .sorted(PrimaryResources.PRIMARY_FIRST)
            .map(Resource::operationIdCandidate)
            .filter(Objects::nonNull)
            .toList();
        List<String> fixed = candidates.stream()
            .filter(c -> Boolean.TRUE.equals(c.get(FIXED)))
            .map(c -> SpecMaps.string(c, ID))
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        if (fixed.size() > 1) {
            warnings.add(key + " has different operationIds " + fixed + " from several tests. Using '" + fixed.get(0)
                + "'; set the same id in every test of this operation.");
        }

        if (!fixed.isEmpty()) {
            return new Slot(operation, key, Kind.FIXED, fixed.get(0), null);
        }

        Map<String, Object> rule = candidates.stream()
            .filter(c -> SpecMaps.string(c, ID) != null)
            .findFirst()
            .orElse(null);

        if (rule != null) {
            return new Slot(operation, key, Kind.RULE, SpecMaps.string(rule, ID), SpecMaps.string(rule, QUALIFIED));
        }

        String id = SpecMaps.string(operation, OPERATION_ID);

        return id == null ? null : new Slot(operation, key, Kind.TEST, normalize(id), null);
    }

    /** 경고와 정렬에 쓰는 operation 이름 ({@code GET /users/{id}}). */
    private static String key(String method, String path) {
        return (method == null ? "" : method.toUpperCase(Locale.ROOT)) + " " + path;
    }

    /** id 의 출처: 고정(그대로), 규칙(겹치면 qualified·숫자), 테스트 이름(다듬고 겹치면 {@code -n}). */
    private enum Kind { FIXED, RULE, TEST }

    /** operationId 를 정할 operation 하나. */
    private static final class Slot {
        private final Map<String, Object> operation;
        private final String key;
        private final Kind kind;
        private final String id;
        private final String qualified;

        private Slot(Map<String, Object> operation, String key, Kind kind, String id, String qualified) {
            this.operation = operation;
            this.key = key;
            this.kind = kind;
            this.id = id;
            this.qualified = qualified;
        }
    }

    /**
     * {@code a/b/} → {@code a.b}. 글자·숫자·{@code . _ -} 밖의 문자(공백·기호)는 {@code -} 로.
     * 글자에는 한글 같은 비영어 글자도 들어간다. Kotest 테스트 이름이나 JUnit 백틱 메서드 이름으로 만든 스니펫 디렉터리
     * ({@code spec/사용자-조회})의 operationId 가 기호만 남거나({@code spec.---}) 다른 테스트와 겹치지 않게 한다.
     */
    static String normalize(String operationId) {
        String trimmed = operationId.replaceAll("[/\\-]+$", "");
        String normalized = trimmed.replace('/', '.')
            .replaceAll("[^\\p{L}\\p{M}\\p{N}._-]+", "-");

        return normalized.isEmpty() ? "operation" : normalized;
    }
}
