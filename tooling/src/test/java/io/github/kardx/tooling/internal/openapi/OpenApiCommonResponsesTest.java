package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 공통 응답 후처리({@link OpenApiCommonResponses}). */
class OpenApiCommonResponsesTest {
    /** 보안이 있는 조회(404 를 문서화), 보안이 없는 공개 API, 문서 최상위 보안을 끈 operation. */
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /orders/{id}:
            get:
              security:
              - bearerAuthJWT: []
              responses:
                "200": {description: OK}
                "404":
                  description: Not Found
                  content:
                    application/problem+json:
                      schema: {$ref: "#/components/schemas/ErrorResponse"}
                "400":
                  description: Bad Request
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/ValidationError"}
          /health:
            get:
              responses:
                "200": {description: OK}
          /public:
            get:
              security: []
              responses:
                "200": {description: OK}
                default: {description: other}
        security:
        - bearerAuthJWT: []
        components:
          schemas:
            ErrorResponse: {type: object, properties: {code: {type: string}}}
            ValidationError: {type: object}
            Other: {type: object}
        """;

    @Test
    void addsMissingStatusesRespectingTestedResponsesAndSecurity() {
        Map<String, Object> document = document();
        Map<Integer, String> declared = OpenApiCommonResponses.descriptions(
            linked(401, "인증 필요", 404, "없음", 500, ""));
        OpenApiCommonResponses.ErrorBody body = OpenApiCommonResponses.errorBody(document, null, new ArrayList<>());

        int added = OpenApiCommonResponses.transform(document, declared, body);

        Map<String, Object> order = responses(document, "/orders/{id}");
        Map<String, Object> health = responses(document, "/health");
        Map<String, Object> open = responses(document, "/public");

        assertEquals(List.of("200", "400", "401", "404", "500"), List.copyOf(order.keySet()), "상태 코드 순서");
        assertEquals("Not Found", SpecMaps.map(order, "404").get("description"), "테스트가 문서화한 응답이 이긴다");
        assertEquals("인증 필요", SpecMaps.map(order, "401").get("description"));
        assertEquals("Internal Server Error", SpecMaps.map(order, "500").get("description"), "빈 설명은 reason phrase");
        assertEquals(List.of("200", "401", "404", "500"), List.copyOf(health.keySet()),
            "operation 에 security 가 없으면 문서 최상위 security 를 따른다");
        assertEquals(List.of("200", "404", "500", "default"), List.copyOf(open.keySet()),
            "security: [] 로 끈 operation 에는 401 을 넣지 않고, default 는 맨 뒤");
        assertEquals(2 + 3 + 2, added);
    }

    @Test
    void refersToMostUsedErrorSchemaWithItsMediaType() {
        Map<String, Object> document = document();
        Map<String, Object> responses = responses(document, "/health");
        // 같은 스키마를 가리키는 오류 응답이 하나 더 있으면 그 스키마가 고른다
        responses.put("409", Map.of("content", Map.of("application/problem+json",
            Map.of("schema", Map.of("$ref", "#/components/schemas/ErrorResponse")))));

        OpenApiCommonResponses.ErrorBody body = OpenApiCommonResponses.errorBody(document, null, new ArrayList<>());

        assertEquals(new OpenApiCommonResponses.ErrorBody("ErrorResponse", "application/problem+json"), body);

        OpenApiCommonResponses.transform(document, Map.of(500, "Server error"), body);
        // 응답을 더한 operation 은 정렬한 새 맵을 가지므로 다시 읽는다
        Map<String, Object> added = SpecMaps.map(responses(document, "/health"), "500");
        Map<String, Object> media = SpecMaps.map(SpecMaps.map(added, "content"), "application/problem+json");

        assertEquals(Map.of("$ref", "#/components/schemas/ErrorResponse"), media.get("schema"));
    }

    @Test
    void tiesPickTheFirstNameAndConfiguredSchemaMustExist() {
        Map<String, Object> document = document();
        List<String> warnings = new ArrayList<>();

        assertEquals("ErrorResponse", OpenApiCommonResponses.errorBody(document, null, warnings).schema(),
            "ErrorResponse·ValidationError 가 한 번씩이면 이름순");
        assertEquals(new OpenApiCommonResponses.ErrorBody("Other", "application/json"),
            OpenApiCommonResponses.errorBody(document, "Other", warnings), "정한 스키마는 오류 응답이 쓰지 않아도 된다");
        assertTrue(warnings.isEmpty(), warnings.toString());

        OpenApiCommonResponses.ErrorBody missing = OpenApiCommonResponses.errorBody(document, "Missing", warnings);

        assertNull(missing.schema());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("'Missing' is not in components.schemas"), warnings.get(0));
    }

    @Test
    void describesOnlyWhenNoErrorSchemaExists() {
        Map<String, Object> document = document();

        responses(document, "/orders/{id}").remove("404");
        responses(document, "/orders/{id}").remove("400");

        OpenApiCommonResponses.ErrorBody body = OpenApiCommonResponses.errorBody(document, null, new ArrayList<>());

        OpenApiCommonResponses.transform(document, Map.of(500, "Server error"), body);

        assertNull(body.schema());
        assertEquals(Map.of("description", "Server error"), responses(document, "/health").get("500"));
    }

    @Test
    void rejectsStatusOutsideHttpRange() {
        ToolingException error = assertThrows(ToolingException.class,
            () -> OpenApiCommonResponses.descriptions(Map.of(42, "x")));

        assertTrue(error.getMessage().contains("between 100 and 599, got 42"), error.getMessage());
    }

    @Test
    void rewritesYamlWithoutAnchorsAndJsonFiles(@TempDir File dir) throws Exception {
        File yaml = new File(dir, "yaml/openapi3.yaml");
        File json = new File(dir, "json/openapi3.json");

        Files.createDirectories(yaml.getParentFile().toPath());
        Files.createDirectories(json.getParentFile().toPath());
        Files.writeString(yaml.toPath(), SPEC, StandardCharsets.UTF_8);
        JsonFiles.write(json, SwaggerYaml.load(SPEC));

        File noSnippets = new File(dir, "snippets");
        OpenApiErrorResponses.Result fromYaml = OpenApiErrorResponses.apply(noSnippets, yaml.getParentFile(),
            Map.of(500, "Server error"), null);
        OpenApiErrorResponses.Result fromJson = OpenApiErrorResponses.apply(noSnippets, json.getParentFile(),
            Map.of(500, "Server error"), null);
        String yamlText = Files.readString(yaml.toPath());

        assertEquals(3, fromYaml.common());
        assertEquals(0, fromYaml.declared());
        assertEquals("ErrorResponse", fromYaml.schema());
        assertEquals(3, fromJson.common());
        assertFalse(yamlText.contains("&id") || yamlText.contains("*id"), "응답마다 새 맵이라 YAML 앵커가 없다\n" + yamlText);
        assertTrue(Files.readString(json.toPath()).contains("\"500\""));
        assertEquals(0, OpenApiErrorResponses.apply(noSnippets, yaml.getParentFile(), Map.of(500, "Server error"), null)
            .common(), "두 번째 적용은 바꾸지 않는다");
        assertEquals(0, OpenApiErrorResponses.apply(noSnippets, yaml.getParentFile(), Map.of(), null).common(),
            "선언이 없으면 끔");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> document() {
        return (Map<String, Object>) SpecMaps.deepCopy(SwaggerYaml.load(SPEC));
    }

    private static Map<String, Object> responses(Map<String, Object> document, String path) {
        return SpecMaps.map(SpecMaps.operation(document, path, "get"), "responses");
    }

    private static Map<Integer, String> linked(Object... pairs) {
        Map<Integer, String> map = new LinkedHashMap<>();

        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Integer) pairs[i], (String) pairs[i + 1]);
        }

        return map;
    }
}
