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

/** Gradle TestKit 으로 실제 빌드를 돌려 확인한다: 멀티 프로젝트 집계, 기준 스펙 비교, operationId 결정, 공통 응답. */
class ApiDocsFunctionalTest {
    private static final String ORDER_SPEC = """
        openapi: 3.0.1
        info: {title: order, version: 1}
        paths:
          /orders:
            get:
              operationId: list
              responses:
                "200":
                  description: OK
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/Item"}
        components:
          schemas:
            Item: {type: object, required: [sku], properties: {sku: {type: string}}}
        """;
    private static final String MEMBER_SPEC = """
        openapi: 3.0.1
        info: {title: member, version: 1}
        paths:
          /members:
            get:
              operationId: list
              responses:
                "200":
                  description: OK
                  content:
                    application/json:
                      schema: {$ref: "#/components/schemas/Item"}
        components:
          schemas:
            Item: {type: object, properties: {name: {type: string}}}
        """;
    /** 가짜 epages: openapi3 가 spec.yaml 을 결과 위치에 복사한다. 플러그인의 후처리가 그 뒤에 돈다. */
    private static final String FAKE_OPENAPI3 = """
        tasks.matching { it.name == 'openapi3' }.configureEach { t ->
            def source = file('spec.yaml')
            def target = layout.buildDirectory.file('api-spec/openapi3.yaml').get().asFile
            t.doLast { target.parentFile.mkdirs(); target.text = source.text }
        }
        """;
    private static final String DOCS_PROJECT = """
        plugins {
            id 'java'
            id 'io.github.kardx-project'
        }
        // 저장소가 없는 픽스처라 라이브러리를 넣지 않는다
        kardx { asyncApi { enabled = false }; addDependencies = false }
        // 테스트 없이 스펙만 둔다: apiDocs 결과에 spec/ 을 더한다
        tasks.named('apiDocs') { from('spec') }
        """;

