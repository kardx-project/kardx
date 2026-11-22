package io.github.kardx.tooling.internal.aggregate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.kardx.tooling.ToolingException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 집계 결과의 프로젝트별 디렉터리 이름 ({@link ProjectDirectories}). */
class ProjectDirectoriesTest {

    @Test
    void namesFollowTheProjectPath() {
        assertEquals("services/member", ProjectDirectories.directoryName(":services:member"));
        assertEquals("root", ProjectDirectories.directoryName(":"));
    }

    /** {@code :root} 와 루트 프로젝트는 둘 다 {@code root/} 다. 한쪽 문서가 다른 쪽을 덮어쓰므로 알리고 실패한다. */
    @Test
    void projectsThatShareADirectoryFail() {
        ToolingException error = assertThrows(ToolingException.class,
            () -> ProjectDirectories.checkDistinct(List.of(":", ":order", ":root")));

        assertEquals("apiDocsAggregate: projects : and :root both go to the directory root/ of the aggregated docs. "
            + "Aggregate only one of them.", error.getMessage());
        assertDoesNotThrow(() -> ProjectDirectories.checkDistinct(List.of(":", ":services", ":services:member")));
    }
}
