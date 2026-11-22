package io.github.kardx.tooling.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 기준 스펙 비교: 깨는 변경 규칙({@link OpenApiBreakingChanges})과 구조 차이({@link OpenApiStructureDiff}). */
class OpenApiCheckTest {
    private static final String BEFORE = """
        openapi: 3.0.1
        info: {title: Shop, version: 1.0.0}
        paths:
          /orders/{id}:
            get:
              parameters:
              - {name: id, in: path, required: true, schema: {type: integer, format: int64}}
              - {name: fields, in: query, required: false, schema: {type: string}}
              responses:
                "200":
                  description: OK
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/Order"}
                "404": {description: Not Found}
            delete:
              responses:
                "204": {description: No Content}
          /orders:
            post:
              requestBody:
                content:
                  application/json:
                    schema: {$ref: "#/components/schemas/CreateOrder"}
              responses:
                "201": {description: Created}
        components:
          schemas:
            Order:
              type: object
              required: [id, status]
              properties:
                id: {type: integer, format: int64}
                status: {type: string, enum: [READY, PAID, SHIPPED]}
                memo: {type: string}
                lines:
                  type: array
                  items:
                    type: object
                    properties:
                      sku: {type: string}
            CreateOrder:
              type: object
              required: [item]
              properties:
                item: {type: string}
                coupon: {type: string}
                channel: {type: string, enum: [WEB, APP]}
        """;

    @Test
    void sameDocumentHasNoChanges() {
        Map<String, Object> before = yaml(BEFORE);

        assertEquals(List.of(), OpenApiBreakingChanges.find(before, yaml(BEFORE)));
        assertEquals(List.of(), OpenApiStructureDiff.compare(before, yaml(BEFORE)));
    }

    @Test
    void findsBreakingChangesByRule() {
        String optionalFields = "{name: fields, in: query, required: false";
        Map<String, Object> after = yaml(BEFORE.replace(optionalFields, optionalFields.replace("false", "true"))
            .replace("enum: [READY, PAID, SHIPPED]", "enum: [READY, PAID, SHIPPED, CANCELED]")
            .replace("enum: [WEB, APP]", "enum: [WEB]")
            .replace("memo: {type: string}", "")
            .replace("sku: {type: string}", "sku: {type: integer}")
            .replace("required: [item]", "required: [item, coupon]"));
        Map<String, Object> item = child(child(after, "paths"), "/orders/{id}");

        item.remove("delete");
        list(child(item, "get"), "parameters").add(yaml("{name: page, in: query, required: true}"));

        assertEquals(List.of(
            "Parameter became required: GET /orders/{id} parameter query fields",
            "Required parameter added: GET /orders/{id} parameter query page",
            "Response enum value added: GET /orders/{id} 200 application/json status [CANCELED]",
            "Response field removed: GET /orders/{id} 200 application/json memo",
            "Type changed: GET /orders/{id} 200 application/json lines[].sku (string → integer)",
            "Endpoint removed: DELETE /orders/{id}",
            "Field became required: POST /orders request application/json coupon",
            "Enum value removed: POST /orders request application/json channel [APP]"),
            OpenApiBreakingChanges.find(yaml(BEFORE), after));
    }

    @Test
    void additionsAndRenamedSchemasAreNotBreaking() {
        String after = BEFORE.replace("\n    Order:\n", "\n    OrderResponse:\n")
            .replace("#/components/schemas/Order\"", "#/components/schemas/OrderResponse\"")
            .replace("memo: {type: string}", "memo: {type: string}\n        note: {type: string}")
            .replace("enum: [READY, PAID, SHIPPED]", "enum: [READY, PAID]")
            .replace("enum: [WEB, APP]", "enum: [WEB, APP, KIOSK]")
            .replace("info: {title: Shop, version: 1.0.0}", "info: {title: Shop, version: 1.1.0}");

        List<String> differences = OpenApiStructureDiff.compare(yaml(BEFORE), yaml(after));

        assertEquals(List.of(), OpenApiBreakingChanges.find(yaml(BEFORE), yaml(after)));
        assertTrue(differences.contains("changed  info > version: 1.0.0 → 1.1.0"), differences.toString());
        assertTrue(differences.contains("removed  components > schemas > Order"), differences.toString());
        assertTrue(differences.contains("added  components > schemas > OrderResponse"), differences.toString());
    }

