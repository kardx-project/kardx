package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kotlin DSL 빌드 스크립트로 {@code coverage}·{@code httpClient}·{@code openApiSecurity}·{@code apiVersioning} 을 설정하고
 * {@code apiDocs} 결과와 증분 실행을 확인한다. 조각과 엔드포인트 목록은 라이브러리가 남기는 형식의 픽스처다.
 */
class DocsReportsFunctionalTest {
    private static final String BUILD_SCRIPT = """
        plugins {
            java
            id("io.github.kardx-project")
            id("com.epages.restdocs-api-spec")
        }

        kardx {
            asyncApi { enabled = false }
            addDependencies = false
            cleanBeforeTest = false
            snippetsDirectory = layout.projectDirectory.dir("snippets")
            coverage {
                exclude("/internal/**")
            }
            httpClient {
                enabled = true
            }
            openApiSecurity {
                oauth2 {
                    clientCredentials("https://auth.example.com/oauth/token")
                    scope("users:read", "Read users")
                }
                openIdConnect("https://auth.example.com/.well-known/openid-configuration")
            }
            apiVersioning {
                header("X-API-Version")
                splitSpecs = true
            }
        }

        // 가짜 epages: openapi3 가 spec.yaml 을 결과 위치에 복사한다. 플러그인의 후처리가 그 뒤에 돈다
        tasks.matching { it.name == "openapi3" }.configureEach {
            val source = file("spec.yaml")
            val target = layout.buildDirectory.file("api-spec/openapi3.yaml").get().asFile
            doLast {
                target.parentFile.mkdirs()
                target.writeText(source.readText())
            }
        }
        """;

    @Test
    void apiDocsCollectsCoverageHttpClientAndVersionSpecs(@TempDir File root) throws IOException {
        write(root, "settings.gradle.kts", "rootProject.name = \"shop\"\n");
        write(root, "build.gradle.kts", BUILD_SCRIPT);
        write(root, "spec.yaml", """
            openapi: 3.0.1
            info: {title: shop, version: 1}
            paths:
              /users:
                get:
                  operationId: users/list-v1
                  security: [{bearerAuthJWT: []}]
                  responses: {"200": {description: OK}}
            components:
              securitySchemes:
                bearerAuthJWT: {type: http, scheme: bearer, bearerFormat: JWT}
            """);
        fragment(root, "users/list-v1", "1.0");
        fragment(root, "users/list-v2", "2.0");
        write(root, "snippets/users/list-v1/http-request.adoc", """
            [source,http,options="nowrap"]
            ----
            GET /users?page=0&access_token=**** HTTP/1.1
            Authorization: Bearer ****
            Cookie: SESSION=****
            Host: localhost:8080

            ----
            """);
        write(root, "snippets/.kardx/endpoints/3f2a9c.json", """
            { "endpoints": [
                { "method": "GET", "path": "/users", "handler": "com.example.UserController#listV1",
                  "deprecated": true, "apiVersion": "1.0" },
                { "method": "GET", "path": "/users", "handler": "com.example.UserController#listV2",
                  "deprecated": false, "apiVersion": "2.0" },
                { "method": "POST", "path": "/orders/{orderId}", "handler": "com.example.OrderController#pay",
                  "deprecated": false, "apiVersion": null },
                { "method": "GET", "path": "/internal/stats", "handler": null, "deprecated": false,
                  "apiVersion": null },
                { "method": "GET", "path": "/actuator/health", "handler": null, "deprecated": false,
                  "apiVersion": null } ] }
            """);

        BuildResult first = gradle(root, "apiDocs", "--configuration-cache").build();
        File docs = new File(root, "build/api-docs");
        String http = Files.readString(new File(docs, "http-client/api.http").toPath());
        Map<String, Object> spec = SpecMaps.map(SwaggerYaml.load(
            Files.readString(new File(docs, "openapi3.yaml").toPath())));
        Map<String, Object> users = SpecMaps.operation(spec, "/users", "get");

        assertTrue(first.getOutput().contains("API docs coverage: 2/3 endpoints documented (1 undocumented, see "
            + "build/api-docs/coverage/coverage.md)"), first.getOutput());
        assertTrue(Files.readString(new File(docs, "coverage/coverage.md").toPath())
            .contains("| POST | `/orders/{orderId}` | `com.example.OrderController#pay` |"));
        assertTrue(new File(docs, "coverage/coverage.json").isFile());
        assertTrue(http.contains("# @name users-list\nGET {{baseUrl}}/users?page=0&access_token={{access_token}}\n"
            + "Authorization: Bearer {{bearerToken}}\nCookie: SESSION={{SESSION}}"), http);
        assertEquals("2.0", users.get("x-api-version"), "가장 높은 버전");
        assertEquals(List.of(Map.of("oauth2", List.of("users:read"))), users.get("security"));
        assertTrue(SpecMaps.map(SpecMaps.map(spec, "components"), "securitySchemes").containsKey("openIdConnect"));
        assertTrue(new File(docs, "openapi3-v1.0.yaml").isFile() && new File(docs, "openapi3-v2.0.yaml").isFile(),
            "버전별 스펙도 apiDocs 에 들어간다");
        assertTrue(Files.readString(new File(docs, "http-client/http-client.env.json").toPath())
            .contains("\"access_token\": \"\""));
        assertTrue(Files.readString(new File(docs, "http-client/http-client.private.env.json").toPath())
            .contains("\"SESSION\": \"\""), "가린 값의 변수는 비밀 환경 파일에도 빈 값으로 선언한다");

        BuildResult second = gradle(root, "apiDocs", "--configuration-cache").build();

        assertTrue(second.getOutput().contains("Reusing configuration cache"), second.getOutput());
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, ":apiDocsCoverage"));
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, ":apiDocsHttpClient"));
    }

    private static void fragment(File root, String id, String version) throws IOException {
        write(root, "snippets/" + id + "/resource.json", """
            {"operationId": "%s", "summary": "%s", "request": {"path": "/users", "method": "GET"},
             "response": {"status": 200}}
            """.formatted(id, id));
        write(root, "snippets/" + id + "/openapi-extras.json", """
            {"apiVersion": "%s", "security": [{"type": "oauth2", "scopes": ["users:read"]}]}
            """.formatted(version));
    }

    private static GradleRunner gradle(File root, String... arguments) {
        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(List.of(arguments));
    }

    private static TaskOutcome outcome(BuildResult result, String path) {
        return result.task(path) == null ? null : result.task(path).getOutcome();
    }

    private static void write(File root, String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
