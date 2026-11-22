package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

/** 문서를 넣을 앱 리소스 경로는 정적 위치 아래의 하위 디렉터리여야 한다 ({@link BootJarPath}). */
class BootJarPathTest {
    @Test
    void normalizesSeparatorsDotsAndTrailingSlashes() {
        assertEquals("static/docs", BootJarPath.of("static/docs"));
        assertEquals("static/docs", BootJarPath.of("./static//docs/"));
        assertEquals("public/api-docs", BootJarPath.of("public\\api-docs"));
        assertEquals("docs", BootJarPath.of("docs"));
    }

    @Test
    void rejectsEmptyAbsoluteAndEscapingPaths() {
        for (String path : List.of("", ".", "/", "./", "/static/docs", "../docs", "static/../../x", "C:/docs")) {
            GradleException error = assertThrows(GradleException.class, () -> BootJarPath.of(path), path);

            assertEquals("kardx.bootJar.path must be a directory inside the app's resources such as "
                + "'static/docs' or 'public/api-docs', got '" + path + "'", error.getMessage());
        }
    }

    @Test
    void rejectsSpringStaticRootsBecauseTheAppOwnsTheirFiles() {
        for (String path : List.of("static", "static/", "public", "resources", "META-INF", "META-INF/resources")) {
            GradleException error = assertThrows(GradleException.class, () -> BootJarPath.of(path), path);

            assertTrue(error.getMessage().startsWith("kardx.bootJar.path '" + path
                + "' is an application resource root"), error.getMessage());
            assertTrue(error.getMessage().endsWith("use a sub directory such as 'static/docs'."), error.getMessage());
        }
    }
}
