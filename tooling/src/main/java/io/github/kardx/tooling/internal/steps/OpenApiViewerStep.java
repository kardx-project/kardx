package io.github.kardx.tooling.internal.steps;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.io.FileTrees;
import io.github.kardx.tooling.internal.io.OutputPaths;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.pages.OpenApiViewerPage;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.io.File;
import java.util.Map;
import java.util.Set;

/** OpenAPI 스펙을 담은 정적 뷰어 페이지(Redoc 또는 Scalar)를 쓴다. */
public final class OpenApiViewerStep {
    private OpenApiViewerStep() {
    }

    /**
     * @param type {@code redoc} 또는 {@code scalar}
     * @param title 페이지 제목. null 이면 스펙의 {@code info.title}
     * @param fileName 뷰어 파일 경로 ({@code directory} 기준, {@link #fileName} 으로 확인한 값)
     */
    public record Settings(String type, String title, String fileName, DocsLanguage language) {
    }

    /** {@code directory} 를 비우고 뷰어 페이지를 쓴다. @return 쓴 파일 */
    public static File run(Set<File> specs, File directory, Settings settings, ToolLog log) {
        Object document = OpenApiSpecFiles.read(OpenApiSpecFiles.existing(specs));

        FileTrees.delete(directory);

        if (!(document instanceof Map<?, ?> map)) {
            throw new ToolingException("Not an OpenAPI document: " + specs);
        }

        File page = new File(directory, settings.fileName());

        TextFiles.write(page, OpenApiViewerPage.render(settings.type(), settings.title(), map, settings.language()));
        log.info("OpenAPI viewer: {}", page);

        return page;
    }

    /**
     * 뷰어 파일 경로 (문서 디렉터리 기준). {@code .html} 이 없으면 붙이고, 문서 디렉터리 밖을 가리키면 거부한다.
     *
     * @param setting 오류 메시지에 보일 설정 이름
     */
    public static String fileName(String original, String setting) {
        String name = OutputPaths.relativePath(original, original, setting, "'openapi.html'");

        return name.endsWith(".html") ? name : name + ".html";
    }
}
