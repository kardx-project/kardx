package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 클래스 KDoc → 컴포넌트 스키마 설명({@link SchemaDescriptions}). */
class SchemaDescriptionsTest {
    private static final String SOURCE_DOCS = """
        {"version": 1, "types": {
          "com.shop.ApiResponse": {"description": "Common envelope."},
          "com.shop.order.Item": {"description": "Order line."},
          "com.shop.cart.Item": {"description": "Cart line."},
          "com.shop.Outer$Inner": {"description": "Nested class."},
          "com.shop.Undocumented": {"properties": {"id": "ID"}}
        }}
        """;

    @Test
    void picksTheClassTheSchemaNameCameFrom() {
        assertEquals("com.shop.ApiResponse",
            SchemaDescriptions.describedClass("ApiResponseItem", "com.shop.ApiResponse<com.shop.order.Item>"));
        assertEquals("com.shop.order.Item",
            SchemaDescriptions.describedClass("Item", "com.shop.ApiResponse<com.shop.order.Item>"),
            "래퍼를 뗀 이름은 데이터 타입");
        assertEquals("kotlin.collections.List",
            SchemaDescriptions.describedClass("ListItem", "kotlin.collections.List<com.shop.order.Item>"));
        assertEquals("com.shop.Account", SchemaDescriptions.describedClass("AccountRequest", "com.shop.Account?"));
        assertNull(SchemaDescriptions.describedClass("Foo", "Foo"), "패키지 없는 이름은 찾지 않는다");
        assertNull(SchemaDescriptions.describedClass("ProductDetailResponse", "com.shop.ApiResponse<com.shop.ProductResponse>"),
            "직접 정한 이름에 어느 클래스 이름도 없으면 래퍼 설명으로 짐작하지 않는다");
        assertEquals("com.shop.ErrorResponse",
            SchemaDescriptions.describedClass("ErrorResponse", "com.shop.ErrorResponse"), "openApiErrorSchema");
    }

    @Test
    void describesSchemasFromTheSourceDocsIndex(@TempDir File dir) throws IOException {
        File snippets = new File(dir, "snippets");
        File sourceDocs = write(dir, "source-docs.json", SOURCE_DOCS);

        snippet(snippets, "order/get", "OrderItem", "com.shop.order.Item");
        snippet(snippets, "cart/get", "CartItem", "com.shop.cart.Item");
        snippet(snippets, "envelope/get", "ApiResponseItem", "com.shop.ApiResponse<com.shop.order.Item>");
        snippet(snippets, "nested/get", "Inner", "com.shop.Outer.Inner");
        snippet(snippets, "plain/get", "Undocumented", "com.shop.Undocumented");
        // 직접 정한 이름을 두 타입이 같이 쓰면 어느 설명인지 모른다
        snippet(snippets, "shared/a", "Shared", "com.shop.order.Item");
        snippet(snippets, "shared/b", "Shared", "com.shop.cart.Item");

        Map<String, String> descriptions = SchemaDescriptions.read(snippets, sourceDocs);

        assertEquals(Map.of("OrderItem", "Order line.", "CartItem", "Cart line.", "ApiResponseItem",
            "Common envelope.", "Inner", "Nested class."), descriptions);

        Map<String, Object> document = SpecMaps.map(SwaggerYaml.load("""
            openapi: 3.0.1
            components:
              schemas:
                OrderItem: {type: object}
                CartItem: {type: object, description: Kept}
            """));

        assertEquals(1, SchemaDescriptions.transform(document, descriptions));
        assertEquals("Order line.", SpecMaps.map(SpecMaps.componentSchemas(document), "OrderItem").get("description"));
        assertEquals("Kept", SpecMaps.map(SpecMaps.componentSchemas(document), "CartItem").get("description"),
            "이미 있는 설명은 둔다");
    }

    @Test
    void doesNothingWithoutTheIndex(@TempDir File dir) throws IOException {
        File snippets = new File(dir, "snippets");

        snippet(snippets, "order/get", "OrderItem", "com.shop.order.Item");

        assertEquals(Map.of(), SchemaDescriptions.read(snippets, null));
        assertEquals(Map.of(), SchemaDescriptions.read(snippets, new File(dir, "missing.json")));
    }

    /** 라이브러리가 남기는 조각: 응답 스키마 이름(충돌 정리 뒤)과 이름의 출처. */
    private static void snippet(File snippets, String id, String name, String type) throws IOException {
        write(snippets, id + "/resource.json", """
            {"operationId": "%s", "request": {"path": "/x", "method": "GET"},
             "response": {"status": 200, "schema": {"name": "%s"}}}
            """.formatted(id, name));
        write(snippets, id + "/openapi-schemas.json", """
            {"request": null, "response": {"name": "%s", "automatic": true, "type": "%s", "candidates": []}}
            """.formatted(name.replace("Order", "").replace("Cart", ""), type));
    }

    private static File write(File root, String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);

        return file;
    }
}
