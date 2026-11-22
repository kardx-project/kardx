package io.github.kardx.tooling.internal.export;

import io.github.kardx.tooling.ToolingException;
import java.io.File;
import java.util.Set;

/**
 * 내보내기·업로드할 생성 결과를 찾는다. 고른 결과가 없으면 왜 없는지와 끄는 설정을 알려 주며 실패한다.
 * 확장자는 파일 이름에서 얻는다 ({@code openapi3.yaml} → {@code yaml}).
 */
public final class GeneratedSpecs {
    private GeneratedSpecs() {
    }

    /**
     * OpenAPI 스펙 후보 중 있는 첫 파일.
     *
     * @param option 이 스펙을 빼는 설정 이름 ({@code kardx.export.openApi})
     */
    public static File openApi(Set<File> files, String option) {

        return files.stream()
            .filter(File::isFile)
            .findFirst()
            .orElseThrow(() -> new ToolingException(files.isEmpty()
                ? "No OpenAPI spec to use: the com.epages.restdocs-api-spec plugin is not applied. "
                    + "Apply it, or set " + option + " = false."
                : "No OpenAPI spec was generated (looked for " + files + "). Check that the openapi3 task "
                    + "ran and tests documented at least one request, or set " + option + " = false."));
    }

    /**
     * AsyncAPI 스펙 파일. [file] 이 비어 있으면 {@code asyncApi.enabled} 가 false 인 것이다.
     *
     * @param option 이 스펙을 빼는 설정 이름 ({@code kardx.export.asyncApi})
     */
    public static File asyncApi(Set<File> file, String option) {
        if (file.isEmpty()) {
            throw new ToolingException("No AsyncAPI spec: kardx.asyncApi.enabled is false. Enable it, "
                + "or set " + option + " = false to leave it out.");
        }

        return file
            .stream()
            .filter(File::isFile)
            .findFirst()
            .orElseThrow(() -> new ToolingException("No AsyncAPI spec was generated at " + file
                + ": no test documented a WebSocket, STOMP or SSE message. Set " + option + " = false to leave it out."));
    }

    /** 비어 있지 않은 문서 디렉터리. */
    public static File htmlDirectory(Set<File> directory, String option) {
        return directory
            .stream()
            .filter(dir -> {
                String[] children = dir.list();

                return children != null && children.length > 0;
            })
            .findFirst()
            .orElseThrow(() -> new ToolingException("The API docs directory " + directory
                + " is missing or empty after apiDocs. Set " + option + " = false to leave it out."));
    }

    /** 파일 확장자 (점 없이). 없으면 빈 글. */
    public static String extension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');

        return dot < 0 ? "" : name.substring(dot + 1);
    }
}
