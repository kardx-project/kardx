package io.github.kardx.tooling.internal.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 엔드포인트 목록(계약 형식의 픽스처)과 문서화한 operation 비교 ({@link CoverageReport}, {@link CoverageInputs}). */
class CoverageReportTest {
    private static final List<String> EXCLUDES = List.of("/error", "/actuator/**", "/docs/**");

    @TempDir
    File snippets;

    @Test
    void matchesIgnoringVariableNamesAndAnyMethod() throws IOException {
        File first = write(CoverageInputs.ENDPOINTS_DIR + "/a1b2c3.json", """
            { "endpoints": [
                { "method": "GET", "path": "/api/v1/users/{id}", "handler": "com.example.UserController#getUser",
                  "deprecated": false, "apiVersion": null },
                { "method": "DELETE", "path": "/api/v1/users/{id:\\\\d+}",
                  "handler": "com.example.UserController#delete",
                  "deprecated": true, "apiVersion": null },
                { "method": "*", "path": "/api/v1/ping", "handler": null, "deprecated": false, "apiVersion": null },
                { "method": "GET", "path": "/actuator/health", "handler": null, "deprecated": false,
                  "apiVersion": null },
                { "method": "GET", "path": "/actuator", "handler": null, "deprecated": false, "apiVersion": null },
                { "method": "GET", "path": "/orders", "handler": "com.example.OrderController#v2",
                  "deprecated": false, "apiVersion": "2.0" } ] }
            """);
        File second = write(CoverageInputs.ENDPOINTS_DIR + "/d4e5f6.json", """
            { "endpoints": [
                { "method": "GET", "path": "/api/v1/users/{userId}", "handler": "dup", "deprecated": false,
                  "apiVersion": null } ] }
            """);
        File getUser = resource("user-docs/get-user", "GET", "/api/v1/users/{userId}/", null);
        File ping = resource("ping-docs/ping", "POST", "/api/v1/ping?verbose", null);
        File orders = resource("order-docs/list-v1", "GET", "/orders", "{ \"apiVersion\": \"1.0\" }");

        // openApi { excluded = true }: resource.json 없이 요청 스니펫만 남는다
        File deleteUser = write("user-docs/delete-user/" + CoverageInputs.REQUEST_SNIPPET, """
            [source,http,options="nowrap"]
            ----
            DELETE /api/v1/users/7?force=true HTTP/1.1
            Host: localhost

            ----
            """);
        File getUserRequest = write("user-docs/get-user/" + CoverageInputs.REQUEST_SNIPPET,
            "----\nGET /x HTTP/1.1\n----\n");
        List<File> operationFiles = List.of(getUser, ping, orders, deleteUser, getUserRequest);

        List<Endpoint> endpoints = CoverageInputs.endpoints(List.of(second, first));
        CoverageReport report = CoverageReport.of(endpoints, CoverageInputs.documented(operationFiles),
            CoverageInputs.excludedRequests(operationFiles), EXCLUDES);

        assertEquals(6, endpoints.size(), "같은 메서드·경로(변수 이름 무시)·버전은 한 번");
        assertEquals(List.of("GET /api/v1/users/{id}", "DELETE /api/v1/users/{id:\\d+}", "* /api/v1/ping"),
            labels(report.documented()), "OpenAPI 에서 뺀 operation 은 실제 요청 경로로 맞춘다");
        assertEquals(List.of("GET /orders (v2.0)"), labels(report.undocumented()), "버전이 다르면 문서화하지 않은 것");
        assertEquals(List.of("GET /actuator/health", "GET /actuator"), labels(report.excluded()),
            "/actuator/** 는 /actuator 자신도 뺀다");
        assertEquals(4, report.total());
        assertEquals(Map.of("endpoints", 4, "documented", 3, "undocumented", 1, "excluded", 2),
            report.toJson().get("summary"));

        String markdown = report.toMarkdown();

        assertTrue(markdown.contains("**3/4** endpoints documented (1 undocumented, 2 excluded)."), markdown);
        assertTrue(markdown.contains("| GET | `/orders` | `com.example.OrderController#v2` |  | `2.0` |"), markdown);
    }

    @Test
    void antPatterns() {
        assertTrue(PathPatterns.compile("/swagger-ui/**").matcher("/swagger-ui/index.html").matches());
        assertTrue(PathPatterns.compile("/internal/*/status").matcher("/internal/{}/status").matches());
        assertFalse(PathPatterns.compile("/internal/*").matcher("/internal/a/b").matches());
        assertEquals("/users/{}/orders/{}", PathPatterns.normalize("users/{userId}/orders/{id:[a-z]{2}}/"));
    }

    /** 사용자는 Spring 이 보여 주는 매핑 문자열을 그대로 제외 패턴에 쓴다. 변수 이름·정규식은 엔드포인트처럼 무시한다. */
    @Test
    void excludePatternsMayContainPathVariables() {
        List<Endpoint> endpoints = List.of(
            new Endpoint("GET", "/internal/users/{id}", "a#get", false, null),
            new Endpoint("DELETE", "/internal/users/{userId:\\d+}", "a#delete", false, null),
            new Endpoint("GET", "/files/{*rest}", "a#file", false, null),
            new Endpoint("GET", "/users/{id}", "a#user", false, null));

        CoverageReport report = CoverageReport.of(endpoints, List.of(), List.of(),
            List.of("/internal/users/{id}", "/files/{*path}"));

        assertEquals(
            List.of("GET /internal/users/{id}", "DELETE /internal/users/{userId:\\d+}", "GET /files/{*rest}"),
            labels(report.excluded()));
        assertEquals(List.of("GET /users/{id}"), labels(report.undocumented()));
        assertTrue(PathPatterns.compile("/a/{id:\\d+}/**").matcher("/a/{}/b").matches());
    }

    private static List<String> labels(List<Endpoint> endpoints) {
        return endpoints.stream()
            .map(Endpoint::label)
            .toList();
    }

    private File resource(String directory, String method, String path, String extras) throws IOException {
        if (extras != null) {
            write(directory + "/" + CoverageInputs.EXTRAS_FILE, extras);
        }

        return write(directory + "/" + CoverageInputs.RESOURCE_FILE, """
            { "request": { "method": "%s", "path": "%s" }, "response": { "status": 200 } }
            """.formatted(method, path));
    }

    private File write(String path, String text) throws IOException {
        File file = new File(snippets, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);

        return file;
    }
}
