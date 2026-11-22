package io.github.kardx.tooling.internal.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.ToolingException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 플러그인이 통째로 지우는 디렉터리는 소스·프로젝트 디렉터리나 사용자 파일이 있는 곳이면 안 된다 ({@link OutputDirectoryGuard}). */
class OutputDirectoryGuardTest {
    private static final String PROPERTY = "kardx.asciidocDirectory";

    @TempDir
    File root;

    private File project;
    private OutputDirectoryGuard guard;

    @BeforeEach
    void createGuard() {
        project = new File(root, "app");
        guard = new OutputDirectoryGuard(project, root, new File(project, "build"),
            List.of(new File(project, "src"), new File(project, "src/docs/asciidoc")));
    }

    @Test
    void refusesProjectBuildAndSourceDirectoriesAndTheirParents() {
        for (String path : List.of("", "..", "build", "src", "src/docs/asciidoc", "src/docs/asciidoc/generated",
            "src/main/resources/static/docs")) {
            ToolingException error = assertThrows(ToolingException.class,
                () -> guard.check(new File(project, path), PROPERTY), path);

            assertTrue(error.getMessage().startsWith(PROPERTY + " must not be"), error.getMessage());
            assertTrue(error.getMessage().contains("because the plugin deletes it before writing"), error.getMessage());
        }
    }

    @Test
    void allowsAnyDirectoryInsideTheBuildDirectory() throws IOException {
        File docs = new File(project, "build/custom/docs");

        write(new File(docs, "user.txt"));

        assertDoesNotThrow(() -> guard.check(docs, PROPERTY));
        guard.mark(docs);
        assertFalse(new File(docs, OutputDirectoryGuard.MARKER).exists(), "빌드 디렉터리 안에는 표시 파일을 남기지 않는다");
    }

    @Test
    void outsideTheBuildDirectoryOnlyEmptyOrMarkedDirectories() throws IOException {
        File docs = new File(project, "docs");

        assertDoesNotThrow(() -> guard.check(docs, PROPERTY), "없는 디렉터리");
        write(new File(docs, "README.md"));

        ToolingException error = assertThrows(ToolingException.class, () -> guard.check(docs, PROPERTY));

        assertTrue(error.getMessage().startsWith(PROPERTY + " points to " + docs.toPath().toAbsolutePath()
            + ", which is outside the build directory and holds files the plugin did not write."), error.getMessage());
        assertTrue(error.getMessage().contains("create an empty file named '.kardx-output'"),
            error.getMessage());

        guard.mark(docs);

        assertTrue(new File(docs, OutputDirectoryGuard.MARKER).isFile());
        assertDoesNotThrow(() -> guard.check(docs, PROPERTY), "플러그인이 표시한 디렉터리");
    }

    @Test
    void refusesAnExistingFileInsideOrOutsideTheBuildDirectory() throws IOException {
        for (String path : List.of("docs/index.html", "build/custom/index.html")) {
            File file = new File(project, path);

            write(file);

            ToolingException error = assertThrows(ToolingException.class, () -> guard.check(file, PROPERTY), path);

            assertTrue(error.getMessage().equals(PROPERTY + " points to " + file.toPath().toAbsolutePath()
                + ", which is a file; it must be a directory"), error.getMessage());
            assertTrue(file.isFile(), "파일을 지우지 않는다");
        }
    }

    @Test
    void siblingNamesThatOnlyShareAPrefixAreNotSourceDirectories() {
        assertDoesNotThrow(() -> guard.check(new File(project, "srcx"), PROPERTY));
    }

    private static void write(File file) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), "x");
    }
}
