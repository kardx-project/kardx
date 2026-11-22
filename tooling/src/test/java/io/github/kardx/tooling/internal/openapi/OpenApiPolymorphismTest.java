package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** sealed·{@code @JsonSubTypes} 본문을 {@code oneOf} 로 바꾼다 ({@link OpenApiPolymorphism}). 픽스처는 계약의 extras 형식이다. */
class OpenApiPolymorphismTest {
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /orders:
            post:
              operationId: createOrder
              requestBody:
                content:
                  application/json:
                    schema: {$ref: "#/components/schemas/CreateOrder"}
              responses:
                "201":
                  description: Created
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/Payment"}
        components:
          schemas:
            CreateOrder:
              type: object
              properties:
                payment:
                  type: object
                  description: 결제 수단
                  properties: {type: {type: string}, cardNumber: {type: string}, bankCode: {type: string}}
                payments:
                  type: array
                  items:
                    type: object
                    properties: {type: {type: string}}
            Payment:
              type: object
              properties: {type: {type: string}, cardNumber: {type: string}}
            CardPayment:
              type: object
              properties: {legacy: {type: boolean}}
        """;
    private static final String WRAPPED_SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /orders:
            post:
              operationId: createOrder
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
                      properties:
                        data:
                          type: object
                          properties:
                            items:
                              type: array
                              items: {$ref: "#/components/schemas/CartItem"}
              responses:
                "201":
                  description: Created
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/RestResponsePaymentResponse"}
        components:
          schemas:
            RestResponsePaymentResponse:
              type: object
              properties:
                code: {type: integer}
                data: {$ref: "#/components/schemas/PaymentResponse"}
            PaymentResponse:
              type: object
              properties:
                id: {type: integer}
                method:
                  type: object
                  description: 결제 수단
                  properties: {type: {type: string}, cardNumber: {type: string}}
            CartItem:
              type: object
              properties:
                name: {type: string}
                method: {$ref: "#/components/schemas/FlatPayment"}
            FlatPayment:
              type: object
              properties: {type: {type: string}}
        """;
    private static final String VARIANTS = """
        [ { "name": "CardPayment",
            "schema": { "type": "object", "required": ["type"],
                        "properties": { "type": { "type": "string" }, "cardNumber": { "type": "string" } } } },
          { "name": "BankTransfer",
            "schema": { "type": "object", "properties": { "type": { "type": "string" },
                                                          "bankCode": { "type": "string" } } } } ]
        """;
    private static final String DISCRIMINATOR =
        "{ \"property\": \"type\", \"mapping\": { \"CARD\": \"CardPayment\", \"BANK\": \"BankTransfer\" } }";

    @TempDir
    File snippets;

    @Test
    void replacesNestedArrayAndRootSchemasWithOneOf() throws IOException {
        fragment("create-order", """
            { "requestBodyPolymorphism": [
                { "path": "payment", "baseName": "Payment", "discriminator": %1$s, "variants": %2$s },
                { "path": "payments[]", "baseName": "Payment", "discriminator": null, "variants": %2$s } ],
              "responseBodyPolymorphism": [
                { "path": "", "baseName": "Payment", "discriminator": %1$s, "variants": %2$s } ] }
            """.formatted(DISCRIMINATOR, VARIANTS));
        Map<String, Object> document = document();

        assertEquals(3, OpenApiPolymorphism.transform(document, OpenApiResources.read(snippets)));

        Map<String, Object> schemas = SpecMaps.componentSchemas(document);
        Map<String, Object> createOrder = SpecMaps.map(SpecMaps.map(schemas, "CreateOrder"), "properties");
        Map<String, Object> discriminator = Map.of("propertyName", "type", "mapping", Map.of(
            "CARD", "#/components/schemas/PaymentCardPayment", "BANK", "#/components/schemas/BankTransfer"));
        List<Object> oneOf = List.of(ref("PaymentCardPayment"), ref("BankTransfer"));

        assertEquals(Map.of("oneOf", oneOf, "discriminator", discriminator, "description", "결제 수단"),
            createOrder.get("payment"), "이름이 겹친 CardPayment 는 상위 타입 이름을 붙이고, 자리의 설명은 남긴다");
        assertEquals(Map.of("oneOf", oneOf), SpecMaps.map(SpecMaps.map(createOrder, "payments"), "items"),
            "식별자가 없으면 discriminator 를 넣지 않는다");
        assertEquals(Map.of("oneOf", oneOf, "discriminator", discriminator),
            SpecMaps.map(responseMedia(document), "schema"));
        assertEquals("PaymentCardPayment", SpecMaps.map(schemas, "PaymentCardPayment").get("title"));
        assertEquals(Map.of("legacy", Map.of("type", "boolean")),
            SpecMaps.map(SpecMaps.map(schemas, "CardPayment"), "properties"), "원래 CardPayment 는 그대로");
        assertNull(schemas.get("Payment"), "본문 루트가 가리키던 평탄한 스키마는 더 쓰는 곳이 없으면 뺀다");
    }

