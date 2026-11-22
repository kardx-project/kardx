package io.github.kardx.gradle.internal.wiring;

import org.gradle.api.GradleException;
import org.gradle.util.GradleVersion;

/**
 * 플러그인이 쓰는 Gradle API 의 최소 버전을 확인한다. 가장 새 API 는 이유를 남기는 {@code Task.onlyIf(String, Spec)} (7.6) 이다.
 * 미만이면 알 수 없는 {@code NoSuchMethodError} 대신 어떤 버전이 필요한지 알리고 실패한다.
 */
public final class GradleVersionCheck {
    /** 지원하는 가장 낮은 Gradle. README 의 호환 표와 같다. */
    public static final String MINIMUM_VERSION = "7.6";
    static final GradleVersion MINIMUM = GradleVersion.version(MINIMUM_VERSION);

    private GradleVersionCheck() {
    }

    public static void requireSupported() {
        requireSupported(GradleVersion.current());
    }

    static void requireSupported(GradleVersion current) {
        if (current.getBaseVersion().compareTo(MINIMUM) < 0) {
            throw new GradleException("The io.github.kardx-project plugin requires Gradle " + MINIMUM_VERSION
                + " or newer, but this build runs Gradle " + current.getVersion() + ". Upgrade the wrapper: "
                + "./gradlew wrapper --gradle-version <version>");
        }
    }
}
