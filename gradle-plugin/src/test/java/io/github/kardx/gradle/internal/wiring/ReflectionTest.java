package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class ReflectionTest {
    private static final IllegalStateException FAILURE = new IllegalStateException("boom");

    /** 리플렉션 대상. getMethod 는 public 멤버만 찾으므로 public 이어야 한다. */
    public static class Target {
        public String getName() {
            return "epages";
        }

        public String getBroken() {
            throw FAILURE;
        }
    }

    @Test
    void returnsValueOfExistingMethod() {
        assertEquals("epages", Reflection.invokeIfPresent(new Target(), "getName"));
    }

    @Test
    void returnsNullOnlyWhenMethodIsMissing() {
        assertNull(Reflection.invokeIfPresent(new Target(), "getOutputDirectory"));
    }

    @Test
    void rethrowsFailureInsideMethodWithCause() {
        GradleException thrown = assertThrows(
            GradleException.class, () -> Reflection.invokeIfPresent(new Target(), "getBroken"));

        assertSame(FAILURE, thrown.getCause());
        assertTrue(thrown.getMessage().contains("getBroken"), thrown.getMessage());
    }
}
