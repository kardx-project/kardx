package io.github.kardx.tooling.internal.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwaggerYamlTest {

    @Test
    void keepsStringsThatLookTypedAndQuotesThem() {
        String yaml = String.join("\n",
            "a: 2026-01-01",
            "b: yes",
            "c: 1",
            "d: true",
            "e: |-",
            "  line1",
            "  line2",
            "");

        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) SwaggerYaml.load(yaml);

        assertEquals("2026-01-01", document.get("a"));
        assertEquals("yes", document.get("b"));
        assertEquals(1, document.get("c"));
        assertEquals(Boolean.TRUE, document.get("d"));

        String dumped = SwaggerYaml.dump(document);
        assertTrue(dumped.contains("a: \"2026-01-01\""), dumped);
        assertTrue(dumped.contains("b: \"yes\""), dumped);
        assertTrue(dumped.contains("e: |-\n  line1\n  line2"), dumped);
    }

    /** Jackson YAML(swagger-core)은 {@code 10:30} 같은 문자열을 따옴표 없이 쓴다. YAML 1.1 의 60진수로 읽으면 안 된다. */
    @Test
    void readsOnlyYaml12CoreNumbersSoJacksonStringsSurviveARewrite() {
        String yaml = String.join("\n",
            "time: 10:30",
            "duration: 1:30:00",
            "clock: 1:30.5",
            "binary: 0b1",
            "grouped: 1_000",
            "octal: 012",
            "int: -12",
            "hex: 0x1F",
            "float: 1.5e3",
            "fraction: .5",
            "infinity: .inf",
            "");

        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) SwaggerYaml.load(yaml);

        assertEquals("10:30", document.get("time"));
        assertEquals("1:30:00", document.get("duration"));
        assertEquals("1:30.5", document.get("clock"));
        assertEquals("0b1", document.get("binary"));
        assertEquals("1_000", document.get("grouped"));
        assertEquals("012", document.get("octal"));
        assertEquals(-12, document.get("int"));
        assertEquals(31, document.get("hex"));
        assertEquals(1500.0, document.get("float"));
        assertEquals(0.5, document.get("fraction"));
        assertEquals(Double.POSITIVE_INFINITY, document.get("infinity"));

        @SuppressWarnings("unchecked")
        Map<String, Object> reread = (Map<String, Object>) SwaggerYaml.load(SwaggerYaml.dump(document));

        assertEquals(document, reread);
    }

    @Test
    void specFilesKeepTheirFormat(@TempDir File dir) throws Exception {
        File json = new File(dir, "openapi3.json");
        Files.writeString(json.toPath(), "{\"openapi\":\"3.0.1\"}", StandardCharsets.UTF_8);
        File yaml = new File(dir, "openapi3.yaml");
        Files.writeString(yaml.toPath(), "openapi: 3.0.1\n", StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "notes.txt").toPath(), "x", StandardCharsets.UTF_8);

        assertEquals(2, OpenApiSpecFiles.list(dir).size());
        assertEquals(List.of(), OpenApiSpecFiles.list(new File(dir, "missing")));

        OpenApiSpecFiles.write(json, OpenApiSpecFiles.read(json));
        OpenApiSpecFiles.write(yaml, OpenApiSpecFiles.read(yaml));

        assertTrue(Files.readString(json.toPath(), StandardCharsets.UTF_8).contains("\"openapi\": \"3.0.1\""));
        assertEquals("openapi: 3.0.1\n", Files.readString(yaml.toPath(), StandardCharsets.UTF_8));
    }
}
