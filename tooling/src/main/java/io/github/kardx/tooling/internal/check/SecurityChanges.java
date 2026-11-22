package io.github.kardx.tooling.internal.check;

import static io.github.kardx.tooling.internal.spec.SpecMaps.listOrEmpty;
import static io.github.kardx.tooling.internal.spec.SpecMaps.mapOrEmpty;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * operation 의 보안 요구사항 변경 ({@link OpenApiBreakingChanges}). operation 의 {@code security} 가 없으면 문서 최상위 값을
 * 쓴다. 빈 요구사항({@code {}})이 대안으로 있으면 인증 없이도 부를 수 있는 것으로 본다.
 * <ul>
 *   <li>인증 없이 부를 수 있던 operation 이 인증을 요구하면 깨진다</li>
 *   <li>요구하는 보안 스키마가 바뀌면(이름 집합이 다르면) 깨진다. scope 변경은 보지 않는다</li>
 * </ul>
 */
final class SecurityChanges {
    private static final String SECURITY = "security";

    private SecurityChanges() {
    }

    /** 깨는 변경 메시지. 없으면 null. */
    static String compare(String operation, Map<?, ?> beforeDocument, Map<?, ?> before, Map<?, ?> afterDocument,
        Map<?, ?> after) {
        Object previous = effective(beforeDocument, before);
        Object next = effective(afterDocument, after);

        if (!secured(next)) {
            return null;
        }

        if (!secured(previous)) {
            return "Security requirement added: " + operation + " " + schemes(next);
        }

        Set<String> previousSchemes = schemes(previous);
        Set<String> nextSchemes = schemes(next);

        return previousSchemes.equals(nextSchemes)
            ? null
            : "Security scheme changed: " + operation + " (" + previousSchemes + " → " + nextSchemes + ")";
    }

    private static Object effective(Map<?, ?> document, Map<?, ?> operation) {
        return operation.containsKey(SECURITY) ? operation.get(SECURITY) : document.get(SECURITY);
    }

    /** 요구사항이 하나라도 있고 빈 대안이 없으면 인증이 필요하다. */
    private static boolean secured(Object security) {
        boolean any = false;

        for (Object requirement : listOrEmpty(security)) {
            if (mapOrEmpty(requirement).isEmpty()) {
                return false;
            }

            any = true;
        }

        return any;
    }

    private static Set<String> schemes(Object security) {
        Set<String> names = new TreeSet<>();

        listOrEmpty(security).forEach(requirement -> mapOrEmpty(requirement).keySet()
            .forEach(name -> names.add(String.valueOf(name))));

        return names;
    }
}
