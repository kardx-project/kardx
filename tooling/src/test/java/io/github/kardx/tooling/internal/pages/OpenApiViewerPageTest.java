package io.github.kardx.tooling.internal.pages;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OpenApiViewerPageTest {
    private static final Map<String, Object> SPEC = Map.of(
        "openapi", "3.0.1",
        "info", Map.of("title", "주문 <API>", "description", "</script><script>alert(1)</script>"));

    @Test
    void embedsSpecWithPinnedScripts() {
        String redoc = OpenApiViewerPage.render(OpenApiViewerPage.REDOC, null, SPEC, DocsLanguage.EN);
        String scalar = OpenApiViewerPage.render(OpenApiViewerPage.SCALAR, "레퍼런스", SPEC, DocsLanguage.EN);

        assertTrue(redoc.contains("<title>주문 &lt;API&gt;</title>"), "제목 기본값은 info.title");
        assertTrue(redoc.contains("https://unpkg.com/redoc@2.5.4/bundles/redoc.standalone.js"));
        assertTrue(redoc.contains("integrity=\"sha384-"));
        assertTrue(redoc.contains("Redoc.init(spec"));
        assertTrue(scalar.contains("<title>레퍼런스</title>"));
        assertTrue(scalar.contains("@scalar/api-reference@1.76.4"));
        assertTrue(scalar.contains("Scalar.createApiReference(root, { content: spec, showDeveloperTools: 'never'"));
        assertTrue(scalar.contains("agent: { disabled: true }"), "스펙을 외부 AI 로 보내는 기능은 끈다");
        assertFalse(redoc.contains("</script><script>alert"), "스펙 문자열이 스크립트를 끝내지 않는다");
        assertTrue(redoc.contains("\\u003c/script>"));
        assertTrue(redoc.contains("<html lang=\"en\">"), redoc);
        assertTrue(redoc.contains("root.textContent = 'Could not load the Redoc script (unpkg.com). Check your "
            + "internet connection or open the OpenAPI spec file in the same directory.';"), redoc);
        assertTrue(scalar.contains("root.textContent = 'Could not load the Scalar script (unpkg.com)."), scalar);
    }

    @Test
    void writesKoreanPageTextsForKo() {
        String redoc = OpenApiViewerPage.render(OpenApiViewerPage.REDOC, null, SPEC, DocsLanguage.KO);
        String scalar = OpenApiViewerPage.render(OpenApiViewerPage.SCALAR, null, SPEC, DocsLanguage.KO);

        assertTrue(redoc.contains("<html lang=\"ko\">"), redoc);
        assertTrue(redoc.contains("root.textContent = 'Redoc 스크립트(unpkg.com)를 불러오지 못했습니다. 인터넷 연결을 확인하거나 "
            + "같은 디렉터리의 OpenAPI 스펙 파일을 보세요.';"), redoc);
        assertTrue(scalar.contains("root.textContent = 'Scalar 스크립트(unpkg.com)를 불러오지 못했습니다. 인터넷 연결을 확인하거나 "
            + "같은 디렉터리의 OpenAPI 스펙 파일을 보세요.';"), scalar);
    }

    @Test
    void leavesTemplateCommentsOutOfEnglishPages() {
        Map<String, Object> spec = Map.of("openapi", "3.0.1", "info", Map.of("title", "Shop", "version", "1"));
        Pattern hangul = Pattern.compile("\\p{IsHangul}");

        for (DocsLanguage language : List.of(DocsLanguage.DEFAULT, DocsLanguage.of("en"))) {
            for (String type : List.of(OpenApiViewerPage.REDOC, OpenApiViewerPage.SCALAR)) {
                String page = OpenApiViewerPage.render(type, null, spec, language);

                assertFalse(hangul.matcher(page).find(), type + " " + language + "\n" + page);
                assertFalse(page.contains("<!--"), page);
                assertFalse(page.lines().anyMatch(line -> line.strip().startsWith("//")), page);
                assertTrue(page.contains("integrity=\"sha384-"), "주석 옆 스크립트 태그는 남는다");
            }
        }
    }

    @Test
    void stripsOnlyTheTemplateNotTheEmbeddedSpec() {
        Map<String, Object> spec = Map.of("openapi", "3.0.1",
            "info", Map.of("title", "Shop", "description", "<!-- kept --> // kept"));
        String page = OpenApiViewerPage.render(OpenApiViewerPage.REDOC, null, spec, DocsLanguage.EN);

        assertTrue(page.contains("\\u003c!-- kept --> // kept"), page);
    }

    @Test
    void rejectsUnknownType() {
        assertThrows(ToolingException.class, () -> OpenApiViewerPage.render("swagger", null, SPEC, DocsLanguage.EN));
    }
}
