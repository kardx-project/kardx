package io.github.kardx.tooling.internal.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.ToolingException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 결과 파일 경로는 문서 디렉터리 안의 상대 경로여야 한다 ({@link OutputPaths}). */
class OutputPathsTest {
    private static final String PROPERTY = "kardx.asyncApi.fileName";
    private static final String EXAMPLES = "'asyncapi' or 'async/index'";

    @Test
    void acceptsRelativePathsAndNormalizesBackslashes() {
        assertEquals("async/index", OutputPaths.relativePath("async\\index", "async\\index", PROPERTY, EXAMPLES));
        assertEquals("asyncapi", OutputPaths.relativePath("asyncapi", "asyncapi", PROPERTY, EXAMPLES));
    }

    @Test
    void rejectsPathsOutsideTheDocsDirectoryIncludingWindowsDrives() {
        for (String path : List.of("", "/x", "x/", "../x", "a/../../x", "\\\\server\\x", "C:/x", "c:\\x", "C:x")) {
            ToolingException error = assertThrows(ToolingException.class,
                () -> OutputPaths.relativePath(path, path, PROPERTY, EXAMPLES), path);

            assertTrue(error.getMessage().startsWith(PROPERTY + " must be a relative path like " + EXAMPLES),
                error.getMessage());
        }
    }
}