    @Test
    void aggregatesSubprojectsThroughVariantsWithConfigurationCache(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\ninclude 'order', 'services:member'\n");
        write(root, "build.gradle", """
            plugins { id 'io.github.kardx-project' }
            dependencies {
                restdocsAggregation project(':order')
                restdocsAggregation project(':services:member')
            }
            kardx {
                aggregate { title = 'Shop API'; version = '2.0.0' }
                openApiViewer { enabled = true }
            }
            """);
        write(root, "order/build.gradle", DOCS_PROJECT);
        write(root, "order/spec/openapi3.yaml", ORDER_SPEC);
        write(root, "services/member/build.gradle", DOCS_PROJECT);
        write(root, "services/member/spec/openapi3.yaml", MEMBER_SPEC);
        write(root, "services/member/spec/async/index.yaml", "asyncapi: 3.0.0\ninfo: {title: m, version: 1}\n");
        write(root, "services/member/spec/async/index.html", "<html></html>");

        BuildResult first = gradle(root, "apiDocsAggregate", "--configuration-cache").build();
        File output = new File(root, "build/api-docs-aggregate");
        String merged = Files.readString(new File(output, "openapi3.yaml").toPath());
        String index = Files.readString(new File(output, "index.html").toPath());

        assertEquals(TaskOutcome.SUCCESS, outcome(first, ":apiDocsAggregate"));
        assertEquals(TaskOutcome.SUCCESS, outcome(first, ":order:apiDocs"), "다른 프로젝트의 apiDocs 를 variant 로 끌어온다");
        assertTrue(merged.contains("title: Shop API") && merged.contains("/orders:") && merged.contains("/members:"),
            merged);
        assertTrue(merged.contains("ServicesMemberItem:") && merged.contains("operationId: services-member-list"),
            "이름 충돌 규칙\n" + merged);
        assertTrue(new File(output, "order/openapi3.yaml").isFile());
        assertTrue(new File(output, "openapi.html").isFile(), "집계 프로젝트의 openApiViewer 로 병합 스펙 뷰어");
        assertTrue(index.contains("href=\"services/member/async/index.html\""), index);
        assertTrue(index.contains("href=\"openapi3.yaml\""), index);
        assertTrue(index.contains("<html lang=\"en\">") && index.contains("<p>Merged OpenAPI: ")
            && index.contains("<th>Project</th>"), "기본 언어는 영어\n" + index);

        BuildResult second = gradle(root, "apiDocsAggregate", "--configuration-cache").build();

        assertTrue(second.getOutput().contains("Reusing configuration cache"), second.getOutput());
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, ":apiDocsAggregate"));
    }

    /** apiVersioning.splitSpecs 의 버전 스펙({@code openapi3-v1.yaml})은 같은 operation 을 담는다. 합치지 않고 목록에만 둔다. */
    @Test
    void aggregationMergesOnlyTheMainSpecOfAProjectWithVersionSpecs(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\ninclude 'order'\n");
        write(root, "build.gradle", """
            plugins { id 'io.github.kardx-project' }
            dependencies { restdocsAggregation project(':order') }
            """);
        write(root, "order/build.gradle", DOCS_PROJECT);
        write(root, "order/spec/openapi3.yaml", ORDER_SPEC);
        write(root, "order/spec/openapi3-v1.yaml", ORDER_SPEC);

        gradle(root, "apiDocsAggregate").build();

        File output = new File(root, "build/api-docs-aggregate");
        String merged = Files.readString(new File(output, "openapi3.yaml").toPath());
        String index = Files.readString(new File(output, "index.html").toPath());

        assertTrue(merged.contains("/orders:") && merged.contains("operationId: list"), merged);
        assertTrue(index.contains("href=\"order/openapi3-v1.yaml\"") && index.contains("href=\"order/openapi3.yaml\""),
            index);
    }

    /** epages 는 결과 디렉터리를 비우지 않는다. 형식을 바꾸면 지난 형식의 스펙이 남아 먼저 읽히면 안 된다. */
    @Test
    void openapi3RemovesTheSpecOfAnEarlierFormat(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
                id 'com.epages.restdocs-api-spec'
            }
            kardx { asyncApi { enabled = false }; addDependencies = false }
            // 가짜 epages: openapi3 가 spec.yaml 을 -Pformat 의 확장자로 결과 위치에 복사한다
            tasks.matching { it.name == 'openapi3' }.configureEach { t ->
                def source = file('spec.yaml')
                def extension = providers.gradleProperty('format').getOrElse('yaml')
                def target = layout.buildDirectory.file("api-spec/openapi3.$extension").get().asFile
                t.doLast {
                    target.parentFile.mkdirs()
                    target.text = extension == 'json' ? groovy.json.JsonOutput.toJson(
                        new org.yaml.snakeyaml.Yaml().load(source.text)) : source.text
                }
            }
            """);
        write(root, "spec.yaml", ORDER_SPEC);
        write(root, "build/api-spec/openapi3-v9.yaml", ORDER_SPEC);

        gradle(root, "apiDocs").build();
        gradle(root, "apiDocs", "-Pformat=json").build();

        assertTrue(new File(root, "build/api-spec/openapi3.json").isFile());
        assertTrue(!new File(root, "build/api-spec/openapi3.yaml").exists(), "지난 형식의 스펙은 지운다");
        assertTrue(!new File(root, "build/api-spec/openapi3-v9.yaml").exists(), "지난 버전 스펙도 지운다");
        assertTrue(!new File(root, "build/api-docs/openapi3.yaml").exists(), "apiDocs 에도 남지 않는다");
    }

    @Test
    void checkFailsWithSummaryAndBaselineUpdates(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
                id 'com.epages.restdocs-api-spec'
            }
            kardx {
                asyncApi { enabled = false }
                openApiCheck { failOn = providers.gradleProperty('failOn').getOrElse('any') }
            }
            // 가짜 epages: openapi3 가 spec.yaml 을 결과 위치에 복사한다
            tasks.matching { it.name == 'openapi3' }.configureEach { t ->
                def source = file('spec.yaml')
                def target = layout.buildDirectory.file('api-spec/openapi3.yaml').get().asFile
                t.doLast { target.parentFile.mkdirs(); target.text = source.text }
            }
            """);
        write(root, "spec.yaml", ORDER_SPEC);

        BuildResult missing = gradle(root, "apiDocsCheck").buildAndFail();

        assertTrue(missing.getOutput().contains("No OpenAPI baseline"), missing.getOutput());

        gradle(root, "apiDocsBaseline").build();

        assertEquals(Files.readString(new File(root, "build/api-spec/openapi3.yaml").toPath()),
            Files.readString(new File(root, "docs/openapi/openapi3.yaml").toPath()), "후처리까지 끝난 스펙을 기준으로 둔다");
        assertEquals(TaskOutcome.SUCCESS, outcome(gradle(root, "apiDocsCheck").build(), ":apiDocsCheck"));

        write(root, "spec.yaml", ORDER_SPEC.replace("required: [sku], ", "").replace("version: 1", "version: 2"));

        BuildResult changed = gradle(root, "apiDocsCheck").buildAndFail();
        BuildResult breakingOnly = gradle(root, "apiDocsCheck", "-PfailOn=breaking").buildAndFail();

        assertTrue(changed.getOutput().contains("Response field became optional: GET /orders 200 application/json sku"),
            changed.getOutput());
        assertTrue(changed.getOutput().contains("changed  info > version: 1 → 2"), changed.getOutput());
        assertTrue(breakingOnly.getOutput().contains("Breaking changes: 1"), breakingOnly.getOutput());

        write(root, "spec.yaml", ORDER_SPEC.replace("version: 1", "version: 2"));

        BuildResult nonBreaking = gradle(root, "apiDocsCheck", "-PfailOn=breaking").build();

        assertTrue(nonBreaking.getOutput().contains("Breaking changes: 0"), nonBreaking.getOutput());
    }

    @Test
    void decidesOperationIdsFromLibraryCandidatesAndWarnsOnDuplicateFixedIds(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
                id 'com.epages.restdocs-api-spec'
            }
            kardx {
                asyncApi { enabled = false }
                cleanBeforeTest = false
                snippetsDirectory = layout.projectDirectory.dir('snippets')
            }
            // 가짜 epages: openapi3 가 spec.yaml 을 결과 위치에 복사한다. 플러그인의 후처리가 그 뒤에 operationId 를 정한다
            tasks.matching { it.name == 'openapi3' }.configureEach { t ->
                def source = file('spec.yaml')
                def target = layout.buildDirectory.file('api-spec/openapi3.yaml').get().asFile
                t.doLast { target.parentFile.mkdirs(); target.text = source.text }
            }
            """);
        // 라이브러리가 남기는 조각: resource.json(테스트 이름 id)과 openapi-extras.json 의 operationId 후보
        snippet(root, "t/create-user", "POST", "/users", "{\"id\":\"create\",\"qualified\":\"userCreate\"}");
        snippet(root, "t/create-order", "POST", "/orders", "{\"id\":\"create\",\"qualified\":\"orderCreate\"}");
        snippet(root, "t/search", "GET", "/users/search", "{\"id\":\"searchUsers\",\"qualified\":\"userSearchUsers\"}");
        snippet(root, "t/a", "GET", "/a", "{\"id\":\"same\",\"fixed\":true}");
        snippet(root, "t/b", "GET", "/b", "{\"id\":\"same\",\"fixed\":true}");
        write(root, "spec.yaml", """
            openapi: 3.0.1
            info: {title: shop, version: 1}
            paths:
              /users: {post: {operationId: t/create-user, responses: {"201": {description: "201"}}}}
              /orders: {post: {operationId: t/create-order, responses: {"201": {description: "201"}}}}
              /users/search: {get: {operationId: t/search, responses: {"200": {description: "200"}}}}
              /a: {get: {operationId: t/a, responses: {"200": {description: "200"}}}}
              /b: {get: {operationId: t/b, responses: {"200": {description: "200"}}}}
            """);

        BuildResult result = gradle(root, "openapi3").build();
        String spec = Files.readString(new File(root, "build/api-spec/openapi3.yaml").toPath());

        assertTrue(spec.contains("operationId: userCreate") && spec.contains("operationId: orderCreate"), spec);
        assertTrue(spec.contains("operationId: searchUsers"), spec);
        assertEquals(2, spec.split("operationId: same\n", -1).length - 1, spec);
        assertTrue(result.getOutput().contains("OpenAPI operationId: 'same' is set on several operations (GET /a, GET /b)"),
            result.getOutput());
    }

    @Test
    void checkFailsWithoutBaselineUnlessLenient(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
                id 'com.epages.restdocs-api-spec'
            }
            kardx {
                asyncApi { enabled = false }
                openApiCheck {
                    failOn = providers.gradleProperty('failOn').getOrElse('breaking')
                    requireBaseline = providers.gradleProperty('requireBaseline').map { it.toBoolean() }.orElse(true)
                }
            }
            """ + FAKE_OPENAPI3);
        write(root, "spec.yaml", ORDER_SPEC);

        BuildResult strict = gradle(root, "apiDocsCheck").buildAndFail();
        BuildResult lenient = gradle(root, "apiDocsCheck", "-PrequireBaseline=false").build();
        BuildResult none = gradle(root, "apiDocsCheck", "-PfailOn=none").build();

        assertTrue(strict.getOutput().contains("No OpenAPI baseline")
            && strict.getOutput().contains("./gradlew apiDocsBaseline")
            && strict.getOutput().contains("requireBaseline = false"), strict.getOutput());
        assertTrue(lenient.getOutput().contains("No OpenAPI baseline"), "예전처럼 경고만 한다\n" + lenient.getOutput());
        assertEquals(TaskOutcome.SUCCESS, outcome(none, ":apiDocsCheck"), "failOn = none 은 실패하지 않는다");
    }

    @Test
    void commonResponsesReachOpenApiAndIndexOnce(@TempDir File root) throws IOException {
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
                // 빌드 디렉터리 밖: Gradle 이 처음 빌드에서 test 출력 위치의 오래된 파일로 보고 지우지 않게 한다
                asciidocDirectory = layout.projectDirectory.dir('generated-asciidoc')
                openApiCommonResponses.put(401, 'Token missing | expired')
                openApiCommonResponses.put(500, '')
            }
            """ + FAKE_OPENAPI3);
        // index CLI 대신 제목 한 줄을 쓰는 가짜 (apiDocsIndex 는 테스트 런타임 클래스패스의 이 클래스를 실행한다)
        write(root, "src/main/java/io/github/kardx/asciidoc/DocumentationIndexCli.java", """
            package io.github.kardx.asciidoc;

            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.util.List;
