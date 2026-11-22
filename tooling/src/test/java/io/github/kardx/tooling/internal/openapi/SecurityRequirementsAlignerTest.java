package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityRequirementsAlignerTest {

    @Test
    void copiesSecurityRequirementsToSnippetsOfTheSameOperation(@TempDir File dir) throws Exception {
        File ok = resource(dir, "get-product", "{\"request\":{\"method\":\"GET\",\"path\":\"/products/{id}\","
            + "\"securityRequirements\":{\"type\":\"JWT_BEARER\"}},\"response\":{\"status\":200}}");
        File notFound = resource(dir, "get-product-not-found",
            "{\"request\":{\"method\":\"GET\",\"path\":\"/products/{id}\"},\"response\":{\"status\":404}}");
        File other = resource(dir, "list-products",
            "{\"request\":{\"method\":\"GET\",\"path\":\"/products\"},\"response\":{\"status\":200}}");

        int changed = SecurityRequirementsAligner.align(dir);

        assertEquals(1, changed);
        assertTrue(Files.readString(notFound.toPath(), StandardCharsets.UTF_8).contains("JWT_BEARER"));
        assertFalse(Files.readString(other.toPath(), StandardCharsets.UTF_8).contains("securityRequirements"),
            "다른 operation 은 건드리지 않는다");
        assertTrue(Files.readString(ok.toPath(), StandardCharsets.UTF_8).contains("JWT_BEARER"));
    }

    private static File resource(File dir, String test, String json) throws Exception {
        File file = new File(new File(new File(dir, "product-api-docs-test"), test), "resource.json");
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);

        return file;
    }
}
