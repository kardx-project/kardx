package io.github.kardx.tooling.internal.aggregate;

import java.util.Map;

/** 이름이 겹친 컴포넌트에 줄 새 이름 ({@code :member} 의 {@code Item} → {@code MemberItem}). */
final class ComponentNames {
    private ComponentNames() {
    }

    /** 합친 것, 이 프로젝트 자신의 것, 이미 바꾼 이름과 겹치지 않는 이름. */
    static String uniqueName(String base, Map<String, Object> taken, Map<String, Object> own,
        Map<String, String> renamed) {
        String name = base;

        for (int i = 2; taken.containsKey(name) || own.containsKey(name) || renamed.containsValue(name); i++) {
            name = base + i;
        }

        return name;
    }

    /** {@code services/order-api} → {@code ServicesOrderApi}. */
    static String pascal(String project) {
        StringBuilder out = new StringBuilder();

        for (String word : project.split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty()) {
                out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
            }
        }

        return out.toString();
    }

    static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
