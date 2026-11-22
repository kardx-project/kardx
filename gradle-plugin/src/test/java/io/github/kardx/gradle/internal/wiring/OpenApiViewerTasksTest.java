package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.tooling.ToolingException;
import java.io.File;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiViewerTasksTest {
    @Test
    void rejectsPathsOutsideDocs(@TempDir File dir) {
        Project project = ProjectBuilder.builder()
            .withProjectDir(dir)
            .build();
        KardxExtension.OpenApiViewer viewer =
            project.getObjects().newInstance(KardxExtension.class).getOpenApiViewer();

        viewer.getFileName().set("api/reference");
        assertEquals("api/reference.html", OpenApiViewerTasks.fileName(viewer));

        for (String invalid : new String[] {"", "/abs.html", "../up.html", "dir/"}) {
            viewer.getFileName().set(invalid);
            assertThrows(ToolingException.class, () -> OpenApiViewerTasks.fileName(viewer), invalid);
        }
    }
}
