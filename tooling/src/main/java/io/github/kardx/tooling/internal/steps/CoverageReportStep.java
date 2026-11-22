package io.github.kardx.tooling.internal.steps;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.coverage.CoverageInputs;
import io.github.kardx.tooling.internal.coverage.CoverageReport;
import io.github.kardx.tooling.internal.coverage.Endpoint;
import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.io.ToolLog;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/** 문서화 범위 보고서: 앱의 엔드포인트 목록과 문서화한 operation 을 비교해 {@value #JSON_FILE}·{@value #MARKDOWN_FILE} 를 쓴다. */
public final class CoverageReportStep {
    /** 기계가 읽는 보고서. */
    public static final String JSON_FILE = "coverage.json";
    /** 사람이 읽는 보고서. */
    public static final String MARKDOWN_FILE = "coverage.md";
    /** 엔드포인트 목록 (스니펫 디렉터리 기준 Ant 패턴). */
    public static final List<String> ENDPOINT_PATTERNS = List.of(CoverageInputs.ENDPOINTS_DIR + "/*.json");
    /** 문서화한 operation (스니펫 디렉터리 기준 Ant 패턴). */
    public static final List<String> OPERATION_PATTERNS = List.of("**/" + CoverageInputs.RESOURCE_FILE,
        "**/" + CoverageInputs.EXTRAS_FILE, "**/" + CoverageInputs.REQUEST_SNIPPET);
    /** 문서화한 operation 에서 뺄 곳: 라이브러리가 남긴 엔드포인트 목록 등. */
    public static final List<String> OPERATION_EXCLUDES = List.of(".kardx/**");
    /**
     * 언제나 빼는 경로: Spring 오류 페이지, Actuator, springdoc(스펙 JSON·YAML, Swagger UI 와 그 리다이렉트).
     * 앱이 내놓는 문서 경로({@code /docs/**})는 쓰는 쪽이 더한다.
     */
    public static final List<String> DEFAULT_EXCLUDES = List.of(
        "/error", "/actuator/**", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html");
    private static final int MESSAGE_LIMIT = 20;

    private CoverageReportStep() {
    }

    /**
     * @param reportLocation 요약 줄에 보일 보고서 위치
     * @param excludeHint 실패 메시지에 보일 제외 설정 예 ({@code kardx { coverage { exclude("/path/**") } }})
     */
    public record Settings(List<String> excludes, boolean failOnUndocumented, String reportLocation,
        String excludeHint) {
    }

    /** @throws ToolingException {@code failOnUndocumented} 이고 문서화하지 않은 엔드포인트가 있을 때 */
    public static void run(Iterable<File> endpointFiles, Iterable<File> operationFiles, File directory,
        Settings settings, ToolLog log) {
        List<Endpoint> endpoints = CoverageInputs.endpoints(endpointFiles);

        if (endpoints.isEmpty()) {
            String message = "API docs coverage: no endpoint inventory (no documentation test ran against a Spring "
                + "application context, e.g. standalone MockMvc only)";

            TextFiles.write(new File(directory, MARKDOWN_FILE), "# API docs coverage\n\n" + message + "\n");
            JsonFiles.writeReadable(new File(directory, JSON_FILE),
                CoverageReport.of(List.of(), List.of(), List.of(), List.of()).toJson());
            log.lifecycle(message);

            return;
        }

        CoverageReport report = CoverageReport.of(endpoints, CoverageInputs.documented(operationFiles),
            CoverageInputs.excludedRequests(operationFiles), settings.excludes());

        JsonFiles.writeReadable(new File(directory, JSON_FILE), report.toJson());
        TextFiles.write(new File(directory, MARKDOWN_FILE), report.toMarkdown());

        String summary = "API docs coverage: " + report.documented().size() + "/" + report.total()
            + " endpoints documented (" + report.undocumented().size() + " undocumented, see "
            + settings.reportLocation() + ")";

        if (settings.failOnUndocumented() && !report.undocumented().isEmpty()) {
            throw new ToolingException(summary + "\n" + report.undocumented()
                .stream()
                .limit(MESSAGE_LIMIT)
                .map(endpoint -> "  - " + endpoint.label())
                .collect(Collectors.joining("\n"))
                + "\nDocument them, or exclude them with " + settings.excludeHint() + ".");
        }

        log.lifecycle("{}", summary);
    }
}
