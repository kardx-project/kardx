package io.github.kardx.tooling.internal.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** openapi3 전에 지우는 지난 스펙은 이 플러그인이 쓴 이름뿐이다 ({@link PreviousSpecFiles}). */
class PreviousSpecFilesTest {
    @TempDir
    File root;

    @Test
    void deletesOnlySpecsAndVersionSpecsThePluginWrote() throws IOException {
        File specs = new File(root, "static/docs");
        File recorded = new File(root, "build/openapi-version-specs.txt");

        for (String name : List.of("openapi3.yaml", "openapi3.json", "openapi3-v1.yaml", "openapi3-v2.1.json",
            "openapi3-vbeta.yaml", "openapi3-validation.yaml", "openapi3-vendor.json", "other-v1.yaml", "index.html")) {
            write(new File(specs, name));
        }

        PreviousSpecFiles.record(recorded, List.of(new File(specs, "openapi3-vbeta.yaml")));

        int deleted = PreviousSpecFiles.delete(specs, "openapi3", recorded);

        assertEquals(5, deleted);
        assertFalse(recorded.exists(), "목록은 한 번 쓰고 지운다");

        for (String kept : List.of("openapi3-validation.yaml", "openapi3-vendor.json", "other-v1.yaml", "index.html")) {
            assertTrue(new File(specs, kept).isFile(), kept);
        }
    }

    @Test
    void recordedNamesMustBelongToTheSpecPrefix() throws IOException {
        File specs = new File(root, "specs");
        File recorded = new File(root, "list.txt");

        write(new File(specs, "keep.yaml"));
        Files.writeString(recorded.toPath(), "keep.yaml\n../outside.yaml\n");

        PreviousSpecFiles.delete(specs, "openapi3", recorded);

        assertTrue(new File(specs, "keep.yaml").isFile());
    }

    private static void write(File file) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), "x");
    }
}
