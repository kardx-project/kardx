package io.github.kardx.tooling.internal.io;

import io.github.kardx.tooling.ToolingException;
import java.util.List;
import java.util.regex.Pattern;

/** 사용자가 정한 결과 파일 경로(문서 디렉터리 기준)를 확인한다. */
public final class OutputPaths {
    /** Windows 드라이브 ({@code C:}, {@code C:/x}). */
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private OutputPaths() {
    }

    /**
     * [path] 의 {@code \} 를 {@code /} 로 바꾼 상대 경로. 비었거나 {@code /} 로 끝나거나 문서 디렉터리 밖을 가리키면
     * ({@code /x}, {@code ../x}, {@code C:/x}) 거부한다.
     *
     * @param original 사용자가 적은 값 (오류 메시지용)
     * @param property 설정 이름 ({@code kardx.asyncApi.fileName})
     * @param examples 올바른 값 예시 ({@code 'asyncapi' or 'async/index'})
     */
    public static String relativePath(String path, String original, String property, String examples) {
        String name = path.replace('\\', '/');

        if (name.isBlank() || name.startsWith("/") || name.endsWith("/") || DRIVE.matcher(name).matches()
            || List.of(name.split("/")).contains("..")) {
            throw new ToolingException(property + " must be a relative path like " + examples + ", got '"
                + original + "'");
        }

        return name;
    }
}
