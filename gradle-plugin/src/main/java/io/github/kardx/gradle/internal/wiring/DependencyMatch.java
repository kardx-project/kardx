package io.github.kardx.gradle.internal.wiring;

import java.util.Set;
import org.gradle.api.artifacts.Dependency;

/** 선언된 의존성을 좌표(그룹·이름)로 찾는다. 해석하지 않고 선언만 본다. */
final class DependencyMatch {
    private DependencyMatch() {
    }

    /** {@code declared} 에 {@code group} 의 {@code names} 중 하나가 있는가. 같은 빌드의 프로젝트 의존성은 프로젝트 이름으로 본다. */
    static boolean declares(Iterable<? extends Dependency> declared, String group, Set<String> names) {
        for (Dependency dependency : declared) {
            if (group.equals(dependency.getGroup()) && names.contains(dependency.getName())) {
                return true;
            }
        }

        return false;
    }

    static boolean declares(Iterable<? extends Dependency> declared, String group, String name) {
        return declares(declared, group, Set.of(name));
    }
}