    /** 경로 변수 이름만 바뀌면 요청은 그대로다. 깨는 변경이 아니라 경고로 알린다. */
    @Test
    void renamedPathVariableIsAWarningNotARemovedEndpoint() {
        Map<String, Object> after = yaml(BEFORE.replace("/orders/{id}:", "/orders/{orderId}:")
            .replace("{name: id, in: path", "{name: orderId, in: path"));

        OpenApiBreakingChanges.Changes changes = OpenApiBreakingChanges.analyze(yaml(BEFORE), after);

        assertEquals(List.of(), changes.breaking());
        assertEquals(List.of("Path variables renamed: /orders/{id} → /orders/{orderId}"), changes.warnings());

        Map<String, Object> typeChanged = yaml(BEFORE.replace("/orders/{id}:", "/orders/{orderId}:")
            .replace("{name: id, in: path, required: true, schema: {type: integer, format: int64}}",
                "{name: orderId, in: path, required: true, schema: {type: string}}"));

        assertEquals(List.of("Type changed: GET /orders/{id} parameter path orderId (integer(int64) → string)"),
            OpenApiBreakingChanges.find(yaml(BEFORE), typeChanged), "이름이 바뀐 변수도 자리로 짝지어 비교한다");
    }

    /** 제한 없던 요청 값에 enum 이 생기면 클라이언트가 보내던 값이 거절될 수 있다. 응답에서 enum 이 빠지는 것은 괜찮다. */
    @Test
    void enumAddedToARequestValueIsBreaking() {
        Map<String, Object> after = yaml(BEFORE.replace("coupon: {type: string}",
                "coupon: {type: string, enum: [WELCOME]}")
            .replace("status: {type: string, enum: [READY, PAID, SHIPPED]}", "status: {type: string}"));

        assertEquals(List.of("Request enum added: POST /orders request application/json coupon [WELCOME]"),
            OpenApiBreakingChanges.find(yaml(BEFORE), after));
    }

    @Test
    void selfReferencingSchemasAreComparedOnce() {
        String tree = """
            openapi: 3.0.1
            paths:
              /categories:
                get:
                  responses:
                    "200":
                      content:
                        application/json:
                          schema: {$ref: "#/components/schemas/Category"}
            components:
              schemas:
                Category:
                  type: object
                  properties:
                    name: {type: string}
                    parent: {$ref: "#/components/schemas/Category"}
                    left: {$ref: "#/components/schemas/Category"}
                    right: {$ref: "#/components/schemas/Category"}
                    children: {type: array, items: {$ref: "#/components/schemas/Category"}}
            """;

        List<String> breaking = assertTimeoutPreemptively(Duration.ofSeconds(5),
            () -> OpenApiBreakingChanges.find(yaml(tree), yaml(tree.replace("name: {type: string}", ""))));

        assertEquals(List.of("Response field removed: GET /categories 200 application/json name"), breaking);
    }

    @Test
    void parameterOrderIsNotAChange() {
        Map<String, Object> swapped = yaml(BEFORE);

        Collections.reverse(list(child(child(child(swapped, "paths"), "/orders/{id}"), "get"), "parameters"));

        assertEquals(List.of(), OpenApiStructureDiff.compare(yaml(BEFORE), swapped), "파라미터는 이름(in)으로 맞춘다");
    }

    /** 바뀐 것이 없으면 보고서 파일(PR 코멘트에 붙이는 글)도 같다고 말한다. */
    @Test
    void summaryOfAnUnchangedSpecSaysItMatches() {
        String summary = OpenApiChangeReport.summary(new File("base.yaml"), new File("gen.yaml"), List.of(), List.of(),
            List.of(), 20);

        assertEquals(String.join("\n",
            "The OpenAPI spec matches the baseline (baseline base.yaml ↔ generated gen.yaml)",
            "Breaking changes: 0",
            "All structural differences: 0"), summary);
    }

