package io.github.kardx.tooling.internal.pages;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 집계 목록 페이지({@link AggregateIndexPage}) 문구의 언어. */
class AggregateIndexPageTest {
    private static final List<AggregateIndexPage.Module> MODULES = List.of(new AggregateIndexPage.Module(
        "order", "order/index.html", List.of("order/openapi3.yaml"), List.of(), List.of()));

    @Test
    void writesEnglishByDefault() {
        String page = AggregateIndexPage.render("Shop", "openapi3.yaml", "openapi.html", MODULES, DocsLanguage.EN);

        assertTrue(page.contains("<html lang=\"en\">"), page);
        assertTrue(page.contains("  <p>Merged OpenAPI: <a href=\"openapi3.yaml\">openapi3.yaml</a> · "
            + "<a href=\"openapi.html\">openapi.html</a></p>\n"), page);
        assertTrue(page.contains("    <tr><th>Project</th><th>REST Docs</th>"), page);
    }

    @Test
    void writesKoreanTextsUnchangedForKo() {
        String page = AggregateIndexPage.render("Shop", "openapi3.yaml", null, MODULES, DocsLanguage.KO);

        assertTrue(page.contains("<html lang=\"ko\">"), page);
        assertTrue(page.contains("  <p>전체 OpenAPI: <a href=\"openapi3.yaml\">openapi3.yaml</a></p>\n"), page);
        assertTrue(page.contains("    <tr><th>프로젝트</th><th>REST Docs</th><th>OpenAPI</th><th>AsyncAPI</th>"
            + "<th>Postman</th></tr>\n"), page);
    }
}
