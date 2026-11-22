package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.openapi.SchemaUsage.REQUEST;
import static io.github.kardx.tooling.internal.openapi.SchemaUsage.RESPONSE;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 이름이 같은데 구조가 다른 자동 스키마 이름을 나눈다. 서로 다른 타입끼리 겹치면 바깥 클래스·패키지 이름으로 한정하고,
 * 같은 타입의 요청과 응답 모양이 다르면 요청 쪽에 {@code Request} 를 붙인다. 직접 정한 이름은 바꾸지 않는다.
 */
final class SchemaRenaming {
    private SchemaRenaming() {
    }

    /**
     * 같은 이름을 쓰는 서로 다른 묶음(({@link SchemaUsage#identity}))에 다른 이름을 준다. 직접 정한 이름은 그대로 두고 자동 이름만 바꾼다.
     *
     * @return 이름을 바꿨으면 true
     */
    static boolean separate(String name, List<SchemaUsage> group, List<String> changes, Set<Path> touched) {
        Map<String, Set<Set<String>>> structuresByIdentity = group.stream().collect(Collectors.groupingBy(
            SchemaUsage::identity, Collectors.mapping(SchemaUsage::structure, Collectors.toSet())));

        // 한 묶음 안에서만 모양이 다르면 이름을 나누지 않고 합친다 (merge)
        if (new LinkedHashSet<>(structuresByIdentity.values()).size() <= 1) {
            return false;
        }

        Map<String, List<SchemaUsage>> byType = new TreeMap<>();
        boolean userNamed = false;

        for (SchemaUsage usage : group) {
            if (usage.automatic()) {
                byType.computeIfAbsent(usage.type(), t -> new ArrayList<>()).add(usage);
            } else {
                userNamed = true;
            }
        }

        if (byType.isEmpty()) {
            return false;
        }

        Map<String, String> typeNames = qualifiedNames(byType, userNamed ? name : null);
        boolean renamed = false;

        for (Map.Entry<String, List<SchemaUsage>> typeEntry : byType.entrySet()) {
            renamed |= rename(typeNames.get(typeEntry.getKey()), typeEntry.getValue(), changes, touched);
        }

        return renamed;
    }

    /**
     * 한 타입의 조각들에 [typeName] 을 준다. 요청과 응답 모양이 다르면 요청 쪽에 {@code Request} 를 붙인다.
     *
     * @return 이름을 바꿨으면 true
     */
    private static boolean rename(String typeName, List<SchemaUsage> usages, List<String> changes, Set<Path> touched) {
        Set<Set<String>> responses = structures(usages, RESPONSE);
        Set<Set<String>> requests = structures(usages, REQUEST);
        boolean sidesDiffer = !responses.isEmpty() && !requests.isEmpty() && Collections.disjoint(responses, requests);
        boolean renamed = false;

        for (SchemaUsage usage : usages) {
            String target = sidesDiffer && usage.side().equals(REQUEST) ? typeName + "Request" : typeName;

            if (!target.equals(usage.name())) {
                changes.add(usage.name() + " → " + target + " (" + usage.type() + ", " + usage.side() + ")");
                usage.rename(target);
                touched.add(usage.file());
                renamed = true;
            }
        }

        return renamed;
    }

    private static Set<Set<String>> structures(List<SchemaUsage> usages, String side) {
        return usages.stream()
            .filter(usage -> usage.side().equals(side))
            .map(SchemaUsage::structure)
            .collect(Collectors.toSet());
    }

    /**
     * 타입마다 이름. 타입이 하나면 원래 이름이고, 여럿이면 모든 타입의 이름이 달라지는 가장 짧은 한정 단계를 쓴다.
     * [reserved] 는 직접 정한 이름이라 자동 이름이 쓸 수 없는 이름이다.
     */
    private static Map<String, String> qualifiedNames(Map<String, List<SchemaUsage>> byType, String reserved) {
        int levels = byType.values().stream()
            .mapToInt(usages -> usages.get(0).candidates().size())
            .max()
            .orElse(1);

        for (int level = 0; level < levels; level++) {
            Map<String, String> names = new LinkedHashMap<>();

            for (Map.Entry<String, List<SchemaUsage>> entry : byType.entrySet()) {
                List<String> candidates = entry.getValue().get(0).candidates();
                names.put(entry.getKey(), candidates.get(Math.min(level, candidates.size() - 1)));
            }

            boolean distinct = new LinkedHashSet<>(names.values()).size() == names.size();

            if (distinct && !names.containsValue(reserved)) {
                return names;
            }
        }

        // 한정해도 겹치면 타입 이름순으로 번호를 붙인다
        Map<String, String> numbered = new LinkedHashMap<>();
        int index = reserved == null ? 1 : 2;

        for (Map.Entry<String, List<SchemaUsage>> entry : byType.entrySet()) {
            List<String> candidates = entry.getValue().get(0).candidates();
            numbered.put(entry.getKey(), candidates.get(candidates.size() - 1) + (index == 1 ? "" : index));
            index++;
        }

        return numbered;
    }
}
