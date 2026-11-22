package io.github.kardx.tooling.internal.sourcedocs;

import java.util.Locale;
import java.util.Set;

/**
 * 파라미터 없는 Java 메서드가 읽는 속성 이름: record 접근자({@code name()}) → {@code name}, {@code getUserName} →
 * {@code userName}, {@code isActive} → {@code active}. 두 글자가 모두 대문자면 그대로 둔다 ({@code getURL} → {@code URL},
 * {@code java.beans.Introspector} 와 같다).
 */
final class AccessorNames {
    private static final String GET = "get";
    private static final String IS = "is";

    private AccessorNames() {
    }

    /** @return 접근자가 아니면 null */
    static String property(String method, Set<String> recordComponents) {
        if (recordComponents.contains(method)) {
            return method;
        }

        if (isAccessor(method, GET)) {
            return decapitalize(method.substring(GET.length()));
        }

        return isAccessor(method, IS) ? decapitalize(method.substring(IS.length())) : null;
    }

    private static boolean isAccessor(String method, String prefix) {
        return method.length() > prefix.length() && method.startsWith(prefix)
            && Character.isUpperCase(method.charAt(prefix.length()));
    }

    private static String decapitalize(String name) {
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            return name;
        }

        return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
    }
}
