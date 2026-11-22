package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.io.OutputDirectoryGuard;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 앱 리소스로 복사할 때 앱 파일은 지우지 않고 이 플러그인이 지난번에 쓴 파일만 정리한다 ({@link AppDocsCopy}). */
class AppDocsCopyTest {
    @TempDir
    File root;

    @Test
    void keepsTheAppsOwnFilesAndRemovesOnlyDocsThatDisappeared() throws IOException {
        File docs = new File(root, "api-docs");
        File resources = new File(root, "resources");
        File manifest = new File(root, "kardx/app-docs-files.txt");

        write(new File(resources, "application.yml"), "app");
        write(new File(resources, "static/docs/custom.css"), "app");
        write(new File(docs, "index.html"), "v1");
        write(new File(docs, "old/page.html"), "v1");
        write(new File(docs, OutputDirectoryGuard.MARKER), "");

        AppDocsCopy.copy(docs, resources, "static/docs", manifest);

        assertEquals("v1", read(new File(resources, "static/docs/index.html")));
        assertFalse(new File(resources, "static/docs/" + OutputDirectoryGuard.MARKER).exists());

        Files.delete(new File(docs, "old/page.html").toPath());
        write(new File(docs, "index.html"), "v2");

        AppDocsCopy.copy(docs, resources, "static/docs", manifest);

        assertEquals("v2", read(new File(resources, "static/docs/index.html")));
        assertFalse(new File(resources, "static/docs/old").exists(), "지운 문서와 빈 디렉터리는 정리한다");
        assertTrue(new File(resources, "static/docs/custom.css").isFile(), "앱이 둔 파일은 남긴다");
        assertTrue(new File(resources, "application.yml").isFile());
    }

    @Test
    void movingThePathOrTurningItOffRemovesEarlierCopies() throws IOException {
        File docs = new File(root, "api-docs");
        File resources = new File(root, "resources");
        File manifest = new File(root, "kardx/app-docs-files.txt");

        write(new File(docs, "index.html"), "docs");
        write(new File(resources, "static/app.js"), "app");

        AppDocsCopy.copy(docs, resources, "static/docs", manifest);
        AppDocsCopy.copy(docs, resources, "static/manual", manifest);

        assertFalse(new File(resources, "static/docs").exists(), "이전 경로의 복사본");
        assertTrue(new File(resources, "static/manual/index.html").isFile());

        AppDocsCopy.removePrevious(resources, manifest);

        assertFalse(new File(resources, "static/manual").exists());
        assertFalse(manifest.exists());
        assertTrue(new File(resources, "static/app.js").isFile(), "앱 파일과 그 디렉터리는 남긴다");
    }

    @Test
    void ignoresManifestLinesOutsideTheResources() throws IOException {
        File outside = new File(root, "keep.txt");
        File resources = new File(root, "resources");
        File manifest = new File(root, "kardx/app-docs-files.txt");

        write(outside, "keep");
        write(manifest, "../keep.txt\n");

        AppDocsCopy.removePrevious(resources, manifest);

        assertTrue(outside.isFile());
    }

    private static String read(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    private static void write(File file, String text) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text);
    }
}
