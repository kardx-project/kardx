package io.github.kardx.tooling.internal.io;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** UTF-8 텍스트 파일 읽기·쓰기와 복사. 쓸 때 상위 디렉터리가 없으면 만든다. */
public final class TextFiles {
    private TextFiles() {
    }

    /** UTF-8 텍스트로 읽는다. */
    public static String read(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** UTF-8 텍스트로 쓴다 (있으면 덮어쓴다). */
    public static void write(File file, String text) {
        try {
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 파일을 그대로 복사한다 (있으면 덮어쓴다). */
    public static void copy(File source, File target) {
        try {
            Files.createDirectories(target.toPath().getParent());
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