    @Test
    void summaryListsBothSectionsWithLimit() {
        String summary = OpenApiChangeReport.summary(new File("base.yaml"), new File("gen.yaml"),
            List.of("Endpoint removed: DELETE /x"), List.of("Default changed: y"),
            List.of("removed  a", "removed  b", "removed  c"), 2);

        assertTrue(summary.contains("Breaking changes: 1\n  - Endpoint removed: DELETE /x\nWarnings: 1\n"
            + "  - Default changed: y\n"), summary);
        assertTrue(summary.startsWith("The OpenAPI spec differs from the baseline (baseline base.yaml ↔ generated "
            + "gen.yaml)\n"), summary);
        assertTrue(summary.contains("All structural differences: 3\n  - removed  a\n  - removed  b\n"
            + "  - … and 1 more (see the report)"), summary);
    }

    @Test
    void findsTightenedConstraintsSecurityHeadersAndVariants() {
        String before = """
            openapi: 3.0.1
            security: []
            paths:
              /payments:
                post:
                  parameters:
                  - {name: q, in: query, schema: {type: string, maxLength: 100}}
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          properties:
                            memo: {type: string, minLength: 1, maxLength: 200}
                            amount: {type: integer, maximum: 1000, default: 10}
                            method:
                              oneOf: [{$ref: "#/components/schemas/Card"}, {$ref: "#/components/schemas/Bank"}]
                  responses:
                    "200":
                      description: OK
                      headers: {X-Total-Count: {schema: {type: integer}}, ETag: {schema: {type: string}}}
                      content:
                        application/json:
                          schema:
                            oneOf: [{$ref: "#/components/schemas/Card"}]
              /orders:
                get:
                  security: [{bearerAuth: []}]
                  responses: {"200": {description: OK}}
            components:
              schemas:
                Card: {type: object}
                Bank: {type: object}
                Cash: {type: object}
            """;
        String after = before.replace("maxLength: 100}", "maxLength: 50}")
            .replace("minLength: 1, maxLength: 200}", "minLength: 2, maxLength: 200, pattern: '^[a-z]+$'}")
            .replace("maximum: 1000, default: 10}", "maximum: 2000, default: 20}")
            .replace("{$ref: \"#/components/schemas/Card\"}, {$ref: \"#/components/schemas/Bank\"}",
                "{$ref: \"#/components/schemas/Card\"}")
            .replace("oneOf: [{$ref: \"#/components/schemas/Card\"}]\n",
                "oneOf: [{$ref: \"#/components/schemas/Card\"}, {$ref: \"#/components/schemas/Cash\"}]\n")
            .replace("{X-Total-Count: {schema: {type: integer}}, ETag", "{etag")
            .replace("security: [{bearerAuth: []}]", "security: [{oauth2: [orders:read]}]")
            .replace("  /payments:\n    post:\n", "  /payments:\n    post:\n      security: [{apiKey: []}]\n");

        OpenApiBreakingChanges.Changes changes = OpenApiBreakingChanges.analyze(yaml(before), yaml(after));

        assertEquals(List.of(
            "Security requirement added: POST /payments [apiKey]",
            "Request constraint tightened: POST /payments parameter query q (maxLength 100 → 50)",
            "Request constraint tightened: POST /payments request application/json memo (minLength 1 → 2)",
            "Request constraint tightened: POST /payments request application/json memo (pattern (none) → ^[a-z]+$)",
            "oneOf variant removed: POST /payments request application/json method [Bank]",
            "Response header removed: POST /payments 200 X-Total-Count",
            "Security scheme changed: GET /orders ([bearerAuth] → [oauth2])"), changes.breaking());
        assertEquals(List.of(
            "Default changed: POST /payments request application/json amount (10 → 20)",
            "Response oneOf variant added: POST /payments 200 application/json [Cash]"), changes.warnings(),
            "헤더 이름 대소문자만 바뀐 ETag 는 그대로, maximum 이 커진 것은 넓어진 것");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String text) {
        return (Map<String, Object>) SwaggerYaml.load(text);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> parent, String key) {
        return (List<Object>) parent.get(key);
    }
}
