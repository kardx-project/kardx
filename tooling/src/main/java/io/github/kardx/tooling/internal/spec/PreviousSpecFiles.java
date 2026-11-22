package io.github.kardx.tooling.internal.spec;

import io.github.kardx.tooling.internal.io.TextFiles;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * epages {@code openapi3} 가 지난 실행에 쓴 스펙. epages 는 결과 디렉터리를 비우지 않으므로 형식({@code yaml} → {@code json})을
 * 바꾸면 지난 형식의 파일이 남고, 결과 후보 중 먼저 오는 그 파일이 검사·뷰어·내보내기에 쓰인다. 그래서 {@code openapi3} 가
 * 돌기 전에 지운다. 결과 디렉터리는 사용자가 {@code src/main/resources/static/docs} 처럼 다른 파일과 같이 쓰는 곳일 수 있으므로
 * 이 플러그인이 쓴 이름만 지운다 ({@code openapi3-vendor.json}, {@code openapi3-validation.yaml} 은 지우지 않는다).
 */
public final class PreviousSpecFiles {
    private static final String EXTENSIONS = "\\.(?:yaml|yml|json)";
    /** 버전 스펙 이름에 쓰는 글자 ({@code OpenApiVersionSplit} 이 다른 글자를 {@code _} 로 바꾼다). */
    private static final String VERSION = "[A-Za-z0-9._-]+";

    private PreviousSpecFiles() {
    }

    /**
     * [directory] 에서 지운다.
     * <ul>
     *   <li>{@code <prefix>.yaml|yml|json}</li>
     *   <li>지난 실행이 [versionSpecs] 에 적어 둔 버전 스펙 ({@link #record})</li>
     *   <li>목록이 없던 때 만든 숫자 버전 스펙 {@code <prefix>-v<숫자로 시작하는 버전>.yaml|yml|json}</li>
     * </ul>
     *
     * @param versionSpecs 지난 실행이 쓴 버전 스펙 이름 목록 (없으면 무시). 읽은 뒤 지운다
     * @return 지운 파일 수
     */
    public static int delete(File directory, String prefix, File versionSpecs) {
        String quoted = Pattern.quote(prefix);
        Pattern names = Pattern.compile(quoted + "(?:-v[0-9][A-Za-z0-9._-]*)?" + EXTENSIONS);
        Pattern recordedNames = Pattern.compile(quoted + "-v" + VERSION + EXTENSIONS);
        Set<String> recorded = recorded(versionSpecs).stream()
            .filter(name -> recordedNames.matcher(name).matches())
            .collect(Collectors.toSet());
        File[] files = directory.listFiles();
        int deleted = 0;

        if (files != null) {
            for (File file : files) {
                String name = file.getName();

                if (file.isFile() && (names.matcher(name).matches() || recorded.contains(name))) {
                    deleteFile(file);
                    deleted++;
                }
            }
        }

        if (versionSpecs.isFile()) {
            deleteFile(versionSpecs);
        }

        return deleted;
    }

    /** 이번 실행이 쓴 버전 스펙 이름을 [versionSpecs] 에 남긴다. 다음 {@link #delete} 가 이 파일들만 지운다. */
    public static void record(File versionSpecs, Collection<File> written) {
        if (written.isEmpty()) {
            return;
        }

        TextFiles.write(versionSpecs, written.stream()
            .map(File::getName)
            .sorted()
            .collect(Collectors.joining("\n")));
    }

    private static Set<String> recorded(File versionSpecs) {
        if (!versionSpecs.isFile()) {
            return Set.of();
        }

        return TextFiles.read(versionSpecs)
            .lines()
            .map(String::strip)
            .collect(Collectors.toSet());
    }

    private static void deleteFile(File file) {
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
