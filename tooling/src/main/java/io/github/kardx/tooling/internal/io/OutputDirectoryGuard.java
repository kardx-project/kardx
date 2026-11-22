package io.github.kardx.tooling.internal.io;

import io.github.kardx.tooling.ToolingException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 플러그인이 통째로 지우거나 동기화하는 디렉터리(사용자가 바꿀 수 있는 설정)를 지우기 전에 확인한다.
 * <ul>
 *   <li>항상 거부: 이미 있는 파일(디렉터리가 아닌 것), 프로젝트·루트 프로젝트·빌드 디렉터리 자체와 그 상위, 소스 디렉터리({@code src/}, AsciiDoc 원본) 안이나 그 상위</li>
 *   <li>빌드 디렉터리 안: 허용 (기본 위치는 모두 여기다)</li>
 *   <li>빌드 디렉터리 밖: 없거나 비었거나 표시 파일 {@value #MARKER} 이 있을 때만 허용. 플러그인이 쓴 뒤 표시 파일을 남긴다
 *       ({@link #mark(File)})</li>
 * </ul>
 * configuration cache 에 저장되도록 경로만 담는다.
 */
public final class OutputDirectoryGuard implements Serializable {
    /** 빌드 디렉터리 밖의 결과 디렉터리를 플러그인이 쓴 것이라고 표시하는 빈 파일. */
    public static final String MARKER = ".kardx-output";

    private static final long serialVersionUID = 1L;

    private final File projectDirectory;
    private final File rootDirectory;
    private final File buildDirectory;
    private final List<File> sourceDirectories;

    /**
     * @param sourceDirectories 사용자가 쓰는 파일이 있는 디렉터리 ({@code src}, AsciiDoc 원본 디렉터리)
     */
    public OutputDirectoryGuard(
        File projectDirectory, File rootDirectory, File buildDirectory, List<File> sourceDirectories) {
        this.projectDirectory = normalized(projectDirectory);
        this.rootDirectory = normalized(rootDirectory);
        this.buildDirectory = normalized(buildDirectory);
        // configuration cache 가 값을 저장하므로 JDK 불변 목록 대신 ArrayList 로 둔다
        this.sourceDirectories = sourceDirectories.stream()
            .map(OutputDirectoryGuard::normalized)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * [directory] 를 지워도 되는지 확인한다. 안 되면 [property] 이름을 담은 {@link ToolingException} 을 던진다.
     *
     * @param property 설정 이름 ({@code kardx.asciidocDirectory})
     */
    public void check(File directory, String property) {
        Path path = normalized(directory).toPath();

        // 설정 실수로 파일을 가리키면 그 파일을 지우고 디렉터리를 만들게 된다. 빌드 디렉터리 안이어도 거부한다
        if (Files.exists(path) && !Files.isDirectory(path)) {
            throw new ToolingException(property + " points to " + path + ", which is a file; it must be a directory");
        }

        if (contains(path, buildDirectory) || contains(path, projectDirectory) || contains(path, rootDirectory)) {
            throw refused(property, path, "must not be the project directory, the build directory or one of their "
                + "parents");
        }

        for (File source : sourceDirectories) {
            if (path.startsWith(source.toPath()) || contains(path, source)) {
                throw refused(property, path, "must not be inside or contain the source directory " + source);
            }
        }

        if (!path.startsWith(buildDirectory.toPath()) && !ownedOrEmpty(path)) {
            throw new ToolingException(property + " points to " + path + ", which is outside the build directory and "
                + "holds files the plugin did not write. The plugin deletes this directory before writing to it. "
                + "Use a directory under " + buildDirectory + ", empty it first, or create an empty file named '"
                + MARKER + "' in it to let the plugin replace its contents.");
        }
    }

    /** 빌드 디렉터리 밖이면 다음 실행이 알아보도록 [directory] 에 표시 파일을 남긴다. */
    public void mark(File directory) {
        File target = normalized(directory);

        if (target.toPath().startsWith(buildDirectory.toPath())) {
            return;
        }

        try {
            Files.createDirectories(target.toPath());

            Path marker = target.toPath().resolve(MARKER);

            if (!Files.exists(marker)) {
                Files.createFile(marker);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + MARKER + " in " + target, e);
        }
    }

    /** [directory] 를 지우면 [other] 도 지워진다 (같거나 상위). */
    private static boolean contains(Path directory, File other) {
        return other.toPath().startsWith(directory);
    }

    private static boolean ownedOrEmpty(Path directory) {
        if (Files.notExists(directory) || Files.exists(directory.resolve(MARKER))) {
            return true;
        }

        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + directory, e);
        }
    }

    private static ToolingException refused(String property, Path path, String reason) {
        return new ToolingException(property + " " + reason + ", because the plugin deletes it before writing: "
            + path);
    }

    private static File normalized(File file) {
        return file.toPath()
            .toAbsolutePath()
            .normalize()
            .toFile();
    }
}
