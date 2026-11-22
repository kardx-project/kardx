package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** 테스트 픽스처: 문서 조각 디렉터리({@code resource.json} + {@code openapi-extras.json})와 YAML 스펙. */
final class SnippetFixtures {
    private SnippetFixtures() {
    }

    /** [snippets]/[name] 에 조각을 쓴다. [extras] 가 null 이면 extras 파일을 쓰지 않는다. */
    static void fragment(File snippets, String name, String method, String path, int status, String extras)
        throws IOException {
        File directory = new File(snippets, name);

        Files.createDirectories(directory.toPath());
        Files.writeString(new File(directory, "resource.json").toPath(), """
            { "operationId": "%s", "summary": "%s",
              "request": { "path": "%s", "method": "%s", "contentType": "application/json" },
              "response": { "status": %d, "contentType": "application/json" } }
            """.formatted(name, name, path, method, status), StandardCharsets.UTF_8);

        if (extras != null) {
            Files.writeString(new File(directory, OpenApiResources.EXTRAS_FILE).toPath(), extras,
                StandardCharsets.UTF_8);
        }
    }

    /** 고쳐도 되는 문서 트리. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(String text) {
        return (Map<String, Object>) SpecMaps.deepCopy(SwaggerYaml.load(text));
    }
}
