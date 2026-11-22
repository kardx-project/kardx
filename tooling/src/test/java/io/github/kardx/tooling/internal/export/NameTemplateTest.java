package io.github.kardx.tooling.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.ToolingException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 파일 이름·URL 자리표시자({@link NameTemplate}). */
class NameTemplateTest {
    private static final Map<String, String> VALUES = NameTemplate.values("shop api", "1.0", SpecKind.OPENAPI, "yaml");

    @Test
    void fillsFileNamesAndRejectsPathsOutsideTheDirectory() {
        assertEquals("shop api-openapi3.yaml", NameTemplate.fileName("{name}-openapi3.{ext}", VALUES, "p"));
        assertEquals("specs/1.0/openapi.yaml", NameTemplate.fileName("specs\\{version}/{type}.{ext}", VALUES, "p"));

        for (String bad : new String[] {"../{name}.yaml", "/tmp/{name}", "C:/x", "{name}/", ""}) {
            ToolingException error = assertThrows(ToolingException.class,
                () -> NameTemplate.fileName(bad, VALUES, "kardx.export.openApiFileName"), bad);

            assertTrue(error.getMessage().startsWith("kardx.export.openApiFileName must be a relative path"),
                error.getMessage());
        }
    }

    @Test
    void encodesUrlValuesAndRejectsUnknownPlaceholders() {
        assertEquals("https://r.example/apis/shop%20api/1.0/openapi",
            NameTemplate.url("https://r.example/apis/{name}/{version}/{type}", VALUES, "u"));

        ToolingException error = assertThrows(ToolingException.class,
            () -> NameTemplate.url("https://r.example/{project}", VALUES, "kardx.upload.url"));

        assertTrue(error.getMessage().contains("Unknown placeholder {project} in kardx.upload.url"),
            error.getMessage());
    }
}
