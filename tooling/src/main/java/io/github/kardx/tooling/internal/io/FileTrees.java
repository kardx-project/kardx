package io.github.kardx.tooling.internal.io;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 디렉터리 아래 파일을 Ant 꼴 패턴({@code **}/resource.json, {@code .kardx/endpoints/*.json})으로 찾고, 디렉터리를
 * 지우거나 복사한다. Gradle 의 {@code fileTree} · {@code Sync} 를 쓰지 못하는 Maven 플러그인과 명령줄이 쓴다.
 */
public final class FileTrees {
    private static final String ANY_DIRECTORY = "**/";

    private FileTrees() {
    }

    /**
     * {@code root} 아래에서 패턴 중 하나에 맞는 파일 (상대 경로, {@code /} 구분). 경로 순서로 정렬한다. {@code root} 가 없으면 빈 목록.
     * Ant 처럼 {@code **}/x 는 {@code root} 바로 아래의 x 에도 맞는다.
     */
    public static List<File> find(File root, List<String> patterns) {
        return find(root, patterns, List.of());
    }

    /** {@link #find(File, List)} 에서 {@code excludes} 패턴에 맞는 파일을 뺀다. */
    public static List<File> find(File root, List<String> patterns, List<String> excludes) {
        if (!root.isDirectory()) {
            return List.of();
        }

        List<PathMatcher> included = matchers(patterns);
        List<PathMatcher> excluded = matchers(excludes);
        Path base = root.toPath();

        try (Stream<Path> files = Files.walk(base)) {
            return files.filter(Files::isRegularFile)
                .filter(file -> matches(included, base.relativize(file)))
                .filter(file -> !matches(excluded, base.relativize(file)))
                .sorted(Comparator.comparing(file -> base.relativize(file).toString()))
                .map(Path::toFile)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code source} 아래 패턴에 맞는 파일을 같은 상대 경로로 {@code target} 에 복사한다. {@code target} 은 먼저 비운다. */
    public static void sync(File source, List<String> patterns, File target) {
        delete(target);

        Path base = source.toPath();

        for (File file : find(source, patterns)) {
            TextFiles.copy(file, target.toPath().resolve(base.relativize(file.toPath())).toFile());
        }

        if (!target.isDirectory() && !target.mkdirs()) {
            throw new UncheckedIOException(new IOException("Could not create " + target));
        }
    }

    /** 디렉터리(또는 파일)를 통째로 지운다. 없으면 아무것도 하지 않는다. */
    public static void delete(File target) {
        if (!target.exists()) {
            return;
        }

        try (Stream<Path> files = Files.walk(target.toPath())) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<PathMatcher> matchers(List<String> patterns) {
        List<PathMatcher> matchers = new ArrayList<>();

        for (String pattern : patterns) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + pattern));

            if (pattern.startsWith(ANY_DIRECTORY)) {
                String topLevel = pattern.substring(ANY_DIRECTORY.length());

                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + topLevel));
            }
        }

        return matchers;
    }

    private static boolean matches(List<PathMatcher> matchers, Path relative) {
        // 패턴은 / 로 쓴다. Windows 에서도 같게 맞추려고 / 로 바꾼 경로로 다시 만든다
        Path normalized = Path.of(relative.toString().replace(File.separatorChar, '/'));

        return matchers.stream().anyMatch(matcher -> matcher.matches(normalized));
    }
}
