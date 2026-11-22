package io.github.kardx.gradle.internal.wiring;

import java.util.HashSet;
import java.util.Set;
import org.gradle.api.artifacts.Dependency;

/**
 * 프로젝트가 선언한 Kotest 판(5.x·6.x)과 그 판에 맞는 확장 아티팩트. {@code io.kotest} 그룹 의존성(BOM 포함)에 적힌 버전만 보고
 * 정한다. 어느 것에도 버전을 적지 않았거나(다른 BOM 이 관리) 5·6 이 섞였거나 다른 판이면 모르는 것으로 본다(null) — 억지로
 * 추론하지 않는다. 5.x 의 Spring 확장({@code io.kotest.extensions}) 은 버전 체계가 달라 보지 않는다.
 */
final class KotestVersionLine {
    private static final String KOTEST_GROUP = "io.kotest";

    private KotestVersionLine() {
    }

    /** Kotest 판에 맞는 확장 아티팩트 이름. 판을 알 수 없으면 null. */
    static String artifact(Iterable<? extends Dependency> declared) {
        Set<String> majors = new HashSet<>();

        for (Dependency dependency : declared) {
            String version = dependency.getVersion();

            if (KOTEST_GROUP.equals(dependency.getGroup()) && version != null && !version.isBlank()) {
                majors.add(major(version));
            }
        }

        if (majors.size() != 1) {
            return null;
        }

        return switch (majors.iterator().next()) {
            case "5" -> LibraryDependencies.KOTEST5;
            case "6" -> LibraryDependencies.KOTEST;
            default -> null;
        };
    }

    private static String major(String version) {
        int dot = version.indexOf('.');

        return dot < 0 ? version : version.substring(0, dot);
    }
}
