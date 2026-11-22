package io.github.kardx.tooling.internal.pages;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.i18n.DocsText;
import java.util.ArrayList;
import java.util.List;

/** {@code apiDocsAggregate} 의 목록 페이지({@code index.html}). 프로젝트별 문서와 병합 스펙으로 가는 링크만 담는다. */
public final class AggregateIndexPage {
    private AggregateIndexPage() {
    }

    /**
     * 한 프로젝트에서 찾은 문서 (집계 디렉터리 기준 경로).
     *
     * @param name 프로젝트 이름 ({@code order}). 결과 디렉터리 이름이기도 하다
     * @param restDocs REST Docs index ({@code order/index.html}). 없으면 null
     * @param openApi OpenAPI 스펙 파일
     * @param asyncApi AsyncAPI 뷰어(있으면) 또는 스펙 파일
     * @param postman Postman Collection 파일
     */
    public record Module(
        String name, String restDocs, List<String> openApi, List<String> asyncApi, List<String> postman) {
    }

    /**
     * @param mergedSpec 병합 스펙 파일 이름. 합칠 OpenAPI 가 없으면 null
     * @param viewer 병합 스펙 뷰어 파일 이름. 없으면 null
     * @param language 페이지 문구와 {@code <html lang>} 의 언어
     */
    public static String render(
        String title, String mergedSpec, String viewer, List<Module> modules, DocsLanguage language) {
        List<String> lines = new ArrayList<>(List.of(
            "<!doctype html>",
            "<html lang=\"" + language.code() + "\">",
            "<head>",
            "  <meta charset=\"utf-8\">",
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
            "  <title>" + HtmlText.escape(title) + "</title>",
            "  <style>body{font-family:system-ui,sans-serif;max-width:56rem;margin:2rem auto;padding:0 1rem;"
                + "line-height:1.6}td,th{padding:.3rem .8rem;text-align:left;vertical-align:top}"
                + "table{border-collapse:collapse}tr{border-bottom:1px solid #ddd}</style>",
            "</head>",
            "<body>",
            "  <h1>" + HtmlText.escape(title) + "</h1>"));

        if (mergedSpec != null) {
            lines.add("  <p>" + DocsText.AGGREGATE_MERGED_SPEC.in(language) + link(mergedSpec)
                + (viewer == null ? "" : " · " + link(viewer)) + "</p>");
        }

        lines.add("  <table>");
        lines.add("    <tr><th>" + DocsText.AGGREGATE_PROJECT.in(language)
            + "</th><th>REST Docs</th><th>OpenAPI</th><th>AsyncAPI</th><th>Postman</th></tr>");
        modules.forEach(module -> lines.add("    <tr><td>" + HtmlText.escape(module.name()) + "</td>"
            + cell(module.restDocs() == null ? List.of() : List.of(module.restDocs()))
            + cell(module.openApi()) + cell(module.asyncApi()) + cell(module.postman()) + "</tr>"));
        lines.add("  </table>");
        lines.add("</body>");
        lines.add("</html>");

        return String.join("\n", lines) + "\n";
    }

    private static String cell(List<String> paths) {
        List<String> links = paths.stream()
            .map(AggregateIndexPage::link)
            .toList();

        return "<td>" + (links.isEmpty() ? "-" : String.join("<br>", links)) + "</td>";
    }

    private static String link(String path) {
        return "<a href=\"" + HtmlText.escape(path) + "\">" + HtmlText.escape(path) + "</a>";
    }
}
