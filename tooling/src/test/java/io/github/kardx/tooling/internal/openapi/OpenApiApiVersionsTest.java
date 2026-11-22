package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.PreviousSpecFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spring Framework 7 API 버전 ({@link OpenApiApiVersions}, {@link OpenApiVersionSplit}). */
class OpenApiApiVersionsTest {
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /users:
            get:
              parameters:
              - {name: x-api-version, in: header, schema: {type: string}}
              responses: {"200": {description: OK}}
          /health:
            get:
              responses: {"200": {description: OK}}
          /reports:
            get:
              responses: {"200": {description: OK}}
        """;
    private static final ApiVersioningSettings HEADER = new ApiVersioningSettings("header", "X-API-Version", false);

    @TempDir
    File snippets;

    @Test
    void highestVersionWinsWithWarningAndRequiredParameter() throws IOException {
        versionedFragments();
        Map<String, Object> document = SnippetFixtures.yaml(SPEC);
        List<String> warnings = new ArrayList<>();

        assertEquals(2, OpenApiApiVersions.transform(document, OpenApiResources.read(snippets), HEADER, warnings));

        Map<String, Object> users = SpecMaps.operation(document, "/users", "get");

        assertEquals("1.10", users.get("x-api-version"), "1.10 > 1.9 (숫자로 비교)");
        assertEquals(List.of(Map.of("name", "x-api-version", "in", "header", "required", true,
            "schema", Map.of("type", "string", "enum", List.of("1.10")), "example", "1.10")),
            users.get("parameters"), "문서화한 같은 헤더(대소문자 무시)를 고친다");
        assertEquals("2", SpecMaps.operation(document, "/reports", "get").get("x-api-version"));
        assertNull(SpecMaps.operation(document, "/health", "get").get("x-api-version"));
        assertEquals(List.of("GET /users is documented for API versions [1.9, 1.10]; the spec shows 1.10 only "
            + "(hidden: [1.9]). Set kardx.apiVersioning.splitSpecs = true for one spec per version"),
            warnings);
    }

    @Test
    void splitSpecsWritesOneSpecPerVersionWithUnversionedOperations(@TempDir File specs) throws IOException {
        versionedFragments();
        ApiVersioningSettings split = new ApiVersioningSettings("query", "version", true);
        File spec = new File(specs, "openapi3.yaml");
        File stale = new File(specs, "openapi3-v0.yaml");
        File otherFormat = new File(specs, "openapi3.json");
        File swagger2 = new File(specs, "openapi.json");

        OpenApiSpecFiles.write(stale, SnippetFixtures.yaml(SPEC));
        OpenApiSpecFiles.write(otherFormat, SnippetFixtures.yaml(SPEC));
        Files.writeString(swagger2.toPath(), "{}");

        assertEquals(2, PreviousSpecFiles.delete(specs, "openapi3", new File(specs, "none.txt")));
        assertFalse(stale.exists(), "지난 실행의 버전 스펙을 지운다");
        assertFalse(otherFormat.exists(), "지난 형식의 스펙을 지운다");
        assertTrue(swagger2.exists(), "다른 epages 태스크의 결과는 둔다");

        OpenApiSpecFiles.write(spec, SnippetFixtures.yaml(SPEC));
        assertEquals(List.of("openapi3-v1.9.yaml", "openapi3-v1.10.yaml", "openapi3-v2.yaml"),
            OpenApiVersionSplit.apply(snippets, specs, split)
                .stream()
                .map(File::getName)
                .toList());

        Map<String, Object> v19 = OpenApiSpecFiles.readOpenApi3(new File(specs, "openapi3-v1.9.yaml"));
        Map<String, Object> v2 = OpenApiSpecFiles.readOpenApi3(new File(specs, "openapi3-v2.yaml"));
        Map<String, Object> users = SpecMaps.operation(v19, "/users", "get");

        assertEquals("1.9", users.get("x-api-version"));
        assertTrue(SpecMaps.maps(users.get("parameters"))
            .contains(Map.of("name", "version", "in", "query", "required", true,
                "schema", Map.of("type", "string", "enum", List.of("1.9")), "example", "1.9")));
        assertTrue(SpecMaps.map(v19, "paths").containsKey("/health"), "버전 없는 operation 은 모든 버전 스펙에");
        assertFalse(SpecMaps.map(v19, "paths").containsKey("/reports"), "다른 버전만 있는 경로는 뺀다");
        assertFalse(SpecMaps.map(v2, "paths").containsKey("/users"));
    }

    private void versionedFragments() throws IOException {
        SnippetFixtures.fragment(snippets, "users-v1-9", "GET", "/users", 200, "{ \"apiVersion\": \"1.9\" }");
        SnippetFixtures.fragment(snippets, "users-v1-10", "GET", "/users", 200, "{ \"apiVersion\": \"1.10\" }");
        SnippetFixtures.fragment(snippets, "health", "GET", "/health", 200, "{ \"apiVersion\": null }");
        SnippetFixtures.fragment(snippets, "reports", "GET", "/reports", 200, "{ \"apiVersion\": \"2\" }");
    }
}
