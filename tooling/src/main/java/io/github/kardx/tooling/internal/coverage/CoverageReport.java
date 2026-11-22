package io.github.kardx.tooling.internal.coverage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 앱의 엔드포인트 중 문서화한 것과 하지 않은 것. 경로는 변수 이름을 무시하고({@code {id}} == {@code {userId}}) 비교하고,
 * 메서드가 {@value Endpoint#ANY_METHOD} 인 엔드포인트는 어느 메서드로 문서화해도 된다. 둘 다 API 버전이 있으면 버전도 같아야 한다.
 *
 * @param documented 문서화한 엔드포인트
 * @param undocumented 문서화하지 않은 엔드포인트
 * @param excluded 제외 패턴에 걸린 엔드포인트
 */
public record CoverageReport(List<Endpoint> documented, List<Endpoint> undocumented, List<Endpoint> excluded) {

    /**
     * 보고서를 만든다. [excludes] 는 Ant 패턴이다 ({@code /actuator/**}).
     *
     * @param requests OpenAPI 에서 뺀 operation 의 실제 요청 (실제 경로). 엔드포인트 패턴의 변수 자리는 아무 한 칸과 맞는다
     */
    public static CoverageReport of(List<Endpoint> endpoints, List<Endpoint> operations, List<Endpoint> requests,
        List<String> excludes) {
        List<Pattern> patterns = excludes.stream()
            .map(PathPatterns::compile)
            .toList();
        List<Endpoint> documented = new ArrayList<>();
        List<Endpoint> undocumented = new ArrayList<>();
        List<Endpoint> excluded = new ArrayList<>();

        for (Endpoint endpoint : endpoints) {
            if (patterns.stream().anyMatch(p -> p.matcher(PathPatterns.normalize(endpoint.path())).matches())) {
                excluded.add(endpoint);
            } else if (operations.stream().anyMatch(operation -> matches(endpoint, operation))
                || requests.stream().anyMatch(request -> matchesRequest(endpoint, request))) {
                documented.add(endpoint);
            } else {
                undocumented.add(endpoint);
            }
        }

        return new CoverageReport(documented, undocumented, excluded);
    }

    /** 제외하지 않은 엔드포인트 수. */
    public int total() {
        return documented.size() + undocumented.size();
    }

    /** {@code coverage.json} 의 내용. */
    public Map<String, Object> toJson() {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> json = new LinkedHashMap<>();

        summary.put("endpoints", total());
        summary.put("documented", documented.size());
        summary.put("undocumented", undocumented.size());
        summary.put("excluded", excluded.size());
        json.put("summary", summary);
        json.put("undocumented", toJson(undocumented));
        json.put("documented", toJson(documented));
        json.put("excluded", toJson(excluded));

        return json;
    }

    /** {@code coverage.md} 의 내용. */
    public String toMarkdown() {
        List<String> lines = new ArrayList<>(List.of(
            "# API docs coverage",
            "",
            "**" + documented.size() + "/" + total() + "** endpoints documented (" + undocumented.size()
                + " undocumented, " + excluded.size() + " excluded).",
            "",
            "## Undocumented",
            ""));

        if (undocumented.isEmpty()) {
            lines.add("None.");
        } else {
            lines.add("| Method | Path | Handler | Deprecated | API version |");
            lines.add("|---|---|---|---|---|");
            undocumented.forEach(e -> lines.add("| " + e.method() + " | `" + e.path() + "` | "
                + cell(e.handler()) + " | " + (e.deprecated() ? "yes" : "") + " | " + cell(e.apiVersion()) + " |"));
        }

        lines.add("");
        lines.add("## Documented");
        lines.add("");
        documented.forEach(e -> lines.add("- " + e.label()));

        if (!excluded.isEmpty()) {
            lines.add("");
            lines.add("## Excluded");
            lines.add("");
            excluded.forEach(e -> lines.add("- " + e.label()));
        }

        return String.join("\n", lines) + "\n";
    }

    private static boolean matches(Endpoint endpoint, Endpoint operation) {
        boolean method = Endpoint.ANY_METHOD.equals(endpoint.method()) || endpoint.method().equals(operation.method());
        boolean version = endpoint.apiVersion() == null || operation.apiVersion() == null
            || Objects.equals(endpoint.apiVersion(), operation.apiVersion());

        return method && version && PathPatterns.normalize(endpoint.path())
            .equals(PathPatterns.normalize(operation.path()));
    }

    private static boolean matchesRequest(Endpoint endpoint, Endpoint request) {
        boolean method = Endpoint.ANY_METHOD.equals(endpoint.method()) || endpoint.method().equals(request.method());

        return method && PathPatterns.template(endpoint.path()).matcher(request.path()).matches();
    }

    private static List<Object> toJson(List<Endpoint> endpoints) {
        List<Object> json = new ArrayList<>();

        endpoints.forEach(endpoint -> json.add(endpoint.toJson()));

        return json;
    }

    private static String cell(String value) {
        return value == null ? "" : "`" + value.replace("|", "\\|") + "`";
    }
}
