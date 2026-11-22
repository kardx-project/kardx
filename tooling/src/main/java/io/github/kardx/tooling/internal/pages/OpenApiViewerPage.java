package io.github.kardx.tooling.internal.pages;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.i18n.DocsText;
import io.github.kardx.tooling.internal.io.JsonFiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 스펙을 넣은 단일 HTML 뷰어 페이지. 템플릿은 플러그인 리소스 {@code viewer-redoc.html}, {@code viewer-scalar.html} 이고
 * 뷰어 스크립트는 버전·무결성 해시를 고정한 CDN 주소에서 받는다. 스펙은 페이지 안에 JSON 으로 넣으므로 파일로 열어도 보인다.
 * 템플릿 주석은 결과에 넣지 않는다({@link TemplateComments}).
 */
public final class OpenApiViewerPage {
    public static final String REDOC = "redoc";
    static final String SCALAR = "scalar";

    private OpenApiViewerPage() {
    }

    /**
     * [type] 뷰어로 [spec] 을 보여 주는 HTML. 제목은 [title], 없으면 스펙의 {@code info.title}.
     * {@code <html lang>} 과 오프라인 안내({@link DocsText#VIEWER_OFFLINE})는 [language] 를 따른다.
     */
    public static String render(String type, String title, Map<?, ?> spec, DocsLanguage language) {
        if (!List.of(REDOC, SCALAR).contains(type)) {
            throw new ToolingException("kardx.openApiViewer.type must be redoc or scalar, got '" + type + "'");
        }

        String pageTitle = title != null ? title : infoTitle(spec);

        // </script> 가 스펙 문자열에 있어도 스크립트가 끝나지 않게 < 를 JSON 이스케이프로 쓴다
        String json = JsonFiles.toCompactJson(spec)
            .replace("<", "\\u003c");

        // 스펙은 마지막에 넣는다: 스펙 문자열 안의 자리 표시자 글자가 바뀌지 않게 한다
        return template(type).replace("__LANG__", language.code())
            .replace("__OFFLINE_NOTE__", DocsText.VIEWER_OFFLINE.format(language, displayName(type)))
            .replace("__TITLE__", HtmlText.escape(pageTitle))
            .replace("__SPEC__", json);
    }

    /** 안내 문구에 쓰는 뷰어 이름 ({@code redoc} → {@code Redoc}). */
    private static String displayName(String type) {
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    private static String infoTitle(Map<?, ?> spec) {
        return spec.get("info") instanceof Map<?, ?> info && info.get("title") != null
            ? String.valueOf(info.get("title"))
            : "API";
    }

    private static String template(String type) {
        String name = "viewer-" + type + ".html";

        try (InputStream in = OpenApiViewerPage.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new ToolingException("Missing plugin resource " + name);
            }

            return TemplateComments.strip(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
