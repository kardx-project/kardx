package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Kotlin 기본값({@code fieldDefaults})을 본문 필드 스키마의 {@code default} 로 ({@link OpenApiFieldDefaults}). */
class OpenApiFieldDefaultsTest {
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /search:
            post:
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
                      properties:
                        page:
                          type: object
                          properties: {size: {type: integer}, sort: {type: string, default: name}}
                        status: {$ref: "#/components/schemas/Status"}
                        tags: {type: array, items: {type: object, properties: {weight: {type: number}}}}
              responses:
                "200":
                  description: OK
                  content:
                    application/json:
                      schema: {type: object, properties: {strict: {type: boolean}}}
        components:
          schemas:
            Status: {type: string, enum: [OPEN, CLOSED]}
        """;

    @TempDir
    File snippets;

    @Test
    void setsDefaultsByFieldPathAndKeepsExplicitOnes() throws IOException {
        SnippetFixtures.fragment(snippets, "search", "POST", "/search", 200, """
            { "fieldDefaults": {
                "requestBody": { "page.size": 20, "page.sort": "id", "status": "OPEN", "tags[].weight": 1.5,
                                 "missing": 1 },
                "responseBody": { "strict": false } } }
            """);
        Map<String, Object> document = SnippetFixtures.yaml(SPEC);
        Map<String, Object> operation = SpecMaps.operation(document, "/search", "post");
        Map<String, Object> request = SpecMaps.map(SpecMaps.requestMedia(operation, "application/json"), "schema");
        Map<String, Object> properties = SpecMaps.map(request, "properties");
        Map<String, Object> page = SpecMaps.map(SpecMaps.map(properties, "page"), "properties");

        assertEquals(3, OpenApiFieldDefaults.transform(document, OpenApiResources.read(snippets)));

        assertEquals(20, SpecMaps.map(page, "size").get("default"));
        assertEquals("name", SpecMaps.map(page, "sort").get("default"), "withDefaultValue 로 정한 값이 이긴다");

        Map<String, Object> tag = SpecMaps.map(SpecMaps.map(SpecMaps.map(properties, "tags"), "items"), "properties");

        assertEquals(1.5, ((Number) SpecMaps.map(tag, "weight").get("default")).doubleValue(), "배열 원소의 필드");
        assertFalse(SpecMaps.map(SpecMaps.componentSchemas(document), "Status").containsKey("default"),
            "공유 스키마는 고치지 않는다");
        assertEquals(false, SpecMaps.map(SpecMaps.map(SpecMaps.map(SpecMaps.responseMedia(operation, 200,
            "application/json"), "schema"), "properties"), "strict").get("default"));
    }
}
