package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gradle TestKit: {@code openApiInfo { }} 와 클래스 KDoc 스키마 설명이 {@code openapi3} 후처리로 스펙에 들어간다 (Groovy·Kotlin DSL,
 * configuration cache).
 */
class OpenApiMetadataFunctionalTest {
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /users: {get: {tags: [Users], responses: {"200": {description: OK}}}}
          /orders:
            get:
              tags: [Orders]
              responses:
                "200":
                  description: OK
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/OrderItem"}
        components:
          schemas:
            OrderItem: {type: object, properties: {sku: {type: string}}}
        """;

    @Test
    void groovyDslFillsInfoTagsAndSchemaDescriptionsWithConfigurationCache(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
                id 'com.epages.restdocs-api-spec'
            }
            kardx {
                asyncApi { enabled = false }
                addDependencies = false
                cleanBeforeTest = false
                snippetsDirectory = layout.projectDirectory.dir('snippets')
                sourceDocs { enabled = true }
                openApiInfo {
                    descriptionFile = file('src/docs/openapi-intro.md')
                    termsOfService = 'https://example.com/terms'
                    contact { name = 'API team'; email = 'api@example.com' }
                    license { name = 'Apache 2.0'; url = 'https://www.apache.org/licenses/LICENSE-2.0' }
                    externalDocs { url = 'https://wiki.example.com'; description = 'Guide' }
                    tag('Orders') {
                        description = 'Order history'
                        externalDocs { url = 'https://wiki.example.com/orders' }
                    }
                    tag('Users')
                    tagGroup('Shopping', 'Orders')
                }
            }
            tasks.matching { it.name == 'openapi3' }.configureEach { t ->
                def source = file('spec.yaml')
                def target = layout.buildDirectory.file('api-spec/openapi3.yaml').get().asFile
                t.doLast { target.parentFile.mkdirs(); target.text = source.text }
            }
            """);
        write(root, "spec.yaml", SPEC);
        write(root, "src/docs/openapi-intro.md", "# Shop API\n\nAll prices are in KRW.\n\n");
        write(root, "src/main/java/com/shop/order/Item.java", """
            package com.shop.order;

            /** One line of an order. */
            public class Item {
                public String sku;
            }
            """);
        // 라이브러리가 남기는 조각: 이름 충돌 정리 뒤의 스키마 이름과 이름을 만든 타입
        write(root, "snippets/order/list/resource.json", """
            {"operationId": "order/list", "request": {"path": "/orders", "method": "GET"},
             "response": {"status": 200, "schema": {"name": "OrderItem"}}}
            """);
        write(root, "snippets/order/list/openapi-schemas.json", """
            {"request": null, "response": {"name": "Item", "automatic": true, "type": "com.shop.order.Item",
             "candidates": ["Item", "OrderItem"]}}
            """);

        BuildResult first = gradle(root, "openapi3", "--configuration-cache").build();
        BuildResult second = gradle(root, "openapi3", "--configuration-cache").build();
        Map<String, Object> document = spec(root);
        Map<String, Object> info = SpecMaps.map(document, "info");

        assertEquals("# Shop API\n\nAll prices are in KRW.", info.get("description"), first.getOutput());
        assertEquals("https://example.com/terms", info.get("termsOfService"));
        assertEquals(Map.of("name", "API team", "email", "api@example.com"), info.get("contact"));
        assertEquals("Apache 2.0", SpecMaps.map(info, "license").get("name"));
        assertEquals(Map.of("description", "Guide", "url", "https://wiki.example.com"), document.get("externalDocs"));
        assertEquals(List.of(
            Map.of("name", "Orders", "description", "Order history",
                "externalDocs", Map.of("url", "https://wiki.example.com/orders")),
            Map.of("name", "Users")), document.get("tags"));
        assertEquals(List.of(Map.of("name", "Shopping", "tags", List.of("Orders")),
            Map.of("name", "Other", "tags", List.of("Users"))), document.get("x-tagGroups"));
        assertEquals("One line of an order.",
            SpecMaps.map(SpecMaps.componentSchemas(document), "OrderItem").get("description"));
        assertTrue(second.getOutput().contains("Reusing configuration cache")
            || second.getOutput().contains("Configuration cache entry reused"), second.getOutput());

        Path build = root.toPath().resolve("build.gradle");

        Files.writeString(build, Files.readString(build)
            .replace("sourceDocs { enabled = true }", "sourceDocs { enabled = true; schemaDescriptions = false }"));
        gradle(root, "openapi3", "--configuration-cache").build();

        assertNull(SpecMaps.map(SpecMaps.componentSchemas(spec(root)), "OrderItem").get("description"),
            "schemaDescriptions = false 면 스키마 설명을 넣지 않는다");
    }

    @Test
    void kotlinDslSetsTheSameValuesAndWarnsAboutUnusedTags(@TempDir File root) throws IOException {
        write(root, "settings.gradle.kts", "rootProject.name = \"shop\"\n");
        write(root, "build.gradle.kts", """
            plugins {
                java
                id("io.github.kardx-project")
                id("com.epages.restdocs-api-spec")
            }
            kardx {
                asyncApi { enabled.set(false) }
                addDependencies.set(false)
                openApiInfo {
                    description.set("Inline intro")
                    license { name.set("MIT") }
                    tag("Users") { description.set("Profiles") }
                    tag("Payments")
                }
            }
            tasks.matching { it.name == "openapi3" }.configureEach {
                val source = file("spec.yaml")
                val target = layout.buildDirectory.file("api-spec/openapi3.yaml").get().asFile
                doLast { target.parentFile.mkdirs(); target.writeText(source.readText()) }
            }
            """);
        write(root, "spec.yaml", SPEC);

        BuildResult result = gradle(root, "openapi3").build();
        Map<String, Object> document = spec(root);

        assertEquals("Inline intro", SpecMaps.map(document, "info").get("description"));
        assertEquals(Map.of("name", "MIT"), SpecMaps.map(document, "info").get("license"));
        assertEquals(List.of("Users", "Orders"), SpecMaps.maps(document.get("tags"))
            .stream()
            .map(tag -> tag.get("name"))
            .toList());
        assertTrue(result.getOutput().contains("OpenAPI info: tag 'Payments' is not used"), result.getOutput());
    }

    @Test
    void failsWhenBothDescriptionAndFileAreSet(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
                id 'com.epages.restdocs-api-spec'
            }
            kardx {
                asyncApi { enabled = false }
                addDependencies = false
                openApiInfo {
                    description = 'Inline'
                    descriptionFile = file('intro.md')
                }
            }
            tasks.matching { it.name == 'openapi3' }.configureEach { t ->
                def target = layout.buildDirectory.file('api-spec/openapi3.yaml').get().asFile
                t.doLast { target.parentFile.mkdirs(); target.text = file('spec.yaml').text }
            }
            """);
        write(root, "spec.yaml", SPEC);
        write(root, "intro.md", "From file");

        BuildResult result = gradle(root, "openapi3").buildAndFail();

        assertTrue(result.getOutput().contains("Set either kardx.openApiInfo.description or descriptionFile"),
            result.getOutput());
    }

    private static Map<String, Object> spec(File root) throws IOException {
        File spec = new File(root, "build/api-spec/openapi3.yaml");

        return SpecMaps.map(SwaggerYaml.load(Files.readString(spec.toPath())));
    }

    private static GradleRunner gradle(File root, String... arguments) {
        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(List.of(arguments));
    }

    private static void write(File root, String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
