package io.github.kardx.tooling.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.io.FileTrees;
import io.github.kardx.tooling.internal.io.TextFiles;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiDocsCliTest {
    @TempDir
    File dir;

    private File snippets;
    private File docs;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void copyFixture() throws URISyntaxException {
        File fixture = Path.of(Objects.requireNonNull(getClass().getResource("cli-snippets")).toURI()).toFile();

        snippets = new File(dir, "target/generated-snippets");
        docs = new File(dir, "target/api-docs");
        FileTrees.sync(fixture, List.of("**"), snippets);
    }

    @Test
    void apiDocsWritesSpecViewerHttpClientPostmanAndCoverage() {
        TextFiles.write(new File(dir, "restdocs-tooling.json"), """
            {
              "language": "ko",
              "openApi": {
                "title": "Memo API", "version": "2.0", "commonResponses": { "500": "Server error" },
                "info": { "contact": { "name": "API team" }, "tags": { "Memos": { "description": "Memo endpoints" } } }
              },
              "viewer": { "enabled": true, "type": "scalar" },
              "httpClient": { "enabled": true },
              "postman": { "enabled": true }
            }
            """);

        int status = run("api-docs", "--base-dir", dir.getPath(), "--config", "restdocs-tooling.json");

        assertEquals(0, status, err());

        String spec = TextFiles.read(new File(docs, "openapi3.yaml"));

        assertTrue(spec.contains("title: Memo API"), spec);
        assertTrue(spec.contains("version: \"2.0\"") || spec.contains("version: '2.0'"), spec);
        assertTrue(spec.contains("/api/v1/memos/{id}:"), spec);
        assertTrue(spec.contains("Server error"), spec);
        assertTrue(spec.contains("name: API team"), spec);
        assertTrue(new File(docs, "openapi.html").isFile());
        assertTrue(new File(docs, "http-client/api.http").isFile());
        assertTrue(new File(docs, "postman-collection.json").isFile());
        assertTrue(TextFiles.read(new File(docs, "coverage/coverage.md")).contains("/api/v1/internal/ping"));
        assertTrue(out().contains("API docs coverage: 3/4 endpoints documented"), out());
    }

    @Test
    void commandLineOptionsOverrideTheConfigFile() {
        TextFiles.write(new File(dir, "config.json"), """
            { "openApi": { "title": "From file", "format": "yaml" } }
            """);

        int status = run("openapi", "--base-dir", dir.getPath(), "--config", "config.json", "--title", "From CLI",
            "--format", "json", "--server", "https://api.example.com");

        assertEquals(0, status, err());

        String spec = TextFiles.read(new File(docs, "openapi3.json"));

        assertTrue(spec.contains("\"From CLI\""), spec);
        assertTrue(spec.contains("https://api.example.com"), spec);
        assertFalse(new File(docs, "openapi3.yaml").exists());
    }

    @Test
    void coverageFailsOnUndocumentedEndpointsWhenAsked() {
        TextFiles.write(new File(dir, "config.json"), """
            { "coverage": { "failOnUndocumented": true } }
            """);

        assertEquals(1, run("coverage", "--base-dir", dir.getPath(), "--config", "config.json"));
        assertTrue(err().contains("GET /api/v1/internal/ping"), err());
        assertTrue(err().contains("coverage.excludes"), err());
    }

    @Test
    void checkComparesWithTheBaseline() {
        assertEquals(0, run("openapi", "--base-dir", dir.getPath()), err());
        assertEquals(1, run("check", "--base-dir", dir.getPath()));
        assertTrue(err().contains("No OpenAPI baseline"), err());
        assertTrue(err().contains("the 'baseline' command"), err());

        assertEquals(0, run("baseline", "--base-dir", dir.getPath()), err());
        assertTrue(new File(dir, "docs/openapi/openapi3.yaml").isFile());
        assertEquals(0, run("check", "--base-dir", dir.getPath()), err());

        assertEquals(0, run("openapi", "--base-dir", dir.getPath(), "--title", "Renamed"), err());
        assertEquals(1, run("check", "--base-dir", dir.getPath()));
        assertEquals(0, run("check", "--base-dir", dir.getPath(), "--fail-on", "breaking"), err());
    }

    @Test
    void sourceDocsWritesTheDescriptionIndex() {
        TextFiles.write(new File(dir, "src/main/kotlin/demo/Memo.kt"), """
            package demo

            /** A short note. */
            class Memo(
                /** Memo text. */
                val text: String,
            )
            """);

        int status = run("source-docs", "--base-dir", dir.getPath(), "--source", "src/main/kotlin", "--output",
            "target/source-docs.json");

        assertEquals(0, status, err());
        assertTrue(TextFiles.read(new File(dir, "target/source-docs.json")).contains("Memo text."));
    }

    @Test
    void reportsUsageAndSettingErrors() {
        assertEquals(2, run());
        assertEquals(2, run("openapi", "--unknown"));
        assertEquals(2, run("publish"));
        assertEquals(0, run("--help"));

        TextFiles.write(new File(dir, "config.json"), """
            { "openApi": { "tilte": "typo" } }
            """);

        assertEquals(1, run("openapi", "--base-dir", dir.getPath(), "--config", "config.json"));
        assertTrue(err().contains("Unknown setting 'openApi.tilte'"), err());
    }

    @Test
    void failsClearlyWithoutSnippets() {
        assertEquals(1, run("api-docs", "--base-dir", dir.getPath(), "--snippets", "missing"));
        assertTrue(err().contains("No snippets directory"), err());
    }

    private int run(String... args) {
        out.reset();
        err.reset();

        return ApiDocsCli.run(new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8), args);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
