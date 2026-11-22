package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.openapi.OpenApiCommonResponses.ErrorBody;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
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

/** 테스트가 선언한 응답({@link OpenApiDeclaredResponses})과 공통 응답과의 순서({@link OpenApiErrorResponses}). */
class OpenApiDeclaredResponsesTest {
    /** 보안이 없는 주문 생성(400 을 테스트가 만듦)과 주문 조회. */
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /orders:
            post:
              operationId: createOrder
              responses:
                "201": {description: Created}
                "400":
                  description: Bad Request
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/ErrorResponse"}
          /orders/{id}:
            get:
              operationId: getOrder
              responses:
                "200": {description: OK}
        components:
          schemas:
            ErrorResponse: {title: ErrorResponse, type: object, properties: {code: {type: string}}}
        """;
    private static final Map<String, Object> DUPLICATE = Map.of(
        "type", "object", "properties", Map.of("code", Map.of("type", "string"), "orderId", Map.of("type", "number")));
    private static final ErrorBody FALLBACK = new ErrorBody("ErrorResponse", "application/json");
    private static final ErrorBody NO_BODY = new ErrorBody(null, null);

    @Test
    void withoutSchemaUsesCommonErrorBodyOrDescriptionOnly() {
        Map<String, Object> document = document();
        Map<String, Object> other = document();
        List<Resource> resources = List.of(resource("a", "POST", "/orders",
            declared(409, "Duplicate order", "application/problem+json", null, null)));

        assertEquals(1, OpenApiDeclaredResponses.transform(document, resources, FALLBACK, new ArrayList<>()));
        assertEquals(1, OpenApiDeclaredResponses.transform(other, resources, NO_BODY, new ArrayList<>()));

        assertEquals(Map.of("description", "Duplicate order", "content", Map.of("application/problem+json",
                Map.of("schema", Map.of("$ref", "#/components/schemas/ErrorResponse")))),
            response(document, "/orders", "post", "409"), "공통 오류 스키마를 선언한 미디어 타입으로 가리킨다");
        assertEquals(Map.of("description", "Duplicate order"), response(other, "/orders", "post", "409"),
            "공통 오류 스키마가 없으면 설명만");
    }

    @Test
    void nonJsonWithoutFieldsIsStringNotCommonErrorSchema() {
        Map<String, Object> document = document();
        List<Resource> resources = List.of(resource("a", "POST", "/orders",
            declared(503, "Maintenance", "text/plain;charset=UTF-8", null, "{\"down\": true}"),
            declared(502, "Bad gateway", "text/html", null, null)));

        assertEquals(2, OpenApiDeclaredResponses.transform(document, resources, FALLBACK, new ArrayList<>()));

        assertEquals(Map.of("description", "Maintenance", "content", Map.of("text/plain;charset=UTF-8",
                Map.of("schema", Map.of("type", "string"), "example", "{\"down\": true}"))),
            response(document, "/orders", "post", "503"), "JSON 이 아니면 공통 JSON 오류 스키마를 붙이지 않고 예시는 글 그대로");
        assertEquals(Map.of("description", "Bad gateway", "content", Map.of("text/html",
                Map.of("schema", Map.of("type", "string")))),
            response(document, "/orders", "post", "502"));
    }

    @Test
    void inlineSchemaBecomesSharedComponent() {
        Map<String, Object> document = document();
        Map<String, Object> sameAsError = Map.of(
            "type", "object", "properties", Map.of("code", Map.of("type", "string")));
        Map<String, Object> other = Map.of("type", "object", "properties", Map.of("reason", Map.of("type", "string")));
        List<Resource> resources = List.of(
            resource("a", "POST", "/orders", declared(409, "Duplicate", "application/json", DUPLICATE, null),
                declared(422, "Invalid", "application/json", sameAsError, null)),
            resource("b", "GET", "/orders/{id}", declared(409, "Locked", "application/json", DUPLICATE, null),
                declared(410, "Gone", "application/json", null, null)),
            resource("c", "GET", "/orders/{id}", declared(429, "Slow down", "application/json", other, null)));

        OpenApiDeclaredResponses.transform(document, resources, FALLBACK, new ArrayList<>());

        Map<String, Object> schemas = SpecMaps.componentSchemas(document);

        assertEquals(ref("ConflictResponse"), schemaOf(document, "/orders", "post", "409"));
        assertEquals(ref("ConflictResponse"), schemaOf(document, "/orders/{id}", "get", "409"), "같은 스키마는 하나를 쓴다");
        assertEquals(ref("ErrorResponse"), schemaOf(document, "/orders", "post", "422"), "이미 있는 같은 스키마를 쓴다");
        assertEquals(ref("TooManyRequestsResponse"), schemaOf(document, "/orders/{id}", "get", "429"));
        assertEquals(List.of("ErrorResponse", "ConflictResponse", "TooManyRequestsResponse"),
            List.copyOf(schemas.keySet()));
        assertEquals("ConflictResponse", SpecMaps.map(schemas, "ConflictResponse").get("title"));
    }

    @Test
    void differentSchemaWithTakenNameIsQualifiedByOperationId() {
        Map<String, Object> document = document();
        Map<String, Object> other = Map.of("type", "object", "properties", Map.of("reason", Map.of("type", "string")));
        List<Resource> resources = List.of(
            resource("a", "POST", "/orders", declared(409, "Duplicate", "application/json", DUPLICATE, null)),
            resource("b", "GET", "/orders/{id}", declared(409, "Locked", "application/json", other, null)),
            resource("c", "GET", "/orders/{id}", declared(499, "Closed", "application/json", other, null)));

        OpenApiDeclaredResponses.transform(document, resources, FALLBACK, new ArrayList<>());

        assertEquals(ref("ConflictResponse"), schemaOf(document, "/orders", "post", "409"));
        assertEquals(ref("GetOrderConflictResponse"), schemaOf(document, "/orders/{id}", "get", "409"));
        assertEquals(ref("GetOrderConflictResponse"), schemaOf(document, "/orders/{id}", "get", "499"),
            "모르는 상태 코드라도 같은 스키마는 하나를 쓴다");
    }

    @Test
    void examplesAreParsedOnlyForJson() {
        Map<String, Object> document = document();
        List<Resource> resources = List.of(resource("a", "POST", "/orders",
            declared(409, "Duplicate", "application/json", DUPLICATE, "{\n  \"code\": \"DUPLICATE\"\n}"),
            declared(422, "Invalid", "application/json", null, "not json"),
            declared(503, "Down", "text/plain", null, "{\"code\": 1}")));

        OpenApiDeclaredResponses.transform(document, resources, NO_BODY, new ArrayList<>());

        assertEquals(Map.of("code", "DUPLICATE"), media(document, "409", "application/json").get("example"));
        assertEquals("not json", media(document, "422", "application/json").get("example"), "읽히지 않으면 글 그대로");
        assertNull(media(document, "422", "application/json").get("schema"), "공통 오류 스키마가 없으면 예시만");
        assertEquals("{\"code\": 1}", media(document, "503", "text/plain").get("example"), "JSON 이 아니면 글 그대로");
    }

    @Test
    void testedStatusWinsAndResponsesAreOrdered() {
        Map<String, Object> document = document();
        List<Resource> resources = List.of(resource("a", "POST", "/orders",
            declared(500, "Boom", "application/json", null, null),
            declared(400, "Declared bad request", "application/json", DUPLICATE, null),
            declared(401, "Login", "application/json", null, null)));

        int added = OpenApiDeclaredResponses.transform(document, resources, FALLBACK, new ArrayList<>());
        Map<String, Object> responses = SpecMaps.map(SpecMaps.operation(document, "/orders", "post"), "responses");

        assertEquals(2, added);
        assertEquals("Bad Request", SpecMaps.map(responses, "400").get("description"), "테스트가 만든 응답이 이긴다");
        assertEquals(List.of("201", "400", "401", "500"), List.copyOf(responses.keySet()), "상태 코드 순서");
        assertEquals(List.of("ErrorResponse"), List.copyOf(SpecMaps.componentSchemas(document).keySet()),
            "더하지 않은 응답의 스키마는 넣지 않는다");
    }

    @Test
    void mergesTestsAndWarnsOnceAboutConflictingDescriptions() {
        Map<String, Object> document = document();
        List<String> warnings = new ArrayList<>();
        List<Resource> resources = List.of(
            resource("a", "GET", "/orders/{id}", declared(404, "No order", "application/json", null, null)),
            resource("b", "GET", "/orders/{id}", declared(404, "Missing", "application/json", null, null),
                declared(409, "Locked", "application/json", null, null)),
            resource("c", "GET", "/orders/{id}", declared(404, "Gone away", "application/json", null, null)));

        int added = OpenApiDeclaredResponses.transform(document, resources, FALLBACK, warnings);

        assertEquals(2, added);
        assertEquals("No order", response(document, "/orders/{id}", "get", "404").get("description"), "앞선 조각이 이긴다");
        assertEquals(List.of("GET /orders/{id}: response 404 is declared with different descriptions by several tests, "
            + "using 'No order'"), warnings);
    }

    @Test
    void declaredResponsesWinOverCommonResponsesAndIgnoreSecurity(@TempDir File dir) throws Exception {
        File specDir = new File(dir, "spec");
        File snippets = new File(dir, "snippets");

        Files.createDirectories(specDir.toPath());
        Files.writeString(new File(specDir, "openapi3.yaml").toPath(), SPEC, StandardCharsets.UTF_8);
        writeSnippet(snippets, "order/get", "GET", "/orders/{id}",
            declared(401, "Login first", "application/json", null, null),
            declared(409, "Locked", "application/json", DUPLICATE, null));

        OpenApiErrorResponses.Result result = OpenApiErrorResponses.apply(snippets, specDir,
            Map.of(401, "Unauthorized", 409, "Common conflict", 500, ""), null);
        Map<String, Object> document = SpecMaps.map(SwaggerYaml.load(
            Files.readString(new File(specDir, "openapi3.yaml").toPath())));
        Map<String, Object> responses = SpecMaps.map(SpecMaps.operation(document, "/orders/{id}", "get"), "responses");

        assertEquals(2, result.declared());
        assertEquals(3, result.common(), "POST 의 409·500, GET 의 500 (보안이 없어 401 은 없다)");
        assertEquals("ErrorResponse", result.schema(), "선언한 스키마가 공통 응답 본문 선택에 끼지 않는다");
        assertEquals(List.of("200", "401", "409", "500"), List.copyOf(responses.keySet()));
        assertEquals("Login first", SpecMaps.map(responses, "401").get("description"), "보안이 없어도 선언한 401 은 넣는다");
        assertEquals("Locked", SpecMaps.map(responses, "409").get("description"), "선언이 공통 응답을 이긴다");
    }

    @SafeVarargs
    private static void writeSnippet(File snippets, String id, String method, String path,
        Map<String, Object>... declared) {
        File directory = new File(snippets, id);
        Map<String, Object> resource = new LinkedHashMap<>();

        resource.put("operationId", id);
        resource.put("request", Map.of("path", path, "method", method));
        resource.put("response", Map.of("status", 200));
        JsonFiles.write(new File(directory, OpenApiSchemaNames.RESOURCE_FILE), resource);
        JsonFiles.write(new File(directory, OpenApiResources.EXTRAS_FILE), Map.of("errorResponses", List.of(declared)));
    }

    @SafeVarargs
    private static Resource resource(String id, String method, String path, Map<String, Object>... declared) {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("operationId", id);
        json.put("request", Map.of("path", path, "method", method));
        json.put("response", Map.of("status", 200));

        return new Resource(id, json, Map.of("errorResponses", List.of(declared)));
    }

    private static Map<String, Object> declared(
        int status, String description, String contentType, Map<String, Object> schema, String example) {
        Map<String, Object> declared = new LinkedHashMap<>();

        declared.put("status", status);
        declared.put("description", description);
        declared.put("contentType", contentType);

        if (schema != null) {
            declared.put("schema", schema);
        }

        if (example != null) {
            declared.put("example", example);
        }

        return declared;
    }

    private static Map<String, Object> response(
        Map<String, Object> document, String path, String method, String status) {
        return SpecMaps.map(SpecMaps.map(SpecMaps.operation(document, path, method), "responses"), status);
    }

    private static Map<String, Object> media(Map<String, Object> document, String status, String mediaType) {
        return SpecMaps.map(SpecMaps.map(response(document, "/orders", "post", status), "content"), mediaType);
    }

    private static Object schemaOf(Map<String, Object> document, String path, String method, String status) {
        Map<String, Object> content = SpecMaps.map(response(document, path, method, status), "content");

        return SpecMaps.map(SpecMaps.map(content, "application/json"), "schema");
    }

    private static Map<String, Object> ref(String name) {
        return Map.of("$ref", SpecMaps.COMPONENT_PREFIX + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> document() {
        return (Map<String, Object>) SpecMaps.deepCopy(SwaggerYaml.load(SPEC));
    }
}