import java.util.Map;

            public class DocumentationIndexCli {
                public static void main(String[] args) throws Exception {
                    List<String> options = List.of(args);
                    Path output = Path.of(options.get(options.indexOf("--output") + 1));

                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "= " + options.get(options.indexOf("--title") + 1) + "\\n");
                }
            }
            """);
        write(root, "generated-asciidoc/.restdocs-documents/order.properties", "document=order\n");
        write(root, "spec.yaml", """
            openapi: 3.0.1
            info: {title: shop, version: 1}
            paths:
              /orders:
                get:
                  security:
                  - bearerAuthJWT: []
                  responses:
                    "200": {description: OK}
                    "404":
                      description: Not Found
                      content:
                        application/json:
                          schema: {$ref: "#/components/schemas/ErrorResponse"}
              /health:
                get:
                  responses:
                    "200": {description: OK}
            components:
              schemas:
                ErrorResponse: {type: object, properties: {code: {type: string}}}
            """);

        BuildResult first = gradle(root, "openapi3", "apiDocsIndex").build();
        BuildResult second = gradle(root, "apiDocsIndex").build();
        String spec = Files.readString(new File(root, "build/api-spec/openapi3.yaml").toPath());
        File indexFile = new File(root, "build/kardx/index/index.adoc");

        assertTrue(indexFile.isFile(), first.getOutput());

        String index = Files.readString(indexFile.toPath());
        Map<String, Object> document = SpecMaps.map(SwaggerYaml.load(spec));
        Map<String, Object> orders = SpecMaps.map(SpecMaps.operation(document, "/orders", "get"), "responses");
        Map<String, Object> health = SpecMaps.map(SpecMaps.operation(document, "/health", "get"), "responses");
        Map<String, Object> unauthorized = SpecMaps.map(orders, "401");

        assertEquals(TaskOutcome.SUCCESS, outcome(first, ":apiDocsIndex"));
        assertEquals(List.of("200", "401", "404", "500"), List.copyOf(orders.keySet()), "상태 코드 순서\n" + spec);
        assertEquals("Token missing | expired", unauthorized.get("description"));
        assertEquals(Map.of("application/json", Map.of("schema", Map.of("$ref", "#/components/schemas/ErrorResponse"))),
            unauthorized.get("content"), "오류 응답이 쓰는 스키마를 가리킨다");
        assertEquals(List.of("200", "500"), List.copyOf(health.keySet()), "보안이 없는 operation 에는 401 을 넣지 않는다");
        assertEquals("Internal Server Error", SpecMaps.map(health, "500").get("description"));
        assertTrue(index.startsWith("= shop\n") && index.contains("== Common error responses"), index);
        assertTrue(index.contains("|`401`\n|Token missing \\| expired"), index);
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, ":apiDocsIndex"));
        assertEquals(1, index.split("== Common error responses", -1).length - 1, index);
    }

    @Test
    void declaredErrorResponsesReachOpenApiBeforeCommonResponses(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
                id 'com.epages.restdocs-api-spec'
            }
            kardx {
                asyncApi { enabled = false }
                cleanBeforeTest = false
                snippetsDirectory = layout.projectDirectory.dir('snippets')
                openApiCommonResponses.put(409, 'Common conflict')
                openApiCommonResponses.put(500, '')
            }
            """ + FAKE_OPENAPI3);
        // 라이브러리가 남기는 조각: 테스트는 201 만 만들고 409 를 선언했다
        write(root, "snippets/order/create/resource.json", """
            {"operationId": "order/create", "request": {"path": "/orders", "method": "POST"},
             "response": {"status": 201}}
            """);
        write(root, "snippets/order/create/openapi-extras.json", """
            {"errorResponses": [{"status": 409, "description": "Duplicate order", "contentType": "application/json",
              "schema": {"type": "object", "properties": {"code": {"type": "string"}}},
              "example": "{\\n  \\"code\\": \\"DUPLICATE\\"\\n}"}]}
            """);
        write(root, "spec.yaml", """
            openapi: 3.0.1
            info: {title: shop, version: 1}
            paths:
              /orders: {post: {operationId: order/create, responses: {"201": {description: Created}}}}
            """);

        BuildResult result = gradle(root, "openapi3", "--info").build();
        String spec = Files.readString(new File(root, "build/api-spec/openapi3.yaml").toPath());
        Map<String, Object> document = SpecMaps.map(SwaggerYaml.load(spec));
        Map<String, Object> responses = SpecMaps.map(SpecMaps.operation(document, "/orders", "post"), "responses");
        Map<String, Object> conflict = SpecMaps.map(SpecMaps.map(SpecMaps.map(responses, "409"), "content"),
            "application/json");

        assertEquals(List.of("201", "409", "500"), List.copyOf(responses.keySet()), spec);
        assertEquals("Duplicate order", SpecMaps.map(responses, "409").get("description"), "선언이 공통 응답을 이긴다");
        assertEquals(Map.of("$ref", "#/components/schemas/ConflictResponse"), conflict.get("schema"), spec);
        assertEquals(Map.of("code", "DUPLICATE"), conflict.get("example"), spec);
        assertTrue(result.getOutput().contains("OpenAPI: added 1 declared responses"), result.getOutput());
        assertTrue(result.getOutput().contains("OpenAPI: added 1 common responses"), result.getOutput());
    }

    private static void snippet(File root, String id, String method, String path, String candidate) throws IOException {
        write(root, "snippets/" + id + "/resource.json", """
            {"operationId": "%s", "summary": "%s", "request": {"path": "%s", "method": "%s"}, "response": {"status": %d}}
            """.formatted(id, id, path, method, method.equals("POST") ? 201 : 200));
        write(root, "snippets/" + id + "/openapi-extras.json", "{\"operationId\": " + candidate + "}");
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
