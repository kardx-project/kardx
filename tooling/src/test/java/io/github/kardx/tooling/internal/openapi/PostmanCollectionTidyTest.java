package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.io.JsonFiles;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostmanCollectionTidyTest {

    @Test
    void replacesPlaceholderTokenWithVariableAndKeepsKorean(@TempDir File dir) throws Exception {
        File collection = new File(dir, "postman-collection.json");
        Files.writeString(collection.toPath(), """
            {"info": {"name": "주문"}, "variable": [],
             "item": [{"request": {"header": [
               {"key": "Authorization", "value": "Bearer eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJ4In0.c2ln"},
               {"key": "X-Request-Id", "value": "req-1"}, {"key": "Last-Event-ID", "disabled": false}]}}]}
            """, StandardCharsets.UTF_8);

        assertEquals(2, PostmanCollectionTidy.apply(collection), "토큰 1개, 값 없는 헤더 1개");

        String json = Files.readString(collection.toPath());

        assertTrue(json.contains("\"value\": \"Bearer {{bearerToken}}\""), json);
        assertTrue(json.contains("\"key\": \"bearerToken\""), json);
        Map<?, ?> header = (Map<?, ?>) ((List<?>) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) ((Map<?, ?>) JsonFiles.read(
            collection)).get("item")).get(0)).get("request")).get("header")).get(2);

        assertEquals(Map.of("key", "Last-Event-ID", "value", "", "disabled", true), header, "스키마가 요구하는 value");
        assertTrue(json.contains("\"name\": \"주문\""), "한글을 이스케이프하지 않는다\n" + json);
        assertEquals(0, PostmanCollectionTidy.apply(collection), "두 번째는 바꿀 것이 없다");
        assertEquals(0, PostmanCollectionTidy.apply(new File(dir, "none.json")));
    }
}