    @Test
    void replacesSchemasUnderWrapperThroughReferencesAndArrays() throws IOException {
        fragment("create-order", """
            { "requestBodyPolymorphism": [
                { "path": "data.items[].method", "baseName": "Payment", "discriminator": null, "variants": %2$s } ],
              "responseBodyPolymorphism": [
                { "path": "data.method", "baseName": "Payment", "discriminator": %1$s, "variants": %2$s } ] }
            """.formatted(DISCRIMINATOR, VARIANTS));
        Map<String, Object> document = SnippetFixtures.yaml(WRAPPED_SPEC);

        assertEquals(2, OpenApiPolymorphism.transform(document, OpenApiResources.read(snippets)));

        Map<String, Object> schemas = SpecMaps.componentSchemas(document);
        Map<String, Object> payment = SpecMaps.map(SpecMaps.map(schemas, "PaymentResponse"), "properties");
        Map<String, Object> item = SpecMaps.map(SpecMaps.map(schemas, "CartItem"), "properties");
        List<Object> oneOf = List.of(ref("CardPayment"), ref("BankTransfer"));

        assertEquals(oneOf, SpecMaps.map(payment, "method").get("oneOf"), "래퍼의 data $ref 를 따라 내려간다");
        assertEquals("결제 수단", SpecMaps.map(payment, "method").get("description"));
        assertEquals(Map.of("oneOf", oneOf), item.get("method"), "배열 원소의 $ref 를 따라 내려간다");
        assertNull(schemas.get("FlatPayment"), "바꾼 자리가 가리키던 스키마는 더 쓰는 곳이 없으면 뺀다");
    }

    @Test
    void replacesWrapperDataItself() throws IOException {
        fragment("create-order", """
            { "responseBodyPolymorphism": [
                { "path": "data", "baseName": "Payment", "discriminator": %1$s, "variants": %2$s } ] }
            """.formatted(DISCRIMINATOR, VARIANTS));
        Map<String, Object> document = SnippetFixtures.yaml(WRAPPED_SPEC);

        assertEquals(1, OpenApiPolymorphism.transform(document, OpenApiResources.read(snippets)));

        Map<String, Object> schemas = SpecMaps.componentSchemas(document);
        Map<String, Object> wrapper = SpecMaps.map(SpecMaps.map(schemas, "RestResponsePaymentResponse"), "properties");

        assertEquals(List.of(ref("CardPayment"), ref("BankTransfer")), SpecMaps.map(wrapper, "data").get("oneOf"));
        assertNull(schemas.get("PaymentResponse"), "data 가 가리키던 평탄한 스키마는 뺀다");
    }

    @Test
    void unknownPathAndMissingExtrasChangeNothing() throws IOException {
        fragment("create-order", """
            { "requestBodyPolymorphism": [
                { "path": "missing.field", "baseName": "Payment", "discriminator": null, "variants": %s } ] }
            """.formatted(VARIANTS));
        Map<String, Object> document = document();

        assertEquals(0, OpenApiPolymorphism.transform(document, OpenApiResources.read(snippets)));
        assertFalse(SpecMaps.componentSchemas(document).containsKey("BankTransfer"));
    }

    private void fragment(String name, String extras) throws IOException {
        SnippetFixtures.fragment(snippets, "order-docs/" + name, "POST", "/orders", 201, extras);
    }

    private static Map<String, Object> responseMedia(Map<String, Object> document) {
        return SpecMaps.responseMedia(SpecMaps.operation(document, "/orders", "post"), 201, "application/json");
    }

    private static Map<String, Object> ref(String name) {
        return Map.of("$ref", SpecMaps.COMPONENT_PREFIX + name);
    }

    private static Map<String, Object> document() {
        return SnippetFixtures.yaml(SPEC);
    }
}
