package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.GradleException;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.Test;

class GradleVersionCheckTest {

    @Test
    void failsBelowMinimumWithReadableMessage() {
        GradleException e = assertThrows(GradleException.class,
            () -> GradleVersionCheck.requireSupported(GradleVersion.version("7.5.1")));

        assertTrue(e.getMessage().contains("requires Gradle 7.6 or newer, but this build runs Gradle 7.5.1"),
            e.getMessage());
    }

    @Test
    void acceptsMinimumAndNewer() {
        for (String version : java.util.List.of("7.6", "7.6.4", "8.14.6", "9.2.1", "9.0-rc-1")) {
            assertDoesNotThrow(() -> GradleVersionCheck.requireSupported(GradleVersion.version(version)), version);
        }

        assertDoesNotThrow(() -> GradleVersionCheck.requireSupported());
    }
}
