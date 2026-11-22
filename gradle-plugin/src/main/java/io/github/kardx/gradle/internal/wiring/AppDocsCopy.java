package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.tooling.internal.io.OutputDirectoryGuard;
import io.github.kardx.tooling.internal.io.TextFiles;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 문서를 앱 리소스({@code build/resources/main/<bootJar.path>})로 복사한다. 앱이 같은 디렉터리에 둔 파일을 지우지 않도록
 * 동기화(Sync) 대신, 지난 실행에 이 플러그인이 쓴 파일 목록({@code manifest})에 있으면서 이번 문서에 없는 파일만 지운다.
 * {@code bootJar.path} 를 바꾸면 이전 경로에 복사한 문서도 이 목록으로 지운다.
 */
final class AppDocsCopy {
    private AppDocsCopy() {
    }

    /**
     * @param docs      문서 디렉터리 ({@code apiDocs} 결과)
     * @param resources 앱 리소스 디렉터리 ({@code build/resources/main})
     * @param path      확인한 {@code bootJar.path} ({@link BootJarPath#of})
     * @param manifest  지난 실행에 쓴 파일 목록 (리소스 디렉터리 기준 경로, 한 줄에 하나)
     */
    static void copy(File docs, File resources, String path, File manifest) {
        Path root = root(resources);
        Path source = docs.toPath();
        Set<String> written = new LinkedHashSet<>();

        for (Path file : files(source)) {
            Path target = root.resolve(path).resolve(source.relativize(file).toString());

            copyFile(file, target);
            written.add(root.relativize(target).toString().replace('\\', '/'));
        }

        removeStale(root, manifest, written);
        TextFiles.write(manifest, String.join("\n", written));
    }

    /** 문서를 앱에 넣지 않게 되면({@code bootJar.enabled = false}) 지난 실행이 복사한 문서와 목록을 지운다. */
    static void removePrevious(File resources, File manifest) {
        removeStale(root(resources), manifest, Set.of());

        try {
            Files.deleteIfExists(manifest.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + manifest, e);
        }
    }

    /** 지난 목록에 있고 [kept] 에 없는 파일을 지운다. */
    private static void removeStale(Path root, File manifest, Set<String> kept) {
        for (String previous : previous(manifest)) {
            Path stale = root.resolve(previous).normalize();

            // 목록은 빌드 디렉터리 안의 파일이지만, 리소스 디렉터리 밖을 가리키는 줄은 믿지 않는다
            if (!kept.contains(previous) && stale.startsWith(root) && !stale.equals(root)) {
                deleteWithEmptyParents(stale, root);
            }
        }
    }

    private static Path root(File resources) {
        return resources.toPath()
            .toAbsolutePath()
            .normalize();
    }

    private static List<Path> files(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile)
                .filter(file -> !file.getFileName().toString().equals(OutputDirectoryGuard.MARKER))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + directory, e);
        }
    }

    private static List<String> previous(File manifest) {
        if (!manifest.isFile()) {
            return List.of();
        }

        return TextFiles.read(manifest)
            .lines()
            .filter(line -> !line.isBlank())
            .toList();
    }

    private static void copyFile(Path file, Path target) {
        try {
            // 같은 내용이면 쓰지 않는다 (문서가 그대로인 빌드에서 디스크 쓰기를 줄인다)
            if (Files.isRegularFile(target) && Files.mismatch(file, target) == -1) {
                return;
            }

            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy " + file + " to " + target, e);
        }
    }

    /** 파일을 지우고, 그래서 빈 상위 디렉터리를 리소스 디렉터리 바로 아래까지 지운다. */
    private static void deleteWithEmptyParents(Path file, Path root) {
        try {
            Files.deleteIfExists(file);

            Path parent = file.getParent();

            while (parent != null && !parent.equals(root) && isEmptyDirectory(parent)) {
                Files.delete(parent);
                parent = parent.getParent();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + file, e);
        }
    }

    private static boolean isEmptyDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }

        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }
}
