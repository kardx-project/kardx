package io.github.kardx.maven;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.io.FileTrees;
import io.github.kardx.tooling.internal.io.TextFiles;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Maven 없이 목표를 부른다: Maven 이 넣을 파라미터를 필드에 직접 넣는다. 실제 Maven 실행은 samples/maven-boot-4 에서 확인한다. */
class MojoTest {
    @TempDir
    File dir;

    private MavenProject project;
    private File target;

    @BeforeEach
    void setUp() throws URISyntaxException {
        Model model = new Model();

        model.setName("memo-service");
        model.setVersion("3.1.0");
        project = new MavenProject(model);
        project.setFile(new File(dir, "pom.xml"));
        target = new File(dir, "target");

        File fixture = Path.of(Objects.requireNonNull(getClass().getResource("cli-snippets")).toURI()).toFile();

        FileTrees.sync(fixture, List.of("**"), new File(target, "generated-snippets"));
    }

    @Test
    void apiDocsUsesProjectNameAndVersionAndPomParameters() throws Exception {
        ApiDocsMojo mojo = apiDocs();

        mojo.openApiViewer = true;
        mojo.commonResponses = Map.of("500", "Server error");
        mojo.coverageExcludes = List.of("/api/v1/internal/**");
        mojo.execute();

        String spec = TextFiles.read(new File(target, "api-docs/openapi3.yaml"));

        assertTrue(spec.contains("title: memo-service"), spec);
        assertTrue(spec.contains("version: 3.1.0"), spec);
        assertTrue(spec.contains("Server error"), spec);
        assertTrue(new File(target, "api-docs/openapi.html").isFile());
        assertTrue(TextFiles.read(new File(target, "api-docs/coverage/coverage.md")).contains("3/3"));
        assertFalse(new File(target, "api-docs/http-client").exists());
    }

    @Test
    void pomParametersOverrideTheConfigFile() throws Exception {
        File config = new File(dir, "restdocs-tooling.json");

        TextFiles.write(config, """
            { "openApi": { "title": "From file", "format": "json" }, "httpClient": { "enabled": true } }
            """);

        ApiDocsMojo mojo = apiDocs();

        mojo.configFile = config;
        mojo.title = "From POM";
        mojo.execute();

        String spec = TextFiles.read(new File(target, "api-docs/openapi3.json"));

        assertTrue(spec.contains("\"From POM\""), spec);
        assertTrue(new File(target, "api-docs/http-client/api.http").isFile());
    }

    @Test
    void checkFailsWithoutBaselineAndPassesAfterBaseline() throws Exception {
        apiDocs().execute();

        CheckMojo check = setUp(new CheckMojo());

        check.baseline = new File(dir, "docs/openapi/openapi3.yaml");

        MojoFailureException missing = assertThrows(MojoFailureException.class, check::execute);

        assertTrue(missing.getMessage().contains(AbstractApiDocsMojo.HINTS.baselineCommand()), missing.getMessage());

        BaselineMojo baseline = setUp(new BaselineMojo());

        baseline.baseline = check.baseline;
        baseline.execute();
        assertDoesNotThrow(check::execute);
    }

    @Test
    void skipDoesNothing() throws Exception {
        ApiDocsMojo mojo = apiDocs();

        mojo.skip = true;
        mojo.execute();

        assertFalse(new File(target, "api-docs").exists());
    }

    @Test
    void sourceDocsScansKotlinSources() throws Exception {
        TextFiles.write(new File(dir, "src/main/kotlin/demo/Memo.kt"), """
            package demo

            class Memo(
                /** Memo text. */
                val text: String,
            )
            """);

        SourceDocsMojo mojo = setUp(new SourceDocsMojo());

        mojo.sourceDocsFile = new File(target, "source-docs.json");
        mojo.execute();

        assertTrue(TextFiles.read(mojo.sourceDocsFile).contains("Memo text."));
    }

    private ApiDocsMojo apiDocs() {
        ApiDocsMojo mojo = setUp(new ApiDocsMojo());

        mojo.asciidocDirectory = new File(target, "generated-asciidoc");
        mojo.asciidocSourceDirectory = new File(dir, "src/docs/asciidoc");
        mojo.asciidocSourcesDirectory = new File(target, "kardx/asciidoc-sources");
        mojo.index = true;
        mojo.indexTemplate = new File(dir, "src/docs/asciidoc/index-template.adoc");
        mojo.indexTitle = "memo-service";
        mojo.asyncApi = true;
        mojo.asyncApiFileName = "asyncapi";

        return mojo;
    }

    private <T extends AbstractApiDocsMojo> T setUp(T mojo) {
        mojo.project = project;
        mojo.snippetsDirectory = new File(target, "generated-snippets");
        mojo.outputDirectory = new File(target, "api-docs");
        mojo.workDirectory = new File(target, "kardx");

        return mojo;
    }
}
