package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiGlobalHeadersTest {

    @Test
    void movesHeaderParameterIntoApiKeySecurityScheme() {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("parameters", new ArrayList<>(List.of(
            param("id", "path"), param("X-Tenant-Id", "header"), param("X-Request-Id", "header"))));
        operation.put("security", new ArrayList<>(List.of(
            new LinkedHashMap<>(Map.of("bearerAuthJWT", new ArrayList<>())))));
        Map<String, Object> document = document(operation);

        int changed = OpenApiGlobalHeaders.transform(document, headers("X-Tenant-Id"));

        assertEquals(1, changed);
        List<?> parameters = (List<?>) operation.get("parameters");
        assertEquals(2, parameters.size());
        Map<?, ?> requirement = (Map<?, ?>) ((List<?>) operation.get("security")).get(0);
        assertTrue(requirement.containsKey("bearerAuthJWT"), "기존 Bearer 요구사항은 남는다");
        assertTrue(requirement.containsKey("X-Tenant-Id"), "같은 묶음(AND)에 헤더 스키마를 넣는다");
        Map<?, ?> scheme = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) document.get("components"))
            .get("securitySchemes"))
            .get("X-Tenant-Id");
        assertEquals("apiKey", scheme.get("type"));
        assertEquals("header", scheme.get("in"));
        assertEquals("X-Tenant-Id", scheme.get("name"));
        assertEquals("X-Tenant-Id 설명", scheme.get("description"));
    }

    @Test
    void addsSecurityWhenOperationHadNoneAndIsIdempotent() {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("parameters", new ArrayList<>(List.of(param("X-Tenant-Id", "header"))));
        Map<String, Object> document = document(operation);

        assertEquals(1, OpenApiGlobalHeaders.transform(document, headers("x-tenant-id")));
        assertNull(operation.get("parameters"), "파라미터가 비면 키를 지운다");
        assertEquals(List.of(Map.of("X-Tenant-Id", List.of())), operation.get("security"));
        assertEquals(0, OpenApiGlobalHeaders.transform(document, headers("X-Tenant-Id")), "두 번째 적용은 바꾸지 않는다");
    }

    @Test
    void rewritesYamlFileKeepingStringsAndQuotingLikeSwagger(@TempDir File dir) throws Exception {
        File snippets = new File(dir, "snippets");
        File settings = new File(snippets, OpenApiGlobalHeaders.SETTINGS_FILE);
        Files.createDirectories(settings.getParentFile().toPath());
        Files.writeString(settings.toPath(), "[\"X-Tenant-Id\"]", StandardCharsets.UTF_8);
        File spec = new File(dir, "openapi3.yaml");
        Files.writeString(spec.toPath(), String.join("\n",
            "openapi: 3.0.1",
            "paths:",
            "  /todos:",
            "    get:",
            "      parameters:",
            "      - name: X-Tenant-Id",
            "        in: header",
            "        description: 테넌트",
            "        schema:",
            "          type: string",
            "        example: 2026-01-01",
            "      responses:",
            "        \"200\":",
            "          description: \"200\"",
            "          content:",
            "            application/json:",
            "              example: \"yes\"",
            ""), StandardCharsets.UTF_8);

        int changed = OpenApiGlobalHeaders.apply(snippets, List.of(), dir);

        assertEquals(1, changed);
        String yaml = Files.readString(spec.toPath(), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("\"200\":"), "숫자처럼 보이는 키는 큰따옴표로 남는다:\n" + yaml);
        assertTrue(yaml.contains("example: \"yes\""), "yes 는 문자열로 남는다:\n" + yaml);
        assertTrue(yaml.contains("security:\n      - X-Tenant-Id: []"), "security 가 들어간다:\n" + yaml);
        assertTrue(yaml.contains("""
            X-Tenant-Id:
                  type: apiKey
                  in: header
                  name: X-Tenant-Id
                  description: 테넌트"""), yaml);
        assertFalse(yaml.contains("- name: X-Tenant-Id"), "헤더 파라미터는 빠진다:\n" + yaml);
    }

    private static Map<String, Object> document(Map<String, Object> operation) {
        Map<String, Object> item = new LinkedHashMap<>(Map.of("get", operation));
        Map<String, Object> paths = new LinkedHashMap<>(Map.of("/todos", item));

        return new LinkedHashMap<>(Map.of("openapi", "3.0.1", "paths", paths));
    }

    private static Map<String, Object> param(String name, String in) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("name", name);
        parameter.put("in", in);
        parameter.put("description", name + " 설명");

        return parameter;
    }

    private static Set<String> headers(String... names) {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(List.of(names));

        return set;
    }
}
