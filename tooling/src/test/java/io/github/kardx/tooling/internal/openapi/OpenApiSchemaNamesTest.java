package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.io.JsonFiles;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiSchemaNamesTest {

    @Test
    void qualifiesAutomaticNamesOfDifferentTypesWithTheSameSimpleName(@TempDir File dir) throws IOException {
        File order = operation(dir, "order", "response", "Item", fields("id", "name"),
            auto("Item", "com.shop.order.Item", "Item", "OrderItem", "ShopOrderItem"));
        File cart = operation(dir, "cart", "response", "Item", fields("code", "qty"),
            auto("Item", "com.shop.cart.Item", "Item", "CartItem", "ShopCartItem"));
        File again = operation(dir, "order-again", "response", "Item", fields("id", "name"),
            auto("Item", "com.shop.order.Item", "Item", "OrderItem", "ShopOrderItem"));

        OpenApiSchemaNames.Result result = OpenApiSchemaNames.resolve(dir);

        assertEquals("OrderItem", schemaName(order, "response"));
        assertEquals("OrderItem", schemaName(again, "response"));
        assertEquals("CartItem", schemaName(cart, "response"));
        assertEquals(2, result.changes().size(), result.changes().toString());
        assertTrue(result.changes().get(0).endsWith("— 2 places") || result.changes().get(1).endsWith("— 2 places"));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void suffixesRequestSchemaWhenRequestAndResponseOfTheSameTypeDiffer(@TempDir File dir) throws IOException {
        File create = operation(dir, "create", "request", "Account", fields("id", "password"),
            auto("Account", "com.shop.Account", "Account"));
        File read = operation(dir, "read", "response", "Account", fields("id", "createdAt"),
            auto("Account", "com.shop.Account", "Account"));

        OpenApiSchemaNames.resolve(dir);

        assertEquals("AccountRequest", schemaName(create, "request"));
        assertEquals("Account", schemaName(read, "response"));
    }

    @Test
    void keepsUserNamesAndMovesClashingAutomaticNames(@TempDir File dir) throws IOException {
        File named = operation(dir, "named", "response", "Item", fields("code"), null);
        File automatic = operation(dir, "automatic", "response", "Item", fields("id"),
            auto("Item", "com.shop.order.Item", "Item", "OrderItem"));

        OpenApiSchemaNames.resolve(dir);

        assertEquals("Item", schemaName(named, "response"));
        assertEquals("OrderItem", schemaName(automatic, "response"));
    }

    @Test
    void mergesDifferentShapesUnderOneUserName(@TempDir File dir) throws IOException {
        File notFound =
            operation(dir, "not-found", "response", "ErrorResponse", fields("status", "message"), null);
        File invalid =
            operation(dir, "invalid", "response", "ErrorResponse", fields("status", "message", "errors"), null);

        OpenApiSchemaNames.Result result = OpenApiSchemaNames.resolve(dir);

        assertEquals("ErrorResponse", schemaName(notFound, "response"));
        assertEquals(Map.of("status", false, "message", false, "errors", true), optionality(notFound));
        assertEquals(optionality(notFound), optionality(invalid));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void warnsWhenUserNamedShapesConflictOnAFieldType(@TempDir File dir) throws IOException {
        operation(dir, "a", "response", "Result", valueField("STRING"), null);
        operation(dir, "b", "response", "Result", valueField("NUMBER"), null);

        OpenApiSchemaNames.Result result = OpenApiSchemaNames.resolve(dir);

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("[value]"), result.warnings().get(0));
    }

    private static String fields(String... paths) {
        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < paths.length; i++) {
            json.append(i == 0 ? "" : ",")
                .append("{\"path\":\"").append(paths[i]).append("\",\"type\":\"STRING\",\"optional\":false}");
        }

        return json.append(']').toString();
    }

    /** 경로가 {@code value} 이고 타입이 [type] 인 필드 하나. */
    private static String valueField(String type) {
        return "[{\"path\":\"value\",\"type\":\"" + type + "\",\"optional\":false}]";
    }

    private static String auto(String name, String type, String... candidates) {
        return "{\"name\":\"" + name + "\",\"automatic\":true,\"type\":\"" + type + "\",\"candidates\":[\""
            + String.join("\",\"", candidates) + "\"]}";
    }

    private static File operation(File dir, String test, String side, String schema, String fields, String origin)
        throws IOException {
        File directory = new File(new File(dir, "shop-api-test"), test);
        String fieldsKey = side.equals("request") ? "requestFields" : "responseFields";
        String part = "{\"schema\":{\"name\":\"" + schema + "\"},\"" + fieldsKey + "\":" + fields + "}";
        String resource = "{\"request\":" + (side.equals("request") ? part : "{}") + ",\"response\":"
            + (side.equals("response") ? part : "{}") + "}";

        Files.createDirectories(directory.toPath());
        Files.writeString(new File(directory, "resource.json").toPath(), resource, StandardCharsets.UTF_8);

        if (origin != null) {
            Files.writeString(new File(directory, OpenApiSchemaNames.METADATA_FILE).toPath(),
                "{\"" + side + "\":" + origin + "}", StandardCharsets.UTF_8);
        }

        return new File(directory, "resource.json");
    }

    @SuppressWarnings("unchecked")
    private static String schemaName(File resource, String side) {
        Map<String, Object> json = (Map<String, Object>) JsonFiles.read(resource);

        return String.valueOf(((Map<String, Object>) ((Map<String, Object>) json.get(side)).get("schema")).get("name"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Boolean> optionality(File resource) {
        Map<String, Object> json = (Map<String, Object>) JsonFiles.read(resource);
        List<Map<String, Object>> fields =
            (List<Map<String, Object>>) ((Map<String, Object>) json.get("response")).get("responseFields");
        Map<String, Boolean> result = new LinkedHashMap<>();

        fields.forEach(field -> result.put(String.valueOf(field.get("path")), (Boolean) field.get("optional")));

        return result;
    }
}
