package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** 라이브러리가 남긴 operationId 후보로 최종 id 를 정하는 규칙 ({@link OpenApiOperationIds#operationIds}). */
class OpenApiOperationIdsTest {

    private static Map<String, Object> spec(String... operations) {
        StringBuilder yaml = new StringBuilder("openapi: 3.0.1\npaths:\n");

        for (String operation : operations) {
            String[] parts = operation.split(" ");

            yaml.append("  '").append(parts[1]).append("': {").append(parts[0].toLowerCase())
                .append(": {operationId: '").append(parts[2]).append("'}}\n");
        }

        return new Yaml().load(yaml.toString());
    }

    /** {@code paths} 의 같은 경로에 메서드를 더한다 (spec 은 경로마다 operation 하나만 만든다). */
    @SuppressWarnings("unchecked")
    private static void addOperation(Map<String, Object> document, String method, String path, String id) {
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> item = (Map<String, Object>) paths.computeIfAbsent(path, k -> new LinkedHashMap<>());
        Map<String, Object> operation = new LinkedHashMap<>();

        operation.put("operationId", id);
        item.put(method.toLowerCase(), operation);
    }

    private static Resource resource(String testId, String method, String path, int status, Map<String, Object> candidate) {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("operationId", testId);
        json.put("request", Map.of("path", path, "method", method));
        json.put("response", Map.of("status", status));

        return new Resource(testId, json, candidate == null ? Map.of() : Map.of("operationId", candidate));
    }

    private static Map<String, Object> rule(String id, String qualified) {
        Map<String, Object> candidate = new LinkedHashMap<>();

        candidate.put("id", id);

        if (qualified != null) {
            candidate.put("qualified", qualified);
        }

        return candidate;
    }

    private static Map<String, Object> fixed(String id) {
        return Map.of("id", id, "fixed", true);
    }

    @SuppressWarnings("unchecked")
    private static String id(Map<String, Object> document, String method, String path) {
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> item = (Map<String, Object>) paths.get(path);

        return (String) ((Map<String, Object>) item.get(method.toLowerCase())).get("operationId");
    }

    @Test
    void usesHandlerMethodNamesAndQualifiesOnlyCollidingOnes() {
        Map<String, Object> document = spec(
            "POST /users t/create-user",
            "POST /orders t/create-order",
            "GET /users/search t/search-users");
        List<Resource> resources = List.of(
            resource("t/create-user", "POST", "/users", 201, rule("create", "userCreate")),
            resource("t/create-order", "POST", "/orders", 201, rule("create", "orderCreate")),
            resource("t/search-users", "GET", "/users/search", 200, rule("searchUsers", "userSearchUsers")));

        List<String> warnings = OpenApiOperationIds.operationIds(document, resources);

        assertEquals(List.of(), warnings);
        assertEquals("userCreate", id(document, "POST", "/users"));
        assertEquals("orderCreate", id(document, "POST", "/orders"));
        assertEquals("searchUsers", id(document, "GET", "/users/search"));
    }

    @Test
    void numbersQualifiedIdsThatStillCollideInPathOrder() {
        // 같은 컨트롤러의 오버로드(같은 이름, 다른 경로): 컨트롤러 이름을 붙여도 같으므로 경로 순으로 숫자를 붙인다
        Map<String, Object> document = spec(
            "GET /users/{id} t/b",
            "GET /users/me t/a");
        List<Resource> resources = new ArrayList<>(List.of(
            resource("t/b", "GET", "/users/{id}", 200, rule("get", "userGet")),
            resource("t/a", "GET", "/users/me", 200, rule("get", "userGet"))));

        OpenApiOperationIds.operationIds(document, resources);

        assertEquals("userGet2", id(document, "GET", "/users/{id}"));
        assertEquals("userGet", id(document, "GET", "/users/me"));

        // 실행 순서(조각 순서)가 달라도 같다
        Map<String, Object> again = spec("GET /users/me t/a", "GET /users/{id} t/b");

        Collections.reverse(resources);
        OpenApiOperationIds.operationIds(again, resources);

        assertEquals("userGet2", id(again, "GET", "/users/{id}"));
        assertEquals("userGet", id(again, "GET", "/users/me"));
    }

    @Test
    void keepsFixedIdsAsTheyAreAndWarnsWhenTheyCollide() {
        Map<String, Object> document = spec(
            "GET /a t/a",
            "GET /b t/b",
            "GET /c t/c");
        List<Resource> resources = List.of(
            resource("t/a", "GET", "/a", 200, fixed("사용자.조회")),
            resource("t/b", "GET", "/b", 200, fixed("사용자.조회")),
            resource("t/c", "GET", "/c", 200, rule("사용자.조회", "cQuery")));

        List<String> warnings = OpenApiOperationIds.operationIds(document, resources);

        assertEquals("사용자.조회", id(document, "GET", "/a"));
        assertEquals("사용자.조회", id(document, "GET", "/b"));
        // 규칙 id 가 고정 id 와 겹치면 규칙 쪽을 qualified 로 바꾼다
        assertEquals("cQuery", id(document, "GET", "/c"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("GET /a, GET /b"), warnings.get(0));
    }

    @Test
    void usesOneIdPerOperationEvenWhenSeveralTestsDocumentIt() {
        Map<String, Object> document = spec("GET /items/{id} merged");
        List<Resource> resources = List.of(
            resource("t/get-item-not-found", "GET", "/items/{id}", 404, fixed("findItemMissing")),
            resource("t/get-item", "GET", "/items/{id}", 200, fixed("findItem")));

        List<String> warnings = OpenApiOperationIds.operationIds(document, resources);

        // 성공 응답 조각이 대표다. 다른 고정 id 가 섞였으면 경고한다
        assertEquals("findItem", id(document, "GET", "/items/{id}"));
        assertEquals(1, warnings.size());
    }

    @Test
    void fallsBackToTestNameIdsWithoutCandidates() {
        Map<String, Object> document = spec("GET /x x/y", "GET /z x/y/");

        addOperation(document, "POST", "/x", "r/create");

        List<Resource> resources = List.of(
            resource("x/y", "GET", "/x", 200, null),
            resource("x/y", "GET", "/z", 200, null),
            resource("r/create", "POST", "/x", 201, rule("create", "rCreate")));

        OpenApiOperationIds.operationIds(document, resources);

        assertEquals("x.y", id(document, "GET", "/x"));
        assertEquals("x.y-2", id(document, "GET", "/z"));
        assertEquals("create", id(document, "POST", "/x"));
    }
}
