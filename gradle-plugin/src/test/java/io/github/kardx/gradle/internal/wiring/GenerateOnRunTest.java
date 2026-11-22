package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GenerateOnRunTest {

    @Test
    void requestedTaskNamesDropsOptionsAndTheirValues() {
        assertEquals(List.of("bootRun"),
            GenerateOnRun.requestedTaskNames(List.of(":app:bootRun", "--args", "--spring.profiles.active=local")));
        assertEquals(List.of("bootRun"), GenerateOnRun.requestedTaskNames(List.of("bootRun", "--args=foo")));
        assertEquals(List.of("test"), GenerateOnRun.requestedTaskNames(List.of("test", "--tests", "FooTest")));
        assertEquals(List.of("bootRun", "bootRun"),
            GenerateOnRun.requestedTaskNames(List.of(":a:bootRun", "--debug-jvm", ":b:bootRun")));
        assertEquals(List.of("classes", "testClasses"),
            GenerateOnRun.requestedTaskNames(List.of(":classes", ":testClasses", "--offline")));
    }

    @Test
    void onlyMainBuildsCountAsIdeAppRuns() {
        assertTrue(GenerateOnRun.isIdeAppRun(List.of("classes")));
        assertTrue(GenerateOnRun.isIdeAppRun(List.of("classes", "processResources")));
        // IntelliJ 가 테스트 하나를 자기 러너로 돌릴 때의 빌드. 여기서 문서를 만들면 전체 테스트가 먼저 돈다
        assertFalse(GenerateOnRun.isIdeAppRun(List.of("classes", "testClasses")));
        assertFalse(GenerateOnRun.isIdeAppRun(List.of("testClasses")));
        assertFalse(GenerateOnRun.isIdeAppRun(List.of()));
    }
}
