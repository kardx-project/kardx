package io.github.kardx.tooling.internal.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 여러 프로젝트의 스펙 병합 규칙. */
class OpenApiMergerTest {
    private static final String ORDER = """
        openapi: 3.0.1
        info: {title: order, version: 1}
        servers: [{url: /}]
        tags: [{name: 주문, description: 주문 API}]
        security: [{bearerAuth: []}]
        paths:
          /orders:
            get:
              operationId: list
              tags: [주문]
              responses:
                "200":
                  description: OK
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/Page"}
        components:
          schemas:
            Item: {type: object, properties: {sku: {type: string}}}
            Page: {type: object, properties: {items: {type: array, items: {$ref: "#/components/schemas/Item"}}}}
            Error: {type: object, properties: {message: {type: string}}}
          securitySchemes:
            bearerAuth: {type: http, scheme: bearer}
        """;
    private static final String MEMBER = """
        openapi: 3.0.1
        info: {title: member, version: 1}
        servers: [{url: /}]
        tags: [{name: 회원}]
        paths:
          /members:
            get:
              operationId: list
              security: [{bearerAuth: []}]
              responses:
                "200":
                  description: OK
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/Page"}
        components:
          schemas:
            Item: {type: object, properties: {name: {type: string}}}
            Page: {type: object, properties: {items: {type: array, items: {$ref: "#/components/schemas/Item"}}}}
            Error: {type: object, properties: {message: {type: string}}}
          securitySchemes:
            bearerAuth: {type: apiKey, in: header, name: X-Token}
        """;

    @Test
    void mergesPathsAndRenamesConflictingComponents() {
        OpenApiMerger.Result result = OpenApiMerger.merge(
            List.of(source("order", ORDER), source("member", MEMBER)), "전체 API", "2.0.0", null);
        Map<String, Object> document = result.document();
        Map<?, ?> schemas = (Map<?, ?>) ((Map<?, ?>) document.get("components")).get("schemas");
        Map<?, ?> security = (Map<?, ?>) ((Map<?, ?>) document.get("components")).get("securitySchemes");
        String yaml = SwaggerYaml.dump(document);

        assertEquals(Map.of("title", "전체 API", "version", "2.0.0"), document.get("info"));
        assertEquals(List.of("Item", "Page", "Error", "MemberItem", "MemberPage"), List.copyOf(schemas.keySet()),
            "같은 내용(Error)은 하나, 다른 내용(Item)과 그것을 참조해 달라진 Page 는 프로젝트 이름을 붙인다");
        assertEquals(List.of("bearerAuth", "MemberBearerAuth"), List.copyOf(security.keySet()));
        assertTrue(yaml.contains("$ref: \"#/components/schemas/MemberItem\""), yaml);
        assertTrue(yaml.contains("$ref: \"#/components/schemas/MemberPage\""), yaml);
        assertTrue(yaml.contains("- MemberBearerAuth: []"), "회원 operation 의 보안 요구사항도 바꾼다\n" + yaml);
        assertTrue(yaml.contains("operationId: member-list"), "겹치는 operationId\n" + yaml);
        assertEquals(List.of(Map.of("url", "/")), document.get("servers"));
        assertEquals(2, ((List<?>) document.get("tags")).size());
        assertEquals(4, result.notes().size(), result.notes().toString());
        assertTrue(!document.containsKey("security") && yaml.contains("      security:\n      - bearerAuth: []"),
            "주문 프로젝트의 최상위 security 는 주문 operation 으로 내려가고 회원 operation 에 번지지 않는다\n" + yaml);
    }

    @Test
    void renamedComponentDoesNotOverwriteTheProjectsOwnName() {
        String own = "MemberItem: {type: object, properties: {id: {type: integer}}}\n    ";
        String member = MEMBER.replace("Error: {type: object", own + "Error: {type: object");
        Map<?, ?> schemas = (Map<?, ?>) ((Map<?, ?>) OpenApiMerger.merge(
            List.of(source("order", ORDER), source("member", member)), "API", "1", null)
            .document().get("components")).get("schemas");

        assertTrue(schemas.containsKey("MemberItem") && schemas.containsKey("MemberItem2"),
            schemas.keySet().toString());
    }

    @Test
    void sameOperationInTwoProjectsFails() {
        ToolingException error = assertThrows(ToolingException.class, () -> OpenApiMerger.merge(
            List.of(source("order", ORDER), source("legacy", ORDER)), "API", "1", null));

        assertTrue(error.getMessage().contains("GET /orders: order, legacy"), error.getMessage());
    }

    @Test
    void projectNameBecomesPascalCasePrefix() {
        assertEquals("ServicesOrderApi", ComponentNames.pascal("services/order-api"));
        assertEquals("services/member", ProjectDirectories.directoryName(":services:member"));
        assertEquals("root", ProjectDirectories.directoryName(":"));
    }

    @SuppressWarnings("unchecked")
    private static OpenApiMerger.Source source(String project, String yaml) {
        return new OpenApiMerger.Source(project, (Map<String, Object>) SwaggerYaml.load(yaml));
    }
}
